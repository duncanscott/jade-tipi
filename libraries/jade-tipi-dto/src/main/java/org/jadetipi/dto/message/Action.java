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
package org.jadetipi.dto.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Action types for Kafka messages.
 *
 * <p>Serializes to lowercase JSON strings: "open", "commit", "create", "update", "delete".
 */
public enum Action {
    OPEN,
    COMMIT,
    CREATE,
    UPDATE,
    DELETE;

    @JsonCreator
    public static Action fromJson(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
