# TASK-032 - Human-readable Kafka property-assignment materialization path

ID: TASK-032
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: human-directed (implemented by Claude Code at Duncan's direction on 2026-06-10; director acceptance review recorded 2026-06-12)
SOURCE_TASK:
  - TASK-031
  - TASK-030
  - TASK-014
NEXT_TASK:
  - TASK-033
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-032-human-readable-kafka-property-assignment-materialization.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Implement the smallest Kafka-first increment that lets human-readable
`ppy + create` messages with `data.kind == "assignment"` materialize as
root-shaped assignment records in the `ppy` collection, gated by the
DIRECTION.md rule that a property must be added to the type before
clients may assign that property to an object of the type.

ACCEPTANCE_CRITERIA:
- Preserve Kafka as the primary submission route. No HTTP data submission
  endpoints.
- Materialize `ppy + create` `data.kind == "assignment"` messages as their
  own root-shaped `ppy` documents per the accepted vocabulary ("a property
  assignment is stored as a property record whose ID is the entity ID plus
  the property ID"): `_id == data.id`, `collection == "ppy"`,
  `type_id == null`, inline `properties.kind`, `properties.entity_id`,
  `properties.property_id`, verbatim object-shaped `properties.value`,
  empty `links`, and `_head.provenance` pointing at the assignment message.
- Enforce the type-registration gate before insert:
  - missing target `ent` root → `skippedMissingTarget`;
  - entity root with missing/blank `type_id`, missing `typ` root, or no
    `properties.property_refs.<property_id>` entry →
    `skippedUnregisteredProperty` (new MaterializeResult counter);
  - missing/blank `data.id`, `data.entity_id`, `data.property_id`, or a
    missing/non-object `data.value` → `skippedInvalid`.
- Reuse the shared duplicate rules: identical payloads are
  `duplicateMatching`; differing payloads are `conflictingDuplicate` and
  never overwritten.
- Do not resolve `data.property_id` against the `ppy` collection, do not
  validate `data.value` against the registered `value_schema`, and do not
  rewrite the entity root's `properties` map (assignment projection onto
  entity roots remains future work, like `lnk` endpoint projections).
- Keep `ppy + create` messages with missing, blank, or unknown `data.kind`
  values `skippedUnsupported`.
- Add the missing canonical registration example
  (`05a-update-entity-type-add-property-volume.json`) so both canonical
  assignments (`07`, `08`) satisfy the gate inside the one example
  transaction; preserve all existing example ID strings.
- Focused automated coverage for the materialized root shape, every gate
  outcome, invalid-field handling, idempotent and conflicting duplicates,
  the mixed-snapshot order/count change, the new example's DTO shape, the
  canonical registration invariant, and a dedicated opt-in Kafka/Mongo
  integration spec covering one registered and one unregistered assignment.

OUT_OF_SCOPE:
- No HTTP data submission endpoints.
- No value-shape validation against `data.value_schema`, no required-property
  enforcement, no semantic `property_id` resolution against `ppy`, no
  permission enforcement, no object extension pages, no endpoint projection
  maintenance, no assignment projection onto entity roots, no broad
  ID-abbreviation cleanup, no nested Kafka operation DSL.
- No contents-link read changes, entity/location materialization changes, or
  group/admin behavior changes.

IMPLEMENTATION_SUMMARY:
- `CommittedTransactionMaterializer`: `isSupported()` now accepts
  `ppy + create` for `data.kind == "assignment"`; new
  `processPpyAssignmentCreate()` implements the gate chain
  (ent root → `type_id` → `typ` root → `property_refs` entry) and inserts
  through the shared `buildDocument()`/`handleInsertError()` path; added
  `FIELD_ENTITY_ID`, `FIELD_VALUE`, `KIND_ASSIGNMENT` constants and the
  generic `extractNonBlankString()` helper (now backing
  `extractPropertyId()`).
- `MaterializeResult`: new `skippedUnregisteredProperty` counter.
- New example `05a-update-entity-type-add-property-volume.json` registers
  the `volume` property-definition on `plate_96` with `required: false`.
- `MessageSpec`: example list extended; focused `05a` shape test; new
  invariant test that every canonical assignment's `property_id` is
  registered on the target entity's type within the example transaction and
  that assignment IDs follow the `<entity_id>~<property_id>` convention.
- `CommittedTransactionMaterializerSpec`: replaced the obsolete
  assignment-skipped test with ten new tests covering the materialized
  shape, all gate outcomes, invalid fields, non-object values, duplicate
  matching/conflict, and extended the mixed-snapshot order test.
- New `PropertyAssignmentKafkaMaterializeIntegrationSpec` (opt-in via
  `JADETIPI_IT_KAFKA=1`) publishes the full property-loop transaction and
  asserts the registered assignment root materializes while the
  unregistered one is gated.
- `PropertyDefinitionCreateKafkaMaterializeIntegrationSpec` comments
  updated: its trailing assignment is now gated by the missing target
  entity rather than counted `skippedUnsupported`.
- Docs: `docs/architecture/kafka-transaction-message-vocabulary.md`
  (assignment materialization semantics, supported-projection list,
  reference example list) and `docs/OVERVIEW.md` (next-steps summary).

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test` — PASSED 2026-06-10; rerun
  PASSED 2026-06-12 (67 tests, 0 failures).
- `./gradlew :jade-tipi:test` — PASSED 2026-06-10; rerun PASSED 2026-06-12
  (223 tests, 0 failures, including CommittedTransactionMaterializerSpec at
  95 tests).
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*PropertyAssignmentKafkaMaterializeIntegrationSpec*'` — originally NOT RUN
  on 2026-06-10: port 9092 on this host was held by another project's Kafka
  broker (`pps-kafka-connect` stack), so the jade-tipi Kafka container could
  not bind, and pointing the test at the foreign broker would be wrong (only
  `./gradlew :jade-tipi:integrationTestClasses` was verified then). PASSED
  2026-06-12 after port 9092 was freed: the stack came up healthy via
  `docker compose -f docker/docker-compose.yml up -d` (kafka-init exited 0)
  and the spec ran green against live Kafka on localhost:9092 (1 test,
  0 failures), proving the registered assignment materializes and the
  unregistered assignment is gated end-to-end.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*PropertyDefinitionCreateKafkaMaterializeIntegrationSpec*'` — PASSED
  2026-06-12 (1 test, 0 failures), covering this task's comment-level
  amendment (the spec's trailing assignment is now gated by the missing
  target entity; its never-materialized assertion is unchanged).
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest` (full opt-in
  suite) — PASSED 2026-06-12: 27 tests, 0 failures, 3 skipped (all three in
  `GroupAdminAuthIntegrationSpec`, which gates itself behind a separate
  opt-in flag). Setup note: the jade-tipi `couchdb`/`couchdb-init` containers
  could not start because another project's container holds 127.0.0.1:5984;
  no jade-tipi source or test references CouchDB, so the suites above were
  unaffected.

DIRECTOR_IMPLEMENTATION_REVIEW:
- DATE: 2026-06-12. RESULT: accepted. Commit `482034a` ("Materialize ppy
  assignments gated by type registration (TASK-032)") was implemented
  human-directed (Duncan + Claude Code) rather than through a developer
  worktree turn.
- SCOPE_CHECK: passed with one recorded exception. The commit changed
  `docs/OVERVIEW.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  this task file,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`,
  the new
  `libraries/jade-tipi-dto/src/main/resources/example/message/05a-update-entity-type-add-property-volume.json`,
  the new
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/PropertyAssignmentKafkaMaterializeIntegrationSpec.groovy`,
  and comment updates in
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/PropertyDefinitionCreateKafkaMaterializeIntegrationSpec.groovy`
  — all inside this task's `OWNED_PATHS` — plus `docker/docker-compose.yml`,
  which is outside them. The docker change (an opt-in `mongo-express` tools
  profile for browsing materialized collections) was human-directed in the
  same commit and is ratified as an accepted scope exception; the stricter
  Orchestrator Protocol Direction in `DIRECTIVES.md` remains in force for
  developer turns.
- BEHAVIOR_REVIEW: passed. Every acceptance criterion is satisfied by the
  committed code, tests, examples, and docs: the materialized assignment root
  shape (`_id == data.id`, conventionally `<entity_id>~<property_id>` — the
  composite format is pinned by the DTO example tests, not enforced by the
  materializer, which copies `data.id` verbatim; `collection ==
  "ppy"`, `type_id == null`, inline `properties.kind`/`entity_id`/
  `property_id`, verbatim object-shaped `properties.value`, empty `links`,
  `_head.provenance`); the type-registration gate ordered
  ent root → `type_id` → `typ` root → `properties.property_refs.<property_id>`
  with `skippedMissingTarget` and the new `skippedUnregisteredProperty`
  counter incremented on the correct branches; missing/blank identity fields
  and non-object values as `skippedInvalid`; missing/blank/unknown `data.kind`
  still `skippedUnsupported`; shared idempotent/conflicting duplicate rules
  via `handleInsertError`/`isSamePayload`. No OUT_OF_SCOPE leak: no HTTP
  submission endpoint, no value-shape validation against `value_schema`, no
  semantic `property_id` resolution against `ppy`, and no entity-root
  rewrite (asserted by the unit spec's zero-update expectations and the
  integration spec's untouched `ent` root `properties`).
- ASSERTION_REVIEW: passed. `MessageSpec` pins the `05a` registration shape,
  the registration invariant (every canonical assignment's `property_id` is
  registered on the target entity's type within the example transaction), and
  the `<entity_id>~<property_id>` ID convention. The materializer spec covers
  the materialized shape, every gate outcome, invalid fields, non-object
  values, duplicate matching/conflict, and the extended mixed-snapshot
  order/count expectations. The opt-in integration spec proves one registered
  and one unregistered assignment end-to-end, including same-transaction
  registration-then-assignment ordering through the snapshot loop against
  real Mongo.
- NON_BLOCKING_NOTE: an assignment message that carried a top-level
  `data.properties` map would bypass the inline-properties projection in
  `buildDocument`; the canonical examples and tests do not use that shape.
  Recorded as a latent shape hazard for a future bounded follow-up, not a
  defect in this task.
- VERIFICATION: see the dated entries in the VERIFICATION section above; the
  deferred Kafka integration spec passed on 2026-06-12 once port 9092 was
  freed, alongside full dto/unit/integration suite reruns.
- FOLLOW_UP: `TASK-033` was created for pre-work on the entity
  property-values read service (the read-side join of an `ent` root with its
  materialized `ppy` assignment roots), following the accepted
  TASK-015/TASK-016 read-service and HTTP-adapter pattern.
