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
 * End-to-end integration coverage for the Kafka property-assignment
 * submission path.
 *
 * <p>Publishes one canonical human-readable property-loop transaction —
 * {@code open}, {@code ppy + create kind=definition}, {@code typ + create},
 * {@code typ + update add_property}, {@code ent + create},
 * {@code ppy + create kind=assignment}, plus a second assignment whose
 * property is intentionally never registered on the type, then
 * {@code commit} — and waits for {@code TransactionMessageListener} plus
 * {@code CommittedTransactionMaterializer} to land:
 * <ul>
 *   <li>a committed {@code txn} header in the {@code txn} collection,</li>
 *   <li>a root-shaped assignment document in the {@code ppy} collection
 *       whose ID is the composite {@code <entity_id>~<property_id>},
 *       carrying root {@code properties.kind == "assignment"},
 *       {@code properties.entity_id}, {@code properties.property_id}, the
 *       verbatim object-shaped {@code properties.value}, empty
 *       {@code links}, and {@code _head.provenance} pointing at the
 *       original assignment message,</li>
 *   <li>no materialized document for the unregistered assignment — the
 *       type-registration gate counts it as
 *       {@code skippedUnregisteredProperty} and never inserts.</li>
 * </ul>
 *
 * <p>Skip / run conditions (deliberately opt-in):
 * <ul>
 *   <li>Environment variable {@code JADETIPI_IT_KAFKA} must be set to
 *       {@code 1}, {@code true}, {@code TRUE}, or {@code yes}.</li>
 *   <li>A 2-second {@code AdminClient.describeCluster} probe against
 *       {@code KAFKA_BOOTSTRAP_SERVERS} (or {@code localhost:9092}) must
 *       succeed.</li>
 * </ul>
 *
 * <p>If either gate fails, the spec is skipped via Spock's
 * {@code @IgnoreIf}; the Spring context is never loaded.
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*PropertyAssignmentKafkaMaterializeIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !PropertyAssignmentKafkaMaterializeIntegrationSpec.kafkaIntegrationGateOpen() })
class PropertyAssignmentKafkaMaterializeIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-ppyasn-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-ppyasn-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String ENT_COLLECTION = 'ent'
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
    String propertyDefinitionId
    String unregisteredPropertyId
    String entityTypeId
    String entityId
    String assignmentId
    String unregisteredAssignmentId

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
        String featureUuid = UUID.randomUUID().toString().substring(0, 8)
        propertyDefinitionId = "jadetipi-itest-ppyasn~pp~barcode_${featureUuid}"
        unregisteredPropertyId = "jadetipi-itest-ppyasn~pp~volume_${featureUuid}"
        entityTypeId = "jadetipi-itest-ppyasn~ty~plate_96_${featureUuid}"
        entityId = "jadetipi-itest-ppyasn~en~plate_${featureUuid}"
        assignmentId = "${entityId}~${propertyDefinitionId}"
        unregisteredAssignmentId = "${entityId}~${unregisteredPropertyId}"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(PPY_COLLECTION): [propertyDefinitionId, unregisteredPropertyId, assignmentId, unregisteredAssignmentId],
         (TYP_COLLECTION): [entityTypeId],
         (ENT_COLLECTION): [entityId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'committed property-loop transaction materializes the registered assignment root and gates the unregistered one'() {
        given: 'one canonical property-loop transaction with a registered and an unregistered assignment'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'opened from ppy assignment kafka integration test'
        ])
        Message ppyDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : propertyDefinitionId,
                name        : 'barcode',
                value_schema: [
                        type      : 'object',
                        required  : ['text'],
                        properties: [text: [type: 'string']]
                ]
        ] as Map<String, Object>)
        Message typCreateMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : entityTypeId,
                name       : 'plate_96',
                description: '96-well sample plate'
        ] as Map<String, Object>)
        Message typAddPropertyMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : entityTypeId,
                operation  : 'add_property',
                property_id: propertyDefinitionId,
                required   : true
        ] as Map<String, Object>)
        Message entCreateMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : entityId,
                type_id   : entityTypeId,
                properties: [:],
                links     : [:]
        ] as Map<String, Object>)
        Message assignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind       : 'assignment',
                id         : assignmentId,
                entity_id  : entityId,
                property_id: propertyDefinitionId,
                value      : [text: 'barcode-1']
        ] as Map<String, Object>)
        Message unregisteredAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind       : 'assignment',
                id         : unregisteredAssignmentId,
                entity_id  : entityId,
                property_id: unregisteredPropertyId,
                value      : [number: 10, unit_id: 'jade-tipi-org~dev~liter~milli~ml~SI']
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'property loop with one registered and one unregistered assignment'
        ])

        when: 'all eight records are produced to the test topic in order'
        [openMsg, ppyDefinitionMsg, typCreateMsg, typAddPropertyMsg,
         entCreateMsg, assignmentMsg, unregisteredAssignmentMsg, commitMsg].each { send(it) }

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header.state == 'committed'

        and: 'the entity type root carries the registered property reference'
        Map typDoc = awaitMongo(
                { mongoTemplate.findById(entityTypeId, Map, TYP_COLLECTION) },
                { Map d -> d != null && ((Map) d.properties)?.property_refs != null },
                'typ root with property_refs'
        )
        Map propertyRefs = (typDoc.properties as Map).property_refs as Map
        propertyRefs[propertyDefinitionId] == [required: true]
        !propertyRefs.containsKey(unregisteredPropertyId)

        and: 'the registered assignment materializes as its own root-shaped ppy document'
        Map assignmentDoc = awaitMongo(
                { mongoTemplate.findById(assignmentId, Map, PPY_COLLECTION) },
                { Map d -> d != null },
                'root-shaped ppy assignment document'
        )
        assignmentDoc['_id'] == assignmentId
        assignmentDoc.id == assignmentId
        assignmentDoc.collection == 'ppy'
        assignmentDoc.type_id == null
        Map assignmentProperties = assignmentDoc.properties as Map
        assignmentProperties.kind == 'assignment'
        assignmentProperties.entity_id == entityId
        assignmentProperties.property_id == propertyDefinitionId
        assignmentProperties.value == [text: 'barcode-1']
        !assignmentProperties.containsKey('id')
        assignmentDoc.links == [:]

        and: '_head carries projection provenance pointing at this txn and the assignment msg uuid'
        Map head = assignmentDoc._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == assignmentId
        Map provenance = head.provenance as Map
        provenance.txn_id == txnId
        provenance.msg_uuid == assignmentMsg.uuid()
        provenance.collection == 'ppy'
        provenance.action == 'create'

        and: 'the entity root properties map is not rewritten by the assignment'
        Map entDoc = mongoTemplate.findById(entityId, Map, ENT_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        entDoc != null
        entDoc.properties == [:]

        and: 'the unregistered assignment is gated and never materialized'
        Map unregisteredDoc = mongoTemplate.findById(unregisteredAssignmentId, Map, PPY_COLLECTION)
                .block(MONGO_BLOCK_TIMEOUT)
        unregisteredDoc == null
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
