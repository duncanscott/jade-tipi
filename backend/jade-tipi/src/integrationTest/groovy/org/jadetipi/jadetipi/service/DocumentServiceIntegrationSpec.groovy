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
package org.jadetipi.jadetipi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.spockframework.spring.EnableSharedInjection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import reactor.test.StepVerifier

@SpringBootTest
@EnableSharedInjection
@ActiveProfiles("test")
class DocumentServiceIntegrationSpec extends Specification {

    @Autowired
    DocumentService documentServiceMongoDb

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    @Autowired
    ObjectMapper objectMapper

    private static final String COLLECTION_NAME = "objectNodes"

    def cleanup() {
        // Drop the collection after each test for isolation
        mongoTemplate.dropCollection(COLLECTION_NAME).block()
    }

    def "should create a new document"() {
        given:
        String id = "test-create-id"
        ObjectNode objectNode = objectMapper.createObjectNode()
        objectNode.put("name", "Test Document")
        objectNode.put("description", "Test description")

        when:
        Mono<ObjectNode> result = documentServiceMongoDb.create(id, objectNode)

        then:
        StepVerifier.create(result)
                .assertNext(created -> {
                    assert created.get("_id").asText() == id
                    assert created.get("name").asText() == "Test Document"
                    assert created.get("description").asText() == "Test description"
                })
                .verifyComplete()
    }

    def "should find document by id"() {
        given:
        String id = "test-find-id"
        ObjectNode objectNode = objectMapper.createObjectNode()
        objectNode.put("name", "Find Test")
        documentServiceMongoDb.create(id, objectNode).block()

        when:
        Mono<ObjectNode> result = documentServiceMongoDb.findById(id)

        then:
        StepVerifier.create(result)
                .assertNext(found -> {
                    assert found.get("_id").asText() == id
                    assert found.get("name").asText() == "Find Test"
                })
                .verifyComplete()
    }

    def "should return empty when document not found"() {
        given:
        String badId = 'this-is-not-a-valid-id'

        when:
        Mono<ObjectNode> result = documentServiceMongoDb.findById(badId)

        then:
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete()
    }

    def "should update an existing document"() {
        given:
        String id = "test-update-id"
        ObjectNode originalNode = objectMapper.createObjectNode()
        originalNode.put("name", "Original Name")
        originalNode.put("value", 100)
        documentServiceMongoDb.create(id, originalNode).block()

        ObjectNode updatedNode = objectMapper.createObjectNode()
        updatedNode.put("name", "Updated Name")
        updatedNode.put("value", 200)

        when:
        Mono<ObjectNode> result = documentServiceMongoDb.update(id, updatedNode)

        then:
        StepVerifier.create(result)
                .assertNext(updated -> {
                    assert updated.get("_id").asText() == id
                    assert updated.get("name").asText() == "Updated Name"
                    assert updated.get("value").asInt() == 200
                })
                .verifyComplete()
    }

    def "should delete a document"() {
        given:
        String id = "test-delete-id"
        ObjectNode objectNode = objectMapper.createObjectNode()
        objectNode.put("name", "To Delete")
        documentServiceMongoDb.create(id, objectNode).block()

        when:
        Mono<Boolean> deleteResult = documentServiceMongoDb.delete(id)
        Mono<ObjectNode> findResult = documentServiceMongoDb.findById(id)

        then:
        StepVerifier.create(deleteResult)
                .expectNext(true)
                .verifyComplete()

        StepVerifier.create(findResult)
                .expectNextCount(0)
                .verifyComplete()
    }

    def "should return false when deleting non-existent document"() {
        given:
        String id = "non-existent-id"

        when:
        Mono<Boolean> result = documentServiceMongoDb.delete(id)

        then:
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
    }

    def "should find all documents summary with only id and name fields"() {
        given:
        ObjectNode doc1 = objectMapper.createObjectNode()
        doc1.put("name", "Document One")
        doc1.put("description", "Should not appear")
        documentServiceMongoDb.create("summary-1", doc1).block()

        ObjectNode doc2 = objectMapper.createObjectNode()
        doc2.put("name", "Document Two")
        doc2.put("value", 999)
        documentServiceMongoDb.create("summary-2", doc2).block()

        ObjectNode doc3 = objectMapper.createObjectNode()
        doc3.put("description", "No name field")
        documentServiceMongoDb.create("summary-3", doc3).block()

        when:
        Flux<ObjectNode> result = documentServiceMongoDb.findAllSummary()

        then:
        StepVerifier.create(result)
                .assertNext(node -> {
                    assert node.has("_id")
                    // Only _id and name should be present
                    assert !node.has("description")
                    assert !node.has("value")
                })
                .assertNext(node -> {
                    assert node.has("_id")
                    assert !node.has("description")
                    assert !node.has("value")
                })
                .assertNext(node -> {
                    assert node.has("_id")
                    assert !node.has("description")
                })
                .verifyComplete()
    }

    def "should delete corrupted documents"() {
        given:
        // Create a normal document
        ObjectNode normalDoc = objectMapper.createObjectNode()
        normalDoc.put("name", "Normal Document")
        documentServiceMongoDb.create("normal-doc", normalDoc).block()

        // Create a corrupted document with _children field (simulating Jackson metadata)
        ObjectNode corruptedDoc = objectMapper.createObjectNode()
        corruptedDoc.put("name", "Corrupted Document")
        corruptedDoc.putArray("_children")
        documentServiceMongoDb.create("corrupted-doc", corruptedDoc).block()

        when:
        Mono<Long> deleteResult = documentServiceMongoDb.deleteCorruptedDocuments()
        Flux<ObjectNode> remainingDocs = documentServiceMongoDb.findAllSummary()

        then:
        StepVerifier.create(deleteResult)
                .expectNext(1L)  // One corrupted document deleted
                .verifyComplete()

        StepVerifier.create(remainingDocs)
                .assertNext(node -> {
                    assert node.get("_id").asText() == "normal-doc"
                    assert node.get("name").asText() == "Normal Document"
                })
                .verifyComplete()
    }
}
