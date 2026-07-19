# TASK-072 - Procedure-centric ESP workflow reconstruction (batch/pool)

ID: TASK-072
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-070
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-072-procedure-centric-reconstruction.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Make ESP workflow → procedure reconstruction correct for batch and
pooling workflows, where a single sample sheet (procedure) is carried by
MANY entity documents. TASK-070 reconstructs per-carrier, which is
correct only for the single-input/single-output pattern; its adversarial
review (2026-07-07) confirmed three entangled defects that all stem from
the per-carrier model.

MOTIVATING DEFECTS (from the TASK-070 review, confirmed against live data):
- **Plate-batch prc collision**: N SOW Items processed on one plate share
  one `sample_sheet_uuid`. Each independently reconstructs the same prc
  id → duplicate prc inserts are counted conflicts and dropped, so the
  prc keeps only ONE carrier's `output_input` while N `produced_by` links
  reference it (internally inconsistent, silently lossy).
- **Pooling owner mis-selection**: a pooling workflow's `input_types`
  includes the pool (output-side) type, so the owner rule can root the
  procedure on the pool with the pool as its own input.
- **Multi-input drop**: only the first biological begat parent is used as
  the procedure input, though `task_input` links all of them.
- (Related) **re-run output double-attribution**: a child in two
  instances' overlapping windows is attributed to both.

PROCEDURE INPUT MODEL (director-ratified 2026-07-08):
Either tasks OR other objects may be inputs to a procedure (not only
tasks). There is **no separate `tasks` map on the prc** (director ruling
2026-07-08): the task relationship is carried by the `fulfills` link and
by the `task_id` back-reference on each input. A procedure root carries
one ID-keyed input map alongside the existing `output_input`:
- `inputs: { <input_id>: { task_id: <task_id>, ... } }` — the object
  inputs. When a task carried the object into the procedure, the object
  is modeled as an input **independent of the task**, with its input
  entry's `task_id` back-referencing the delivering task. A directly
  supplied object input omits `task_id`.
- `output_input: { <output_id>: { <input_id>: {} } }` — unchanged
  (outputs and their contributing inputs).

Worked example (SOW Item task carries the NA into Aliquot Creation):
`inputs: {na_id: {task_id: sow_id}}`,
`output_input: {aliquot_id: {na_id: {}}}`, plus a `fulfills` link
prc→SOW Item. A task-free object input: `inputs: {obj_id: {}}`. A pool:
every member library is an `inputs` entry (each with its `task_id` if
delivered by a task), so ALL inputs are captured — the multi-input defect
dissolves.

