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
package org.jadetipi.jadetipi.admin

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.time.Duration

/**
 * End-to-end integration coverage for the admin group endpoints over a real
 * Keycloak realm. Acquires JWTs via direct-access password grants for
 * {@code dnscott} (admin) and {@code testuser} (non-admin) and asserts that
 * the {@code /api/admin/groups} routes accept admin tokens, reject
 * unauthenticated and authenticated-non-admin requests, and persist
 * root-shaped {@code grp} documents.
 *
 * <p>Skip / run conditions (deliberately opt-in):
 * <ul>
 *   <li>Environment variable {@code JADETIPI_IT_ADMIN_GROUPS} must be set to
 *       {@code 1}, {@code true}, {@code TRUE}, or {@code yes}.</li>
 *   <li>The Keycloak realm OpenID configuration at
 *       {@code ${TEST_KEYCLOAK_URL ?: KEYCLOAK_URL ?: 'http://localhost:8484'}/realms/jade-tipi/.well-known/openid-configuration}
 *       must respond {@code 2xx}.</li>
 * </ul>
 *
 * <p>Run locally:
 * <pre>
 * docker compose -f docker/docker-compose.yml up -d
 * JADETIPI_IT_ADMIN_GROUPS=1 ./gradlew :jade-tipi:integrationTest \
 *     --tests '*GroupAdminAuthIntegrationSpec*'
 * </pre>
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles('test')
@IgnoreIf({ !GroupAdminAuthIntegrationSpec.integrationGateOpen() })
class GroupAdminAuthIntegrationSpec extends Specification {

    private static final String KEYCLOAK_BASE_URL =
            System.getenv('TEST_KEYCLOAK_URL') ?:
                    (System.getenv('KEYCLOAK_URL') ?: 'http://localhost:8484')
    private static final String REALM = 'jade-tipi'
    // Use the jade-tipi confidential backend client for direct-access password
    // grants. The frontend client is public and intentionally does not enable
    // direct-access grants. Realm roles ride on the access token regardless of
    // which client did the login.
    private static final String DIRECT_GRANT_CLIENT_ID = 'jade-tipi'
    private static final String DIRECT_GRANT_CLIENT_SECRET =
            System.getenv('TEST_CLIENT_SECRET') ?: 'd84c91af-7f37-4b8d-9157-0f6c6a91fb45'

    private static final String GRP_COLLECTION = 'grp'
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(10)

    static boolean integrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_ADMIN_GROUPS')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        return keycloakReachable()
    }

    private static boolean keycloakReachable() {
        HttpURLConnection conn = null
        try {
            URL url = new URL("${KEYCLOAK_BASE_URL}/realms/${REALM}/.well-known/openid-configuration")
            conn = (HttpURLConnection) url.openConnection()
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = 'GET'
            int code = conn.responseCode
            return code >= 200 && code < 300
        } catch (Exception ignored) {
            return false
        } finally {
            if (conn != null) {
                conn.disconnect()
            }
        }
    }

    private static String passwordGrant(String username, String password) {
        WebClient webClient = WebClient.builder().baseUrl(KEYCLOAK_BASE_URL).build()
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>()
        form.add('grant_type', 'password')
        form.add('client_id', DIRECT_GRANT_CLIENT_ID)
        form.add('client_secret', DIRECT_GRANT_CLIENT_SECRET)
        form.add('username', username)
        form.add('password', password)
        form.add('scope', 'openid')
        Map response = webClient.post()
                .uri("/realms/${REALM}/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block()
        return response.access_token as String
    }

    @Autowired
    WebTestClient webTestClient

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    String createdId

    def cleanup() {
        if (createdId != null) {
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(createdId)),
                    GRP_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
        }
    }

    def 'admin token can create, read, and list grp records'() {
        given:
        String adminToken = passwordGrant('dnscott', 'dnscott')

        when: 'POST /api/admin/groups creates a root-shaped grp'
        Map createBody = [
                name       : 'IT analytics',
                description: 'integration spec admin-created grp',
                permissions: [:]
        ]
        Map createResponse = webTestClient.post()
                .uri('/api/admin/groups')
                .header('Authorization', "Bearer ${adminToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .responseBody
        createdId = createResponse.id as String

        then:
        createdId != null
        createResponse.collection == 'grp'
        createResponse.head.provenance.action == 'create'
        (createResponse.head.provenance.txnId as String).startsWith('admin~')

        when: 'GET /api/admin/groups/{id} returns the same record'
        Map readBody = webTestClient.get()
                .uri('/api/admin/groups/{id}', createdId)
                .header('Authorization', "Bearer ${adminToken}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .responseBody

        then:
        readBody.id == createdId
        readBody.name == 'IT analytics'

        when: 'GET /api/admin/groups returns a list including the created id'
        Map listBody = webTestClient.get()
                .uri('/api/admin/groups')
                .header('Authorization', "Bearer ${adminToken}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .responseBody

        then:
        ((List) listBody.items).any { Map row -> row.id == createdId }
    }

    def 'unauthenticated request to /api/admin/groups returns 401'() {
        expect:
        webTestClient.get()
                .uri('/api/admin/groups')
                .exchange()
                .expectStatus().isUnauthorized()
    }

    def 'authenticated non-admin request to /api/admin/groups returns 403'() {
        given:
        String userToken = passwordGrant('testuser', 'testpassword')

        expect:
        webTestClient.get()
                .uri('/api/admin/groups')
                .header('Authorization', "Bearer ${userToken}")
                .exchange()
                .expectStatus().isForbidden()
    }
}
