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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.f4b6a3.uuid.UuidCreator;
import org.jadetipi.dto.util.Constants;
import org.jadetipi.dto.util.MessageMapper;
import org.jadetipi.dto.util.MessageSchemaValidator;
import org.jadetipi.dto.util.ValidationException;

import java.util.Map;
import java.util.Objects;

/**
 * Kafka message DTO for transaction and entity operations.
 *
 * <p>JSON structure:
 * <pre>
 * {
 *   "txn": { "uuid": "...", "group": {...}, "client": "..." },
 *   "uuid": "018fd849-2a40-7def-8b56-222222222222",
 *   "action": "open",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>Message ID format: {@code <txn.getId()>~<uuid>}
 *
 * <p>Equality is based on txn and uuid only.
 *
 * <p>All keys in the {@code data} map (including nested objects) must use snake_case
 * format (lowercase letters, digits, and underscores, starting with a letter).
 */
public record Message(
        @JsonProperty("txn") Transaction txn,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("action") Action action,
        @JsonProperty("data") Map<String, Object> data
) {

    public static Message newInstance(Transaction txn, Action action, Map<String, Object> data) {
        return new Message(txn, UuidCreator.getTimeOrderedEpoch().toString(), action, data);
    }

    public void validate() throws ValidationException, JsonProcessingException {
        MessageSchemaValidator.ValidationResult result = MessageSchemaValidator.validate(MessageMapper.toJson(this));
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public String getId() {
        return txn.getId() + Constants.ID_SEPARATOR + uuid + Constants.ID_SEPARATOR + action;
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
