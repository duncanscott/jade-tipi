# Bulk Import Design

Director-ratified 2026-07-05. This document records the design for bulk
import of the JGI Clarity and ESP LIMS CouchDB replicas into Jade-Tipi,
grounded in a live survey of both databases.

## Sources

Two CouchDB databases with structurally different data — **two importers
sharing one queue and driver discipline**, never one importer:

- **`clarity`** (~4.05M docs, **static** — import first). Documents keyed
  by `<rel>_<LIMSID>` (`processes_24-35613`, `artifacts_2-79367`,
  `containers_27-8546`), carrying `type.rel` ∈ {artifacts, artifactgroups,
  containers, processes, files, ...} with the source payload under
  `json`. **99,486 first-class process entities across 51 process types**
  (`_design/processes` views), each with an explicit `input-output-map` —
  they map directly onto JDTP procedures.
- **`esp-entity`** (~1.79M docs, **live** — the ESP LIMS replicates into
  it continuously; import second). UUIDv7-keyed documents with
  `class_name` (Container, Sample, SOP, JgiIndex, JgiProposal, JgiProject,
  SOW Item), `parents[]`/`children[]` provenance edges (`relationship:
  "begat"`), containment (`container`/`contents`), `variables{}`, and
  `sample_sheets[]` referencing workflows/protocols.

## Ordering: the dependency queue

Director ruling: imports may start anywhere, but an entity's dependencies
must be queued ahead of it, recursively (a sample's producing process
before the sample; the process's inputs before the process). The queue is
**persistent** so an import can stop and resume.

Mechanism: a MongoDB collection (`import_queue`) — ordering is a
monotonic sequence counter, dedup is the natural `_id` (`<source>~<doc>`),
and it adds zero infrastructure (director approved Mongo or an embedded
file DB; Mongo chosen). Rows: source, doc key, kind, state
(pending → done | failed), `seq`, and the minted `jdtp_id` once emitted.
The ordering invariant — a dependency's `seq` is always lower than its
dependent's — holds by construction: each planner walk enqueues
dependencies first, and re-enqueueing an existing row never changes its
original (lower) `seq`.

Ordering composes with the rest of the system: dependency-ordered
emission satisfies declare-before-use, so the TASK-058 link-validation
warnings stay silent on a healthy import; `message_count`/`apply_state`
(TASK-056) give per-row forensics; the background worker (TASK-052)
decouples ingest from projection cost.

## Identity

Imported objects use the **message-UUID form** of the object identifier
convention: each root's UUID segment is its create message's UUIDv7, and
the suffix is the lowercased source identity
(`clarity_art_2-79367` → `...~ent~clarity_art_2-79367`). The minted ID is
recorded on the queue row at emit time, so later items reference earlier
imports through the queue — across any transaction batching. Re-runs are
guarded by queue state (done rows are not re-emitted); JDTP's create-only
conflict handling is the backstop, never the mechanism.

## Phases

1. **Clarity slice (TASK-059)**: one process type — *AC Sample Aliquot
   Creation* — end to end: procedure type, containers, artifacts
   (Analyte → `ent`, ResultFile → `fil`), the process as `prc` with its
   `output_input` map from the source's `input-output-map`, and
   `procedure_input` (prc → input) / `produced_by` (output → prc) /
   `contents` (container → artifact, positioned) links. Proves queue,
   planner, mapper, and driver on real data at small scale.
2. **Clarity phase 2 (TASK-067, core delivered)**: discovery through the
   replica's own `_design/processes` views (`--jgi-import.mode=types`
   for the histogram; `--jgi-import.process-type='<name>'` with an
   optional `--jgi-import.limit` to plan by type), procedure types
   minted dynamically per clarity process type (all 51 import with no
   per-type code), and submitted samples as typed `ent` roots with a
   `sample_of` link from every artifact (container → sample → artifact
   dependency order).
   **Phase 2b (TASK-068, files delivered)**: file metadata lands on the
   EXISTING fil roots as object-targeted property assignments — the
   import's first live use of the value-update machinery: five
   properties (content_location — the retrieval URL, original_name,
   original_location, is_published, file_limsid) are defined and
   registered on the ResultFile type once, then `planFiles`
   (`--jgi-import.files=true`) walks the `files_` prefix and enqueues
   every file whose attached-to artifact was imported
   (`--jgi-import.limit` bounds the scan; unset = the full pass).
   Re-importing a changed file updates the current value with the trail
   in `hst`.
   **Artifact groups: replica deficiency (recorded 2026-07-07).**
   `artifactgroups_` documents carry only a name and a live-API query
   URI — no member list exists in the replica, so membership links
   cannot be built from replicated data. Deferred until a membership
   source exists or bare named groups are wanted.
