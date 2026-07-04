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
package org.jadetipi.jadetipi.importer

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

/**
 * TASK-043 mapper coverage against fixture documents captured verbatim
 * (trimmed) from the live local {@code clarity}/{@code esp-entity}
 * replicas, so the mapping rules are proven against real source shapes.
 */
class ClarityEspContainerImportMapperSpec extends Specification {

    static final String PREFIX = 'jade-tipi-org~dev~itest-import'
    static final String PPY_NAME = "${PREFIX}~ppy~name"
    static final String PPY_BARCODE = "${PREFIX}~ppy~barcode"
    static final String TYP_CONTENTS = "${PREFIX}~typ~contents"
    static final String TYP_CONTAINER = "${PREFIX}~typ~container"
    static final String TYP_FREEZER = "${PREFIX}~typ~freezer"
    static final String TYP_BIN = "${PREFIX}~typ~bin"
    static final String TYP_PLATE96 = "${PREFIX}~typ~plate_96_well"
    static final String TYP_TUBE = "${PREFIX}~typ~tube"
    static final String TYP_LIBRARY = "${PREFIX}~typ~illumina_library"

    ClarityEspContainerModel model = new ClarityEspContainerModel(
            propertyNameId: PPY_NAME,
            propertyBarcodeId: PPY_BARCODE,
            contentsTypeId: TYP_CONTENTS,
            fallbackContainerTypeId: TYP_CONTAINER,
            kindMappings: [
                    'Freezer (6-shelf)': new ClarityEspKindMapping(
                            typeId: TYP_FREEZER, idSlug: 'freezer', positionKind: 'freezer_slot'),
                    'Bin 9x3'          : new ClarityEspKindMapping(
                            typeId: TYP_BIN, idSlug: 'bin', positionKind: 'bin_slot'),
                    '96W Plate'        : new ClarityEspKindMapping(
                            typeId: TYP_PLATE96, idSlug: 'plate', positionKind: 'plate_well'),
                    'Tube'             : new ClarityEspKindMapping(
                            typeId: TYP_TUBE, idSlug: 'tube', positionKind: null),
                    'Illumina Library' : new ClarityEspKindMapping(
                            typeId: TYP_LIBRARY, idSlug: 'library', positionKind: null)
            ])

    private static final ObjectMapper JSON = new ObjectMapper()

    private static Map fixture(String name) {
        InputStream stream = ClarityEspContainerImportMapperSpec
                .getResourceAsStream("/importfixtures/${name}")
        assert stream != null: "fixture not found: ${name}"
        return JSON.readValue(stream, Map)
    }

    def 'maps the ESP bin to a typed loc create, assignments, and a child-side freezer link'() {
        when:
        List<MappedImportMessage> messages =
                ClarityEspContainerImportMapper.mapEspDocument(fixture('esp-bin.json'), model, PREFIX)

        then: 'root create follows D4 with source facts only in the inline bag'
        messages.size() == 4
        MappedImportMessage root = messages[0]
        root.collection == 'loc'
        root.action == 'create'
        root.data.id == "${PREFIX}~loc~esp_bin_019a3a60-9628" as String
        root.data.type_id == TYP_BIN
        Map bag = root.data.properties as Map
        bag.source_kind == 'Bin 9x3'
        bag.source_system == 'esp-entity'
        bag.source_id == '019a3a60-9628-7c90-bc47-f40518a12127'
        bag.source_type_id == '019a3a49-3672-73ec-842d-6c21c5ad9be7'
        bag.source_numeric_id == 50
        !bag.containsKey('name')
        !bag.containsKey('barcode')
        !bag.containsKey('kind')

        and: 'name and barcode become object-targeted assignments'
        messages[1].collection == 'ppy'
        messages[1].data.kind == 'assignment'
        messages[1].data.object_collection == 'loc'
        messages[1].data.object_id == root.data.id
        messages[1].data.property_id == PPY_NAME
        messages[1].data.value == [text: 'PP050']
        messages[2].data.property_id == PPY_BARCODE
        messages[2].data.value == [text: 'BIN057']

        and: 'the containment link derives from the bin\'s own container pointer (freezer slot 2)'
        MappedImportMessage link = messages[3]
        link.collection == 'lnk'
        link.data.id == "${PREFIX}~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2" as String
        link.data.type_id == TYP_CONTENTS
        link.data.left == "${PREFIX}~loc~esp_freezer_019a3a62-8fa8" as String
        link.data.right == root.data.id
        (link.data.properties as Map).position == [kind: 'freezer_slot', label: '2', slot: 2]
    }

