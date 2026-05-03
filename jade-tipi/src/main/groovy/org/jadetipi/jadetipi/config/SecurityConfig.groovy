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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration()
        // Allow all localhost ports for local development
        configuration.addAllowedOriginPattern('http://localhost:*')
        // Allow specific IP address for development on local network
        configuration.addAllowedOrigin('http://192.168.1.231:3000')
        configuration.addAllowedMethod('*') // Allow all HTTP methods
        configuration.addAllowedHeader('*') // Allow all headers
        configuration.allowCredentials = true

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration('/**', configuration)
        return source
    }

    /**
     * Builds the WebFlux JWT-to-authentication converter that emits
     * {@code ROLE_<name>} authorities from {@code realm_access.roles}. The
     * underlying {@link RealmAccessRolesAuthoritiesConverter} returns the
     * {@code Collection<GrantedAuthority>}; {@link JwtAuthenticationConverter}
     * adapts it to a {@code JwtAuthenticationToken}; and
     * {@link ReactiveJwtAuthenticationConverterAdapter} adapts that to the
     * reactive shape WebFlux expects.
     */
    @Bean
    ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter()
        delegate.setJwtGrantedAuthoritiesConverter(new RealmAccessRolesAuthoritiesConverter())
        return new ReactiveJwtAuthenticationConverterAdapter(delegate)
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter) {
        http
                .cors { cors -> cors.configurationSource(corsConfigurationSource()) } // Enable CORS
                .authorizeExchange { exchanges ->
                    exchanges
                            .pathMatchers(
                                    '/',
                                    '/hello',
                                    '/version',
                                    '/docs',
                                    '/swagger-ui/**',
                                    '/swagger-ui.html',
                                    '/webjars/**',
                                    '/v3/api-docs/**',
                                    '/actuator/**',
                                    '/error',
                                    '/css/**'
                            ).permitAll()
                            .pathMatchers('/api/admin/**').hasRole('jade-tipi-admin')
                            .anyExchange().authenticated()
                }
                .csrf { csrf -> csrf.disable() } // not needed for stateless APIs
                .oauth2ResourceServer { oauth2 ->
                    oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
                }
        return http.build()
    }

}
