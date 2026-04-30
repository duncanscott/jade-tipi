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
package org.jadetipi.jadetipi.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jadetipi.dto.collections.Group
import org.jadetipi.dto.collections.Transaction
import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Collection
import org.jadetipi.dto.message.Message
import org.jadetipi.dto.util.JsonMapper
import org.jadetipi.jadetipi.service.PersistResult
import org.jadetipi.jadetipi.service.TransactionMessagePersistenceService
import org.springframework.kafka.support.Acknowledgment
import reactor.core.publisher.Mono
import spock.lang.Specification

class TransactionMessageListenerSpec extends Specification {

    TransactionMessagePersistenceService persistenceService
    KafkaIngestProperties properties
    Acknowledgment ack
    TransactionMessageListener listener

    static final Transaction TXN = new Transaction(
            'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
            new Group('test-org', 'test-grp'),
            'jade-cli',
            '0000-0000-0000-0001'
    )

    def setup() {
        persistenceService = Mock(TransactionMessagePersistenceService)
        properties = new KafkaIngestProperties(persistTimeoutSeconds: 5)
        ack = Mock(Acknowledgment)
        listener = new TransactionMessageListener(persistenceService, properties)
    }

    private static ConsumerRecord<String, byte[]> record(byte[] value, String topic = 'jdtp-txn-prd') {
        return new ConsumerRecord<String, byte[]>(topic, 0, 7L, 'key', value)
    }

    def 'valid Kafka record is parsed, persisted, and acknowledged'() {
        given:
        def message = new Message(TXN, '11111111-1111-7111-8111-111111111111',
                Collection.TRANSACTION, Action.OPEN, [hint: 'open'])
        def bytes = JsonMapper.toBytes(message)
        Message persisted = null
        KafkaSourceMetadata capturedSource = null

        when:
        listener.onMessage(record(bytes), ack)

        then:
        1 * persistenceService.persist(_ as Message, _ as KafkaSourceMetadata) >>
                { Message m, KafkaSourceMetadata s ->
                    persisted = m
                    capturedSource = s
                    return Mono.just(PersistResult.OPENED)
                }
        1 * ack.acknowledge()

        and:
        persisted.uuid() == message.uuid()
        persisted.collection() == Collection.TRANSACTION
        persisted.action() == Action.OPEN
        capturedSource.topic == 'jdtp-txn-prd'
        capturedSource.partition == 0
        capturedSource.offset == 7L
    }

    def 'unparseable bytes are acknowledged and not forwarded'() {
        given:
        def garbage = 'not json {{{'.bytes

        when:
        listener.onMessage(record(garbage), ack)

        then:
        0 * persistenceService.persist(_, _)
        1 * ack.acknowledge()
    }

    def 'schema-invalid message is acknowledged and not forwarded'() {
        given: 'a message that parses but fails schema validation (no collection)'
        def json = '{"txn":{"uuid":"x","group":{"org":"o","grp":"g"},"client":"c","user":"u"},' +
                '"uuid":"u","action":"open","data":{}}'

        when:
        listener.onMessage(record(json.bytes), ack)

        then:
        0 * persistenceService.persist(_, _)
        1 * ack.acknowledge()
    }

    def 'persistence error propagates and the record is NOT acknowledged'() {
        given:
        def message = new Message(TXN, '11111111-1111-7111-8111-111111111111',
                Collection.TRANSACTION, Action.OPEN, [hint: 'open'])
        def bytes = JsonMapper.toBytes(message)

        when:
        listener.onMessage(record(bytes), ack)

        then:
        1 * persistenceService.persist(_, _) >> Mono.error(new RuntimeException('mongo down'))
        0 * ack.acknowledge()
        thrown(RuntimeException)
    }
}
