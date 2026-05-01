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
import org.jadetipi.jadetipi.service.CommittedTransactionMessage
import org.jadetipi.jadetipi.service.CommittedTransactionReadService
import org.jadetipi.jadetipi.service.CommittedTransactionSnapshot
import org.jadetipi.jadetipi.service.KafkaProvenance
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.Constructor
import java.time.Instant

/**
 * Narrow controller-level coverage for {@link CommittedTransactionReadController}.
 *
 * <p>Uses {@link WebTestClient#bindToController} (no server, no Spring context,
 * no Mongo/Kafka/Keycloak) to exercise the actual route, JSON body
 * serialization, the 404 empty-body path, and the 400 path through the real
 * {@link GlobalExceptionHandler} advice. The {@link CommittedTransactionReadService}
 * collaborator is mocked so the controller never touches persistence.
 */
class CommittedTransactionReadControllerSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'
    static final String SNAPSHOT_PATH = '/api/transactions/{id}/snapshot'

    CommittedTransactionReadService readService
    CommittedTransactionReadController controller
    WebTestClient webTestClient

    def setup() {
        readService = Mock(CommittedTransactionReadService)
        controller = new CommittedTransactionReadController(readService)
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers({ configurer ->
                    configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(
                            ReactiveAdapterRegistry.getSharedInstance()))
                })
                .build()
    }

    private static CommittedTransactionSnapshot fullSnapshot() {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: Instant.parse('2026-01-01T00:00:00Z'),
                committedAt: Instant.parse('2026-01-01T00:00:05Z'),
                openData: [hint: 'open'],
                commitData: [reason: 'done'],
                messages: [
                        new CommittedTransactionMessage(
                                msgUuid: '11111111-1111-7111-8111-111111111111',
                                collection: 'ppy',
                                action: 'create',
                                data: [name: 'first'],
                                receivedAt: Instant.parse('2026-01-01T00:00:01Z'),
                                kafka: new KafkaProvenance(
                                        topic: 'jdtp-txn-prd',
                                        partition: 0,
                                        offset: 42L,
                                        timestampMs: 1700000000000L
                                )
                        ),
                        new CommittedTransactionMessage(
                                msgUuid: '22222222-2222-7222-8222-222222222222',
                                collection: 'ppy',
                                action: 'create',
                                data: [name: 'second'],
                                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                                kafka: new KafkaProvenance(
                                        topic: 'jdtp-txn-prd',
                                        partition: 0,
                                        offset: 43L,
                                        timestampMs: 1700000001000L
                                )
                        )
                ]
        )
    }

    def 'committed snapshot returns 200 with serialized body and preserves message order'() {
        given:
        readService.findCommitted(TXN_ID) >> Mono.just(fullSnapshot())

        expect:
        webTestClient.get()
                .uri(SNAPSHOT_PATH, TXN_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType('application/json')
                .expectBody()
                .jsonPath('$.txnId').isEqualTo(TXN_ID)
                .jsonPath('$.commitId').isEqualTo(COMMIT_ID)
                .jsonPath('$.state').isEqualTo('committed')
                .jsonPath('$.openData.hint').isEqualTo('open')
                .jsonPath('$.commitData.reason').isEqualTo('done')
                .jsonPath('$.messages.length()').isEqualTo(2)
                .jsonPath('$.messages[0].msgUuid').isEqualTo('11111111-1111-7111-8111-111111111111')
                .jsonPath('$.messages[0].collection').isEqualTo('ppy')
                .jsonPath('$.messages[0].action').isEqualTo('create')
                .jsonPath('$.messages[0].data.name').isEqualTo('first')
                .jsonPath('$.messages[0].kafka.topic').isEqualTo('jdtp-txn-prd')
                .jsonPath('$.messages[0].kafka.partition').isEqualTo(0)
                .jsonPath('$.messages[0].kafka.offset').isEqualTo(42)
                .jsonPath('$.messages[0].kafka.timestampMs').isEqualTo(1700000000000L)
                .jsonPath('$.messages[1].msgUuid').isEqualTo('22222222-2222-7222-8222-222222222222')
    }

    def 'controller delegates committed lookup to CommittedTransactionReadService and to no other collaborator'() {
        when:
        webTestClient.get()
                .uri(SNAPSHOT_PATH, TXN_ID)
                .exchange()
                .expectStatus().isOk()

        then:
        1 * readService.findCommitted(TXN_ID) >> Mono.just(fullSnapshot())
        0 * _
    }

    def 'missing or non-committed snapshot returns 404 with empty body'() {
        given:
        readService.findCommitted(TXN_ID) >> Mono.empty()

        expect:
        webTestClient.get()
                .uri(SNAPSHOT_PATH, TXN_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty()
    }

    def 'whitespace-only id surfaces service Assert.hasText as 400 ErrorResponse via GlobalExceptionHandler'() {
        given:
        readService.findCommitted('   ') >> {
            throw new IllegalArgumentException('txnId must not be blank')
        }

        expect:
        webTestClient.get()
                .uri(SNAPSHOT_PATH, '   ')
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath('$.status').isEqualTo(400)
                .jsonPath('$.error').isEqualTo('Bad Request')
                .jsonPath('$.message').isEqualTo('txnId must not be blank')
    }

    def 'controller has no direct Mongo collaborator: only constructor argument is the read service'() {
        when:
        Constructor<?>[] constructors = CommittedTransactionReadController.getDeclaredConstructors()
        Constructor<?> ctor = constructors.find { it.parameterCount == 1 }

        then:
        ctor != null
        ctor.parameterTypes.length == 1
        ctor.parameterTypes[0] == CommittedTransactionReadService
    }
}
