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
package org.jadetipi.jadetipi.contents

import org.jadetipi.dto.collections.Transaction
import org.jadetipi.jadetipi.config.KeycloakTestHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.time.Duration

/**
 * End-to-end TASK-054 coverage for the paged location browse
 * ({@code GET /api/locations}). Roots are planted directly in MongoDB (no
 * Kafka involved); the spec proves the authenticated 200 with the page
 * envelope and planted summaries, the query-read convention, and the 401
 * for unauthenticated callers.
 *
 * <p>Gated on the docker itest stack being up (the shared
 * {@code JADETIPI_IT_KAFKA} opt-in flag) plus Keycloak reachability for
 * the bearer token; Kafka itself is not used.
 */
@SpringBootTest
@ActiveProfiles('test')
@AutoConfigureWebTestClient
@IgnoreIf({ !LocationBrowseHttpReadIntegrationSpec.integrationGateOpen() })
class LocationBrowseHttpReadIntegrationSpec extends Specification {

    private static final String KEYCLOAK_BASE_URL = 'http://localhost:8484'
    private static final String KEYCLOAK_REALM = 'jade-tipi'
    private static final String LOC_COLLECTION = 'loc'
    private static final Duration MONGO_BLOCK_TIMEOUT = Duration.ofSeconds(5)

    static boolean integrationGateOpen() {
        String flag = System.getenv('JADETIPI_IT_KAFKA')
        if (!(flag in ['1', 'true', 'TRUE', 'yes'])) {
            return false
        }
        return keycloakReachable()
    }

    private static boolean keycloakReachable() {
        HttpURLConnection conn = null
        try {
            URL url = new URL("${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration")
            conn = (HttpURLConnection) url.openConnection()
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            return conn.responseCode == 200
        } catch (Exception ignored) {
            return false
        } finally {
            conn?.disconnect()
        }
    }

    @Autowired
    WebTestClient webTestClient

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    Transaction txn
    String freezerLocId
    String plateLocId

    def setup() {
        txn = Transaction.newInstance('jade-itest-org', 'browse', 'jade-itest-cli', 'itest-user')
        // Object identifier convention (TASK-044): transaction-UUID form.
        String idPrefix = "jade-itest-org~browse~${txn.uuid()}"
        freezerLocId = "${idPrefix}~loc~freezer_a"
        plateLocId = "${idPrefix}~loc~plate_0001"
    }

    def cleanup() {
        [freezerLocId, plateLocId].findAll { it != null }.each { String id ->
            mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)),
                    LOC_COLLECTION).block(Duration.ofSeconds(10))
        }
    }

    private void plantLocRoot(String id, Map<String, Object> properties) {
        Map<String, Object> doc = [
                _id       : id,
                id        : id,
                collection: 'loc',
                type_id   : null,
                properties: properties,
                links     : [:],
                _head     : [schema_version: 1, document_kind: 'root', root_id: id]
        ] as Map<String, Object>
        mongoTemplate.insert(doc, LOC_COLLECTION).block(MONGO_BLOCK_TIMEOUT)
    }

    def 'authenticated browse returns the page envelope with planted summaries'() {
        given: 'two loc roots planted directly (no Kafka involved)'
        plantLocRoot(freezerLocId, [name: 'freezer_a', description: 'minus-80 freezer'])
        plantLocRoot(plateLocId, [name: 'plate_0001'])
        String token = KeycloakTestHelper.getAccessToken()

        expect: 'a size-100 first page contains both planted summaries and the envelope'
        webTestClient.get()
                .uri('/api/locations?page=0&size=100')
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.page').isEqualTo(0)
                .jsonPath('$.size').isEqualTo(100)
                .jsonPath('$.total').value { Number total -> assert total.longValue() >= 2L }
                .jsonPath("\$.items[?(@.location_id == '${freezerLocId}')].name").isEqualTo('freezer_a')
                .jsonPath("\$.items[?(@.location_id == '${freezerLocId}')].description").isEqualTo('minus-80 freezer')
                .jsonPath("\$.items[?(@.location_id == '${plateLocId}')].name").isEqualTo('plate_0001')
    }

    def 'oversized page size is clamped and echoed'() {
        given:
        String token = KeycloakTestHelper.getAccessToken()

        expect:
        webTestClient.get()
                .uri('/api/locations?page=0&size=5000')
                .header('Authorization', "Bearer ${token}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.size').isEqualTo(100)
    }

    def 'unauthenticated browse is rejected'() {
        expect:
        webTestClient.get()
                .uri('/api/locations')
                .exchange()
                .expectStatus().isUnauthorized()
    }
}
