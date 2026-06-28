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

class ObjectLocationsReadServiceSpec extends Specification {

    static final String OBJECT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a1'
    static final String PLATE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String FREEZER_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~freezer_01'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String LINK_A_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a1'
    static final String LINK_B_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~freezer_sample_a1'

    ContentsLinkReadService contentsLinkReadService
    LocationRootReadService locationRootReadService
    ObjectLocationsReadService service

    def setup() {
        contentsLinkReadService = Mock(ContentsLinkReadService)
        locationRootReadService = Mock(LocationRootReadService)
        service = new ObjectLocationsReadService(contentsLinkReadService, locationRootReadService)
    }

    private static ContentsLinkRecord link(String linkId,
                                           String containerId,
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
                left: containerId,
                right: OBJECT_ID,
                properties: properties,
                provenance: [
                        txn_id   : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id: "COMMIT-${linkId[-2..-1]}".toString(),
                        msg_uuid : "msg-${linkId[-2..-1]}".toString()
                ]
        )
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

    def 'findObjectLocations resolves reverse contents links to container loc roots in link order'() {
        given:
        ContentsLinkRecord plateLink = link(LINK_A_ID, PLATE_ID)
        ContentsLinkRecord freezerLink = link(LINK_B_ID, FREEZER_ID, [position: [kind: 'freezer_slot', path: 'A/1']])
        LocationRootRecord plate = location(PLATE_ID, 'Plate B1')
        LocationRootRecord freezer = location(FREEZER_ID, 'Freezer 01')

        when:
        ObjectLocationsRecord record = service.findObjectLocations(OBJECT_ID).block()

        then:
        1 * contentsLinkReadService.findLocations(OBJECT_ID) >> Flux.just(plateLink, freezerLink)
        1 * locationRootReadService.findLocation(PLATE_ID) >> Mono.just(plate)
        1 * locationRootReadService.findLocation(FREEZER_ID) >> Mono.just(freezer)
        0 * _

        and:
        record.objectId == OBJECT_ID
        record.locations*.linkId == [LINK_A_ID, LINK_B_ID]
        record.locations*.containerId == [PLATE_ID, FREEZER_ID]
        record.locations[0].typeId == TYPE_ID
        record.locations[0].position == [kind: 'plate_well', label: 'A1', row: 'A', column: 1]
        record.locations[0].linkProvenance.commit_id == 'COMMIT-a1'
        record.locations[0].container == plate
        record.locations[1].position == [kind: 'freezer_slot', path: 'A/1']
        record.locations[1].container == freezer
    }

    def 'findObjectLocations returns an empty locations list when no contents links reference the object'() {
        when:
        ObjectLocationsRecord record = service.findObjectLocations(OBJECT_ID).block()

        then:
        1 * contentsLinkReadService.findLocations(OBJECT_ID) >> Flux.empty()
        0 * locationRootReadService._

        and:
        record.objectId == OBJECT_ID
        record.locations == []
    }

    def 'missing container roots are tolerated without dropping the link'() {
        given:
        ContentsLinkRecord plateLink = link(LINK_A_ID, PLATE_ID)

        when:
        ObjectLocationsRecord record = service.findObjectLocations(OBJECT_ID).block()

        then:
        1 * contentsLinkReadService.findLocations(OBJECT_ID) >> Flux.just(plateLink)
        1 * locationRootReadService.findLocation(PLATE_ID) >> Mono.empty()
        0 * _

        and:
        record.locations.size() == 1
        record.locations[0].linkId == LINK_A_ID
        record.locations[0].containerId == PLATE_ID
        record.locations[0].container == null
    }

    def 'links without a left endpoint are not resolved through the location reader'() {
        given:
        ContentsLinkRecord missingLeft = link(LINK_A_ID, null)

        when:
        ObjectLocationsRecord record = service.findObjectLocations(OBJECT_ID).block()

        then:
        1 * contentsLinkReadService.findLocations(OBJECT_ID) >> Flux.just(missingLeft)
        0 * locationRootReadService._

        and:
        record.locations.size() == 1
        record.locations[0].containerId == null
        record.locations[0].container == null
    }

    def 'links without an object-shaped position keep a null position'() {
        given:
        ContentsLinkRecord noPosition = link(LINK_A_ID, PLATE_ID, [:])

        when:
        ObjectLocationsRecord record = service.findObjectLocations(OBJECT_ID).block()

        then:
        1 * contentsLinkReadService.findLocations(OBJECT_ID) >> Flux.just(noPosition)
        1 * locationRootReadService.findLocation(PLATE_ID) >> Mono.just(location(PLATE_ID, 'Plate B1'))
        0 * _

        and:
        record.locations[0].position == null
    }

    def 'blank object id is rejected before collaborator access'() {
        when:
        service.findObjectLocations(input).block()

        then:
        thrown(IllegalArgumentException)
        0 * contentsLinkReadService._
        0 * locationRootReadService._

        where:
        input << [null, '', '   ']
    }
}
