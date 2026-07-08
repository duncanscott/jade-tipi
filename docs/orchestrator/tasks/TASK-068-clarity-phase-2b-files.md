# TASK-068 - Clarity phase 2b: the files pass; artifact-group deficiency recorded

ID: TASK-068
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-067
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-068-clarity-phase-2b-files.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Bring clarity file metadata into the graph — and do it through the
protocol's own value machinery: file documents land on the EXISTING fil
roots (the ResultFile artifacts) as object-targeted property
assignments, the import's first live use of the TASK-061 value model.
Also: fix two latent scale-out bugs surfaced by surveying real
documents, and record the artifact-group deficiency.

DESIGN (grounded in the live replica, surveyed 2026-07-07):
- **Files are assignments, not new roots.** A `files_<limsid>` document
  carries `content-location` (a retrieval URL — exactly the fil
  candidate property deferred in TASK-049), `original-name`,
  `original-location`, `is-published`, and `attached-to` (an artifact
  URI; the limsid is its tail). The attached artifact ALREADY has a fil
  root, and roots are create-only — so file metadata maps to `ppy`
  assignment messages targeting that root (the target collection read
  from the resolved artifact id's collection segment). Re-importing a
  changed file updates the current value with the trail kept in `hst`.
- **The property vocabulary bootstraps once**: five ppy definitions
  (content_location, original_name, original_location, is_published,
  file_limsid) plus their `add_property` registrations on the
  clarity_result_file type, enqueued as `file_property` rows ahead of
  the first file row — so registration materializes before the
  assignments in the same dependency order.
- **The files pass is a scan with an imported-artifact gate**: no
  by-artifact view exists, so `planFiles` walks the `files_` prefix
  (new `CouchDbDocumentReader.docIdsByPrefix`, `_all_docs`
  startkey/startkey_docid paging) and enqueues each file whose
  attached-to artifact has a recorded queue row; files outside the
  imported scope are skipped and counted. `--jgi-import.files=true`
  invokes it from the CLI; `--jgi-import.limit` bounds how many file
  DOCUMENTS are scanned (the walk is the expensive part — unset means
  the full production pass).
- **Driver generalization**: mapped messages now carry their action
  (`typ add_property` is the first update the import emits), and only
  root creates derive their message uuid from `data.id` — assignments
  and updates mint fresh message uuids.
- **Two real-data robustness fixes** (both would have failed at
  scale-out): clarity's XML→JSON collapses single-element lists to the
  bare object — seen live on `input-output-map` (process 24-1048849)
  and on `sample` — so both are normalized (`asImportList`) in the
  planner and mapper; and pooled artifacts carry a sample LIST, so
  artifacts now record `sample_limsids` (list property) and emit one
  sample_of link per referenced sample.
- **Artifact groups: unrecoverable from the replica (deficiency).**
  `artifactgroups_<n>` documents carry only a name and a LIVE-API query
  URI (`artifacts?artifactgroup=<name>`) — the replica holds no member
  list, so membership links cannot be built from replicated data.
  Confirmed across sampled groups (127+ docs). Importing bare named
  group ents with no members adds little; deferred until the director
  wants them (or a membership source exists). Recorded here like the
  esp workflow deficiency.

ACCEPTANCE_CRITERIA:
- Unit: file-property keys declare the ppy definition plus the
  add_property registration; file documents map to assignments onto the
  attached artifact's root (values shaped per property, is_published as
  boolean, no data.id); unresolvable attachments map to nothing; the
  files pass enqueues property rows once, then eligible files, skipping
  unimported artifacts; the scan limit bounds the walk; single-entry
  input-output-map (bare object) and pooled sample lists map correctly.
- Live: a surveyed small process (SQ Sequencing 24-1050055) imports
  through the production driver; `planFiles` then plans and drives the
  attached file (files_40-100013), and the EXISTING fil root gains
  `property_values.content_location` matching the live document's
  sftp URL, with the assignment preserved in `hst`.
- Docs: bulk-import-design.md phase 2b updated (files delivered, the
  artifact-group deficiency recorded); DIRECTION.md Bulk Import
  updated.

OUT_OF_SCOPE:
- Artifact groups (deficiency recorded above).
- ESP phases.
- Concurrent file-doc fetching in the scan (sequential is fine for the
  one-time static pass; tune later if needed).

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as designed and proven live: the files pass assigned the
real `sftp://...` content-location onto the already-imported fil root
as a property value — registration (ppy definitions + add_property),
assignment, current-value projection, and the `hst` history row all
through the production drive path. All suites green in both modules
(unit + integration, `JADETIPI_IT_KAFKA=1`); `git diff --check` clean.

NOTES FOR REVIEW:
- The file property vocabulary (five properties, registered on the
  clarity_result_file type) is my choice of names and shapes; the
  is_published value is `{boolean: ...}` parsed from clarity's string.
- Files attached to non-ResultFile artifacts would assign onto ent
  roots where these properties are unregistered — the gate counts them
  (`skippedUnregisteredProperty`), never errors; none seen in the
  scanned sample.
- The artifact-group deficiency parallels the esp workflow deficiency:
  membership exists only behind the live Clarity API, not in the
  replica.

TESTS:
- Mapper: file-property declaration pair; file → five assignments with
  per-property value shapes onto the resolved fil root; unresolvable
  attachment → empty; pooled sample list → sample_limsids + one
  sample_of link each; bare-object input-output-map maps.
- Planner: property rows once + eligible files + stranger skipped;
  scan-bound limit (fixture file docs added).
- Live itest: process 24-1050055 through the driver (a second process
  type exercising the dynamic procedure types), planFiles(20) → drive →
  fil root property_values.content_location equals the live document's
  URL with drive-transaction provenance; the assignment row present in
  `hst`; zero failed items in both drives.