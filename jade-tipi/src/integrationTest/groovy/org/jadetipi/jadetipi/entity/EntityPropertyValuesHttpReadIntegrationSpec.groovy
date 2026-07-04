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
package org.jadetipi.jadetipi.entity

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
 * End-to-end integration coverage for the entity property-values HTTP read
 * route over materialized {@code ent}, {@code typ}, and {@code ppy} roots.
 *
 * <p>Publishes one canonical property-loop transaction to a per-spec Kafka
 * topic, waits for the transaction header plus the projected
 * {@code property_values} entry on the entity root (TASK-045: assignments
 * project onto object roots; standalone assignment roots are retired), then
 * asserts that {@code GET /api/entities/{id}/property-values} returns the
 * generic object property-values shape. The same feature also covers an
 * existing entity with no assignments and a missing entity id.
 *
 * <p>Skip / run conditions are deliberately opt-in:
 * <ul>
 *   <li>Environment variable {@code JADETIPI_IT_KAFKA} must be set to
 *       {@code 1}, {@code true}, {@code TRUE}, or {@code yes}.</li>
 *   <li>A 2-second {@code AdminClient.describeCluster} probe against
 *       {@code KAFKA_BOOTSTRAP_SERVERS} (or {@code localhost:9092}) must
 *       succeed.</li>
 *   <li>A 2-second HTTP probe against the Keycloak realm OpenID
 *       configuration at
 *       {@code ${TEST_KEYCLOAK_URL ?: KEYCLOAK_URL ?: 'http://localhost:8484'}/realms/jade-tipi/.well-known/openid-configuration}
 *       must respond {@code 2xx}.</li>
 * </ul>
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*EntityPropertyValuesHttpReadIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles('test')
@IgnoreIf({ !EntityPropertyValuesHttpReadIntegrationSpec.integrationGateOpen() })
class EntityPropertyValuesHttpReadIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String KEYCLOAK_BASE_URL =
            System.getenv('TEST_KEYCLOAK_URL') ?:
                    (System.getenv('KEYCLOAK_URL') ?: 'http://localhost:8484')
    private static final String KEYCLOAK_REALM = 'jade-tipi'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-entppy-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-entppy-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String ENT_COLLECTION = 'ent'
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

    @Autowired
    WebTestClient webTestClient

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction txn
    String txnId
    String propertyDefinitionId
    String entityTypeId
    String entityId
    String emptyEntityId

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
        txn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
        // Object identifier convention (TASK-044): transaction-UUID form.
        String idPrefix = "jade-itest-org~kafka~${txn.uuid()}"
        propertyDefinitionId = "${idPrefix}~ppy~barcode"
        entityTypeId = "${idPrefix}~typ~plate_96"
        entityId = "${idPrefix}~ent~plate"
        emptyEntityId = "${idPrefix}~ent~empty_plate"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(PPY_COLLECTION): [propertyDefinitionId],
         (TYP_COLLECTION): [entityTypeId],
         (ENT_COLLECTION): [entityId, emptyEntityId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'entity property-values route joins materialized assignments and handles empty or missing entities'() {
        given: 'one property-loop transaction with one assigned entity and one unassigned entity'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'opened from entity property-values http integration test'
        ])
        Message ppyDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : propertyDefinitionId,
                name        : 'barcode',
                value_schema: [
                        type      : 'object',
                        required  : ['text'],
                        properties: [text: [type: 'string']]
                ]
        ] as Map<String, Object>)
        Message typCreateMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : entityTypeId,
                name       : 'plate_96',
                description: '96-well sample plate'
        ] as Map<String, Object>)
        Message typAddPropertyMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : entityTypeId,
                operation  : 'add_property',
                property_id: propertyDefinitionId,
                required   : true
        ] as Map<String, Object>)
        Message entCreateMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : entityId,
                type_id   : entityTypeId,
                properties: [label: 'Plate A'],
                links     : [:]
        ] as Map<String, Object>)
        Message emptyEntCreateMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : emptyEntityId,
                type_id   : entityTypeId,
                properties: [label: 'Empty Plate'],
                links     : [:]
        ] as Map<String, Object>)
        Message assignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'ent',
                object_id        : entityId,
                property_id      : propertyDefinitionId,
                value            : [text: 'barcode-1']
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'entity property-values http read fixture'
        ])
        String token = KeycloakTestHelper.getAccessToken()

        when: 'all records are produced to the test topic in order'
        [openMsg, ppyDefinitionMsg, typCreateMsg, typAddPropertyMsg,
         entCreateMsg, emptyEntCreateMsg, assignmentMsg, commitMsg].each { send(it) }

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header.state == 'committed'

        and: 'the materialized roots needed by the read service are present'
        awaitMongo(
                { mongoTemplate.findById(propertyDefinitionId, Map, PPY_COLLECTION) },
                { Map d -> ((Map) d?.properties)?.kind == 'definition' },
                'root-shaped ppy definition document'
        )
        Map assignedEntity = awaitMongo(
                { mongoTemplate.findById(entityId, Map, ENT_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(propertyDefinitionId) },
                'entity root with projected property_values entry'
        )
        awaitMongo(
                { mongoTemplate.findById(emptyEntityId, Map, ENT_COLLECTION) },
                { Map d -> d != null },
                'unassigned entity root'
        )

        and: 'no standalone assignment root is written for the object-targeted form'
        ((Map) ((Map) assignedEntity.property_values)[propertyDefinitionId]).value == [text: 'barcode-1']
        mongoTemplate.findById("${entityId}~${propertyDefinitionId}" as String, Map, PPY_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT) == null

        when: 'the assigned entity is read through the HTTP route'
        String valuePath = "\$.propertyValues['${propertyDefinitionId}']"
        WebTestClient.ResponseSpec assignedResponse = webTestClient.get()
                .uri('/api/entities/{id}/property-values', entityId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the route returns the generic object property-values shape with the projected entry'
        assignedResponse.expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(entityId)
                .jsonPath('$.collection').isEqualTo('ent')
                .jsonPath('$.typeId').isEqualTo(entityTypeId)
                .jsonPath('$.properties.label').isEqualTo('Plate A')
                .jsonPath('$.provenance.txn_id').isEqualTo(txnId)
                .jsonPath('$.propertyValues.length()').isEqualTo(1)
                .jsonPath("${valuePath}.propertyId").isEqualTo(propertyDefinitionId)
                .jsonPath("${valuePath}.propertyName").isEqualTo('barcode')
                .jsonPath("${valuePath}.value.text").isEqualTo('barcode-1')
                .jsonPath("${valuePath}.txnId").isEqualTo(txnId)
                .jsonPath("${valuePath}.commitId").exists()
                .jsonPath("${valuePath}.msgUuid").isEqualTo(assignmentMsg.uuid())
                .jsonPath("${valuePath}.appliedAt").exists()

        when: 'an existing entity with no assignments is read through the same route'
        WebTestClient.ResponseSpec emptyResponse = webTestClient.get()
                .uri('/api/entities/{id}/property-values', emptyEntityId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the route returns 200 with an empty propertyValues map'
        emptyResponse.expectStatus().isOk()
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(emptyEntityId)
                .jsonPath('$.propertyValues').exists()
                .jsonPath('$.propertyValues.length()').isEqualTo(0)

        when: 'a never-materialized entity id is read'
        WebTestClient.ResponseSpec missingResponse = webTestClient.get()
                .uri('/api/entities/{id}/property-values',
                        "jade-itest-org~kafka~${txn.uuid()}~ent~missing")
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'missing entity roots are surfaced as 404'
        missingResponse.expectStatus().isNotFound()
                .expectBody().isEmpty()
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
}
