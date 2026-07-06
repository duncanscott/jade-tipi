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
 * Registry of the already-established Jade-Tipi container model that the
 * import mapper targets (TASK-043): the {@code ppy} definition IDs for the
 * source-present domain facts, the {@code contents} link type, the base
 * {@code container} type used as the fallback for unmapped ESP container
 * kinds, and the per-source-kind mappings.
 *
 * <p>The importer deliberately does not create the model itself; the caller
 * establishes it (typically as its own transaction) and passes the IDs in.
 */
class ClarityEspContainerModel {
    String propertyNameId
    String propertyBarcodeId
    String contentsTypeId
    String fallbackContainerTypeId
    Map<String, ClarityEspKindMapping> kindMappings = [:]

    ClarityEspKindMapping mappingFor(String sourceKind) {
        return sourceKind == null ? null : kindMappings[sourceKind]
    }
}
