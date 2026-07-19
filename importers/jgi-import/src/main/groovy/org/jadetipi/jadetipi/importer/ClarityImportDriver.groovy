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

import com.github.f4b6a3.uuid.UuidCreator
import groovy.util.logging.Slf4j
import org.jadetipi.dto.collections.Transaction
import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Collection
import org.jadetipi.dto.message.Message
import org.springframework.stereotype.Service
import org.springframework.util.Assert

import java.time.Duration
import java.time.Instant
import java.util.function.BiFunction

/**
 * The production drive loop (TASK-066), promoted from the live integration
 * test into main scope: drain the dependency-ordered {@code import_queue}
 * in batches, map each item by kind, and publish one JDTP transaction per
 * batch (open → mapped messages → commit) to the transaction topic. Since
 * TASK-069 it drives BOTH sources — clarity and esp rows dispatch to their
 * own mappers, and ids resolve within source-scoped namespaces.
 *
 * <p><b>Ids and resume.</b> Object ids are message-UUID form
 * ({@code <org>~<grp>~<uuid>~<collection>~<suffix>}, plain-String concat —
 * a GString would serialize as a JSON object). The resolver consults, in
 * order: this run's minted ids, the queue row's recorded {@code jdtp_id}
 * (the cross-batch/cross-run mechanism), then mints fresh and records it
 * on the row BEFORE publishing. Interruption is therefore safe end to end:
 * items not marked done stay pending; a batch whose commit never published
 * leaves an open transaction for the backend lease to roll back; the
 * re-run re-emits the same recorded ids in a fresh transaction, where root
 * creates land as clean creates or counted idempotent conflicts.
 *
 * <p><b>Failure isolation.</b> An item whose source document is missing
 * (or whose mapping throws) is marked failed with the error and the batch
 * continues — bulk resilience; the report and the CLI exit code surface
 * it.
 */
@Slf4j
@Service
class ClarityImportDriver {

