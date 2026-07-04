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
 * Read view of one materialized object root and its projected
 * {@code property_values} (TASK-041). The legacy first-pass inline
 * {@code properties} bag is returned verbatim and deliberately separated
 * from the typed {@code propertyValues} map so reviewers can see both
 * representations during the transition.
 */
class ObjectPropertyValuesRecord {
    String objectId
    String collection
    String typeId
    Map<String, Object> properties
    Map<String, Object> links
    Map<String, Object> provenance
    Map<String, ObjectPropertyValueEntryRecord> propertyValues
}
