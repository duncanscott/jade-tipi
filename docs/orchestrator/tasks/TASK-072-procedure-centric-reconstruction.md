# TASK-072 - Procedure-centric ESP workflow reconstruction (batch/pool)

ID: TASK-072
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_IMPLEMENTATION
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
tasks). A procedure root carries two ID-keyed maps alongside the
existing `output_input`:
- `tasks: { <task_id>: {} }` — the task inputs (value objects empty for
  now).
- `inputs: { <input_id>: { task_id: <task_id>, ... } }` — the NON-task
  inputs. When a task carried the object into the procedure, the object
  is modeled as an input **independent of the task**, with its input
  entry's `task_id` back-referencing the delivering task.
- `output_input: { <output_id>: { <input_id>: {} } }` — unchanged
  (outputs and their contributing non-task inputs).

Worked example (SOW Item task carries the NA into Aliquot Creation):
`tasks: {sow_id: {}}`, `inputs: {na_id: {task_id: sow_id}}`,
`output_input: {aliquot_id: {na_id: {}}}`. A task-free object input:
`inputs: {obj_id: {}}`, `tasks: {}`. A pool: every member library is an
`inputs` entry (each with its `task_id` if delivered by a task), so ALL
inputs are captured — the multi-input defect dissolves.

**Links are KEPT** (director ruling): the maps are the canonical record,
and the `procedure_input`/`fulfills`/`produced_by` links remain for graph
traversal (mirrors the clarity procedure model, which carries both
`output_input` and links). Fix the `procedure_input` link id to include
the input uuid (TASK-070's omits it and collides across carriers).

Schema/materializer (general, not just the importer): the wire schema's
`ProcedureData` branch must allow `tasks` and `inputs` (same ID-keyed,
snake_case-key shape as `output_input`), and the materializer must
hoist/store them on the prc root like `output_input`. General to the
JDTP procedure model — clarity procedures could adopt the maps later;
clarity's existing `output_input` stays valid. Spec §1.9 gets the maps
and a version bump when this task lands.

DESIGN DIRECTION:
- Reconstruct **procedure-centrically**: a procedure is identified by its
  `sample_sheet_uuid` (with the TASK-070 protocol-count merge for
  multi-sheet instances). Gather ALL input-side carriers of that sheet,
  unioning their tasks into `tasks`, their carried/direct objects into
  `inputs` (with `task_id` back-refs), and all outputs into
  `output_input` + `produced_by`.
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
