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
 * One materialized {@code property_values} entry on an object root
 * (TASK-040 contract): the verbatim object-shaped value plus the transaction
 * provenance that answers "who wrote this value?" through the durable
 * {@code txn} record. {@code propertyName} is resolved from the {@code ppy}
 * definition root when present and is {@code null} for a dangling
 * {@code property_id}.
 */
@Immutable
class ObjectPropertyValueEntryRecord {
    String propertyId
    String propertyName
    Map<String, Object> value
    String txnId
    String commitId
    String msgUuid
    Instant appliedAt
}
