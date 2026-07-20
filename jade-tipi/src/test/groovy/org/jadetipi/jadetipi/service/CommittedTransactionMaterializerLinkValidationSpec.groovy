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
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

/**
 * UT-9/TASK-058 coverage for the warn-only link validation layer on
 * {@code lnk + create}: type resolution and kind, endpoint conformance and
 * resolution, {@code allowed_*_collections}, and
 * {@code assignable_properties} — one warning count per issue, and a
 * warned link always still materializes.
 */
class CommittedTransactionMaterializerLinkValidationSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'

    static final String TYP_CONTENTS = '018fd849-8a01-7111-8a01-818181818181~jade-tipi-org~dev~typ~contents'
    static final String TYP_BARE = '018fd849-8a02-7222-8a02-828282828282~jade-tipi-org~dev~typ~container'
    static final String LOC_PLATE = '018fd849-8a03-7333-8a03-838383838383~jade-tipi-org~dev~loc~plate_b1'
    static final String ENT_SAMPLE = '018fd849-8a04-7444-8a04-848484848484~jade-tipi-org~dev~ent~sample_x1'
    static final String GRP_TEAM = '018fd849-8a05-7555-8a05-858585858585~jade-tipi-org~dev~grp~team'
    static final String LNK_ID = '018fd849-8a06-7666-8a06-868686868686~jade-tipi-org~dev~lnk~plate_sample'
    static final String MSG_UUID = '018fd849-8a06-7666-8a06-868686868686'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        // apply_state stamps (TASK-056) write to the txn WAL rows
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'txn') >> Mono.empty()
        mongoTemplate.insert(_ as Map, 'lnk') >> { Map doc, String _c -> Mono.just(doc) }
    }

    private static CommittedTransactionSnapshot snapshot(List<CommittedTransactionMessage> messages) {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: Instant.parse('2026-01-01T00:00:00Z'),
                committedAt: Instant.parse('2026-01-01T00:00:05Z'),
                openData: [:],
                commitData: [:],
                messages: messages
        )
    }

    private static CommittedTransactionMessage linkMessage(Map dataOverrides = [:]) {
        Map<String, Object> data = [
                id        : LNK_ID,
                type_id   : TYP_CONTENTS,
                left      : LOC_PLATE,
                right     : ENT_SAMPLE,
                properties: [position: [label: 'A1']]
        ] as Map<String, Object>
        data.putAll(dataOverrides)
        dataOverrides.each { k, v -> if (v == null) data.remove(k) }
        return new CommittedTransactionMessage(
                msgUuid: MSG_UUID,
                collection: 'lnk',
                action: 'create',
                data: data,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                kafka: null
        )
    }

    private void stubLinkType(Map propertyOverrides = [:]) {
        Map properties = [
                kind                     : 'link_type',
                name                     : 'contents',
                allowed_left_collections : ['loc'],
                allowed_right_collections: ['loc', 'ent'],
                assignable_properties    : ['position']
        ] as Map
        properties.putAll(propertyOverrides)
        mongoTemplate.findById(TYP_CONTENTS, Map.class, 'typ') >> Mono.just([
                _id: TYP_CONTENTS, id: TYP_CONTENTS, collection: 'typ', type_id: null,
                properties: properties, links: [:]
        ] as Map)
    }

    private void stubEndpoint(String id, String collection) {
        mongoTemplate.findById(id, Map.class, collection) >> Mono.just([
                _id: id, id: id, collection: collection, type_id: null,
                properties: [:], links: [:]
        ] as Map)
    }

    def 'a fully valid link warns zero times and materializes'() {
        given:
        stubLinkType()
        stubEndpoint(LOC_PLATE, 'loc')
        stubEndpoint(ENT_SAMPLE, 'ent')

        when:
        MaterializeResult result = materializer.materialize(snapshot([linkMessage()])).block()

        then:
        result.materialized == 1
        result.linkValidationWarnings == 0
    }

    def 'each defect class warns once and the link still materializes'() {
        given:
        setupForCase(defect)

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then: 'warn-only: the defective link still applies'
        result.materialized == 1
        result.linkValidationWarnings == expectedWarnings

        where:
        defect                | message                                                              || expectedWarnings
        'unresolved type'     | linkMessage()                                                        || 1
        'non-link-type type'  | linkMessage(type_id: TYP_BARE)                                       || 1
        'blank type_id'       | linkMessage(type_id: null)                                           || 1
        'unresolved left'     | linkMessage()                                                        || 1
        'forbidden right'     | linkMessage(right: GRP_TEAM)                                         || 1
        'nonconforming left'  | linkMessage(left: 'not-a-conforming-id')                             || 1
        'undeclared property' | linkMessage(properties: [position: [label: 'A1'], color: 'red'])     || 1
    }

    private void setupForCase(String defect) {
        switch (defect) {
            case 'unresolved type':
                // no type stub: type_id resolves nothing; endpoints fine
                stubEndpoint(LOC_PLATE, 'loc')
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
            case 'non-link-type type':
                mongoTemplate.findById(TYP_BARE, Map.class, 'typ') >> Mono.just([
                        _id: TYP_BARE, id: TYP_BARE, collection: 'typ', type_id: null,
                        properties: [name: 'container'], links: [:]
                ] as Map)
                stubEndpoint(LOC_PLATE, 'loc')
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
            case 'blank type_id':
                // endpoints fine; allowed-collections checks are skipped with no type
                stubEndpoint(LOC_PLATE, 'loc')
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
            case 'unresolved left':
                stubLinkType()
                // left endpoint resolves nothing
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
            case 'forbidden right':
                stubLinkType()
                stubEndpoint(LOC_PLATE, 'loc')
                stubEndpoint(GRP_TEAM, 'grp')
                break
            case 'nonconforming left':
                stubLinkType()
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
            case 'undeclared property':
                stubLinkType()
                stubEndpoint(LOC_PLATE, 'loc')
                stubEndpoint(ENT_SAMPLE, 'ent')
                break
        }
    }

    def 'defects accumulate one warning per issue'() {
        given: 'no type, unresolved left, nonconforming right'
        CommittedTransactionMessage message = linkMessage(right: 'junk-id')

        when:
        MaterializeResult result = materializer.materialize(snapshot([message])).block()

        then: 'type unresolved + left unresolved + right nonconforming'
        result.materialized == 1
        result.linkValidationWarnings == 3
    }

    def 'declare-before-use within one transaction resolves without warnings'() {
        given: 'the type and both endpoints are created earlier in the same snapshot'
        Map<String, Object> typData = [
                kind                     : 'link_type',
                id                       : TYP_CONTENTS,
                name                     : 'contents',
                allowed_left_collections : ['loc'],
                allowed_right_collections: ['loc', 'ent'],
                assignable_properties    : ['position']
        ] as Map<String, Object>
        CommittedTransactionMessage typMsg = new CommittedTransactionMessage(
                msgUuid: '018fd849-8a07-7777-8a07-878787878787', collection: 'typ', action: 'create',
                data: typData, receivedAt: Instant.parse('2026-01-01T00:00:01Z'), kafka: null)
        CommittedTransactionMessage locMsg = new CommittedTransactionMessage(
                msgUuid: '018fd849-8a08-7888-8a08-888888888888', collection: 'loc', action: 'create',
                data: [id: LOC_PLATE, name: 'plate_b1'] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'), kafka: null)
        CommittedTransactionMessage entMsg = new CommittedTransactionMessage(
                msgUuid: '018fd849-8a09-7999-8a09-898989898989', collection: 'ent', action: 'create',
                data: [id: ENT_SAMPLE, name: 'sample_x1'] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:01Z'), kafka: null)

        and: 'inserts land in a store the later validation lookups read back'
        Map<String, Map> store = [:]
        mongoTemplate.insert(_ as Map, _ as String) >> { Map doc, String collection ->
            store[doc._id as String] = doc
            return Mono.just(doc)
        }
        mongoTemplate.findById(_ as String, Map.class, _ as String) >> { String id, Class _t, String _c ->
            Map doc = store[id]
            return doc == null ? Mono.empty() : Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(
                snapshot([typMsg, locMsg, entMsg, linkMessage()])).block()

        then:
        result.materialized == 4
        result.linkValidationWarnings == 0
    }
}
