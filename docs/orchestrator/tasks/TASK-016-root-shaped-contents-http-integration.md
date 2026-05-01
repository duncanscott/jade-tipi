# TASK-016 - Plan root-shaped contents HTTP integration coverage

ID: TASK-016
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
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

Director pre-work review accepted on 2026-05-01. Scope check passed:
claude-1's latest pre-work commit changed only
`docs/agents/claude-1-next-step.md`, inside the base developer-owned paths.
The plan satisfies this task's source of truth: it proposes one opt-in
`ContentsHttpReadIntegrationSpec` under the `contents` integration-test
package, reuses the accepted Kafka integration-test pattern, adds a local
Keycloak readiness gate before Spring context startup, publishes one
transaction with `loc + create`, canonical `typ + create` `contents`, and
`lnk + create` with `properties.position`, waits for committed `txn`
visibility plus root-shaped materialized `typ`/`lnk` rows, and asserts both
contents HTTP routes against a real authenticated `RANDOM_PORT` context.

Director decisions for implementation:
- Keep the Keycloak reachability probe inline in the new spec; do not refactor
  `KeycloakTestHelper` or existing integration tests.
- Build messages with DTO helpers and inline payload maps that mirror the
  canonical examples while using per-run ids; do not load the bundled example
  JSON verbatim and do not add a fixture resource unless implementation proves
  it is required.
- It is acceptable for the `lnk.right` content id to remain an unresolved
  `ent` id. This preserves the canonical example's passthrough semantics and
  does not authorize endpoint joins or semantic reference validation.
- No `application-test.yml` edit is expected. Use the existing test-profile
  Keycloak issuer and Mongo settings unless source inspection during
  implementation proves a blocking contradiction.
- Assert the empty-result route as HTTP 200 with a JSON empty array; prefer a
  direct body assertion such as `expectBody().json('[]')` or an equivalent
  JSON-path length assertion rather than adding deserialization concerns.

Implementation should add only the narrow integration spec and any strictly
necessary cleanup/isolation code inside the task-owned paths. Preserve the
out-of-scope boundaries above: no production service/controller/materializer,
Kafka listener, DTO/schema/example, Docker, Gradle, security, frontend,
response-envelope, pagination, endpoint-join, semantic-validation,
update/delete replay, or backfill changes.

After implementation, set this task to `READY_FOR_REVIEW` and append the
implementation report to `docs/agents/claude-1-changes.md`. Required
verification remains the Gradle command list in this task file, including the
focused opt-in Kafka integration command. If local tooling, Gradle locks,
Docker, Kafka, Mongo, or Keycloak setup blocks verification, report the
documented setup command `docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop` when stale Gradle daemons are implicated, and the exact
blocked command/error rather than treating setup as a product blocker.
