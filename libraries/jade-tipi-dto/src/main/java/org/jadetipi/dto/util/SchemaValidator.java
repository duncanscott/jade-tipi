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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates JSON strings against JSON Schemas.
 *
 * <p>Loads and caches schemas by classpath resource path. All schemas use
 * JSON Schema draft-2020-12.
 *
 * <p>Example usage:
 * <pre>
 * ValidationResult result = SchemaValidator.validate(jsonString, "/schema/message.schema.json");
 * if (result.isValid()) {
 *     // proceed with valid JSON
 * } else {
 *     System.err.println(result.getErrors());
 * }
 * </pre>
 */
public final class SchemaValidator {

    private static final Map<String, JsonSchema> SCHEMAS = new ConcurrentHashMap<>();

    private SchemaValidator() {
        // Utility class - prevent instantiation
    }

    private static JsonSchema getSchema(String schemaPath) {
        return SCHEMAS.computeIfAbsent(schemaPath, path -> {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();

            InputStream schemaStream = SchemaValidator.class.getResourceAsStream(path);
            if (schemaStream == null) {
                throw new IllegalStateException("Schema not found at: " + path);
            }
            return factory.getSchema(schemaStream, config);
        });
    }

    /**
     * Validates a JSON string against the schema at the given classpath resource path.
     *
     * @param json the JSON string to validate
     * @param schemaPath classpath resource path (e.g. "/schema/message.schema.json")
     * @return a ValidationResult containing the validation outcome
     */
    public static ValidationResult validate(String json, String schemaPath) {
        try {
            JsonSchema schema = getSchema(schemaPath);
            Set<ValidationMessage> errors = schema.validate(json, InputFormat.JSON);
            return new ValidationResult(errors);
        } catch (Exception e) {
            return new ValidationResult(e);
        }
    }

    /**
     * Checks if a JSON string is valid against the schema at the given classpath resource path.
     *
     * @param json the JSON string to validate
     * @param schemaPath classpath resource path (e.g. "/schema/message.schema.json")
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String json, String schemaPath) {
        return validate(json, schemaPath).isValid();
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
