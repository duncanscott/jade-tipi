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

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

import java.util.function.BiFunction

/**
 * TASK-059 coverage for the clarity aliquot mapper against real fixture
 * documents (importfixtures/clarity-aliquot/): container → loc, Analyte →
 * ent with a positioned contents link, ResultFile → fil without one, and
 * the process → prc whose output_input keys/values are the resolved
 * artifact ids plus the procedure_input and produced_by links. All ids
 * come through the resolver; payload keys are snake_case.
 */
class ClarityAliquotImportMapperSpec extends Specification {

    static final ObjectMapper JSON = new ObjectMapper()

    ClarityAliquotImportMapper mapper = new ClarityAliquotImportMapper()
    Map<String, String> minted = [:]

    private BiFunction<String, String, String> idFor = { String key, String collection ->
        return minted.computeIfAbsent(key, {
            "018fd849-9a01-7111-8a01-919191919191~jade-itest-org~import~${collection}~" +
                    ClarityAliquotImportMapper.suffixFor(key)
        })
    } as BiFunction<String, String, String>

    private static Map<String, Object> fixture(String name) {
        InputStream stream = ClarityAliquotImportMapperSpec
                .getResourceAsStream("/importfixtures/clarity-aliquot/${name}.json")
        assert stream != null: "missing fixture ${name}"
        return JSON.readValue(stream, Map)
    }

    def 'a container maps to a typed loc root with source-traceability properties'() {
        when:
        List<MappedImportMessage> messages = mapper.mapContainer(fixture('container_27-8546'), idFor)

        then:
        messages.size() == 1
        MappedImportMessage loc = messages[0]
        loc.collection == 'loc'
        loc.action == 'create'
        loc.data.id == minted[ClarityAliquotImportMapper.containerKey('27-8546')]
        loc.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_CONTAINER]
        Map properties = loc.data.properties as Map
        properties.source_kind == 'clarity_container'
        properties.clarity_limsid == '27-8546'
        properties.containsKey('name')

