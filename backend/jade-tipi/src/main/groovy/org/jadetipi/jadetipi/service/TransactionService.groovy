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
package org.jadetipi.jadetipi.service

import groovy.transform.Immutable
import org.jadetipi.id.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono

import java.time.Instant

/**
 * Generates transaction identifiers and secrets, and persists them to MongoDB.
 */
@Service
class TransactionService {

    private static final String COLLECTION_NAME = "transaction"

    private final ReactiveMongoTemplate mongoTemplate
    private final IdGenerator idGenerator

    TransactionService(ReactiveMongoTemplate mongoTemplate, IdGenerator idGenerator) {
        this.mongoTemplate = mongoTemplate
        this.idGenerator = idGenerator
    }

    /**
     * Creates a new transaction record and returns its public/secret components.
     */
    Mono<TransactionToken> createTransaction(String organization, String group) {
        Assert.hasText(organization, "organization must not be blank")
        Assert.hasText(group, "group must not be blank")

        String baseId = idGenerator.nextId()
        String transactionId = "${baseId}~${organization}~${group}"
        String secret = idGenerator.nextKey()

        Map<String, Object> doc = [
                _id          : transactionId,
                organization : organization,
                group        : group,
                secret       : secret,
                createdAt    : Instant.now()
        ]

        return mongoTemplate.save(doc, COLLECTION_NAME)
                .thenReturn(new TransactionToken(transactionId, secret))
    }

    @Immutable
    static class TransactionToken {
        String transactionId
        String secret
    }
}
