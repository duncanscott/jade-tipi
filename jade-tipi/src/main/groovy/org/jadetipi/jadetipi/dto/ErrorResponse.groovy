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
package org.jadetipi.jadetipi.dto

import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

/**
 * Standard error response for API endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class ErrorResponse {
    String message
    int status
    String error
    Instant timestamp
    Map<String, String> validationErrors

    ErrorResponse(String message, int status) {
        this.message = message
        this.status = status
        this.error = getErrorName(status)
        this.timestamp = Instant.now()
    }

    ErrorResponse(String message, int status, Map<String, String> validationErrors) {
        this(message, status)
        this.validationErrors = validationErrors
    }

    private static String getErrorName(int status) {
        switch (status) {
            case 400: return 'Bad Request'
            case 401: return 'Unauthorized'
            case 403: return 'Forbidden'
            case 404: return 'Not Found'
            case 409: return 'Conflict'
            case 413: return 'Payload Too Large'
            case 500: return 'Internal Server Error'
            default: return 'Error'
        }
    }
}
