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
package org.jadetipi.jadetipi.controller

import org.jadetipi.jadetipi.exception.GlobalExceptionHandler
import org.jadetipi.jadetipi.service.ObjectPropertyValuesRecord
import org.jadetipi.jadetipi.service.LocationContentsEntryRecord
import org.jadetipi.jadetipi.service.LocationContentsReadService
import org.jadetipi.jadetipi.service.LocationContentsRecord
import org.jadetipi.jadetipi.service.LocationRootRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Constructor

class LocationContentsReadControllerSpec extends Specification {

    static final String LOCATION_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String CHILD_LOC_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~tube_a1'
    static final String CHILD_ENT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a2'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String LINK_LOC_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_tube_a1'
    static final String LINK_ENT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a2'
    static final String LOCATION_CONTENTS_PATH = '/api/locations/{id}/contents'

    LocationContentsReadService readService
    LocationContentsReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(LocationContentsReadService)
        controller = new LocationContentsReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static LocationContentsRecord record() {
        return new LocationContentsRecord(
                locationId: LOCATION_ID,
                location: new LocationRootRecord(
                        locationId: LOCATION_ID,
                        typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~plate_96',
                        properties: [name: 'Plate B1'],
                        links: [:],
                        provenance: [commit_id: 'COMMIT-LOC']
                ),
                contents: [
                        new LocationContentsEntryRecord(
                                linkId: LINK_LOC_ID,
                                typeId: TYPE_ID,
                                containerId: LOCATION_ID,
                                contentId: CHILD_LOC_ID,
                                position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1],
                                linkProvenance: [commit_id: 'COMMIT-LNK-LOC'],
                                contentLocation: new LocationRootRecord(
                                        locationId: CHILD_LOC_ID,
                                        typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~tube',
                                        properties: [name: 'Tube A1'],
                                        links: [:],
                                        provenance: [commit_id: 'COMMIT-CHILD-LOC']
                                ),
                                contentEntity: null
                        ),
                        new LocationContentsEntryRecord(
                                linkId: LINK_ENT_ID,
                                typeId: TYPE_ID,
                                containerId: LOCATION_ID,
                                contentId: CHILD_ENT_ID,
                                position: [kind: 'plate_well', label: 'A2', row: 'A', column: 2],
                                linkProvenance: [commit_id: 'COMMIT-LNK-ENT'],
                                contentLocation: null,
                                contentEntity: new ObjectPropertyValuesRecord(
                                        objectId: CHILD_ENT_ID,
                                        collection: 'ent',
                                        typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~sample',
                                        properties: [name: 'Sample A2'],
                                        links: [:],
                                        provenance: [commit_id: 'COMMIT-ENT'],
                                        propertyValues: [:]
                                )
                        )
                ]
        )
    }

    def 'route returns 200 with serialized location contents record'() {
        given:
        readService.findLocationContents(LOCATION_ID) >> Mono.just(record())

        expect:
        webTestClient.get()
                .uri(LOCATION_CONTENTS_PATH, LOCATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.locationId').isEqualTo(LOCATION_ID)
                .jsonPath('$.location.locationId').isEqualTo(LOCATION_ID)
                .jsonPath('$.location.properties.name').isEqualTo('Plate B1')
                .jsonPath('$.location.provenance.commit_id').isEqualTo('COMMIT-LOC')
                .jsonPath('$.contents.length()').isEqualTo(2)
                .jsonPath('$.contents[0].linkId').isEqualTo(LINK_LOC_ID)
                .jsonPath('$.contents[0].typeId').isEqualTo(TYPE_ID)
                .jsonPath('$.contents[0].containerId').isEqualTo(LOCATION_ID)
                .jsonPath('$.contents[0].contentId').isEqualTo(CHILD_LOC_ID)
                .jsonPath('$.contents[0].position.kind').isEqualTo('plate_well')
                .jsonPath('$.contents[0].position.label').isEqualTo('A1')
                .jsonPath('$.contents[0].linkProvenance.commit_id').isEqualTo('COMMIT-LNK-LOC')
                .jsonPath('$.contents[0].contentLocation.locationId').isEqualTo(CHILD_LOC_ID)
                .jsonPath('$.contents[0].contentLocation.properties.name').isEqualTo('Tube A1')
                .jsonPath('$.contents[1].linkId').isEqualTo(LINK_ENT_ID)
                .jsonPath('$.contents[1].contentId').isEqualTo(CHILD_ENT_ID)
                .jsonPath('$.contents[1].position.label').isEqualTo('A2')
                .jsonPath('$.contents[1].contentEntity.objectId').isEqualTo(CHILD_ENT_ID)
                .jsonPath('$.contents[1].contentEntity.properties.name').isEqualTo('Sample A2')
    }

    def 'route delegates to LocationContentsReadService and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(LOCATION_CONTENTS_PATH, LOCATION_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findLocationContents(LOCATION_ID) >> Mono.just(record())
        0 * _
    }

    def 'missing subject location returns 404 with empty body'() {
        given:
        readService.findLocationContents(LOCATION_ID) >> Mono.empty()

        expect:
        webTestClient.get()
                .uri(LOCATION_CONTENTS_PATH, LOCATION_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty()
    }

    def 'blank id surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findLocationContents('   ') >> {
            throw new IllegalArgumentException('locationId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(LOCATION_CONTENTS_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('locationId must not be blank')
    }

    def 'controller has no direct read collaborators other than the location contents read service'() {
        when:
        Constructor<?>[] constructors = LocationContentsReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == LocationContentsReadService
    }

    def 'route path binds exactly to /api/locations/{id}/contents'() {
        expect:
        LOCATION_CONTENTS_PATH == '/api/locations/{id}/contents'
    }
}
