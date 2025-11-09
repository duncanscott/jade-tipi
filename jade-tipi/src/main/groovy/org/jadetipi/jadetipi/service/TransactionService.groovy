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

import groovy.util.logging.Slf4j
import org.jadetipi.dto.permission.Group
import org.jadetipi.dto.transaction.CommitToken
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.id.IdGenerator
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Mono

import java.time.Instant

import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS
import static org.jadetipi.jadetipi.util.Constants.TRANSACTION_ID_SEPARATOR

/**
 * Generates transaction identifiers and secrets, and persists them to MongoDB.
 */
@Slf4j
@Service
class TransactionService {

    private static final String COLLECTION_NAME = COLLECTION_TRANSACTIONS

    private final ReactiveMongoTemplate mongoTemplate
    private final IdGenerator idGenerator

    TransactionService(ReactiveMongoTemplate mongoTemplate, IdGenerator idGenerator) {
        this.mongoTemplate = mongoTemplate
        this.idGenerator = idGenerator
    }

    /**
     * Opens a new transaction record and returns its public/secret components.
     */
    Mono<TransactionToken> openTransaction(Group group) {
        Assert.notNull(group, 'group must not be null')
        Assert.hasText(group.organization(), 'organization must not be blank')
        Assert.hasText(group.group(), 'group must not be blank')

        String transactionId = nextId(group)
        String secret = idGenerator.nextKey()

        log.debug('Opening transaction: id={}', transactionId)

        Map<String, Object> txn = [
                id        : transactionId,
                secret    : secret,
                commit    : null,
                opened    : Instant.now(),
                committed : null
        ] as Map<String,Object>

        Map<String, Object> grp = [
                organization : group.organization(),
                group        : group.group()
        ] as Map<String,Object>

        Map<String, Object> doc = [
                _id : transactionId,
                grp : grp,
                txn : txn
        ] as Map<String,Object>

        return mongoTemplate.save(doc, COLLECTION_NAME)
                .doOnSuccess { log.info('Transaction opened: id={}', transactionId) }
                .doOnError { ex -> log.error('Failed to open transaction: id={}', transactionId, ex) }
                .thenReturn(new TransactionToken(transactionId, secret, group))
    }

    /**
     * Commits a transaction and returns a commit token.
     */
    Mono<CommitToken> commitTransaction(TransactionToken transactionToken) {
        Assert.notNull(transactionToken, 'transactionToken must not be null')
        Assert.hasText(transactionToken.transactionId(), 'transactionId must not be blank')
        Assert.hasText(transactionToken.secret(), 'secret must not be blank')
        Assert.notNull(transactionToken.group(), 'group must not be null')
        Assert.hasText(transactionToken.group().organization(), 'organization must not be blank')
        Assert.hasText(transactionToken.group().group(), 'group must not be blank')

        String commitId = nextId(transactionToken.group())
        log.debug('Committing transaction: id={}, commit={}', transactionToken.transactionId(), commitId)

        return mongoTemplate.findById(transactionToken.transactionId(), Map.class, COLLECTION_NAME)
                .switchIfEmpty(Mono.error(new IllegalArgumentException('Transaction not found')))
                .flatMap { Map doc ->
                    Map<String, Object> txn = doc.get('txn') as Map<String, Object>
                    if (txn == null) {
                        log.error('Transaction document missing txn field: id={}', transactionToken.transactionId())
                        return Mono.error(new IllegalStateException('Invalid transaction document structure'))
                    }

                    String storedSecret = txn.get('secret') as String
                    if (storedSecret != transactionToken.secret()) {
                        log.warn('Invalid secret for transaction: id={}', transactionToken.transactionId())
                        return Mono.error(new IllegalArgumentException('Invalid transaction secret'))
                    }
                    if (txn.get('commit') != null) {
                        log.warn('Transaction already committed: id={}', transactionToken.transactionId())
                        return Mono.error(new IllegalStateException('Transaction already committed'))
                    }

                    txn.put('commit', commitId)
                    txn.put('committed', Instant.now())

                    return mongoTemplate.save(doc, COLLECTION_NAME)
                            .doOnSuccess { log.info('Transaction committed: id={}, commit={}',
                                transactionToken.transactionId(), commitId) }
                            .doOnError { ex -> log.error('Failed to commit transaction: id={}',
                                transactionToken.transactionId(), ex) }
                            .thenReturn(new CommitToken(transactionToken.transactionId(), commitId))
                } as Mono<CommitToken>
    }

    /**
     * Generates a transaction identifier with format: {tipiId}~{organization}~{group}
     *
     * Example: "abc123xyz~jade-tipi_org~some-group"
     *
     * The transaction ID is globally unique and contains:
     * - tipiId: Random identifier from IdGenerator (20 chars, base62)
     * - organization: Organization identifier
     * - group: Group identifier within organization
     * - separator: '~' character (TRANSACTION_ID_SEPARATOR)
     *
     * This format allows:
     * - Easy extraction of organization/group from transaction ID
     * - Natural shard key for distributed deployments
     * - Human-readable transaction identification
     * - Hierarchical organization of transactions
     *
     * @param group The group containing organization and group identifiers
     * @return A globally unique transaction ID in the format tipiId~organization~group
     */
    private String nextId(Group group) {
        StringBuilder sb = new StringBuilder(idGenerator.nextId())
        sb.append(TRANSACTION_ID_SEPARATOR)
        sb.append(group.organization())
        sb.append(TRANSACTION_ID_SEPARATOR)
        sb.append(group.group())
        return sb.toString()
    }
}
