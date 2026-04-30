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

import java.time.Instant

/**
 * One staged message record belonging to a committed transaction snapshot.
 *
 * <p>Carries the fields a later materializer or HTTP layer needs to route the
 * message: {@code collection} (abbreviation), {@code action}, {@code data},
 * {@code msgUuid}, and Kafka provenance. Domain-level reference validation is
 * intentionally not performed at this boundary.
 */
@Immutable
class CommittedTransactionMessage {
    String msgUuid
    String collection
    String action
    Map<String, Object> data
    Instant receivedAt
    KafkaProvenance kafka
}
