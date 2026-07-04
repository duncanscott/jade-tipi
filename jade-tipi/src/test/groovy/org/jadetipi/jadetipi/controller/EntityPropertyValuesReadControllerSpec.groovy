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

/**
 * TASK-045: the entity route keeps its path and 404 contract but delegates
 * to the generic {@link ObjectPropertyValuesReadService} with the fixed
 * {@code ent} collection, returning the generic {@code propertyValues}
 * response shape.
 */
class EntityPropertyValuesReadControllerSpec extends Specification {

    static final String ENT_ID = 'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~plate_a'
    static final String PPY_BARCODE = 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~ppy~barcode'
    static final String PATH = '/api/entities/{id}/property-values'

    ObjectPropertyValuesReadService readService
    EntityPropertyValuesReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ObjectPropertyValuesReadService)
        controller = new EntityPropertyValuesReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static ObjectPropertyValuesRecord record() {
        return new ObjectPropertyValuesRecord(
                objectId: ENT_ID,
                collection: 'ent',
                typeId: 'jade-tipi-org~dev~018fd849-2a44-7444-8d04-dddddddddddd~typ~plate_96',
                properties: [:],
                links: [:],
                provenance: [commit_id: 'COMMIT-1'],
                propertyValues: [(PPY_BARCODE): new ObjectPropertyValueEntryRecord(
                        propertyId: PPY_BARCODE,
                        propertyName: 'barcode',
                        value: [text: 'barcode-1'],
                        txnId: 'txn-1',
                        commitId: 'COMMIT-1',
                        msgUuid: 'msg-1',
                        appliedAt: java.time.Instant.parse('2026-07-04T00:00:00Z')
                )]
        )
    }

    def 'returns 200 with the generic property-values record for an existing ent root'() {
        given:
        readService.findPropertyValues('ent', ENT_ID) >> Mono.just(record())

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(ENT_ID)
                .jsonPath('$.collection').isEqualTo('ent')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].propertyName").isEqualTo('barcode')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].value.text").isEqualTo('barcode-1')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].commitId").isEqualTo('COMMIT-1')
    }

    def 'returns 404 when the ent root is not materialized'() {
        given:
        readService.findPropertyValues('ent', ENT_ID) >> Mono.empty()

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isNotFound()
    }

    def 'route delegates to the generic reader with the fixed ent collection and no other collaborator'() {
        when:
        webTestClient.get().uri(PATH, ENT_ID).exchange().expectStatus().isOk()

        then:
        1 * readService.findPropertyValues('ent', ENT_ID) >> Mono.just(record())
        0 * _
    }
}
