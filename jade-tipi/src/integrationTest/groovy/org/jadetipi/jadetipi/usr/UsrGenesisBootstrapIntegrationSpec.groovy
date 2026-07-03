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
package org.jadetipi.jadetipi.usr

import org.jadetipi.jadetipi.service.UsrGenesisResult
import org.jadetipi.jadetipi.service.UsrGenesisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

import java.time.Duration

/**
 * Integration coverage for the TASK-039 genesis bootstrap {@code usr} ensure.
 * Requires MongoDB from the project Docker stack
 * ({@code docker compose -f docker/docker-compose.yml up -d}); no Kafka or
 * Keycloak interaction is exercised.
 */
@SpringBootTest
@ActiveProfiles('test')
class UsrGenesisBootstrapIntegrationSpec extends Specification {

    static final Duration TIMEOUT = Duration.ofSeconds(10)
    static final String COLLECTION = 'usr'

    @Autowired
    UsrGenesisService service

    @Autowired
    ReactiveMongoTemplate mongoTemplate

    def 'startup ensure created the bootstrap usr root and re-ensure is idempotent'() {
        given:
        String id = service.bootstrapUsrId()

        expect: 'the CommandLineRunner created the root during context startup'
        Map root = mongoTemplate.findById(id, Map.class, COLLECTION).block(TIMEOUT)
        root != null
        root.id == id
        root.collection == 'usr'
        root.properties.kind == 'system'
        root.properties.display_name == 'JDTP Bootstrap Admin'
        root.properties.status == 'reserved'
        root.properties.identity_provenance.source == 'bootstrap'
        root._head.document_kind == 'root'
        root._head.provenance.txn_id == 'genesis~jdtp-admin'
        root._head.provenance.commit_id == 'genesis~jdtp-admin'

        and: 'no external identity keys are present on the bootstrap root'
        !root.properties.containsKey('orcid')
        !root.properties.containsKey('oidc_issuer')
        !root.properties.containsKey('oidc_subject')

        when: 're-running the ensure'
        UsrGenesisResult again = service.ensureBootstrapUsr().block(TIMEOUT)

        then:
        again == UsrGenesisResult.ALREADY_PRESENT

        and: 'exactly one bootstrap root exists'
        mongoTemplate.count(Query.query(Criteria.where('_id').is(id)), COLLECTION)
                .block(TIMEOUT) == 1L
    }

    def 'ensure recreates the bootstrap usr root when it is missing'() {
        given:
        String id = service.bootstrapUsrId()
        mongoTemplate.remove(Query.query(Criteria.where('_id').is(id)), COLLECTION).block(TIMEOUT)

        when:
        UsrGenesisResult result = service.ensureBootstrapUsr().block(TIMEOUT)

        then:
        result == UsrGenesisResult.CREATED
        mongoTemplate.findById(id, Map.class, COLLECTION).block(TIMEOUT) != null
    }
}
