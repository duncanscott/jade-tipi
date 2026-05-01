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

import com.mongodb.MongoWriteException
import com.mongodb.ServerAddress
import com.mongodb.WriteError
import org.bson.BsonDocument
import org.jadetipi.dto.collections.Group
import org.jadetipi.dto.collections.Transaction
import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Collection
import org.jadetipi.dto.message.Message
import org.jadetipi.id.IdGenerator
import org.jadetipi.jadetipi.exception.ConflictingDuplicateException
import org.jadetipi.jadetipi.kafka.KafkaSourceMetadata
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Mono
import spock.lang.Specification

class TransactionMessagePersistenceServiceSpec extends Specification {

    ReactiveMongoTemplate mongoTemplate
    IdGenerator idGenerator
    CommittedTransactionMaterializer materializer
    TransactionMessagePersistenceService service

    static final Transaction TXN = new Transaction(
            'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
            new Group('test-org', 'test-grp'),
            'jade-cli',
            '0000-0000-0000-0001'
    )
    static final String TXN_ID = TXN.getId()
    static final String COLLECTION = 'txn'

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        idGenerator = Mock(IdGenerator)
        materializer = Mock(CommittedTransactionMaterializer)
        service = new TransactionMessagePersistenceService(mongoTemplate, idGenerator, materializer)
    }

    private static Message openMessage(Map data = [hint: 'open']) {
        return new Message(TXN, '11111111-1111-7111-8111-111111111111',
                Collection.TRANSACTION, Action.OPEN, data)
    }

    private static Message commitMessage(Map data = [:]) {
        return new Message(TXN, '99999999-9999-7999-8999-999999999999',
                Collection.TRANSACTION, Action.COMMIT, data)
    }

    private static Message rollbackMessage() {
        return new Message(TXN, '88888888-8888-7888-8888-888888888888',
                Collection.TRANSACTION, Action.ROLLBACK, [:])
    }

    private static Message dataMessage(String uuid = '22222222-2222-7222-8222-222222222222',
                                       Map data = [name: 'value']) {
        return new Message(TXN, uuid, Collection.PROPERTY, Action.CREATE, data)
    }

    private static KafkaSourceMetadata source() {
        return new KafkaSourceMetadata('jdtp-txn-prd', 0, 42L, 1700000000000L)
    }

    private static DuplicateKeyException springDuplicate() {
        def writeError = new WriteError(11000, 'duplicate key', new BsonDocument())
        def cause = new MongoWriteException(writeError, new ServerAddress('localhost'))
        return new DuplicateKeyException('duplicate key', cause)
    }

    def 'open a new transaction header upserts and returns OPENED'() {
        given:
        def message = openMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.empty()
        Query capturedQuery = null
        Update capturedUpdate = null
        mongoTemplate.upsert(_ as Query, _ as Update, COLLECTION) >> { Query q, Update u, String _c ->
            capturedQuery = q
            capturedUpdate = u
            return Mono.empty()
        }

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.OPENED
        capturedQuery.getQueryObject().get('_id') == TXN_ID
        def setOnInsert = capturedUpdate.getUpdateObject().get('$setOnInsert')
        setOnInsert.get('record_type') == 'transaction'
        setOnInsert.get('state') == 'open'
        setOnInsert.get('txn_id') == TXN_ID
        setOnInsert.get('open_data') == [hint: 'open']
        setOnInsert.containsKey('opened_at')

        and: 'commit_id generator must not be touched on open'
        0 * idGenerator.nextId()
    }

    def 'open re-delivered for already-open transaction returns OPEN_CONFIRMED_DUPLICATE'() {
        given:
        def message = openMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                txn_id: TXN_ID,
                record_type: 'transaction',
                state: 'open'
        ])

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.OPEN_CONFIRMED_DUPLICATE
        0 * mongoTemplate.upsert(_, _, _)
    }

    def 'open re-delivered after commit returns OPEN_CONFIRMED_DUPLICATE'() {
        given:
        def message = openMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'committed',
                commit_id: 'cid-1'
        ])

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.OPEN_CONFIRMED_DUPLICATE
        0 * mongoTemplate.upsert(_, _, _)
    }

    def 'first-time data message inserts and returns APPENDED'() {
        given:
        def message = dataMessage()
        Map captured = null
        mongoTemplate.insert(_ as Map, COLLECTION) >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.APPENDED
        captured._id == TXN_ID + '~' + message.uuid()
        captured.record_type == 'message'
        captured.txn_id == TXN_ID
        captured.msg_uuid == message.uuid()
        captured.collection == 'ppy'
        captured.action == 'create'
        captured.data == [name: 'value']
        captured.kafka.topic == 'jdtp-txn-prd'
        captured.kafka.partition == 0
        captured.kafka.offset == 42L
        captured.kafka.timestamp_ms == 1700000000000L
        0 * mongoTemplate.findById(_, _, _)
    }

    def 'duplicate data message with equal payload returns APPEND_DUPLICATE'() {
        given:
        def message = dataMessage()
        def recordId = TXN_ID + '~' + message.uuid()
        mongoTemplate.insert(_ as Map, COLLECTION) >> Mono.error(springDuplicate())
        mongoTemplate.findById(recordId, Map.class, COLLECTION) >> Mono.just([
                _id: recordId,
                record_type: 'message',
                collection: 'ppy',
                action: 'create',
                data: [name: 'value']
        ])

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.APPEND_DUPLICATE
    }

    def 'duplicate data message with conflicting payload errors with ConflictingDuplicateException'() {
        given:
        def message = dataMessage('33333333-3333-7333-8333-333333333333',
                [name: 'incoming-value'])
        def recordId = TXN_ID + '~' + message.uuid()
        mongoTemplate.insert(_ as Map, COLLECTION) >> Mono.error(springDuplicate())
        mongoTemplate.findById(recordId, Map.class, COLLECTION) >> Mono.just([
                _id: recordId,
                record_type: 'message',
                collection: 'ppy',
                action: 'create',
                data: [name: 'stored-value']
        ])

        when:
        service.persist(message, source()).block()

        then:
        ConflictingDuplicateException ex = thrown()
        ex.recordId == recordId
    }

    def 'commit assigns commit_id from IdGenerator and returns COMMITTED'() {
        given:
        def message = commitMessage(reason: 'done')
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'open'
        ])
        idGenerator.nextId() >> 'COMMIT-001'
        Update capturedUpdate = null
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> { Query q, Update u, String _c ->
            capturedUpdate = u
            return Mono.empty()
        }
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.COMMITTED
        def updateObject = capturedUpdate.getUpdateObject().get('$set')
        updateObject.get('state') == 'committed'
        updateObject.get('commit_id') == 'COMMIT-001'
        updateObject.get('commit_data') == [reason: 'done']
        updateObject.containsKey('committed_at')
    }

    def 'commit re-delivered after commit returns COMMIT_DUPLICATE without re-generating id'() {
        given:
        def message = commitMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'committed',
                commit_id: 'COMMIT-001'
        ])
        materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.COMMIT_DUPLICATE
        0 * idGenerator.nextId()
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'post-commit hook invokes materializer exactly once on first successful commit'() {
        given:
        def message = commitMessage(reason: 'done')
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'open'
        ])
        idGenerator.nextId() >> 'COMMIT-001'
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.COMMITTED
        1 * materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
    }

    def 'post-commit hook also invokes materializer on commit re-delivery to fill projection gaps'() {
        given:
        def message = commitMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'committed',
                commit_id: 'COMMIT-001'
        ])

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.COMMIT_DUPLICATE
        1 * materializer.materialize(TXN_ID) >> Mono.just(new MaterializeResult())
        0 * idGenerator.nextId()
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'materializer failure on the commit path is swallowed and surface result is COMMITTED'() {
        given:
        def message = commitMessage(reason: 'done')
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'open'
        ])
        idGenerator.nextId() >> 'COMMIT-001'
        mongoTemplate.updateFirst(_ as Query, _ as Update, COLLECTION) >> Mono.empty()
        materializer.materialize(TXN_ID) >> Mono.error(new RuntimeException('materializer boom'))

        when:
        def result = service.persist(message, source()).block()

        then: 'commit is durable in txn regardless of projection failure'
        result == PersistResult.COMMITTED
        noExceptionThrown()
    }

    def 'materializer failure on commit re-delivery is swallowed and surface result is COMMIT_DUPLICATE'() {
        given:
        def message = commitMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.just([
                _id: TXN_ID,
                state: 'committed',
                commit_id: 'COMMIT-001'
        ])
        materializer.materialize(TXN_ID) >> Mono.error(new RuntimeException('materializer boom'))

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.COMMIT_DUPLICATE
        noExceptionThrown()
        0 * idGenerator.nextId()
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'commit before open errors with IllegalStateException'() {
        given:
        def message = commitMessage()
        mongoTemplate.findById(TXN_ID, Map.class, COLLECTION) >> Mono.empty()

        when:
        service.persist(message, source()).block()

        then:
        IllegalStateException ex = thrown()
        ex.message.contains(TXN_ID)
        0 * idGenerator.nextId()
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'rollback returns ROLLBACK_NOT_PERSISTED and writes nothing'() {
        given:
        def message = rollbackMessage()

        when:
        def result = service.persist(message, source()).block()

        then:
        result == PersistResult.ROLLBACK_NOT_PERSISTED
        0 * mongoTemplate.findById(_, _, _)
        0 * mongoTemplate.upsert(_, _, _)
        0 * mongoTemplate.insert(_, _)
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * idGenerator.nextId()
    }

    def 'null message rejected with IllegalArgumentException'() {
        when:
        service.persist(null, source()).block()

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate._
    }
}
