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
package org.jadetipi.dto.collections;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.jadetipi.dto.util.Constants;
import org.jadetipi.dto.util.JsonMapper;
import org.jadetipi.dto.util.SchemaValidator;
import org.jadetipi.dto.util.ValidationException;

import java.util.Objects;

/**
 * A unit of measurement with optional SI prefix.
 *
 * <p>JSON structure:
 * <pre>
 * {
 *   "unit": "volt",
 *   "prefix": null,
 *   "symbol": "V",
 *   "system": "SI"
 * }
 * </pre>
 */
public record Unit(
        @JsonProperty("unit") String unit,
        @JsonProperty("prefix") String prefix,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("system") String system
) {

    private static final String SCHEMA_PATH = "/schema/unit.schema.json";

    public void validate() throws ValidationException, JsonProcessingException {
        SchemaValidator.ValidationResult result = SchemaValidator.validate(JsonMapper.toJson(this), SCHEMA_PATH);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    @JsonIgnore
    public String getId() {
        if (prefix == null) {
            return system + Constants.ID_SEPARATOR + unit;
        }
        return system + Constants.ID_SEPARATOR + unit + Constants.ID_SEPARATOR + prefix;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Unit unit1)) return false;
        return Objects.equals(unit, unit1.unit) && Objects.equals(prefix, unit1.prefix) && Objects.equals(system, unit1.system);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, prefix, system);
    }
}
