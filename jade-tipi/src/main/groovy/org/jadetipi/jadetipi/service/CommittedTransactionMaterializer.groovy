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
import org.springframework.dao.DuplicateKeyException as SpringDuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

/**
 * Projects committed transaction messages from the {@code txn} write-ahead log
 * into their long-term MongoDB collections. Reads via
 * {@link CommittedTransactionReadService} so the accepted committed-visibility
 * gate (record_type=transaction, state=committed, non-blank commit_id) is the
 * single source of truth for visibility.
 *
 * <p>Supported messages in this iteration:
 * <ul>
 *   <li>{@code loc + create} → {@code loc} collection.</li>
 *   <li>{@code typ + create} where {@code data.kind == "link_type"} → {@code typ}
 *       collection. Bare entity-type {@code typ} records are intentionally
 *       skipped here.</li>
 *   <li>{@code lnk + create} → {@code lnk} collection.</li>
 * </ul>
 * Every other collection/action combination — including update, delete, and
 * txn-control actions — is counted as {@code skippedUnsupported} without
 * raising an error.
 *
 * <p>Materialized documents copy {@code data} verbatim, set {@code _id} to
 * {@code data.id}, and add a reserved {@link #FIELD_PROVENANCE} sub-document
 * so projection metadata cannot collide with payload keys.
 *
 * <p>Semantic reference validation (resolution of {@code lnk.type_id},
 * {@code left}, {@code right}, or {@code allowed_*_collections}) is
 * intentionally out of scope at this boundary.
 */
@Slf4j
@Service
class CommittedTransactionMaterializer {

    static final String FIELD_ID = '_id'
    static final String FIELD_DATA_ID = 'id'
    static final String FIELD_PROVENANCE = '_jt_provenance'

    static final String PROV_TXN_ID = 'txn_id'
    static final String PROV_COMMIT_ID = 'commit_id'
    static final String PROV_MSG_UUID = 'msg_uuid'
    static final String PROV_COMMITTED_AT = 'committed_at'
    static final String PROV_MATERIALIZED_AT = 'materialized_at'

    static final String COLLECTION_LOC = 'loc'
    static final String COLLECTION_TYP = 'typ'
    static final String COLLECTION_LNK = 'lnk'

    static final String ACTION_CREATE = 'create'
    static final String DATA_KIND = 'kind'
    static final String LINK_TYPE_KIND = 'link_type'

    private final ReactiveMongoTemplate mongoTemplate
    private final CommittedTransactionReadService readService

    CommittedTransactionMaterializer(ReactiveMongoTemplate mongoTemplate,
                                     CommittedTransactionReadService readService) {
        this.mongoTemplate = mongoTemplate
        this.readService = readService
    }

    /**
     * Convenience entry point that resolves the committed snapshot through
     * {@link CommittedTransactionReadService#findCommitted(String)} and
     * delegates to {@link #materialize(CommittedTransactionSnapshot)}. Returns
     * {@code Mono.empty()} when the transaction is not yet visible as
     * committed.
     */
    Mono<MaterializeResult> materialize(String txnId) {
        Assert.hasText(txnId, 'txnId must not be blank')
        return readService.findCommitted(txnId)
                .flatMap { CommittedTransactionSnapshot snapshot -> materialize(snapshot) } as Mono<MaterializeResult>
    }

    /**
     * Materialize the supported messages in the given snapshot. The caller is
     * responsible for ensuring the snapshot represents a committed transaction;
     * the snapshot is processed verbatim and not re-validated against the
     * {@code txn} header.
     */
    Mono<MaterializeResult> materialize(CommittedTransactionSnapshot snapshot) {
        if (snapshot == null) {
            return Mono.empty()
        }
        MaterializeResult result = new MaterializeResult()
        List<CommittedTransactionMessage> messages = snapshot.messages ?: []
        return Flux.fromIterable(messages)
                .concatMap { CommittedTransactionMessage message ->
                    processMessage(snapshot, message, result)
                }
                .then(Mono.just(result)) as Mono<MaterializeResult>
    }

