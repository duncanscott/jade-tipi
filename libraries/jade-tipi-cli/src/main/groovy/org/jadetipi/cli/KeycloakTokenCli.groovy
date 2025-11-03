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

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Shared implementation for the Jade/Tipi Keycloak token CLIs.
 */
class KeycloakTokenCli {

    private final String programName
    private final String envPrefix
    private final String fallbackKeycloakUrl
    private final String fallbackRealm
    private final String fallbackClientId
    private final String fallbackClientSecret
    private final String fallbackApiUrl

    KeycloakTokenCli(
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
            case 'create-transaction':
                handleCreateTransaction(commandArgs, globalVerbose)
                break
            case 'help':
                printGlobalUsage(cli)
                break
            default:
                System.err.println("Unknown command: ${command}\n")
                printGlobalUsage(cli)
                System.exit(1)
        }
    }

    private void handleCreateTransaction(String[] args, boolean globalVerbose) {
        String defaultUrl = resolveEnv('KEYCLOAK_URL', fallbackKeycloakUrl)
        String defaultRealmValue = resolveEnv('REALM', fallbackRealm)
        String defaultClientIdValue = resolveEnv('CLIENT_ID', fallbackClientId)
        String defaultClientSecretValue = resolveEnv('CLIENT_SECRET', fallbackClientSecret)
        String defaultApiUrl = resolveEnv('API_URL', fallbackApiUrl)

        def cli = new CliBuilder(
                name: "${programName} create-transaction",
                usage: "${programName} create-transaction [options]",
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

        requestTokenAndPrint(effective, globalVerbose || verbose)
    }

    private void requestTokenAndPrint(Map<String, String> effective, boolean verbose) {
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
                System.err.println("Failed to obtain token (${response.statusCode()}): ${response.body()}")
                System.exit(1)
            }

            Map payload = (Map) new JsonSlurper().parseText(response.body())
            if (!payload.access_token) {
                System.err.println('Token response missing access_token field.')
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

            String organization = (effective.organizationOverride ?: payloadClaims?.get('tipi_org')) as String
            String group = (effective.groupOverride ?: payloadClaims?.get('tipi_group')) as String

            if (!organization?.trim()) {
                System.err.println("Organization could not be determined. Provide --organization or ensure the JWT contains the 'tipi_org' claim.")
                System.exit(1)
            }
            if (!group?.trim()) {
                System.err.println("Group could not be determined. Provide --group or ensure the JWT contains the 'tipi_group' claim.")
                System.exit(1)
            }

            String apiBase = sanitizeBaseUrl(effective.apiUrl)
            URI transactionUri = URI.create("${apiBase}/api/transactions")
            String transactionBody = JsonOutput.toJson([
                    organization: organization,
                    group        : group
            ])

            HttpRequest transactionRequest = HttpRequest.newBuilder()
                    .uri(transactionUri)
                    .header('Content-Type', 'application/json')
                    .header('Authorization', "Bearer ${token}")
                    .POST(HttpRequest.BodyPublishers.ofString(transactionBody))
                    .build()

            HttpResponse<String> transactionResponse = client.send(transactionRequest, HttpResponse.BodyHandlers.ofString())
            if (transactionResponse.statusCode() < 200 || transactionResponse.statusCode() >= 300) {
                System.err.println("Failed to create transaction (${transactionResponse.statusCode()}): ${transactionResponse.body()}")
                System.exit(1)
            }

            Map txnPayload = (Map) new JsonSlurper().parseText(transactionResponse.body())
            if (verbose) {
                println JsonOutput.prettyPrint(JsonOutput.toJson(txnPayload))
            } else {
                println "Transaction created. ID: ${txnPayload.transactionId}, secret: ${txnPayload.secret}"
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            System.err.println("Token request interrupted: ${e.message}")
            System.exit(1)
        } catch (IOException e) {
            System.err.println("Error requesting token: ${e.message}")
            System.exit(1)
        }
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
            System.err.println('Token did not contain a payload segment.')
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

    private void printGlobalUsage(CliBuilder cli) {
        println """${programName.capitalize()} CLI - Jade Tipi command-line client

Usage:
  ${programName} create-transaction [options]
  ${programName} help

Commands:
  create-transaction   Obtain a JWT for service-to-service transactions.
"""
        cli.usage()
    }
}
