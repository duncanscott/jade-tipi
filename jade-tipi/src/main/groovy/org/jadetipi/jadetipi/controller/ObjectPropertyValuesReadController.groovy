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
 * The generic object read route (TASK-064, director-ratified 2026-07-07):
 * one route for every property-value-bearing collection, dereferencing the
 * collection from the object ID itself — the ID is the complete address.
 *
 * <p>Dereference rule: an object ID is exactly five tilde-separated segments
 * (spec section 1.2, schema-enforced) and the fourth is the collection
 * abbreviation. A malformed ID or a collection outside
 * {@link ObjectPropertyValuesReadService#SUPPORTED_COLLECTIONS} is 404 —
 * never a guess. This is the sanctioned READ-path counterpart of the
 * write-path rule that assignment messages carry {@code object_collection}
 * explicitly (spec section 2.3.1).
 *
 * <p>Resource-read convention: a missing subject root is 404.
 */
@Slf4j
@RestController
@RequestMapping('/api/objects')
class ObjectPropertyValuesReadController {

    private final ObjectPropertyValuesReadService readService

    ObjectPropertyValuesReadController(ObjectPropertyValuesReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/property-values')
    Mono<ResponseEntity<ObjectPropertyValuesRecord>> getObjectPropertyValues(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        String collection = ObjectIdDereference.collectionOf(
                id, ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS)
        if (collection == null) {
            log.debug('Object id does not dereference to a readable collection: id={}', id)
            return Mono.just(ResponseEntity.notFound().build())
        }

        log.debug('Retrieving object property values: collection={}, id={}', collection, id)
        return readService.findPropertyValues(collection, id)
                .map { ObjectPropertyValuesRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
