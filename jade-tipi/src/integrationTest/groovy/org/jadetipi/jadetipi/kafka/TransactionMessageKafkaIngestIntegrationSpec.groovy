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
 * End-to-end integration coverage for the Kafka transaction ingest path.
 *
 * <p>Publishes canonical {@link Message} records (open + data + commit) to a
 * per-spec Kafka topic and asserts that {@code TransactionMessageListener}
 * persists the expected header and message documents into MongoDB's
 * {@code txn} collection.
 *
 * <p>Skip / run conditions (deliberately opt-in):
 * <ul>
 *   <li>Environment variable {@code JADETIPI_IT_KAFKA} must be set to
 *       {@code 1}, {@code true}, {@code TRUE}, or {@code yes}.</li>
 *   <li>A 2-second {@code AdminClient.describeCluster} probe against
 *       {@code KAFKA_BOOTSTRAP_SERVERS} (or {@code localhost:9092}) must
 *       succeed.</li>
 * </ul>
 *
 * <p>If either gate fails, the spec is skipped via Spock's
 * {@code @IgnoreIf}; the Spring context is never loaded.
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !TransactionMessageKafkaIngestIntegrationSpec.kafkaIntegrationGateOpen() })
class TransactionMessageKafkaIngestIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    // Topic name matches the default jdtp-txn-.*|jdtp_cli_kli pattern but is
    // overridden below to a strict regex so the listener only sees this run.
    private static final String TEST_TOPIC = "jdtp-txn-itest-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    /**
     * Spock {@code @IgnoreIf} gate. Skips the spec entirely (so the Spring
     * context never loads) unless the env flag is set and a Kafka broker is
     * reachable within ~2 seconds.
     */
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

    /**
     * Override Spring properties before context creation:
     * <ul>
     *   <li>Force the listener to auto-start (test profile sets it false).</li>
     *   <li>Restrict the topic pattern to this spec's per-run topic.</li>
     *   <li>Use a unique consumer group to read from offset 0 deterministically.</li>
     *   <li>Shorten metadata refresh so pattern subscription discovers the
     *       test topic within ~2s instead of the 5-minute default.</li>
     * </ul>
     * Also creates the test topic up-front so the listener can subscribe on
     * its first metadata refresh after context start.
     */
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
                log.info('Created Kafka test topic {}', TEST_TOPIC)
            } catch (ExecutionException ex) {
                if (!(ex.cause instanceof TopicExistsException)) {
                    throw ex
                }
                log.info('Kafka test topic {} already exists, reusing', TEST_TOPIC)
            }
        }
    }

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction txn
    String txnId

    def setupSpec() {
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')
        producer = new KafkaProducer<>(props)
    }

    def cleanupSpec() {
        if (producer != null) {
            producer.close(Duration.ofSeconds(5))
        }
        Properties props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.deleteTopics([TEST_TOPIC]).all().get(15, TimeUnit.SECONDS)
                log.info('Deleted Kafka test topic {}', TEST_TOPIC)
            } catch (Exception ex) {
                // Best-effort cleanup. A leftover empty topic does not affect
                // future runs because each run picks a fresh SHORT_UUID.
                log.warn('Failed to delete test topic {}: {}', TEST_TOPIC, ex.message)
            }
        }
    }

    def setup() {
        // Each feature gets its own canonical txn id for document isolation.
        txn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
    }

    def cleanup() {
        // Targeted delete: keeps coexistence with TransactionServiceIntegrationSpec,
        // which writes its own (different-shape) documents to txn.
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'open + data + commit are persisted as one header and one message document'() {
        given: 'canonical open, data, and commit messages for a fresh transaction'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'opened from kafka integration test'
        ])
        Message dataMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                name       : 'specific_heat_capacity',
                description: 'energy required to raise unit mass by one kelvin'
        ])
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'one property created'
        ])

        when: 'all three records are produced to the test topic'
        send(openMsg)
        send(dataMsg)
        send(commitMsg)

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header['_id'] == txnId
        header.record_type == 'transaction'
        header.txn_id == txnId
        header.state == 'committed'
        header.commit_id instanceof String
        ((String) header.commit_id).length() > 0
        header.opened_at != null
        header.committed_at != null
        header.open_data?.hint == 'opened from kafka integration test'
        header.commit_data?.summary == 'one property created'

        and: 'the data message document is keyed by txn_id~msg_uuid with kafka provenance'
        String dataMsgId = "${txnId}~${dataMsg.uuid()}"
        Map dataDoc = awaitMongo(
                { mongoTemplate.findById(dataMsgId, Map, TXN_COLLECTION) },
                { Map d -> d != null },
                'data message document'
        )
        dataDoc['_id'] == dataMsgId
        dataDoc.record_type == 'message'
        dataDoc.txn_id == txnId
        dataDoc.msg_uuid == dataMsg.uuid()
        dataDoc.collection == 'ppy'
        dataDoc.action == 'create'
        dataDoc.data?.name == 'specific_heat_capacity'
        dataDoc.received_at != null
        dataDoc.kafka != null
        dataDoc.kafka.topic == TEST_TOPIC
        dataDoc.kafka.partition == 0
        dataDoc.kafka.offset != null
        dataDoc.kafka.timestamp_ms != null

        and: 'exactly one header + one message document for this txn'
        long total = mongoTemplate.count(
                Query.query(Criteria.where('txn_id').is(txnId)), TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        total == 2L
    }

    def 'a re-published data message is deduplicated by natural _id'() {
        given:
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'idempotency check'
        ])
        Message dataMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                name       : 'molar_mass',
                description: 'mass per mole'
        ])
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'idempotency'
        ])

        when: 'first publication of open + data + commit'
        send(openMsg)
        send(dataMsg)
        send(commitMsg)

        and: 'the listener has persisted the header and the message document'
        awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' },
                'committed header (initial)'
        )
        String dataMsgId = "${txnId}~${dataMsg.uuid()}"
        awaitMongo(
                { mongoTemplate.findById(dataMsgId, Map, TXN_COLLECTION) },
                { Map d -> d != null },
                'data message document (initial)'
        )

        and: 'the same data message is re-published'
        send(dataMsg)

        and: 'the listener has time to process the duplicate (poll cycle + persist round-trip)'
        awaitConditionTrue(
                {
                    long count = mongoTemplate.count(
                            Query.query(Criteria.where('txn_id').is(txnId)), TXN_COLLECTION)
                            .block(MONGO_BLOCK_TIMEOUT)
                    return count == 2L
                },
                Duration.ofSeconds(10),
                'count stays at 2 after duplicate'
        )

        then: 'still exactly one header + one message document for this txn'
        long total = mongoTemplate.count(
                Query.query(Criteria.where('txn_id').is(txnId)), TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        total == 2L
    }

    private void send(Message message) {
        byte[] bytes = JsonMapper.toBytes(message)
        ProducerRecord<String, byte[]> record =
                new ProducerRecord<String, byte[]>(TEST_TOPIC, txnId, bytes)
        producer.send(record).get(10, TimeUnit.SECONDS)
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
        if (lastError != null) {
            throw new AssertionError(
                    "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                            "last error: ${lastError.message}", lastError)
        }
        throw new AssertionError(
                "Timed out waiting for ${description} within ${AWAIT_TIMEOUT}; " +
                        "last value: ${last}")
    }

    private static void awaitConditionTrue(Supplier<Boolean> condition,
                                           Duration timeout,
                                           String description) {
        long deadline = System.currentTimeMillis() + timeout.toMillis()
        boolean met = false
        while (System.currentTimeMillis() < deadline) {
            try {
                met = Boolean.TRUE == condition.get()
            } catch (Throwable ignored) {
                met = false
            }
            if (met) {
                return
            }
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
        throw new AssertionError("Condition not met within ${timeout}: ${description}")
    }
}
