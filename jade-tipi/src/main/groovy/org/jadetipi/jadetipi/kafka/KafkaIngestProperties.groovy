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
package org.jadetipi.jadetipi.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the Kafka transaction-message ingestion path.
 *
 * <p>Bound from {@code jadetipi.kafka.*} in application configuration.
 */
@ConfigurationProperties(prefix = 'jadetipi.kafka')
class KafkaIngestProperties {

    /**
     * Java regex topic pattern that the transaction-message listener subscribes to.
     * Default targets the design pattern {@code jdtp-txn-.*} and the local
     * docker-compose topic {@code jdtp_cli_kli}.
     */
    String txnTopicPattern = 'jdtp-txn-.*|jdtp_cli_kli'

    /**
     * When false, the listener container is created but does not auto-start.
     * Useful for tests and environments without a Kafka broker.
     */
    boolean enabled = true

    /**
     * Maximum time the listener thread will wait for the persistence Mono to
     * complete before failing the record (and triggering Kafka retry).
     */
    int persistTimeoutSeconds = 30
}
