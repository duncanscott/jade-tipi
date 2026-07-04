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
 * One effective property of a type: a {@code property_refs} registration
 * found on the type itself or inherited from an ancestor via
 * {@code parent_type_id}. {@code sourceTypeId} names the type that
 * registered it (the most-derived registration when a property appears at
 * multiple levels); {@code reference} is the verbatim registration metadata
 * (for example {@code {required: true}} or an empty map).
 */
class TypeEffectivePropertyRecord {
    String propertyId
    String propertyName
    String sourceTypeId
    Map<String, Object> reference
}
