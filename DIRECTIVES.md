# Director Directives

SIGNAL: PROCEED_TO_IMPLEMENTATION

## Active Focus

Implement the Kafka-first transaction message vocabulary for property/type/entity operations. The pre-work plan for `TASK-002` has been accepted; proceed with the bounded DTO/schema/example/test/CLI changes only.

## Active Task

- `TASK-002`: Define Kafka transaction message vocabulary
- Owner: `claude-1`
- Current status: `READY_FOR_IMPLEMENTATION`

## Scope Expansion

For `TASK-002`, `claude-1` may inspect and propose changes within:

- `docs/architecture/kafka-transaction-message-vocabulary.md`
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/`
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
- `libraries/jade-tipi-dto/src/main/resources/example/message/`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/`
- `clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/`
- `clients/kafka-kli/src/test/groovy/org/jadetipi/kafka/cli/`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/UnitSpec.groovy`

Implementation is authorized for `TASK-002` within the task-owned paths only.

## Known Baseline

- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- Direct director verification later exposed a narrow Spock block-label compile issue in `UnitSpec` (`expect` after `when`). If `TASK-002` DTO verification is blocked by that existing issue, fix only that narrow test-harness problem and report it separately.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
