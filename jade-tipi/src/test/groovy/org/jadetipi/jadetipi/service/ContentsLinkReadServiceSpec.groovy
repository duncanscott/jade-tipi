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

import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.time.Instant

class ContentsLinkReadServiceSpec extends Specification {

    static final String CONTENTS_TYPE_ID =
            'jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents'
    static final String CONTENTS_TYPE_ID_ALT =
            'jade-tipi-org~dev~018fd849-2b00-7000-8000-000000000000~typ~contents'

    static final String CONTAINER_ID =
            'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1'
    static final String OBJECT_ID =
            'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1'

    static final String LNK_ID_A =
            'jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-aaaaaaaaaaaa~lnk~plate_b1_sample_x1'
    static final String LNK_ID_B =
            'jade-tipi-org~dev~018fd849-2a4b-7bbb-8b0b-bbbbbbbbbbbb~lnk~plate_b1_sample_y1'
    static final String LNK_ID_C =
            'jade-tipi-org~dev~018fd849-2a4c-7ccc-8b0c-cccccccccccc~lnk~plate_b1_sample_z1'

    ReactiveMongoTemplate mongoTemplate
    ContentsLinkReadService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new ContentsLinkReadService(mongoTemplate)
    }

    /**
     * Build a root-shaped {@code typ} document as written by
     * {@link CommittedTransactionMaterializer} for a {@code typ + create}
     * with {@code data.kind == "link_type"} and {@code data.name == "contents"}.
     * The materializer copies every payload field other than {@code id} and
     * {@code type_id} into root {@code properties}, so {@code kind} and
     * {@code name} live under {@code properties} on a root-shaped document.
     */
    private static Map typRow(String id, Map propertyOverrides = [:]) {
        Map properties = [
                kind: 'link_type',
                name: 'contents'
        ]
        properties.putAll(propertyOverrides)
        return [
                _id       : id,
                id        : id,
                collection: 'typ',
                type_id   : null,
                properties: properties,
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : id,
                        provenance    : [
                                txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id      : 'COMMIT-TYP',
                                msg_uuid       : '018fd849-2a49-7999-8a09-cccccccccccc',
                                collection     : 'typ',
                                action         : 'create',
                                committed_at   : Instant.parse('2026-01-01T00:00:01Z'),
                                materialized_at: Instant.parse('2026-01-01T00:00:02Z')
                        ]
                ]
        ]
    }

    /**
     * Build a root-shaped {@code lnk} document. The materializer keeps
     * top-level {@code type_id}, {@code left}, {@code right}, and the
     * verbatim {@code properties} sub-document at the root, and writes
     * provenance to {@code _head.provenance}; new roots no longer carry
     * top-level {@code _jt_provenance}.
     */
    private static Map lnkRow(String id, String left, String right,
                              Map overrides = [:]) {
        Map base = [
                _id       : id,
                id        : id,
                collection: 'lnk',
                type_id   : CONTENTS_TYPE_ID,
                left      : left,
                right     : right,
                properties: [
                        position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]
                ],
                links     : [:],
                _head     : [
                        schema_version: 1,
                        document_kind : 'root',
                        root_id       : id,
                        provenance    : [
                                txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                commit_id      : 'COMMIT-001',
                                msg_uuid       : '018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb',
                                collection     : 'lnk',
                                action         : 'create',
                                committed_at   : Instant.parse('2026-01-01T00:00:05Z'),
                                materialized_at: Instant.parse('2026-01-01T00:00:06Z')
                        ]
                ]
        ]
        base.putAll(overrides)
        return base
    }

    /**
     * Build a legacy copied-data-shape {@code lnk} document materialized
     * before TASK-014 — top-level {@code _jt_provenance} is present and
     * there is no {@code _head} sub-document. Used to verify the explicit
     * fallback path in the service.
     */
    private static Map legacyLnkRow(String id, String left, String right) {
        return [
                _id           : id,
                id            : id,
                type_id       : CONTENTS_TYPE_ID,
                left          : left,
                right         : right,
                properties    : [
                        position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]
                ],
                _jt_provenance: [
                        txn_id         : 'legacy-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id      : 'COMMIT-LEGACY',
                        msg_uuid       : '018fd849-2a4a-7aaa-8b0a-cccccccccccc',
                        committed_at   : Instant.parse('2025-12-01T00:00:05Z'),
                        materialized_at: Instant.parse('2025-12-01T00:00:06Z')
                ]
        ]
    }

    def 'findContents returns one record per matching lnk with verbatim fields and provenance from _head.provenance'() {
        given:
        Map typ = typRow(CONTENTS_TYPE_ID)
        Map lnk = lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID)
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typ)
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnk)

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results.size() == 1
        ContentsLinkRecord r = results[0]
        r.linkId == LNK_ID_A
        r.typeId == CONTENTS_TYPE_ID
        r.left == CONTAINER_ID
        r.right == OBJECT_ID
        Map position = r.properties.position as Map
        position.kind == 'plate_well'
        position.label == 'A1'
        position.row == 'A'
        position.column == 1

        and: 'provenance is read from the root-shaped _head.provenance sub-document'
        r.provenance != null
        r.provenance.txn_id == 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
        r.provenance.commit_id == 'COMMIT-001'
        r.provenance.msg_uuid == '018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb'
        r.provenance.collection == 'lnk'
        r.provenance.action == 'create'
        r.provenance.committed_at == Instant.parse('2026-01-01T00:00:05Z')
        r.provenance.materialized_at == Instant.parse('2026-01-01T00:00:06Z')
    }

    def 'findContents queries lnk with type_id $in resolved IDs, left == containerId, sorted by _id ASC'() {
        given:
        Query capturedTypQuery = null
        Query capturedLnkQuery = null
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> { Query q, Class _t, String _c ->
            capturedTypQuery = q
            return Flux.just(typRow(CONTENTS_TYPE_ID), typRow(CONTENTS_TYPE_ID_ALT))
        }
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> { Query q, Class _t, String _c ->
            capturedLnkQuery = q
            return Flux.just(
                    lnkRow(LNK_ID_C, CONTAINER_ID, OBJECT_ID, [type_id: CONTENTS_TYPE_ID_ALT]),
                    lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID),
                    lnkRow(LNK_ID_B, CONTAINER_ID, OBJECT_ID)
            )
        }

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then: 'the typ query filters on properties.kind=link_type AND properties.name=contents and sorts by _id ASC'
        capturedTypQuery != null
        Map typQueryDoc = capturedTypQuery.queryObject
        typQueryDoc.get('properties.kind') == 'link_type'
        typQueryDoc.get('properties.name') == 'contents'
        capturedTypQuery.sortObject == new Document('_id', 1)

        and: 'the lnk query carries an $in filter on type_id with both resolved IDs and is left-keyed'
        capturedLnkQuery != null
        Map lnkQueryDoc = capturedLnkQuery.queryObject
        Map typeIdClause = lnkQueryDoc.get('type_id') as Map
        (typeIdClause.get('$in') as List).toSet() == [CONTENTS_TYPE_ID, CONTENTS_TYPE_ID_ALT].toSet()
        lnkQueryDoc.get('left') == CONTAINER_ID
        lnkQueryDoc.containsKey('right') == false

        and: 'the lnk query carries a deterministic _id ASC sort proven independently of mock order'
        capturedLnkQuery.sortObject == new Document('_id', 1)

        and: 'the service preserves the Mongo-returned order (test of sort is via captured sortObject)'
        results*.linkId == [LNK_ID_C, LNK_ID_A, LNK_ID_B]

        and: 'links carrying either resolved type_id come back, including the alternate declaration'
        results*.typeId.toSet() == [CONTENTS_TYPE_ID, CONTENTS_TYPE_ID_ALT].toSet()
    }

    def 'findContents emits Flux.empty() when no contents link type has been declared, and never queries lnk'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.empty()

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results == []
        0 * mongoTemplate.find(_, Map.class, 'lnk')
    }

    def 'findContents emits Flux.empty() when contents type exists but no lnk references the container'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.empty()

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results == []

        and: 'the reader does not consult loc or ent to decide'
        0 * mongoTemplate.find(_, _, 'loc')
        0 * mongoTemplate.find(_, _, 'ent')
        0 * mongoTemplate.findById(_, _, _)
    }

    def 'findContents returns a lnk verbatim even when its right endpoint string would not resolve in loc or ent'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        Map lnk = lnkRow(LNK_ID_A, CONTAINER_ID, 'jade-tipi-org~dev~018fd849-9999-7999-8999-999999999999~ent~not_yet_materialized')
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnk)

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results.size() == 1
        results[0].right == 'jade-tipi-org~dev~018fd849-9999-7999-8999-999999999999~ent~not_yet_materialized'

        and: 'the reader does not attempt endpoint resolution against loc or ent'
        0 * mongoTemplate.find(_, _, 'loc')
        0 * mongoTemplate.find(_, _, 'ent')
        0 * mongoTemplate.findById(_, _, 'loc')
        0 * mongoTemplate.findById(_, _, 'ent')
    }

    def 'findLocations returns one record per matching lnk with verbatim fields and provenance from _head.provenance'() {
        given:
        Map typ = typRow(CONTENTS_TYPE_ID)
        Map lnk = lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID)
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typ)
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnk)

        when:
        List<ContentsLinkRecord> results = service.findLocations(OBJECT_ID).collectList().block()

        then:
        results.size() == 1
        ContentsLinkRecord r = results[0]
        r.linkId == LNK_ID_A
        r.typeId == CONTENTS_TYPE_ID
        r.left == CONTAINER_ID
        r.right == OBJECT_ID
        (r.properties.position as Map).label == 'A1'
        r.provenance.commit_id == 'COMMIT-001'
        r.provenance.collection == 'lnk'
        r.provenance.action == 'create'
    }

    def 'findLocations queries lnk with type_id $in resolved IDs, right == objectId, sorted by _id ASC'() {
        given:
        Query capturedLnkQuery = null
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> { Query q, Class _t, String _c ->
            capturedLnkQuery = q
            return Flux.just(
                    lnkRow(LNK_ID_B, CONTAINER_ID, OBJECT_ID),
                    lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID)
            )
        }

        when:
        List<ContentsLinkRecord> results = service.findLocations(OBJECT_ID).collectList().block()

        then:
        capturedLnkQuery != null
        Map lnkQueryDoc = capturedLnkQuery.queryObject
        Map typeIdClause = lnkQueryDoc.get('type_id') as Map
        typeIdClause.get('$in') == [CONTENTS_TYPE_ID]
        lnkQueryDoc.get('right') == OBJECT_ID
        lnkQueryDoc.containsKey('left') == false
        capturedLnkQuery.sortObject == new Document('_id', 1)

        and: 'order matches the Mongo-returned order'
        results*.linkId == [LNK_ID_B, LNK_ID_A]
    }

    def 'findLocations emits Flux.empty() when no contents link type has been declared, and never queries lnk'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.empty()

        when:
        List<ContentsLinkRecord> results = service.findLocations(OBJECT_ID).collectList().block()

        then:
        results == []
        0 * mongoTemplate.find(_, Map.class, 'lnk')
    }

    def 'findLocations returns a lnk whose left points at a loc and one whose left points at a different collection'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        String leftLocId = CONTAINER_ID
        String leftOtherId = 'jade-tipi-org~dev~018fd849-7777-7777-8777-777777777777~loc~freezer_a'
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(
                lnkRow(LNK_ID_A, leftLocId, OBJECT_ID),
                lnkRow(LNK_ID_B, leftOtherId, OBJECT_ID)
        )

        when:
        List<ContentsLinkRecord> results = service.findLocations(OBJECT_ID).collectList().block()

        then:
        results.size() == 2
        results*.left == [leftLocId, leftOtherId]
    }

    def 'typ records that are not link_type or are not named contents are not used as type filters'() {
        given: 'the service builds the criteria; the unit spec captures and asserts them'
        Query capturedTypQuery = null
        Query capturedLnkQuery = null
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> { Query q, Class _t, String _c ->
            capturedTypQuery = q
            // Real Mongo would apply the criteria; the unit spec asserts the criteria the
            // service builds, then returns only the row that would actually match.
            return Flux.just(typRow(CONTENTS_TYPE_ID))
        }
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> { Query q, Class _t, String _c ->
            capturedLnkQuery = q
            return Flux.just(lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID))
        }

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then: 'the typ criteria require BOTH properties.kind=link_type AND properties.name=contents'
        capturedTypQuery != null
        Map typDoc = capturedTypQuery.queryObject
        typDoc.get('properties.kind') == 'link_type'
        typDoc.get('properties.name') == 'contents'

        and: 'the lnk type_id $in only contains the resolved canonical contents type IDs'
        capturedLnkQuery != null
        Map lnkDoc = capturedLnkQuery.queryObject
        (lnkDoc.get('type_id') as Map).get('$in') == [CONTENTS_TYPE_ID]
        results*.typeId == [CONTENTS_TYPE_ID]
    }

    def 'a materialized lnk with no _head sub-document falls back to legacy top-level _jt_provenance verbatim'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        Map legacy = legacyLnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID)
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(legacy)

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results.size() == 1
        ContentsLinkRecord r = results[0]
        r.linkId == LNK_ID_A
        r.left == CONTAINER_ID
        r.right == OBJECT_ID

        and: 'provenance is sourced from the legacy _jt_provenance sub-document verbatim'
        r.provenance != null
        r.provenance.txn_id == 'legacy-bbbb-7ccc-8ddd-eeeeeeeeeeee'
        r.provenance.commit_id == 'COMMIT-LEGACY'
        r.provenance.msg_uuid == '018fd849-2a4a-7aaa-8b0a-cccccccccccc'
        r.provenance.committed_at == Instant.parse('2025-12-01T00:00:05Z')
        r.provenance.materialized_at == Instant.parse('2025-12-01T00:00:06Z')
    }

    def 'a materialized lnk with neither _head.provenance nor _jt_provenance returns null provenance'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        Map lnk = lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID, [_head: null])
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnk)

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results.size() == 1
        results[0].linkId == LNK_ID_A
        results[0].provenance == null
    }

    def 'a root-shaped lnk whose _head has no provenance sub-map and no legacy _jt_provenance returns null provenance'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        Map lnk = lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID, [
                _head: [schema_version: 1, document_kind: 'root', root_id: LNK_ID_A]
        ])
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnk)

        when:
        List<ContentsLinkRecord> results = service.findContents(CONTAINER_ID).collectList().block()

        then:
        results.size() == 1
        results[0].linkId == LNK_ID_A
        results[0].provenance == null
    }

    def 'findContents rejects blank containerId with IllegalArgumentException and never touches Mongo'() {
        when:
        service.findContents(input).collectList().block()

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate.find(_, _, _)
        0 * mongoTemplate.findById(_, _, _)

        where:
        input << [null, '', '   ']
    }

    def 'findLocations rejects blank objectId with IllegalArgumentException and never touches Mongo'() {
        when:
        service.findLocations(input).collectList().block()

        then:
        thrown(IllegalArgumentException)
        0 * mongoTemplate.find(_, _, _)
        0 * mongoTemplate.findById(_, _, _)

        where:
        input << [null, '', '   ']
    }

    def 'service does not write to Mongo on either query path'() {
        given:
        mongoTemplate.find(_ as Query, Map.class, 'typ') >> Flux.just(typRow(CONTENTS_TYPE_ID))
        mongoTemplate.find(_ as Query, Map.class, 'lnk') >> Flux.just(lnkRow(LNK_ID_A, CONTAINER_ID, OBJECT_ID))

        when:
        service.findContents(CONTAINER_ID).collectList().block()
        service.findLocations(OBJECT_ID).collectList().block()

        then:
        0 * mongoTemplate.insert(_, _)
        0 * mongoTemplate.insert(_)
        0 * mongoTemplate.save(_, _)
        0 * mongoTemplate.save(_)
        0 * mongoTemplate.updateFirst(_, _, _)
        0 * mongoTemplate.updateMulti(_, _, _)
        0 * mongoTemplate.remove(_, _)
        0 * mongoTemplate.remove(_)
    }
}
