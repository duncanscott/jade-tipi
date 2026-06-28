/**
 * Part of Jade-Tipi -- an open scientific metadata framework.
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
import org.jadetipi.dto.collections.Group
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
 * TASK-036 review seed: publish a small Clarity/ESP container/sample fixture
 * through Kafka and intentionally leave the materialized roots in MongoDB for
 * manual JSON inspection.
 *
 * <p>This is not part of the normal integration suite. It requires both
 * {@code JADETIPI_IT_KAFKA} and {@code JADETIPI_REVIEW_SEED} so ordinary Kafka
 * verification does not modify the review fixture. Before publishing, it
 * deletes only the stable seed roots and the stable seed transaction WAL rows;
 * after a successful run it performs no cleanup of the materialized roots.
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 JADETIPI_REVIEW_SEED=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*ClarityEspContainerReviewSeedKafkaIntegrationSpec*'
 * docker compose -f docker/docker-compose.yml --profile tools up -d
 * </pre>
 *
 * <p>Then inspect database {@code jdtp} (or
 * {@code JADETIPI_REVIEW_SEED_MONGO_DATABASE}, when set) collections
 * {@code loc}, {@code lnk}, {@code ent}, and {@code typ} in Mongo Express at
 * {@code http://localhost:8081}. The review roots all start with
 * {@code jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e501}.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !ClarityEspContainerReviewSeedKafkaIntegrationSpec.reviewSeedGateOpen() })
class ClarityEspContainerReviewSeedKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String MONGO_DATABASE =
            System.getenv('JADETIPI_REVIEW_SEED_MONGO_DATABASE') ?: 'jdtp'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-review-container-seed-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-review-container-seed-${SHORT_UUID}"

    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LNK_COLLECTION = 'lnk'
    private static final String ENT_COLLECTION = 'ent'

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    private static final String REVIEW_TXN_UUID = '018fd849-c0c0-7000-8a01-c1a141e5e501'
    private static final String ID_PREFIX = "jade-tipi-org~dev~${REVIEW_TXN_UUID}"

    private static final String LOC_FREEZER_ID = "${ID_PREFIX}~loc~esp_freezer_019a3a62-8fa8"
    private static final String LOC_BIN_ID = "${ID_PREFIX}~loc~esp_bin_019a3a60-9628"
    private static final String LOC_PLATE_ID = "${ID_PREFIX}~loc~esp_plate_019a420c-728d"
    private static final String LOC_TUBE_ID = "${ID_PREFIX}~loc~clarity_tube_27-10000"
    private static final String TYP_CONTENTS_ID = "${ID_PREFIX}~typ~contents"
    private static final String TYP_LIBRARY_ID = "${ID_PREFIX}~typ~illumina_library"
    private static final String ENT_LIBRARY_ID = "${ID_PREFIX}~ent~esp_library_lhcpot"
    private static final String LNK_FREEZER_BIN_ID =
            "${ID_PREFIX}~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2"
    private static final String LNK_BIN_PLATE_ID =
            "${ID_PREFIX}~lnk~contents_bin_pp050_to_plate_27-474501_a1"
    private static final String LNK_PLATE_LIBRARY_ID =
            "${ID_PREFIX}~lnk~contents_plate_27-474501_to_library_lhcpot_a2"

    private static final String MSG_OPEN = '018fd849-c0c0-7100-8a01-bbbbbbbb0001'
    private static final String MSG_TYP_CONTENTS = '018fd849-c0c0-7100-8a01-bbbbbbbb0002'
    private static final String MSG_TYP_LIBRARY = '018fd849-c0c0-7100-8a01-bbbbbbbb0003'
    private static final String MSG_LOC_FREEZER = '018fd849-c0c0-7100-8a01-bbbbbbbb0004'
    private static final String MSG_LOC_BIN = '018fd849-c0c0-7100-8a01-bbbbbbbb0005'
    private static final String MSG_LOC_PLATE = '018fd849-c0c0-7100-8a01-bbbbbbbb0006'
    private static final String MSG_LOC_TUBE = '018fd849-c0c0-7100-8a01-bbbbbbbb0007'
    private static final String MSG_ENT_LIBRARY = '018fd849-c0c0-7100-8a01-bbbbbbbb0008'
    private static final String MSG_LNK_FREEZER_BIN = '018fd849-c0c0-7100-8a01-bbbbbbbb0009'
    private static final String MSG_LNK_BIN_PLATE = '018fd849-c0c0-7100-8a01-bbbbbbbb0010'
    private static final String MSG_LNK_PLATE_LIBRARY = '018fd849-c0c0-7100-8a01-bbbbbbbb0011'
    private static final String MSG_COMMIT = '018fd849-c0c0-7100-8a01-bbbbbbbb0012'

    static boolean reviewSeedGateOpen() {
        String kafkaFlag = System.getenv('JADETIPI_IT_KAFKA')
        String seedFlag = System.getenv('JADETIPI_REVIEW_SEED')
        if (!(kafkaFlag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        if (!(seedFlag in ['1', 'true', 'TRUE', 'yes'])) {
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
        registry.add('spring.data.mongodb.database', { MONGO_DATABASE })
        ensureTestTopic()
    }

    private static void ensureTestTopic() {
        Properties props = new Properties()
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
        try (AdminClient admin = AdminClient.create(props)) {
            try {
                admin.createTopics([new NewTopic(TEST_TOPIC, 1, (short) 1)])
                        .all().get(15, TimeUnit.SECONDS)
                log.info('Created Kafka review seed topic {}', TEST_TOPIC)
            } catch (ExecutionException ex) {
                if (!(ex.cause instanceof TopicExistsException)) {
                    throw ex
                }
                log.info('Kafka review seed topic {} already exists, reusing', TEST_TOPIC)
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
                log.info('Deleted Kafka review seed topic {}', TEST_TOPIC)
            } catch (Exception ex) {
                log.warn('Failed to delete review seed topic {}: {}', TEST_TOPIC, ex.message)
            }
        }
    }

    def setup() {
        txn = new Transaction(REVIEW_TXN_UUID,
                new Group('jade-tipi-org', 'dev'),
                'review-seed',
                'direct-codex')
        txnId = txn.id
        resetSeedRows()
    }

    def 'publishes the review container and sample seed through Kafka and leaves root documents in MongoDB'() {
        given: 'one stable review seed transaction'
        List<Message> messages = [
                message(MSG_OPEN, JtpCollection.TRANSACTION, Action.OPEN, [
                        seed_id    : 'TASK-036',
                        description: 'TASK-036 Kafka container review seed'
                ]),
                message(MSG_TYP_CONTENTS, JtpCollection.TYPE, Action.CREATE, [
                        kind                     : 'link_type',
                        id                       : TYP_CONTENTS_ID,
                        name                     : 'contents',
                        description              : 'containment relationship between a container location and its contents',
                        left_role                : 'container',
                        right_role               : 'content',
                        left_to_right_label      : 'contains',
                        right_to_left_label      : 'contained_by',
                        allowed_left_collections : ['loc'],
                        allowed_right_collections: ['loc', 'ent'],
                        assignable_properties    : ['position']
                ] as Map<String, Object>),
                message(MSG_TYP_LIBRARY, JtpCollection.TYPE, Action.CREATE, [
                        id              : TYP_LIBRARY_ID,
                        name            : 'illumina_library',
                        description     : 'ESP Illumina Library sample entity',
                        source_system   : 'esp-entity',
                        source_type_id  : '019a3a49-4aa2-72f7-889e-17f1c58e2224',
                        source_type_name: 'Illumina Library'
                ] as Map<String, Object>),
                message(MSG_LOC_FREEZER, JtpCollection.LOCATION, Action.CREATE, [
                        id            : LOC_FREEZER_ID,
                        name          : 'Illumina 130-32',
                        kind          : 'Freezer (6-shelf)',
                        barcode       : 'FREEZE012',
                        source_system : 'esp-entity',
                        source_id     : '019a3a62-8fa8-74d8-ad5c-c7f294c9a331',
                        source_type_id: '019a3a49-6dd8-7dcc-af68-130207d9a1de'
                ] as Map<String, Object>),
                message(MSG_LOC_BIN, JtpCollection.LOCATION, Action.CREATE, [
                        id               : LOC_BIN_ID,
                        name             : 'PP050',
                        kind             : 'Bin 9x3',
                        barcode          : 'BIN057',
                        source_system    : 'esp-entity',
                        source_id        : '019a3a60-9628-7c90-bc47-f40518a12127',
                        source_type_id   : '019a3a49-3672-73ec-842d-6c21c5ad9be7',
                        source_numeric_id: 50
                ] as Map<String, Object>),
                message(MSG_LOC_PLATE, JtpCollection.LOCATION, Action.CREATE, [
                        id               : LOC_PLATE_ID,
                        name             : '27-474501',
                        kind             : '96W Plate',
                        barcode          : '27-474501',
                        format           : '96-well',
                        rows             : 8,
                        columns          : 12,
                        source_system    : 'esp-entity',
                        source_id        : '019a420c-728d-7f4c-a817-cd8ba13a1e36',
                        source_type_id   : '019a3ac2-b494-71ac-82cc-fadc028be18f',
                        source_numeric_id: 474501
                ] as Map<String, Object>),
                message(MSG_LOC_TUBE, JtpCollection.LOCATION, Action.CREATE, [
                        id           : LOC_TUBE_ID,
                        name         : '27-170230',
                        kind         : 'Tube',
                        source_system: 'clarity',
                        source_id    : '27-10000',
                        source_state : 'Populated'
                ] as Map<String, Object>),
                message(MSG_ENT_LIBRARY, JtpCollection.ENTITY, Action.CREATE, [
                        id        : ENT_LIBRARY_ID,
                        type_id   : TYP_LIBRARY_ID,
                        properties: [
                                name            : 'LHCPOT',
                                barcode         : '27-474501',
                                source_system   : 'esp-entity',
                                source_id       : '019a420b-a021-7332-a375-e348af611ac8',
                                source_type_id  : '019a3a49-4aa2-72f7-889e-17f1c58e2224',
                                source_type_name: 'Illumina Library'
                        ],
                        links     : [:]
                ] as Map<String, Object>),
                message(MSG_LNK_FREEZER_BIN, JtpCollection.LINK, Action.CREATE, [
                        id        : LNK_FREEZER_BIN_ID,
                        type_id   : TYP_CONTENTS_ID,
                        left      : LOC_FREEZER_ID,
                        right     : LOC_BIN_ID,
                        properties: [
                                position: [kind: 'freezer_slot', label: '2', slot: 2]
                        ]
                ] as Map<String, Object>),
                message(MSG_LNK_BIN_PLATE, JtpCollection.LINK, Action.CREATE, [
                        id        : LNK_BIN_PLATE_ID,
                        type_id   : TYP_CONTENTS_ID,
                        left      : LOC_BIN_ID,
                        right     : LOC_PLATE_ID,
                        properties: [
                                position: [kind: 'bin_slot', label: 'A1', row: 'A', column: 1]
                        ]
                ] as Map<String, Object>),
                message(MSG_LNK_PLATE_LIBRARY, JtpCollection.LINK, Action.CREATE, [
                        id        : LNK_PLATE_LIBRARY_ID,
                        type_id   : TYP_CONTENTS_ID,
                        left      : LOC_PLATE_ID,
                        right     : ENT_LIBRARY_ID,
                        properties: [
                                position: [kind: 'plate_well', label: 'A2', row: 'A', column: 2]
                        ]
                ] as Map<String, Object>),
                message(MSG_COMMIT, JtpCollection.TRANSACTION, Action.COMMIT, [
                        seed_id: 'TASK-036',
                        comment: 'TASK-036 Kafka container review seed committed'
                ])
        ]

        when: 'the transaction messages are produced to Kafka in order'
        messages.each { Message msg -> send(msg) }

        then: 'the stable transaction header reaches committed state'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed review seed transaction header'
        )
        header['_id'] == txnId
        header.record_type == 'transaction'
        header.open_data.seed_id == 'TASK-036'
        header.commit_data.seed_id == 'TASK-036'

        and: 'the representative container roots are present for JSON review'
        Map freezer = awaitDocument(LOC_FREEZER_ID, LOC_COLLECTION)
        Map bin = awaitDocument(LOC_BIN_ID, LOC_COLLECTION)
        Map plate = awaitDocument(LOC_PLATE_ID, LOC_COLLECTION)
        Map tube = awaitDocument(LOC_TUBE_ID, LOC_COLLECTION)
        [freezer, bin, plate, tube]*.collection == ['loc', 'loc', 'loc', 'loc']
        ((Map) freezer.properties).source_system == 'esp-entity'
        ((Map) freezer.properties).name == 'Illumina 130-32'
        ((Map) bin.properties).barcode == 'BIN057'
        ((Map) plate.properties).format == '96-well'
        ((Map) tube.properties).source_system == 'clarity'
        ((Map) tube.properties).source_id == '27-10000'

        and: 'the sample entity root is present with its entity type'
        Map libraryType = awaitDocument(TYP_LIBRARY_ID, TYP_COLLECTION)
        Map library = awaitDocument(ENT_LIBRARY_ID, ENT_COLLECTION)
        libraryType.collection == 'typ'
        ((Map) libraryType.properties).name == 'illumina_library'
        library.collection == 'ent'
        library.type_id == TYP_LIBRARY_ID
        ((Map) library.properties).name == 'LHCPOT'
        ((Map) library.properties).source_id == '019a420b-a021-7332-a375-e348af611ac8'

        and: 'the contents link type and three containment links are present'
        Map contentsType = awaitDocument(TYP_CONTENTS_ID, TYP_COLLECTION)
        Map freezerToBin = awaitDocument(LNK_FREEZER_BIN_ID, LNK_COLLECTION)
        Map binToPlate = awaitDocument(LNK_BIN_PLATE_ID, LNK_COLLECTION)
        Map plateToLibrary = awaitDocument(LNK_PLATE_LIBRARY_ID, LNK_COLLECTION)
        ((Map) contentsType.properties).kind == 'link_type'
        ((Map) contentsType.properties).name == 'contents'
        freezerToBin.left == LOC_FREEZER_ID
        freezerToBin.right == LOC_BIN_ID
        ((Map) ((Map) freezerToBin.properties).position).label == '2'
        binToPlate.left == LOC_BIN_ID
        binToPlate.right == LOC_PLATE_ID
        ((Map) ((Map) binToPlate.properties).position).label == 'A1'
        plateToLibrary.left == LOC_PLATE_ID
        plateToLibrary.right == ENT_LIBRARY_ID
        ((Map) ((Map) plateToLibrary.properties).position).kind == 'plate_well'
        ((Map) ((Map) plateToLibrary.properties).position).label == 'A2'

        and: 'projection provenance confirms the roots came from this Kafka-backed transaction'
        ((Map) ((Map) freezer._head).provenance).txn_id == txnId
        ((Map) ((Map) library._head).provenance).msg_uuid == MSG_ENT_LIBRARY
        ((Map) ((Map) plateToLibrary._head).provenance).msg_uuid == MSG_LNK_PLATE_LIBRARY
    }

    private Message message(String uuid,
                            JtpCollection collection,
                            Action action,
                            Map<String, Object> data) {
        return new Message(txn, uuid, collection, action, data)
    }

    private void send(Message message) {
        byte[] bytes = JsonMapper.toBytes(message)
        ProducerRecord<String, byte[]> record =
                new ProducerRecord<String, byte[]>(TEST_TOPIC, txnId, bytes)
        producer.send(record).get(10, TimeUnit.SECONDS)
        producer.flush()
    }

    private Map awaitDocument(String id, String collection) {
        return awaitMongo(
                { mongoTemplate.findById(id, Map, collection) },
                { Map d -> d != null },
                "${collection} root ${id}"
        )
    }

    private void resetSeedRows() {
        mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                TXN_COLLECTION).block(Duration.ofSeconds(10))
        [
                (LOC_COLLECTION): [LOC_FREEZER_ID, LOC_BIN_ID, LOC_PLATE_ID, LOC_TUBE_ID],
                (TYP_COLLECTION): [TYP_CONTENTS_ID, TYP_LIBRARY_ID],
                (ENT_COLLECTION): [ENT_LIBRARY_ID],
                (LNK_COLLECTION): [LNK_FREEZER_BIN_ID, LNK_BIN_PLATE_ID, LNK_PLATE_LIBRARY_ID]
        ].each { String collection, List<String> ids ->
            ids.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
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
