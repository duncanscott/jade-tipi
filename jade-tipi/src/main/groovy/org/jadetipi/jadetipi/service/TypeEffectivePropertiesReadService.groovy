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
 * Answers "which properties may objects of this type carry?" over
 * materialized {@code typ} roots (TASK-041): the union of
 * {@code properties.property_refs} on the subject type and every ancestor
 * reached through {@code properties.parent_type_id}, per the ratified
 * single-inheritance direction.
 *
 * <p>The walk mirrors the TASK-040 registration gate — single parent,
 * bounded depth, cycle-safe — but where the write gate fails closed, this
 * read surfaces what it found plus {@code chainComplete: false} so a broken
 * hierarchy is inspectable. When a property is registered at multiple
 * levels, the most-derived registration wins ({@code sourceTypeId} and
 * reference metadata come from the type nearest the subject).
 */
@Slf4j
@Service
class TypeEffectivePropertiesReadService {

    static final String COLLECTION_TYP = 'typ'
    static final String COLLECTION_PPY = 'ppy'

    static final String FIELD_ID = '_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_PROPERTY_REFS = 'property_refs'
    static final String FIELD_PARENT_TYPE_ID = 'parent_type_id'
    static final String FIELD_PROPERTIES_KIND = 'properties.kind'
    static final String KIND_DEFINITION = 'definition'
    static final String PROP_NAME = 'name'

    /** Same bound as the TASK-040 registration gate. */
    static final int MAX_TYPE_INHERITANCE_DEPTH = 10

    private final ReactiveMongoTemplate mongoTemplate

    TypeEffectivePropertiesReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Returns the effective properties of {@code typeId}. Empty {@link Mono}
     * means the subject {@code typ} root is not materialized.
     */
    Mono<TypeEffectivePropertiesRecord> findEffectiveProperties(String typeId) {
        Assert.hasText(typeId, 'typeId must not be blank')
        return mongoTemplate.findById(typeId, Map.class, COLLECTION_TYP)
                .flatMap { Map subjectRoot ->
                    walkChain(subjectRoot, new LinkedHashSet<String>(), [], true)
                            .flatMap { ChainWalk walk -> resolveNames(subjectRoot, walk) }
                } as Mono<TypeEffectivePropertiesRecord>
    }

    /**
     * Accumulator for the ancestor walk: the ordered {@code typ} roots
     * (subject first) and whether the chain resolved to its end.
     */
    private static class ChainWalk {
        List<Map> roots = []
        boolean complete = true
    }

    private Mono<ChainWalk> walkChain(Map currentRoot,
                                      Set<String> visited,
                                      List<Map> collected,
                                      boolean complete) {
        String currentId = currentRoot.get(FIELD_ID) as String
        if (currentId == null || !visited.add(currentId)) {
            log.warn('Type inheritance cycle detected at typeId={}; effective-properties chain incomplete', currentId)
            return Mono.just(new ChainWalk(roots: collected, complete: false))
        }
        if (visited.size() > MAX_TYPE_INHERITANCE_DEPTH) {
            log.warn('Type inheritance walk exceeded depth {} at typeId={}; effective-properties chain incomplete',
                    MAX_TYPE_INHERITANCE_DEPTH, currentId)
            return Mono.just(new ChainWalk(roots: collected, complete: false))
        }
        List<Map> nextCollected = collected + [currentRoot]
        String parentId = extractParentTypeId(currentRoot)
        if (parentId == null) {
            return Mono.just(new ChainWalk(roots: nextCollected, complete: complete))
        }
        return mongoTemplate.findById(parentId, Map.class, COLLECTION_TYP)
                .map({ Map parent -> Optional.of(parent) })
                .defaultIfEmpty(Optional.empty())
                .flatMap({ Optional<Map> parentProbe ->
                    if (!parentProbe.isPresent()) {
                        log.warn('Type inheritance walk found no typ root for parent typeId={}; ' +
                                'effective-properties chain incomplete', parentId)
                        return Mono.just(new ChainWalk(roots: nextCollected, complete: false))
                    }
                    return walkChain(parentProbe.get(), visited, nextCollected, complete)
                }) as Mono<ChainWalk>
    }

