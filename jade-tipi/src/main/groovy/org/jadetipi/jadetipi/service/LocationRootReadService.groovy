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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Mono

/**
 * Reads materialized {@code loc} root documents.
 */
@Slf4j
@Service
class LocationRootReadService {

    static final String COLLECTION_LOC = 'loc'

    static final String FIELD_ID = '_id'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_HEAD = '_head'
    static final String HEAD_PROVENANCE = 'provenance'
    static final String FIELD_PROVENANCE_LEGACY = '_jt_provenance'

    private final ReactiveMongoTemplate mongoTemplate

    LocationRootReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Returns the materialized {@code loc} root for {@code locationId}, or an
     * empty {@link Mono} when no such root exists.
     */
    Mono<LocationRootRecord> findLocation(String locationId) {
        Assert.hasText(locationId, 'locationId must not be blank')
        return mongoTemplate.findById(locationId, Map.class, COLLECTION_LOC)
                .map(this.&toRecord)
    }

    private LocationRootRecord toRecord(Map row) {
        return new LocationRootRecord(
                locationId: row.get(FIELD_ID) as String,
                typeId: row.get(FIELD_TYPE_ID) as String,
                properties: mapOrEmpty(row.get(FIELD_PROPERTIES)),
                links: mapOrEmpty(row.get(FIELD_LINKS)),
                provenance: extractProvenance(row)
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
        Object legacyValue = row?.get(FIELD_PROVENANCE_LEGACY)
        if (legacyValue instanceof Map) {
            return (Map<String, Object>) legacyValue
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
