# TASK-004 - Add Kafka transaction ingest integration coverage

ID: TASK-004
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/build.gradle
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/
  - jade-tipi/src/main/resources/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/resources/
  - jade-tipi/src/test/resources/application-test.yml
  - docs/orchestrator/tasks/TASK-004-kafka-transaction-ingest-integration-test.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Add a practical integration test for the accepted Kafka-first transaction ingestion path. The test should exercise Kafka message consumption through `TransactionMessageListener` into MongoDB's `txn` collection using the repository's documented local Docker services where possible.

ACCEPTANCE_CRITERIA:
- Pre-work identifies the lowest-friction integration strategy using the current repository setup before implementation. Prefer project-documented Docker Compose services and existing Gradle integration-test wiring over adding new infrastructure.
- The integration test publishes canonical `Message` records for a transaction open, one data message, and commit, then asserts MongoDB `txn` contains the expected transaction header and message document.
- The test is reliable for local/documented verification: it should either use an existing reliably created topic such as `jdtp_cli_kli` or create a test topic through a documented/admin client path without relying on Kafka auto-topic creation.
- The test must be opt-in or environment-gated if it cannot run without the Docker stack, and its skip/run conditions must be explicit in the test or task report.
- Do not change production Kafka ingestion behavior unless the integration test exposes a real bug in the accepted implementation.
- Verification includes the narrow integration-test command selected during pre-work and any required documented Docker setup command.

OUT_OF_SCOPE:
- Do not implement transaction materialization into `ent`, `ppy`, `typ`, `lnk`, or other long-term collections.
- Do not build the HTTP submission wrapper.
- Do not introduce Kafka ACLs, OAuth/SASL hardening, Kafka Streams, or exactly-once processing.
- Do not redesign the message envelope or persistence record shape accepted in `TASK-003`.

DESIGN_NOTES:
- `TASK-003` accepted the unit-tested ingest path and deferred Kafka integration coverage because Testcontainers is not wired.
- `docker/docker-compose.yml` currently starts MongoDB and Kafka and creates `jdtp_cli_kli`; Kafka auto-topic creation is disabled.
- Keep this task focused on proving the accepted end-to-end path, not broadening product semantics.

DEPENDENCIES:
- `TASK-003` is accepted and provides the Spring Kafka listener, transaction persistence service, and `txn` record shape.
- Local Docker-backed verification should use the documented project command: `docker compose -f docker/docker-compose.yml up -d` or a narrower documented service subset if pre-work confirms it is sufficient.

LATEST_REPORT:
Director reviewed claude-1 pre-work on 2026-04-30 and approved implementation. Ownership check passed: the latest claude-1 pre-work commit changed only `docs/agents/claude-1-next-step.md`, which is inside the developer assignment's owned paths.

Implementation should follow the defaults proposed in the pre-work:
- Add the Kafka ingest integration spec under `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/`.
- Use the documented Docker Kafka/Mongo stack, not Testcontainers.
- Create a unique per-test topic with Kafka `AdminClient` using a name matched by the existing `jdtp-txn-.*|jdtp_cli_kli` pattern. Do not reuse `jdtp_cli_kli` for the test unless AdminClient topic creation proves unavailable.
- Gate the spec with `JADETIPI_IT_KAFKA=1` plus a fast broker probe against `KAFKA_BOOTSTRAP_SERVERS` or `localhost:9092`; skipped conditions must be explicit in the test/report.
- Use a per-run unique `spring.kafka.consumer.group-id`.
- Use a raw `KafkaProducer<String, byte[]>` and the project `JsonMapper` to publish canonical open, one data message, and commit records.
- Poll MongoDB with a small bounded helper; do not add Awaitility unless inline polling becomes materially worse.
- Clean up only the topic and the `txn` documents created by this spec.
- Keep production Kafka ingestion behavior unchanged unless the integration test exposes a real bug.

Two features are acceptable: the required happy path and a narrow idempotency sanity check. The implementation should use inline `@TestPropertySource`/dynamic properties rather than a new integration profile unless a profile file is necessary. A logback test file is optional only if Kafka logs make verification output unreadable.

Topic/listener startup guidance: create the test topic before the Spring listener subscribes when possible, and also shorten `spring.kafka.consumer.properties.metadata.max.age.ms` for this spec so topic-pattern discovery is not dependent on the default long metadata refresh. If Spock/Spring lifecycle makes the exact hook awkward, document the chosen ordering in the report and keep the test bounded and deterministic.

Expected setup and verification:
- `docker compose -f docker/docker-compose.yml up -d`
- `./gradlew :jade-tipi:compileGroovy`
- `./gradlew :jade-tipi:compileIntegrationTestGroovy`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
- `./gradlew :jade-tipi:test`

If local verification fails because Docker, Gradle wrapper state, or other local tooling is missing/stale, report the documented setup command that should be run rather than treating that as a product blocker.
