# TASK-042 - Typed Clarity/ESP container review seed

ID: TASK-042
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-040
  - TASK-038
  - TASK-036
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-042-typed-container-review-seed.md
  - docs/architecture/clarity-esp-container-mapping.md
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement plan tasks H and I together: model the real Clarity/ESP container
fields as a typed hierarchy with `ppy` property definitions, and migrate the
TASK-036 review seed from name-keyed `loc.properties` bags to typed `loc`
creates plus object-targeted assignments, so the MongoDB inspection point
shows the intended target shape with real laboratory data.

H and I are merged deliberately: TASK-040's integration spec already proved
plan task H's gate-acceptance criterion (a `loc`-targeted assignment for an
inherited registered property materializes), all message forms already
exist, and the container types/definitions have no consumer other than the
seed that exercises them.

CONTEXT:
- Modeling (per the ratified inheritance direction and the mapping doc):
  - The source `kind` strings become the type hierarchy: `container` (base)
    with subtypes `freezer`, `bin`, `plate`, and `tube`; `plate_96_well`
    extends `plate`. Instances stay in `loc` regardless of type.
  - Domain fields become `ppy` definitions and typed values: `name` and
    `barcode` registered on `container` (inherited by every subtype);
    `format`, `rows`, and `columns` registered on `plate` (inherited by
    `plate_96_well`). The `illumina_library` `ent` type registers `name`
    and `barcode` directly — `ppy` definitions are collection-agnostic and
    shared.
  - Source-system traceability facts stay in the inline `properties` bag,
    per drift-note decision 8.6/10: `source_system`, `source_id`,
    `source_type_id`, `source_numeric_id`, `source_state`,
    `source_type_name`, and the verbatim source type label renamed to
    `source_kind` (it is a source fact once the kind is expressed by
    `type_id`).
- The seed transaction is system-authored: `txn.user` carries the bootstrap
  identity `jade-tipi-org~dev~genesis~usr~jdtp-admin` (TASK-039), replacing
  the ad-hoc `direct-codex` string. Durable writer persistence remains
  deferred (plan task C); the envelope convention is established now so it
  resolves cleanly later.
- The seed stays an opt-in review artifact (`JADETIPI_IT_KAFKA` +
  `JADETIPI_REVIEW_SEED`), keeps its stable transaction/root IDs, and
  leaves materialized roots in MongoDB for direct inspection. Row reset
  before each run switches from an explicit ID list to a prefix match so
  older seed versions' rows cannot linger.

ACCEPTANCE_CRITERIA:
- One committed seed transaction creates: five `ppy` definitions (`name`,
  `barcode`, `format`, `rows`, `columns`), the typed hierarchy (`container`
  + `freezer`/`bin`/`plate`/`tube`, `plate_96_well` extends `plate`) with
  registrations via `typ + update add_property`, the `contents` link type,
  the `illumina_library` entity type with its registrations, four typed
  `loc` roots and one typed `ent` root whose inline `properties` carry only
  source facts, object-targeted assignments for every domain value, the
  three containment links, and the commit.
- Materialized assertions prove the target shape: subtype roots carry
  `properties.parent_type_id`; the plate `loc` root has
  `type_id == plate_96_well` and `property_values` entries for
  `name`/`barcode`/`format`/`rows`/`columns` with values and transaction
  provenance, all landed through inherited registrations; the tube has
  `name` but no `barcode` entry; the `ent` root carries typed
  `name`/`barcode`; inline bags contain no domain fields (no `name`,
  `barcode`, `kind`, `format`, `rows`, `columns`).
- `docs/architecture/clarity-esp-container-mapping.md` gains a TASK-042
  section superseding the name-keyed mapping with the typed field mapping
  (including the `kind` → `source_kind` rename) while preserving the
  historical TASK-019/036 sections.
- Seed remains re-runnable: stable IDs, prefix-based reset (including the
  new `ppy` rows), roots left in place after success.

OUT_OF_SCOPE:
- No CouchDB import loop; the seed remains a hand-mapped representative
  fixture from the sampled records documented in the mapping doc.
- No UI, no read-service changes, no materializer changes.
- No type-level facts modeling (whether `rows`/`columns` belong on the
  plate type rather than per instance is noted as an open modeling
  question, not resolved here).
- No `usr`/writer persistence (plan task C); only the envelope convention.
- No changes to canonical dto examples or the kli runbook.

VERIFICATION:
- `./gradlew :jade-tipi:compileIntegrationTestGroovy`
- `JADETIPI_IT_KAFKA=1 JADETIPI_REVIEW_SEED=1 ./gradlew :jade-tipi:integrationTest --tests '*ClarityEspContainerReviewSeedKafkaIntegrationSpec*'`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest` (coexistence)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-03):
- Seed spec rewritten in place (same class, same stable transaction UUID and
  root IDs): one 42-message transaction — five `ppy` definitions, the
  container hierarchy (`container` registers name/barcode; `freezer`, `bin`,
  `plate`, `tube` extend it; `plate` registers format/rows/columns;
  `plate_96_well` extends `plate`), the `contents` link type, the
  `illumina_library` entity type with registrations, four typed `loc` roots
  and one typed `ent` root (inline bags carry source facts only, including
  the `kind` → `source_kind` rename), twelve object-targeted assignments,
  three containment links, commit. Message UUID numbering encodes processing
  order (definitions/types before registrations, roots before assignments).
- `txn.user` now carries the TASK-039 bootstrap identity
  `jade-tipi-org~dev~genesis~usr~jdtp-admin` (was `direct-codex`).
- Row reset switched to a `Pattern.quote(ID_PREFIX)` regex delete across
  `loc`/`typ`/`ent`/`lnk`/`ppy` plus the txn WAL rows, so rows from earlier
  seed versions cannot linger.
- Assertions prove the target shape: `parent_type_id` on the five subtypes,
  inherited registrations only (no domain field appears in any inline bag),
  five projected values on the plate (text and number shapes), tube has
  `name` but no `barcode`, the `ent` root carries typed name/barcode, and
  provenance ties every value to the seed transaction.
- Mapping doc gains the "TASK-042 typed review seed update" section with the
  supersession table (previous vs typed targets), the hierarchy description,
  the writer convention, the `ppy` review-roots addition, and the recorded
  open question (instance values vs plate-type facts for format/rows/columns).
- No production code changed; plan task H's remaining substance (real
  container types/definitions) ships inside the seed per the merge rationale.
- Verification results: integration compile BUILD SUCCESSFUL; the seed spec
  green (1/1) against the local stack, leaving the typed roots in `jdtp`
  (plate root inspected directly: typed `type_id`, source-facts-only inline
  bag, five provenance-carrying `property_values` entries); full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` green as coexistence;
  `git diff --check` clean.
