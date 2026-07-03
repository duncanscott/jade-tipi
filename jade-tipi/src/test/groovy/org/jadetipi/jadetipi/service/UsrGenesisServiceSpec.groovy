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

import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class UsrGenesisServiceSpec extends Specification {

    static final String ORG = 'jade-tipi-org'
    static final String GRP = 'dev'
    static final String EXPECTED_ID = 'jade-tipi-org~dev~genesis~usr~jdtp-admin'
    static final String COLLECTION = 'usr'

    ReactiveMongoTemplate mongoTemplate
    UsrGenesisService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new UsrGenesisService(mongoTemplate, ORG, GRP, true)
    }

    def 'bootstrapUsrId composes org, grp, genesis segment, collection, and suffix'() {
        expect:
        service.bootstrapUsrId() == EXPECTED_ID

        and: 'a different instance org/grp changes only the prefix'
        new UsrGenesisService(mongoTemplate, 'lbl_gov', 'jgi_pps', true).bootstrapUsrId() ==
                'lbl_gov~jgi_pps~genesis~usr~jdtp-admin'
    }

    def 'ensure inserts the bootstrap usr root when absent'() {
        given:
        Map<String, Object> inserted = null

        when:
        UsrGenesisResult result = service.ensureBootstrapUsr().block()

        then:
        1 * mongoTemplate.findById(EXPECTED_ID, Map.class, COLLECTION) >> Mono.empty()
        1 * mongoTemplate.insert(_ as Map, COLLECTION) >> { args ->
            inserted = (Map<String, Object>) args[0]
            return Mono.just(args[0])
        }
        result == UsrGenesisResult.CREATED

        and: 'the document follows the accepted root shape'
        inserted._id == EXPECTED_ID
        inserted.id == EXPECTED_ID
        inserted.collection == 'usr'
        inserted.containsKey('type_id')
        inserted.type_id == null
        inserted.links == [:]

        and: 'the properties mark the record as reserved/system/bootstrap'
        inserted.properties.kind == 'system'
        inserted.properties.display_name == 'JDTP Bootstrap Admin'
        inserted.properties.status == 'reserved'
        inserted.properties.identity_provenance.source == 'bootstrap'
        inserted.properties.identity_provenance.first_seen_at instanceof Instant

        and: 'no external identity keys are present'
        !inserted.properties.containsKey('orcid')
        !inserted.properties.containsKey('oidc_issuer')
        !inserted.properties.containsKey('oidc_subject')

        and: 'the reserved head carries the genesis provenance sentinel'
        inserted._head.schema_version == 1
        inserted._head.document_kind == 'root'
        inserted._head.root_id == EXPECTED_ID
        inserted._head.provenance.txn_id == 'genesis~jdtp-admin'
        inserted._head.provenance.commit_id == 'genesis~jdtp-admin'
        inserted._head.provenance.msg_uuid != null
        inserted._head.provenance.collection == 'usr'
        inserted._head.provenance.action == 'create'
        inserted._head.provenance.committed_at instanceof Instant
        inserted._head.provenance.materialized_at instanceof Instant
    }

    def 'ensure is a no-op when the bootstrap usr root already exists'() {
        when:
        UsrGenesisResult result = service.ensureBootstrapUsr().block()

        then:
        1 * mongoTemplate.findById(EXPECTED_ID, Map.class, COLLECTION) >> Mono.just([_id: EXPECTED_ID] as Map)
        0 * mongoTemplate.insert(*_)
        result == UsrGenesisResult.ALREADY_PRESENT
    }

    def 'a concurrent duplicate insert resolves to ALREADY_PRESENT'() {
        when:
        UsrGenesisResult result = service.ensureBootstrapUsr().block()

        then:
        1 * mongoTemplate.findById(EXPECTED_ID, Map.class, COLLECTION) >> Mono.empty()
        1 * mongoTemplate.insert(_ as Map, COLLECTION) >>
                Mono.error(new DuplicateKeyException('duplicate _id'))
        result == UsrGenesisResult.ALREADY_PRESENT
    }

    def 'a non-duplicate insert failure propagates from the ensure'() {
        when:
        service.ensureBootstrapUsr().block()

        then:
        1 * mongoTemplate.findById(EXPECTED_ID, Map.class, COLLECTION) >> Mono.empty()
        1 * mongoTemplate.insert(_ as Map, COLLECTION) >>
                Mono.error(new IllegalStateException('mongo unavailable'))
        thrown(IllegalStateException)
    }

    def 'run continues startup when the ensure fails'() {
        when:
        service.run()

        then:
        1 * mongoTemplate.findById(EXPECTED_ID, Map.class, COLLECTION) >>
                Mono.error(new IllegalStateException('mongo unavailable'))
        notThrown(Exception)
    }

    def 'run skips the ensure when genesis is disabled'() {
        given:
        UsrGenesisService disabled = new UsrGenesisService(mongoTemplate, ORG, GRP, false)

        when:
        disabled.run()

        then:
        0 * mongoTemplate._
    }
}
