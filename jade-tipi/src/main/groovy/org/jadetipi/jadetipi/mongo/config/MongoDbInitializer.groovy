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

    /**
     * Backend-internal collections that are not part of the wire
     * {@link Collection} vocabulary. {@code usr} holds local user/identity
     * records (spec section 1.8); creating it here keeps the collection
     * visible even when {@code jadetipi.genesis.enabled} is off. The
     * planned {@code msg} staging collection is deliberately absent until
     * the txn/msg split is implemented.
     */
    private static final List<String> BACKEND_COLLECTIONS = ['usr']

    MongoDbInitializer(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    @Override
    void run(String... args) throws Exception {
        log.info "Initializing MongoDB collections"

        Collection.values().each { collection ->
            ensureCollection(collection.abbreviation)
        }
        BACKEND_COLLECTIONS.each { collectionName ->
            ensureCollection(collectionName)
        }

        log.info "MongoDB initialization completed"
    }

    private void ensureCollection(String collectionName) {
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
