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
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_OPENED_AT
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_RECORD_TYPE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_ROLLBACK_DATA
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_ROLLED_BACK_AT
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_SNAPSHOT_ID
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.FIELD_STATE
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.RECORD_TYPE_TRANSACTION
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.STATE_COMMITTED
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.STATE_OPEN
import static org.jadetipi.jadetipi.service.TransactionMessagePersistenceService.STATE_ROLLED_BACK
import static org.jadetipi.jadetipi.util.Constants.COLLECTION_TRANSACTIONS

/**
 * Background owner of all committed-transaction projection (TASK-052;
 * DIRECTION.md, Transaction Materialization). The terminal paths (commit and
 * rollback) only mark the header and call {@link #nudge}; this worker
 * materializes committed transactions whose header lacks the
 * {@code materialized_at} watermark and stamps the watermark when a
 * projection pass completes.
 *
 * <p>The nudge is a best-effort in-process signal — a dropped nudge is only a
 * latency event, never a correctness event — because the periodic sweep over
 * committed-but-unwatermarked headers (plus one sweep at startup for crash
 * recovery) guarantees every committed transaction is eventually projected,
 * with no dependence on transport redelivery (UT-7). The classic
 * outbox-processor pattern. A nudge triggers a full sweep pass rather than a
 * single-transaction projection: any terminal outcome can release
 * transactions other than its own (TASK-063).
 *
 * <p><b>Snapshot-isolation watermark (TASK-063; DIRECTION.md, Snapshot
 * Isolation And Orderable Commit IDs).</b> The sweep materializes a committed
 * transaction only when no open transaction has a {@code snapshot_id} older
 * than that transaction's {@code commit_id} — both are backend-minted
 * UUIDv7s, so the comparison is a string compare on one timeline. Roots
 * therefore always hold the floor state: nothing an open transaction is not
 * entitled to see. A header predating {@code snapshot_id} participates via
 * the leading UUID segment of its transaction ID; a legacy non-UUID
 * {@code commit_id} predates every live open and is always eligible.
 *
 * <p><b>Transaction leases (TASK-063).</b> An abandoned open transaction
 * would hold the watermark back forever, so each sweep first rolls back —
 * durably, guarded on {@code state: open}, with audit {@code rollback_data}
 * — every open header older than {@code jadetipi.transaction.lease}. A late
 * commit then meets the ordinary COMMIT_REFUSED_ROLLED_BACK semantics.
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
    private final Duration transactionLease

    private final Sinks.Many<String> nudges = Sinks.many().unicast().onBackpressureBuffer()
    private Disposable nudgePipeline
    private Disposable sweepPipeline

    CommittedTransactionMaterializationWorker(
            ReactiveMongoTemplate mongoTemplate,
            CommittedTransactionMaterializer materializer,
            @Value('${jadetipi.materialization.enabled:true}') boolean enabled,
            @Value('${jadetipi.materialization.sweep-interval:PT30S}') Duration sweepInterval,
            @Value('${jadetipi.transaction.lease:PT1H}') Duration transactionLease) {
        this.mongoTemplate = mongoTemplate
        this.materializer = materializer
        this.enabled = enabled
        this.sweepInterval = sweepInterval
        this.transactionLease = transactionLease
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
                    sweepOnce()
                            .onErrorResume { Throwable ex ->
                                log.warn('Nudged sweep failed, periodic sweep will retry: txnId={}',
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
     * Best-effort signal that {@code txnId} just reached a terminal state
     * (committed or rolled back). Triggers a sweep pass — a terminal outcome
     * advances the snapshot watermark, so transactions other than
     * {@code txnId} may have become eligible. Failure to enqueue (buffer
     * pressure, shutdown, concurrent emission) is logged and dropped — the
     * periodic sweep covers it.
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
     * One sweep pass: first roll back opens whose lease expired (they would
     * hold the watermark back forever), then materialize every committed,
     * {@code commit_id}-bearing header that lacks the {@code materialized_at}
     * watermark AND is not blocked by the snapshot watermark — no open
     * transaction may have a {@code snapshot_id} older than the header's
     * {@code commit_id}. Returns the number of headers processed. Per-header
     * failures are logged and skipped so one broken transaction cannot stall
     * the rest; blocked headers are simply left for a later sweep.
     */
    Mono<Long> sweepOnce() {
        Query query = Query.query(
                Criteria.where(FIELD_RECORD_TYPE).is(RECORD_TYPE_TRANSACTION)
                        .and(FIELD_STATE).is(STATE_COMMITTED)
                        .and(FIELD_COMMIT_ID).exists(true)
                        .and(FIELD_MATERIALIZED_AT).exists(false)
        ).with(Sort.by(Sort.Direction.ASC, FIELD_ID))

        return expireStaleOpens()
                .then(openSnapshotWatermark())
                .flatMap { Optional<String> watermark ->
                    mongoTemplate.find(query, Map.class, COLLECTION_NAME)
                            .filter { Map header ->
                                boolean eligible = eligibleUnderWatermark(
                                        header.get(FIELD_COMMIT_ID) as String, watermark.orElse(null))
                                if (!eligible) {
                                    log.debug('Materialization blocked by open snapshot watermark {}: txnId={}',
                                            watermark.get(), header.get(FIELD_ID))
                                }
                                return eligible
                            }
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
                }
                .doOnNext { Long processed ->
                    if (processed > 0) {
                        log.info('Materialization sweep processed {} transaction(s)', processed)
                    }
                } as Mono<Long>
    }

    /**
     * The snapshot watermark: the minimum {@code snapshot_id} over open
     * transaction headers, empty when nothing is open (no bound). A header
     * predating the {@code snapshot_id} field contributes the leading UUID
     * segment of its transaction ID instead.
     */
    private Mono<Optional<String>> openSnapshotWatermark() {
        Query openQuery = Query.query(
                Criteria.where(FIELD_RECORD_TYPE).is(RECORD_TYPE_TRANSACTION)
                        .and(FIELD_STATE).is(STATE_OPEN))
        return mongoTemplate.find(openQuery, Map.class, COLLECTION_NAME)
                .map { Map header -> effectiveSnapshotId(header) }
                .reduce { String a, String b -> a <= b ? a : b }
                .map { String min -> Optional.of(min) }
                .defaultIfEmpty(Optional.<String> empty()) as Mono<Optional<String>>
    }

    private static String effectiveSnapshotId(Map header) {
        String snapshotId = header.get(FIELD_SNAPSHOT_ID) as String
        if (snapshotId) {
            return snapshotId
        }
        String txnId = header.get(FIELD_ID) as String
        return txnId.tokenize('~').first()
    }

    /**
     * A committed transaction is blocked while any open transaction has an
     * older snapshot point. A legacy non-UUID {@code commit_id} predates the
     * UUIDv7 scheme — and therefore every live open — so it is always
     * eligible.
     */
    private static boolean eligibleUnderWatermark(String commitId, String watermark) {
        if (watermark == null) {
            return true
        }
        try {
            UUID.fromString(commitId)
        } catch (IllegalArgumentException ignored) {
            return true
        }
        return commitId < watermark
    }

    /**
     * Roll back every open transaction older than the configured lease —
     * durably, guarded on the header still being open, with the expiry
     * recorded as {@code rollback_data} (audit). The expired opens stop
     * holding the watermark back within the same sweep pass; a later commit
     * for an expired transaction is refused by the ordinary terminal-state
     * guard.
     */
    private Mono<Long> expireStaleOpens() {
        Instant cutoff = Instant.now() - transactionLease
        Query expired = Query.query(
                Criteria.where(FIELD_RECORD_TYPE).is(RECORD_TYPE_TRANSACTION)
                        .and(FIELD_STATE).is(STATE_OPEN)
                        .and(FIELD_OPENED_AT).lt(cutoff))
        return mongoTemplate.find(expired, Map.class, COLLECTION_NAME)
                .map { Map header -> header.get(FIELD_ID) as String }
                .concatMap { String txnId ->
                    Query guard = Query.query(Criteria.where(FIELD_ID).is(txnId)
                            .and(FIELD_STATE).is(STATE_OPEN))
                    Update update = new Update()
                            .set(FIELD_STATE, STATE_ROLLED_BACK)
                            .set(FIELD_ROLLED_BACK_AT, Instant.now())
                            .set(FIELD_ROLLBACK_DATA, [
                                    reason: 'lease_expired',
                                    lease : transactionLease.toString()
                            ] as Map<String, Object>)
                    return mongoTemplate.updateFirst(guard, update, COLLECTION_NAME)
                            .doOnSuccess {
                                log.warn('Rolled back open transaction past its lease ({}): txnId={}',
                                        transactionLease, txnId)
                            }
                            .onErrorResume { Throwable ex ->
                                log.warn('Lease-expiry rollback failed, will retry next sweep: txnId={}',
                                        txnId, ex)
                                return Mono.empty()
                            }
                }
                .count() as Mono<Long>
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
