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

import org.jadetipi.jadetipi.kafka.KafkaIngestProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers configuration-properties beans for the Kafka ingestion path.
 *
 * <p>Kept separate from {@code application.yml}-driven Spring Kafka auto-configuration:
 * Spring Boot creates the {@code KafkaTemplate}, listener container factory, and
 * consumer factory automatically when {@code spring-kafka} is on the classpath
 * and {@code spring.kafka.*} properties are present.
 */
@Configuration
@EnableConfigurationProperties(KafkaIngestProperties)
class KafkaIngestConfig {
}
