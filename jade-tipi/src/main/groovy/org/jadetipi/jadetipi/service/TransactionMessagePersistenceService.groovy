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

import com.mongodb.DuplicateKeyException
import groovy.util.logging.Slf4j
import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Collection
import org.jadetipi.dto.message.Message
import org.jadetipi.id.IdGenerator
import org.jadetipi.jadetipi.exception.ConflictingDuplicateException
import org.jadetipi.jadetipi.kafka.KafkaSourceMetadata
import org.springframework.dao.DuplicateKeyException as SpringDuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

import java.time.Instant

import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS
import static org.jadetipi.jadetipi.util.Constants.TRANSACTION_ID_SEPARATOR

/**
 * Persists canonical Jade-Tipi {@link Message} records into MongoDB's {@code txn}
 * collection as the durable transaction write-ahead log.
 *
 * <p>Two record kinds live in {@code txn}:
 * <ul>
 *   <li>Header (record_type=transaction): {@code _id = txn_id}, holds the
 *       lifecycle state — {@code open}, then terminally {@code committed}
 *       (with an orderable backend-generated {@code commit_id}) or
 *       {@code rolled_back} (with {@code rolled_back_at} and the rollback
 *       message's {@code rollback_data} as the audit fact; UT-2/TASK-050).
 *       The two terminal states are mutually exclusive: commit-after-rollback
 *       and rollback-after-commit are refused, never overwritten, and the
 *       state transitions are additionally guarded by a {@code state: open}
 *       condition on the update query.</li>
 *   <li>Message (record_type=message): {@code _id = txn_id~msg_uuid}, one row per
 *       received {@link Message} with collection/action/data and Kafka source
 *       metadata. Rows appended before a rollback remain stored (audit); the
 *       committed-visibility gate keeps them from ever materializing.</li>
 * </ul>
 *
 * <p>The service is intentionally Kafka-free and HTTP-free: a thin HTTP adapter can
 * later reuse it without dragging in {@code org.apache.kafka.*} or web types.
 */
@Slf4j
@Service
class TransactionMessagePersistenceService {

    static final String FIELD_RECORD_TYPE = 'record_type'
    static final String FIELD_TXN_ID = 'txn_id'
    static final String FIELD_STATE = 'state'
    static final String FIELD_COMMIT_ID = 'commit_id'
    static final String FIELD_OPENED_AT = 'opened_at'
    static final String FIELD_COMMITTED_AT = 'committed_at'
    static final String FIELD_ROLLED_BACK_AT = 'rolled_back_at'
    static final String FIELD_OPEN_DATA = 'open_data'
    static final String FIELD_COMMIT_DATA = 'commit_data'
    static final String FIELD_ROLLBACK_DATA = 'rollback_data'
    static final String FIELD_MSG_UUID = 'msg_uuid'
    static final String FIELD_COLLECTION = 'collection'
    static final String FIELD_ACTION = 'action'
    static final String FIELD_DATA = 'data'
    static final String FIELD_RECEIVED_AT = 'received_at'
    static final String FIELD_KAFKA = 'kafka'

    static final String RECORD_TYPE_TRANSACTION = 'transaction'
    static final String RECORD_TYPE_MESSAGE = 'message'

    static final String STATE_OPEN = 'open'
    static final String STATE_COMMITTED = 'committed'
    static final String STATE_ROLLED_BACK = 'rolled_back'

    private static final String COLLECTION_NAME = COLLECTION_TRANSACTIONS

    private final ReactiveMongoTemplate mongoTemplate
    private final IdGenerator idGenerator
    private final CommittedTransactionMaterializer materializer

    TransactionMessagePersistenceService(ReactiveMongoTemplate mongoTemplate,
                                         IdGenerator idGenerator,
                                         CommittedTransactionMaterializer materializer) {
        this.mongoTemplate = mongoTemplate
        this.idGenerator = idGenerator
        this.materializer = materializer
    }

    /**
     * Persist a canonical {@link Message} into {@code txn}.
     *
     * <p>The caller is responsible for envelope/schema validation; this service
     * does not re-validate but does enforce a small set of structural pre-conditions
     * (txn id present, collection present, action present).
     *
     * @param message the canonical message DTO
     * @param source provenance for the Kafka record carrying the message; may be {@code null}
     *        when reused by a non-Kafka caller in the future.
     * @return {@code Mono<PersistResult>} indicating the outcome.
     */
    Mono<PersistResult> persist(Message message, KafkaSourceMetadata source) {
        if (message == null) {
            return Mono.error(new IllegalArgumentException('message must not be null'))
        }
        if (message.txn() == null) {
            return Mono.error(new IllegalArgumentException('message.txn must not be null'))
        }
        if (message.collection() == null) {
            return Mono.error(new IllegalArgumentException('message.collection must not be null'))
        }
        if (message.action() == null) {
            return Mono.error(new IllegalArgumentException('message.action must not be null'))
        }

        String txnId = message.txn().getId()
        Collection collection = message.collection()
        Action action = message.action()

        if (collection == Collection.TRANSACTION) {
            switch (action) {
                case Action.OPEN:
                    return openHeader(txnId, message)
                case Action.COMMIT:
                    return commitHeader(txnId, message)
                case Action.ROLLBACK:
                    return rollbackHeader(txnId, message)
                default:
                    return Mono.error(new IllegalArgumentException(
                            "Unsupported action for collection 'txn': ${action}"))
            }
        }

        return appendDataMessage(txnId, message, source)
    }

