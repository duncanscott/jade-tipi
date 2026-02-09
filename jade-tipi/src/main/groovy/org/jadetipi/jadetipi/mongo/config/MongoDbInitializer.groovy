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
package org.jadetipi.jadetipi.mongo.config

import groovy.util.logging.Slf4j
import org.jadetipi.dto.message.Collection
import org.springframework.boot.CommandLineRunner
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Component

@Slf4j
@Component
class MongoDbInitializer implements CommandLineRunner {

    private final ReactiveMongoTemplate mongoTemplate
    private static final String COLLECTION_NAME = "tipi"

    MongoDbInitializer(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    @Override
    void run(String... args) throws Exception {
        log.info "Initializing MongoDB collections"

        // Create the tipi metadata collection
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

        // Create collections from the Collection enum
        Collection.values().each { collection ->
            def collectionName = collection.abbreviation
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

        log.info "MongoDB initialization completed"
    }
}
