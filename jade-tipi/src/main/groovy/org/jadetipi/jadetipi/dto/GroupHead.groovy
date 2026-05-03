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
 * Admin-endpoint projection of a stored root document's {@code _head}
 * sub-document. Only fields useful to the admin UI's audit display are
 * surfaced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class GroupHead {
    Integer schemaVersion
    String documentKind
    String rootId
    GroupProvenance provenance
}
