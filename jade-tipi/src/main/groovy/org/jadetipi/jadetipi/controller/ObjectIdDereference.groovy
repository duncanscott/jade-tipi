/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 *
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 *
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 *
 * https://jade-tipi.org/license
 */
package org.jadetipi.jadetipi.controller

/**
 * The object-ID dereference rule for resource reads (TASK-064, spec
 * section 3): an object ID is the complete address — exactly five
 * tilde-separated segments (spec section 1.2, schema-enforced) with the
 * collection abbreviation fourth. A malformed ID, or one naming a
 * collection the caller does not serve, dereferences to {@code null} and
 * the route answers 404 — never a guess.
 */
final class ObjectIdDereference {

    private static final String ID_SEPARATOR = '~'
    private static final int ID_SEGMENT_COUNT = 5
    private static final int COLLECTION_SEGMENT_INDEX = 3

    private ObjectIdDereference() {
    }

    /**
     * The collection segment of a well-formed object ID when it is in
     * {@code servedCollections}, otherwise {@code null}.
     */
    static String collectionOf(String id, Set<String> servedCollections) {
        if (id == null) {
            return null
        }
        String[] segments = id.split(ID_SEPARATOR, -1)
        if (segments.length != ID_SEGMENT_COUNT) {
            return null
        }
        String collection = segments[COLLECTION_SEGMENT_INDEX]
        return servedCollections.contains(collection) ? collection : null
    }
}
