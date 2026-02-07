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

import com.networknt.schema.*;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates JSON strings against the Message JSON Schema.
 *
 * <p>Uses the schema defined in {@code schema/message.schema.json} which follows
 * JSON Schema draft-2020-12.
 *
 * <p>Example usage:
 * <pre>
 * ValidationResult result = MessageSchemaValidator.validate(jsonString);
 * if (result.isValid()) {
 *     // proceed with valid JSON
 * } else {
 *     System.err.println(result.getErrors());
 * }
 * </pre>
 */
public final class MessageSchemaValidator {

    private static final String SCHEMA_PATH = "/schema/message.schema.json";
    private static final JsonSchema SCHEMA;

    static {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();

        InputStream schemaStream = MessageSchemaValidator.class.getResourceAsStream(SCHEMA_PATH);
        if (schemaStream == null) {
            throw new IllegalStateException("Message schema not found at: " + SCHEMA_PATH);
        }
        SCHEMA = factory.getSchema(schemaStream, config);
    }

    private MessageSchemaValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a JSON string against the Message schema.
     *
     * @param json the JSON string to validate
     * @return a ValidationResult containing the validation outcome
     */
    public static ValidationResult validate(String json) {
        try {
            Set<ValidationMessage> errors = SCHEMA.validate(json, com.networknt.schema.InputFormat.JSON);
            return new ValidationResult(errors);
        } catch (Exception e) {
            return new ValidationResult(e);
        }
    }

    /**
     * Checks if a JSON string is valid against the Message schema.
     *
     * @param json the JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String json) {
        return validate(json).isValid();
    }

    /**
     * Result of a schema validation.
     */
    public static final class ValidationResult {
        private final Set<ValidationMessage> errors;
        private final Exception exception;

        private ValidationResult(Set<ValidationMessage> errors) {
            this.errors = errors;
            this.exception = null;
        }

        private ValidationResult(Exception exception) {
            this.errors = Set.of();
            this.exception = exception;
        }

        /**
         * Returns true if the JSON is valid against the schema.
         */
        public boolean isValid() {
            return errors.isEmpty() && exception == null;
        }

        /**
         * Returns the validation errors, if any.
         */
        public Set<ValidationMessage> getValidationMessages() {
            return errors;
        }

        /**
         * Returns the error messages as a list of strings.
         */
        public java.util.List<String> getErrors() {
            if (exception != null) {
                return java.util.List.of("Parse error: " + exception.getMessage());
            }
            return errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
        }

        /**
         * Returns a single string with all errors joined by newlines.
         */
        public String getErrorsAsString() {
            return String.join("\n", getErrors());
        }

        /**
         * Returns the exception if parsing failed, or null if parsing succeeded.
         */
        public Exception getException() {
            return exception;
        }
    }
}
