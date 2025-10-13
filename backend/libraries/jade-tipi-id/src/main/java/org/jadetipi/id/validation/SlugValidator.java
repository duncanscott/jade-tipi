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
// SlugValidator.java
package org.jadetipi.id.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public final class SlugValidator implements ConstraintValidator<Slug, String> {
    private int max;
    private String seps;
    private boolean forbidMixed;

    @Override
    public void initialize(Slug ann) {
        this.max = ann.max();
        this.seps = ann.separators();
        this.forbidMixed = ann.forbidMixedSeparators();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true; // use @NotNull where needed
        if (value.isEmpty() || value.length() > max) {
            fail(ctx, "length must be between 1 and " + max + " characters");
            return false;
        }
        // must be lowercase ascii only
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!isLowerLetter(ch) && !isDigit(ch) && seps.indexOf(ch) < 0) {
                fail(ctx, "contains invalid character '" + ch + "'");
                return false;
            }
        }
        // start with letter, end with letter or digit
        if (!isLowerLetter(value.charAt(0))) {
            fail(ctx, "must start with a lowercase letter");
            return false;
        }
        char last = value.charAt(value.length() - 1);
        if (!(isLowerLetter(last) || isDigit(last))) {
            fail(ctx, "must end with a letter or digit");
            return false;
        }
        // no leading/trailing separator already covered by start/end rule; check doubles and mixing
        boolean seenAnySep = false;
        Character firstSep = null;
        for (int i = 1; i < value.length(); i++) {
            char prev = value.charAt(i - 1);
            char ch = value.charAt(i);
            boolean prevIsSep = seps.indexOf(prev) >= 0;
            boolean chIsSep = seps.indexOf(ch) >= 0;
            if (prevIsSep && chIsSep) {
                fail(ctx, "must not contain consecutive separators");
                return false;
            }
            if (chIsSep) {
                if (!seenAnySep) {
                    seenAnySep = true;
                    firstSep = ch;
                } else if (forbidMixed && firstSep != null && ch != firstSep) {
                    fail(ctx, "must not mix different separators");
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isLowerLetter(char c) { return c >= 'a' && c <= 'z'; }
    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static void fail(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
