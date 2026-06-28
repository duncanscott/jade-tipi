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
import org.jadetipi.jadetipi.service.EntityPropertyValueRecord
import org.jadetipi.jadetipi.service.EntityPropertyValuesRecord
import org.jadetipi.jadetipi.service.PlateContentsEntryRecord
import org.jadetipi.jadetipi.service.PlateContentsReadService
import org.jadetipi.jadetipi.service.PlateContentsRecord
import org.jadetipi.jadetipi.service.PlateContentsUnplacedReason
import org.jadetipi.jadetipi.service.PlateContentsWellRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Constructor

class PlateContentsReadControllerSpec extends Specification {

    static final String PLATE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String SAMPLE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_a1'
    static final String LINK_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_a1'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String BARCODE_PROPERTY_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~pp~barcode'
    static final String PLATE_CONTENTS_PATH = '/api/contents/plate/{id}'

    PlateContentsReadService readService
    PlateContentsReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(PlateContentsReadService)
        controller = new PlateContentsReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static PlateContentsRecord record() {
        PlateContentsEntryRecord entry = new PlateContentsEntryRecord(
                linkId: LINK_ID,
                typeId: TYPE_ID,
                objectId: SAMPLE_ID,
                position: [kind: 'plate_well', row: 'A', column: 1],
                unplacedReason: null,
                linkProvenance: [commit_id: 'COMMIT-LNK'],
                entity: new EntityPropertyValuesRecord(
                        entityId: SAMPLE_ID,
                        typeId: 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~sample',
                        properties: [label: 'Sample A1'],
                        links: [:],
                        provenance: [commit_id: 'COMMIT-ENT'],
                        valuesByPropertyId: [
                                (BARCODE_PROPERTY_ID): [new EntityPropertyValueRecord(
                                        assignmentId: "${SAMPLE_ID}~${BARCODE_PROPERTY_ID}".toString(),
                                        propertyId: BARCODE_PROPERTY_ID,
                                        propertyName: 'barcode',
                                        value: [text: 'barcode-a1'],
                                        provenance: [commit_id: 'COMMIT-PPY']
                                )]
                        ]
                )
        )
        PlateContentsEntryRecord unplaced = new PlateContentsEntryRecord(
                linkId: "${LINK_ID}_unplaced".toString(),
                typeId: TYPE_ID,
                objectId: SAMPLE_ID,
                position: [kind: 'plate_well', row: 'A', column: 'north'],
                unplacedReason: PlateContentsUnplacedReason.COLUMN_MALFORMED,
                linkProvenance: [commit_id: 'COMMIT-LNK-UNPLACED'],
                entity: null
        )
        return new PlateContentsRecord(
                containerId: PLATE_ID,
                rowCount: 8,
                columnCount: 12,
                rowLabels: ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'],
                columnLabels: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
                wells: [new PlateContentsWellRecord(
                        label: 'A1',
                        row: 'A',
                        column: 1,
                        contents: [entry]
                )],
                unplacedContents: [unplaced]
        )
    }

    def 'route returns 200 with serialized plate contents record'() {
        given:
        readService.findPlateContents(PLATE_ID) >> Mono.just(record())

        expect:
        webTestClient.get()
                .uri(PLATE_CONTENTS_PATH, PLATE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.containerId').isEqualTo(PLATE_ID)
                .jsonPath('$.rowCount').isEqualTo(8)
                .jsonPath('$.columnCount').isEqualTo(12)
                .jsonPath('$.rowLabels.length()').isEqualTo(8)
                .jsonPath('$.columnLabels.length()').isEqualTo(12)
                .jsonPath('$.columnLabels[0]').isEqualTo(1)
                .jsonPath('$.columnLabels[11]').isEqualTo(12)
                .jsonPath('$.wells.length()').isEqualTo(1)
                .jsonPath('$.wells[0].label').isEqualTo('A1')
                .jsonPath('$.wells[0].contents.length()').isEqualTo(1)
                .jsonPath('$.wells[0].contents[0].linkId').isEqualTo(LINK_ID)
                .jsonPath('$.wells[0].contents[0].objectId').isEqualTo(SAMPLE_ID)
                .jsonPath('$.wells[0].contents[0].position.kind').isEqualTo('plate_well')
                .jsonPath('$.wells[0].contents[0].linkProvenance.commit_id').isEqualTo('COMMIT-LNK')
                .jsonPath('$.wells[0].contents[0].entity.entityId').isEqualTo(SAMPLE_ID)
                .jsonPath("\$.wells[0].contents[0].entity.valuesByPropertyId['${BARCODE_PROPERTY_ID}'][0].value.text")
                .isEqualTo('barcode-a1')
                .jsonPath('$.unplacedContents.length()').isEqualTo(1)
                .jsonPath('$.unplacedContents[0].unplacedReason').isEqualTo('COLUMN_MALFORMED')
    }

    def 'route delegates to PlateContentsReadService and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(PLATE_CONTENTS_PATH, PLATE_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findPlateContents(PLATE_ID) >> Mono.just(record())
        0 * _
    }

    def 'blank id surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findPlateContents('   ') >> {
            throw new IllegalArgumentException('containerId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(PLATE_CONTENTS_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('containerId must not be blank')
    }

    def 'controller has no direct read collaborators other than the plate read service'() {
        when:
        Constructor<?>[] constructors = PlateContentsReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == PlateContentsReadService
    }

    def 'route path binds exactly to /api/contents/plate/{id}'() {
        expect:
        PLATE_CONTENTS_PATH == '/api/contents/plate/{id}'
    }
}
