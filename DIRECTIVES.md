# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

The active bounded unit is `TASK-007`: add `loc` as a first-class Jade-Tipi collection so DTO/schema messages can target locations and the Spring Boot Mongo initializer creates the `loc` collection at application startup.

New product direction is recorded in `DIRECTION.md`: add a first-class `loc` collection for laboratory locations, keep containment relationships canonical in `lnk`, define `contents` as a typed link/class through `typ`, and model plate well coordinates as instance properties on `contents` links unless wells need independent lifecycle.

## Active Task

- `TASK-007 - Add location collection` is READY_FOR_PREWORK and assigned to claude-1.

## Scope Expansion

For pre-work only, claude-1 may inspect and propose changes within the `TASK-007` owned paths. Do not begin implementation until the task is moved to READY_FOR_IMPLEMENTATION.

## TASK-006 Director Review

- `TASK-006` is accepted on 2026-05-01. The backend now exposes `GET /api/transactions/{id}/snapshot` as a thin WebFlux adapter over `CommittedTransactionReadService`.
- The controller delegates committed visibility entirely to `CommittedTransactionReadService.findCommitted(String)`, returns a populated `CommittedTransactionSnapshot` as HTTP 200, maps `Mono.empty()` to HTTP 404 with no body, and relies on the service `Assert.hasText` plus `GlobalExceptionHandler` for blank/whitespace-only IDs.
- Scope check passed. The merge changed only `docs/agents/claude-1-changes.md`, the `TASK-006` task file, `CommittedTransactionReadController.groovy`, and `CommittedTransactionReadControllerSpec.groovy`; code and tests stayed within the task-owned controller/test paths and the only developer-owned report path changed was `docs/agents/claude-1-changes.md`.
- Required controller assertions are present: actual route coverage, 200 JSON serialization with header/message/Kafka provenance fields, message order, 404 empty body, 400 `ErrorResponse` through the real global handler, service delegation, and no direct Mongo collaborator.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'` and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`, `./gradlew :jade-tipi:test`, and `./gradlew :jade-tipi:compileIntegrationTestGroovy` passing.
- No automatic next task was created because the next bounded unit is not singularly obvious. Human direction is needed to choose among materialization, HTTP submission rebuild, API response hardening, and authorization/scoping policy.

## TASK-005 Director Decisions

- Pre-work review passed on 2026-04-30. The latest claude-1 pre-work commit changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Start from a Kafka-free and HTTP-free read service over the `txn` collection. Add or change a controller only if pre-work shows a minimal API surface is needed for useful verification.
- Do not add a controller for `TASK-005`; service-level coverage is sufficient.
- The transaction header's `commit_id` remains the authoritative committed-visibility marker. Child message stamping is still not required.
- Require `record_type=transaction`, `state=committed`, and a non-blank `commit_id`; ignore older `TransactionService`-shape records in `txn`.
- Keep snapshot return classes in the `service` package for now. Use a service-local Kafka provenance value object instead of reusing the write-side `kafka.KafkaSourceMetadata` type.
- Order messages by `_id` ascending. In unit tests, assert the Mongo query's sort rather than relying on mocked result order to prove database sorting.
- After implementation, set `TASK-005` to `READY_FOR_REVIEW`; `IMPLEMENTATION_COMPLETE` is not a valid lifecycle status.
- Preserve the current `txn` write-ahead log shape from `TASK-003`; do not redesign the message envelope or persistence record shape.
- Do not materialize to `ent`, `ppy`, `typ`, `lnk`, or other long-term collections in `TASK-005`.
- If Docker or local Gradle tooling is unavailable during verification, report the exact documented setup command rather than treating it as a product blocker.

## TASK-005 Director Review

- `TASK-005` is accepted on 2026-04-30. The read service now returns committed `txn` snapshots only for WAL-shaped headers with `record_type=transaction`, `state=committed`, and non-blank `commit_id`; it preserves message fields and Kafka provenance, orders message queries by `_id` ASC, and stays Kafka-free and HTTP-free.
- The director's previous blocking timestamp issue is resolved. `CommittedTransactionReadService` now coerces raw `Instant`, `java.util.Date`, and `null` timestamp values for header `opened_at`, header `committed_at`, and message `received_at`; unexpected timestamp types fail loudly instead of silently degrading.
- Scope check passed. The latest claude-1 commit changed only `docs/agents/claude-1-changes.md`, the `TASK-005` task file, `CommittedTransactionReadService.groovy`, and `CommittedTransactionReadServiceSpec.groovy`, all within the active task expansion plus the developer report path. The full `TASK-005` implementation stayed inside service/test/task scope and did not add controllers, DTO-package files, write-side persistence changes, build changes, or resource changes.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`; `gradle --version` failed loading the native-platform dylib; `docker compose -f docker/docker-compose.yml ps` could not access the Docker socket. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` and `./gradlew :jade-tipi:test` passing.

## TASK-004 Director Review

- `TASK-004` is accepted. The backend now has opt-in Docker-backed Kafka integration coverage that publishes canonical open/data/commit messages, consumes them through `TransactionMessageListener`, and asserts the committed `txn` header plus message document and Kafka provenance in MongoDB.
- Scope check passed. The implementation changed only claude-1's report file, the `TASK-004` task file, and the new integration spec under the approved integration-test path.
- Director verification was blocked by local sandbox permissions on the Gradle wrapper lock and Docker socket, not by an observed product failure. In a normal developer shell, use `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:compileIntegrationTestGroovy` and `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`.
- Non-blocking note: the optional duplicate-delivery integration feature can pass before the duplicate record is proven consumed because the document count is already `2`; keep this in mind if idempotency becomes a required integration-level assertion.

## TASK-003 Director Review

- `TASK-003` is accepted. The backend now has a Spring Kafka listener that deserializes canonical `Message` records, validates them, and persists transaction headers/messages into MongoDB's `txn` collection through a Kafka-free/HTTP-free persistence service.
- Director verification passed for `./gradlew :jade-tipi:compileGroovy`; targeted test reruns were blocked by local Gradle wrapper lock-file permissions and Docker socket access, not by an observed product failure. The developer reported `./gradlew :jade-tipi:test` passing with MongoDB started via Docker.

## Known Baseline

- `TASK-002` is accepted. The canonical Kafka message envelope now has a first-class `collection` field and examples for transaction, property, type, entity, and property-assignment messages.
- Kafka-first remains the project direction. HTTP submission should later be rebuilt as a thin adapter over the same transaction persistence service.
- The MongoDB `txn` collection is intended to be the durable write-ahead log. A commit is authoritative when the transaction header receives a backend-generated `commit_id`; child message stamping can be deferred.
- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- Direct director verification later exposed a narrow Spock block-label compile issue in `UnitSpec` (`expect` after `when`). If `TASK-002` DTO verification is blocked by that existing issue, fix only that narrow test-harness problem and report it separately.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
