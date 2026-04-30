# Director Directives

SIGNAL: PROCEED_TO_IMPLEMENTATION

## Active Focus

Continue the backend Kafka-first path by adding the first committed read-side layer over the accepted Kafka-to-Mongo transaction write-ahead log. `TASK-004` is accepted; `TASK-005` remains ready for implementation after director review found one blocking read-conversion issue.

## Active Task

- `TASK-005`: Add committed transaction snapshot read layer
- Owner: `claude-1`
- Current status: `READY_FOR_IMPLEMENTATION`

## Scope Expansion

For `TASK-005`, `claude-1` may inspect and propose changes within:

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`
- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
- `docs/architecture/kafka-transaction-message-vocabulary.md`

Implementation remains approved. `claude-1` should fix the submitted committed transaction snapshot read service described in the task file and pre-work review.

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

- Implementation review failed on 2026-04-30; `TASK-005` is back to `READY_FOR_IMPLEMENTATION`.
- Blocking issue: `CommittedTransactionReadService` casts raw Mongo `Map` timestamp fields directly to `Instant`. Real BSON date values read into raw maps may arrive as `java.util.Date`, so committed snapshots can fail at runtime even though the mocked unit tests pass with `Instant` fixtures.
- Fix by coercing supported raw date representations to `Instant` for header `opened_at`, header `committed_at`, and message `received_at`, or by introducing typed read projections that let Spring perform the conversion. Preserve null tolerance.
- Add a test that uses `java.util.Date` timestamp values for the header and message row, then run `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`.

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
