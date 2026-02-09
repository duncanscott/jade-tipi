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
// Slug.java
package org.jadetipi.id.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SlugValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Slug {
    String message() default
            "must be 1–{max} chars, start with a letter, end with a letter/digit, " +
                    "use single separators only ({separators}), and no consecutive separators";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Maximum length (inclusive). */
    int max();

    /** Allowed separator characters inside the slug. */
    String separators() default "-_";  // org/group: "-_", subtype often "-"

    /** If true, mixing different separators is forbidden (e.g., both '-' and '_' in same value). */
    boolean forbidMixedSeparators() default false;
}
