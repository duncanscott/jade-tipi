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
 * End-to-end integration coverage for the Kafka entity-submission path.
 *
 * <p>Publishes one canonical
 * {@code open + typ + typ-update-add_property + ent + commit} transaction to a
 * per-spec Kafka topic and waits for {@code TransactionMessageListener} plus
 * the {@code CommittedTransactionMaterializer} to land:
 * <ul>
 *   <li>a committed {@code txn} header in the {@code txn} collection,</li>
 *   <li>a root-shaped bare entity-type {@code typ} document in the
 *       {@code typ} collection, carrying top-level {@code _id}, {@code id},
 *       {@code collection}, {@code type_id == null}, inline
 *       {@code properties.name}/{@code description}, the
 *       {@code properties.property_refs.<property_id>} reference entry written
 *       by the {@code typ + update add_property} message, and
 *       {@code _head.provenance} pointing at the original create message,</li>
 *   <li>a root-shaped {@code ent} document in the {@code ent} collection,
 *       carrying top-level {@code _id}, {@code id}, {@code collection},
 *       {@code type_id} that matches the entity-type id, explicit empty
 *       {@code properties}/{@code links}, and
 *       {@code _head.provenance.txn_id}/{@code msg_uuid}.</li>
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
 *     --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !EntityCreateKafkaMaterializeIntegrationSpec.kafkaIntegrationGateOpen() })
class EntityCreateKafkaMaterializeIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-ent-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-ent-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
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
    String entityId
    String entityTypeId
    String propertyDefinitionId

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
        entityId = "jadetipi-itest-ent~ent~plate_${featureUuid}"
        entityTypeId = "jadetipi-itest-ent~typ~plate_96_${featureUuid}"
        propertyDefinitionId = "jadetipi-itest-ent~ppy~barcode_${featureUuid}"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (entityTypeId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(entityTypeId)),
                    TYP_COLLECTION).block(Duration.ofSeconds(10))
        }
        if (entityId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(entityId)),
                    ENT_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    def 'open + typ + typ-update-add_property + ent + commit materializes the property reference on the typ root and the ent root with linked type_id'() {
        given: 'one canonical entity transaction (open + typ + typ-update + ent + commit)'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                hint: 'opened from ent kafka integration test'
        ])
        Message typMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : entityTypeId,
                name       : 'plate_96',
                description: '96-well sample plate'
        ] as Map<String, Object>)
        Message typUpdateMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : entityTypeId,
                operation  : 'add_property',
                property_id: propertyDefinitionId,
                required   : true
        ] as Map<String, Object>)
        Message entMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : entityId,
                type_id   : entityTypeId,
                properties: [:],
                links     : [:]
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'one entity-type, one type-update add_property, and one entity created'
        ])

        when: 'all five records are produced to the test topic'
        send(openMsg)
        send(typMsg)
        send(typUpdateMsg)
        send(entMsg)
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

        and: 'the bare entity-type typ document is materialized in root shape with the add_property reference under properties.property_refs'
        Map typDoc = awaitMongo(
                { mongoTemplate.findById(entityTypeId, Map, TYP_COLLECTION) },
                { Map d ->
                    Map p = (d?.properties as Map)
                    p?.property_refs instanceof Map &&
                            ((Map) p.property_refs).containsKey(propertyDefinitionId)
                },
                'root-shaped bare entity-type typ document with property_refs populated'
        )
        typDoc['_id'] == entityTypeId
        typDoc.id == entityTypeId
        typDoc.collection == 'typ'
        typDoc.type_id == null
        Map typProperties = typDoc.properties as Map
        typProperties.name == 'plate_96'
        typProperties.description == '96-well sample plate'
        !typProperties.containsKey('id')
        !typProperties.containsKey('type_id')
        !typProperties.containsKey('kind')
        typDoc.links == [:]

        and: 'the property_refs sub-map carries the wire required value verbatim, keyed by the property-definition id'
        Map propertyRefs = typProperties.property_refs as Map
        propertyRefs.size() == 1
        propertyRefs[propertyDefinitionId] == [required: true]

        and: 'typ _head still carries the create-time projection provenance; add_property does not rewrite _head.provenance'
        Map typHead = typDoc._head as Map
        typHead.schema_version == 1
        typHead.document_kind == 'root'
        typHead.root_id == entityTypeId
        Map typProvenance = typHead.provenance as Map
        typProvenance.txn_id == txnId
        typProvenance.msg_uuid == typMsg.uuid()
        typProvenance.collection == 'typ'
        typProvenance.action == 'create'

        and: 'the ent document is materialized in root shape with top-level type_id matching the materialized typ id'
        Map entDoc = awaitMongo(
                { mongoTemplate.findById(entityId, Map, ENT_COLLECTION) },
                { Map d -> d != null },
                'root-shaped ent document'
        )
        entDoc['_id'] == entityId
        entDoc.id == entityId
        entDoc.collection == 'ent'
        entDoc.type_id == entityTypeId
        entDoc.type_id == typDoc['_id']
        entDoc.properties == [:]
        entDoc.links == [:]

        and: '_head carries projection provenance pointing at this txn and msg uuid'
        Map head = entDoc._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == entityId
        Map provenance = head.provenance as Map
        provenance.txn_id == txnId
        provenance.commit_id instanceof String
        ((String) provenance.commit_id).length() > 0
        provenance.msg_uuid == entMsg.uuid()
        provenance.collection == 'ent'
        provenance.action == 'create'
        provenance.committed_at != null
        provenance.materialized_at != null
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
