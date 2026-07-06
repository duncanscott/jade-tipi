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
            "jade-itest-org~import~018fd849-9a01-7111-8a01-919191919191~${collection}~" +
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

    def 'an Analyte artifact maps to a typed ent root with a positioned contents link'() {
        when:
        List<MappedImportMessage> messages = mapper.mapArtifact(fixture('artifact_2-79367'), idFor)

        then:
        messages.size() == 2
        MappedImportMessage ent = messages[0]
        ent.collection == 'ent'
        ent.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_ANALYTE]
        (ent.data.properties as Map).output_type == 'Analyte'
        (ent.data.properties as Map).sample_limsid == 'DES439A6'

        and: 'the contents link joins the container to the artifact with the source well'
        MappedImportMessage lnk = messages[1]
        lnk.collection == 'lnk'
        lnk.data.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_LINK_CONTENTS]
        lnk.data.left == minted[ClarityAliquotImportMapper.containerKey('27-8546')]
        lnk.data.right == ent.data.id
        ((lnk.data.properties as Map).position as Map).label == '1:1'
        snakeCaseKeys(ent.data)
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
