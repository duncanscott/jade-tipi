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
package org.jadetipi.dto.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jadetipi.dto.permission.Group;

/**
 * Public token returned when a transaction is created.
 */
public record TransactionToken(
        @NotBlank(message = "id is required")
        String id,

        @NotBlank(message = "secret is required")
        String secret,

        @Valid
        @NotNull(message = "grp is required")
        Group grp
) {
}
