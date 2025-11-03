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
package org.jadetipi.tipi.cli

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

class TipiCli {

    private static final String DEFAULT_KEYCLOAK_URL = System.getenv('TIPI_KEYCLOAK_URL') ?: 'http://localhost:8484'
    private static final String DEFAULT_REALM = System.getenv('TIPI_REALM') ?: 'jade-tipi'
    private static final String DEFAULT_CLIENT_ID = System.getenv('TIPI_CLIENT_ID') ?: 'tipi-cli'
    private static final String DEFAULT_CLIENT_SECRET = System.getenv('TIPI_CLIENT_SECRET') ?: '7e8d5df5-5afb-4cc0-8d56-9f3f5c7cc5fd'

    static void main(String[] args) {
        def cli = new CliBuilder(
                name: 'tipi',
                usage: 'tipi <command> [options]',
                footer: '\nEnvironment overrides: TIPI_KEYCLOAK_URL, TIPI_REALM, TIPI_CLIENT_ID, TIPI_CLIENT_SECRET'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
        }

        if (!args) {
            printGlobalUsage(cli)
            return
        }

        def command = args[0]
        String[] commandArgs = args.length > 1 ? (args[1..-1] as String[]) : new String[0]

        if (command in ['-h', '--help']) {
            printGlobalUsage(cli)
            return
        }

        switch (command) {
            case 'create-transaction':
                handleCreateTransaction(commandArgs)
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

    private static void handleCreateTransaction(String[] args) {
        def cli = new CliBuilder(
                name: 'tipi create-transaction',
                usage: 'tipi create-transaction [options]',
                header: 'Obtain a JWT for service-to-service transactions.',
                footer: '\nEnvironment overrides: TIPI_KEYCLOAK_URL, TIPI_REALM, TIPI_CLIENT_ID, TIPI_CLIENT_SECRET'
        )
        cli.with {
            h(longOpt: 'help', 'Show this help message')
            _(longOpt: 'url', args: 1, argName: 'keycloak-url', 'Keycloak base URL', defaultValue: DEFAULT_KEYCLOAK_URL)
            _(longOpt: 'realm', args: 1, argName: 'realm', 'Keycloak realm', defaultValue: DEFAULT_REALM)
            _(longOpt: 'client-id', args: 1, argName: 'client-id', 'OAuth client identifier', defaultValue: DEFAULT_CLIENT_ID)
            _(longOpt: 'client-secret', args: 1, argName: 'secret', 'OAuth client secret', defaultValue: DEFAULT_CLIENT_SECRET)
        }

        OptionAccessor options = cli.parse(args)
        if (!options) {
            // CLI prints errors itself
            System.exit(1)
        }

        if (options.h) {
            cli.usage()
            return
        }

        Map<String, String> effective = [
                url         : options.'url' ?: DEFAULT_KEYCLOAK_URL,
                realm       : options.'realm' ?: DEFAULT_REALM,
                clientId    : options.'client-id' ?: DEFAULT_CLIENT_ID,
                clientSecret: options.'client-secret' ?: DEFAULT_CLIENT_SECRET
        ]

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
            println token

            String decodedPayloadJson = decodeJwtPayload(token)
            if (decodedPayloadJson) {
                println ''
                def payloadObj = new JsonSlurper().parseText(decodedPayloadJson)
                println JsonOutput.prettyPrint(JsonOutput.toJson(payloadObj))
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

    private static void printGlobalUsage(CliBuilder cli) {
        println """Tipi CLI - Jade Tipi command-line client

Usage:
  tipi create-transaction [options]
  tipi help

Commands:
  create-transaction   Obtain a JWT for service-to-service transactions.
"""
        cli.usage()
    }
}
