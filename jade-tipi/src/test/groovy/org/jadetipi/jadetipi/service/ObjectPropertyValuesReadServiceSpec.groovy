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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class ObjectPropertyValuesReadServiceSpec extends Specification {

    static final String LOC_ID = 'jade-tipi-org~dev~018fd849-2a61-7111-8a01-aaaaaaaaaaaa~loc~plate_0001'
    static final String TYP_ID = 'jade-tipi-org~dev~018fd849-2a62-7222-8a02-bbbbbbbbbbbb~typ~plate_96_well'
    static final String PPY_BARCODE = 'jade-tipi-org~dev~018fd849-2a63-7333-8a03-cccccccccccc~ppy~barcode'
    static final String PPY_VOLUME = 'jade-tipi-org~dev~018fd849-2a64-7444-8a04-dddddddddddd~ppy~volume'
    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee~org~grp~kli'
    static final Instant APPLIED_AT = Instant.parse('2026-07-03T00:00:00Z')

    ReactiveMongoTemplate mongoTemplate
    ObjectPropertyValuesReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new ObjectPropertyValuesReadService(mongoTemplate)
    }

    private static Map locRoot(Map extra = [:]) {
        Map root = [
                _id       : LOC_ID,
                id        : LOC_ID,
                collection: 'loc',
                type_id   : TYP_ID,
                properties: [name: 'demo plate 0001'],
                links     : [:],
                _head     : [provenance: [txn_id: TXN_ID, commit_id: 'COMMIT-1']]
        ]
        root.putAll(extra)
        return root
    }

    private static Map entry(Map overrides = [:]) {
        Map base = [
                value     : [text: 'PLATE-BC-0001'],
                txn_id    : TXN_ID,
                commit_id : 'COMMIT-1',
                msg_uuid  : '018fd849-2a59-7999-8a09-efefefefefef',
                applied_at: APPLIED_AT
        ]
        base.putAll(overrides)
        return base
    }

    def 'returns the loc root with projected property values and resolved names'() {
        given:
        Map root = locRoot([property_values: [(PPY_BARCODE): entry()]])
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(root)
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.just([
                _id       : PPY_BARCODE,
                properties: [kind: 'definition', name: 'barcode']
        ] as Map)

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('loc', LOC_ID).block()

        then:
        record.objectId == LOC_ID
        record.collection == 'loc'
        record.typeId == TYP_ID
        record.properties == [name: 'demo plate 0001']
        record.links == [:]
        record.provenance.txn_id == TXN_ID

        and: 'the entry carries value, provenance, and the resolved name'
        record.propertyValues.size() == 1
        ObjectPropertyValueEntryRecord barcode = record.propertyValues[PPY_BARCODE]
        barcode.propertyId == PPY_BARCODE
        barcode.propertyName == 'barcode'
        barcode.value == [text: 'PLATE-BC-0001']
        barcode.txnId == TXN_ID
        barcode.commitId == 'COMMIT-1'
        barcode.msgUuid == '018fd849-2a59-7999-8a09-efefefefefef'
        barcode.appliedAt == APPLIED_AT
    }

    def 'an existing root with no projected values returns an empty propertyValues map without a definitions query'() {
        given:
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(locRoot())

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('loc', LOC_ID).block()

        then:
        record.propertyValues == [:]
        0 * mongoTemplate.find(*_)
    }

    def 'a missing root resolves empty for the controller 404'() {
        given:
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.empty()

        expect:
        service.findPropertyValues('loc', LOC_ID).block() == null
    }

    def 'a dangling property definition leaves propertyName null'() {
        given:
        Map root = locRoot([property_values: [(PPY_BARCODE): entry()]])
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(root)
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('loc', LOC_ID).block()

        then:
        record.propertyValues[PPY_BARCODE].propertyName == null
    }

    def 'stale rows are tolerated: non-map entries are skipped and a non-map value becomes an empty map'() {
        given:
        Map root = locRoot([property_values: [
                (PPY_BARCODE): entry(value: 'not-an-object'),
                (PPY_VOLUME) : 'stale-scalar-entry'
        ]])
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(root)
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('loc', LOC_ID).block()

        then:
        record.propertyValues.keySet() == [PPY_BARCODE] as Set
        record.propertyValues[PPY_BARCODE].value == [:]
    }

    def 'entries are returned sorted by property id'() {
        given:
        Map root = locRoot([property_values: [
                (PPY_VOLUME) : entry(value: [number: 200]),
                (PPY_BARCODE): entry()
        ]])
        mongoTemplate.findById(LOC_ID, Map.class, 'loc') >> Mono.just(root)
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('loc', LOC_ID).block()

        then:
        record.propertyValues.keySet().toList() == [PPY_BARCODE, PPY_VOLUME].sort()
    }

    def 'ent roots are supported by the generic service'() {
        given:
        String entId = 'jade-tipi-org~dev~018fd849-2a65-7555-8a05-eeeeeeeeeeee~ent~sample_x'
        mongoTemplate.findById(entId, Map.class, 'ent') >> Mono.just([
                _id: entId, collection: 'ent', type_id: TYP_ID, properties: [:], links: [:]
        ] as Map)

        when:
        ObjectPropertyValuesRecord record = service.findPropertyValues('ent', entId).block()

        then:
        record.objectId == entId
        record.collection == 'ent'
    }

    def 'blank arguments and unsupported collections are rejected'() {
        when:
        service.findPropertyValues(collection, objectId)

        then:
        thrown(IllegalArgumentException)

        where:
        collection | objectId
        'loc'      | ' '
        ' '        | LOC_ID
        'grp'      | LOC_ID
    }
}
