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
package org.jadetipi.jadetipi.mongo.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.util.logging.Slf4j
import org.bson.Document
import org.bson.types.ObjectId
import org.jadetipi.jadetipi.service.DocumentService
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import static org.jadetipi.jadetipi.util.Constants.COLLECTION_DOCUMENTS
import static org.jadetipi.jadetipi.util.Constants.FIELD_CHILDREN
import static org.jadetipi.jadetipi.util.Constants.FIELD_ID
import static org.jadetipi.jadetipi.util.Constants.FIELD_NAME
import static org.jadetipi.jadetipi.util.Constants.OBJECTID_PATTERN

@Slf4j
@Service
class DocumentServiceMongoDbImpl implements DocumentService {

    private final ReactiveMongoTemplate mongoTemplate
    private final ObjectMapper objectMapper
    private static final String COLLECTION_NAME = COLLECTION_DOCUMENTS

    DocumentServiceMongoDbImpl(ReactiveMongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate
        this.objectMapper = objectMapper
    }

    @Override
    Mono<ObjectNode> create(String id, ObjectNode objectNode) {
        log.debug('Creating document: id={}', id)
        objectNode.put(FIELD_ID, id)
        // Convert ObjectNode to Map to avoid Jackson metadata in MongoDB
        def map = objectMapper.convertValue(objectNode, Map.class)
        return mongoTemplate.insert(map, COLLECTION_NAME)
                .doOnSuccess { log.info('Document created in MongoDB: id={}', id) }
                .doOnError { ex -> log.error('Failed to create document in MongoDB: id={}', id, ex) }
                .map(saved -> objectMapper.convertValue(saved, ObjectNode.class))
    }

    @Override
    Mono<ObjectNode> findById(String id) {
        log.debug('Finding document: id={}', id)
        // Try to parse as ObjectId if it's a valid hex string
        def idValue
        if (id.matches(OBJECTID_PATTERN)) {
            idValue = new ObjectId(id)
        } else {
            idValue = id
        }

        return Mono.from(
                mongoTemplate.mongoDatabase
                        .map(db -> db.getCollection(COLLECTION_NAME))
                        .flatMapMany(collection -> collection.find(new Document(FIELD_ID, idValue)).limit(1))
        ).map(doc -> {
            // Convert BSON Document to plain Map, ensuring _id is a string
            def map = new LinkedHashMap(doc)
            def idVal = map.get(FIELD_ID)
            if (idVal != null) {
                map.put(FIELD_ID, idVal.toString())
            }
            log.debug('Document found: id={}', id)
            return objectMapper.convertValue(map, ObjectNode.class)
        })
    }

    @Override
    Mono<ObjectNode> update(String id, ObjectNode objectNode) {
        log.debug('Updating document: id={}', id)
        objectNode.put(FIELD_ID, id)
        def map = objectMapper.convertValue(objectNode, Map.class)
        return mongoTemplate.save(map, COLLECTION_NAME)
                .doOnSuccess { log.info('Document updated in MongoDB: id={}', id) }
                .doOnError { ex -> log.error('Failed to update document in MongoDB: id={}', id, ex) }
                .map(saved -> objectMapper.convertValue(saved, ObjectNode.class))
    }

    @Override
    Mono<Boolean> delete(String id) {
        log.debug('Deleting document: id={}', id)
        Query query = new Query(Criteria.where(FIELD_ID).is(id))
        return mongoTemplate.remove(query, Map.class, COLLECTION_NAME)
                .doOnSuccess { result ->
                    if (result.deletedCount > 0) {
                        log.info('Document deleted from MongoDB: id={}', id)
                    } else {
                        log.debug('Document not found for deletion: id={}', id)
                    }
                }
                .map(result -> result.deletedCount > 0)
    }

    @Override
    Flux<ObjectNode> findAllSummary() {
        log.debug('Finding all documents (summary)')
        Query query = new Query()
        query.fields().include(FIELD_ID, FIELD_NAME)
        return mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                .map(map -> {
                    // Ensure _id is always a string
                    def id = map.get(FIELD_ID)
                    if (id != null) {
                        map.put(FIELD_ID, id.toString())
                    }
                    return objectMapper.convertValue(map, ObjectNode.class)
                })
    }

    @Override
    Mono<Long> deleteCorruptedDocuments() {
        log.info('Deleting corrupted documents with {} field', FIELD_CHILDREN)
        Query query = new Query(Criteria.where(FIELD_CHILDREN).exists(true))
        return mongoTemplate.remove(query, COLLECTION_NAME)
                .doOnSuccess { result -> log.info('Deleted {} corrupted documents', result.deletedCount) }
                .map(result -> result.deletedCount)
    }
}
