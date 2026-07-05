# TASK-052 - Background materialization worker (UT-7)

ID: TASK-052
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-051
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-052-background-materialization.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - DIRECTION.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/resources/application.yml
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the director-ratified materialization architecture (DIRECTION.md,
Transaction Materialization; ratified 2026-07-05): a background worker owns
all projection. The commit path only marks the header and nudges; a
`materialized_at` watermark plus a periodic sweep guarantee that committed
data can never stay silently invisible (resolves UT-7), and the Kafka
consumer thread never bears projection cost.

DESIGN (ratified 2026-07-05):
- Commit handling shrinks to: durably mark `committed` with the orderable
  `commit_id`, nudge the worker, return. Commit re-delivery no longer
  triggers projection at all — the worker owns it.
- The worker materializes committed headers lacking `materialized_at` and
  stamps the watermark when a pass completes. The nudge (in-process
  signal, best-effort — a dropped nudge is only a latency event) makes
  the typical case effectively immediate; the sweep (startup + periodic,
  configurable interval) is the guarantee. Outbox-processor pattern.
- Idempotency is the safety net throughout: double runs (nudge + sweep
  race, multiple instances) are harmless; the TASK-050/051 guards keep
  terminal-state and late-append semantics intact; the watermark stamp is
  itself guarded (`materialized_at` absent) so it lands once.
- A projection failure leaves the header unwatermarked; the next sweep
  retries. Persistent failures keep logging warnings (no poison-quarantine
  yet — plan task F territory).
- Config: `jadetipi.materialization.enabled` (default true) and
  `jadetipi.materialization.sweep-interval` (default PT30S).

ACCEPTANCE_CRITERIA:
- `TransactionMessagePersistenceService` no longer depends on the
  materializer: first commit nudges the worker; commit re-delivery,
  refusals, and rollback paths never nudge. The inline
  `materializeQuietly` path is retired.
- New worker service: `materializeAndStamp` skips already-watermarked
  headers, materializes via the existing committed-snapshot path, and
  stamps `materialized_at` only after a successful pass (guarded
  update); `sweepOnce` selects committed, commit_id-bearing,
  unwatermarked headers; nudge and sweep pipelines run on background
  scheduling, survive errors, and honor the enabled flag.
- Unit features cover the persistence-service rewire (nudge exactly once
  on first commit, never on duplicates/refusals) and the worker
  (stamp-after-materialize with guard, skip-when-watermarked,
  no-stamp-on-failure, sweep query shape and per-header processing).
- A Mongo-only integration spec plants a committed-but-unmaterialized
  header and message rows directly (the crash-between-commit-and-
  projection state) and proves the sweep materializes the roots and
  stamps the watermark — with a late_append row planted alongside and
  asserted never to materialize.
- The full existing Kafka integration suite stays green through the nudge
  path (every existing spec's post-commit await now exercises the
  worker).
- UT-7 is resolved in place; spec §2.4 documents the worker-owned
  lifecycle (version bump); vocabulary doc's Transaction Records and
  Committed Materialization sections describe the trigger, watermark, and
  sweep.

OUT_OF_SCOPE:
- No per-message `apply_state`, `message_count`, `applied` watermark,
  staging split, or cleanup (plan task F).
- No poison-transaction quarantine or retry backoff beyond
  sweep-interval retries.
- No distributed work coordination between multiple app instances
  (idempotency makes duplicate work safe; efficiency is deferred).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- New `CommittedTransactionMaterializationWorker`: `nudge` is a
  best-effort emit onto a Reactor sink (a failed emit is logged and
  dropped — the sweep covers it); the nudge pipeline runs on
  boundedElastic so the Kafka consumer thread never projects.
  `sweepOnce` selects committed, commit_id-bearing headers lacking
  `materialized_at` (sorted by `_id`), processing each with per-header
  error isolation so one broken transaction cannot stall the rest; the
  sweep runs at startup (crash recovery) and on the configured interval.
  `materializeAndStamp` skips already-watermarked headers, materializes
  through the existing committed-snapshot path (the visibility gate and
  the TASK-051 late-append exclusion apply unchanged), and stamps
  `materialized_at` via a guarded update (`state: committed`, watermark
  absent) so racing runs stamp once; a failed projection stamps nothing
  and stays sweep-eligible. Config:
  `jadetipi.materialization.enabled` (default true) and
  `jadetipi.materialization.sweep-interval` (default PT30S).
- `TransactionMessagePersistenceService`: the materializer dependency is
  replaced by the worker; first commit nudges after the durable mark;
  the COMMIT_DUPLICATE branch no longer materializes (redelivery
  triggers nothing); `materializeQuietly` is deleted. Refusal and
  rollback paths assert zero nudges.
- Unit specs: the persistence spec's two post-commit-hook features and
  two materializer-failure-swallow features are replaced by nudge
  assertions (exactly once on first commit; never on
  duplicates/refusals). New worker spec (seven features): guarded
  stamp shape, skip-when-watermarked, no-stamp-on-failure,
  not-committed-visible no-op, sweep query shape, per-header failure
  isolation, disabled no-op.
- Integration: new Mongo-only `MaterializationSweepIntegrationSpec`
  (sweep-interval PT2S) plants a committed header + message rows
  directly — the crash-between-commit-and-projection state — plus a
  `late_append` row, and proves the sweep alone materializes the root
  with full provenance, stamps the watermark, and never materializes the
  flagged row. The run's output contains the worker's 'Materialization
  sweep processed' line. The full existing Kafka suite (every
  post-commit await now flowing through the nudge path) is green
  unchanged.
- Docs: DIRECTION.md gains the ratified Transaction Materialization
  section; UT-7 flipped to RESOLVED in place (residuals: header-coarse
  watermark until plan-F apply_state; no poison quarantine); spec bumped
  to 0.4.0-draft with §2.4 describing the worker-owned lifecycle and §4
  dropping the re-drive gap; vocabulary doc's Transaction Records and
  Committed Materialization sections describe the trigger, watermark,
  and sweep (the latter's supported-message list also caught up with
  prc/tsk/fil).
- Verification results: `:jade-tipi:test` and `:libraries:jade-tipi-dto:test`
  BUILD SUCCESSFUL; full `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest`
  BUILD SUCCESSFUL (1m52s) — 21 spec classes, zero failures, new sweep
  spec running live; `git diff --check` clean.
