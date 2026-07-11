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
 * End-to-end TASK-069 proof of the esp-entity import against the LIVE
 * esp-entity CouchDB, driven through the production path: plan one
 * Aliquot's full begat ancestry (SOW Item, Sequencing Project, PM SOW
 * Item, Nucleic Acid with its container, Final Deliv Project, Proposal),
 * drive it as one transaction, and assert the materialized graph — typed
 * ent roots with verbatim variables, begat links along the ancestry, and
 * the Nucleic Acid's positioned contents link from its 96W plate.
 *
 * <p>Same opt-in gates as the clarity specs, with the esp-entity
 * database probed instead.
 */
@Slf4j
@SpringBootTest(classes = JadetipiApplication)
@ActiveProfiles('test')
@IgnoreIf({ !EspEntityImportKafkaIntegrationSpec.integrationGateOpen() })
class EspEntityImportKafkaIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String COUCHDB_URL =
            System.getenv('JADETIPI_IMPORT_COUCHDB_URL') ?: 'http://localhost:5984'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-espimport-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-espimport-${SHORT_UUID}"
    /** Surveyed live 2026-07-07: Aliquot AQ00155404 with a 6-ancestor begat chain. */
    private static final String ALIQUOT_UUID = '019a3ea4-4e3f-787c-878a-b51665a0f3a9'
    private static final String TXN_COLLECTION = 'txn'
    private static final List<String> ROOT_COLLECTIONS = ['typ', 'loc', 'ent', 'fil', 'prc', 'tsk', 'lnk', 'ppy']
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
            URL url = new URL("${COUCHDB_URL}/esp-entity")
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
    EspEntityImportPlanner espPlanner
    @Autowired
    ImportQueueService queueService
    @Autowired
    ClarityImportDriver driver
    @Autowired
    ImportMessagePublisher publisher
    @Autowired
    CouchDbDocumentReader reader

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
        mongoTemplate.remove(new Query(), ImportQueueService.COLLECTION_NAME)
                .block(Duration.ofSeconds(10))
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
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)), 'hst')
                    .block(Duration.ofSeconds(10))
        }
        mongoTemplate.remove(new Query(), ImportQueueService.COLLECTION_NAME)
                .block(Duration.ofSeconds(10))
    }

    private String jdtpId(String key) {
        return queueService.jdtpIdOf(ImportQueueService.rowId('esp', key))
                .block(MONGO_BLOCK_TIMEOUT)
    }

    def 'an aliquot ancestry imports through the production path with begat links and positioned containment'() {
        given: 'the live ancestry facts the assertions anchor to'
        Map aliquotDoc = reader.findDocument('esp-entity', ALIQUOT_UUID).block(MONGO_BLOCK_TIMEOUT)
        String sowUuid = ((aliquotDoc.parents as List)[0] as Map).uuid
        Map sowDoc = reader.findDocument('esp-entity', sowUuid).block(MONGO_BLOCK_TIMEOUT)
        Map naEdge = (sowDoc.parents as List).find { (it as Map).type_name == 'Nucleic Acid' } as Map
        String naUuid = naEdge.uuid
        Map naDoc = reader.findDocument('esp-entity', naUuid).block(MONGO_BLOCK_TIMEOUT)
        String containerUuid = (naDoc.container as Map).uuid
        Map containerDoc = reader.findDocument('esp-entity', containerUuid).block(MONGO_BLOCK_TIMEOUT)
        String expectedWell = ((containerDoc.contents as Map).find { Object k, Object v ->
            (v as Map).uuid == naUuid
        } as Map.Entry)?.key

        when: 'the ancestry plans and drives through the production path'
        Long inserted = espPlanner.planEspEntity(ALIQUOT_UUID).block(Duration.ofSeconds(60))
        ImportDriveReport report = driver.drive(publisher, 'jade-itest-org', 'import',
                'itest-user', 200)
        driveTxnIds = report.txnIds

        then: 'one clean batch covering the whole ancestry'
        inserted > 10
        report.batches == 1
        report.itemsFailed == 0

        and: 'the aliquot materialized as a typed ent with esp identity and its begat link'
        String aliquotId = jdtpId(ALIQUOT_UUID)
        aliquotId.split('~')[4].startsWith('esp_')
        Map aliquotRoot = awaitMongo(
                { mongoTemplate.findById(aliquotId, Map, 'ent') },
                { Map d -> d != null }, 'imported aliquot ent root')
        aliquotRoot.type_id == jdtpId(EspEntityImportMapper.typeKey('Sample', 'Aliquot'))
        (aliquotRoot.properties as Map).esp_uuid == ALIQUOT_UUID
        awaitMongo(
                { findLink(jdtpId(sowUuid), aliquotId) },
                { Map d -> d != null }, 'begat link SOW Item → Aliquot')

        and: 'the ancestry chain materialized (the SOW Item as a tsk, the NA as an ent)'
        mongoTemplate.findById(jdtpId(sowUuid), Map, 'tsk').block(MONGO_BLOCK_TIMEOUT) != null
        mongoTemplate.findById(jdtpId(naUuid), Map, 'ent').block(MONGO_BLOCK_TIMEOUT) != null

        and: 'the nucleic acid sits in its 96W plate with the live well position'
        Map containerRoot = mongoTemplate.findById(jdtpId(containerUuid), Map, 'loc')
                .block(MONGO_BLOCK_TIMEOUT)
        containerRoot != null
        Map contentsLink = awaitMongo(
                { findLink(jdtpId(containerUuid), jdtpId(naUuid)) },
                { Map d -> d != null }, 'positioned contents link for the nucleic acid')
        expectedWell == null ||
                (((contentsLink.properties as Map)?.position as Map)?.label == expectedWell)

        and: 'variables came through verbatim on the nucleic acid'
        Map naRoot = mongoTemplate.findById(jdtpId(naUuid), Map, 'ent').block(MONGO_BLOCK_TIMEOUT)
        ((naRoot.properties as Map).variables instanceof Map)
    }

    def 'an esp container reuses the id of an already-imported clarity container (TASK-071 precedence)'() {
        given: 'the clarity container 27-279088 is imported FIRST (clarity-before-esp), recording its jdtp_id'
        // surveyed live: esp 96W Plate 019a3ea7... name "27-279088" == clarity containers_27-279088
        String espPlateUuid = '019a3ea7-8f4b-771c-9f58-8c833082e9b6'
        String clarityKey = 'containers_27-279088'
        queueService.enqueue('clarity', ClarityAliquotImportMapper.KEY_TYPE_CONTAINER, 'type')
                .block(MONGO_BLOCK_TIMEOUT)
        queueService.enqueue('clarity', clarityKey, 'container').block(MONGO_BLOCK_TIMEOUT)
        ImportDriveReport clarityReport = driver.drive(publisher, 'jade-itest-org', 'import',
                'itest-user', 200)
        driveTxnIds.addAll(clarityReport.txnIds)
        String clarityRootId = queueService.jdtpIdOf(ImportQueueService.rowId('clarity', clarityKey))
                .block(MONGO_BLOCK_TIMEOUT)

        and: 'the clarity container loc root materialized'
        awaitMongo(
                { mongoTemplate.findById(clarityRootId, Map, 'loc') },
                { Map d -> d != null }, 'clarity container loc root')

        when: 'the matching esp container is planned and driven (clarity already present)'
        Long inserted = espPlanner.planEspEntity(espPlateUuid).block(Duration.ofSeconds(60))
        ImportDriveReport espReport = driver.drive(publisher, 'jade-itest-org', 'import',
                'itest-user', 200)
        driveTxnIds.addAll(espReport.txnIds)

        then: 'the esp container row reused the clarity root id — no duplicate plate root'
        inserted > 0
        espReport.itemsFailed == 0
        String espRootId = queueService.jdtpIdOf(ImportQueueService.rowId('esp', espPlateUuid))
                .block(MONGO_BLOCK_TIMEOUT)
        espRootId == clarityRootId

        and: 'esp minted NO second loc root for this plate (no doc carries its esp_uuid)'
        mongoTemplate.find(
                Query.query(Criteria.where('properties.esp_uuid').is(espPlateUuid)),
                Map, 'loc').collectList().block(MONGO_BLOCK_TIMEOUT).isEmpty()

        and: 'the shared clarity root is the only loc for that plate'
        mongoTemplate.findById(clarityRootId, Map, 'loc').block(MONGO_BLOCK_TIMEOUT) != null
    }

    def 'a SOW Item reconstructs its Aliquot Creation procedure from sample sheets (TASK-070)'() {
        given: 'the worked example: plan the Aliquot (its ancestry pulls in the SOW Item task and NA input)'
        // surveyed live: SOW05715930 (SOW Item) carries SOW QC + Aliquot Creation sheets;
        // begat NA00462849 -> SOW05715930 -> AQ00425694 (created inside the Aliquot Creation window)
        String aliquotUuid = '019eccd0-ce7c-7eec-ba71-b5e403b6c25e'
        String sowUuid = '019dfbd5-3a63-7d0d-a460-15850f00cc4b'
        String naUuid = '019dfbd6-9114-7a77-89ab-7f77e672fbf5'
        String aliquotCreationSheet = '019eccc3-fb0b-7a6a-bc6f-1535cb13a3ba'

        when: 'the plan is driven through the production path'
        Long inserted = espPlanner.planEspEntity(aliquotUuid).block(Duration.ofSeconds(120))
        ImportDriveReport report = driver.drive(publisher, 'jade-itest-org', 'import',
                'itest-user', 500)
        driveTxnIds = report.txnIds

        then: 'a clean drive'
        inserted > 0
        report.itemsFailed == 0

        and: 'the SOW Item materialized as a tsk (not an ent)'
        String sowId = jdtpId(sowUuid)
        awaitMongo({ mongoTemplate.findById(sowId, Map, 'tsk') }, { Map d -> d != null }, 'SOW Item tsk root')
        mongoTemplate.findById(sowId, Map, 'ent').block(MONGO_BLOCK_TIMEOUT) == null

        and: 'the Aliquot Creation procedure materialized as a prc from the sample sheet'
        Map prc = awaitMongo(
                { mongoTemplate.find(Query.query(
                        Criteria.where('properties.esp_sample_sheet').is(aliquotCreationSheet)),
                        Map, 'prc').next() },
                { Map d -> d != null }, 'Aliquot Creation prc')
        (prc.properties as Map).esp_workflow == 'Aliquot Creation'
        String prcId = prc._id

        and: 'the prc fulfills the SOW Item task, consumes the NA input, and produced the Aliquot'
        String naId = jdtpId(naUuid)
        String aliquotId = jdtpId(aliquotUuid)
        awaitMongo({ findLink(prcId, sowId) }, { Map d -> d != null }, 'fulfills prc -> SOW Item tsk')
        awaitMongo({ findLink(prcId, naId) }, { Map d -> d != null }, 'procedure_input prc -> NA')
        awaitMongo({ findLink(aliquotId, prcId) }, { Map d -> d != null }, 'produced_by Aliquot -> prc')

        and: 'output_input maps the Aliquot output to the NA input'
        Map outputInput = prc.output_input as Map
        outputInput.containsKey(aliquotId)
        (outputInput.get(aliquotId) as Map).containsKey(naId)

        and: 'the SOW Item task carries the NA as a task_input'
        awaitMongo({ findLink(sowId, naId) }, { Map d -> d != null }, 'task_input SOW Item -> NA')
    }

    private Mono<Map> findLink(String left, String right) {
        return mongoTemplate.find(
                Query.query(Criteria.where('left').is(left).and('right').is(right)),
                Map, 'lnk').next() as Mono<Map>
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
