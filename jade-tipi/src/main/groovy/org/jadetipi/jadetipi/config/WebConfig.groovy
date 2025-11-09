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

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.EnableWebFlux

/**
 * WebFlux configuration.
 *
 * Note: CORS configuration has been consolidated into SecurityConfig to avoid duplication.
 * See SecurityConfig.corsConfigurationSource() for all CORS settings.
 */
@Configuration
@EnableWebFlux
class WebConfig {
    // CORS configuration removed - see SecurityConfig
}
