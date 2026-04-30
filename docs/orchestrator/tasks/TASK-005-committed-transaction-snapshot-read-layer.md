# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
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
Director implementation review failed on 2026-04-30; keep `TASK-005` in `READY_FOR_IMPLEMENTATION`.

Blocking finding:

- `CommittedTransactionReadService` reads persisted `txn` documents as raw `Map` values and casts timestamp fields directly with `as Instant` (`opened_at`, `committed_at`, and message `received_at`). The writer stores `Instant` values through Spring Data Mongo, but when BSON date values are read back into a raw `Map`, they may be `java.util.Date`/driver-native date values rather than `Instant`. In that case a real committed snapshot can fail with `GroovyCastException` before returning, even though the mocked unit tests pass because they inject `Instant` values directly. Fix by coercing supported date representations to `Instant` (while preserving null tolerance), or by reading through typed projection classes that let Spring perform the conversion.

Required follow-up before acceptance:

- Add coverage that exercises non-`Instant` timestamp values representative of raw Mongo `Map` reads, at minimum `java.util.Date` for `opened_at`, `committed_at`, and `received_at`.
- Keep the existing committed-visibility, missing/open transaction, old-shape rejection, and `_id` ascending sort assertions.
- Re-run the selected narrow verification command: `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`. If the full unit suite is run, start the documented local stack first with `docker compose -f docker/docker-compose.yml up -d` because `JadetipiApplicationTests.contextLoads` opens a Mongo connection.

Scope review:

- The merge touched `docs/agents/claude-1-changes.md`, this task file, and new files under `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/` and `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`.
- Those production/test paths are outside the base assignment-owned docs paths listed in `docs/agents/claude-1.md`, but they are inside the implementation scope explicitly expanded by this task file and `DIRECTIVES.md`.
- No controller, DTO, integration-test, build, resource, Kafka, or write-side persistence files were changed.

Director verification notes:

- `git diff --check HEAD^ HEAD` passed.
- Direct local Gradle verification was blocked before project code loaded: the wrapper could not open `/Users/duncanscott/.gradle/wrapper/.../gradle-8.14.3-bin.zip.lck` inside the sandbox; retrying with `GRADLE_USER_HOME=/tmp/jade-tipi-gradle-home` required network access to `services.gradle.org`; local Gradle 9.4.1 failed to create its file-lock socket with `Operation not permitted`. This is local tooling/sandbox friction, not an observed product failure.
- claude-1 reported successful verification in its worktree for `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`, `./gradlew :jade-tipi:test`, and `./gradlew :jade-tipi:compileIntegrationTestGroovy`, but those runs did not cover raw Mongo date value conversion.
