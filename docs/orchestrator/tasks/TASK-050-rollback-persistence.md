# TASK-050 - Rollback persistence (UT-2)

ID: TASK-050
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-047
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-050-rollback-persistence.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Resolve UT-2: `txn + rollback` becomes a durable, terminal header state
with audit data, and the commit/rollback pair becomes mutually exclusive —
a commit arriving after a rollback (replay, bug, or malice) must never
commit and materialize the transaction.

DESIGN:
- The transaction header gains a third terminal state: `open` →
  `committed` **or** `open` → `rolled_back`. Rollback durably sets
  `state: "rolled_back"`, `rolled_back_at`, and `rollback_data` (the
  rollback message's `data`, kept as the audit fact).
- Refusals, never overwrites: commit-after-rollback is refused
  (`COMMIT_REFUSED_ROLLED_BACK` — no commit_id, no materialization);
  rollback-after-commit is refused (`ROLLBACK_REFUSED_COMMITTED`).
  Rollback re-delivery is an idempotent duplicate. Rollback before open
  errors like commit before open.
- The header `state` transitions are guarded in the update query
  (`state: "open"` condition) as write-time defense in depth on top of
  the read-then-branch flow.
- Appended message rows are untouched: they remain stored for a
  rolled-back transaction (audit). Late-append guards stay UT-6; the
  committed-visibility gate already excludes non-committed headers, so
  the materializer needs no change.
- `PersistResult.ROLLBACK_NOT_PERSISTED` retires in favor of
  `ROLLED_BACK` / `ROLLBACK_DUPLICATE` / `ROLLBACK_REFUSED_COMMITTED` /
  `COMMIT_REFUSED_ROLLED_BACK`.

ACCEPTANCE_CRITERIA:
- Unit features cover: rollback on an open transaction (guarded update,
  audit fields, no commit-id generation); rollback re-delivery
  (idempotent, no write); rollback after commit (refused, no write);
  commit after rollback (refused — no id generation, no update, no
  materializer call); rollback before open (IllegalStateException).
- A Kafka integration spec proves the integrity fix end to end on one
  partition: open → data message → rollback → **late commit** → a
  sentinel second transaction committing afterwards (proving the late
  commit was consumed) → the rolled-back header still has
  `state: "rolled_back"`, no `commit_id`, the data message's root was
  never materialized, and the appended message row remains stored as
  audit.
- UT-2 is resolved in place in `docs/uncomfortable-truths.md` (status
  flipped, resolution recorded, entry never deleted).
- Specification: the §2.3 `txn + rollback` row and §2.4 lifecycle text
  state the durable rolled_back semantics; the §4 gaps sentence and §5
  open-decisions list drop rollback; version bump.
- Vocabulary doc Transaction Records section documents the three header
  states and the refusal semantics.

OUT_OF_SCOPE:
- No guard on late data-message appends to rolled-back or committed
  transactions (UT-6).
- No staged-message cleanup or `msg` split (plan task F); rolled-back
  transactions keep their appended rows.
- No re-drive/sweep of committed-but-unmaterialized transactions (UT-7).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- `TransactionMessagePersistenceService`: new `rollbackHeader` mirrors
  `commitHeader`'s read-then-branch shape — missing header errors
  (IllegalStateException, like commit-before-open), `rolled_back` state
  is an idempotent `ROLLBACK_DUPLICATE`, `committed` state refuses with
  `ROLLBACK_REFUSED_COMMITTED`, and the open path durably sets
  `state/rolled_back_at/rollback_data`. `commitHeader` gains the
  mirror-image branch: `rolled_back` state refuses with
  `COMMIT_REFUSED_ROLLED_BACK` — no commit_id generation, no update, no
  materialization. Both terminal transitions now carry a
  `state: "open"` condition on the update query (write-time defense in
  depth on top of the read-then-branch flow; the commit query previously
  matched on `_id` alone). Class javadoc documents the three-state
  lifecycle and audit semantics.
- `PersistResult`: `ROLLBACK_NOT_PERSISTED` retired (it was referenced
  only by the service and its spec); `COMMIT_REFUSED_ROLLED_BACK`,
  `ROLLED_BACK`, `ROLLBACK_DUPLICATE`, `ROLLBACK_REFUSED_COMMITTED`
  added.
- No materializer or read-service change needed: the committed-visibility
  gate (state=committed + non-blank commit_id) already excludes
  rolled-back headers, and refusing the commit means no commit_id ever
  exists. Appended message rows remain stored as audit; new-append
  guards remain UT-6.
- Unit spec: the retired ROLLBACK_NOT_PERSISTED feature replaced by five
  features (durable rollback with guarded query + audit fields;
  idempotent re-delivery; rollback-after-commit refused;
  commit-after-rollback refused with zero materializer interactions;
  rollback-before-open errors); the existing commit feature additionally
  asserts the new state guard in its update query.
- Integration: `RollbackPersistenceKafkaIntegrationSpec` — open → loc
  create → rollback → **late commit** → sentinel second transaction
  (open+commit) on the same single-partition topic. The sentinel's
  commit proves strict ordering consumed the late commit; assertions:
  header still `rolled_back` with `rollback_data`/`rolled_back_at` and
  no `commit_id`/`committed_at`, the loc root never materialized, and
  the appended loc message row remains stored (record_type=message).
  The live run's output contains the service's 'Commit refused for
  rolled-back transaction' log line — the refusal path demonstrably
  fired.
- Docs: UT-2 flipped to RESOLVED in place (ledger gains the RESOLVED
  status definition); spec bumped to 0.3.1-draft — §2.3 rollback row
  states the durable semantics, §2.4 describes the two mutually
  exclusive terminal states and refusals, §4's gap sentence and §5's
  open-decisions list drop rollback; vocabulary doc's Transaction
  Records section documents the header states, refusal semantics, and
  audit retention.
- Verification results: `:jade-tipi:test` BUILD SUCCESSFUL; full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` BUILD SUCCESSFUL
  (1m37s) with the new spec running live (1 test, 0 failures);
  `git diff --check` clean.
