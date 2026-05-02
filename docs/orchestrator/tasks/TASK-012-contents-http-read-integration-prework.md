# TASK-012 - Plan contents HTTP read integration coverage

ID: TASK-012
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASKS:
  - TASK-011
NEXT_TASK:
  - TASK-013
OWNED_PATHS:
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/resources/
  - jade-tipi/src/test/resources/application-test.yml
  - docs/orchestrator/tasks/TASK-012-contents-http-read-integration-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Plan opt-in end-to-end integration coverage for the accepted contents read
path, proving canonical location/type/link messages can flow through Kafka
ingestion, committed materialization, and the `GET /api/contents/...` HTTP read
routes without changing product semantics.

ACCEPTANCE_CRITERIA:
- Pre-work inspects the accepted `TASK-004` Kafka integration pattern,
  `TransactionMessageKafkaIngestIntegrationSpec`, `KeycloakTestHelper`,
  existing WebTestClient integration tests, canonical examples
  `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json`, `CommittedTransactionMaterializer`,
  `ContentsLinkReadService`, `ContentsLinkReadController`, Docker Compose
  service names, and integration-test Gradle wiring.
- Pre-work proposes the narrowest reliable integration strategy. Prefer the
  existing project-documented Docker stack, explicit environment gating, an
  isolated Kafka topic/consumer group, bounded Mongo/HTTP polling helpers, and
  authenticated `WebTestClient` requests through the real Spring context.
- The proposed implementation should publish or otherwise submit one canonical
  transaction containing the `loc`, `typ` `contents`, and `lnk` `contents`
  create messages, wait for committed materialization, then assert both
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` return the expected flat JSON array.
- The proposal must define cleanup/isolation for Mongo `txn`, `loc`, `typ`,
  `lnk`, Kafka topics, and any authenticated test state so repeated local runs
  do not depend on global database emptiness.
- The proposal must specify whether to reuse bundled canonical example JSON,
  construct messages with DTO helpers, or use a small local fixture, and must
  justify the choice based on stability and existing test patterns.
- Pre-work proposes exact verification commands. If local tooling, Gradle
  locks, Docker, Kafka, Mongo, or Keycloak are unavailable, report the
  project-documented setup command rather than treating setup as a product
  blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change `ContentsLinkReadService`, `ContentsLinkReadController`,
  `CommittedTransactionMaterializer`, `TransactionMessagePersistenceService`,
  Kafka listener behavior, materialized document semantics, DTO schemas,
  canonical message examples, Docker Compose, security policy, or frontend.
- Do not add endpoint joins to `loc` or `ent`, response envelopes, pagination,
  authorization/scoping policy, semantic write-time validation, update/delete
  replay, backfill jobs, or plate-shaped UI/API projections.
- Do not make existing integration tests depend on a globally clean database or
  an ungated Docker stack.

DESIGN_NOTES:
- `TASK-011` accepted the thin HTTP adapter with pure controller coverage only;
  integration coverage was intentionally deferred from that task.
- `TASK-004` already established an opt-in Kafka/Mongo integration-test pattern
  using the documented Docker stack and an explicit environment gate.
- This task is for pre-work first because it crosses Kafka, Mongo, materializer,
  security, and HTTP layers; source inspection should choose the smallest stable
  path before implementation.

DEPENDENCIES:
- `TASK-009`, `TASK-010`, and `TASK-011` are accepted and provide committed
  materialization, the contents read service, and the HTTP adapter.

LATEST_REPORT:
Director pause on 2026-05-01:
- Do not implement `TASK-012` while `TASK-013` is active. Product direction now
  says materialized MongoDB documents should move toward a canonical
  root-document contract rather than hardening the current copied-data
  projection shape from `TASK-009`.
- `TASK-013` is a research/design task that pauses this source task and should
  define the materialized root document shape before this integration coverage
  is resumed, rewritten, or replaced.

Director pre-work review accepted on 2026-05-01. Scope check passed:
claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the
developer-owned pre-work paths. Implement the narrow opt-in integration spec
proposed there, using the accepted `TASK-004` Kafka pattern and authenticated
`WebTestClient` through a real `RANDOM_PORT` Spring context.

Implementation direction:
- Add one integration spec under
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/`.
- Reuse the existing project-documented Docker stack and the existing
  `JADETIPI_IT_KAFKA` opt-in flag. Include the Kafka reachability probe and add
  the proposed Keycloak readiness probe so missing local services skip before
  Spring context load.
- Use per-run Kafka topic, consumer group, transaction id, and materialized
  document ids. Create and delete only the spec topic; do not rely on global
  topic or database emptiness.
- Construct the transaction messages with DTO helpers and inline payload maps
  matching the canonical `10`, `11`, and `12` example shapes. Do not load the
  bundled JSON examples verbatim. Use one `loc` container row, the canonical
  `typ + create` `contents` link type row, and one `lnk + create` row with the
  plate-well `properties.position` shape. An extra unrelated `loc` row is not
  required.
- Publish one transaction through Kafka, wait for committed `txn` visibility,
  then wait for the materialized `typ` and `lnk` rows before exercising HTTP.
- Assert both `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` in one feature against the same
  materialized link. Include the empty-result HTTP 200 `[]` assertion.
- Keep await/polling helpers private in the new spec for this task. Do not
  refactor `TransactionMessageKafkaIngestIntegrationSpec`.
- Keep assertions inline; no new resource fixture is needed.
- Rely on per-run id/topic/group isolation rather than serializing all
  integration tests.
- After implementation, set this task to `READY_FOR_REVIEW` and append the
  implementation report to `docs/agents/claude-1-changes.md`.

Required verification after implementation:
`./gradlew :jade-tipi:compileGroovy`,
`./gradlew :jade-tipi:compileTestGroovy`,
`./gradlew :jade-tipi:compileIntegrationTestGroovy`,
`JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`,
and `./gradlew :jade-tipi:test`. Also run
`JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
if time permits, because it cheaply checks coexistence with the accepted
Kafka-ingest integration pattern. If local tooling, Gradle locks, Docker,
Kafka, Mongo, or Keycloak are unavailable, report the documented setup command
`docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop` when
stale Gradle daemons are the issue, and the exact command/error rather than
treating setup as a product blocker.

Director supersession closure on 2026-05-02:
- `TASK-012` is closed as accepted historical context after acceptance of
  replacement task `TASK-016`.
- Do not implement this task as-is. Its original copied-data-shape integration
  path was paused by `TASK-013`, replaced after the root-document contract was
  accepted, and satisfied by `TASK-016` using the accepted root-shaped
  materializer and contents read service.
- Keeping this task accepted prevents the orchestrator from routing obsolete
  `TASK-012` implementation work after `TASK-016` is accepted.
