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
package org.jadetipi.jadetipi.exception

/**
 * Thrown when a Kafka transaction message tries to write a document whose
 * natural {@code _id} already exists in {@code txn} with a different payload.
 * Idempotent re-delivery (same payload) is treated as success and never
 * raises this exception.
 */
class ConflictingDuplicateException extends RuntimeException {
    final String recordId

    ConflictingDuplicateException(String recordId, String message) {
        super(message)
        this.recordId = recordId
    }
}
