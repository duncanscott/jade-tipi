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
package com.jadetipi.jadetipi.service

import com.fasterxml.jackson.databind.node.ObjectNode
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface DocumentService {

    /**
     * Creates a new document in MongoDB
     * @param id The unique identifier for the document
     * @param objectNode The Jackson ObjectNode to create
     * @return Mono of the created ObjectNode
     */
    Mono<ObjectNode> create(String id, ObjectNode objectNode)

    /**
     * Retrieves a Jackson ObjectNode from MongoDB for the given String ID
     * @param id The unique identifier for the document
     * @return Mono of the ObjectNode, or empty if not found
     */
    Mono<ObjectNode> findById(String id)

    /**
     * Updates an existing document in MongoDB
     * @param id The unique identifier for the document
     * @param objectNode The Jackson ObjectNode with updated data
     * @return Mono of the updated ObjectNode
     */
    Mono<ObjectNode> update(String id, ObjectNode objectNode)

    /**
     * Deletes a document from MongoDB
     * @param id The unique identifier for the document to delete
     * @return Mono<Boolean> true if deleted, false if not found
     */
    Mono<Boolean> delete(String id)

    /**
     * Retrieves all documents with only _id and name fields
     * @return Flux of ObjectNodes containing only _id and name
     */
    Flux<ObjectNode> findAllSummary()

    /**
     * Deletes all corrupted documents (containing Jackson metadata)
     * @return Mono with count of deleted documents
     */
    Mono<Long> deleteCorruptedDocuments()
}
