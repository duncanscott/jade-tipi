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
 * TASK-061 coverage for the value-update model: newest-message-wins current
 * values, every applied assignment preserved in {@code hst} (idempotent by
 * {@code _id = msg_uuid}), older arrivals landing in history only
 * ({@code applied_historical}), and the {@code history: false} opt-out
 * convention resolved through the type chain (property-level most specific,
 * then the nearest object-level declaration, then enabled).
 */
class CommittedTransactionMaterializerValueUpdateSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-002'

    static final String TYP_SENSOR = 'jade-tipi-org~dev~018fd849-9b01-7111-8a01-a1a1a1a1a1a1~typ~sensor'
    static final String TYP_BASE = 'jade-tipi-org~dev~018fd849-9b02-7222-8a02-a2a2a2a2a2a2~typ~device'
    static final String ENT_PROBE = 'jade-tipi-org~dev~018fd849-9b03-7333-8a03-a3a3a3a3a3a3~ent~probe_1'
    static final String PPY_READING = 'jade-tipi-org~dev~018fd849-9b04-7444-8a04-a4a4a4a4a4a4~ppy~reading'
    // v7 uuids: OLD < CURRENT < NEW lexicographically and temporally
    static final String MSG_OLD = '018fd849-9b05-7555-8a05-a5a5a5a5a5a5'
    static final String MSG_CURRENT = '018fd849-9b06-7666-8a06-a6a6a6a6a6a6'
    static final String MSG_NEW = '018fd849-9b07-7777-8a07-a7a7a7a7a7a7'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer
    List<Map> hstInserts
    List<Update> rootUpdates

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'txn') >> Mono.empty()
        hstInserts = []
        mongoTemplate.insert(_ as Map, 'hst') >> { Map doc, String _c ->
            hstInserts << doc
            return Mono.just(doc)
        }
        rootUpdates = []
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'ent') >> { Query q, Update u, String _c ->
            rootUpdates << u
            return Mono.empty()
        }
    }

    private static CommittedTransactionSnapshot snapshot(List<CommittedTransactionMessage> messages) {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: Instant.parse('2026-01-01T00:00:00Z'),
                committedAt: Instant.parse('2026-01-01T00:00:05Z'),
                openData: [:],
                commitData: [:],
                messages: messages
        )
    }

    private static CommittedTransactionMessage assignment(String msgUuid, Map value) {
        return new CommittedTransactionMessage(
                msgUuid: msgUuid,
                collection: 'ppy',
                action: 'create',
                data: [
                        kind             : 'assignment',
                        object_collection: 'ent',
                        object_id        : ENT_PROBE,
                        property_id      : PPY_READING,
                        value            : value
                ] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    private static Map currentEntry(String msgUuid, Map value) {
        return [
                value     : value,
                txn_id    : 'earlier-txn',
                commit_id : 'COMMIT-001',
                msg_uuid  : msgUuid,
                applied_at: Instant.parse('2025-12-31T00:00:01Z')
        ]
    }

    private void stubRoot(Map propertyValues, String typeId = TYP_SENSOR) {
        mongoTemplate.findById(ENT_PROBE, Map.class, 'ent') >> Mono.just([
                _id: ENT_PROBE, id: ENT_PROBE, collection: 'ent', type_id: typeId,
                properties: [:], links: [:],
                property_values: propertyValues
        ] as Map)
    }

    private void stubType(Map typeProperties) {
        mongoTemplate.findById(TYP_SENSOR, Map.class, 'typ') >> Mono.just([
                _id: TYP_SENSOR, id: TYP_SENSOR, collection: 'typ', type_id: null,
                properties: typeProperties, links: [:]
        ] as Map)
    }

    def 'a newer assignment replaces the current value and lands in history'() {
        given:
        stubRoot([(PPY_READING): currentEntry(MSG_CURRENT, [number: 1])])
        stubType([name: 'sensor', property_refs: [(PPY_READING): [:]]])

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_NEW, [number: 2])])).block()

        then:
        result.materialized == 1
        result.appliedHistorical == 0
        result.conflictingDuplicate == 0

        and: 'the root current entry is replaced'
        rootUpdates.size() == 1
        Map set = rootUpdates[0].getUpdateObject().get('$set') as Map
        Map entry = set.get("property_values.${PPY_READING}" as String) as Map
        entry.value == [number: 2]
        entry.msg_uuid == MSG_NEW

        and: 'the assignment is preserved in hst keyed by its message uuid'
        hstInserts.size() == 1
        hstInserts[0]._id == MSG_NEW
        hstInserts[0].object_id == ENT_PROBE
        hstInserts[0].property_id == PPY_READING
        hstInserts[0].value == [number: 2]
    }

    def 'an assignment older than current lands in history only'() {
        given:
        stubRoot([(PPY_READING): currentEntry(MSG_CURRENT, [number: 1])])
        stubType([name: 'sensor', property_refs: [(PPY_READING): [:]]])

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_OLD, [number: 0])])).block()

        then:
        result.appliedHistorical == 1
        result.materialized == 0
        rootUpdates.isEmpty()
        hstInserts.size() == 1
        hstInserts[0]._id == MSG_OLD
    }

    def 'a same-message redelivery is a duplicate whose history re-insert self-heals'() {
        given: 'the identical entry from the SAME transaction (a true redelivery)'
        stubRoot([(PPY_READING): [
                value     : [number: 1],
                txn_id    : TXN_ID,
                commit_id : COMMIT_ID,
                msg_uuid  : MSG_CURRENT,
                applied_at: Instant.parse('2025-12-31T00:00:01Z')
        ]])
        stubType([name: 'sensor', property_refs: [(PPY_READING): [:]]])

        when: 'the identical assignment message is applied again'
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_CURRENT, [number: 1])])).block()

        then:
        result.duplicateMatching == 1
        rootUpdates.isEmpty()

        and: 'the tolerant hst insert re-runs (a duplicate key would be a no-op)'
        hstInserts.size() == 1
        hstInserts[0]._id == MSG_CURRENT
    }

    def 'the first assignment for a property applies and is preserved in history'() {
        given:
        stubRoot([:])
        stubType([name: 'sensor', property_refs: [(PPY_READING): [:]]])

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_CURRENT, [number: 1])])).block()

        then:
        result.materialized == 1
        rootUpdates.size() == 1
        hstInserts.size() == 1
    }

    def 'history opt-outs suppress the hst write but never the current update'() {
        given:
        stubRoot([:])
        stubType(typeProperties)

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_CURRENT, [number: 1])])).block()

        then:
        result.materialized == 1
        rootUpdates.size() == 1
        hstInserts.size() == expectedHstWrites

        where:
        case_                              | typeProperties                                                                || expectedHstWrites
        'property-level opt-out'           | [name: 's', property_refs: [(PPY_READING): [history: false]]]                || 0
        'object-level opt-out'             | [name: 's', history: false, property_refs: [(PPY_READING): [:]]]             || 0
        'property true overrides object'   | [name: 's', history: false, property_refs: [(PPY_READING): [history: true]]] || 1
        'enabled by default'               | [name: 's', property_refs: [(PPY_READING): [:]]]                             || 1
    }

    def 'the nearest object-level declaration in the chain wins'() {
        given: 'the subtype declares history: false; the registering ancestor declares true'
        stubRoot([:])
        mongoTemplate.findById(TYP_SENSOR, Map.class, 'typ') >> Mono.just([
                _id: TYP_SENSOR, id: TYP_SENSOR, collection: 'typ', type_id: null,
                properties: [name: 'sensor', history: false, parent_type_id: TYP_BASE], links: [:]
        ] as Map)
        mongoTemplate.findById(TYP_BASE, Map.class, 'typ') >> Mono.just([
                _id: TYP_BASE, id: TYP_BASE, collection: 'typ', type_id: null,
                properties: [name: 'device', history: true, property_refs: [(PPY_READING): [:]]], links: [:]
        ] as Map)

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([assignment(MSG_CURRENT, [number: 1])])).block()

        then: 'the nearer false wins: current updates, no history row'
        result.materialized == 1
        rootUpdates.size() == 1
        hstInserts.isEmpty()
    }
}
