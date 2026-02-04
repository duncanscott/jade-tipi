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

import org.jadetipi.dto.util.Constants;

import java.util.Map;
import java.util.Objects;

/**
 * Kafka message DTO for transaction and entity operations.
 *
 * <p>Message structure:
 * <ul>
 *   <li>txn: {@link Transaction} containing UUIDv7, grp, and client</li>
 *   <li>uuid: UUIDv7 unique to this message</li>
 *   <li>action: {@link Action} enum (OPEN, COMMIT, CREATE, UPDATE, DELETE)</li>
 *   <li>data: message-specific data</li>
 * </ul>
 *
 * <p>Message IDs are composed via {@link #getId()} as:
 * {@code <txn.idString()>-<uuid>-<action.idString()>}
 *
 * <p>Equality is based on txn and uuid only.
 */
public record Message(
        Transaction txn,
        String uuid,
        Action action,
        Map<String, Object> data
) {
    public String getId() {
        return txn.getId() + Constants.ID_SEPARATOR + uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Message message)) return false;
        return Objects.equals(uuid, message.uuid) && Objects.equals(txn, message.txn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txn, uuid);
    }
}
