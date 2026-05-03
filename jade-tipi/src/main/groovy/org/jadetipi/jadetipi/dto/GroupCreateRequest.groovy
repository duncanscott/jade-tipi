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
package org.jadetipi.jadetipi.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Admin-only create-group request body. Bound to
 * {@code POST /api/admin/groups}. Fields:
 *
 * <ul>
 *   <li>{@code id} — optional caller-supplied world-unique grp id; if blank,
 *       the service synthesizes one.</li>
 *   <li>{@code name} — required, non-blank, ≤ 255 chars.</li>
 *   <li>{@code description} — optional, ≤ 4096 chars.</li>
 *   <li>{@code permissions} — map keyed by world-unique grp ids; values
 *       must be exactly {@code "rw"} or {@code "r"}.</li>
 * </ul>
 *
 * <p>Used only by the narrow local-development admin path. Not a wire DTO
 * for ingest/Kafka transactions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class GroupCreateRequest {
    String id
    String name
    String description
    Map<String, String> permissions
}
