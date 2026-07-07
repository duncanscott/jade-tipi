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

import groovy.util.logging.Slf4j
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.jadetipi.dto.collections.Transaction
import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Collection as JtpCollection
import org.jadetipi.dto.message.Message
import org.jadetipi.dto.util.JsonMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * End-to-end TASK-061 proof of the value-update model: transaction one
 * registers and assigns a property; transaction two assigns a new value
 * for the same property. The root's current entry carries the second
 * value with the second transaction's provenance, and the {@code hst}
 * history collection holds BOTH assignments, retrievable by
 * (object, property) in message order.
 *
 * <p>Same opt-in gates as the other Kafka specs.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !PropertyValueUpdateKafkaIntegrationSpec.kafkaIntegrationGateOpen() })
class PropertyValueUpdateKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-valupd-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-valupd-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String HST_COLLECTION = 'hst'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    static boolean kafkaIntegrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_KAFKA')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        Properties props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000)
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000)
        try (AdminClient admin = AdminClient.create(props)) {
            admin.describeCluster().clusterId().get(2, TimeUnit.SECONDS)
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add('jadetipi.kafka.enabled', { 'true' })
        registry.add('jadetipi.kafka.txn-topic-pattern', { TEST_TOPIC })
        registry.add('spring.kafka.bootstrap-servers', { BOOTSTRAP_SERVERS })
        registry.add('spring.kafka.consumer.group-id', { CONSUMER_GROUP })
        registry.add('spring.kafka.consumer.properties.metadata.max.age.ms', { '2000' })
        ensureTestTopic()
    }

    private static void ensureTestTopic() {
        Properties props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.createTopics([new NewTopic(TEST_TOPIC, 1, (short) 1)])
                        .all().get(15, TimeUnit.SECONDS)
            } catch (ExecutionException ex) {
                if (!(ex.cause instanceof TopicExistsException)) {
                    throw ex
                }
            }
        }
    }

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction firstTxn
    Transaction secondTxn
    String statusPropertyId
    String sampleTypeId
    String sampleEntId
    List<String> assignmentMsgUuids = []

    def setupSpec() {
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')
        producer = new KafkaProducer<>(props)
    }

    def cleanupSpec() {
        producer?.close(Duration.ofSeconds(5))
        Properties props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.deleteTopics([TEST_TOPIC]).all().get(15, TimeUnit.SECONDS)
            } catch (Exception ignored) {
            }
        }
    }

    def setup() {
        firstTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        secondTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        String idPrefix = "jade-itest-org~kafka~${firstTxn.uuid()}"
        statusPropertyId = "${idPrefix}~ppy~status"
        sampleTypeId = "${idPrefix}~typ~sample"
        sampleEntId = "${idPrefix}~ent~sample_1"
    }

    def cleanup() {
        [firstTxn.id, secondTxn.id].each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(statusPropertyId): 'ppy', (sampleTypeId): 'typ', (sampleEntId): 'ent'].each { String id, String coll ->
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)), coll)
                    .block(Duration.ofSeconds(10))
        }
        assignmentMsgUuids.each { String msgUuid ->
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(msgUuid)), HST_COLLECTION)
                    .block(Duration.ofSeconds(10))
        }
    }

    def 'a second transaction updates the current value and hst holds both assignments'() {
        given: 'transaction one registers the property and assigns the first value'
        Message open1 = Message.newInstance(firstTxn, JtpCollection.TRANSACTION, Action.OPEN,
                [description: 'first assignment'] as Map<String, Object>)
        Message ppyMsg = Message.newInstance(firstTxn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : statusPropertyId,
                name        : 'status',
                value_schema: [type: 'object', required: ['text'], properties: [text: [type: 'string']]]
        ] as Map<String, Object>)
        Message typMsg = Message.newInstance(firstTxn, JtpCollection.TYPE, Action.CREATE, [
                id  : sampleTypeId,
                name: 'sample'
        ] as Map<String, Object>)
        Message addPpyMsg = Message.newInstance(firstTxn, JtpCollection.TYPE, Action.UPDATE, [
                id         : sampleTypeId,
                operation  : 'add_property',
                property_id: statusPropertyId
        ] as Map<String, Object>)
        Message entMsg = Message.newInstance(firstTxn, JtpCollection.ENTITY, Action.CREATE, [
                id     : sampleEntId,
                type_id: sampleTypeId,
                name   : 'sample_1'
        ] as Map<String, Object>)
        Message assign1 = Message.newInstance(firstTxn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'ent',
                object_id        : sampleEntId,
                property_id      : statusPropertyId,
                value            : [text: 'received']
        ] as Map<String, Object>)
        Message commit1 = Message.newInstance(firstTxn, JtpCollection.TRANSACTION, Action.COMMIT,
                [summary: 'first value'] as Map<String, Object>)
        assignmentMsgUuids << assign1.uuid()

        when: 'the first transaction lands'
        [open1, ppyMsg, typMsg, addPpyMsg, entMsg, assign1, commit1].each { send(it, firstTxn.id) }
        Map entAfterFirst = awaitMongo(
                { mongoTemplate.findById(sampleEntId, Map, 'ent') },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(statusPropertyId) },
                'ent root with the first value')

        then: 'the first value is current'
        ((entAfterFirst.property_values as Map)[statusPropertyId] as Map).value == [text: 'received']

        when: 'a second transaction assigns a new value to the same property'
        Message open2 = Message.newInstance(secondTxn, JtpCollection.TRANSACTION, Action.OPEN,
                [description: 'update assignment'] as Map<String, Object>)
        Message assign2 = Message.newInstance(secondTxn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'ent',
                object_id        : sampleEntId,
                property_id      : statusPropertyId,
                value            : [text: 'in_process']
        ] as Map<String, Object>)
        Message commit2 = Message.newInstance(secondTxn, JtpCollection.TRANSACTION, Action.COMMIT,
                [summary: 'second value'] as Map<String, Object>)
        assignmentMsgUuids << assign2.uuid()
        [open2, assign2, commit2].each { send(it, secondTxn.id) }

        Map entAfterSecond = awaitMongo(
                { mongoTemplate.findById(sampleEntId, Map, 'ent') },
                { Map d ->
                    Map entry = ((Map) d?.property_values)?.get(statusPropertyId) as Map
                    entry?.value == [text: 'in_process']
                },
                'ent root updated to the second value')

        then: 'the second value is current with the second transaction provenance'
        Map current = (entAfterSecond.property_values as Map)[statusPropertyId] as Map
        current.value == [text: 'in_process']
        current.txn_id == secondTxn.id
        current.msg_uuid == assign2.uuid()

        and: 'hst holds both assignments for (object, property) in message order'
        List<Map> history = mongoTemplate.find(
                Query.query(Criteria.where('object_id').is(sampleEntId)
                        .and('property_id').is(statusPropertyId))
                        .with(Sort.by(Sort.Direction.ASC, 'msg_uuid')),
                Map, HST_COLLECTION).collectList().block(MONGO_BLOCK_TIMEOUT)
        history.size() == 2
        history[0]._id == assign1.uuid()
        history[0].value == [text: 'received']
        history[0].txn_id == firstTxn.id
        history[1]._id == assign2.uuid()
        history[1].value == [text: 'in_process']
        history[1].txn_id == secondTxn.id
    }

    private void send(Message message, String key) {
        byte[] bytes = JsonMapper.toBytes(message)
        producer.send(new ProducerRecord<String, byte[]>(TEST_TOPIC, key, bytes))
                .get(10, TimeUnit.SECONDS)
        producer.flush()
    }

    private static <T> T awaitMongo(Supplier<Mono<T>> source,
                                    Predicate<T> condition,
                                    String description) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT.toMillis()
        T last = null
        Throwable lastError = null
        while (System.currentTimeMillis() < deadline) {
            try {
                last = source.get().block(MONGO_BLOCK_TIMEOUT)
            } catch (Throwable t) {
                lastError = t
            }
            if (last != null && condition.test(last)) {
                return last
            }
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
        throw new AssertionError(
                "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                        "last value: ${last}", lastError)
    }
}
