# TASK-016 - Plan root-shaped contents HTTP integration coverage

ID: TASK-016
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-012
  - TASK-015
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/
  - jade-tipi/src/integrationTest/resources/
  - jade-tipi/src/test/resources/application-test.yml
  - docs/orchestrator/tasks/TASK-016-root-shaped-contents-http-integration.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Rewrite the paused `TASK-012` integration coverage for the accepted
root-shaped materializer and contents read service, proving one canonical
contents transaction can flow through Kafka ingestion, committed
materialization, and the existing contents HTTP routes.

ACCEPTANCE_CRITERIA:
- Pre-work inspects the accepted `TASK-004` Kafka integration pattern,
  `TransactionMessageKafkaIngestIntegrationSpec`, `KeycloakTestHelper`,
  existing WebTestClient integration tests, canonical examples
  `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json`, `CommittedTransactionMaterializer`,
  `ContentsLinkReadService`, `ContentsLinkReadController`, Docker Compose
  service names, integration-test Gradle wiring, and the accepted `TASK-014`
  and `TASK-015` reports.
- Pre-work proposes the smallest reliable opt-in integration spec for the
  root-shaped contract. Prefer the project-documented Docker stack, the
  existing `JADETIPI_IT_KAFKA` opt-in flag, an isolated Kafka topic/consumer
  group, per-run transaction/materialized ids, bounded Mongo/HTTP polling, and
  authenticated `WebTestClient` requests through a real `RANDOM_PORT` Spring
  context.
- The proposal must publish or otherwise submit one transaction containing one
  `loc + create`, one canonical `typ + create` `contents` link-type
  declaration, and one `lnk + create` contents relationship with
  `properties.position`; wait for committed `txn` visibility and root-shaped
  materialized `typ`/`lnk` rows; then assert both
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` return the expected flat JSON array.
- The proposal must include an empty-result HTTP 200 `[]` assertion.
- The proposal must define cleanup/isolation for the Kafka topic, `txn` rows,
  and materialized `loc`/`typ`/`lnk` rows without requiring a globally clean
  database.
- The proposal must specify whether to construct messages with DTO helpers,
  reuse bundled canonical JSON, or use a small local fixture, and justify the
  choice against existing test patterns.
- Pre-work proposes exact Gradle verification commands. If local tooling,
  Gradle locks, Docker, Kafka, Mongo, or Keycloak setup blocks verification,
  report the project-documented setup command rather than treating setup as a
  product blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves this task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change `ContentsLinkReadService`, `ContentsLinkReadController`,
  `CommittedTransactionMaterializer`, `TransactionMessagePersistenceService`,
  Kafka listener behavior, materialized document semantics, DTO schemas,
  canonical message examples, Docker Compose, Gradle files, security policy,
  frontend, response envelopes, pagination, endpoint joins to `loc` or `ent`,
  authorization/scoping policy, semantic write-time validation, update/delete
  replay, backfill jobs, or plate-shaped UI/API projections.
- Do not make existing integration tests depend on a globally clean database
  or an ungated Docker stack.
- Do not use this task to refresh broad architecture documentation beyond any
  short test-specific note the pre-work proves is required.

DESIGN_NOTES:
- `TASK-012` had accepted pre-work for contents HTTP integration, but it was
  paused because the materialized copied-data shape was replaced by the
  root-shaped contract.
- `TASK-014` accepted root-shaped materialized `loc`, `typ link_type`, and
  `lnk` documents with `_head.provenance`.
- `TASK-015` accepted the contents read-service update for root-shaped
  `typ.properties.kind/name`, top-level `lnk` fields, and `_head.provenance`
  with a narrow legacy fallback.

DEPENDENCIES:
- `TASK-014` and `TASK-015` are accepted.
- `TASK-012` remains historical context and must not be implemented as-is.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy`
- `./gradlew :jade-tipi:compileTestGroovy`
- `./gradlew :jade-tipi:compileIntegrationTestGroovy`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`
- `./gradlew :jade-tipi:test`
- If time permits: `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`

If local tooling, Gradle locks, Docker, Kafka, Mongo, or Keycloak setup blocks
verification, report the project-documented setup command
`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop` when stale Gradle daemons are implicated, and the exact
blocked command/error rather than treating setup as a product blocker.

LATEST_REPORT:
Created by director on 2026-05-01 after accepting `TASK-015`. This task
replaces the paused `TASK-012` implementation path with fresh pre-work that
must account for the accepted root-shaped materializer and contents read
service before any integration implementation begins.
