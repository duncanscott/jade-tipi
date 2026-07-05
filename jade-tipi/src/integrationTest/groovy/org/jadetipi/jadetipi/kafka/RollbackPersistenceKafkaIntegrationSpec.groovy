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
 * End-to-end TASK-050 (UT-2) coverage for durable rollback: {@code open} →
 * {@code loc + create} → {@code rollback} → a **late commit** for the same
 * transaction → a sentinel second transaction ({@code open} +
 * {@code commit}) produced afterwards on the same single-partition topic.
 *
 * <p>Once the sentinel transaction reaches {@code committed}, strict
 * partition ordering guarantees the late commit for the rolled-back
 * transaction was already consumed — so asserting the first header still
 * shows {@code state: "rolled_back"} with no {@code commit_id}, and that
 * the {@code loc} root never materialized, proves commit-after-rollback is
 * refused rather than merely not-yet-processed. The appended {@code loc}
 * message row must remain stored as the audit trail.
 *
 * <p>Same opt-in gates as the other Kafka specs. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*RollbackPersistenceKafkaIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !RollbackPersistenceKafkaIntegrationSpec.kafkaIntegrationGateOpen() })
class RollbackPersistenceKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-rollback-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-rollback-${SHORT_UUID}"
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

    Transaction rolledBackTxn
    Transaction sentinelTxn
    String rolledBackTxnId
    String sentinelTxnId
    String locId
    String locMessageRecordId

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
        rolledBackTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        sentinelTxn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        rolledBackTxnId = rolledBackTxn.id
        sentinelTxnId = sentinelTxn.id
        // Object identifier convention (TASK-044): transaction-UUID form.
        locId = "jade-itest-org~kafka~${rolledBackTxn.uuid()}~loc~rollback_probe"
    }

    def cleanup() {
        [rolledBackTxnId, sentinelTxnId].findAll { it != null }.each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (locId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(locId)),
                    LOC_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'rollback is durable and a late commit is refused: no commit_id, no materialization, audit rows retained'() {
        given: 'an opened transaction carrying one loc create'
        Message openMsg = Message.newInstance(rolledBackTxn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'transaction that will be rolled back'
        ])
        Message locMsg = Message.newInstance(rolledBackTxn, JtpCollection.LOCATION, Action.CREATE, [
                id  : locId,
                name: 'rollback probe location'
        ] as Map<String, Object>)
        Message rollbackMsg = Message.newInstance(rolledBackTxn, JtpCollection.TRANSACTION, Action.ROLLBACK, [
                reason: 'user abort'
        ])
        Message lateCommitMsg = Message.newInstance(rolledBackTxn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'late commit that must be refused'
        ])
        Message sentinelOpenMsg = Message.newInstance(sentinelTxn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'sentinel proving the late commit was consumed'
        ])
        Message sentinelCommitMsg = Message.newInstance(sentinelTxn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'sentinel commit'
        ])
        locMessageRecordId = "${rolledBackTxnId}~${locMsg.uuid()}"

        when: 'open, loc create, and rollback are produced'
        [openMsg, locMsg, rollbackMsg].each { send(it) }

        then: 'the header durably reaches rolled_back with the audit fields and no commit_id'
        Map rolledBackHeader = awaitMongo(
                { mongoTemplate.findById(rolledBackTxnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'rolled_back' },
                'rolled_back transaction header'
        )
        rolledBackHeader.rollback_data == [reason: 'user abort']
        rolledBackHeader.rolled_back_at != null
        rolledBackHeader.commit_id == null

        when: 'a late commit for the rolled-back transaction is produced, then the sentinel transaction'
        [lateCommitMsg, sentinelOpenMsg, sentinelCommitMsg].each { send(it) }

        and: 'the sentinel commits — on one partition this proves the late commit was already consumed'
        Map sentinelHeader = awaitMongo(
                { mongoTemplate.findById(sentinelTxnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed sentinel transaction header'
        )

        then: 'the rolled-back header is untouched by the late commit'
        sentinelHeader.state == 'committed'
        Map headerAfterLateCommit = mongoTemplate.findById(rolledBackTxnId, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        headerAfterLateCommit.state == 'rolled_back'
        headerAfterLateCommit.commit_id == null
        !headerAfterLateCommit.containsKey('committed_at')

        and: 'the loc root never materialized'
        mongoTemplate.findById(locId, Map, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == null

        and: 'the appended loc message row remains stored as the audit trail'
        Map messageRow = mongoTemplate.findById(locMessageRecordId, Map, TXN_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        messageRow != null
        messageRow.record_type == 'message'
        messageRow.collection == 'loc'
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
