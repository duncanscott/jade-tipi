# TASK-004 - Add Kafka transaction ingest integration coverage

ID: TASK-004
TYPE: implementation
STATUS: READY_FOR_PREWORK
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
Created by director on 2026-04-30 after accepting `TASK-003`. Pre-work should decide whether to use the existing `jdtp_cli_kli` topic or an explicit test-topic creation step, and should document the exact Docker/Gradle commands required before any implementation changes.
