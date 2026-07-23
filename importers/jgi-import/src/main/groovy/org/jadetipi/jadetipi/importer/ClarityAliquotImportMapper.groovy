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
import org.springframework.stereotype.Service

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
 *
 * <p>Stateless; a bean so the drive path (TASK-066) can inject it.
 */
@Slf4j
@Service
class ClarityAliquotImportMapper {

    static final String SOURCE = 'clarity'

    static final String PROCEDURE_TYPE_KEY_PREFIX = 'type:procedure:'
    static final String KEY_TYPE_ANALYTE = 'type:entity:clarity_analyte'
    static final String KEY_TYPE_SAMPLE = 'type:entity:clarity_sample'
    static final String KEY_TYPE_RESULT_FILE = 'type:file:clarity_result_file'
    static final String KEY_TYPE_CONTAINER = 'type:location:clarity_container'
    static final String KEY_TYPE_LINK_CONTENTS = 'type:link:contents'
    static final String KEY_TYPE_LINK_PROCEDURE_INPUT = 'type:link:procedure_input'
    static final String KEY_TYPE_LINK_PRODUCED_BY = 'type:link:produced_by'
    static final String KEY_TYPE_LINK_SAMPLE_OF = 'type:link:sample_of'

    /**
     * The static bootstrap types. Procedure types are NOT here: phase 2
     * generalizes them — one per clarity process type, enqueued
     * dynamically from each planned process document's
     * {@code json.type['']} name via {@link #processTypeKey}.
     */
    static final List<String> BOOTSTRAP_KEYS = List.of(
            KEY_TYPE_ANALYTE, KEY_TYPE_SAMPLE, KEY_TYPE_RESULT_FILE,
            KEY_TYPE_CONTAINER, KEY_TYPE_LINK_CONTENTS,
            KEY_TYPE_LINK_PROCEDURE_INPUT, KEY_TYPE_LINK_PRODUCED_BY,
            KEY_TYPE_LINK_SAMPLE_OF)

    static final String OUTPUT_TYPE_ANALYTE = 'Analyte'
    static final String OUTPUT_TYPE_RESULT_FILE = 'ResultFile'

    /**
     * File metadata lands on the EXISTING fil root (the ResultFile
     * artifact) as object-targeted property assignments (TASK-068) — the
     * root is create-only, so assignments are the sanctioned route, and
     * this is the import's first live use of the TASK-061 value machinery.
     */
    static final String KEY_FILE_PROPERTY_PREFIX = 'property:file:'
    static final List<String> FILE_PROPERTY_KEYS = List.of(
            KEY_FILE_PROPERTY_PREFIX + 'content_location',
            KEY_FILE_PROPERTY_PREFIX + 'original_name',
            KEY_FILE_PROPERTY_PREFIX + 'original_location',
            KEY_FILE_PROPERTY_PREFIX + 'is_published',
            KEY_FILE_PROPERTY_PREFIX + 'file_limsid')

    // plain Strings, never GStrings: these values reach JSON payloads, and
    // Jackson serializes a GString as a bean, not text
    static String artifactKey(String limsid) { return 'artifacts_' + limsid }
    static String containerKey(String limsid) { return 'containers_' + limsid }
    static String processKey(String limsid) { return 'processes_' + limsid }
    static String sampleKey(String limsid) { return 'samples_' + limsid }
    static String fileKey(String limsid) { return 'files_' + limsid }

    /**
     * Clarity's XML→JSON collapses a single-element list to the bare
     * object (seen live on {@code input-output-map} and {@code sample});
     * normalize before iterating (TASK-068).
     */
    static List asImportList(Object value) {
        if (value == null) {
            return []
        }
        return value instanceof List ? (List) value : [value]
    }

    /** Every sample limsid an artifact references (pooled artifacts carry a list). */
    static List<String> sampleLimsids(Map json) {
        return asImportList(json?.get('sample')).findResults { Object ref ->
            ref instanceof Map ? ((Map) ref).get('limsid') as String : null
        } as List<String>
    }

