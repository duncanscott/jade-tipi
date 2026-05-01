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

    private static CommittedTransactionMessage entityTypeMessage() {
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a48-7888-8a08-eeeeeeeeeeee',
                collection: 'typ',
                action: 'create',
                data: [
                        id  : 'jade-tipi-org~dev~018fd849-2a48-7888-8a08-eeeeeeeeeeee~typ~plate_96',
                        name: 'plate_96'
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

    private static CommittedTransactionMessage entityCreateMessage() {
        return new CommittedTransactionMessage(
                msgUuid: '018fd849-2a42-7222-8a02-dddddddddddd',
                collection: 'ent',
                action: 'create',
                data: [id: 'jade-tipi-org~dev~018fd849-2a42-7222-8a02-dddddddddddd~ent~plate_a'],
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
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

    def 'skips a typ create whose data.kind is not link_type'() {
        when:
        MaterializeResult result = materializer.materialize(snapshot([entityTypeMessage()])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 1
        0 * mongoTemplate.insert(_, _)
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

    def 'skips ppy and ent create messages'() {
        when:
        MaterializeResult result = materializer.materialize(
                snapshot([propertyCreateMessage(), entityCreateMessage()])).block()

        then:
        result.materialized == 0
        result.skippedUnsupported == 2
        0 * mongoTemplate.insert(_, _)
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
        mongoTemplate.insert(_ as Map, 'loc') >> { Map doc, String _coll ->
            insertOrder << 'loc'
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'typ') >> { Map doc, String _coll ->
            insertOrder << 'typ'
            return Mono.just(doc)
        }
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _coll ->
            insertOrder << 'lnk'
            return Mono.just(doc)
        }

        when: 'snapshot has loc, ppy (skip), typ link-type, ent (skip), lnk in that order'
        MaterializeResult result = materializer.materialize(snapshot([
                locMessage(),
                propertyCreateMessage(),
                linkTypeMessage(),
                entityCreateMessage(),
                linkMessage()
        ])).block()

        then:
        insertOrder == ['loc', 'typ', 'lnk']
        result.materialized == 3
        result.skippedUnsupported == 2
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
