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
package org.jadetipi.jade.cli

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Base64

class JadeCli {

    private static final String DEFAULT_KEYCLOAK_URL = System.getenv('JADE_KEYCLOAK_URL') ?: 'http://localhost:8484'
    private static final String DEFAULT_REALM = System.getenv('JADE_REALM') ?: 'jade-tipi'
    private static final String DEFAULT_CLIENT_ID = System.getenv('JADE_CLIENT_ID') ?: 'jade-cli'
    private static final String DEFAULT_CLIENT_SECRET = System.getenv('JADE_CLIENT_SECRET') ?: '62ba4c1e-7f7e-46c7-9793-f752c63f2e10'

    static void main(String[] args) {
        if (!args) {
            printUsage()
            return
        }

        String command = args[0]
        String[] commandArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]

        switch (command) {
            case 'create-transaction':
                handleCreateTransaction(commandArgs)
                break
            case 'help':
            case '-h':
            case '--help':
                printUsage()
                break
            default:
                System.err.println("Unknown command: ${command}\n")
                printUsage()
                System.exit(1)
        }
    }

    private static void handleCreateTransaction(String[] args) {
        Map<String, String> options = parseOptions(args)

        String tokenEndpoint = buildTokenEndpoint(options.url, options.realm)
        String formBody = buildClientCredentialsBody(options.clientId, options.clientSecret)

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

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opts = [
                url         : DEFAULT_KEYCLOAK_URL,
                realm       : DEFAULT_REALM,
                clientId    : DEFAULT_CLIENT_ID,
                clientSecret: DEFAULT_CLIENT_SECRET
        ]

        Iterator<String> iter = Arrays.asList(args).iterator()
        while (iter.hasNext()) {
            String flag = iter.next()
            if (!flag.startsWith('--')) {
                System.err.println("Unexpected argument '${flag}'.")
                printCreateTransactionUsage()
                System.exit(1)
            }

            String key = flag.substring(2)
            if (!iter.hasNext()) {
                System.err.println("Missing value for option '${flag}'.")
                printCreateTransactionUsage()
                System.exit(1)
            }
            String value = iter.next()

            switch (key) {
                case 'url':
                    opts.url = value
                    break
                case 'realm':
                    opts.realm = value
                    break
                case 'client-id':
                    opts.clientId = value
                    break
                case 'client-secret':
                    opts.clientSecret = value
                    break
                default:
                    System.err.println("Unknown option '--${key}'.")
                    printCreateTransactionUsage()
                    System.exit(1)
            }
        }
        return opts
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

    private static void printUsage() {
        println """Jade CLI - Jade Tipi command-line client

Usage:
  jade create-transaction [options]
  jade help

Commands:
  create-transaction   Obtain a JWT for service-to-service transactions.

Run 'jade create-transaction --help' for command-specific options."""
    }

    private static void printCreateTransactionUsage() {
        println """Usage: jade create-transaction [options]

Options:
  --url <keycloak-url>          Keycloak base URL (default: ${DEFAULT_KEYCLOAK_URL})
  --realm <realm>               Keycloak realm (default: ${DEFAULT_REALM})
  --client-id <client-id>       OAuth client identifier (default: ${DEFAULT_CLIENT_ID})
  --client-secret <secret>      OAuth client secret (default: value bundled with CLI)

Environment overrides:
  JADE_KEYCLOAK_URL, JADE_REALM, JADE_CLIENT_ID, JADE_CLIENT_SECRET"""
    }
}
