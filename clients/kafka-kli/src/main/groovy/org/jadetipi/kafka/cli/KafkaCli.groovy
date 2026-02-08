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

import org.jadetipi.dto.message.Action
import org.jadetipi.dto.message.Message
import org.jadetipi.dto.message.Transaction
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
    private static final String DEFAULT_TOPIC = 'jdtp_cli_kli'
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
            case 'config':
                handleConfig(commandArgs, globalVerbose)
                break
            case 'open':
                handleOpen(commandArgs, globalVerbose)
                break
            case 'rollback':
                handleRollback(commandArgs, globalVerbose)
                break
            case 'commit':
                handleCommit(commandArgs, globalVerbose)
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
        String defaultBootstrapServer = resolveEnv('BOOTSTRAP_SERVERS', DEFAULT_BOOTSTRAP_SERVERS)
        String defaultTopic = resolveEnv('TOPIC', DEFAULT_TOPIC)

        def cli = new CliBuilder(
                name: 'kli login',
                usage: 'kli login [options]',
                header: 'Authenticate via device authorization grant.',
                footer: "\nEnvironment overrides: ${ENV_PREFIX}_KEYCLOAK_URL, ${ENV_PREFIX}_REALM, ${ENV_PREFIX}_CLIENT_ID, ${ENV_PREFIX}_BOOTSTRAP_SERVERS, ${ENV_PREFIX}_TOPIC"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'url', args: 1, argName: 'keycloak-url', 'Keycloak base URL', defaultValue: defaultKeycloakUrl)
            _(longOpt: 'realm', args: 1, argName: 'realm', 'Keycloak realm', defaultValue: defaultRealm)
            _(longOpt: 'client-id', args: 1, argName: 'client-id', 'OAuth client identifier', defaultValue: defaultClientId)
            _(longOpt: 'bootstrap-server', args: 1, argName: 'server',
                    'Kafka bootstrap server', defaultValue: defaultBootstrapServer)
            _(longOpt: 'topic', args: 1, argName: 'topic', 'Kafka topic',
                    defaultValue: defaultTopic)
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
        String bootstrapServer = options.'bootstrap-server' ?: defaultBootstrapServer
        String topic = options.'topic' ?: defaultTopic
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
        String now = Instant.now().toString()
        Map sessionData = [
                sessionId  : sessionId,
                orcid      : orcid,
                claims     : claims,
                config     : [
                        topic             : topic,
                        'bootstrap-server': bootstrapServer
                ],
                createdAt  : now,
                refreshedAt: now
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
                footer: ""
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
        refreshSession(sessionId, session)

        System.err.println("Session:  ${session.sessionId}")
        if (session.orcid) {
            System.err.println("ORCID iD: ${session.orcid}")
        }
        System.err.println("Created:  ${session.createdAt}")
        if (session.txn) {
            Map txn = session.txn as Map
            Map grp = txn.group as Map
            System.err.println("Transaction: ${txn.uuid}~${grp.org}~${grp.grp}~${txn.client}")
        }
        if (session.config) {
            Map config = session.config as Map
            System.err.println("Kafka:")
            System.err.println("  Bootstrap server: ${config.'bootstrap-server'}")
            System.err.println("  Topic:            ${config.topic}")
        }
    }

    // ---- config command ----

    private void handleConfig(String[] args, boolean globalVerbose) {
        String defaultBootstrapServer = resolveEnv('BOOTSTRAP_SERVERS', DEFAULT_BOOTSTRAP_SERVERS)
        String defaultTopic = resolveEnv('TOPIC', DEFAULT_TOPIC)

        def cli = new CliBuilder(
                name: 'kli config',
                usage: 'kli config [options]',
                header: 'Set Kafka configuration for the current session.',
                footer: "\nEnvironment overrides: ${ENV_PREFIX}_BOOTSTRAP_SERVERS, ${ENV_PREFIX}_TOPIC"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'bootstrap-server', args: 1, argName: 'server',
                    'Kafka bootstrap server', defaultValue: defaultBootstrapServer)
            _(longOpt: 'topic', args: 1, argName: 'topic', 'Kafka topic',
                    defaultValue: defaultTopic)
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

        String bootstrapServer = options.'bootstrap-server' ?: defaultBootstrapServer
        String topic = options.'topic' ?: defaultTopic
        boolean verbose = options.'verbose' || globalVerbose

        // Require active session
        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. Run: kli login")
            System.exit(1)
        }
        Map session = loadSession(sessionId)

        // Store config in session
        session.config = [
                topic             : topic,
                'bootstrap-server': bootstrapServer
        ]
        refreshSession(sessionId, session)

        System.err.println("Kafka configuration saved:")
        System.err.println("  Bootstrap server: ${bootstrapServer}")
        System.err.println("  Topic:            ${topic}")
    }

    // ---- open command ----

    private void handleOpen(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli open',
                usage: 'kli open [options]',
                header: 'Open a new transaction.'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
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

        boolean verbose = options.'verbose' || globalVerbose

        // Require active session
        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. Run: kli login")
            System.exit(1)
        }
        Map session = loadSession(sessionId)

        // Require config
        if (!session.config) {
            printError("No Kafka configuration. Run: kli config")
            System.exit(1)
        }
        Map config = session.config as Map
        String bootstrapServer = config.'bootstrap-server' as String
        String topic = config.topic as String

        // Fail if transaction already open
        if (session.txn) {
            printError("Transaction already open: ${session.txn.uuid}")
            System.exit(1)
        }

        // Extract org/group from claims, orcid for user
        Map claims = session.claims as Map ?: [:]
        String org = claims.tipi_org as String
        String grp = claims.tipi_group as String
        String orcid = session.orcid as String

        if (!org?.trim() || !grp?.trim()) {
            printError("Session claims missing tipi_org or tipi_group.")
            System.exit(1)
        }

        // Create transaction and OPEN message
        Transaction txn = Transaction.newInstance(org, grp, DEFAULT_CLIENT_ID, orcid ?: 'unknown')
        Message message = Message.newInstance(txn, Action.OPEN, [:])
        String key = txn.getId()

        if (verbose) {
            System.err.println("Bootstrap server: ${bootstrapServer}")
            System.err.println("Topic:           ${topic}")
            System.err.println("Transaction ID:  ${txn.getId()}")
            System.err.println("Message ID:      ${message.getId()}")
            System.err.println("ORCID iD:        ${orcid ?: 'unknown'}")
            System.err.println('')
        }

        byte[] messageBytes
        try {
            messageBytes = MessageMapper.toBytes(message)
        } catch (Exception e) {
            printError("Failed to serialize message: ${e.message}")
            System.exit(1)
        }

        publishMessage(bootstrapServer, topic, key, messageBytes, message, verbose)

        // Store transaction in session
        session.txn = [
                uuid  : txn.uuid(),
                group : [org: txn.group().org(), grp: txn.group().grp()],
                client: txn.client(),
                user  : txn.user()
        ]
        refreshSession(sessionId, session)

        System.err.println("Transaction opened: ${txn.getId()}")
    }

    // ---- rollback command ----

    private void handleRollback(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli rollback',
                usage: 'kli rollback [options]',
                header: 'Roll back the current transaction.'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
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

        boolean verbose = options.'verbose' || globalVerbose

        // Require active session
        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. Run: kli login")
            System.exit(1)
        }
        Map session = loadSession(sessionId)

        // Require open transaction
        if (!session.txn) {
            printError("No open transaction. Run: kli open")
            System.exit(1)
        }

        // Reconstruct transaction from session
        Map txnMap = session.txn as Map
        Map grpMap = txnMap.group as Map
        Transaction txn = new Transaction(
                txnMap.uuid as String,
                new org.jadetipi.dto.message.Group(grpMap.org as String, grpMap.grp as String),
                txnMap.client as String,
                txnMap.user as String
        )

        // Get config from session
        Map config = session.config as Map
        String bootstrapServer = config?.'bootstrap-server' as String ?: DEFAULT_BOOTSTRAP_SERVERS
        String topic = config?.topic as String ?: DEFAULT_TOPIC

        // Create ROLLBACK message
        Message message = Message.newInstance(txn, Action.ROLLBACK, [:])
        String key = txn.getId()

        if (verbose) {
            System.err.println("Bootstrap server: ${bootstrapServer}")
            System.err.println("Topic:           ${topic}")
            System.err.println("Transaction ID:  ${txn.getId()}")
            System.err.println("Message ID:      ${message.getId()}")
            System.err.println('')
        }

        byte[] messageBytes
        try {
            messageBytes = MessageMapper.toBytes(message)
        } catch (Exception e) {
            printError("Failed to serialize message: ${e.message}")
            System.exit(1)
        }

        publishMessage(bootstrapServer, topic, key, messageBytes, message, verbose)

        // Remove transaction from session
        session.remove('txn')
        refreshSession(sessionId, session)

        System.err.println("Transaction rolled back: ${txn.getId()}")
    }

    // ---- commit command ----

    private void handleCommit(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli commit',
                usage: 'kli commit [options]',
                header: 'Commit the current transaction.'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
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

        boolean verbose = options.'verbose' || globalVerbose

        // Require active session
        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. Run: kli login")
            System.exit(1)
        }
        Map session = loadSession(sessionId)

        // Require open transaction
        if (!session.txn) {
            printError("No open transaction. Run: kli open")
            System.exit(1)
        }

        // Reconstruct transaction from session
        Map txnMap = session.txn as Map
        Map grpMap = txnMap.group as Map
        Transaction txn = new Transaction(
                txnMap.uuid as String,
                new org.jadetipi.dto.message.Group(grpMap.org as String, grpMap.grp as String),
                txnMap.client as String,
                txnMap.user as String
        )

        // Get config from session
        Map config = session.config as Map
        String bootstrapServer = config?.'bootstrap-server' as String ?: DEFAULT_BOOTSTRAP_SERVERS
        String topic = config?.topic as String ?: DEFAULT_TOPIC

        // Create COMMIT message
        Message message = Message.newInstance(txn, Action.COMMIT, [:])
        String key = txn.getId()

        if (verbose) {
            System.err.println("Bootstrap server: ${bootstrapServer}")
            System.err.println("Topic:           ${topic}")
            System.err.println("Transaction ID:  ${txn.getId()}")
            System.err.println("Message ID:      ${message.getId()}")
            System.err.println('')
        }

        byte[] messageBytes
        try {
            messageBytes = MessageMapper.toBytes(message)
        } catch (Exception e) {
            printError("Failed to serialize message: ${e.message}")
            System.exit(1)
        }

        publishMessage(bootstrapServer, topic, key, messageBytes, message, verbose)

        // Remove transaction from session
        session.remove('txn')
        refreshSession(sessionId, session)

        System.err.println("Transaction committed: ${txn.getId()}")
    }

    // ---- publish command ----

    private void handlePublish(String[] args, boolean globalVerbose) {
        def cli = new CliBuilder(
                name: 'kli publish',
                usage: 'kli publish --file <message.json> [options]',
                header: 'Publish a message to a Kafka topic (testing).'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'file', args: 1, argName: 'path', 'Path to JSON file containing the message')
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

        String filePath = options.'file'
        boolean verbose = options.'verbose' || globalVerbose

        if (!filePath?.trim()) {
            printError('A message file is required. Use --file <path>.')
            System.exit(1)
        }

        // Require active session
        String sessionId = System.getenv(SESSION_ENV_VAR)
        if (!sessionId?.trim()) {
            printError("No active session. Run: kli login")
            System.exit(1)
        }
        Map session = loadSession(sessionId)
        refreshSession(sessionId, session)

        // Require config
        if (!session.config) {
            printError("No Kafka configuration. Run: kli config")
            System.exit(1)
        }
        Map config = session.config as Map
        String bootstrapServer = config.'bootstrap-server' as String
        String topic = config.topic as String

        String orcid = session?.orcid as String
        log.info("Publishing with authenticated user ORCID={}", orcid ?: 'unknown')
        if (verbose) {
            System.err.println("ORCID iD: ${orcid ?: 'unknown'}")
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

        String key = message.txn().getId()

        if (verbose) {
            println "Bootstrap server: ${bootstrapServer}"
            println "Topic:            ${topic}"
            println "Message ID:       ${message.getId()}"
            println "Action:           ${message.action()}"
            println "Key:              ${key}"
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

        publishMessage(bootstrapServer, topic, key, messageBytes, message, verbose)
    }

    private void publishMessage(String bootstrapServer, String topic, String key,
                                byte[] messageBytes, Message message, boolean verbose) {
        Properties props = new Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.name)
        props.put(ProducerConfig.ACKS_CONFIG, 'all')
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000L)
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000)
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000)

        KafkaProducer<String, byte[]> producer = null
        try {
            producer = new KafkaProducer<>(props)

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, messageBytes)

            log.info("Publishing message {} to topic {}", message.getId(), topic)

            Future<RecordMetadata> future = producer.send(record)
            RecordMetadata metadata = future.get(15, java.util.concurrent.TimeUnit.SECONDS)

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

    private void refreshSession(String sessionId, Map sessionData) {
        sessionData.refreshedAt = Instant.now().toString()
        persistSession(sessionId, sessionData)
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

        Path directory = Paths.get(home, '.jade-tipi', 'clients', 'kafka-kli', 'sessions')
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
        println 'Kli - Jade Tipi Kafka message publisher\n'
        cli.usage()
        println """
Commands:
  login     Authenticate via device authorization grant.
  logout    End the current session.
  status    Show current session information.
  config    Set Kafka configuration for the current session.
  open      Open a new transaction.
  rollback  Roll back the current transaction.
  commit    Commit the current transaction.
  publish   Publish a message to a Kafka topic (testing)."""
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