3. **ESP entities (TASK-069, core delivered)**: any entity plans by uuid
   (`--jgi-import.esp-entity=<uuid>`) or by type name through the
   replica's `entity_views/by_type_name`
   (`--jgi-import.esp-type-name='Aliquot'`, `--jgi-import.limit`). The
   planner walks the begat ancestry recursively (parents first), then
   the entity's container, then the entity; the mapper emits typed roots
   (Container class → `loc`, else `ent`; dynamic types per
   class/type_name), one `begat` link per parent edge, and a positioned
   `contents` link (the driver looks the well up in the container's
   contents map at drive time). The esp `variables` bag rides
   `properties.variables` with keys sanitized to the wire schema's
   snake_case rule and originals preserved in `variable_names`.
   **Overlap key discovered (for the precedence phase)**: esp re-imports
   of clarity entities keep the clarity limsid as their esp
   name/barcode (e.g. the 96W plate named `27-279088`) — so matching is
   a lookup against the recorded clarity queue rows, and esp values can
   then land as assignments on the SAME object (newest wins, clarity
   trail in `hst`). Deliberately not resolved this slice: esp entities
   mint esp-keyed roots.
4. **ESP workflow reconstruction (TASK-070, designed 2026-07-07)**: esp
   has no first-class process entities — but procedures can be
   reconstructed from sample sheets, the begat graph, and the esplims
   workflow configuration. Full design below.

## ESP workflow → procedure reconstruction (TASK-070)

ESP records lab work as **sample sheets** embedded in entity/task
documents (`sample_sheets[]`, each `{workflow_uuid, workflow_name,
protocol_uuid, protocol_name, sample_sheet_uuid, sample_sheet_start_time,
sample_sheet_end_time, state}`). `workflow_uuid`/`protocol_uuid` reference
generic **types**, not executions (one `workflow_uuid` spans thousands of
samples over months). Procedures are reconstructed **locally per task**,
grounded in three sources: the sheet, the begat graph, and a vendored
snapshot of the esplims workflow configuration.

**The esplims config snapshot** (director ruling 2026-07-07: vendor a
snapshot). Each esplims `content/workflows/*.yml` declares a workflow's
`protocols:` (its ordered protocol sequence) and `sample_types:` (its
accepted input entity types). `scripts/regen_esp_workflow_config.py`
extracts these into `src/main/resources/esp-workflow-config.json` — the
importer carries no runtime dependency on the esplims repo. The snapshot
answers three questions at once:
- **Sheet-merge count** — `protocol_count` is how many sheets form one
  workflow *instance* (Aliquot Creation = 2, Illumina Sequencing = 3,
  qPCR = 4, Protein Expression = 5, Sample QC = 1).
- **Input identification** — `input_types` names the accepted input
  (Aliquot Creation ← `SOW Item`; Illumina Library Creation ← `Aliquot`;
  Sample QC ← `Nucleic Acid`).
- **Lab vs administrative** — a `lab_procedure` flag (proposed:
  Edit/Add Create/Label Printing are administrative CRUD, excluded;
  27 lab / 7 administrative of the 34 workflows). Curated, director-review.

**SOW Items are tasks** (director ruling 2026-07-07). `class_name:
"SOW Item"` (type_names `SOW Item` and `PM SOW Item`) → `tsk`, not `ent`.
A SOW Item is the task delivering its begat-parent sample into the
workflows for which it carries sheets. Its begat children are the
candidate outputs. Everything a procedure needs is thus local to one
document — no cross-entity scan.

