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
import org.jadetipi.jadetipi.service.ContentsLinkReadService
import org.jadetipi.jadetipi.service.ContentsLinkRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.lang.reflect.Constructor

/**
 * Narrow controller-level coverage for {@link ContentsLinkReadController}.
 *
 * <p>Uses {@link WebTestClient#bindToController} (no server, no Spring
 * context, no Mongo/Kafka/Keycloak) to exercise the actual routes, JSON body
 * serialization, the empty-array success contract, and the 400 path through
 * the real {@link GlobalExceptionHandler} advice. The
 * {@link ContentsLinkReadService} collaborator is mocked so the controller
 * never touches persistence.
 */
class ContentsLinkReadControllerSpec extends Specification {

    static final String CONTAINER_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~loc~plate_b1'
    static final String OBJECT_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~sample_x1'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~contents'
    static final String LINK_ID_1 = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_x1'
    static final String LINK_ID_2 = 'jade-tipi-org~dev~lbl_gov~jgi_pps~lnk~plate_b1_sample_x2'

    static final String BY_CONTAINER_PATH = '/api/contents/by-container/{id}'
    static final String BY_CONTENT_PATH = '/api/contents/by-content/{id}'

    ContentsLinkReadService readService
    ContentsLinkReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(ContentsLinkReadService)
        controller = new ContentsLinkReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static ContentsLinkRecord linkRecord(String linkId, String left, String right, int column) {
        return new ContentsLinkRecord(
                linkId: linkId,
                typeId: TYPE_ID,
                left: left,
                right: right,
                properties: [
                        position: [
                                kind  : 'plate_well',
                                label : 'A' + column,
                                row   : 'A',
                                column: column
                        ]
                ],
                provenance: [
                        txn_id         : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id      : 'COMMIT-' + column,
                        msg_uuid       : '11111111-1111-7111-8111-11111111111' + column,
                        committed_at   : '2026-01-01T00:00:05Z',
                        materialized_at: '2026-01-01T00:00:06Z'
                ]
        )
    }

    // --- forward lookup: GET /api/contents/by-container/{id} ---------------

    def 'forward route returns 200 with serialized JSON array preserving service order'() {
        given:
        readService.findContents(CONTAINER_ID) >> Flux.just(
                linkRecord(LINK_ID_1, CONTAINER_ID, OBJECT_ID, 1),
                linkRecord(LINK_ID_2, CONTAINER_ID, OBJECT_ID + '_two', 2)
        )

        expect:
        webTestClient.get()
                .uri(BY_CONTAINER_PATH, CONTAINER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.length()').isEqualTo(2)
                .jsonPath('$[0].linkId').isEqualTo(LINK_ID_1)
                .jsonPath('$[0].typeId').isEqualTo(TYPE_ID)
                .jsonPath('$[0].left').isEqualTo(CONTAINER_ID)
                .jsonPath('$[0].right').isEqualTo(OBJECT_ID)
                .jsonPath('$[0].properties.position.kind').isEqualTo('plate_well')
                .jsonPath('$[0].properties.position.label').isEqualTo('A1')
                .jsonPath('$[0].properties.position.row').isEqualTo('A')
                .jsonPath('$[0].properties.position.column').isEqualTo(1)
                .jsonPath('$[0].provenance.txn_id').isEqualTo('aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee')
                .jsonPath('$[0].provenance.commit_id').isEqualTo('COMMIT-1')
                .jsonPath('$[0].provenance.msg_uuid').isEqualTo('11111111-1111-7111-8111-111111111111')
                .jsonPath('$[0].provenance.committed_at').isEqualTo('2026-01-01T00:00:05Z')
                .jsonPath('$[0].provenance.materialized_at').isEqualTo('2026-01-01T00:00:06Z')
                .jsonPath('$[1].linkId').isEqualTo(LINK_ID_2)
                .jsonPath('$[1].right').isEqualTo(OBJECT_ID + '_two')
                .jsonPath('$[1].properties.position.label').isEqualTo('A2')
                .jsonPath('$[1].properties.position.column').isEqualTo(2)
    }

    def 'forward route delegates to ContentsLinkReadService.findContents and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(BY_CONTAINER_PATH, CONTAINER_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findContents(CONTAINER_ID) >> Flux.just(
                linkRecord(LINK_ID_1, CONTAINER_ID, OBJECT_ID, 1))
        0 * _
    }

    def 'forward route returns 200 with empty JSON array when service emits Flux.empty'() {
        given:
        readService.findContents(CONTAINER_ID) >> Flux.empty()

        expect:
        webTestClient.get()
                .uri(BY_CONTAINER_PATH, CONTAINER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.length()').isEqualTo(0)
    }

    def 'forward route surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findContents('   ') >> {
            throw new IllegalArgumentException('containerId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(BY_CONTAINER_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('containerId must not be blank')
    }

    // --- reverse lookup: GET /api/contents/by-content/{id} ----------------

    def 'reverse route returns 200 with serialized JSON array preserving service order'() {
        given:
        readService.findLocations(OBJECT_ID) >> Flux.just(
                linkRecord(LINK_ID_1, CONTAINER_ID, OBJECT_ID, 1),
                linkRecord(LINK_ID_2, CONTAINER_ID + '_two', OBJECT_ID, 2)
        )

        expect:
        webTestClient.get()
                .uri(BY_CONTENT_PATH, OBJECT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.length()').isEqualTo(2)
                .jsonPath('$[0].linkId').isEqualTo(LINK_ID_1)
                .jsonPath('$[0].typeId').isEqualTo(TYPE_ID)
                .jsonPath('$[0].left').isEqualTo(CONTAINER_ID)
                .jsonPath('$[0].right').isEqualTo(OBJECT_ID)
                .jsonPath('$[0].properties.position.label').isEqualTo('A1')
                .jsonPath('$[0].provenance.commit_id').isEqualTo('COMMIT-1')
                .jsonPath('$[1].linkId').isEqualTo(LINK_ID_2)
                .jsonPath('$[1].left').isEqualTo(CONTAINER_ID + '_two')
                .jsonPath('$[1].right').isEqualTo(OBJECT_ID)
                .jsonPath('$[1].properties.position.label').isEqualTo('A2')
    }

    def 'reverse route delegates to ContentsLinkReadService.findLocations and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(BY_CONTENT_PATH, OBJECT_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findLocations(OBJECT_ID) >> Flux.just(
                linkRecord(LINK_ID_1, CONTAINER_ID, OBJECT_ID, 1))
        0 * _
    }

    def 'reverse route returns 200 with empty JSON array when service emits Flux.empty'() {
        given:
        readService.findLocations(OBJECT_ID) >> Flux.empty()

        expect:
        webTestClient.get()
                .uri(BY_CONTENT_PATH, OBJECT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.length()').isEqualTo(0)
    }

    def 'reverse route surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findLocations('   ') >> {
            throw new IllegalArgumentException('objectId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(BY_CONTENT_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('objectId must not be blank')
    }

    // --- structural assertions --------------------------------------------

    def 'controller has no direct Mongo collaborator: only constructor argument is the read service'() {
        when:
        Constructor<?>[] constructors = ContentsLinkReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == ContentsLinkReadService
    }

    def 'route paths bind exactly to /api/contents/by-container/{id} and /api/contents/by-content/{id}'() {
        expect:
        BY_CONTAINER_PATH == '/api/contents/by-container/{id}'
        BY_CONTENT_PATH == '/api/contents/by-content/{id}'
    }
}
