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
package org.jadetipi.cli

import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.io.IOException
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

/**
 * Shared implementation for the Jade/Tipi Keycloak token CLIs.
 */
@Slf4j
class JadeTipiCli {

    private final String programName
    private final String envPrefix
    private final String fallbackKeycloakUrl
    private final String fallbackRealm
    private final String fallbackClientId
    private final String fallbackClientSecret
    private final String fallbackApiUrl
    JadeTipiCli(
            String programName,
            String envPrefix,
            String fallbackClientId,
            String fallbackClientSecret,
            String fallbackKeycloakUrl = 'http://localhost:8484',
            String fallbackRealm = 'jade-tipi',
            String fallbackApiUrl = 'http://localhost:8765'
    ) {
        this.programName = programName
        this.envPrefix = envPrefix
        this.fallbackClientId = fallbackClientId
        this.fallbackClientSecret = fallbackClientSecret
        this.fallbackKeycloakUrl = fallbackKeycloakUrl
        this.fallbackRealm = fallbackRealm
        this.fallbackApiUrl = fallbackApiUrl
        ensureLogDirectoryExists()
    }

    void run(String[] args) {
        def cli = new CliBuilder(
                name: programName,
                usage: "${programName} <command> [options]",
                footer: "\nEnvironment overrides: ${envPrefix}_KEYCLOAK_URL, ${envPrefix}_REALM, ${envPrefix}_CLIENT_ID, ${envPrefix}_CLIENT_SECRET"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            v(longOpt: 'verbose', 'Print the raw JWT and decoded payload')
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
            case 'open-transaction':
                handleOpenTransaction(commandArgs, globalVerbose)
                break
            case 'commit':
                handleCommit(commandArgs, globalVerbose)
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

    private void handleOpenTransaction(String[] args, boolean globalVerbose) {
        String defaultUrl = resolveEnv('KEYCLOAK_URL', fallbackKeycloakUrl)
        String defaultRealmValue = resolveEnv('REALM', fallbackRealm)
        String defaultClientIdValue = resolveEnv('CLIENT_ID', fallbackClientId)
        String defaultClientSecretValue = resolveEnv('CLIENT_SECRET', fallbackClientSecret)
        String defaultApiUrl = resolveEnv('API_URL', fallbackApiUrl)

        def cli = new CliBuilder(
                name: "${programName} open-transaction",
                usage: "${programName} open-transaction [options]",
                header: 'Obtain a JWT for service-to-service transactions.',
                footer: "\nEnvironment overrides: ${envPrefix}_KEYCLOAK_URL, ${envPrefix}_REALM, ${envPrefix}_CLIENT_ID, ${envPrefix}_CLIENT_SECRET, ${envPrefix}_API_URL"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'url', args: 1, argName: 'keycloak-url', 'Keycloak base URL', defaultValue: defaultUrl)
            _(longOpt: 'realm', args: 1, argName: 'realm', 'Keycloak realm', defaultValue: defaultRealmValue)
            _(longOpt: 'client-id', args: 1, argName: 'client-id', 'OAuth client identifier', defaultValue: defaultClientIdValue)
            _(longOpt: 'client-secret', args: 1, argName: 'secret', 'OAuth client secret', defaultValue: defaultClientSecretValue)
            _(longOpt: 'api-url', args: 1, argName: 'api-url', 'Backend API base URL', defaultValue: defaultApiUrl)
            _(longOpt: 'organization', args: 1, argName: 'organization', 'Override organization claim sent to the API')
            _(longOpt: 'group', args: 1, argName: 'group', 'Override group claim sent to the API')
            v(longOpt: 'verbose', 'Print the raw JWT and decoded payload')
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            System.exit(1)
        }
        if (options.h) {
            cli.usage()
            return
        }

        Map<String, String> effective = [
                url         : options.'url' ?: defaultUrl,
                realm       : options.'realm' ?: defaultRealmValue,
                clientId    : options.'client-id' ?: defaultClientIdValue,
                clientSecret: options.'client-secret' ?: defaultClientSecretValue,
                apiUrl      : options.'api-url' ?: defaultApiUrl,
                organizationOverride: options.'organization',
                groupOverride: options.'group'
        ]

        boolean verbose = options.'verbose'

        Map tokenResult = fetchAccessToken(effective, globalVerbose || verbose)
        executeOpenTransaction(effective, tokenResult, globalVerbose || verbose)
    }

    private void executeOpenTransaction(Map<String, String> effective, Map tokenResult, boolean verbose) {
        String token = tokenResult.token as String
        Map<String, Object> payloadClaims = (Map<String, Object>) (tokenResult.claims ?: [:])

        String organization = (effective.organizationOverride ?: payloadClaims?.get('tipi_org')) as String
        String group = (effective.groupOverride ?: payloadClaims?.get('tipi_group')) as String

        if (!organization?.trim()) {
            printError("Organization could not be determined. Provide --organization or ensure the JWT contains the 'tipi_org' claim.")
            System.exit(1)
        }
        if (!group?.trim()) {
            printError("Group could not be determined. Provide --group or ensure the JWT contains the 'tipi_group' claim.")
            System.exit(1)
        }
        log.info("Opening transaction for organization={}, group={}", organization, group)

        String apiBase = sanitizeBaseUrl(effective.apiUrl)
        URI transactionUri = URI.create("${apiBase}/api/transactions/open")
        Map<String, String> transactionPayload = [
                organization: organization,
                group        : group
        ]
        String transactionBody = JsonOutput.toJson(transactionPayload)

        HttpClient client = HttpClient.newHttpClient()
        HttpRequest transactionRequest = HttpRequest.newBuilder()
                .uri(transactionUri)
                .header('Content-Type', 'application/json')
                .header('Authorization', "Bearer ${token}")
                .POST(HttpRequest.BodyPublishers.ofString(transactionBody))
                .build()

        try {
            HttpResponse<String> transactionResponse = client.send(transactionRequest, HttpResponse.BodyHandlers.ofString())
            if (transactionResponse.statusCode() < 200 || transactionResponse.statusCode() >= 300) {
                printError("Failed to open transaction (${transactionResponse.statusCode()}): ${transactionResponse.body()}")
                System.exit(1)
            }

            String transactionResponseBody = transactionResponse.body()
            Map txnPayload = (Map) new JsonSlurper().parseText(transactionResponseBody)
            String transactionId = txnPayload.id as String
            persistTransactionToken(effective.clientId as String, transactionId, transactionResponseBody)

            println transactionId
            log.info("Opened transaction {} for organization={}, group={}", transactionId, organization, group)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            printError("Transaction request interrupted: ${e.message}")
            System.exit(1)
        } catch (IOException e) {
            printError("Error opening transaction: ${e.message}")
            System.exit(1)
        }
    }

    private void handleCommit(String[] args, boolean globalVerbose) {
        String defaultUrl = resolveEnv('KEYCLOAK_URL', fallbackKeycloakUrl)
        String defaultRealmValue = resolveEnv('REALM', fallbackRealm)
        String defaultClientIdValue = resolveEnv('CLIENT_ID', fallbackClientId)
        String defaultClientSecretValue = resolveEnv('CLIENT_SECRET', fallbackClientSecret)
        String defaultApiUrl = resolveEnv('API_URL', fallbackApiUrl)

        def cli = new CliBuilder(
                name: "${programName} commit",
                usage: "${programName} commit -t <transactionId> [-t <transactionId> ...] [options]",
                header: 'Commit one or more open transactions.',
                footer: "\nEnvironment overrides: ${envPrefix}_KEYCLOAK_URL, ${envPrefix}_REALM, ${envPrefix}_CLIENT_ID, ${envPrefix}_CLIENT_SECRET, ${envPrefix}_API_URL"
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'url', args: 1, argName: 'keycloak-url', 'Keycloak base URL', defaultValue: defaultUrl)
            _(longOpt: 'realm', args: 1, argName: 'realm', 'Keycloak realm', defaultValue: defaultRealmValue)
            _(longOpt: 'client-id', args: 1, argName: 'client-id', 'OAuth client identifier', defaultValue: defaultClientIdValue)
            _(longOpt: 'client-secret', args: 1, argName: 'secret', 'OAuth client secret', defaultValue: defaultClientSecretValue)
            _(longOpt: 'api-url', args: 1, argName: 'api-url', 'Backend API base URL', defaultValue: defaultApiUrl)
            t(longOpt: 'transaction', args: 1, argName: 'transactionId', 'Transaction ID to commit (repeatable)')
            v(longOpt: 'verbose', 'Print the raw JWT and decoded payload')
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            System.exit(1)
        }
        if (options.h) {
            cli.usage()
            return
        }

        List<String> transactionIds = normalizeTransactionIds(options.'transaction')
        if (transactionIds.isEmpty()) {
            printError("At least one -t/--transaction value is required.")
            System.exit(1)
        }

        Map<String, String> effective = [
                url         : options.'url' ?: defaultUrl,
                realm       : options.'realm' ?: defaultRealmValue,
                clientId    : options.'client-id' ?: defaultClientIdValue,
                clientSecret: options.'client-secret' ?: defaultClientSecretValue,
                apiUrl      : options.'api-url' ?: defaultApiUrl
        ]

        boolean verbose = options.'verbose'

        Map tokenResult = fetchAccessToken(effective, globalVerbose || verbose)
        executeCommit(effective, tokenResult, transactionIds)
    }

