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

import java.util.List;

/**
 * Tipi collection types.
 *
 * <p>Each collection has a full name and a three-letter abbreviation.
 * Serializes to the abbreviation in JSON: "ent", "grp", "lnk", etc.
 */
public enum Collection {
    ENTITY("entity", "ent"),
    GROUP("group", "grp"),
    LINK("link", "lnk"),
    UNIT("unit", "uni"),
    PROPERTY("property", "ppy"),
    TYPE("type", "typ"),
    TRANSACTION("transaction", "txn"),
    VALIDATION("validation", "vdn");

    private final String name;
    private final String abbreviation;
    private final List<Action> actions;

    Collection(String name, String abbreviation) {
        this.name = name;
        this.abbreviation = abbreviation;
        this.actions = "transaction".equals(name)
                ? List.of(Action.OPEN, Action.ROLLBACK, Action.COMMIT)
                : List.of(Action.CREATE, Action.UPDATE, Action.DELETE);
    }

    @JsonCreator
    public static Collection fromJson(String value) {
        for (Collection c : values()) {
            if (c.abbreviation.equals(value) || c.name.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown collection: " + value);
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public List<Action> getActions() {
        return this.actions;
    }

    @JsonValue
    public String toJson() {
        return abbreviation;
    }

    @Override
    public String toString() {
        return abbreviation;
    }
}
