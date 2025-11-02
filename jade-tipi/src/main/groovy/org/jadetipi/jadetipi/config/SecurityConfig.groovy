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

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange { exchanges ->
                    exchanges
                            .pathMatchers(
                                    '/',
                                    '/version',
                                    '/docs',
                                    '/actuator/**',
                                    '/error',
                                    '/css/**'
                            ).permitAll()
                            .anyExchange().authenticated()
                }
                .csrf { csrf -> csrf.disable() } // not needed for stateless APIs
                .oauth2ResourceServer { oauth2 -> oauth2.jwt { } } // enable JWT-based auth
        return http.build()
    }

}
