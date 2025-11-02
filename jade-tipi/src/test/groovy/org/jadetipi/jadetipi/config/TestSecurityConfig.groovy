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
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

@TestConfiguration
class TestSecurityConfig {

    @Bean
    @Primary
    ReactiveJwtDecoder reactiveJwtDecoder() {
        // Mock JWT decoder for tests that always rejects tokens
        return { token ->
            Mono.error(new IllegalArgumentException("JWT validation not configured for tests"))
        } as ReactiveJwtDecoder
    }

}