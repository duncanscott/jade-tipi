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
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_ACTION
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_COLLECTION
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_COMMITTED_AT
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_COMMIT_DATA
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_COMMIT_ID
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_DATA
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_KAFKA
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_MSG_UUID
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_OPENED_AT
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_OPEN_DATA
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_RECEIVED_AT
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_RECORD_TYPE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_STATE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_TXN_ID
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.RECORD_TYPE_MESSAGE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.RECORD_TYPE_TRANSACTION
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.STATE_COMMITTED
import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS

/**
 * Reads a committed transaction snapshot (header + staged messages) from the
 * {@code txn} write-ahead log populated by
 * {@link TransactionMessagePersistenceService}.
 *
 * <p>The transaction header is the authoritative committed-visibility marker:
 * a snapshot is only produced when the header has
 * {@code record_type = "transaction"}, {@code state = "committed"}, and a
 * non-blank backend-generated {@code commit_id}. Older
 * {@link TransactionService}-shape documents in the same {@code txn}
 * collection (which do not carry {@code record_type}) are ignored.
 *
 * <p>The service is intentionally Kafka-free and HTTP-free; later
 * materializers or HTTP adapters can reuse it without depending on Kafka or
 * web types.
 */
@Slf4j
@Service
class CommittedTransactionReadService {

    private static final String COLLECTION_NAME = COLLECTION_TRANSACTIONS
    private static final String FIELD_ID = '_id'

    private static final String KAFKA_FIELD_TOPIC = 'topic'
    private static final String KAFKA_FIELD_PARTITION = 'partition'
    private static final String KAFKA_FIELD_OFFSET = 'offset'
    private static final String KAFKA_FIELD_TIMESTAMP_MS = 'timestamp_ms'

    private final ReactiveMongoTemplate mongoTemplate

    CommittedTransactionReadService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Returns the committed snapshot for {@code txnId}, or {@code Mono.empty()}
     * if the transaction does not exist, is not committed, lacks a
     * {@code commit_id}, or is not a WAL-shaped header (i.e. the older
     * {@link TransactionService} document shape).
     */
    Mono<CommittedTransactionSnapshot> findCommitted(String txnId) {
        Assert.hasText(txnId, 'txnId must not be blank')

        return mongoTemplate.findById(txnId, Map.class, COLLECTION_NAME)
                .filter({ Map header -> isCommittedWalHeader(header) } as java.util.function.Predicate<Map>)
                .flatMap { Map header ->
                    findMessagesForTxn(txnId)
                            .collectList()
                            .map { List<CommittedTransactionMessage> messages ->
                                buildSnapshot(header, messages)
                            }
                } as Mono<CommittedTransactionSnapshot>
    }

    private Flux<CommittedTransactionMessage> findMessagesForTxn(String txnId) {
        Query query = Query.query(
                Criteria.where(FIELD_RECORD_TYPE).is(RECORD_TYPE_MESSAGE)
                        .and(FIELD_TXN_ID).is(txnId)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

        return mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                .map(this.&toMessage)
    }

    private static boolean isCommittedWalHeader(Map header) {
        if (header == null) {
            return false
        }
        if ((header.get(FIELD_RECORD_TYPE) as String) != RECORD_TYPE_TRANSACTION) {
            return false
        }
        if ((header.get(FIELD_STATE) as String) != STATE_COMMITTED) {
            return false
        }
        String commitId = header.get(FIELD_COMMIT_ID) as String
        return commitId != null && !commitId.trim().isEmpty()
    }

    private static CommittedTransactionSnapshot buildSnapshot(Map header,
                                                              List<CommittedTransactionMessage> messages) {
        return new CommittedTransactionSnapshot(
                txnId: header.get(FIELD_TXN_ID) as String,
                state: header.get(FIELD_STATE) as String,
                commitId: header.get(FIELD_COMMIT_ID) as String,
                openedAt: header.get(FIELD_OPENED_AT) as Instant,
                committedAt: header.get(FIELD_COMMITTED_AT) as Instant,
                openData: header.get(FIELD_OPEN_DATA) as Map<String, Object>,
                commitData: header.get(FIELD_COMMIT_DATA) as Map<String, Object>,
                messages: messages
        )
    }

    private CommittedTransactionMessage toMessage(Map row) {
        return new CommittedTransactionMessage(
                msgUuid: row.get(FIELD_MSG_UUID) as String,
                collection: row.get(FIELD_COLLECTION) as String,
                action: row.get(FIELD_ACTION) as String,
                data: row.get(FIELD_DATA) as Map<String, Object>,
                receivedAt: row.get(FIELD_RECEIVED_AT) as Instant,
                kafka: toKafkaProvenance(row.get(FIELD_KAFKA))
        )
    }

    private static KafkaProvenance toKafkaProvenance(Object kafkaSubDoc) {
        if (!(kafkaSubDoc instanceof Map)) {
            return null
        }
        Map kafka = (Map) kafkaSubDoc
        return new KafkaProvenance(
                topic: kafka.get(KAFKA_FIELD_TOPIC) as String,
                partition: kafka.get(KAFKA_FIELD_PARTITION) as Integer,
                offset: kafka.get(KAFKA_FIELD_OFFSET) as Long,
                timestampMs: kafka.get(KAFKA_FIELD_TIMESTAMP_MS) as Long
        )
    }
}
