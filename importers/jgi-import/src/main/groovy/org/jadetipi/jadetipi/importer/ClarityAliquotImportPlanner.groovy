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
 * Plans clarity processes into the dependency-ordered import queue
 * (TASK-059 slice, TASK-067 generalization;
 * docs/architecture/bulk-import-design.md): bootstrap type rows and the
 * process's own procedure-type row first, then each artifact's container
 * and submitted sample before the artifact (inputs before outputs), then
 * the process last. Enqueueing an already-queued row is a no-op that
 * preserves its original — lower — seq, so shared dependencies across
 * processes keep the ordering invariant. {@link #planProcessesByType}
 * discovers process documents through the replica's own process-type
 * view, so any of the 51 clarity process types plans the same way.
 */
@Slf4j
@Service
class ClarityAliquotImportPlanner {

    static final String DATABASE = 'clarity'
    static final String KIND_TYPE = 'type'
    static final String KIND_CONTAINER = 'container'
    static final String KIND_SAMPLE = 'sample'
    static final String KIND_ARTIFACT = 'artifact'
    static final String KIND_PROCESS = 'process'
    static final String KIND_FILE_PROPERTY = 'file_property'
    static final String KIND_FILE = 'file'

    private final CouchDbDocumentReader reader
    private final ImportQueueService queue

    ClarityAliquotImportPlanner(CouchDbDocumentReader reader, ImportQueueService queue) {
        this.reader = reader
        this.queue = queue
    }

    /**
     * Enqueue the full dependency-ordered plan for one process document
     * ({@code processes_<limsid>}). Returns the number of newly enqueued
     * rows (already-queued rows are skipped, keeping their seq).
     */
    Mono<Long> planProcess(String processDocId) {
        return reader.findDocument(DATABASE, processDocId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Clarity process document not found: ${processDocId}")))
                .flatMap { Map<String, Object> process ->
                    List iom = ClarityAliquotImportMapper.asImportList(
                            ((process.get('json') ?: [:]) as Map).get('input-output-map'))
                    List<String> inputLimsids = []
                    List<String> outputLimsids = []
                    iom.each { Object entry ->
                        Map mapping = entry instanceof Map ? (Map) entry : [:]
                        Object inp = mapping.get('input')
                        Object out = mapping.get('output')
                        String inLimsid = inp instanceof Map ? ((Map) inp).get('limsid') : null
                        String outLimsid = out instanceof Map ? ((Map) out).get('limsid') : null
                        if (inLimsid && !inputLimsids.contains(inLimsid)) {
                            inputLimsids.add(inLimsid)
                        }
                        if (outLimsid && !outputLimsids.contains(outLimsid)) {
                            outputLimsids.add(outLimsid)
                        }
                    }
                    // inputs before outputs; each artifact preceded by its container
                    List<String> artifactLimsids = inputLimsids + outputLimsids.findAll {
                        !inputLimsids.contains(it)
                    }
                    // element text lands under the '' key of its JSON object
                    String typeName = (((process.get('json') ?: [:]) as Map)
                            .get('type') as Map)?.get('') ?: 'unknown'
                    return enqueueBootstrap()
                            .concatWith(enqueueRow(
                                    ClarityAliquotImportMapper.processTypeKey(typeName), KIND_TYPE))
                            .concatWith(Flux.fromIterable(artifactLimsids)
                                    .concatMap { String limsid -> enqueueArtifactWithDependencies(limsid) })
                            .concatWith(enqueueRow(ClarityAliquotImportMapper.processKey(
                                    processDocId.replaceFirst('^processes_', '')), KIND_PROCESS))
                            .reduce(0L, { Long acc, Boolean inserted -> inserted ? acc + 1 : acc })
                } as Mono<Long>
    }

    /**
     * Discover and plan every process of one clarity process type (raw
     * display name, e.g. 'LP Pool Creation') via the replica's
     * process-type view; {@code limit} (when positive) caps how many
     * process documents are planned. Returns the number of newly enqueued
     * rows across all planned processes.
     */
    Mono<Long> planProcessesByType(String processTypeName, Integer limit) {
        return reader.processDocIdsByType(DATABASE, processTypeName, limit)
                .concatMap { String processDocId ->
                    planProcess(processDocId)
                            .doOnNext { Long rows ->
                                log.info('Planned {}: {} newly enqueued row(s)', processDocId, rows)
                            }
                }
                .reduce(0L, { Long acc, Long rows -> acc + rows }) as Mono<Long>
    }

    /**
     * Plan the clarity files pass (TASK-068): walk every {@code files_}
     * document, and for each whose attached-to artifact has already been
     * imported (its queue row carries a recorded jdtp id), enqueue the
     * file row — plus, once, the file-property declaration rows (the ppy
     * definitions and their add_property registrations, which must
     * precede the assignments). Files attached to artifacts outside the
     * imported scope are skipped and counted. {@code limit} (when
     * positive) bounds how many file DOCUMENTS are scanned — the walk is
     * the expensive part; leave it unset for the full production pass.
     */
    Mono<Long> planFiles(Integer limit) {
        java.util.concurrent.atomic.AtomicLong planned = new java.util.concurrent.atomic.AtomicLong()
        java.util.concurrent.atomic.AtomicLong skipped = new java.util.concurrent.atomic.AtomicLong()
        java.util.concurrent.atomic.AtomicBoolean propertiesEnqueued =
                new java.util.concurrent.atomic.AtomicBoolean(false)

        Flux<String> fileDocIds = reader.docIdsByPrefix(DATABASE, 'files_')
        if (limit != null && limit > 0) {
            fileDocIds = fileDocIds.take((long) limit)
        }
        return fileDocIds
                .concatMap { String fileDocId -> planOneFile(fileDocId, planned, skipped, propertiesEnqueued) }
                .reduce(0L, { Long acc, Long rows -> acc + rows })
                .doOnNext { Long rows ->
                    log.info('Files pass planned {} file(s) ({} newly enqueued row(s)); {} skipped (artifact not imported)',
                            planned.get(), rows, skipped.get())
                } as Mono<Long>
    }

    /**
     * One file document: 0 rows when skipped, otherwise the newly
     * enqueued row count (property declarations ride the first eligible
     * file). Emitted as a single element so the plan limit truncates
     * between files, never inside one.
     */
    private Mono<Long> planOneFile(String fileDocId,
                                   java.util.concurrent.atomic.AtomicLong planned,
                                   java.util.concurrent.atomic.AtomicLong skipped,
                                   java.util.concurrent.atomic.AtomicBoolean propertiesEnqueued) {
        return reader.findDocument(DATABASE, fileDocId)
                .flatMap { Map<String, Object> fileDoc ->
                    Map json = (fileDoc.get('json') ?: [:]) as Map
                    String attachedTo = json.get('attached-to') as String
                    String artifactLimsid = attachedTo ? attachedTo.tokenize('/').last() : null
                    if (!artifactLimsid) {
                        skipped.incrementAndGet()
                        return Mono.just(0L)
                    }
                    String artifactRowId = ImportQueueService.rowId(
                            ClarityAliquotImportMapper.SOURCE,
                            ClarityAliquotImportMapper.artifactKey(artifactLimsid))
                    return queue.jdtpIdOf(artifactRowId)
                            .flatMap { String recorded ->
                                planned.incrementAndGet()
                                Flux<Boolean> propertyRows =
                                        propertiesEnqueued.compareAndSet(false, true)
                                                ? Flux.fromIterable(ClarityAliquotImportMapper.FILE_PROPERTY_KEYS)
                                                .concatMap { String key ->
                                                    enqueueRow(key, KIND_FILE_PROPERTY)
                                                }
                                                : Flux.<Boolean>empty()
                                return propertyRows.concatWith(enqueueRow(fileDocId, KIND_FILE))
                                        .reduce(0L, { Long acc, Boolean inserted ->
                                            inserted ? acc + 1 : acc
                                        })
                            }
                            .switchIfEmpty(Mono.defer {
                                skipped.incrementAndGet()
                                return Mono.just(0L)
                            })
                } as Mono<Long>
    }

    private Flux<Boolean> enqueueBootstrap() {
        return Flux.fromIterable(ClarityAliquotImportMapper.BOOTSTRAP_KEYS)
                .concatMap { String key -> enqueueRow(key, KIND_TYPE) }
    }

    private Flux<Boolean> enqueueArtifactWithDependencies(String artifactLimsid) {
        String artifactKey = ClarityAliquotImportMapper.artifactKey(artifactLimsid)
        return reader.findDocument(DATABASE, artifactKey)
                .flatMapMany { Map<String, Object> artifact ->
                    Map json = (artifact.get('json') ?: [:]) as Map
                    String containerLimsid = ((json.get('location') as Map)
                            ?.get('container') as Map)?.get('limsid')
                    Flux<Boolean> containerFirst = containerLimsid
                            ? enqueueRow(ClarityAliquotImportMapper.containerKey(containerLimsid),
                                    KIND_CONTAINER)
                            : Flux.<Boolean>empty()
                    Flux<Boolean> samplesNext = Flux.fromIterable(
                            ClarityAliquotImportMapper.sampleLimsids(json))
                            .concatMap { String sampleLimsid ->
                                enqueueRow(ClarityAliquotImportMapper.sampleKey(sampleLimsid),
                                        KIND_SAMPLE)
                            }
                    return containerFirst.concatWith(samplesNext)
                            .concatWith(enqueueRow(artifactKey, KIND_ARTIFACT))
                }
                .switchIfEmpty(Flux.defer {
                    log.warn('Clarity artifact document not found, enqueueing anyway for visibility: {}',
                            artifactKey)
                    return enqueueRow(artifactKey, KIND_ARTIFACT)
                })
    }

    private Flux<Boolean> enqueueRow(String key, String kind) {
        // deferred so the enqueue call happens at subscription time,
        // preserving the plan's dependency order end to end
        return Flux.defer {
            queue.enqueue(ClarityAliquotImportMapper.SOURCE, key, kind).flux()
        }
    }
}
