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

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.Future

@Slf4j
class KafkaCli {

    private static final String DEFAULT_CLIENT_ID = 'kli'
    private static final String DEFAULT_KEYCLOAK_URL = 'http://localhost:8484'
    private static final String DEFAULT_REALM = 'jade-tipi'
    private static final String DEFAULT_BOOTSTRAP_SERVERS = 'localhost:9092'
    private static final String ENV_PREFIX = 'KLI'
    private static final String SESSION_ENV_VAR = 'KLI_SESSION'

    static void main(String[] args) {
        new KafkaCli().run(args)
    }

    void run(String[] args) {
        def cli = new CliBuilder(
                name: 'kli',
                usage: 'kli <command> [options]',
                footer: "\nEnvironment overrides: ${ENV_PREFIX}_KEYCLOAK_URL, ${ENV_PREFIX}_REALM, ${ENV_PREFIX}_CLIENT_ID, ${ENV_PREFIX}_BOOTSTRAP_SERVERS, ${ENV_PREFIX}_TOPIC"
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
            case 'login':
                handleLogin(commandArgs, globalVerbose)
                break
            case 'logout':
                handleLogout(commandArgs, globalVerbose)
                break
            case 'status':
                handleStatus(commandArgs, globalVerbose)
                break
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

    // ---- login command ----

    private void handleLogin(String[] args, boolean globalVerbose) {
        String defaultKeycloakUrl = resolveEnv('KEYCLOAK_URL', DEFAULT_KEYCLOAK_URL)
        String defaultRealm = resolveEnv('REALM', DEFAULT_REALM)
        String defaultClientId = resolveEnv('CLIENT_ID', DEFAULT_CLIENT_ID)

        def cli = new CliBuilder(
                name: 'kli login',
                usage: 'kli login [options]',
                header: 'Authenticate via device authorization grant.',
                footer: "\nUsage: eval \$(kli login)\n\nEnvironment overrides: ${ENV_PREFIX}_KEYCLOAK_URL, ${ENV_PREFIX}_REALM, ${ENV_PREFIX}_CLIENT_ID"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'url', args: 1, argName: 'keycloak-url', 'Keycloak base URL', defaultValue: defaultKeycloakUrl)
            _(longOpt: 'realm', args: 1, argName: 'realm', 'Keycloak realm', defaultValue: defaultRealm)
            _(longOpt: 'client-id', args: 1, argName: 'client-id', 'OAuth client identifier', defaultValue: defaultClientId)
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

        String keycloakUrl = options.'url' ?: defaultKeycloakUrl
        String realm = options.'realm' ?: defaultRealm
        String clientId = options.'client-id' ?: defaultClientId
        boolean verbose = options.'verbose' || globalVerbose

        // Step 1: Device authorization request
        Map deviceResponse = requestDeviceAuthorization(keycloakUrl, realm, clientId)

        // Step 2: Display instructions to stderr (stdout is reserved for eval output)
        System.err.println('')
        System.err.println('To log in, open this URL in your browser:')
        if (deviceResponse.verification_uri_complete) {
            System.err.println("  ${deviceResponse.verification_uri_complete}")
            System.err.println('')
            System.err.println("Or go to ${deviceResponse.verification_uri} and enter code: ${deviceResponse.user_code}")
        } else {
            System.err.println("  ${deviceResponse.verification_uri}")
            System.err.println('')
            System.err.println("Enter code: ${deviceResponse.user_code}")
        }
        System.err.println('')
        System.err.println('Waiting for authentication...')

        // Step 3: Poll for token
        int interval = (deviceResponse.interval as Integer) ?: 5
        int expiresIn = (deviceResponse.expires_in as Integer) ?: 600
        String deviceCode = deviceResponse.device_code as String

        Map tokenResponse = pollForToken(keycloakUrl, realm, clientId, deviceCode, interval, expiresIn)

        // Step 4: Decode ID token and extract claims
        String idToken = tokenResponse.id_token as String
        Map claims = decodeJwtPayload(idToken)
        String orcid = claims?.orcid as String

        if (verbose) {
            System.err.println('')
            System.err.println(JsonOutput.prettyPrint(JsonOutput.toJson(claims)))
            System.err.println('')
        }

        // Step 5: Create session
        String sessionId = UUID.randomUUID().toString()
        Map sessionData = [
                sessionId: sessionId,
                orcid    : orcid,
                claims   : claims,
                createdAt: Instant.now().toString()
        ]
        persistSession(sessionId, sessionData)

        // Step 6: Output for eval
        System.err.println('')
        System.err.println('Login successful.')
        if (orcid) {
            System.err.println("ORCID iD: ${orcid}")
        }
        System.err.println("Session:  ${sessionId}")
        System.err.println('')

        println "export ${SESSION_ENV_VAR}=${sessionId}"
    }

    private Map requestDeviceAuthorization(String keycloakUrl, String realm, String clientId) {
        String url = "${sanitizeBaseUrl(keycloakUrl)}/realms/${realm}/protocol/openid-connect/auth/device"
        String body = "client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}&scope=openid"

        HttpClient client = HttpClient.newHttpClient()
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                printError("Device authorization failed (${response.statusCode()}): ${response.body()}")
                System.exit(1)
            }
            return (Map) new JsonSlurper().parseText(response.body())
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            printError("Device authorization request interrupted: ${e.message}")
            System.exit(1)
        } catch (IOException e) {
            printError("Error requesting device authorization: ${e.message}")
            System.exit(1)
        }
    }

