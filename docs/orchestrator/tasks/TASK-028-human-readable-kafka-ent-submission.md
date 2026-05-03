# TASK-028 - Human-readable Kafka entity submission path

ID: TASK-028
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-027
  - TASK-026
  - TASK-013
  - TASK-014
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - DIRECTION.md
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/jade-tipi-object-model-design-brief.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-028-human-readable-kafka-ent-submission.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Continue the Kafka-first domain write path by planning the smallest increment
that lets a human-readable Kafka transaction create a root-shaped `ent`
document, starting from the existing entity-type and entity examples.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  the accepted TASK-013/TASK-014 root-document notes, and TASK-026/TASK-027's
  accepted Kafka submission reviews before planning.
- Preserve Kafka as the primary submission route. Do not add HTTP data
  submission endpoints.
- Inspect the current `typ + create` entity-type example
  `04-create-entity-type.json`, the `ent + create` example
  `06-create-entity.json`, DTO validation, and
  `CommittedTransactionMaterializer` support/skipping behavior.
- Identify the intended human-readable `ent + create` shape:
  - top-level `collection: "ent"` and `action: "create"`;
  - `data.id` is the materialized entity object ID;
  - `data.type_id` references a `typ` entity-type record;
  - `data.properties` is optional or an explicit plain JSON object for inline
    entity facts only if current direction supports that in this increment;
  - `data.links` is optional or empty on create because canonical
    relationships are separate `lnk` messages.
- Identify whether entity-type `typ + create` materialization is required for
  this bounded proof, or whether the first entity increment should materialize
  only `ent + create` while leaving type-reference semantic validation deferred.
- Add or update example resource JSON only if the existing examples do not
  already show the accepted human-readable shape and transaction sequence.
- Propose focused automated coverage for DTO validation, materialized root
  shape, idempotency/skipping behavior, and the narrowest practical
  Kafka/Mongo integration check.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not implement property assignment materialization, permission enforcement,
  object extension pages, endpoint projection maintenance, full Clarity/ESP
  import, or a nested Kafka operation DSL.
- Do not add semantic validation that `data.type_id` resolves to a committed
  `typ` record.
- Do not redesign entity typing, property-definition validation, required
  property enforcement, or the transaction write-ahead log.
- Do not change contents-link read semantics or group/admin behavior.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify current materializer behavior for `typ + create` entity types and
  `ent + create`, including exactly what is skipped today and why.
- Identify whether the existing examples already provide the desired
  human-readable shape, including any ID suffix or collection-abbreviation
  mismatches that need director review.
- Identify the smallest implementation/test changes needed for examples, DTO
  tests, materializer coverage, and integration verification.
- Stop after pre-work. Do not implement until the director advances this task
  to `READY_FOR_IMPLEMENTATION`.

DIRECTOR_PREWORK_REVIEW:
- 2026-05-03: Pre-work accepted for implementation with the constraints below.
  Scope check passed: claude-1 changed only
  `docs/agents/claude-1-next-step.md`, which is inside the base owned paths in
  `docs/agents/claude-1.md`.
- Source correction for implementation: current
  `06-create-entity.json` uses `~en~plate_a`, not `~ent~plate_a`. Current
  `04-create-entity-type.json` and `06-create-entity.json` also use `~ty~` for
  the entity-type ID/reference. Treat those existing IDs as stable for this
  task; the materializer should preserve submitted IDs verbatim. Do not do a
  broad ID-abbreviation cleanup in this implementation turn. If the mismatch
  needs cleanup, record it as follow-up rather than changing `05-*`, `07-*`,
  `08-*`, or the architecture vocabulary examples opportunistically.
- Implement the smallest behavioral increment: support `ent + create`
  materialization into the same root-shaped document contract used by `loc`,
  `lnk`, `typ` link-type, and `grp`. Leave bare entity-type `typ + create`
  unsupported in this task; do not introduce `data.kind: "entity_type"` or
  semantic resolution of `data.type_id`.
- It is acceptable to update `06-create-entity.json` to make the existing
  entity wire shape explicit with `data.properties: {}` and `data.links: {}`.
  Preserve `data.id` and `data.type_id` values unless a test requires only a
  local synthetic ID.
- Required focused coverage for the implementation turn:
  DTO coverage for the `06-create-entity.json` shape and its reference to
  `04-create-entity-type.json`; materializer unit coverage for `ent + create`
  root shape, missing/blank `data.id`, idempotent duplicate handling,
  conflicting duplicate handling, and mixed-message count/order changes with
  `ppy` still skipped; and the narrowest practical Kafka/Mongo opt-in check for
  `open + ent + commit` materialization if the local stack is available.
- Verification should use the task commands below. If local tooling blocks the
  checks because Docker, Kafka, Mongo, Gradle cache permissions, or stale Gradle
  daemons are unavailable, report the exact command and error plus the
  documented setup command instead of treating setup friction as a product
  blocker.

VERIFICATION:
- Expected implementation-turn commands:
  - `./gradlew :libraries:jade-tipi-dto:test`
  - `./gradlew :jade-tipi:test`
  - the narrowest practical Kafka/Mongo integration test if local Docker is
    running and the project has a documented opt-in flag for it
- If local verification is blocked by Docker, Kafka, Mongo, Gradle cache
  permissions, or missing dependencies, report the exact command and error
  rather than treating setup friction as a product blocker. Prefer documented
  setup commands: `docker compose -f docker/docker-compose.yml --profile mongodb up -d`
  for Mongo-backed unit tests, `docker compose -f docker/docker-compose.yml up -d`
  for the full Kafka/Mongo stack, and `./gradlew --stop` only when stale Gradle
  daemons are implicated.
