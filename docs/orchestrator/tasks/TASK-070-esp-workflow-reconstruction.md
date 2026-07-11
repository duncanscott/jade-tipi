# TASK-070 - ESP workflow reconstruction: sample sheets → procedures

ID: TASK-070
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-069
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-070-esp-workflow-reconstruction.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Reconstruct JDTP procedures (and the tasks that carry their inputs) from
ESP sample sheets, the begat graph, and the vendored esplims workflow
configuration — the last import piece and the recorded esp deficiency.
Full design: `importers/jgi-import/docs/bulk-import-design.md`, "ESP
workflow → procedure reconstruction". Grounded in a live survey of
esp-entity and the esplims `content/workflows/*.yml` configs, and in the
director rulings of 2026-07-07 (SOW Items are tasks; sample sheets are
procedures; multi-sheet workflow instances merge by the config protocol
count; vendor a config snapshot).

DESIGN (see the design doc for the full narrative):
- **Vendored config snapshot** (ratified: snapshot, not runtime-read).
  `scripts/regen_esp_workflow_config.py` → `src/main/resources/
  esp-workflow-config.json`: per workflow, its `protocols`,
  `protocol_count`, `input_types`, and a proposed `lab_procedure` flag.
  The importer reads the snapshot; no esplims dependency at build/run.
- **SOW Item class → `tsk`** (type_names SOW Item, PM SOW Item). The task
  carries its Sample-class begat parent as input into the workflows for
  which it has sheets; its begat children are candidate outputs.
- **Procedure = one workflow instance**: a carrier document's sheets
  sharing a `workflow_uuid`, proximal in time, up to `protocol_count`
  from the snapshot. Window = earliest start .. latest end + configurable
  slack. Skipped when `lab_procedure` is false.
- **Type**: `workflow_name` → dynamic `procedure_type`.
- **Inputs**: config `input_types` selects the input — the SOW Item's
  Sample-class begat parent for task-carried workflows, or the carrier
  entity itself for entity-carried workflows (direct sample→sample).
- **Outputs**: the carrier's Sample-class begat children created within
  the window + slack.
- **Links** (§1.9): `task_input`, `fulfills`, `procedure_input`,
  `produced_by`, and the prc `output_input` map.
- Rides the existing esp source lane (queue kind(s), driver dispatch, id
  resolver) added in TASK-069.

ACCEPTANCE_CRITERIA:
- A workflow-config service loads the vendored snapshot and answers
  protocol_count / input_types / lab_procedure per workflow name; a unit
  spec pins the merge count for representative workflows (Aliquot
  Creation=2, Illumina Sequencing=3, Sample QC=1).
- Mapper/planner: SOW Item → tsk with task_input to its Sample parent;
  each lab workflow instance on a carrier → a prc typed by workflow_name,
  with fulfills/procedure_input/produced_by links and the output_input
  map; outputs are the carrier's begat children in the instance window +
  slack; multi-sheet instances merge to one prc by workflow_uuid +
  proximity bounded by protocol_count; administrative workflows produce
  no procedure. Unit specs cover the worked example (SOW05715930:
  Aliquot Creation prc with NA input and Aliquot output; SOW QC prc with
  input, no output) and the direct sample→sample case (Aliquot →
  Illumina Library).
