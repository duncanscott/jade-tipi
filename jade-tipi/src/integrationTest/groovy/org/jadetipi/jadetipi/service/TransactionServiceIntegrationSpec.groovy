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
import org.spockframework.spring.EnableSharedInjection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import spock.lang.Specification

@SpringBootTest
@EnableSharedInjection
@ActiveProfiles("test")
class TransactionServiceIntegrationSpec extends Specification {

    @Autowired
    TransactionService transactionService

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    private static final String COLLECTION_NAME = "transaction"

    def cleanup() {
        mongoTemplate.collectionExists(COLLECTION_NAME)
                .flatMap { exists -> exists ? mongoTemplate.dropCollection(COLLECTION_NAME) : Mono.empty() }
                .block()
    }

    def "openTransaction stores secret and returns composed transaction id"() {
        given:
        Group group = new Group('jade-tipi_org','some-group')

        when:
        def token = transactionService.openTransaction(group).block()

        then:
        token != null
        token.transactionId().endsWith("~${group.organization()}~${group.group()}")
        token.secret() != null
        token.secret().length() == 43
        token.group() == group

        and: "document is persisted with the secret"
        def stored = mongoTemplate.findById(token.transactionId(), Map, COLLECTION_NAME).block()
        stored != null
        stored.secret == token.secret()
        stored.organization == group.organization()
        stored.group == group.group()
    }

    def "commitTransaction validates secret and stores commit metadata"() {
        given:
        Group group = new Group('jade-tipi_org','some-group')
        TransactionToken token = transactionService.openTransaction(group).block()

        when:
        def commit = transactionService.commitTransaction(token).block()

        then:
        commit != null
        commit.transactionId() == token.transactionId()
        commit.commitId().endsWith("~${group.organization()}~${group.group()}")

        and:
        def stored = mongoTemplate.findById(token.transactionId(), Map, COLLECTION_NAME).block()
        stored.commit == commit.commitId()
        stored.committed != null
    }
}