    private Mono<PersistResult> openHeader(String txnId, Message message) {
        return mongoTemplate.findById(txnId, Map.class, COLLECTION_NAME)
                .map { Map existing -> Optional.of(existing) }
                .defaultIfEmpty(Optional.empty())
                .flatMap { Optional<Map> maybeExisting ->
                    if (maybeExisting.isPresent()) {
                        Map existing = maybeExisting.get()
                        String state = existing.get(FIELD_STATE) as String
                        if (state == STATE_COMMITTED) {
                            log.warn('Open re-delivered for already-committed transaction: txnId={}', txnId)
                        } else {
                            log.info('Open re-delivered for already-open transaction: txnId={}', txnId)
                        }
                        return Mono.just(PersistResult.OPEN_CONFIRMED_DUPLICATE)
                    }

                    Query query = Query.query(Criteria.where('_id').is(txnId))
                    Update update = new Update()
                            .setOnInsert('_id', txnId)
                            .setOnInsert(FIELD_TXN_ID, txnId)
                            .setOnInsert(FIELD_RECORD_TYPE, RECORD_TYPE_TRANSACTION)
                            .setOnInsert(FIELD_STATE, STATE_OPEN)
                            .setOnInsert(FIELD_OPENED_AT, Instant.now())
                            .setOnInsert(FIELD_OPEN_DATA, message.data())

                    return mongoTemplate.upsert(query, update, COLLECTION_NAME)
                            .doOnSuccess { log.info('Transaction header opened: txnId={}', txnId) }
                            .doOnError { ex -> log.error('Failed to open header: txnId={}', txnId, ex) }
                            .thenReturn(PersistResult.OPENED)
                } as Mono<PersistResult>
    }

    private Mono<PersistResult> commitHeader(String txnId, Message message) {
        return mongoTemplate.findById(txnId, Map.class, COLLECTION_NAME)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Cannot commit transaction before open: txnId=${txnId}")))
                .flatMap { Map existing ->
                    String state = existing.get(FIELD_STATE) as String
                    if (state == STATE_COMMITTED) {
                        log.info('Commit re-delivered for already-committed transaction: txnId={}', txnId)
                        return materializeQuietly(txnId)
                                .thenReturn(PersistResult.COMMIT_DUPLICATE)
                    }
                    if (state == STATE_ROLLED_BACK) {
                        log.error('Commit refused for rolled-back transaction: txnId={}, msgUuid={}',
                                txnId, message.uuid())
                        return Mono.just(PersistResult.COMMIT_REFUSED_ROLLED_BACK)
                    }

                    String commitId = idGenerator.nextId()
                    Instant now = Instant.now()
                    Query query = Query.query(Criteria.where('_id').is(txnId)
                            .and(FIELD_STATE).is(STATE_OPEN))
                    Update update = new Update()
                            .set(FIELD_STATE, STATE_COMMITTED)
                            .set(FIELD_COMMIT_ID, commitId)
                            .set(FIELD_COMMITTED_AT, now)
                            .set(FIELD_COMMIT_DATA, message.data())

                    return mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
                            .doOnSuccess { log.info('Transaction committed: txnId={}, commitId={}',
                                    txnId, commitId) }
                            .doOnError { ex -> log.error('Failed to commit transaction: txnId={}', txnId, ex) }
                            .then(materializeQuietly(txnId))
                            .thenReturn(PersistResult.COMMITTED)
                } as Mono<PersistResult>
    }

    /**
     * Durably mark the header {@code rolled_back} (UT-2/TASK-050). The
     * rollback message's {@code data} is kept as {@code rollback_data} — the
     * audit fact that a rollback was requested and honored. Re-delivery is an
     * idempotent duplicate; a rollback arriving after commit is refused
     * without touching the header; a rollback before open errors like a
     * commit before open. The {@code state: open} condition on the update
     * query is write-time defense in depth against a racing terminal
     * transition. Appended message rows remain stored; the
     * committed-visibility gate keeps them from ever materializing.
     */
    private Mono<PersistResult> rollbackHeader(String txnId, Message message) {
        return mongoTemplate.findById(txnId, Map.class, COLLECTION_NAME)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Cannot roll back transaction before open: txnId=${txnId}")))
                .flatMap { Map existing ->
                    String state = existing.get(FIELD_STATE) as String
                    if (state == STATE_ROLLED_BACK) {
                        log.info('Rollback re-delivered for already-rolled-back transaction: txnId={}', txnId)
                        return Mono.just(PersistResult.ROLLBACK_DUPLICATE)
                    }
                    if (state == STATE_COMMITTED) {
                        log.error('Rollback refused for committed transaction: txnId={}, msgUuid={}',
                                txnId, message.uuid())
                        return Mono.just(PersistResult.ROLLBACK_REFUSED_COMMITTED)
                    }

                    Query query = Query.query(Criteria.where('_id').is(txnId)
                            .and(FIELD_STATE).is(STATE_OPEN))
                    Update update = new Update()
                            .set(FIELD_STATE, STATE_ROLLED_BACK)
                            .set(FIELD_ROLLED_BACK_AT, Instant.now())
                            .set(FIELD_ROLLBACK_DATA, message.data())

                    return mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
                            .doOnSuccess { log.info('Transaction rolled back: txnId={}', txnId) }
                            .doOnError { ex -> log.error('Failed to roll back transaction: txnId={}', txnId, ex) }
                            .thenReturn(PersistResult.ROLLED_BACK)
                } as Mono<PersistResult>
    }

