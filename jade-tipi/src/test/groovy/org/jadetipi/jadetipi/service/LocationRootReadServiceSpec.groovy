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

import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

class LocationRootReadServiceSpec extends Specification {

    static final String LOCATION_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String LOCATION_TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~plate_96'

    ReactiveMongoTemplate mongoTemplate
    LocationRootReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new LocationRootReadService(mongoTemplate)
    }

    private static Map locRow(Map overrides = [:]) {
        Map base = [
                _id       : LOCATION_ID,
                id        : LOCATION_ID,
                collection: 'loc',
                type_id   : LOCATION_TYPE_ID,
                properties: [name: 'Plate B1', barcode: 'PB1'],
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : LOCATION_ID,
                        provenance    : [
                                txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id      : 'COMMIT-LOC',
                                msg_uuid       : '11111111-1111-7111-8111-111111111111',
                                collection     : 'loc',
                                action         : 'create',
                                committed_at   : Instant.parse('2026-01-01T00:00:05Z'),
                                materialized_at: Instant.parse('2026-01-01T00:00:06Z')
                        ]
                ]
        ]
        base.putAll(overrides)
        return base
    }

    def 'findLocation returns the root-shaped loc record with provenance from _head'() {
        when:
        LocationRootRecord record = service.findLocation(LOCATION_ID).block()

        then:
        1 * mongoTemplate.findById(LOCATION_ID, Map.class, 'loc') >> Mono.just(locRow())
        0 * _

        and:
        record.locationId == LOCATION_ID
        record.typeId == LOCATION_TYPE_ID
        record.properties == [name: 'Plate B1', barcode: 'PB1']
        record.links == [:]
        record.provenance.txn_id == 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
        record.provenance.commit_id == 'COMMIT-LOC'
        record.provenance.collection == 'loc'
        record.provenance.action == 'create'
    }

    def 'findLocation falls back to legacy provenance for stale loc roots'() {
        given:
        Map row = locRow([
                _head         : null,
                _jt_provenance: [
                        txn_id   : 'legacy-aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id: 'COMMIT-LEGACY-LOC'
                ]
        ])

        when:
        LocationRootRecord record = service.findLocation(LOCATION_ID).block()

        then:
        1 * mongoTemplate.findById(LOCATION_ID, Map.class, 'loc') >> Mono.just(row)
        0 * _

        and:
        record.provenance.txn_id == 'legacy-aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
        record.provenance.commit_id == 'COMMIT-LEGACY-LOC'
    }

    def 'findLocation tolerates non-object properties and links as empty maps'() {
        when:
        LocationRootRecord record = service.findLocation(LOCATION_ID).block()

        then:
        1 * mongoTemplate.findById(LOCATION_ID, Map.class, 'loc') >>
                Mono.just(locRow([properties: 'bad-shape', links: ['not', 'map']]))
        0 * _

        and:
        record.properties == [:]
        record.links == [:]
    }

    def 'missing loc root returns empty'() {
        when:
        LocationRootRecord record = service.findLocation(LOCATION_ID).block()

        then:
        1 * mongoTemplate.findById(LOCATION_ID, Map.class, 'loc') >> Mono.empty()
        0 * _

        and:
        record == null
    }

    def 'blank location id is rejected before Mongo access'() {
        when:
        service.findLocation(input).block()

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate._

        where:
        input << [null, '', '   ']
    }
}
