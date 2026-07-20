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
 * TASK-043 end-to-end import loop: read the sampled container/sample
 * documents live from the locally replicated {@code clarity} and
 * {@code esp-entity} CouchDB databases, map them onto the typed container
 * model with {@link ClarityEspContainerImportMapper}, publish one
 * system-authored transaction through Kafka, and assert the materialized
 * roots dynamically against the live source values (source → JDTP
 * fidelity, not literals). Rows are left in MongoDB for review, reset by
 * the stable ID prefix per run.
 *
 * <p>Opt-in gates: {@code JADETIPI_IT_KAFKA} and
 * {@code JADETIPI_COUCHDB_IMPORT} must be set, a Kafka broker must be
 * reachable, and the sampled ESP bin document must exist in the local
 * replica (skipped otherwise — the loop needs live source data).
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 JADETIPI_COUCHDB_IMPORT=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*ClarityEspCouchDbImportKafkaIntegrationSpec*'
 * </pre>
 * Then inspect database {@code jdtp}; the imported roots all start with
 * {@code 018fd849-c0c0-7000-8a01-c1a141e5e543~jade-tipi-org~dev}.
 */
@Slf4j
@SpringBootTest(classes = JadetipiApplication)
@ActiveProfiles('test')
@IgnoreIf({ !ClarityEspCouchDbImportKafkaIntegrationSpec.importGateOpen() })
class ClarityEspCouchDbImportKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String COUCHDB_URL =
            System.getenv('JADETIPI_IMPORT_COUCHDB_URL') ?: 'http://localhost:5984'
    private static final String COUCHDB_USERNAME =
            System.getenv('JADETIPI_IMPORT_COUCHDB_USERNAME') ?: 'admin'
    private static final String COUCHDB_PASSWORD =
            System.getenv('JADETIPI_IMPORT_COUCHDB_PASSWORD') ?: 'admin'
    private static final String MONGO_DATABASE =
            System.getenv('JADETIPI_REVIEW_SEED_MONGO_DATABASE') ?: 'jdtp'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-couchdb-import-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-couchdb-import-${SHORT_UUID}"

    private static final String TXN_COLLECTION = 'txn'
    private static final String LOC_COLLECTION = 'loc'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LNK_COLLECTION = 'lnk'
    private static final String ENT_COLLECTION = 'ent'
    private static final String PPY_COLLECTION = 'ppy'

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)
    private static final Duration COUCHDB_READ_TIMEOUT = Duration.ofSeconds(10)

    private static final String IMPORT_TXN_UUID = '018fd849-c0c0-7000-8a01-c1a141e5e543'
    private static final String ID_PREFIX = "jade-tipi-org~dev~${IMPORT_TXN_UUID}"
    private static final String SEED_WRITER_USER = 'genesis~jade-tipi-org~dev~usr~jdtp-admin'

    /** The sampled source documents (see clarity-esp-container-mapping.md). */
    private static final String ESP_DB = 'esp-entity'
    private static final String CLARITY_DB = 'clarity'
    private static final String ESP_FREEZER_UUID = '019a3a62-8fa8-74d8-ad5c-c7f294c9a331'
    private static final String ESP_BIN_UUID = '019a3a60-9628-7c90-bc47-f40518a12127'
    private static final String ESP_PLATE_UUID = '019a420c-728d-7f4c-a817-cd8ba13a1e36'
    private static final String ESP_LIBRARY_UUID = '019a420b-a021-7332-a375-e348af611ac8'
    private static final String CLARITY_TUBE_DOC_ID = 'containers_27-10000'

    private static final String PPY_NAME_ID = "${ID_PREFIX}~ppy~name"
    private static final String PPY_BARCODE_ID = "${ID_PREFIX}~ppy~barcode"
    private static final String TYP_CONTAINER_ID = "${ID_PREFIX}~typ~container"
    private static final String TYP_FREEZER_ID = "${ID_PREFIX}~typ~freezer"
    private static final String TYP_BIN_ID = "${ID_PREFIX}~typ~bin"
    private static final String TYP_PLATE_ID = "${ID_PREFIX}~typ~plate"
    private static final String TYP_PLATE96_ID = "${ID_PREFIX}~typ~plate_96_well"
    private static final String TYP_TUBE_ID = "${ID_PREFIX}~typ~tube"
    private static final String TYP_CONTENTS_ID = "${ID_PREFIX}~typ~contents"
    private static final String TYP_LIBRARY_ID = "${ID_PREFIX}~typ~illumina_library"

    private static String msgUuid(int sequence) {
        return String.format('018fd849-c0c0-7200-8a01-cccccccc%04d', sequence)
    }

    static boolean importGateOpen() {
        String kafkaFlag = System.getenv('JADETIPI_IT_KAFKA')
        String importFlag = System.getenv('JADETIPI_COUCHDB_IMPORT')
        if (!(kafkaFlag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        if (!(importFlag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        return kafkaReachable() && sampledDocumentPresent()
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

    private static boolean sampledDocumentPresent() {
        HttpURLConnection conn = null
        try {
            URL url = new URL("${COUCHDB_URL}/${ESP_DB}/${ESP_BIN_UUID}")
            conn = (HttpURLConnection) url.openConnection()
            String token = Base64.encoder.encodeToString(
                    "${COUCHDB_USERNAME}:${COUCHDB_PASSWORD}".getBytes('UTF-8'))
            conn.setRequestProperty('Authorization', "Basic ${token}")
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = 'GET'
            return conn.responseCode == 200
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
                log.info('Created Kafka import topic {}', TEST_TOPIC)
            } catch (ExecutionException ex) {
                if (!(ex.cause instanceof TopicExistsException)) {
                    throw ex
                }
                log.info('Kafka import topic {} already exists, reusing', TEST_TOPIC)
            }
        }
    }

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    @Autowired
    CouchDbDocumentReader couchDbReader

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction txn
    String txnId
    ClarityEspContainerModel model

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
            } catch (Exception ex) {
                log.warn('Failed to delete import topic {}: {}', TEST_TOPIC, ex.message)
            }
        }
    }

    def setup() {
        txn = new Transaction(IMPORT_TXN_UUID,
                new Group('jade-tipi-org', 'dev'),
                'couchdb-import',
                SEED_WRITER_USER)
        txnId = txn.id
        model = new ClarityEspContainerModel(
                propertyNameId: PPY_NAME_ID,
                propertyBarcodeId: PPY_BARCODE_ID,
                contentsTypeId: TYP_CONTENTS_ID,
                fallbackContainerTypeId: TYP_CONTAINER_ID,
                kindMappings: [
                        'Freezer (6-shelf)': new ClarityEspKindMapping(
                                typeId: TYP_FREEZER_ID, idSlug: 'freezer', positionKind: 'freezer_slot'),
                        'Bin 9x3'          : new ClarityEspKindMapping(
                                typeId: TYP_BIN_ID, idSlug: 'bin', positionKind: 'bin_slot'),
                        '96W Plate'        : new ClarityEspKindMapping(
                                typeId: TYP_PLATE96_ID, idSlug: 'plate', positionKind: 'plate_well'),
                        'Tube'             : new ClarityEspKindMapping(
                                typeId: TYP_TUBE_ID, idSlug: 'tube', positionKind: null),
                        'Illumina Library' : new ClarityEspKindMapping(
                                typeId: TYP_LIBRARY_ID, idSlug: 'library', positionKind: null)
                ])
        resetImportedRows()
    }

    def 'imports the sampled containers live from CouchDB through Kafka and materializes the typed roots'() {
        given: 'the live source documents from the local replicas'
        Map freezerDoc = readCouchDoc(ESP_DB, ESP_FREEZER_UUID)
        Map binDoc = readCouchDoc(ESP_DB, ESP_BIN_UUID)
        Map plateDoc = readCouchDoc(ESP_DB, ESP_PLATE_UUID)
        Map libraryDoc = readCouchDoc(ESP_DB, ESP_LIBRARY_UUID)
        Map tubeDoc = readCouchDoc(CLARITY_DB, CLARITY_TUBE_DOC_ID)

        and: 'the mapped import messages over the typed model'
        List<MappedImportMessage> mapped = []
        mapped.addAll(ClarityEspContainerImportMapper.mapEspDocument(freezerDoc, model, ID_PREFIX))
        mapped.addAll(ClarityEspContainerImportMapper.mapEspDocument(binDoc, model, ID_PREFIX))
        mapped.addAll(ClarityEspContainerImportMapper.mapEspDocument(plateDoc, model, ID_PREFIX))
        mapped.addAll(ClarityEspContainerImportMapper.mapEspDocument(libraryDoc, model, ID_PREFIX))
        mapped.addAll(ClarityEspContainerImportMapper.mapClarityContainerDocument(tubeDoc, model, ID_PREFIX))
        String freezerRootId = "${ID_PREFIX}~loc~esp_freezer_019a3a62-8fa8"
        String binRootId = "${ID_PREFIX}~loc~esp_bin_019a3a60-9628"
        String plateRootId = "${ID_PREFIX}~loc~esp_plate_019a420c-728d"
        String tubeRootId = "${ID_PREFIX}~loc~clarity_tube_27-10000"

        when: 'the model transaction and the mapped messages publish as one system-authored transaction'
        int seq = 0
        List<Message> messages = []
        messages << message(msgUuid(++seq), JtpCollection.TRANSACTION, Action.OPEN, [
                seed_id    : 'TASK-043',
                description: 'TASK-043 CouchDB container import loop'
        ])
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE, [
                kind: 'definition', id: PPY_NAME_ID, name: 'name',
                value_schema: [type: 'object', required: ['text'], properties: [text: [type: 'string']]]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.PROPERTY, Action.CREATE, [
                kind: 'definition', id: PPY_BARCODE_ID, name: 'barcode',
                value_schema: [type: 'object', required: ['text'], properties: [text: [type: 'string']]]
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id: TYP_CONTAINER_ID, name: 'container', description: 'generic physical container'
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.UPDATE, [
                id: TYP_CONTAINER_ID, operation: 'add_property', property_id: PPY_NAME_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.UPDATE, [
                id: TYP_CONTAINER_ID, operation: 'add_property', property_id: PPY_BARCODE_ID
        ] as Map<String, Object>)
        [TYP_FREEZER_ID, TYP_BIN_ID, TYP_PLATE_ID, TYP_TUBE_ID].eachWithIndex { String typeId, int i ->
            messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                    id            : typeId,
                    name          : ['freezer', 'bin', 'plate', 'tube'][i],
                    parent_type_id: TYP_CONTAINER_ID
            ] as Map<String, Object>)
        }
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id: TYP_PLATE96_ID, name: 'plate_96_well', parent_type_id: TYP_PLATE_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                kind: 'link_type', id: TYP_CONTENTS_ID, name: 'contents',
                left_role: 'container', right_role: 'content',
                left_to_right_label: 'contains', right_to_left_label: 'contained_by',
                allowed_left_collections: ['loc'], allowed_right_collections: ['loc', 'ent']
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.CREATE, [
                id: TYP_LIBRARY_ID, name: 'illumina_library',
                description: 'ESP Illumina Library sample entity'
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.UPDATE, [
                id: TYP_LIBRARY_ID, operation: 'add_property', property_id: PPY_NAME_ID
        ] as Map<String, Object>)
        messages << message(msgUuid(++seq), JtpCollection.TYPE, Action.UPDATE, [
                id: TYP_LIBRARY_ID, operation: 'add_property', property_id: PPY_BARCODE_ID
        ] as Map<String, Object>)
        mapped.each { MappedImportMessage m ->
            messages << message(msgUuid(++seq),
                    JtpCollection.fromJson(m.collection),
                    Action.valueOf(m.action.toUpperCase(Locale.ROOT)),
                    m.data)
        }
        messages << message(msgUuid(++seq), JtpCollection.TRANSACTION, Action.COMMIT, [
                seed_id: 'TASK-043',
                comment: 'TASK-043 CouchDB container import committed'
        ])
        messages.each { Message msg -> send(msg) }

        then: 'the transaction commits'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed import transaction header'
        )
        header.open_data.seed_id == 'TASK-043'

        and: 'the imported bin matches the live source document (source -> JDTP fidelity)'
        Map bin = awaitMongo(
                { mongoTemplate.findById(binRootId, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(PPY_NAME_ID) },
                'typed imported bin root'
        )
        bin.type_id == TYP_BIN_ID
        ((Map) ((Map) bin.property_values)[PPY_NAME_ID]).value == [text: binDoc.name]
        ((Map) ((Map) bin.property_values)[PPY_BARCODE_ID]).value == [text: binDoc.barcode]
        ((Map) bin.properties).source_kind == binDoc.type_name
        ((Map) bin.properties).source_id == binDoc.uuid
        !((Map) bin.properties).containsKey('name')

        and: 'the freezer and plate import typed with their live values'
        Map freezer = awaitDocument(freezerRootId, LOC_COLLECTION)
        Map plate = awaitDocument(plateRootId, LOC_COLLECTION)
        freezer.type_id == TYP_FREEZER_ID
        ((Map) ((Map) freezer.property_values)[PPY_NAME_ID]).value == [text: freezerDoc.name]
        plate.type_id == TYP_PLATE96_ID
        ((Map) ((Map) plate.property_values)[PPY_BARCODE_ID]).value == [text: plateDoc.barcode]

        and: 'the Clarity tube imports with its live name and state, and no barcode'
        Map tube = awaitMongo(
                { mongoTemplate.findById(tubeRootId, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(PPY_NAME_ID) },
                'typed imported tube root'
        )
        tube.type_id == TYP_TUBE_ID
        ((Map) ((Map) tube.property_values)[PPY_NAME_ID]).value == [text: ((Map) tubeDoc.json).name]
        !((Map) tube.property_values).containsKey(PPY_BARCODE_ID)
        ((Map) tube.properties).source_state == ((Map) tubeDoc.json).state

        and: 'the library imports as a typed ent root'
        Map library = awaitMongo(
                { mongoTemplate.findById("${ID_PREFIX}~ent~esp_library_lhcpot" as String, Map, ENT_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(PPY_NAME_ID) },
                'typed imported library ent root'
        )
        library.type_id == TYP_LIBRARY_ID
        ((Map) ((Map) library.property_values)[PPY_NAME_ID]).value == [text: libraryDoc.name]

        and: 'containment links derive child-side from each document\'s own container pointer'
        Map freezerToBin = awaitLink(freezerRootId, binRootId)
        Map binToPlate = awaitLink(binRootId, plateRootId)
        Map plateToLibrary = awaitLink(plateRootId, library['_id'] as String)
        ((Map) ((Map) freezerToBin.properties).position).label == ((Map) binDoc.container).slot
        ((Map) ((Map) binToPlate.properties).position).label == ((Map) plateDoc.container).slot
        ((Map) ((Map) plateToLibrary.properties).position).label == ((Map) libraryDoc.container).slot
        ((Map) ((Map) plateToLibrary.properties).position).kind == 'plate_well'

        and: 'projection provenance ties every root to this import transaction'
        ((Map) ((Map) bin._head).provenance).txn_id == txnId
        ((Map) ((Map) tube._head).provenance).txn_id == txnId
    }

    private Map readCouchDoc(String database, String documentId) {
        Map doc = couchDbReader.findDocument(database, documentId).block(COUCHDB_READ_TIMEOUT)
        assert doc != null: "sampled source document missing from local replica: ${database}/${documentId}"
        return doc
    }

    private Map awaitLink(String leftId, String rightId) {
        return awaitMongo(
                {
                    Query query = Query.query(Criteria.where('left').is(leftId).and('right').is(rightId))
                    mongoTemplate.find(query, Map, LNK_COLLECTION).next()
                },
                { Map d -> d != null },
                "contents link ${leftId} -> ${rightId}"
        )
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

    private void resetImportedRows() {
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
