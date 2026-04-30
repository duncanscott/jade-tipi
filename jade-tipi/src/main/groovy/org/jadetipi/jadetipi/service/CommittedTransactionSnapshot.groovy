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
 * Read-side snapshot of one committed transaction in the {@code txn}
 * write-ahead log: the transaction header plus its staged message records.
 *
 * <p>Returned by {@link CommittedTransactionReadService}. A snapshot is only
 * produced when the header is committed (state=committed and a non-blank
 * backend-generated commit_id). Producers and downstream materializers must
 * treat the header as the authoritative committed-visibility marker; child
 * messages may not carry their own commit marker.
 */
@Immutable
class CommittedTransactionSnapshot {
    String txnId
    String state
    String commitId
    Instant openedAt
    Instant committedAt
    Map<String, Object> openData
    Map<String, Object> commitData
    List<CommittedTransactionMessage> messages
}
