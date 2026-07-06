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
package org.jadetipi.jadetipi.importer

/**
 * One canonical JDTP message payload produced by the import mapper
 * (TASK-043): the target {@code collection} abbreviation, the
 * {@code action}, and the message {@code data}. Transport concerns —
 * transaction identity, message UUIDs, Kafka — stay with the caller.
 */
class MappedImportMessage {
    String collection
    String action
    Map<String, Object> data
}
