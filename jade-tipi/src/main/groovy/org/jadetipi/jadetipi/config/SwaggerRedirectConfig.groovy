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
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

import static org.springframework.web.reactive.function.server.RequestPredicates.GET

@Configuration
class SwaggerRedirectConfig {

    @Bean
    RouterFunction<ServerResponse> swaggerUiRedirect() {
        RouterFunctions
                .route(GET('/swagger-ui.html')) {
                    ServerResponse.permanentRedirect(URI.create('/swagger-ui/index.html')).build()
                }
                .andRoute(GET('/swagger-ui')) {
                    ServerResponse.permanentRedirect(URI.create('/swagger-ui/index.html')).build()
                }
    }
}
