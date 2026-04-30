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
package org.jadetipi.jadetipi.service

import groovy.transform.Immutable

/**
 * Read-side Kafka provenance for a committed transaction message.
 *
 * <p>Mirrors the {@code kafka} sub-document persisted by the write-side
 * {@code TransactionMessagePersistenceService}. Kept distinct from the
 * write-side {@code kafka.KafkaSourceMetadata} so the read service does not
 * import the {@code kafka} package; later non-Kafka callers can reuse this
 * value object without introducing the write-side type.
 */
@Immutable
class KafkaProvenance {
    String topic
    Integer partition
    Long offset
    Long timestampMs
}