    /**
     * The import key for one clarity process type's procedure-type
     * declaration. Carries the RAW display name (dedup identity and
     * display fidelity); {@link #suffixFor} sanitizes it for the id.
     */
    static String processTypeKey(String processTypeName) {
        return PROCEDURE_TYPE_KEY_PREFIX + processTypeName
    }

    /** Kept for the original slice's fixture (now just one of the 51). */
    static final String KEY_TYPE_PROCEDURE_AC = processTypeKey('AC Sample Aliquot Creation')

    /** Suffix segments are at most 128 characters (director ruling 2026-07-20). */
    static final int MAX_SUFFIX_LENGTH = 128

    /**
     * A CLEAN id suffix derived from the import key's grammar (director ruling
     * 2026-07-20: suffixes carry no dataset-specific bloat). Universal TYPES
     * are kind-qualified with no source branding — a clarity type
     * {@code type:entity:clarity_analyte} becomes {@code entity_analyte}, a
     * container {@code location_container}, a link {@code link_contents}, a
     * process type {@code procedure_<name>}. A source-specific PROPERTY carries
     * the source as a trailing qualifier — {@code property:file:content_location}
     * becomes {@code content_location_clarity}. Record INSTANCES drop the
     * source tag entirely — {@code artifacts_2-79367} stays as-is. The internal
     * key stays fully source-namespaced for dedup; only this readable suffix is
     * cleaned. A pathologically long suffix is capped (safe: message-UUID-form
     * ids, so the suffix never carries uniqueness; the raw queue key does).
     */
    static String suffixFor(String key) {
        String suffix
        if (key.startsWith('type:procedure:')) {                    // process type
            suffix = 'procedure_' + snake(key.substring('type:procedure:'.length()))
        } else if (key.startsWith('type:link:')) {                  // link type
            suffix = 'link_' + snake(key.substring('type:link:'.length()))
        } else if (key.startsWith('type:')) {                       // type:<kind>:clarity_<name>
            String rest = key.substring('type:'.length())
            int colon = rest.indexOf(':')
            String kind = colon > 0 ? rest.substring(0, colon) : rest
            String name = colon > 0 ? rest.substring(colon + 1) : rest
            if (name.startsWith('clarity_')) {
                name = name.substring('clarity_'.length())
            }
            suffix = snake(kind) + '_' + snake(name)
        } else if (key.startsWith(KEY_FILE_PROPERTY_PREFIX)) {       // property:file:<name> — source-specific
            suffix = snake(key.substring(KEY_FILE_PROPERTY_PREFIX.length())) + '_clarity'
        } else if (key.startsWith('link:')) {                       // link instance: <relationship>_<endpoints>
            suffix = snake(key.substring('link:'.length()))
        } else {                                                    // instance (artifacts_/containers_/... — bare)
            suffix = snake(key)
        }
        return capSuffix(suffix, key)
    }

    /** Lowercase, invalid chars to underscore, separator runs collapsed, ends trimmed. */
    static String snake(String raw) {
        return raw.toLowerCase()
                .replaceAll('[^a-z0-9_-]', '_')
                .replaceAll('[_-]{2,}', '_')
                .replaceAll('^[_-]+|[_-]+$', '')
    }

    /** Cap an over-length suffix at {@link #MAX_SUFFIX_LENGTH} with a stable hash tail of the source key. */
    static String capSuffix(String suffix, String key) {
        if (suffix.length() <= MAX_SUFFIX_LENGTH) {
            return suffix
        }
        String tail = String.format('%08x', key.hashCode())
        return suffix.substring(0, MAX_SUFFIX_LENGTH - 9).replaceAll('[_-]+$', '') + '_' + tail
    }

    /** Lowercase snake name for a clarity process type display name. */
    static String processTypeName(String displayName) {
        return displayName.toLowerCase().replaceAll('[^a-z0-9_-]', '_')
    }

    /** The ent-or-fil collection an artifact document maps to. */
    static String artifactCollection(Map<String, Object> doc) {
        Map json = (doc.get('json') ?: [:]) as Map
        return OUTPUT_TYPE_RESULT_FILE == json.get('output-type') ? 'fil' : 'ent'
    }

