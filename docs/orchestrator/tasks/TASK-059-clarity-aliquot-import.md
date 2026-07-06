# TASK-059 - Clarity aliquot import slice

ID: TASK-059
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-043
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-059-clarity-aliquot-import.md
  - docs/architecture/bulk-import-design.md
  - DIRECTION.md
  - docs/jdtp-specification.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/importer/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/importer/
  - jade-tipi/src/test/resources/importfixtures/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - couchdb-import
  - gradle-verification

GOAL:
The first vertical slice of the ratified bulk-import design
(docs/architecture/bulk-import-design.md): a persistent dependency-ordered
import queue plus a clarity mapper covering the *AC Sample Aliquot
Creation* process type, proven end to end against the live clarity
CouchDB — procedure types, containers, artifacts (Analyte → ent,
ResultFile → fil), the process as prc with its output_input map, and the
procedure_input / produced_by / contents links.

DESIGN (per the ratified bulk-import design):
- `ImportQueueService`: MongoDB `import_queue` collection; rows
  `{_id: <source>~<key>, source, key, kind, state, seq, jdtp_id?,
  txn_id?, error?}`; `seq` from a counter row via findAndModify; enqueue
  is dedup-by-_id and never changes an existing row's seq (the ordering
  invariant); drain is pending-by-seq-ascending.
- `ClarityAliquotImportPlanner`: walks one process document's
  dependencies via the CouchDB reader and enqueues in dependency order —
  bootstrap type rows first (procedure type, clarity_analyte ent type,
  clarity_result_file fil type, clarity_container loc type, and the
  procedure_input / produced_by / contents link types), then each
  artifact's container before the artifact (inputs before outputs), then
  the process last.
- `ClarityAliquotImportMapper` (pure, like the TASK-043 mappers): maps a
  queue row's source document to MappedImportMessages given an
  id-resolver over queue keys. Identity is message-UUID-form with
  lowercased source suffixes (`clarity_art_2-79367`,
  `clarity_cont_27-8546`, `clarity_proc_24-35613`). Artifacts map by
  `output-type`: Analyte → ent, ResultFile → fil, anything else → ent
  (logged). An artifact with a location gains a positioned `contents`
  link from its container (link-type *name* `contents`, so the existing
  contents read views resolve it). The process maps to prc with
  `output_input` built from the source `input-output-map` (open
  contribution objects, empty for now), one `procedure_input` link per
  distinct input, and one `produced_by` link per output (ent and fil
  outputs alike — endpoints are unconstrained by protocol).
- The drive loop for this slice lives in the integration spec (the
  TASK-043 precedent): batch pending rows in queue order into one
  transaction, mint ids, record them on the rows, send via Kafka, mark
  done. A production trigger (CLI/HTTP) is follow-on work.

ACCEPTANCE_CRITERIA:
- Queue unit coverage: sequence assignment, dedup that preserves the
  original seq, pending-in-order drain shape, emit/done/failed
  transitions.
- Mapper unit coverage against real fixture documents
  (importfixtures/clarity-aliquot/): container → loc; Analyte → ent with
  positioned contents link; ResultFile → fil without one; process → prc
  whose output_input keys/values are the resolved output/input jdtp ids,
  plus the procedure_input and produced_by links; all payload keys
  snake_case; all ids convention-conformant.
- Planner unit coverage: dependency order (types → containers →
  input artifacts → output artifacts → process) and dedup across
  repeated planning.
- A live-gated integration spec plans and drives process
  `processes_24-35613` end to end: queue rows all done; typed prc root
  with a 3-output output_input; produced_by links (2 fil + 1 ent
  outputs); procedure_input link; positioned contents link; fil roots
  for the ResultFiles — with zero link-validation warnings (dependency
  order satisfies declare-before-use by construction).
- The bulk-import design doc and DIRECTION.md record the ratified
  design; the spec records the staged-payload deletion ruling.

OUT_OF_SCOPE:
- No production trigger surface (CLI/HTTP) for the import — follow-on.
- No other clarity process types, samples, artifact groups, or the files
  rel (phase 2); no esp phases (3/4).
- No value-update semantics (required before real production import;
  separate prerequisite task per director ruling).
- No contribution-weight extraction into output_input values (open
  objects stay empty this slice).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- Design grounded in a live survey of both CouchDBs (recorded in
  docs/architecture/bulk-import-design.md): clarity ~4.05M docs with
  99,486 first-class processes across 51 types; esp-entity ~1.79M docs
  with begat-edge provenance and sample sheets but no process entities
  (the esp deficiency reported to the director; workflow → prc
  reconstruction deferred as its own phase).
- `ImportQueueService`: Mongo `import_queue` rows keyed
  `<source>~<key>` with counter-driven `seq`; dedup preserves the
  original (lower) seq — the ordering invariant; pending-in-order drain;
  recordJdtpId/markDone/markFailed transitions.
- `ClarityAliquotImportPlanner`: bootstrap type rows → each artifact's
  container before the artifact → inputs before outputs → process last;
  Flux.defer keeps enqueue execution at subscription time so the plan's
  order holds end to end; replanning inserts nothing.
- `ClarityAliquotImportMapper` (pure): idFor(key, collection) resolver
  contract; suffixes are `clarity_` + the sanitized key; container →
  loc, Analyte → ent (+ positioned `contents` link, link-type *name*
  `contents` so the existing read views resolve it), ResultFile → fil,
  process → prc with output_input from the source input-output-map plus
  procedure_input and produced_by links (ent and fil outputs alike).
- Real fixture documents under importfixtures/clarity-aliquot/ (process
  24-35613: one input analyte, one output analyte, two ResultFiles, two
  containers) drive the mapper and planner unit specs.
- Live-gated `ClarityAliquotImportKafkaIntegrationSpec`: plans the real
  process (14 rows), drives one Kafka transaction with
  message-UUID-form ids recorded on the rows, and asserts the full
  materialized graph — committed header with the exact message_count,
  prc with the three resolved outputs in output_input, positioned
  contents link, fil roots with produced_by links, procedure_input
  link, every queue row done with its id and txn — with **zero
  link-validation warnings and zero schema skips** (dependency order
  satisfies declare-before-use by construction).
- Bug found and fixed en route: Groovy GStrings leaking into message
  payloads serialize as JSON *objects* (Jackson treats GString as a
  bean), which the wire schema rejected (`/data/id: object found,
  string expected`) — the transaction sat open with every data message
  skipped. Key/id helpers now return plain Strings with a comment
  pinning the constraint.
- Rulings recorded: staged payloads deleted after application (spec
  0.5.2-draft §2.4/§5); DIRECTION.md Bulk Import section; value-update
  semantics noted as a prerequisite for real production import;
  UT-3/value_schema deferral already in 0.5.1.
- Follow-ons recorded: production trigger surface (CLI/HTTP) for
  plan+drive; clarity phase 2 (all process types, samples, artifact
  groups, files rel); esp phases; value-update semantics.
- Verification results: `:jade-tipi:test` green; full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` BUILD SUCCESSFUL
  (2m03s) with the live import spec passing (1 test, 0 failures);
  `git diff --check` clean.
