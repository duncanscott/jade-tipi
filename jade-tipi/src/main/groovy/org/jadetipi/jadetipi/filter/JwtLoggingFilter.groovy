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
package org.jadetipi.jadetipi.filter

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

import java.security.Principal

@Slf4j
@Component
@Order(2147483637)
class JwtLoggingFilter implements WebFilter {

    private final ObjectMapper objectMapper

    JwtLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper
    }

    @Override
    Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .doOnNext { Principal principal -> logJwtIfPresent(principal, exchange) }
                .onErrorResume { ex ->
                    log.warn('Failed to resolve principal for JWT logging', ex)
                    Mono.empty()
                }
                .then(chain.filter(exchange))
    }

    private void logJwtIfPresent(Principal principal, ServerWebExchange exchange) {
        Jwt jwt = extractJwt(principal)
        if (!jwt) {
            return
        }

        try {
            def pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jwt.claims)
            log.info('JWT for {} {}:\n{}', exchange.request.method, exchange.request.path.value(), pretty)
        } catch (Exception ex) {
            log.warn('Unable to serialize JWT for logging', ex)
        }
    }

    private Jwt extractJwt(Principal principal) {
        if (!principal) {
            return null
        }

        if (principal instanceof JwtAuthenticationToken) {
            return (principal as JwtAuthenticationToken).token
        }

        if (principal instanceof AbstractAuthenticationToken) {
            def credentials = (principal as AbstractAuthenticationToken).credentials
            if (credentials instanceof Jwt) {
                return credentials as Jwt
            }
        }

        if (principal instanceof Jwt) {
            return principal as Jwt
        }

        return null
    }
}
