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
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

/**
 * TASK-056 coverage for per-message {@code apply_state} stamping: every
 * processed message gets exactly one terminal-outcome stamp on its WAL row,
 * named by the counter that moved, guarded on the field being absent
 * (first outcome wins), with an {@code apply_state_at} timestamp. Also pins
 * the message_count/snapshot-size mismatch tolerance (warn, never block).
 */
class CommittedTransactionMaterializerApplyStateSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'

    static final String LOC_ID = 'jade-tipi-org~dev~018fd849-7a01-7111-8a01-717171717171~loc~freezer_a'
    static final String TYP_ID = 'jade-tipi-org~dev~018fd849-7a02-7222-8a02-727272727272~typ~container'
    static final String PPY_ID = 'jade-tipi-org~dev~018fd849-7a03-7333-8a03-737373737373~ppy~barcode'
    static final String MSG_LOC = '018fd849-7a04-7444-8a04-747474747474'
    static final String MSG_TYP_UPDATE = '018fd849-7a05-7555-8a05-757575757575'
    static final String MSG_UNSUPPORTED = '018fd849-7a06-7666-8a06-767676767676'
    static final String MSG_BLANK_ID = '018fd849-7a07-7777-8a07-777777777777'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer
    List<Query> stampQueries
    List<Update> stampUpdates

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        stampQueries = []
        stampUpdates = []
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'txn') >> { Query q, Update u, String _c ->
            stampQueries << q
            stampUpdates << u
            return Mono.empty()
        }
    }

    private static CommittedTransactionSnapshot snapshot(List<CommittedTransactionMessage> messages,
                                                         Integer messageCount = null) {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: Instant.parse('2026-01-01T00:00:00Z'),
                committedAt: Instant.parse('2026-01-01T00:00:05Z'),
                openData: [:],
                commitData: [:],
                messageCount: messageCount,
                messages: messages
        )
    }

    private static CommittedTransactionMessage message(String msgUuid, String collection,
                                                       String action, Map data) {
        return new CommittedTransactionMessage(
                msgUuid: msgUuid,
                collection: collection,
                action: action,
                data: data as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    def 'every processed message is stamped with its terminal outcome, guarded and timestamped'() {
        given: 'four messages resolving four different terminal outcomes'
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _c -> Mono.just(doc) }
        // add_property whose target already carries a matching (empty) reference -> duplicate
        mongoTemplate.findById(TYP_ID, Map.class, 'typ') >> Mono.just([
                _id: TYP_ID, id: TYP_ID, collection: 'typ', type_id: null,
                properties: [name: 'container', property_refs: [(PPY_ID): [:]]],
                links: [:]
        ] as Map)
        List<CommittedTransactionMessage> messages = [
                message(MSG_LOC, 'loc', 'create', [id: LOC_ID, name: 'freezer_a']),
                message(MSG_TYP_UPDATE, 'typ', 'update',
                        [id: TYP_ID, operation: 'add_property', property_id: PPY_ID]),
                message(MSG_UNSUPPORTED, 'ent', 'delete', [id: LOC_ID]),
                message(MSG_BLANK_ID, 'loc', 'create', [name: 'no id']),
        ]

        when:
        MaterializeResult result = materializer.materialize(snapshot(messages)).block()

        then: 'the counters resolve one outcome per message'
        result.materialized == 1
        result.duplicateMatching == 1
        result.skippedUnsupported == 1
        result.skippedInvalid == 1

        and: 'each row is stamped once, in processing order, with the matching state'
        stampUpdates.size() == 4
        List<String> states = stampUpdates.collect {
            (it.getUpdateObject().get('$set') as Map).get('apply_state') as String
        }
        states == ['applied', 'duplicate', 'skipped_unsupported', 'skipped_invalid']

        and: 'stamps target the WAL row id and are guarded on apply_state being absent'
        stampQueries.eachWithIndex { Query q, int i ->
            String expectedRowId = "${TXN_ID}~${messages[i].msgUuid}"
            assert q.getQueryObject().get('_id') == expectedRowId
            assert (q.getQueryObject().get('apply_state') as Map).get('$exists') == false
        }

        and: 'every stamp carries an apply_state_at timestamp'
        stampUpdates.every {
            (it.getUpdateObject().get('$set') as Map).get('apply_state_at') instanceof Instant
        }
    }

    def 'a message_count mismatch warns without blocking the pass'() {
        given:
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _c -> Mono.just(doc) }
        List<CommittedTransactionMessage> messages = [
                message(MSG_LOC, 'loc', 'create', [id: LOC_ID, name: 'freezer_a']),
        ]

        when: 'the header claims five messages but the snapshot holds one'
        MaterializeResult result = materializer.materialize(snapshot(messages, 5)).block()

        then: 'the pass still completes and stamps normally'
        result.materialized == 1
        stampUpdates.size() == 1
    }
}
