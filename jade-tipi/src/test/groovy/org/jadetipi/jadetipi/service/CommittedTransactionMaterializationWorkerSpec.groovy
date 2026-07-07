/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 *
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 *
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 *
 * https://jade-tipi.org/license
 */
package org.jadetipi.jadetipi.service

import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 * TASK-052 (UT-7) coverage for the background materialization worker:
 * materialize-then-stamp with the watermark guard, the skip for
 * already-watermarked headers, no stamp on projection failure, and the
 * sweep's committed-but-unwatermarked selection — plus the TASK-063
 * snapshot-isolation duties: the oldest-open-snapshot watermark gate, the
 * legacy fallbacks on both sides of the comparison, and the lease pass that
 * durably rolls back expired opens. The nudge/sweep pipelines themselves are
 * proven by the integration specs; these features pin the per-pass units of
 * work.
 */
class CommittedTransactionMaterializationWorkerSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee~test-org~test-grp~jade-cli'
    static final String COLLECTION = 'txn'
    // v7 uuids: SNAPSHOT_OLD < COMMIT_MID < SNAPSHOT_NEW lexicographically and temporally
    static final String SNAPSHOT_OLD = '018fd849-9b01-7111-8a01-a1a1a1a1a1a1'
    static final String COMMIT_MID = '018fd849-9b02-7222-8a02-a2a2a2a2a2a2'
    static final String SNAPSHOT_NEW = '018fd849-9b03-7333-8a03-a3a3a3a3a3a3'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionMaterializer materializer
    CommittedTransactionMaterializationWorker worker

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        materializer = Mock(CommittedTransactionMaterializer)
        worker = new CommittedTransactionMaterializationWorker(
                mongoTemplate, materializer, true, Duration.ofSeconds(30), Duration.ofHours(1))
    }

    private static Map committedHeader(Map overrides = [:]) {
        Map base = [
                _id        : TXN_ID,
                txn_id     : TXN_ID,
                record_type: 'transaction',
                state      : 'committed',
                commit_id  : 'COMMIT-001'
        ]
        base.putAll(overrides)
        return base
    }

    private static Map openHeader(Map overrides = [:]) {
        Map base = [
                _id        : 'bbbbbbbb-0000-7000-8000-000000000000~test-org~test-grp~jade-cli',
                record_type: 'transaction',
                state      : 'open',
                opened_at  : Instant.parse('2026-01-01T00:00:00Z'),
                snapshot_id: SNAPSHOT_OLD
        ]
        base.putAll(overrides)
        return base
    }

    /**
     * The sweep issues three find shapes against txn: the lease-expiry query
     * (state open + opened_at bound), the watermark query (state open), and
     * the committed-unwatermarked selection. Discriminate on the query.
     */
    private void stubSweepFinds(List<Map> expiredOpens, List<Map> opens, List<Map> committed,
                                List<Query> committedQueryCapture = null) {
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> { Query q, Class _t, String _c ->
            Map queryObject = q.getQueryObject()
            if (queryObject.get('state') == 'open') {
                return queryObject.containsKey('opened_at') ?
                        Flux.fromIterable(expiredOpens) : Flux.fromIterable(opens)
            }
            committedQueryCapture?.add(q)
            return Flux.fromIterable(committed)
        }
    }

    def 'materializeAndStamp materializes and stamps the watermark with a guarded update'() {
        given:
        Query capturedQuery = null
        Update capturedUpdate = null
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> { Query q, Update u, String _c ->
            capturedQuery = q
            capturedUpdate = u
            return Mono.empty()
        }

        when:
        MaterializeResult result = worker.materializeAndStamp(TXN_ID).block()

        then:
        result != null

        and: 'the stamp is guarded on committed state and the watermark still being absent'
        capturedQuery.getQueryObject().get('_id') == TXN_ID
        capturedQuery.getQueryObject().get('state') == 'committed'
        (capturedQuery.getQueryObject().get('materialized_at') as Map).get('$exists') == false

        and: 'the update sets only the watermark'
        Map setOps = capturedUpdate.getUpdateObject().get('$set') as Map
        setOps.keySet() == ['materialized_at'] as Set
        setOps.get('materialized_at') instanceof Instant
    }

    def 'materializeAndStamp skips a header that is already watermarked'() {
        given:
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(
                committedHeader(materialized_at: Instant.parse('2026-01-01T00:00:10Z')))

        when:
        MaterializeResult result = worker.materializeAndStamp(TXN_ID).block()

        then:
        result == null
        0 * materializer.materialize(_)
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'a failed projection stamps nothing and stays sweep-eligible'() {
        given:
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        materializer.materialize(TXN_ID) >> Mono.error(new RuntimeException('projection boom'))

        when:
        worker.materializeAndStamp(TXN_ID).block()

        then:
        thrown(RuntimeException)
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'a transaction not visible as committed materializes nothing and stamps nothing'() {
        given: 'the committed-snapshot gate resolves empty'
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader(state: 'open', commit_id: null))
        materializer.materialize(TXN_ID) >> Mono.empty()

        when:
        MaterializeResult result = worker.materializeAndStamp(TXN_ID).block()

        then:
        result == null
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'sweepOnce selects committed, commit_id-bearing, unwatermarked headers and processes each'() {
        given:
        List<Query> committedQueries = []
        stubSweepFinds([], [], [committedHeader()], committedQueries)
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 1L

        and: 'the sweep query mirrors the committed-visibility gate plus the missing watermark'
        Map queryObject = committedQueries.first().getQueryObject()
        queryObject.get('record_type') == 'transaction'
        queryObject.get('state') == 'committed'
        (queryObject.get('commit_id') as Map).get('$exists') == true
        (queryObject.get('materialized_at') as Map).get('$exists') == false
    }

    def 'sweepOnce isolates per-transaction failures so one broken transaction cannot stall the rest'() {
        given: 'two committed headers; the first projection fails'
        String otherTxnId = 'bbbbbbbb-cccc-7ddd-8eee-ffffffffffff~test-org~test-grp~jade-cli'
        stubSweepFinds([], [], [committedHeader(), committedHeader(_id: otherTxnId, txn_id: otherTxnId)])
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        mongoTemplate.findById(otherTxnId, Map.class, COLLECTION) >> Mono.just(
                committedHeader(_id: otherTxnId, txn_id: otherTxnId))
        materializer.materialize(TXN_ID) >> Mono.error(new RuntimeException('projection boom'))
        materializer.materialize(otherTxnId) >> Mono.just(new MaterializeResult())
        Update capturedUpdate = null
        Query capturedQuery = null
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> { Query q, Update u, String _c ->
            capturedQuery = q
            capturedUpdate = u
            return Mono.empty()
        }

        when:
        Long processed = worker.sweepOnce().block()

        then: 'the healthy transaction is still processed and stamped'
        processed == 1L
        capturedQuery.getQueryObject().get('_id') == otherTxnId
        (capturedUpdate.getUpdateObject().get('$set') as Map).containsKey('materialized_at')
    }

    def 'the snapshot watermark gates the sweep: an older open snapshot blocks, otherwise the commit runs'() {
        given: 'a committed UUIDv7 commit and one open transaction'
        stubSweepFinds([], [openHeader(snapshot_id: openSnapshot)],
                [committedHeader(commit_id: COMMIT_MID)])
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(
                committedHeader(commit_id: COMMIT_MID))
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == expectedProcessed
        calls * materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())

        where:
        case_                                  | openSnapshot || expectedProcessed | calls
        'open older than the commit blocks'    | SNAPSHOT_OLD || 0L                | 0
        'open newer than the commit is no bar' | SNAPSHOT_NEW || 1L                | 1
    }

    def 'the oldest open snapshot is the watermark when several transactions are open'() {
        given: 'two opens straddle the commit — the older one wins'
        stubSweepFinds([], [openHeader(snapshot_id: SNAPSHOT_NEW), openHeader(snapshot_id: SNAPSHOT_OLD)],
                [committedHeader(commit_id: COMMIT_MID)])

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 0L
        0 * materializer.materialize(_)
    }

    def 'a legacy open header without snapshot_id participates via its transaction uuid segment'() {
        given: 'the legacy open txn uuid predates the commit id'
        Map legacyOpen = openHeader(_id: "${SNAPSHOT_OLD}~test-org~test-grp~jade-cli" as String)
        legacyOpen.remove('snapshot_id')
        stubSweepFinds([], [legacyOpen], [committedHeader(commit_id: COMMIT_MID)])

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 0L
        0 * materializer.materialize(_)
    }

    def 'a legacy non-uuid commit_id predates every live open and is always eligible'() {
        given:
        stubSweepFinds([], [openHeader(snapshot_id: SNAPSHOT_OLD)], [committedHeader()])
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 1L
    }

    def 'the lease pass durably rolls back an expired open with a guarded update and audit data'() {
        given: 'one open transaction past its lease; nothing committed to project'
        String expiredTxnId = openHeader().get('_id') as String
        stubSweepFinds([openHeader()], [], [])
        Query capturedGuard = null
        Update capturedUpdate = null
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> { Query q, Update u, String _c ->
            capturedGuard = q
            capturedUpdate = u
            return Mono.empty()
        }

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 0L

        and: 'the rollback is guarded on the header still being open'
        capturedGuard.getQueryObject().get('_id') == expiredTxnId
        capturedGuard.getQueryObject().get('state') == 'open'

        and: 'the terminal state carries the audit fact'
        Map setOps = capturedUpdate.getUpdateObject().get('$set') as Map
        setOps.get('state') == 'rolled_back'
        setOps.get('rolled_back_at') instanceof Instant
        (setOps.get('rollback_data') as Map).get('reason') == 'lease_expired'
        (setOps.get('rollback_data') as Map).get('lease') == 'PT1H'
    }

    def 'nudge is a no-op when the worker is disabled'() {
        given:
        CommittedTransactionMaterializationWorker disabled =
                new CommittedTransactionMaterializationWorker(
                        mongoTemplate, materializer, false, Duration.ofSeconds(30), Duration.ofHours(1))
        disabled.start()

        when:
        disabled.nudge(TXN_ID)

        then:
        0 * materializer.materialize(_)
        0 * mongoTemplate._
    }
}
