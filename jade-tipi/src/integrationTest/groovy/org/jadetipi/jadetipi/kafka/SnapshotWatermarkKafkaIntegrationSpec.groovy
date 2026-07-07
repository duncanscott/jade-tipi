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
 * End-to-end TASK-063 proof of snapshot isolation through Kafka: while an
 * older transaction is open, a newer transaction's commit is durably marked
 * but NOT materialized (its effects stay invisible in the roots the open
 * transaction reads); the moment the older transaction commits, the released
 * transaction materializes. Also pins the backend-minted `snapshot_id` and
 * UUIDv7 `commit_id` on the headers, ordered by Kafka processing order.
 *
 * <p>Same opt-in gates as the other Kafka specs.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !SnapshotWatermarkKafkaIntegrationSpec.kafkaIntegrationGateOpen() })
class SnapshotWatermarkKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-watermark-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-watermark-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)
    /** Window in which the blocked commit must stay unmaterialized. */
    private static final long BLOCKED_OBSERVATION_MILLIS = 4000

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

    Transaction olderTxn
    Transaction newerTxn
    String locId

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
        olderTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        newerTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        locId = "jade-itest-org~kafka~${newerTxn.uuid()}~loc~watermark_probe"
    }

    def cleanup() {
        [olderTxn.id, newerTxn.id].each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        mongoTemplate.remove(Query.query(Criteria.where('_id').is(locId)),
                LOC_COLLECTION).block(Duration.ofSeconds(10))
    }

    def 'a commit stays unmaterialized while an older transaction is open and releases when it closes'() {
        given: 'an older transaction opens and stays open'
        Message olderOpen = Message.newInstance(olderTxn, JtpCollection.TRANSACTION, Action.OPEN,
                [description: 'older open holds the watermark'] as Map<String, Object>)
        send(olderOpen, olderTxn.id)
        Map olderHeader = awaitMongo(
                { mongoTemplate.findById(olderTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'open' && h?.snapshot_id != null },
                'older open header with backend snapshot_id')

        and: 'the backend minted its snapshot point as a UUIDv7'
        assert UUID.fromString(olderHeader.snapshot_id as String).version() == 7

        when: 'a newer transaction creates a location and commits'
        Message newerOpen = Message.newInstance(newerTxn, JtpCollection.TRANSACTION, Action.OPEN,
                [description: 'newer transaction'] as Map<String, Object>)
        Message locMsg = Message.newInstance(newerTxn, JtpCollection.LOCATION, Action.CREATE, [
                id  : locId,
                name: 'watermark probe location'
        ] as Map<String, Object>)
        Message newerCommit = Message.newInstance(newerTxn, JtpCollection.TRANSACTION, Action.COMMIT,
                [summary: 'blocked behind the older open'] as Map<String, Object>)
        [newerOpen, locMsg, newerCommit].each { send(it, newerTxn.id) }
        Map committedHeader = awaitMongo(
                { mongoTemplate.findById(newerTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'newer transaction durably committed')

        then: 'the commit id is a UUIDv7 newer than the older open snapshot'
        UUID.fromString(committedHeader.commit_id as String).version() == 7
        (committedHeader.commit_id as String) > (olderHeader.snapshot_id as String)

        and: 'the committed transaction stays unmaterialized while the older transaction is open'
        Thread.sleep(BLOCKED_OBSERVATION_MILLIS)
        mongoTemplate.findById(locId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == null
        mongoTemplate.findById(newerTxn.id, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT).materialized_at == null

        when: 'the older transaction reaches its terminal state'
        Message olderCommit = Message.newInstance(olderTxn, JtpCollection.TRANSACTION, Action.COMMIT,
                [summary: 'release the watermark'] as Map<String, Object>)
        send(olderCommit, olderTxn.id)

        then: 'the released transaction materializes and both headers are watermarked'
        Map locDoc = awaitMongo(
                { mongoTemplate.findById(locId, Map, LOC_COLLECTION) },
                { Map d -> d != null },
                'released loc root')
        ((locDoc._head as Map).provenance as Map).txn_id == newerTxn.id
        awaitMongo(
                { mongoTemplate.findById(newerTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.materialized_at != null },
                'released header watermarked')
        awaitMongo(
                { mongoTemplate.findById(olderTxn.id, Map, TXN_COLLECTION) },
                { Map h -> h?.materialized_at != null },
                'older (empty) transaction watermarked')
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
