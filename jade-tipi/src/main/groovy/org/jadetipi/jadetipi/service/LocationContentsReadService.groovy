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
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Mono

/**
 * Resolves "what does this location contain?" from materialized roots.
 *
 * <p>The service composes accepted readers: it resolves the subject with
 * {@link LocationRootReadService}, reads outgoing {@code contents} links with
 * {@link ContentsLinkReadService}, then resolves each right endpoint as either
 * a {@code loc} root or an {@code ent} root when present. It performs no
 * writes, no recursive traversal, and keeps links visible even when a child
 * endpoint is not materialized.
 */
@Slf4j
@Service
class LocationContentsReadService {

    static final String POSITION = 'position'

    private final LocationRootReadService locationRootReadService
    private final ContentsLinkReadService contentsLinkReadService
    private final EntityPropertyValuesReadService entityPropertyValuesReadService

    LocationContentsReadService(
            LocationRootReadService locationRootReadService,
            ContentsLinkReadService contentsLinkReadService,
            EntityPropertyValuesReadService entityPropertyValuesReadService) {

        this.locationRootReadService = locationRootReadService
        this.contentsLinkReadService = contentsLinkReadService
        this.entityPropertyValuesReadService = entityPropertyValuesReadService
    }

    /**
     * Returns the subject {@code loc} root and its immediate outgoing contents
     * links. Empty {@link Mono} means the subject location root is not
     * materialized.
     */
    Mono<LocationContentsRecord> findLocationContents(String locationId) {
        Assert.hasText(locationId, 'locationId must not be blank')
        return locationRootReadService.findLocation(locationId)
                .flatMap { LocationRootRecord location ->
                    contentsLinkReadService.findContents(locationId)
                            .concatMap { ContentsLinkRecord link -> resolveEntry(link) }
                            .collectList()
                            .map { List<LocationContentsEntryRecord> entries ->
                                new LocationContentsRecord(
                                        locationId: locationId,
                                        location: location,
                                        contents: entries
                                )
                            }
                } as Mono<LocationContentsRecord>
    }

    private Mono<LocationContentsEntryRecord> resolveEntry(ContentsLinkRecord link) {
        LocationContentsEntryRecord unresolved = toEntry(link, null, null)
        if (!hasText(link.right)) {
            return Mono.just(unresolved)
        }
        return locationRootReadService.findLocation(link.right)
                .map { LocationRootRecord location -> toEntry(link, location, null) }
                .switchIfEmpty(Mono.defer {
                    entityPropertyValuesReadService.findPropertyValues(link.right)
                            .map { EntityPropertyValuesRecord entity -> toEntry(link, null, entity) }
                })
                .defaultIfEmpty(unresolved)
    }

    private static LocationContentsEntryRecord toEntry(
            ContentsLinkRecord link,
            LocationRootRecord contentLocation,
            EntityPropertyValuesRecord contentEntity) {

        return new LocationContentsEntryRecord(
                linkId: link.linkId,
                typeId: link.typeId,
                containerId: link.left,
                contentId: link.right,
                position: extractPositionMap(link),
                linkProvenance: link.provenance,
                contentLocation: contentLocation,
                contentEntity: contentEntity
        )
    }

    private static Map<String, Object> extractPositionMap(ContentsLinkRecord link) {
        Object properties = link?.properties
        if (properties instanceof Map) {
            Object position = ((Map) properties).get(POSITION)
            if (position instanceof Map) {
                return (Map<String, Object>) position
            }
        }
        return null
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty()
    }
}
