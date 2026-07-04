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

import groovy.util.logging.Slf4j
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Mono

/**
 * Reads one materialized object root ({@code loc} or {@code ent}) and its
 * projected {@code property_values} entries (TASK-041; root-only per drift
 * note 8.4 — no overlay of committed-but-unapplied messages).
 *
 * <p>Property names are resolved by joining the referenced {@code ppy}
 * definition roots, mirroring {@link EntityPropertyValuesReadService}; a
 * dangling {@code property_id} is tolerated and leaves
 * {@code propertyName == null}. Stale tolerance mirrors the accepted
 * readers: a non-map {@code property_values} sub-document or a non-map
 * entry is ignored, and a non-map entry {@code value} surfaces as an empty
 * map rather than failing the read. This service does not read the legacy
 * standalone {@code ppy} assignment roots; that transitional shape stays
 * with the entity-only reader until the planned cleanup.
 */
@Slf4j
@Service
class ObjectPropertyValuesReadService {

    static final String COLLECTION_ENT = 'ent'
    static final String COLLECTION_LOC = 'loc'
    static final String COLLECTION_PPY = 'ppy'
    static final Set<String> SUPPORTED_COLLECTIONS = Set.of(COLLECTION_ENT, COLLECTION_LOC)

    static final String FIELD_ID = '_id'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_PROPERTY_VALUES = 'property_values'
    static final String FIELD_HEAD = '_head'
    static final String HEAD_PROVENANCE = 'provenance'
    static final String FIELD_PROPERTIES_KIND = 'properties.kind'
    static final String KIND_DEFINITION = 'definition'

    static final String ENTRY_VALUE = 'value'
    static final String ENTRY_TXN_ID = 'txn_id'
    static final String ENTRY_COMMIT_ID = 'commit_id'
    static final String ENTRY_MSG_UUID = 'msg_uuid'
    static final String ENTRY_APPLIED_AT = 'applied_at'
    static final String PROP_NAME = 'name'

    private final ReactiveMongoTemplate mongoTemplate

    ObjectPropertyValuesReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Returns the object root and its projected property values. Empty
     * {@link Mono} means the object root is not materialized. An existing
     * root with no projected values returns a populated record with an empty
     * {@code propertyValues} map.
     */
    Mono<ObjectPropertyValuesRecord> findPropertyValues(String collection, String objectId) {
        Assert.hasText(collection, 'collection must not be blank')
        Assert.hasText(objectId, 'objectId must not be blank')
        Assert.isTrue(SUPPORTED_COLLECTIONS.contains(collection),
                "collection must be one of ${SUPPORTED_COLLECTIONS}")

        return mongoTemplate.findById(objectId, Map.class, collection)
                .flatMap { Map root ->
                    Map<String, Map<String, Object>> rawEntries = extractRawEntries(root)
                    if (rawEntries.isEmpty()) {
                        return Mono.just(toRecord(root, collection, [:]))
                    }
                    List<String> propertyIds = rawEntries.keySet().toList()
                    Query definitionsQuery = Query.query(
                            Criteria.where(FIELD_ID).in(propertyIds)
                                    .and(FIELD_PROPERTIES_KIND).is(KIND_DEFINITION)
                    ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

                    return mongoTemplate.find(definitionsQuery, Map.class, COLLECTION_PPY)
                            .collectList()
                            .map { List<Map> definitionRows ->
                                Map<String, String> namesById = definitionNames(definitionRows)
                                Map<String, ObjectPropertyValueEntryRecord> entries = new LinkedHashMap<>()
                                rawEntries.each { String propertyId, Map<String, Object> raw ->
                                    entries[propertyId] = toEntryRecord(propertyId, raw, namesById)
                                }
                                return toRecord(root, collection, entries)
                            }
                } as Mono<ObjectPropertyValuesRecord>
    }

    /**
     * Extract the {@code property_values} entries from the root, sorted by
     * property ID for deterministic responses. Non-map sub-documents and
     * non-map entries are skipped (stale-row tolerance).
     */
    private static Map<String, Map<String, Object>> extractRawEntries(Map root) {
        Object valuesMap = root?.get(FIELD_PROPERTY_VALUES)
        if (!(valuesMap instanceof Map)) {
            return new LinkedHashMap<String, Map<String, Object>>()
        }
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>()
        ((Map) valuesMap).keySet().collect { Object k -> k.toString() }.sort().each { String propertyId ->
            Object entry = ((Map) valuesMap).get(propertyId)
            if (entry instanceof Map) {
                entries[propertyId] = (Map<String, Object>) entry
            }
        }
        return entries
    }

    private static ObjectPropertyValueEntryRecord toEntryRecord(String propertyId,
                                                                Map<String, Object> raw,
                                                                Map<String, String> namesById) {
        return new ObjectPropertyValueEntryRecord(
                propertyId: propertyId,
                propertyName: namesById[propertyId],
                value: mapOrEmpty(raw.get(ENTRY_VALUE)),
                txnId: raw.get(ENTRY_TXN_ID) as String,
                commitId: raw.get(ENTRY_COMMIT_ID) as String,
                msgUuid: raw.get(ENTRY_MSG_UUID) as String,
                appliedAt: raw.get(ENTRY_APPLIED_AT)
        )
    }

    private static Map<String, String> definitionNames(List<Map> definitionRows) {
        Map<String, String> namesById = new LinkedHashMap<>()
        definitionRows.each { Map row ->
            String id = row.get(FIELD_ID) as String
            Map properties = mapOrEmpty(row.get(FIELD_PROPERTIES))
            Object name = properties.get(PROP_NAME)
            String text = name == null ? null : name.toString()
            if (id != null && text != null && !text.trim().isEmpty()) {
                namesById[id] = text
            }
        }
        return namesById
    }

    private static ObjectPropertyValuesRecord toRecord(Map root,
                                                       String collection,
                                                       Map<String, ObjectPropertyValueEntryRecord> entries) {
        return new ObjectPropertyValuesRecord(
                objectId: root.get(FIELD_ID) as String,
                collection: collection,
                typeId: root.get(FIELD_TYPE_ID) as String,
                properties: mapOrEmpty(root.get(FIELD_PROPERTIES)),
                links: mapOrEmpty(root.get(FIELD_LINKS)),
                provenance: extractProvenance(root),
                propertyValues: entries
        )
    }

    private static Map<String, Object> extractProvenance(Map row) {
        Object headValue = row?.get(FIELD_HEAD)
        if (headValue instanceof Map) {
            Object provenanceValue = ((Map) headValue).get(HEAD_PROVENANCE)
            if (provenanceValue instanceof Map) {
                return (Map<String, Object>) provenanceValue
            }
        }
        return null
    }

    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value
        }
        return new LinkedHashMap<String, Object>()
    }
}
