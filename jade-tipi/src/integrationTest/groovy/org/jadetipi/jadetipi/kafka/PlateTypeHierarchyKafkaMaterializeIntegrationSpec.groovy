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
 * End-to-end TASK-040 coverage for the plate type-hierarchy sequence:
 * {@code open} → {@code ppy + create kind=definition} (barcode) →
 * {@code typ + create} (container) → {@code typ + update add_property}
 * (barcode on container) → {@code typ + create} (plate,
 * {@code parent_type_id} container) → {@code typ + create} (plate_96_well,
 * {@code parent_type_id} plate) → {@code loc + create} (typed plate
 * instance) → object-targeted {@code ppy + create kind=assignment}
 * (barcode onto the loc root) → an unregistered object-targeted assignment
 * (gated) → {@code commit}.
 *
 * <p>Asserts the materialized hierarchy (`properties.parent_type_id` on the
 * subtype roots), the typed {@code loc} root, and the projected
 * {@code property_values.<barcode>} entry carrying
 * {@code value}/{@code txn_id}/{@code commit_id}/{@code msg_uuid}/
 * {@code applied_at}. The barcode registration lives only on the
 * {@code container} type, so a successful projection proves the
 * inheritance-aware registration walk.
 *
 * <p>Same opt-in gates as the other Kafka specs. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*PlateTypeHierarchyKafkaMaterializeIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !PlateTypeHierarchyKafkaMaterializeIntegrationSpec.kafkaIntegrationGateOpen() })
class PlateTypeHierarchyKafkaMaterializeIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-plate96-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-plate96-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String LOC_COLLECTION = 'loc'
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
    String barcodePropertyId
    String unregisteredPropertyId
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
        barcodePropertyId = "${idPrefix}~ppy~barcode"
        unregisteredPropertyId = "${idPrefix}~ppy~volume"
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
        [(PPY_COLLECTION): [barcodePropertyId, unregisteredPropertyId],
         (TYP_COLLECTION): [containerTypeId, plateTypeId, plate96TypeId],
         (LOC_COLLECTION): [plateLocId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'committed plate hierarchy transaction projects the inherited barcode onto the loc root and gates the unregistered value'() {
        given: 'the full plate-96-well sequence in one transaction'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'plate-96-well type hierarchy demo'
        ])
        Message barcodeDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : barcodePropertyId,
                name        : 'barcode',
                value_schema: [
                        type      : 'object',
                        required  : ['text'],
                        properties: [text: [type: 'string']]
                ]
        ] as Map<String, Object>)
        Message containerTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : containerTypeId,
                name       : 'container',
                description: 'generic physical container'
        ] as Map<String, Object>)
        Message containerAddBarcodeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : containerTypeId,
                operation  : 'add_property',
                property_id: barcodePropertyId
        ] as Map<String, Object>)
        Message plateTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id            : plateTypeId,
                name          : 'plate',
                parent_type_id: containerTypeId
        ] as Map<String, Object>)
        Message plate96TypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id            : plate96TypeId,
                name          : 'plate_96_well',
                parent_type_id: plateTypeId
        ] as Map<String, Object>)
        Message plateInstanceMsg = Message.newInstance(txn, JtpCollection.LOCATION, Action.CREATE, [
                id        : plateLocId,
                type_id   : plate96TypeId,
                properties: [name: 'demo plate 0001'],
                links     : [:]
        ] as Map<String, Object>)
        Message barcodeAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'loc',
                object_id        : plateLocId,
                property_id      : barcodePropertyId,
                value            : [text: 'PLATE-BC-0001']
        ] as Map<String, Object>)
        Message unregisteredAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'loc',
                object_id        : plateLocId,
                property_id      : unregisteredPropertyId,
                value            : [number: 200, unit_id: 'jade-tipi-org~dev~liter~micro~ul~SI']
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'plate hierarchy with inherited barcode assignment'
        ])

        when: 'all ten records are produced to the test topic in order'
        [openMsg, barcodeDefinitionMsg, containerTypeMsg, containerAddBarcodeMsg,
         plateTypeMsg, plate96TypeMsg, plateInstanceMsg, barcodeAssignmentMsg,
         unregisteredAssignmentMsg, commitMsg].each { send(it) }

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header.state == 'committed'

        and: 'the container type carries the barcode registration'
        Map containerDoc = awaitMongo(
                { mongoTemplate.findById(containerTypeId, Map, TYP_COLLECTION) },
                { Map d -> d != null && ((Map) d.properties)?.property_refs != null },
                'container typ root with property_refs'
        )
        ((containerDoc.properties as Map).property_refs as Map).containsKey(barcodePropertyId)

        and: 'the subtype roots materialize with parent_type_id under properties'
        Map plateDoc = awaitMongo(
                { mongoTemplate.findById(plateTypeId, Map, TYP_COLLECTION) },
                { Map d -> d != null },
                'plate typ root'
        )
        (plateDoc.properties as Map).parent_type_id == containerTypeId
        (plateDoc.properties as Map).name == 'plate'

        Map plate96Doc = awaitMongo(
                { mongoTemplate.findById(plate96TypeId, Map, TYP_COLLECTION) },
                { Map d -> d != null },
                'plate_96_well typ root'
        )
        (plate96Doc.properties as Map).parent_type_id == plateTypeId

        and: 'the barcode value is projected onto the typed loc root via the inherited registration'
        Map locDoc = awaitMongo(
                { mongoTemplate.findById(plateLocId, Map, LOC_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(barcodePropertyId) },
                'loc root with projected property_values entry'
        )
        locDoc.type_id == plate96TypeId
        (locDoc.properties as Map).name == 'demo plate 0001'
        Map barcodeEntry = (locDoc.property_values as Map)[barcodePropertyId] as Map
        barcodeEntry.value == [text: 'PLATE-BC-0001']
        barcodeEntry.txn_id == txnId
        barcodeEntry.commit_id == header.commit_id
        barcodeEntry.msg_uuid == barcodeAssignmentMsg.uuid()
        barcodeEntry.applied_at != null

        and: 'no standalone assignment root was created for the object-targeted form'
        mongoTemplate.count(Query.query(Criteria.where('properties.object_id').is(plateLocId)),
                PPY_COLLECTION).block(MONGO_BLOCK_TIMEOUT) == 0L

        and: 'the unregistered assignment is gated and never lands on the root'
        !((locDoc.property_values as Map).containsKey(unregisteredPropertyId))
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