    private Mono<Void> processMessage(CommittedTransactionSnapshot snapshot,
                                      CommittedTransactionMessage message,
                                      MaterializeResult result) {
        if (!isSupported(message)) {
            log.debug('Materializer skipping unsupported message: txnId={}, msgUuid={}, collection={}, action={}',
                    snapshot.txnId, message?.msgUuid, message?.collection, message?.action)
            result.skippedUnsupported++
            return Mono.empty()
        }

        Map<String, Object> data = message.data
        String docId = extractDocId(data)
        if (docId == null) {
            log.error('Materializer skipping message with missing or blank data.id: ' +
                    'txnId={}, commitId={}, collection={}, msgUuid={}',
                    snapshot.txnId, snapshot.commitId, message.collection, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }

        Map<String, Object> doc = buildDocument(docId, snapshot, message)
        return mongoTemplate.insert(doc, message.collection)
                .doOnSuccess({ Object inserted ->
                    log.info('Materialized {} record: id={}, txnId={}, commitId={}',
                            message.collection, docId, snapshot.txnId, snapshot.commitId)
                    result.materialized++
                })
                .onErrorResume({ Throwable ex ->
                    handleInsertError(snapshot, message, doc, docId, ex, result)
                })
                .then() as Mono<Void>
    }

    private Mono<Void> handleInsertError(CommittedTransactionSnapshot snapshot,
                                         CommittedTransactionMessage message,
                                         Map<String, Object> incomingDoc,
                                         String docId,
                                         Throwable ex,
                                         MaterializeResult result) {
        if (!isDuplicateKey(ex)) {
            log.error('Materializer insert failed for {} record id={}: txnId={}, commitId={}',
                    message.collection, docId, snapshot.txnId, snapshot.commitId, ex)
            return Mono.error(ex)
        }
        return mongoTemplate.findById(docId, Map.class, message.collection)
                .switchIfEmpty(Mono.error(ex))
                .flatMap({ Map existing ->
                    if (isSamePayload(existing, incomingDoc)) {
                        log.info('Materialize duplicate with matching payload: collection={}, id={}, txnId={}',
                                message.collection, docId, snapshot.txnId)
                        result.duplicateMatching++
                    } else {
                        log.error('Materialize conflicting duplicate (not overwriting): ' +
                                'collection={}, id={}, txnId={}, commitId={}, msgUuid={}',
                                message.collection, docId, snapshot.txnId,
                                snapshot.commitId, message.msgUuid)
                        result.conflictingDuplicate++
                    }
                    return Mono.empty()
                })
                .then() as Mono<Void>
    }

    private static boolean isSupported(CommittedTransactionMessage message) {
        if (message == null) {
            return false
        }
        if (message.action != ACTION_CREATE) {
            return false
        }
        switch (message.collection) {
            case COLLECTION_LOC:
                return true
            case COLLECTION_LNK:
                return true
            case COLLECTION_TYP:
                Object kind = message.data?.get(DATA_KIND)
                return LINK_TYPE_KIND == kind
            default:
                return false
        }
    }

    private static String extractDocId(Map<String, Object> data) {
        if (data == null) {
            return null
        }
        Object idValue = data.get(FIELD_DATA_ID)
        if (idValue == null) {
            return null
        }
        String asString = idValue.toString()
        return asString.trim().isEmpty() ? null : asString
    }

    private static Map<String, Object> buildDocument(String docId,
                                                     CommittedTransactionSnapshot snapshot,
                                                     CommittedTransactionMessage message) {
        Map<String, Object> doc = new LinkedHashMap<>()
        doc.put(FIELD_ID, docId)
        if (message.data != null) {
            doc.putAll(message.data)
        }
        Map<String, Object> provenance = new LinkedHashMap<>()
        provenance.put(PROV_TXN_ID, snapshot.txnId)
        provenance.put(PROV_COMMIT_ID, snapshot.commitId)
        provenance.put(PROV_MSG_UUID, message.msgUuid)
        provenance.put(PROV_COMMITTED_AT, snapshot.committedAt)
        provenance.put(PROV_MATERIALIZED_AT, Instant.now())
        doc.put(FIELD_PROVENANCE, provenance)
        return doc
    }

    private static boolean isSamePayload(Map existing, Map incoming) {
        Map<String, Object> existingCopy = new LinkedHashMap<>(existing ?: [:])
        existingCopy.remove(FIELD_PROVENANCE)
        Map<String, Object> incomingCopy = new LinkedHashMap<>(incoming ?: [:])
        incomingCopy.remove(FIELD_PROVENANCE)
        return Objects.equals(existingCopy, incomingCopy)
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
}
