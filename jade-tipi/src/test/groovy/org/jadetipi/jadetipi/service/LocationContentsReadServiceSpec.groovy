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

class LocationContentsReadServiceSpec extends Specification {

    static final String LOCATION_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String CHILD_LOC_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~tube_a1'
    static final String CHILD_ENT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a2'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String LINK_LOC_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_tube_a1'
    static final String LINK_ENT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a2'
    static final String LINK_UNRESOLVED_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_missing'

    LocationRootReadService locationRootReadService
    ContentsLinkReadService contentsLinkReadService
    ObjectPropertyValuesReadService objectPropertyValuesReadService
    LocationContentsReadService service

    def setup() {
        locationRootReadService = Mock(LocationRootReadService)
        contentsLinkReadService = Mock(ContentsLinkReadService)
        objectPropertyValuesReadService = Mock(ObjectPropertyValuesReadService)
        service = new LocationContentsReadService(
                locationRootReadService,
                contentsLinkReadService,
                objectPropertyValuesReadService)
    }

    private static LocationRootRecord location(String id, String name) {
        return new LocationRootRecord(
                locationId: id,
                typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~location',
                properties: [name: name],
                links: [:],
                provenance: [commit_id: "COMMIT-${name}".toString()]
        )
    }

    private static ObjectPropertyValuesRecord entity(String id, String name) {
        return new ObjectPropertyValuesRecord(
                objectId: id,
                collection: 'ent',
                typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~sample',
                properties: [name: name],
                links: [:],
                provenance: [commit_id: "COMMIT-${name}".toString()],
                propertyValues: [:]
        )
    }

    private static ContentsLinkRecord link(String linkId,
                                           String contentId,
                                           Map properties = [
                                                   position: [
                                                           kind  : 'plate_well',
                                                           label : 'A1',
                                                           row   : 'A',
                                                           column: 1
                                                   ]
                                           ]) {
        return new ContentsLinkRecord(
                linkId: linkId,
                typeId: TYPE_ID,
                left: LOCATION_ID,
                right: contentId,
                properties: properties,
                provenance: [
                        txn_id   : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id: "COMMIT-${linkId[-2..-1]}".toString(),
                        msg_uuid : "msg-${linkId[-2..-1]}".toString()
                ]
        )
    }

    def 'findLocationContents resolves loc and ent children in outgoing contents-link order'() {
        given:
        LocationRootRecord subject = location(LOCATION_ID, 'Plate B1')
        LocationRootRecord childLocation = location(CHILD_LOC_ID, 'Tube A1')
        ObjectPropertyValuesRecord childEntity = entity(CHILD_ENT_ID, 'Sample A2')
        ContentsLinkRecord locLink = link(LINK_LOC_ID, CHILD_LOC_ID,
                [position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]])
        ContentsLinkRecord entLink = link(LINK_ENT_ID, CHILD_ENT_ID,
                [position: [kind: 'plate_well', label: 'A2', row: 'A', column: 2]])

        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.just(subject)
        1 * contentsLinkReadService.findContents(LOCATION_ID) >> Flux.just(locLink, entLink)
        1 * locationRootReadService.findLocation(CHILD_LOC_ID) >> Mono.just(childLocation)
        1 * locationRootReadService.findLocation(CHILD_ENT_ID) >> Mono.empty()
        1 * objectPropertyValuesReadService.findPropertyValues('ent', CHILD_ENT_ID) >> Mono.just(childEntity)
        0 * _

        and:
        record.locationId == LOCATION_ID
        record.location == subject
        record.contents*.linkId == [LINK_LOC_ID, LINK_ENT_ID]
        record.contents*.containerId == [LOCATION_ID, LOCATION_ID]
        record.contents*.contentId == [CHILD_LOC_ID, CHILD_ENT_ID]
        record.contents[0].typeId == TYPE_ID
        record.contents[0].position == [kind: 'plate_well', label: 'A1', row: 'A', column: 1]
        record.contents[0].linkProvenance.commit_id == 'COMMIT-a1'
        record.contents[0].contentLocation == childLocation
        record.contents[0].contentEntity == null
        record.contents[1].position == [kind: 'plate_well', label: 'A2', row: 'A', column: 2]
        record.contents[1].contentLocation == null
        record.contents[1].contentEntity == childEntity
    }

    def 'missing subject location returns empty without contents lookup'() {
        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.empty()
        0 * contentsLinkReadService._
        0 * objectPropertyValuesReadService._

        and:
        record == null
    }

    def 'existing location with no contents returns empty contents list'() {
        given:
        LocationRootRecord subject = location(LOCATION_ID, 'Plate B1')

        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.just(subject)
        1 * contentsLinkReadService.findContents(LOCATION_ID) >> Flux.empty()
        0 * _

        and:
        record.locationId == LOCATION_ID
        record.location == subject
        record.contents == []
    }

    def 'unresolved nonblank child endpoint keeps the link visible'() {
        given:
        LocationRootRecord subject = location(LOCATION_ID, 'Plate B1')
        ContentsLinkRecord unresolvedLink = link(LINK_UNRESOLVED_ID, CHILD_ENT_ID)

        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.just(subject)
        1 * contentsLinkReadService.findContents(LOCATION_ID) >> Flux.just(unresolvedLink)
        1 * locationRootReadService.findLocation(CHILD_ENT_ID) >> Mono.empty()
        1 * objectPropertyValuesReadService.findPropertyValues('ent', CHILD_ENT_ID) >> Mono.empty()
        0 * _

        and:
        record.contents.size() == 1
        record.contents[0].linkId == LINK_UNRESOLVED_ID
        record.contents[0].contentId == CHILD_ENT_ID
        record.contents[0].contentLocation == null
        record.contents[0].contentEntity == null
    }

    def 'blank child endpoint is not resolved through child readers'() {
        given:
        LocationRootRecord subject = location(LOCATION_ID, 'Plate B1')
        ContentsLinkRecord blankRight = link(LINK_UNRESOLVED_ID, '   ')

        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.just(subject)
        1 * contentsLinkReadService.findContents(LOCATION_ID) >> Flux.just(blankRight)
        0 * objectPropertyValuesReadService._
        0 * _

        and:
        record.contents.size() == 1
        record.contents[0].contentId == '   '
        record.contents[0].contentLocation == null
        record.contents[0].contentEntity == null
    }

    def 'links without an object-shaped position keep a null position'() {
        given:
        LocationRootRecord subject = location(LOCATION_ID, 'Plate B1')
        ContentsLinkRecord noPosition = link(LINK_LOC_ID, CHILD_LOC_ID, [:])

        when:
        LocationContentsRecord record = service.findLocationContents(LOCATION_ID).block()

        then:
        1 * locationRootReadService.findLocation(LOCATION_ID) >> Mono.just(subject)
        1 * contentsLinkReadService.findContents(LOCATION_ID) >> Flux.just(noPosition)
        1 * locationRootReadService.findLocation(CHILD_LOC_ID) >> Mono.just(location(CHILD_LOC_ID, 'Tube A1'))
        0 * _

        and:
        record.contents[0].position == null
    }

    def 'blank location id is rejected before collaborator access'() {
        when:
        service.findLocationContents(input).block()

        then:
        thrown(IllegalArgumentException)
        0 * locationRootReadService._
        0 * contentsLinkReadService._
        0 * objectPropertyValuesReadService._

        where:
        input << [null, '', '   ']
    }
}
