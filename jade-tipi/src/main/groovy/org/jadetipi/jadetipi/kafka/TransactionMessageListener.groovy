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

import com.fasterxml.jackson.core.JsonProcessingException
import groovy.util.logging.Slf4j
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jadetipi.dto.message.Message
import org.jadetipi.dto.util.JsonMapper
import org.jadetipi.dto.util.ValidationException
import org.jadetipi.jadetipi.service.PersistResult
import org.jadetipi.jadetipi.service.TransactionMessagePersistenceService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

import java.time.Duration

/**
 * Kafka listener that consumes canonical Jade-Tipi {@link Message} records and
 * forwards them to the transaction-message persistence service.
 *
 * <p>The listener owns all Kafka-client and Spring-Kafka imports; the persistence
 * service stays Kafka-free.
 *
 * <p>Acknowledgement semantics:
 * <ul>
 *   <li>Malformed JSON or schema-invalid messages are logged and acknowledged
 *       (poison-pill skip) so a single bad record does not stall the consumer.</li>
 *   <li>Successful persistence acknowledges the record.</li>
 *   <li>Persistence failures (including conflicting duplicates and commit-before-open)
 *       leave the record un-acked so the listener container can retry.</li>
 * </ul>
 */
@Slf4j
@Component
class TransactionMessageListener {

    private final TransactionMessagePersistenceService persistenceService
    private final KafkaIngestProperties properties

    TransactionMessageListener(TransactionMessagePersistenceService persistenceService,
                               KafkaIngestProperties properties) {
        this.persistenceService = persistenceService
        this.properties = properties
    }

    @KafkaListener(
            topicPattern = '${jadetipi.kafka.txn-topic-pattern}',
            autoStartup = '${jadetipi.kafka.enabled}',
            groupId = '${spring.kafka.consumer.group-id:jadetipi-txn-ingest}'
    )
    void onMessage(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        Message message
        try {
            message = JsonMapper.fromBytes(record.value(), Message.class)
        } catch (IOException ex) {
            log.error('Skipping unparseable Kafka record: topic={}, partition={}, offset={}, key={}',
                    record.topic(), record.partition(), record.offset(), record.key(), ex)
            acknowledgment.acknowledge()
            return
        }

        try {
            message.validate()
        } catch (ValidationException ex) {
            log.error('Skipping schema-invalid Kafka record: topic={}, partition={}, offset={}, errors={}',
                    record.topic(), record.partition(), record.offset(),
                    ex.message)
            acknowledgment.acknowledge()
            return
        } catch (JsonProcessingException ex) {
            log.error('Skipping unencodable Kafka record on validate: topic={}, partition={}, offset={}',
                    record.topic(), record.partition(), record.offset(), ex)
            acknowledgment.acknowledge()
            return
        }

        KafkaSourceMetadata source = new KafkaSourceMetadata(
                topic: record.topic(),
                partition: record.partition(),
                offset: record.offset(),
                timestampMs: record.timestamp()
        )

        Duration timeout = Duration.ofSeconds(Math.max(1, properties.persistTimeoutSeconds))
        PersistResult result = persistenceService.persist(message, source).block(timeout)
        log.debug('Persisted Kafka record: topic={}, partition={}, offset={}, result={}',
                record.topic(), record.partition(), record.offset(), result)
        acknowledgment.acknowledge()
    }
}
