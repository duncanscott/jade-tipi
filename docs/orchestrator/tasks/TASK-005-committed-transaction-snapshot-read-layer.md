# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: READY_FOR_REVIEW
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Add the smallest backend read-side layer over the accepted `txn` write-ahead log so callers can retrieve a committed transaction snapshot without materializing long-term domain collections yet.

ACCEPTANCE_CRITERIA:
- Pre-work inspects the existing `TransactionService`, `TransactionMessagePersistenceService`, controllers, tests, and `docs/architecture/kafka-transaction-message-vocabulary.md` before implementation.
- Pre-work proposes the smallest useful service boundary and return shape for reading one committed transaction from `txn`.
- The implementation must only expose committed visibility when the transaction header has `state=committed` and a backend-generated `commit_id`.
- The read result should include the transaction header and the staged message records for that `txn_id`, preserving enough fields for a later materializer or API layer to know `collection`, `action`, `data`, `msg_uuid`, and Kafka provenance.
- Tests cover at least: committed transaction returns header plus messages, open/uncommitted transaction is not exposed as committed, missing transaction returns a clear empty/not-found result, and message ordering is deterministic.
- Verification includes the narrow `:jade-tipi` unit or integration command selected during pre-work. If local tooling or Docker is unavailable, report the documented setup command instead of treating that as a product blocker.

OUT_OF_SCOPE:
- Do not materialize messages into `ent`, `ppy`, `typ`, `lnk`, or other long-term collections.
- Do not implement semantic reference validation between properties, types, entities, and assignments.
- Do not rebuild the HTTP submission wrapper.
- Do not change Kafka ingestion, topic configuration, or message envelope semantics.
- Do not add authentication or authorization policy beyond matching existing controller/test patterns if a read endpoint is proposed.

DESIGN_NOTES:
- `TASK-003` accepted `txn` as the durable write-ahead log. `TASK-004` accepted Docker-backed integration coverage for Kafka-to-`txn` ingestion.
- The transaction header's `commit_id` is authoritative for committed visibility; child messages may still lack their own commit marker.
- `docs/architecture/kafka-transaction-message-vocabulary.md` notes that later readers and materializers should not infer domain intent from arbitrary payloads because `collection` is stored explicitly on each message record.
- Prefer a Kafka-free and HTTP-free service for the core read logic. Add or adjust a controller only if pre-work shows a minimal API surface is needed for useful verification.

DEPENDENCIES:
- `TASK-004` is accepted and proves the Kafka listener can populate the `txn` collection through the documented local stack.

LATEST_REPORT:
Implementation re-submitted on 2026-04-30 addressing the director review's blocking finding on raw Mongo timestamp coercion. `TASK-005` flipped to `READY_FOR_REVIEW`.

Fix for the blocking finding:

- `CommittedTransactionReadService` no longer casts raw header/message timestamp fields directly with `as Instant`. A new `private static Instant toInstant(Object)` helper accepts `Instant`, `java.util.Date`, and `null`; any other type throws `IllegalStateException` so a future schema change does not silently degrade to `null`. The helper is applied to header `opened_at`, header `committed_at`, and message `received_at`.
- Null tolerance is preserved: passing `null` returns `null` (verified by the existing null-payload feature).
- No write-side or schema change. The writer in `TransactionMessagePersistenceService` continues to set `Instant` values; the read service now tolerates whichever representation the Mongo driver surfaces when the document round-trips through a raw `Map`.

New unit coverage (per directive):

- `CommittedTransactionReadServiceSpec` adds the feature `'java.util.Date timestamps from raw Mongo documents are coerced to Instant'`. The header is seeded with `Date.from(...)` for `opened_at` and `committed_at`, and the message row is seeded with `Date.from(...)` for `received_at`. The spec asserts that `snapshot.openedAt`, `snapshot.committedAt`, and `snapshot.messages[0].receivedAt` are all `instanceof Instant` and equal the original `Instant` values.
- All previously-required features are retained: committed snapshot returns header + messages, open/uncommitted is hidden, missing-`commit_id` partial-write is hidden, blank `commit_id` is hidden, missing header returns empty, older `TransactionService`-shape (no `record_type`) is hidden, blank/null/whitespace `txnId` raises `IllegalArgumentException`, null payload fields tolerated, and the Mongo query is asserted to carry `Sort.by(ASC, '_id')` (the directive-required assertion that the database is doing the sort).

Verification (Docker stack already up: `jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`):

- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` — BUILD SUCCESSFUL. Spec report: `tests=12, skipped=0, failures=0, errors=0` (was 11; +1 new Date-coercion feature).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 49 unit tests across:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (12) — was 11
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)
- If Docker is not up locally, run `docker compose -f docker/docker-compose.yml up -d` (only Mongo is strictly required for the unit suite because `JadetipiApplicationTests.contextLoads` opens a Mongo connection).

Scope:

- Changed only `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`, `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`, this task file, and `docs/agents/claude-1-changes.md`. All inside the implementation scope explicitly expanded by `DIRECTIVES.md` for `TASK-005`.
- No write-side persistence, controller, DTO-package, integration-test, build, or resource files changed. The `txn` write-ahead log shape from `TASK-003` is preserved.
