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

import groovy.transform.Immutable

/**
 * One contained object in a plate-shaped contents view.
 */
@Immutable
class PlateContentsEntryRecord {
    String linkId
    String typeId
    String objectId
    Map<String, Object> position
    Map<String, Object> linkProvenance
    EntityPropertyValuesRecord entity
}
