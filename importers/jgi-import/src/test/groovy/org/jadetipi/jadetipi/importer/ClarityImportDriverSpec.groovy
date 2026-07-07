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

import org.jadetipi.dto.message.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.function.BiFunction

/**
 * TASK-066 coverage for the production drive loop: one transaction per
 * batch (open → mapped messages in queue order → commit), recorded ids
 * before publishing and done-marks after, the resolver preferring recorded
 * ids over fresh mints (resume/cross-batch), failure isolation for a
 * missing source document, and nothing published for an empty queue.
 */
class ClarityImportDriverSpec extends Specification {

    static final String ORG = 'jade-test-org'
    static final String GRP = 'import'
    static final String USER = 'itest-user'
    static final String TYPE_KEY = ClarityAliquotImportMapper.KEY_TYPE_ANALYTE
    static final String ARTIFACT_KEY = ClarityAliquotImportMapper.artifactKey('2-79367')

    ImportQueueService queue
    CouchDbDocumentReader reader
    ClarityAliquotImportMapper mapper
    ImportMessagePublisher publisher
    ClarityImportDriver driver
    List<Message> published

    def setup() {
        queue = Mock(ImportQueueService)
        reader = Mock(CouchDbDocumentReader)
        mapper = Mock(ClarityAliquotImportMapper)
        publisher = Mock(ImportMessagePublisher)
        driver = new ClarityImportDriver(queue, reader, mapper)
        published = []
        publisher.publish(_ as Message, _ as String) >> { Message m, String key ->
            published.add(m)
        }
        queue.recordJdtpId(_, _) >> Mono.empty()
        queue.markDone(_, _) >> Mono.empty()
        queue.markFailed(_, _) >> Mono.empty()
    }

    private static ImportQueueItem item(String key, String kind, long seq) {
        return new ImportQueueItem(
                id: ImportQueueService.rowId('clarity', key),
                source: 'clarity', key: key, kind: kind, state: 'pending', seq: seq)
    }

    /** Mapper stubs mint through the driver's resolver, like the real mapper. */
    private static MappedImportMessage mapped(String collection, String key,
                                              BiFunction<String, String, String> idFor,
                                              Map extra = [:]) {
        Map data = [id: idFor.apply(key, collection)] as Map<String, Object>
        data.putAll(extra)
        return new MappedImportMessage(collection: collection, action: 'create', data: data)
    }

    def 'a batch publishes one transaction — open, mapped messages in queue order, commit — and marks rows'() {
        given: 'two pending items, then an empty queue'
        queue.pendingInOrder(200) >>> [
                Flux.just(item(TYPE_KEY, 'type', 1), item(ARTIFACT_KEY, 'artifact', 2)),
                Flux.empty()]
        queue.jdtpIdOf(_) >> Mono.empty()
        mapper.mapBootstrapType(TYPE_KEY, _) >> { String key, BiFunction idFor ->
            mapped('typ', key, idFor)
        }
        reader.findDocument('clarity', ARTIFACT_KEY) >> Mono.just([json: [:]] as Map)
        mapper.mapArtifact(_, _) >> { Map doc, BiFunction idFor ->
            [mapped('ent', ARTIFACT_KEY, idFor)]
        }

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'one transaction: open, the two mapped messages in order, commit'
        published.size() == 4
        published[0].collection().abbreviation == 'txn'
        published[0].action().toString() == 'open'
        published[1].collection().abbreviation == 'typ'
        published[2].collection().abbreviation == 'ent'
        published[3].collection().abbreviation == 'txn'
        published[3].action().toString() == 'commit'

        and: 'all four ride the same transaction with org/grp/user identity'
        published*.txn().unique().size() == 1
        published[0].txn().group().org() == ORG
        published[0].txn().user() == USER

        and: 'the mapped ids are message-UUID form under the drive org/grp'
        String typId = published[1].data().id as String
        typId.startsWith("${ORG}~${GRP}~")
        typId.split('~').length == 5
        published[1].uuid() == typId.split('~')[2]

        and: 'ids are recorded before the drive completes and rows marked done with the txn id'
        1 * queue.recordJdtpId(ImportQueueService.rowId('clarity', TYPE_KEY), { it != null }) >> Mono.empty()
        1 * queue.recordJdtpId(ImportQueueService.rowId('clarity', ARTIFACT_KEY), { it != null }) >> Mono.empty()
        1 * queue.markDone(ImportQueueService.rowId('clarity', TYPE_KEY),
                { String txnId -> txnId == published[0].txn().id }) >> Mono.empty()
        1 * queue.markDone(ImportQueueService.rowId('clarity', ARTIFACT_KEY),
                { String txnId -> txnId == published[0].txn().id }) >> Mono.empty()

        and: 'the report accounts for everything'
        report.batches == 1
        report.itemsDone == 2
        report.itemsFailed == 0
        report.messagesPublished == 4
        report.txnIds == [published[0].txn().id]
    }