    private Mono<TypeEffectivePropertiesRecord> resolveNames(Map subjectRoot, ChainWalk walk) {
        Map<String, TypeEffectivePropertyRecord> effective = unionPropertyRefs(walk.roots)
        if (effective.isEmpty()) {
            return Mono.just(toRecord(subjectRoot, walk, effective))
        }
        Query definitionsQuery = Query.query(
                Criteria.where(FIELD_ID).in(effective.keySet().toList())
                        .and(FIELD_PROPERTIES_KIND).is(KIND_DEFINITION)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

        return mongoTemplate.find(definitionsQuery, Map.class, COLLECTION_PPY)
                .collectList()
                .map { List<Map> definitionRows ->
                    definitionRows.each { Map row ->
                        String id = row.get(FIELD_ID) as String
                        String name = extractDefinitionName(row)
                        if (id != null && name != null && effective.containsKey(id)) {
                            effective[id].propertyName = name
                        }
                    }
                    return toRecord(subjectRoot, walk, effective)
                } as Mono<TypeEffectivePropertiesRecord>
    }

    /**
     * Union of {@code property_refs} across the walked roots. Roots are
     * ordered subject-first, so the first (most-derived) registration of a
     * property wins; ancestor registrations of the same property are
     * ignored. Keys are sorted for deterministic responses.
     */
    private static Map<String, TypeEffectivePropertyRecord> unionPropertyRefs(List<Map> roots) {
        Map<String, TypeEffectivePropertyRecord> byPropertyId = new LinkedHashMap<>()
        roots.each { Map root ->
            String sourceTypeId = root.get(FIELD_ID) as String
            Map refs = extractPropertyRefs(root)
            refs.each { Object propertyIdValue, Object referenceValue ->
                String propertyId = propertyIdValue?.toString()
                if (propertyId == null || propertyId.trim().isEmpty()
                        || byPropertyId.containsKey(propertyId)) {
                    return
                }
                Map<String, Object> reference = referenceValue instanceof Map
                        ? new LinkedHashMap<String, Object>((Map<String, Object>) referenceValue)
                        : new LinkedHashMap<String, Object>()
                byPropertyId[propertyId] = new TypeEffectivePropertyRecord(
                        propertyId: propertyId,
                        sourceTypeId: sourceTypeId,
                        reference: reference
                )
            }
        }
        Map<String, TypeEffectivePropertyRecord> sorted = new LinkedHashMap<>()
        byPropertyId.keySet().sort().each { String key -> sorted[key] = byPropertyId[key] }
        return sorted
    }

    private static TypeEffectivePropertiesRecord toRecord(Map subjectRoot,
                                                          ChainWalk walk,
                                                          Map<String, TypeEffectivePropertyRecord> effective) {
        Map subjectProperties = mapOrEmpty(subjectRoot.get(FIELD_PROPERTIES))
        Object name = subjectProperties.get(PROP_NAME)
        return new TypeEffectivePropertiesRecord(
                typeId: subjectRoot.get(FIELD_ID) as String,
                typeName: name == null ? null : name.toString(),
                typeChain: walk.roots.collect { Map root -> root.get(FIELD_ID) as String },
                chainComplete: walk.complete,
                effectiveProperties: effective
        )
    }

    private static Map extractPropertyRefs(Map root) {
        Map properties = mapOrEmpty(root?.get(FIELD_PROPERTIES))
        Object refs = properties.get(FIELD_PROPERTY_REFS)
        return refs instanceof Map ? (Map) refs : new LinkedHashMap()
    }

    private static String extractParentTypeId(Map root) {
        Map properties = mapOrEmpty(root?.get(FIELD_PROPERTIES))
        Object parent = properties.get(FIELD_PARENT_TYPE_ID)
        if (parent == null) {
            return null
        }
        String text = parent.toString()
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

    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value
        }
        return new LinkedHashMap<String, Object>()
    }
}
