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
package org.jadetipi.jadetipi.controller

import groovy.util.logging.Slf4j
import org.jadetipi.jadetipi.service.ContentsLinkReadService
import org.jadetipi.jadetipi.service.ContentsLinkRecord
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/**
 * Thin WebFlux read adapter over {@link ContentsLinkReadService}.
 *
 * <p>Exposes the two {@code DIRECTION.md} contents questions as HTTP routes
 * over the materialized {@code lnk} collection:
 *
 * <ul>
 *   <li>{@code GET /api/contents/by-container/{id}} — forward lookup
 *       ("what are the contents of this container?"), delegating to
 *       {@link ContentsLinkReadService#findContents(String)}.</li>
 *   <li>{@code GET /api/contents/by-content/{id}} — reverse lookup
 *       ("where is this object located?"), delegating to
 *       {@link ContentsLinkReadService#findLocations(String)}.</li>
 * </ul>
 *
 * <p>Both routes return a flat JSON array of {@link ContentsLinkRecord}
 * preserving service order ({@code _id} ASC). An empty service result maps to
 * HTTP 200 with body {@code []}; blank or whitespace-only ids surface the
 * service {@code Assert.hasText(...)} {@link IllegalArgumentException} as a
 * 400 {@code ErrorResponse} through the global exception handler.
 *
 * <p>The controller is intentionally Kafka-free, Mongo-free, and
 * materializer-free: its only collaborator is {@link ContentsLinkReadService}.
 */
@Slf4j
@RestController
@RequestMapping('/api/contents')
class ContentsLinkReadController {

    private final ContentsLinkReadService readService

    ContentsLinkReadController(ContentsLinkReadService readService) {
        this.readService = readService
    }

    @GetMapping('/by-container/{id}')
    Flux<ContentsLinkRecord> getContentsByContainer(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Listing contents by container: id={}', id)
        return readService.findContents(id)
    }

    @GetMapping('/by-content/{id}')
    Flux<ContentsLinkRecord> getContentsByContent(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Listing contents by content: id={}', id)
        return readService.findLocations(id)
    }
}
