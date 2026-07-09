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
package org.jadetipi.jadetipi.importer

import spock.lang.Specification

import java.util.function.BiFunction

/**
 * TASK-069 coverage for the esp-entity mapper: Sample-class documents →
 * typed ent roots with begat links and (when the driver injected the
 * well) a positioned contents link; Container-class documents → loc
 * roots; dynamic (class, type) typ declarations plus esp's own contents
 * and begat link types; the variables bag preserved verbatim.
 */
class EspEntityImportMapperSpec extends Specification {

    static final String ENTITY_UUID = '019a3ea6-c704-7d47-b951-65268a951c9e'
    static final String PARENT_UUID = '019a3ea4-4e3b-7191-bcfd-ce08d1d49c98'
    static final String CONTAINER_UUID = '019a3ea7-8f4b-771c-9f58-8c833082e9b6'

    EspEntityImportMapper mapper = new EspEntityImportMapper()
    Map<String, String> minted = [:]

    private BiFunction<String, String, String> idFor = { String key, String collection ->
        return minted.computeIfAbsent(key, {
            "jade-itest-org~import~018fd849-9a02-7222-8a02-929292929292~${collection}~" +
                    EspEntityImportMapper.suffixFor(key)
        })
    } as BiFunction<String, String, String>

    def 'a Sample-class document maps to a typed ent root with begat and positioned contents links'() {
        given: 'a contained entity with one begat parent and a driver-injected well'
        Map<String, Object> doc = [
                _id       : ENTITY_UUID,
                uuid      : ENTITY_UUID,
                class_name: 'Sample',
                type_name : 'Nucleic Acid',
                name      : 'NA00049569',
                barcode   : 'NA00049569',
                parents   : [[relationship: 'begat', uuid: PARENT_UUID, type_name: 'SOW Item']],
                container : [uuid: CONTAINER_UUID, name: '27-279088'],
                variables : ['Concentration (ng/ul)': '12.5', 'QC Result': 'Pass'],
                (EspEntityImportMapper.IMPORT_WELL): 'A2'
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapEntity(doc, idFor)

        then: 'ent root with verbatim variables and source traceability'
        messages.size() == 3
        MappedImportMessage ent = messages[0]
        ent.collection == 'ent'
        ent.data.id == minted[ENTITY_UUID]
        ent.data.type_id == minted[EspEntityImportMapper.typeKey('Sample', 'Nucleic Acid')]
        Map properties = ent.data.properties as Map
        properties.source_kind == 'esp_entity'
        properties.esp_uuid == ENTITY_UUID
        properties.esp_class == 'Sample'
        properties.esp_type == 'Nucleic Acid'
        (properties.variables as Map).concentration_ng_ul == '12.5'
        (properties.variable_names as Map).concentration_ng_ul == 'Concentration (ng/ul)'

        and: 'the begat link joins parent to child'
        MappedImportMessage begat = messages[1]
        begat.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_BEGAT]
        begat.data.left == minted[PARENT_UUID]
        begat.data.right == ent.data.id

        and: 'the contents link carries the injected well position'
        MappedImportMessage contents = messages[2]
        contents.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_CONTENTS]
        contents.data.left == minted[CONTAINER_UUID]
        contents.data.right == ent.data.id
        ((contents.data.properties as Map).position as Map).label == 'A2'
    }

    def 'a Container-class document maps to a loc root'() {
        when:
        List<MappedImportMessage> messages = mapper.mapEntity([
                _id: CONTAINER_UUID, uuid: CONTAINER_UUID,
                class_name: 'Container', type_name: '96W Plate',
                name: '27-279088', barcode: '27-279088'
        ] as Map<String, Object>, idFor)

        then:
        messages.size() == 1
        messages[0].collection == 'loc'
        messages[0].data.type_id == minted[EspEntityImportMapper.typeKey('Container', '96W Plate')]
        (messages[0].data.properties as Map).esp_type == '96W Plate'
    }

    def 'esp type keys declare dynamic typ roots and the esp link types'() {
        expect: 'the dynamic entity type'
        MappedImportMessage typ = mapper.mapBootstrapType(
                EspEntityImportMapper.typeKey('Sample', 'Nucleic Acid'), idFor)
        typ.collection == 'typ'
        typ.data.name == 'esp_nucleic_acid'
        (typ.data.description as String).contains('Nucleic Acid')

        and: 'esp mints its own contents and begat link types'
        mapper.mapBootstrapType(EspEntityImportMapper.KEY_TYPE_LINK_CONTENTS, idFor)
                .data.name == 'contents'
        mapper.mapBootstrapType(EspEntityImportMapper.KEY_TYPE_LINK_BEGAT, idFor)
                .data.name == 'begat'
    }
}
