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
            ('018fd849-9a02-7222-8a02-929292929292~jade-itest-org~import~' + collection + '~' +
                    EspEntityImportMapper.suffixFor(key)).toString()
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

    def 'isClarityContainerCandidate accepts clean container limsids only (TASK-071)'() {
        expect:
        EspEntityImportMapper.isClarityContainerCandidate(
                [class_name: 'Container', name: name] as Map<String, Object>) == expected

        where:
        name              || expected
        '27-279088'       || true
        '27-624292'       || true
        '27-810364_X'     || false   // re-plate suffix — esp-native, mint new
        'Guadelou'        || false   // free-text rack name
        'TR-A0192'        || false   // tube-rack namespace
        null              || false
    }

    def 'a Sample-class doc is never a clarity container candidate even with a limsid-shaped name'() {
        expect:
        !EspEntityImportMapper.isClarityContainerCandidate(
                [class_name: 'Sample', name: '27-66958'] as Map<String, Object>)
        EspEntityImportMapper.clarityContainerKey('27-279088') == 'containers_27-279088'
    }

    def 'an overlay container reuses the (pre-resolved) root id and emits NO duplicate create (TASK-071)'() {
        given: 'the driver pre-seeded the resolver to the clarity id and flagged the doc as an overlay'
        String clarityRootId = '018fd849-c0c0-7000-8000-000000000001~lbl-gov~jgi-pps~loc~containers_27-279088'
        BiFunction<String, String, String> overlayIdFor = { String key, String collection ->
            key == CONTAINER_UUID ? clarityRootId : minted.computeIfAbsent(key, {
                "018fd849-9a02-7222-8a02-929292929292~jade-itest-org~import~${collection}~" +
                        EspEntityImportMapper.suffixFor(key)
            })
        } as BiFunction<String, String, String>

        when:
        List<MappedImportMessage> messages = mapper.mapEntity([
                _id: CONTAINER_UUID, uuid: CONTAINER_UUID,
                class_name: 'Container', type_name: '96W Plate',
                name: '27-279088', barcode: '27-279088',
                (EspEntityImportMapper.PRECEDENCE_OVERLAY): Boolean.TRUE
        ] as Map<String, Object>, overlayIdFor)

        then: 'no loc/root create is emitted for the overlay container (the clarity root already exists)'
        messages.every { it.collection != 'loc' }
        !messages.any { it.data.id == clarityRootId && it.data.containsKey('properties') }
    }

    def 'an overlay entity still emits its begat and contents links onto the reused root (TASK-071)'() {
        given:
        String reused = 'clarity~root~id'
        BiFunction<String, String, String> overlayIdFor = { String key, String collection ->
            key == ENTITY_UUID ? reused : minted.computeIfAbsent(key, {
                "018fd849-9a02-7222-8a02-929292929292~jade-itest-org~import~${collection}~" +
                        EspEntityImportMapper.suffixFor(key)
            })
        } as BiFunction<String, String, String>

        when:
        List<MappedImportMessage> messages = mapper.mapEntity([
                _id: ENTITY_UUID, uuid: ENTITY_UUID, class_name: 'Sample', type_name: 'Nucleic Acid',
                name: 'NA1', parents: [[uuid: PARENT_UUID]],
                (EspEntityImportMapper.PRECEDENCE_OVERLAY): Boolean.TRUE
        ] as Map<String, Object>, overlayIdFor)

        then: 'no ent create, but the begat link is present pointing at the reused root'
        messages.every { it.collection == 'lnk' }
        messages.find { it.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_BEGAT] }.data.right == reused
    }

    def 'reconstructWorkflowInstances keeps only lab workflows this carrier owns (TASK-070)'() {
        given: 'a SOW Item task carrying SOW QC + Aliquot Creation sheets (both input_types include SOW Item)'
        EspWorkflowConfigService config = new EspWorkflowConfigService()
        Map<String, Object> sow = [
                uuid: 'sow-1', class_name: 'SOW Item', type_name: 'SOW Item',
                sample_sheets: [
                        [workflow_name: 'SOW QC', workflow_uuid: 'wf-qc', sample_sheet_uuid: 'ss-qc',
                         sample_sheet_start_time: '2026-06-12T17:47:00Z', sample_sheet_end_time: '2026-06-12T18:16:00Z'],
                        [workflow_name: 'Aliquot Creation', workflow_uuid: 'wf-ac', sample_sheet_uuid: 'ss-ac',
                         sample_sheet_start_time: '2026-06-15T19:30:00Z', sample_sheet_end_time: '2026-06-17T15:45:00Z'],
                ]
        ] as Map<String, Object>

        when:
        List<Map<String, Object>> instances = EspEntityImportMapper.reconstructWorkflowInstances(sow, config)

        then:
        instances*.workflow_name.toSet() == ['SOW QC', 'Aliquot Creation'] as Set
        Map ac = instances.find { it.workflow_name == 'Aliquot Creation' }
        ac.procedure_key == 'ss-ac'
        ac.start == '2026-06-15T19:30:00Z'
        ac.end == '2026-06-17T15:45:00Z'
    }

    def 'an output entity does not reconstruct a workflow it only participates in (owner dedup, TASK-070)'() {
        given: 'an Aliquot carrying Aliquot Creation (input SOW Item) and Illumina Library Creation (input Aliquot)'
        EspWorkflowConfigService config = new EspWorkflowConfigService()
        Map<String, Object> aliquot = [
                uuid: 'aq-1', class_name: 'Sample', type_name: 'Aliquot',
                sample_sheets: [
                        [workflow_name: 'Aliquot Creation', workflow_uuid: 'wf-ac', sample_sheet_uuid: 'ss-ac',
                         sample_sheet_start_time: '2026-06-15T19:30:00Z', sample_sheet_end_time: '2026-06-17T15:45:00Z'],
                        [workflow_name: 'Illumina Library Creation', workflow_uuid: 'wf-ill', sample_sheet_uuid: 'ss-ill',
                         sample_sheet_start_time: '2026-06-21T03:41:00Z', sample_sheet_end_time: '2026-06-25T17:32:00Z'],
                ]
        ] as Map<String, Object>

        expect: 'only Illumina Library Creation (whose input type is Aliquot) is owned here'
        EspEntityImportMapper.reconstructWorkflowInstances(aliquot, config)*.workflow_name ==
                ['Illumina Library Creation']
    }

    def 'administrative workflows are not reconstructed as procedures (TASK-070)'() {
        given:
        EspWorkflowConfigService config = new EspWorkflowConfigService()
        Map<String, Object> pm = [
                uuid: 'pm-1', class_name: 'SOW Item', type_name: 'PM SOW Item',
                sample_sheets: [[workflow_name: 'SOW Item Edit', workflow_uuid: 'wf-e', sample_sheet_uuid: 'ss-e',
                                 sample_sheet_start_time: 't', sample_sheet_end_time: 't']]
        ] as Map<String, Object>

        expect:
        EspEntityImportMapper.reconstructWorkflowInstances(pm, config).isEmpty()
    }

    def 'a SOW Item maps to a tsk with task_input to its biological parent, and emits no procedure (TASK-072)'() {
        given: 'a SOW Item with a Nucleic Acid parent (input) and a Sequencing Project parent (not an input)'
        Map<String, Object> sow = [
                uuid   : 'sow-1', class_name: 'SOW Item', type_name: 'SOW Item',
                parents: [[uuid: 'na-1', type_name: 'Nucleic Acid'],
                          [uuid: 'sp-1', type_name: 'Sequencing Project']]
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapEntity(sow, idFor)

        then: 'the root is a tsk'
        MappedImportMessage root = messages.find { it.data.containsKey('properties') && it.data.id == minted['sow-1'] }
        root.collection == 'tsk'

        and: 'task_input links the tsk to the Nucleic Acid input, never the Sequencing Project'
        List<MappedImportMessage> taskInputs = messages.findAll {
            it.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_TASK_INPUT]
        }
        taskInputs*.data.right == [minted['na-1']]

        and: 'procedures are NOT emitted per entity — the driver aggregates them by workflow instance (TASK-072)'
        messages.every { it.collection != 'prc' }
    }

    def 'workflowInstanceProcedure aggregates a run into one prc with an inputs map and links (TASK-072)'() {
        given: 'an accumulated workflow instance with two task-carried inputs and one output'
        Map<String, Object> wi = [
                workflow_instance_uuid: 'wi-ac', workflow_name: 'Aliquot Creation',
                started               : '2026-06-15T19:30:00Z', ended: '2026-06-17T15:45:00Z',
                inputs                : ['na-1': 'sow-1', 'na-2': 'sow-2'],
                tasks                 : ['sow-1', 'sow-2'] as LinkedHashSet,
                outputs               : ['aq-1'] as LinkedHashSet
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.workflowInstanceProcedure(wi, idFor)

        then: 'one prc typed by the workflow, tagged with the workflow-instance id'
        MappedImportMessage prc = messages.find { it.collection == 'prc' }
        prc.data.type_id == minted[EspEntityImportMapper.procedureTypeKey('Aliquot Creation')]
        (prc.data.properties as Map).esp_workflow == 'Aliquot Creation'
        (prc.data.properties as Map).esp_workflow_instance == 'wi-ac'

        and: 'the inputs map carries every input, each back-referencing its delivering task'
        Map inputs = prc.data.inputs as Map
        inputs.keySet() == [minted['na-1'], minted['na-2']] as Set
        inputs[minted['na-1']] == [task_id: minted['sow-1']]
        inputs[minted['na-2']] == [task_id: minted['sow-2']]

        and: 'output_input maps the output to all of the run inputs'
        (prc.data.output_input as Map)[minted['aq-1']].keySet() == [minted['na-1'], minted['na-2']] as Set

        and: 'one fulfills per task, one procedure_input per input, one produced_by per output'
        messages.findAll { it.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_FULFILLS] }.size() == 2
        messages.findAll { it.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_PROCEDURE_INPUT] }.size() == 2
        messages.findAll { it.data.type_id == minted[EspEntityImportMapper.KEY_TYPE_LINK_PRODUCED_BY] }.size() == 1
    }

    def 'suffixFor caps a pathologically long key at 128 characters with a stable, distinct hash tail'() {
        given: 'two long keys that agree beyond the truncation point'
        String base = 'type:esp:Container:' + ('Very Long Type Name ' * 10)
        String keyA = base + 'variant-alpha'
        String keyB = base + 'variant-beta'

        when:
        String a = EspEntityImportMapper.suffixFor(keyA)
        String b = EspEntityImportMapper.suffixFor(keyB)

        then: 'both fit the 128-character suffix limit and stay well-formed'
        a.length() <= EspEntityImportMapper.MAX_SUFFIX_LENGTH
        b.length() <= EspEntityImportMapper.MAX_SUFFIX_LENGTH
        a.startsWith('container_')
        !a.matches('.*[_-]{2,}.*') && !a.matches('.*[_-]$')

        and: 'the hash tail keeps distinct keys distinct and the result deterministic'
        a != b
        a == EspEntityImportMapper.suffixFor(keyA)

        and: 'a short key is untouched'
        EspEntityImportMapper.suffixFor('plain-key') == 'plain-key'
    }

    def 'esp type keys declare dynamic typ roots and the esp link types'() {
        expect: 'the dynamic entity type'
        MappedImportMessage typ = mapper.mapBootstrapType(
                EspEntityImportMapper.typeKey('Sample', 'Nucleic Acid'), idFor)
        typ.collection == 'typ'
        typ.data.name == 'nucleic_acid'
        (typ.data.description as String).contains('Nucleic Acid')

        and: 'esp mints its own contents and begat link types'
        mapper.mapBootstrapType(EspEntityImportMapper.KEY_TYPE_LINK_CONTENTS, idFor)
                .data.name == 'contents'
        mapper.mapBootstrapType(EspEntityImportMapper.KEY_TYPE_LINK_BEGAT, idFor)
                .data.name == 'begat'
    }
}
