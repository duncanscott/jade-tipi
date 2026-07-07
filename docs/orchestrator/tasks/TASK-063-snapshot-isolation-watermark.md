# TASK-063 - Snapshot isolation: snapshot_id, materialization watermark, transaction leases

ID: TASK-063
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_IMPLEMENTATION
OWNER: unassigned
SOURCE_TASK:
  - TASK-052
  - TASK-061
  - TASK-062
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-063-snapshot-isolation-watermark.md
  - docs/jdtp-specification.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the ratified snapshot-isolation design (DIRECTION.md, Snapshot
Isolation And Orderable Commit IDs): a transaction sees the world as of
its birth — it cannot read any effect of a commit newer than its
snapshot point — enforced structurally by gating background
materialization on the oldest-open-transaction watermark, with
transaction leases guaranteeing liveness. Depends on TASK-062
(UUIDv7 commit IDs).

DESIGN (ratified 2026-07-06):

Visibility rule:
- A transaction cannot read any property value or entity written by a
  commit whose `commit_id` is newer than the transaction's snapshot
  point. Any transaction newer than a commit may read the entities
  created and the property values set by that commit.
- The snapshot point is a backend-assigned **`snapshot_id`**: a UUIDv7
  minted when the open message is processed
  (`UuidCreator.getTimeOrderedEpoch()`, same generator as TASK-062
  commit IDs), stored on the transaction header (`$setOnInsert` in
  `openHeader`). The client-minted transaction UUID remains the
  transaction's identity — it is baked into object IDs — but it is a
  client clock; `snapshot_id` and `commit_id` are minted by the single
  Kafka consumer in processing order, so their ordering is total and
  correct by construction (an open processed after a commit always
  receives a larger ID). Comparisons use `snapshot_id`, never the raw
  transaction UUID.
- Legacy tolerance: a header without `snapshot_id` (pre-existing dev
  data) falls back to the leading UUID segment of its `txn_id` for
  watermark purposes.

Materialization watermark (the retention mechanism):
- Old values must be preserved as long as an open transaction is
  entitled to read them. Therefore the worker materializes a committed
  transaction only when **no open transaction has a `snapshot_id` older
  than that transaction's `commit_id`**.
- Implementation: watermark = minimum `snapshot_id` over headers with
  `record_type: "transaction"`, `state: "open"` (unbounded when none);
  the sweep projects exactly the committed, unwatermarked headers with
  `commit_id < watermark` (string `$lt` — both are bare UUIDv7s).
  The director's original formulation — each queued materialization job
  carries a set of open transactions it waits on, shrinking as they
  reach terminal state, running when empty — is the same invariant
  tracked incrementally; the watermark form is chosen (no per-job
  bookkeeping, jobs become eligible in commit order automatically),
  with per-job sets a revisit if the min-query ever bottlenecks.
- Roots thus always hold the floor state (nothing newer than what every
  open transaction may see), so the existing read surface serves every
  open transaction a consistent snapshot with no per-query filtering.
  Newer transactions see bounded staleness until the watermark
  advances; the already-ratified overlay reads (plan task F) close that
  gap per reader later. Value application is order-independent
  (newest-message-wins by msg UUIDv7, TASK-061), so delayed and
  out-of-order materialization stay correct.
- Every terminal outcome advances the watermark and must nudge the
  worker: commit already nudges; add the nudge to rollback (and to
  lease-expiry rollback).
- Index to support the watermark min-query and the sweep filter
  (state + snapshot_id), ensured at startup alongside the existing
  initializer duties.

Transaction leases (liveness):
- An abandoned open transaction would halt materialization globally,
  forever. Open transactions therefore carry a lease: configurable
  `jadetipi.transaction.lease` (Duration, suggest default PT1H;
  evaluated at sweep time from the header's `opened_at`, so config
  changes apply retroactively).
- The periodic sweep auto-rolls-back expired opens through the normal
  durable rollback path: guarded on `state: "open"`, stamping
  `rolled_back_at` and a synthetic `rollback_data` recording the lease
  expiry (auditable), then nudging the worker. A late commit after
  expiry meets the existing COMMIT_REFUSED_ROLLED_BACK semantics
  (UT-2 machinery, unchanged).

ACCEPTANCE_CRITERIA:
- Open stamps `snapshot_id` (UUIDv7) on the header; redelivered opens
  do not overwrite it.
- Worker gate proven at unit level: a committed header with `commit_id`
  above the minimum open `snapshot_id` is not materialized; it is
  materialized once no older open remains (commit, rollback, and
  expiry each release it); with no opens everything eligible is
  materialized; a legacy header without `snapshot_id` participates via
  its txn-UUID fallback.
- Rollback nudges the worker.
- Lease expiry proven: an open header older than the lease is durably
  rolled back by the sweep with the synthetic rollback_data, the
  worker is nudged, and a subsequent commit is refused.
- Mongo-only integration spec (MaterializationSweepIntegrationSpec
  pattern): plant an open transaction with an older `snapshot_id` and a
  committed newer transaction — sweep leaves it unmaterialized;
  terminal the open — sweep materializes.
- Kafka integration spec proves it end to end: open T1; open and commit
  T2 (T2's data includes an entity create); T2 stays unmaterialized
  while T1 is open; commit T1; both transactions materialize and T2's
  root exists. Uses the established gates/cleanup discipline.
- Spec updated: snapshot isolation becomes a Normative lifecycle
  subsection (visibility rule, `snapshot_id`, watermark gating, leases;
  overlay reads stay Planned); §2.4 worker prose gains the watermark
  gate; version bumped to 0.7.0-draft with a change note. Vocabulary
  doc lifecycle section updated. Uncomfortable-truths reviewed for
  coherence (UT-7's "guaranteed projection" now reads "guaranteed once
  the watermark passes; leases bound the wait").

OUT_OF_SCOPE:
- Overlay reads / per-reader snapshot filtering on the HTTP read
  surface (ratified separately as plan task F).
- Point-in-time reads from `hst`.
- Read-your-own-open-transaction support.
- Multi-partition ordering concerns.
- Retiring the legacy HTTP transaction path.

VERIFICATION:
- `./gradlew test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules — the
  importer's serial open/commit pattern must remain unblocked)
- `git diff --check`
