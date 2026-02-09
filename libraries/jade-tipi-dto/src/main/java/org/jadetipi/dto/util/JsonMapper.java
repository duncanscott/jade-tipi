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

/**
 * Utility class for serializing and deserializing objects to/from JSON.
 *
 * <p>Uses a shared {@link ObjectMapper} instance.
 */
public final class JsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param object the object to serialize
     * @return JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return MAPPER.writeValueAsString(object);
    }

    /**
     * Serializes an object to a pretty-printed JSON string.
     *
     * @param object the object to serialize
     * @return pretty-printed JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJsonPretty(Object object) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * Deserializes a JSON string to an object of the specified type.
     *
     * @param json the JSON string to deserialize
     * @param type the target class
     * @param <T> the target type
     * @return the deserialized object
     * @throws JsonProcessingException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        return MAPPER.readValue(json, type);
    }

    /**
     * Serializes an object to a byte array.
     *
     * @param object the object to serialize
     * @return JSON as byte array (UTF-8)
     * @throws JsonProcessingException if serialization fails
     */
    public static byte[] toBytes(Object object) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(object);
    }

    /**
     * Deserializes a byte array to an object of the specified type.
     *
     * @param bytes the JSON byte array to deserialize
     * @param type the target class
     * @param <T> the target type
     * @return the deserialized object
     * @throws java.io.IOException if deserialization fails
     */
    public static <T> T fromBytes(byte[] bytes, Class<T> type) throws java.io.IOException {
        return MAPPER.readValue(bytes, type);
    }
}
