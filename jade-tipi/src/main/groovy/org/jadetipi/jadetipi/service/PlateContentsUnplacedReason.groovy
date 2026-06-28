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
 * Why a {@code contents} link could not be placed into the fixed 96-well grid.
 *
 * <p>Columns are numeric, so a column failure distinguishes
 * {@link #COLUMN_MALFORMED} (not interpretable as a number) from
 * {@link #COLUMN_OUT_OF_RANGE} (a number outside 1..12). Rows are a fixed label
 * set ({@code A}..{@code H}); there is no separate parse step, so any present
 * row value that is not a usable label collapses into the single
 * {@link #ROW_INVALID}.
 */
enum PlateContentsUnplacedReason {
    POSITION_MISSING,
    POSITION_KIND_UNSUPPORTED,
    ROW_MISSING,
    ROW_INVALID,
    COLUMN_MISSING,
    COLUMN_MALFORMED,
    COLUMN_OUT_OF_RANGE
}
