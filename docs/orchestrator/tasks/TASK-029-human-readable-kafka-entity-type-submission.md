# TASK-029 - Human-readable Kafka entity-type submission path

ID: TASK-029
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
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

DIRECTOR_PREWORK_REVIEW:
- Reviewed claude-1's pre-work in `docs/agents/claude-1-next-step.md`.
  The pre-work stayed within the developer's pre-work ownership boundary:
  only `docs/agents/claude-1-next-step.md` was identified as the developer
  artifact for this turn.
- Proceed with the default proposals in the pre-work unless contradicted below:
  leave `04-create-entity-type.json` unchanged; materialize bare
  `typ + create` by dropping the `data.kind == "link_type"` support gate;
  remove now-unused materializer constants if they become dead; rely on the
  existing link-type materialization test for the preserved path; extend the
  mixed-message snapshot test in place; defer `typ + update add_property`
  materialization to a later task; and do not perform ID-abbreviation cleanup.
- Amend the planned documentation update: the
  `docs/architecture/kafka-transaction-message-vocabulary.md` materialization
  sentence is currently stale and should list the full current supported set
  after this task, including `ent + create` as well as `loc + create`,
  `typ + create` for both link-type and bare entity-type records,
  `lnk + create`, and `grp + create`. Keep `typ + update` property-reference
  changes explicitly unsupported/deferred.
- If extending `EntityCreateKafkaMaterializeIntegrationSpec.groovy`, update the
  spec description from `open + ent + commit` to the new
  `open + typ + ent + commit` flow, add a `typ` collection constant, wait/assert
  the materialized bare entity-type root, and clean up the generated `typ`
  document in `cleanup()`.
- Use the project-documented setup commands if verification is blocked:
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` for
  Mongo-backed unit tests, `docker compose -f docker/docker-compose.yml up -d`
  for the full Kafka/Mongo stack, and `./gradlew --stop` only when stale Gradle
  daemons are implicated. Report exact blocked commands/errors rather than
  treating local setup friction as a product blocker.

DIRECTOR_IMPLEMENTATION_REVIEW:
- 2026-05-03: Requested changes before acceptance. claude-1 implemented the
  main bare entity-type `typ + create` path and added useful DTO,
  materializer, duplicate, skipped-`typ + update`, mixed-snapshot, and
  Kafka/Mongo integration assertions, but one accepted wire-shape variant is
  not handled correctly.
- Blocking finding: the task's accepted shape says `data.links` for a bare
  entity-type `typ + create` may be absent or empty. The current materializer
  only produces the right root shape when `data.links` is absent or when
  `data.properties` is present. If a valid flat bare entity-type payload
  includes `data.links: {}` without `data.properties`, `buildDocument()` falls
  through to `buildInlineProperties(data)`, and `buildInlineProperties()`
  excludes only `id` and `type_id`. That means the empty `links` object is
  copied into root `properties.links` instead of being treated only as root
  `links: {}`. Fix this narrow case and add a focused assertion proving an
  otherwise-flat bare entity-type `typ + create` with `links: [:]`
  materializes with root `links == [:]` and no `properties.links`.
- Scope review: the implementation changed
  `docs/agents/claude-1-changes.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`.
  Those source/test/doc paths are inside this task's expanded
  implementation-owned paths, and the report file is inside claude-1's base
  assignment paths. They are not limited to the three static
  `docs/agents/claude-1*` base paths listed in `docs/agents/claude-1.md`; the
  active task file explicitly expands implementation ownership for TASK-029.
- Accepted parts to preserve: do not add HTTP data submission endpoints,
  property-assignment materialization, required-property enforcement, semantic
  `type_id` resolution, permission enforcement, endpoint projection
  maintenance, contents-link read changes, broad ID-abbreviation cleanup, or a
  nested Kafka operation DSL. Keep `typ + update` property-reference changes
  unsupported/deferred. Keep the existing `04-create-entity-type.json` example
  unchanged unless the narrow fix proves an example edit is strictly required.
- Director verification: `git diff --check origin/director..HEAD` passed.
  Local Gradle verification in the Codex sandbox was blocked before product
  tests by Gradle wrapper cache permissions:
  `./gradlew :jade-tipi:test --tests 'org.jadetipi.jadetipi.service.CommittedTransactionMaterializerSpec' --console=plain`
  failed opening
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck`
  with `Operation not permitted`. In a normal developer shell, use
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` for
  Mongo-backed unit tests, `docker compose -f docker/docker-compose.yml up -d`
  for the full Kafka/Mongo stack, run `./gradlew --stop` only if stale Gradle
  daemons are implicated, then rerun
  `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`.
