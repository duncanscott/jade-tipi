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
import org.jadetipi.jadetipi.dto.GroupCreateRequest
import org.jadetipi.jadetipi.dto.GroupHead
import org.jadetipi.jadetipi.dto.GroupProvenance
import org.jadetipi.jadetipi.dto.GroupRecord
import org.jadetipi.jadetipi.dto.GroupUpdateRequest
import org.springframework.dao.DuplicateKeyException as SpringDuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Instant
import java.util.regex.Pattern

/**
 * Direct-write admin service for first-class root-shaped {@code grp}
 * records. This is the narrow local-development admin path authorized by
 * {@code TASK-021}; it bypasses the Kafka transaction → materializer flow
 * intentionally so {@code grp + update} semantics do not re-open the
 * accepted {@code TASK-020} materializer.
 *
 * <p>Persisted documents follow the {@code TASK-020} root-shape contract:
 * {@code _id == id}, {@code collection == "grp"}, {@code properties} carries
 * {@code name}, {@code description}, and the grp-id-keyed
 * {@code permissions} map (values exactly {@code "rw"} or {@code "r"}),
 * {@code links} is empty, and {@code _head.provenance} carries the audit
 * fields. Admin direct writes mark provenance with an
 * {@code admin~<uuid>} sentinel for {@code txn_id} and {@code commit_id}.
 *
 * <p>Validation surface (kept narrow):
 * <ul>
 *   <li>{@code name} must be non-blank, ≤ 255 chars.</li>
 *   <li>{@code description} is optional, ≤ 4096 chars.</li>
 *   <li>{@code permissions} keys must be non-blank; values must be exactly
 *       {@code "rw"} or {@code "r"}.</li>
 *   <li>For create-with-supplied-id, the id must match
 *       {@link #ID_PATTERN}.</li>
 * </ul>
 * Validation failures surface as {@link ResponseStatusException} with
 * {@code 400}; missing-id read/update surfaces as {@code 404}; create with
 * a duplicate id surfaces as {@code 409}.
 */
@Slf4j
@Service
class GroupAdminService {

    static final String COLLECTION_GRP = 'grp'
    static final String PERM_READ = 'r'
    static final String PERM_READ_WRITE = 'rw'
    static final String ACTION_CREATE = 'create'
    static final String ACTION_UPDATE = 'update'
    static final String ADMIN_TXN_PREFIX = 'admin~'

    static final int MAX_NAME_LENGTH = 255
    static final int MAX_DESCRIPTION_LENGTH = 4096

    static final Pattern ID_PATTERN = Pattern.compile('^[A-Za-z0-9._\\-~]+$')

    static final String FIELD_ID = '_id'
    static final String FIELD_DATA_ID = 'id'
    static final String FIELD_COLLECTION = 'collection'
    static final String FIELD_PROPERTIES = 'properties'
    static final String FIELD_LINKS = 'links'
    static final String FIELD_HEAD = '_head'

    static final String PROP_NAME = 'name'
    static final String PROP_DESCRIPTION = 'description'
    static final String PROP_PERMISSIONS = 'permissions'

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

    private final ReactiveMongoTemplate mongoTemplate

    GroupAdminService(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate
    }

    /**
     * Create a new {@code grp} root document. Returns the persisted record.
     * Throws {@code 400} on validation failure and {@code 409} on duplicate id.
     */
    Mono<GroupRecord> create(GroupCreateRequest request) {
        if (request == null) {
            return Mono.error(badRequest('request body is required'))
        }
        String name = trimToNull(request.name)
        if (name == null) {
            return Mono.error(badRequest('name is required'))
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return Mono.error(badRequest("name exceeds ${MAX_NAME_LENGTH} characters"))
        }
        String description = request.description
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return Mono.error(badRequest("description exceeds ${MAX_DESCRIPTION_LENGTH} characters"))
        }
        Map<String, String> permissions = validatePermissions(request.permissions)
        if (permissions == null) {
            return Mono.error(badRequest('permissions values must be exactly "rw" or "r" and keys must be non-blank'))
        }

