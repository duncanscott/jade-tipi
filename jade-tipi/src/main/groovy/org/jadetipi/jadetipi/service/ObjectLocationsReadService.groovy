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
 * Resolves "where is this object located?" from materialized contents links.
 *
 * <p>The service composes the accepted flat reverse contents link reader with
 * the reusable {@code loc} root reader. It does not query {@code lnk}
 * directly, performs no writes, and keeps links visible even when their
 * container root is absent.
 */
@Slf4j
@Service
class ObjectLocationsReadService {

    static final String POSITION = 'position'

    private final ContentsLinkReadService contentsLinkReadService
    private final LocationRootReadService locationRootReadService

    ObjectLocationsReadService(
            ContentsLinkReadService contentsLinkReadService,
            LocationRootReadService locationRootReadService) {

        this.contentsLinkReadService = contentsLinkReadService
        this.locationRootReadService = locationRootReadService
    }

    /**
     * Returns the materialized contents links whose right endpoint is
     * {@code objectId}, with each left endpoint resolved as a {@code loc} root
     * when present.
     */
    Mono<ObjectLocationsRecord> findObjectLocations(String objectId) {
        Assert.hasText(objectId, 'objectId must not be blank')
        return contentsLinkReadService.findLocations(objectId)
                .concatMap { ContentsLinkRecord link -> resolveEntry(link) }
                .collectList()
                .map { List<ObjectLocationEntryRecord> entries ->
                    new ObjectLocationsRecord(objectId: objectId, locations: entries)
                } as Mono<ObjectLocationsRecord>
    }

    private Mono<ObjectLocationEntryRecord> resolveEntry(ContentsLinkRecord link) {
        ObjectLocationEntryRecord unresolved = toEntry(link, null)
        if (!hasText(link.left)) {
            return Mono.just(unresolved)
        }
        return locationRootReadService.findLocation(link.left)
                .map { LocationRootRecord location -> toEntry(link, location) }
                .defaultIfEmpty(unresolved)
    }

    private static ObjectLocationEntryRecord toEntry(
            ContentsLinkRecord link,
            LocationRootRecord container) {

        return new ObjectLocationEntryRecord(
                linkId: link.linkId,
                typeId: link.typeId,
                containerId: link.left,
                position: extractPositionMap(link),
                linkProvenance: link.provenance,
                container: container
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
