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
import org.jadetipi.jadetipi.service.ObjectPropertyValueEntryRecord
import org.jadetipi.jadetipi.service.ObjectPropertyValuesReadService
import org.jadetipi.jadetipi.service.ObjectPropertyValuesRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

class LocationPropertyValuesReadControllerSpec extends Specification {

    static final String LOC_ID = 'jade-tipi-org~dev~aaa~loc~plate_0001'
    static final String PPY_BARCODE = 'jade-tipi-org~dev~ccc~ppy~barcode'
    static final String PATH = '/api/locations/{id}/property-values'

    ObjectPropertyValuesReadService readService
    LocationPropertyValuesReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ObjectPropertyValuesReadService)
        controller = new LocationPropertyValuesReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    def 'returns 200 with the property-values record for an existing loc root'() {
        given:
        readService.findPropertyValues('loc', LOC_ID) >> Mono.just(new ObjectPropertyValuesRecord(
                objectId: LOC_ID,
                collection: 'loc',
                typeId: 'jade-tipi-org~dev~bbb~typ~plate_96_well',
                properties: [name: 'demo plate 0001'],
                links: [:],
                provenance: [commit_id: 'COMMIT-1'],
                propertyValues: [(PPY_BARCODE): new ObjectPropertyValueEntryRecord(
                        propertyId: PPY_BARCODE,
                        propertyName: 'barcode',
                        value: [text: 'PLATE-BC-0001'],
                        txnId: 'txn-1',
                        commitId: 'COMMIT-1',
                        msgUuid: 'msg-1',
                        appliedAt: '2026-07-03T00:00:00Z'
                )]
        ))

        expect:
        webTestClient.get().uri(PATH, LOC_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(LOC_ID)
                .jsonPath('$.collection').isEqualTo('loc')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].propertyName").isEqualTo('barcode')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].value.text").isEqualTo('PLATE-BC-0001')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].commitId").isEqualTo('COMMIT-1')
    }

    def 'returns 404 when the loc root is not materialized'() {
        given:
        readService.findPropertyValues('loc', LOC_ID) >> Mono.empty()

        expect:
        webTestClient.get().uri(PATH, LOC_ID)
                .exchange()
                .expectStatus().isNotFound()
    }
}
