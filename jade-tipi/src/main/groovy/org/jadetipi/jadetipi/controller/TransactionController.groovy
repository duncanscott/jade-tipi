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

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.jadetipi.dto.transaction.TransactionRequest
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.jadetipi.service.TransactionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

@Slf4j
@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    private final TransactionService transactionService
    private final ObjectMapper objectMapper

    TransactionController(TransactionService transactionService, ObjectMapper objectMapper) {
        this.transactionService = transactionService
        this.objectMapper = objectMapper
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TransactionToken>> C(
            @RequestBody TransactionRequest request, @AuthenticationPrincipal Jwt jwt) {

        if (jwt) {
            try {
                def prettyJwt = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jwt.claims)
                log.info 'Received JWT:\n{}', prettyJwt
            } catch (Exception ex) {
                log.warn 'Unable to serialize JWT for logging', ex
            }
        } else {
            log.warn 'Received null JWT in createTransaction request'
        }

        if (!request?.organization()?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "organization is required"))
        }
        if (!request.group()?.trim()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "group is required"))
        }

        return transactionService.createTransaction(request.organization().trim(), request.group().trim())
                .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
    }

}
