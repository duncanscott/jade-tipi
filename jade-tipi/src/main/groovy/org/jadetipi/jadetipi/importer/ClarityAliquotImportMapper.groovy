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

import groovy.util.logging.Slf4j

import java.util.function.BiFunction

/**
 * Pure mapper for the clarity aliquot import slice (TASK-059;
 * docs/architecture/bulk-import-design.md): clarity documents →
 * {@link MappedImportMessage}s. Transaction identity, message UUIDs, id
 * minting, Kafka, and queue state stay with the caller, which supplies an
 * id resolver {@code idFor(key, collection)} over import keys — queue
 * keys ({@code artifacts_2-79367}, {@code containers_27-8546},
 * {@code processes_24-35613}), the synthetic bootstrap {@code type:*}
 * keys, and per-link {@code link:*} keys. The resolver returns the
 * recorded id for keys already emitted (references), and mints a
 * message-UUID-form id — suffix {@code clarity_} + the sanitized key —
 * for keys owned by the current mapping.
 *
 * <p>Artifacts map by {@code output-type}: Analyte → {@code ent},
 * ResultFile → {@code fil}, anything else → {@code ent} (logged). An
 * artifact with a location also yields a positioned {@code contents} link
 * from its container — the link-type <em>name</em> is {@code contents},
 * so the existing contents read views resolve it. A process maps to
 * {@code prc} with {@code output_input} built from the source
 * {@code input-output-map} (open contribution objects, empty this slice),
 * one {@code procedure_input} link per distinct input, and one
 * {@code produced_by} link per output — ent and fil outputs alike
 * (endpoint collections are unconstrained by protocol).
 */
@Slf4j
class ClarityAliquotImportMapper {

    static final String SOURCE = 'clarity'

    static final String KEY_TYPE_PROCEDURE_AC = 'type:procedure:ac_sample_aliquot_creation'
    static final String KEY_TYPE_ANALYTE = 'type:entity:clarity_analyte'
    static final String KEY_TYPE_RESULT_FILE = 'type:file:clarity_result_file'
    static final String KEY_TYPE_CONTAINER = 'type:location:clarity_container'
    static final String KEY_TYPE_LINK_CONTENTS = 'type:link:contents'
    static final String KEY_TYPE_LINK_PROCEDURE_INPUT = 'type:link:procedure_input'
    static final String KEY_TYPE_LINK_PRODUCED_BY = 'type:link:produced_by'

    static final List<String> BOOTSTRAP_KEYS = List.of(
            KEY_TYPE_PROCEDURE_AC, KEY_TYPE_ANALYTE, KEY_TYPE_RESULT_FILE,
            KEY_TYPE_CONTAINER, KEY_TYPE_LINK_CONTENTS,
            KEY_TYPE_LINK_PROCEDURE_INPUT, KEY_TYPE_LINK_PRODUCED_BY)

    static final String OUTPUT_TYPE_ANALYTE = 'Analyte'
    static final String OUTPUT_TYPE_RESULT_FILE = 'ResultFile'

    // plain Strings, never GStrings: these values reach JSON payloads, and
    // Jackson serializes a GString as a bean, not text
    static String artifactKey(String limsid) { return 'artifacts_' + limsid }
    static String containerKey(String limsid) { return 'containers_' + limsid }
    static String processKey(String limsid) { return 'processes_' + limsid }

    /** Convention-conformant id suffix for any import key. */
    static String suffixFor(String key) {
        return 'clarity_' + key.toLowerCase().replaceAll('[^a-z0-9_-]', '_')
    }

    /** The ent-or-fil collection an artifact document maps to. */
    static String artifactCollection(Map<String, Object> doc) {
        Map json = (doc.get('json') ?: [:]) as Map
        return OUTPUT_TYPE_RESULT_FILE == json.get('output-type') ? 'fil' : 'ent'
    }

