# TASK-069 - ESP entity phase: ancestry import with begat links and containment

ID: TASK-069
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-067
  - TASK-068
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-069-esp-entity-phase.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Bring the esp-entity database into the import (phase 3 core): any esp
entity plans by uuid or by type name, its begat ancestry imports
recursively as provenance links, containers land as loc roots with
positioned containment, and the esp variables ride the roots — all
through the same queue, driver, and CLI as clarity.

DESIGN (grounded in the live replica, surveyed 2026-07-07):
- **Source 'esp'** shares the queue and driver: rows are source-scoped
  (`esp~<uuid>`), the driver dispatches by source to the new
  `EspEntityImportMapper`, and the id resolver works within per-source
  namespaces with per-source suffix rules (`esp_...`).
- **Mapping**: Container-class documents → `loc`; every other class →
  `ent`. Types are dynamic — one typ per (class_name, type_name), like
  clarity's procedure types. Esp mints its OWN `contents` and `begat`
  link types (independently minted declarations coexist; the read views
  resolve `contents` by name). Each parent edge yields a begat link
  (left = parent, right = child). A contained entity emits its
  positioned contents link — the well lives only in the container's
  contents map, so the driver looks it up at drive time and injects it
  (`_import_well`).
- **Variables**: the wire schema constrains property keys (recursively)
  to snake_case, and esp variable names carry spaces, units, and
  punctuation ('Concentration (ng/ul)') — keys sanitize
  (`concentration_ng_ul`) and the originals are preserved in a sibling
  `variable_names` map. Discovered live: the unsanitized form is
  schema-rejected at ingest.
- **Planner** (`EspEntityImportPlanner`): begat ancestry first
  (recursively, visited-set + depth guard), then the container (itself
  planned recursively), then the entity's type row, then the entity.
  Discovery by type name rides the replica's
  `entity_views/by_type_name` view (new generic
  `CouchDbDocumentReader.docIdsByViewKey`, paged).
- **CLI**: `--jgi-import.esp-entity=<uuid>[,...]` and
  `--jgi-import.esp-type-name='<name>'` (+ `--jgi-import.limit`),
  composing with the clarity options in one plan/import run.
- **Overlap deliberately unresolved this slice** — and the matching key
  is now known: esp re-imports of clarity entities keep the clarity
  limsid as their esp name/barcode (seen live: the 96W plate named
  `27-279088`). The precedence phase follows: match esp entities
  against recorded clarity queue rows, reuse the SAME object ids, and
  land esp values as assignments (newest wins per TASK-061, clarity
  trail in `hst`). This slice mints esp-keyed roots unconditionally.

ACCEPTANCE_CRITERIA:
- Unit: mapper covers Sample→ent with begat + positioned contents links
  and sanitized-variables-with-originals, Container→loc, dynamic type
  declarations, and the esp link types; planner covers
  ancestry-before-container-before-entity ordering with types before
  each entity, dedup on replan, view discovery, and missing-document
  tolerance; driver covers esp dispatch with source-scoped ids and well
  injection.
- Live: a surveyed Aliquot's full 6-ancestor chain (Proposal, Final
  Deliv Project, Sequencing Project, PM SOW Item, Nucleic Acid + its
  96W plate, SOW Item) plans by uuid and drives through the production
  path in one clean batch — typed ent roots with esp identity, the
  begat link, the loc container, the positioned contents link matching
  the live well, and variables present.
- Docs: bulk-import-design.md phase 3 updated with the delivered scope
  and the overlap key; DIRECTION.md Bulk Import updated.

OUT_OF_SCOPE:
- Overlap/precedence resolution (next esp slice; key recorded).
- ESP workflow → procedure reconstruction (recorded deficiency).
- Children-direction planning (children import via their own plans).

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as designed and proven live: the surveyed Aliquot ancestry
imported through the production driver in one clean batch — begat links
along the chain, the 96W plate as a loc root, the Nucleic Acid's
contents link carrying the live well position, and sanitized variables
with original names preserved. All suites green in both modules (unit +
integration, `JADETIPI_IT_KAFKA=1`); `git diff --check` clean.

NOTES FOR REVIEW:
- The `begat` link-type vocabulary (roles parent/child, labels
  begat/begat_by, endpoints ent/loc/fil both sides) is my choice.
- Variable-key sanitization was forced by the wire schema (nested
  property keys are constrained to snake_case — discovered live when
  the verbatim form was rejected at ingest); the `variable_names` map
  preserves display fidelity. Sanitization collisions log a warning
  (last wins).
- `ClarityImportDriver` now drives both sources — a rename to something
  source-neutral (e.g. JgiImportDriver) is a cheap follow-on if you
  want it.
- The overlap key discovery (clarity limsid as esp name/barcode) sets
  up the precedence slice; recorded in the design doc.

TESTS:
- `EspEntityImportMapperSpec` (3 features), `EspEntityImportPlannerSpec`
  (4 features), driver esp-dispatch feature (source-scoped ids, well
  injection, esp suffix), all green.
- `EspEntityImportKafkaIntegrationSpec` (live): plan-by-uuid over the
  real ancestry, one batch, zero failures, graph asserted against live
  document facts (well position read from the live container at test
  time).