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
package org.jadetipi.jadetipi.importer

import com.mongodb.DuplicateKeyException
import groovy.util.logging.Slf4j
import org.springframework.dao.DuplicateKeyException as SpringDuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

/**
 * Persistent dependency-ordered import queue over the MongoDB
 * {@code import_queue} collection (TASK-059;
 * docs/architecture/bulk-import-design.md).
 *
 * <p>Ordering: {@code seq} comes from a monotonic counter row, so drain
 * order is enqueue order. The ordering invariant — a dependency's seq is
 * lower than its dependent's — holds because planners enqueue dependencies
 * first and {@link #enqueue} never changes an existing row's seq (dedup by
 * the natural {@code <source>~<key>} id keeps the original, lower seq).
 *
 * <p>Lifecycle: {@code pending} → {@code done} (or {@code failed}). The
 * minted JDTP id is recorded on the row at emit time so later rows can
 * reference earlier imports across transaction batches; re-runs skip done
 * rows (create-only conflicts are the backstop, never the mechanism).
 */
@Slf4j
@Service
class ImportQueueService {

    static final String COLLECTION_NAME = 'import_queue'
    static final String SEQ_COUNTER_ID = 'seq_counter'

    static final String FIELD_ID = '_id'
    static final String FIELD_SOURCE = 'source'
    static final String FIELD_KEY = 'key'
    static final String FIELD_KIND = 'kind'
    static final String FIELD_STATE = 'state'
    static final String FIELD_SEQ = 'seq'
    static final String FIELD_JDTP_ID = 'jdtp_id'
    static final String FIELD_TXN_ID = 'txn_id'
    static final String FIELD_ERROR = 'error'
    static final String FIELD_ENQUEUED_AT = 'enqueued_at'
    static final String FIELD_DONE_AT = 'done_at'
    static final String FIELD_COUNTER_VALUE = 'value'

    static final String STATE_PENDING = 'pending'
    static final String STATE_DONE = 'done'
    static final String STATE_FAILED = 'failed'

    private final ReactiveMongoTemplate mongoTemplate

    ImportQueueService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    static String rowId(String source, String key) {
        return "${source}~${key}"
    }

    /**
     * Enqueue one item. Returns {@code true} when the row was newly
     * inserted, {@code false} when it already existed (its original seq —
     * and therefore its dependency position — is preserved).
     */
    Mono<Boolean> enqueue(String source, String key, String kind) {
        return nextSeq()
                .flatMap { Long seq ->
                    Map<String, Object> row = [
                            (FIELD_ID)         : rowId(source, key),
                            (FIELD_SOURCE)     : source,
                            (FIELD_KEY)        : key,
                            (FIELD_KIND)       : kind,
                            (FIELD_STATE)      : STATE_PENDING,
                            (FIELD_SEQ)        : seq,
                            (FIELD_ENQUEUED_AT): Instant.now()
                    ] as Map<String, Object>
                    return mongoTemplate.insert(row, COLLECTION_NAME)
                            .thenReturn(Boolean.TRUE)
                            .onErrorResume({ Throwable ex ->
                                isDuplicateKey(ex) ? Mono.just(Boolean.FALSE) : Mono.error(ex)
                            })
                } as Mono<Boolean>
    }

    /** Pending rows in dependency (seq-ascending) order. */
    Flux<ImportQueueItem> pendingInOrder(int limit) {
        Query query = Query.query(Criteria.where(FIELD_STATE).is(STATE_PENDING))
                .with(Sort.by(Sort.Direction.ASC, FIELD_SEQ))
                .limit(limit)
        return mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                .map(this.&toItem)
    }

    /** Record the minted JDTP id at emit time. */
    Mono<Void> recordJdtpId(String id, String jdtpId) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where(FIELD_ID).is(id)),
                new Update().set(FIELD_JDTP_ID, jdtpId),
                COLLECTION_NAME).then() as Mono<Void>
    }

    Mono<Void> markDone(String id, String txnId) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where(FIELD_ID).is(id)),
                new Update().set(FIELD_STATE, STATE_DONE)
                        .set(FIELD_TXN_ID, txnId)
                        .set(FIELD_DONE_AT, Instant.now()),
                COLLECTION_NAME).then() as Mono<Void>
    }

    Mono<Void> markFailed(String id, String error) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where(FIELD_ID).is(id)),
                new Update().set(FIELD_STATE, STATE_FAILED)
                        .set(FIELD_ERROR, error),
                COLLECTION_NAME).then() as Mono<Void>
    }

    /** The recorded JDTP id of an earlier row (cross-batch reference). */
    Mono<String> jdtpIdOf(String id) {
        return mongoTemplate.findById(id, Map.class, COLLECTION_NAME)
                .mapNotNull { Map row -> row.get(FIELD_JDTP_ID) as String } as Mono<String>
    }

    private Mono<Long> nextSeq() {
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where(FIELD_ID).is(SEQ_COUNTER_ID)),
                new Update().inc(FIELD_COUNTER_VALUE, 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Map.class,
                COLLECTION_NAME)
                .map { Map counter -> ((Number) counter.get(FIELD_COUNTER_VALUE)).longValue() } as Mono<Long>
    }

    private ImportQueueItem toItem(Map row) {
        return new ImportQueueItem(
                id: row.get(FIELD_ID) as String,
                source: row.get(FIELD_SOURCE) as String,
                key: row.get(FIELD_KEY) as String,
                kind: row.get(FIELD_KIND) as String,
                state: row.get(FIELD_STATE) as String,
                seq: ((Number) (row.get(FIELD_SEQ) ?: 0L)).longValue(),
                jdtpId: row.get(FIELD_JDTP_ID) as String,
                txnId: row.get(FIELD_TXN_ID) as String,
                error: row.get(FIELD_ERROR) as String
        )
    }

    private static boolean isDuplicateKey(Throwable ex) {
        Throwable current = ex
        while (current != null) {
            if (current instanceof SpringDuplicateKeyException || current instanceof DuplicateKeyException) {
                return true
            }
            current = current.cause === current ? null : current.cause
        }
        return false
    }
}
