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

import java.time.Instant
import java.util.Date

/**
 * Paged read over the {@code hst} history collection (TASK-065): every
 * applied assignment for one object — optionally one property — in
 * message-UUID (chronological) order, with the same tolerant
 * property-name resolution as the current-values reader.
 *
 * <p>Resource-read convention: an empty {@link Mono} means the subject
 * root is not materialized (404 at the route). An existing root with no
 * history — including one whose type opts out via {@code history: false}
 * — returns an empty page, never an error.
 */
@Slf4j
@Service
class ObjectHistoryReadService {

    static final Set<String> SUPPORTED_COLLECTIONS =
            ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS
    static final String COLLECTION_HST = 'hst'
    static final String COLLECTION_PPY = 'ppy'
    static final int DEFAULT_PAGE_SIZE = 25
    static final int MAX_PAGE_SIZE = 100

    static final String FIELD_ID = '_id'
    static final String FIELD_OBJECT_ID = 'object_id'
    static final String FIELD_PROPERTY_ID = 'property_id'
    static final String FIELD_MSG_UUID = 'msg_uuid'
    static final String FIELD_VALUE = 'value'
    static final String FIELD_TXN_ID = 'txn_id'
    static final String FIELD_COMMIT_ID = 'commit_id'
    static final String FIELD_APPLIED_AT = 'applied_at'
    static final String FIELD_PROPERTIES_KIND = 'properties.kind'
    static final String KIND_DEFINITION = 'definition'
    static final String PROP_NAME = 'name'
    static final String FIELD_PROPERTIES = 'properties'

    private final ReactiveMongoTemplate mongoTemplate

    ObjectHistoryReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * One page of the object's assignment history. {@code propertyId}
     * narrows to one property when non-blank. Empty {@link Mono} when the
     * subject root is not materialized.
     */
    Mono<ObjectHistoryRecord> findHistory(String collection, String objectId,
                                          String propertyId, int page, int size) {
        Assert.hasText(collection, 'collection must not be blank')
        Assert.hasText(objectId, 'objectId must not be blank')
        Assert.isTrue(SUPPORTED_COLLECTIONS.contains(collection),
                "collection must be one of ${SUPPORTED_COLLECTIONS}")

        int effectivePage = Math.max(page, 0)
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        String effectivePropertyId = propertyId?.trim() ? propertyId.trim() : null

        return mongoTemplate.findById(objectId, Map.class, collection)
                .flatMap { Map root ->
                    Criteria criteria = Criteria.where(FIELD_OBJECT_ID).is(objectId)
                    if (effectivePropertyId != null) {
                        criteria = criteria.and(FIELD_PROPERTY_ID).is(effectivePropertyId)
                    }
                    Query itemsQuery = Query.query(criteria)
                            .with(Sort.by(Sort.Direction.ASC, FIELD_MSG_UUID))
                            .skip((long) effectivePage * effectiveSize)
                            .limit(effectiveSize)

                    Mono<List<Map>> rowsMono = mongoTemplate
                            .find(itemsQuery, Map.class, COLLECTION_HST)
                            .collectList()
                    Mono<Long> totalMono = mongoTemplate
                            .count(Query.query(criteria), COLLECTION_HST)

                    return Mono.zip(rowsMono, totalMono).flatMap { tuple ->
                        List<Map> rows = tuple.getT1()
                        long total = tuple.getT2()
                        return resolvePropertyNames(rows).map { Map<String, String> namesById ->
                            new ObjectHistoryRecord(
                                    objectId: objectId,
                                    collection: collection,
                                    propertyId: effectivePropertyId,
                                    items: rows.collect { Map row -> toEntry(row, namesById) },
                                    page: effectivePage,
                                    size: effectiveSize,
                                    total: total
                            )
                        }
                    }
                } as Mono<ObjectHistoryRecord>
    }

    /**
     * Resolve the page's distinct property IDs to definition names, same
     * tolerance as the current-values reader: a missing or malformed
     * definition simply yields no name.
     */
    private Mono<Map<String, String>> resolvePropertyNames(List<Map> rows) {
        List<String> propertyIds = rows.collect { Map row -> row.get(FIELD_PROPERTY_ID) as String }
                .findAll { it != null }.unique()
        if (propertyIds.isEmpty()) {
            return Mono.just([:] as Map<String, String>)
        }
        Query definitionsQuery = Query.query(
                Criteria.where(FIELD_ID).in(propertyIds)
                        .and(FIELD_PROPERTIES_KIND).is(KIND_DEFINITION))
        return mongoTemplate.find(definitionsQuery, Map.class, COLLECTION_PPY)
                .collectList()
                .map { List<Map> definitionRows ->
                    Map<String, String> namesById = new LinkedHashMap<>()
                    definitionRows.each { Map definition ->
                        Object properties = definition.get(FIELD_PROPERTIES)
                        Object name = properties instanceof Map ? ((Map) properties).get(PROP_NAME) : null
                        if (name != null) {
                            namesById[definition.get(FIELD_ID) as String] = name.toString()
                        }
                    }
                    return namesById
                } as Mono<Map<String, String>>
    }

    private static ObjectHistoryEntryRecord toEntry(Map row, Map<String, String> namesById) {
        String propertyId = row.get(FIELD_PROPERTY_ID) as String
        return new ObjectHistoryEntryRecord(
                msgUuid: (row.get(FIELD_MSG_UUID) ?: row.get(FIELD_ID)) as String,
                propertyId: propertyId,
                propertyName: namesById[propertyId],
                value: row.get(FIELD_VALUE) instanceof Map ? (Map) row.get(FIELD_VALUE) : [:],
                txnId: row.get(FIELD_TXN_ID) as String,
                commitId: row.get(FIELD_COMMIT_ID) as String,
                appliedAt: toInstant(row.get(FIELD_APPLIED_AT))
        )
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant) {
            return (Instant) value
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant()
        }
        return null
    }
}
