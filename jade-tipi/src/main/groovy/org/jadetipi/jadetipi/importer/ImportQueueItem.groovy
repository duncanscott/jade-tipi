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

import groovy.transform.Immutable

/**
 * One row of the persistent dependency-ordered import queue (TASK-059;
 * docs/architecture/bulk-import-design.md). The row id is
 * {@code <source>~<key>}; {@code seq} carries the dependency order
 * (a dependency's seq is always lower than its dependent's);
 * {@code jdtpId} is recorded when the row is emitted so later rows can
 * reference earlier imports across transaction batches.
 */
@Immutable
class ImportQueueItem {
    String id
    String source
    String key
    String kind
    String state
    long seq
    String jdtpId
    String txnId
    String error
}