    /**
     * One typ/link-type declaration per type key — the static bootstrap
     * vocabulary plus the dynamic per-process-type procedure types.
     */
    MappedImportMessage mapBootstrapType(String key, BiFunction<String, String, String> idFor) {
        String id = idFor.apply(key, 'typ')
        if (key.startsWith(PROCEDURE_TYPE_KEY_PREFIX)) {
            String displayName = key.substring(PROCEDURE_TYPE_KEY_PREFIX.length())
            return message('typ', [
                    kind       : 'procedure_type',
                    id         : id,
                    name       : processTypeName(displayName),
                    description: 'Clarity process type: ' + displayName
            ])
        }
        switch (key) {
            case KEY_TYPE_ANALYTE:
                return message('typ', [
                        id         : id,
                        name       : 'analyte',
                        description: 'Clarity analyte artifact'
                ])
            case KEY_TYPE_RESULT_FILE:
                return message('typ', [
                        id         : id,
                        name       : 'result_file',
                        description: 'Clarity result-file artifact'
                ])
            case KEY_TYPE_CONTAINER:
                return message('typ', [
                        id         : id,
                        name       : 'container',
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
            case KEY_TYPE_SAMPLE:
                return message('typ', [
                        id         : id,
                        name       : 'sample',
                        description: 'Clarity submitted sample'
                ])
            case KEY_TYPE_LINK_SAMPLE_OF:
                return message('typ', [
                        kind                     : 'link_type',
                        id                       : id,
                        name                     : 'sample_of',
                        description              : 'an artifact and the submitted sample it derives from',
                        left_role                : 'artifact',
                        right_role               : 'sample',
                        left_to_right_label      : 'sample_of',
                        right_to_left_label      : 'has_artifact',
                        allowed_left_collections : ['ent', 'fil'],
                        allowed_right_collections: ['ent']
                ])
            default:
                throw new IllegalArgumentException("Unknown bootstrap key: ${key}")
        }
    }

    /** Submitted sample → ent root. */
    List<MappedImportMessage> mapSample(Map<String, Object> doc,
                                        BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        Map<String, Object> properties = [
                source_kind   : 'clarity_sample',
                clarity_limsid: limsid
        ] as Map<String, Object>
        putIfPresent(properties, 'name', json.get('name'))
        putIfPresent(properties, 'date_received', json.get('date-received'))
        Object controlType = json.get('control-type')
        putIfPresent(properties, 'control_type',
                controlType instanceof Map ? ((Map) controlType).get('name') : controlType)
        Map submitter = json.get('submitter') as Map
        if (submitter) {
            String name = [submitter.get('first-name'), submitter.get('last-name')]
                    .findAll { it }.join(' ')
            putIfPresent(properties, 'submitter', name ?: null)
        }
        return [message('ent', [
                id        : idFor.apply(sampleKey(limsid), 'ent'),
                type_id   : idFor.apply(KEY_TYPE_SAMPLE, 'typ'),
                properties: properties,
                links     : [:]
        ])]
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
        List<String> sampleLimsids = sampleLimsids(json)
        if (!sampleLimsids.isEmpty()) {
            properties.put('sample_limsids', sampleLimsids)
        }

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

        sampleLimsids.each { String sampleLimsid ->
            messages.add(message('lnk', [
                    id     : idFor.apply("link:sample_of:${limsid}:${sampleLimsid}".toString(), 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_SAMPLE_OF, 'typ'),
                    left   : artifactId,
                    right  : idFor.apply(sampleKey(sampleLimsid), 'ent')
            ]))
        }
        return messages
    }

    /**
     * One file-property declaration row (TASK-068): the ppy definition
     * plus the add_property registration on the ResultFile type.
     */
    List<MappedImportMessage> mapFileProperty(String key, BiFunction<String, String, String> idFor) {
        if (!key.startsWith(KEY_FILE_PROPERTY_PREFIX)) {
            throw new IllegalArgumentException("Unknown file-property key: ${key}")
        }
        String name = key.substring(KEY_FILE_PROPERTY_PREFIX.length())
        Map valueSchema = name == 'is_published'
                ? [type: 'object', properties: [boolean: [type: 'boolean']]]
                : [type: 'object', properties: [text: [type: 'string']]]
        String propertyId = idFor.apply(key, 'ppy')
        return [
                message('ppy', [
                        kind        : 'definition',
                        id          : propertyId,
                        name        : name + '_clarity',
                        description : 'Clarity file ' + name.replace('_', ' '),
                        value_schema: valueSchema
                ]),
                new MappedImportMessage(collection: 'typ', action: 'update', data: [
                        id         : idFor.apply(KEY_TYPE_RESULT_FILE, 'typ'),
                        operation  : 'add_property',
                        property_id: propertyId
                ] as Map<String, Object>)
        ]
    }

    /**
     * File document → property assignments onto the attached artifact's
     * existing root (TASK-068). The target collection comes from the
     * resolved artifact id's collection segment, so ent-attached files
     * assign onto ent roots the same way (unregistered properties there
     * surface as counted gate outcomes, never errors).
     */
    List<MappedImportMessage> mapFile(Map<String, Object> doc,
                                      BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        String attachedTo = json.get('attached-to') as String
        String artifactLimsid = attachedTo ? attachedTo.tokenize('/').last() : null
        if (!artifactLimsid) {
            log.warn('Clarity file without a resolvable attached-to artifact, skipped: limsid={}', limsid)
            return []
        }
        String artifactId = idFor.apply(artifactKey(artifactLimsid), 'fil')
        String objectCollection = artifactId.split('~')[3]

        Map<String, Map<String, Object>> values = new LinkedHashMap<>()
        putValue(values, 'content_location', textValue(json.get('content-location')))
        putValue(values, 'original_name', textValue(json.get('original-name')))
        putValue(values, 'original_location', textValue(json.get('original-location')))
        if (json.get('is-published') != null) {
            values.put('is_published',
                    [boolean: 'true' == (json.get('is-published') as String)] as Map<String, Object>)
        }
        values.put('file_limsid', [text: limsid] as Map<String, Object>)

        return values.collect { String name, Map<String, Object> value ->
            message('ppy', [
                    kind             : 'assignment',
                    object_collection: objectCollection,
                    object_id        : artifactId,
                    property_id      : idFor.apply(KEY_FILE_PROPERTY_PREFIX + name, 'ppy'),
                    value            : value
            ])
        }
    }

    private static Map<String, Object> textValue(Object raw) {
        String text = raw as String
        return text?.trim() ? ([text: text] as Map<String, Object>) : null
    }

    private static void putValue(Map<String, Map<String, Object>> values,
                                 String name, Map<String, Object> value) {
        if (value != null) {
            values.put(name, value)
        }
    }

    /** Process → prc with output_input, procedure_input links, produced_by links. */
    List<MappedImportMessage> mapProcess(Map<String, Object> doc,
                                         BiFunction<String, String, String> idFor) {
        String limsid = doc.get('limsid') as String
        Map json = (doc.get('json') ?: [:]) as Map
        String processId = idFor.apply(processKey(limsid), 'prc')

        List iom = asImportList(json.get('input-output-map'))
        Map<String, Map<String, Object>> outputInput = new LinkedHashMap<>()
        Set<String> inputLimsids = new LinkedHashSet<>()
        Set<String> outputLimsids = new LinkedHashSet<>()
        iom.each { Object entry ->
            Map mapping = entry instanceof Map ? (Map) entry : [:]
            Object out = mapping.get('output')
            Object inp = mapping.get('input')
            String outLimsid = out instanceof Map ? ((Map) out).get('limsid') : null
            String inLimsid = inp instanceof Map ? ((Map) inp).get('limsid') : null
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

        // clarity element text lands under the '' key of its JSON object
        String processTypeDisplayName = (json.get('type') as Map)?.get('') as String
        if (!processTypeDisplayName) {
            log.warn('Clarity process without a type name, imported under "unknown": limsid={}', limsid)
            processTypeDisplayName = 'unknown'
        }
        Map<String, Object> properties = [
                source_kind   : 'clarity_process',
                clarity_limsid: limsid,
                process_type  : processTypeDisplayName
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
                type_id     : idFor.apply(processTypeKey(processTypeDisplayName), 'typ'),
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
