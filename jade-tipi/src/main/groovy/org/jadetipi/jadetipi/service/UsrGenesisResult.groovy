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
 * Outcome of the genesis bootstrap {@code usr} ensure performed by
 * {@link UsrGenesisService}.
 */
enum UsrGenesisResult {
    /** The bootstrap {@code usr} root was inserted by this ensure. */
    CREATED,
    /** The bootstrap {@code usr} root already existed (including a concurrent duplicate insert). */
    ALREADY_PRESENT
}
