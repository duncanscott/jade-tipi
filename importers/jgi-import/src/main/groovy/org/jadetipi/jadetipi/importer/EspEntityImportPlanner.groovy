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

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Plans esp entities into the dependency-ordered import queue (TASK-069,
 * phase 3): the begat ancestry first (parents recursively, so provenance
 * targets exist before their links), then the entity's container (itself
 * planned recursively — containers can have ancestry too), then the
 * entity's own type row, then the entity. The esp link types bootstrap
 * once per plan. Dedup rides the queue's natural id, so shared ancestors
 * keep their original — lower — seq across plans.
 *
 * <p>{@link #planEspEntitiesByTypeName} discovers entities through the
 * replica's own {@code _design/entity_views/_view/by_type_name}.
 */
@Slf4j
@Service
class EspEntityImportPlanner {

    static final String KIND_TYPE = 'esp_type'
    static final String KIND_ENTITY = 'esp_entity'
    /** Recursion guard: real ancestries run ~5-10 deep; a cycle would loop. */
    static final int MAX_DEPTH = 50

    private final CouchDbDocumentReader reader
    private final ImportQueueService queue
    private final EspWorkflowConfigService workflowConfig

    EspEntityImportPlanner(CouchDbDocumentReader reader, ImportQueueService queue,
                           EspWorkflowConfigService workflowConfig) {
        this.reader = reader
        this.queue = queue
        this.workflowConfig = workflowConfig
    }

    /**
     * Enqueue the full dependency-ordered plan for one esp entity uuid.
     * Returns the number of newly enqueued rows.
     */
    Mono<Long> planEspEntity(String uuid) {
        Set<String> visited = [] as Set
        return enqueueBootstrap()
                .concatWith(planEntityRecursive(uuid, visited, 0))
                .reduce(0L, { Long acc, Boolean inserted -> inserted ? acc + 1 : acc }) as Mono<Long>
    }

    /**
     * Discover entities of one esp type name (raw display name, e.g.
     * 'Aliquot') through the by_type_name view and plan each;
     * {@code limit} (when positive) caps how many entities are planned.
     */
    Mono<Long> planEspEntitiesByTypeName(String typeName, Integer limit) {
        return reader.docIdsByViewKey(EspEntityImportMapper.DATABASE,
                'entity_views', 'by_type_name', typeName, limit)
                .concatMap { String uuid ->
                    planEspEntity(uuid)
                            .doOnNext { Long rows ->
                                log.info('Planned esp entity {}: {} newly enqueued row(s)', uuid, rows)
                            }
                }
                .reduce(0L, { Long acc, Long rows -> acc + rows }) as Mono<Long>
    }

    private Flux<Boolean> planEntityRecursive(String uuid, Set<String> visited, int depth) {
        if (uuid == null || !visited.add(uuid)) {
            return Flux.empty()
        }
        if (depth > MAX_DEPTH) {
            log.warn('esp ancestry deeper than {} at {}, truncating (cycle?)', MAX_DEPTH, uuid)
            return Flux.empty()
        }
        return reader.findDocument(EspEntityImportMapper.DATABASE, uuid)
                .flatMapMany { Map<String, Object> doc ->
                    Flux<Boolean> parentsFirst = Flux.fromIterable(
                            ClarityAliquotImportMapper.asImportList(doc.get('parents')))
                            .concatMap { Object edge ->
                                String parentUuid = edge instanceof Map
                                        ? ((Map) edge).get('uuid') as String : null
                                planEntityRecursive(parentUuid, visited, depth + 1)
                            }
                    Map container = doc.get('container') instanceof Map
                            ? (Map) doc.get('container') : null
                    Flux<Boolean> containerNext = planEntityRecursive(
                            container?.get('uuid') as String, visited, depth + 1)
                    Flux<Boolean> typeRow = enqueueRow(EspEntityImportMapper.typeKey(
                            doc.get('class_name') as String, doc.get('type_name') as String),
                            KIND_TYPE)
                    // procedure types for the lab workflows this carrier owns
                    // (TASK-070) — enqueued before the entity that references them
                    List<Map<String, Object>> instances =
                            EspEntityImportMapper.reconstructWorkflowInstances(doc, workflowConfig)
                    Set<String> procTypeKeys = instances
                            .collect { EspEntityImportMapper.procedureTypeKey(it.get('workflow_name') as String) }
                            .toSet()
                    Flux<Boolean> procTypeRows = Flux.fromIterable(procTypeKeys)
                            .concatMap { String k -> enqueueRow(k, KIND_TYPE) }
                    return parentsFirst.concatWith(containerNext)
                            .concatWith(typeRow)
                            .concatWith(procTypeRows)
                            .concatWith(enqueueRow(uuid, KIND_ENTITY))
                }
                .switchIfEmpty(Flux.defer {
                    log.warn('esp entity document not found, skipped: {}', uuid)
                    return Flux.<Boolean> empty()
                })
    }

    private Flux<Boolean> enqueueBootstrap() {
        return Flux.fromIterable(EspEntityImportMapper.BOOTSTRAP_KEYS)
                .concatMap { String key -> enqueueRow(key, KIND_TYPE) }
    }

    private Flux<Boolean> enqueueRow(String key, String kind) {
        return Flux.defer {
            queue.enqueue(EspEntityImportMapper.SOURCE, key, kind).flux()
        }
    }
}
