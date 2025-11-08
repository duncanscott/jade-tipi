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

import org.jadetipi.dto.permission.Group
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
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
@RequestMapping('/api/transactions')
class TransactionController {

    private final TransactionService transactionService

    TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TransactionToken>> createTransaction(
            @RequestBody Group group, @AuthenticationPrincipal Jwt jwt) {

        if (!group?.organization()?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, 'organization is required'))
        }
        if (!group.group()?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, 'group is required'))
        }

        return transactionService.createTransaction(group)
                .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
    }

}