    private List<String> normalizeTransactionIds(Object rawTransactions) {
        List<String> ids = []
        if (rawTransactions instanceof List) {
            ids.addAll(rawTransactions.collect { it?.toString() }.findAll { it })
        } else if (rawTransactions) {
            ids.add(rawTransactions.toString())
        }
        return ids
    }

    private void executeCommit(Map<String, String> effective, Map tokenResult, List<String> transactionIds) {
        String token = tokenResult.token as String
        HttpClient client = HttpClient.newHttpClient()
        String apiBase = sanitizeBaseUrl(effective.apiUrl)
        URI commitUri = URI.create("${apiBase}/api/transactions/commit")

        transactionIds.each { String transactionId ->
            Map stored = loadStoredTransaction(effective.clientId as String, transactionId)
            String payloadJson = stored.json as String
            HttpRequest commitRequest = HttpRequest.newBuilder()
                    .uri(commitUri)
                    .header('Content-Type', 'application/json')
                    .header('Authorization', "Bearer ${token}")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build()

            try {
                log.info("Committing transaction {}", transactionId)
                HttpResponse<String> commitResponse = client.send(commitRequest, HttpResponse.BodyHandlers.ofString())
                int status = commitResponse.statusCode()
                String commitResponseBody = commitResponse.body()
                if (status == 409) {
                    handleCommitConflict(transactionId, commitResponseBody)
                }
                if (status < 200 || status >= 300) {
                    printError("Failed to commit transaction ${transactionId} (${status}): ${commitResponseBody}")
                    System.exit(1)
                }

                Map responsePayload = (Map) new JsonSlurper().parseText(commitResponseBody)
                String commitId = responsePayload.commitId as String
                persistCommitRecord(effective.clientId as String, transactionId, commitResponseBody)
                println commitId ?: transactionId
                log.info("Committed transaction {} with commitId={}", transactionId, commitId)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                printError("Commit request interrupted for ${transactionId}: ${e.message}")
                System.exit(1)
            } catch (IOException e) {
                printError("Error committing transaction ${transactionId}: ${e.message}")
                System.exit(1)
            }
        }
    }

