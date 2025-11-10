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
package org.jadetipi.jadetipi.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

import java.time.Instant

@TestConfiguration
class TestSecurityConfig {

    @Bean
    @Primary
    ReactiveJwtDecoder reactiveJwtDecoder() {
        // Mock JWT decoder for tests that returns a valid test JWT
        return { token ->
            Map<String, Object> headers = [
                alg: "none"
            ]
            Map<String, Object> claims = [
                sub: "test-user",
                tipi_org: "test-org",
                tipi_group: "test-grp",
                iat: Instant.now().epochSecond,
                exp: Instant.now().plusSeconds(3600).epochSecond
            ]
            Jwt jwt = new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), headers, claims)
            return Mono.just(jwt)
        } as ReactiveJwtDecoder
    }

}