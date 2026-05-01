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
import reactor.core.publisher.Flux

/**
 * Reads materialized {@code contents} links from the long-term {@code lnk}
 * collection populated by {@link CommittedTransactionMaterializer}.
 *
 * <p>Answers the two {@code DIRECTION.md} contents questions:
 * <ul>
 *   <li>{@link #findContents(String)} — "what are the contents of this
 *       container?": forward lookup over {@code lnk} where
 *       {@code left == containerId} and {@code type_id} matches a
 *       canonical {@code contents} link-type declaration.</li>
 *   <li>{@link #findLocations(String)} — "where is this object located?":
 *       reverse lookup over {@code lnk} where {@code right == objectId}
 *       and {@code type_id} matches the same canonical declaration.</li>
 * </ul>
 *
 * <p>The canonical {@code contents} link type is resolved against root-shaped
 * {@code typ} documents written by {@link CommittedTransactionMaterializer}:
 * a {@code typ + create} with {@code data.kind == "link_type"} and
 * {@code data.name == "contents"} carries those facts as
 * {@code properties.kind} and {@code properties.name}, so the service queries
 * {@code typ} for {@code properties.kind == "link_type"} and
 * {@code properties.name == "contents"} and uses every matching {@code _id}
 * in an {@code $in} filter on {@code lnk.type_id}. No type id is hardcoded
 * and the caller is not required to supply one.
 *
 * <p>{@code lnk} reads stay on the canonical top-level fields written by the
 * materializer ({@code type_id}, {@code left}, {@code right}, and the
 * instance {@code properties} sub-document); the service does not depend on
 * any endpoint {@code links} projection.
 *
 * <p>Provenance for each returned record is read from {@code _head.provenance}
 * (the new reserved location). For documents materialized before the root
 * shape was adopted, the service falls back to the legacy top-level
 * {@code _jt_provenance} sub-document. The fallback is intentional and
 * narrow; it can be removed once stale legacy rows are confirmed gone.
 *
 * <p>The service is intentionally Kafka-free and HTTP-free, mirroring
 * {@link CommittedTransactionReadService}. It does not join {@code lnk}
 * to {@code loc} or {@code ent}; endpoint id strings are returned
 * verbatim. Results are sorted by {@code _id} ASC and are not deduplicated
 * or grouped — each materialized {@code lnk} row produces one
 * {@link ContentsLinkRecord}.
 */
@Slf4j
@Service
class ContentsLinkReadService {

    static final String COLLECTION_TYP = 'typ'
    static final String COLLECTION_LNK = 'lnk'

    static final String FIELD_ID = '_id'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_LEFT = 'left'
    static final String FIELD_RIGHT = 'right'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_PROPERTIES_KIND = 'properties.kind'
    static final String FIELD_PROPERTIES_NAME = 'properties.name'
    static final String FIELD_HEAD = '_head'
    static final String HEAD_PROVENANCE = 'provenance'
    static final String FIELD_PROVENANCE_LEGACY = '_jt_provenance'

    static final String LINK_TYPE_KIND = 'link_type'
    static final String CONTENTS_TYPE_NAME = 'contents'

    private final ReactiveMongoTemplate mongoTemplate

    ContentsLinkReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Forward lookup: returns {@code contents} links whose {@code left}
     * endpoint is {@code containerId}, in {@code _id} ASC order. Returns an
     * empty {@link Flux} when no canonical {@code contents} link type has
     * been declared yet, when no link references the container, or when the
     * container itself does not exist.
     */
    Flux<ContentsLinkRecord> findContents(String containerId) {
        Assert.hasText(containerId, 'containerId must not be blank')
        return findByEndpoint(FIELD_LEFT, containerId)
    }

    /**
     * Reverse lookup: returns {@code contents} links whose {@code right}
     * endpoint is {@code objectId}, in {@code _id} ASC order. Returns an
     * empty {@link Flux} when no canonical {@code contents} link type has
     * been declared yet, when no link references the object, or when the
     * object itself does not exist.
     */
    Flux<ContentsLinkRecord> findLocations(String objectId) {
        Assert.hasText(objectId, 'objectId must not be blank')
        return findByEndpoint(FIELD_RIGHT, objectId)
    }

    private Flux<ContentsLinkRecord> findByEndpoint(String endpointField, String endpointId) {
        return resolveContentsTypeIds()
                .collectList()
                .flatMapMany { List<String> typeIds ->
                    if (typeIds.isEmpty()) {
                        return Flux.<ContentsLinkRecord> empty()
                    }
                    Query query = Query.query(
                            Criteria.where(FIELD_TYPE_ID).in(typeIds)
                                    .and(endpointField).is(endpointId)
                    ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))
                    return mongoTemplate.find(query, Map.class, COLLECTION_LNK)
                            .map(this.&toRecord)
                } as Flux<ContentsLinkRecord>
    }

    private Flux<String> resolveContentsTypeIds() {
        Query query = Query.query(
                Criteria.where(FIELD_PROPERTIES_KIND).is(LINK_TYPE_KIND)
                        .and(FIELD_PROPERTIES_NAME).is(CONTENTS_TYPE_NAME)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))
        return mongoTemplate.find(query, Map.class, COLLECTION_TYP)
                .map { Map row -> row.get(FIELD_ID) as String }
                .filter { String id -> id != null && !id.trim().isEmpty() }
    }

    private ContentsLinkRecord toRecord(Map row) {
        return new ContentsLinkRecord(
                linkId: row.get(FIELD_ID) as String,
                typeId: row.get(FIELD_TYPE_ID) as String,
                left: row.get(FIELD_LEFT) as String,
                right: row.get(FIELD_RIGHT) as String,
                properties: row.get(FIELD_PROPERTIES) as Map<String, Object>,
                provenance: extractProvenance(row)
        )
    }

    /**
     * Reads provenance from the new {@code _head.provenance} location, falling
     * back to legacy top-level {@code _jt_provenance} for documents
     * materialized before the root shape was adopted. Returns {@code null}
     * when neither source is present, preserving the existing
     * "missing provenance is null" wire contract.
     */
    private static Map<String, Object> extractProvenance(Map row) {
        Object headValue = row?.get(FIELD_HEAD)
        if (headValue instanceof Map) {
            Object provenanceValue = ((Map) headValue).get(HEAD_PROVENANCE)
            if (provenanceValue instanceof Map) {
                return (Map<String, Object>) provenanceValue
            }
        }
        Object legacy = row?.get(FIELD_PROVENANCE_LEGACY)
        if (legacy instanceof Map) {
            return (Map<String, Object>) legacy
        }
        return null
    }
}
