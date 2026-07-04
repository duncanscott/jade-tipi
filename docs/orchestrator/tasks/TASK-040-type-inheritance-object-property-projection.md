# TASK-040 - Type inheritance and object-targeted property projection

ID: TASK-040
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-038
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-040-type-inheritance-object-property-projection.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/object-property-model-drift.md
  - docs/kli-plate-runbook.md
  - DIRECTION.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/
  - clients/kafka-kli/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

DIRECTOR_RESEQUENCING (2026-07-03):
The director deferred the identity chain (plan tasks B-D: usr resolution,
writer persistence, txn/msg split) and pulled plan task E forward, extended
with type inheritance, to reach a concrete milestone: a complete
kli-driven Kafka transaction that creates a barcode property, a
container/plate/plate-96-well type hierarchy, a typed plate-96-well `loc`
instance, and a barcode value projected onto that instance's root.

GOAL:
Make this message sequence materialize end-to-end, driven by kafka-kli:
open txn -> create `barcode` ppy definition -> create `container` typ ->
`typ + update add_property` barcode on container -> create `plate` typ with
`parent_type_id` container -> create `plate_96_well` typ with
`parent_type_id` plate -> create a `loc` instance with
`type_id` plate_96_well -> object-targeted barcode assignment onto the loc
root -> commit.

CONTEXT:
- Type inheritance is new modeling direction: a `typ + create` may carry an
  inline `parent_type_id` (single inheritance). Storage needs no materializer
  change: inline fields already land under root `properties`, so the parent
  reference materializes as `properties.parent_type_id`.
- The semantic change is the assignment registration gate: a property is
  assignable to an object when it is listed under `properties.property_refs`
  on the object's own `typ` root or on any ancestor reached by following
  `properties.parent_type_id` (bounded depth, cycle-safe).
- The object-targeted assignment message and `property_values` projection
  follow the ratified drift-note contracts 8.2.6/8.2.7: shape-determined
  routing (`object_collection` + `object_id` -> project onto the object
  root; legacy `entity_id`-only -> unchanged standalone `ppy` root path),
  value entry `{ value, txn_id, commit_id, msg_uuid, applied_at }` under
  `property_values.<ppy_id>`, dotted `$set`, equality ignoring `applied_at`.
- No wire-schema change: `parent_type_id`, `object_collection`, and
  `object_id` are snake_case and already valid under `SnakeCaseObject`.
- kafka-kli already supports the flow (`login` tolerates a missing orcid
  claim; the local realm's `kli` client has device grant plus
  `tipi_org`/`tipi_group`/`orcid` mappers; `Collection.fromJson('loc')`
  works). Only the `--collection` help text omits `loc`.

ACCEPTANCE_CRITERIA:
- Materializer routes `ppy + create` assignments with `object_collection`
  (`ent` or `loc`) plus `object_id` onto the target object root as
  `property_values.<property_id>` entries carrying
  `value`/`txn_id`/`commit_id`/`msg_uuid`/`applied_at`.
- Registration gate walks the `parent_type_id` chain: registered on own type
  or any ancestor -> allowed; chain exhausted, ancestor root missing, depth
  cap exceeded, or cycle detected -> `skippedUnregisteredProperty`.
- Object-form validation: unknown `object_collection`, blank `object_id` or
  `property_id`, or non-object `value` -> `skippedInvalid`; missing target
  root -> `skippedMissingTarget`; blank target `type_id` or missing typ ->
  `skippedUnregisteredProperty`.
- Duplicate rules mirror the accepted precedent: existing equal entry
  (ignoring `applied_at`) -> `duplicateMatching`; differing ->
  `conflictingDuplicate`, never overwritten.
- Legacy `entity_id`-only assignments behave byte-for-byte as before
  (strict own-type gate, standalone `ppy` roots); existing TASK-031/032/033
  examples and tests stay green.
- Canonical envelope examples added for the two new shapes (typ create with
  `parent_type_id`; object-targeted loc assignment) and registered in
  `MessageSpec` (round-trip + schema validation).
