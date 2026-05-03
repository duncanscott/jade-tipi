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
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant

/**
 * Projects committed transaction messages from the {@code txn} write-ahead log
 * into root-shaped long-term MongoDB documents. Reads via
 * {@link CommittedTransactionReadService} so the accepted committed-visibility
 * gate (record_type=transaction, state=committed, non-blank commit_id) is the
 * single source of truth for visibility.
 *
 * <p>Supported messages in this iteration:
 * <ul>
 *   <li>{@code loc + create} → {@code loc} collection.</li>
 *   <li>{@code typ + create} → {@code typ} collection. Both link-type
 *       ({@code data.kind == "link_type"}) and bare entity-type
 *       ({@code data.kind} absent) records materialize as root-shaped
 *       {@code typ} documents; the materializer does not enforce a kind
 *       discriminator.</li>
 *   <li>{@code typ + update} with {@code data.operation == "add_property"} →
 *       sets {@code properties.property_refs.<data.property_id>} on the
 *       existing target {@code typ} root. The reference value carries only
 *       the wire-shape metadata that is present (currently {@code required}
 *       when supplied); the materializer never invents reference metadata
 *       and never resolves {@code data.property_id} against the {@code ppy}
 *       collection. Idempotent repeats with matching metadata count as
 *       {@code duplicateMatching}; conflicting metadata counts as
 *       {@code conflictingDuplicate} and never overwrites. A missing target
 *       {@code typ} root counts as {@code skippedMissingTarget}. Other
 *       {@code typ + update} operations remain {@code skippedUnsupported}.</li>
 *   <li>{@code lnk + create} → {@code lnk} collection.</li>
 *   <li>{@code ent + create} → {@code ent} collection. Top-level
 *       {@code data.type_id} surfaces as the root {@code type_id};
 *       semantic resolution that {@code data.type_id} points at a committed
 *       {@code typ} record is intentionally deferred.</li>
 *   <li>{@code grp + create} → {@code grp} collection. The first-pass
 *       permissions map (keyed by world-unique grp IDs with values {@code "rw"}
 *       or {@code "r"}) is copied verbatim through {@code properties.permissions};
 *       no permission enforcement is added at materialization time.</li>
 * </ul>
 * Every other collection/action combination — including delete and
 * txn-control actions, and every {@code typ + update} whose
 * {@code data.operation} is not {@code "add_property"} — is counted as
 * {@code skippedUnsupported} without raising an error.
 *
 * <p>Each materialized document is a self-describing root with {@code _id},
 * {@code id}, {@code collection}, top-level {@code type_id}, explicit
 * {@code properties}, denormalized {@code links}, and a reserved {@code _head}
 * sub-document carrying schema metadata and projection provenance under
 * {@code _head.provenance}.
 *
 * <p>Endpoint {@code links} projections for {@code lnk} endpoints are
 * intentionally not maintained here; this materializer writes {@code links: {}}
 * for every supported root and leaves endpoint-projection maintenance to a
 * later task.
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
    static final String FIELD_COLLECTION = 'collection'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_LEFT = 'left'
    static final String FIELD_RIGHT = 'right'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_HEAD = '_head'

    static final String HEAD_SCHEMA_VERSION = 'schema_version'
    static final String HEAD_DOCUMENT_KIND = 'document_kind'
    static final String HEAD_ROOT_ID = 'root_id'
    static final String HEAD_PROVENANCE = 'provenance'
    static final int ROOT_SCHEMA_VERSION = 1
    static final String DOCUMENT_KIND_ROOT = 'root'

    static final String PROV_TXN_ID = 'txn_id'
    static final String PROV_COMMIT_ID = 'commit_id'
    static final String PROV_MSG_UUID = 'msg_uuid'
    static final String PROV_COLLECTION = 'collection'
    static final String PROV_ACTION = 'action'
    static final String PROV_COMMITTED_AT = 'committed_at'
    static final String PROV_MATERIALIZED_AT = 'materialized_at'

    static final String COLLECTION_LOC = 'loc'
    static final String COLLECTION_TYP = 'typ'
    static final String COLLECTION_LNK = 'lnk'
    static final String COLLECTION_GRP = 'grp'
    static final String COLLECTION_ENT = 'ent'

    static final String ACTION_CREATE = 'create'
    static final String ACTION_UPDATE = 'update'

    static final String FIELD_OPERATION = 'operation'
    static final String FIELD_PROPERTY_ID = 'property_id'
    static final String FIELD_REQUIRED = 'required'
    static final String FIELD_PROPERTY_REFS = 'property_refs'

    static final String OPERATION_ADD_PROPERTY = 'add_property'

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

        if (message.action == ACTION_UPDATE
                && message.collection == COLLECTION_TYP
                && OPERATION_ADD_PROPERTY == message.data?.get(FIELD_OPERATION)) {
            return processTypUpdateAddProperty(snapshot, message, result)
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
                    log.info('Materialized {} root: id={}, txnId={}, commitId={}',
                            message.collection, docId, snapshot.txnId, snapshot.commitId)
                    result.materialized++
                })
                .onErrorResume({ Throwable ex ->
                    handleInsertError(snapshot, message, doc, docId, ex, result)
                })
                .then() as Mono<Void>
    }

    private Mono<Void> processTypUpdateAddProperty(CommittedTransactionSnapshot snapshot,
                                                   CommittedTransactionMessage message,
                                                   MaterializeResult result) {
        Map<String, Object> data = message.data
        String targetId = extractDocId(data)
        if (targetId == null) {
            log.error('Materializer skipping typ + update add_property with missing or blank data.id: ' +
                    'txnId={}, commitId={}, msgUuid={}',
                    snapshot.txnId, snapshot.commitId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }
        String propertyId = extractPropertyId(data)
        if (propertyId == null) {
            log.error('Materializer skipping typ + update add_property with missing or blank data.property_id: ' +
                    'txnId={}, commitId={}, id={}, msgUuid={}',
                    snapshot.txnId, snapshot.commitId, targetId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }

        Map<String, Object> referenceEntry = buildPropertyReferenceEntry(data)
        String dottedKey = FIELD_PROPERTIES + '.' + FIELD_PROPERTY_REFS + '.' + propertyId

        return mongoTemplate.findById(targetId, Map.class, COLLECTION_TYP)
                .map({ Map existing -> Optional.of(existing) })
                .defaultIfEmpty(Optional.empty())
                .flatMap({ Optional<Map> probe ->
                    if (!probe.isPresent()) {
                        log.warn('Materializer skipping typ + update add_property with missing target typ root: ' +
                                'id={}, propertyId={}, txnId={}, commitId={}, msgUuid={}',
                                targetId, propertyId, snapshot.txnId, snapshot.commitId, message.msgUuid)
                        result.skippedMissingTarget++
                        return Mono.empty()
                    }
                    Map existing = probe.get()
                    Map<String, Object> existingEntry = readExistingPropertyRef(existing, propertyId)
                    if (existingEntry != null) {
                        if (Objects.equals(existingEntry, referenceEntry)) {
                            log.info('Materialize duplicate matching typ + update add_property: ' +
                                    'id={}, propertyId={}, txnId={}',
                                    targetId, propertyId, snapshot.txnId)
                            result.duplicateMatching++
                        } else {
                            log.error('Materialize conflicting typ + update add_property (not overwriting): ' +
                                    'id={}, propertyId={}, txnId={}, commitId={}, msgUuid={}',
                                    targetId, propertyId, snapshot.txnId, snapshot.commitId, message.msgUuid)
                            result.conflictingDuplicate++
                        }
                        return Mono.empty()
                    }
                    Query query = Query.query(Criteria.where(FIELD_ID).is(targetId))
                    Update update = new Update().set(dottedKey, referenceEntry)
                    return mongoTemplate.updateFirst(query, update, COLLECTION_TYP)
                            .doOnSuccess({ Object updateResult ->
                                log.info('Materialized typ + update add_property: id={}, propertyId={}, txnId={}, commitId={}',
                                        targetId, propertyId, snapshot.txnId, snapshot.commitId)
                                result.materialized++
                            })
                            .then()
                })
                .then() as Mono<Void>
    }

    private static String extractPropertyId(Map<String, Object> data) {
        if (data == null) {
            return null
        }
        Object value = data.get(FIELD_PROPERTY_ID)
        if (value == null) {
            return null
        }
        String asString = value.toString()
        return asString.trim().isEmpty() ? null : asString
    }

    private static Map<String, Object> buildPropertyReferenceEntry(Map<String, Object> data) {
        Map<String, Object> entry = new LinkedHashMap<>()
        if (data != null && data.containsKey(FIELD_REQUIRED)) {
            entry.put(FIELD_REQUIRED, data.get(FIELD_REQUIRED))
        }
        return entry
    }

    private static Map<String, Object> readExistingPropertyRef(Map existing, String propertyId) {
        if (existing == null) {
            return null
        }
        Object propertiesValue = existing.get(FIELD_PROPERTIES)
        if (!(propertiesValue instanceof Map)) {
            return null
        }
        Object refsValue = ((Map) propertiesValue).get(FIELD_PROPERTY_REFS)
        if (!(refsValue instanceof Map)) {
            return null
        }
        Object existingEntry = ((Map) refsValue).get(propertyId)
        if (existingEntry == null) {
            return null
        }
        if (existingEntry instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) existingEntry)
        }
        return null
    }

    private Mono<Void> handleInsertError(CommittedTransactionSnapshot snapshot,
                                         CommittedTransactionMessage message,
                                         Map<String, Object> incomingDoc,
                                         String docId,
                                         Throwable ex,
                                         MaterializeResult result) {
        if (!isDuplicateKey(ex)) {
            log.error('Materializer insert failed for {} root id={}: txnId={}, commitId={}',
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
        if (message.action == ACTION_CREATE) {
            switch (message.collection) {
                case COLLECTION_LOC:
                    return true
                case COLLECTION_LNK:
                    return true
                case COLLECTION_GRP:
                    return true
                case COLLECTION_ENT:
                    return true
                case COLLECTION_TYP:
                    return true
                default:
                    return false
            }
        }
        if (message.action == ACTION_UPDATE
                && message.collection == COLLECTION_TYP
                && OPERATION_ADD_PROPERTY == message.data?.get(FIELD_OPERATION)) {
            return true
        }
        return false
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
        Map<String, Object> data = (message.data ?: [:]) as Map<String, Object>

        Map<String, Object> doc = new LinkedHashMap<>()
        doc.put(FIELD_ID, docId)
        doc.put(FIELD_DATA_ID, docId)
        doc.put(FIELD_COLLECTION, message.collection)
        doc.put(FIELD_TYPE_ID, data.get(FIELD_TYPE_ID))

        if (COLLECTION_LNK == message.collection) {
            doc.put(FIELD_LEFT, data.get(FIELD_LEFT))
            doc.put(FIELD_RIGHT, data.get(FIELD_RIGHT))
            doc.put(FIELD_PROPERTIES, copyProperties(data.get(FIELD_PROPERTIES)))
            doc.put(FIELD_LINKS, new LinkedHashMap<String, Object>())
        } else if (data.get(FIELD_PROPERTIES) instanceof Map) {
            doc.put(FIELD_PROPERTIES, copyProperties(data.get(FIELD_PROPERTIES)))
            doc.put(FIELD_LINKS, copyProperties(data.get(FIELD_LINKS)))
        } else {
            doc.put(FIELD_PROPERTIES, buildInlineProperties(data))
            doc.put(FIELD_LINKS, new LinkedHashMap<String, Object>())
        }

        doc.put(FIELD_HEAD, buildHead(docId, snapshot, message))
        return doc
    }

    private static Map<String, Object> buildInlineProperties(Map<String, Object> data) {
        Map<String, Object> properties = new LinkedHashMap<>()
        data.each { String key, Object value ->
            if (key != FIELD_DATA_ID && key != FIELD_TYPE_ID && key != FIELD_LINKS) {
                properties.put(key, value)
            }
        }
        return properties
    }

    private static Map<String, Object> copyProperties(Object propertiesValue) {
        if (propertiesValue instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) propertiesValue)
        }
        return new LinkedHashMap<String, Object>()
    }

    private static Map<String, Object> buildHead(String docId,
                                                 CommittedTransactionSnapshot snapshot,
                                                 CommittedTransactionMessage message) {
        Map<String, Object> provenance = new LinkedHashMap<>()
        provenance.put(PROV_TXN_ID, snapshot.txnId)
        provenance.put(PROV_COMMIT_ID, snapshot.commitId)
        provenance.put(PROV_MSG_UUID, message.msgUuid)
        provenance.put(PROV_COLLECTION, message.collection)
        provenance.put(PROV_ACTION, message.action)
        provenance.put(PROV_COMMITTED_AT, snapshot.committedAt)
        provenance.put(PROV_MATERIALIZED_AT, Instant.now())

        Map<String, Object> head = new LinkedHashMap<>()
        head.put(HEAD_SCHEMA_VERSION, ROOT_SCHEMA_VERSION)
        head.put(HEAD_DOCUMENT_KIND, DOCUMENT_KIND_ROOT)
        head.put(HEAD_ROOT_ID, docId)
        head.put(HEAD_PROVENANCE, provenance)
        return head
    }

    /**
     * Compare an existing materialized document with an incoming candidate,
     * ignoring only {@code _head.provenance.materialized_at}. Retried matching
     * payloads remain idempotent while real payload or provenance differences
     * still surface as conflicts.
     */
    private static boolean isSamePayload(Map existing, Map incoming) {
        Map<String, Object> existingCopy = stripVolatileFields(existing)
        Map<String, Object> incomingCopy = stripVolatileFields(incoming)
        return Objects.equals(existingCopy, incomingCopy)
    }

    private static Map<String, Object> stripVolatileFields(Map source) {
        Map<String, Object> copy = new LinkedHashMap<>(source ?: [:])
        Object headValue = copy.get(FIELD_HEAD)
        if (headValue instanceof Map) {
            Map<String, Object> headCopy = new LinkedHashMap<>((Map<String, Object>) headValue)
            Object provenanceValue = headCopy.get(HEAD_PROVENANCE)
            if (provenanceValue instanceof Map) {
                Map<String, Object> provenanceCopy = new LinkedHashMap<>((Map<String, Object>) provenanceValue)
                provenanceCopy.remove(PROV_MATERIALIZED_AT)
                headCopy.put(HEAD_PROVENANCE, provenanceCopy)
            }
            copy.put(FIELD_HEAD, headCopy)
        }
        return copy
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
