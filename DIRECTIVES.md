# Director Directives

SIGNAL: PROCEED_TO_IMPLEMENTATION

## Active Focus

Continue the backend Kafka-first ingestion path by adding practical integration coverage for the accepted Kafka-to-Mongo transaction write-ahead log. `TASK-004` pre-work has been reviewed and is approved for implementation.

## Active Task

- `TASK-004`: Add Kafka transaction ingest integration coverage
- Owner: `claude-1`
- Current status: `READY_FOR_IMPLEMENTATION`

## Scope Expansion

For `TASK-004`, `claude-1` may inspect and propose changes within:

- `jade-tipi/build.gradle`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/`
- `jade-tipi/src/main/resources/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/`
- `jade-tipi/src/test/resources/application-test.yml`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`
- `jade-tipi/src/integrationTest/resources/`
- `docs/orchestrator/tasks/TASK-004-kafka-transaction-ingest-integration-test.md`

Implementation is approved. Keep changes inside the scope expansion above and record implementation outcomes in `docs/agents/claude-1-changes.md`.

## TASK-004 Director Decisions

- Prefer the current Docker Compose services and Gradle integration-test wiring over adding Testcontainers unless pre-work shows the documented setup cannot make the test reliable.
- Kafka auto-topic creation is disabled. Pre-work must decide whether to use the existing `jdtp_cli_kli` topic or create a per-test topic through a reliable documented/admin-client path.
- The integration test should publish canonical open, data, and commit messages, then assert the `txn` header and message documents in MongoDB.
- If Docker or local Gradle tooling is unavailable during verification, report the exact documented setup command rather than treating it as a product blocker.
- Director approved the claude-1 pre-work defaults on 2026-04-30: use an AdminClient-created per-test topic, gate with `JADETIPI_IT_KAFKA=1` and a fast broker probe, use a unique consumer group, use a raw `KafkaProducer<String, byte[]>`, poll MongoDB with an inline bounded helper, and clean up only the test topic/documents. Do not add Awaitility, a new profile file, or logback noise controls unless implementation shows they are needed.
- Create the test topic before Spring's topic-pattern listener needs to discover it where possible, and shorten `spring.kafka.consumer.properties.metadata.max.age.ms` for this spec to avoid a long metadata-refresh wait.

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
