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
package org.jadetipi.jadetipi.propertyvalues

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
 * End-to-end TASK-041 coverage: publishes the TASK-040 plate hierarchy
 * sequence to a per-spec Kafka topic, waits for materialization, then
 * asserts the two new HTTP read routes with a real JWT:
 * <ul>
 *   <li>{@code GET /api/locations/{id}/property-values} — the typed loc root
 *       with its projected, name-resolved {@code propertyValues};</li>
 *   <li>{@code GET /api/types/{id}/effective-properties} — the subtype's
 *       inherited property union with source attribution.</li>
 * </ul>
 * Missing-subject 404 contracts are asserted for both routes.
 *
 * <p>Same opt-in gates as {@code ContentsHttpReadIntegrationSpec} (env flag,
 * Kafka probe, Keycloak probe). Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*PlatePropertyValuesHttpReadIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles('test')
@IgnoreIf({ !PlatePropertyValuesHttpReadIntegrationSpec.integrationGateOpen() })
class PlatePropertyValuesHttpReadIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String KEYCLOAK_BASE_URL =
            System.getenv('TEST_KEYCLOAK_URL') ?:
                    (System.getenv('KEYCLOAK_URL') ?: 'http://localhost:8484')
    private static final String KEYCLOAK_REALM = 'jade-tipi'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-ppyvals-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-ppyvals-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LOC_COLLECTION = 'loc'
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
    WebTestClient webTestClient

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    @Shared
    KafkaProducer<String, byte[]> producer

    private static String accessToken

    Transaction txn
    String txnId
    String barcodePropertyId
    String containerTypeId
    String plateTypeId
    String plate96TypeId
    String plateLocId

    def setupSpec() {
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')
        producer = new KafkaProducer<>(props)
        accessToken = KeycloakTestHelper.getAccessToken()
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
            } catch (Exception ex) {
                log.warn('Failed to delete test topic {}: {}', TEST_TOPIC, ex.message)
            }
        }
    }

    def setup() {
        txn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
        // Object identifier convention (TASK-044): transaction-UUID form —
        // per-feature uniqueness comes from the fresh transaction UUIDv7.
        String idPrefix = "jade-itest-org~kafka~${txn.uuid()}"
        barcodePropertyId = "${idPrefix}~ppy~barcode"
        containerTypeId = "${idPrefix}~typ~container"
        plateTypeId = "${idPrefix}~typ~plate"
        plate96TypeId = "${idPrefix}~typ~plate_96_well"
        plateLocId = "${idPrefix}~loc~plate_0001"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(PPY_COLLECTION): [barcodePropertyId],
         (TYP_COLLECTION): [containerTypeId, plateTypeId, plate96TypeId],
         (LOC_COLLECTION): [plateLocId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'the projected loc property values and the inherited effective properties read back over HTTP'() {
        given: 'the committed plate hierarchy sequence'
        publishPlateHierarchyTransaction()

        and: 'materialization has landed the projected value'
        awaitMongo(
                { mongoTemplate.findById(plateLocId, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(barcodePropertyId) },
                'loc root with projected property_values entry'
        )

        expect: 'the loc property-values route returns the typed root with the resolved entry'
        webTestClient.get().uri('/api/locations/{id}/property-values', plateLocId)
                .header('Authorization', "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.object_id').isEqualTo(plateLocId)
                .jsonPath('$.collection').isEqualTo('loc')
                .jsonPath('$.type_id').isEqualTo(plate96TypeId)
                .jsonPath('$.properties.name').isEqualTo('demo plate 0001')
                .jsonPath("\$.property_values['${barcodePropertyId}'].property_name").isEqualTo('barcode')
                .jsonPath("\$.property_values['${barcodePropertyId}'].value.text").isEqualTo('PLATE-BC-0001')
                .jsonPath("\$.property_values['${barcodePropertyId}'].txn_id").isEqualTo(txnId)
                .jsonPath("\$.property_values['${barcodePropertyId}'].commit_id").isNotEmpty()
                .jsonPath("\$.property_values['${barcodePropertyId}'].applied_at").isNotEmpty()

        and: 'the subtype effective-properties route reports the inherited barcode with source attribution'
        webTestClient.get().uri('/api/types/{id}/effective-properties', plate96TypeId)
                .header('Authorization', "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.type_id').isEqualTo(plate96TypeId)
                .jsonPath('$.type_name').isEqualTo('plate_96_well')
                .jsonPath('$.chain_complete').isEqualTo(true)
                .jsonPath('$.type_chain[0]').isEqualTo(plate96TypeId)
                .jsonPath('$.type_chain[1]').isEqualTo(plateTypeId)
                .jsonPath('$.type_chain[2]').isEqualTo(containerTypeId)
                .jsonPath("\$.effective_properties['${barcodePropertyId}'].source_type_id").isEqualTo(containerTypeId)
                .jsonPath("\$.effective_properties['${barcodePropertyId}'].property_name").isEqualTo('barcode')

        and: 'both routes 404 for unknown subjects'
        webTestClient.get().uri('/api/locations/{id}/property-values', 'no-such-loc')
                .header('Authorization', "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isNotFound()
        webTestClient.get().uri('/api/types/{id}/effective-properties', 'no-such-typ')
                .header('Authorization', "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isNotFound()
    }

    private void publishPlateHierarchyTransaction() {
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'plate property values http read demo'
        ])
        Message barcodeDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : barcodePropertyId,
                name        : 'barcode',
                value_schema: [type: 'object', required: ['text'], properties: [text: [type: 'string']]]
        ] as Map<String, Object>)
        Message containerTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id: containerTypeId, name: 'container'
        ] as Map<String, Object>)
        Message containerAddBarcodeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id: containerTypeId, operation: 'add_property', property_id: barcodePropertyId
        ] as Map<String, Object>)
        Message plateTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id: plateTypeId, name: 'plate', parent_type_id: containerTypeId
        ] as Map<String, Object>)
        Message plate96TypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id: plate96TypeId, name: 'plate_96_well', parent_type_id: plateTypeId
        ] as Map<String, Object>)
        Message plateInstanceMsg = Message.newInstance(txn, JtpCollection.LOCATION, Action.CREATE, [
                id: plateLocId, type_id: plate96TypeId,
                properties: [name: 'demo plate 0001'], links: [:]
        ] as Map<String, Object>)
        Message barcodeAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'loc',
                object_id        : plateLocId,
                property_id      : barcodePropertyId,
                value            : [text: 'PLATE-BC-0001']
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'plate hierarchy for http property-value reads'
        ])

        [openMsg, barcodeDefinitionMsg, containerTypeMsg, containerAddBarcodeMsg,
         plateTypeMsg, plate96TypeMsg, plateInstanceMsg, barcodeAssignmentMsg,
         commitMsg].each { send(it) }
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
