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
import org.jadetipi.jadetipi.service.ObjectPropertyValuesReadService
import org.jadetipi.jadetipi.service.ObjectPropertyValuesRecord
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Thin WebFlux read adapter over {@link ObjectPropertyValuesReadService} for
 * {@code loc} roots. Resource-read convention: a missing subject root is 404.
 * The legacy {@code GET /api/entities/{id}/property-values} route keeps its
 * transitional reader until the planned cleanup.
 */
@Slf4j
@RestController
@RequestMapping('/api/locations')
class LocationPropertyValuesReadController {

    private static final String COLLECTION_LOC = 'loc'

    private final ObjectPropertyValuesReadService readService

    LocationPropertyValuesReadController(ObjectPropertyValuesReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/property-values')
    Mono<ResponseEntity<ObjectPropertyValuesRecord>> getLocationPropertyValues(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Retrieving location property values: id={}', id)
        return readService.findPropertyValues(COLLECTION_LOC, id)
                .map { ObjectPropertyValuesRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
