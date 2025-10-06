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
package com.jadetipi.jadetipi.mongo.config

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.boot.CommandLineRunner
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component

@Slf4j
@Component
class MongoDbInitializer implements CommandLineRunner {

    private final ReactiveMongoTemplate mongoTemplate
    private final ObjectMapper objectMapper
    private static final String COLLECTION_NAME = "tipi"
    private static final String RESOURCE_PATTERN = "classpath:tipi/*.json"

    MongoDbInitializer(ReactiveMongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate
        this.objectMapper = objectMapper
    }

    @Override
    void run(String... args) throws Exception {
        log.info "Initializing MongoDB collection '{}' from resources", COLLECTION_NAME

        // Check if collection exists, create if it doesn't
        mongoTemplate.collectionExists(COLLECTION_NAME)
            .flatMap { exists ->
                if (!exists) {
                    log.info "Creating collection '{}'", COLLECTION_NAME
                    mongoTemplate.createCollection(COLLECTION_NAME)
                } else {
                    log.info "Collection '{}' already exists", COLLECTION_NAME
                    return mongoTemplate.getCollection(COLLECTION_NAME)
                }
            }
            .block()

        // Load all JSON files from the tipi directory
        def resolver = new PathMatchingResourcePatternResolver()
        Resource[] resources = resolver.getResources(RESOURCE_PATTERN)

        log.info "Found {} JSON files to load", resources.length

        resources.each { resource ->
            try {
                // Extract the base name without extension as the key
                def fileName = resource.filename
                def key = fileName.substring(0, fileName.lastIndexOf('.'))

                log.info "Loading document with key '{}' from file '{}'", key, fileName

                // Read and parse the JSON file
                def jsonContent = objectMapper.readValue(resource.inputStream, Map.class)

                // Add the key as the _id field
                jsonContent.put("_id", key)

                // Upsert the document (update if exists, insert if not)
                mongoTemplate.save(jsonContent, COLLECTION_NAME)
                    .doOnSuccess { saved ->
                        log.info "Successfully saved document with key '{}'", key
                    }
                    .doOnError { error ->
                        log.error "Error saving document with key '{}': {}", key, error.message
                    }
                    .block()

                // If this is the collections document, create collections for each entry
                if (key == "collections") {
                    log.info "Creating collections from 'collections' document"
                    jsonContent.each { collectionKey, collectionData ->
                        if (collectionKey != "_id" && collectionData instanceof Map) {
                            def collectionName = collectionData.get("name")
                            if (collectionName) {
                                mongoTemplate.collectionExists(collectionName)
                                    .flatMap { exists ->
                                        if (!exists) {
                                            log.info "Creating collection '{}'", collectionName
                                            mongoTemplate.createCollection(collectionName)
                                        } else {
                                            log.info "Collection '{}' already exists", collectionName
                                            return mongoTemplate.getCollection(collectionName)
                                        }
                                    }
                                    .block()
                            }
                        }
                    }
                }

            } catch (Exception e) {
                log.error "Error processing file '{}': {}", resource.filename, e.message, e
            }
        }

        // Create open-transactions collection
        def openTransactionsCollection = "open-transactions"
        mongoTemplate.collectionExists(openTransactionsCollection)
            .flatMap { exists ->
                if (!exists) {
                    log.info "Creating collection '{}'", openTransactionsCollection
                    mongoTemplate.createCollection(openTransactionsCollection)
                } else {
                    log.info "Collection '{}' already exists", openTransactionsCollection
                    return mongoTemplate.getCollection(openTransactionsCollection)
                }
            }
            .block()

        log.info "MongoDB initialization completed"
    }
}
