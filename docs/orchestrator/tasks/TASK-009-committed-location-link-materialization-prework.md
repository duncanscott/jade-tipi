# TASK-009 - Plan committed location/link materialization

ID: TASK-009
TYPE: implementation
STATUS: READY_FOR_REVIEW
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Prepare the smallest backend materialization unit that can copy committed
transaction messages for the accepted location/link vocabulary into their
long-term MongoDB collections, so `loc`, `typ` link-type declarations, and
`lnk` relationship records can exist outside the `txn` write-ahead log before
reader/query APIs are designed.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, the canonical
  examples `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json`,
  `CommittedTransactionReadService`, `TransactionMessagePersistenceService`,
  Mongo collection/initializer code, and existing backend service/test patterns.
- Pre-work proposes a narrow materialization boundary, including whether the
  first implementation should be a Kafka-free service over committed snapshots,
  a commit-time hook, or another minimal integration point already present in
  the codebase.
- Pre-work proposes the first supported message set. The default target is
  committed `create` messages for `loc`, `typ` records with
  `data.kind: "link_type"`, and `lnk` records matching the TASK-008
  vocabulary. Broader generic materialization is allowed only if inspection
  shows it is simpler and does not expand behavior risk.
- Pre-work proposes the materialized document shape, idempotency behavior,
  ordering expectations, missing/duplicate/conflicting `data.id` behavior, and
  how committed visibility remains tied to the accepted `txn` header gate.
- Pre-work proposes focused unit/integration assertions and the exact Gradle
  verification commands for the chosen boundary.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not implement semantic reference validation for `lnk.type_id`, `left`,
  `right`, or `allowed_*_collections` in pre-work or implementation unless the
  director explicitly approves a later task for it.
- Do not add plate/well read APIs, "what is in this plate?" queries, "where is
  this sample?" queries, controllers, HTTP submission rebuilds, or UI work.
- Do not add `parent_location_id` to `loc` records.
- Do not change Kafka topic configuration, listener deserialization semantics,
  the `txn` write-ahead log record shape, committed snapshot response shape,
  security policy, Docker Compose, or build configuration.
- Do not implement update/delete replay, backfill jobs, background workers,
  multi-transaction conflict resolution, or authorization/scoping policy in this
  task unless pre-work identifies a narrow correctness prerequisite and the
  director explicitly approves it.

DESIGN_NOTES:
- `TASK-007` established `loc` as a first-class collection and startup-created
  MongoDB collection.
- `TASK-008` established canonical `contents` examples: a `typ` link-type
  declaration and a concrete `lnk` relationship using plate-well position as an
  instance property.
- `DIRECTION.md` says containment parentage is canonical in `lnk`, not on
  `loc`, and that query readers should eventually resolve plate contents by
  looking up `contents` links.
- A materializer should preserve the accepted committed-visibility rule:
  records become eligible only when the `txn` header is committed with a
  non-blank backend `commit_id`.

DEPENDENCIES:
- `TASK-005` is accepted and provides committed snapshot reads over `txn`.
- `TASK-006` is accepted and exposes the committed snapshot HTTP adapter.
- `TASK-007` is accepted and adds `loc`.
- `TASK-008` is accepted and adds canonical `contents` link vocabulary
  examples.

LATEST_REPORT:
Implementation done on 2026-05-01 against the director's accepted pre-work
directives. Added a Kafka-free, HTTP-free `CommittedTransactionMaterializer`
service that consumes a `CommittedTransactionSnapshot` (or a `txnId` resolved
through `CommittedTransactionReadService.findCommitted`) and projects supported
committed `create` messages — `loc`, `typ` with `data.kind == "link_type"`, and
`lnk` — into the matching long-term collection. Other collections, other
actions, and bare entity-type `typ` records are counted as `skippedUnsupported`
and not materialized. A small `MaterializeResult` POGO carries
`materialized`, `duplicateMatching`, `conflictingDuplicate`,
`skippedUnsupported`, and `skippedInvalid` counts.

Materialized documents set Mongo `_id` to `data.id`, copy the committed `data`
payload verbatim (the original `id` field is retained), and add a reserved
`_jt_provenance` sub-document with `txn_id`, `commit_id`, `msg_uuid`,
`committed_at`, and `materialized_at`. Duplicate `_id` writes with an identical
payload (after stripping `_jt_provenance` from both sides) increment
`duplicateMatching` without overwrite; differing payloads increment
`conflictingDuplicate`, log at ERROR, and do not overwrite, so a single
conflict does not block later messages in the same snapshot. Missing or blank
`data.id` is logged at ERROR and counted as `skippedInvalid`; ids are never
synthesized.

`TransactionMessagePersistenceService.commitHeader` gained a single
`materializeQuietly(txnId)` post-commit step that runs after the successful
header `updateFirst` and also on the `COMMIT_DUPLICATE` re-delivery branch (so
a retry can fill a projection gap). Materializer errors are logged at WARN and
swallowed; the outward `PersistResult` is unchanged (`COMMITTED` and
`COMMIT_DUPLICATE` respectively). The constructor now takes the materializer
as a third dependency. The `txn` write-ahead log shape, listener wiring,
Kafka topic configuration, schema, examples, DTOs, build files, and Docker
Compose are all unchanged.

Tests added under `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`:
- `CommittedTransactionMaterializerSpec` (19 features) — pure Spock with
  `Mock(ReactiveMongoTemplate)` and `Mock(CommittedTransactionReadService)`.
  Covers `loc`, link-type `typ`, and `lnk` materialization with provenance
  assertions; skips unsupported collections (`ppy`, `ent`); skips
  unsupported actions (update/delete); skips entity-type `typ` records;
  identical-payload duplicate (idempotent success); differing-payload
  duplicate (counted, not overwritten); conflict does not block later
  messages; missing/blank `data.id` skipped; mixed-message snapshot ordering
  (asserts insert order matches snapshot order); `materialize(txnId)`
  delegates to the read service and returns `Mono.empty()` when not visible;
  null snapshot returns `Mono.empty()`; blank `txnId` rejected.
- `TransactionMessagePersistenceServiceSpec` extended with four new features
  pinning the post-commit hook: materializer invoked exactly once on first
  commit, also invoked on commit re-delivery, and that materializer failure
  is swallowed on both paths so the outward `PersistResult` is preserved.

Documentation updated:
- `docs/architecture/kafka-transaction-message-vocabulary.md` gained a short
  "Committed Materialization Of Locations And Links" section describing the
  post-commit projection, the supported message set, the provenance shape,
  the duplicate/conflict/missing-id behavior, and the standing
  semantic-validation-deferred caveat.

Verification (Docker stack confirmed up via `docker compose -f
docker/docker-compose.yml ps`: `jade-tipi-mongo`, `jade-tipi-kafka`, and
`jade-tipi-keycloak` all healthy):
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
  — BUILD SUCCESSFUL (`tests=19, failures=0, errors=0`).
- `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`
  — BUILD SUCCESSFUL (`tests=15, failures=0, errors=0`; was 11, +4 new
  post-commit features).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 79 unit tests across
  `JadetipiApplicationTests` (1), `TransactionMessageListenerSpec` (4),
  `DocumentServiceMongoDbImplSpec` (9),
  `CommittedTransactionMaterializerSpec` (19),
  `CommittedTransactionReadServiceSpec` (12),
  `CommittedTransactionReadControllerSpec` (5),
  `TransactionMessagePersistenceServiceSpec` (15),
  `TransactionServiceSpec` (12), and `MongoDbInitializerSpec` (2). All green;
  `failures=0, errors=0`.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL
  (no integration spec was added in this task).
