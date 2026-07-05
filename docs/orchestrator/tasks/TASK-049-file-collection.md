# TASK-049 - File collection

ID: TASK-049
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-048
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-049-file-collection.md
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
Implement the director-ratified Files direction (DIRECTION.md, Files
section): a first-class `fil` collection end to end — wire vocabulary,
materialization as a standard typed root, typed property values, canonical
examples, and an integration proof that files participate in the
procedure/task provenance loop.

DESIGN (ratified 2026-07-04):
- `fil` = retrievable electronic asset ("file"), including assets that are
  no longer retrievable (deleted) or only retrievable locally. Aggregates
  of files (datasets, run folders) remain `ent` records with membership
  links to their `fil` members.
- A `fil` record is a **standard typed root** — no hoisted content-identity
  fields, no `fil`-specific schema branch, no dedup policy. The property
  set will become evident as real file objects are imported (a retrieval
  URL is one candidate property, but not every file has a URL; some files
  have a non-URL retrieval protocol). Those deferrals are deliberate
  director rulings, to be revisited when imports reveal the shape.
- File types are ordinary `typ` records with ordinary inheritance; file
  facts are ordinary object-targeted property values.
- The provenance loop is unchanged: link types admit `fil` endpoints
  through their ordinary `allowed_*_collections` declarations (e.g.
  `produced_by` fil → prc, `task_input` tsk → fil).

ACCEPTANCE_CRITERIA:
- `Collection` enum gains FILE (`file`/`fil`) with create/update/delete
  actions; `message.schema.json` gains `fil` in the collection enum, the
  data-action matrix, and the `ObjectId` collection segment set; the
  materializer's `ID_COLLECTION_SEGMENTS` and supported-create set gain
  `fil`. (`MongoDbInitializer` picks the collection up automatically from
  the enum.)
- `fil + create` materializes a standard typed root (the `tsk` shape —
  no hoist).
- Object-targeted property assignments accept `fil` targets (materializer
  `OBJECT_ASSIGNMENT_COLLECTIONS` and the generic read service's supported
  set), with the inheritance-aware gate unchanged.
- Canonical examples cover: a file type declaration, a typed
  `fil + create`, and an object-targeted assignment onto the `fil` root —
  all registered in `MessageSpec` (round-trip + schema), with
  message-UUID-form conformant IDs.
- A Kafka integration spec proves the loop: file type → typed `fil`
  create → property assignment onto the `fil` root → a `produced_by`
  link joining the `fil` to a `prc` (own link-type declaration admitting
  `fil` on the left) → typed root, projected value, and link all
  materialized, with convention-conformant IDs throughout.
- kli `--collection` help and the unknown-collection error list `fil`.
- Vocabulary doc gains a Files message section and updated collection
  lists; the specification gains the §1.1 `fil` row and a Files section
  (Normative for the implemented surface; content-identity hoisting and
  dedup recorded as deliberately deferred), with a version bump.

OUT_OF_SCOPE:
- No content-identity hoisting (checksums, sizes, locators) and no
  `fil`-specific schema payload branch — deferred by director ruling.
- No deduplication policy for identical bytes — deferred by director
  ruling.
- No retrieval semantics, storage-location modeling (e.g. storage systems
  as `loc`), or bulk file import.

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- Wire layer: `Collection` gains `FILE("file","fil")`;
  `message.schema.json` adds `fil` to the collection enum, the
  data-action matrix, and both occurrences of the `ObjectId` collection
  segment set. No `fil`-specific payload branch — `fil` data rides the
  ordinary SnakeCaseObject + DataWithObjectId branch, per the deferral
  rulings. `MongoDbInitializer` picked the collection up automatically
  from the enum (its exact-set unit feature now expects `fil` without an
  edit).
- Materializer: `fil` joins `ID_COLLECTION_SEGMENTS` and the supported
  creates; `fil + create` is a standard typed root (the `tsk` shape).
  `OBJECT_ASSIGNMENT_COLLECTIONS` and the generic
  `ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS` become
  {ent, loc, prc, tsk, fil}; the inheritance-aware gate is untouched.
- New `CommittedTransactionMaterializerFileSpec` (two features: the
  typed root with a key-set assertion proving nothing file-specific is
  hoisted; assignment routing against the `fil` collection); the generic
  reader spec's where-block and the ID-convention spec gain `fil` rows.
- Canonical examples 23–27 (message-UUID-form IDs, one shared example
  transaction): fastq file type (an ordinary bare `typ`, no kind
  discriminator), `retrieval_url` property definition, its registration
  on the file type, the typed `fil + create` (`run42_r1.fastq` — dots
  are legal in property values, only ID suffixes ban them), and the
  object-targeted assignment onto the `fil` root. Registered in
  `MessageSpec` EXAMPLE_PATHS; two new features pin the FILE enum entry
  and the cross-referenced file sequence.
- Integration: `FileProvenanceKafkaMaterializeIntegrationSpec` drives
  one 11-message transaction (file type, retrieval_url def + add,
  sequencing procedure type, prc, produced_by link type admitting `fil`
  on the left, typed fil, assignment onto fil, produced_by lnk
  fil → prc, commit) and asserts the typed `fil` root with an exact
  key-set check (standard contract + property_values, nothing hoisted),
  the projected retrieval_url entry with full provenance, the
  produced_by link endpoints, and the typed prc root.
- kli: `--collection` help and the unknown-collection error list `fil`.
- Docs: vocabulary doc gains the Files section (wire shape, deferral
  rulings, provenance participation) plus updated collection lists,
  assignment targets, reader surface, and the 23–27 example inventory.
  Specification bumped to 0.3.0-draft: §1.1 `fil` row, new §1.10 Files
  [Normative] with the deferrals recorded as *deliberately unspecified*
  open decisions (not Planned — they are unratified), §2.3 create row
  gains `fil`, §2.3.1 and §3 list the five assignment/read collections,
  §5 open-decisions list gains file content-identity and dedup.
- Verification results: `:libraries:jade-tipi-dto:test` and
  `:jade-tipi:test` BUILD SUCCESSFUL; full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m30s) with the new spec
  running live (1 test, 0 failures) and zero
  object-identifier-convention warnings; `git diff --check` clean.
