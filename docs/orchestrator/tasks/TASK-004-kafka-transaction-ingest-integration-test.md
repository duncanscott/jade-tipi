# TASK-004 - Add Kafka transaction ingest integration coverage

ID: TASK-004
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-005
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
Director implementation review on 2026-04-30:
- Accepted claude-1 implementation commit `a759cf1`.
- Scope check passed. The implementation changed `docs/agents/claude-1-changes.md`, this task file, and `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`; no edits were found outside claude-1's report path or the `TASK-004` scope expansion.
- Acceptance criteria are satisfied. The new integration spec publishes canonical transaction open, property create, and commit `Message` records to Kafka, exercises the real `TransactionMessageListener` and `TransactionMessagePersistenceService`, and asserts the committed `txn` header plus the expected message document and Kafka provenance in MongoDB.
- The implementation honors `DIRECTIVES.md`: it uses the documented Docker Kafka/Mongo stack, creates an isolated AdminClient-managed per-run topic, gates the spec with `JADETIPI_IT_KAFKA=1` plus a fast broker probe, uses a unique consumer group, serializes with `JsonMapper` through a raw `KafkaProducer<String, byte[]>`, polls MongoDB with inline bounded helpers, and leaves production ingestion behavior unchanged.
- Non-blocking review note: the optional duplicate-delivery feature's final count assertion can pass before the duplicate record has actually been consumed because the per-`txn_id` count is already `2`. The required happy-path integration coverage is unaffected; if idempotency becomes a required integration guarantee, add an explicit consumer-offset/logical-processing assertion.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:compileIntegrationTestGroovy` failed opening `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` (`Operation not permitted`), `gradle --version` failed loading the native-platform dylib in this sandbox, and `docker compose -f docker/docker-compose.yml ps` could not access the Docker socket. In a normal developer shell, use the project-documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, `./gradlew :jade-tipi:compileIntegrationTestGroovy`, and `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileIntegrationTestGroovy`, `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`, skipped-mode integration-test verification, and `./gradlew :jade-tipi:test --rerun-tasks` all passing.
- Out-of-scope items stayed out of scope: no materialization to `ent`/`ppy`/`typ`/`lnk`, no HTTP submission wrapper, no Kafka ACL/SASL/Streams/exactly-once work, and no message envelope or persistence record-shape redesign.
