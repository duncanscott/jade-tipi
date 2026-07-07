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
 * Outcome of one drive run (TASK-066): how many batches (= transactions)
 * were published, how many queue items completed or failed, how many
 * messages went to the topic, and the transaction ids for the audit
 * trail. A failed item count above zero is the CLI's non-zero exit.
 */
@Immutable
class ImportDriveReport {
    long batches
    long itemsDone
    long itemsFailed
    long messagesPublished
    List<String> txnIds
}
