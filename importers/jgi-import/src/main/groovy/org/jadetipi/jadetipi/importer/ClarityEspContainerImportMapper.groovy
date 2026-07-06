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

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Pure mapper from locally replicated Clarity/ESP CouchDB documents to
 * canonical JDTP message payloads over the TASK-042 typed container model
 * (TASK-043). No I/O: the caller reads the documents, supplies the
 * established {@link ClarityEspContainerModel}, and owns transaction
 * identity, message UUIDs, and transport.
 *
 * <p>Mapping rules (per the clarity-esp-container-mapping doc):
 * <ul>
 *   <li>ESP {@code class_name == "Container"} → {@code loc + create};
 *       other ESP classes (e.g. {@code Sample}) → {@code ent + create}.
 *       Clarity {@code containers_*} documents → {@code loc + create}.</li>
 *   <li>The root {@code type_id} comes from the model's kind mapping for
 *       the source type label; an unmapped ESP container kind falls back
 *       to the base {@code container} type. An unmapped non-container
 *       class maps to nothing (skipped, logged).</li>
 *   <li>Identifiers follow D4: {@code esp_<slug>_<uuid-prefix>} /
 *       {@code clarity_<slug>_<limsid>} for roots, both endpoint names
 *       embedded in link IDs.</li>
 *   <li>Only source-present domain facts become object-targeted
 *       assignments ({@code name}, {@code barcode}); source-traceability
 *       facts stay in the inline {@code properties} bag with
 *       {@code source_kind} carrying the verbatim source type label.</li>
 *   <li>Containment derives from the imported document's own upward
 *       {@code container} pointer, never from a parent's {@code contents}
 *       map (the parent-side map goes stale in the live data). Positions
 *       follow the D3 vocabulary keyed by the parent kind.</li>
 * </ul>
 */
@Slf4j
class ClarityEspContainerImportMapper {

    static final String SOURCE_SYSTEM_ESP = 'esp-entity'
    static final String SOURCE_SYSTEM_CLARITY = 'clarity'
    static final String CLASS_NAME_CONTAINER = 'Container'
    static final String FALLBACK_ID_SLUG = 'container'
    static final String POSITION_KIND_FALLBACK = 'slot'

    private static final Pattern ROW_COLUMN_LABEL = Pattern.compile('^([A-Za-z]+)(\\d+)$')
    private static final Pattern NUMERIC_LABEL = Pattern.compile('^\\d+$')

    private ClarityEspContainerImportMapper() {
        throw new UnsupportedOperationException('static mapper')
    }

    /**
     * Map one ESP document (container or sample class) to its JDTP
     * messages: the typed root create, {@code name}/{@code barcode}
     * assignments when present, and the upward containment link when the
     * document carries a {@code container} pointer. Returns an empty list
     * (logged) when the document cannot be mapped.
     */
    static List<MappedImportMessage> mapEspDocument(Map<String, Object> doc,
                                                    ClarityEspContainerModel model,
                                                    String idPrefix) {
        String uuid = nonBlank(doc?.get('uuid'))
        if (uuid == null) {
            log.warn('Skipping ESP document without uuid: _id={}', doc?.get('_id'))
            return []
        }
        String typeName = nonBlank(doc.get('type_name'))
        String className = nonBlank(doc.get('class_name'))
        boolean isContainer = CLASS_NAME_CONTAINER == className
        ClarityEspKindMapping mapping = model.mappingFor(typeName)
        String typeId = mapping?.typeId ?: (isContainer ? model.fallbackContainerTypeId : null)
        if (typeId == null) {
            log.warn('Skipping ESP document with unmapped non-container kind: uuid={}, class={}, type={}',
                    uuid, className, typeName)
            return []
        }
        String collection = isContainer ? 'loc' : 'ent'
        String idSlug = mapping?.idSlug ?: FALLBACK_ID_SLUG
        String name = nonBlank(doc.get('name'))
        String rootId = isContainer
                ? espLocId(idPrefix, idSlug, uuid)
                : espEntId(idPrefix, idSlug, name, uuid)

        Map<String, Object> sourceProperties = new LinkedHashMap<>()
        if (typeName != null) {
            sourceProperties.put('source_kind', typeName)
        }
        sourceProperties.put('source_system', SOURCE_SYSTEM_ESP)
        sourceProperties.put('source_id', uuid)
        String typeUuid = nonBlank(doc.get('type_uuid'))
        if (typeUuid != null) {
            sourceProperties.put('source_type_id', typeUuid)
        }
        Object numericId = doc.get('numeric_id')
        if (numericId != null) {
            sourceProperties.put('source_numeric_id', numericId)
        }

        List<MappedImportMessage> messages = []
        messages << create(collection, [
                id        : rootId,
                type_id   : typeId,
                properties: sourceProperties,
                links     : [:]
        ] as Map<String, Object>)
        messages.addAll(assignments(model, collection, rootId, name, nonBlank(doc.get('barcode'))))

        MappedImportMessage containmentLink = containmentLink(doc, model, idPrefix, idSlug, name, rootId)
        if (containmentLink != null) {
            messages << containmentLink
        }
        return messages
    }

