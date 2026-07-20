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
 * TASK-040 coverage for object-targeted {@code ppy + create} assignments:
 * routing, inheritance-aware registration via {@code parent_type_id}, and
 * projection onto object roots under {@code property_values.<ppy_id>}.
 * Legacy {@code entity_id}-only behavior is covered by
 * {@link CommittedTransactionMaterializerSpec}; one routing feature here
 * proves the legacy path is still taken when object fields are absent.
 */
class CommittedTransactionMaterializerObjectPropertySpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'
    static final Instant OPENED_AT = Instant.parse('2026-01-01T00:00:00Z')
    static final Instant COMMITTED_AT = Instant.parse('2026-01-01T00:00:05Z')

    static final String PPY_BARCODE = '018fd849-2a41-7111-8a01-cccccccccccc~jade-tipi-org~dev~ppy~barcode'
    static final String TYP_CONTAINER = '018fd849-2a51-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~typ~container'
    static final String TYP_PLATE = '018fd849-2a52-7222-8a02-bbbbbbbbbbbb~jade-tipi-org~dev~typ~plate'
    static final String TYP_PLATE96 = '018fd849-2a53-7333-8a03-cccccccccccc~jade-tipi-org~dev~typ~plate_96_well'
    static final String LOC_PLATE = '018fd849-2a54-7444-8a04-dddddddddddd~jade-tipi-org~dev~loc~plate_0001'
    static final String ENT_SAMPLE = '018fd849-2a55-7555-8a05-eeeeeeeeeeee~jade-tipi-org~dev~ent~sample_x'
    static final String MSG_UUID = '018fd849-2a56-7666-8f06-ffffffffffff'

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

    private static CommittedTransactionMessage objectAssignment(Map<String, Object> dataOverrides = [:]) {
        Map<String, Object> data = [
                kind             : 'assignment',
                object_collection: 'loc',
                object_id        : LOC_PLATE,
                property_id      : PPY_BARCODE,
                value            : [text: 'BC-0001']
        ] as Map<String, Object>
        data.putAll(dataOverrides)
        dataOverrides.each { k, v -> if (v == null) data.remove(k) }
        return new CommittedTransactionMessage(
                msgUuid: MSG_UUID,
                collection: 'ppy',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
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

    private static Map objectRoot(String id, String collection, String typeId, Map extra = [:]) {
        Map root = [
                _id       : id,
                id        : id,
                collection: collection,
                type_id   : typeId,
                properties: [:],
                links     : [:]
        ]
        root.putAll(extra)
        return root
    }

    def 'projects a loc-targeted assignment onto property_values when the property is registered on the type'() {
        given:
        Query capturedQuery = null
        Update capturedUpdate = null
        String capturedCollection = null
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'loc') >> {
            Query q, Update u, String coll ->
                capturedQuery = q
                capturedUpdate = u
                capturedCollection = coll
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.materialized == 1
        result.skippedInvalid == 0
        result.skippedMissingTarget == 0
        result.skippedUnregisteredProperty == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0

        and: 'the update is a dotted $set on property_values.<ppy_id> against the loc collection'
        capturedCollection == 'loc'
        capturedQuery.queryObject.get('_id') == LOC_PLATE
        Map setOps = capturedUpdate.updateObject.get('$set') as Map
        setOps.size() == 1
        Map entry = setOps["property_values.${PPY_BARCODE}" as String] as Map
        entry.value == [text: 'BC-0001']
        entry.txn_id == TXN_ID
        entry.commit_id == COMMIT_ID
        entry.msg_uuid == MSG_UUID
        entry.applied_at instanceof Instant

        and: 'no standalone assignment root is inserted'
        0 * mongoTemplate.insert(_, !'hst')
    }

    def 'registration walks parent_type_id to an ancestor that lists the property'() {
        given: 'barcode is registered on container; plate_96_well -> plate -> container'
        Update capturedUpdate = null
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE, [name: 'plate', parent_type_id: TYP_CONTAINER]))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'loc') >> {
            Query q, Update u, String coll ->
                capturedUpdate = u
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.materialized == 1
        result.skippedUnregisteredProperty == 0
        (capturedUpdate.updateObject.get('$set') as Map).containsKey("property_values.${PPY_BARCODE}" as String)
    }

    def 'projects an ent-targeted object-form assignment onto the ent root'() {
        given:
        String capturedCollection = null
        mongoTemplate.findById(ENT_SAMPLE, Map.class, 'ent') >>
                Mono.just(objectRoot(ENT_SAMPLE, 'ent', TYP_CONTAINER))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'ent') >> {
            Query q, Update u, String coll ->
                capturedCollection = coll
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([
                objectAssignment(object_collection: 'ent', object_id: ENT_SAMPLE)])).block()

        then:
        result.materialized == 1
        capturedCollection == 'ent'
        0 * mongoTemplate.insert(_, !'hst')
    }

    def 'skips as unregistered when the parent chain is exhausted without the property'() {
        given:
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_CONTAINER]))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_CONTAINER, [name: 'container']))

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedUnregisteredProperty == 1
        result.materialized == 0
        0 * mongoTemplate.updateFirst(_, _, !'txn')
    }

    def 'skips as unregistered when an ancestor typ root is missing'() {
        given:
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >> Mono.empty()

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedUnregisteredProperty == 1
        result.materialized == 0
        0 * mongoTemplate.updateFirst(_, _, !'txn')
    }

    def 'terminates and skips as unregistered when the parent chain has a cycle'() {
        given: 'plate_96_well and plate point at each other'
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE, [name: 'plate', parent_type_id: TYP_PLATE96]))

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedUnregisteredProperty == 1
        result.materialized == 0
    }

    def 'skips as unregistered when the chain exceeds the inheritance depth cap'() {
        given: 'a 12-type chain with the property registered only past the cap'
        List<String> chain = (0..11).collect { int i -> "018fd849-2a5c-7ccc-8a0c-121212121212~jade-tipi-org~dev~typ~t${i}" as String }
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', chain[0]))
        (0..11).each { int i ->
            Map properties = [name: "t${i}" as String]
            if (i < 11) {
                properties.parent_type_id = chain[i + 1]
            } else {
                properties.property_refs = [(PPY_BARCODE): [:]]
            }
            mongoTemplate.findById(chain[i], Map.class, 'typ') >>
                    Mono.just(typRoot(chain[i], properties))
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedUnregisteredProperty == 1
        result.materialized == 0
    }

    def 'skips as missing target when the object root does not exist'() {
        given:
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >> Mono.empty()

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedMissingTarget == 1
        result.materialized == 0
    }

    def 'skips as unregistered when the target root carries no type_id'() {
        given:
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', null))

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.skippedUnregisteredProperty == 1
        result.materialized == 0
    }

    def 'skips as invalid for unsupported object_collection, missing fields, and non-object value'() {
        when:
        MaterializeResult result = materializer.materialize(snapshot([
                objectAssignment(object_collection: 'grp'),
                objectAssignment(object_id: null),
                objectAssignment(property_id: null),
                objectAssignment(value: 'BC-0001')
        ])).block()

        then: 'each malformed message is counted and nothing is read or written'
        result.skippedInvalid == 4
        result.materialized == 0
        0 * mongoTemplate.updateFirst(_, _, !'txn')
        0 * mongoTemplate.insert(_, !'hst')
    }

    def 'a payload with object_id but no object_collection routes to the object path and is invalid'() {
        given: 'entity_id is also present, but the object form takes precedence over legacy routing'
        CommittedTransactionMessage message = objectAssignment(
                object_collection: null, entity_id: ENT_SAMPLE)

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then:
        result.skippedInvalid == 1
        result.materialized == 0
        0 * mongoTemplate.insert(_, !'hst')
    }

    def 'an existing equal entry ignoring applied_at is duplicate-matching and is not re-written'() {
        given:
        Map existingEntry = [
                value     : [text: 'BC-0001'],
                txn_id    : TXN_ID,
                commit_id : COMMIT_ID,
                msg_uuid  : MSG_UUID,
                applied_at: Instant.parse('2025-12-31T00:00:01Z')
        ]
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96,
                        [property_values: [(PPY_BARCODE): existingEntry]]))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', property_refs: [(PPY_BARCODE): [:]]]))

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.duplicateMatching == 1
        result.conflictingDuplicate == 0
        result.materialized == 0
        0 * mongoTemplate.updateFirst(_, _, !'txn')
    }

    def 'a same-message assignment with a differing payload is conflicting and never overwritten'() {
        given: 'the same message id already applied with a different value (WAL-corruption guard)'
        Map existingEntry = [
                value     : [text: 'BC-9999'],
                txn_id    : 'other-txn',
                commit_id : 'other-commit',
                msg_uuid  : MSG_UUID,
                applied_at: Instant.parse('2025-12-31T00:00:01Z')
        ]
        mongoTemplate.findById(LOC_PLATE, Map.class, 'loc') >>
                Mono.just(objectRoot(LOC_PLATE, 'loc', TYP_PLATE96,
                        [property_values: [(PPY_BARCODE): existingEntry]]))
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_PLATE96, [name: 'plate_96_well', property_refs: [(PPY_BARCODE): [:]]]))

        when:
        MaterializeResult result = materializer.materialize(snapshot([objectAssignment()])).block()

        then:
        result.conflictingDuplicate == 1
        result.duplicateMatching == 0
        result.materialized == 0
        0 * mongoTemplate.updateFirst(_, _, !'txn')
    }

    def 'a legacy entity_id-only assignment resolves through the deprecated alias onto the ent root'() {
        given:
        String capturedCollection = null
        Update capturedUpdate = null
        mongoTemplate.findById(ENT_SAMPLE, Map.class, 'ent') >>
                Mono.just(objectRoot(ENT_SAMPLE, 'ent', TYP_CONTAINER))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >>
                Mono.just(typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'ent') >> {
            Query q, Update u, String coll ->
                capturedCollection = coll
                capturedUpdate = u
                return Mono.empty()
        }
        CommittedTransactionMessage legacy = new CommittedTransactionMessage(
                msgUuid: MSG_UUID,
                collection: 'ppy',
                action: 'create',
                data: [
                        kind       : 'assignment',
                        id         : ENT_SAMPLE + '~' + PPY_BARCODE,
                        entity_id  : ENT_SAMPLE,
                        property_id: PPY_BARCODE,
                        value      : [text: 'BC-0001']
                ],
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )

        when:
        MaterializeResult result = materializer.materialize(snapshot([legacy])).block()

        then: 'the value projects onto the ent root; no standalone assignment root is written'
        result.materialized == 1
        capturedCollection == 'ent'
        (capturedUpdate.updateObject.get('$set') as Map)
                .containsKey("property_values.${PPY_BARCODE}" as String)
        0 * mongoTemplate.insert(_, !'hst')
    }
}
