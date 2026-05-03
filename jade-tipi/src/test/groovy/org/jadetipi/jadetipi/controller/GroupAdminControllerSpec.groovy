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

import org.jadetipi.jadetipi.dto.GroupCreateRequest
import org.jadetipi.jadetipi.dto.GroupHead
import org.jadetipi.jadetipi.dto.GroupProvenance
import org.jadetipi.jadetipi.dto.GroupRecord
import org.jadetipi.jadetipi.dto.GroupUpdateRequest
import org.jadetipi.jadetipi.exception.GlobalExceptionHandler
import org.jadetipi.jadetipi.service.GroupAdminService
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.http.HttpStatus
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

/**
 * Narrow controller-level coverage for {@link GroupAdminController}.
 *
 * <p>The Spring Security path-matcher rule that gates {@code /api/admin/**}
 * by {@code hasRole('jade-tipi-admin')} runs in the security filter chain,
 * not in this slice. Those 401/403 paths are exercised by the opt-in
 * integration spec; here we verify the route surface, JSON projection, and
 * 404 / 400 / 409 mappings.
 */
class GroupAdminControllerSpec extends Specification {

    static final String GRP_ID = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics'

    GroupAdminService service
    GroupAdminController controller
    WebTestClient webTestClient

    def setup() {
        service = Mock(GroupAdminService)
        controller = new GroupAdminController(service)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static GroupRecord sampleRecord(String action = 'create') {
        GroupProvenance prov = new GroupProvenance(
                txnId: 'admin~11111111-1111-7111-8111-111111111111',
                commitId: 'admin~11111111-1111-7111-8111-111111111111',
                msgUuid: '22222222-2222-7222-8222-222222222222',
                collection: 'grp',
                action: action,
                committedAt: Instant.parse('2026-05-03T00:00:00Z'),
                materializedAt: Instant.parse('2026-05-03T00:00:00Z')
        )
        GroupHead head = new GroupHead(
                schemaVersion: 1,
                documentKind: 'root',
                rootId: GRP_ID,
                provenance: prov
        )
        return new GroupRecord(
                id: GRP_ID,
                collection: 'grp',
                name: 'Analytics',
                description: 'Analytics group',
                permissions: [(GRP_ID): 'rw'],
                head: head
        )
    }

    def 'POST creates a group and returns 201 with projected record'() {
        given:
        service.create(_ as GroupCreateRequest) >> Mono.just(sampleRecord('create'))

        expect:
        webTestClient.post()
                .uri('/api/admin/groups')
                .header('Content-Type', 'application/json')
                .bodyValue([
                        id         : GRP_ID,
                        name       : 'Analytics',
                        description: 'Analytics group',
                        permissions: [(GRP_ID): 'rw']
                ])
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath('$.id').isEqualTo(GRP_ID)
                .jsonPath('$.collection').isEqualTo('grp')
                .jsonPath('$.name').isEqualTo('Analytics')
                .jsonPath('$.description').isEqualTo('Analytics group')
                .jsonPath('$.permissions.' + GRP_ID).isEqualTo('rw')
                .jsonPath('$.head.provenance.action').isEqualTo('create')
                .jsonPath('$.head.provenance.txnId').isEqualTo(
                        'admin~11111111-1111-7111-8111-111111111111')
    }

    def 'POST surfaces service 400 through GlobalExceptionHandler'() {
        given:
        service.create(_ as GroupCreateRequest) >> Mono.error(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        'permissions values must be exactly "rw" or "r" and keys must be non-blank'))

        expect:
        webTestClient.post()
                .uri('/api/admin/groups')
                .header('Content-Type', 'application/json')
                .bodyValue([
                        name       : 'Analytics',
                        permissions: [(GRP_ID): 'admin']
                ])
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
    }

    def 'POST surfaces service 409 through GlobalExceptionHandler'() {
        given:
        service.create(_ as GroupCreateRequest) >> Mono.error(
                new ResponseStatusException(HttpStatus.CONFLICT, "grp already exists with id ${GRP_ID}"))

        expect:
        webTestClient.post()
                .uri('/api/admin/groups')
                .header('Content-Type', 'application/json')
                .bodyValue([
                        id         : GRP_ID,
                        name       : 'Analytics',
                        permissions: [:]
                ])
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT.value())
    }

    def 'GET list returns items array'() {
        given:
        service.list() >> Flux.just(sampleRecord('create'))

        expect:
        webTestClient.get()
                .uri('/api/admin/groups')
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.items.length()').isEqualTo(1)
                .jsonPath('$.items[0].id').isEqualTo(GRP_ID)
                .jsonPath('$.items[0].name').isEqualTo('Analytics')
    }

    def 'GET by id returns 200 when present'() {
        given:
        service.findById(GRP_ID) >> Mono.just(sampleRecord('create'))

        expect:
        webTestClient.get()
                .uri('/api/admin/groups/{id}', GRP_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.id').isEqualTo(GRP_ID)
                .jsonPath('$.name').isEqualTo('Analytics')
    }

    def 'GET by id returns 404 when service returns empty'() {
        given:
        service.findById(GRP_ID) >> Mono.empty()

        expect:
        webTestClient.get()
                .uri('/api/admin/groups/{id}', GRP_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty()
    }

    def 'PUT updates and returns 200 with projected record'() {
        given:
        service.update(GRP_ID, _ as GroupUpdateRequest) >> Mono.just(sampleRecord('update'))

        expect:
        webTestClient.put()
                .uri('/api/admin/groups/{id}', GRP_ID)
                .header('Content-Type', 'application/json')
                .bodyValue([
                        name       : 'Analytics renamed',
                        description: 'updated',
                        permissions: [(GRP_ID): 'rw']
                ])
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath('$.id').isEqualTo(GRP_ID)
                .jsonPath('$.head.provenance.action').isEqualTo('update')
    }

    def 'PUT returns 404 when service returns empty (missing id)'() {
        given:
        service.update(GRP_ID, _ as GroupUpdateRequest) >> Mono.empty()

        expect:
        webTestClient.put()
                .uri('/api/admin/groups/{id}', GRP_ID)
                .header('Content-Type', 'application/json')
                .bodyValue([
                        name       : 'Analytics renamed',
                        permissions: [:]
                ])
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty()
    }
}
