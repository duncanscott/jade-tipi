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
import org.jadetipi.jadetipi.service.EntityPropertyValuesReadService
import org.jadetipi.jadetipi.service.EntityPropertyValuesRecord
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Constructor

class EntityPropertyValuesReadControllerSpec extends Specification {

    static final String ENTITY_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~ent~plate_a'
    static final String TYPE_ID = 'jade-tipi-org~dev~lbl_gov~jgi_pps~typ~plate_96'
    static final String PROPERTY_ID = 'barcode'
    static final String ASSIGNMENT_ID = "${ENTITY_ID}~${PROPERTY_ID}"
    static final String PROPERTY_VALUES_PATH = '/api/entities/{id}/property-values'

    EntityPropertyValuesReadService readService
    EntityPropertyValuesReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(EntityPropertyValuesReadService)
        controller = new EntityPropertyValuesReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static EntityPropertyValuesRecord record(
            Map<String, List<EntityPropertyValueRecord>> valuesByPropertyId = [
                    (PROPERTY_ID): [new EntityPropertyValueRecord(
                            assignmentId: ASSIGNMENT_ID,
                            propertyId: PROPERTY_ID,
                            propertyName: 'barcode',
                            value: [text: 'barcode-1'],
                            provenance: [
                                    txn_id   : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                                    commit_id: 'COMMIT-PPY',
                                    msg_uuid : '22222222-2222-7222-8222-222222222222'
                            ]
                    )]
            ]) {
        return new EntityPropertyValuesRecord(
                entityId: ENTITY_ID,
                typeId: TYPE_ID,
                properties: [label: 'Plate A'],
                links: [:],
                provenance: [
                        txn_id   : 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee',
                        commit_id: 'COMMIT-ENT',
                        msg_uuid : '11111111-1111-7111-8111-111111111111'
                ],
                valuesByPropertyId: valuesByPropertyId
        )
    }

    def 'route returns 200 with serialized entity property-values record'() {
        given:
        readService.findPropertyValues(ENTITY_ID) >> Mono.just(record())

        expect:
        webTestClient.get()
                .uri(PROPERTY_VALUES_PATH, ENTITY_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.entityId').isEqualTo(ENTITY_ID)
                .jsonPath('$.typeId').isEqualTo(TYPE_ID)
                .jsonPath('$.properties.label').isEqualTo('Plate A')
                .jsonPath('$.provenance.commit_id').isEqualTo('COMMIT-ENT')
                .jsonPath('$.valuesByPropertyId.barcode.length()').isEqualTo(1)
                .jsonPath('$.valuesByPropertyId.barcode[0].assignmentId').isEqualTo(ASSIGNMENT_ID)
                .jsonPath('$.valuesByPropertyId.barcode[0].propertyId').isEqualTo(PROPERTY_ID)
                .jsonPath('$.valuesByPropertyId.barcode[0].propertyName').isEqualTo('barcode')
                .jsonPath('$.valuesByPropertyId.barcode[0].value.text').isEqualTo('barcode-1')
                .jsonPath('$.valuesByPropertyId.barcode[0].provenance.msg_uuid')
                .isEqualTo('22222222-2222-7222-8222-222222222222')
    }

    def 'route delegates to EntityPropertyValuesReadService and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(PROPERTY_VALUES_PATH, ENTITY_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findPropertyValues(ENTITY_ID) >> Mono.just(record())
        0 * _
    }

    def 'missing entity returns 404 with empty body'() {
        given:
        readService.findPropertyValues(ENTITY_ID) >> Mono.empty()

        expect:
        webTestClient.get()
                .uri(PROPERTY_VALUES_PATH, ENTITY_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty()
    }

    def 'existing entity with no assignments returns 200 with empty values map'() {
        given:
        readService.findPropertyValues(ENTITY_ID) >> Mono.just(record([:]))

        expect:
        webTestClient.get()
                .uri(PROPERTY_VALUES_PATH, ENTITY_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.entityId').isEqualTo(ENTITY_ID)
                .jsonPath('$.valuesByPropertyId').exists()
                .jsonPath('$.valuesByPropertyId.length()').isEqualTo(0)
    }

    def 'blank id surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findPropertyValues('   ') >> {
            throw new IllegalArgumentException('entityId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(PROPERTY_VALUES_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('entityId must not be blank')
    }

    def 'controller has no direct Mongo collaborator: only constructor argument is the read service'() {
        when:
        Constructor<?>[] constructors = EntityPropertyValuesReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == EntityPropertyValuesReadService
    }

    def 'route path binds exactly to /api/entities/{id}/property-values'() {
        expect:
        PROPERTY_VALUES_PATH == '/api/entities/{id}/property-values'
    }
}
