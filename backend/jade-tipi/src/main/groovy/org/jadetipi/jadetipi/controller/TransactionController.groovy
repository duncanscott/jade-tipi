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

import org.jadetipi.jadetipi.service.TransactionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    private final TransactionService transactionService

    TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService
    }

    @PostMapping
    Mono<ResponseEntity<TransactionService.TransactionToken>> createTransaction(
            @RequestBody TransactionRequest request) {

        if (!request?.organization?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "organization is required"))
        }
        if (!request.group?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "group is required"))
        }

        return transactionService.createTransaction(request.organization.trim(), request.group.trim())
                .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
    }

    static class TransactionRequest {
        String organization
        String group
    }
}
