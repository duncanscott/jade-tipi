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
package org.jadetipi.jadetipi.importer

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
import org.jadetipi.jadetipi.JadetipiApplication
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
import java.util.regex.Pattern

/**
 * TASK-042 typed review seed (supersedes the TASK-036 name-keyed seed):
 * publish the representative Clarity/ESP container/sample fixture through
 * Kafka in the intended target shape and intentionally leave the
 * materialized roots in MongoDB for manual JSON inspection.
 *
 * <p>Target shape (drift note + ratified inheritance direction):
 * <ul>
 *   <li>The source {@code kind} strings become a type hierarchy:
 *       {@code container} with subtypes {@code freezer}, {@code bin},
 *       {@code plate}, {@code tube}; {@code plate_96_well} extends
 *       {@code plate}. Instances stay in {@code loc}.</li>
 *   <li>Domain fields are {@code ppy} definitions and typed values:
 *       {@code name}/{@code barcode} registered on {@code container},
 *       {@code format}/{@code rows}/{@code columns} on {@code plate};
 *       every assignment lands via inherited registration on the object
 *       root under {@code property_values.<ppy_id>} with transaction
 *       provenance.</li>
 *   <li>Source-system traceability facts stay in the inline
 *       {@code properties} bag ({@code source_system}, {@code source_id},
 *       {@code source_type_id}, {@code source_numeric_id},
 *       {@code source_state}, {@code source_type_name}, and the verbatim
 *       source type label as {@code source_kind}).</li>
 *   <li>The transaction is system-authored: {@code txn.user} carries the
 *       TASK-039 bootstrap identity {@code ...~usr~jdtp-admin}.</li>
 * </ul>
 *
 * <p>This is not part of the normal integration suite. It requires both
 * {@code JADETIPI_IT_KAFKA} and {@code JADETIPI_REVIEW_SEED} so ordinary
 * Kafka verification does not modify the review fixture. Before publishing,
 * it deletes every row whose {@code _id} starts with the stable seed prefix
 * (plus the seed transaction WAL rows); after a successful run it performs
 * no cleanup of the materialized roots.
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
 * {@code loc}, {@code lnk}, {@code ent}, {@code typ}, and {@code ppy} in
 * Mongo Express at {@code http://localhost:8081}. The review roots all
 * start with {@code 018fd849-c0c0-7000-8a01-c1a141e5e501~jade-tipi-org~dev}.
 */
