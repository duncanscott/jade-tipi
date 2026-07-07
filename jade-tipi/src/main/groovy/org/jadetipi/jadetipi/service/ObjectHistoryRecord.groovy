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
 * One page of an object's assignment history (TASK-065): the subject's
 * identity, the optional property filter that produced the page, the
 * entries in message-UUID (chronological) order, and the effective
 * (clamped) paging parameters with the filtered total.
 */
@Immutable
class ObjectHistoryRecord {
    String objectId
    String collection
    String propertyId
    List<ObjectHistoryEntryRecord> items
    int page
    int size
    long total
}