**Procedure identity and the merge.** A procedure = one workflow
*instance*: the group of a carrier document's sheets that share a
`workflow_uuid`, are proximal in time, and number up to the config's
`protocol_count`. (Observed: a workflow instance's protocols often share
one `sample_sheet_uuid` already — the SOW Item's and the Aliquot's
"Aliquot Creation" sheets shared `sample_sheet_uuid` across two
protocols. Where a workflow splits into distinct sheet uuids, the
`workflow_uuid` + proximity + `protocol_count` bound is the robust key.)
The instance's window = earliest sheet start .. latest sheet end, plus a
configurable slack for outputs created just after the recorded end.

**The reconstruction, per procedure:**
- **type** — `workflow_name` → a dynamic `procedure_type` (like clarity's
  process types), skipped when `lab_procedure` is false.
- **carrier / inputs** — the config `input_types` names the input entity
  type. For task-carried workflows (input type `SOW Item`) the input is
  the SOW Item's Sample-class begat parent, and the procedure `fulfills`
  the task. For entity-carried workflows (input type `Aliquot`,
  `Nucleic Acid`, …) the input is the carrier entity itself (handles the
  direct sample→sample steps like Aliquot → Illumina Library).
- **outputs** — the carrier's Sample-class begat children whose `created`
  falls within the instance window + slack. QC/logistics workflows
  legitimately produce none (a `prc` with inputs, no outputs — like the
  clarity slice's empty contributions).
- **links** (§1.9 vocabulary): `task_input` (tsk → input ent),
  `fulfills` (prc → tsk), `procedure_input` (prc → input),
  `produced_by` (output → prc), and the `output_input` map on the prc.

**Open decisions for review:** the `lab_procedure` classification (the
7 administrative workflows); the output slack duration; whether the
logistics workflows (Receipt, Ship, Migration, Quarantine — physical but
non-transforming) should be procedures or annotations. Recorded in
TASK-070.

## Overlap and precedence (TASK-071)

Director ruling: **esp-entity properties take precedence over clarity**;
clarity imports first (static), esp second. The value-update machinery
this needs is in place (TASK-061). But a live overlap investigation
(2026-07-07, adversarially verified) found the overlap is far narrower
than the ruling's framing assumed:

- **Only plate-type Containers overlap by identity.** An esp Container
  that is a clarity re-import keeps the clarity container limsid as its
  `name` (== `barcode` == clarity `containers_<limsid>` == the clarity
  doc id, same-entity-verified). The transform is a literal
  `"containers_" + name`.
- **Sample-level entities do NOT overlap at all.** Nucleic Acid, Aliquot,
  and Illumina Library carry their own JGI ITS ids (NA…, AQ…, 5-char
  library codes) with **no clarity limsid in any field** (0/90 matched,
  positive-control validated). Clarity's biological ids (DES… samples,
  2-NNNNN artifacts) are a disjoint namespace. SOW Items and JgiProjects
  likewise carry JGI ids, not clarity keys.

**Consequence — the ruling's character changes.** Because samples don't
overlap and containers carry no rich esp measurements, there is no
*value* to merge under precedence. What IS implementable, and valuable,
is **identity dedup**: when both databases are imported, the same
physical plate must not become two `loc` roots. That is the TASK-071
slice.

**Implemented (TASK-071): container id-unification.** An esp Container is
an OVERLAY onto the clarity root when (1) `class_name == "Container"`,
(2) `name` matches `^[0-9]+-[0-9]+$` (clean limsid, no `_X` re-plate
suffix), and (3) the clarity import_queue row `clarity~containers_<name>`
exists with a recorded `jdtp_id`. The esp entity then **reuses that
jdtp_id** as its root (recorded on the esp queue row, so every downstream
esp reference — contents links from contained samples, begat links —
resolves to the shared clarity plate) and emits **no duplicate
root-create**. Existence-gated: absence of the clarity row (clarity not
imported, or an esp-native container in the 27-810xxx block, ~11% of
limsid-shaped names) falls through to the normal esp mint path. Keyed on
`name` only (rack `barcode` can hold the esp UUID). Container-only.