- The live esp itest (extending TASK-069's) drives the worked-example
  ancestry through the production path and asserts: the SOW Item tsk, the
  Aliquot Creation prc fulfilling it with the NA as procedure_input and
  the Aliquot as produced_by output, and no procedure for an
  administrative workflow.
- Docs: design doc already carries the design; DIRECTION.md updated;
  the snapshot regenerator documented.

OPEN DECISIONS (director-review, recorded in the design doc):
- The `lab_procedure` classification — confirm the 7 administrative
  workflows (Edit / Add Create / Label Printing) and whether the
  physical-logistics workflows (Sample Receipt/Ship/Migration/
  Quarantine — physical but non-transforming) are procedures or
  annotations.
- The output-window slack duration.

OUT_OF_SCOPE:
- Overlap/precedence resolution (its own esp slice; TASK-069 recorded the
  key).
- Contribution weights in output_input (empty objects, as clarity).
- Reading esplims at runtime (snapshot ratified).

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`
- Snapshot regeneration: `python3 scripts/regen_esp_workflow_config.py
  --esplims <path> --out src/main/resources/esp-workflow-config.json`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented and proven live. Procedures are reconstructed locally per
carrier from sample sheets, the begat graph, and the vendored config
snapshot. All suites green in both modules (unit + integration,
`JADETIPI_IT_KAFKA=1`); `git diff --check` clean. The live itest drives
the worked-example ancestry and asserts the SOW Item as a `tsk`, the
Aliquot Creation `prc` reconstructed from its sample sheet with
`fulfills` → the task, `procedure_input` → the NA, `produced_by` ← the
Aliquot, `output_input` {Aliquot: {NA}}, and the `task_input` link.

CHANGES:
- `EspWorkflowConfigService`: reads the vendored `esp-workflow-config.json`
  (protocol_count / input_types / lab_procedure); unknown workflows are
  conservatively non-procedures with a warn.
- `EspEntityImportMapper`: `SOW Item` class → `tsk`; four new link types
  (task_input, fulfills, procedure_input, produced_by) and dynamic
  procedure types; `reconstructWorkflowInstances` (owner dedup via
  input_types, lab-only, sheets grouped by workflow_uuid and chunked by
  protocol_count); `mapEntity` emits the tsk task_input and, per
  driver-injected instance, the prc + its three link kinds + output_input.
- `ClarityImportDriver.withWorkflowProcedures`: resolves each instance's
  input (SOW Item's non-task/project begat parent, or the carrier) and
  window-filtered outputs (begat children in [start .. end + 72h]),
  fetching child creation dates.
- `EspEntityImportPlanner`: enqueues procedure_type rows for the lab
  workflows each carrier owns, ahead of the entity.

TESTS:
- Config service (3), mapper reconstruction (owner dedup, admin filter,
  tsk+task_input+prc+links+output_input — 4 features), plus the live
  `EspEntityImportKafkaIntegrationSpec` procedure feature.
- Adversarial review ran as a workflow (3 lenses → per-finding verify),
  7 real findings. FIXED now: (a) the `begat` link type didn't admit
  `tsk`, so every SOW-Item begat edge tripped link validation — both
  sides now allow `tsk` (and `contents` admits `tsk`); (b) freshly
  minted ids are now recorded on their own queue rows at mint time (and
  overlays in applyClarityPrecedence), so a forward reference (a prc's
  `produced_by` pointing at an output entity driven later) survives a
  resume instead of dangling — the per-item record moved to the resolver;
  (c) a window-less workflow instance now WARNs that its outputs are
  dropped instead of failing silently.

KNOWN LIMITATION — batch/pool workflows (deferred to TASK-072):
The reconstruction is **per-carrier**, which is correct for the
single-input/single-output pattern (SOW Item → one Aliquot, proven live)
but NOT for workflows where many carriers share one sample sheet:
- Plate-batch: N SOW Items aliquoted on one plate share one
  `sample_sheet_uuid`, so each reconstructs the same prc id — the
  duplicate prc inserts are counted conflicts and dropped, leaving the
  prc with only ONE carrier's `output_input` while N `produced_by` links
  point at it (inconsistent).
- Pooling: `input_types` lists the pool (output) type, so the owner rule
  can root the procedure on the pool with itself as input.
- Multi-input: only the first biological parent becomes the procedure
  input though `task_input` links them all.
These share one root cause — a procedure (sample sheet) is a batch over
many inputs/outputs carried by many documents, so reconstruction must be
**procedure-centric** (aggregate all carriers of a `sample_sheet_uuid`
into one prc), not per-carrier. That is a substantial redesign (needs a
by-sample-sheet grouping/view) opened as TASK-072.

DEFERRED / NOTES (as scoped):
- Task types carry no `procedure_type_id` linkage yet; output
  contributions are empty objects (as clarity).
- Open for director review: the `lab_procedure` classification, the 72h
  output slack, and the logistics workflows.
