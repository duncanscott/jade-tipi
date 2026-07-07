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
package org.jadetipi.jadetipi.history

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
import org.jadetipi.jadetipi.config.KeycloakTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
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
 * End-to-end TASK-065 proof of the history read: two transactions assign the
 * same property (the TASK-061 update model), then
 * {@code GET /api/objects/{id}/history} returns BOTH assignments in
 * message-UUID (chronological) order with values, provenance, and resolved
 * property names; the {@code property_id} filter narrows correctly; a
 * foreign-property filter is an empty page; malformed IDs and missing roots
 * are 404.
 *
 * <p>Same opt-in gates as the other Keycloak+Kafka HTTP read specs
 * ({@code JADETIPI_IT_KAFKA=1} plus reachable Kafka and Keycloak).
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles('test')
@IgnoreIf({ !ObjectHistoryHttpReadIntegrationSpec.integrationGateOpen() })
class ObjectHistoryHttpReadIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String KEYCLOAK_BASE_URL =
            System.getenv('TEST_KEYCLOAK_URL') ?:
                    (System.getenv('KEYCLOAK_URL') ?: 'http://localhost:8484')
    private static final String KEYCLOAK_REALM = 'jade-tipi'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-history-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-history-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String HST_COLLECTION = 'hst'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    static boolean integrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_KAFKA')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        return kafkaReachable() && keycloakReachable()
    }

    private static boolean kafkaReachable() {
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

    private static boolean keycloakReachable() {
        HttpURLConnection conn = null
        try {
            URL url = new URL("${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration")
            conn = (HttpURLConnection) url.openConnection()
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = 'GET'
            int code = conn.responseCode
            return code >= 200 && code < 300
        } catch (Exception ignored) {
            return false
        } finally {
            if (conn != null) {
                conn.disconnect()
            }
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

    @Autowired
    WebTestClient webTestClient

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
        assignmentMsgUuids = []
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

    def 'the history route returns both assignments in msg-uuid order and honors the property filter'() {
        given: 'two committed transactions assigning the same property (TASK-061 update model)'
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
        [open1, ppyMsg, typMsg, addPpyMsg, entMsg, assign1, commit1].each { send(it, firstTxn.id) }
        awaitMongo(
                { mongoTemplate.findById(sampleEntId, Map, 'ent') },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(statusPropertyId) },
                'ent root with the first value')

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
        awaitMongo(
                { mongoTemplate.findById(sampleEntId, Map, 'ent') },
                { Map d ->
                    Map entry = ((Map) d?.property_values)?.get(statusPropertyId) as Map
                    entry?.value == [text: 'in_process']
                },
                'ent root updated to the second value')

        and: 'a bearer token'
        String token = KeycloakTestHelper.getAccessToken()

        expect: 'the unfiltered history holds both assignments in msg-uuid order with names and provenance'
        String entryPath0 = '$.items[0]'
        String entryPath1 = '$.items[1]'
        webTestClient.get().uri('/api/objects/{id}/history', sampleEntId)
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.object_id').isEqualTo(sampleEntId)
                .jsonPath('$.collection').isEqualTo('ent')
                .jsonPath('$.property_id').isEmpty()
                .jsonPath('$.total').isEqualTo(2)
                .jsonPath('$.page').isEqualTo(0)
                .jsonPath('$.size').isEqualTo(25)
                .jsonPath("${entryPath0}.msg_uuid").isEqualTo(assignmentMsgUuids[0])
                .jsonPath("${entryPath0}.property_id").isEqualTo(statusPropertyId)
                .jsonPath("${entryPath0}.property_name").isEqualTo('status')
                .jsonPath("${entryPath0}.value.text").isEqualTo('received')
                .jsonPath("${entryPath0}.txn_id").isEqualTo(firstTxn.id)
                .jsonPath("${entryPath0}.commit_id").exists()
                .jsonPath("${entryPath0}.applied_at").exists()
                .jsonPath("${entryPath1}.msg_uuid").isEqualTo(assignmentMsgUuids[1])
                .jsonPath("${entryPath1}.value.text").isEqualTo('in_process')
                .jsonPath("${entryPath1}.txn_id").isEqualTo(secondTxn.id)

        and: 'the property filter returns the same rows and echoes the filter'
        webTestClient.get()
                .uri({ builder ->
                    builder.path('/api/objects/{id}/history')
                            .queryParam('property_id', statusPropertyId)
                            .build(sampleEntId)
                })
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.property_id').isEqualTo(statusPropertyId)
                .jsonPath('$.total').isEqualTo(2)

        and: 'a foreign property filter is an empty page, not an error'
        webTestClient.get()
                .uri({ builder ->
                    builder.path('/api/objects/{id}/history')
                            .queryParam('property_id', "${statusPropertyId}_absent".toString())
                            .build(sampleEntId)
                })
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.total').isEqualTo(0)
                .jsonPath('$.items.length()').isEqualTo(0)

        and: 'a missing root and a malformed id are 404'
        webTestClient.get().uri('/api/objects/{id}/history',
                "jade-itest-org~kafka~${firstTxn.uuid()}~ent~missing")
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isNotFound()
        webTestClient.get().uri('/api/objects/{id}/history', 'not-an-object-id')
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isNotFound()
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