    /**
     * Trigger the post-commit projection for {@code txnId} and swallow any
     * failure. The {@code txn} commit is already durable when this runs; a
     * projection failure must not invert that durability ordering or fail the
     * outward {@link PersistResult}. A retry on commit re-delivery will re-run
     * the materializer because the {@code COMMIT_DUPLICATE} branch also calls
     * this method, so a transient projection gap can self-heal.
     */
    private Mono<Void> materializeQuietly(String txnId) {
        return materializer.materialize(txnId)
                .doOnError { ex -> log.warn('Post-commit materialization failed: txnId={}', txnId, ex) }
                .onErrorResume({ Throwable ignored -> Mono.empty() } as java.util.function.Function)
                .then() as Mono<Void>
    }

    private Mono<PersistResult> appendDataMessage(String txnId, Message message, KafkaSourceMetadata source) {
        String recordId = txnId + TRANSACTION_ID_SEPARATOR + message.uuid()
        Map<String, Object> doc = [
                _id              : recordId,
                (FIELD_RECORD_TYPE): RECORD_TYPE_MESSAGE,
                (FIELD_TXN_ID)    : txnId,
                (FIELD_MSG_UUID)  : message.uuid(),
                (FIELD_COLLECTION): message.collection().abbreviation,
                (FIELD_ACTION)    : message.action().toString(),
                (FIELD_DATA)      : message.data(),
                (FIELD_RECEIVED_AT): Instant.now()
        ] as Map<String, Object>

        if (source != null) {
            doc.put(FIELD_KAFKA, [
                    topic       : source.topic,
                    partition   : source.partition,
                    offset      : source.offset,
                    timestamp_ms: source.timestampMs
            ] as Map<String, Object>)
        }

        return mongoTemplate.insert(doc, COLLECTION_NAME)
                .doOnSuccess { log.info('Transaction message appended: id={}', recordId) }
                .thenReturn(PersistResult.APPENDED)
                .onErrorResume({ Throwable ex -> handleAppendDuplicate(recordId, message, ex) }) as Mono<PersistResult>
    }

    private Mono<PersistResult> handleAppendDuplicate(String recordId, Message message, Throwable ex) {
        if (!isDuplicateKey(ex)) {
            return Mono.error(ex)
        }
        return mongoTemplate.findById(recordId, Map.class, COLLECTION_NAME)
                .flatMap { Map existing ->
                    if (isSamePayload(existing, message)) {
                        log.info('Duplicate transaction message with matching payload: id={}', recordId)
                        return Mono.just(PersistResult.APPEND_DUPLICATE)
                    }
                    String summary = "stored=(collection=${existing.get(FIELD_COLLECTION)}, " +
                            "action=${existing.get(FIELD_ACTION)})" +
                            ", incoming=(collection=${message.collection().abbreviation}, " +
                            "action=${message.action()})"
                    log.error('Conflicting duplicate transaction message: id={}, {}', recordId, summary)
                    return Mono.error(new ConflictingDuplicateException(recordId,
                            "Conflicting duplicate for ${recordId}: ${summary}"))
                }
                .switchIfEmpty(Mono.error(ex)) as Mono<PersistResult>
    }

    private static boolean isDuplicateKey(Throwable ex) {
        if (ex instanceof SpringDuplicateKeyException) return true
        if (ex instanceof DuplicateKeyException) return true
        Throwable cause = ex.cause
        while (cause != null && cause !== ex) {
            if (cause instanceof SpringDuplicateKeyException) return true
            if (cause instanceof DuplicateKeyException) return true
            cause = cause.cause
        }
        return false
    }

    private static boolean isSamePayload(Map existing, Message incoming) {
        String storedCollection = existing.get(FIELD_COLLECTION) as String
        String storedAction = existing.get(FIELD_ACTION) as String
        Object storedData = existing.get(FIELD_DATA)
        return storedCollection == incoming.collection().abbreviation &&
                storedAction == incoming.action().toString() &&
                Objects.equals(storedData, incoming.data())
    }
}