- kli `--collection` help text lists `loc`.
- A kli runbook (`docs/kli-plate-runbook.md`) walks the full sequence with
  data-only JSON files under `clients/kafka-kli/examples/plate-96-well/`,
  including login, publish steps, commit, and MongoDB/HTTP inspection.
- Kafka integration spec proves the full sequence end-to-end: typ hierarchy
  roots with `properties.parent_type_id`, typed `loc` root, and the barcode
  entry under `property_values` with transaction provenance.
- Docs updated: DIRECTION.md gains the single-inheritance direction;
  vocabulary doc gains a Type Inheritance section and current-behavior text
  for object-targeted assignment; drift note 8.5 records the resequencing.

OUT_OF_SCOPE:
- No usr/writer/txn-split work (deferred plan tasks B-D).
- No `msg` staging, applied watermark, or cleanup (plan tasks D/F/G).
- No generic object property-value read service or overlay (plan tasks J/K).
- No value validation against the `ppy` definition `value_schema`.
- No multiple inheritance, no property overriding/shadowing semantics, no
  required-property enforcement.
- No changes to the legacy standalone assignment-root path beyond routing.
- No `message.schema.json` changes.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy :jade-tipi:compileTestGroovy :jade-tipi:compileIntegrationTestGroovy`
- `./gradlew :libraries:jade-tipi-dto:test`
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*PlateTypeHierarchyKafka*'`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*PropertyAssignmentKafkaMaterializeIntegrationSpec*'` (legacy coexistence)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-03):
- Materializer (`CommittedTransactionMaterializer`): object-targeted
  assignment routing (`isObjectTargetedAssignment` — a payload carrying
  either object field routes to the object path so incompleteness surfaces
  as `skippedInvalid` instead of silently falling back to legacy);
  `processObjectPropertyAssignment` with the full validation/gate sequence;
  `isPropertyRegisteredInHierarchy`/`checkTypeChain` (depth cap 10,
  visited-set cycle guard, fail-closed on missing ancestors);
  `projectPropertyValue` writing `property_values.<ppy_id>` entries via
  dotted `$set` with `applied_at`-exempt equality
  (`samePropertyValueEntry`). Legacy `entity_id` path untouched.
- Type inheritance needed no storage change: inline `parent_type_id` on
  `typ + create` already lands under root `properties` via
  `buildInlineProperties`.
- Tests: `CommittedTransactionMaterializerObjectPropertySpec` (14 features:
  direct + inherited registration, ent/loc targets, invalid/missing/
  unregistered outcomes, cycle, depth cap, duplicate matching/conflicting,
  legacy routing preserved); existing materializer spec 95/95 unchanged.
  New `PlateTypeHierarchyKafkaMaterializeIntegrationSpec` publishes the full
  ten-message sequence and asserts the materialized hierarchy, the typed
  `loc` root, the projected inherited barcode entry with provenance, no
  standalone root for the object form, and the gated unregistered value.
- Examples: `14-create-plate-type-extends-container.json` and
  `15-assign-object-property-value.json` registered in `MessageSpec`
  (round-trip + schema validation prove no `message.schema.json` change is
  needed).
- kli: `--collection` help and error text now list `loc`; no functional
  change was required (login already tolerates a missing orcid claim and the
  local realm's `kli` client carries device grant plus
  `tipi_org`/`tipi_group`/`orcid` mappers).
- Runbook: `docs/kli-plate-runbook.md` with data-only step files under
  `clients/kafka-kli/examples/plate-96-well/` (00-open through 08-commit),
  MongoDB inspection commands, re-run semantics, and troubleshooting.
- Docs: DIRECTION.md single-inheritance direction; vocabulary doc Type
  Inheritance section, Object-Targeted Property Assignment
  current-implementation section, materialization summary and example list
  updates; drift note 8.5 records the director resequencing (A/E done as
  TASK-039/040, B-D deferred, F-L unchanged).
- Verification results: all compile targets, `:libraries:jade-tipi-dto:test`,
  `:jade-tipi:test`, and `:clients:kafka-kli:installDist` BUILD SUCCESSFUL;
  both Kafka-gated integration specs green (1/1 each) against the local
  Docker stack; `git diff --check` clean.
