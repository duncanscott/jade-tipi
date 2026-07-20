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
 * TASK-048 coverage for procedure ({@code prc}) and task ({@code tsk}) roots:
 * {@code prc + create} hoists a top-level {@code data.output_input} map onto
 * the root — parallel to {@code lnk}'s {@code left}/{@code right} — and keeps
 * it out of the inline {@code properties} bag; {@code tsk + create}
 * materializes a standard typed root; and object-targeted {@code ppy}
 * assignments accept {@code prc} and {@code tsk} targets.
 */
class CommittedTransactionMaterializerProcedureTaskSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'
    static final Instant OPENED_AT = Instant.parse('2026-01-01T00:00:00Z')
    static final Instant COMMITTED_AT = Instant.parse('2026-01-01T00:00:05Z')

    static final String TYP_PROCEDURE = '018fd849-3b01-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~typ~dna_pooling'
    static final String TYP_TASK = '018fd849-3b02-7222-8a02-bbbbbbbbbbbb~jade-tipi-org~dev~typ~dna_pooling_task'
    static final String PRC_ID = '018fd849-3b03-7333-8a03-cccccccccccc~jade-tipi-org~dev~prc~pool_run_1'
    static final String TSK_ID = '018fd849-3b04-7444-8a04-dddddddddddd~jade-tipi-org~dev~tsk~pool_batch_7'
    static final String ENT_IN_1 = '018fd849-3b05-7555-8a05-eeeeeeeeeeee~jade-tipi-org~dev~ent~sample_a'
    static final String ENT_IN_2 = '018fd849-3b06-7666-8a06-ffffffffffff~jade-tipi-org~dev~ent~sample_b'
    static final String ENT_OUT = '018fd849-3b07-7777-8a07-aaaaaaaaaaab~jade-tipi-org~dev~ent~pool_1'
    static final String PPY_NAME = '018fd849-3b08-7888-8a08-bbbbbbbbbbbc~jade-tipi-org~dev~ppy~name'
    static final String PRC_MSG_UUID = '018fd849-3b03-7333-8a03-cccccccccccc'
    static final String TSK_MSG_UUID = '018fd849-3b04-7444-8a04-dddddddddddd'
    static final String ASSIGN_MSG_UUID = '018fd849-3b09-7999-8f09-cccccccccccd'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        // apply_state stamps (TASK-056) write to the txn WAL rows
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'txn') >> Mono.empty()
    }

    private static CommittedTransactionSnapshot snapshot(List<CommittedTransactionMessage> messages) {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: OPENED_AT,
                committedAt: COMMITTED_AT,
                openData: [hint: 'open'],
                commitData: [reason: 'done'],
                messages: messages
        )
    }

    private static Map<String, Object> outputInput() {
        return [
                (ENT_OUT): [
                        (ENT_IN_1): [volume: 5.0],
                        (ENT_IN_2): [volume: 7.5]
                ]
        ] as Map<String, Object>
    }

    private static CommittedTransactionMessage procedureCreateMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id          : PRC_ID,
                type_id     : TYP_PROCEDURE,
                name        : 'pool_run_1',
                output_input: outputInput()
        ] as Map<String, Object>
        data.putAll(dataOverrides)
        dataOverrides.each { k, v -> if (v == null) data.remove(k) }
        return new CommittedTransactionMessage(
                msgUuid: PRC_MSG_UUID,
                collection: 'prc',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:03Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage taskCreateMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id     : TSK_ID,
                type_id: TYP_TASK,
                name   : 'pool_batch_7'
        ] as Map<String, Object>
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: TSK_MSG_UUID,
                collection: 'tsk',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage objectAssignment(String objectCollection, String objectId) {
        return new CommittedTransactionMessage(
                msgUuid: ASSIGN_MSG_UUID,
                collection: 'ppy',
                action: 'create',
                data: [
                        kind             : 'assignment',
                        object_collection: objectCollection,
                        object_id        : objectId,
                        property_id      : PPY_NAME,
                        value            : [text: 'renamed']
                ] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:04Z'),
                kafka: null
        )
    }

    private static Map typRoot(String id, Map properties) {
        return [
                _id       : id,
                id        : id,
                collection: 'typ',
                type_id   : null,
                properties: properties,
                links     : [:]
        ]
    }

    private static Map objectRoot(String id, String collection, String typeId) {
        return [
                _id       : id,
                id        : id,
                collection: collection,
                type_id   : typeId,
                properties: [:],
                links     : [:]
        ]
    }

    def 'materializes a prc create with output_input hoisted top-level and excluded from inline properties'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'prc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([procedureCreateMessage()])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id, source collection, and top-level type_id'
        captured._id == PRC_ID
        captured.id == PRC_ID
        captured.collection == 'prc'
        captured.type_id == TYP_PROCEDURE

        and: 'output_input is hoisted top-level like lnk left/right, verbatim'
        captured.output_input == outputInput()

        and: 'the inline properties bag excludes output_input but keeps other payload facts'
        Map properties = captured.properties as Map
        properties.name == 'pool_run_1'
        !properties.containsKey('output_input')
        !properties.containsKey('id')
        !properties.containsKey('type_id')

        and: 'links is initialized to an empty map'
        captured.links == [:]

        and: '_head carries the standard schema metadata and provenance'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == PRC_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == PRC_MSG_UUID
        provenance.collection == 'prc'
        provenance.action == 'create'
    }

    def 'prc create with explicit data.properties hoists output_input and copies properties verbatim'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'prc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        CommittedTransactionMessage message = procedureCreateMessage(
                name: null,
                properties: [name: 'pool_run_1', operator: 'robot_7'],
                links: [:]
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1

        and: 'output_input stays top-level and does not leak into the verbatim properties bag'
        captured.output_input == outputInput()
        captured.properties == [name: 'pool_run_1', operator: 'robot_7']
        captured.links == [:]
    }

    def 'prc create without output_input materializes with no top-level output_input key'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'prc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([procedureCreateMessage(output_input: null)])).block()

        then:
        result.materialized == 1
        !captured.containsKey('output_input')
        (captured.properties as Map).name == 'pool_run_1'
    }

    def 'materializes a tsk create as a standard typed root'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'tsk') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([taskCreateMessage()])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0

        and:
        captured._id == TSK_ID
        captured.id == TSK_ID
        captured.collection == 'tsk'
        captured.type_id == TYP_TASK
        (captured.properties as Map).name == 'pool_batch_7'
        captured.links == [:]
        (captured._head as Map).root_id == TSK_ID

        and: 'a tsk root never carries an output_input key'
        !captured.containsKey('output_input')
    }

    def 'projects a tsk-targeted assignment onto property_values against the tsk collection'() {
        given:
        Update capturedUpdate = null
        String capturedCollection = null
        mongoTemplate.findById(TSK_ID, Map.class, 'tsk') >>
                Mono.just(objectRoot(TSK_ID, 'tsk', TYP_TASK))
        mongoTemplate.findById(TYP_TASK, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_TASK, [name: 'dna_pooling_task', property_refs: [(PPY_NAME): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'tsk') >> {
            Query q, Update u, String coll ->
                capturedUpdate = u
                capturedCollection = coll
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([objectAssignment('tsk', TSK_ID)])).block()

        then:
        result.materialized == 1
        result.skippedInvalid == 0
        result.skippedUnregisteredProperty == 0
        capturedCollection == 'tsk'
        Map entry = (capturedUpdate.updateObject.get('$set') as Map)
                .get("property_values.${PPY_NAME}" as String) as Map
        entry.value == [text: 'renamed']
        entry.txn_id == TXN_ID
    }

    def 'projects a prc-targeted assignment onto property_values against the prc collection'() {
        given:
        String capturedCollection = null
        mongoTemplate.findById(PRC_ID, Map.class, 'prc') >>
                Mono.just(objectRoot(PRC_ID, 'prc', TYP_PROCEDURE))
        mongoTemplate.findById(TYP_PROCEDURE, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PROCEDURE, [name: 'dna_pooling', property_refs: [(PPY_NAME): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'prc') >> {
            Query q, Update u, String coll ->
                capturedCollection = coll
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([objectAssignment('prc', PRC_ID)])).block()

        then:
        result.materialized == 1
        result.skippedInvalid == 0
        capturedCollection == 'prc'
    }
}
