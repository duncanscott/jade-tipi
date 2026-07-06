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

import com.github.f4b6a3.uuid.UuidCreator
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
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * End-to-end TASK-059 proof of the clarity aliquot import slice against
 * the LIVE clarity CouchDB: plan process {@code processes_24-35613} into
 * the persistent dependency-ordered queue, drive the queue into one JDTP
 * transaction over Kafka (message-UUID-form ids minted at emit and
 * recorded on the rows), and assert the materialized graph — typed
 * containers, Analyte ent with a positioned contents link, ResultFile
 * fil roots, and the prc whose output_input carries the three resolved
 * outputs, with procedure_input and produced_by links. Dependency order
 * satisfies declare-before-use by construction, so the TASK-058 link
 * warnings stay silent.
 *
 * <p>Gated on the docker itest stack (shared {@code JADETIPI_IT_KAFKA}
 * flag) plus Kafka and the clarity CouchDB being reachable. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
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
    CouchDbDocumentReader reader

    @Shared
    KafkaProducer<String, byte[]> producer

    ClarityAliquotImportMapper mapper = new ClarityAliquotImportMapper()
    Transaction txn
    String txnId
    Map<String, String> minted = [:]

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
        txn = Transaction.newInstance('jade-itest-org', 'import', 'jade-itest-cli', 'itest-user')
        txnId = txn.id
        removeQueueRows()
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        minted.each { String key, String id ->
            String collection = id.split('~')[3]
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)), collection)
                    .block(Duration.ofSeconds(10))
        }
        removeQueueRows()
    }

    private void removeQueueRows() {
        planKeys().each { String key ->
            mongoTemplate.remove(Query.query(Criteria.where('_id')
                    .is(ImportQueueService.rowId('clarity', key))),
                    ImportQueueService.COLLECTION_NAME).block(Duration.ofSeconds(10))
        }
    }

    /** Every queue key the fixture process's plan produces. */
    private static List<String> planKeys() {
        return ClarityAliquotImportMapper.BOOTSTRAP_KEYS +
                ['containers_27-8528', 'containers_27-8546'].collect { it } +
                ['artifacts_DES439A6PA1', 'artifacts_2-79367',
                 'artifacts_92-79368', 'artifacts_92-79369'] +
                [PROCESS_DOC_ID]
    }

    def 'the planned queue drives one Kafka transaction that materializes the full aliquot graph'() {
        given: 'the dependency-ordered plan for the live process document'
        Long inserted = planner.planProcess(PROCESS_DOC_ID).block(Duration.ofSeconds(30))
        List<ImportQueueItem> pending = queueService.pendingInOrder(100)
                .filter { ImportQueueItem item -> planKeys().contains(item.key) }
                .collectList().block(Duration.ofSeconds(10))

        expect: 'the full plan is queued in dependency order'
        inserted == 14L
        pending.size() == 14
        pending.first().kind == 'type'
        pending.last().key == PROCESS_DOC_ID

        when: 'the drive loop emits the queue as one transaction (message-UUID-form ids, recorded on rows)'
        BiFunction<String, String, String> idFor = { String key, String collection ->
            minted.computeIfAbsent(key, {
                String uuid = UuidCreator.timeOrderedEpoch.toString()
                // plain String: a GString here would serialize as a JSON object
                'jade-itest-org~import~' + uuid + '~' + collection + '~' +
                        ClarityAliquotImportMapper.suffixFor(key)
            })
        } as BiFunction<String, String, String>

        List<Message> messages = []
        pending.each { ImportQueueItem item ->
            List<MappedImportMessage> mapped
            switch (item.kind) {
                case 'type':
                    mapped = [mapper.mapBootstrapType(item.key, idFor)]
                    break
                case 'container':
                    mapped = mapper.mapContainer(fetchDoc(item.key), idFor)
                    break
                case 'artifact':
                    mapped = mapper.mapArtifact(fetchDoc(item.key), idFor)
                    break
                case 'process':
                    mapped = mapper.mapProcess(fetchDoc(item.key), idFor)
                    break
                default:
                    throw new IllegalStateException("Unknown queue kind: ${item.kind}")
            }
            queueService.recordJdtpId(item.id, minted[item.key]).block(MONGO_BLOCK_TIMEOUT)
            mapped.each { MappedImportMessage m ->
                // msg-UUID form: the message uuid IS the id's uuid segment
                String uuid = (m.data.id as String).split('~')[2]
                messages.add(new Message(txn, uuid,
                        JtpCollection.fromJson(m.collection), Action.CREATE, m.data))
            }
        }
        send(Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN,
                [description: 'clarity aliquot import slice'] as Map<String, Object>))
        messages.each { send(it) }
        send(Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT,
                [summary: 'clarity ' + PROCESS_DOC_ID + ' import'] as Map<String, Object>))
        pending.each { ImportQueueItem item ->
            queueService.markDone(item.id, txnId).block(MONGO_BLOCK_TIMEOUT)
        }

        then: 'the transaction commits with the full committed set recorded'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed import transaction header'
        )
        header.message_count == messages.size()

        and: 'the prc root carries the three resolved outputs in output_input'
        String processId = minted[ClarityAliquotImportMapper.processKey('24-35613')]
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

        and: 'the analyte materialized as ent with a positioned contents link from its container'
        String analyteId = minted[ClarityAliquotImportMapper.artifactKey('2-79367')]
        Map analyteDoc = awaitMongo(
                { mongoTemplate.findById(analyteId, Map, 'ent') },
                { Map d -> d != null }, 'imported analyte ent root')
        analyteDoc.type_id == minted[ClarityAliquotImportMapper.KEY_TYPE_ANALYTE]
        Map contentsLink = awaitMongo(
                { mongoTemplate.findById(minted['link:contents:2-79367'], Map, 'lnk') },
                { Map d -> d != null }, 'contents link for the analyte')
        contentsLink.left == minted[ClarityAliquotImportMapper.containerKey('27-8546')]
        contentsLink.right == analyteId
        (((contentsLink.properties as Map).position) as Map).label == '1:1'

        and: 'the ResultFiles materialized as fil roots with produced_by links to the prc'
        ['92-79368', '92-79369'].every { String limsid ->
            Map filDoc = mongoTemplate.findById(
                    minted[ClarityAliquotImportMapper.artifactKey(limsid)], Map, 'fil')
                    .block(MONGO_BLOCK_TIMEOUT)
            Map producedBy = mongoTemplate.findById(
                    minted["link:produced_by:${limsid}".toString()], Map, 'lnk')
                    .block(MONGO_BLOCK_TIMEOUT)
            filDoc != null && producedBy.right == processId
        }

        and: 'the procedure_input link consumes the input analyte'
        Map inputLink = mongoTemplate.findById(
                minted['link:procedure_input:24-35613:DES439A6PA1'], Map, 'lnk')
                .block(MONGO_BLOCK_TIMEOUT)
        inputLink.left == processId
        inputLink.right == minted[ClarityAliquotImportMapper.artifactKey('DES439A6PA1')]

        and: 'every queue row is done with the transaction and its recorded id'
        List<ImportQueueItem> after = planKeys().collect { String key ->
            mongoTemplate.findById(ImportQueueService.rowId('clarity', key), Map,
                    ImportQueueService.COLLECTION_NAME).block(MONGO_BLOCK_TIMEOUT)
        }.collect { Map row ->
            new ImportQueueItem(id: row._id as String, source: row.source as String,
                    key: row.key as String, kind: row.kind as String,
                    state: row.state as String, seq: ((Number) row.seq).longValue(),
                    jdtpId: row.jdtp_id as String, txnId: row.txn_id as String,
                    error: row.error as String)
        }
        after.every { it.state == 'done' && it.txnId == txnId && it.jdtpId == minted[it.key] }
    }

    private Map<String, Object> fetchDoc(String key) {
        Map<String, Object> doc = reader.findDocument('clarity', key)
                .block(Duration.ofSeconds(15))
        assert doc != null: "clarity document vanished mid-import: ${key}"
        return doc
    }

    private void send(Message message) {
        byte[] bytes = JsonMapper.toBytes(message)
        producer.send(new ProducerRecord<String, byte[]>(TEST_TOPIC, txnId, bytes))
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
