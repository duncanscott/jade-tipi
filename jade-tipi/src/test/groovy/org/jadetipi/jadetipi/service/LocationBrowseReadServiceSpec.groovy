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
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * TASK-054 coverage for the paged location browse: deterministic query
 * shape, defensive paging clamps, tolerant summary mapping, and the page
 * envelope.
 */
class LocationBrowseReadServiceSpec extends Specification {

    static final String LOC_A = 'jade-tipi-org~dev~018fd849-6a01-7111-8a01-616161616161~loc~freezer_a'
    static final String LOC_B = 'jade-tipi-org~dev~018fd849-6a02-7222-8a02-626262626262~loc~plate_0001'
    static final String TYP_ID = 'jade-tipi-org~dev~018fd849-6a03-7333-8a03-636363636363~typ~container'

    ReactiveMongoTemplate mongoTemplate
    LocationBrowseReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new LocationBrowseReadService(mongoTemplate)
    }

    def 'browse sorts by _id ascending and applies the requested skip and limit'() {
        given:
        Query capturedQuery = null
        mongoTemplate.find(_ as Query, Map.class, 'loc') >> { Query q, Class _t, String _c ->
            capturedQuery = q
            return Flux.empty()
        }
        mongoTemplate.count(_ as Query, 'loc') >> Mono.just(0L)

        when:
        LocationBrowseRecord result = service.browse(2, 10).block()

        then:
        result.items.isEmpty()
        result.page == 2
        result.size == 10
        result.total == 0L
        capturedQuery.getSortObject().get('_id') == 1
        capturedQuery.getSkip() == 20L
        capturedQuery.getLimit() == 10
    }

    def 'paging parameters are clamped defensively'() {
        given:
        Query capturedQuery = null
        mongoTemplate.find(_ as Query, Map.class, 'loc') >> { Query q, Class _t, String _c ->
            capturedQuery = q
            return Flux.empty()
        }
        mongoTemplate.count(_ as Query, 'loc') >> Mono.just(0L)

        when:
        LocationBrowseRecord result = service.browse(requestedPage, requestedSize).block()

        then:
        result.page == expectedPage
        result.size == expectedSize
        capturedQuery.getSkip() == (long) expectedPage * expectedSize
        capturedQuery.getLimit() == expectedSize

        where:
        requestedPage | requestedSize || expectedPage | expectedSize
        -3            | 0             || 0            | 1
        0             | 500           || 0            | 100
        1             | 25            || 1            | 25
    }

    def 'summaries map identity, type, and inline name/description, tolerating sparse roots'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'loc') >> Flux.just(
                [
                        _id       : LOC_A,
                        id        : LOC_A,
                        collection: 'loc',
                        type_id   : TYP_ID,
                        properties: [name: 'freezer_a', description: 'minus-80 freezer'],
                        links     : [:]
                ] as Map,
                // sparse/legacy shape: no properties bag, no type
                [_id: LOC_B] as Map
        )
        mongoTemplate.count(_ as Query, 'loc') >> Mono.just(2L)

        when:
        LocationBrowseRecord result = service.browse(0, 25).block()

        then:
        result.total == 2L
        result.items.size() == 2

        and:
        result.items[0].locationId == LOC_A
        result.items[0].typeId == TYP_ID
        result.items[0].name == 'freezer_a'
        result.items[0].description == 'minus-80 freezer'

        and: 'the sparse root surfaces nulls, never a failed read'
        result.items[1].locationId == LOC_B
        result.items[1].typeId == null
        result.items[1].name == null
        result.items[1].description == null
    }
}
