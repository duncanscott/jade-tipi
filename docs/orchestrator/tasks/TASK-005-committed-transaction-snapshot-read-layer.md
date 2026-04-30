# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-006
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
Director implementation review on 2026-04-30:
- Accepted claude-1 implementation commit `788ef68`.
- Acceptance criteria are satisfied. The backend now has a Kafka-free and HTTP-free `CommittedTransactionReadService` over the accepted `txn` write-ahead log. It returns a committed snapshot only when the transaction header is WAL-shaped (`record_type=transaction`), has `state=committed`, and has a non-blank backend-generated `commit_id`; missing, open, missing-commit, blank-commit, and older `TransactionService`-shape rows are hidden as empty/not-found results.
- The read result includes the transaction header plus staged message records with `collection`, `action`, `data`, `msg_uuid`, `received_at`, and service-local Kafka provenance. No materialization into long-term collections was added.
- The previous director-blocking timestamp conversion issue is resolved. Header `opened_at`, header `committed_at`, and message `received_at` now coerce `Instant`, `java.util.Date`, and `null`; unexpected timestamp types fail loudly.
- Required assertions are present in `CommittedTransactionReadServiceSpec`: committed header + messages, open/uncommitted hidden, missing and blank `commit_id` hidden, missing header empty, older shape ignored, blank/null/whitespace `txnId` rejected, null payload fields tolerated, `_id` ASC query sort asserted, and `java.util.Date` timestamp rows coerced to `Instant`.
- Scope check passed. The latest rework changed only `docs/agents/claude-1-changes.md`, this task file, `CommittedTransactionReadService.groovy`, and `CommittedTransactionReadServiceSpec.groovy`, all within the active task expansion plus the developer report path. The full `TASK-005` implementation stayed within `service/`, `src/test/`, and this task file; no controller, DTO-package, integration-test, build, resource, write-side persistence, Kafka, or envelope semantics changes were made.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` failed opening `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` (`Operation not permitted`), `gradle --version` failed loading the native-platform dylib, and `docker compose -f docker/docker-compose.yml ps` could not access the Docker socket. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` passing with `tests=12, skipped=0, failures=0, errors=0`, and `./gradlew :jade-tipi:test` passing with 49 unit tests.
- Follow-up: `TASK-006` was created for pre-work on a thin HTTP read adapter over the accepted service.