**Links are KEPT** (director ruling): the `inputs` map is the canonical
record, and the `procedure_input`/`fulfills`/`produced_by` links remain
for graph traversal (mirrors the clarity procedure model, which carries
both `output_input` and links). Fix the `procedure_input` link id to
include the input uuid (TASK-070's omits it and collides across carriers).

Schema/materializer (general, not just the importer): the wire schema's
`ProcedureData` branch must allow `inputs` (same ID-keyed, snake_case-key
shape as `output_input`), and the materializer must hoist/store it on the
prc root like `output_input`. General to the JDTP procedure model —
clarity procedures could adopt the map later; clarity's existing
`output_input` stays valid. Spec §1.9 gets the map and a version bump
when this task lands.

DESIGN DIRECTION:
- Reconstruct **procedure-centrically**: a procedure is identified by its
  `sample_sheet_uuid` (with the TASK-070 protocol-count merge for
  multi-sheet instances). Gather ALL input-side carriers of that sheet,
  unioning their carried/direct objects into `inputs` (with `task_id`
  back-refs and one `fulfills` link per delivering task), and all outputs
  into `output_input` + `produced_by`.
- This needs a **by-sample-sheet grouping** the per-carrier walk cannot
  provide. Options: (a) a replica view keyed by `sample_sheet_uuid`
  (mirrors clarity phase 2 / esp entity discovery — the preferred shape);
  (b) a two-pass importer aggregation (first pass indexes sheet →
  carriers into the queue/Mongo, second pass emits one prc per sheet).
- Distinguish true input types from pool/output types in the vendored
  config snapshot (add an `output_types` or pool flag) so pooling roots
  on the pooled members, not the pool.

ACCEPTANCE_CRITERIA:
- A plate-batch spec: ≥2 SOW Item carriers sharing one Aliquot Creation
  `sample_sheet_uuid` reconstruct ONE prc whose `output_input`,
  `procedure_input`, and `produced_by` links cover BOTH samples
  consistently.
- A pooling spec: a pooling workflow roots the prc on the pooled member
  libraries as inputs and the pool as output, not the pool-as-input.
- Multi-input procedures record every biological input in `output_input`
  and one `procedure_input` link each (unique link ids).
- Each output attributed to at most one procedure instance.
- Live itest against a real plate-batch process.

OUT_OF_SCOPE:
- The TASK-070 fixes already applied (begat/contents admit `tsk`;
  mint-time id recording; window-less warn).
- Task-type → procedure_type_id linkage; contribution weights.

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented and proven live on a real 17-library pool (PRU40280). Procedures
are reconstructed **procedure-centrically**, grouped by the
`workflow_instance_uuid` (sourced from the enriched service where the bulk
replica lacks it), so all carriers of one run aggregate into a single `prc`
carrying the full `inputs` map. The pooling run that produced the PRU now
carries all 17 libraries as inputs — each back-referencing its delivering
SOW Item via `task_id` — where the per-carrier TASK-070 build recorded one.
All unit suites green (dto, materializer, importer); the esp itest's TASK-072
feature passes end to end (drive → Kafka → materialize).

CHANGES (app-side — the ratified `inputs` map, prerequisite):
- Wire schema `message.schema.json`: `ProcedureData` admits an `inputs` map
  (new `ProcedureInputs` def) — object-ID keys → `{ task_id?, … }` — legal
  only on `prc`, mirroring the `output_input` / `grp`-permissions escape.
- `CommittedTransactionMaterializer`: hoists `inputs` onto the `prc` root,
  parallel to `output_input`, excluded from the inline properties bag.
- Spec `jdtp-specification.md` → 0.8.0-draft, §1.9 Normative: either tasks
  or objects may be inputs; no separate `tasks` map; the task relationship
  rides the `fulfills` link and the `task_id` back-reference.
- `MessageSpec` schema tests (accept the map with `task_id`; reject a
  non-object value).

CHANGES (importer-side — the aggregation):
- `EspEnrichedEntityClient` (new): fetches `workflow_instance_uuid`-bearing
  sample sheets from `GET /api/v2/entities/{uuid}` (the V2 cache-first,
  self-backfilling route). Config `jadetipi.import.esp-api.base-url` /
  `auth-header` / `max-in-memory-mb`; disabled → falls back to local sheets.
- `EspEntityImportMapper`: `mapEntity` no longer emits per-carrier
  procedures; new `workflowInstanceProcedure(accumulator, idFor)` emits one
  `prc` per run — `inputs` map, `output_input`, and `procedure_input`
  (link id now includes the input uuid, fixing the collision) / `fulfills`
  (per task) / `produced_by` (per output) links.
- `ClarityImportDriver`: `accumulateWorkflowInstances` (replaces
  `withWorkflowProcedures`) sources enriched sheets and accumulates each
  carrier's inputs/tasks/outputs by `workflow_instance_uuid` across the
  batch; the batch loop emits aggregated procedures after the items.
- `CouchDbDocumentReader`: raised the WebClient in-memory codec limit
  (`jadetipi.import.couchdb.max-in-memory-mb:64`) — real pool/plate docs
  exceed WebFlux's 256 KB default and blew up the reader.

TESTS:
- `EspEntityImportMapperSpec`: SOW Item → tsk emits no per-entity procedure;
  `workflowInstanceProcedure` builds the `inputs` map with `task_id`
  back-references, `output_input`, and the three link kinds.
- `ClarityImportDriverSpec`: constructor takes the enriched client
  (disabled stub).
- Esp itest: the worked example drives to one aggregated `prc` keyed by
  workflow instance, asserting the `inputs` map (`{NA: {task_id: SOW}}`),
  `output_input`, and the fulfills/procedure_input/produced_by/task_input
  links — with the enriched client stubbed (`@SpringBean`) so it stays
  hermetic.

KNOWN / NOTES:
- The bulk replica predates `workflow_instance_uuid`; until the pps-esp-entity
  backfill completes, the importer sources it per entity from the V2 endpoint
  (~one call per entity during drive, which also backfills prod CouchDB).
- Rebuild the backend after the schema change — an old backend rejects the
  `inputs` map and leaves the prc-referencing links dangling.
- `output_input` records each output against ALL of the run's inputs (the
  replica gives no finer per-output attribution); `reconstructWorkflowInstances`
  now serves only the planner's procedure-type enqueue.