    private Map pollForToken(String keycloakUrl, String realm, String clientId,
                             String deviceCode, int interval, int expiresIn) {
        String tokenUrl = "${sanitizeBaseUrl(keycloakUrl)}/realms/${realm}/protocol/openid-connect/token"
        String body = "grant_type=${URLEncoder.encode('urn:ietf:params:oauth:grant-type:device_code', StandardCharsets.UTF_8)}" +
                "&device_code=${URLEncoder.encode(deviceCode, StandardCharsets.UTF_8)}" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}"

        HttpClient client = HttpClient.newHttpClient()
        long deadline = System.currentTimeMillis() + (expiresIn * 1000L)
        int currentInterval = interval

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(currentInterval * 1000L)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                printError("Login interrupted.")
                System.exit(1)
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header('Content-Type', 'application/x-www-form-urlencoded')
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
                Map responseBody = (Map) new JsonSlurper().parseText(response.body())

                if (response.statusCode() == 200 && responseBody.access_token) {
                    return responseBody
                }

                String error = responseBody.error as String
                switch (error) {
                    case 'authorization_pending':
                        break
                    case 'slow_down':
                        currentInterval += 5
                        break
                    case 'expired_token':
                        printError('Login expired. Please try again.')
                        System.exit(1)
                        break
                    case 'access_denied':
                        printError('Login was denied by the user.')
                        System.exit(1)
                        break
                    default:
                        printError("Unexpected error during login: ${error} - ${responseBody.error_description}")
                        System.exit(1)
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                printError("Login interrupted.")
                System.exit(1)
            } catch (IOException e) {
                printError("Error polling for token: ${e.message}")
                System.exit(1)
            }
        }

        printError('Login timed out. Please try again.')
        System.exit(1)
    }

    // ---- logout command ----

    private void handleLogout(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli logout',
                usage: 'kli logout',
                header: 'End the current session.',
                footer: "\nUsage: eval \$(kli logout)"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            System.exit(1)
        }
        if (options.h) {
            cli.usage()
            return
        }

        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. ${SESSION_ENV_VAR} is not set.")
            System.exit(1)
        }

        Path sessionsDir = ensureSessionsDirectory()
        Path sessionFile = sessionsDir.resolve("${sessionId}.json")

        if (Files.exists(sessionFile)) {
            try {
                Files.delete(sessionFile)
                log.info("Deleted session file {}", sessionFile)
            } catch (IOException e) {
                printError("Failed to delete session file: ${e.message}")
                System.exit(1)
            }
        } else {
            System.err.println('Warning: session file not found (may have already been removed).')
        }

        System.err.println('Logged out.')
        println "unset ${SESSION_ENV_VAR}"
    }

    // ---- status command ----

    private void handleStatus(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli status',
                usage: 'kli status',
                header: 'Show current session information.'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            System.exit(1)
        }
        if (options.h) {
            cli.usage()
            return
        }

        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            System.err.println("No active session. ${SESSION_ENV_VAR} is not set.")
            System.err.println('Run: eval $(kli login)')
            System.exit(1)
        }

        Map session = loadSession(sessionId)

        System.err.println("Session:  ${session.sessionId}")
        if (session.orcid) {
            System.err.println("ORCID iD: ${session.orcid}")
        }
        System.err.println("Created:  ${session.createdAt}")
    }

    // ---- publish command ----

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
            printError('A topic is required. Use --topic or set KLI_TOPIC.')
            System.exit(1)
        }
        if (!filePath?.trim()) {
            printError('A message file is required. Use --file <path>.')
            System.exit(1)
        }

        // Load session if available
        String sessionId = System.getenv(SESSION_ENV_VAR)
        String orcid = null
        if (sessionId?.trim()) {
            Map session = loadSession(sessionId)
            orcid = session?.orcid as String
            log.info("Publishing with authenticated user ORCID={}", orcid ?: 'unknown')
            if (verbose) {
                System.err.println("ORCID iD: ${orcid ?: 'unknown'}")
            }
        } else {
            log.warn("No active session (${SESSION_ENV_VAR} not set). Publishing without user identity.")
            if (verbose) {
                System.err.println("Warning: No active session. Run: eval \$(kli login)")
            }
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

    // ---- session persistence ----

    private void persistSession(String sessionId, Map sessionData) {
        Path directory = ensureSessionsDirectory()
        Path destination = directory.resolve("${sessionId}.json")
        String json = JsonOutput.prettyPrint(JsonOutput.toJson(sessionData))
        try {
            Files.writeString(destination, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            applyOwnerOnlyPermissions(destination, 'rw-------')
            log.info("Stored session {} at {}", sessionId, destination)
        } catch (IOException e) {
            printError("Failed to store session at ${destination}: ${e.message}")
            System.exit(1)
        }
    }

    private Map loadSession(String sessionId) {
        Path directory = ensureSessionsDirectory()
        Path sessionFile = directory.resolve("${sessionId}.json")
        if (!Files.exists(sessionFile)) {
            printError("Session not found: ${sessionId}")
            System.exit(1)
        }
        if (!Files.isReadable(sessionFile)) {
            printError("Session file is not readable: ${sessionFile}")
            System.exit(1)
        }
        try {
            String json = Files.readString(sessionFile)
            return (Map) new JsonSlurper().parseText(json)
        } catch (IOException e) {
            printError("Failed to read session ${sessionId}: ${e.message}")
            System.exit(1)
        }
    }

    private Path ensureSessionsDirectory() {
        String home = System.getProperty('user.home')
        if (!home?.trim()) {
            printError('Cannot determine user home directory to store session data.')
            System.exit(1)
        }

        Path directory = Paths.get(home, '.jade-tipi', 'clients', 'kafka-cli', 'sessions')
        try {
            Files.createDirectories(directory)
            applyOwnerOnlyPermissions(directory, 'rwx------')
        } catch (IOException e) {
            printError("Unable to create sessions directory ${directory}: ${e.message}")
            System.exit(1)
        }

        if (!Files.isDirectory(directory) || !Files.isReadable(directory) || !Files.isWritable(directory)) {
            printError("Sessions directory ${directory} must exist and be both readable and writable.")
            System.exit(1)
        }

        return directory
    }

    // ---- utility methods ----

    private Map decodeJwtPayload(String token) {
        if (!token) {
            return [:]
        }
        String[] parts = token.split('\\.')
        if (parts.length < 2) {
            printError('Token did not contain a payload segment.')
            return [:]
        }
        String payloadSegment = ensurePadding(parts[1])
        byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment)
        String json = new String(decoded, StandardCharsets.UTF_8)
        return (Map) new JsonSlurper().parseText(json)
    }

    private static String ensurePadding(String segment) {
        int mod = segment.length() % 4
        if (mod == 0) {
            return segment
        }
        return segment + ('=' * (4 - mod))
    }

    private static String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ''
        }
        return baseUrl.endsWith('/') ? baseUrl[0..-2] : baseUrl
    }

    private void applyOwnerOnlyPermissions(Path path, String permissions) {
        if (path == null) {
            return
        }
        if (!FileSystems.default.supportedFileAttributeViews().contains('posix')) {
            return
        }
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions)
            Files.setPosixFilePermissions(path, perms)
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX FS; nothing to do.
        } catch (IOException e) {
            printError("Unable to set permissions on ${path}: ${e.message}")
            System.exit(1)
        }
    }

    private String resolveEnv(String suffix, String fallback) {
        return System.getenv("${ENV_PREFIX}_${suffix}") ?: fallback
    }

    private void printGlobalUsage(CliBuilder cli) {
        println """Kli - Jade Tipi Kafka message publisher

Usage:
  eval \$(kli login [options])
  eval \$(kli logout)
  kli status
  kli publish --topic <topic> --file <message.json> [options]
  kli help

Commands:
  login     Authenticate via device authorization grant.
  logout    End the current session.
  status    Show current session information.
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
