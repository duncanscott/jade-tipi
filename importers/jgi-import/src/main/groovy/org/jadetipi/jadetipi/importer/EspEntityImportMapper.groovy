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
    static final String KEY_TYPE_LINK_CONTENTS = 'type:esp:link:contents'
    static final String KEY_TYPE_LINK_BEGAT = 'type:esp:link:begat'
    static final List<String> BOOTSTRAP_KEYS = List.of(
            KEY_TYPE_LINK_CONTENTS, KEY_TYPE_LINK_BEGAT)

    static final String CLASS_CONTAINER = 'Container'
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
        return CLASS_CONTAINER == doc.get('class_name') ? 'loc' : 'ent'
    }

    /** One typ/link-type declaration per esp type key. */
    MappedImportMessage mapBootstrapType(String key, BiFunction<String, String, String> idFor) {
        String id = idFor.apply(key, 'typ')
        if (key == KEY_TYPE_LINK_CONTENTS) {
            return message('typ', [
                    kind                     : 'link_type',
                    id                       : id,
                    name                     : 'contents',
                    description              : 'containment between an esp container and its contents',
                    left_role                : 'container',
                    right_role               : 'content',
                    left_to_right_label      : 'contains',
                    right_to_left_label      : 'contained_by',
                    allowed_left_collections : ['loc'],
                    allowed_right_collections: ['loc', 'ent', 'fil']
            ])
        }
        if (key == KEY_TYPE_LINK_BEGAT) {
            return message('typ', [
                    kind                     : 'link_type',
                    id                       : id,
                    name                     : 'begat',
                    description              : 'esp provenance: a parent entity begat a child entity',
                    left_role                : 'parent',
                    right_role               : 'child',
                    left_to_right_label      : 'begat',
                    right_to_left_label      : 'begat_by',
                    allowed_left_collections : ['ent', 'loc', 'fil'],
                    allowed_right_collections: ['ent', 'loc', 'fil']
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
