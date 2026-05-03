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

import com.mongodb.MongoWriteException
import com.mongodb.ServerAddress
import com.mongodb.WriteError
import org.bson.BsonDocument
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class CommittedTransactionMaterializerSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'
    static final Instant OPENED_AT = Instant.parse('2026-01-01T00:00:00Z')
    static final Instant COMMITTED_AT = Instant.parse('2026-01-01T00:00:05Z')

    static final String LOC_ID = 'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_a'
    static final String TYP_ID = 'jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents'
    static final String LNK_ID = 'jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1'
    static final String GRP_ID = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics'
    static final String GRP_PEER_RW = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops'
    static final String GRP_PEER_R = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers'
    static final String ENT_ID = 'jade-tipi-org~dev~018fd849-2a42-7222-8a02-dddddddddddd~ent~plate_a'
    static final String ENT_TYPE_ID = 'jade-tipi-org~dev~018fd849-2a48-7888-8a08-eeeeeeeeeeee~typ~plate_96'
    static final String ENT_MSG_UUID = '018fd849-2a42-7222-8a02-dddddddddddd'
    static final String ENTITY_TYPE_MSG_UUID = '018fd849-2a48-7888-8a08-eeeeeeeeeeee'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
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

    private static CommittedTransactionMessage locMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id         : LOC_ID,
                name       : 'freezer_a',
                description: 'minus-80 freezer in room 110'
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                collection: 'loc',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage linkTypeMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                kind                      : 'link_type',
                id                        : TYP_ID,
                name                      : 'contents',
                description               : 'containment relationship between a container location and its contents',
                left_role                 : 'container',
                right_role                : 'content',
                left_to_right_label       : 'contains',
                right_to_left_label       : 'contained_by',
                allowed_left_collections  : ['loc'],
                allowed_right_collections : ['loc', 'ent']
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a49-7999-8a09-aaaaaaaaaaab',
                collection: 'typ',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage entityTypeMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id         : ENT_TYPE_ID,
                name       : 'plate_96',
                description: '96-well sample plate'
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: ENTITY_TYPE_MSG_UUID,
                collection: 'typ',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage updateEntityTypeMessage() {
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a44-7444-8d04-dddddddddddd',
                collection: 'typ',
                action: 'update',
                data: [
                        id         : ENT_TYPE_ID,
                        operation  : 'add_property',
                        property_id: 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~ppy~barcode',
                        required   : true
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage linkMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id     : LNK_ID,
                type_id: TYP_ID,
                left   : 'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1',
                right  : 'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1',
                properties: [
                        position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]
                ]
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb',
                collection: 'lnk',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:03Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage propertyCreateMessage() {
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a41-7111-8a01-cccccccccccc',
                collection: 'ppy',
                action: 'create',
                data: [
                        kind: 'definition',
                        id  : 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-cccccccccccc~ppy~barcode',
                        name: 'barcode'
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage entityCreateMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id        : ENT_ID,
                type_id   : ENT_TYPE_ID,
                properties: [:],
                links     : [:]
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: ENT_MSG_UUID,
                collection: 'ent',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage groupMessage(Map dataOverrides = [:],
                                                              String action = 'create') {
        Map<String, Object> data = [
                id         : GRP_ID,
                name       : 'analytics',
                description: 'analytics team',
                permissions: [
                        (GRP_PEER_RW): 'rw',
                        (GRP_PEER_R) : 'r'
                ]
        ]
        data.putAll(dataOverrides)
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a4d-7d0d-8d0d-cccccccccccc',
                collection: 'grp',
                action: action,
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:04Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage updateLocationMessage() {
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a4b-7bbb-8b0b-ffffffffffff',
                collection: 'loc',
                action: 'update',
                data: [id: LOC_ID, name: 'freezer_a_renamed'],
                receivedAt: Instant.parse('2026-01-01T00:00:04Z'),
                kafka: null
        )
    }

    private static DuplicateKeyException springDuplicate() {
        WriteError writeError = new WriteError(11000, 'duplicate key', new BsonDocument())
        MongoWriteException cause = new MongoWriteException(writeError, new ServerAddress('localhost'))
        return new DuplicateKeyException('duplicate key', cause)
    }

    def 'materializes a loc create as a root document with inline properties and _head.provenance'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([locMessage()])).block()

        then:
        result != null
        result.materialized == 1
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id and source collection'
        captured._id == LOC_ID
        captured.id == LOC_ID
        captured.collection == 'loc'
        captured.type_id == null

        and: 'explicit payload fields other than id and type_id move under properties'
        Map properties = captured.properties as Map
        properties.name == 'freezer_a'
        properties.description == 'minus-80 freezer in room 110'
        !properties.containsKey('id')
        !properties.containsKey('type_id')

        and: 'links is initialized to an empty map for new roots'
        captured.links == [:]

        and: '_head carries schema metadata and projection provenance'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == LOC_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == '018fd849-2a47-7777-8f01-aaaaaaaaaaaa'
        provenance.collection == 'loc'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant

        and: 'the legacy _jt_provenance field is not written on new roots'
        !captured.containsKey('_jt_provenance')
    }

    def 'materializes a human-readable loc create with explicit data.properties and data.links into the root shape'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        CommittedTransactionMessage message = new CommittedTransactionMessage(
                msgUuid: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                collection: 'loc',
                action: 'create',
                data: [
                        id        : LOC_ID,
                        properties: [
                                name       : 'freezer_a',
                                description: 'minus-80 freezer in room 110'
                        ],
                        links     : [:]
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id and source collection'
        captured._id == LOC_ID
        captured.id == LOC_ID
        captured.collection == 'loc'
        captured.type_id == null

        and: 'data.properties is preferred verbatim over the legacy flat-flatten path'
        Map properties = captured.properties as Map
        properties == [name: 'freezer_a', description: 'minus-80 freezer in room 110']
        !properties.containsKey('properties')
        !properties.containsKey('links')

        and: 'submitted data.links lands at root, not nested under properties'
        captured.links == [:]

        and: '_head carries the standard schema metadata and provenance for the human-readable shape'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == LOC_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == '018fd849-2a47-7777-8f01-aaaaaaaaaaaa'
        provenance.collection == 'loc'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant
    }

    def 'human-readable loc create with explicit data.type_id sets top-level type_id and keeps properties clean'() {
        given:
        String typeId = 'jade-tipi-org~dev~018fd849-2a4c-7ccc-8c0c-cccccccccccc~typ~freezer'
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        CommittedTransactionMessage message = new CommittedTransactionMessage(
                msgUuid: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                collection: 'loc',
                action: 'create',
                data: [
                        id        : LOC_ID,
                        type_id   : typeId,
                        properties: [name: 'freezer_a'],
                        links     : [:]
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1
        captured.type_id == typeId
        Map properties = captured.properties as Map
        properties == [name: 'freezer_a']
        !properties.containsKey('type_id')
        !properties.containsKey('id')
        captured.links == [:]
    }

    def 'human-readable loc create copies a non-empty data.links map verbatim into the root'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        Map<String, Object> linksValue = [
                contents: [endpoint_role: 'container', count: 4]
        ]
        CommittedTransactionMessage message = new CommittedTransactionMessage(
                msgUuid: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                collection: 'loc',
                action: 'create',
                data: [
                        id        : LOC_ID,
                        properties: [name: 'freezer_a'],
                        links     : linksValue
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1
        captured.links == linksValue
        Map properties = captured.properties as Map
        !properties.containsKey('links')
    }

    def 'loc with an explicit data.type_id sets top-level type_id and excludes it from properties'() {
        given:
        String typeId = 'jade-tipi-org~dev~018fd849-2a4c-7ccc-8c0c-cccccccccccc~typ~freezer'
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([locMessage([type_id: typeId])])).block()

        then:
        result.materialized == 1
        captured.type_id == typeId
        Map properties = captured.properties as Map
        !properties.containsKey('type_id')
        properties.name == 'freezer_a'
    }

    def 'materializes a link-type typ create root with all six declarative facts under properties'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([linkTypeMessage()])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0

        and: 'shared root fields are populated and type_id is null for a typ link-type root'
        captured._id == TYP_ID
        captured.id == TYP_ID
        captured.collection == 'typ'
        captured.type_id == null

        and: 'all six declarative facts plus allowed_*_collections survive under properties'
        Map properties = captured.properties as Map
        properties.kind == 'link_type'
        properties.name == 'contents'
        properties.left_role == 'container'
        properties.right_role == 'content'
        properties.left_to_right_label == 'contains'
        properties.right_to_left_label == 'contained_by'
        properties.allowed_left_collections == ['loc']
        properties.allowed_right_collections == ['loc', 'ent']
        !properties.containsKey('id')
        !properties.containsKey('type_id')

        and: 'links is empty and _head.provenance carries source collection/action'
        captured.links == [:]
        Map provenance = (captured._head as Map).provenance as Map
        provenance.collection == 'typ'
        provenance.action == 'create'
        provenance.txn_id == TXN_ID
        !captured.containsKey('_jt_provenance')
    }

    def 'materializes a bare entity-type typ create as a root document with name and description under properties and null type_id'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityTypeMessage()])).block()

        then:
        result != null
        result.materialized == 1
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id and source collection'
        captured._id == ENT_TYPE_ID
        captured.id == ENT_TYPE_ID
        captured.collection == 'typ'
        captured.type_id == null

        and: 'inline-properties fallback lifts name and description into root properties without a kind discriminator'
        Map properties = captured.properties as Map
        properties.name == 'plate_96'
        properties.description == '96-well sample plate'
        !properties.containsKey('id')
        !properties.containsKey('type_id')
        !properties.containsKey('kind')

        and: 'links is initialized to an empty map for new bare entity-type roots'
        captured.links == [:]

        and: '_head carries schema metadata and typ provenance'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == ENT_TYPE_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == ENTITY_TYPE_MSG_UUID
        provenance.collection == 'typ'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant

        and: 'the legacy _jt_provenance field is not written on new typ roots'
        !captured.containsKey('_jt_provenance')
    }

    def 'bare entity-type typ create with empty data.links materializes with root links == [:] and no properties.links'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([entityTypeMessage([links: [:]])])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'empty data.links surfaces only at the root, never under properties'
        captured.links == [:]
        Map properties = captured.properties as Map
        !properties.containsKey('links')
        properties.name == 'plate_96'
        properties.description == '96-well sample plate'
        !properties.containsKey('id')
        !properties.containsKey('type_id')
    }

    def 'skips a typ + update message even after dropping the kind guard'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([updateEntityTypeMessage()])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
    }

    def 'identical-payload bare entity-type typ duplicate is matching even when materialized_at differs'() {
        given: 'existing typ root has the same payload but an earlier materialized_at'
        Map existing = [
                _id        : ENT_TYPE_ID,
                id         : ENT_TYPE_ID,
                collection : 'typ',
                type_id    : null,
                properties : [
                        name       : 'plate_96',
                        description: '96-well sample plate'
                ],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : ENT_TYPE_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : ENTITY_TYPE_MSG_UUID,
                                collection     : 'typ',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'typ') >> Mono.error(springDuplicate())
        mongoTemplate.findById(ENT_TYPE_ID, Map.class, 'typ') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityTypeMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 1
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0
    }

    def 'differing-payload bare entity-type typ duplicate is conflicting and not overwritten'() {
        given:
        Map existing = [
                _id        : ENT_TYPE_ID,
                id         : ENT_TYPE_ID,
                collection : 'typ',
                type_id    : null,
                properties : [
                        name       : 'plate_96_OLD',
                        description: 'a different description'
                ],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : ENT_TYPE_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : ENTITY_TYPE_MSG_UUID,
                                collection     : 'typ',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'typ') >> Mono.error(springDuplicate())
        mongoTemplate.findById(ENT_TYPE_ID, Map.class, 'typ') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityTypeMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 1

        and: 'no save, update, or overwrite path is taken'
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.save(_, _)
    }

    def 'materializes a lnk create as a root with top-level type_id, left, right, and properties.position'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([linkMessage()])).block()

        then:
        result.materialized == 1

        and: 'shared root and lnk-specific fields are at the top level'
        captured._id == LNK_ID
        captured.id == LNK_ID
        captured.collection == 'lnk'
        captured.type_id == TYP_ID
        captured.left.endsWith('~loc~plate_b1')
        captured.right.endsWith('~ent~sample_x1')

        and: 'plate-well coordinate is preserved verbatim under properties.position'
        Map properties = captured.properties as Map
        Map position = properties.position as Map
        position.kind == 'plate_well'
        position.label == 'A1'
        position.row == 'A'
        position.column == 1

        and: 'links is empty and _head.provenance is populated for the lnk source'
        captured.links == [:]
        Map provenance = (captured._head as Map).provenance as Map
        provenance.collection == 'lnk'
        provenance.action == 'create'
        provenance.msg_uuid == '018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb'
        !captured.containsKey('_jt_provenance')
    }

    def 'lnk create without payload properties defaults root properties to an empty map'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([linkMessage([properties: null])])).block()

        then:
        result.materialized == 1
        captured.properties == [:]
        captured.type_id == TYP_ID
        captured.left.endsWith('~loc~plate_b1')
        captured.right.endsWith('~ent~sample_x1')
    }

    def 'materialized typ link-type and lnk roots satisfy ContentsLinkReadService resolution criteria'() {
        given: 'capture both inserts that the contents-flow snapshot triggers'
        Map<String, Object> capturedTyp = null
        Map<String, Object> capturedLnk = null
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            capturedTyp = doc
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            capturedLnk = doc
            return Mono.just(doc)
        }

        when: 'snapshot mirrors the canonical 11 -> 12 contents-flow message pair'
        MaterializeResult result = materializer.materialize(
                snapshot([linkTypeMessage(), linkMessage()])).block()

        then: 'both messages materialize and nothing is skipped'
        result.materialized == 2
        result.skippedUnsupported == 0
        result.skippedInvalid == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0

        and: 'the typ root carries the dotted-path facts ContentsLinkReadService queries on'
        capturedTyp._id == TYP_ID
        capturedTyp.id == TYP_ID
        capturedTyp.collection == 'typ'
        Map typProperties = capturedTyp.properties as Map
        typProperties.kind == 'link_type'
        typProperties.name == 'contents'

        and: 'the lnk root references the typ root by _id at top-level type_id'
        capturedLnk._id == LNK_ID
        capturedLnk.collection == 'lnk'
        capturedLnk.type_id == capturedTyp._id
        capturedLnk.left.endsWith('~loc~plate_b1')
        capturedLnk.right.endsWith('~ent~sample_x1')
        Map position = (capturedLnk.properties as Map).position as Map
        position.kind == 'plate_well'
        position.label == 'A1'

        and: 'both roots carry _head.provenance with the source collection'
        ((capturedTyp._head as Map).provenance as Map).collection == 'typ'
        ((capturedLnk._head as Map).provenance as Map).collection == 'lnk'
    }

    def 'materializes a grp create as a root document with permissions under properties and _head.provenance'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'grp') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([groupMessage()])).block()

        then:
        result != null
        result.materialized == 1
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id and source collection'
        captured._id == GRP_ID
        captured.id == GRP_ID
        captured.collection == 'grp'
        captured.type_id == null

        and: 'name, description, and permissions live under properties verbatim'
        Map properties = captured.properties as Map
        properties.name == 'analytics'
        properties.description == 'analytics team'
        Map permissions = properties.permissions as Map
        permissions.size() == 2
        permissions[GRP_PEER_RW] == 'rw'
        permissions[GRP_PEER_R] == 'r'
        !properties.containsKey('id')
        !properties.containsKey('type_id')

        and: 'links is initialized to an empty map for new roots'
        captured.links == [:]

        and: '_head carries schema metadata and grp provenance'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == GRP_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == '018fd849-2a4d-7d0d-8d0d-cccccccccccc'
        provenance.collection == 'grp'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant

        and: 'the legacy _jt_provenance field is not written on new roots'
        !captured.containsKey('_jt_provenance')
    }

    def 'grp create that omits permissions still materializes with name and description under properties'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'grp') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        CommittedTransactionMessage message = new CommittedTransactionMessage(
                msgUuid: '018fd849-2a4d-7d0d-8d0d-cccccccccccc',
                collection: 'grp',
                action: 'create',
                data: [
                        id  : GRP_ID,
                        name: 'analytics'
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:04Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1
        Map properties = captured.properties as Map
        properties.name == 'analytics'
        !properties.containsKey('permissions')
        captured.collection == 'grp'
    }

    def 'skips a grp update message'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([groupMessage([:], 'update')])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
    }

    def 'skips a grp delete message'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([groupMessage([:], 'delete')])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
    }

    def 'skips a grp create with missing data.id'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([groupMessage([id: null])])).block()

        then:
        result.materialized == 0
        result.skippedInvalid == 1
        result.skippedUnsupported == 0
        0 * mongoTemplate.insert(_, _)
    }

    def 'skips a grp create with blank data.id'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([groupMessage([id: input])])).block()

        then:
        result.skippedInvalid == 1
        result.materialized == 0
        0 * mongoTemplate.insert(_, _)

        where:
        input << ['', '   ']
    }

    def 'skips a ppy create message'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([propertyCreateMessage()])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
    }

    def 'materializes an ent create as a root document with top-level type_id, empty properties, and empty links'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'ent') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityCreateMessage()])).block()

        then:
        result != null
        result.materialized == 1
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'shared root fields use the payload id and source collection'
        captured._id == ENT_ID
        captured.id == ENT_ID
        captured.collection == 'ent'
        captured.type_id == ENT_TYPE_ID

        and: 'explicit empty data.properties / data.links land at the root verbatim'
        captured.properties == [:]
        captured.links == [:]

        and: '_head carries schema metadata and ent provenance'
        Map head = captured._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == ENT_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_ID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == ENT_MSG_UUID
        provenance.collection == 'ent'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant

        and: 'the legacy _jt_provenance field is not written on new ent roots'
        !captured.containsKey('_jt_provenance')
    }

    def 'ent create without payload properties or links defaults root properties and links to empty maps'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'ent') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }
        CommittedTransactionMessage message = new CommittedTransactionMessage(
                msgUuid: ENT_MSG_UUID,
                collection: 'ent',
                action: 'create',
                data: [id: ENT_ID, type_id: ENT_TYPE_ID],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.materialized == 1
        captured._id == ENT_ID
        captured.id == ENT_ID
        captured.collection == 'ent'
        captured.type_id == ENT_TYPE_ID

        and: 'inline-properties fallback yields empty maps when only id and type_id are present'
        captured.properties == [:]
        captured.links == [:]
    }

    def 'ent create with missing data.id is counted as skippedInvalid'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([entityCreateMessage([id: null])])).block()

        then:
        result.materialized == 0
        result.skippedInvalid == 1
        result.skippedUnsupported == 0
        0 * mongoTemplate.insert(_, _)
    }

    def 'ent create with blank or whitespace data.id is also counted as skippedInvalid'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([entityCreateMessage([id: input])])).block()

        then:
        result.skippedInvalid == 1
        result.materialized == 0
        0 * mongoTemplate.insert(_, _)

        where:
        input << ['', '   ']
    }

    def 'identical-payload ent duplicate is matching even when materialized_at differs'() {
        given: 'existing ent root has the same payload but an earlier materialized_at'
        Map existing = [
                _id        : ENT_ID,
                id         : ENT_ID,
                collection : 'ent',
                type_id    : ENT_TYPE_ID,
                properties : [:],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : ENT_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : ENT_MSG_UUID,
                                collection     : 'ent',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'ent') >> Mono.error(springDuplicate())
        mongoTemplate.findById(ENT_ID, Map.class, 'ent') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityCreateMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 1
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0
    }

    def 'differing-payload ent duplicate is conflicting and not overwritten'() {
        given:
        Map existing = [
                _id        : ENT_ID,
                id         : ENT_ID,
                collection : 'ent',
                type_id    : 'jade-tipi-org~dev~018fd849-2a48-7888-8a08-ffffffffffff~typ~older_type',
                properties : [:],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : ENT_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : ENT_MSG_UUID,
                                collection     : 'ent',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'ent') >> Mono.error(springDuplicate())
        mongoTemplate.findById(ENT_ID, Map.class, 'ent') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([entityCreateMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 1

        and: 'no save, update, or overwrite path is taken'
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.save(_, _)
    }

    def 'skips update and delete actions on supported collections'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([updateLocationMessage()])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
    }

    def 'identical-payload duplicate is matching even when materialized_at differs'() {
        given: 'existing root has the same payload but an earlier materialized_at'
        Map existing = [
                _id        : LOC_ID,
                id         : LOC_ID,
                collection : 'loc',
                type_id    : null,
                properties : [
                        name       : 'freezer_a',
                        description: 'minus-80 freezer in room 110'
                ],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : LOC_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                                collection     : 'loc',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'loc') >> Mono.error(springDuplicate())
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([locMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 1
        result.conflictingDuplicate == 0
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'no second insert or update is attempted'
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.save(_, _)
    }

    def 'differing-payload duplicate is conflicting and not overwritten'() {
        given:
        Map existing = [
                _id        : LOC_ID,
                id         : LOC_ID,
                collection : 'loc',
                type_id    : null,
                properties : [
                        name       : 'freezer_a_OLD',
                        description: 'a different description'
                ],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : LOC_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : COMMIT_ID,
                                msg_uuid       : '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                                collection     : 'loc',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'loc') >> Mono.error(springDuplicate())
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([locMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and: 'no save, update, or overwrite path is taken'
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.save(_, _)
    }

    def 'differing _head.provenance (excluding materialized_at) is a conflict'() {
        given: 'same payload, but provenance differs on commit_id'
        Map existing = [
                _id        : LOC_ID,
                id         : LOC_ID,
                collection : 'loc',
                type_id    : null,
                properties : [
                        name       : 'freezer_a',
                        description: 'minus-80 freezer in room 110'
                ],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : LOC_ID,
                        provenance    : [
                                txn_id         : TXN_ID,
                                commit_id      : 'OTHER-COMMIT',
                                msg_uuid       : '018fd849-2a47-7777-8f01-aaaaaaaaaaaa',
                                collection     : 'loc',
                                action         : 'create',
                                committed_at   : COMMITTED_AT,
                                materialized_at: Instant.parse('2025-12-31T00:00:01Z')
                        ]
                ]
        ]
        mongoTemplate.insert(_ as Map, 'loc') >> Mono.error(springDuplicate())
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(existing)

        when:
        MaterializeResult result = materializer.materialize(snapshot([locMessage()])).block()

        then:
        result.materialized == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 1
    }

    def 'a single conflicting duplicate does not block subsequent messages'() {
        given:
        Map existingLoc = [
                _id        : LOC_ID,
                id         : LOC_ID,
                collection : 'loc',
                type_id    : null,
                properties : [name: 'older-freezer'],
                links      : [:],
                _head      : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : LOC_ID,
                        provenance    : [
                                txn_id         : 'older-txn',
                                commit_id      : 'older-commit',
                                msg_uuid       : 'older-msg',
                                collection     : 'loc',
                                action         : 'create',
                                committed_at   : Instant.parse('2025-12-30T00:00:00Z'),
                                materialized_at: Instant.parse('2025-12-30T00:00:01Z')
                        ]
                ]
        ]
        boolean linkInserted = false
        mongoTemplate.insert(_ as Map, 'loc') >> Mono.error(springDuplicate())
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(existingLoc)
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            linkInserted = true
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([locMessage(), linkMessage()])).block()

        then:
        linkInserted
        result.materialized == 1
        result.conflictingDuplicate == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0
    }

    def 'non-duplicate insert failure propagates as an error'() {
        given:
        mongoTemplate.insert(_ as Map, 'loc') >> Mono.error(new RuntimeException('mongo down'))

        when:
        materializer.materialize(snapshot([locMessage()])).block()

        then:
        RuntimeException ex = thrown(RuntimeException)
        ex.message == 'mongo down'
        0 * mongoTemplate.findById(_, _, _)
    }

    def 'missing data.id is counted as skippedInvalid and not auto-id\'d'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([locMessage([id: null])])).block()

        then:
        result.materialized == 0
        result.skippedInvalid == 1
        result.skippedUnsupported == 0
        0 * mongoTemplate.insert(_, _)
    }

    def 'blank or whitespace data.id is also counted as skippedInvalid'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([locMessage([id: input])])).block()

        then:
        result.skippedInvalid == 1
        result.materialized == 0
        0 * mongoTemplate.insert(_, _)

        where:
        input << ['', '   ']
    }

    def 'mixed-message snapshot inserts in snapshot order with correct counts'() {
        given:
        List<String> insertOrder = []
        List<String> typInsertIds = []
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            insertOrder << 'loc'
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            insertOrder << 'typ'
            typInsertIds << (doc._id as String)
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'ent') >> { Map doc, String _coll ->
            insertOrder << 'ent'
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            insertOrder << 'lnk'
            return Mono.just(doc)
        }

        when: 'snapshot has loc, ppy (skip), typ link-type, typ bare entity-type, ent, lnk in that order'
        MaterializeResult result = materializer.materialize(snapshot([
                locMessage(),
                propertyCreateMessage(),
                linkTypeMessage(),
                entityTypeMessage(),
                entityCreateMessage(),
                linkMessage()
        ])).block()

        then: 'both typ kinds materialize back-to-back; only the ppy create skips'
        insertOrder == ['loc', 'typ', 'typ', 'ent', 'lnk']
        typInsertIds == [TYP_ID, ENT_TYPE_ID]
        result.materialized == 5
        result.skippedUnsupported == 1
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0
        result.skippedInvalid == 0
    }

    def 'materialize(txnId) delegates to readService and returns Mono.empty() when not visible'() {
        given:
        readService.findCommitted(TXN_ID) >> Mono.empty()

        when:
        MaterializeResult result = materializer.materialize(TXN_ID).block()

        then:
        result == null
        0 * mongoTemplate.insert(_, _)
        0 * mongoTemplate.findById(_, _, _)
    }

    def 'materialize(txnId) materializes through the snapshot returned by readService'() {
        given:
        readService.findCommitted(TXN_ID) >> Mono.just(snapshot([locMessage()]))
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(TXN_ID).block()

        then:
        result != null
        result.materialized == 1
        captured._id == LOC_ID
        captured.collection == 'loc'
        (captured._head as Map).document_kind == 'root'
    }

    def 'blank txnId is rejected with IllegalArgumentException'() {
        when: 'cast disambiguates the null/String case from the snapshot overload'
        materializer.materialize((String) input)

        then:
        thrown(IllegalArgumentException)
        0 * readService.findCommitted(_)
        0 * mongoTemplate.insert(_, _)

        where:
        input << [null, '', '   ']
    }

    def 'null snapshot returns Mono.empty() without touching mongoTemplate'() {
        when:
        MaterializeResult result = materializer.materialize((CommittedTransactionSnapshot) null).block()

        then:
        result == null
        0 * mongoTemplate.insert(_, _)
    }
}
