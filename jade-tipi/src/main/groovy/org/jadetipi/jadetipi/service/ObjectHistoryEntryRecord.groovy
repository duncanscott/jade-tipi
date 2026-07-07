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
 * One applied property assignment from the {@code hst} history collection
 * (TASK-065): the assignment message's UUID (the entry's identity and its
 * chronological ordering key), the property with its resolved name (null
 * when the definition is missing), the value verbatim, and full
 * transaction provenance.
 */
@Immutable
class ObjectHistoryEntryRecord {
    String msgUuid
    String propertyId
    String propertyName
    Map<String, Object> value
    String txnId
    String commitId
    Instant appliedAt
}
