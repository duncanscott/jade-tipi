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
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Paged browse over materialized {@code loc} roots (TASK-054), the
 * discovery counterpart to the per-container resource reads. Query-read
 * convention: an empty collection is an empty page, never an error.
 *
 * <p>Ordering is deterministic by {@code _id} ascending. Paging is
 * defensive: {@code page} floors at 0 and {@code size} is clamped to
 * 1..{@link #MAX_PAGE_SIZE}, and the clamped values are echoed in the
 * response envelope. Summary mapping tolerates sparse or legacy roots —
 * a missing {@code properties} bag or absent {@code name}/{@code description}
 * surfaces as nulls, never a failed read.
 */
@Slf4j
@Service
class LocationBrowseReadService {

    static final String COLLECTION_LOC = 'loc'
    static final int DEFAULT_PAGE_SIZE = 25
    static final int MAX_PAGE_SIZE = 100

    static final String FIELD_ID = '_id'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String PROP_NAME = 'name'
    static final String PROP_DESCRIPTION = 'description'

    private final ReactiveMongoTemplate mongoTemplate

    LocationBrowseReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    Mono<LocationBrowseRecord> browse(int page, int size) {
        int effectivePage = Math.max(page, 0)
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE)

        Query itemsQuery = new Query()
                .with(Sort.by(Sort.Direction.ASC, FIELD_ID))
                .skip((long) effectivePage * effectiveSize)
                .limit(effectiveSize)

        Mono<List<LocationSummaryRecord>> itemsMono =
                mongoTemplate.find(itemsQuery, Map.class, COLLECTION_LOC)
                        .map(this.&toSummary)
                        .collectList()
        Mono<Long> totalMono = mongoTemplate.count(new Query(), COLLECTION_LOC)

        return Mono.zip(itemsMono, totalMono)
                .map { tuple ->
                    new LocationBrowseRecord(
                            items: tuple.getT1(),
                            page: effectivePage,
                            size: effectiveSize,
                            total: tuple.getT2()
                    )
                } as Mono<LocationBrowseRecord>
    }

    private LocationSummaryRecord toSummary(Map row) {
        Map properties = row.get(FIELD_PROPERTIES) instanceof Map
                ? (Map) row.get(FIELD_PROPERTIES)
                : [:]
        return new LocationSummaryRecord(
                locationId: row.get(FIELD_ID) as String,
                typeId: row.get(FIELD_TYPE_ID) as String,
                name: properties.get(PROP_NAME) as String,
                description: properties.get(PROP_DESCRIPTION) as String
        )
    }
}
