/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 * <p>
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 * <p>
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 * <p>
 * https://jade-tipi.org/license
 */
// Patterns.java
package org.jadetipi.id.validation;

public final class Patterns {
    // 1) prefix: letters only
    public static final String PREFIX = "^[a-z]{16}$";
    // 2) timestamp ms: digits only, ready for 2286+
    public static final String TIMESTAMP = "^[0-9]{13,15}$";
    // 3) sequence: base-36 lowercase, 1–16
    public static final String SEQUENCE = "^[0-9a-z]{1,16}$";
    // 4) tx type: letters only
    public static final String TYPE = "^[a-z]{1,16}$";
    // 5) UUID (case-insensitive accepted)
    public static final String UUID36 = "^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$";

    private Patterns() {
    }
}
