# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

Begin the backend Kafka-first ingestion path. `TASK-003` should plan how the Spring Boot backend will consume canonical Jade-Tipi `Message` records from Kafka and persist them into MongoDB's `txn` collection as the durable transaction write-ahead log.

## Active Task

- `TASK-003`: Persist Kafka transaction messages to txn
- Owner: `claude-1`
- Current status: `READY_FOR_PREWORK`

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

The pre-work response should propose the smallest implementation plan that validates and persists Kafka messages to `txn`. Do not begin implementation until the director moves `TASK-003` to `READY_FOR_IMPLEMENTATION`.

## Known Baseline

- `TASK-002` is accepted. The canonical Kafka message envelope now has a first-class `collection` field and examples for transaction, property, type, entity, and property-assignment messages.
- Kafka-first remains the project direction. HTTP submission should later be rebuilt as a thin adapter over the same transaction persistence service.
- The MongoDB `txn` collection is intended to be the durable write-ahead log. A commit is authoritative when the transaction header receives a backend-generated `commit_id`; child message stamping can be deferred.
- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- Direct director verification later exposed a narrow Spock block-label compile issue in `UnitSpec` (`expect` after `when`). If `TASK-002` DTO verification is blocked by that existing issue, fix only that narrow test-harness problem and report it separately.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