    private void handleCommitConflict(String transactionId, String responseBody) {
        String message = extractErrorMessage(responseBody)
        String base = "Transaction ${transactionId} has already been committed."
        if (message && !message.equalsIgnoreCase('Transaction already committed')) {
            log.warn("{} Details: {}", base, message)
            printError("${base} Details: ${message}")
        } else {
            log.warn(base)
            printError(base)
        }
        System.exit(1)
    }

    private String resolveEnv(String suffix, String fallback) {
        return System.getenv("${envPrefix}_${suffix}") ?: fallback
    }

    private static String buildTokenEndpoint(String baseUrl, String realm) {
        String sanitizedBase = baseUrl.endsWith('/') ? baseUrl[0..-2] : baseUrl
        return "${sanitizedBase}/realms/${realm}/protocol/openid-connect/token"
    }

    private static String buildClientCredentialsBody(String clientId, String clientSecret) {
        Map<String, String> params = [
                grant_type   : 'client_credentials',
                client_id    : clientId,
                client_secret: clientSecret
        ]

        params.collect { key, value ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }.join('&')
    }

    private static String decodeJwtPayload(String token) {
        String[] parts = token.split('\\.')
        if (parts.length < 2) {
            printError('Token did not contain a payload segment.')
            return null
        }

        String payloadSegment = ensurePadding(parts[1])
        byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment)
        return new String(decoded, StandardCharsets.UTF_8)
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

