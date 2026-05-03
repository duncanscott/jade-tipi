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
 * Admin-only update-group request body. Bound to
 * {@code PUT /api/admin/groups/{id}}.
 *
 * <p>{@code PUT} fully replaces the editable fields ({@code name},
 * {@code description}, {@code permissions}). The path id is the immutable
 * group identity; the persisted {@code _id}, {@code id}, and
 * {@code collection} are not changed by the update.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class GroupUpdateRequest {
    String name
    String description
    Map<String, String> permissions
}