**Sample matching needs an external cross-reference (available).** There
is no clean field-based join between esp and clarity sample-level
entities. The conceptual key is the SOW Item (director, 2026-07-07):
clarity samples/analytes carry a `SOW Item ID` UDF, and esp SOW Items
are first-class entities — but the two id spaces do NOT align by string
equality (clarity `SOW Item ID` 125510 does not resolve to an esp SOW
Item; esp SOW numbers are a different space; many clarity samples carry
no SOW Item ID), so a translation is required, not a lookup. This is
moot for now: **the director holds the actual list of clarity entities
that were migrated into ESP**, so the clarity-only (non-duplicated) set
is determinable directly from that list when clarity migration is
scheduled — no field-based inference needed.

## ESP-only migration (ratified 2026-07-07)

Director decision: **migrate esp-entity data only for now; defer clarity
entirely.** Rationale — esp is self-sufficient for a complete Jade-Tipi
graph (entities + containment + begat provenance + reconstructed
procedures, TASK-070), and esp-only sidesteps the cross-database
matching problem entirely (confirmed non-trivial: only container limsids
align cleanly; samples need a SOW-Item translation). Consequences:

- **No rework.** The importer already runs esp standalone
  (`--jgi-import.esp-entity` / `--jgi-import.esp-type-name`). The
  clarity phases (TASK-059/066/067/068) and the TASK-071 precedence code
  remain but are simply not run; precedence goes dormant (its existence
  gate never fires without clarity rows — harmless, and ready for when
  clarity is eventually imported).
- **What esp-only omits:** the clarity-only historical tail (records
  predating the esp LIMS, never re-imported into esp). Migrated later,
  using the director's migrated-entity list to select the
  non-duplicated set.
- **Priority becomes** TASK-070 (workflow reconstruction — the central
  esp piece, since no clarity supplies first-class procedures) then a
  full-esp migration sweep (scale-out of TASK-069's plan-by-uuid, like
  the clarity production trigger).

## The production trigger (TASK-066)

The drive loop lives in main scope (`ClarityImportDriver`) behind a CLI
(`JgiImportCliApplication`, Spring Boot web-type NONE, its own
`spring.config.name=jgi-import-cli`):

```bash
./gradlew :importers:jgi-import:run --args='\
    --jgi-import.mode=import \
    --jgi-import.process=processes_24-35613 \
    --jgi-import.org=<org> --jgi-import.grp=<grp> \
    --jgi-import.kafka.bootstrap-servers=localhost:9092 \
    --jgi-import.kafka.topic=<txn topic> \
    --spring.data.mongodb.uri=mongodb://localhost:27017/jadetipi'
```

- Modes: `plan` (fill the queue for the named process document ids),
  `drive` (drain whatever is pending), `import` (both). Scope is
  explicit process document ids; process-type discovery belongs to
  phase 2.
- `jgi-import.org`/`grp` are REQUIRED to drive — ids are minted under
  them, and there is deliberately no default identity. CouchDB and Mongo
  ride the standard `jadetipi.import.couchdb.*` and
  `spring.data.mongodb.uri` properties (defaults in
  `jgi-import-cli.yml`).
- One transaction per batch (`jgi-import.batch-size`, default 200):
  open → mapped messages in queue order → commit, published with
  acks=all and every send awaited.
- **Resume**: each row's minted id is recorded (`jdtp_id`) before
  publishing; the id resolver prefers recorded ids over fresh mints, so
  reruns and cross-batch references reuse identity. Items not marked
  done stay pending; an interrupted batch whose commit never published
  leaves an open transaction that the backend lease (TASK-063) rolls
  back durably; re-emitted roots land as clean creates or counted
  idempotent conflicts.
- **Failure isolation**: a missing source document marks that item
  `failed` (with the error) and the drive continues; the report and the
  non-zero exit code surface it. Inspect `import_queue` rows with
  `state: "failed"`, fix, flip them back to `pending` (or re-plan), and
  rerun.
- Health signal after a drive: the backend materialization counters —
  zero link-validation warnings and zero schema skips means the mapping
  held; anything else needs attention before going wider.

## Staged-payload cleanup (ruling recorded)

Director ruling 2026-07-05: staged message payloads are simply **deleted
after they are applied** — no archiving requirement. If an output feed is
ever wanted, applied payloads can be emitted to a Kafka topic or other
output queue at that time. (Lifecycle plan task F implements the
cleanup.)