    /**
     * Map one Clarity {@code containers_*} document to its JDTP messages:
     * the typed {@code loc} root and a {@code name} assignment. Clarity
     * container documents carry no barcode and no physical location, so no
     * containment link is derived.
     */
    static List<MappedImportMessage> mapClarityContainerDocument(Map<String, Object> doc,
                                                                 ClarityEspContainerModel model,
                                                                 String idPrefix) {
        Map<String, Object> json = doc?.get('json') instanceof Map
                ? (Map<String, Object>) doc.get('json') : [:]
        String limsid = nonBlank(doc?.get('limsid')) ?: nonBlank(json.get('limsid'))
        if (limsid == null) {
            log.warn('Skipping Clarity document without limsid: _id={}', doc?.get('_id'))
            return []
        }
        String typeName = json.get('type') instanceof Map
                ? nonBlank(((Map) json.get('type')).get('name')) : null
        ClarityEspKindMapping mapping = model.mappingFor(typeName)
        String typeId = mapping?.typeId ?: model.fallbackContainerTypeId
        String idSlug = mapping?.idSlug ?: FALLBACK_ID_SLUG
        String rootId = "${idPrefix}~loc~clarity_${idSlug}_${limsid}" as String

        Map<String, Object> sourceProperties = new LinkedHashMap<>()
        if (typeName != null) {
            sourceProperties.put('source_kind', typeName)
        }
        sourceProperties.put('source_system', SOURCE_SYSTEM_CLARITY)
        sourceProperties.put('source_id', limsid)
        String state = nonBlank(json.get('state'))
        if (state != null) {
            sourceProperties.put('source_state', state)
        }

        List<MappedImportMessage> messages = []
        messages << create('loc', [
                id        : rootId,
                type_id   : typeId,
                properties: sourceProperties,
                links     : [:]
        ] as Map<String, Object>)
        messages.addAll(assignments(model, 'loc', rootId, nonBlank(json.get('name')), null))
        return messages
    }

    private static List<MappedImportMessage> assignments(ClarityEspContainerModel model,
                                                         String collection,
                                                         String rootId,
                                                         String name,
                                                         String barcode) {
        List<MappedImportMessage> messages = []
        if (name != null) {
            messages << assignment(collection, rootId, model.propertyNameId, [text: name])
        }
        if (barcode != null) {
            messages << assignment(collection, rootId, model.propertyBarcodeId, [text: barcode])
        }
        return messages
    }

