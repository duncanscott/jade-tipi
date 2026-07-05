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
 * End-to-end TASK-049 coverage for the file sequence: {@code open} →
 * {@code typ + create} (fastq file type) →
 * {@code ppy + create kind=definition} (retrieval_url) →
 * {@code typ + update add_property} (retrieval_url on the file type) →
 * {@code typ + create kind=procedure_type} (sequencing) →
 * {@code prc + create} → {@code typ + create kind=link_type}
 * (produced_by admitting {@code fil} on the left) → {@code fil + create}
 * (typed file) → object-targeted {@code ppy + create kind=assignment}
 * (retrieval_url onto the fil root) → produced_by {@code lnk + create}
 * (fil → prc) → {@code commit}.
 *
 * <p>Asserts the typed {@code fil} root as a standard root — no hoisted
 * file-specific structure (content identity and dedup are deferred
 * director rulings; DIRECTION.md, Files) — the projected
 * {@code property_values.<retrieval_url>} entry, and the produced_by
 * link joining the file to the procedure that created it. All IDs follow
 * the transaction-UUID form of the object identifier convention.
 *
 * <p>Same opt-in gates as the other Kafka specs. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*FileProvenanceKafkaMaterializeIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !FileProvenanceKafkaMaterializeIntegrationSpec.kafkaIntegrationGateOpen() })
class FileProvenanceKafkaMaterializeIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-fil-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-fil-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LNK_COLLECTION = 'lnk'
    private static final String PRC_COLLECTION = 'prc'
    private static final String FIL_COLLECTION = 'fil'
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30)
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250)
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    static boolean kafkaIntegrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_KAFKA')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
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

    @Shared
    KafkaProducer<String, byte[]> producer

    Transaction txn
    String txnId
    String retrievalUrlPropertyId
    String fastqTypeId
    String sequencingTypeId
    String producedByTypeId
    String fileId
    String procedureId
    String producedByLinkId

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
        // Object identifier convention (TASK-044): transaction-UUID form —
        // per-feature uniqueness comes from the fresh transaction UUIDv7.
        String idPrefix = "jade-itest-org~kafka~${txn.uuid()}"
        retrievalUrlPropertyId = "${idPrefix}~ppy~retrieval_url"
        fastqTypeId = "${idPrefix}~typ~fastq"
        sequencingTypeId = "${idPrefix}~typ~sequencing"
        producedByTypeId = "${idPrefix}~typ~produced_by"
        fileId = "${idPrefix}~fil~run42_r1_fastq"
        procedureId = "${idPrefix}~prc~sequencing_run_42"
        producedByLinkId = "${idPrefix}~lnk~run42_r1_produced_by"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(PPY_COLLECTION): [retrievalUrlPropertyId],
         (TYP_COLLECTION): [fastqTypeId, sequencingTypeId, producedByTypeId],
         (FIL_COLLECTION): [fileId],
         (PRC_COLLECTION): [procedureId],
         (LNK_COLLECTION): [producedByLinkId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'committed file transaction materializes the typed fil root, projected retrieval_url, and produced_by provenance'() {
        given: 'the full file sequence in one transaction'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'typed file with retrieval_url and produced_by provenance'
        ])
        Message fastqTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : fastqTypeId,
                name       : 'fastq',
                description: 'FASTQ sequencing-reads file'
        ] as Map<String, Object>)
        Message urlDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : retrievalUrlPropertyId,
                name        : 'retrieval_url',
                value_schema: [
                        type      : 'object',
                        required  : ['url'],
                        properties: [url: [type: 'string']]
                ]
        ] as Map<String, Object>)
        Message addUrlMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : fastqTypeId,
                operation  : 'add_property',
                property_id: retrievalUrlPropertyId
        ] as Map<String, Object>)
        Message sequencingTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind       : 'procedure_type',
                id         : sequencingTypeId,
                name       : 'sequencing',
                description: 'sequencing run producing read files'
        ] as Map<String, Object>)
        Message procedureMsg = Message.newInstance(txn, JtpCollection.PROCEDURE, Action.CREATE, [
                id     : procedureId,
                type_id: sequencingTypeId,
                name   : 'sequencing_run_42'
        ] as Map<String, Object>)
        Message producedByTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : producedByTypeId,
                name                     : 'produced_by',
                left_role                : 'output',
                right_role               : 'procedure',
                left_to_right_label      : 'produced_by',
                right_to_left_label      : 'produced',
                allowed_left_collections : ['ent', 'fil'],
                allowed_right_collections: ['prc']
        ] as Map<String, Object>)
        Message fileMsg = Message.newInstance(txn, JtpCollection.FILE, Action.CREATE, [
                id         : fileId,
                type_id    : fastqTypeId,
                name       : 'run42_r1.fastq',
                description: 'forward reads for sequencing run 42'
        ] as Map<String, Object>)
        Message urlAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'fil',
                object_id        : fileId,
                property_id      : retrievalUrlPropertyId,
                value            : [url: 'https://data.example.org/run42/r1.fastq']
        ] as Map<String, Object>)
        Message producedByLinkMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id     : producedByLinkId,
                type_id: producedByTypeId,
                left   : fileId,
                right  : procedureId
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'typed file with provenance back to its sequencing run'
        ])

        when: 'all eleven records are produced to the test topic in order'
        [openMsg, fastqTypeMsg, urlDefinitionMsg, addUrlMsg, sequencingTypeMsg,
         procedureMsg, producedByTypeMsg, fileMsg, urlAssignmentMsg,
         producedByLinkMsg, commitMsg].each { send(it) }

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header.state == 'committed'

        and: 'the fil root is a standard typed root with the projected retrieval_url value'
        Map filDoc = awaitMongo(
                { mongoTemplate.findById(fileId, Map, FIL_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(retrievalUrlPropertyId) },
                'fil root with projected property_values entry'
        )
        filDoc.collection == 'fil'
        filDoc.type_id == fastqTypeId
        (filDoc.properties as Map).name == 'run42_r1.fastq'
        Map urlEntry = (filDoc.property_values as Map)[retrievalUrlPropertyId] as Map
        urlEntry.value == [url: 'https://data.example.org/run42/r1.fastq']
        urlEntry.txn_id == txnId
        urlEntry.commit_id == header.commit_id
        urlEntry.msg_uuid == urlAssignmentMsg.uuid()
        urlEntry.applied_at != null

        and: 'nothing file-specific is hoisted onto the root'
        filDoc.keySet() == ['_id', 'id', 'collection', 'type_id', 'properties',
                            'links', '_head', 'property_values'] as Set

        and: 'the produced_by link joins the file to the procedure that created it'
        Map producedByDoc = awaitMongo(
                { mongoTemplate.findById(producedByLinkId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'produced_by link'
        )
        producedByDoc.type_id == producedByTypeId
        producedByDoc.left == fileId
        producedByDoc.right == procedureId

        and: 'the procedure materialized as a typed prc root'
        Map prcDoc = awaitMongo(
                { mongoTemplate.findById(procedureId, Map, PRC_COLLECTION) },
                { Map d -> d != null },
                'prc root'
        )
        prcDoc.type_id == sequencingTypeId
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
