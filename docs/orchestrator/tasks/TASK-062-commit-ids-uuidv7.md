# TASK-062 - Commit IDs become UUIDv7, comparable with transaction IDs

ID: TASK-062
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_IMPLEMENTATION
OWNER: unassigned
SOURCE_TASK:
  - TASK-003
  - TASK-061
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-062-commit-ids-uuidv7.md
  - docs/jdtp-specification.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the director ruling of 2026-07-06 (DIRECTION.md, Snapshot
Isolation And Orderable Commit IDs): commit IDs must be orderable and
comparable with transaction IDs, generated the same way — a fresh
UUIDv7. This is the standalone first slice; the snapshot_id/watermark/
lease machinery follows in TASK-063.

BACKGROUND (from the drift investigation, 2026-07-06):
The manifesto's model (docs/Jade-Tipi.md) — a commit ID is "just another
new ID issued by the transaction ID generator", with commit order
preserved in ASCII sort — was honored by the legacy HTTP path
(`TransactionService` extracted the orderable `<epoch>~<seq>` tail of
`IdGenerator.nextId()` into a stored `commit_seq`). TASK-003 required an
"orderable commit_id" for the Kafka path and simultaneously declared the
`TransactionController`/`TransactionService`/`IdGenerator` trio legacy;
the implementation nevertheless stored raw `IdGenerator.nextId()` output
(`<16 random letters>~<epoch ms>~<seq>`) as `commit_id` and dropped the
seq extraction — opaque and backend-generated, but not orderable. Later
docs copied the orderability claim forward unverified. Nothing sorts by
commit_id today, so the fix is a clean swap.

DESIGN (ratified 2026-07-06):
- `commitHeader` in `TransactionMessagePersistenceService` mints
  `commit_id = UuidCreator.getTimeOrderedEpoch().toString()` — a bare
  UUIDv7, the same call `Transaction.newInstance` uses for transaction
  UUIDs (`uuid-creator` is already on the classpath via the dto
  library's `api libs.uuid.creator`).
- Comparability contract: the canonical comparison is between UUIDv7
  values — a transaction ID's leading UUID segment against a commit ID.
  (Plain string comparison of a full transaction ID against a bare
  commit ID also happens to decide within the first 36 characters, but
  the spec should state the segment-vs-value rule, not the accident.)
- `IdGenerator` leaves the Kafka path: remove the constructor dependency
  from `TransactionMessagePersistenceService` (the commit path was its
  only use there). The `IdGeneratorConfig` bean and the legacy
  HTTP `TransactionController`/`TransactionService` remain untouched and
  quarantined per TASK-003; their retirement is a separate future task.
- No data migration: the committed-visibility gate only requires a
  non-blank `commit_id`, so legacy-format values in existing dev
  databases stay readable; no code may assume the new format for old
  rows.

ACCEPTANCE_CRITERIA:
- Commit stamps a `commit_id` that parses as a UUID with version 7, and
  the persistence service no longer depends on `IdGenerator` (the
  'commit assigns commit_id from IdGenerator' feature is rewritten to
  assert the new shape; constructor ripples fixed across specs).
- Redelivered commits still return COMMIT_DUPLICATE without minting a
  fresh ID (existing guard unchanged).
- Spec updated to make the orderability prose true and normative:
  §2.4 commit row and lifecycle prose state that `commit_id` is a
  backend-minted UUIDv7, orderable and comparable with transaction
  UUIDs (segment-vs-value comparison rule); the 0.6.0 change-note flag
  is answered by a 0.6.1 change note recording the ruling; version
  bumped to 0.6.1-draft.
- UT-10's "not lexicographically orderable" note updated to record the
  resolution (ruled 2026-07-06, fixed by this task).
- Vocabulary doc mentions of the commit_id form updated to match.

OUT_OF_SCOPE:
- `snapshot_id`, the materialization watermark, and transaction leases
  (TASK-063).
- Deleting or refactoring the legacy `TransactionController`,
  `TransactionService`, or the `jade-tipi-id` library.
- Any read-path visibility filtering.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`
