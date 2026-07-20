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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.dao.DuplicateKeyException as SpringDuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

import java.time.Duration
import java.time.Instant

/**
 * Ensures the reserved bootstrap {@code usr} root ({@code jdtp-admin}) exists
 * as a genesis storage fact (TASK-039; contract in
 * {@code docs/architecture/object-property-model-drift.md} section 8.2.5).
 *
 * <p>{@code jdtp-admin} is a system/audit identity, not a login account and
 * not an external identity-provider user. It exists so the first durable
 * transactions can carry an attributable {@code writer.user_id} without a
 * user/transaction creation cycle. The root deliberately carries no external
 * identity keys (no ORCID, no OIDC issuer/subject) so identity resolution can
 * never match it.
 *
 * <p>The ensure runs at startup as a {@link CommandLineRunner} (the
 * {@code MongoDbInitializer} precedent), is insert-if-absent by {@code _id},
 * and tolerates a concurrent duplicate insert. Unlike a Kafka-materialized
 * root, the document is written directly with a {@code genesis~jdtp-admin}
 * sentinel under {@code _head.provenance.txn_id}/{@code commit_id}, mirroring
 * the accepted {@code admin~<uuid>} sentinel from {@link GroupAdminService}.
 * A failed ensure is logged and does not abort application startup; the
 * bootstrap identity is only required once transactions reference it.
 *
 * <p>{@code usr} is intentionally absent from the wire {@code Collection}
 * enum and {@code message.schema.json}; genesis creation is backend-internal.
 */
@Slf4j
@Service
class UsrGenesisService implements CommandLineRunner {

    static final String COLLECTION_USR = 'usr'
    static final String GENESIS_SEGMENT = 'genesis'
    static final String BOOTSTRAP_SUFFIX = 'jdtp-admin'
    static final String GENESIS_TXN_SENTINEL = 'genesis~jdtp-admin'
    static final String BOOTSTRAP_DISPLAY_NAME = 'JDTP Bootstrap Admin'
    static final String ID_SEPARATOR = '~'

    static final String FIELD_ID = '_id'
    static final String FIELD_DATA_ID = 'id'
    static final String FIELD_COLLECTION = 'collection'
    static final String FIELD_TYPE_ID = 'type_id'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_HEAD = '_head'

    static final String PROP_KIND = 'kind'
    static final String PROP_DISPLAY_NAME = 'display_name'
    static final String PROP_STATUS = 'status'
    static final String PROP_IDENTITY_PROVENANCE = 'identity_provenance'
    static final String PROP_SOURCE = 'source'
    static final String PROP_FIRST_SEEN_AT = 'first_seen_at'

    static final String KIND_SYSTEM = 'system'
    static final String STATUS_RESERVED = 'reserved'
    static final String SOURCE_BOOTSTRAP = 'bootstrap'

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

    static final String ACTION_CREATE = 'create'

    private static final Duration ENSURE_TIMEOUT = Duration.ofSeconds(30)

    private final ReactiveMongoTemplate mongoTemplate
    private final String instanceOrg
    private final String instanceGrp
    private final boolean genesisEnabled

    UsrGenesisService(ReactiveMongoTemplate mongoTemplate,
                      @Value('${jadetipi.instance.org:jade-tipi-org}') String instanceOrg,
                      @Value('${jadetipi.instance.grp:dev}') String instanceGrp,
                      @Value('${jadetipi.genesis.enabled:true}') boolean genesisEnabled) {
        this.mongoTemplate = mongoTemplate
        this.instanceOrg = instanceOrg
        this.instanceGrp = instanceGrp
        this.genesisEnabled = genesisEnabled
    }

    /**
     * The stable, well-known bootstrap {@code usr} ID:
     * {@code genesis~<instance_org>~<instance_grp>~usr~jdtp-admin}. The
     * literal {@code genesis} segment sits in the leading timestamp position
     * of the world-unique ID convention (uuid~org~grp~collection~suffix),
     * marking the record as a genesis fact rather than a generated ID.
     */
    String bootstrapUsrId() {
        return GENESIS_SEGMENT + ID_SEPARATOR + instanceOrg + ID_SEPARATOR + instanceGrp +
                ID_SEPARATOR + COLLECTION_USR + ID_SEPARATOR + BOOTSTRAP_SUFFIX
    }

