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
import jakarta.validation.Valid
import org.jadetipi.dto.permission.Group
import org.jadetipi.dto.transaction.CommitToken
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.jadetipi.service.TransactionService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Slf4j
@RestController
@RequestMapping('/api/transactions')
class TransactionController {

    private final TransactionService transactionService

    TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService
    }

    @PostMapping(path = '/open', consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TransactionToken>> openTransaction(
            @Valid @RequestBody Group group, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Opening transaction for organization={}, group={}', group.organization(), group.group())

        return transactionService.openTransaction(group)
                .doOnSuccess { token -> log.info('Transaction opened: id={}', token.transactionId()) }
                .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
    }

    @PostMapping(path = '/commit', consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<CommitToken>> commitTransaction(
            @Valid @RequestBody TransactionToken transactionToken, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Committing transaction: id={}', transactionToken.transactionId())

        return transactionService.commitTransaction(transactionToken)
                .doOnSuccess { commit -> log.info('Transaction committed: id={}, commit={}',
                    transactionToken.transactionId(), commit.commitId()) }
                .map { commit -> ResponseEntity.ok(commit) }
    }

}
