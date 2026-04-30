# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

Continue the backend Kafka-first path by adding the first committed read-side layer over the accepted Kafka-to-Mongo transaction write-ahead log. `TASK-004` is accepted; `TASK-005` is ready for pre-work only.

## Active Task

- `TASK-005`: Add committed transaction snapshot read layer
- Owner: `claude-1`
- Current status: `READY_FOR_PREWORK`

## Scope Expansion

For `TASK-005`, `claude-1` may inspect and propose changes within:

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`
- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
- `docs/architecture/kafka-transaction-message-vocabulary.md`

Pre-work only. `claude-1` should inspect the current backend read/write services and tests, then record the smallest implementation plan in `docs/agents/claude-1-next-step.md`. Do not implement until the director moves `TASK-005` to `READY_FOR_IMPLEMENTATION`.

## TASK-005 Director Decisions

- Start from a Kafka-free and HTTP-free read service over the `txn` collection. Add or change a controller only if pre-work shows a minimal API surface is needed for useful verification.
- The transaction header's `commit_id` remains the authoritative committed-visibility marker. Child message stamping is still not required.
- Preserve the current `txn` write-ahead log shape from `TASK-003`; do not redesign the message envelope or persistence record shape.
- Do not materialize to `ent`, `ppy`, `typ`, `lnk`, or other long-term collections in `TASK-005`.
- If Docker or local Gradle tooling is unavailable during verification, report the exact documented setup command rather than treating it as a product blocker.

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
