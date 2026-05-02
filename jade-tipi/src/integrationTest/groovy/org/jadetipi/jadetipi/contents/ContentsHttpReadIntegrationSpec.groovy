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
package org.jadetipi.jadetipi.contents

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
 * End-to-end integration coverage for the contents HTTP read routes over the
 * accepted root-shaped materialized contract.
 *
 * <p>Publishes one canonical contents transaction (open + {@code loc + create}
 * + {@code typ + create} link-type declaration + {@code lnk + create} with
 * plate-well {@code properties.position} + commit) to a per-spec Kafka topic,
 * waits for the committed {@code txn} header and the root-shaped materialized
 * {@code typ} and {@code lnk} rows, then asserts that
 * {@code GET /api/contents/by-container/{id}} and
 * {@code GET /api/contents/by-content/{id}} both return the same flat JSON
 * record. A second feature exercises an unrelated id and asserts the empty
 * HTTP 200 {@code []} contract.
 *
 * <p>Skip / run conditions (deliberately opt-in):
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
 * <p>If any gate fails, the spec is skipped via Spock's {@code @IgnoreIf};
 * the Spring context is never loaded and the {@code test} profile's real
 * Keycloak issuer is never resolved at startup.
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*ContentsHttpReadIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles('test')
@IgnoreIf({ !ContentsHttpReadIntegrationSpec.integrationGateOpen() })
class ContentsHttpReadIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String KEYCLOAK_BASE_URL =
            System.getenv('TEST_KEYCLOAK_URL') ?:
                    (System.getenv('KEYCLOAK_URL') ?: 'http://localhost:8484')
    private static final String KEYCLOAK_REALM = 'jade-tipi'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-contents-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-contents-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LNK_COLLECTION = 'lnk'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    /**
     * Spock {@code @IgnoreIf} gate. Skips the spec entirely (so the Spring
     * context never loads) unless the env flag is set, a Kafka broker is
     * reachable, and the Keycloak realm OpenID configuration responds. The
     * Keycloak probe is needed because the {@code test} profile points the
     * resource server at a real Keycloak issuer and Spring resolves the
     * issuer at context startup.
     */
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

    /**
     * Override Spring properties before context creation, mirroring the
     * accepted Kafka ingest spec:
     * <ul>
     *   <li>Force the listener to auto-start (test profile sets it false).</li>
     *   <li>Restrict the topic pattern to this spec's per-run topic.</li>
     *   <li>Use a unique consumer group to read from offset 0 deterministically.</li>
     *   <li>Shorten metadata refresh so pattern subscription discovers the
     *       test topic quickly instead of the 5-minute default.</li>
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

    @Autowired
    WebTestClient webTestClient

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction txn
    String txnId
    String containerId
    String typeId
    String linkId
    String contentId

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
        // Per-feature canonical txn plus per-feature materialized object ids.
        // Cleanup uses these ids to delete only this feature's rows.
        txn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
        String featureUuid = UUID.randomUUID().toString().substring(0, 8)
        containerId = "jadetipi-itest-contents~loc~plate_${featureUuid}"
        typeId = "jadetipi-itest-contents~typ~contents_${featureUuid}"
        linkId = "jadetipi-itest-contents~lnk~plate_${featureUuid}_well_a1"
        contentId = "jadetipi-itest-contents~ent~sample_${featureUuid}"
    }

    def cleanup() {
        // Targeted deletes scoped strictly to this feature's per-run ids.
        // Each remove() is independent so a missing row in one collection
        // does not stop cleanup of the others.
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (containerId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(containerId)),
                    LOC_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (typeId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(typeId)),
                    TYP_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (linkId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(linkId)),
                    LNK_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'forward and reverse contents HTTP routes return the materialized link'() {
        given: 'one canonical contents transaction (open + loc + typ + lnk + commit)'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'opened from contents http integration test'
        ])
        Message locMsg = Message.newInstance(txn, JtpCollection.LOCATION, Action.CREATE, [
                id         : containerId,
                name       : "plate_${SHORT_UUID}",
                description: 'integration plate'
        ] as Map<String, Object>)
        Message typMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : typeId,
                name                     : 'contents',
                description              : 'containment relationship between a container location and its contents',
                left_role                : 'container',
                right_role               : 'content',
                left_to_right_label      : 'contains',
                right_to_left_label      : 'contained_by',
                allowed_left_collections : ['loc'],
                allowed_right_collections: ['loc', 'ent']
        ] as Map<String, Object>)
        Message lnkMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id        : linkId,
                type_id   : typeId,
                left      : containerId,
                right     : contentId,
                properties: [
                        position: [
                                kind  : 'plate_well',
                                label : 'A1',
                                row   : 'A',
                                column: 1
                        ]
                ]
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'one container, one contents type, one contents link'
        ])
        String token = KeycloakTestHelper.getAccessToken()

        when: 'all five records are produced to the test topic'
        send(openMsg)
        send(locMsg)
        send(typMsg)
        send(lnkMsg)
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

        and: 'the typ document is materialized in root shape with properties.kind/name'
        Map typDoc = awaitMongo(
                { mongoTemplate.findById(typeId, Map, TYP_COLLECTION) },
                { Map d -> d != null },
                'root-shaped typ document'
        )
        typDoc['_id'] == typeId
        typDoc.id == typeId
        typDoc.collection == 'typ'
        ((Map) typDoc.properties)?.kind == 'link_type'
        ((Map) typDoc.properties)?.name == 'contents'
        ((Map) ((Map) typDoc._head)?.provenance)?.txn_id == txnId

        and: 'the lnk document is materialized in root shape with top-level type_id/left/right and _head.provenance'
        Map lnkDoc = awaitMongo(
                { mongoTemplate.findById(linkId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'root-shaped lnk document'
        )
        lnkDoc['_id'] == linkId
        lnkDoc.id == linkId
        lnkDoc.collection == 'lnk'
        lnkDoc.type_id == typeId
        lnkDoc.left == containerId
        lnkDoc.right == contentId
        ((Map) ((Map) lnkDoc.properties)?.position)?.kind == 'plate_well'
        ((Map) ((Map) lnkDoc.properties)?.position)?.label == 'A1'
        ((Map) ((Map) lnkDoc.properties)?.position)?.row == 'A'
        ((Map) ((Map) lnkDoc.properties)?.position)?.column == 1
        ((Map) ((Map) lnkDoc._head)?.provenance)?.txn_id == txnId
        ((Map) ((Map) lnkDoc._head)?.provenance)?.materialized_at != null

        when: 'the forward HTTP route is exercised against the container id'
        WebTestClient.ResponseSpec forwardResponse = webTestClient.get()
                .uri('/api/contents/by-container/{id}', containerId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the route returns one ContentsLinkRecord with the expected fields'
        forwardResponse.expectStatus().isOk()
                .expectBody()
                .jsonPath('$.length()').isEqualTo(1)
                .jsonPath('$[0].linkId').isEqualTo(linkId)
                .jsonPath('$[0].typeId').isEqualTo(typeId)
                .jsonPath('$[0].left').isEqualTo(containerId)
                .jsonPath('$[0].right').isEqualTo(contentId)
                .jsonPath('$[0].properties.position.kind').isEqualTo('plate_well')
                .jsonPath('$[0].properties.position.label').isEqualTo('A1')
                .jsonPath('$[0].properties.position.row').isEqualTo('A')
                .jsonPath('$[0].properties.position.column').isEqualTo(1)
                .jsonPath('$[0].provenance.txn_id').isEqualTo(txnId)
                .jsonPath('$[0].provenance.commit_id').exists()
                .jsonPath('$[0].provenance.msg_uuid').isEqualTo(lnkMsg.uuid())

        when: 'the reverse HTTP route is exercised against the unresolved content id'
        WebTestClient.ResponseSpec reverseResponse = webTestClient.get()
                .uri('/api/contents/by-content/{id}', contentId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the reverse route returns the same materialized link'
        reverseResponse.expectStatus().isOk()
                .expectBody()
                .jsonPath('$.length()').isEqualTo(1)
                .jsonPath('$[0].linkId').isEqualTo(linkId)
                .jsonPath('$[0].typeId').isEqualTo(typeId)
                .jsonPath('$[0].left').isEqualTo(containerId)
                .jsonPath('$[0].right').isEqualTo(contentId)
                .jsonPath('$[0].properties.position.kind').isEqualTo('plate_well')
                .jsonPath('$[0].properties.position.label').isEqualTo('A1')
                .jsonPath('$[0].properties.position.row').isEqualTo('A')
                .jsonPath('$[0].properties.position.column').isEqualTo(1)
                .jsonPath('$[0].provenance.txn_id').isEqualTo(txnId)
                .jsonPath('$[0].provenance.commit_id').exists()
                .jsonPath('$[0].provenance.msg_uuid').isEqualTo(lnkMsg.uuid())
    }

    def 'empty-result contents HTTP routes return 200 with an empty array'() {
        given: 'a fresh container/content id that has never been materialized'
        String emptyEndpointId = "jadetipi-itest-contents~loc~empty_${UUID.randomUUID().toString().substring(0, 8)}"
        String token = KeycloakTestHelper.getAccessToken()

        when: 'the forward route is exercised against the empty id'
        WebTestClient.ResponseSpec forwardResponse = webTestClient.get()
                .uri('/api/contents/by-container/{id}', emptyEndpointId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the route returns HTTP 200 with []'
        forwardResponse.expectStatus().isOk()
                .expectBody()
                .json('[]')

        when: 'the reverse route is exercised against the empty id'
        WebTestClient.ResponseSpec reverseResponse = webTestClient.get()
                .uri('/api/contents/by-content/{id}', emptyEndpointId)
                .header('Authorization', "Bearer ${token}")
                .exchange()

        then: 'the reverse route also returns HTTP 200 with []'
        reverseResponse.expectStatus().isOk()
                .expectBody()
                .json('[]')
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