    private void persistTransactionToken(String clientId, String transactionId, String jsonBody) {
        if (!clientId?.trim()) {
            printError('Client identifier is required to store the transaction token.')
            System.exit(1)
        }
        if (!transactionId?.trim()) {
            printError('Transaction response did not include a transactionId; cannot persist token.')
            System.exit(1)
        }

        Path directory = ensureTransactionsDirectory(clientId)
        Path destination = directory.resolve("${transactionId}.json")
        try {
            Files.writeString(destination, jsonBody, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            applyOwnerOnlyPermissions(destination, "rw-------")
            log.info("Stored transaction token for {} at {}", transactionId, destination)
        } catch (IOException e) {
            printError("Failed to store transaction token at ${destination}: ${e.message}")
            System.exit(1)
        }
    }

    private void persistCommitRecord(String clientId, String transactionId, String jsonBody) {
        if (!transactionId?.trim()) {
            printError('Transaction ID is required to store commit metadata.')
            System.exit(1)
        }
        if (!clientId?.trim()) {
            printError('Client identifier is required to store commit metadata.')
            System.exit(1)
        }

        Path directory = ensureCommitDirectory(clientId)
        Path destination = directory.resolve("${transactionId}.json")
        try {
            Files.writeString(destination, jsonBody, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            applyOwnerOnlyPermissions(destination, "rw-------")
            log.info("Stored commit record for {} at {}", transactionId, destination)
        } catch (IOException e) {
            printError("Failed to store commit data at ${destination}: ${e.message}")
            System.exit(1)
        }
    }

    private Path ensureTransactionsDirectory(String clientId) {
        String home = System.getProperty('user.home')
        if (!home?.trim()) {
            printError("Cannot determine user home directory to store transaction tokens.")
            System.exit(1)
        }

        Path directory = Paths.get(home, '.jade-tipi', 'clients', clientId, 'transactions')
        try {
            Files.createDirectories(directory)
            applyOwnerOnlyPermissions(directory, "rwx------")
        } catch (IOException e) {
            printError("Unable to create transactions directory ${directory}: ${e.message}")
            System.exit(1)
        }

        if (!Files.isDirectory(directory) || !Files.isReadable(directory) || !Files.isWritable(directory)) {
            printError("Transactions directory ${directory} must exist and be both readable and writable.")
            System.exit(1)
        }

        return directory
    }

    private String extractErrorMessage(String responseBody) {
        if (!responseBody) {
            return null
        }
        try {
            Map payload = (Map) new JsonSlurper().parseText(responseBody)
            String message = payload.message as String
            if (message?.trim()) {
                return message
            }
            String description = payload.error_description as String
            if (description?.trim()) {
                return description
            }
            String error = payload.error as String
            return error?.trim()
        } catch (Exception ignored) {
            return responseBody
        }
    }

    private Path ensureCommitDirectory(String clientId) {
        String home = System.getProperty('user.home')
        if (!home?.trim()) {
            printError("Cannot determine user home directory to store commit metadata.")
            System.exit(1)
        }

        Path directory = Paths.get(home, '.jade-tipi', 'clients', clientId, 'commits')
        try {
            Files.createDirectories(directory)
            applyOwnerOnlyPermissions(directory, "rwx------")
        } catch (IOException e) {
            printError("Unable to create commit directory ${directory}: ${e.message}")
            System.exit(1)
        }

        if (!Files.isDirectory(directory) || !Files.isReadable(directory) || !Files.isWritable(directory)) {
            printError("Commit directory ${directory} must exist and be both readable and writable.")
            System.exit(1)
        }

        return directory
    }

    private Map fetchAccessToken(Map<String, String> effective, boolean verbose) {
        String tokenEndpoint = buildTokenEndpoint(effective.url, effective.realm)
        String formBody = buildClientCredentialsBody(effective.clientId, effective.clientSecret)

        HttpClient client = HttpClient.newHttpClient()
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build()

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                printError("Failed to obtain token (${response.statusCode()}): ${response.body()}")
                System.exit(1)
            }

            Map payload = (Map) new JsonSlurper().parseText(response.body())
            if (!payload.access_token) {
                printError('Token response missing access_token field.')
                System.exit(1)
            }

            String token = payload.access_token as String
            String decodedPayloadJson = decodeJwtPayload(token)
            Map<String, Object> payloadClaims = [:]
            if (decodedPayloadJson) {
                payloadClaims = (Map<String, Object>) new JsonSlurper().parseText(decodedPayloadJson)
            }

            if (verbose) {
                println token
                if (!payloadClaims.isEmpty()) {
                    println ''
                    println JsonOutput.prettyPrint(JsonOutput.toJson(payloadClaims))
                    println ''
                }
            }

            return [token: token, claims: payloadClaims]
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            printError("Token request interrupted: ${e.message}")
            System.exit(1)
        } catch (IOException e) {
            printError("Error requesting token: ${e.message}")
            System.exit(1)
        }
    }

