# TASK-015 - Update contents read service for root-shaped documents

ID: TASK-015
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-014
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadControllerSpec.groovy
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-015-root-shaped-contents-read-service.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Update the contents read path so the accepted HTTP/service queries read the
root-shaped materialized `typ` and `lnk` documents produced by `TASK-014`,
without changing route shape, response envelope, write semantics, or endpoint
join policy.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `TASK-013`, accepted `TASK-014`,
  `CommittedTransactionMaterializer`, `ContentsLinkReadService`,
  `ContentsLinkRecord`, `ContentsLinkReadController`, their focused specs, and
  the "Reading `contents` Links" architecture documentation before proposing
  implementation.
- Pre-work proposes the smallest read-service update for resolving canonical
  `contents` link types from root-shaped `typ` rows where
  `properties.kind == "link_type"` and `properties.name == "contents"`.
- Pre-work proposes how `ContentsLinkRecord.provenance` should be populated
  from `_head.provenance`, including whether to keep a short fallback to legacy
  `_jt_provenance` rows during the copied-data-shape transition.
- Pre-work specifies focused service assertions for root-shaped `typ` query
  criteria, `lnk` row mapping, `_head.provenance` mapping, empty-result
  behavior, blank-id behavior, ordering, unresolved endpoint pass-through, and
  any approved legacy fallback.
- Pre-work specifies whether the controller spec needs only serialization
  expectation updates or any production controller change. Preserve the current
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` routes unless source inspection reveals a
  blocking contradiction.
- Pre-work proposes exact Gradle verification commands. If local tooling,
  Gradle locks, Docker, or Mongo setup blocks verification, report the
  project-documented setup command rather than treating setup as a product
  blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves this task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change `CommittedTransactionMaterializer`,
  `TransactionMessagePersistenceService`, Kafka listener behavior, committed
  snapshot shape, DTO schemas, canonical examples, Docker Compose, Gradle
  files, security policy, frontend, response envelopes, pagination, endpoint
  joins to `loc` or `ent`, semantic reference validation, endpoint projection
  maintenance, extension pages, pending pages, update/delete replay, backfill,
  transaction-overlay reads, required properties, or default values.
- Do not resume or implement `TASK-012` in this task.
- Do not add integration coverage in this task; rewrite or replace the paused
  `TASK-012` integration task after this read-service update is accepted.

DESIGN_NOTES:
- `TASK-014` intentionally did not update the read service. Its accepted design
  notes identify this as the next required follow-up: the service must query
  `typ.properties.kind/name` and read provenance from `_head.provenance`.
- Keep `lnk` documents canonical for relationships. This task reads top-level
  `lnk.type_id`, `lnk.left`, `lnk.right`, and `lnk.properties`; it must not
  depend on endpoint `links` projections.
- `TASK-013` allowed readers to carry a short fallback while the copied-data
  shape is removed. The pre-work should make that compatibility choice
  explicit and keep it covered if used.

DEPENDENCIES:
- `TASK-014` is accepted and provides root-shaped materialized `loc`,
  `typ link_type`, and `lnk` writes.
- `TASK-010` and `TASK-011` are accepted and provide the current contents read
  service and HTTP adapter.
- `TASK-012` remains paused and must be rewritten or replaced after this
  read-service update lands.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy`
- `./gradlew :jade-tipi:compileTestGroovy`
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
- `./gradlew :jade-tipi:test`

If local tooling, Gradle locks, Docker, or Mongo setup blocks verification,
report the project-documented setup command
`docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
`./gradlew --stop` when stale Gradle daemons are implicated, and the exact
blocked command/error rather than treating setup as a product blocker.

LATEST_REPORT:
Created on 2026-05-01 from accepted `TASK-014` implementation review.
