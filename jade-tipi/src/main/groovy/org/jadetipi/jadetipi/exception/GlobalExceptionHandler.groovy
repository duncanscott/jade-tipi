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
package org.jadetipi.jadetipi.exception

import groovy.util.logging.Slf4j
import org.jadetipi.jadetipi.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

/**
 * Global exception handler for all API endpoints.
 * Provides consistent error responses across the application.
 */
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException)
    Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex) {
        log.warn('ResponseStatusException: {} - {}', ex.statusCode, ex.reason)
        def errorResponse = new ErrorResponse(ex.reason ?: ex.message, ex.statusCode.value())
        return Mono.just(ResponseEntity.status(ex.statusCode).body(errorResponse))
    }

    @ExceptionHandler(WebExchangeBindException)
    Mono<ResponseEntity<ErrorResponse>> handleValidation(WebExchangeBindException ex) {
        log.warn('Validation failed: {} errors', ex.errorCount)
        def errors = ex.fieldErrors.collectEntries { fieldError ->
            [(fieldError.field): fieldError.defaultMessage]
        }
        def errorResponse = new ErrorResponse('Validation failed', 400, errors)
        return Mono.just(ResponseEntity.badRequest().body(errorResponse))
    }

    @ExceptionHandler(IllegalArgumentException)
    Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn('IllegalArgumentException: {}', ex.message)
        def errorResponse = new ErrorResponse(ex.message, 400)
        return Mono.just(ResponseEntity.badRequest().body(errorResponse))
    }

    @ExceptionHandler(IllegalStateException)
    Mono<ResponseEntity<ErrorResponse>> handleIllegalState(IllegalStateException ex) {
        log.warn('IllegalStateException: {}', ex.message)
        def errorResponse = new ErrorResponse(ex.message, 409)
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse))
    }

    @ExceptionHandler(Exception)
    Mono<ResponseEntity<ErrorResponse>> handleGeneral(Exception ex) {
        log.error('Unexpected error occurred', ex)
        def errorResponse = new ErrorResponse('An unexpected error occurred', 500)
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse))
    }
}