    /** One typ/link-type declaration per bootstrap key. */
    MappedImportMessage mapBootstrapType(String key, BiFunction<String, String, String> idFor) {
        String id = idFor.apply(key, 'typ')
        switch (key) {
            case KEY_TYPE_PROCEDURE_AC:
                return message('typ', [
                        kind       : 'procedure_type',
                        id         : id,
                        name       : 'ac_sample_aliquot_creation',
                        description: 'Clarity process type: AC Sample Aliquot Creation'
                ])
            case KEY_TYPE_ANALYTE:
                return message('typ', [
                        id         : id,
                        name       : 'clarity_analyte',
                        description: 'Clarity analyte artifact'
                ])
            case KEY_TYPE_RESULT_FILE:
                return message('typ', [
                        id         : id,
                        name       : 'clarity_result_file',
                        description: 'Clarity result-file artifact'
                ])
            case KEY_TYPE_CONTAINER:
                return message('typ', [
                        id         : id,
                        name       : 'clarity_container',
                        description: 'Clarity container'
                ])
            case KEY_TYPE_LINK_CONTENTS:
                return message('typ', [
                        kind                     : 'link_type',
                        id                       : id,
                        name                     : 'contents',
                        description              : 'containment between a clarity container and its contents',
                        left_role                : 'container',
                        right_role               : 'content',
                        left_to_right_label      : 'contains',
                        right_to_left_label      : 'contained_by',
                        allowed_left_collections : ['loc'],
                        allowed_right_collections: ['loc', 'ent', 'fil']
                ])
            case KEY_TYPE_LINK_PROCEDURE_INPUT:
                return message('typ', [
                        kind                     : 'link_type',
                        id                       : id,
                        name                     : 'procedure_input',
                        description              : 'an input consumed by a performed procedure',
                        left_role                : 'procedure',
                        right_role               : 'input',
                        left_to_right_label      : 'consumed',
                        right_to_left_label      : 'consumed_by',
                        allowed_left_collections : ['prc'],
                        allowed_right_collections: ['ent', 'fil']
                ])
            case KEY_TYPE_LINK_PRODUCED_BY:
                return message('typ', [
                        kind                     : 'link_type',
                        id                       : id,
                        name                     : 'produced_by',
                        description              : 'an output and the performed procedure that created it',
                        left_role                : 'output',
                        right_role               : 'procedure',
                        left_to_right_label      : 'produced_by',
                        right_to_left_label      : 'produced',
                        allowed_left_collections : ['ent', 'fil'],
                        allowed_right_collections: ['prc']
                ])
            default:
                throw new IllegalArgumentException("Unknown bootstrap key: ${key}")
        }
    }

    List<MappedImportMessage> mapContainer(Map<String, Object> doc,
                                           BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        Map<String, Object> properties = [
                source_kind   : 'clarity_container',
                clarity_limsid: limsid
        ] as Map<String, Object>
        putIfPresent(properties, 'name', json.get('name'))
        putIfPresent(properties, 'container_type', (json.get('type') as Map)?.get('name'))
        putIfPresent(properties, 'occupied_wells', json.get('occupied-wells'))
        putIfPresent(properties, 'state', json.get('state'))
        return [message('loc', [
                id        : idFor.apply(containerKey(limsid), 'loc'),
                type_id   : idFor.apply(KEY_TYPE_CONTAINER, 'typ'),
                properties: properties,
                links     : [:]
        ])]
    }