    def 'maps the ESP plate with a bin_slot position parsed from the row-column label'() {
        when:
        List<MappedImportMessage> messages =
                ClarityEspContainerImportMapper.mapEspDocument(fixture('esp-plate.json'), model, PREFIX)

        then:
        messages[0].data.id == "${PREFIX}~loc~esp_plate_019a420c-728d" as String
        messages[0].data.type_id == TYP_PLATE96
        MappedImportMessage link = messages[3]
        link.data.id == "${PREFIX}~lnk~contents_bin_pp050_to_plate_27-474501_a1" as String
        link.data.left == "${PREFIX}~loc~esp_bin_019a3a60-9628" as String
        (link.data.properties as Map).position == [kind: 'bin_slot', label: 'A1', row: 'A', column: 1]

        and: 'format/rows/columns are never synthesized — only source-present facts map'
        messages.every { MappedImportMessage m ->
            !(m.data.toString().contains('format')) }
        messages.size() == 4
    }

    def 'maps the ESP freezer without a containment link (container is null)'() {
        when:
        List<MappedImportMessage> messages =
                ClarityEspContainerImportMapper.mapEspDocument(fixture('esp-freezer.json'), model, PREFIX)

        then:
        messages.size() == 3
        messages[0].data.id == "${PREFIX}~loc~esp_freezer_019a3a62-8fa8" as String
        messages[0].data.type_id == TYP_FREEZER
        messages*.collection == ['loc', 'ppy', 'ppy']
    }

    def 'maps the ESP library (class Sample) to a typed ent root with a plate_well link'() {
        when:
        List<MappedImportMessage> messages =
                ClarityEspContainerImportMapper.mapEspDocument(fixture('esp-library.json'), model, PREFIX)

        then:
        MappedImportMessage root = messages[0]
        root.collection == 'ent'
        root.data.id == "${PREFIX}~ent~esp_library_lhcpot" as String
        root.data.type_id == TYP_LIBRARY
        (root.data.properties as Map).source_system == 'esp-entity'
        !(root.data.properties as Map).containsKey('name')

        and: 'assignments target the ent collection'
        messages[1].data.object_collection == 'ent'
        messages[1].data.value == [text: 'LHCPOT']
        messages[2].data.value == [text: '27-474501']

        and: 'the plate link carries the A2 plate_well position'
        MappedImportMessage link = messages[3]
        link.data.id == "${PREFIX}~lnk~contents_plate_27-474501_to_library_lhcpot_a2" as String
        link.data.left == "${PREFIX}~loc~esp_plate_019a420c-728d" as String
        link.data.right == root.data.id
        (link.data.properties as Map).position == [kind: 'plate_well', label: 'A2', row: 'A', column: 2]
    }

    def 'maps the Clarity tube to a typed loc with a name assignment and no barcode or link'() {
        when:
        List<MappedImportMessage> messages = ClarityEspContainerImportMapper
                .mapClarityContainerDocument(fixture('clarity-tube.json'), model, PREFIX)

        then:
        messages.size() == 2
        MappedImportMessage root = messages[0]
        root.collection == 'loc'
        root.data.id == "${PREFIX}~loc~clarity_tube_27-10000" as String
        root.data.type_id == TYP_TUBE
        Map bag = root.data.properties as Map
        bag.source_kind == 'Tube'
        bag.source_system == 'clarity'
        bag.source_id == '27-10000'
        bag.source_state == 'Populated'
        messages[1].data.property_id == PPY_NAME
        messages[1].data.value == [text: '27-170230']
        messages.every { MappedImportMessage m -> m.collection != 'lnk' }
    }

    def 'an unmapped ESP container kind falls back to the base container type'() {
        given:
        Map doc = fixture('esp-freezer.json')
        doc.type_name = 'Cryo Vault (unknown)'

        when:
        List<MappedImportMessage> messages =
                ClarityEspContainerImportMapper.mapEspDocument(doc, model, PREFIX)

        then:
        messages[0].data.type_id == TYP_CONTAINER
        messages[0].data.id == "${PREFIX}~loc~esp_container_019a3a62-8fa8" as String
        (messages[0].data.properties as Map).source_kind == 'Cryo Vault (unknown)'
    }

    def 'an unmapped non-container class is skipped'() {
        given:
        Map doc = fixture('esp-library.json')
        doc.type_name = 'Mystery Analyte'

        expect:
        ClarityEspContainerImportMapper.mapEspDocument(doc, model, PREFIX).isEmpty()
    }

    def 'a document without a uuid is skipped'() {
        expect:
        ClarityEspContainerImportMapper.mapEspDocument([_id: 'x'], model, PREFIX).isEmpty()
        ClarityEspContainerImportMapper.mapClarityContainerDocument([_id: 'y'], model, PREFIX).isEmpty()
    }
}
