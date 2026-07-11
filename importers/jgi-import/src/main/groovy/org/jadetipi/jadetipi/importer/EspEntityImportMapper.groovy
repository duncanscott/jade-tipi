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
 * Pure mapper for the esp-entity import (TASK-069, phase 3;
 * docs/bulk-import-design.md): UUIDv7-keyed esp documents →
 * {@link MappedImportMessage}s. Container-class documents map to
 * {@code loc} roots; every other class maps to {@code ent}. Entity types
 * are dynamic — one typ per (class_name, type_name), like the clarity
 * procedure types — and esp mints its OWN {@code contents} and
 * {@code begat} link types (independently minted link-type declarations
 * coexist; read views resolve {@code contents} by name).
 *
 * <p>Provenance rides the {@code begat} edges: each parent edge on a
 * document yields one begat link (left = parent, right = child).
 * Containment is emitted by the CONTAINED entity — a positioned contents
 * link (container → entity) when the driver injects the entity's well
 * ({@code _import_well}, looked up from the container's contents map at
 * drive time).
 *
 * <p>The esp {@code variables} bag rides under {@code properties.variables}
 * with keys sanitized to the wire schema's snake_case rule (the schema
 * constrains nested property keys); the original human names
 * ('Concentration (ng/ul)') are preserved in a sibling
 * {@code variable_names} map.
 *
 * <p>Clarity overlap (TASK-071): esp re-imports of clarity containers keep
 * the clarity limsid as the esp name. A Container passing
 * {@link #isClarityContainerCandidate} whose clarity row was already
 * imported becomes an OVERLAY — the driver reuses the clarity root id and
 * sets {@link #PRECEDENCE_OVERLAY}, so this mapper emits the entity's links
 * (attaching the esp graph to the shared clarity plate) but not a
 * duplicate root-create. Sample-level entities carry no clarity key, so
 * value-level precedence is not reconstructable from the replica — a
 * recorded deficiency; see the design doc.
 */
@Slf4j
@Service
class EspEntityImportMapper {

    static final String SOURCE = 'esp'
    static final String DATABASE = 'esp-entity'

    static final String TYPE_KEY_PREFIX = 'type:esp:'
    static final String PROCEDURE_TYPE_KEY_PREFIX = 'type:esp:procedure:'
    static final String KEY_TYPE_LINK_CONTENTS = 'type:esp:link:contents'
    static final String KEY_TYPE_LINK_BEGAT = 'type:esp:link:begat'
    static final String KEY_TYPE_LINK_TASK_INPUT = 'type:esp:link:task_input'
    static final String KEY_TYPE_LINK_FULFILLS = 'type:esp:link:fulfills'
    static final String KEY_TYPE_LINK_PROCEDURE_INPUT = 'type:esp:link:procedure_input'
    static final String KEY_TYPE_LINK_PRODUCED_BY = 'type:esp:link:produced_by'
    static final List<String> BOOTSTRAP_KEYS = List.of(
            KEY_TYPE_LINK_CONTENTS, KEY_TYPE_LINK_BEGAT, KEY_TYPE_LINK_TASK_INPUT,
            KEY_TYPE_LINK_FULFILLS, KEY_TYPE_LINK_PROCEDURE_INPUT, KEY_TYPE_LINK_PRODUCED_BY)

    static final String CLASS_CONTAINER = 'Container'
    static final String CLASS_SOW_ITEM = 'SOW Item'
    /**
     * Driver-injected reconstructed workflow procedures (TASK-070): a list of
     * maps { workflow_name, procedure_key, input_uuid, output_uuids } for the
     * lab workflow instances the carrier's sample sheets attest, with inputs
     * and window-filtered outputs already resolved by the driver.
     */
    static final String WORKFLOW_PROCEDURES = '_workflow_procedures'

    /** esp begat-parent type_names that are tasks/projects, not biological inputs. */
    static final Set<String> NON_INPUT_PARENT_TYPES = Set.of(
            'SOW Item', 'PM SOW Item', 'Sequencing Project', 'Final Deliv Project', 'Proposal')

    /** The procedure-type import key for one esp workflow name (dynamic type). */
    static String procedureTypeKey(String workflowName) {
        return PROCEDURE_TYPE_KEY_PREFIX + workflowName
    }

    /**
     * Reconstruct the lab workflow INSTANCES this document is the input-side
     * carrier for (TASK-070). A document's sample sheets are grouped by
     * {@code workflow_uuid} and chunked into instances of the workflow's
     * {@code protocol_count} (so a wizard's 2–4 protocol sheets merge into
     * one procedure, and a re-run splits). Only LAB workflows are kept, and
     * only when THIS carrier's {@code type_name} is one of the workflow's
     * configured input types — so a workflow instance is reconstructed once,
     * from its input-side carrier (the SOW Item task for e.g. Aliquot
     * Creation, the Aliquot itself for Illumina Library Creation), never
     * duplicated by the output entity that also carries the sheet.
     *
     * <p>Returns skeletons {@code {workflow_name, procedure_key, start, end}};
     * the driver resolves each instance's input and window-filtered outputs.
     *
     * <p><b>Limitation (TASK-072):</b> reconstruction is per-carrier, correct
     * for the single-input/single-output pattern but NOT for batch/pool
     * workflows where many carriers share one {@code sample_sheet_uuid}
     * (each reconstructs the same prc, so only one carrier's contributions
     * survive). Procedure-centric aggregation is deferred to TASK-072.
     */
    static List<Map<String, Object>> reconstructWorkflowInstances(Map<String, Object> doc,
                                                                  EspWorkflowConfigService config) {
        String carrierType = doc.get('type_name') as String
        Map<String, List<Map>> byWorkflow = new LinkedHashMap<>()
        ClarityAliquotImportMapper.asImportList(doc.get('sample_sheets')).each { Object s ->
            if (!(s instanceof Map)) {
                return
            }
            Map sheet = (Map) s
            String wfName = sheet.get('workflow_name') as String
            if (wfName == null || !config.isLabProcedure(wfName)) {
                return
            }
            if (!config.inputTypes(wfName).contains(carrierType)) {
                return   // not the input-side carrier for this workflow
            }
            String wfUuid = sheet.get('workflow_uuid') as String ?: wfName
            byWorkflow.computeIfAbsent(wfUuid, { new ArrayList<Map>() }).add(sheet)
        }

        List<Map<String, Object>> instances = []
        byWorkflow.each { String wfUuid, List<Map> group ->
            String wfName = group.first().get('workflow_name') as String
            int chunk = Math.max(1, config.protocolCount(wfName))
            List<Map> ordered = group.sort(false) { (it.get('sample_sheet_start_time') as String) ?: '' }
            for (int i = 0; i < ordered.size(); i += chunk) {
                List<Map> inst = ordered.subList(i, Math.min(i + chunk, ordered.size()))
                List<String> starts = inst.collect { it.get('sample_sheet_start_time') as String }.findAll { it }
                List<String> ends = inst.collect { it.get('sample_sheet_end_time') as String }.findAll { it }
                List<String> sheetUuids = inst.collect { it.get('sample_sheet_uuid') as String }.findAll { it }.sort()
                instances.add([
                        workflow_name: wfName,
                        procedure_key: sheetUuids ? sheetUuids.first() : (wfUuid + ':' + i),
                        start        : starts ? starts.min() : null,
                        end          : ends ? ends.max() : null,
                ] as Map<String, Object>)
            }
        }
        return instances
    }

    /** The prc import key for one reconstructed procedure instance. */
    static String procedureKey(String procedureInstanceKey) {
        return 'procedure:esp:' + procedureInstanceKey
    }
    /** Driver-injected well label for a contained entity (not source data). */
    static final String IMPORT_WELL = '_import_well'
    /**
     * Driver-injected flag (TASK-071): this esp entity is an overlay onto an
     * already-imported clarity root whose id it reuses — the mapper emits its
     * links but NOT a duplicate root-create.
     */
    static final String PRECEDENCE_OVERLAY = '_precedence_overlay'

    /**
     * True when an esp document is a Container whose name is a clean clarity
     * container limsid ({@code <digits>-<digits>}, no re-plate suffix like
     * {@code _X}). Per the TASK-071 overlap investigation this is the sole
     * class where esp identity maps to a clarity container with verified
     * same-entity identity (esp name == barcode == clarity limsid ==
     * clarity doc id). The driver still EXISTENCE-GATES against the clarity
     * import_queue before reusing an id — shape alone is not sufficient
     * (~11% of limsid-shaped esp names have no clarity doc).
     */
    static boolean isClarityContainerCandidate(Map<String, Object> doc) {
        String name = doc?.get('name') as String
        return CLASS_CONTAINER == doc?.get('class_name') && name != null && name.matches('[0-9]+-[0-9]+')
    }

    /** The clarity container document key for a bare container limsid. */
    static String clarityContainerKey(String containerLimsid) {
        return 'containers_' + containerLimsid
    }

    /** One dynamic type per esp (class_name, type_name); raw names carry identity. */
    static String typeKey(String className, String typeName) {
        return TYPE_KEY_PREFIX + className + ':' + typeName
    }

    static String suffixFor(String key) {
        return 'esp_' + key.toLowerCase().replaceAll('[^a-z0-9_-]', '_')
    }

    /** The Mongo collection an esp document's root lands in. */
    static String entityCollection(Map<String, Object> doc) {
        String className = doc.get('class_name')
        if (CLASS_CONTAINER == className) {
            return 'loc'
        }
        if (CLASS_SOW_ITEM == className) {
            return 'tsk'   // SOW Items are tasks (TASK-070)
        }
        return 'ent'
    }

    /** One typ/link-type declaration per esp type key. */
    MappedImportMessage mapBootstrapType(String key, BiFunction<String, String, String> idFor) {
        String id = idFor.apply(key, 'typ')
        if (key == KEY_TYPE_LINK_CONTENTS) {
            return linkType(id, 'contents', 'containment between an esp container and its contents',
                    'container', 'content', 'contains', 'contained_by',
                    ['loc'], ['loc', 'ent', 'fil', 'tsk'])
        }
        if (key == KEY_TYPE_LINK_BEGAT) {
            // begat edges connect entities, containers, files AND tasks (SOW
            // Items are tsk since TASK-070), so both sides admit tsk.
            return linkType(id, 'begat', 'esp provenance: a parent entity begat a child entity',
                    'parent', 'child', 'begat', 'begat_by',
                    ['ent', 'loc', 'fil', 'tsk'], ['ent', 'loc', 'fil', 'tsk'])
        }
        if (key == KEY_TYPE_LINK_TASK_INPUT) {
            return linkType(id, 'task_input', 'an input entity a task delivers into its workflows',
                    'task', 'input', 'task_input', 'input_of', ['tsk'], ['ent', 'loc', 'fil'])
        }
        if (key == KEY_TYPE_LINK_FULFILLS) {
            return linkType(id, 'fulfills', 'the performed procedure that fulfilled a task',
                    'procedure', 'task', 'fulfills', 'fulfilled_by', ['prc'], ['tsk'])
        }
        if (key == KEY_TYPE_LINK_PROCEDURE_INPUT) {
            return linkType(id, 'procedure_input', 'an input consumed by a performed procedure',
                    'procedure', 'input', 'consumed', 'consumed_by', ['prc'], ['ent', 'loc', 'fil', 'tsk'])
        }
        if (key == KEY_TYPE_LINK_PRODUCED_BY) {
            return linkType(id, 'produced_by', 'an output and the performed procedure that created it',
                    'output', 'procedure', 'produced_by', 'produced', ['ent', 'loc', 'fil'], ['prc'])
        }
        if (key.startsWith(PROCEDURE_TYPE_KEY_PREFIX)) {
            String workflowName = key.substring(PROCEDURE_TYPE_KEY_PREFIX.length())
            return message('typ', [
                    kind       : 'procedure_type',
                    id         : id,
                    name       : 'esp_' + workflowName.toLowerCase().replaceAll('[^a-z0-9_-]', '_'),
                    description: 'ESP workflow: ' + workflowName
            ])
        }
        if (key.startsWith(TYPE_KEY_PREFIX)) {
            String qualified = key.substring(TYPE_KEY_PREFIX.length())
            int colon = qualified.indexOf(':')
            String className = colon > 0 ? qualified.substring(0, colon) : qualified
            String typeName = colon > 0 ? qualified.substring(colon + 1) : qualified
            return message('typ', [
                    id         : id,
                    name       : 'esp_' + typeName.toLowerCase().replaceAll('[^a-z0-9_-]', '_'),
                    description: 'ESP ' + className + ' type: ' + typeName
            ])
        }
        throw new IllegalArgumentException("Unknown esp type key: ${key}")
    }

    private static MappedImportMessage linkType(String id, String name, String description,
                                                String leftRole, String rightRole,
                                                String leftToRight, String rightToLeft,
                                                List<String> allowedLeft, List<String> allowedRight) {
        return message('typ', [
                kind                     : 'link_type',
                id                       : id,
                name                     : name,
                description              : description,
                left_role                : leftRole,
                right_role               : rightRole,
                left_to_right_label      : leftToRight,
                right_to_left_label      : rightToLeft,
                allowed_left_collections : allowedLeft,
                allowed_right_collections: allowedRight
        ])
    }

    /**
     * Entity document → typed root plus its begat links and (when the
     * driver injected the well) its positioned contents link.
     */
    List<MappedImportMessage> mapEntity(Map<String, Object> doc,
                                        BiFunction<String, String, String> idFor) {
        String uuid = doc.get('uuid') as String ?: doc.get('_id') as String
        String collection = entityCollection(doc)
        String entityId = idFor.apply(uuid, collection)
        // TASK-071: an overlay entity reuses an already-imported clarity root
        // (its id is pre-resolved by the driver), so it contributes its links
        // but must NOT re-create the root (create-only; a duplicate create
        // would be a counted conflict, never the intent).
        boolean overlay = doc.get(PRECEDENCE_OVERLAY) == Boolean.TRUE

        List<MappedImportMessage> messages = []
        if (!overlay) {
            Map<String, Object> properties = [
                    source_kind: 'esp_entity',
                    esp_uuid   : uuid
            ] as Map<String, Object>
            putIfPresent(properties, 'name', doc.get('name'))
            putIfPresent(properties, 'barcode', doc.get('barcode'))
            putIfPresent(properties, 'esp_class', doc.get('class_name'))
            putIfPresent(properties, 'esp_type', doc.get('type_name'))
            putIfPresent(properties, 'created', doc.get('created'))
            if (doc.get('archived')) {
                properties.put('archived', true)
            }
            Object variables = doc.get('variables')
            if (variables instanceof Map && !((Map) variables).isEmpty()) {
                Map<String, Object> sanitized = new LinkedHashMap<>()
                Map<String, String> originalNames = new LinkedHashMap<>()
                ((Map) variables).each { Object k, Object v ->
                    String key = sanitizeKey(k as String)
                    if (sanitized.containsKey(key)) {
                        log.warn('esp variable key collision after sanitizing, last wins: {} → {}', k, key)
                    }
                    sanitized.put(key, v)
                    originalNames.put(key, k as String)
                }
                properties.put('variables', sanitized)
                properties.put('variable_names', originalNames)
            }
            messages.add(message(collection, [
                    id        : entityId,
                    type_id   : idFor.apply(typeKey(doc.get('class_name') as String,
                            doc.get('type_name') as String), 'typ'),
                    properties: properties,
                    links     : [:]
            ]))
        }

        ClarityAliquotImportMapper.asImportList(doc.get('parents')).each { Object edge ->
            Map parent = edge instanceof Map ? (Map) edge : [:]
            String parentUuid = parent.get('uuid') as String
            if (!parentUuid) {
                return
            }
            messages.add(message('lnk', [
                    id     : idFor.apply('link:esp:begat:' + parentUuid + ':' + uuid, 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_BEGAT, 'typ'),
                    left   : idFor.apply(parentUuid, 'ent'),
                    right  : entityId
            ]))
        }

        Map container = doc.get('container') instanceof Map ? (Map) doc.get('container') : null
        String containerUuid = container?.get('uuid') as String
        if (containerUuid) {
            Map<String, Object> linkData = [
                    id     : idFor.apply('link:esp:contents:' + containerUuid + ':' + uuid, 'lnk'),
                    type_id: idFor.apply(KEY_TYPE_LINK_CONTENTS, 'typ'),
                    left   : idFor.apply(containerUuid, 'loc'),
                    right  : entityId
            ] as Map<String, Object>
            Object well = doc.get(IMPORT_WELL)
            if (well != null) {
                linkData.put('properties', [position: [kind: 'esp_well', label: well]])
            }
            messages.add(message('lnk', linkData))
        }

        // task_input (TASK-070): a SOW Item task delivers its biological
        // (sample) begat parent as an input into its workflows.
        if (CLASS_SOW_ITEM == doc.get('class_name')) {
            ClarityAliquotImportMapper.asImportList(doc.get('parents')).each { Object edge ->
                Map parent = edge instanceof Map ? (Map) edge : [:]
                String parentUuid = parent.get('uuid') as String
                if (parentUuid && !NON_INPUT_PARENT_TYPES.contains(parent.get('type_name'))) {
                    messages.add(message('lnk', [
                            id     : idFor.apply('link:esp:task_input:' + uuid + ':' + parentUuid, 'lnk'),
                            type_id: idFor.apply(KEY_TYPE_LINK_TASK_INPUT, 'typ'),
                            left   : entityId,
                            right  : idFor.apply(parentUuid, 'ent')
                    ]))
                }
            }
        }

        // reconstructed workflow procedures (TASK-070): each instance was
        // enriched by the driver with its resolved input and window-filtered
        // outputs; emit the prc plus its fulfills/procedure_input/produced_by
        // links and the output_input map.
        ClarityAliquotImportMapper.asImportList(doc.get(WORKFLOW_PROCEDURES)).each { Object p ->
            Map inst = p instanceof Map ? (Map) p : [:]
            String workflowName = inst.get('workflow_name') as String
            if (workflowName == null) {
                return
            }
            String procInstKey = inst.get('procedure_key') as String
            String procId = idFor.apply(procedureKey(procInstKey), 'prc')
            String inputUuid = inst.get('input_uuid') as String
            String inputId = inputUuid ? idFor.apply(inputUuid, 'ent') : null
            List<String> outputUuids = (inst.get('output_uuids') ?: []) as List<String>

            Map<String, Object> outputInput = new LinkedHashMap<>()
            outputUuids.each { String outUuid ->
                outputInput.put(idFor.apply(outUuid, 'ent'),
                        inputId ? [(inputId): [:] as Map<String, Object>] : [:] as Map<String, Object>)
            }
            Map<String, Object> prcProps = [
                    source_kind : 'esp_workflow',
                    esp_workflow: workflowName
            ] as Map<String, Object>
            putIfPresent(prcProps, 'started', inst.get('start'))
            putIfPresent(prcProps, 'ended', inst.get('end'))
            putIfPresent(prcProps, 'esp_sample_sheet', procInstKey)
            messages.add(message('prc', [
                    id          : procId,
                    type_id     : idFor.apply(procedureTypeKey(workflowName), 'typ'),
                    properties  : prcProps,
                    links       : [:],
                    output_input: outputInput
            ]))
            if (inst.get('task_carried') == Boolean.TRUE) {
                messages.add(message('lnk', [
                        id     : idFor.apply('link:esp:fulfills:' + procInstKey + ':' + uuid, 'lnk'),
                        type_id: idFor.apply(KEY_TYPE_LINK_FULFILLS, 'typ'),
                        left   : procId,
                        right  : entityId
                ]))
            }
            if (inputId) {
                messages.add(message('lnk', [
                        id     : idFor.apply('link:esp:procedure_input:' + procInstKey, 'lnk'),
                        type_id: idFor.apply(KEY_TYPE_LINK_PROCEDURE_INPUT, 'typ'),
                        left   : procId,
                        right  : inputId
                ]))
            }
            outputUuids.each { String outUuid ->
                messages.add(message('lnk', [
                        id     : idFor.apply('link:esp:produced_by:' + procInstKey + ':' + outUuid, 'lnk'),
                        type_id: idFor.apply(KEY_TYPE_LINK_PRODUCED_BY, 'typ'),
                        left   : idFor.apply(outUuid, 'ent'),
                        right  : procId
                ]))
            }
        }
        return messages
    }

    /**
     * The wire schema constrains property keys (recursively) to
     * {@code ^[a-z][a-z0-9_]*$}; esp variable names carry spaces, units,
     * and punctuation, so keys sanitize and the originals ride
     * {@code variable_names}.
     */
    static String sanitizeKey(String raw) {
        String key = raw.toLowerCase()
                .replaceAll('[^a-z0-9]+', '_')
                .replaceAll('^_+|_+$', '')
        if (!key || !Character.isLetter(key.charAt(0))) {
            key = 'v_' + key
        }
        return key
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
