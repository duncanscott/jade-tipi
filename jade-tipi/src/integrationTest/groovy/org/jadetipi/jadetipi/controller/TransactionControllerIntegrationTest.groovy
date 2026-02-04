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
import org.jadetipi.dto.message.Group
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.jadetipi.config.KeycloakTestHelper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
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
        def group = "grp-${UUID.randomUUID()}"

        def response = webTestClient.post()
                .uri("/api/transactions/open")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new Group(organization, group))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map)
                .returnResult()
                .responseBody

        assert response != null
        assert response.id
        assert response.secret
        assert (response.id as String).contains(organization)
        assert (response.id as String).contains(group)
        assert response.grp
        assert response.grp.organization == organization
        assert response.grp.group == group

        def transactionQuery = Query.query(Criteria.where("_id").is(response.id))
        def createdDocument = reactiveMongoTemplate.findOne(transactionQuery, Document, "txn").block()
        try {
            assert createdDocument != null
            def txn = createdDocument.get("txn", Document)
            def grp = createdDocument.get("grp", Document)
            assert txn.getString("secret") == response.secret
            assert txn.getString("open_seq") != null
            assert txn.getDate("opened") != null
            assert txn.getString("commit_seq") == null
            assert txn.get("committed") == null
            assert grp.getString("org") == organization
            assert grp.getString("group") == group
        } finally {
            reactiveMongoTemplate.remove(transactionQuery, "txn").block()
        }
    }

    @Test
    void 'should commit transaction and write commit metadata when authenticated'() {
        def organization = "org-${UUID.randomUUID()}"
        def group = "grp-${UUID.randomUUID()}"

        def createResponse = webTestClient.post()
                .uri("/api/transactions/open")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new Group(organization, group))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map)
                .returnResult()
                .responseBody

        def commitRequest = new TransactionToken(
                createResponse.id as String,
                createResponse.secret as String,
                new Group(organization, group)
        )

        def commitResponse = webTestClient.post()
                .uri("/api/transactions/commit")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commitRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map)
                .returnResult()
                .responseBody

        assert commitResponse != null
        assert commitResponse.transactionId == createResponse.id
        assert commitResponse.commitId

        def transactionQuery = Query.query(Criteria.where("_id").is(createResponse.id))
        def committedDocument = reactiveMongoTemplate.findOne(transactionQuery, Document, "txn").block()
        try {
            assert committedDocument != null
            def txn = committedDocument.get("txn", Document)
            assert txn.getString("secret") == createResponse.secret
            assert txn.getString("commit") == commitResponse.commitId
            assert txn.getString("commit_seq") != null
            assert txn.getDate("committed") != null
        } finally {
            reactiveMongoTemplate.remove(transactionQuery, "txn").block()
        }
    }

    @Test
    void 'should return conflict when committing an already committed transaction'() {
        def organization = "org-${UUID.randomUUID()}"
        def group = "grp-${UUID.randomUUID()}"

        def createResponse = webTestClient.post()
                .uri("/api/transactions/open")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new Group(organization, group))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map)
                .returnResult()
                .responseBody

        def commitRequest = new TransactionToken(
                createResponse.id as String,
                createResponse.secret as String,
                new Group(organization, group)
        )

        webTestClient.post()
                .uri("/api/transactions/commit")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commitRequest)
                .exchange()
                .expectStatus().isOk()

        webTestClient.post()
                .uri("/api/transactions/commit")
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commitRequest)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }
}
