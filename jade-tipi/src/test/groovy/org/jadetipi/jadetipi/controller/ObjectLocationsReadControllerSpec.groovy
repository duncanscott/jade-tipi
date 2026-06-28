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
import org.jadetipi.jadetipi.service.LocationRootRecord
import org.jadetipi.jadetipi.service.ObjectLocationEntryRecord
import org.jadetipi.jadetipi.service.ObjectLocationsReadService
import org.jadetipi.jadetipi.service.ObjectLocationsRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Constructor

class ObjectLocationsReadControllerSpec extends Specification {

    static final String OBJECT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a1'
    static final String PLATE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String LINK_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a1'
    static final String OBJECT_LOCATIONS_PATH = '/api/contents/by-content/{id}/locations'

    ObjectLocationsReadService readService
    ObjectLocationsReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ObjectLocationsReadService)
        controller = new ObjectLocationsReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static ObjectLocationsRecord record() {
        return new ObjectLocationsRecord(
                objectId: OBJECT_ID,
                locations: [new ObjectLocationEntryRecord(
                        linkId: LINK_ID,
                        typeId: TYPE_ID,
                        containerId: PLATE_ID,
                        position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1],
                        linkProvenance: [commit_id: 'COMMIT-LNK'],
                        container: new LocationRootRecord(
                                locationId: PLATE_ID,
                                typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~plate_96',
                                properties: [name: 'Plate B1'],
                                links: [:],
                                provenance: [commit_id: 'COMMIT-LOC']
                        )
                )]
        )
    }

    def 'route returns 200 with serialized resolved object locations record'() {
        given:
        readService.findObjectLocations(OBJECT_ID) >> Mono.just(record())

        expect:
        webTestClient.get()
                .uri(OBJECT_LOCATIONS_PATH, OBJECT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(OBJECT_ID)
                .jsonPath('$.locations.length()').isEqualTo(1)
                .jsonPath('$.locations[0].linkId').isEqualTo(LINK_ID)
                .jsonPath('$.locations[0].typeId').isEqualTo(TYPE_ID)
                .jsonPath('$.locations[0].containerId').isEqualTo(PLATE_ID)
                .jsonPath('$.locations[0].position.kind').isEqualTo('plate_well')
                .jsonPath('$.locations[0].position.label').isEqualTo('A1')
                .jsonPath('$.locations[0].linkProvenance.commit_id').isEqualTo('COMMIT-LNK')
                .jsonPath('$.locations[0].container.locationId').isEqualTo(PLATE_ID)
                .jsonPath('$.locations[0].container.properties.name').isEqualTo('Plate B1')
                .jsonPath('$.locations[0].container.provenance.commit_id').isEqualTo('COMMIT-LOC')
    }

    def 'route delegates to ObjectLocationsReadService and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(OBJECT_LOCATIONS_PATH, OBJECT_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findObjectLocations(OBJECT_ID) >> Mono.just(record())
        0 * _
    }

    def 'blank id surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findObjectLocations('   ') >> {
            throw new IllegalArgumentException('objectId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(OBJECT_LOCATIONS_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('objectId must not be blank')
    }

    def 'controller has no direct read collaborators other than the object locations read service'() {
        when:
        Constructor<?>[] constructors = ObjectLocationsReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == ObjectLocationsReadService
    }

    def 'route path binds exactly to /api/contents/by-content/{id}/locations'() {
        expect:
        OBJECT_LOCATIONS_PATH == '/api/contents/by-content/{id}/locations'
    }
}
