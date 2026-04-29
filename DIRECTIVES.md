# Director Directives

SIGNAL: HUMAN_REQUIRED

## Active Focus

TASK-001 restored the Jade Tipi DTO units test baseline. Await human direction before resuming larger Kafka transaction work.

## Active Task

- `TASK-001`: Restore DTO units test baseline
- Owner: `codex-1`
- Current status: `ACCEPTED`

## Scope Expansion

For `TASK-001`, `codex-1` may inspect and propose changes within:

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/collections/`
- `libraries/jade-tipi-dto/src/main/resources/schema/`
- `libraries/jade-tipi-dto/src/main/resources/units/`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/`

No further TASK-001 implementation should be routed. The next project direction should come from the human before opening follow-up Kafka transaction work.

## Known Baseline

- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
