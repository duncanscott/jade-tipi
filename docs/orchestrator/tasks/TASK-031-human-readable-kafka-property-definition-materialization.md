# TASK-031 - Human-readable Kafka property-definition materialization path

ID: TASK-031
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-030
  - TASK-013
  - TASK-014
NEXT_TASK:
  - TASK-032
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
  - docs/orchestrator/tasks/TASK-031-human-readable-kafka-property-definition-materialization.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Plan the smallest Kafka-first increment that lets human-readable
`ppy + create` messages with `data.kind == "definition"` materialize
root-shaped property-definition documents, starting from the existing
`02-create-property-definition-text.json` and
`03-create-property-definition-numeric.json` examples.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  the accepted TASK-013/TASK-014 root-document notes, and TASK-026 through
  TASK-030's accepted Kafka submission reviews before planning.
- Preserve Kafka as the primary submission route. Do not add HTTP data
  submission endpoints.
- Inspect `02-create-property-definition-text.json`,
  `03-create-property-definition-numeric.json`,
  `07-assign-property-value-text.json`, `08-assign-property-value-number.json`,
  DTO validation, `CommittedTransactionMaterializer` support/skipping behavior,
  and existing duplicate handling around materialized root documents.
- Identify the intended human-readable property-definition wire shape:
  top-level `collection: "ppy"` and `action: "create"`,
  `data.kind: "definition"`, `data.id` as the property-definition ID,
  `data.name` as the human-readable property name, and `data.value_schema` as
  the submitted JSON-object value contract.
- Preserve the current example ID strings unless the plan identifies a
  necessary local synthetic ID in tests. Do not perform broad ID-abbreviation
  cleanup.
- Decide the smallest materialized root shape for property definitions in the
  `ppy` collection, including which facts should live under root
  `properties`, whether `data.kind` remains materialized as a discriminator,
  and how `value_schema` should be copied without validating future assignment
  values.
- Decide whether this bounded proof should materialize only
  `data.kind == "definition"` and continue skipping
  `data.kind == "assignment"` property-value examples, or whether a separate
  follow-up task is required for assignments.
- Add or update example resource JSON only if the existing examples do not
  already show the accepted human-readable shape and transaction sequence.
- Propose focused automated coverage for DTO validation, materialized root
  shape, skipped assignment behavior if assignment remains deferred,
  missing/blank `data.id`, idempotent duplicate handling, conflicting
  duplicate handling, and the narrowest practical Kafka/Mongo integration
  check.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not implement property-value assignment materialization, required
  property enforcement, semantic validation that `typ.data.property_id`
  resolves, semantic validation that assignments reference allowed properties,
  value-shape validation against `data.value_schema`, permission enforcement,
  object extension pages, endpoint projection maintenance, full Clarity/ESP
  import, or a nested Kafka operation DSL.
- Do not redesign the ID abbreviation scheme in this task.
- Do not change contents-link read semantics, entity materialization, location
  materialization, type-reference update behavior, or group/admin behavior
  except where a pre-work finding identifies a direct TASK-031 dependency.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify current materializer behavior for `ppy + create` definition and
  assignment messages, including exactly why they are skipped today.
- Identify whether the existing `02` and `03` examples already provide the
  desired human-readable property-definition shape, including any ID suffix or
  collection-abbreviation mismatches that need director review.
- Identify the smallest implementation/test changes needed for examples, DTO
  tests, materializer coverage, and integration verification.
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

DIRECTOR_PREWORK_REVIEW:
- DATE: 2026-05-03
- RESULT: accepted for implementation.
- SCOPE_CHECK: passed. claude-1's latest pre-work commit changed only
  `docs/agents/claude-1-next-step.md`, which is within the developer's base
  owned paths. `git diff --check HEAD^..HEAD` passed.
- TOOLING_NOTE: director attempted the documented orchestrator status command
  from `/Users/duncanscott/orchestrator/tools/agent-orchestrator`, but local
  `tsx` failed before status inspection with `listen EPERM` on its IPC pipe,
  including with `TMPDIR=/private/tmp`. Treat this as local tooling/sandbox
  friction, not a product blocker. In a normal operator shell, rerun
  `npm run dev -- status --config /Users/duncanscott/orchestrator/jade-tipi/config/orchestrator.local.json`;
  if tool dependencies are stale or missing, the documented setup refresh is
  `npm install` in the orchestrator tool checkout.
- IMPLEMENTATION_DIRECTION: use the pre-work default proposals. Materialize
  only `ppy + create` messages whose `data.kind == "definition"`. Continue to
  count `ppy + create` assignments, missing/blank kinds, and unknown kinds as
  `skippedUnsupported`.
