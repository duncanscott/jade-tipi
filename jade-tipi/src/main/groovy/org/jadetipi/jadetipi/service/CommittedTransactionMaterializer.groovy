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

import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS
import static org.jadetipi.jadetipi.util.Constants.TRANSACTION_ID_SEPARATOR

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
 *   <li>{@code prc + create} → {@code prc} collection. A top-level
 *       {@code data.output_input} map (execution provenance:
 *       {@code {output_id: {input_id: contribution}}}) is hoisted to the
 *       root top level — parallel to {@code lnk}'s {@code left}/{@code right}
 *       — and excluded from the inline {@code properties} bag. Contribution
 *       objects are copied verbatim; validation against a procedure-type
 *       {@code vdn} schema is deferred (spec §1.9, UT-4).</li>
 *   <li>{@code tsk + create} → {@code tsk} collection as a standard typed
 *       root. Task semantics (task-type {@code procedure_type_id}, input and
 *       fulfillment links) live in {@code typ} and {@code lnk} records and
 *       are not interpreted here.</li>
 *   <li>{@code fil + create} → {@code fil} collection as a standard typed
 *       root (TASK-049). File facts — retrieval URL and the like — are
 *       ordinary typed property values; content-identity hoisting and
 *       dedup are deliberately deferred (DIRECTION.md, Files).</li>
 *   <li>{@code ppy + create} with {@code data.kind == "definition"} →
 *       {@code ppy} collection. The wire-shape {@code data.kind},
 *       {@code data.name}, and {@code data.value_schema} land verbatim
 *       under root {@code properties}; {@code data.value_schema} is
 *       copied as an opaque JSON object and is not validated at
 *       materialization time. Every other {@code data.kind} value
 *       (missing, blank, or unknown) remains {@code skippedUnsupported}.</li>
 *   <li>{@code ppy + create} with {@code data.kind == "assignment"} → the
 *       committed value is projected onto the target object root under
 *       {@code property_values.<data.property_id>} as
 *       {@code { value, txn_id, commit_id, msg_uuid, applied_at }} via a
 *       dotted-path {@code $set} (TASK-040/TASK-045; drift-note contracts
 *       8.2.6/8.2.7). The target is explicit
 *       {@code data.object_collection} in {@code {ent, loc, prc, tsk, fil}}
 *       plus {@code data.object_id}; a payload carrying only the deprecated
 *       {@code entity_id} resolves as ({@code ent}, {@code entity_id})
 *       with a warning. Registration is inheritance-aware: the property
 *       must be listed under {@code properties.property_refs} on the
 *       target's {@code typ} root or on an ancestor reached by following
 *       {@code properties.parent_type_id} (single inheritance, bounded
 *       depth, cycle-safe); an exhausted or broken chain counts as
 *       {@code skippedUnregisteredProperty}. Unknown
 *       {@code object_collection}, blank {@code object_id} or
 *       {@code property_id}, or a non-object {@code value} count as
 *       {@code skippedInvalid}; a missing target root counts as
 *       {@code skippedMissingTarget}. An existing entry equal to the
 *       incoming one ignoring {@code applied_at} counts as
 *       {@code duplicateMatching}; a differing entry is
 *       {@code conflictingDuplicate} and never overwritten. {@code data.id}
 *       is ignored on assignments; standalone assignment roots are no
 *       longer written (existing rows remain readable as historical
 *       data).</li>
 * </ul>
 * Every other collection/action combination — including delete and
 * txn-control actions, every {@code typ + update} whose
 * {@code data.operation} is not {@code "add_property"}, and every
 * {@code ppy + create} whose {@code data.kind} is neither
 * {@code "definition"} nor {@code "assignment"} — is counted as
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
    static final String COLLECTION_PPY = 'ppy'
    static final String COLLECTION_PRC = 'prc'
    static final String COLLECTION_TSK = 'tsk'
    static final String COLLECTION_FIL = 'fil'

    static final String ACTION_CREATE = 'create'
    static final String ACTION_UPDATE = 'update'

    static final String FIELD_OPERATION = 'operation'
    static final String FIELD_PROPERTY_ID = 'property_id'
    static final String FIELD_ENTITY_ID = 'entity_id'
    static final String FIELD_VALUE = 'value'
    static final String FIELD_REQUIRED = 'required'
    static final String FIELD_PROPERTY_REFS = 'property_refs'
    static final String FIELD_KIND = 'kind'
    static final String FIELD_OBJECT_COLLECTION = 'object_collection'
    static final String FIELD_OBJECT_ID = 'object_id'
    static final String FIELD_PROPERTY_VALUES = 'property_values'
    static final String FIELD_PARENT_TYPE_ID = 'parent_type_id'
    static final String FIELD_OUTPUT_INPUT = 'output_input'
    static final String ENTRY_APPLIED_AT = 'applied_at'

    static final String FIELD_APPLY_STATE = 'apply_state'
    static final String FIELD_APPLY_STATE_AT = 'apply_state_at'
    static final String APPLY_STATE_UNKNOWN = 'unknown'
    /** Terminal apply_state names, index-aligned with {@link MaterializeResult#counters()}. */
    static final List<String> APPLY_STATES = List.of(
            'applied', 'duplicate', 'conflict', 'skipped_unsupported',
            'skipped_invalid', 'skipped_missing_target', 'skipped_unregistered_property')

    static final String OPERATION_ADD_PROPERTY = 'add_property'
    static final String KIND_DEFINITION = 'definition'
    static final String KIND_ASSIGNMENT = 'assignment'

    /** Collections that may receive object-targeted property assignments. */
    static final Set<String> OBJECT_ASSIGNMENT_COLLECTIONS =
            Set.of(COLLECTION_ENT, COLLECTION_LOC, COLLECTION_PRC, COLLECTION_TSK,
                    COLLECTION_FIL)

    /** Bound on the {@code parent_type_id} walk; prevents runaway chains. */
    static final int MAX_TYPE_INHERITANCE_DEPTH = 10

    /**
     * Object identifier convention (TASK-044):
     * {@code <org>~<grp>~<uuidv7>~<collection>~<suffix>}, where the UUIDv7
     * is the creating transaction's or creating message's UUID. The literal
     * {@code genesis} segment is the single sanctioned non-UUID exception
     * (the bootstrap {@code usr}); legacy composite assignment IDs are two
     * conforming IDs joined ({@code <object_id>~<property_id>}).
     * Enforcement is warn-only in this iteration; schema validation is
     * ledgered for the transitional-shapes cleanup.
     */
    static final java.util.regex.Pattern UUIDV7_SEGMENT = java.util.regex.Pattern.compile(
            '^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
    static final String GENESIS_SEGMENT = 'genesis'
    static final Set<String> ID_COLLECTION_SEGMENTS =
            Set.of('ent', 'fil', 'ppy', 'lnk', 'loc', 'prc', 'tsk', 'uni', 'grp', 'typ', 'vdn', 'usr')

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
        if (snapshot.messageCount != null && snapshot.messageCount != messages.size()) {
            log.warn('Committed snapshot size differs from the message_count recorded at ' +
                    'commit time — possible tampering or bug: txnId={}, messageCount={}, snapshotSize={}',
                    snapshot.txnId, snapshot.messageCount, messages.size())
        }
        return Flux.fromIterable(messages)
                .concatMap { CommittedTransactionMessage message ->
                    // Every terminal path increments exactly one counter and
                    // processing is strictly sequential, so the counter diff
                    // around one message names its apply_state (TASK-056).
                    List<Integer> before = result.counters()
                    processMessage(snapshot, message, result)
                            .then(Mono.defer {
                                stampApplyState(snapshot, message, applyStateOf(before, result.counters()))
                            })
                }
                .then(Mono.just(result)) as Mono<MaterializeResult>
    }

    private static String applyStateOf(List<Integer> before, List<Integer> after) {
        for (int i = 0; i < APPLY_STATES.size(); i++) {
            if (after[i] > before[i]) {
                return APPLY_STATES[i]
            }
        }
        return APPLY_STATE_UNKNOWN
    }

    /**
     * Stamp the WAL message row with its terminal outcome (TASK-056). The
     * {@code apply_state}-absent guard makes the FIRST terminal outcome win:
     * an idempotent re-run, whose repeat naturally resolves {@code duplicate},
     * cannot overwrite the original truth. A failed stamp fails the pass, so
     * the header stays unwatermarked and the sweep retries.
     */
    private Mono<Void> stampApplyState(CommittedTransactionSnapshot snapshot,
                                       CommittedTransactionMessage message,
                                       String applyState) {
        if (applyState == APPLY_STATE_UNKNOWN) {
            log.warn('No counter moved for a processed message; apply_state left unstamped: ' +
                    'txnId={}, msgUuid={}', snapshot.txnId, message.msgUuid)
            return Mono.empty()
        }
        String rowId = "${snapshot.txnId}${TRANSACTION_ID_SEPARATOR}${message.msgUuid}"
        Query query = Query.query(Criteria.where(FIELD_ID).is(rowId)
                .and(FIELD_APPLY_STATE).exists(false))
        Update update = new Update()
                .set(FIELD_APPLY_STATE, applyState)
                .set(FIELD_APPLY_STATE_AT, Instant.now())
        return mongoTemplate.updateFirst(query, update, COLLECTION_TRANSACTIONS).then() as Mono<Void>
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

        if (message.action == ACTION_CREATE
                && message.collection == COLLECTION_PPY
                && KIND_ASSIGNMENT == message.data?.get(FIELD_KIND)) {
            return processObjectPropertyAssignment(snapshot, message, result)
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
        warnIfNonconformingObjectId(docId, snapshot, message)

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

    /**
     * Materialize one object-targeted {@code ppy + create} assignment by
     * projecting the value onto the target object root under
     * {@code property_values.<property_id>}, gated by inheritance-aware type
     * registration. See the class Javadoc for the full decision table.
     */
    private Mono<Void> processObjectPropertyAssignment(CommittedTransactionSnapshot snapshot,
                                                       CommittedTransactionMessage message,
                                                       MaterializeResult result) {
        Map<String, Object> data = message.data
        String objectCollection = extractNonBlankString(data, FIELD_OBJECT_COLLECTION)
        String aliasObjectId = null
        if (objectCollection == null && extractNonBlankString(data, FIELD_OBJECT_ID) == null) {
            String legacyEntityId = extractNonBlankString(data, FIELD_ENTITY_ID)
            if (legacyEntityId != null) {
                log.warn('Deprecated entity_id-only assignment form; resolving as object_collection=ent. ' +
                        'Submit object_collection/object_id instead: entityId={}, txnId={}, msgUuid={}',
                        legacyEntityId, snapshot.txnId, message.msgUuid)
                objectCollection = COLLECTION_ENT
                aliasObjectId = legacyEntityId
            }
        }
        if (objectCollection == null || !OBJECT_ASSIGNMENT_COLLECTIONS.contains(objectCollection)) {
            log.error('Materializer skipping object assignment with missing or unsupported data.object_collection: ' +
                    'objectCollection={}, txnId={}, commitId={}, msgUuid={}',
                    objectCollection, snapshot.txnId, snapshot.commitId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }
        String objectId = extractNonBlankString(data, FIELD_OBJECT_ID) ?: aliasObjectId
        if (objectId == null) {
            log.error('Materializer skipping object assignment with missing or blank data.object_id: ' +
                    'objectCollection={}, txnId={}, commitId={}, msgUuid={}',
                    objectCollection, snapshot.txnId, snapshot.commitId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }
        String propertyId = extractPropertyId(data)
        if (propertyId == null) {
            log.error('Materializer skipping object assignment with missing or blank data.property_id: ' +
                    'objectId={}, txnId={}, commitId={}, msgUuid={}',
                    objectId, snapshot.txnId, snapshot.commitId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }
        if (!(data.get(FIELD_VALUE) instanceof Map)) {
            log.error('Materializer skipping object assignment whose data.value is missing or not a JSON object: ' +
                    'objectId={}, propertyId={}, txnId={}, commitId={}, msgUuid={}',
                    objectId, propertyId, snapshot.txnId, snapshot.commitId, message.msgUuid)
            result.skippedInvalid++
            return Mono.empty()
        }

        return mongoTemplate.findById(objectId, Map.class, objectCollection)
                .map({ Map existing -> Optional.of(existing) })
                .defaultIfEmpty(Optional.empty())
                .flatMap({ Optional<Map> rootProbe ->
                    if (!rootProbe.isPresent()) {
                        log.warn('Materializer skipping object assignment with missing target root: ' +
                                'objectCollection={}, objectId={}, propertyId={}, txnId={}, commitId={}, msgUuid={}',
                                objectCollection, objectId, propertyId,
                                snapshot.txnId, snapshot.commitId, message.msgUuid)
                        result.skippedMissingTarget++
                        return Mono.empty()
                    }
                    Map<String, Object> root = rootProbe.get() as Map<String, Object>
                    String typeId = extractNonBlankString(root, FIELD_TYPE_ID)
                    if (typeId == null) {
                        log.warn('Materializer skipping object assignment whose target root has no type_id; ' +
                                'the property cannot be registered: objectCollection={}, objectId={}, propertyId={}, txnId={}',
                                objectCollection, objectId, propertyId, snapshot.txnId)
                        result.skippedUnregisteredProperty++
                        return Mono.empty()
                    }
                    return isPropertyRegisteredInHierarchy(typeId, propertyId)
                            .flatMap({ Boolean registered ->
                                if (!registered) {
                                    log.warn('Materializer skipping object assignment whose property is not ' +
                                            'registered on the target type or any ancestor: typeId={}, ' +
                                            'objectCollection={}, objectId={}, propertyId={}, txnId={}',
                                            typeId, objectCollection, objectId, propertyId, snapshot.txnId)
                                    result.skippedUnregisteredProperty++
                                    return Mono.empty()
                                }
                                return projectPropertyValue(root, objectCollection, objectId,
                                        propertyId, data, snapshot, message, result)
                            })
                })
                .then() as Mono<Void>
    }

    /**
     * Inheritance-aware registration check: walk from {@code typeId} up the
     * {@code properties.parent_type_id} chain until a {@code typ} root lists
     * {@code propertyId} under {@code properties.property_refs}. A missing
     * ancestor root, an exhausted chain, a cycle, or exceeding
     * {@link #MAX_TYPE_INHERITANCE_DEPTH} resolves to {@code false}.
     */
    private Mono<Boolean> isPropertyRegisteredInHierarchy(String typeId, String propertyId) {
        return checkTypeChain(typeId, propertyId, new HashSet<String>(), 0)
    }

    private Mono<Boolean> checkTypeChain(String typeId, String propertyId,
                                         Set<String> visited, int depth) {
        if (typeId == null) {
            return Mono.just(Boolean.FALSE)
        }
        if (depth >= MAX_TYPE_INHERITANCE_DEPTH) {
            log.warn('Type inheritance walk exceeded depth {} at typeId={}; treating property as unregistered',
                    MAX_TYPE_INHERITANCE_DEPTH, typeId)
            return Mono.just(Boolean.FALSE)
        }
        if (!visited.add(typeId)) {
            log.warn('Type inheritance cycle detected at typeId={}; treating property as unregistered', typeId)
            return Mono.just(Boolean.FALSE)
        }
        return mongoTemplate.findById(typeId, Map.class, COLLECTION_TYP)
                .map({ Map existing -> Optional.of(existing) })
                .defaultIfEmpty(Optional.empty())
                .flatMap({ Optional<Map> typProbe ->
                    if (!typProbe.isPresent()) {
                        log.warn('Type inheritance walk found no typ root for typeId={}; treating property as unregistered',
                                typeId)
                        return Mono.just(Boolean.FALSE)
                    }
                    Map typRoot = typProbe.get()
                    if (readExistingPropertyRef(typRoot, propertyId) != null) {
                        return Mono.just(Boolean.TRUE)
                    }
                    return checkTypeChain(extractParentTypeId(typRoot), propertyId, visited, depth + 1)
                }) as Mono<Boolean>
    }

    private static String extractParentTypeId(Map typRoot) {
        if (typRoot == null) {
            return null
        }
        Object propertiesValue = typRoot.get(FIELD_PROPERTIES)
        if (!(propertiesValue instanceof Map)) {
            return null
        }
        Object parent = ((Map) propertiesValue).get(FIELD_PARENT_TYPE_ID)
        if (parent == null) {
            return null
        }
        String asString = parent.toString()
        return asString.trim().isEmpty() ? null : asString
    }

    /**
     * Write the property-value entry onto the already-fetched target root via
     * a dotted-path {@code $set}, honoring the shared duplicate rules. The
     * entry equality check ignores {@code applied_at}, mirroring the
     * {@code materialized_at} exemption on root duplicates.
     */
    private Mono<Void> projectPropertyValue(Map<String, Object> root,
                                            String objectCollection,
                                            String objectId,
                                            String propertyId,
                                            Map<String, Object> data,
                                            CommittedTransactionSnapshot snapshot,
                                            CommittedTransactionMessage message,
                                            MaterializeResult result) {
        Map<String, Object> entry = new LinkedHashMap<>()
        entry.put(FIELD_VALUE, new LinkedHashMap<>((Map<String, Object>) data.get(FIELD_VALUE)))
        entry.put(PROV_TXN_ID, snapshot.txnId)
        entry.put(PROV_COMMIT_ID, snapshot.commitId)
        entry.put(PROV_MSG_UUID, message.msgUuid)
        entry.put(ENTRY_APPLIED_AT, Instant.now())

        Map<String, Object> existingEntry = readExistingPropertyValue(root, propertyId)
        if (existingEntry != null) {
            if (samePropertyValueEntry(existingEntry, entry)) {
                log.info('Materialize duplicate matching object property value: ' +
                        'objectCollection={}, objectId={}, propertyId={}, txnId={}',
                        objectCollection, objectId, propertyId, snapshot.txnId)
                result.duplicateMatching++
            } else {
                log.error('Materialize conflicting object property value (not overwriting): ' +
                        'objectCollection={}, objectId={}, propertyId={}, txnId={}, commitId={}, msgUuid={}',
                        objectCollection, objectId, propertyId,
                        snapshot.txnId, snapshot.commitId, message.msgUuid)
                result.conflictingDuplicate++
            }
            return Mono.empty()
        }

        String dottedKey = FIELD_PROPERTY_VALUES + '.' + propertyId
        Query query = Query.query(Criteria.where(FIELD_ID).is(objectId))
        Update update = new Update().set(dottedKey, entry)
        return mongoTemplate.updateFirst(query, update, objectCollection)
                .doOnSuccess({ Object updateResult ->
                    log.info('Materialized object property value: objectCollection={}, objectId={}, ' +
                            'propertyId={}, txnId={}, commitId={}',
                            objectCollection, objectId, propertyId, snapshot.txnId, snapshot.commitId)
                    result.materialized++
                })
                .then() as Mono<Void>
    }

    private static Map<String, Object> readExistingPropertyValue(Map root, String propertyId) {
        if (root == null) {
            return null
        }
        Object valuesMap = root.get(FIELD_PROPERTY_VALUES)
        if (!(valuesMap instanceof Map)) {
            return null
        }
        Object entry = ((Map) valuesMap).get(propertyId)
        if (entry instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) entry)
        }
        return null
    }

    private static boolean samePropertyValueEntry(Map<String, Object> existing,
                                                  Map<String, Object> incoming) {
        Map<String, Object> existingCopy = new LinkedHashMap<>(existing)
        Map<String, Object> incomingCopy = new LinkedHashMap<>(incoming)
        existingCopy.remove(ENTRY_APPLIED_AT)
        incomingCopy.remove(ENTRY_APPLIED_AT)
        return Objects.equals(existingCopy, incomingCopy)
    }

    private static String extractPropertyId(Map<String, Object> data) {
        return extractNonBlankString(data, FIELD_PROPERTY_ID)
    }

    private static String extractNonBlankString(Map<String, Object> data, String field) {
        if (data == null) {
            return null
        }
        Object value = data.get(field)
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
                case COLLECTION_PRC:
                    return true
                case COLLECTION_TSK:
                    return true
                case COLLECTION_FIL:
                    return true
                case COLLECTION_TYP:
                    return true
                case COLLECTION_PPY:
                    Object kind = message.data?.get(FIELD_KIND)
                    return KIND_DEFINITION == kind || KIND_ASSIGNMENT == kind
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

    /**
     * Structural check against the object identifier convention: at least
     * five {@code ~}-separated segments, a UUIDv7 (or the sanctioned
     * {@code genesis} literal) in the third segment, and a known collection
     * abbreviation in the fourth. A composite legacy assignment ID (ten or
     * more segments) must conform in both halves.
     */
    static boolean isConformingObjectId(String id) {
        if (id == null) {
            return false
        }
        String[] segments = id.split('~')
        if (segments.length < 5) {
            return false
        }
        if (!conformingIdCore(segments, 0)) {
            return false
        }
        if (segments.length >= 10 && !conformingIdCore(segments, 5)) {
            return false
        }
        return true
    }

    private static boolean conformingIdCore(String[] segments, int offset) {
        String uuidSegment = segments[offset + 2]
        boolean uuidOk = GENESIS_SEGMENT == uuidSegment ||
                UUIDV7_SEGMENT.matcher(uuidSegment).matches()
        return uuidOk && ID_COLLECTION_SEGMENTS.contains(segments[offset + 3])
    }

    private static void warnIfNonconformingObjectId(String docId,
                                                    CommittedTransactionSnapshot snapshot,
                                                    CommittedTransactionMessage message) {
        if (!isConformingObjectId(docId)) {
            log.warn('data.id does not follow the object identifier convention ' +
                    '<org>~<grp>~<uuidv7>~<collection>~<suffix>: id={}, collection={}, txnId={}, msgUuid={}',
                    docId, message.collection, snapshot.txnId, message.msgUuid)
        }
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

        if (COLLECTION_PRC == message.collection
                && data.get(FIELD_OUTPUT_INPUT) instanceof Map) {
            // Canonical execution provenance is hoisted top-level, parallel
            // to lnk's left/right (DIRECTION.md, Procedures And Tasks).
            doc.put(FIELD_OUTPUT_INPUT, copyProperties(data.get(FIELD_OUTPUT_INPUT)))
        }

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
            if (key != FIELD_DATA_ID && key != FIELD_TYPE_ID && key != FIELD_LINKS
                    && key != FIELD_OUTPUT_INPUT) {
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
