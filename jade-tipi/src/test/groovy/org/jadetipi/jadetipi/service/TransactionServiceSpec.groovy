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

import org.jadetipi.dto.permission.Group
import org.jadetipi.dto.transaction.TransactionToken
import org.jadetipi.id.IdGenerator
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import spock.lang.Specification

class TransactionServiceSpec extends Specification {

    ReactiveMongoTemplate mongoTemplate
    IdGenerator idGenerator
    TransactionService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        idGenerator = Mock(IdGenerator)
        service = new TransactionService(mongoTemplate, idGenerator)
    }

    def "openTransaction should generate valid transaction ID with correct format"() {
        given: "a group and mocked ID generator"
        def group = new Group('test-org', 'test-group')
        idGenerator.nextId() >> 'abc123xyz'
        idGenerator.nextKey() >> 'secretKey123'
        mongoTemplate.save(_ as Map, 'transaction') >> Mono.just([:])

        when: "opening a transaction"
        def result = service.openTransaction(group)

        then: "transaction ID should have correct format"
        StepVerifier.create(result)
                .expectNextMatches { token ->
                    token.transactionId() == 'abc123xyz~test-org~test-group' &&
                    token.secret() == 'secretKey123' &&
                    token.group() == group
                }
                .verifyComplete()
    }

    def "openTransaction should persist transaction to MongoDB"() {
        given: "a group"
        def group = new Group('org1', 'group1')
        idGenerator.nextId() >> 'id123'
        idGenerator.nextKey() >> 'secret456'

        and: "capture the saved document"
        Map savedDoc = null
        mongoTemplate.save(_ as Map, 'transaction') >> { Map doc, String collection ->
            savedDoc = doc
            Mono.just(doc)
        }

        when: "opening a transaction"
        service.openTransaction(group).block()

        then: "document should be saved with correct fields"
        savedDoc != null
        savedDoc._id == 'id123~org1~group1'
        savedDoc.organization == 'org1'
        savedDoc.group == 'group1'
        savedDoc.secret == 'secret456'
        savedDoc.created != null
    }

    def "openTransaction should reject null group"() {
        when: "opening transaction with null group"
        service.openTransaction(null)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }

    def "openTransaction should reject blank organization"() {
        given: "group with blank organization"
        def group = new Group('', 'test-group')

        when: "opening transaction"
        service.openTransaction(group)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }

    def "openTransaction should reject blank group name"() {
        given: "group with blank name"
        def group = new Group('test-org', '')

        when: "opening transaction"
        service.openTransaction(group)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }

    def "commitTransaction should validate and update transaction"() {
        given: "an existing transaction"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('tx-123', 'secret123', group)
        def existingDoc = [
            _id: 'tx-123',
            organization: 'org1',
            group: 'group1',
            secret: 'secret123',
            created: new Date()
        ]

        and: "mocked dependencies"
        idGenerator.nextId() >> 'commit-456'
        mongoTemplate.findById('tx-123', Map.class, 'transaction') >> Mono.just(existingDoc)

        Map savedDoc = null
        mongoTemplate.save(_ as Map, 'transaction') >> { Map doc, String collection ->
            savedDoc = doc
            Mono.just(doc)
        }

        when: "committing transaction"
        def result = service.commitTransaction(token)

        then: "commit should succeed"
        StepVerifier.create(result)
                .expectNextMatches { commit ->
                    commit.transactionId() == 'tx-123' &&
                    commit.commitId() == 'commit-456~org1~group1'
                }
                .verifyComplete()

        and: "document should be updated with commit info"
        savedDoc.commit == 'commit-456~org1~group1'
        savedDoc.committed != null
    }

    def "commitTransaction should reject transaction not found"() {
        given: "a transaction token for non-existent transaction"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('tx-404', 'secret123', group)
        idGenerator.nextId() >> 'commit-id'
        mongoTemplate.findById('tx-404', Map.class, 'transaction') >> Mono.empty()

        when: "committing transaction"
        def result = service.commitTransaction(token)

        then: "should error with transaction not found"
        StepVerifier.create(result)
                .expectErrorMatches { ex ->
                    ex instanceof IllegalArgumentException &&
                    ex.message == 'Transaction not found'
                }
                .verify()
    }

    def "commitTransaction should reject invalid secret"() {
        given: "transaction with wrong secret"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('tx-123', 'wrong-secret', group)
        def existingDoc = [
            _id: 'tx-123',
            secret: 'correct-secret',
            created: new Date()
        ]
        idGenerator.nextId() >> 'commit-id'
        mongoTemplate.findById('tx-123', Map.class, 'transaction') >> Mono.just(existingDoc)

        when: "committing with wrong secret"
        def result = service.commitTransaction(token)

        then: "should error with invalid secret"
        StepVerifier.create(result)
                .expectErrorMatches { ex ->
                    ex instanceof IllegalArgumentException &&
                    ex.message == 'Invalid transaction secret'
                }
                .verify()
    }

    def "commitTransaction should reject already committed transaction"() {
        given: "an already committed transaction"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('tx-123', 'secret123', group)
        def existingDoc = [
            _id: 'tx-123',
            secret: 'secret123',
            commit: 'existing-commit',  // Already committed
            created: new Date()
        ]
        idGenerator.nextId() >> 'commit-id'
        mongoTemplate.findById('tx-123', Map.class, 'transaction') >> Mono.just(existingDoc)

        when: "committing again"
        def result = service.commitTransaction(token)

        then: "should error with already committed"
        StepVerifier.create(result)
                .expectErrorMatches { ex ->
                    ex instanceof IllegalStateException &&
                    ex.message == 'Transaction already committed'
                }
                .verify()
    }

    def "commitTransaction should reject null token"() {
        when: "committing with null token"
        service.commitTransaction(null)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }

    def "commitTransaction should reject blank transaction ID"() {
        given: "token with blank ID"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('', 'secret123', group)

        when: "committing"
        service.commitTransaction(token)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }

    def "commitTransaction should reject blank secret"() {
        given: "token with blank secret"
        def group = new Group('org1', 'group1')
        def token = new TransactionToken('tx-123', '', group)

        when: "committing"
        service.commitTransaction(token)

        then: "IllegalArgumentException should be thrown"
        thrown(IllegalArgumentException)
    }
}