- MATERIALIZED_SHAPE: write a root-shaped `ppy` document through the existing
  create pipeline. Keep `_id`/`id == data.id`, `collection == "ppy"`,
  `type_id == null`, `links == {}`, and `_head.provenance`. Copy
  `data.kind`, `data.name`, and `data.value_schema` under root `properties`.
  Treat `value_schema` as an opaque verbatim JSON object; do not validate
  assignment values against it.
- ID_DIRECTION: preserve all example resource ID strings. For local synthetic
  unit-test IDs, avoid broad ID-abbreviation cleanup; if the existing helper is
  renamed anyway, it may be aligned to the canonical `~pp~` segment only within
  that helper/test fixture.
- COVERAGE_DIRECTION: add focused DTO tests for the existing `02`/`03`
  definition shapes and the `07` assignment shape/cross-references; add
  materializer unit coverage for definition materialization, assignment and
  non-definition skipping, missing/blank `data.id`, idempotent duplicates,
  conflicting duplicates, and the mixed-snapshot count/order change. Add a
  dedicated narrow Kafka/Mongo integration spec for property-definition
  materialization when local Docker is available.
- OUT_OF_SCOPE_CONFIRMATION: do not add HTTP submission endpoints,
  property-value assignment materialization, semantic `property_id` or
  assignment/type validation, required-property enforcement, value-shape
  validation, permission enforcement, contents-link read changes, endpoint
  projection maintenance, broad ID cleanup, or a nested Kafka operation DSL.

DIRECTOR_IMPLEMENTATION_REVIEW:
- DATE: 2026-06-12. RESULT: accepted. The implementation landed in claude-1's
  commit `7ccbf00` and was reported `STATUS: COMPLETED` in
  `docs/agents/claude-1-changes.md` on 2026-05-03; this acceptance is recorded
  retroactively: the task file was never advanced past
  `READY_FOR_IMPLEMENTATION`, and `DIRECTIVES.md` still listed TASK-031 as
  `READY_FOR_PREWORK`, until the 2026-06-12 close-out.
- SCOPE_CHECK: passed. The implementation turn changed
  `docs/agents/claude-1-changes.md`, `docs/OVERVIEW.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and the new
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/PropertyDefinitionCreateKafkaMaterializeIntegrationSpec.groovy`.
  Every changed path is inside TASK-031 `OWNED_PATHS` plus claude-1's base
  report path.
- BEHAVIOR_REVIEW: passed against the `DIRECTOR_PREWORK_REVIEW` rulings.
  `isSupported()` accepts `ppy + create` only when
  `data.kind == "definition"`; the existing inline-properties fallback
  projects `kind`, `name`, and the opaque verbatim `value_schema` under root
  `properties`; the materialized root carries `_id`/`id == data.id`,
  `collection == "ppy"`, `type_id == null`, `links == {}`, and standard
  `_head.provenance`. No new `MaterializeResult` counter, no schema-file
  change, and no example-resource edit was needed. At that commit,
  `kind == "assignment"`, missing, blank, and unknown kinds stayed
  `skippedUnsupported`; the assignment path is now separately accepted
  through `TASK-032`.
- ASSERTION_REVIEW: passed. Focused DTO features pin the `02`/`03` definition
  shapes, the `07` assignment shape, and the `01 + 02 + 04 + 06 + 07 + 09`
  cross-reference chain. Materializer features cover the positive definition
  root shape, assignment/missing/unknown-kind skips, missing/blank `data.id`
  as `skippedInvalid`, idempotent and conflicting duplicates, and the updated
  mixed-snapshot order/counts. The dedicated opt-in Kafka/Mongo integration
  spec proves `open + definition + commit` end-to-end.
- CREDITED_DEVELOPER_VERIFICATION (2026-05-03):
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*PropertyDefinitionCreateKafkaMaterializeIntegrationSpec*'`, and the
  `'*EntityCreateKafkaMaterializeIntegrationSpec*'` regression all reported
  passing with the local Docker stack healthy.
- DIRECTOR_VERIFICATION (2026-06-12, develop at `482034a`): full reruns
  passed with the local Docker stack healthy —
  `:libraries:jade-tipi-dto:test` (67 tests), `:jade-tipi:test` (223 tests),
  the PropertyDefinition and PropertyAssignment Kafka integration specs, and
  the full `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
  (27 tests, 0 failures, 3 skipped under `GroupAdminAuthIntegrationSpec`'s
  separate opt-in gate). Note: accepted `TASK-032` re-routed the trailing
  assignment's skip outcome in the materializer (now gated
  `skippedMissingTarget` rather than counted `skippedUnsupported`, asserted
  by the materializer unit spec) and updated this task's integration spec
  comments accordingly; the spec's own assertion — the assignment document is
  never materialized — is unchanged, and the rerun verifies it as amended.
- FOLLOW_UP: `TASK-032` (assignment materialization) was implemented
  human-directed on 2026-06-10 and accepted on 2026-06-12. `TASK-033` was
  created for entity property-values read-service pre-work.
