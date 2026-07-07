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
import org.jadetipi.jadetipi.service.ObjectHistoryEntryRecord
import org.jadetipi.jadetipi.service.ObjectHistoryReadService
import org.jadetipi.jadetipi.service.ObjectHistoryRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * TASK-065: the history route shares the TASK-064 dereference rule (the
 * collection comes from the ID's collection segment; malformed or unserved
 * is 404 with the read service untouched), keeps the resource-read 404 for
 * a missing root, and passes the property filter and paging through.
 */
class ObjectHistoryReadControllerSpec extends Specification {

    static final String ID_PREFIX = 'jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee'
    static final String ENT_ID = "${ID_PREFIX}~ent~plate_a"
    static final String PPY_BARCODE = 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~ppy~barcode'
    static final String MSG_UUID = '018fd849-2a42-7222-8a02-bbbbbbbbbbbb'
    static final String PATH = '/api/objects/{id}/history'

    ObjectHistoryReadService readService
    ObjectHistoryReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ObjectHistoryReadService)
        controller = new ObjectHistoryReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static ObjectHistoryRecord record() {
        return new ObjectHistoryRecord(
                objectId: ENT_ID,
                collection: 'ent',
                propertyId: null,
                items: [new ObjectHistoryEntryRecord(
                        msgUuid: MSG_UUID,
                        propertyId: PPY_BARCODE,
                        propertyName: 'barcode',
                        value: [text: 'barcode-1'],
                        txnId: 'txn-1',
                        commitId: '018fd849-2a46-7666-8f06-ffffffffffff',
                        appliedAt: java.time.Instant.parse('2026-07-06T00:00:00Z')
                )],
                page: 0,
                size: 25,
                total: 1L
        )
    }

    def 'returns 200 with the history envelope for an existing root'() {
        given:
        readService.findHistory('ent', ENT_ID, null, 0, 25) >> Mono.just(record())

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.objectId').isEqualTo(ENT_ID)
                .jsonPath('$.collection').isEqualTo('ent')
                .jsonPath('$.total').isEqualTo(1)
                .jsonPath('$.items[0].msgUuid').isEqualTo(MSG_UUID)
                .jsonPath('$.items[0].propertyName').isEqualTo('barcode')
                .jsonPath('$.items[0].value.text').isEqualTo('barcode-1')
    }

    def 'passes the property filter and paging through to the reader'() {
        when:
        webTestClient.get()
                .uri({ builder ->
                    builder.path('/api/objects/{id}/history')
                            .queryParam('property_id', PPY_BARCODE)
                            .queryParam('page', '2')
                            .queryParam('size', '5')
                            .build(ENT_ID)
                })
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findHistory('ent', ENT_ID, PPY_BARCODE, 2, 5) >> Mono.just(record())
        0 * _
    }

    def 'returns 404 when the subject root is not materialized'() {
        given:
        readService.findHistory('ent', ENT_ID, null, 0, 25) >> Mono.empty()

        expect:
        webTestClient.get().uri(PATH, ENT_ID)
                .exchange()
                .expectStatus().isNotFound()
    }

    def 'a malformed or non-dereferenceable id is 404 and never touches the read service'() {
        when:
        webTestClient.get().uri(PATH, badId).exchange().expectStatus().isNotFound()

        then:
        0 * readService._

        where:
        case_                                | badId
        'too few segments'                   | 'jade-tipi-org~dev~plate_a'
        'non-value-bearing collection (typ)' | "${ID_PREFIX}~typ~plate_96"
        'unknown collection segment'         | "${ID_PREFIX}~xyz~plate_a"
    }
}
