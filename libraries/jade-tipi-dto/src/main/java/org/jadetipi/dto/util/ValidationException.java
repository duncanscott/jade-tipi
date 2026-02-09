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
package org.jadetipi.dto.util;

import java.io.Serial;

public class ValidationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient MessageSchemaValidator.ValidationResult validationResult;

    public ValidationException(MessageSchemaValidator.ValidationResult validationResult) {
        super(validationResult.getErrorsAsString());
        this.validationResult = validationResult;
    }

    public MessageSchemaValidator.ValidationResult getValidationResult() {
        return validationResult;
    }
}
