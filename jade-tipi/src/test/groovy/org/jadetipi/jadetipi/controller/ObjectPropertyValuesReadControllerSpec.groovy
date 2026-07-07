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
 * TASK-064: the generic object route dereferences the Mongo collection from
 * the ID's collection segment (the ID is the complete address) and keeps the
 * resource-read 404 contract. A malformed ID, or one naming a collection the
 * route does not serve, is 404 without ever touching the read service.
 */
class ObjectPropertyValuesReadControllerSpec extends Specification {

    static final String ID_PREFIX = 'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee'
    static final String ENT_ID = "${ID_PREFIX}~ent~plate_a"
    static final String PPY_BARCODE = 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~ppy~barcode'
    static final String PATH = '/api/objects/{id}/property-values'

    ObjectPropertyValuesReadService readService
    ObjectPropertyValuesReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ObjectPropertyValuesReadService)
        controller = new ObjectPropertyValuesReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static ObjectPropertyValuesRecord record(String objectId, String collection) {
        return new ObjectPropertyValuesRecord(
                objectId: objectId,
                collection: collection,
                typeId: 'jade-tipi-org~dev~018fd849-2a44-7444-8d04-dddddddddddd~typ~plate_96',
                properties: [:],
                links: [:],
                provenance: [commit_id: '018fd849-2a46-7666-8f06-ffffffffffff'],
                propertyValues: [(PPY_BARCODE): new ObjectPropertyValueEntryRecord(
                        propertyId: PPY_BARCODE,
                        propertyName: 'barcode',
                        value: [text: 'barcode-1'],
                        txnId: 'txn-1',
                        commitId: '018fd849-2a46-7666-8f06-ffffffffffff',
                        msgUuid: 'msg-1',
                        appliedAt: java.time.Instant.parse('2026-07-04T00:00:00Z')
                )]
        )
    }

    def 'returns 200 with the generic record for an existing root'() {
        given:
        readService.findPropertyValues('ent', ENT_ID) >> Mono.just(record(ENT_ID, 'ent'))

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(ENT_ID)
                .jsonPath('$.collection').isEqualTo('ent')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].propertyName").isEqualTo('barcode')
                .jsonPath("\$.propertyValues['${PPY_BARCODE}'].value.text").isEqualTo('barcode-1')
    }

    def 'dereferences the collection from the id segment for every value-bearing collection'() {
        given:
        String objectId = "${ID_PREFIX}~${collection}~probe_1"

        when:
        webTestClient.get().uri(PATH, objectId).exchange().expectStatus().isOk()

        then: 'the route delegates to the generic reader with the parsed collection'
        1 * readService.findPropertyValues(collection, objectId) >>
                Mono.just(record(objectId, collection))
        0 * _

        where:
        collection << ['ent', 'loc', 'prc', 'tsk', 'fil']
    }

    def 'returns 404 when the root is not materialized'() {
        given:
        readService.findPropertyValues('ent', ENT_ID) >> Mono.empty()

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isNotFound()
    }

    def 'a malformed or non-dereferenceable id is 404 and never touches the read service'() {
        when:
        webTestClient.get().uri(PATH, badId).exchange().expectStatus().isNotFound()

        then: 'no dereference, no guess'
        0 * readService._

        where:
        case_                                  | badId
        'too few segments'                     | 'jade-tipi-org~dev~plate_a'
        'too many segments'                    | "${ID_PREFIX}~ent~plate_a~extra"
        'non-value-bearing collection (typ)'   | "${ID_PREFIX}~typ~plate_96"
        'unknown collection segment'           | "${ID_PREFIX}~xyz~plate_a"
        'no separators at all'                 | 'just-a-string'
    }
}
