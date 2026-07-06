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
2. **Clarity full**: all 51 process types plus remaining containers,
   samples, artifact groups, and files; scale-out of the same machinery.
3. **ESP entities**: containers, samples, and the `begat` edges as
   generic provenance links, with the overlap rule (below).
4. **ESP workflow reconstruction** (separate, later): esp has no process
   entities — `sample_sheets[]` gives `{workflow_uuid, protocol_uuid,
   ...}` with **no input/output lists**, and `workflow_uuid` resolves to
   no document. Building `prc` + `output_input` from esp means inference
   over `begat` edges and sheet variables; deferred until the simpler
   phases surface whether contribution data is recoverable. This is the
   known esp deficiency, reported to the director 2026-07-05.

## Overlap and precedence

Many clarity entities were re-imported into the ESP LIMS and appear in
both databases. Director ruling: **esp-entity properties take precedence
over clarity**. Clarity imports first (static), esp second — and applying
esp values onto clarity-created objects requires **value-update
semantics, which must be implemented before a real production import**
(director ruling 2026-07-05; today's ingestion is create-only and
first-value-wins). Bulk-import machinery development proceeds before that
feature lands.

## Staged-payload cleanup (ruling recorded)

Director ruling 2026-07-05: staged message payloads are simply **deleted
after they are applied** — no archiving requirement. If an output feed is
ever wanted, applied payloads can be emitted to a Kafka topic or other
output queue at that time. (Lifecycle plan task F implements the
cleanup.)