    def 'the resolver prefers a recorded id over a fresh mint (resume and cross-batch references)'() {
        given: 'the artifact references the analyte type, imported by an earlier run'
        String recordedTypeId = "${ORG}~${GRP}~018fd849-9b01-7111-8a01-a1a1a1a1a1a1~typ~clarity_analyte"
        queue.pendingInOrder(200) >>> [Flux.just(item(ARTIFACT_KEY, 'artifact', 7)), Flux.empty()]
        queue.jdtpIdOf(ImportQueueService.rowId('clarity', TYPE_KEY)) >> Mono.just(recordedTypeId)
        queue.jdtpIdOf(_) >> Mono.empty()
        reader.findDocument('clarity', ARTIFACT_KEY) >> Mono.just([json: [:]] as Map)
        mapper.mapArtifact(_, _) >> { Map doc, BiFunction idFor ->
            [mapped('ent', ARTIFACT_KEY, idFor,
                    [type_id: idFor.apply(TYPE_KEY, 'typ')])]
        }

        when:
        driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'the reference resolves to the recorded id, not a new mint'
        published[1].data().type_id == recordedTypeId
    }

    def 'a missing source document fails that item and the batch continues'() {
        given:
        queue.pendingInOrder(200) >>> [
                Flux.just(item(ARTIFACT_KEY, 'artifact', 1), item(TYPE_KEY, 'type', 2)),
                Flux.empty()]
        queue.jdtpIdOf(_) >> Mono.empty()
        reader.findDocument('clarity', ARTIFACT_KEY) >> Mono.empty()
        mapper.mapBootstrapType(TYPE_KEY, _) >> { String key, BiFunction idFor ->
            mapped('typ', key, idFor)
        }

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'the missing item is marked failed with the reason'
        1 * queue.markFailed(ImportQueueService.rowId('clarity', ARTIFACT_KEY),
                { String error -> error.contains(ARTIFACT_KEY) }) >> Mono.empty()

        and: 'the healthy item still ships in a committed transaction'
        published.size() == 3
        report.itemsDone == 1
        report.itemsFailed == 1
        report.batches == 1
    }

    def 'an empty queue publishes nothing'() {
        given:
        queue.pendingInOrder(200) >> Flux.empty()

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 200)

        then:
        published.isEmpty()
        0 * publisher.publish(_, _)
        report.batches == 0
        report.itemsDone == 0
        report.txnIds.isEmpty()
    }

    def 'a small batch size splits the queue into one transaction per batch'() {
        given:
        queue.pendingInOrder(1) >>> [
                Flux.just(item(TYPE_KEY, 'type', 1)),
                Flux.just(item(ARTIFACT_KEY, 'artifact', 2)),
                Flux.empty()]
        queue.jdtpIdOf(_) >> Mono.empty()
        mapper.mapBootstrapType(TYPE_KEY, _) >> { String key, BiFunction idFor ->
            mapped('typ', key, idFor)
        }
        reader.findDocument('clarity', ARTIFACT_KEY) >> Mono.just([json: [:]] as Map)
        mapper.mapArtifact(_, _) >> { Map doc, BiFunction idFor ->
            [mapped('ent', ARTIFACT_KEY, idFor)]
        }

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 1)

        then: 'two transactions, three messages each (open + one mapped + commit)'
        report.batches == 2
        report.txnIds.size() == 2
        report.txnIds.toSet().size() == 2
        published.size() == 6
        published.count { it.collection().abbreviation == 'txn' && it.action().toString() == 'open' } == 2
        published.count { it.collection().abbreviation == 'txn' && it.action().toString() == 'commit' } == 2
    }
}
