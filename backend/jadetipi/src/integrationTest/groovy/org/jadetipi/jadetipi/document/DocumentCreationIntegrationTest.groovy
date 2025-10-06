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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Integration test for document creation flow
 * Tests the complete workflow: create document -> verify it's accessible
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class DocumentCreationIntegrationTest {

    @Autowired
    WebTestClient webTestClient

    @Autowired
    ObjectMapper objectMapper

    @Test
    void 'should create document and make it immediately accessible'() {
        def documentId = UUID.randomUUID().toString()
        def documentData = [name: 'Test Document', value: 42, description: 'Integration test']

        // Step 1: Create document
        webTestClient.post()
            .uri("/api/documents/{id}", documentId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(documentData)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath('$._id').isEqualTo(documentId)
            .jsonPath('$.name').isEqualTo('Test Document')
            .jsonPath('$.value').isEqualTo(42)

        // Step 2: Small delay to allow for reactive stream completion and MongoDB write
        Thread.sleep(50)

        // Step 3: Verify document is readable (read-after-write)
        webTestClient.get()
            .uri("/api/documents/{id}", documentId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath('$._id').isEqualTo(documentId)
            .jsonPath('$.name').isEqualTo('Test Document')
            .jsonPath('$.value').isEqualTo(42)
            .jsonPath('$.description').isEqualTo('Integration test')
    }

    @Test
    void 'should handle document not found'() {
        def nonExistentId = UUID.randomUUID().toString()

        webTestClient.get()
            .uri("/api/documents/{id}", nonExistentId)
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    void 'should update existing document'() {
        def documentId = UUID.randomUUID().toString()
        def initialData = [name: 'Original', value: 1]
        def updatedData = [name: 'Updated', value: 2, newField: 'added']

        // Create
        webTestClient.post()
            .uri("/api/documents/{id}", documentId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(initialData)
            .exchange()
            .expectStatus().isCreated()

        // Allow time for document to be persisted
        Thread.sleep(50)

        // Update
        webTestClient.put()
            .uri("/api/documents/{id}", documentId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updatedData)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath('$.name').isEqualTo('Updated')
            .jsonPath('$.value').isEqualTo(2)
            .jsonPath('$.newField').isEqualTo('added')

        // Allow time for update to be persisted
        Thread.sleep(50)

        // Verify
        webTestClient.get()
            .uri("/api/documents/{id}", documentId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath('$.name').isEqualTo('Updated')
            .jsonPath('$.value').isEqualTo(2)
    }

    @Test
    void 'should delete document'() {
        def documentId = UUID.randomUUID().toString()
        def documentData = [name: 'To Delete', value: 99]

        // Create
        webTestClient.post()
            .uri("/api/documents/{id}", documentId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(documentData)
            .exchange()
            .expectStatus().isCreated()

        // Allow time for document to be persisted
        Thread.sleep(50)

        // Delete
        webTestClient.delete()
            .uri("/api/documents/{id}", documentId)
            .exchange()
            .expectStatus().isNoContent()

        // Verify deletion
        webTestClient.get()
            .uri("/api/documents/{id}", documentId)
            .exchange()
            .expectStatus().isNotFound()
    }
}
