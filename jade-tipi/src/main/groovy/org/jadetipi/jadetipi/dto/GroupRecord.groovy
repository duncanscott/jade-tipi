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
 * Admin-endpoint projection of a stored root-shaped {@code grp} document.
 *
 * <p>The persisted document keeps the {@code TASK-020} root-document contract
 * ({@code _id}, {@code id}, {@code collection: "grp"}, {@code properties},
 * {@code links}, {@code _head.provenance}). This DTO surfaces only the
 * fields the admin UI consumes:
 *
 * <ul>
 *   <li>{@code id} — the world-unique grp id; same as the persisted
 *       {@code _id}.</li>
 *   <li>{@code collection} — always {@code "grp"}.</li>
 *   <li>{@code name}, {@code description}, {@code permissions} — projected
 *       from {@code properties}.</li>
 *   <li>{@code head} — admin-visible projection of {@code _head} including
 *       provenance for audit display.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class GroupRecord {
    String id
    String collection
    String name
    String description
    Map<String, String> permissions
    GroupHead head
}
