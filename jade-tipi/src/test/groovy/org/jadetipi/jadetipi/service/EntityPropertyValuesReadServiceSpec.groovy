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

class EntityPropertyValuesReadServiceSpec extends Specification {

    static final String ENTITY_ID = 'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~plate_a'
    static final String ENTITY_TYPE_ID = 'jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~typ~plate_96'
    static final String BARCODE_PROPERTY_ID = 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode'
    static final String VOLUME_PROPERTY_ID = 'jade-tipi-org~dev~018fd849-2a42-7222-8b02-bbbbbbbbbbbb~pp~volume'
    static final String ASSIGNMENT_ID = "${ENTITY_ID}~${BARCODE_PROPERTY_ID}"
    static final String ASSIGNMENT_ID_ALT = "${ENTITY_ID}~alt~${BARCODE_PROPERTY_ID}"
    static final String VOLUME_ASSIGNMENT_ID = "${ENTITY_ID}~${VOLUME_PROPERTY_ID}"

    ReactiveMongoTemplate mongoTemplate
    EntityPropertyValuesReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new EntityPropertyValuesReadService(mongoTemplate)
    }

    private static Map entityRow(Map overrides = [:]) {
        Map base = [
                _id       : ENTITY_ID,
                id        : ENTITY_ID,
                collection: 'ent',
                type_id   : ENTITY_TYPE_ID,
                properties: [label: 'Plate A'],
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : ENTITY_ID,
                        provenance    : [
                                txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id      : 'COMMIT-ENT',
                                msg_uuid       : '11111111-1111-7111-8111-111111111111',
                                collection     : 'ent',
                                action         : 'create',
                                committed_at   : Instant.parse('2026-01-01T00:00:05Z'),
                                materialized_at: Instant.parse('2026-01-01T00:00:06Z')
                        ]
                ]
        ]
        base.putAll(overrides)
        return base
    }

    private static Map assignmentRow(String id = ASSIGNMENT_ID,
                                     String propertyId = BARCODE_PROPERTY_ID,
                                     Map value = [text: 'barcode-1'],
                                     Map overrides = [:]) {
        Map base = [
                _id       : id,
                id        : id,
                collection: 'ppy',
                type_id   : null,
                properties: [
                        kind       : 'assignment',
                        entity_id  : ENTITY_ID,
                        property_id: propertyId,
                        value      : value
                ],
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : id,
                        provenance    : [
                                txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id      : 'COMMIT-PPY',
                                msg_uuid       : '22222222-2222-7222-8222-222222222222',
                                collection     : 'ppy',
                                action         : 'create',
                                committed_at   : Instant.parse('2026-01-01T00:00:07Z'),
                                materialized_at: Instant.parse('2026-01-01T00:00:08Z')
                        ]
                ]
        ]
        base.putAll(overrides)
        return base
    }

    private static Map definitionRow(String id = BARCODE_PROPERTY_ID, String name = 'barcode') {
        return [
                _id       : id,
                id        : id,
                collection: 'ppy',
                type_id   : null,
                properties: [
                        kind        : 'definition',
                        name        : name,
                        value_schema: [type: 'object']
                ],
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : id,
                        provenance    : [
                                txn_id    : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id : 'COMMIT-DEF',
                                msg_uuid  : '33333333-3333-7333-8333-333333333333',
                                collection: 'ppy',
                                action    : 'create'
                        ]
                ]
        ]
    }

    def 'findPropertyValues returns entity root plus assignment values and definition names'() {
        given:
        Query capturedAssignmentQuery = null
        Query capturedDefinitionQuery = null
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow())
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> { Query q, Class _t, String _c ->
            if (capturedAssignmentQuery == null) {
                capturedAssignmentQuery = q
                return Flux.just(
                        assignmentRow(ASSIGNMENT_ID, BARCODE_PROPERTY_ID, [text: 'barcode-1']),
                        assignmentRow(VOLUME_ASSIGNMENT_ID, VOLUME_PROPERTY_ID,
                                [number: 10, unit_id: 'ml'])
                )
            }
            capturedDefinitionQuery = q
            return Flux.just(
                    definitionRow(BARCODE_PROPERTY_ID, 'barcode'),
                    definitionRow(VOLUME_PROPERTY_ID, 'volume')
            )
        }

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record.entityId == ENTITY_ID
        record.typeId == ENTITY_TYPE_ID
        record.properties == [label: 'Plate A']
        record.links == [:]
        record.provenance.txn_id == 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
        record.provenance.commit_id == 'COMMIT-ENT'

        and:
        record.valuesByPropertyId.keySet() as List == [BARCODE_PROPERTY_ID, VOLUME_PROPERTY_ID]
        record.valuesByPropertyId[BARCODE_PROPERTY_ID].size() == 1
        EntityPropertyValueRecord barcode = record.valuesByPropertyId[BARCODE_PROPERTY_ID][0]
        barcode.assignmentId == ASSIGNMENT_ID
        barcode.propertyId == BARCODE_PROPERTY_ID
        barcode.propertyName == 'barcode'
        barcode.value == [text: 'barcode-1']
        barcode.provenance.collection == 'ppy'
        barcode.provenance.action == 'create'

        and:
        EntityPropertyValueRecord volume = record.valuesByPropertyId[VOLUME_PROPERTY_ID][0]
        volume.propertyName == 'volume'
        volume.value == [number: 10, unit_id: 'ml']

        and: 'assignment query is scoped to materialized ppy assignments for this entity'
        capturedAssignmentQuery != null
        Map assignmentQueryDoc = capturedAssignmentQuery.queryObject
        assignmentQueryDoc.get('properties.kind') == 'assignment'
        assignmentQueryDoc.get('properties.entity_id') == ENTITY_ID
        capturedAssignmentQuery.sortObject == new Document('properties.property_id', 1).append('_id', 1)

        and: 'definition query resolves only the assignment property ids'
        capturedDefinitionQuery != null
        Map definitionQueryDoc = capturedDefinitionQuery.queryObject
        (definitionQueryDoc.get('_id') as Map).get('$in').toSet() ==
                [BARCODE_PROPERTY_ID, VOLUME_PROPERTY_ID] as Set
        definitionQueryDoc.get('properties.kind') == 'definition'
        capturedDefinitionQuery.sortObject == new Document('_id', 1)
    }

    def 'missing entity root returns empty and does not query assignments'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.empty()

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record == null
        0 * mongoTemplate.find(_, Map.class, 'ppy')
    }

    def 'existing entity with zero assignments returns empty values map and skips definition lookup'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow())

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record.entityId == ENTITY_ID
        record.valuesByPropertyId == [:]

        and:
        1 * mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()
        0 * mongoTemplate.find(_, _, _)
    }

    def 'dangling property id keeps assignment with null propertyName'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow())
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >>> [
                Flux.just(assignmentRow()),
                Flux.empty()
        ]

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record.valuesByPropertyId[BARCODE_PROPERTY_ID].size() == 1
        record.valuesByPropertyId[BARCODE_PROPERTY_ID][0].propertyName == null
        record.valuesByPropertyId[BARCODE_PROPERTY_ID][0].value == [text: 'barcode-1']
    }

    def 'multiple assignment roots for one entity property are preserved as a list'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow())
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >>> [
                Flux.just(
                        assignmentRow(ASSIGNMENT_ID, BARCODE_PROPERTY_ID, [text: 'barcode-1']),
                        assignmentRow(ASSIGNMENT_ID_ALT, BARCODE_PROPERTY_ID, [text: 'barcode-2'])
                ),
                Flux.just(definitionRow(BARCODE_PROPERTY_ID, 'barcode'))
        ]

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record.valuesByPropertyId.keySet() as List == [BARCODE_PROPERTY_ID]
        record.valuesByPropertyId[BARCODE_PROPERTY_ID]*.assignmentId == [ASSIGNMENT_ID, ASSIGNMENT_ID_ALT]
        record.valuesByPropertyId[BARCODE_PROPERTY_ID]*.value == [[text: 'barcode-1'], [text: 'barcode-2']]
    }

    def 'missing provenance maps to null'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow([_head: null]))
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >>> [
                Flux.just(assignmentRow(ASSIGNMENT_ID, BARCODE_PROPERTY_ID, [text: 'barcode-1'], [_head: null])),
                Flux.just(definitionRow(BARCODE_PROPERTY_ID, 'barcode'))
        ]

        when:
        EntityPropertyValuesRecord record = service.findPropertyValues(ENTITY_ID).block()

        then:
        record.provenance == null
        record.valuesByPropertyId[BARCODE_PROPERTY_ID][0].provenance == null
    }

    def 'blank entityId is rejected before Mongo access'() {
        when:
        service.findPropertyValues(input).block()

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate.findById(_, _, _)
        0 * mongoTemplate.find(_, _, _)

        where:
        input << [null, '', '   ']
    }

    def 'service does not write to Mongo'() {
        given:
        mongoTemplate.findById(ENTITY_ID, Map.class, 'ent') >> Mono.just(entityRow())
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >>> [
                Flux.just(assignmentRow()),
                Flux.just(definitionRow())
        ]

        when:
        service.findPropertyValues(ENTITY_ID).block()

        then:
        0 * mongoTemplate.insert(_, _)
        0 * mongoTemplate.insert(_)
        0 * mongoTemplate.save(_, _)
        0 * mongoTemplate.save(_)
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.updateMulti(_, _, _)
        0 * mongoTemplate.remove(_, _)
        0 * mongoTemplate.remove(_)
    }
}
