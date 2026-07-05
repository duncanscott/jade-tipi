# TASK-048 - Procedure and task collections

ID: TASK-048
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-047
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-048-procedures-and-tasks.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - clients/kafka-kli/src/main/groovy/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the director-ratified Procedures And Tasks direction
(DIRECTION.md; spec section 1.9): first-class `prc` and `tsk` collections
end to end — wire vocabulary, materialization, typed property values,
canonical examples, and an integration proof of the full task/procedure
provenance loop.

DESIGN (ratified 2026-07-04):
- `prc` = performed procedure (execution event); `tsk` = intention to
  perform a procedure of a given type on a set of inputs.
- Tasks have their own task types; procedure types are never overloaded to
  define both. The task type carries `properties.procedure_type_id` (plus
  optionally the human-readable procedure name); task instances carry no
  per-instance procedure pointer. Type declarations use
  `kind: "task_type"` / `kind: "procedure_type"`, mirroring `link_type`.
- Task inputs (`ent`), task fulfillment (recorded on completion), and each
  output's produced-by relationship are canonical `lnk` records; on-root
  pointers wait for the planned links projection.
- The `prc` root carries a top-level `output_input` map (parallel to
  `lnk`'s `left`/`right`): `{output_ent_id: {input_ent_id:
  <contribution object>}}`; the contribution object is open (e.g.
  `{"volume": 12.5}` for pooling). `vdn`-supplied contribution schemas are
  deferred until `vdn` materializes (UT-4).
- Object identifier suffix charset is `[a-z0-9_-]` (dots removed by
  director ruling 2026-07-04).

ACCEPTANCE_CRITERIA:
- `Collection` enum gains PROCEDURE (`procedure`/`prc`) and TASK
  (`task`/`tsk`) with create/update/delete actions; `message.schema.json`
  gains both in the collection enum, the data-action matrix, and the
  `ObjectId` collection segment set; the materializer's
  `ID_COLLECTION_SEGMENTS` and supported-create set gain both.
- `prc + create` materializes a root document whose top-level
  `output_input` map (when present) is hoisted like `lnk`'s endpoints;
  `tsk + create` materializes a standard typed root.
- Object-targeted property assignments accept `prc` and `tsk` targets
  (materializer `OBJECT_ASSIGNMENT_COLLECTIONS` and the generic read
  service's supported set), with the inheritance-aware gate unchanged.
- Canonical examples cover: a procedure type and a task type (with
  `procedure_type_id`), a task create, input links, a `prc + create`
  carrying `output_input`, the fulfillment link, and a produced-by link —
  all registered in `MessageSpec` (round-trip + schema).
- A Kafka integration spec proves the loop end to end: types → task with
  inputs → procedure with `output_input` → fulfillment and produced-by
  links → typed roots and the map materialized, with
  convention-conformant IDs throughout.
- kli `--collection` help lists `prc` and `tsk`.
- Vocabulary doc gains the Procedures And Tasks message section; the
  specification's section 1.1 rows and section 1.9 flip from Planned to
  Normative for the implemented surface (the `vdn` association stays
  Planned).

OUT_OF_SCOPE:
- No `vdn` materialization or contribution-schema validation (tracked
  with UT-3/UT-4).
- No task lifecycle/status modeling beyond the fulfillment link.
- No links projection onto roots; no read views specific to procedures or
  tasks (the generic property-values reader gains the two collections;
  richer provenance views are follow-on work).

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- Wire layer: `Collection` gains `PROCEDURE("procedure","prc")` and
  `TASK("task","tsk")` (create/update/delete via the existing non-txn
  constructor branch). `message.schema.json` adds both to the collection
  enum, the data-action matrix branch, and the `ObjectId` collection
  segment set.
- Schema addition beyond the acceptance list, forced by the snake_case
  rule: `output_input` keys are object IDs, so `prc` payloads get their
  own `ProcedureData` branch (a three-way data picker: grp → GroupData,
  prc → ProcedureData, else SnakeCaseObject) with an `OutputInput` `$def`
  — ID-keyed map of ID-keyed maps whose contribution values must be
  objects. This mirrors the grp `permissions` escape exactly; on every
  other collection an ID-keyed `output_input` still fails snake_case.
  Negative `MessageSpec` features pin both directions (non-object
  contribution rejected on prc; ID-keyed map rejected on ent).
- Materializer: `prc`/`tsk` join `ID_COLLECTION_SEGMENTS` and the
  supported creates; `prc + create` hoists a top-level
  `data.output_input` map onto the root (parallel to `lnk` left/right,
  `copyProperties` verbatim) and `buildInlineProperties` excludes it;
  `tsk + create` is a standard typed root.
  `OBJECT_ASSIGNMENT_COLLECTIONS` and the generic
  `ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS` become
  {ent, loc, prc, tsk}; the inheritance-aware gate is untouched.
- New `CommittedTransactionMaterializerProcedureTaskSpec` (six features:
  hoist with inline bag, hoist with explicit data.properties, absent
  output_input, tsk root, assignment onto tsk, assignment onto prc);
  reader spec gains a prc/tsk feature; the ID-convention spec gains
  conforming prc/tsk identifiers.
- Canonical examples 16–22a (ten files, message-UUID-form IDs, one shared
  example transaction): procedure type, task type carrying
  `procedure_type_id`/`procedure_name`, task, task_input link type +
  instance, prc with `output_input` (two inputs → one pool), fulfills
  link type + instance (prc → tsk), produced_by link type + instance
  (ent → prc). All registered in `MessageSpec` EXAMPLE_PATHS; three new
  positive features pin the enum, the prc wire shape, and the full
  cross-referenced provenance loop.
- Integration: `ProcedureTaskProvenanceKafkaMaterializeIntegrationSpec`
  drives one 20-message transaction (types incl. the three link types and
  a material type, three typed ents, task, two input links, procedure
  with output_input, fulfills + produced_by links, note assignment onto
  the tsk root, commit) and asserts the typed prc root with the hoisted
  map (and its absence from properties), the typed tsk root with the
  projected note entry, the task type's procedure_type_id, and all four
  link records.
- kli: `--collection` help and the unknown-collection error list prc/tsk.
- Docs: vocabulary doc gains the Procedures And Tasks section (wire
  shapes, ProcedureData escape, link relations) plus updated collection
  lists, assignment targets, reader surface, and the 16–22a example
  inventory. Specification bumped to 0.2.0-draft: §1.1 rows normative,
  §1.9 flipped to [Normative; contribution schemas Planned], §2.2 records
  the second snake_case exception, §2.3 vocabulary table gains
  tsk/prc rows and the kind discriminators, §2.3.1 and §3 list the four
  assignment/read collections, §4 gains the output_input enforcement row,
  §5 drops the implemented model (vdn association stays).
- Verification results: `:libraries:jade-tipi-dto:test` and
  `:jade-tipi:test` BUILD SUCCESSFUL; full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m22s) with the new spec
  running live (1 test, 0 failures) and zero object-identifier-convention
  warnings; `git diff --check` clean.

REVIEW_AMENDMENT (2026-07-05, director):
- Task inputs are **not** constrained to `ent` — an input may be an
  object of any collection (e.g. the new `fil`); constraints are not
  baked into the protocol at this juncture. Applied symmetrically to
  procedure outputs, whose `ent`-only prose was already contradicted by
  TASK-049's `FileProvenanceKafkaMaterializeIntegrationSpec` (a `prc`
  producing a `fil` via `produced_by`). Nothing in enforcement ever
  constrained either side — the correction is prose: DIRECTION.md, the
  specification (§1.9; 0.3.2-draft), the vocabulary doc, and the
  schema's ProcedureData/OutputInput descriptions now state that input
  and output collections are unconstrained by the protocol (typically
  `ent` or `fil`) and that only per-deployment link-type
  `allowed_*_collections` declarations may constrain them. Canonical
  examples 19/22 broadened to `["ent", "fil"]` on the input/output
  sides to model the unconstrained reading. The DESIGN section above is
  left as the historical pre-review record.
