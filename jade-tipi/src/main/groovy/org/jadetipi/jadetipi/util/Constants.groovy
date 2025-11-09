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
package org.jadetipi.jadetipi.util

/**
 * Application-wide constants.
 */
class Constants {

    // Document limits
    static final int MAX_DOCUMENT_SIZE_BYTES = 1_000_000  // 1MB
    static final String FIELD_DELETED_COUNT = 'deletedCount'

    // MongoDB ObjectId pattern
    static final String OBJECTID_PATTERN = '^[0-9a-fA-F]{24}$'

    // Collection names
    static final String COLLECTION_DOCUMENTS = 'objectNodes'
    static final String COLLECTION_TRANSACTIONS = 'txn'
    static final String COLLECTION_TIPI = 'tipi'

    // Transaction
    static final String TRANSACTION_ID_SEPARATOR = '~'

    // Document fields
    static final String FIELD_ID = '_id'
    static final String FIELD_NAME = 'name'
    static final String FIELD_CHILDREN = '_children'  // Legacy field for corrupted documents

    private Constants() {
        throw new UnsupportedOperationException('This is a utility class and cannot be instantiated')
    }
}
