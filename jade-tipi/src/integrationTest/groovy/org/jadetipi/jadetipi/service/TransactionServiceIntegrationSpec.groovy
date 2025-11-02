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

    def "createTransaction stores secret and returns composed transaction id"() {
        given:
        String organization = "acme"
        String group = "research"

        when:
        def token = transactionService.createTransaction(organization, group).block()

        then:
        token != null
        token.transactionId.endsWith("~${organization}~${group}")
        token.secret != null
        token.secret.length() == 43

        and: "document is persisted with the secret"
        def stored = mongoTemplate.findById(token.transactionId, Map, COLLECTION_NAME).block()
        stored != null
        stored.secret == token.secret
        stored.organization == organization
        stored.group == group
    }
}
