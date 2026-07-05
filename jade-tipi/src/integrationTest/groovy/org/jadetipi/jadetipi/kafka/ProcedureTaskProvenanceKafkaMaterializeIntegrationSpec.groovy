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
 * End-to-end TASK-048 coverage for the full procedure/task provenance loop
 * in one transaction: {@code open} → {@code ppy + create kind=definition}
 * (note) → {@code typ + create kind=procedure_type} (dna_pooling) →
 * {@code typ + create kind=task_type} (dna_pooling_task carrying
 * {@code procedure_type_id}) → {@code typ + update add_property} (note on
 * the task type) → three {@code typ + create kind=link_type} declarations
 * (task_input, fulfills, produced_by) → {@code typ + create} (material) →
 * three typed {@code ent + create} records (two inputs, one pool output) →
 * {@code tsk + create} → two task_input {@code lnk + create} records →
 * {@code prc + create} carrying the top-level {@code output_input} map →
 * fulfills and produced_by {@code lnk + create} records → an
 * object-targeted {@code ppy + create kind=assignment} (note onto the tsk
 * root) → {@code commit}.
 *
 * <p>Asserts the typed {@code prc} root with {@code output_input} hoisted
 * top-level (and absent from {@code properties}), the typed {@code tsk}
 * root with the projected {@code property_values.<note>} entry, the task
 * type's {@code procedure_type_id}, and the three link records wiring
 * task → inputs, procedure → task, and output → procedure. All IDs follow
 * the transaction-UUID form of the object identifier convention.
 *
 * <p>Same opt-in gates as the other Kafka specs. Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*ProcedureTaskProvenanceKafkaMaterializeIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles('test')
@IgnoreIf({ !ProcedureTaskProvenanceKafkaMaterializeIntegrationSpec.kafkaIntegrationGateOpen() })
class ProcedureTaskProvenanceKafkaMaterializeIntegrationSpec extends Specification {

