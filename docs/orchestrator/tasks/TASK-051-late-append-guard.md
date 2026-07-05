# TASK-051 - Late-append guard (UT-6)

ID: TASK-051
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-050
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-051-late-append-guard.md
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
Resolve UT-6's integrity failure: a data message appended after a
transaction reaches a terminal state must never materialize. Today a
post-commit append sits inert until a commit re-delivery re-runs
materialization, at which point it suddenly materializes — late-effect
semantics that depend on transport redelivery behavior.

DESIGN:
- Follows TASK-050's shape: terminal states are now real; appends must
  respect them. On append, the header is read first; if it exists in a
  terminal state (`committed` or `rolled_back`), the row is still stored
  — JDTP never discards submitted facts — but flagged `late_append: true`
  and the persist outcome is the new `PersistResult.APPENDED_LATE`
  (logged as a warning).
- The committed-snapshot reader excludes `late_append` rows from
  `findMessagesForTxn`, mirroring the committed-visibility gate: the
  snapshot is *the messages that were part of the transaction when it
  committed*. Late rows therefore never reach the materializer, on first
  materialization or on any redelivery. Quarantine-not-refusal matches
  the ratified lifecycle language (skipped/conflicting payloads are
  retained, never silently dropped).
- Appends **before open** (no header yet) remain allowed and unflagged:
  they are deterministic — they materialize at the explicit commit like
  any other row — and single-partition ordering makes them a producer
  anomaly, not an integrity hole. Orphan rows for never-opened
  transactions stay inert; full staging/cleanup remains plan task F.
- Duplicate semantics unchanged: payload comparison ignores the flag, so
  a re-delivered pre-commit row that arrives again post-commit resolves
  as the usual matching duplicate.

ACCEPTANCE_CRITERIA:
- Unit features cover: append to an open transaction (unflagged, header
  checked); append with no header (unflagged, allowed); append after
  commit and after rollback (stored with `late_append: true`,
  `APPENDED_LATE`); the committed-snapshot message query excludes
  `late_append` rows.
- A Kafka integration spec proves the fix end to end: open → loc create →
  commit → materialized; then a **late** loc create followed by a commit
  re-delivery, then a sentinel transaction (proving both were consumed);
  the late row is stored flagged, its root never materializes, and the
  original root is unaffected.
- UT-6 is resolved in place in `docs/uncomfortable-truths.md`, with the
  explicit residuals recorded (before-open appends allowed; staging and
  cleanup remain plan F).
- Specification §2.4 documents the guard (version bump); the §4 gaps
  sentence drops unguarded late appends; vocabulary doc's Transaction
  Records section describes the flag and snapshot exclusion.

OUT_OF_SCOPE:
- No refusal of before-open appends and no orphan-row cleanup (plan
  task F staging).
- No `msg` split, `message_count`, or per-message `apply_state` (plan
  task F).
- No re-drive of committed-but-unmaterialized transactions (UT-7).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- `TransactionMessagePersistenceService.appendDataMessage` now reads the
  header first (`isTerminalHeader`: missing header and `open` both
  resolve false); a terminal-state append stores the row with
  `late_append: true`, logs a warning, and returns the new
  `PersistResult.APPENDED_LATE`. Open and before-open appends are
  byte-identical to before (no flag field written). Duplicate handling
  is unchanged — payload comparison ignores the flag.
- `CommittedTransactionReadService.findMessagesForTxn` excludes flagged
  rows (`late_append ≠ true`) with a javadoc stating the semantic: the
  snapshot is the set of messages that were part of the transaction when
  it committed. Late rows therefore never reach the materializer on
  first materialization or any re-delivery; the materializer itself is
  untouched.
- Unit specs: three existing append features gained the now-required
  header stub (the first one previously asserted `0 * findById`, which
  the guard inverts); new features cover before-open append (unflagged),
  open append (asserts no flag), terminal append (`where`-driven over
  committed/rolled_back, asserting the stored flag and APPENDED_LATE),
  and the snapshot query's `$ne` exclusion (captured-query assertion).
- Integration: `LateAppendGuardKafkaIntegrationSpec` — open → loc create
  → commit → root materialized; then a late loc create + a **commit
  re-delivery** (the exact mechanism that used to surprise-materialize
  late rows) + the TASK-050 sentinel-transaction technique. Assertions:
  the late row is stored with `late_append: true`, its root never
  materialized, and the original root and header are unaffected. The
  live run's output contains the guard's warning line.
- Docs: UT-6 flipped to RESOLVED in place with explicit residuals
  (before-open appends allowed and deterministic; staging,
  `message_count`, `apply_state`, watermark, and cleanup remain plan
  tasks D/F with UT-7). Spec bumped to 0.3.3-draft: §2.4 documents the
  guard and drops "no state guard on append"; §4's gap sentence swaps
  late appends for the UT-7 re-drive gap. Vocabulary doc's Transaction
  Records message-record bullet describes the flag and snapshot
  exclusion.
- Verification results: `:jade-tipi:test` and full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m46s) with the new spec
  running live (1 test, 0 failures) and the warn line present in its
  output; `git diff --check` clean.
