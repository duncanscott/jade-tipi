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
// JadeTipiIdDto.java
package org.jadetipi.id.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jadetipi.id.validation.Patterns;
import org.jadetipi.id.validation.Slug;

public record JadeTipiIdDto(
        @NotBlank(message = "prefix is required")
        @Pattern(regexp = Patterns.PREFIX, message = "prefix must be 1–16 lowercase letters [a–z]")
        String prefix,

        @NotBlank(message = "timestamp is required")
        @Pattern(regexp = Patterns.TIMESTAMP, message = "timestamp must be 1–14 digits (epoch ms)")
        String timestamp,

        @NotBlank(message = "sequence is required")
        @Pattern(regexp = Patterns.SEQUENCE, message = "sequence must be 1–16 base-36 lowercase [0–9a–z]")
        String sequence,

        @NotBlank(message = "organization is required")
        @Slug(max = 48, separators = "-_", forbidMixedSeparators = false)
        String organization,

        @NotBlank(message = "group is required")
        @Slug(max = 48, separators = "-_", forbidMixedSeparators = false)
        String group,

        @NotBlank(message = "uuid is required")
        @Pattern(regexp = Patterns.UUID36, message = "uuid must be RFC 4122 36-char form (8-4-4-4-12 hex)")
        String uuid,

        @NotBlank(message = "type is required")
        @Pattern(regexp = Patterns.TYPE, message = "type must be 1–16 lowercase letters [a–z]")
        String type,

        // optional; if required, add @NotBlank
        @Slug(max = 30, separators = "-", forbidMixedSeparators = false)
        String subtype
) {}
