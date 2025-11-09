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

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI jadeTipiOpenAPI() {
        new OpenAPI()
                .info(new Info()
                        .title('Jade-Tipi API')
                        .description('Reactive REST interface for managing Jade-Tipi documents and transactions.')
                        .version('v1')
                        .contact(new Contact()
                                .name('Jade-Tipi Team')
                                .url('https://jade-tipi.org'))
                        .license(new License()
                                .name('AGPL-3.0-only OR Commercial')
                                .url('https://jade-tipi.org/license')))
    }
}
