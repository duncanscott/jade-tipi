# TASK-030 - Human-readable Kafka entity-type property-reference update path

ID: TASK-030
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-029
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
  - docs/orchestrator/tasks/TASK-030-human-readable-kafka-entity-type-property-reference-update.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Plan the smallest Kafka-first increment that lets a human-readable
`typ + update` `operation: "add_property"` message add a property reference to
an existing root-shaped bare entity-type `typ` document.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  the accepted TASK-013/TASK-014 root-document notes, and TASK-026 through
  TASK-029's accepted Kafka submission reviews before planning.
- Preserve Kafka as the primary submission route. Do not add HTTP data
  submission endpoints.
- Inspect `02-create-property-definition-text.json`,
  `03-create-property-definition-numeric.json`,
  `04-create-entity-type.json`, `05-update-entity-type-add-property.json`, DTO
  validation, `CommittedTransactionMaterializer` support/skipping behavior, and
  the current Mongo update/duplicate handling around materialized root
  documents.
- Identify the intended human-readable `typ + update add_property` wire shape:
  top-level `collection: "typ"` and `action: "update"`, `data.id` as the target
  type ID, `data.operation: "add_property"`, `data.property_id` as a referenced
  property-definition ID, and `data.required` as optional reference metadata.
- Preserve the current example ID strings unless the plan identifies a
  necessary local synthetic ID in tests. Do not perform broad ID-abbreviation
  cleanup.
- Decide the smallest materialized root shape for property references on a
  `typ` document, including whether the reference belongs under root
  `properties`, root `links`, or another already-documented root field. Keep
  the shape reference-only; do not embed property definitions.
- Decide whether `ppy + create` property-definition materialization is required
  for this bounded proof, or whether `typ + update` can record the reference
  without semantic validation that `data.property_id` resolves.
- Decide how the materializer should behave when the target `typ` root is
  missing, when the same property reference is added twice, and when an
  existing reference conflicts on metadata such as `required`.
- Add or update example resource JSON only if the existing examples do not
  already show the accepted human-readable shape and transaction sequence.
- Propose focused automated coverage for DTO validation, materialized root
  update shape, skipped unsupported `typ + update` operations, missing-target
  behavior, idempotent repeated property-reference handling, conflicting
  property-reference handling, and the narrowest practical Kafka/Mongo
  integration check.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not implement property-value assignment materialization, required property
  enforcement, semantic validation that `data.property_id` resolves,
  permission enforcement, object extension pages, endpoint projection
  maintenance, full Clarity/ESP import, or a nested Kafka operation DSL.
- Do not redesign the ID abbreviation scheme in this task.
- Do not change contents-link read semantics, entity materialization, location
  materialization, or group/admin behavior except where a pre-work finding
  identifies a direct TASK-030 dependency.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify current materializer behavior for `typ + update add_property`,
  including exactly why it is skipped today.
- Identify the smallest implementation/test changes needed for examples, DTO
  tests, materializer update coverage, and integration verification.
- Identify any shape ambiguity that needs director or human review before
  implementation.
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
