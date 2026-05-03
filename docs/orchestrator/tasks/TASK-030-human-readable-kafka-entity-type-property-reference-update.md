# TASK-030 - Human-readable Kafka entity-type property-reference update path

ID: TASK-030
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-029
  - TASK-013
  - TASK-014
NEXT_TASK:
  - VALIDATE-TASK-030-e5986755
  - TASK-031
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

DIRECTOR_PREWORK_REVIEW:
- 2026-05-03: claude-1's pre-work response in
  `docs/agents/claude-1-next-step.md` is accepted. Scope check passed for the
  pre-work turn: the latest claude-1 agent commit changed only
  `docs/agents/claude-1-next-step.md`, which is inside the developer's base
  owned paths for pre-work.
- Proceed with the plan's bounded implementation unit: materialize only
  `typ + update` messages where `data.operation == "add_property"`; preserve
  Kafka as the submission route; do not add HTTP submission endpoints,
  `ppy + create` materialization, semantic `data.property_id` resolution,
  property-value assignment materialization, required-property enforcement,
  broad ID-abbreviation cleanup, or a nested Kafka operation DSL.
- Use `properties.property_refs.<property_id>` as the materialized reference
  location on the root `typ` document. The value is reference metadata only:
  include `required` when it is present on the wire, and do not invent
  `required: false` when it is absent.
- Add the proposed additive `MaterializeResult.skippedMissingTarget` counter
  for supported update messages whose target `typ` root is missing. Treat
  missing/blank `data.id` or `data.property_id` as `skippedInvalid`.
- Repeated `add_property` with matching metadata should be idempotent and count
  as `duplicateMatching` without writing. Repeated `add_property` with
  conflicting metadata should count as `conflictingDuplicate` and must not
  overwrite the existing reference.
- Do not update `_head.provenance` for the successful `add_property` write in
  this task. Updating only `_head.provenance.materialized_at` would make the
  provenance subdocument mix create-message fields with an update timestamp;
  full update history is out of scope for TASK-030.
- Keep the existing example JSON IDs unchanged. It is acceptable for the unit
  test helper to use its local synthetic `~ppy~` property ID unless the
  implementation naturally switches it to the canonical `05-...` `~pp~` value
  in a focused test; do not perform broad ID-abbreviation cleanup.
- Add focused DTO tests for the existing `05-update-entity-type-add-property`
  shape and the `01 -> 02 -> 04 -> 05 -> 09` transaction references. Add
  materializer coverage for positive update shape, unsupported other
  operations, missing target, idempotent repeat, conflicting metadata, and
  missing `property_id`. Extend the narrow Kafka/Mongo integration proof when
  the local Docker/Kafka stack is available; if tooling blocks it, report the
  documented setup command and exact error.
- Director static check on the pre-work artifact passed:
  `git show --check --format=short aab8356 -- docs/agents/claude-1-next-step.md`.
  The orchestrator status check could not run in this sandbox because `tsx`
  failed to open its IPC pipe under `/var/folders/...` with `EPERM` while local
  Node reported `v25.9.0`; per project guidance this is setup/sandbox friction,
  not a product blocker. In a normal shell, use the documented Node 20
  development environment before rerunning the orchestrator status command.

DIRECTOR_IMPLEMENTATION_REVIEW:
- 2026-05-03: Accepted. claude-1 implemented the requested bounded
  `typ + update` `data.operation == "add_property"` materializer path. The
  accepted behavior records references under
  `properties.property_refs.<data.property_id>` on the existing root-shaped
  `typ` document, carries only wire-provided metadata such as `required`, does
  not synthesize `required: false`, does not resolve `data.property_id`
  against `ppy`, and deliberately leaves `_head.provenance` at the original
  create-message values.
- Scope review passed. The implementation changed
  `docs/agents/claude-1-changes.md`, `docs/OVERVIEW.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`.
  These source, test, integration, and documentation paths are outside the
  three base paths listed in `docs/agents/claude-1.md`, but that file allows
  expansion by the active task file, and every changed path is inside
  TASK-030's expanded `OWNED_PATHS`.
- Behavior review passed. The materializer still skips unsupported update and
  delete actions, treats missing `data.id` or `data.property_id` as
  `skippedInvalid` before Mongo reads, counts a missing target root as the new
  `skippedMissingTarget`, treats matching repeated references as
  `duplicateMatching`, and treats conflicting reference metadata as
  `conflictingDuplicate` without overwriting. The implementation did not add
  HTTP submission endpoints, `ppy + create` materialization, semantic
  property-reference validation, property-value assignment materialization,
  required-property enforcement, permission enforcement, contents-link read
  changes, endpoint projection maintenance, broad ID-abbreviation cleanup, or
  a nested Kafka operation DSL.
- Assertion review passed. DTO tests now pin the existing
  `05-update-entity-type-add-property.json` wire shape and the
  `01 -> 02 -> 04 -> 05 -> 09` reference sequence. Materializer tests cover
  the positive `$set` shape, omitted `required`, unsupported other
  operations, missing target, idempotent repeat, conflicting metadata,
  missing `data.id`, missing `data.property_id`, and preserving unrelated
  existing properties. The opt-in Kafka integration now proves
  `open + typ + typ-update-add_property + ent + commit` through Kafka, Mongo
  `txn`, and materialized `typ`/`ent` roots.
- Director static verification passed `git diff --check HEAD^..HEAD`.
  Director Gradle rerun was blocked before product tests by sandbox/tooling
  permissions opening
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck`
  with `Operation not permitted` while running
  `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`. In a normal
  developer shell, use
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` for
  Mongo-backed unit tests, `docker compose -f docker/docker-compose.yml up -d`
  for the full Kafka/Mongo stack when the integration suite is needed, run
  `./gradlew --stop` only if stale Gradle daemons are implicated, then rerun
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest` passing with the
  local Docker stack healthy.
- Created `TASK-031` for pre-work on the next bounded human-readable Kafka
  increment: `ppy + create` property-definition materialization. Property-value
  assignment materialization, required-property enforcement, semantic
  validation, and ID-abbreviation cleanup remain future work.
