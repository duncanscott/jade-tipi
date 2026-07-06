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
package org.jadetipi.jadetipi.importer

/**
 * How one source container kind (an ESP {@code type_name} or Clarity
 * {@code json.type.name} label) maps onto the Jade-Tipi typed container
 * model: the {@code typ} root to reference as {@code type_id}, the D4
 * identifier slug (for example {@code freezer} in
 * {@code ...~loc~esp_freezer_019a3a62-8fa8}), and — for kinds that act as
 * containers — the D3 {@code position.kind} used on {@code contents} links
 * where this kind is the parent.
 */
class ClarityEspKindMapping {
    String typeId
    String idSlug
    String positionKind
}
