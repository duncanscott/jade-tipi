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
 * Reads one materialized {@code ent} root and its materialized {@code ppy}
 * assignment roots.
 *
 * <p>The service deliberately reads the canonical assignment roots written by
 * {@link CommittedTransactionMaterializer}. It does not depend on a future
 * denormalized projection onto the entity root, does not validate values
 * against {@code value_schema}, and performs no writes.
 */
@Slf4j
@Service
class EntityPropertyValuesReadService {

    static final String COLLECTION_ENT = 'ent'
    static final String COLLECTION_PPY = 'ppy'

    static final String FIELD_ID = '_id'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_HEAD = '_head'
    static final String HEAD_PROVENANCE = 'provenance'
    static final String FIELD_PROPERTIES_KIND = 'properties.kind'
    static final String FIELD_PROPERTIES_ENTITY_ID = 'properties.entity_id'
    static final String FIELD_PROPERTIES_PROPERTY_ID = 'properties.property_id'

    static final String PROP_NAME = 'name'
    static final String PROP_PROPERTY_ID = 'property_id'
    static final String PROP_VALUE = 'value'

    static final String KIND_ASSIGNMENT = 'assignment'
    static final String KIND_DEFINITION = 'definition'

    private final ReactiveMongoTemplate mongoTemplate

    EntityPropertyValuesReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Returns the entity root and all materialized property assignment roots for
     * {@code entityId}. Empty {@link Mono} means the entity root itself is not
     * materialized. An existing entity with no assignments returns a populated
     * record with an empty {@code valuesByPropertyId} map.
     */
    Mono<EntityPropertyValuesRecord> findPropertyValues(String entityId) {
        Assert.hasText(entityId, 'entityId must not be blank')
        return mongoTemplate.findById(entityId, Map.class, COLLECTION_ENT)
                .flatMap { Map entityRow ->
                    Query assignmentQuery = Query.query(
                            Criteria.where(FIELD_PROPERTIES_KIND).is(KIND_ASSIGNMENT)
                                    .and(FIELD_PROPERTIES_ENTITY_ID).is(entityId)
                    ).with(Sort.by(Sort.Direction.ASC, FIELD_PROPERTIES_PROPERTY_ID, FIELD_ID))

                    return mongoTemplate.find(assignmentQuery, Map.class, COLLECTION_PPY)
                            .collectList()
                            .flatMap { List<Map> assignmentRows ->
                                if (assignmentRows.isEmpty()) {
                                    return Mono.just(toRecord(entityRow, [:]))
                                }
                                List<String> propertyIds = assignmentRows
                                        .collect { Map row -> extractPropertyId(row) }
                                        .findAll { String id -> id != null }
                                        .unique()
                                if (propertyIds.isEmpty()) {
                                    return Mono.just(toRecord(entityRow,
                                            groupAssignments(assignmentRows, [:])))
                                }

                                Query definitionsQuery = Query.query(
                                        Criteria.where(FIELD_ID).in(propertyIds)
                                                .and(FIELD_PROPERTIES_KIND).is(KIND_DEFINITION)
                                ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

                                return mongoTemplate.find(definitionsQuery, Map.class, COLLECTION_PPY)
                                        .collectList()
                                        .map { List<Map> definitionRows ->
                                            Map<String, String> namesById = definitionRows.inject(
                                                    new LinkedHashMap<String, String>()) {
                                                Map<String, String> names, Map row ->
                                                    String id = row.get(FIELD_ID) as String
                                                    String name = extractDefinitionName(row)
                                                    if (id != null && name != null) {
                                                        names[id] = name
                                                    }
                                                    return names
                                            }
                                            return toRecord(entityRow,
                                                    groupAssignments(assignmentRows, namesById))
                                        }
                            }
                } as Mono<EntityPropertyValuesRecord>
    }

    private static EntityPropertyValuesRecord toRecord(
            Map entityRow,
            Map<String, List<EntityPropertyValueRecord>> valuesByPropertyId) {

        return new EntityPropertyValuesRecord(
                entityId: entityRow.get(FIELD_ID) as String,
                typeId: entityRow.get(FIELD_TYPE_ID) as String,
                properties: mapOrEmpty(entityRow.get(FIELD_PROPERTIES)),
                links: mapOrEmpty(entityRow.get(FIELD_LINKS)),
                provenance: extractProvenance(entityRow),
                valuesByPropertyId: valuesByPropertyId
        )
    }

    private static Map<String, List<EntityPropertyValueRecord>> groupAssignments(
            List<Map> assignmentRows,
            Map<String, String> propertyNamesById) {

        Map<String, List<EntityPropertyValueRecord>> values = new LinkedHashMap<>()
        assignmentRows.each { Map row ->
            String propertyId = extractPropertyId(row)
            if (propertyId == null) {
                // New assignment rows with missing property_id are invalid at
                // materialization time; this tolerates stale/drifted rows.
                return
            }
            if (!values.containsKey(propertyId)) {
                values[propertyId] = []
            }
            Map properties = mapOrEmpty(row.get(FIELD_PROPERTIES))
            values[propertyId] << new EntityPropertyValueRecord(
                    assignmentId: row.get(FIELD_ID) as String,
                    propertyId: propertyId,
                    propertyName: propertyNamesById[propertyId],
                    value: extractAssignmentValue(properties),
                    provenance: extractProvenance(row)
            )
        }
        return values
    }

    private static Map<String, Object> extractAssignmentValue(Map properties) {
        // Assignment materialization only writes object-shaped values. If an
        // older or drifted row is not object-shaped, keep the entity read
        // tolerant and return an empty map rather than failing the response.
        return mapOrEmpty(properties.get(PROP_VALUE))
    }

    private static String extractPropertyId(Map row) {
        Map properties = mapOrEmpty(row?.get(FIELD_PROPERTIES))
        Object value = properties.get(PROP_PROPERTY_ID)
        if (value == null) {
            return null
        }
        String text = value.toString()
        return text.trim().isEmpty() ? null : text
    }

    private static String extractDefinitionName(Map row) {
        Map properties = mapOrEmpty(row?.get(FIELD_PROPERTIES))
        Object value = properties.get(PROP_NAME)
        if (value == null) {
            return null
        }
        String text = value.toString()
        return text.trim().isEmpty() ? null : text
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
