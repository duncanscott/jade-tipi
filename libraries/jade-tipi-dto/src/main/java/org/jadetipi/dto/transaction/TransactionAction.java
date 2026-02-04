/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 * <p>
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 * <p>
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 * <p>
 * https://jade-tipi.org/license
 */
package org.jadetipi.dto.transaction;

import jakarta.validation.constraints.NotBlank;

/**
 * Public token returned when a transaction is created.
 */
public record TransactionAction(
        @NotBlank(message = "txn is required")
        String transactionId,

        @NotBlank(message = "messageId is required")
        String messageId,

        @NotBlank(message = "hash is required")
        String hash,

        @NotBlank(message = "message is required")
        String message
) {
}
