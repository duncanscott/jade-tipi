# TASK-032 - Human-readable Kafka property-assignment materialization path

ID: TASK-032
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: IMPLEMENTED
OWNER: human-directed (implemented by Claude Code at Duncan's direction on 2026-06-10; pending director acceptance review)
SOURCE_TASK:
  - TASK-031
  - TASK-030
  - TASK-014
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
- `./gradlew :libraries:jade-tipi-dto:test` — PASSED 2026-06-10.
- `./gradlew :jade-tipi:test` — PASSED 2026-06-10.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*PropertyAssignmentKafkaMaterializeIntegrationSpec*'` — NOT RUN: port
  9092 on this host is held by another project's Kafka broker
  (`pps-kafka-connect` stack), so the jade-tipi Kafka container cannot
  bind and pointing the test at the foreign broker would be wrong. Rerun
  after freeing 9092 with `docker compose -f docker/docker-compose.yml up -d`.
  The integration test source set compiles
  (`./gradlew :jade-tipi:integrationTestClasses`).
