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
import org.jadetipi.jadetipi.service.ObjectHistoryReadService
import org.jadetipi.jadetipi.service.ObjectHistoryRecord
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * The object assignment-history read (TASK-065), sibling of the generic
 * property-values route: {@code GET /api/objects/{id}/history} returns the
 * object's applied assignments from {@code hst} in message-UUID
 * (chronological) order, optionally narrowed to one property
 * ({@code ?property_id=...}), paged ({@code ?page=&size=}).
 *
 * <p>Same dereference rule as the property-values route (spec section 3):
 * the collection comes from the ID's collection segment; a malformed ID or
 * an unserved collection is 404 — never a guess. Resource-read convention:
 * a missing subject root is 404; an existing root with no history is an
 * empty page.
 */
@Slf4j
@RestController
@RequestMapping('/api/objects')
class ObjectHistoryReadController {

    private final ObjectHistoryReadService readService

    ObjectHistoryReadController(ObjectHistoryReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/history')
    Mono<ResponseEntity<ObjectHistoryRecord>> getObjectHistory(
            @PathVariable('id') String id,
            @RequestParam(value = 'property_id', required = false) String propertyId,
            @RequestParam(value = 'page', defaultValue = '0') int page,
            @RequestParam(value = 'size', defaultValue = '25') int size,
            @AuthenticationPrincipal Jwt jwt) {

        String collection = ObjectIdDereference.collectionOf(
                id, ObjectHistoryReadService.SUPPORTED_COLLECTIONS)
        if (collection == null) {
            log.debug('Object id does not dereference to a readable collection: id={}', id)
            return Mono.just(ResponseEntity.notFound().build())
        }

        log.debug('Retrieving object history: collection={}, id={}, propertyId={}, page={}, size={}',
                collection, id, propertyId, page, size)
        return readService.findHistory(collection, id, propertyId, page, size)
                .map { ObjectHistoryRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