    private Map loadStoredTransaction(String clientId, String transactionId) {
        if (!transactionId?.trim()) {
            printError("Transaction ID is required.")
            System.exit(1)
        }

        Path directory = ensureTransactionsDirectory(clientId)
        Path tokenFile = directory.resolve("${transactionId}.json")
        if (!Files.exists(tokenFile)) {
            printError("Stored transaction ${transactionId} was not found at ${tokenFile}.")
            System.exit(1)
        }
        if (!Files.isReadable(tokenFile)) {
            printError("Stored transaction ${transactionId} is not readable at ${tokenFile}.")
            System.exit(1)
        }

        try {
            String json = Files.readString(tokenFile)
            Map payload = (Map) new JsonSlurper().parseText(json)
            return [json: json, payload: payload]
        } catch (IOException e) {
            printError("Failed to read stored transaction ${transactionId}: ${e.message}")
            System.exit(1)
        }
    }

    private void applyOwnerOnlyPermissions(Path path, String permissions) {
        if (path == null) {
            return
        }
        if (!FileSystems.default.supportedFileAttributeViews().contains("posix")) {
            return
        }
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions)
            Files.setPosixFilePermissions(path, perms)
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX FS for this path; nothing to do.
        } catch (IOException e) {
            printError("Unable to set permissions on ${path}: ${e.message}")
            System.exit(1)
        }
    }

    private void printGlobalUsage(CliBuilder cli) {
        println """${programName.capitalize()} CLI - Jade Tipi command-line client

Usage:
  ${programName} open-transaction [options]
  ${programName} commit -t <transactionId> [options]
  ${programName} help

Commands:
  open-transaction   Obtain a JWT and open a transaction for document writes.
  commit             Commit one or more previously opened transactions.
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

    private void ensureLogDirectoryExists() {
        String home = System.getProperty('user.home') ?: '.'
        Path dir = Paths.get(home, 'logs')
        try {
            Files.createDirectories(dir)
        } catch (IOException e) {
            System.err.println("ERROR Unable to create log directory ${dir}: ${e.message}")
        }
    }
}
