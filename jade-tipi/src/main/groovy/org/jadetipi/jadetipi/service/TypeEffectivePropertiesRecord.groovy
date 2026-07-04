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
package org.jadetipi.jadetipi.service

/**
 * Read view answering "which properties may objects of this type carry?"
 * (TASK-041): the union of {@code property_refs} on the subject {@code typ}
 * root and every ancestor reached through {@code parent_type_id}, per the
 * ratified single-inheritance direction ("a subtype inherits all the
 * properties of the parent type").
 *
 * <p>{@code typeChain} lists the walked type IDs, subject first.
 * {@code chainComplete} is {@code false} when the walk stopped early — a
 * missing ancestor root, a cycle, or the bounded depth — in which case
 * {@code effectiveProperties} still carries everything found before the
 * break. Writes gate on the same walk and fail closed; this read surfaces
 * the partial view for inspection instead.
 */
class TypeEffectivePropertiesRecord {
    String typeId
    String typeName
    List<String> typeChain
    boolean chainComplete
    Map<String, TypeEffectivePropertyRecord> effectiveProperties
}
