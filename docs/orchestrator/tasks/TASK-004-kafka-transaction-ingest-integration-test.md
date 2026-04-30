# TASK-004 - Add Kafka transaction ingest integration coverage

ID: TASK-004
TYPE: implementation
STATUS: IMPLEMENTATION_COMPLETE
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
Implementation complete on 2026-04-30 by `claude-1`. The accepted Kafka transaction ingestion path now has end-to-end integration coverage that exercises `TransactionMessageListener` and `TransactionMessagePersistenceService` against the documented Docker Kafka/Mongo stack.

As-built (matches pre-work defaults; no production code changed):
- New file: `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`. Two features:
  1. Happy path — produce open + data + commit canonical `Message` records to a per-spec topic; assert the `txn` header reaches `state=committed` with backend `commit_id`, the data message document has `_id=txnId~msgUuid`, `record_type=message`, `collection=ppy`, and `kafka.{topic,partition,offset,timestamp_ms}` populated; assert exactly one header + one message document for the txn.
  2. Idempotency sanity check — re-publish the same data message after commit; assert per-`txn_id` document count stays at 2 (`APPEND_DUPLICATE` path).
- Per-spec topic `jdtp-txn-itest-${shortUuid}` created via `AdminClient.createTopics` from a static `@DynamicPropertySource` method (so the topic exists before the Spring listener container starts) and deleted in `cleanupSpec`.
- Listener subscribes only to the per-run topic (regex override of `jadetipi.kafka.txn-topic-pattern`), uses a per-run unique `spring.kafka.consumer.group-id`, and runs with `metadata.max.age.ms=2000` so pattern subscription discovers the test topic in ~2s.
- Producer is a plain `KafkaProducer<String, byte[]>` (acks=all) serializing each `Message` with `org.jadetipi.dto.util.JsonMapper.toBytes(...)`. No new dependency.
- Spock `@IgnoreIf` gate: spec runs only when `JADETIPI_IT_KAFKA in {1, true, TRUE, yes}` AND a 2s `AdminClient.describeCluster` probe to `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`) succeeds. Otherwise both features report `skipped` and the Spring context is never loaded.
- Mongo cleanup is targeted: `mongoTemplate.remove({txn_id: ...}, 'txn')` per feature so the spec coexists with `TransactionServiceIntegrationSpec`.
- Production Kafka ingestion behavior, `application.yml`, and existing test profile are unchanged. `application-test.yml` still sets `jadetipi.kafka.enabled: false` for the unit-test context loads; the integration spec re-enables it via `@DynamicPropertySource` for its own context only.

Verification (2026-04-30, full Docker stack up):
- `docker compose -f docker/docker-compose.yml up -d` — `mongodb`, `keycloak`, `kafka` all healthy.
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'` — BUILD SUCCESSFUL. Test report: `tests=2, skipped=0, failures=0, errors=0, time=5.347s` (initial run); `4.917s` on a `--rerun-tasks` re-run (stable).
- `./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'` (env flag NOT set) — BUILD SUCCESSFUL. Test report: `tests=2, skipped=2, failures=0, errors=0, time=0.0s`.
- `./gradlew :jade-tipi:test --rerun-tasks` — BUILD SUCCESSFUL. 37 unit tests pass (no regression).

Out-of-scope items confirmed not implemented: materialization to `ent`/`ppy`/`typ`/`lnk`, HTTP submission wrapper, Kafka ACLs / SASL / Streams / exactly-once, message envelope or persistence record shape redesign. The integration test exposed no bug, so production code remains as accepted in `TASK-003`.
