# TASK-043 - Narrow CouchDB-to-JDTP container import loop

ID: TASK-043
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-042
  - TASK-040
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-043-couchdb-import-loop.md
  - docs/architecture/clarity-esp-container-mapping.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/importer/
  - jade-tipi/src/main/resources/application.yml
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/importer/
  - jade-tipi/src/test/resources/importfixtures/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the roadmap Track-1 slice: a narrow local CouchDB-to-JDTP import
loop that reads a small selected set of real records from the locally
replicated `clarity` and `esp-entity` databases, emits canonical JDTP
messages over the TASK-042 typed container model, and proves the data lands
in MongoDB through the same Kafka listener and committed materializer path.

CONTEXT:
- The local replicas are populated (clarity ~4.0M docs, esp-entity ~1.8M)
  with continuous replication running, so the loop reads live local data.
- Source shapes verified against the live replicas: ESP documents carry
  `uuid`/`name`/`barcode`/`numeric_id`/`type_uuid`/`type_name`/`class_name`,
  an upward `container` pointer (`uuid`, `type_name`, `slot`), and a
  `contents` map; Clarity `containers_*` documents carry `limsid`,
  `json.name`, `json.type.name`, and `json.state`.
- Containment is derived from each imported document's own upward
  `container` pointer, never from a parent's `contents` map. Justification
  observed in the live data: the sampled freezer's `contents` shows bin
  PP058 at slot 2 while bin PP050's `container` pointer claims the same
  slot — the parent-side map goes stale, the child-side pointer is current
  (the bin document is five months fresher).
- The mapper follows the documented conventions: D4 identifier convention
  (`esp_<kind>_<uuid-prefix>`, `clarity_tube_<limsid>`, link IDs embedding
  both endpoint names), D3 position vocabulary (`freezer_slot`,
  `bin_slot`, `plate_well`), and the TASK-042 typed model (types by source
  `type_name`; `name`/`barcode` as object-targeted assignments; source
  facts in the inline bag with `source_kind`).
- The importer maps only source-present facts: `format`/`rows`/`columns`
  are NOT synthesized for plates (they exist nowhere in the source
  document), which keeps the open instance-vs-type-fact question open
  rather than baking an inference into the importer.
- ESP `class_name` selects the target collection: `Container` -> `loc`,
  otherwise `ent` (the sampled Illumina Library is `class_name: "Sample"`).
  An unmapped `type_name` falls back to the base `container` type for
  `Container`-class documents so unknown container kinds still import
  typed.
- The loop is create-only and is not a synchronizer: re-importing an
  unchanged set under the same import transaction is idempotent; a new
  transaction re-importing the same IDs surfaces as conflicting duplicates
  (never overwritten). Source updates are not propagated; that is future
  work with the deferred lifecycle tasks.

ACCEPTANCE_CRITERIA:
- New importer components in production code: a CouchDB document reader
  (WebClient, basic auth, configurable via `jadetipi.import.couchdb.*`
  with env overrides) and a pure mapper from source documents to JDTP
  message payloads (`loc`/`ent` creates, object-targeted `name`/`barcode`
  assignments, child-side `contents` links with D3 positions), given a
  container-model registry of type/property IDs.
- Mapper unit spec runs against fixture JSON captured verbatim (trimmed)
  from the live replicas: ESP freezer/bin/plate/library and the Clarity
  tube, asserting D4 IDs, typed creates with source-facts-only inline
  bags, assignments (including no-barcode for the Clarity tube),
  child-side link derivation with parsed positions, `Sample`->`ent`
  routing, and the unknown-kind fallback.
- Opt-in integration spec (`JADETIPI_IT_KAFKA` + `JADETIPI_COUCHDB_IMPORT`
  plus Kafka and CouchDB/document probes) publishes a stable model
  transaction (types + `name`/`barcode` definitions + registrations),
  reads the five real documents from the local replicas, maps and
  publishes the import transaction (system-authored: `txn.user` is the
  bootstrap identity), commits, and asserts the materialized roots
  dynamically against the live source values (source->JDTP fidelity, not
  literals): typed roots, projected `property_values`, the three
  containment links with positions, and no domain fields in inline bags.
  Rows are left in MongoDB for review, reset by stable ID prefix per run.
- Mapping doc gains a TASK-043 section documenting the loop, the
  child-side containment decision with the observed staleness evidence,
  the source-present-facts-only rule, and the re-import semantics
  boundary.

OUT_OF_SCOPE:
- No scheduling, no bulk/broad import, no synchronization of source
  updates or deletes, no HTTP submission endpoint.
- No Kafka producer in production code: the importer produces message
  payloads; transport stays with the caller (the spec), matching the seed.
- No import of plate wells' analyte contents beyond the one sampled
  library, no Clarity UDFs, no ESP variables/sheets.
- No `format`/`rows`/`columns` synthesis (open modeling question stands).
- No writer persistence (plan task C); envelope convention only.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy :jade-tipi:compileTestGroovy :jade-tipi:compileIntegrationTestGroovy`
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 JADETIPI_COUCHDB_IMPORT=1 ./gradlew :jade-tipi:integrationTest --tests '*ClarityEspCouchDbImportKafkaIntegrationSpec*'`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest` (coexistence)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-03):
- Ground truth verified before design: local replicas populated (clarity
  ~4.05M docs, esp-entity ~1.79M, continuous replication running) and the
  five sampled documents fetched live to confirm shapes. The freezer's
  stale `contents` map (PP058 vs the bin's own claim of slot 2) was
  observed directly and drove the child-side containment rule.
- New production code (`org.jadetipi.jadetipi.importer`):
  `CouchDbDocumentReader` (WebClient, basic auth, 404 -> empty,
  `jadetipi.import.couchdb.*` config with env overrides),
  `ClarityEspContainerImportMapper` (pure; D4 IDs, D3 positions,
  class_name routing, unknown-kind fallback, child-side links,
  source-present-facts-only), `ClarityEspContainerModel` +
  `ClarityEspKindMapping` (registry), `MappedImportMessage` (payload +
  collection/action; transport stays with the caller).
- Tests: `ClarityEspContainerImportMapperSpec` (8 features) against
  fixture JSON captured verbatim (trimmed) from the live replicas under
  `src/test/resources/importfixtures/`; link IDs asserted equal to the D4
  examples byte-for-byte. `ClarityEspCouchDbImportKafkaIntegrationSpec`
  reads the five documents live, publishes one 33-message system-authored
  transaction (model + mapped data), and asserts the materialized roots
  dynamically against the live source values: typed roots for all five,
  projected name/barcode values, no-barcode Clarity tube with
  `source_state`, `Sample`->`ent` routing, and the three child-side
  containment links located by endpoints with source-slot positions.
- Docs: mapping doc "TASK-043 CouchDB import loop" section (rules,
  staleness evidence, re-import boundary, run command).
- Verification results: all compile targets and `:jade-tipi:test` BUILD
  SUCCESSFUL (mapper spec 8/8); import spec green (1/1, not skipped)
  against the live stack, leaving imported roots in `jdtp` under prefix
  `...c1a141e5e543` (bin root inspected directly: typed `type_id`,
  source-facts-only inline bag, projected PP050/BIN057 values); full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` green as coexistence;
  `git diff --check` clean.
