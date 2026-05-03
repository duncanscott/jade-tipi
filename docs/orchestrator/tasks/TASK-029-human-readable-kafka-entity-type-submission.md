# TASK-029 - Human-readable Kafka entity-type submission path

ID: TASK-029
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-028
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
  - docs/orchestrator/tasks/TASK-029-human-readable-kafka-entity-type-submission.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Continue the Kafka-first domain write path by planning the smallest increment
that lets a human-readable Kafka transaction create a root-shaped entity-type
`typ` document from the existing bare entity-type example.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  the accepted TASK-013/TASK-014 root-document notes, and TASK-026 through
  TASK-028's accepted Kafka submission reviews before planning.
- Preserve Kafka as the primary submission route. Do not add HTTP data
  submission endpoints.
- Inspect the current bare entity-type `typ + create` example
  `04-create-entity-type.json`, the entity-type property-update example
  `05-update-entity-type-add-property.json`, DTO validation, and
  `CommittedTransactionMaterializer` support/skipping behavior.
- Identify the intended human-readable entity-type `typ + create` shape:
  - top-level `collection: "typ"` and `action: "create"`;
  - no `data.kind: "link_type"` marker for an entity type;
  - `data.id` is the materialized type object ID;
  - `data.name` and optional descriptive facts materialize under root
    `properties`;
  - `data.links` is absent or empty because canonical relationships remain
    separate `lnk` messages.
- Preserve the current example ID strings for this task unless the plan
  identifies a necessary local synthetic ID in tests. Do not perform broad
  ID-abbreviation cleanup.
- Decide whether the implementation should materialize only bare
  entity-type `typ + create` while leaving `typ + update` property-reference
  changes unsupported, or whether the property-reference update needs a
  separate follow-up task.
- Add or update example resource JSON only if the existing examples do not
  already show the accepted human-readable shape and transaction sequence.
- Propose focused automated coverage for DTO validation, materialized root
  shape, skipped `typ + update` behavior if it remains deferred, idempotent
  duplicate handling, conflicting duplicate handling, and the narrowest
  practical Kafka/Mongo integration check.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not implement property assignment materialization, required property
  enforcement, semantic validation that `ent.data.type_id` resolves, permission
  enforcement, object extension pages, endpoint projection maintenance, full
  Clarity/ESP import, or a nested Kafka operation DSL.
- Do not redesign the ID abbreviation scheme in this task.
- Do not change contents-link read semantics or group/admin behavior.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify current materializer behavior for `typ + create` link types,
  bare entity-type `typ + create`, and `typ + update` property-reference
  messages, including exactly what is materialized or skipped today and why.
- Identify whether the existing examples already provide the desired
  human-readable entity-type shape, including any ID suffix or collection
  abbreviation mismatches that need director review.
- Identify the smallest implementation/test changes needed for examples, DTO
  tests, materializer coverage, and integration verification.
- Stop after pre-work. Do not implement until the director advances this task
  to `READY_FOR_IMPLEMENTATION`.

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
