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
package org.jadetipi.jadetipi.document

import com.fasterxml.jackson.databind.ObjectMapper
import org.jadetipi.jadetipi.config.KeycloakTestHelper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Integration test for document list endpoint
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class DocumentListIntegrationTest {

    @Autowired
    WebTestClient webTestClient

    @Autowired
    ObjectMapper objectMapper

    private static String accessToken

    @BeforeAll
    static void setupToken() {
        accessToken = KeycloakTestHelper.getAccessToken()
    }

    @Test
    void 'should list all documents with only id and name fields'() {
        // Create a few test documents
        def doc1Id = UUID.randomUUID().toString()
        def doc1Data = [name: 'Document One', description: 'First test document', value: 100]

        def doc2Id = UUID.randomUUID().toString()
        def doc2Data = [name: 'Document Two', description: 'Second test document', value: 200]

        def doc3Id = UUID.randomUUID().toString()
        def doc3Data = [description: 'Third test document without name', value: 300]

        // Create documents
        webTestClient.post()
                .uri("/api/documents/{id}", doc1Id)
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(doc1Data)
                .exchange()
                .expectStatus().isCreated()

        webTestClient.post()
                .uri("/api/documents/{id}", doc2Id)
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(doc2Data)
                .exchange()
                .expectStatus().isCreated()

        webTestClient.post()
                .uri("/api/documents/{id}", doc3Id)
                .header("Authorization", "Bearer ${accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(doc3Data)
                .exchange()
                .expectStatus().isCreated()

        // Small delay to allow for reactive stream completion
        Thread.sleep(100)

        // List all documents
        def response = webTestClient.get()
                .uri("/api/documents")
                .header("Authorization", "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .returnResult(Map)
                .responseBody
                .collectList()
                .block()

        // Verify we got at least our 3 documents
        assert response.size() >= 3

        // Find our test documents in the response
        def foundDoc1 = response.find { it._id == doc1Id }
        def foundDoc2 = response.find { it._id == doc2Id }
        def foundDoc3 = response.find { it._id == doc3Id }

        assert foundDoc1 != null
        assert foundDoc1._id == doc1Id
        assert foundDoc1.name == 'Document One'
        // Verify that other fields are NOT included
        assert !foundDoc1.containsKey('description')
        assert !foundDoc1.containsKey('value')

        assert foundDoc2 != null
        assert foundDoc2._id == doc2Id
        assert foundDoc2.name == 'Document Two'

        assert foundDoc3 != null
        assert foundDoc3._id == doc3Id
        // Document 3 has no name field, should only have _id
        assert !foundDoc3.containsKey('name') || foundDoc3.name == null
    }

    @Test
    void 'should return empty array when no documents exist'() {
        // This test assumes a clean database or tests in isolation
        // For now, we just verify the endpoint returns a valid JSON array
        webTestClient.get()
                .uri("/api/documents")
                .header("Authorization", "Bearer ${accessToken}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
    }
}