        and: 'every payload key is snake_case'
        snakeCaseKeys(loc.data)
    }

    def 'an Analyte artifact maps to a typed ent root with contents and sample_of links'() {
        when:
        List<MappedImportMessage> messages = mapper.mapArtifact(fixture('artifact_2-79367'), idFor)

        then:
        messages.size() == 3
        MappedImportMessage ent = messages[0]
        ent.collection == 'ent'
        ent.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_ANALYTE]
        (ent.data.properties as Map).output_type == 'Analyte'
        (ent.data.properties as Map).sample_limsids == ['DES439A6']

        and: 'the contents link joins the container to the artifact with the source well'
        MappedImportMessage lnk = messages[1]
        lnk.collection == 'lnk'
        lnk.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_LINK_CONTENTS]
        lnk.data.left == minted[ClarityAliquotImportMapper.containerKey('27-8546')]
        lnk.data.right == ent.data.id
        ((lnk.data.properties as Map).position as Map).label == '1:1'
        snakeCaseKeys(ent.data)

        and: 'the sample_of link joins the artifact to its submitted sample (TASK-067)'
        MappedImportMessage sampleLnk = messages[2]
        sampleLnk.collection == 'lnk'
        sampleLnk.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_LINK_SAMPLE_OF]
        sampleLnk.data.left == ent.data.id
        sampleLnk.data.right == minted[ClarityAliquotImportMapper.sampleKey('DES439A6')]
    }

    def 'a sample maps to a typed ent root with source-traceability properties (TASK-067)'() {
        given: 'a sample document shaped like the live samples_ docs'
        Map<String, Object> doc = [
                _id   : 'samples_DES439A6',
                limsid: 'DES439A6',
                json  : [
                        limsid         : 'DES439A6',
                        name           : 'DES439A6 sample',
                        'date-received': '2016-07-01',
                        submitter      : ['first-name': 'Clarity', 'last-name': 'Migration']
                ]
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapSample(doc, idFor)

        then:
        messages.size() == 1
        MappedImportMessage ent = messages[0]
        ent.collection == 'ent'
        ent.data.id == minted[ClarityAliquotImportMapper.sampleKey('DES439A6')]
        ent.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_SAMPLE]
        Map properties = ent.data.properties as Map
        properties.source_kind == 'clarity_sample'
        properties.clarity_limsid == 'DES439A6'
        properties.date_received == '2016-07-01'
        properties.submitter == 'Clarity Migration'
        snakeCaseKeys(ent.data)
    }

    def 'procedure types are dynamic: any clarity process type maps to its own typ declaration (TASK-067)'() {
        when:
        String key = ClarityAliquotImportMapper.processTypeKey('LP Pool Creation')
        MappedImportMessage msg = mapper.mapBootstrapType(key, idFor)

        then:
        msg.collection == 'typ'
        msg.data.kind == 'procedure_type'
        msg.data.id == minted[key]
        msg.data.name == 'lp_pool_creation'
        (msg.data.description as String).contains('LP Pool Creation')

        and: 'the id suffix sanitizes the raw display name'
        (msg.data.id as String).split('~')[4].matches('[a-z0-9_-]+')
    }

    def 'a ResultFile artifact maps to a typed fil root'() {
        when:
        List<MappedImportMessage> messages = mapper.mapArtifact(fixture('artifact_92-79368'), idFor)

        then:
        MappedImportMessage fil = messages[0]
        fil.collection == 'fil'
        fil.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_RESULT_FILE]
        (fil.data.properties as Map).output_type == 'ResultFile'
    }

    def 'a pooled artifact links every referenced sample (clarity collapses single-element lists) (TASK-068)'() {
        given: 'an unlocated artifact whose sample field is a LIST, as pooled artifacts carry'
        Map<String, Object> doc = [
                _id   : 'artifacts_92-9999',
                limsid: '92-9999',
                json  : [
                        limsid       : '92-9999',
                        name         : 'pooled result',
                        'output-type': 'ResultFile',
                        sample       : [[limsid: 'POOLA1'], [limsid: 'POOLA2']]
                ]
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapArtifact(doc, idFor)

        then: 'the fil root lists both samples and links each'
        (messages[0].data.properties as Map).sample_limsids == ['POOLA1', 'POOLA2']
        List<MappedImportMessage> links = messages.drop(1)
        links.size() == 2
        links*.data.right == [minted[ClarityAliquotImportMapper.sampleKey('POOLA1')],
                              minted[ClarityAliquotImportMapper.sampleKey('POOLA2')]]
    }

    def 'a single-entry input-output-map arrives as a bare object and still maps (TASK-068)'() {
        given: 'clarity XML→JSON collapses one-element lists to the object'
        Map<String, Object> doc = [
                _id   : 'processes_24-9999',
                limsid: '24-9999',
                json  : [
                        limsid            : '24-9999',
                        type              : ['': 'SM Sample Receipt'],
                        'input-output-map': [input: [limsid: 'IN1'], output: [limsid: 'OUT1']]
                ]
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapProcess(doc, idFor)

        then: 'prc + 1 procedure_input + 1 produced_by'
        messages.size() == 3
        (messages[0].data.output_input as Map).size() == 1
        messages[0].data.type_id == minted[ClarityAliquotImportMapper.processTypeKey('SM Sample Receipt')]
    }

    def 'a file-property key declares the ppy definition and registers it on the ResultFile type (TASK-068)'() {
        when:
        String key = ClarityAliquotImportMapper.KEY_FILE_PROPERTY_PREFIX + 'content_location'
        List<MappedImportMessage> messages = mapper.mapFileProperty(key, idFor)

        then: 'a definition create plus an add_property update'
        messages.size() == 2
        messages[0].collection == 'ppy'
        messages[0].action == 'create'
        messages[0].data.kind == 'definition'
        messages[0].data.name == 'content_location'
        messages[0].data.id == minted[key]
        messages[1].collection == 'typ'
        messages[1].action == 'update'
        messages[1].data.operation == 'add_property'
        messages[1].data.id == minted[ClarityAliquotImportMapper.KEY_TYPE_RESULT_FILE]
        messages[1].data.property_id == minted[key]
    }

    def 'a file document maps to property assignments onto the attached artifact root (TASK-068)'() {
        given: 'the attached artifact id is already recorded as a fil root'
        String filId = idFor.apply(ClarityAliquotImportMapper.artifactKey('92-4425141'), 'fil')
        Map<String, Object> doc = [
                _id   : 'files_40-100013',
                limsid: '40-100013',
                json  : [
                        limsid             : '40-100013',
                        'attached-to'      : 'https://jgi-prd.claritylims.com/api/v2/artifacts/92-4425141',
                        'content-location' : 'sftp://host/path/report.xls',
                        'original-name'    : 'report.xls',
                        'original-location': '/tmp/report.xls',
                        'is-published'     : 'false'
                ]
        ] as Map<String, Object>

        when:
        List<MappedImportMessage> messages = mapper.mapFile(doc, idFor)

        then: 'one assignment per present property, targeting the fil root'
        messages.size() == 5
        messages.every { MappedImportMessage m ->
            m.collection == 'ppy' && m.action == 'create' &&
                    m.data.kind == 'assignment' &&
                    m.data.object_id == filId &&
                    m.data.object_collection == 'fil' &&
                    m.data.id == null
        }
        Map<String, Map> bySuffix = messages.collectEntries { MappedImportMessage m ->
            String propertyKey = minted.find { k, v -> v == m.data.property_id }.key
            [propertyKey.substring(ClarityAliquotImportMapper.KEY_FILE_PROPERTY_PREFIX.length()),
             m.data.value as Map]
        }
        bySuffix.content_location == [text: 'sftp://host/path/report.xls']
        bySuffix.original_name == [text: 'report.xls']
        bySuffix.is_published == [boolean: false]
        bySuffix.file_limsid == [text: '40-100013']
    }

    def 'a file without a resolvable attachment maps to nothing (TASK-068)'() {
        expect:
        mapper.mapFile([_id: 'files_x', limsid: 'x', json: [:]] as Map<String, Object>, idFor).isEmpty()
    }

    def 'the process maps to prc with resolved output_input and the provenance links'() {
        given: 'artifact ids already minted (artifacts are always emitted before their process)'
        ['DES439A6PA1', '2-79367', '92-79368', '92-79369'].each {
            idFor.apply(ClarityAliquotImportMapper.artifactKey(it), 'ent')
        }

        when:
        List<MappedImportMessage> messages = mapper.mapProcess(fixture('process_24-35613'), idFor)

        then: 'prc + 1 procedure_input + 3 produced_by'
        messages.size() == 5
        MappedImportMessage prc = messages[0]
        prc.collection == 'prc'
        prc.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_PROCEDURE_AC]
        (prc.data.properties as Map).process_type == 'AC Sample Aliquot Creation'
        (prc.data.properties as Map).date_run == '2016-07-16'

        and: 'output_input keys are the three resolved output ids, each fed by the one input'
        Map outputInput = prc.data.output_input as Map
        String inputId = minted[ClarityAliquotImportMapper.artifactKey('DES439A6PA1')]
        outputInput.keySet() == ['2-79367', '92-79368', '92-79369']
                .collect { minted[ClarityAliquotImportMapper.artifactKey(it)] } as Set
        outputInput.values().every { (it as Map).keySet() == [inputId] as Set }

        and: 'the procedure_input link consumes the input'
        List<MappedImportMessage> links = messages.drop(1)
        MappedImportMessage inputLink = links.find {
            it.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_LINK_PROCEDURE_INPUT]
        }
        inputLink.data.left == prc.data.id
        inputLink.data.right == inputId

        and: 'each output is produced_by the process, ent and fil outputs alike'
        List<MappedImportMessage> producedBy = links.findAll {
            it.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_LINK_PRODUCED_BY]
        }
        producedBy.size() == 3
        producedBy.every { it.data.right == prc.data.id }

        and: 'all minted ids are convention-conformant (lowercase suffixes, no illegal chars)'
        minted.values().every { String id ->
            id.split('~')[4].matches('[a-z0-9_-]+')
        }
    }

    def 'every bootstrap key maps to a type declaration'() {
        expect:
        ClarityAliquotImportMapper.BOOTSTRAP_KEYS.every { String key ->
            MappedImportMessage msg = mapper.mapBootstrapType(key, idFor)
            msg.collection == 'typ' && msg.data.id == minted[key] && snakeCaseKeys(msg.data)
        }
    }

    private static boolean snakeCaseKeys(Map data) {
        return data.keySet().every { Object key -> (key as String).matches('[a-z][a-z0-9_]*') }
    }
}
