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
// JadeTipiValidators.java
package org.jadetipi.id.validation;

import java.util.regex.Pattern;

public final class JadeTipiValidators {
    private JadeTipiValidators(){}

    private static final Pattern P_PREFIX = Pattern.compile(Patterns.PREFIX);
    private static final Pattern P_TS = Pattern.compile(Patterns.TIMESTAMP);
    private static final Pattern P_SEQ = Pattern.compile(Patterns.SEQUENCE);
    private static final Pattern P_UUID = Pattern.compile(Patterns.UUID36);
    private static final Pattern P_TYPE = Pattern.compile(Patterns.TYPE);

    public static boolean isPrefix(String s) { return s != null && P_PREFIX.matcher(s).matches(); }
    public static boolean isTimestamp(String s) { return s != null && P_TS.matcher(s).matches(); }
    public static boolean isSequence(String s) { return s != null && P_SEQ.matcher(s).matches(); }
    public static boolean isUuid(String s) { return s != null && P_UUID.matcher(s).matches(); }
    public static boolean isType(String s) { return s != null && P_TYPE.matcher(s).matches(); }
}
