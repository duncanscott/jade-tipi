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
package org.jadetipi.kafka.cli

import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.ByteArraySerializer

import org.jadetipi.dto.message.Message
import org.jadetipi.dto.util.MessageMapper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Future

@Slf4j
class KafkaCli {

    private static final String DEFAULT_CLIENT_ID = 'kli'
    private static final String DEFAULT_CLIENT_SECRET = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    private static final String DEFAULT_BOOTSTRAP_SERVERS = 'localhost:9092'
    private static final String ENV_PREFIX = 'KLI'

    static void main(String[] args) {
        new KafkaCli().run(args)
    }

    void run(String[] args) {
        def cli = new CliBuilder(
                name: 'kli',
                usage: 'kli <command> [options]',
                footer: "\nEnvironment overrides: ${ENV_PREFIX}_BOOTSTRAP_SERVERS, ${ENV_PREFIX}_TOPIC"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            v(longOpt: 'verbose', 'Enable verbose output')
        }

        OptionAccessor topLevel = cli.parse(args)
        if (!topLevel) {
            System.exit(1)
        }

        if (topLevel.h) {
            printGlobalUsage(cli)
            return
        }

        boolean globalVerbose = topLevel.v
        List<String> remaining = topLevel.arguments()

        if (!remaining) {
            printGlobalUsage(cli)
            return
        }

        String command = remaining[0]
        String[] commandArgs = remaining.size() > 1 ? (remaining[1..-1] as String[]) : new String[0]

        switch (command) {
            case 'publish':
                handlePublish(commandArgs, globalVerbose)
                break
            case 'help':
                printGlobalUsage(cli)
                break
            default:
                printError("Unknown command: ${command}\n")
                printGlobalUsage(cli)
                System.exit(1)
        }
    }

    private void handlePublish(String[] args, boolean globalVerbose) {
        String defaultBootstrapServers = resolveEnv('BOOTSTRAP_SERVERS', DEFAULT_BOOTSTRAP_SERVERS)
        String defaultTopic = resolveEnv('TOPIC', '')

        def cli = new CliBuilder(
                name: 'kli publish',
                usage: 'kli publish --topic <topic> --file <message.json> [options]',
                header: 'Publish a message to a Kafka topic.',
                footer: "\nEnvironment overrides: ${ENV_PREFIX}_BOOTSTRAP_SERVERS, ${ENV_PREFIX}_TOPIC"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'bootstrap-servers', args: 1, argName: 'servers',
                    'Kafka bootstrap servers', defaultValue: defaultBootstrapServers)
            _(longOpt: 'topic', args: 1, argName: 'topic', 'Kafka topic to publish to',
                    defaultValue: defaultTopic)
            _(longOpt: 'file', args: 1, argName: 'path', 'Path to JSON file containing the message')
            _(longOpt: 'key', args: 1, argName: 'key', 'Optional message key')
            v(longOpt: 'verbose', 'Enable verbose output')
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            System.exit(1)
        }
        if (options.h) {
            cli.usage()
            return
        }

        String bootstrapServers = options.'bootstrap-servers' ?: defaultBootstrapServers
        String topic = options.'topic' ?: defaultTopic
        String filePath = options.'file'
        String key = options.'key'
        boolean verbose = options.'verbose' || globalVerbose

        if (!topic?.trim()) {
            printError('A topic is required. Use --topic or set ${ENV_PREFIX}_TOPIC.')
            System.exit(1)
        }
        if (!filePath?.trim()) {
            printError('A message file is required. Use --file <path>.')
            System.exit(1)
        }

        Path messageFile = Paths.get(filePath)
        if (!Files.exists(messageFile)) {
            printError("Message file not found: ${filePath}")
            System.exit(1)
        }
        if (!Files.isReadable(messageFile)) {
            printError("Message file is not readable: ${filePath}")
            System.exit(1)
        }

        String json = Files.readString(messageFile)
        Message message
        try {
            message = MessageMapper.fromJson(json)
        } catch (Exception e) {
            printError("Failed to parse message JSON: ${e.message}")
            System.exit(1)
        }

        if (verbose) {
            println "Bootstrap servers: ${bootstrapServers}"
            println "Topic:            ${topic}"
            println "Message ID:       ${message.getId()}"
            println "Action:           ${message.action()}"
            if (key) {
                println "Key:              ${key}"
            }
            println ''
            println JsonOutput.prettyPrint(json)
            println ''
        }

        byte[] messageBytes
        try {
            messageBytes = MessageMapper.toBytes(message)
        } catch (Exception e) {
            printError("Failed to serialize message: ${e.message}")
            System.exit(1)
        }

        publishMessage(bootstrapServers, topic, key, messageBytes, message, verbose)
    }

    private void publishMessage(String bootstrapServers, String topic, String key,
                                byte[] messageBytes, Message message, boolean verbose) {
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')

        KafkaProducer<String, byte[]> producer = null
        try {
            producer = new KafkaProducer<>(props)

            ProducerRecord<String, byte[]> record = key
                    ? new ProducerRecord<>(topic, key, messageBytes)
                    : new ProducerRecord<>(topic, messageBytes)

            log.info("Publishing message {} to topic {}", message.getId(), topic)

            Future<RecordMetadata> future = producer.send(record)
            RecordMetadata metadata = future.get()

            println message.getId()
            log.info("Published message {} to {}:partition={}:offset={}",
                    message.getId(), metadata.topic(), metadata.partition(), metadata.offset())

            if (verbose) {
                println "Published to partition ${metadata.partition()} at offset ${metadata.offset()}"
            }
        } catch (Exception e) {
            printError("Failed to publish message: ${e.message}")
            System.exit(1)
        } finally {
            if (producer != null) {
                producer.close()
            }
        }
    }

    private String resolveEnv(String suffix, String fallback) {
        return System.getenv("${ENV_PREFIX}_${suffix}") ?: fallback
    }

    private void printGlobalUsage(CliBuilder cli) {
        println """Kli - Jade Tipi Kafka message publisher

Usage:
  kli publish --topic <topic> --file <message.json> [options]
  kli help

Commands:
  publish   Publish a message to a Kafka topic.
"""
        cli.usage()
    }

    private void printError(Object message) {
        String text = message?.toString() ?: ''
        String plain = text.startsWith('ERROR ') ? text.substring('ERROR '.length()) : text
        log.error(plain)
        if (text.startsWith('ERROR ')) {
            System.err.println(text)
        } else {
            System.err.println("ERROR ${text}")
        }
    }
}
