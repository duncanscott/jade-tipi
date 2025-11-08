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
package org.jadetipi.jadetipi.controller

import org.bson.Document
import org.jadetipi.dto.permission.Group
import org.jadetipi.jadetipi.config.KeycloakTestHelper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    private static String accessToken

    @Autowired
    WebTestClient webTestClient

    @Autowired
    ReactiveMongoTemplate reactiveMongoTemplate

    @BeforeAll
    static void setupToken() {
        accessToken = KeycloakTestHelper.getAccessToken()
    }

    @Test
    void 'should create transaction and persist record when authenticated'() {
        def organization = "org-${UUID.randomUUID()}"
        def group = "group-${UUID.randomUUID()}"

        def response = webTestClient.post()
                .uri("/api/transactions")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new Group(organization, group))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map)
                .returnResult()
                .responseBody

        assert response != null
        assert response.transactionId
        assert response.secret
        assert (response.transactionId as String).contains(organization)
        assert (response.transactionId as String).contains(group)

        def transactionQuery = Query.query(Criteria.where("_id").is(response.transactionId))
        def createdDocument = reactiveMongoTemplate.findOne(transactionQuery, Document, "transaction").block()
        try {
            assert createdDocument != null
            assert createdDocument.getString("secret") == response.secret
            assert createdDocument.getString("organization") == organization
            assert createdDocument.getString("group") == group
        } finally {
            reactiveMongoTemplate.remove(transactionQuery, "transaction").block()
        }
    }
}