        String suppliedId = trimToNull(request.id)
        if (suppliedId != null && !ID_PATTERN.matcher(suppliedId).matches()) {
            return Mono.error(badRequest("id must match ${ID_PATTERN.pattern()}"))
        }
        String groupId = suppliedId ?: synthesizeId(name)

        Instant now = Instant.now()
        Map<String, Object> doc = buildDocument(groupId, name, description, permissions, ACTION_CREATE, now)

        return mongoTemplate.insert(doc, COLLECTION_GRP)
                .doOnSuccess { Object inserted ->
                    log.info('Admin create grp: id={}, txnId={}', groupId, doc[FIELD_HEAD][HEAD_PROVENANCE][PROV_TXN_ID])
                }
                .map { Object inserted -> projectRecord((Map<String, Object>) inserted) }
                .onErrorResume { Throwable ex ->
                    if (isDuplicateKey(ex)) {
                        log.warn('Admin create grp duplicate id: id={}', groupId)
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "grp already exists with id ${groupId}"))
                    }
                    return Mono.error(ex)
                } as Mono<GroupRecord>
    }

    /**
     * List all {@code grp} root documents. Returns the projected admin
     * records sorted by id.
     */
    Flux<GroupRecord> list() {
        Query query = new Query()
        return mongoTemplate.find(query, Map.class, COLLECTION_GRP)
                .map { Map doc -> projectRecord((Map<String, Object>) doc) }
                .sort { a, b -> a.id <=> b.id } as Flux<GroupRecord>
    }

    /**
     * Read one {@code grp} root document by id. Empty Mono indicates 404.
     */
    Mono<GroupRecord> findById(String id) {
        String trimmed = trimToNull(id)
        if (trimmed == null) {
            return Mono.empty()
        }
        return mongoTemplate.findById(trimmed, Map.class, COLLECTION_GRP)
                .map { Map doc -> projectRecord((Map<String, Object>) doc) } as Mono<GroupRecord>
    }

    /**
     * Full-replacement update of the editable fields on an existing
     * {@code grp} root. Returns empty Mono when the id does not exist.
     * Throws {@code 400} on validation failure.
     */
    Mono<GroupRecord> update(String id, GroupUpdateRequest request) {
        String trimmed = trimToNull(id)
        if (trimmed == null) {
            return Mono.error(badRequest('id path variable is required'))
        }
        if (request == null) {
            return Mono.error(badRequest('request body is required'))
        }
        String name = trimToNull(request.name)
        if (name == null) {
            return Mono.error(badRequest('name is required'))
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return Mono.error(badRequest("name exceeds ${MAX_NAME_LENGTH} characters"))
        }
        String description = request.description
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return Mono.error(badRequest("description exceeds ${MAX_DESCRIPTION_LENGTH} characters"))
        }
        Map<String, String> permissions = validatePermissions(request.permissions)
        if (permissions == null) {
            return Mono.error(badRequest('permissions values must be exactly "rw" or "r" and keys must be non-blank'))
        }

        Instant now = Instant.now()
        String adminTxnId = newAdminTxnId()
        String msgUuid = UUID.randomUUID().toString()

        Map<String, Object> properties = new LinkedHashMap<>()
        properties.put(PROP_NAME, name)
        if (description != null) {
            properties.put(PROP_DESCRIPTION, description)
        }
        properties.put(PROP_PERMISSIONS, copyPermissions(permissions))

        Map<String, Object> provenance = buildProvenance(adminTxnId, msgUuid, ACTION_UPDATE, now)

        Update mongoUpdate = new Update()
                .set(FIELD_PROPERTIES, properties)
                .set(FIELD_HEAD + '.' + HEAD_SCHEMA_VERSION, ROOT_SCHEMA_VERSION)
                .set(FIELD_HEAD + '.' + HEAD_DOCUMENT_KIND, DOCUMENT_KIND_ROOT)
                .set(FIELD_HEAD + '.' + HEAD_ROOT_ID, trimmed)
                .set(FIELD_HEAD + '.' + HEAD_PROVENANCE, provenance)

        Query query = new Query(Criteria.where(FIELD_ID).is(trimmed))

        return mongoTemplate.findAndModify(query, mongoUpdate, Map.class, COLLECTION_GRP)
                .flatMap { Map ignored ->
                    return mongoTemplate.findById(trimmed, Map.class, COLLECTION_GRP)
                }
                .doOnSuccess { Map doc ->
                    if (doc != null) {
                        log.info('Admin update grp: id={}, txnId={}', trimmed, adminTxnId)
                    } else {
                        log.warn('Admin update grp not found: id={}', trimmed)
                    }
                }
                .map { Map doc -> projectRecord((Map<String, Object>) doc) } as Mono<GroupRecord>
    }

    private static Map<String, Object> buildDocument(String groupId,
                                                     String name,
                                                     String description,
                                                     Map<String, String> permissions,
                                                     String action,
                                                     Instant now) {
        Map<String, Object> properties = new LinkedHashMap<>()
        properties.put(PROP_NAME, name)
        if (description != null) {
            properties.put(PROP_DESCRIPTION, description)
        }
        properties.put(PROP_PERMISSIONS, copyPermissions(permissions))

        String adminTxnId = newAdminTxnId()
        String msgUuid = UUID.randomUUID().toString()

        Map<String, Object> provenance = buildProvenance(adminTxnId, msgUuid, action, now)

        Map<String, Object> head = new LinkedHashMap<>()
        head.put(HEAD_SCHEMA_VERSION, ROOT_SCHEMA_VERSION)
        head.put(HEAD_DOCUMENT_KIND, DOCUMENT_KIND_ROOT)
        head.put(HEAD_ROOT_ID, groupId)
        head.put(HEAD_PROVENANCE, provenance)

        Map<String, Object> doc = new LinkedHashMap<>()
        doc.put(FIELD_ID, groupId)
        doc.put(FIELD_DATA_ID, groupId)
        doc.put(FIELD_COLLECTION, COLLECTION_GRP)
        doc.put(FIELD_PROPERTIES, properties)
        doc.put(FIELD_LINKS, new LinkedHashMap<String, Object>())
        doc.put(FIELD_HEAD, head)
        return doc
    }

    private static Map<String, Object> buildProvenance(String adminTxnId,
                                                       String msgUuid,
                                                       String action,
                                                       Instant now) {
        Map<String, Object> provenance = new LinkedHashMap<>()
        provenance.put(PROV_TXN_ID, adminTxnId)
        provenance.put(PROV_COMMIT_ID, adminTxnId)
        provenance.put(PROV_MSG_UUID, msgUuid)
        provenance.put(PROV_COLLECTION, COLLECTION_GRP)
        provenance.put(PROV_ACTION, action)
        provenance.put(PROV_COMMITTED_AT, now)
        provenance.put(PROV_MATERIALIZED_AT, now)
        return provenance
    }

    private static Map<String, String> validatePermissions(Map<String, String> source) {
        if (source == null) {
            return new LinkedHashMap<>()
        }
        Map<String, String> validated = new LinkedHashMap<>()
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.key
            String value = entry.value
            if (key == null || key.trim().isEmpty()) {
                return null
            }
            if (value != PERM_READ && value != PERM_READ_WRITE) {
                return null
            }
            validated.put(key, value)
        }
        return validated
    }

    private static Map<String, String> copyPermissions(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>()
        if (source != null) {
            copy.putAll(source)
        }
        return copy
    }

    private static String newAdminTxnId() {
        return ADMIN_TXN_PREFIX + UUID.randomUUID().toString()
    }

    /**
     * Synthesize a world-unique grp id following the canonical example shape
     * {@code jade-tipi-org~dev~<uuid>~grp~<slug>}. Slug is derived from the
     * group name with non-alphanumerics collapsed to single dashes and
     * lowercased.
     */
    private static String synthesizeId(String name) {
        String slug = name == null ? '' : name.toLowerCase(Locale.ROOT)
                .replaceAll('[^a-z0-9]+', '-')
                .replaceAll('^-+|-+$', '')
        if (slug.isEmpty()) {
            slug = 'grp'
        }
        return "jade-tipi-org~dev~${UUID.randomUUID()}~grp~${slug}"
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null
        }
        String trimmed = value.trim()
        return trimmed.isEmpty() ? null : trimmed
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message)
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

    /**
     * Project a stored Mongo {@code grp} document onto the admin
     * {@link GroupRecord} DTO. Returns {@code null} when the document is
     * {@code null}.
     */
    static GroupRecord projectRecord(Map<String, Object> doc) {
        if (doc == null) {
            return null
        }
        Map<String, Object> properties = (Map<String, Object>) (doc[FIELD_PROPERTIES] ?: [:])
        Map<String, Object> head = (Map<String, Object>) doc[FIELD_HEAD]

        GroupRecord record = new GroupRecord()
        record.id = (doc[FIELD_DATA_ID] ?: doc[FIELD_ID])?.toString()
        record.collection = doc[FIELD_COLLECTION]?.toString()
        record.name = properties[PROP_NAME]?.toString()
        record.description = properties[PROP_DESCRIPTION]?.toString()
        Object perms = properties[PROP_PERMISSIONS]
        if (perms instanceof Map) {
            Map<String, String> permsCopy = new LinkedHashMap<>()
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) perms).entrySet()) {
                if (entry.value != null) {
                    permsCopy.put(entry.key.toString(), entry.value.toString())
                }
            }
            record.permissions = permsCopy
        } else {
            record.permissions = new LinkedHashMap<>()
        }

        if (head != null) {
            GroupHead headDto = new GroupHead()
            Object schemaVersion = head[HEAD_SCHEMA_VERSION]
            if (schemaVersion instanceof Number) {
                headDto.schemaVersion = ((Number) schemaVersion).intValue()
            }
            headDto.documentKind = head[HEAD_DOCUMENT_KIND]?.toString()
            headDto.rootId = head[HEAD_ROOT_ID]?.toString()
            Object provenance = head[HEAD_PROVENANCE]
            if (provenance instanceof Map) {
                Map<String, Object> p = (Map<String, Object>) provenance
                GroupProvenance provDto = new GroupProvenance()
                provDto.txnId = p[PROV_TXN_ID]?.toString()
                provDto.commitId = p[PROV_COMMIT_ID]?.toString()
                provDto.msgUuid = p[PROV_MSG_UUID]?.toString()
                provDto.collection = p[PROV_COLLECTION]?.toString()
                provDto.action = p[PROV_ACTION]?.toString()
                Object committedAt = p[PROV_COMMITTED_AT]
                if (committedAt instanceof Instant) {
                    provDto.committedAt = (Instant) committedAt
                } else if (committedAt instanceof Date) {
                    provDto.committedAt = ((Date) committedAt).toInstant()
                } else if (committedAt != null) {
                    provDto.committedAt = Instant.parse(committedAt.toString())
                }
                Object materializedAt = p[PROV_MATERIALIZED_AT]
                if (materializedAt instanceof Instant) {
                    provDto.materializedAt = (Instant) materializedAt
                } else if (materializedAt instanceof Date) {
                    provDto.materializedAt = ((Date) materializedAt).toInstant()
                } else if (materializedAt != null) {
                    provDto.materializedAt = Instant.parse(materializedAt.toString())
                }
                headDto.provenance = provDto
            }
            record.head = headDto
        }
        return record
    }
}
