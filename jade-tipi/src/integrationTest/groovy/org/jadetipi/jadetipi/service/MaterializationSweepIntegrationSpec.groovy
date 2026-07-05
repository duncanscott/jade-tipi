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

import org.jadetipi.dto.collections.Transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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
 * End-to-end TASK-052 (UT-7) coverage for the materialization sweep. The
 * spec plants a committed-but-unmaterialized transaction directly in
 * MongoDB — a header with {@code state: "committed"} and a
 * {@code commit_id} but no {@code materialized_at}, plus message rows —
 * which is exactly the state left behind by a crash between commit and
 * projection (or, before TASK-052, by any swallowed projection failure
 * with no commit redelivery). No Kafka is involved: the periodic sweep
 * alone must find the transaction, materialize its messages, and stamp
 * the watermark. A {@code late_append}-flagged row is planted alongside
 * and must never materialize (the TASK-051 snapshot exclusion holds on
 * the sweep path too).
 *
 * <p>Mongo-only: requires the docker-compose MongoDB, like the other
 * ungated {@code @SpringBootTest} integration specs.
 */
@SpringBootTest
@ActiveProfiles('test')
class MaterializationSweepIntegrationSpec extends Specification {

    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add('jadetipi.materialization.sweep-interval', { 'PT2S' })
    }

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    Transaction txn
    String txnId
    String locId
    String lateLocId

    def setup() {
        txn = Transaction.newInstance('jade-itest-org', 'sweep', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
        // Object identifier convention (TASK-044): transaction-UUID form.
        String idPrefix = "jade-itest-org~sweep~${txn.uuid()}"
        locId = "${idPrefix}~loc~sweep_probe"
        lateLocId = "${idPrefix}~loc~late_probe"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [locId, lateLocId].findAll { it != null }.each { String id ->
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                    LOC_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'the sweep materializes a committed-but-unmaterialized transaction and stamps the watermark'() {
        given: 'the crash-between-commit-and-projection state, planted directly in Mongo'
        Instant now = Instant.now()
        Map header = [
                _id         : txnId,
                txn_id      : txnId,
                record_type : 'transaction',
                state       : 'committed',
                commit_id   : "SWEEP-ITEST-${txn.uuid()}".toString(),
                opened_at   : now,
                committed_at: now,
                open_data   : [description: 'planted committed transaction, never projected'],
                commit_data : [summary: 'sweep must pick this up']
        ] as Map<String, Object>
        String msgUuid = '018fd849-5a01-7111-8a01-515151515151'
        Map locRow = [
                _id        : "${txnId}~${msgUuid}".toString(),
                record_type: 'message',
                txn_id     : txnId,
                msg_uuid   : msgUuid,
                collection : 'loc',
                action     : 'create',
                data       : [id: locId, name: 'sweep probe location'],
                received_at: now
        ] as Map<String, Object>
        String lateMsgUuid = '018fd849-5a02-7222-8a02-525252525252'
        Map lateRow = [
                _id        : "${txnId}~${lateMsgUuid}".toString(),
                record_type: 'message',
                txn_id     : txnId,
                msg_uuid   : lateMsgUuid,
                collection : 'loc',
                action     : 'create',
                data       : [id: lateLocId, name: 'late probe location'],
                received_at: now,
                late_append: true
        ] as Map<String, Object>

        when: 'the rows are planted and the periodic sweep is left to find them'
        mongoTemplate.insert(header, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        mongoTemplate.insert(locRow, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        mongoTemplate.insert(lateRow, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)

        then: 'the loc root materializes with full provenance, with no Kafka involved'
        Map locDoc = awaitMongo(
                { mongoTemplate.findById(locId, Map, LOC_COLLECTION) },
                { Map d -> d != null },
                'swept loc root'
        )
        locDoc.collection == 'loc'
        (locDoc.properties as Map).name == 'sweep probe location'
        ((locDoc._head as Map).provenance as Map).txn_id == txnId
        ((locDoc._head as Map).provenance as Map).commit_id == header.commit_id

        and: 'the header is stamped with the materialized_at watermark'
        Map stamped = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.materialized_at != null },
                'watermarked transaction header'
        )
        stamped.state == 'committed'

        and: 'the late_append row never materializes on the sweep path either'
        mongoTemplate.findById(lateLocId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == null
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
        if (lastError != null) {
            throw new AssertionError(
                    "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                            "last error: ${lastError.message}", lastError)
        }
        throw new AssertionError(
                "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                        "last value: ${last}")
    }
}
