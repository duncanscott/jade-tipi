# TASK-045 - One property model: retire the standalone assignment-root path

ID: TASK-045
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-04)
SOURCE_TASK:
  - TASK-038
  - TASK-040
  - TASK-044
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-045-one-property-model.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/object-property-model-drift.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - libraries/jade-tipi-dto/src/main/resources/example/message/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

DIRECTOR_RESEQUENCING (2026-07-04):
The director selected the first slice of drift-note plan task L: with the
typed shape proven against real data (TASK-042 seed, TASK-043 import), the
transitional standalone `ppy` assignment-root path is deliberately retired
so exactly one property model remains. The rest of task L (inline-bag
endgame, `lnk` alignment, legacy `txn` message rows, hard `data.id` schema
validation with the remaining fixture-ID normalization) stays ledgered.

GOAL:
Make the object-targeted `property_values` projection the only property
assignment model: every `ppy + create` assignment materializes onto the
target object root; the legacy `entity_id`-only wire form becomes a
deprecated alias for `object_collection: "ent"`; the standalone
assignment-root write path and the entity-only reader are removed; all
composed read views resolve child entities through the generic
`ObjectPropertyValuesReadService`.

ACCEPTANCE_CRITERIA:
- Materializer: all `kind == "assignment"` messages route to the
  object-targeted projection. Explicit `object_collection`/`object_id`
  win; a payload carrying only `entity_id` resolves as
  (`ent`, `entity_id`) with a deprecation warning; neither present is
  `skippedInvalid`. `processPpyAssignmentCreate` and the standalone-root
  insert are deleted; `data.id` is ignored on assignments.
- Canonical examples 07/08 convert to the object-targeted form and keep
  round-tripping in `MessageSpec`.
- `GET /api/entities/{id}/property-values` swaps onto
  `ObjectPropertyValuesReadService` (response shape becomes the generic
  `propertyValues` record; 404 contract unchanged).
  `EntityPropertyValuesReadService`, `EntityPropertyValuesRecord`, and
  `EntityPropertyValueRecord` are deleted.
- `PlateContentsReadService` and `LocationContentsReadService` resolve
  child entities through the generic reader; their entry records embed
  `ObjectPropertyValuesRecord`.
- Unit and controller specs updated for the new shapes; the materializer
  object-property spec covers the alias routing; the legacy-path routing
  feature is replaced by alias coverage.
- `PropertyAssignmentKafkaMaterializeIntegrationSpec` is repurposed: the
  registered assignment (including one sent in the legacy alias form)
  projects onto the `ent` root's `property_values`; no standalone root is
  created; the unregistered assignment is still gated; fixture IDs become
  convention-conformant. `EntityPropertyValuesHttpReadIntegrationSpec`
  seeds object-form assignments and asserts the generic response shape,
  with conformant fixture IDs.
- Vocabulary doc: the standalone-root "current implementation" paragraphs
  are replaced by the unified model with the `entity_id` alias note; the
  entity-reading section describes the generic reader; the drift note 8.5
  records this task-L slice.

OUT_OF_SCOPE:
- No `message.schema.json` change (hard `data.id` validation and the
  remaining TASK-030/031-era fixture normalization stay ledgered).
- No MongoDB migration of existing standalone assignment roots (they
  remain readable as historical rows; nothing writes new ones).
- No inline-`properties`-bag endgame, no `lnk` alignment, no `msg` split.
- No value-update semantics.

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- Materializer: every `kind == "assignment"` message routes to the
  object-targeted projection; a payload with neither `object_collection` nor
  `object_id` resolves the deprecated `entity_id` alias as (`ent`,
  `entity_id`) with a warning; `processPpyAssignmentCreate` and
  `isObjectTargetedAssignment` are deleted (~110 lines); the class Javadoc
  documents the single unified assignment bullet.
- Canonical examples 07/08 converted to the object-targeted form (IDs
  unchanged; segment normalization stays ledgered); the three MessageSpec
  consistency features updated to the new shape.
- Reader swap: `EntityPropertyValuesReadService`,
  `EntityPropertyValuesRecord`, and `EntityPropertyValueRecord` deleted;
  `GET /api/entities/{id}/property-values` delegates to
  `ObjectPropertyValuesReadService('ent', id)` (path and 404 contract
  unchanged, response is the generic `propertyValues` shape);
  `PlateContentsReadService` and `LocationContentsReadService` resolve child
  entities through the generic reader and their entry records embed
  `ObjectPropertyValuesRecord`. The TASK-041 records gained the read-record
  `@Immutable` convention (required once embedded in immutable entries);
  `appliedAt` is typed `Instant` with tolerant Date coercion in the reader.
- Specs: nine legacy standalone-root features removed from the materializer
  spec; the mixed-message feature asserts the assignment as a projection;
  the object-property spec's routing feature became alias coverage; both
  composer service/controller specs and the rewritten entity controller
  spec use the generic shapes; the entity HTTP integration spec seeds the
  object form and asserts the generic response plus
  no-standalone-root; `PropertyAssignmentKafkaMaterializeIntegrationSpec`
  repurposed to prove alias resolution end-to-end with
  convention-conformant fixture IDs (removing the last five ID-convention
  warnings from the suite).
- Docs: vocabulary doc unified (definitions-only `ppy`, alias note,
  object-targeted section as the single model, entity-route note); drift
  note 8.5 ledgers this task-L slice and enumerates the remaining task-L
  items.
- Verification results: `:libraries:jade-tipi-dto:test` and
  `:jade-tipi:test` BUILD SUCCESSFUL; full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m13s) with the rewritten
  specs; `git diff --check` clean.
