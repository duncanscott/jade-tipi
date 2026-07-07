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

import com.github.f4b6a3.uuid.UuidCreator
import org.jadetipi.dto.collections.Transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * TASK-063 coverage for the snapshot watermark and the transaction lease,
 * Mongo-only (MaterializationSweepIntegrationSpec pattern): state is planted
 * directly and the periodic sweep does the rest.
 *
 * <p>Watermark: an open transaction with a {@code snapshot_id} older than a
 * committed transaction's {@code commit_id} keeps that commit unmaterialized
 * — old values stay readable for the open snapshot — until the open reaches
 * a terminal state, after which the sweep projects it.
 *
 * <p>Lease: an open transaction older than {@code jadetipi.transaction.lease}
 * is durably rolled back by the sweep with the expiry recorded as audit
 * {@code rollback_data}, so an abandoned open cannot hold the watermark back
 * forever.
 */
@SpringBootTest
@ActiveProfiles('test')
class SnapshotWatermarkIntegrationSpec extends Specification {

    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)
    /** Long enough for at least two PT2S sweep passes to have run. */
    private static final long BLOCKED_OBSERVATION_MILLIS = 5000

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add('jadetipi.materialization.sweep-interval', { 'PT2S' })
    }

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    Transaction openTxn
    Transaction committedTxn
    String locId

    def setup() {
        openTxn = Transaction.newInstance('jade-itest-org', 'watermark', 'jade-itest-cli', 'itest-user')
        committedTxn = Transaction.newInstance('jade-itest-org', 'watermark', 'jade-itest-cli', 'itest-user')
        locId = "jade-itest-org~watermark~${committedTxn.uuid()}~loc~watermark_probe"
    }

    def cleanup() {
        [openTxn.id, committedTxn.id].each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        mongoTemplate.remove(Query.query(Criteria.where('_id').is(locId)),
                LOC_COLLECTION).block(Duration.ofSeconds(10))
    }

    def 'an older open snapshot blocks materialization until the open reaches a terminal state'() {
        given: 'an open transaction whose snapshot predates a committed transaction'
        Instant now = Instant.now()
        String olderSnapshotId = UuidCreator.timeOrderedEpoch.toString()
        String newerCommitId = UuidCreator.timeOrderedEpoch.toString()
        Map openHeader = [
                _id        : openTxn.id,
                txn_id     : openTxn.id,
                record_type: 'transaction',
                state      : 'open',
                snapshot_id: olderSnapshotId,
                opened_at  : now,
                open_data  : [description: 'holds the watermark back']
        ] as Map<String, Object>
        Map committedHeader = [
                _id          : committedTxn.id,
                txn_id       : committedTxn.id,
                record_type  : 'transaction',
                state        : 'committed',
                commit_id    : newerCommitId,
                snapshot_id  : olderSnapshotId,
                opened_at    : now,
                committed_at : now,
                open_data    : [:],
                commit_data  : [summary: 'blocked until the open closes'],
                message_count: 1
        ] as Map<String, Object>
        String msgUuid = UuidCreator.timeOrderedEpoch.toString()
        Map locRow = [
                _id        : "${committedTxn.id}~${msgUuid}".toString(),
                record_type: 'message',
                txn_id     : committedTxn.id,
                msg_uuid   : msgUuid,
                collection : 'loc',
                action     : 'create',
                data       : [id: locId, name: 'watermark probe location'],
                received_at: now
        ] as Map<String, Object>

        when: 'the state is planted and the sweep runs repeatedly'
        mongoTemplate.insert(openHeader, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        mongoTemplate.insert(committedHeader, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        mongoTemplate.insert(locRow, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        Thread.sleep(BLOCKED_OBSERVATION_MILLIS)

        then: 'the committed transaction stays unmaterialized while the older open lives'
        mongoTemplate.findById(locId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == null
        Map blockedHeader = mongoTemplate.findById(committedTxn.id, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        blockedHeader.materialized_at == null

        when: 'the open transaction reaches a terminal state'
        mongoTemplate.updateFirst(
                Query.query(Criteria.where('_id').is(openTxn.id).and('state').is('open')),
                new Update().set('state', 'rolled_back')
                        .set('rolled_back_at', Instant.now())
                        .set('rollback_data', [reason: 'itest release']),
                TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)

        then: 'the sweep materializes the released transaction and stamps the watermark'
        Map locDoc = awaitMongo(
                { mongoTemplate.findById(locId, Map, LOC_COLLECTION) },
                { Map d -> d != null },
                'released loc root')
        ((locDoc._head as Map).provenance as Map).commit_id == newerCommitId
        awaitMongo(
                { mongoTemplate.findById(committedTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.materialized_at != null },
                'watermarked released header')
    }

    def 'the sweep durably rolls back an open transaction past its lease with audit data'() {
        given: 'an open transaction opened well past the configured lease (default PT1H)'
        Map staleOpen = [
                _id        : openTxn.id,
                txn_id     : openTxn.id,
                record_type: 'transaction',
                state      : 'open',
                snapshot_id: UuidCreator.timeOrderedEpoch.toString(),
                opened_at  : Instant.now() - Duration.ofHours(2),
                open_data  : [description: 'abandoned open transaction']
        ] as Map<String, Object>

        when:
        mongoTemplate.insert(staleOpen, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)

        then: 'the sweep expires it through the normal durable rollback path'
        Map expired = awaitMongo(
                { mongoTemplate.findById(openTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'rolled_back' },
                'lease-expired header rolled back')
        expired.rolled_back_at != null
        (expired.rollback_data as Map).reason == 'lease_expired'
        (expired.rollback_data as Map).lease == 'PT1H'
    }

    private static <T> T awaitMongo(Supplier<Mono<T>> source,
                                    Predicate<T> condition,
                                    String description) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT.toMillis()
        T last = null
        Throwable lastError = null
        while (System.currentTimeMillis() < deadline) {
            try {
                last = source.get().block(MONGO_BLOCK_TIMEOUT)
            } catch (Throwable t) {
                lastError = t
            }
            if (last != null && condition.test(last)) {
                return last
            }
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
        throw new AssertionError(
                "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                        "last value: ${last}", lastError)
    }
}