    private static final String BOOTSTRAP_SERVERS =
            System.getenv('KAFKA_BOOTSTRAP_SERVERS') ?: 'localhost:9092'
    private static final String SHORT_UUID =
            UUID.randomUUID().toString().substring(0, 8)
    private static final String TEST_TOPIC = "jdtp-txn-itest-prctsk-${SHORT_UUID}"
    private static final String CONSUMER_GROUP = "jadetipi-itest-prctsk-${SHORT_UUID}"
    private static final String TXN_COLLECTION = 'txn'
    private static final String PPY_COLLECTION = 'ppy'
    private static final String TYP_COLLECTION = 'typ'
    private static final String ENT_COLLECTION = 'ent'
    private static final String LNK_COLLECTION = 'lnk'
    private static final String PRC_COLLECTION = 'prc'
    private static final String TSK_COLLECTION = 'tsk'
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
    String notePropertyId
    String procedureTypeId
    String taskTypeId
    String taskInputTypeId
    String fulfillsTypeId
    String producedByTypeId
    String materialTypeId
    String sampleAEntId
    String sampleBEntId
    String poolEntId
    String taskId
    String procedureId
    String inputLinkAId
    String inputLinkBId
    String fulfillsLinkId
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
        notePropertyId = "${idPrefix}~ppy~note"
        procedureTypeId = "${idPrefix}~typ~dna_pooling"
        taskTypeId = "${idPrefix}~typ~dna_pooling_task"
        taskInputTypeId = "${idPrefix}~typ~task_input"
        fulfillsTypeId = "${idPrefix}~typ~fulfills"
        producedByTypeId = "${idPrefix}~typ~produced_by"
        materialTypeId = "${idPrefix}~typ~material"
        sampleAEntId = "${idPrefix}~ent~sample_a"
        sampleBEntId = "${idPrefix}~ent~sample_b"
        poolEntId = "${idPrefix}~ent~pool_1"
        taskId = "${idPrefix}~tsk~pool_batch_7"
        procedureId = "${idPrefix}~prc~pool_run_1"
        inputLinkAId = "${idPrefix}~lnk~input_sample_a"
        inputLinkBId = "${idPrefix}~lnk~input_sample_b"
        fulfillsLinkId = "${idPrefix}~lnk~pool_run_1_fulfills"
        producedByLinkId = "${idPrefix}~lnk~pool_1_produced_by"
    }

    def cleanup() {
        if (txnId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),
                    TXN_COLLECTION).block(Duration.ofSeconds(10))
        }
        [(PPY_COLLECTION): [notePropertyId],
         (TYP_COLLECTION): [procedureTypeId, taskTypeId, taskInputTypeId,
                            fulfillsTypeId, producedByTypeId, materialTypeId],
         (ENT_COLLECTION): [sampleAEntId, sampleBEntId, poolEntId],
         (TSK_COLLECTION): [taskId],
         (PRC_COLLECTION): [procedureId],
         (LNK_COLLECTION): [inputLinkAId, inputLinkBId, fulfillsLinkId,
                            producedByLinkId]].each { String collection, List<String> ids ->
            ids.findAll { it != null }.each { String id ->
                mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                        collection).block(Duration.ofSeconds(10))
            }
        }
    }

    def 'committed procedure-task transaction materializes the full provenance loop with output_input hoisted onto the prc root'() {
        given: 'the full task/procedure provenance sequence in one transaction'
        Message openMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.OPEN, [
                description: 'dna pooling task and procedure provenance demo'
        ])
        Message noteDefinitionMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind        : 'definition',
                id          : notePropertyId,
                name        : 'note',
                value_schema: [
                        type      : 'object',
                        required  : ['text'],
                        properties: [text: [type: 'string']]
                ]
        ] as Map<String, Object>)
        Message procedureTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind       : 'procedure_type',
                id         : procedureTypeId,
                name       : 'dna_pooling',
                description: 'pool DNA samples into library pools'
        ] as Map<String, Object>)
        Message taskTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind             : 'task_type',
                id               : taskTypeId,
                name             : 'dna_pooling_task',
                procedure_type_id: procedureTypeId,
                procedure_name   : 'dna_pooling'
        ] as Map<String, Object>)
        Message taskTypeAddNoteMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.UPDATE, [
                id         : taskTypeId,
                operation  : 'add_property',
                property_id: notePropertyId
        ] as Map<String, Object>)
        Message taskInputTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : taskInputTypeId,
                name                     : 'task_input',
                left_role                : 'task',
                right_role               : 'input',
                left_to_right_label      : 'has_input',
                right_to_left_label      : 'input_of',
                allowed_left_collections : ['tsk'],
                allowed_right_collections: ['ent']
        ] as Map<String, Object>)
        Message fulfillsTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : fulfillsTypeId,
                name                     : 'fulfills',
                left_role                : 'procedure',
                right_role               : 'task',
                left_to_right_label      : 'fulfills',
                right_to_left_label      : 'fulfilled_by',
                allowed_left_collections : ['prc'],
                allowed_right_collections: ['tsk']
        ] as Map<String, Object>)
        Message producedByTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                kind                     : 'link_type',
                id                       : producedByTypeId,
                name                     : 'produced_by',
                left_role                : 'output',
                right_role               : 'procedure',
                left_to_right_label      : 'produced_by',
                right_to_left_label      : 'produced',
                allowed_left_collections : ['ent'],
                allowed_right_collections: ['prc']
        ] as Map<String, Object>)
        Message materialTypeMsg = Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
                id         : materialTypeId,
                name       : 'material',
                description: 'physical sample material'
        ] as Map<String, Object>)
        Message sampleAMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : sampleAEntId,
                type_id   : materialTypeId,
                properties: [name: 'sample_a'],
                links     : [:]
        ] as Map<String, Object>)
        Message sampleBMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : sampleBEntId,
                type_id   : materialTypeId,
                properties: [name: 'sample_b'],
                links     : [:]
        ] as Map<String, Object>)
        Message poolMsg = Message.newInstance(txn, JtpCollection.ENTITY, Action.CREATE, [
                id        : poolEntId,
                type_id   : materialTypeId,
                properties: [name: 'pool_1'],
                links     : [:]
        ] as Map<String, Object>)
        Message taskMsg = Message.newInstance(txn, JtpCollection.TASK, Action.CREATE, [
                id     : taskId,
                type_id: taskTypeId,
                name   : 'pool_batch_7'
        ] as Map<String, Object>)
        Message inputLinkAMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id     : inputLinkAId,
                type_id: taskInputTypeId,
                left   : taskId,
                right  : sampleAEntId
        ] as Map<String, Object>)
        Message inputLinkBMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id     : inputLinkBId,
                type_id: taskInputTypeId,
                left   : taskId,
                right  : sampleBEntId
        ] as Map<String, Object>)
        Message procedureMsg = Message.newInstance(txn, JtpCollection.PROCEDURE, Action.CREATE, [
                id          : procedureId,
                type_id     : procedureTypeId,
                name        : 'pool_run_1',
                output_input: [
                        (poolEntId): [
                                (sampleAEntId): [volume: 5.0d],
                                (sampleBEntId): [volume: 7.5d]
                        ]
                ]
        ] as Map<String, Object>)
        Message fulfillsLinkMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id     : fulfillsLinkId,
                type_id: fulfillsTypeId,
                left   : procedureId,
                right  : taskId
        ] as Map<String, Object>)
        Message producedByLinkMsg = Message.newInstance(txn, JtpCollection.LINK, Action.CREATE, [
                id     : producedByLinkId,
                type_id: producedByTypeId,
                left   : poolEntId,
                right  : procedureId
        ] as Map<String, Object>)
        Message noteAssignmentMsg = Message.newInstance(txn, JtpCollection.PROPERTY, Action.CREATE, [
                kind             : 'assignment',
                object_collection: 'tsk',
                object_id        : taskId,
                property_id      : notePropertyId,
                value            : [text: 'rush order for batch 7']
        ] as Map<String, Object>)
        Message commitMsg = Message.newInstance(txn, JtpCollection.TRANSACTION, Action.COMMIT, [
                summary: 'task intention fulfilled by a pooling procedure with recorded contributions'
        ])

        when: 'all nineteen records are produced to the test topic in order'
        [openMsg, noteDefinitionMsg, procedureTypeMsg, taskTypeMsg, taskTypeAddNoteMsg,
         taskInputTypeMsg, fulfillsTypeMsg, producedByTypeMsg, materialTypeMsg,
         sampleAMsg, sampleBMsg, poolMsg, taskMsg, inputLinkAMsg, inputLinkBMsg,
         procedureMsg, fulfillsLinkMsg, producedByLinkMsg, noteAssignmentMsg,
         commitMsg].each { send(it) }

        then: 'the transaction header reaches committed state with a backend commit_id'
        Map header = awaitMongo(
                { mongoTemplate.findById(txnId, Map, TXN_COLLECTION) },
                { Map h -> h?.state == 'committed' && h?.commit_id != null },
                'committed transaction header'
        )
        header.state == 'committed'

        and: 'the task type root carries the procedure_type_id and kind discriminator'
        Map taskTypeDoc = awaitMongo(
                { mongoTemplate.findById(taskTypeId, Map, TYP_COLLECTION) },
                { Map d -> d != null && ((Map) d.properties)?.property_refs != null },
                'task type root with the note registration'
        )
        (taskTypeDoc.properties as Map).kind == 'task_type'
        (taskTypeDoc.properties as Map).procedure_type_id == procedureTypeId
        ((taskTypeDoc.properties as Map).property_refs as Map).containsKey(notePropertyId)

        and: 'the prc root is typed by the procedure type with output_input hoisted top-level'
        Map prcDoc = awaitMongo(
                { mongoTemplate.findById(procedureId, Map, PRC_COLLECTION) },
                { Map d -> d != null },
                'prc root'
        )
        prcDoc.collection == 'prc'
        prcDoc.type_id == procedureTypeId
        Map outputs = prcDoc.output_input as Map
        outputs.keySet() == [poolEntId] as Set
        Map contributions = outputs[poolEntId] as Map
        contributions.keySet() == [sampleAEntId, sampleBEntId] as Set
        (contributions[sampleAEntId] as Map).volume == 5.0d
        (contributions[sampleBEntId] as Map).volume == 7.5d

        and: 'output_input stays out of the prc inline properties bag'
        (prcDoc.properties as Map).name == 'pool_run_1'
        !((prcDoc.properties as Map).containsKey('output_input'))

        and: 'the tsk root is typed by the task type and carries the projected note value'
        Map tskDoc = awaitMongo(
                { mongoTemplate.findById(taskId, Map, TSK_COLLECTION) },
                { Map d -> d != null && ((Map) d.property_values)?.containsKey(notePropertyId) },
                'tsk root with projected property_values entry'
        )
        tskDoc.collection == 'tsk'
        tskDoc.type_id == taskTypeId
        (tskDoc.properties as Map).name == 'pool_batch_7'
        Map noteEntry = (tskDoc.property_values as Map)[notePropertyId] as Map
        noteEntry.value == [text: 'rush order for batch 7']
        noteEntry.txn_id == txnId
        noteEntry.commit_id == header.commit_id
        noteEntry.msg_uuid == noteAssignmentMsg.uuid()
        noteEntry.applied_at != null

        and: 'the input links join the task to both input ents under the task_input type'
        Map inputLinkADoc = awaitMongo(
                { mongoTemplate.findById(inputLinkAId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'task_input link for sample_a'
        )
        inputLinkADoc.type_id == taskInputTypeId
        inputLinkADoc.left == taskId
        inputLinkADoc.right == sampleAEntId
        Map inputLinkBDoc = awaitMongo(
                { mongoTemplate.findById(inputLinkBId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'task_input link for sample_b'
        )
        inputLinkBDoc.left == taskId
        inputLinkBDoc.right == sampleBEntId

        and: 'the fulfills link records the procedure that fulfilled the task'
        Map fulfillsDoc = awaitMongo(
                { mongoTemplate.findById(fulfillsLinkId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'fulfills link'
        )
        fulfillsDoc.type_id == fulfillsTypeId
        fulfillsDoc.left == procedureId
        fulfillsDoc.right == taskId

        and: 'the produced_by link joins the pool output to the procedure that created it'
        Map producedByDoc = awaitMongo(
                { mongoTemplate.findById(producedByLinkId, Map, LNK_COLLECTION) },
                { Map d -> d != null },
                'produced_by link'
        )
        producedByDoc.type_id == producedByTypeId
        producedByDoc.left == poolEntId
        producedByDoc.right == procedureId

        and: 'the pool output materialized as a typed ent root'
        Map poolDoc = awaitMongo(
                { mongoTemplate.findById(poolEntId, Map, ENT_COLLECTION) },
                { Map d -> d != null },
                'pool ent root'
        )
        poolDoc.type_id == materialTypeId
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
