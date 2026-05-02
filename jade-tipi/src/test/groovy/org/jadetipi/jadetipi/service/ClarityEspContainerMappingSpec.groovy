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

/**
 * TASK-019 prototype: drive {@link CommittedTransactionMaterializer} with the
 * documented Clarity tube and ESP freezer/bin/plate examples and assert the
 * seven materialized roots match the shape pinned in
 * {@code docs/architecture/clarity-esp-container-mapping.md}.
 *
 * <p>The single committed transaction wraps four {@code loc + create}, one
 * {@code typ + create} (the transaction-local {@code contents} link type), and
 * two {@code lnk + create} messages. Open/commit are header-level on the
 * snapshot; the materializer only iterates {@code snapshot.messages}.
 */
class ClarityEspContainerMappingSpec extends Specification {

    static final String TXN_UUID = '018fd849-c0c0-7000-8a01-c1a141e5e500'
    static final String COMMIT_ID = '0000000000000001'
    static final Instant OPENED_AT = Instant.parse('2026-05-02T03:49:00Z')
    static final Instant COMMITTED_AT = Instant.parse('2026-05-02T03:50:00Z')

    static final String ID_PREFIX = "jade-tipi-org~dev~${TXN_UUID}"

    static final String LOC_FREEZER_ID = "${ID_PREFIX}~loc~esp_freezer_019a3a62-8fa8"
    static final String LOC_BIN_ID = "${ID_PREFIX}~loc~esp_bin_019a3a60-9628"
    static final String LOC_PLATE_ID = "${ID_PREFIX}~loc~esp_plate_019a420c-728d"
    static final String LOC_TUBE_ID = "${ID_PREFIX}~loc~clarity_tube_27-10000"
    static final String TYP_CONTENTS_ID = "${ID_PREFIX}~typ~contents"
    static final String LNK_FREEZER_BIN_ID =
            "${ID_PREFIX}~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2"
    static final String LNK_BIN_PLATE_ID =
            "${ID_PREFIX}~lnk~contents_bin_pp050_to_plate_27-474501_a1"

