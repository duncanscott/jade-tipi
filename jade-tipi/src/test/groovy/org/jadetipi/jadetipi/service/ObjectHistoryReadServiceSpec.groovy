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

import java.time.Instant

/**
 * TASK-065 coverage for the history read: resource-read empty on a
 * missing root, criteria and chronological (ascending msg_uuid) ordering
 * with and without the property filter, defensive paging with the
 * filtered total, tolerant property-name resolution, and stale-row
 * tolerance on the value.
 */
class ObjectHistoryReadServiceSpec extends Specification {

    static final String ENT_ID = 'jade-tipi-org~dev~018fd849-9b03-7333-8a03-a3a3a3a3a3a3~ent~probe_1'
    static final String PPY_READING = 'jade-tipi-org~dev~018fd849-9b04-7444-8a04-a4a4a4a4a4a4~ppy~reading'
    static final String PPY_ORPHAN = 'jade-tipi-org~dev~018fd849-9b05-7555-8a05-a5a5a5a5a5a5~ppy~orphan'
    static final String MSG_1 = '018fd849-9b06-7666-8a06-a6a6a6a6a6a6'
    static final String MSG_2 = '018fd849-9b07-7777-8a07-a7a7a7a7a7a7'

    ReactiveMongoTemplate mongoTemplate
    ObjectHistoryReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new ObjectHistoryReadService(mongoTemplate)
    }

    private static Map hstRow(String msgUuid, String propertyId, Map value) {
        return [
                _id       : msgUuid,
                object_id : ENT_ID,
                property_id: propertyId,
                msg_uuid  : msgUuid,
                value     : value,
                txn_id    : 'txn-1',
                commit_id : '018fd849-9b08-7888-8a08-a8a8a8a8a8a8',
                applied_at: Instant.parse('2026-07-06T00:00:00Z')
        ] as Map
    }

    private void stubRootPresent() {
        mongoTemplate.findById(ENT_ID, Map.class, 'ent') >> Mono.just(
                [_id: ENT_ID, collection: 'ent'] as Map)
    }

    private void stubDefinitions(List<Map> rows) {
        mongoTemplate.find(_ as Query, Map.class, 'ppy') >> Flux.fromIterable(rows)
    }

    def 'a missing subject root resolves empty (route answers 404)'() {
        given:
        mongoTemplate.findById(ENT_ID, Map.class, 'ent') >> Mono.empty()

        when:
        def result = service.findHistory('ent', ENT_ID, null, 0, 25).block()

        then:
        result == null
        0 * mongoTemplate.find(_, _, 'hst')
    }

    def 'entries come back in ascending msg_uuid order with resolved names and full provenance'() {
        given:
        stubRootPresent()
        Query capturedItems = null
        mongoTemplate.find(_ as Query, Map.class, 'hst') >> { Query q, Class _t, String _c ->
            capturedItems = q
            return Flux.just(hstRow(MSG_1, PPY_READING, [number: 1]),
                    hstRow(MSG_2, PPY_READING, [number: 2]))
        }
        mongoTemplate.count(_ as Query, 'hst') >> Mono.just(2L)
        stubDefinitions([[_id: PPY_READING, properties: [kind: 'definition', name: 'reading']] as Map])

        when:
        ObjectHistoryRecord record = service.findHistory('ent', ENT_ID, null, 0, 25).block()

        then:
        record.objectId == ENT_ID
        record.collection == 'ent'
        record.propertyId == null
        record.total == 2L
        record.items*.msgUuid == [MSG_1, MSG_2]
        record.items*.propertyName == ['reading', 'reading']
        record.items[1].value == [number: 2]
        record.items[1].txnId == 'txn-1'
        record.items[1].appliedAt == Instant.parse('2026-07-06T00:00:00Z')

        and: 'the query is object-scoped, chronological, and paged'
        capturedItems.getQueryObject().get('object_id') == ENT_ID
        !capturedItems.getQueryObject().containsKey('property_id')
        capturedItems.getSortObject().get('msg_uuid') == 1
        capturedItems.getSkip() == 0L
        capturedItems.getLimit() == 25
    }

    def 'the property filter narrows the criteria and the total'() {
        given:
        stubRootPresent()
        Query capturedItems = null
        Query capturedCount = null
        mongoTemplate.find(_ as Query, Map.class, 'hst') >> { Query q, Class _t, String _c ->
            capturedItems = q
            return Flux.just(hstRow(MSG_1, PPY_READING, [number: 1]))
        }
        mongoTemplate.count(_ as Query, 'hst') >> { Query q, String _c ->
            capturedCount = q
            return Mono.just(1L)
        }
        stubDefinitions([[_id: PPY_READING, properties: [kind: 'definition', name: 'reading']] as Map])

        when:
        ObjectHistoryRecord record = service.findHistory('ent', ENT_ID, PPY_READING, 0, 25).block()

        then:
        record.propertyId == PPY_READING
        record.total == 1L
        capturedItems.getQueryObject().get('property_id') == PPY_READING
        capturedCount.getQueryObject().get('property_id') == PPY_READING
    }

    def 'paging is defensive and echoed: page floors at 0 and size clamps to 1..100'() {
        given:
        stubRootPresent()
        Query capturedItems = null
        mongoTemplate.find(_ as Query, Map.class, 'hst') >> { Query q, Class _t, String _c ->
            capturedItems = q
            return Flux.empty()
        }
        mongoTemplate.count(_ as Query, 'hst') >> Mono.just(0L)

        when:
        ObjectHistoryRecord record = service.findHistory('ent', ENT_ID, null, page, size).block()

        then:
        record.page == expectedPage
        record.size == expectedSize
        capturedItems.getSkip() == (long) expectedPage * expectedSize
        capturedItems.getLimit() == expectedSize
        record.items.isEmpty()
        record.total == 0L

        where:
        page | size || expectedPage | expectedSize
        -5   | 0    || 0            | 1
        2    | 500  || 2            | 100
        1    | 25   || 1            | 25
    }

    def 'a missing definition yields a null name and a non-map value surfaces as an empty map'() {
        given:
        stubRootPresent()
        mongoTemplate.find(_ as Query, Map.class, 'hst') >> Flux.just(
                hstRow(MSG_1, PPY_ORPHAN, null))
        mongoTemplate.count(_ as Query, 'hst') >> Mono.just(1L)
        stubDefinitions([])

        when:
        ObjectHistoryRecord record = service.findHistory('ent', ENT_ID, null, 0, 25).block()

        then:
        record.items[0].propertyName == null
        record.items[0].value == [:]
    }

    def 'an unsupported collection is rejected by the service contract'() {
        when:
        service.findHistory('typ', ENT_ID, null, 0, 25)

        then:
        thrown(IllegalArgumentException)
    }
}
