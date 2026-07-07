# TASK-067 - Clarity phase 2: process-type discovery, all process types, samples

ID: TASK-067
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-059
  - TASK-066
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-067-clarity-phase-2.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Grow the clarity import from the single proven process type to the whole
database's process graph: discover process documents by type through the
replica's own views, generalize the mapper so ANY of the 51 clarity
process types imports the way AC Sample Aliquot Creation does, and bring
the submitted samples behind the artifacts into the graph. Scope-planning
moves from "name document ids" to "name a process type" on the TASK-066
CLI.

DESIGN (grounded in the live replica, surveyed 2026-07-07):
- **Discovery rides the replica's design doc** — no writes, no Mango
  indexes: `_design/processes/_view/process-type` emits
  `[type name, index] → url` with the process document id as the row id,
  and `_view/process-type-count?group=true` gives the 51-type histogram.
  `CouchDbDocumentReader` gains `processDocIdsByType(db, name, limit)`
  (paged with startkey/startkey_docid, constant memory at any type size;
  positive limit caps the total) and `processTypeCounts(db)`.
- **Procedure types become dynamic** (they leave `BOOTSTRAP_KEYS`): each
  planned process enqueues `type:procedure:<raw display name>` — the raw
  name is the dedup identity and keeps display fidelity; `suffixFor`
  sanitizes it for the minted id, and the typ declaration carries the
  snake_case name plus the display name in its description. The prc root
  resolves its `type_id` through the same key from the document's own
  `json.type['']`, so 'LP Pool Creation' and friends need no per-type
  code.
- **Samples join the graph**: every clarity artifact carries
  `json.sample.limsid` (confirmed for Analytes AND ResultFiles). The
  planner enqueues `samples_<limsid>` (kind `sample`) after the
  artifact's container and before the artifact; the mapper maps sample
  documents to typed `ent` roots (`type:entity:clarity_sample`;
  properties: name, date_received, control_type, submitter) and every
  artifact emits a `sample_of` link (artifact → sample; new bootstrap
  link type `type:link:sample_of`, left artifact ent/fil, right sample
  ent). Shared samples dedup in the queue like every other row.
- **CLI**: `--jgi-import.process-type='<display name>'` plans by
  discovery (combinable with explicit `--jgi-import.process` ids);
  `--jgi-import.limit=N` caps discovered processes for staged rollout;
  new mode `--jgi-import.mode=types` prints the live histogram.
- **Deferred to phase 2b — files and artifact groups** (shapes surveyed,
  mapping designed later): clarity `files_<limsid>` documents carry
  `content-location` (a retrieval URL — exactly the fil candidate
  property from the TASK-049 deferrals) and `attached-to` artifact refs,
  but there is no by-artifact view, so importing them is its own pass
  (an `_all_docs` prefix walk or a new view decision). Artifact groups
  (`artifactgroups_<n>`: name + artifact list) need a membership-link
  vocabulary ruling. Both are additive passes over this machinery.

ACCEPTANCE_CRITERIA:
- Unit: the planner enqueues bootstrap types, the document-discovered
  procedure type, container and sample before each artifact, inputs
  first, process last (17 rows for the fixture process);
  `planProcessesByType` discovers through the reader and dedups
  replans. The mapper maps samples to typed ent roots, emits sample_of
  links from artifacts, and declares a procedure type for ANY process
  type name with a sanitized id suffix.
- The live itest plans and drives the fixture process through the
  production path with the phase-2 graph — 17/17 items, zero failures,
  the sample ent materialized with sample_of links from all four
  artifacts — and discovery streams five AC process ids and the 51-type
  histogram from the live view.
- Docs: bulk-import-design.md phases updated (phase 2 core delivered,
  2b scoped); DIRECTION.md Bulk Import updated.

OUT_OF_SCOPE:
- Files and artifact groups (phase 2b).
- ESP phases; workflow reconstruction.
- Running the full production import (the trigger exists; scale-out is
  operational, not code).

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as designed and proven live. Any clarity process type can
now be planned by name from the CLI and drives through the TASK-066
production path; submitted samples materialize as typed ents with
sample_of links from every artifact. All suites green in both modules
(unit + integration, `JADETIPI_IT_KAFKA=1`); `git diff --check` clean.

NOTES FOR REVIEW:
- The `sample_of` link-type naming (artifact → sample, roles
  artifact/sample, labels sample_of/has_artifact) is my choice — flag if
  you want different vocabulary before samples import at scale.
- Procedure-type queue keys carry the RAW display name
  ('type:procedure:AC Sample Aliquot Creation') so dedup identity and
  the typ description keep display fidelity; ids sanitize via the
  existing suffix rule. The previous sanitized AC key is retired with
  this change (queue rows are transient; tests purge and dev replans).
- One dependency addition: `org.apache.groovy:groovy-json` (the core
  groovy artifact does not include JsonOutput in Groovy 4), used for
  CouchDB view key encoding.

TESTS:
- Planner spec: 17-row plan with bootstrap-then-procedure-type ordering,
  container+sample before each artifact, shared-sample dedup;
  discovery-driven planning replans to zero.
- Mapper spec: sample → typed ent with traceability properties; Analyte
  artifact emits contents AND sample_of links; dynamic procedure type
  for 'LP Pool Creation' with sanitized suffix; bootstrap set (now 8)
  still fully declared.
- Live itest: plan 17 → drive 1 batch/17 done/0 failed → full graph
  asserted including the sample ent and its four sample_of links;
  discovery feature streams 5 AC ids and the 51-type histogram from the
  replica view.