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
 * End-to-end TASK-051 (UT-6) coverage for the late-append guard:
 * {@code open} → {@code loc + create} → {@code commit} → first root
 * materialized; then a **late** {@code loc + create} for the same
 * (already committed) transaction, a **commit re-delivery** — the exact
 * mechanism that used to surprise-materialize late rows — and a sentinel
 * second transaction produced afterwards on the same single-partition
 * topic.
 *
 * <p>Once the sentinel commits, strict partition ordering guarantees the
 * late append and the commit re-delivery were both consumed — so
 * asserting the late row is stored with {@code late_append: true} while
 * its root never materialized, and the original root is unaffected,
 * proves the guard rather than a not-yet-processed race.
 *
 * <p>Same opt-in gates as the other Kafka specs. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*LateAppendGuardKafkaIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !LateAppendGuardKafkaIntegrationSpec.kafkaIntegrationGateOpen() })
class LateAppendGuardKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-lateappend-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-lateappend-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
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

    Transaction committedTxn
    Transaction sentinelTxn
    String committedTxnId
    String sentinelTxnId
    String firstLocId
    String lateLocId
    String lateMessageRecordId

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
                log.warn('Failed to delete test topic {}: {}', TEST_TOPIC, ex.message)
            }
        }
    }

    def setup() {
        committedTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        sentinelTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        committedTxnId = committedTxn.id
        sentinelTxnId = sentinelTxn.id
        // Object identifier convention (TASK-044): transaction-UUID form.
        String idPrefix = "jade-itest-org~kafka~${committedTxn.uuid()}"
        firstLocId = "${idPrefix}~loc~committed_probe"
        lateLocId = "${idPrefix}~loc~late_probe"
    }

    def cleanup() {
        [committedTxnId, sentinelTxnId].findAll { it != null }.each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [firstLocId, lateLocId].findAll { it != null }.each { String id ->
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                    LOC_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'a message appended after commit is stored flagged and never materializes, even on commit re-delivery'() {
        given: 'a committed transaction with one materialized loc root'
        Message openMsg = Message.newInstance(committedTxn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'transaction that commits before the late append'
        ])
        Message firstLocMsg = Message.newInstance(committedTxn, JtpCollection.LOCATION, Action.CREATE, [
                id  : firstLocId,
                name: 'committed probe location'
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(committedTxn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'legitimate commit'
        ])
        Message lateLocMsg = Message.newInstance(committedTxn, JtpCollection.LOCATION, Action.CREATE, [
                id  : lateLocId,
                name: 'late probe location'
        ] as Map<String, Object>)
        Message sentinelOpenMsg = Message.newInstance(sentinelTxn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'sentinel proving the late append and redelivery were consumed'
        ])
        Message sentinelCommitMsg = Message.newInstance(sentinelTxn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'sentinel commit'
        ])
        lateMessageRecordId = "${committedTxnId}~${lateLocMsg.uuid()}"

        when: 'open, loc create, and commit are produced'
        [openMsg, firstLocMsg, commitMsg].each { send(it) }

        then: 'the first root materializes normally'
        Map committedHeader = awaitMongo(
                { mongoTemplate.findById(committedTxnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        awaitMongo(
                { mongoTemplate.findById(firstLocId, Map, LOC_COLLECTION) },
                { Map d -> d != null },
                'first loc root'
        ) != null

        when: 'a late loc create, a commit re-delivery, and the sentinel transaction are produced'
        [lateLocMsg, commitMsg, sentinelOpenMsg, sentinelCommitMsg].each { send(it) }

        and: 'the sentinel commits — strict ordering proves the late append and redelivery were consumed'
        Map sentinelHeader = awaitMongo(
                { mongoTemplate.findById(sentinelTxnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed sentinel transaction header'
        )

        then: 'the late row is stored flagged as the audit trail'
        sentinelHeader.state == 'committed'
        Map lateRow = mongoTemplate.findById(lateMessageRecordId, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        lateRow != null
        lateRow.record_type == 'message'
        lateRow.late_append == true

        and: 'the late root never materialized, despite the commit re-delivery'
        mongoTemplate.findById(lateLocId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == null

        and: 'the original root and header are unaffected'
        mongoTemplate.findById(firstLocId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) != null
        Map headerAfter = mongoTemplate.findById(committedTxnId, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        headerAfter.state == 'committed'
        headerAfter.commit_id == committedHeader.commit_id

        and: 'the commit fixed the committed set size and the applied row is stamped (TASK-056)'
        headerAfter.message_count == 1
        Map appliedRow = mongoTemplate.findById("${committedTxnId}~${firstLocMsg.uuid()}".toString(),
                Map, TXN_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        appliedRow.apply_state == 'applied'
        !lateRow.containsKey('apply_state')
    }

    private void send(Message message) {
        byte[] bytes = JsonMapper.toBytes(message)
        ProducerRecord<String, byte[]> record =
                new ProducerRecord<String, byte[]>(TEST_TOPIC, message.txn().getId(), bytes)
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
}