    @Override
    void run(String... args) {
        if (!genesisEnabled) {
            log.info('Genesis bootstrap usr ensure disabled (jadetipi.genesis.enabled=false)')
            return
        }
        try {
            UsrGenesisResult result = ensureBootstrapUsr().block(ENSURE_TIMEOUT)
            log.info('Genesis bootstrap usr ensure completed: id={}, result={}', bootstrapUsrId(), result)
        } catch (Exception ex) {
            log.error('Genesis bootstrap usr ensure failed; continuing startup. ' +
                    'Durable transactions cannot reference the bootstrap writer until it exists: id={}',
                    bootstrapUsrId(), ex)
        }
    }

    /**
     * Insert-if-absent ensure for the bootstrap {@code usr} root. Returns
     * {@link UsrGenesisResult#CREATED} when this call inserted the root and
     * {@link UsrGenesisResult#ALREADY_PRESENT} when it already existed,
     * including the concurrent-duplicate-insert race.
     */
    Mono<UsrGenesisResult> ensureBootstrapUsr() {
        String id = bootstrapUsrId()
        return mongoTemplate.findById(id, Map.class, COLLECTION_USR)
                .map { Map existing -> UsrGenesisResult.ALREADY_PRESENT }
                .switchIfEmpty(Mono.defer { insertBootstrapUsr(id) }) as Mono<UsrGenesisResult>
    }

    private Mono<UsrGenesisResult> insertBootstrapUsr(String id) {
        Map<String, Object> doc = buildBootstrapUsrDocument(id, Instant.now())
        return mongoTemplate.insert(doc, COLLECTION_USR)
                .doOnSuccess { Object inserted ->
                    log.info('Created genesis bootstrap usr root: id={}', id)
                }
                .map { Object inserted -> UsrGenesisResult.CREATED }
                .onErrorResume { Throwable ex ->
                    if (isDuplicateKey(ex)) {
                        log.info('Genesis bootstrap usr root already created concurrently: id={}', id)
                        return Mono.just(UsrGenesisResult.ALREADY_PRESENT)
                    }
                    return Mono.error(ex)
                } as Mono<UsrGenesisResult>
    }

    /**
     * Build the bootstrap {@code usr} root following the accepted
     * root-document contract. The properties mark the record as
     * reserved/system/bootstrap; no external identity keys are present.
     */
    static Map<String, Object> buildBootstrapUsrDocument(String id, Instant now) {
        Map<String, Object> identityProvenance = new LinkedHashMap<>()
        identityProvenance.put(PROP_SOURCE, SOURCE_BOOTSTRAP)
        identityProvenance.put(PROP_FIRST_SEEN_AT, now)

        Map<String, Object> properties = new LinkedHashMap<>()
        properties.put(PROP_KIND, KIND_SYSTEM)
        properties.put(PROP_DISPLAY_NAME, BOOTSTRAP_DISPLAY_NAME)
        properties.put(PROP_STATUS, STATUS_RESERVED)
        properties.put(PROP_IDENTITY_PROVENANCE, identityProvenance)

        Map<String, Object> provenance = new LinkedHashMap<>()
        provenance.put(PROV_TXN_ID, GENESIS_TXN_SENTINEL)
        provenance.put(PROV_COMMIT_ID, GENESIS_TXN_SENTINEL)
        provenance.put(PROV_MSG_UUID, UUID.randomUUID().toString())
        provenance.put(PROV_COLLECTION, COLLECTION_USR)
        provenance.put(PROV_ACTION, ACTION_CREATE)
        provenance.put(PROV_COMMITTED_AT, now)
        provenance.put(PROV_MATERIALIZED_AT, now)

        Map<String, Object> head = new LinkedHashMap<>()
        head.put(HEAD_SCHEMA_VERSION, ROOT_SCHEMA_VERSION)
        head.put(HEAD_DOCUMENT_KIND, DOCUMENT_KIND_ROOT)
        head.put(HEAD_ROOT_ID, id)
        head.put(HEAD_PROVENANCE, provenance)

        Map<String, Object> doc = new LinkedHashMap<>()
        doc.put(FIELD_ID, id)
        doc.put(FIELD_DATA_ID, id)
        doc.put(FIELD_COLLECTION, COLLECTION_USR)
        doc.put(FIELD_TYPE_ID, null)
        doc.put(FIELD_PROPERTIES, properties)
        doc.put(FIELD_LINKS, new LinkedHashMap<String, Object>())
        doc.put(FIELD_HEAD, head)
        return doc
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
