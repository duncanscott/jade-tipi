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
import org.apache.kafka.common.errors.TopicExistsException
import org.jadetipi.jadetipi.JadetipiApplication
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
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * End-to-end proof of the clarity aliquot import against the LIVE clarity
 * CouchDB, driven through the PRODUCTION path (TASK-059 slice, TASK-066
 * trigger): plan process {@code processes_24-35613} into the persistent
 * dependency-ordered queue, then let {@link ClarityImportDriver} drain it —
 * one JDTP transaction over Kafka via the module's own
 * {@link ImportMessagePublisher}, message-UUID-form ids minted at emit and
 * recorded on the rows — and assert the materialized graph: typed
 * containers, Analyte ent with a positioned contents link, ResultFile fil
 * roots, and the prc whose output_input carries the three resolved
 * outputs, with procedure_input and produced_by links. Dependency order
 * satisfies declare-before-use by construction, so the TASK-058 link
 * warnings stay silent.
 *
 * <p>Gated on the docker itest stack (shared {@code JADETIPI_IT_KAFKA}
 * flag) plus Kafka and the clarity CouchDB being reachable. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :importers:jgi-import:integrationTest \
 *     --tests '*ClarityAliquotImportKafkaIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest(classes = JadetipiApplication)
@ActiveProfiles('test')
@IgnoreIf({ !ClarityAliquotImportKafkaIntegrationSpec.integrationGateOpen() })
class ClarityAliquotImportKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String COUCHDB_URL =
            System.getenv('JADETIPI_IMPORT_COUCHDB_URL') ?: 'http://localhost:5984'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-claimport-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-claimport-${SHORT_UUID}"
    private static final String PROCESS_DOC_ID = 'processes_24-35613'
    private static final String TXN_COLLECTION = 'txn'
    private static final String LNK_COLLECTION = 'lnk'
    private static final List<String> ROOT_COLLECTIONS = ['typ', 'loc', 'ent', 'fil', 'prc', 'lnk']
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    static boolean integrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_KAFKA')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        return kafkaReachable() && couchReachable()
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

    private static boolean couchReachable() {
        HttpURLConnection conn = null
        try {
            URL url = new URL("${COUCHDB_URL}/clarity")
            conn = (HttpURLConnection) url.openConnection()
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            String auth = Base64.encoder.encodeToString('admin:admin'.bytes)
            conn.setRequestProperty('Authorization', "Basic ${auth}")
            return conn.responseCode == 200
        } catch (Exception ignored) {
            return false
        } finally {
            conn?.disconnect()
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add('jadetipi.kafka.enabled', { 'true' })
        registry.add('jadetipi.kafka.txn-topic-pattern', { TEST_TOPIC })
        registry.add('spring.kafka.bootstrap-servers', { BOOTSTRAP_SERVERS })
        registry.add('spring.kafka.consumer.group-id', { CONSUMER_GROUP })
        registry.add('spring.kafka.consumer.properties.metadata.max.age.ms', { '2000' })
        // Activate the module's own publisher bean (TASK-066): the driver
        // publishes through the production path, not a test producer.
        registry.add('jgi-import.kafka.bootstrap-servers', { BOOTSTRAP_SERVERS })
        registry.add('jgi-import.kafka.topic', { TEST_TOPIC })
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
    ClarityAliquotImportPlanner planner
    @Autowired
    ImportQueueService queueService
    @Autowired
    ClarityImportDriver driver
    @Autowired
    ImportMessagePublisher publisher
    @Autowired
    CouchDbDocumentReader reader

    Map<String, String> minted = [:]
    List<String> driveTxnIds = []

    def cleanupSpec() {
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
        // The driver drains EVERYTHING pending, so start from a clean queue
        // (test database; the seq counter re-upserts on first enqueue).
        mongoTemplate.remove(new Query(), ImportQueueService.COLLECTION_NAME)
                .block(Duration.ofSeconds(10))
        minted = [:]
        driveTxnIds = []
    }

    def cleanup() {
        driveTxnIds.each { String txnId ->
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
            ROOT_COLLECTIONS.each { String collection ->
                mongoTemplate.remove(
                        Query.query(Criteria.where('_head.provenance.txn_id').is(txnId)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
        mongoTemplate.remove(new Query(), ImportQueueService.COLLECTION_NAME)
                .block(Duration.ofSeconds(10))
    }

    /** Every queue key the fixture process's plan produces. */
    private static List<String> planKeys() {
        return ClarityAliquotImportMapper.BOOTSTRAP_KEYS +
                [ClarityAliquotImportMapper.KEY_TYPE_PROCEDURE_AC] +
                ['containers_27-8528', 'containers_27-8546'].collect { it } +
                ['samples_DES439A6'] +
                ['artifacts_DES439A6PA1', 'artifacts_2-79367',
                 'artifacts_92-79368', 'artifacts_92-79369'] +
                [PROCESS_DOC_ID]
    }

    def 'the planned queue drives one Kafka transaction that materializes the full aliquot graph'() {
        given: 'the dependency-ordered plan for the live process document'
        Long inserted = planner.planProcess(PROCESS_DOC_ID).block(Duration.ofSeconds(30))
        List<ImportQueueItem> pending = queueService.pendingInOrder(100)
                .collectList().block(Duration.ofSeconds(10))

        expect: 'the full plan is queued in dependency order'
        inserted == 17L
        pending.size() == 17
        pending.first().kind == 'type'
        pending.last().key == PROCESS_DOC_ID

        when: 'the production driver drains the queue through the module publisher (TASK-066)'
        ImportDriveReport report = driver.drive(publisher, 'jade-itest-org', 'import',
                'itest-user', 100)
        driveTxnIds = report.txnIds
        minted = planKeys().collectEntries { String key ->
            [key, queueService.jdtpIdOf(ImportQueueService.rowId('clarity', key))
                    .block(MONGO_BLOCK_TIMEOUT)]
        } as Map<String, String>

        then: 'one batch, every item done, nothing failed'
        report.batches == 1
        report.itemsDone == 17
        report.itemsFailed == 0
        report.txnIds.size() == 1

        and: 'every row minted a conformant message-UUID-form id under the drive org/grp'
        minted.values().every { String id ->
            id != null && id.startsWith('jade-itest-org~import~') && id.split('~').length == 5
        }

        and: 'the transaction commits with the full committed set recorded'
        String txnId = report.txnIds.first()
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed import transaction header'
        )
        header.message_count == report.messagesPublished - 2

        and: 'the prc root carries the three resolved outputs in output_input'
        String processId = minted[PROCESS_DOC_ID]
        Map prcDoc = awaitMongo(
                { mongoTemplate.findById(processId, Map, 'prc') },
                { Map d -> d != null },
                'imported prc root'
        )
        prcDoc.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_PROCEDURE_AC]
        Map outputInput = prcDoc.output_input as Map
        outputInput.keySet() == ['2-79367', '92-79368', '92-79369']
                .collect { minted[ClarityAliquotImportMapper.artifactKey(it)] } as Set
        (prcDoc.properties as Map).clarity_limsid == '24-35613'

        and: 'the submitted sample materialized as ent with sample_of links from every artifact (TASK-067)'
        String sampleId = minted[ClarityAliquotImportMapper.sampleKey('DES439A6')]
        Map sampleDoc = awaitMongo(
                { mongoTemplate.findById(sampleId, Map, 'ent') },
                { Map d -> d != null }, 'imported sample ent root')
        sampleDoc.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_SAMPLE]
        (sampleDoc.properties as Map).source_kind == 'clarity_sample'
        ['DES439A6PA1', '2-79367', '92-79368', '92-79369'].every { String limsid ->
            findLink(minted[ClarityAliquotImportMapper.artifactKey(limsid)], sampleId)
                    .block(MONGO_BLOCK_TIMEOUT) != null
        }

        and: 'the analyte materialized as ent with a positioned contents link from its container'
        String analyteId = minted[ClarityAliquotImportMapper.artifactKey('2-79367')]
        String containerId = minted[ClarityAliquotImportMapper.containerKey('27-8546')]
        Map analyteDoc = awaitMongo(
                { mongoTemplate.findById(analyteId, Map, 'ent') },
                { Map d -> d != null }, 'imported analyte ent root')
        analyteDoc.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_ANALYTE]
        Map contentsLink = awaitMongo(
                { findLink(containerId, analyteId) },
                { Map d -> d != null }, 'contents link for the analyte')
        (((contentsLink.properties as Map).position) as Map).label == '1:1'

        and: 'the ResultFiles materialized as fil roots with produced_by links to the prc'
        ['92-79368', '92-79369'].every { String limsid ->
            String filId = minted[ClarityAliquotImportMapper.artifactKey(limsid)]
            Map filDoc = mongoTemplate.findById(filId, Map, 'fil').block(MONGO_BLOCK_TIMEOUT)
            Map producedBy = findLink(filId, processId).block(MONGO_BLOCK_TIMEOUT)
            filDoc != null && producedBy != null
        }

        and: 'the procedure_input link consumes the input analyte'
        String inputAnalyteId = minted[ClarityAliquotImportMapper.artifactKey('DES439A6PA1')]
        Map inputLink = findLink(processId, inputAnalyteId).block(MONGO_BLOCK_TIMEOUT)
        inputLink != null

        and: 'every queue row is done with the drive transaction and its recorded id'
        List<Map> after = planKeys().collect { String key ->
            mongoTemplate.findById(ImportQueueService.rowId('clarity', key), Map,
                    ImportQueueService.COLLECTION_NAME).block(MONGO_BLOCK_TIMEOUT)
        }
        after.every { Map row ->
            row.state == 'done' && row.txn_id == txnId && row.jdtp_id == minted[row.key as String]
        }
    }

    def 'process-type discovery streams document ids and the histogram from the live view (TASK-067)'() {
        when: 'five AC process documents are discovered through the replica view'
        List<String> ids = reader.processDocIdsByType('clarity', 'AC Sample Aliquot Creation', 5)
                .collectList().block(Duration.ofSeconds(30))

        then:
        ids.size() == 5
        ids.every { it.startsWith('processes_') }
        ids.toSet().size() == 5

        and: 'the histogram lists all 51 clarity process types'
        Map<String, Long> counts = reader.processTypeCounts('clarity')
                .block(Duration.ofSeconds(30))
        counts.size() == 51
        counts['AC Sample Aliquot Creation'] > 0
    }

    private Mono<Map> findLink(String left, String right) {
        return mongoTemplate.find(
                Query.query(Criteria.where('left').is(left).and('right').is(right)),
                Map, LNK_COLLECTION).next() as Mono<Map>
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
