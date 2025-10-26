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
package org.jadetipi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical transport object describing a Jade-Tipi document.
 */
public record DocumentDto(
        @NotBlank(message = "id is required")
        String id,

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be 120 characters or fewer")
        String name,

        @Size(max = 512, message = "description must be 512 characters or fewer")
        String description,

        @NotNull(message = "payload is required")
        Map<String, Object> payload,

        @NotNull(message = "updatedAt is required")
        Instant updatedAt
) {
}
