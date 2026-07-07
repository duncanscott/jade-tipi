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

import groovy.transform.ToString

/**
 * Outcome of a {@link CommittedTransactionMaterializer} run over a single
 * committed transaction snapshot. The counts are intended for tests and logs;
 * the materializer is a read-after-commit projection so individual non-fatal
 * outcomes (matching duplicate, conflicting duplicate, missing {@code data.id})
 * are surfaced as counts rather than as exceptions.
 */
@ToString(includeNames = true)
class MaterializeResult {
    /** Documents inserted into a long-term collection on this run. */
    int materialized = 0
    /** Duplicate-id inserts where the existing document matched the incoming payload. */
    int duplicateMatching = 0
    /** Duplicate-id inserts where the existing document differed; not overwritten. */
    int conflictingDuplicate = 0
    /** Messages whose collection/action/kind is not in this materializer's scope. */
    int skippedUnsupported = 0
    /** Supported messages whose {@code data.id} was missing or blank; never auto-id'd. */
    int skippedInvalid = 0
    /** Supported update messages whose target root document does not yet exist in the long-term collection. */
    int skippedMissingTarget = 0
    /**
     * Supported {@code ppy + create} assignment messages whose
     * {@code data.property_id} is not registered on the target entity's type:
     * the entity root has no {@code type_id}, the referenced {@code typ} root
     * does not exist, or the {@code typ} root has no
     * {@code properties.property_refs} entry for the property.
     */
    int skippedUnregisteredProperty = 0

    /**
     * Assignments older than the target's current value (TASK-061): applied
     * into the {@code hst} history collection only, current untouched.
     */
    int appliedHistorical = 0

    /**
     * Warn-only link-reference issues observed on {@code lnk + create}
     * messages (UT-9/TASK-058): one count per issue. Deliberately excluded
     * from {@link #counters()} — a warned link still applies, so warnings
     * are not terminal apply_state outcomes.
     */
    int linkValidationWarnings = 0

    /**
     * Counter values in a fixed order, index-aligned with
     * {@code CommittedTransactionMaterializer.APPLY_STATES} — the projection
     * loop diffs consecutive snapshots of this list to name each message's
     * terminal {@code apply_state} (TASK-056). New counters append so
     * existing indices stay stable.
     */
    List<Integer> counters() {
        return [materialized, duplicateMatching, conflictingDuplicate,
                skippedUnsupported, skippedInvalid, skippedMissingTarget,
                skippedUnregisteredProperty, appliedHistorical]
    }
}
