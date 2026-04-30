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

import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class CommittedTransactionReadServiceSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COLLECTION = 'txn'
    static final String COMMIT_ID = 'COMMIT-001'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new CommittedTransactionReadService(mongoTemplate)
    }

    private static Map committedHeader(Map overrides = [:]) {
        Map base = [
                _id        : TXN_ID,
                txn_id     : TXN_ID,
                record_type: 'transaction',
                state      : 'committed',
                commit_id  : COMMIT_ID,
                opened_at  : Instant.parse('2026-01-01T00:00:00Z'),
                committed_at: Instant.parse('2026-01-01T00:00:05Z'),
                open_data  : [hint: 'open'],
                commit_data: [reason: 'done']
        ]
        base.putAll(overrides)
        return base
    }

    private static Map messageRow(String msgUuid, Map overrides = [:]) {
        Map base = [
                _id        : TXN_ID + '~' + msgUuid,
                record_type: 'message',
                txn_id     : TXN_ID,
                msg_uuid   : msgUuid,
                collection : 'ppy',
                action     : 'create',
                data       : [name: msgUuid],
                received_at: Instant.parse('2026-01-01T00:00:01Z'),
                kafka      : [
                        topic       : 'jdtp-txn-prd',
                        partition   : 0,
                        offset      : 42L,
                        timestamp_ms: 1700000000000L
                ]
        ]
        base.putAll(overrides)
        return base
    }

    def 'committed transaction returns header plus messages snapshot'() {
        given:
        Map header = committedHeader()
        Map m1 = messageRow('11111111-1111-7111-8111-111111111111')
        Map m2 = messageRow('22222222-2222-7222-8222-222222222222')
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> Flux.just(m1, m2)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot != null
        snapshot.txnId == TXN_ID
        snapshot.state == 'committed'
        snapshot.commitId == COMMIT_ID
        snapshot.openedAt == Instant.parse('2026-01-01T00:00:00Z')
        snapshot.committedAt == Instant.parse('2026-01-01T00:00:05Z')
        snapshot.openData == [hint: 'open']
        snapshot.commitData == [reason: 'done']
        snapshot.messages.size() == 2

        and: 'first message round-trips fields and Kafka provenance'
        CommittedTransactionMessage first = snapshot.messages[0]
        first.msgUuid == '11111111-1111-7111-8111-111111111111'
        first.collection == 'ppy'
        first.action == 'create'
        first.data == [name: '11111111-1111-7111-8111-111111111111']
        first.receivedAt == Instant.parse('2026-01-01T00:00:01Z')
        first.kafka != null
        first.kafka.topic == 'jdtp-txn-prd'
        first.kafka.partition == 0
        first.kafka.offset == 42L
        first.kafka.timestampMs == 1700000000000L
    }

    def 'open (uncommitted) header is not exposed as committed and skips message lookup'() {
        given:
        Map header = committedHeader(state: 'open', commit_id: null, committed_at: null, commit_data: null)
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot == null
        0 * mongoTemplate.find(_, _, _)
    }

    def 'committed header without commit_id is not exposed (partial-write guard)'() {
        given:
        Map header = committedHeader(commit_id: null)
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot == null
        0 * mongoTemplate.find(_, _, _)
    }

    def 'committed header with blank commit_id is not exposed'() {
        given:
        Map header = committedHeader(commit_id: '   ')
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot == null
        0 * mongoTemplate.find(_, _, _)
    }

    def 'missing header returns Mono.empty()'() {
        given:
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.empty()

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot == null
        0 * mongoTemplate.find(_, _, _)
    }

    def 'older TransactionService-shape document (no record_type) is ignored'() {
        given: 'a document that looks like the older TransactionService shape'
        Map oldShape = [
                _id: TXN_ID,
                grp: [organization: 'test-org', group: 'test-grp'],
                txn: [
                        id       : TXN_ID,
                        secret   : 'shh',
                        commit   : 'old-commit',
                        committed: Instant.parse('2026-01-01T00:00:05Z')
                ]
        ]
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(oldShape)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot == null
        0 * mongoTemplate.find(_, _, _)
    }

    def 'message ordering relies on a Mongo sort by _id ASC, not on mocked Flux order'() {
        given: 'three message rows returned in non-_id order from the mock'
        Map header = committedHeader()
        Map mC = messageRow('cccccccc-cccc-7ccc-8ccc-cccccccccccc')
        Map mA = messageRow('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa')
        Map mB = messageRow('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb')
        Query capturedQuery = null
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> { Query q, Class _t, String _c ->
            capturedQuery = q
            return Flux.just(mC, mA, mB)
        }

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then: 'the issued query carries the deterministic _id ASC sort'
        capturedQuery != null
        capturedQuery.sortObject == new Document('_id', 1)

        and: 'the criteria filters to the message records for this txn'
        Map queryDoc = capturedQuery.queryObject
        queryDoc.get('record_type') == 'message'
        queryDoc.get('txn_id') == TXN_ID

        and: 'the snapshot preserves the Mongo-returned order'
        snapshot.messages*.msgUuid == [
                'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
                'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
                'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
        ]
    }

    def 'blank txnId is rejected with IllegalArgumentException'() {
        when:
        service.findCommitted(input)

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate.findById(_, _, _)
        0 * mongoTemplate.find(_, _, _)

        where:
        input << [null, '', '   ']
    }

    def 'null payload fields on header and messages do not NPE'() {
        given:
        Map header = committedHeader(opened_at: null, committed_at: null,
                open_data: null, commit_data: null)
        Map row = messageRow('11111111-1111-7111-8111-111111111111',
                [data: null, received_at: null, kafka: null])
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just(header)
        mongoTemplate.find(_ as Query, Map.class, COLLECTION) >> Flux.just(row)

        when:
        CommittedTransactionSnapshot snapshot = service.findCommitted(TXN_ID).block()

        then:
        snapshot != null
        snapshot.openedAt == null
        snapshot.committedAt == null
        snapshot.openData == null
        snapshot.commitData == null
        snapshot.messages.size() == 1
        snapshot.messages[0].data == null
        snapshot.messages[0].receivedAt == null
        snapshot.messages[0].kafka == null
    }
}
