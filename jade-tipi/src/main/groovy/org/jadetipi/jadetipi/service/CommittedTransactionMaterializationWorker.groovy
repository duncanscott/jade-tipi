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
package org.jadetipi.jadetipi.service

import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers

import java.time.Duration
import java.time.Instant

import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_COMMIT_ID
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_RECORD_TYPE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_STATE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.RECORD_TYPE_TRANSACTION
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.STATE_COMMITTED
import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS

/**
 * Background owner of all committed-transaction projection (TASK-052;
 * DIRECTION.md, Transaction Materialization). The commit path only marks the
 * header and calls {@link #nudge}; this worker materializes committed
 * transactions whose header lacks the {@code materialized_at} watermark and
 * stamps the watermark when a projection pass completes.
 *
 * <p>The nudge is a best-effort in-process signal — a dropped nudge is only a
 * latency event, never a correctness event — because the periodic sweep over
 * committed-but-unwatermarked headers (plus one sweep at startup for crash
 * recovery) guarantees every committed transaction is eventually projected,
 * with no dependence on transport redelivery (UT-7). The classic
 * outbox-processor pattern.
 *
 * <p>Everything downstream is idempotent, so nudge/sweep races and multiple
 * app instances at worst duplicate harmless work; the watermark stamp itself
 * is guarded ({@code materialized_at} absent) so it lands once. A projection
 * failure leaves the header unwatermarked and the next sweep retries;
 * persistent failures keep logging warnings (poison-quarantine is plan-F
 * territory).
 */
@Slf4j
@Service
class CommittedTransactionMaterializationWorker {

    static final String FIELD_MATERIALIZED_AT = 'materialized_at'
    private static final String COLLECTION_NAME = COLLECTION_TRANSACTIONS
    private static final String FIELD_ID = '_id'

    private final ReactiveMongoTemplate mongoTemplate
    private final CommittedTransactionMaterializer materializer
    private final boolean enabled
    private final Duration sweepInterval

    private final Sinks.Many<String> nudges = Sinks.many().unicast().onBackpressureBuffer()
    private Disposable nudgePipeline
    private Disposable sweepPipeline

    CommittedTransactionMaterializationWorker(
            ReactiveMongoTemplate mongoTemplate,
            CommittedTransactionMaterializer materializer,
            @Value('${jadetipi.materialization.enabled:true}') boolean enabled,
            @Value('${jadetipi.materialization.sweep-interval:PT30S}') Duration sweepInterval) {
        this.mongoTemplate = mongoTemplate
        this.materializer = materializer
        this.enabled = enabled
        this.sweepInterval = sweepInterval
    }

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info('Materialization worker disabled (jadetipi.materialization.enabled=false)')
            return
        }
        nudgePipeline = nudges.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .concatMap { String txnId ->
                    materializeAndStamp(txnId)
                            .onErrorResume { Throwable ex ->
                                log.warn('Nudged materialization failed, sweep will retry: txnId={}',
                                        txnId, ex)
                                return Mono.empty()
                            }
                }
                .subscribe()
        sweepPipeline = Flux.interval(sweepInterval, sweepInterval, Schedulers.boundedElastic())
                .onBackpressureDrop()
                .concatMap { Long tick ->
                    sweepOnce().onErrorResume { Throwable ex ->
                        log.warn('Materialization sweep failed, will retry next interval', ex)
                        return Mono.empty()
                    }
                }
                .subscribe()
        log.info('Materialization worker started: sweepInterval={}', sweepInterval)
        // Startup sweep: recover transactions committed before a crash.
        sweepOnce()
                .onErrorResume { Throwable ex ->
                    log.warn('Startup materialization sweep failed, periodic sweep will retry', ex)
                    return Mono.empty()
                }
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
    }

    @PreDestroy
    void stop() {
        nudgePipeline?.dispose()
        sweepPipeline?.dispose()
        nudges.tryEmitComplete()
    }

    /**
     * Best-effort signal that {@code txnId} just committed. Failure to enqueue
     * (buffer pressure, shutdown, concurrent emission) is logged and dropped —
     * the sweep covers it.
     */
    void nudge(String txnId) {
        if (!enabled) {
            return
        }
        Sinks.EmitResult result = nudges.tryEmitNext(txnId)
        if (result.isFailure()) {
            log.warn('Materialization nudge dropped ({}), sweep will cover: txnId={}', result, txnId)
        }
    }

    /**
     * One sweep pass: materialize every committed, {@code commit_id}-bearing
     * header that lacks the {@code materialized_at} watermark. Returns the
     * number of headers processed. Per-header failures are logged and skipped
     * so one broken transaction cannot stall the rest.
     */
    Mono<Long> sweepOnce() {
        Query query = Query.query(
                Criteria.where(FIELD_RECORD_TYPE).is(RECORD_TYPE_TRANSACTION)
                        .and(FIELD_STATE).is(STATE_COMMITTED)
                        .and(FIELD_COMMIT_ID).exists(true)
                        .and(FIELD_MATERIALIZED_AT).exists(false)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

        return mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                .map { Map header -> header.get(FIELD_ID) as String }
                .concatMap { String txnId ->
                    materializeAndStamp(txnId)
                            .onErrorResume { Throwable ex ->
                                log.warn('Sweep materialization failed, will retry next interval: txnId={}',
                                        txnId, ex)
                                return Mono.empty()
                            }
                }
                .count()
                .doOnNext { Long processed ->
                    if (processed > 0) {
                        log.info('Materialization sweep processed {} transaction(s)', processed)
                    }
                } as Mono<Long>
    }

    /**
     * Materialize {@code txnId} and stamp the header watermark. Skips headers
     * that are already watermarked or not visible as committed (the
     * committed-snapshot gate). The stamp is guarded on the watermark still
     * being absent, so racing runs stamp once; a failed projection stamps
     * nothing and stays sweep-eligible.
     */
    Mono<MaterializeResult> materializeAndStamp(String txnId) {
        return mongoTemplate.findById(txnId, Map.class, COLLECTION_NAME)
                .filter { Map header -> header.get(FIELD_MATERIALIZED_AT) == null }
                .flatMap { Map header -> materializer.materialize(txnId) }
                .flatMap { MaterializeResult result ->
                    stampWatermark(txnId)
                            .doOnSuccess {
                                log.info('Materialized committed transaction: txnId={}, materialized={}, ' +
                                        'duplicateMatching={}, conflictingDuplicate={}, skippedUnsupported={}',
                                        txnId, result.materialized, result.duplicateMatching,
                                        result.conflictingDuplicate, result.skippedUnsupported)
                            }
                            .thenReturn(result)
                } as Mono<MaterializeResult>
    }

    private Mono<Void> stampWatermark(String txnId) {
        Query query = Query.query(
                Criteria.where(FIELD_ID).is(txnId)
                        .and(FIELD_STATE).is(STATE_COMMITTED)
                        .and(FIELD_MATERIALIZED_AT).exists(false)
        )
        Update update = new Update().set(FIELD_MATERIALIZED_AT, Instant.now())
        return mongoTemplate.updateFirst(query, update, COLLECTION_NAME).then() as Mono<Void>
    }
}
