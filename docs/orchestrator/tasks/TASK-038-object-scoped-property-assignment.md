# TASK-038 - Object-scoped property-value assignment

ID: TASK-038
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: unassigned
SOURCE_TASK:
  - TASK-032
  - TASK-031
  - TASK-030
  - TASK-026
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/object-property-model-drift.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-038-object-scoped-property-assignment.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - libraries/jade-tipi-dto/src/main/resources/example/message/
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification

GOAL:
Generalize `ppy + create` property-value assignment from entity-scoped to
object-scoped, so any supported domain object (starting with `loc` and `ent`)
can hold type-gated property values. This is the keystone step of the
`object-property-model-drift.md` migration: `DIRECTION.md` specifies that
"each object" is a typed collection of property-value assignments, but the
current materializer hard-wires assignment to `data.entity_id` and an `ent`
root, so locations cannot have property values at all.

CONTEXT:
- See `docs/architecture/object-property-model-drift.md` Section 4 (blast
  radius) and Section 6 (migration order). This task is migration step 2 and
  depends on the Section 5 storage-shape ratification (Option A — keep
  standalone `ppy` assignment roots, join at read).
- The entity assignment path already gates correctly on the target's
  `type_id` -> `typ.properties.property_refs`
  (`CommittedTransactionMaterializer.groovy:320-410`). The only entity-specific
  pieces are the `entity_id` field name and the hard-coded
  `findById(entityId, Map, COLLECTION_ENT)` lookup. This task makes the target
  collection-agnostic; it does not change the gating rule or the storage shape.

ACCEPTANCE_CRITERIA:
- Accept a generic target reference on `ppy + create` assignment `data`. The
  assignment carries `object_id` plus an explicit `object_collection`
  (recommended) naming the target collection. Continue to accept legacy
  `entity_id` as an alias for `object_id` with `object_collection == "ent"` so
  existing TASK-031/032/033 examples, tests, and the `07`/`08` canonical
  messages keep passing unchanged.
- The materializer resolves the target root by `object_id` in the named
  `object_collection` (initially restricted to `loc` and `ent`), and applies
  the existing gate unchanged: the target root must exist, must have a non-blank
  `type_id`, that `typ` root must exist, and it must list the assignment's
  `property_id` under `properties.property_refs`. A target in an unsupported
  collection, a missing target, an untyped target, or a property absent from
  the type's refs is skipped with a clear log message and counts toward the
  existing skipped/unsupported tally (no new MaterializeResult fields unless
  justified in pre-work).
- The assignment root storage shape is unchanged: a standalone `ppy` root keyed
  by `data.id`, carrying `properties.kind == "assignment"`, the target
  reference, `properties.property_id`, and the object-shaped `properties.value`.
  Do not project values onto the target object root.
- Do not change `loc + create`, `ent + create`, `typ`, link, or definition
  materialization. Do not add HTTP submission. Do not change the read views in
  this task.
- Update `message.schema.json` only as needed to permit the new
  `object_id` / `object_collection` assignment fields while preserving the
  existing `entity_id` form.
- Update `docs/architecture/kafka-transaction-message-vocabulary.md`
  "Property Value Assignment" to document the object-scoped form and the
  retained `entity_id` alias.

OUT_OF_SCOPE:
- No location types or location property definitions (that is TASK-039); this
  task only makes the mechanism object-generic. Location assignments become
  usable once TASK-039 supplies typed locations, but this task is verifiable
  now with a test location that is given a `type_id` and a location `typ` with
  `property_refs` inside the test fixture.
- No migration of existing inline name-keyed `loc`/`ent` root properties to
  assignments (that is TASK-040).
- No generic object property-values read view (that is TASK-041).
- No projection of values onto object roots, no required/default properties,
  no permission enforcement, no value-shape validation against `value_schema`.

PREWORK_REQUIREMENTS:
- Confirm the target-resolution mechanism: explicit `object_collection` field
  (recommended) vs. probing supported collections by `object_id`. The
  materializer must not parse IDs to infer collection.
- Identify the exact materializer changes (field constant, target lookup,
  alias handling) and the minimal schema change.
- Identify the test matrix: entity-assignment regression (unchanged behavior),
  new loc-assignment happy path (typed location + property in refs), and gate
  rejections (untyped target, property not in refs, unsupported collection,
  missing target).
- Stop after pre-work. Do not implement until the director advances this task to
  READY_FOR_IMPLEMENTATION and Section 5 of the drift note is ratified.

VERIFICATION:
- `./gradlew :jade-tipi:test` (service specs: object-generic resolution,
  entity regression, gate rejections, legacy `entity_id` alias).
- The narrowest opt-in Kafka/Mongo integration check that publishes a typed
  location plus a `ppy` assignment to it and confirms the materialized
  assignment root, if local Docker is available
  (`JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`).
- `git diff --check`.

DESIGN_NOTES:
- Keystone for the drift migration: TASK-039 (type locations) and TASK-040
  (migrate container fields to assignments) both depend on this mechanism
  existing.
- Keep the change additive. The legacy `entity_id` alias means no existing
  artifact has to change in this task; the cleanup of `entity_id` usage can be
  a later, separate task once all producers emit `object_id`.
