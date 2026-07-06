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
import org.jadetipi.jadetipi.service.LocationBrowseReadService
import org.jadetipi.jadetipi.service.LocationBrowseRecord
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Thin WebFlux read adapter over {@link LocationBrowseReadService}.
 * Query-read convention: always 200; an empty collection is an empty page.
 * Paging parameters are clamped by the service and echoed in the response.
 */
@Slf4j
@RestController
@RequestMapping('/api/locations')
class LocationBrowseReadController {

    private final LocationBrowseReadService browseService

    LocationBrowseReadController(LocationBrowseReadService browseService) {
        this.browseService = browseService
    }

    @GetMapping('')
    Mono<LocationBrowseRecord> browseLocations(
            @RequestParam(name = 'page', defaultValue = '0') int page,
            @RequestParam(name = 'size', defaultValue = '25') int size,
            @AuthenticationPrincipal Jwt jwt) {

        log.debug('Browsing locations: page={}, size={}', page, size)
        return browseService.browse(page, size)
    }
}
