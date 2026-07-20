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

class TypeEffectivePropertiesReadServiceSpec extends Specification {

    static final String TYP_CONTAINER = '018fd849-2a61-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~typ~container'
    static final String TYP_PLATE = '018fd849-2a62-7222-8a02-bbbbbbbbbbbb~jade-tipi-org~dev~typ~plate'
    static final String TYP_PLATE96 = '018fd849-2a63-7333-8a03-cccccccccccc~jade-tipi-org~dev~typ~plate_96_well'
    static final String PPY_BARCODE = '018fd849-2a64-7444-8a04-dddddddddddd~jade-tipi-org~dev~ppy~barcode'
    static final String PPY_FORMAT = '018fd849-2a65-7555-8a05-eeeeeeeeeeee~jade-tipi-org~dev~ppy~format'

    ReactiveMongoTemplate mongoTemplate
    TypeEffectivePropertiesReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new TypeEffectivePropertiesReadService(mongoTemplate)
    }

    private static Map typRoot(String id, Map properties) {
        return [_id: id, id: id, collection: 'typ', type_id: null,
                properties: properties, links: [:]]
    }

    def 'a type without a parent returns its own registrations'() {
        given:
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [required: true]]]))
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.just([
                _id: PPY_BARCODE, properties: [kind: 'definition', name: 'barcode']] as Map)

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(TYP_CONTAINER).block()

        then:
        record.typeId == TYP_CONTAINER
        record.typeName == 'container'
        record.typeChain == [TYP_CONTAINER]
        record.chainComplete
        record.effectiveProperties.size() == 1
        with(record.effectiveProperties[PPY_BARCODE]) {
            propertyId == PPY_BARCODE
            propertyName == 'barcode'
            sourceTypeId == TYP_CONTAINER
            reference == [required: true]
        }
    }

    def 'a subtype inherits all the properties of its ancestors with source attribution'() {
        given: 'barcode on container, format on plate, nothing on plate_96_well'
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE, [name: 'plate', parent_type_id: TYP_CONTAINER,
                                    property_refs: [(PPY_FORMAT): [:]]]))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(TYP_PLATE96).block()

        then:
        record.typeChain == [TYP_PLATE96, TYP_PLATE, TYP_CONTAINER]
        record.chainComplete
        record.effectiveProperties.keySet() == [PPY_BARCODE, PPY_FORMAT] as Set
        record.effectiveProperties[PPY_BARCODE].sourceTypeId == TYP_CONTAINER
        record.effectiveProperties[PPY_FORMAT].sourceTypeId == TYP_PLATE
    }

    def 'the most-derived registration wins when a property is registered at multiple levels'() {
        given: 'barcode registered on both plate (required) and container (optional)'
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE, [name: 'plate', parent_type_id: TYP_CONTAINER,
                                    property_refs: [(PPY_BARCODE): [required: true]]]))
        mongoTemplate.findById(TYP_CONTAINER, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_CONTAINER, [name: 'container', property_refs: [(PPY_BARCODE): [:]]]))
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(TYP_PLATE).block()

        then:
        record.effectiveProperties[PPY_BARCODE].sourceTypeId == TYP_PLATE
        record.effectiveProperties[PPY_BARCODE].reference == [required: true]
    }

    def 'a missing ancestor root yields a partial result with chainComplete false'() {
        given:
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE,
                                      property_refs: [(PPY_FORMAT): [:]]]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >> Mono.empty()
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(TYP_PLATE96).block()

        then:
        !record.chainComplete
        record.typeChain == [TYP_PLATE96]
        record.effectiveProperties.keySet() == [PPY_FORMAT] as Set
    }

    def 'a parent cycle terminates with chainComplete false'() {
        given:
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE96, [name: 'plate_96_well', parent_type_id: TYP_PLATE]))
        mongoTemplate.findById(TYP_PLATE, Map.class, 'typ') >> Mono.just(
                typRoot(TYP_PLATE, [name: 'plate', parent_type_id: TYP_PLATE96]))
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(TYP_PLATE96).block()

        then:
        !record.chainComplete
        record.typeChain == [TYP_PLATE96, TYP_PLATE]
    }

    def 'a chain longer than the depth cap is truncated with chainComplete false'() {
        given:
        List<String> chain = (0..11).collect { int i -> "018fd849-2a5c-7ccc-8a0c-121212121212~jade-tipi-org~dev~typ~t${i}" as String }
        (0..11).each { int i ->
            Map properties = [name: "t${i}" as String]
            if (i < 11) {
                properties.parent_type_id = chain[i + 1]
            }
            mongoTemplate.findById(chain[i], Map.class, 'typ') >> Mono.just(typRoot(chain[i], properties))
        }
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.empty()

        when:
        TypeEffectivePropertiesRecord record = service.findEffectiveProperties(chain[0]).block()

        then:
        !record.chainComplete
        record.typeChain.size() == TypeEffectivePropertiesReadService.MAX_TYPE_INHERITANCE_DEPTH
    }

    def 'a missing subject type resolves empty for the controller 404'() {
        given:
        mongoTemplate.findById(TYP_PLATE96, Map.class, 'typ') >> Mono.empty()

        expect:
        service.findEffectiveProperties(TYP_PLATE96).block() == null
    }

    def 'a blank type id is rejected'() {
        when:
        service.findEffectiveProperties(' ')

        then:
        thrown(IllegalArgumentException)
    }
}