    static final String MSG_FREEZER = '018fd849-c0c0-7100-8a01-aaaaaaaaaa02'
    static final String MSG_BIN     = '018fd849-c0c0-7100-8a01-aaaaaaaaaa03'
    static final String MSG_PLATE   = '018fd849-c0c0-7100-8a01-aaaaaaaaaa04'
    static final String MSG_TUBE    = '018fd849-c0c0-7100-8a01-aaaaaaaaaa05'
    static final String MSG_TYP     = '018fd849-c0c0-7100-8a01-aaaaaaaaaa06'
    static final String MSG_LNK_FB  = '018fd849-c0c0-7100-8a01-aaaaaaaaaa07'
    static final String MSG_LNK_BP  = '018fd849-c0c0-7100-8a01-aaaaaaaaaa08'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer
    Map<String, List<Map<String, Object>>> insertsByCollection
    List<String> insertOrder

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        insertsByCollection = [loc: [], typ: [], lnk: []]
        insertOrder = []
        mongoTemplate.insert(_ as Map, _ as String) >> { Map doc, String collection ->
            insertsByCollection.computeIfAbsent(collection, { [] }) << doc
            insertOrder << "${collection}:${doc._id}".toString()
            return Mono.just(doc)
        }
    }

    private static CommittedTransactionSnapshot prototypeSnapshot() {
        return new CommittedTransactionSnapshot(
                txnId: TXN_UUID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: OPENED_AT,
                committedAt: COMMITTED_AT,
                openData: [description: 'TASK-019 Clarity/ESP container materialization prototype'],
                commitData: [comment: 'TASK-019 prototype committed'],
                messages: [
                        freezerLocMessage(),
                        binLocMessage(),
                        plateLocMessage(),
                        tubeLocMessage(),
                        contentsTypMessage(),
                        freezerToBinLnkMessage(),
                        binToPlateLnkMessage()
                ]
        )
    }

    private static CommittedTransactionMessage freezerLocMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_FREEZER,
                collection: 'loc',
                action: 'create',
                data: [
                        id            : LOC_FREEZER_ID,
                        name          : 'Illumina 130-32',
                        kind          : 'Freezer (6-shelf)',
                        barcode       : 'FREEZE012',
                        source_system : 'esp-entity',
                        source_id     : '019a3a62-8fa8-74d8-ad5c-c7f294c9a331',
                        source_type_id: '019a3a49-6dd8-7dcc-af68-130207d9a1de'
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:01Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage binLocMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_BIN,
                collection: 'loc',
                action: 'create',
                data: [
                        id               : LOC_BIN_ID,
                        name             : 'PP050',
                        kind             : 'Bin 9x3',
                        barcode          : 'BIN057',
                        source_system    : 'esp-entity',
                        source_id        : '019a3a60-9628-7c90-bc47-f40518a12127',
                        source_type_id   : '019a3a49-3672-73ec-842d-6c21c5ad9be7',
                        source_numeric_id: 50
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage plateLocMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_PLATE,
                collection: 'loc',
                action: 'create',
                data: [
                        id               : LOC_PLATE_ID,
                        name             : '27-474501',
                        kind             : '96W Plate',
                        barcode          : '27-474501',
                        format           : '96-well',
                        rows             : 8,
                        columns          : 12,
                        source_system    : 'esp-entity',
                        source_id        : '019a420c-728d-7f4c-a817-cd8ba13a1e36',
                        source_type_id   : '019a3ac2-b494-71ac-82cc-fadc028be18f',
                        source_numeric_id: 474501
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:03Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage tubeLocMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_TUBE,
                collection: 'loc',
                action: 'create',
                data: [
                        id           : LOC_TUBE_ID,
                        name         : '27-170230',
                        kind         : 'Tube',
                        source_system: 'clarity',
                        source_id    : '27-10000',
                        source_state : 'Populated'
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:04Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage contentsTypMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_TYP,
                collection: 'typ',
                action: 'create',
                data: [
                        kind                     : 'link_type',
                        id                       : TYP_CONTENTS_ID,
                        name                     : 'contents',
                        description              : 'containment relationship between a container location and its contents',
                        left_role                : 'container',
                        right_role               : 'content',
                        left_to_right_label      : 'contains',
                        right_to_left_label      : 'contained_by',
                        allowed_left_collections : ['loc'],
                        allowed_right_collections: ['loc', 'ent'],
                        assignable_properties    : ['position']
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:05Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage freezerToBinLnkMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_LNK_FB,
                collection: 'lnk',
                action: 'create',
                data: [
                        id        : LNK_FREEZER_BIN_ID,
                        type_id   : TYP_CONTENTS_ID,
                        left      : LOC_FREEZER_ID,
                        right     : LOC_BIN_ID,
                        properties: [
                                position: [kind: 'freezer_slot', label: '2', slot: 2]
                        ]
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:06Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage binToPlateLnkMessage() {
        return new CommittedTransactionMessage(
                msgUuid: MSG_LNK_BP,
                collection: 'lnk',
                action: 'create',
                data: [
                        id        : LNK_BIN_PLATE_ID,
                        type_id   : TYP_CONTENTS_ID,
                        left      : LOC_BIN_ID,
                        right     : LOC_PLATE_ID,
                        properties: [
                                position: [kind: 'bin_slot', label: 'A1', row: 'A', column: 1]
                        ]
                ],
                receivedAt: Instant.parse('2026-05-02T03:49:07Z'),
                kafka: null
        )
    }

    def 'materializes seven prototype roots into loc, typ, and lnk in snapshot order'() {
        when:
        MaterializeResult result = materializer.materialize(prototypeSnapshot()).block()

        then:
        result != null
        result.materialized == 7
        result.skippedUnsupported == 0
        result.skippedInvalid == 0
        result.duplicateMatching == 0
        result.conflictingDuplicate == 0

        and: 'each collection received the expected number of inserts'
        insertsByCollection.loc.size() == 4
        insertsByCollection.typ.size() == 1
        insertsByCollection.lnk.size() == 2

        and: 'inserts happen in snapshot order: four loc, one typ, two lnk'
        insertOrder == [
                "loc:${LOC_FREEZER_ID}",
                "loc:${LOC_BIN_ID}",
                "loc:${LOC_PLATE_ID}",
                "loc:${LOC_TUBE_ID}",
                "typ:${TYP_CONTENTS_ID}",
                "lnk:${LNK_FREEZER_BIN_ID}",
                "lnk:${LNK_BIN_PLATE_ID}"
        ]
    }

    def 'ESP freezer loc has source-traceability properties and no parentage fields'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map freezer = insertsByCollection.loc.find { it._id == LOC_FREEZER_ID }

        then:
        freezer != null
        freezer._id == LOC_FREEZER_ID
        freezer.id == LOC_FREEZER_ID
        freezer.collection == 'loc'
        freezer.type_id == null
        freezer.links == [:]

        and: 'inline payload fields move under properties'
        Map properties = freezer.properties as Map
        properties.name == 'Illumina 130-32'
        properties.kind == 'Freezer (6-shelf)'
        properties.barcode == 'FREEZE012'
        properties.source_system == 'esp-entity'
        properties.source_id == '019a3a62-8fa8-74d8-ad5c-c7f294c9a331'
        properties.source_type_id == '019a3a49-6dd8-7dcc-af68-130207d9a1de'

        and: 'D6: parentage is not duplicated on the loc'
        !properties.containsKey('parent_location_id')
        !properties.containsKey('parent_loc_id')
        !properties.containsKey('parent_id')
        !properties.containsKey('container')

        and: '_head.provenance reflects the source loc create message'
        Map head = freezer._head as Map
        head.schema_version == 1
        head.document_kind == 'root'
        head.root_id == LOC_FREEZER_ID
        Map provenance = head.provenance as Map
        provenance.txn_id == TXN_UUID
        provenance.commit_id == COMMIT_ID
        provenance.msg_uuid == MSG_FREEZER
        provenance.collection == 'loc'
        provenance.action == 'create'
        provenance.committed_at == COMMITTED_AT
        provenance.materialized_at instanceof Instant
    }

    def 'ESP bin loc preserves source numeric id and ESP type uuid'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map bin = insertsByCollection.loc.find { it._id == LOC_BIN_ID }

        then:
        bin != null
        bin.collection == 'loc'
        bin.type_id == null
        Map properties = bin.properties as Map
        properties.name == 'PP050'
        properties.kind == 'Bin 9x3'
        properties.barcode == 'BIN057'
        properties.source_system == 'esp-entity'
        properties.source_id == '019a3a60-9628-7c90-bc47-f40518a12127'
        properties.source_type_id == '019a3a49-3672-73ec-842d-6c21c5ad9be7'
        properties.source_numeric_id == 50

        and: 'no parent_location_id is denormalized onto the loc'
        !properties.containsKey('parent_location_id')
    }

    def 'ESP plate loc carries plate format and dimensions verbatim'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map plate = insertsByCollection.loc.find { it._id == LOC_PLATE_ID }

        then:
        plate != null
        plate.collection == 'loc'
        Map properties = plate.properties as Map
        properties.name == '27-474501'
        properties.kind == '96W Plate'
        properties.barcode == '27-474501'
        properties.format == '96-well'
        properties.rows == 8
        properties.columns == 12
        properties.source_system == 'esp-entity'
        properties.source_id == '019a420c-728d-7f4c-a817-cd8ba13a1e36'
        properties.source_numeric_id == 474501
        !properties.containsKey('parent_location_id')
    }

    def 'Clarity tube loc preserves Clarity LIMSID and source state, with no contents link'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map tube = insertsByCollection.loc.find { it._id == LOC_TUBE_ID }

        then:
        tube != null
        tube.collection == 'loc'
        tube.type_id == null
        Map properties = tube.properties as Map
        properties.name == '27-170230'
        properties.kind == 'Tube'
        properties.source_system == 'clarity'
        properties.source_id == '27-10000'
        properties.source_state == 'Populated'

        and: 'Clarity-only fields that point at sample analytes are not materialized'
        !properties.containsKey('placement')
        !properties.containsKey('field')
        !properties.containsKey('xml')
        !properties.containsKey('parent_location_id')

        and: 'no lnk row references the Clarity tube as either side'
        insertsByCollection.lnk.every { it.left != LOC_TUBE_ID && it.right != LOC_TUBE_ID }
    }

    def 'typ contents declaration carries assignable_properties: ["position"] and directional labels'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map typ = insertsByCollection.typ[0]

        then:
        typ._id == TYP_CONTENTS_ID
        typ.id == TYP_CONTENTS_ID
        typ.collection == 'typ'
        typ.type_id == null
        typ.links == [:]

        and: 'declarative facts and link-type metadata move under properties'
        Map properties = typ.properties as Map
        properties.kind == 'link_type'
        properties.name == 'contents'
        properties.description == 'containment relationship between a container location and its contents'
        properties.left_role == 'container'
        properties.right_role == 'content'
        properties.left_to_right_label == 'contains'
        properties.right_to_left_label == 'contained_by'
        properties.allowed_left_collections == ['loc']
        properties.allowed_right_collections == ['loc', 'ent']

        and: 'assignable_properties is the flat list required by the design brief'
        properties.assignable_properties == ['position']

        and: 'no required/optional or default-value scaffolding is introduced'
        !properties.containsKey('required')
        !properties.containsKey('optional')
        !properties.containsKey('defaults')

        and: '_head.provenance is the typ source create message'
        Map provenance = (typ._head as Map).provenance as Map
        provenance.collection == 'typ'
        provenance.action == 'create'
        provenance.msg_uuid == MSG_TYP
    }

    def 'freezer→bin lnk references transaction-local typ and carries freezer_slot position only on the link'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map lnk = insertsByCollection.lnk.find { it._id == LNK_FREEZER_BIN_ID }

        then:
        lnk != null
        lnk.collection == 'lnk'
        lnk.type_id == TYP_CONTENTS_ID
        lnk.left == LOC_FREEZER_ID
        lnk.right == LOC_BIN_ID
        lnk.links == [:]

        and: 'freezer slot lives on the link instance, not on either loc endpoint'
        Map properties = lnk.properties as Map
        Map position = properties.position as Map
        position.kind == 'freezer_slot'
        position.label == '2'
        position.slot == 2

        and: 'D7: directional labels live on typ~contents, not on lnk instances'
        !properties.containsKey('left_to_right_label')
        !properties.containsKey('right_to_left_label')
        !properties.containsKey('label')
    }

    def 'bin→plate lnk carries bin_slot position with row/column components'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        Map lnk = insertsByCollection.lnk.find { it._id == LNK_BIN_PLATE_ID }

        then:
        lnk != null
        lnk.collection == 'lnk'
        lnk.type_id == TYP_CONTENTS_ID
        lnk.left == LOC_BIN_ID
        lnk.right == LOC_PLATE_ID

        and: 'bin slot is encoded as the link instance position'
        Map properties = lnk.properties as Map
        Map position = properties.position as Map
        position.kind == 'bin_slot'
        position.label == 'A1'
        position.row == 'A'
        position.column == 1
    }

    def 'every materialized root carries the expected schema metadata and provenance shape'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()
        List<Map> allRoots = []
        allRoots.addAll(insertsByCollection.loc)
        allRoots.addAll(insertsByCollection.typ)
        allRoots.addAll(insertsByCollection.lnk)

        then:
        allRoots.size() == 7
        allRoots.every { Map doc ->
            Map head = doc._head as Map
            Map provenance = head.provenance as Map
            head.schema_version == 1 &&
                    head.document_kind == 'root' &&
                    head.root_id == doc._id &&
                    provenance.txn_id == TXN_UUID &&
                    provenance.commit_id == COMMIT_ID &&
                    provenance.committed_at == COMMITTED_AT &&
                    provenance.action == 'create' &&
                    provenance.collection == doc.collection &&
                    provenance.materialized_at instanceof Instant
        }

        and: 'no root carries the legacy _jt_provenance field'
        allRoots.every { !it.containsKey('_jt_provenance') }

        and: 'every root has links initialized to an empty map'
        allRoots.every { it.links == [:] }
    }

    def 'all four loc roots are free of parent_location_id (D6)'() {
        when:
        materializer.materialize(prototypeSnapshot()).block()

        then: 'parentage is single-sourced in lnk, never copied to loc.properties'
        insertsByCollection.loc.every { Map loc ->
            Map properties = loc.properties as Map
            !properties.containsKey('parent_location_id') &&
                    !properties.containsKey('parent_loc_id') &&
                    !properties.containsKey('parent_id') &&
                    !properties.containsKey('container')
        }
    }
}
