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

import org.jadetipi.dto.permission.Group
import org.jadetipi.dto.transaction.CommitToken
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.id.IdGenerator
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Mono

import java.time.Instant
/**
 * Generates transaction identifiers and secrets, and persists them to MongoDB.
 */
@Service
class TransactionService {

    private static final String COLLECTION_NAME = 'transaction'
    private static final String SEPARATOR = '~'

    private final ReactiveMongoTemplate mongoTemplate
    private final IdGenerator idGenerator

    TransactionService(ReactiveMongoTemplate mongoTemplate, IdGenerator idGenerator) {
        this.mongoTemplate = mongoTemplate
        this.idGenerator = idGenerator
    }

    /**
     * Creates a new transaction record and returns its public/secret components.
     */
    Mono<TransactionToken> createTransaction(Group group) {
        Assert.hasText(group.organization(), 'organization must not be blank')
        Assert.hasText(group.group(), 'group must not be blank')

        String transactionId = nextId(group)
        String secret = idGenerator.nextKey()

        Map<String, Object> doc = [
                _id          : transactionId,
                organization : group.organization(),
                group        : group.group(),
                secret       : secret,
                created    : Instant.now()
        ] as Map<String,Object>

        return mongoTemplate.save(doc, COLLECTION_NAME)
                .thenReturn(new TransactionToken(transactionId, secret))
    }

    /**
     * Commits a transaction and returns a commit token.
     */
    Mono<CommitToken> commitTransaction(TransactionToken transactionToken, Group group) {
        Assert.notNull(transactionToken, 'transactionToken must not be null')
        Assert.hasText(transactionToken.transactionId(), 'transactionId must not be blank')
        Assert.hasText(transactionToken.secret(), 'secret must not be blank')
        Assert.hasText(group.organization(), 'organization must not be blank')
        Assert.hasText(group.group(), 'group must not be blank')

        String commitId = nextId(group)
        return mongoTemplate.findById(transactionToken.transactionId(), Map.class, COLLECTION_NAME)
                .switchIfEmpty(Mono.error(new IllegalArgumentException('Transaction not found')))
                .flatMap { Map doc ->
                    String storedSecret = doc.get('secret') as String
                    if (storedSecret != transactionToken.secret()) {
                        return Mono.error(new IllegalArgumentException('Invalid transaction secret'))
                    }

                    doc.put('commit', commitId)
                    doc.put('committed', Instant.now())

                    return mongoTemplate.save(doc, COLLECTION_NAME)
                            .thenReturn(new CommitToken(transactionToken.transactionId(), commitId))
                } as Mono<CommitToken>
    }

    private String nextId(Group group) {
        StringBuilder sb = new StringBuilder(idGenerator.nextId())
        sb.append(SEPARATOR)
        sb.append(group.organization())
        sb.append(SEPARATOR)
        sb.append(group.group())
        return sb.toString()
    }
}
