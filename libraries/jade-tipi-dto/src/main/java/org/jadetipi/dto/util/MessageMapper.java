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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jadetipi.dto.message.Message;

/**
 * Utility class for serializing and deserializing {@link Message} objects to/from JSON.
 *
 * <p>Uses a shared {@link ObjectMapper} instance configured for Message serialization.
 */
public final class MessageMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Serializes a Message to a JSON string.
     *
     * @param message the message to serialize
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJson(Message message) throws JsonProcessingException {
        return MAPPER.writeValueAsString(message);
    }

    /**
     * Serializes a Message to a pretty-printed JSON string.
     *
     * @param message the message to serialize
     * @return pretty-printed JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJsonPretty(Message message) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(message);
    }

    /**
     * Deserializes a JSON string to a Message.
     *
     * @param json the JSON string to deserialize
     * @return the deserialized Message
     * @throws JsonProcessingException if deserialization fails
     */
    public static Message fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, Message.class);
    }

    /**
     * Serializes a Message to a byte array.
     *
     * @param message the message to serialize
     * @return JSON as byte array (UTF-8)
     * @throws JsonProcessingException if serialization fails
     */
    public static byte[] toBytes(Message message) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(message);
    }

    /**
     * Deserializes a byte array to a Message.
     *
     * @param bytes the JSON byte array to deserialize
     * @return the deserialized Message
     * @throws java.io.IOException if deserialization fails
     */
    public static Message fromBytes(byte[] bytes) throws java.io.IOException {
        return MAPPER.readValue(bytes, Message.class);
    }
}
