# TASK-012 - Plan contents HTTP read integration coverage

ID: TASK-012
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASKS:
  - TASK-011
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
Created by director on 2026-05-01 after accepting `TASK-011`. Start with
pre-work only; do not implement until this task is reviewed and moved to
`READY_FOR_IMPLEMENTATION`.