    /** Artifact → ent or fil root, plus a positioned contents link when located. */
    List<MappedImportMessage> mapArtifact(Map<String, Object> doc,
                                          BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        String outputType = json.get('output-type') as String
        String collection = artifactCollection(doc)
        String typeKey = collection == 'fil' ? KEY_TYPE_RESULT_FILE : KEY_TYPE_ANALYTE
        if (collection == 'ent' && OUTPUT_TYPE_ANALYTE != outputType) {
            log.info('Clarity artifact with unmapped output-type imported as ent: limsid={}, outputType={}',
                    limsid, outputType)
        }
        String artifactId = idFor.apply(artifactKey(limsid), collection)
        Map<String, Object> properties = [
                source_kind   : 'clarity_artifact',
                clarity_limsid: limsid
        ] as Map<String, Object>
        putIfPresent(properties, 'name', json.get('name'))
        putIfPresent(properties, 'output_type', outputType)
        putIfPresent(properties, 'qc_flag', json.get('qc-flag'))
        putIfPresent(properties, 'sample_limsid', (json.get('sample') as Map)?.get('limsid'))

        List<MappedImportMessage> messages = [message(collection, [
                id        : artifactId,
                type_id   : idFor.apply(typeKey, 'typ'),
                properties: properties,
                links     : [:]
        ])]

        Map location = json.get('location') as Map
        String containerLimsid = (location?.get('container') as Map)?.get('limsid')
        if (containerLimsid) {
            Map<String, Object> linkData = [
                    id     : idFor.apply("link:contents:${limsid}".toString(), 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_CONTENTS, 'typ'),
                    left   : idFor.apply(containerKey(containerLimsid), 'loc'),
                    right  : artifactId
            ] as Map<String, Object>
            Object well = location.get('value')
            if (well != null) {
                linkData.put('properties', [position: [kind: 'clarity_well', label: well]])
            }
            messages.add(message('lnk', linkData))
        }
        return messages
    }

    /** Process → prc with output_input, procedure_input links, produced_by links. */
    List<MappedImportMessage> mapProcess(Map<String, Object> doc,
                                         BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        String processId = idFor.apply(processKey(limsid), 'prc')

        List iom = (json.get('input-output-map') ?: []) as List
        Map<String, Map<String, Object>> outputInput = new LinkedHashMap<>()
        Set<String> inputLimsids = new LinkedHashSet<>()
        Set<String> outputLimsids = new LinkedHashSet<>()
        iom.each { Object entry ->
            Map mapping = entry as Map
            String outLimsid = (mapping.get('output') as Map)?.get('limsid')
            String inLimsid = (mapping.get('input') as Map)?.get('limsid')
            if (outLimsid == null || inLimsid == null) {
                log.warn('Clarity process input-output-map entry missing an endpoint, skipped: process={}', limsid)
                return
            }
            String outId = idFor.apply(artifactKey(outLimsid), 'ent')
            String inId = idFor.apply(artifactKey(inLimsid), 'ent')
            outputInput.computeIfAbsent(outId, { new LinkedHashMap<String, Object>() })
                    .put(inId, [:] as Map<String, Object>)
            inputLimsids.add(inLimsid)
            outputLimsids.add(outLimsid)
        }

        Map<String, Object> properties = [
                source_kind   : 'clarity_process',
                clarity_limsid: limsid,
                // clarity element text lands under the '' key of its JSON object
                process_type  : ((json.get('type') as Map)?.get('') ?: 'AC Sample Aliquot Creation')
        ] as Map<String, Object>
        putIfPresent(properties, 'date_run', json.get('date-run'))
        Map technician = json.get('technician') as Map
        if (technician) {
            String name = [technician.get('first-name'), technician.get('last-name')]
                    .findAll { it }.join(' ')
            putIfPresent(properties, 'technician', name ?: null)
        }

        List<MappedImportMessage> messages = [message('prc', [
                id          : processId,
                type_id     : idFor.apply(KEY_TYPE_PROCEDURE_AC, 'typ'),
                properties  : properties,
                links       : [:],
                output_input: outputInput
        ])]
        inputLimsids.each { String inLimsid ->
            messages.add(message('lnk', [
                    id     : idFor.apply("link:procedure_input:${limsid}:${inLimsid}".toString(), 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_PROCEDURE_INPUT, 'typ'),
                    left   : processId,
                    right  : idFor.apply(artifactKey(inLimsid), 'ent')
            ]))
        }
        outputLimsids.each { String outLimsid ->
            messages.add(message('lnk', [
                    id     : idFor.apply("link:produced_by:${outLimsid}".toString(), 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_PRODUCED_BY, 'typ'),
                    left   : idFor.apply(artifactKey(outLimsid), 'ent'),
                    right  : processId
            ]))
        }
        return messages
    }

    private static MappedImportMessage message(String collection, Map<String, Object> data) {
        return new MappedImportMessage(collection: collection, action: 'create', data: data)
    }

    private static void putIfPresent(Map<String, Object> properties, String key, Object value) {
        if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
            properties.put(key, value)
        }
    }
}
