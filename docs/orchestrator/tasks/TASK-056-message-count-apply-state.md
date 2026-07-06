# TASK-056 - message_count and per-message apply_state

ID: TASK-056
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-052
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-056-message-count-apply-state.md
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
Implement the next slice of the ratified lifecycle (plan task F; spec
§2.4 Planned): commit records `message_count` on the header, and the
projection stamps each message row's terminal `apply_state` — making a
committed transaction's application auditable row by row. No new director
decisions: this is ratified direction, and it builds directly on the
TASK-052 worker. The `msg` staging split, cleanup, and overlay reads
remain Planned.

DESIGN:
- **message_count**: when the commit durably marks the header, it also
  records the count of the transaction's non-late message rows
  (`record_type: message`, `late_append ≠ true`) — the size of the
  committed set, fixed at commit time. Strict partition ordering means
  every pre-commit row is already appended when the commit is processed;
  the TASK-051 guard flags anything later.
- **apply_state**: the materializer already resolves exactly one terminal
  outcome per message (each path increments exactly one MaterializeResult
  counter, and processing is strictly sequential), so the projection loop
  diffs the counters around each message and stamps the row:
  `applied`, `duplicate`, `conflict`, `skipped_unsupported`,
  `skipped_invalid`, `skipped_missing_target`, or
  `skipped_unregistered_property`, plus `apply_state_at`. The stamp is
  guarded on `apply_state` being absent — the FIRST terminal outcome
  wins, so an idempotent re-run (whose repeat naturally resolves
  `duplicate`) cannot overwrite the original truth. A failed stamp fails
  the pass: the header stays unwatermarked and the sweep retries.
- **Integrity check**: the committed snapshot carries the header's
  `message_count`; the materializer logs a warning when it differs from
  the snapshot's message list size (they must agree by construction —
  a mismatch signals tampering or a bug). Warn, never block.
- No handler changes; no `msg` split; the `materialized_at` watermark
  semantics are unchanged.

ACCEPTANCE_CRITERIA:
- First commit sets `message_count` on the header (late rows excluded
  from the count); duplicate/refused commits never touch it.
- Every message processed by a projection pass carries a terminal
  `apply_state` + `apply_state_at` on its WAL row, first-outcome-wins;
  rows the snapshot excludes (late appends) are never stamped.
- The snapshot record carries `messageCount`; a size mismatch logs a
  warning without blocking.
- Unit coverage: the commit-path count query and `$set`; the stamp shape
  (guard + fields) across at least the applied/duplicate/skipped
  outcomes; snapshot mapping.
- Integration: the sweep spec proves planted rows get
  `apply_state: applied` while the late row stays unstamped; a Kafka
  spec proves `message_count` lands via the real commit path.
- Spec §2.4 flips message_count/apply_state from Planned to Normative
  (version bump); vocabulary doc's Transaction Records section documents
  the new fields.

OUT_OF_SCOPE:
- No `msg` transient staging, no cleanup, no overlay reads,
  no read-your-own-open-transaction (remaining plan-F work).
- No separate `applied` watermark distinct from `materialized_at`: with
  synchronous per-pass stamping they coincide; the distinction becomes
  meaningful only with staged asynchronous application.
- No apply_state read API surface.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- Commit path: `commitHeader` counts the transaction's non-late message
  rows and sets `message_count` in the same committed update. Strict
  partition ordering plus the TASK-051 guard make that set immutable at
  commit time. Duplicate/refused commits never touch it.
- Materializer: `materialize(snapshot)` diffs `MaterializeResult`
  counters around each message (every terminal path increments exactly
  one counter; processing is strictly sequential via concatMap) and
  stamps the WAL row: `apply_state` ∈ {applied, duplicate, conflict,
  skipped_unsupported, skipped_invalid, skipped_missing_target,
  skipped_unregistered_property} plus `apply_state_at`. The update is
  guarded on `apply_state` absent — first terminal outcome wins, so an
  idempotent re-run (whose repeat naturally resolves duplicate) cannot
  overwrite the original truth. A failed stamp fails the pass (header
  stays unwatermarked; sweep retries). No handler changes:
  `MaterializeResult.counters()` exposes the fixed-order list
  index-aligned with `APPLY_STATES`.
- Snapshot: `CommittedTransactionSnapshot` gains nullable
  `messageCount` (mapped by the read service); the materializer warns on
  a size mismatch without blocking.
- Unit coverage: new `CommittedTransactionMaterializerApplyStateSpec`
  (one pass over four messages resolving four different outcomes —
  stamp order, row-id targeting, absent-guard, timestamps; plus the
  mismatch-warns-not-blocks feature); the persistence commit feature
  asserts the count query shape (record_type/txn_id/late_append≠true)
  and the `message_count` `$set`; the read-service spec pins the
  snapshot mapping. Test ripple from stamping in-band: the five
  materializer-driving specs gained a `txn`-scoped stamp stub in setup,
  broad `_ as String` updateFirst matchers were narrowed to their
  intended collections, and `0 * updateFirst` assertions became
  `0 * updateFirst(_, _, !'txn')`.
- Integration: the sweep spec plants `message_count: 1` and asserts the
  projected row is stamped `applied` while the planted `late_append` row
  is never stamped; the late-append Kafka spec asserts the real commit
  path fixes `message_count == 1` and stamps the applied row — both ran
  live in the full suite.
- Docs: spec bumped to 0.5.0-draft — §2.4's Normative paragraph gains
  message_count and apply_state (first-outcome-wins, mismatch warning)
  and the Planned paragraph shrinks to the remaining staging/cleanup/
  overlay work; vocabulary doc's Transaction Records section documents
  both fields.
- Verification results: `:jade-tipi:test` and full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m57s);
  `git diff --check` clean.
