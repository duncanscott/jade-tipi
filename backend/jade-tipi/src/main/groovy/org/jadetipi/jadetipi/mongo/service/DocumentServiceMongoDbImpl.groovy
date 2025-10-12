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
import org.jadetipi.jadetipi.service.DocumentService
import groovy.util.logging.Slf4j
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Slf4j
@Service
class DocumentServiceMongoDbImpl implements DocumentService {

    private final ReactiveMongoTemplate mongoTemplate
    private final ObjectMapper objectMapper
    private static final String COLLECTION_NAME = "objectNodes"

    DocumentServiceMongoDbImpl(ReactiveMongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate
        this.objectMapper = objectMapper
    }

    @Override
    Mono<ObjectNode> create(String id, ObjectNode objectNode) {
        log.info 'creating MongoDB document for ID {}', id
        objectNode.put("_id", id)
        // Convert ObjectNode to Map to avoid Jackson metadata in MongoDB
        def map = objectMapper.convertValue(objectNode, Map.class)
        return mongoTemplate.insert(map, COLLECTION_NAME)
                .map(saved -> objectMapper.convertValue(saved, ObjectNode.class))
    }

    @Override
    Mono<ObjectNode> findById(String id) {
        // Try to parse as ObjectId if it's a valid hex string
        def idValue
        if (id.matches('^[0-9a-fA-F]{24}$')) {
            idValue = new ObjectId(id)
        } else {
            idValue = id
        }

        return Mono.from(
            mongoTemplate.mongoDatabase
                .map(db -> db.getCollection(COLLECTION_NAME))
                .flatMapMany(collection -> collection.find(new Document("_id", idValue)).limit(1))
        ).map(doc -> {
            // Convert BSON Document to plain Map, ensuring _id is a string
            def map = new LinkedHashMap(doc)
            def idVal = map.get("_id")
            if (idVal != null) {
                map.put("_id", idVal.toString())
            }
            return objectMapper.convertValue(map, ObjectNode.class)
        })
    }

    @Override
    Mono<ObjectNode> update(String id, ObjectNode objectNode) {
        objectNode.put("_id", id)
        def map = objectMapper.convertValue(objectNode, Map.class)
        return mongoTemplate.save(map, COLLECTION_NAME)
                .map(saved -> objectMapper.convertValue(saved, ObjectNode.class))
    }

    @Override
    Mono<Boolean> delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id))
        return mongoTemplate.remove(query, Map.class, COLLECTION_NAME)
                .map(result -> result.deletedCount > 0)
    }

    @Override
    Flux<ObjectNode> findAllSummary() {
        Query query = new Query()
        query.fields().include("_id", "name")
        return mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                .map(map -> {
                    // Ensure _id is always a string
                    def id = map.get("_id")
                    if (id != null) {
                        map.put("_id", id.toString())
                    }
                    return objectMapper.convertValue(map, ObjectNode.class)
                })
    }

    @Override
    Mono<Long> deleteCorruptedDocuments() {
        Query query = new Query(Criteria.where("_children").exists(true))
        return mongoTemplate.remove(query, COLLECTION_NAME)
                .map(result -> result.deletedCount)
    }
}
