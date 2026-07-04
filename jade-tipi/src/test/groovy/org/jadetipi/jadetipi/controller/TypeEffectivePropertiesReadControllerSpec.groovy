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
import org.jadetipi.jadetipi.service.TypeEffectivePropertiesReadService
import org.jadetipi.jadetipi.service.TypeEffectivePropertiesRecord
import org.jadetipi.jadetipi.service.TypeEffectivePropertyRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

class TypeEffectivePropertiesReadControllerSpec extends Specification {

    static final String TYP_PLATE96 = 'jade-tipi-org~dev~018fd849-2a63-7333-8a03-cccccccccccc~typ~plate_96_well'
    static final String TYP_CONTAINER = 'jade-tipi-org~dev~018fd849-2a61-7111-8a01-aaaaaaaaaaaa~typ~container'
    static final String PPY_BARCODE = 'jade-tipi-org~dev~018fd849-2a64-7444-8a04-dddddddddddd~ppy~barcode'
    static final String PATH = '/api/types/{id}/effective-properties'

    TypeEffectivePropertiesReadService readService
    TypeEffectivePropertiesReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(TypeEffectivePropertiesReadService)
        controller = new TypeEffectivePropertiesReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    def 'returns 200 with the effective-properties record for an existing type'() {
        given:
        readService.findEffectiveProperties(TYP_PLATE96) >> Mono.just(new TypeEffectivePropertiesRecord(
                typeId: TYP_PLATE96,
                typeName: 'plate_96_well',
                typeChain: [TYP_PLATE96, TYP_CONTAINER],
                chainComplete: true,
                effectiveProperties: [(PPY_BARCODE): new TypeEffectivePropertyRecord(
                        propertyId: PPY_BARCODE,
                        propertyName: 'barcode',
                        sourceTypeId: TYP_CONTAINER,
                        reference: [required: true]
                )]
        ))

        expect:
        webTestClient.get().uri(PATH, TYP_PLATE96)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.typeId').isEqualTo(TYP_PLATE96)
                .jsonPath('$.chainComplete').isEqualTo(true)
                .jsonPath('$.typeChain[0]').isEqualTo(TYP_PLATE96)
                .jsonPath("\$.effectiveProperties['${PPY_BARCODE}'].sourceTypeId").isEqualTo(TYP_CONTAINER)
                .jsonPath("\$.effectiveProperties['${PPY_BARCODE}'].reference.required").isEqualTo(true)
    }

    def 'returns 404 when the subject type root is not materialized'() {
        given:
        readService.findEffectiveProperties(TYP_PLATE96) >> Mono.empty()

        expect:
        webTestClient.get().uri(PATH, TYP_PLATE96)
                .exchange()
                .expectStatus().isNotFound()
    }
}
