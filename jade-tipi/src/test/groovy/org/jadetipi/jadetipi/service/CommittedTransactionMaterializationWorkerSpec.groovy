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
 * sweep's committed-but-unwatermarked selection. The nudge/sweep pipelines
 * themselves are proven by the integration specs; these features pin the
 * per-transaction unit of work.
 */
class CommittedTransactionMaterializationWorkerSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee~test-org~test-grp~jade-cli'
    static final String COLLECTION = 'txn'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionMaterializer materializer
    CommittedTransactionMaterializationWorker worker

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        materializer = Mock(CommittedTransactionMaterializer)
        worker = new CommittedTransactionMaterializationWorker(
                mongoTemplate, materializer, true, Duration.ofSeconds(30))
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
        Query capturedQuery = null
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> { Query q, Class _t, String _c ->
            capturedQuery = q
            return Flux.just(committedHeader())
        }
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(committedHeader())
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()

        when:
        Long processed = worker.sweepOnce().block()

        then:
        processed == 1L

        and: 'the sweep query mirrors the committed-visibility gate plus the missing watermark'
        Map queryObject = capturedQuery.getQueryObject()
        queryObject.get('record_type') == 'transaction'
        queryObject.get('state') == 'committed'
        (queryObject.get('commit_id') as Map).get('$exists') == true
        (queryObject.get('materialized_at') as Map).get('$exists') == false
    }

    def 'sweepOnce isolates per-transaction failures so one broken transaction cannot stall the rest'() {
        given: 'two committed headers; the first projection fails'
        String otherTxnId = 'bbbbbbbb-cccc-7ddd-8eee-ffffffffffff~test-org~test-grp~jade-cli'
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> Flux.just(
                committedHeader(), committedHeader(_id: otherTxnId, txn_id: otherTxnId))
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

    def 'nudge is a no-op when the worker is disabled'() {
        given:
        CommittedTransactionMaterializationWorker disabled =
                new CommittedTransactionMaterializationWorker(
                        mongoTemplate, materializer, false, Duration.ofSeconds(30))
        disabled.start()

        when:
        disabled.nudge(TXN_ID)

        then:
        0 * materializer.materialize(_)
        0 * mongoTemplate._
    }
}
