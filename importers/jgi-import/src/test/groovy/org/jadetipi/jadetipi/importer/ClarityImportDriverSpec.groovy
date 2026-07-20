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
    EspEntityImportMapper espMapper
    ImportMessagePublisher publisher
    ClarityImportDriver driver
    List<Message> published

    def setup() {
        queue = Mock(ImportQueueService)
        reader = Mock(CouchDbDocumentReader)
        mapper = Mock(ClarityAliquotImportMapper)
        espMapper = Mock(EspEntityImportMapper)
        publisher = Mock(ImportMessagePublisher)
        driver = new ClarityImportDriver(queue, reader, mapper, espMapper, new EspWorkflowConfigService(),
                new EspEnrichedEntityClient(
                        org.springframework.web.reactive.function.client.WebClient.builder(), '', '', 64))
        published = []
        publisher.publish(_ as Message, _ as String) >> { Message m, String key ->
            published.add(m)
        }
        queue.recordJdtpId(_, _) >> Mono.empty()
        queue.markDone(_, _) >> Mono.empty()
        queue.markFailed(_, _) >> Mono.empty()
        queue.rowExists(_) >> Mono.just(false)
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

        and: 'the mapped ids are message-UUID form (uuid leads) under the drive org/grp'
        String typId = published[1].data().id as String
        typId.split('~').length == 5
        typId.split('~')[1] == ORG
        typId.split('~')[2] == GRP
        published[1].uuid() == typId.split('~')[0]

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
        String recordedTypeId = "018fd849-9b01-7111-8a01-a1a1a1a1a1a1~${ORG}~${GRP}~typ~clarity_analyte"
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

    def 'esp rows dispatch to the esp mapper with source-scoped ids and injected wells (TASK-069)'() {
        given: 'one esp entity row whose doc sits in a container'
        String entityUuid = '019a3ea6-c704-7d47-b951-65268a951c9e'
        String containerUuid = '019a3ea7-8f4b-771c-9f58-8c833082e9b6'
        ImportQueueItem espItem = new ImportQueueItem(
                id: ImportQueueService.rowId('esp', entityUuid),
                source: 'esp', key: entityUuid, kind: 'esp_entity', state: 'pending', seq: 3)
        queue.pendingInOrder(200) >>> [Flux.just(espItem), Flux.empty()]
        queue.jdtpIdOf(_) >> Mono.empty()
        reader.findDocument('esp-entity', entityUuid) >> Mono.just([
                _id: entityUuid, uuid: entityUuid, class_name: 'Sample', type_name: 'Nucleic Acid',
                container: [uuid: containerUuid]] as Map)
        reader.findDocument('esp-entity', containerUuid) >> Mono.just([
                _id: containerUuid, uuid: containerUuid, class_name: 'Container',
                contents: [A2: [uuid: entityUuid]]] as Map)
        Map capturedDoc = null
        espMapper.mapEntity(_, _) >> { Map doc, BiFunction idFor ->
            capturedDoc = doc
            [new MappedImportMessage(collection: 'ent', action: 'create',
                    data: [id: idFor.apply(entityUuid, 'ent')] as Map<String, Object>)]
        }

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'the well is injected from the container contents and the id carries the esp suffix'
        report.itemsDone == 1
        capturedDoc.get(EspEntityImportMapper.IMPORT_WELL) == 'A2'
        String entId = published[1].data().id as String
        entId.split('~')[4].startsWith('esp_')

        and: 'the recorded id rode the esp-scoped queue row'
        1 * queue.recordJdtpId(ImportQueueService.rowId('esp', entityUuid), { it != null }) >> Mono.empty()
    }

    def 'an esp container matching an imported clarity container reuses the clarity id, no duplicate create (TASK-071)'() {
        given: 'clarity container 27-279088 already imported — its queue row carries a jdtp_id'
        String espUuid = '019a3ea7-8f4b-771c-9f58-8c833082e9b6'
        String clarityId = '018fd849-c0c0-7000-8000-000000000009~jade-test-org~import~loc~clarity_containers_27-279088'
        ImportQueueItem espItem = new ImportQueueItem(
                id: ImportQueueService.rowId('esp', espUuid),
                source: 'esp', key: espUuid, kind: 'esp_entity', state: 'pending', seq: 5)
        queue.pendingInOrder(200) >>> [Flux.just(espItem), Flux.empty()]
        reader.findDocument('esp-entity', espUuid) >> Mono.just([
                _id: espUuid, uuid: espUuid, class_name: 'Container', type_name: '96W Plate',
                name: '27-279088', barcode: '27-279088'] as Map)
        queue.jdtpIdOf(ImportQueueService.rowId('clarity', 'containers_27-279088')) >> Mono.just(clarityId)
        queue.jdtpIdOf(_) >> Mono.empty()
        Map capturedDoc = null
        espMapper.mapEntity(_, _) >> { Map doc, BiFunction idFor ->
            capturedDoc = doc
            return []   // an overlay container emits no messages — its id was reused
        }

        when:
        ImportDriveReport report = driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'the mapper saw the overlay flag and the esp row recorded the CLARITY id'
        capturedDoc[EspEntityImportMapper.PRECEDENCE_OVERLAY] == Boolean.TRUE
        1 * queue.recordJdtpId(ImportQueueService.rowId('esp', espUuid), clarityId) >> Mono.empty()

        and: 'nothing was published (no duplicate root create), yet the item is done and counted'
        published.isEmpty()
        report.itemsDone == 1
        report.batches == 0

        and: 'the zero-message overlay batch marks done with a NULL txn id, not a phantom one'
        1 * queue.markDone(ImportQueueService.rowId('esp', espUuid), null) >> Mono.empty()
    }

    def 'a candidate whose clarity container is queued-but-not-driven warns and mints esp-native (TASK-071 ordering guard)'() {
        given: 'the clarity container row exists but has no jdtp_id yet (ordering violation)'
        String espUuid = '019a3ea7-8f4b-771c-9f58-8c833082e9b6'
        ImportQueueItem espItem = new ImportQueueItem(
                id: ImportQueueService.rowId('esp', espUuid),
                source: 'esp', key: espUuid, kind: 'esp_entity', state: 'pending', seq: 5)
        queue.pendingInOrder(200) >>> [Flux.just(espItem), Flux.empty()]
        reader.findDocument('esp-entity', espUuid) >> Mono.just([
                _id: espUuid, uuid: espUuid, class_name: 'Container', type_name: '96W Plate',
                name: '27-279088', barcode: '27-279088'] as Map)
        queue.jdtpIdOf(_) >> Mono.empty()
        Map capturedDoc = null
        espMapper.mapEntity(_, _) >> { Map doc, BiFunction idFor ->
            capturedDoc = doc
            [new MappedImportMessage(collection: 'loc', action: 'create',
                    data: [id: idFor.apply(espUuid, 'loc')] as Map<String, Object>)]
        }

        when:
        driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'the ordering violation is detected (clarity row existence checked) and the plate mints esp-native'
        1 * queue.rowExists(ImportQueueService.rowId('clarity', 'containers_27-279088')) >> Mono.just(true)
        capturedDoc[EspEntityImportMapper.PRECEDENCE_OVERLAY] == null
        (published.find { it.collection().abbreviation == 'loc' }.data().id as String).split('~')[4].startsWith('esp_')
    }

    def 'an esp container with no matching clarity row takes the normal esp mint path (TASK-071)'() {
        given: 'no clarity container row exists for the name (esp-native container)'
        String espUuid = '019f14a8-e5d8-7594-88d4-a19bc8998f5f'
        ImportQueueItem espItem = new ImportQueueItem(
                id: ImportQueueService.rowId('esp', espUuid),
                source: 'esp', key: espUuid, kind: 'esp_entity', state: 'pending', seq: 5)
        queue.pendingInOrder(200) >>> [Flux.just(espItem), Flux.empty()]
        reader.findDocument('esp-entity', espUuid) >> Mono.just([
                _id: espUuid, uuid: espUuid, class_name: 'Container', type_name: '96W Plate',
                name: '27-810708', barcode: '27-810708'] as Map)
        queue.jdtpIdOf(_) >> Mono.empty()   // clarity row absent -> mint new
        Map capturedDoc = null
        espMapper.mapEntity(_, _) >> { Map doc, BiFunction idFor ->
            capturedDoc = doc
            [new MappedImportMessage(collection: 'loc', action: 'create',
                    data: [id: idFor.apply(espUuid, 'loc')] as Map<String, Object>)]
        }

        when:
        driver.drive(publisher, ORG, GRP, USER, 200)

        then: 'no overlay flag; the container mints a fresh esp-keyed loc root'
        capturedDoc[EspEntityImportMapper.PRECEDENCE_OVERLAY] == null
        published.any { it.collection().abbreviation == 'loc' }
        (published.find { it.collection().abbreviation == 'loc' }.data().id as String).split('~')[4].startsWith('esp_')
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
