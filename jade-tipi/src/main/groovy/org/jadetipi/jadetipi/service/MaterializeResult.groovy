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
}
