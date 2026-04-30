# Director Directives

SIGNAL: PROCEED_TO_IMPLEMENTATION

## Active Focus

Begin the backend Kafka-first ingestion path. `TASK-003` is approved for implementation: the Spring Boot backend should consume canonical Jade-Tipi `Message` records from Kafka and persist them into MongoDB's `txn` collection as the durable transaction write-ahead log.

## Active Task

- `TASK-003`: Persist Kafka transaction messages to txn
- Owner: `claude-1`
- Current status: `READY_FOR_IMPLEMENTATION`

## Scope Expansion

For `TASK-003`, `claude-1` may inspect and propose changes within:

- `jade-tipi/build.gradle`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/`
- `jade-tipi/src/main/resources/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`
- `docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`

Implement the smallest backend path that validates and persists Kafka messages to `txn`. Keep changes inside the scope expansion above and record the implementation outcome in the task report.

## TASK-003 Director Decisions

- Use `spring-kafka` for this first implementation. Keep the persistence service Kafka-free and HTTP-free.
- Default the Kafka topic pattern to include both `jdtp-txn-.*` and the current local topic `jdtp_cli_kli`; do not edit `docker/docker-compose.yml` in this task.
- Use `IdGenerator.nextId()` only for `commit_id`; transaction IDs must come from `Message.txn`.
- Acknowledge and log malformed or schema-invalid messages. Do not acknowledge persistence failures, conflicting duplicates, or `txn/commit` before `txn/open`.
- Treat `txn/rollback` as an explicit no-op result for now; do not implement rollback semantics.
- Update the existing `jade-tipi/src/test/resources/application-test.yml` to disable Kafka listener startup in tests.
- Defer the optional Kafka integration test unless it can use or create a topic reliably with the documented Docker setup. Unit tests for the persistence service and listener are enough for this turn.

## Known Baseline

- `TASK-002` is accepted. The canonical Kafka message envelope now has a first-class `collection` field and examples for transaction, property, type, entity, and property-assignment messages.
- Kafka-first remains the project direction. HTTP submission should later be rebuilt as a thin adapter over the same transaction persistence service.
- The MongoDB `txn` collection is intended to be the durable write-ahead log. A commit is authoritative when the transaction header receives a backend-generated `commit_id`; child message stamping can be deferred.
- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- Direct director verification later exposed a narrow Spock block-label compile issue in `UnitSpec` (`expect` after `when`). If `TASK-002` DTO verification is blocked by that existing issue, fix only that narrow test-harness problem and report it separately.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
