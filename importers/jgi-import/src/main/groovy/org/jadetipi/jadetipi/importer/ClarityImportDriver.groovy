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
import java.util.function.BiFunction

/**
 * The production drive loop (TASK-066), promoted from the live integration
 * test into main scope: drain the dependency-ordered {@code import_queue}
 * in batches, map each item by kind, and publish one JDTP transaction per
 * batch (open → mapped messages → commit) to the transaction topic.
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

    private final ImportQueueService queue
    private final CouchDbDocumentReader reader
    private final ClarityAliquotImportMapper mapper

    ClarityImportDriver(ImportQueueService queue,
                        CouchDbDocumentReader reader,
                        ClarityAliquotImportMapper mapper) {
        this.queue = queue
        this.reader = reader
        this.mapper = mapper
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
        BiFunction<String, String, String> idFor = { String key, String collection ->
            String existing = minted[key]
            if (existing != null) {
                return existing
            }
            String recorded = queue.jdtpIdOf(
                    ImportQueueService.rowId(ClarityAliquotImportMapper.SOURCE, key))
                    .block(BLOCK_TIMEOUT)
            String id = recorded ?:
                    (org + '~' + grp + '~' + UuidCreator.timeOrderedEpoch.toString() +
                            '~' + collection + '~' + ClarityAliquotImportMapper.suffixFor(key))
            minted[key] = id
            return id
        } as BiFunction<String, String, String>

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

            batch.each { ImportQueueItem item ->
                try {
                    List<MappedImportMessage> mapped = mapItem(item, idFor)
                    String mintedId = minted[item.key]
                    if (mintedId != null) {
                        queue.recordJdtpId(item.id, mintedId).block(BLOCK_TIMEOUT)
                    }
                    mapped.each { MappedImportMessage m ->
                        Action action = 'update' == m.action ? Action.UPDATE : Action.CREATE
                        // roots use the msg-UUID id form (the message uuid IS the
                        // id's uuid segment); assignments and updates carry no new
                        // root id, so their message uuid is minted fresh (TASK-068)
                        String dataId = m.data.id as String
                        boolean rootCreate = action == Action.CREATE && dataId != null &&
                                'assignment' != m.data.kind
                        String uuid = rootCreate
                                ? dataId.split('~')[2]
                                : UuidCreator.timeOrderedEpoch.toString()
                        messages.add(new Message(txn, uuid,
                                Collection.fromJson(m.collection), action, m.data))
                    }
                    mappedItems.add(item)
                } catch (Exception ex) {
                    log.error('Import item failed, continuing: id={}, error={}', item.id, ex.message)
                    queue.markFailed(item.id, ex.message ?: ex.class.name).block(BLOCK_TIMEOUT)
                    itemsFailed++
                }
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

                mappedItems.each { ImportQueueItem item ->
                    queue.markDone(item.id, txn.id).block(BLOCK_TIMEOUT)
                }
                batches++
                itemsDone += mappedItems.size()
                messagesPublished += messages.size() + 2
                txnIds.add(txn.id)
                log.info('Import batch committed: txnId={}, items={}, messages={}',
                        txn.id, mappedItems.size(), messages.size())
            }
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
                                              BiFunction<String, String, String> idFor) {
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
            default:
                throw new IllegalStateException("Unknown queue kind: ${item.kind}")
        }
    }

    private Map<String, Object> fetchDoc(String key) {
        Map<String, Object> doc = reader.findDocument(ClarityAliquotImportPlanner.DATABASE, key)
                .block(BLOCK_TIMEOUT)
        if (doc == null) {
            throw new IllegalStateException('clarity document not found: ' + key)
        }
        return doc
    }
}
