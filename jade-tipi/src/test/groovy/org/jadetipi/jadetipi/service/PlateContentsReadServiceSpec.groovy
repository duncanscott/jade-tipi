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

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class PlateContentsReadServiceSpec extends Specification {

    static final String PLATE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String SAMPLE_A_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a1'
    static final String SAMPLE_B_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_b12'
    static final String SAMPLE_C_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_c3'
    static final String LINK_A_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a1'
    static final String LINK_B_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_b12'
    static final String LINK_C_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_c3'
    static final String BARCODE_PROPERTY_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~pp~barcode'

    ContentsLinkReadService contentsLinkReadService
    EntityPropertyValuesReadService entityPropertyValuesReadService
    PlateContentsReadService service

    def setup() {
        contentsLinkReadService = Mock(ContentsLinkReadService)
        entityPropertyValuesReadService = Mock(EntityPropertyValuesReadService)
        service = new PlateContentsReadService(contentsLinkReadService, entityPropertyValuesReadService)
    }

    private static ContentsLinkRecord link(String linkId,
                                           String objectId,
                                           Map position = [kind: 'plate_well', row: 'A', column: 1]) {
        return linkWithProperties(linkId, objectId, [position: position])
    }

    private static ContentsLinkRecord linkWithProperties(String linkId, String objectId, Map properties) {
        return new ContentsLinkRecord(
                linkId: linkId,
                typeId: TYPE_ID,
                left: PLATE_ID,
                right: objectId,
                properties: properties,
                provenance: [
                        txn_id   : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id: "COMMIT-${linkId[-2..-1]}".toString(),
                        msg_uuid : "msg-${linkId[-2..-1]}".toString()
                ]
        )
    }

    private static EntityPropertyValuesRecord entity(String entityId, String barcode) {
        return new EntityPropertyValuesRecord(
                entityId: entityId,
                typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~sample',
                properties: [label: barcode],
                links: [:],
                provenance: [commit_id: "COMMIT-${barcode}".toString()],
                valuesByPropertyId: [
                        (BARCODE_PROPERTY_ID): [new EntityPropertyValueRecord(
                                assignmentId: "${entityId}~${BARCODE_PROPERTY_ID}".toString(),
                                propertyId: BARCODE_PROPERTY_ID,
                                propertyName: 'barcode',
                                value: [text: barcode],
                                provenance: [commit_id: "COMMIT-${barcode}-ASSIGNMENT".toString()]
                        )]
                ]
        )
    }

    private static PlateContentsWellRecord well(PlateContentsRecord record, String label) {
        return record.wells.find { PlateContentsWellRecord well -> well.label == label }
    }

    def 'findPlateContents builds a fixed 96-well view and resolves placed entity property values'() {
        given:
        ContentsLinkRecord linkA1 = link(LINK_A_ID, SAMPLE_A_ID,
                [kind: 'plate_well', row: 'a', column: '1'])
        ContentsLinkRecord linkB12 = link(LINK_B_ID, SAMPLE_B_ID,
                [kind: 'plate_well', row: 'B', column: 12])
        EntityPropertyValuesRecord sampleA = entity(SAMPLE_A_ID, 'barcode-a1')
        EntityPropertyValuesRecord sampleB = entity(SAMPLE_B_ID, 'barcode-b12')

        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.just(linkA1, linkB12)
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_A_ID) >> Mono.just(sampleA)
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_B_ID) >> Mono.just(sampleB)
        0 * _

        and:
        record.containerId == PLATE_ID
        record.rowCount == 8
        record.columnCount == 12
        record.rowLabels == ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
        record.columnLabels == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
        record.wells.size() == 96
        record.wells.first().label == 'A1'
        record.wells.last().label == 'H12'
        record.unplacedContents == []

        and: 'valid positions are normalized and placed into the matching wells'
        PlateContentsWellRecord a1 = well(record, 'A1')
        a1.row == 'A'
        a1.column == 1
        a1.contents.size() == 1
        a1.contents[0].linkId == LINK_A_ID
        a1.contents[0].objectId == SAMPLE_A_ID
        a1.contents[0].position.row == 'a'
        a1.contents[0].position.column == '1'
        a1.contents[0].unplacedReason == null
        a1.contents[0].linkProvenance.commit_id == 'COMMIT-a1'
        a1.contents[0].entity == sampleA
        a1.contents[0].entity.valuesByPropertyId[BARCODE_PROPERTY_ID][0].value == [text: 'barcode-a1']

        and:
        PlateContentsWellRecord b12 = well(record, 'B12')
        b12.contents.size() == 1
        b12.contents[0].linkId == LINK_B_ID
        b12.contents[0].entity == sampleB

        and: 'empty wells remain present'
        well(record, 'A2').contents == []
    }

    def 'findPlateContents returns the empty 96-well grid when the plate has no contents links'() {
        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.empty()
        0 * entityPropertyValuesReadService._

        and:
        record.containerId == PLATE_ID
        record.columnLabels == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
        record.wells.size() == 96
        record.wells.every { PlateContentsWellRecord well -> well.contents.isEmpty() }
        record.unplacedContents == []
    }

    def 'multiple contents links in the same well are preserved in link read order'() {
        given:
        ContentsLinkRecord first = link(LINK_A_ID, SAMPLE_A_ID,
                [kind: 'plate_well', row: 'A', column: 1])
        ContentsLinkRecord second = link(LINK_C_ID, SAMPLE_C_ID,
                [kind: 'plate_well', row: 'A', column: 1])

        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.just(first, second)
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_A_ID) >>
                Mono.just(entity(SAMPLE_A_ID, 'barcode-a1'))
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_C_ID) >>
                Mono.just(entity(SAMPLE_C_ID, 'barcode-c3'))
        0 * _

        and:
        well(record, 'A1').contents*.linkId == [LINK_A_ID, LINK_C_ID]
        record.unplacedContents == []
    }

    def 'missing entity roots are tolerated and invalid positions are surfaced as unplaced'() {
        given:
        ContentsLinkRecord placedMissingEntity = link(LINK_C_ID, SAMPLE_C_ID,
                [kind: 'plate_well', row: 'C', column: 3])
        ContentsLinkRecord invalidPosition = link(LINK_B_ID, SAMPLE_B_ID,
                [kind: 'plate_well', row: 'Z', column: 1])

        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.just(placedMissingEntity, invalidPosition)
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_C_ID) >> Mono.empty()
        1 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_B_ID) >>
                Mono.just(entity(SAMPLE_B_ID, 'barcode-b12'))
        0 * _

        and: 'the link remains placed even when the right endpoint has no entity root'
        well(record, 'C3').contents.size() == 1
        well(record, 'C3').contents[0].linkId == LINK_C_ID
        well(record, 'C3').contents[0].entity == null

        and: 'invalid positions are not dropped'
        record.unplacedContents.size() == 1
        record.unplacedContents[0].linkId == LINK_B_ID
        record.unplacedContents[0].unplacedReason == PlateContentsUnplacedReason.ROW_INVALID
        record.unplacedContents[0].entity.entityId == SAMPLE_B_ID
    }

    def 'unplaced contents report distinct placement reasons'() {
        given:
        List<ContentsLinkRecord> links = [
                linkWithProperties('link-position-missing', SAMPLE_A_ID, [:]),
                link('link-kind-unsupported', SAMPLE_A_ID,
                        [kind: 'rack_slot', row: 'A', column: 1]),
                link('link-row-missing', SAMPLE_A_ID,
                        [kind: 'plate_well', column: 1]),
                link('link-column-out-of-range', SAMPLE_A_ID,
                        [kind: 'plate_well', row: 'A', column: 13]),
                link('link-column-missing', SAMPLE_A_ID,
                        [kind: 'plate_well', row: 'A']),
                link('link-column-malformed', SAMPLE_A_ID,
                        [kind: 'plate_well', row: 'A', column: 'north'])
        ]

        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.fromIterable(links)
        6 * entityPropertyValuesReadService.findPropertyValues(SAMPLE_A_ID) >>
                Mono.just(entity(SAMPLE_A_ID, 'barcode-a1'))
        0 * _

        and:
        record.wells.every { PlateContentsWellRecord well -> well.contents.isEmpty() }
        record.unplacedContents*.linkId == [
                'link-position-missing',
                'link-kind-unsupported',
                'link-row-missing',
                'link-column-out-of-range',
                'link-column-missing',
                'link-column-malformed'
        ]
        record.unplacedContents*.position == [
                null,
                [kind: 'rack_slot', row: 'A', column: 1],
                [kind: 'plate_well', column: 1],
                [kind: 'plate_well', row: 'A', column: 13],
                [kind: 'plate_well', row: 'A'],
                [kind: 'plate_well', row: 'A', column: 'north']
        ]
        record.unplacedContents*.unplacedReason == [
                PlateContentsUnplacedReason.POSITION_MISSING,
                PlateContentsUnplacedReason.POSITION_KIND_UNSUPPORTED,
                PlateContentsUnplacedReason.ROW_MISSING,
                PlateContentsUnplacedReason.COLUMN_OUT_OF_RANGE,
                PlateContentsUnplacedReason.COLUMN_MISSING,
                PlateContentsUnplacedReason.COLUMN_MALFORMED
        ]
    }

    def 'links without a right endpoint are not resolved through the entity reader'() {
        given:
        ContentsLinkRecord missingRight = link(LINK_A_ID, null,
                [kind: 'plate_well', row: 'A', column: 1])

        when:
        PlateContentsRecord record = service.findPlateContents(PLATE_ID).block()

        then:
        1 * contentsLinkReadService.findContents(PLATE_ID) >> Flux.just(missingRight)
        0 * entityPropertyValuesReadService._

        and:
        well(record, 'A1').contents.size() == 1
        well(record, 'A1').contents[0].objectId == null
        well(record, 'A1').contents[0].unplacedReason == null
        well(record, 'A1').contents[0].entity == null
    }

    def 'blank container id is rejected before collaborator access'() {
        when:
        service.findPlateContents(input).block()

        then:
        thrown(IllegalArgumentException)
        0 * contentsLinkReadService._
        0 * entityPropertyValuesReadService._

        where:
        input << [null, '', '   ']
    }
}
