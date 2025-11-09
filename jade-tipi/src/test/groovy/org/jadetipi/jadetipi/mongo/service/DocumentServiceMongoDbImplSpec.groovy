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
package org.jadetipi.jadetipi.mongo.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.mongodb.client.result.DeleteResult
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import spock.lang.Specification

class DocumentServiceMongoDbImplSpec extends Specification {

    ReactiveMongoTemplate mongoTemplate
    ObjectMapper objectMapper
    DocumentServiceMongoDbImpl service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        objectMapper = new ObjectMapper()
        service = new DocumentServiceMongoDbImpl(mongoTemplate, objectMapper)
    }

    def "create should save document with ID"() {
        given: "a document to create"
        def id = 'test-doc-123'
        def docNode = objectMapper.createObjectNode()
        docNode.put('name', 'Test Document')
        docNode.put('value', 42)

        and: "mock MongoDB insert"
        Map savedMap = null
        mongoTemplate.insert(_ as Map, 'objectNodes') >> { Map map, String collection ->
            savedMap = map
            Mono.just(map)
        }

        when: "creating document"
        def result = service.create(id, docNode)

        then: "document should be inserted with ID"
        StepVerifier.create(result)
                .expectNextMatches { node ->
                    node.get('_id').asText() == id &&
                    node.get('name').asText() == 'Test Document' &&
                    node.get('value').asInt() == 42
                }
                .verifyComplete()

        and: "saved map should have correct structure"
        savedMap._id == id
        savedMap.name == 'Test Document'
        savedMap.value == 42
    }

    def "create should handle insert errors"() {
        given: "a document"
        def docNode = objectMapper.createObjectNode()
        docNode.put('name', 'Test')

        and: "MongoDB insert fails"
        mongoTemplate.insert(_ as Map, 'objectNodes') >>
            Mono.error(new RuntimeException('Duplicate key'))

        when: "creating document"
        def result = service.create('test-id', docNode)

        then: "error should propagate"
        StepVerifier.create(result)
                .expectError(RuntimeException)
                .verify()
    }

    def "update should save document with ID"() {
        given: "a document to update"
        def id = 'update-doc-123'
        def docNode = objectMapper.createObjectNode()
        docNode.put('name', 'Updated Document')
        docNode.put('version', 2)

        and: "mock MongoDB save"
        Map savedMap = null
        mongoTemplate.save(_ as Map, 'objectNodes') >> { Map map, String collection ->
            savedMap = map
            Mono.just(map)
        }

        when: "updating document"
        def result = service.update(id, docNode)

        then: "document should be saved"
        StepVerifier.create(result)
                .expectNextMatches { node ->
                    node.get('_id').asText() == id &&
                    node.get('name').asText() == 'Updated Document'
                }
                .verifyComplete()

        and: "saved map should include ID"
        savedMap._id == id
    }

    def "delete should remove document and return true when found"() {
        given: "a document ID"
        def id = 'delete-doc-123'

        and: "mock successful deletion"
        def deleteResult = Mock(DeleteResult)
        deleteResult.getDeletedCount() >> 1
        mongoTemplate.remove(_ as Query, Map.class, 'objectNodes') >> Mono.just(deleteResult)

        when: "deleting document"
        def result = service.delete(id)

        then: "should return true"
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete()
    }

    def "delete should return false when document not found"() {
        given: "a document ID"
        def id = 'missing-doc'

        and: "mock deletion with no results"
        def deleteResult = Mock(DeleteResult)
        deleteResult.getDeletedCount() >> 0
        mongoTemplate.remove(_ as Query, Map.class, 'objectNodes') >> Mono.just(deleteResult)

        when: "deleting non-existent document"
        def result = service.delete(id)

        then: "should return false"
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
    }

    def "findAllSummary should return documents with only ID and name"() {
        given: "mock documents in MongoDB"
        def doc1 = [_id: 'doc1', name: 'Document 1', value: 100]
        def doc2 = [_id: 'doc2', name: 'Document 2', value: 200]

        mongoTemplate.find(_ as Query, Map.class, 'objectNodes') >>
            Flux.just(doc1, doc2)

        when: "finding all summaries"
        def result = service.findAllSummary()

        then: "should return summary nodes"
        StepVerifier.create(result)
                .expectNextMatches { node ->
                    node.get('_id').asText() == 'doc1' &&
                    node.get('name').asText() == 'Document 1'
                }
                .expectNextMatches { node ->
                    node.get('_id').asText() == 'doc2' &&
                    node.get('name').asText() == 'Document 2'
                }
                .verifyComplete()
    }

    def "findAllSummary should handle empty collection"() {
        given: "no documents in MongoDB"
        mongoTemplate.find(_ as Query, Map.class, 'objectNodes') >> Flux.empty()

        when: "finding all summaries"
        def result = service.findAllSummary()

        then: "should return empty flux"
        StepVerifier.create(result)
                .verifyComplete()
    }

    def "deleteCorruptedDocuments should remove documents with _children field"() {
        given: "mock corrupted documents"
        def deleteResult = Mock(DeleteResult)
        deleteResult.getDeletedCount() >> 5

        mongoTemplate.remove(_ as Query, 'objectNodes') >> Mono.just(deleteResult)

        when: "deleting corrupted documents"
        def result = service.deleteCorruptedDocuments()

        then: "should return count of deleted documents"
        StepVerifier.create(result)
                .expectNext(5L)
                .verifyComplete()
    }

    def "deleteCorruptedDocuments should return zero when no corrupted documents"() {
        given: "no corrupted documents"
        def deleteResult = Mock(DeleteResult)
        deleteResult.getDeletedCount() >> 0

        mongoTemplate.remove(_ as Query, 'objectNodes') >> Mono.just(deleteResult)

        when: "deleting corrupted documents"
        def result = service.deleteCorruptedDocuments()

        then: "should return zero"
        StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete()
    }
}
