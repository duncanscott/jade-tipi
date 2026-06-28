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
import org.jadetipi.jadetipi.service.ObjectLocationsReadService
import org.jadetipi.jadetipi.service.ObjectLocationsRecord
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Thin WebFlux read adapter over {@link ObjectLocationsReadService}.
 */
@Slf4j
@RestController
@RequestMapping('/api/contents')
class ObjectLocationsReadController {

    private final ObjectLocationsReadService readService

    ObjectLocationsReadController(ObjectLocationsReadService readService) {
        this.readService = readService
    }

    @GetMapping('/by-content/{id}/locations')
    Mono<ObjectLocationsRecord> getObjectLocations(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Listing resolved locations for object: id={}', id)
        return readService.findObjectLocations(id)
    }
}
