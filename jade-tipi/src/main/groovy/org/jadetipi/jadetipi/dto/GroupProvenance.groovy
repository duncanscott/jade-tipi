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

import java.time.Instant

/**
 * Admin-endpoint projection of {@code _head.provenance} for a {@code grp}
 * root document. For admin direct writes, {@code txnId} and {@code commitId}
 * carry an {@code admin~<uuid>} sentinel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class GroupProvenance {
    String txnId
    String commitId
    String msgUuid
    String collection
    String action
    Instant committedAt
    Instant materializedAt
}
