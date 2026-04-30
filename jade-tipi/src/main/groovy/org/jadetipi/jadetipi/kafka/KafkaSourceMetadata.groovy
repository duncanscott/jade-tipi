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

import groovy.transform.Immutable

/**
 * Provenance for one Kafka record handed to the persistence service.
 *
 * <p>Kept Kafka-client free at the persistence boundary so the persistence
 * service can be reused later by an HTTP adapter without depending on
 * {@code org.apache.kafka.*} types.
 */
@Immutable
class KafkaSourceMetadata {
    String topic
    int partition
    long offset
    long timestampMs
}