    /**
     * Derive the upward containment link from the document's own
     * {@code container} pointer (child-side authoritative; the parent's
     * {@code contents} map is observably stale in the live replicas).
     */
    private static MappedImportMessage containmentLink(Map<String, Object> doc,
                                                       ClarityEspContainerModel model,
                                                       String idPrefix,
                                                       String childSlug,
                                                       String childName,
                                                       String childRootId) {
        Object containerValue = doc.get('container')
        if (!(containerValue instanceof Map)) {
            return null
        }
        Map parentRef = (Map) containerValue
        String parentUuid = nonBlank(parentRef.get('uuid'))
        if (parentUuid == null) {
            return null
        }
        String parentKind = nonBlank(parentRef.get('type_name'))
        ClarityEspKindMapping parentMapping = model.mappingFor(parentKind)
        String parentSlug = parentMapping?.idSlug ?: FALLBACK_ID_SLUG
        String parentId = espLocId(idPrefix, parentSlug, parentUuid)
        String parentName = nonBlank(parentRef.get('name'))
        String slot = nonBlank(parentRef.get('slot'))
        String positionKind = parentMapping?.positionKind ?: POSITION_KIND_FALLBACK

        Map<String, Object> properties = new LinkedHashMap<>()
        String positionSuffix = 'unpositioned'
        if (slot != null) {
            properties.put('position', position(positionKind, slot))
            positionSuffix = positionKind == 'freezer_slot'
                    ? 'slot' + nameSlug(slot)
                    : nameSlug(slot)
        }

        String linkId = "${idPrefix}~lnk~contents_${parentSlug}_${nameSlug(parentName ?: parentUuid)}" +
                "_to_${childSlug}_${nameSlug(childName ?: childRootId)}_${positionSuffix}" as String

        return create('lnk', [
                id        : linkId,
                type_id   : model.contentsTypeId,
                left      : parentId,
                right     : childRootId,
                properties: properties
        ] as Map<String, Object>)
    }

    /** D3 position object for a slot label, keyed by the parent kind. */
    private static Map<String, Object> position(String positionKind, String label) {
        Map<String, Object> position = new LinkedHashMap<>()
        position.put('kind', positionKind)
        position.put('label', label)
        Matcher rowColumn = ROW_COLUMN_LABEL.matcher(label)
        if (positionKind == 'freezer_slot' || NUMERIC_LABEL.matcher(label).matches()) {
            if (NUMERIC_LABEL.matcher(label).matches()) {
                position.put('slot', Integer.parseInt(label))
            }
        } else if (rowColumn.matches()) {
            position.put('row', rowColumn.group(1).toUpperCase(Locale.ROOT))
            position.put('column', Integer.parseInt(rowColumn.group(2)))
        }
        return position
    }

    /** D4: {@code esp_<slug>_<first-two-uuid-segments>}. */
    private static String espLocId(String idPrefix, String idSlug, String uuid) {
        return "${idPrefix}~loc~esp_${idSlug}_${uuidShort(uuid)}" as String
    }

    private static String espEntId(String idPrefix, String idSlug, String name, String uuid) {
        String suffix = name != null ? nameSlug(name) : uuidShort(uuid)
        return "${idPrefix}~ent~esp_${idSlug}_${suffix}" as String
    }

    /** First two hyphen-separated UUID segments, e.g. {@code 019a3a62-8fa8}. */
    private static String uuidShort(String uuid) {
        List<String> segments = uuid.split('-') as List<String>
        return segments.size() >= 2 ? segments[0] + '-' + segments[1] : uuid
    }

    /** Lowercase, spaces removed, only {@code [a-z0-9-]} kept. */
    private static String nameSlug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll('[^a-z0-9-]', '')
        return slug.isEmpty() ? 'x' : slug
    }

    private static MappedImportMessage create(String collection, Map<String, Object> data) {
        return new MappedImportMessage(collection: collection, action: 'create', data: data)
    }

    private static MappedImportMessage assignment(String objectCollection,
                                                  String objectId,
                                                  String propertyId,
                                                  Map<String, Object> value) {
        return new MappedImportMessage(collection: 'ppy', action: 'create', data: [
                kind             : 'assignment',
                object_collection: objectCollection,
                object_id        : objectId,
                property_id      : propertyId,
                value            : value
        ] as Map<String, Object>)
    }

    private static String nonBlank(Object value) {
        if (value == null) {
            return null
        }
        String text = value.toString()
        return text.trim().isEmpty() ? null : text
    }
}
