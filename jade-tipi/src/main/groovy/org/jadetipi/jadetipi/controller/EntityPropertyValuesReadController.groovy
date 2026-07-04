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
 * Thin WebFlux read adapter over {@link ObjectPropertyValuesReadService}.
 */
@Slf4j
@RestController
@RequestMapping('/api/entities')
class EntityPropertyValuesReadController {

    private final ObjectPropertyValuesReadService readService

    EntityPropertyValuesReadController(ObjectPropertyValuesReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/property-values')
    Mono<ResponseEntity<ObjectPropertyValuesRecord>> getPropertyValues(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Retrieving entity property values: id={}', id)
        return readService.findPropertyValues('ent', id)
                .map { ObjectPropertyValuesRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