@Slf4j
@SpringBootTest(classes = JadetipiApplication)
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
    private static final String PPY_COLLECTION = 'ppy'

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    private static final String REVIEW_TXN_UUID = '018fd849-c0c0-7000-8a01-c1a141e5e501'
    private static final String ID_PREFIX = "jade-tipi-org~dev~${REVIEW_TXN_UUID}"

    /** TASK-039 bootstrap identity: the seed is a system-authored transaction. */
    private static final String SEED_WRITER_USER = 'genesis~jade-tipi-org~dev~usr~jdtp-admin'

    private static final String PPY_NAME_ID = "${ID_PREFIX}~ppy~name"
    private static final String PPY_BARCODE_ID = "${ID_PREFIX}~ppy~barcode"
    private static final String PPY_FORMAT_ID = "${ID_PREFIX}~ppy~format"
    private static final String PPY_ROWS_ID = "${ID_PREFIX}~ppy~rows"
    private static final String PPY_COLUMNS_ID = "${ID_PREFIX}~ppy~columns"

    private static final String TYP_CONTAINER_ID = "${ID_PREFIX}~typ~container"
    private static final String TYP_FREEZER_ID = "${ID_PREFIX}~typ~freezer"
    private static final String TYP_BIN_ID = "${ID_PREFIX}~typ~bin"
    private static final String TYP_PLATE_ID = "${ID_PREFIX}~typ~plate"
    private static final String TYP_PLATE96_ID = "${ID_PREFIX}~typ~plate_96_well"
    private static final String TYP_TUBE_ID = "${ID_PREFIX}~typ~tube"
    private static final String TYP_CONTENTS_ID = "${ID_PREFIX}~typ~contents"
    private static final String TYP_LIBRARY_ID = "${ID_PREFIX}~typ~illumina_library"

    private static final String LOC_FREEZER_ID = "${ID_PREFIX}~loc~esp_freezer_019a3a62-8fa8"
    private static final String LOC_BIN_ID = "${ID_PREFIX}~loc~esp_bin_019a3a60-9628"
    private static final String LOC_PLATE_ID = "${ID_PREFIX}~loc~esp_plate_019a420c-728d"
    private static final String LOC_TUBE_ID = "${ID_PREFIX}~loc~clarity_tube_27-10000"
    private static final String ENT_LIBRARY_ID = "${ID_PREFIX}~ent~esp_library_lhcpot"
    private static final String LNK_FREEZER_BIN_ID =
            "${ID_PREFIX}~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2"
    private static final String LNK_BIN_PLATE_ID =
            "${ID_PREFIX}~lnk~contents_bin_pp050_to_plate_27-474501_a1"
    private static final String LNK_PLATE_LIBRARY_ID =
            "${ID_PREFIX}~lnk~contents_plate_27-474501_to_library_lhcpot_a2"

    /**
     * Stable message UUIDs. The materializer processes messages in
     * {@code _id} (msg uuid) order, so the numbering encodes the required
     * ordering: definitions and types before registrations, roots before
     * assignments.
     */
    private static String msgUuid(int sequence) {
        return String.format('018fd849-c0c0-7100-8a01-bbbbbbbb%04d', sequence)
    }

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
                SEED_WRITER_USER)
        txnId = txn.id
        resetSeedRows()
    }

    def 'publishes the typed review container and sample seed through Kafka and leaves root documents in MongoDB'() {
        given: 'one stable typed review seed transaction'
        List<Message> messages = []
        int seq = 0

        messages << message(msgUuid(++seq), JtpCollection.TRANSACTION, Action.OPEN, [
                seed_id    : 'TASK-042',
                description: 'TASK-042 typed Kafka container review seed'
        ])

        and: 'property definitions for the domain container fields'
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE,
                textPropertyDefinition(PPY_NAME_ID, 'name'))
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE,
                textPropertyDefinition(PPY_BARCODE_ID, 'barcode'))
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE,
                textPropertyDefinition(PPY_FORMAT_ID, 'format'))
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE,
                numberPropertyDefinition(PPY_ROWS_ID, 'rows'))
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE,
                numberPropertyDefinition(PPY_COLUMNS_ID, 'columns'))

        and: 'the container type hierarchy with inherited registrations'
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id         : TYP_CONTAINER_ID,
                name       : 'container',
                description: 'generic physical container'
        ] as Map<String, Object>)
        messages << addProperty(msgUuid(++seq), TYP_CONTAINER_ID, PPY_NAME_ID)
        messages << addProperty(msgUuid(++seq), TYP_CONTAINER_ID, PPY_BARCODE_ID)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id            : TYP_FREEZER_ID,
                name          : 'freezer',
                parent_type_id: TYP_CONTAINER_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id            : TYP_BIN_ID,
                name          : 'bin',
                parent_type_id: TYP_CONTAINER_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id            : TYP_PLATE_ID,
                name          : 'plate',
                parent_type_id: TYP_CONTAINER_ID
        ] as Map<String, Object>)
        messages << addProperty(msgUuid(++seq), TYP_PLATE_ID, PPY_FORMAT_ID)
        messages << addProperty(msgUuid(++seq), TYP_PLATE_ID, PPY_ROWS_ID)
        messages << addProperty(msgUuid(++seq), TYP_PLATE_ID, PPY_COLUMNS_ID)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id            : TYP_PLATE96_ID,
                name          : 'plate_96_well',
                description   : '96-well sample plate',
                parent_type_id: TYP_PLATE_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id            : TYP_TUBE_ID,
                name          : 'tube',
                parent_type_id: TYP_CONTAINER_ID
        ] as Map<String, Object>)

        and: 'the contents link type and the typed sample entity type'
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : TYP_CONTENTS_ID,
                name                     : 'contents',
                description              : 'containment relationship between a container location and its contents',
                left_role                : 'container',
                right_role               : 'content',
                left_to_right_label     : 'contains',
                right_to_left_label     : 'contained_by',
                allowed_left_collections : ['loc'],
                allowed_right_collections: ['loc', 'ent'],
                assignable_properties    : ['position']
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id              : TYP_LIBRARY_ID,
                name            : 'illumina_library',
                description     : 'ESP Illumina Library sample entity',
                source_system   : 'esp-entity',
                source_type_id  : '019a3a49-4aa2-72f7-889e-17f1c58e2224',
                source_type_name: 'Illumina Library'
        ] as Map<String, Object>)
        messages << addProperty(msgUuid(++seq), TYP_LIBRARY_ID, PPY_NAME_ID)
        messages << addProperty(msgUuid(++seq), TYP_LIBRARY_ID, PPY_BARCODE_ID)

        and: 'typed object roots whose inline properties carry source facts only'
        messages << message(msgUuid(++seq), JtpCollection.LOCATION, Action.CREATE, [
                id        : LOC_FREEZER_ID,
                type_id   : TYP_FREEZER_ID,
                properties: [
                        source_kind   : 'Freezer (6-shelf)',
                        source_system : 'esp-entity',
                        source_id     : '019a3a62-8fa8-74d8-ad5c-c7f294c9a331',
                        source_type_id: '019a3a49-6dd8-7dcc-af68-130207d9a1de'
                ],
                links     : [:]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.LOCATION, Action.CREATE, [
                id        : LOC_BIN_ID,
                type_id   : TYP_BIN_ID,
                properties: [
                        source_kind      : 'Bin 9x3',
                        source_system    : 'esp-entity',
                        source_id        : '019a3a60-9628-7c90-bc47-f40518a12127',
                        source_type_id   : '019a3a49-3672-73ec-842d-6c21c5ad9be7',
                        source_numeric_id: 50
                ],
                links     : [:]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.LOCATION, Action.CREATE, [
                id        : LOC_PLATE_ID,
                type_id   : TYP_PLATE96_ID,
                properties: [
                        source_kind      : '96W Plate',
                        source_system    : 'esp-entity',
                        source_id        : '019a420c-728d-7f4c-a817-cd8ba13a1e36',
                        source_type_id   : '019a3ac2-b494-71ac-82cc-fadc028be18f',
                        source_numeric_id: 474501
                ],
                links     : [:]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.LOCATION, Action.CREATE, [
                id        : LOC_TUBE_ID,
                type_id   : TYP_TUBE_ID,
                properties: [
                        source_kind  : 'Tube',
                        source_system: 'clarity',
                        source_id    : '27-10000',
                        source_state : 'Populated'
                ],
                links     : [:]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.ENTITY, Action.CREATE, [
                id        : ENT_LIBRARY_ID,
                type_id   : TYP_LIBRARY_ID,
                properties: [
                        source_system   : 'esp-entity',
                        source_id       : '019a420b-a021-7332-a375-e348af611ac8',
                        source_type_id  : '019a3a49-4aa2-72f7-889e-17f1c58e2224',
                        source_type_name: 'Illumina Library'
                ],
                links     : [:]
        ] as Map<String, Object>)

        and: 'object-targeted assignments for every domain value'
        messages << assignLoc(msgUuid(++seq), LOC_FREEZER_ID, PPY_NAME_ID, [text: 'Illumina 130-32'])
        messages << assignLoc(msgUuid(++seq), LOC_FREEZER_ID, PPY_BARCODE_ID, [text: 'FREEZE012'])
        messages << assignLoc(msgUuid(++seq), LOC_BIN_ID, PPY_NAME_ID, [text: 'PP050'])
        messages << assignLoc(msgUuid(++seq), LOC_BIN_ID, PPY_BARCODE_ID, [text: 'BIN057'])
        messages << assignLoc(msgUuid(++seq), LOC_PLATE_ID, PPY_NAME_ID, [text: '27-474501'])
        messages << assignLoc(msgUuid(++seq), LOC_PLATE_ID, PPY_BARCODE_ID, [text: '27-474501'])
        messages << assignLoc(msgUuid(++seq), LOC_PLATE_ID, PPY_FORMAT_ID, [text: '96-well'])
        messages << assignLoc(msgUuid(++seq), LOC_PLATE_ID, PPY_ROWS_ID, [number: 8])
        messages << assignLoc(msgUuid(++seq), LOC_PLATE_ID, PPY_COLUMNS_ID, [number: 12])
        messages << assignLoc(msgUuid(++seq), LOC_TUBE_ID, PPY_NAME_ID, [text: '27-170230'])
        messages << assignEnt(msgUuid(++seq), ENT_LIBRARY_ID, PPY_NAME_ID, [text: 'LHCPOT'])
        messages << assignEnt(msgUuid(++seq), ENT_LIBRARY_ID, PPY_BARCODE_ID, [text: '27-474501'])

        and: 'the containment links'
        messages << message(msgUuid(++seq), JtpCollection.LINK, Action.CREATE, [
                id        : LNK_FREEZER_BIN_ID,
                type_id   : TYP_CONTENTS_ID,
                left      : LOC_FREEZER_ID,
                right     : LOC_BIN_ID,
                properties: [
                        position: [kind: 'freezer_slot', label: '2', slot: 2]
                ]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.LINK, Action.CREATE, [
                id        : LNK_BIN_PLATE_ID,
                type_id   : TYP_CONTENTS_ID,
                left      : LOC_BIN_ID,
                right     : LOC_PLATE_ID,
                properties: [
                        position: [kind: 'bin_slot', label: 'A1', row: 'A', column: 1]
                ]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.LINK, Action.CREATE, [
                id        : LNK_PLATE_LIBRARY_ID,
                type_id   : TYP_CONTENTS_ID,
                left      : LOC_PLATE_ID,
                right     : ENT_LIBRARY_ID,
                properties: [
                        position: [kind: 'plate_well', label: 'A2', row: 'A', column: 2]
                ]
        ] as Map<String, Object>)

        messages << message(msgUuid(++seq), JtpCollection.TRANSACTION, Action.COMMIT, [
                seed_id: 'TASK-042',
                comment: 'TASK-042 typed Kafka container review seed committed'
        ])

        when: 'the transaction messages are produced to Kafka in order'
        messages.each { Message msg -> send(msg) }

        then: 'the stable transaction header reaches committed state with the system writer in the WAL rows'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed review seed transaction header'
        )
        header['_id'] == txnId
        header.record_type == 'transaction'
        header.open_data.seed_id == 'TASK-042'
        header.commit_data.seed_id == 'TASK-042'

        and: 'the type hierarchy materializes with parent_type_id and inherited registrations'
        Map containerType = awaitDocument(TYP_CONTAINER_ID, TYP_COLLECTION)
        Map freezerType = awaitDocument(TYP_FREEZER_ID, TYP_COLLECTION)
        Map plateType = awaitDocument(TYP_PLATE_ID, TYP_COLLECTION)
        Map plate96Type = awaitDocument(TYP_PLATE96_ID, TYP_COLLECTION)
        Map tubeType = awaitDocument(TYP_TUBE_ID, TYP_COLLECTION)
        ((Map) ((Map) containerType.properties).property_refs).keySet() ==
                [PPY_NAME_ID, PPY_BARCODE_ID] as Set
        ((Map) freezerType.properties).parent_type_id == TYP_CONTAINER_ID
        ((Map) plateType.properties).parent_type_id == TYP_CONTAINER_ID
        ((Map) ((Map) plateType.properties).property_refs).keySet() ==
                [PPY_FORMAT_ID, PPY_ROWS_ID, PPY_COLUMNS_ID] as Set
        ((Map) plate96Type.properties).parent_type_id == TYP_PLATE_ID
        ((Map) tubeType.properties).parent_type_id == TYP_CONTAINER_ID

        and: 'the plate loc root is typed with every domain value projected through inherited registrations'
        Map plate = awaitMongo(
                { mongoTemplate.findById(LOC_PLATE_ID, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.size() == 5 },
                'typed plate loc root with five projected values'
        )
        plate.type_id == TYP_PLATE96_ID
        Map plateValues = plate.property_values as Map
        ((Map) plateValues[PPY_NAME_ID]).value == [text: '27-474501']
        ((Map) plateValues[PPY_BARCODE_ID]).value == [text: '27-474501']
        ((Map) plateValues[PPY_FORMAT_ID]).value == [text: '96-well']
        ((Map) plateValues[PPY_ROWS_ID]).value == [number: 8]
        ((Map) plateValues[PPY_COLUMNS_ID]).value == [number: 12]
        ((Map) plateValues[PPY_BARCODE_ID]).txn_id == txnId
        ((Map) plateValues[PPY_BARCODE_ID]).commit_id == header.commit_id
        ((Map) plateValues[PPY_BARCODE_ID]).applied_at != null

        and: 'the plate inline bag carries source facts only — no domain fields'
        Map plateBag = plate.properties as Map
        plateBag.source_kind == '96W Plate'
        plateBag.source_system == 'esp-entity'
        plateBag.source_numeric_id == 474501
        !plateBag.containsKey('name')
        !plateBag.containsKey('barcode')
        !plateBag.containsKey('kind')
        !plateBag.containsKey('format')
        !plateBag.containsKey('rows')
        !plateBag.containsKey('columns')

        and: 'the other typed containers project their values; the tube has a name but no barcode'
        Map freezer = awaitDocument(LOC_FREEZER_ID, LOC_COLLECTION)
        Map bin = awaitDocument(LOC_BIN_ID, LOC_COLLECTION)
        Map tube = awaitMongo(
                { mongoTemplate.findById(LOC_TUBE_ID, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(PPY_NAME_ID) },
                'typed tube loc root with projected name'
        )
        freezer.type_id == TYP_FREEZER_ID
        ((Map) ((Map) freezer.property_values)[PPY_NAME_ID]).value == [text: 'Illumina 130-32']
        ((Map) ((Map) freezer.property_values)[PPY_BARCODE_ID]).value == [text: 'FREEZE012']
        bin.type_id == TYP_BIN_ID
        ((Map) ((Map) bin.property_values)[PPY_BARCODE_ID]).value == [text: 'BIN057']
        tube.type_id == TYP_TUBE_ID
        ((Map) ((Map) tube.property_values)[PPY_NAME_ID]).value == [text: '27-170230']
        !((Map) tube.property_values).containsKey(PPY_BARCODE_ID)
        ((Map) tube.properties).source_system == 'clarity'
        ((Map) tube.properties).source_id == '27-10000'

        and: 'the sample entity root is typed with projected name and barcode'
        Map library = awaitMongo(
                { mongoTemplate.findById(ENT_LIBRARY_ID, Map, ENT_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.size() == 2 },
                'typed library ent root with projected values'
        )
        library.type_id == TYP_LIBRARY_ID
        ((Map) ((Map) library.property_values)[PPY_NAME_ID]).value == [text: 'LHCPOT']
        ((Map) ((Map) library.property_values)[PPY_BARCODE_ID]).value == [text: '27-474501']
        !((Map) library.properties).containsKey('name')
        !((Map) library.properties).containsKey('barcode')
        ((Map) library.properties).source_id == '019a420b-a021-7332-a375-e348af611ac8'

        and: 'the contents link type and three containment links are present'
        Map contentsType = awaitDocument(TYP_CONTENTS_ID, TYP_COLLECTION)
        Map freezerToBin = awaitDocument(LNK_FREEZER_BIN_ID, LNK_COLLECTION)
        Map binToPlate = awaitDocument(LNK_BIN_PLATE_ID, LNK_COLLECTION)
        Map plateToLibrary = awaitDocument(LNK_PLATE_LIBRARY_ID, LNK_COLLECTION)
        ((Map) contentsType.properties).kind == 'link_type'
        freezerToBin.left == LOC_FREEZER_ID
        freezerToBin.right == LOC_BIN_ID
        binToPlate.left == LOC_BIN_ID
        binToPlate.right == LOC_PLATE_ID
        plateToLibrary.left == LOC_PLATE_ID
        plateToLibrary.right == ENT_LIBRARY_ID
        ((Map) ((Map) plateToLibrary.properties).position).label == 'A2'

        and: 'projection provenance confirms the roots came from this Kafka-backed transaction'
        ((Map) ((Map) freezer._head).provenance).txn_id == txnId
        ((Map) ((Map) plate._head).provenance).txn_id == txnId
    }

    private static Map<String, Object> textPropertyDefinition(String id, String name) {
        return [
                kind        : 'definition',
                id          : id,
                name        : name,
                value_schema: [
                        type      : 'object',
                        required  : ['text'],
                        properties: [text: [type: 'string']]
                ]
        ] as Map<String, Object>
    }

    private static Map<String, Object> numberPropertyDefinition(String id, String name) {
        return [
                kind        : 'definition',
                id          : id,
                name        : name,
                value_schema: [
                        type      : 'object',
                        required  : ['number'],
                        properties: [number: [type: 'number']]
                ]
        ] as Map<String, Object>
    }

    private Message addProperty(String uuid, String typeId, String propertyId) {
        return message(uuid, JtpCollection.TYPE, Action.UPDATE, [
                id         : typeId,
                operation  : 'add_property',
                property_id: propertyId
        ] as Map<String, Object>)
    }

    private Message assignLoc(String uuid, String objectId, String propertyId, Map value) {
        return objectAssignment(uuid, 'loc', objectId, propertyId, value)
    }

    private Message assignEnt(String uuid, String objectId, String propertyId, Map value) {
        return objectAssignment(uuid, 'ent', objectId, propertyId, value)
    }

    private Message objectAssignment(String uuid,
                                     String objectCollection,
                                     String objectId,
                                     String propertyId,
                                     Map value) {
        return message(uuid, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: objectCollection,
                object_id        : objectId,
                property_id      : propertyId,
                value            : value
        ] as Map<String, Object>)
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

    /**
     * Delete every row whose {@code _id} starts with the stable seed prefix
     * (covering rows written by any prior seed version) plus the seed
     * transaction WAL rows.
     */
    private void resetSeedRows() {
        mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                TXN_COLLECTION).block(Duration.ofSeconds(10))
        String prefixPattern = '^' + Pattern.quote(ID_PREFIX)
        [LOC_COLLECTION, TYP_COLLECTION, ENT_COLLECTION, LNK_COLLECTION, PPY_COLLECTION]
                .each { String collection ->
                    mongoTemplate.remove(
                            Query.query(Criteria.where('_id').regex(prefixPattern)),
                            collection).block(Duration.ofSeconds(10))
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
