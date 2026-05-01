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
import org.jadetipi.jadetipi.service.CommittedTransactionReadService
import org.jadetipi.jadetipi.service.CommittedTransactionSnapshot
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Thin WebFlux read adapter over {@link CommittedTransactionReadService}.
 *
 * <p>Exposes a single committed-transaction snapshot from the {@code txn}
 * write-ahead log. Committed visibility is delegated entirely to the read
 * service: the controller does not duplicate the
 * {@code record_type}/{@code state}/{@code commit_id} gate. A populated
 * snapshot maps to HTTP 200; {@code Mono.empty()} maps to HTTP 404 with no
 * body so callers cannot distinguish "no such id" from
 * "id exists but is not yet committed".
 */
@Slf4j
@RestController
@RequestMapping('/api/transactions')
class CommittedTransactionReadController {

    private final CommittedTransactionReadService readService

    CommittedTransactionReadController(CommittedTransactionReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/snapshot')
    Mono<ResponseEntity<CommittedTransactionSnapshot>> getSnapshot(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Retrieving committed transaction snapshot: id={}', id)
        return readService.findCommitted(id)
                .doOnNext { snapshot -> log.info(
                        'Committed snapshot returned: id={}, messages={}',
                        id, snapshot.messages?.size() ?: 0) }
                .map { snapshot -> ResponseEntity.ok(snapshot) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
