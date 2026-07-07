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
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.jadetipi.dto.message.Message
import org.jadetipi.dto.util.JsonMapper

import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Publishes canonical JDTP {@link Message}s to the transaction topic for the
 * import driver (TASK-066). Keyed by transaction id (partition affinity =
 * strict per-transaction ordering on the single-partition topic),
 * acks=all, and each send awaited — the driver's resume semantics assume a
 * message either landed or the drive failed loudly.
 */
@Slf4j
class ImportMessagePublisher {

    private static final long SEND_TIMEOUT_SECONDS = 30

    private final KafkaProducer<String, byte[]> producer
    private final String topic

    ImportMessagePublisher(String bootstrapServers, String topic) {
        this.topic = topic
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')
        this.producer = new KafkaProducer<>(props)
        log.info('Import publisher ready: topic={}, bootstrap={}', topic, bootstrapServers)
    }

    /** Test seam: wrap an existing producer. */
    protected ImportMessagePublisher(KafkaProducer<String, byte[]> producer, String topic) {
        this.producer = producer
        this.topic = topic
    }

    void publish(Message message, String key) {
        byte[] bytes = JsonMapper.toBytes(message)
        producer.send(new ProducerRecord<String, byte[]>(topic, key, bytes))
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    void flush() {
        producer.flush()
    }

    void close() {
        producer.close(Duration.ofSeconds(5))
    }
}