    static final String CLIENT = 'jgi-import'

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30)

    /** Outputs created within this slack after a workflow's last sheet end still count (TASK-070). */
    private static final Duration OUTPUT_SLACK = Duration.ofHours(72)

    private final ImportQueueService queue
    private final CouchDbDocumentReader reader
    private final ClarityAliquotImportMapper mapper
    private final EspEntityImportMapper espMapper
    private final EspWorkflowConfigService workflowConfig
    private final EspEnrichedEntityClient enrichedClient

    ClarityImportDriver(ImportQueueService queue,
                        CouchDbDocumentReader reader,
                        ClarityAliquotImportMapper mapper,
                        EspEntityImportMapper espMapper,
                        EspWorkflowConfigService workflowConfig,
                        EspEnrichedEntityClient enrichedClient) {
        this.queue = queue
        this.reader = reader
        this.mapper = mapper
        this.espMapper = espMapper
        this.workflowConfig = workflowConfig
        this.enrichedClient = enrichedClient
    }

    /**
     * Drain the pending queue to the topic, one transaction per batch,
     * until nothing is pending. Synchronous by design — this is a CLI
     * drive, not a reactive pipeline.
     */
    ImportDriveReport drive(ImportMessagePublisher publisher,
                            String org, String grp, String user, int batchSize) {
        Assert.notNull(publisher, 'publisher must not be null')
        Assert.hasText(org, 'org must not be blank')
        Assert.hasText(grp, 'grp must not be blank')
        Assert.hasText(user, 'user must not be blank')
        Assert.isTrue(batchSize > 0, 'batchSize must be positive')

        Map<String, String> minted = new LinkedHashMap<>()
        // ids resolve within one SOURCE namespace (queue rows are
        // source-scoped, and each source has its own suffix rule)
        Closure<BiFunction<String, String, String>> idForSource = { String source ->
            return { String key, String collection ->
                String cacheKey = source + '~' + key
                String existing = minted[cacheKey]
                if (existing != null) {
                    return existing
                }
                String rowId = ImportQueueService.rowId(source, key)
                String recorded = queue.jdtpIdOf(rowId).block(BLOCK_TIMEOUT)
                String suffix = source == EspEntityImportMapper.SOURCE
                        ? EspEntityImportMapper.suffixFor(key)
                        : ClarityAliquotImportMapper.suffixFor(key)
                String id = recorded ?:
                        (org + '~' + grp + '~' + UuidCreator.timeOrderedEpoch.toString() +
                                '~' + collection + '~' + suffix)
                minted[cacheKey] = id
                if (recorded == null) {
                    // Persist the freshly minted id on its own queue row NOW, so a
                    // reference minted while processing another item (e.g. a prc's
                    // produced_by pointing at an output entity that has not been
                    // driven yet) survives a resume: the re-run re-resolves the
                    // identical id via jdtpIdOf instead of minting a new one and
                    // dangling the link. A no-op for synthetic keys with no row.
                    queue.recordJdtpId(rowId, id).block(BLOCK_TIMEOUT)
                }
                return id
            } as BiFunction<String, String, String>
        }

        long batches = 0
        long itemsDone = 0
        long itemsFailed = 0
        long messagesPublished = 0
        List<String> txnIds = []

        while (true) {
            List<ImportQueueItem> batch = queue.pendingInOrder(batchSize)
                    .collectList().block(BLOCK_TIMEOUT)
            if (batch == null || batch.isEmpty()) {
                break
            }

            Transaction txn = Transaction.newInstance(org, grp, CLIENT, user)
            List<Message> messages = []
            List<ImportQueueItem> mappedItems = []
            List<MappedImportMessage> mapped = []
            // TASK-072: a workflow instance (procedure) is carried by many
            // entities; accumulate its inputs/tasks/outputs across the batch's
            // carriers, keyed by workflow_instance_uuid, then emit ONE prc each.
            Map<String, Map<String, Object>> wiAccumulator = new LinkedHashMap<>()

            batch.each { ImportQueueItem item ->
                try {
                    // Ids are recorded on their rows at mint time (in the
                    // resolver) and, for pre-seeded overlays, in
                    // applyClarityPrecedence — so no per-item record here.
                    mapped.addAll(mapItem(item, minted, idForSource, wiAccumulator))
                    mappedItems.add(item)
                } catch (Exception ex) {
                    log.error('Import item failed, continuing: id={}, error={}', item.id, ex.message)
                    queue.markFailed(item.id, ex.message ?: ex.class.name).block(BLOCK_TIMEOUT)
                    itemsFailed++
                }
            }

            // TASK-072: emit one prc per accumulated workflow instance, with the
            // run's full input set (inputs map + output_input + links).
            if (!wiAccumulator.isEmpty()) {
                BiFunction<String, String, String> espIdFor = idForSource(EspEntityImportMapper.SOURCE)
                wiAccumulator.values().each { Map<String, Object> wi ->
                    mapped.addAll(espMapper.workflowInstanceProcedure(wi, espIdFor))
                }
            }

            // Wrap mapped messages into transaction messages. A transaction must
            // not carry two messages with the same id, so de-duplicate root
            // creates by their message uuid (the id's uuid segment) — a defensive
            // guard; TASK-072 aggregation already emits each prc once.
            Set<String> seenRootUuids = new HashSet<>()
            mapped.each { MappedImportMessage m ->
                Action action = 'update' == m.action ? Action.UPDATE : Action.CREATE
                // roots use the msg-UUID id form (the message uuid IS the id's
                // uuid segment); assignments and updates carry no new root id, so
                // their message uuid is minted fresh (TASK-068)
                String dataId = m.data.id as String
                boolean rootCreate = action == Action.CREATE && dataId != null &&
                        'assignment' != m.data.kind
                String uuid = rootCreate
                        ? dataId.split('~')[2]
                        : UuidCreator.timeOrderedEpoch.toString()
                if (rootCreate && !seenRootUuids.add(uuid)) {
                    return
                }
                messages.add(new Message(txn, uuid,
                        Collection.fromJson(m.collection), action, m.data))
            }

            if (!messages.isEmpty()) {
                publisher.publish(Message.newInstance(txn, Collection.TRANSACTION, Action.OPEN,
                        [description: 'jgi-import drive batch ' + (batches + 1)] as Map<String, Object>),
                        txn.id)
                messages.each { Message m -> publisher.publish(m, txn.id) }
                publisher.publish(Message.newInstance(txn, Collection.TRANSACTION, Action.COMMIT,
                        [summary: 'jgi-import: ' + mappedItems.size() + ' item(s), ' +
                                messages.size() + ' message(s)'] as Map<String, Object>),
                        txn.id)
                publisher.flush()
                batches++
                messagesPublished += messages.size() + 2
                txnIds.add(txn.id)
                log.info('Import batch committed: txnId={}, items={}, messages={}',
                        txn.id, mappedItems.size(), messages.size())
            }

            // Every successfully-mapped item is marked done — including
            // TASK-071 overlay containers that emit zero messages (their id
            // was reused from clarity). Guarding markDone on published
            // messages would leave an all-overlay batch pending forever. A
            // zero-message batch published no transaction, so its rows carry a
            // null txn_id rather than a phantom (never-committed) one.
            String doneTxnId = messages.isEmpty() ? null : txn.id
            mappedItems.each { ImportQueueItem item ->
                queue.markDone(item.id, doneTxnId).block(BLOCK_TIMEOUT)
            }
            itemsDone += mappedItems.size()
        }

        return new ImportDriveReport(
                batches: batches,
                itemsDone: itemsDone,
                itemsFailed: itemsFailed,
                messagesPublished: messagesPublished,
                txnIds: txnIds
        )
    }

    private List<MappedImportMessage> mapItem(ImportQueueItem item,
                                              Map<String, String> minted,
                                              Closure<BiFunction<String, String, String>> idForSource,
                                              Map<String, Map<String, Object>> wiAccumulator) {
        BiFunction<String, String, String> idFor = idForSource(item.source)
        switch (item.kind) {
            case ClarityAliquotImportPlanner.KIND_TYPE:
                return [mapper.mapBootstrapType(item.key, idFor)]
            case ClarityAliquotImportPlanner.KIND_CONTAINER:
                return mapper.mapContainer(fetchDoc(item.key), idFor)
            case ClarityAliquotImportPlanner.KIND_SAMPLE:
                return mapper.mapSample(fetchDoc(item.key), idFor)
            case ClarityAliquotImportPlanner.KIND_ARTIFACT:
                return mapper.mapArtifact(fetchDoc(item.key), idFor)
            case ClarityAliquotImportPlanner.KIND_PROCESS:
                return mapper.mapProcess(fetchDoc(item.key), idFor)
            case ClarityAliquotImportPlanner.KIND_FILE_PROPERTY:
                return mapper.mapFileProperty(item.key, idFor)
            case ClarityAliquotImportPlanner.KIND_FILE:
                return mapper.mapFile(fetchDoc(item.key), idFor)
            case EspEntityImportPlanner.KIND_TYPE:
                return [espMapper.mapBootstrapType(item.key, idFor)]
            case EspEntityImportPlanner.KIND_ENTITY:
                Map<String, Object> doc = withImportWell(fetchEspDoc(item.key))
                applyClarityPrecedence(doc, item, minted)
                accumulateWorkflowInstances(doc, wiAccumulator)
                return espMapper.mapEntity(doc, idFor)
            default:
                throw new IllegalStateException("Unknown queue kind: ${item.kind}")
        }
    }

    /**
     * ESP-over-clarity precedence for shared containers (TASK-071). When an
     * esp Container's name is a clean clarity container limsid AND that
     * clarity container was already imported — its {@code import_queue} row
     * (\"clarity~containers_&lt;name&gt;\") carries a recorded {@code jdtp_id} —
     * the esp entity REUSES the clarity root id instead of minting a
     * duplicate: it pre-seeds the resolver so the esp uuid resolves to the
     * clarity id (recorded on the esp row for downstream references), and
     * flags the doc so the mapper emits the entity's links but not a
     * duplicate root-create. Existence-gated: absence of the clarity row
     * (clarity not imported, or an esp-native container) means the standard
     * esp mint path — shape alone is never sufficient. Container-only per
     * the overlap investigation; sample-level entities carry no clarity key.
     */
    private void applyClarityPrecedence(Map<String, Object> doc, ImportQueueItem item,
                                        Map<String, String> minted) {
        if (!EspEntityImportMapper.isClarityContainerCandidate(doc)) {
            return
        }
        String name = doc.get('name') as String
        String clarityRowId = ImportQueueService.rowId(ClarityAliquotImportMapper.SOURCE,
                EspEntityImportMapper.clarityContainerKey(name))
        String clarityId = queue.jdtpIdOf(clarityRowId).block(BLOCK_TIMEOUT)
        if (clarityId == null) {
            // Distinguish an expected esp-native plate (no clarity row at all —
            // e.g. the 27-810xxx block) from an ORDERING VIOLATION (the clarity
            // container is planned but not yet driven, so esp-before-clarity
            // would mint a duplicate root for the same physical plate). Only the
            // latter warrants a warning — clarity-first is the ratified order.
            if (Boolean.TRUE == queue.rowExists(clarityRowId).block(BLOCK_TIMEOUT)) {
                log.warn('esp Container {} matches clarity {} but that clarity container is queued and ' +
                        'not yet driven — importing esp before clarity splits plate identity; ' +
                        'import clarity containers first', name, clarityRowId)
            }
            return
        }
        String uuid = doc.get('uuid') as String ?: doc.get('_id') as String
        minted[item.source + '~' + uuid] = clarityId
        // the id is pre-seeded (not minted through the resolver), so record it
        // on the esp row here for downstream/resume resolution
        queue.recordJdtpId(item.id, clarityId).block(BLOCK_TIMEOUT)
        doc.put(EspEntityImportMapper.PRECEDENCE_OVERLAY, Boolean.TRUE)
        log.info('esp Container reuses clarity root (precedence): esp name={} -> clarity id={}',
                name, clarityId)
    }

    private Map<String, Object> fetchDoc(String key) {
        Map<String, Object> doc = reader.findDocument(ClarityAliquotImportPlanner.DATABASE, key)
                .block(BLOCK_TIMEOUT)
        if (doc == null) {
            throw new IllegalStateException('clarity document not found: ' + key)
        }
        return doc
    }

    private Map<String, Object> fetchEspDoc(String key) {
        Map<String, Object> doc = reader.findDocument(EspEntityImportMapper.DATABASE, key)
                .block(BLOCK_TIMEOUT)
        if (doc == null) {
            throw new IllegalStateException('esp document not found: ' + key)
        }
        return doc
    }

    /**
     * Accumulate this esp entity's contribution to the workflow instances it
     * participates in as an INPUT-side carrier (TASK-072), keyed by
     * {@code workflow_instance_uuid} so the batch's carriers aggregate into one
     * prc. The workflow-instance id is not in the bulk replica's sample sheets,
     * so it is sourced from the enriched service ({@link EspEnrichedEntityClient},
     * the V2 cache-first endpoint), falling back to none when the client is
     * disabled or the entity is unknown.
     *
     * <p>Per lab workflow instance the entity owns (its {@code type_name} is one
     * of the workflow's configured input types): the input is the SOW Item's
     * first non-task/project (biological) begat parent for a task-carried
     * workflow — recorded with {@code task_id} = the SOW Item — or the carrier
     * itself otherwise; the outputs are the carrier's begat children
     * (tasks/projects excluded) created within the instance window plus
     * {@link #OUTPUT_SLACK}. Child creation dates live only on the child
     * documents and are fetched once here.
     */
    private void accumulateWorkflowInstances(Map<String, Object> doc,
                                             Map<String, Map<String, Object>> wiAccumulator) {
        String carrierType = doc.get('type_name') as String
        String uuid = doc.get('uuid') as String ?: doc.get('_id') as String

        // workflow-instance-bearing sheets: prefer the enriched service (carries
        // workflow_instance_uuid), fall back to local sheets (which lack it).
        List<Map<String, Object>> sheets = enrichedClient.enrichedSampleSheets(uuid)
        if (sheets.isEmpty()) {
            sheets = ClarityAliquotImportMapper.asImportList(doc.get('sample_sheets'))
                    .findAll { it instanceof Map } as List<Map<String, Object>>
        }
        // this carrier's owned lab workflow instances: wiUuid -> {name, start, end}
        Map<String, Map<String, Object>> owned = new LinkedHashMap<>()
        sheets.each { Map sheet ->
            String wfName = sheet.get('workflow_name') as String
            String wiUuid = sheet.get('workflow_instance_uuid') as String
            if (wfName == null || wiUuid == null || !workflowConfig.isLabProcedure(wfName)) {
                return
            }
            if (!workflowConfig.inputTypes(wfName).contains(carrierType)) {
                return   // not the input-side carrier for this workflow
            }
            Map<String, Object> w = owned.computeIfAbsent(wiUuid, {
                [workflow_name: wfName, starts: [] as List, ends: [] as List] as Map<String, Object>
            })
            String s = (sheet.get('workflow_instance_start_time') ?: sheet.get('sample_sheet_start_time')) as String
            String e = (sheet.get('workflow_instance_end_time') ?: sheet.get('sample_sheet_end_time')) as String
            if (s) { ((List) w.get('starts')).add(s) }
            if (e) { ((List) w.get('ends')).add(e) }
        }
        if (owned.isEmpty()) {
            return
        }

        boolean taskCarried = EspEntityImportMapper.CLASS_SOW_ITEM == doc.get('class_name')
        String inputUuid = taskCarried
                ? ClarityAliquotImportMapper.asImportList(doc.get('parents')).findResult { Object edge ->
                    Map parent = edge instanceof Map ? (Map) edge : [:]
                    (parent.get('uuid') && !EspEntityImportMapper.NON_INPUT_PARENT_TYPES
                            .contains(parent.get('type_name'))) ? parent.get('uuid') as String : null
                }
                : uuid
        if (inputUuid == null) {
            return
        }

        // candidate outputs: begat children (tasks/projects excluded), created dates fetched once
        Map<String, String> childCreated = new LinkedHashMap<>()
        ClarityAliquotImportMapper.asImportList(doc.get('children')).each { Object edge ->
            Map child = edge instanceof Map ? (Map) edge : [:]
            String childUuid = child.get('uuid') as String
            if (childUuid && !EspEntityImportMapper.NON_INPUT_PARENT_TYPES.contains(child.get('type_name'))) {
                Map<String, Object> childDoc = reader.findDocument(EspEntityImportMapper.DATABASE, childUuid)
                        .block(BLOCK_TIMEOUT)
                if (childDoc != null) {
                    childCreated.put(childUuid, childDoc.get('created') as String)
                }
            }
        }

        owned.each { String wiUuid, Map<String, Object> w ->
            Map<String, Object> acc = wiAccumulator.computeIfAbsent(wiUuid, {
                [workflow_instance_uuid: wiUuid, workflow_name: w.get('workflow_name'),
                 inputs: new LinkedHashMap<String, String>(), tasks: new LinkedHashSet<String>(),
                 outputs: new LinkedHashSet<String>(), starts: new TreeSet<String>(),
                 ends: new TreeSet<String>()] as Map<String, Object>
            })
            ((Map) acc.get('inputs')).put(inputUuid, taskCarried ? uuid : null)
            if (taskCarried) { ((Set) acc.get('tasks')).add(uuid) }
            ((Set) acc.get('starts')).addAll((List) w.get('starts'))
            ((Set) acc.get('ends')).addAll((List) w.get('ends'))

            List<String> starts = ((Set) acc.get('starts')) as List
            List<String> ends = ((Set) acc.get('ends')) as List
            acc.put('started', starts ? starts.first() : null)
            acc.put('ended', ends ? ends.last() : null)
            Instant start = starts ? parseInstant(starts.first()) : null
            Instant end = ends ? parseInstant(ends.last()) : null
            if (start != null && end != null) {
                Instant windowEnd = end.plus(OUTPUT_SLACK)
                childCreated.each { String childUuid, String created ->
                    Instant c = parseInstant(created)
                    if (c != null && !c.isBefore(start) && !c.isAfter(windowEnd)) {
                        ((Set) acc.get('outputs')).add(childUuid)
                    }
                }
            }
        }
    }

    private static Instant parseInstant(String iso) {
        if (!iso) {
            return null
        }
        try {
            return Instant.parse(iso)
        } catch (Exception ignored) {
            return null
        }
    }

    /**
     * The well position of a contained esp entity lives only in its
     * container's contents map; look it up at drive time and inject it
     * for the mapper (TASK-069).
     */
    private Map<String, Object> withImportWell(Map<String, Object> doc) {
        Map container = doc.get('container') instanceof Map ? (Map) doc.get('container') : null
        String containerUuid = container?.get('uuid') as String
        String uuid = doc.get('uuid') as String ?: doc.get('_id') as String
        if (!containerUuid || !uuid) {
            return doc
        }
        Map<String, Object> containerDoc = reader
                .findDocument(EspEntityImportMapper.DATABASE, containerUuid)
                .block(BLOCK_TIMEOUT)
        Object contents = containerDoc?.get('contents')
        if (contents instanceof Map) {
            Map.Entry wellEntry = ((Map) contents).find { Object k, Object v ->
                v instanceof Map && uuid == ((Map) v).get('uuid')
            } as Map.Entry
            if (wellEntry != null) {
                doc.put(EspEntityImportMapper.IMPORT_WELL, wellEntry.key as String)
            }
        }
        return doc
    }
}
