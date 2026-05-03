# claude-1 Changes

The developer writes completed work reports here.

STATUS: COMPLETED
TASK: TASK-029 — Human-readable Kafka entity-type submission path
  (director follow-up fix for empty `data.links` inline payload)
DATE: 2026-05-03
SUMMARY: Applied the narrow fix the director requested in
`DIRECTOR_IMPLEMENTATION_REVIEW`: an otherwise-flat bare entity-type
`typ + create` whose `data` carries `links: [:]` (empty Map) but no
`data.properties` was previously falling into
`CommittedTransactionMaterializer.buildInlineProperties(data)` and copying
the empty `links` Map into root `properties.links`, leaking a reserved
field into properties. `buildInlineProperties()` now also excludes
`FIELD_LINKS` alongside `FIELD_DATA_ID` and `FIELD_TYPE_ID`, so empty
`data.links` is dropped from inline properties; the surrounding `else`
branch in `buildDocument()` already initializes the root `links` to an
empty `LinkedHashMap`, so this case now materializes with
`root.links == [:]` and no `properties.links`. Added a focused new
materializer feature
`'bare entity-type typ create with empty data.links materializes with
 root links == [:] and no properties.links'` that injects
`links: [:]` into the existing `entityTypeMessage()` helper, captures
the inserted document, and asserts root `links == [:]`, no
`properties.links`, surviving `properties.name == 'plate_96'` /
`properties.description == '96-well sample plate'`, and absence of
`id`/`type_id` from properties. No production behavior change for the
already-handled shapes (link-type `typ + create`, `loc + create` with
`data.properties` Map, `lnk + create`, `grp + create`, `ent + create`,
inline-flat bare entity-type without `data.links`); no example resource
JSON, schema, ID convention, HTTP submission endpoint, property-assignment
materialization, semantic `data.type_id` validation, permission
enforcement, endpoint projection maintenance, contents-link read change,
or nested Kafka operation DSL was added. `typ + update`
property-reference changes remain intentionally `skippedUnsupported`.
All edits stayed within TASK-029's `OWNED_PATHS`.

Files changed (this turn):

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — Extended the `if`-guard inside `buildInlineProperties()` from
  `key != FIELD_DATA_ID && key != FIELD_TYPE_ID` to also require
  `key != FIELD_LINKS`. No other production code changed: the
  `buildDocument()` branch structure, the `data.properties instanceof Map`
  detection, the `data.links` propagation through `copyProperties()` for
  the rich-shape branch, the `lnk` branch's `left`/`right`/`properties`/
  `links: [:]` handling, `_head.provenance`, duplicate matching/conflict
  handling, and `isSupported()`'s flat `case COLLECTION_TYP: return true`
  arm are untouched.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — Added the new feature
  `'bare entity-type typ create with empty data.links materializes with
   root links == [:] and no properties.links'` between the existing
  `'materializes a bare entity-type typ create as a root document …'`
  and `'skips a typ + update message even after dropping the kind guard'`
  features. The new feature reuses the existing `entityTypeMessage(Map
   dataOverrides = [:])` helper with `links: [:]` to build the otherwise-flat
  bare entity-type payload, mocks `mongoTemplate.insert(_ as Map, 'typ')`
  to capture the document, and asserts: `result.materialized == 1`,
  `result.skippedUnsupported == 0`, `result.skippedInvalid == 0`,
  `captured.links == [:]`, `!properties.containsKey('links')`,
  `properties.name == 'plate_96'`, `properties.description ==
  '96-well sample plate'`, `!properties.containsKey('id')`,
  `!properties.containsKey('type_id')`. All previously added bare
  entity-type, duplicate, skipped-update, mixed-snapshot, and link-type
  features remain unchanged.

Verification (this turn):

- `./gradlew :jade-tipi:test --tests
  'org.jadetipi.jadetipi.service.CommittedTransactionMaterializerSpec'
  --console=plain` → BUILD SUCCESSFUL in 3s; recompiled production
  Groovy + test Groovy and re-ran the materializer spec.
- `./gradlew :jade-tipi:test --console=plain` → BUILD SUCCESSFUL in 5s.
  Aggregated test report `jade-tipi/build/reports/tests/test/index.html`
  shows 173 tests, 0 failures, 0 ignored, 100% success across the
  jade-tipi unit-test set, including the existing bare entity-type
  `typ + create` features and the new empty-`data.links` focused
  feature.
- `./gradlew :libraries:jade-tipi-dto:test --console=plain` → BUILD
  SUCCESSFUL in 360ms (UP-TO-DATE; no DTO changes this turn). The
  previously added DTO assertion proving `04-create-entity-type.json`
  uses the human-readable `data.id` / `data.name` / optional
  `data.description` / no `data.kind` / no `data.links` shape continues
  to pass.
- Did not rerun `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest
  --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'` this turn
  because the integration spec exercises the same `buildDocument()`
  inline path through Kafka/Mongo and would not change behavior for the
  empty-`data.links` payload variant covered here. Local Docker stack
  status was not re-verified for the integration suite this turn.

Previous turn report retained verbatim below.

---

STATUS: COMPLETED
TASK: TASK-029 — Human-readable Kafka entity-type submission path (implementation)
DATE: 2026-05-03
SUMMARY: Dropped the `data.kind == "link_type"` support gate from
`CommittedTransactionMaterializer.isSupported()` so the existing human-readable
bare entity-type `typ + create` example (`04-create-entity-type.json`,
flat `data.id`/`data.name`/`data.description`, no `data.kind`, no
`data.links`) now materializes as a root-shaped `typ` document with
`_id`/`id` from `data.id`, `collection: "typ"`, `type_id: null`, root
`properties: {name, description}` via the existing inline-properties
fallback, empty `links: {}`, and `_head.provenance` pointing at this txn,
commit, and msg uuid. The `data.kind == "link_type"` link-type
materialization path is preserved unchanged. `typ + update`
property-reference changes remain intentionally
`skippedUnsupported`; no `update`-action code path was added. No
example resource JSON, schema, ID convention, HTTP submission endpoint,
property-assignment materialization, semantic `data.type_id` validation,
permission enforcement, endpoint projection maintenance, contents-link
read change, or nested Kafka operation DSL was added. All work stayed
within the task's `OWNED_PATHS`.

Files changed (this turn):

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — Replaced the `case COLLECTION_TYP: return LINK_TYPE_KIND == kind`
  arm in `isSupported()` with a flat `case COLLECTION_TYP: return true`,
  matching `loc`/`lnk`/`grp`/`ent`. Removed the now-unused
  `static final String DATA_KIND` and `LINK_TYPE_KIND` constants.
  `ContentsLinkReadService` keeps its own private `LINK_TYPE_KIND`
  declaration unaffected. Updated the class-level Javadoc supported list
  to describe `typ + create` as covering both link-type
  (`data.kind == "link_type"`) and bare entity-type (`data.kind` absent)
  records, and to note that `typ + update` property-reference changes
  remain intentionally unsupported. No other production code changed:
  `buildDocument()`'s inline-properties fallback for non-`lnk`,
  non-Map-`data.properties` payloads already lifts `name`/`description`
  into root `properties` and writes `links: {}`; `_head` provenance,
  duplicate-key matching/conflicting handling, and missing/blank
  `data.id` handling are reused unchanged.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — Replaced the `'skips a typ create whose data.kind is not link_type'`
  feature with a positive
  `'materializes a bare entity-type typ create as a root document with
   name and description under properties and null type_id'` feature
  capturing `mongoTemplate.insert(_, 'typ')` and asserting `_id`/`id`,
  `collection`, null `type_id`, root `properties.name == 'plate_96'`
  and `properties.description == '96-well sample plate'`, no `id`/
  `type_id`/`kind` leakage into properties, empty `links`, and full
  `_head.provenance` (`txn_id`, `commit_id`, `msg_uuid`, `collection`,
  `action`, `committed_at`, `materialized_at`). Extended the
  `entityTypeMessage(Map dataOverrides = [:])` helper to default
  `description: '96-well sample plate'` and accept overrides; introduced
  a new `ENTITY_TYPE_MSG_UUID` constant and a new
  `updateEntityTypeMessage()` helper modelled on the
  `05-update-entity-type-add-property.json` shape. Added three new
  features:
  `'skips a typ + update message even after dropping the kind guard'`
  (proves the `update` action remains skipped),
  `'identical-payload bare entity-type typ duplicate is matching even
   when materialized_at differs'` (proves idempotent retry coverage),
  and `'differing-payload bare entity-type typ duplicate is conflicting
   and not overwritten'` (proves conflict logging without overwrite).
  Extended the `'mixed-message snapshot inserts in snapshot order with
   correct counts'` feature in place: the snapshot now contains
  `[loc, ppy (skip), typ link-type, typ bare entity-type, ent, lnk]`,
  the assertion now verifies `insertOrder == ['loc', 'typ', 'typ',
   'ent', 'lnk']`, the two `typ` insert ids are
  `[TYP_ID, ENT_TYPE_ID]` in snapshot order, `materialized == 5`, and
  `skippedUnsupported == 1` (only `ppy`). The existing
  `'materializes a typ link-type create root with all six declarative
   facts under properties'` and contents-flow features remain
  unchanged and continue to prove the link-type path still
  materializes.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — Added a focused feature
  `'bare entity-type typ create example uses the human-readable
   data.id, data.name, optional data.description, and no
   data.kind/data.links shape'` that reads
  `04-create-entity-type.json` and asserts
  `message.collection() == Collection.TYPE`,
  `message.action() == Action.CREATE`, the canonical `~ty~plate_96` id,
  `data.name == 'plate_96'`, `data.description == '96-well sample plate'`,
  `!data.containsKey('kind')`, `!data.containsKey('links')`, and
  `data.keySet() == ['id', 'name', 'description'] as Set`. The existing
  `EXAMPLE_PATHS` round-trip loop and the cross-reference feature
  `'entity transaction example sequence shares one txn id and the
   entity references the entity-type by id'` continue to cover the
  `04-…` ↔ `06-…` link.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`
  — Updated the spec-level Javadoc and the feature description from
  `open + ent + commit` to `open + typ + ent + commit`. Added a
  `TYP_COLLECTION = 'typ'` constant, published a bare entity-type
  `Message.newInstance(txn, JtpCollection.TYPE, Action.CREATE, [
   id: entityTypeId, name: 'plate_96',
   description: '96-well sample plate'])` ahead of the entity create,
  and added a wait/assert block for the materialized `typ` root that
  checks `_id`/`id`, `collection`, null `type_id`, inline
  `properties.name`/`description`, no `kind` discriminator, empty
  `links`, and `_head.provenance` pointing at the typ message uuid.
  Also asserts `entDoc.type_id == typDoc['_id']` to prove the entity
  references the materialized entity-type by id end-to-end. Added a
  cleanup step that removes the `typ` document by `_id` for the test's
  generated `entityTypeId`. Spec remains opt-in via `JADETIPI_IT_KAFKA`
  + Kafka reachable.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  — Updated the materialization sentence in
  "Committed Materialization Of Locations And Links" to list the full
  current supported set: `loc + create`, `typ + create` (both link-type
  records where `data.kind == "link_type"` and bare entity-type records
  where `data.kind` is absent), `lnk + create`, `ent + create`, and
  `grp + create`. The "intentionally not materialized" sentence now
  calls out `typ + update` property-reference changes specifically, and
  the historical "bare entity-type `typ` records are intentionally not
  materialized" wording is removed.

Verification commands run (all from
`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`):

- `docker compose -f docker/docker-compose.yml --profile mongodb up -d`
  — succeeded; brought the Mongo/Kafka/Couch/Keycloak stack up
  (mongodb container was already running, the other containers
  recreated and went healthy).
- `./gradlew :libraries:jade-tipi-dto:test --console=plain`
  — `BUILD SUCCESSFUL` in ~1s; new MessageSpec feature plus the
  existing EXAMPLE_PATHS round-trip and cross-reference features
  passed.
- `./gradlew :jade-tipi:test --console=plain`
  — `BUILD SUCCESSFUL` in ~7s; the flipped
  bare-entity-type materialization feature, the new
  `typ + update` skip feature, the two new bare-entity-type duplicate
  features, the extended mixed-message snapshot feature, and the
  unchanged `loc`/`typ link-type`/`lnk`/`grp`/`ent`/duplicate/error
  features all pass.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest
   --tests '*EntityCreateKafkaMaterializeIntegrationSpec*' --console=plain`
  — `BUILD SUCCESSFUL` in ~9s; the extended
  `open + typ + ent + commit` flow lands a root-shaped bare
  entity-type `typ` document and a root-shaped `ent` document whose
  `type_id` matches the materialized typ `_id`, with full
  `_head.provenance` on both.

Decisions taken per the director's pre-work review:

- Left `04-create-entity-type.json` unchanged. The existing flat
  `data.id`/`data.name`/`data.description` shape already matches the
  accepted human-readable bare entity-type contract, and the materializer's
  inline-properties fallback writes `properties: {name, description}`
  and `links: {}` exactly as specified.
- Removed the now-dead `DATA_KIND` and `LINK_TYPE_KIND` constants from
  the materializer (per pre-work default proposal). The
  `ContentsLinkReadService` private `LINK_TYPE_KIND` constant is
  unaffected.
- Relied on the existing
  `'materializes a typ link-type create root with all six declarative
   facts under properties'` feature (and the contents-flow snapshot
  feature) to prove the link-type path still materializes after the
  guard was dropped; no duplicate "kept-behavior" feature was added.
- Extended the mixed-message snapshot feature in place (per pre-work
  default proposal) rather than adding a new one.
- Deferred `typ + update` property-reference materialization to a
  future task, as documented in the pre-work decision and the director's
  pre-work review.

No setup blockers. Local Docker stack (mongodb + kafka + keycloak +
couchdb) was already running from a prior session; running
`docker compose up -d` recreated keycloak/couchdb cleanly. Gradle
wrapper cache is warm; no `./gradlew --stop` was needed.

---

STATUS: COMPLETED
TASK: TASK-028 — Human-readable Kafka entity submission path (implementation)
DATE: 2026-05-03
SUMMARY: Added `ent + create` root materialization to the existing
`CommittedTransactionMaterializer` so the human-readable
`open + ent + commit` Kafka transaction now lands a root-shaped `ent`
document in MongoDB with top-level `_id`, `id`, `collection: "ent"`,
`type_id` from the message payload, explicit empty `properties` and
`links`, and `_head.provenance` pointing at the source `txn_id`,
`commit_id`, and `msg_uuid`. Updated `06-create-entity.json` to make the
human-readable wire shape explicit with `data.properties: {}` and
`data.links: {}`; preserved the existing `~en~plate_a` and `~ty~plate_96`
ID strings verbatim per director direction. Bare entity-type
`typ + create` (no `data.kind`) remains intentionally `skippedUnsupported`;
no `data.kind: "entity_type"` discriminator was introduced; semantic
resolution of `data.type_id` against a committed `typ` record remains
deferred.

Files changed (this turn):

- `libraries/jade-tipi-dto/src/main/resources/example/message/06-create-entity.json`
  — added explicit empty `data.properties: {}` and `data.links: {}`
  blocks alongside the existing `data.id` and `data.type_id`. The
  `~en~plate_a` and `~ty~plate_96` ID strings are unchanged per the
  director's pre-work review (no broad ID-abbreviation cleanup).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — added `static final String COLLECTION_ENT = 'ent'` constant and a
  new `case COLLECTION_ENT: return true` branch in `isSupported()`
  alongside the existing `loc`/`lnk`/`grp`/`typ link-type` branches.
  Updated the class-level Javadoc supported-messages list to include
  `ent + create` and to note that semantic resolution of `data.type_id`
  is intentionally deferred. No other production code path changed:
  `buildDocument()` already routes non-`lnk` collections through the
  `data.properties`-Map preferred path so an `ent + create` with
  explicit `data.properties: {}` materializes as
  `properties: {}, links: {}` automatically; with only `id` and
  `type_id` the inline-properties fallback yields the same empty maps.
  `_head.provenance`, idempotent matching-duplicate handling, and
  conflicting-duplicate detection are reused unchanged for `ent`.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — refactored `entityCreateMessage(Map dataOverrides = [:])` to default
  `data.type_id`, `data.properties: [:]`, and `data.links: [:]` and to
  accept overrides for missing/blank-id and duplicate-payload scenarios.
  Added new constants `ENT_ID`, `ENT_TYPE_ID`, and `ENT_MSG_UUID`.
  Replaced the `'skips ppy and ent create messages'` feature with
  `'skips a ppy create message'` (since `ent` is no longer skipped).
  Added five new features:
  1. `'materializes an ent create as a root document with top-level
     type_id, empty properties, and empty links'` — captures
     `mongoTemplate.insert(_, 'ent')`, asserts root `_id`/`id`/
     `collection: 'ent'`/`type_id`/empty `properties`/empty `links`,
     and `_head.{schema_version, document_kind, root_id}` plus full
     `provenance.{txn_id, commit_id, msg_uuid, collection, action,
     committed_at, materialized_at}`. Asserts no legacy
     `_jt_provenance` field is written.
  2. `'ent create without payload properties or links defaults root
     properties and links to empty maps'` — uses an inline message with
     only `id` and `type_id`; asserts the inline-properties fallback
     yields `properties == [:]` and `links == [:]`.
  3. `'ent create with missing data.id is counted as skippedInvalid'` —
     mirrors the analogous `loc` and `grp` skip-on-missing-id features.
  4. `'ent create with blank or whitespace data.id is also counted as
     skippedInvalid'` — Spock `where:` block over `'', '   '`.
  5. `'identical-payload ent duplicate is matching even when
     materialized_at differs'` — stubs `springDuplicate()` insert error,
     stubs `findById(ENT_ID, Map.class, 'ent')` to return a payload-
     equivalent existing root with an earlier `materialized_at`,
     asserts `result.duplicateMatching == 1`.
  6. `'differing-payload ent duplicate is conflicting and not
     overwritten'` — same scaffolding but the existing root's `type_id`
     differs; asserts `result.conflictingDuplicate == 1` and that
     `updateFirst`/`save` are never called.
  Updated `'mixed-message snapshot inserts in snapshot order with
  correct counts'` to expect insert order `['loc', 'typ', 'ent',
  'lnk']`, `materialized == 4`, and `skippedUnsupported == 1` (the
  lone remaining skip is the `ppy` message).
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — added two Spock features that reuse the existing `EXAMPLE_PATHS`
  round-trip / schema-validation loops:
  1. `"ent create example uses the human-readable data.id,
     data.type_id, and explicit empty data.properties / data.links
     shape"` — reads `06-create-entity.json` and asserts
     `Collection.ENTITY` + `Action.CREATE`, the verbatim `data.id`
     ending in `~en~plate_a`, the verbatim `data.type_id` ending in
     `~ty~plate_96`, `data.properties == [:]`, `data.links == [:]`,
     and that the data root contains exactly
     `{id, type_id, properties, links}` (no leakage of human-authored
     facts next to `id`).
  2. `"entity transaction example sequence shares one txn id and the
     entity references the entity-type by id"` — reads `01-…`,
     `04-…`, `06-…`, `09-…`; asserts all four messages share the
     same `txn.uuid`; asserts `01`/`09` are `Collection.TRANSACTION`
     `OPEN`/`COMMIT`; asserts `04` is `Collection.TYPE` + `CREATE`
     and `06` is `Collection.ENTITY` + `CREATE`; asserts the
     in-resource cross-reference `entData.type_id == typData.id`.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`
  (new file) — opt-in (gated on `JADETIPI_IT_KAFKA=1` and a 2-second
  Kafka `AdminClient.describeCluster` probe) end-to-end Kafka → Mongo
  spec modeled on `TransactionMessageKafkaIngestIntegrationSpec`.
  Publishes one canonical `open + ent + commit` transaction to a
  per-spec topic and asserts:
  1. The committed `txn` header reaches `state == 'committed'` with a
     non-blank `commit_id`.
  2. The materialized `ent` document at `findById(entityId, Map,
     'ent')` carries top-level `_id == entityId`, `id == entityId`,
     `collection == 'ent'`, `type_id == entityTypeId`,
     `properties == [:]`, `links == [:]`, and
     `_head.schema_version == 1`, `_head.document_kind == 'root'`,
     `_head.root_id == entityId`, plus a full `_head.provenance`
     pointing at `txnId`, the actual `commit_id`, the published
     `entMsg.uuid()`, `collection: 'ent'`, `action: 'create'`, plus
     non-null `committed_at` and `materialized_at`.
  Per-feature `setup()` derives a fresh per-run `entityId`/
  `entityTypeId` from a short UUID so concurrent or repeat runs do
  not collide; `cleanup()` removes the `txn` rows by `txn_id` and the
  `ent` row by `_id`. The spec does not gate on Keycloak (matches
  the existing ingest spec, since this transaction never exercises
  HTTP), and the `test` profile's mock `ReactiveJwtDecoder` is not
  invoked.

Files NOT changed (consciously):

- `DIRECTION.md`, `docs/OVERVIEW.md`, and
  `docs/architecture/jade-tipi-object-model-design-brief.md` — already
  describe `ent` as a long-term materialized collection alongside
  `loc`/`lnk`/`typ`/`grp`/`ppy`/`uni`/`vdn`. No wording mismatch
  needed updating for this bounded `ent + create` materialization.
- `docs/architecture/kafka-transaction-message-vocabulary.md` —
  "Entity Creation" already documents the canonical
  `collection: "ent"` / `action: "create"` / `data.id` /
  `data.type_id` shape. The vocabulary code block uses
  `~en~plate_a` and `~ty~plate_96`, matching the preserved IDs in
  `06-create-entity.json`. No vocabulary change in this turn (the
  director directed against broad ID-abbreviation cleanup here).
- `04-create-entity-type.json` — the example remains the
  bare-entity-type `typ + create` shape that the materializer
  intentionally skips (no `data.kind: "entity_type"` discriminator was
  introduced). The DTO sequence spec validates the in-resource
  `data.type_id == 04-….data.id` cross-reference without requiring
  any change to `04-…`.
- `jade-tipi/src/main/resources/schema/message.schema.json` (out of
  OWNED_PATHS anyway) — `data` already validates as
  `SnakeCaseObject`, so the `06-…` shape with explicit empty
  `properties`/`links` round-trips through schema validation
  unchanged. Tightening the schema to require `ent.data.id` /
  `ent.data.type_id` is OUT_OF_SCOPE for TASK-028.
- `clients/kafka-kli/**`, `frontend/**`,
  `ContentsLinkReadService.groovy`, `TransactionService.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy` — none reference `ent`
  materialization specifically; the persistence service already
  invokes the materializer on commit, so an `ent` root is materialized
  end-to-end without any further wiring change.

Verification commands run (all pass):

- `./gradlew :libraries:jade-tipi-dto:test` — green; the new
  `06-create-entity.json` example continues to pass the round-trip and
  schema-validation loops, and the two new focused features
  (`"ent create example uses the human-readable data.id, data.type_id,
  and explicit empty data.properties / data.links shape"` and
  `"entity transaction example sequence shares one txn id and the
  entity references the entity-type by id"`) pass.
- `./gradlew :jade-tipi:test` — green; the materializer spec covers
  the new `ent + create` root-shape, missing/blank-id, idempotent
  duplicate, conflicting duplicate, and updated mixed-snapshot
  features; the Spring boot context-loads test continues to start
  against the running Mongo from the local Docker stack.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'` — green; the new
  spec drove `open + ent + commit` end-to-end and observed
  `Materialized ent root: id=jadetipi-itest-ent~ent~plate_…` in the
  service logs. Stack reachable: docker compose up confirmed
  `jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`, and
  `jade-tipi-couchdb` all healthy on `127.0.0.1` ports
  `27017`/`9092`/`8484`/`5984`.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*' --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'` — green; existing
  integration specs continue to pass after the materializer change.

No setup blockers were encountered: the local Docker stack
(`docker compose -f docker/docker-compose.yml ps`) was already running
healthy, no Gradle daemon restart was needed, and no Gradle wrapper
cache permission issue surfaced.

---

STATUS: COMPLETED
TASK: TASK-027 — Human-readable Kafka contents link submission path (implementation)
DATE: 2026-05-03
SUMMARY: Proved that the existing human-readable contents-flow Kafka examples
already round-trip end-to-end into the documented root-shaped `typ` and `lnk`
documents and remain consumable by `ContentsLinkReadService`, by adding two
focused Spock features. No example resource, schema, materializer source,
read-service source, HTTP endpoint, DTO type, or documentation file was
changed: source review (see `docs/agents/claude-1-next-step.md`) and the
director's pre-work review confirmed the shape is already correct. Added one
backend co-presence test that materializes the canonical `typ + create`
link-type and `lnk + create` snapshot pair through the real
`CommittedTransactionMaterializer` and asserts the captured Mongo documents
satisfy the dotted-path criteria the reader queries on. Added one DTO
co-presence test asserting that the `01 → 11 → 12 → 09` example trio shares a
single `txn.uuid`, that `12-…`'s `data.type_id` matches `11-…`'s `data.id`,
and that `12-…`'s endpoints respect `11-…`'s `allowed_*_collections`.

Files changed (this turn):

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — added one Spock feature, `'materialized typ link-type and lnk roots
  satisfy ContentsLinkReadService resolution criteria'`. The feature:
  1. Stubs `mongoTemplate.insert(_ as Map, 'typ')` and `... 'lnk'` to capture
     the documents the materializer hands to Mongo (no in-memory Mongo, no
     network).
  2. Materializes a snapshot containing `linkTypeMessage()` and
     `linkMessage()` (the existing helpers that mirror `11-create-contents-type.json`
     and `12-create-contents-link-plate-sample.json`).
  3. Asserts `result.materialized == 2` with `skippedUnsupported == 0`,
     `skippedInvalid == 0`, `duplicateMatching == 0`, `conflictingDuplicate == 0`.
  4. Asserts the captured `typ` doc carries `properties.kind == 'link_type'`
     and `properties.name == 'contents'` — the exact dotted-path criteria
     `ContentsLinkReadService.resolveContentsTypeIds()` queries on (root-shaped
     `typ` filter on `properties.kind == 'link_type'` AND
     `properties.name == 'contents'`).
  5. Asserts the captured `lnk` doc carries top-level `_id`, `collection ==
     'lnk'`, `type_id == capturedTyp._id` (the materialized cross-document
     reference between the typ root and the referencing lnk root),
     `left.endsWith('~loc~plate_b1')`, `right.endsWith('~ent~sample_x1')`,
     and `properties.position.{kind, label}` populated with the canonical
     plate-well facts.
  6. Asserts both roots carry `_head.provenance.collection` matching their
     source collection.
  ~50 lines of Spock added; no production code changes; no other features
  in the spec were modified.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — added one Spock feature, `"contents transaction example trio shares one
  txn id and pairs typ link-type with a referencing lnk create"`. The
  feature:
  1. Reads `01-open-transaction.json`, `11-create-contents-type.json`,
     `12-create-contents-link-plate-sample.json`, and
     `09-commit-transaction.json` with the existing `JsonMapper.fromJson`
     `Message` round-trip.
  2. Asserts all four messages share the same `txn.uuid` (the in-resource
     transaction chain that the materializer assumes).
  3. Asserts `01-…` and `09-…` are transaction-control messages
     (`Collection.TRANSACTION` + `Action.OPEN` / `Action.COMMIT`).
  4. Asserts `lnkData.type_id == typData.id` (the explicit cross-reference
     between the contents type and the contents link, which was previously
     only asserted per-message and not as a paired check).
  5. Asserts the lnk `data.left` is on `loc` and `data.right` is on `ent`,
     consistent with `typData.allowed_left_collections` /
     `allowed_right_collections`.
  ~35 lines of Spock added. No example file rewrite; the existing
  `EXAMPLE_PATHS` round-trip and schema-validation loops already exercise
  every example file independently.

Files NOT changed (in OWNED_PATHS but no edit was needed, as confirmed by
the accepted pre-work plan and the director's pre-work review):

- `DIRECTION.md` — already prescribes the canonical contents-link shape
  (`type_id`, `left`, `right`, instance `properties` with `position`); no
  wording mismatch.
- `docs/architecture/kafka-transaction-message-vocabulary.md` — already
  prescribes the human-readable `typ + create` link-type and `lnk + create`
  shape, names the `11-…` / `12-…` reference examples, and explicitly
  defers semantic validation. No edit needed.
- `docs/architecture/jade-tipi-object-model-design-brief.md` — references
  `contents` and well positions in line with the existing examples.
- `docs/OVERVIEW.md` — not load-bearing for this change.
- `libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`
  and `12-create-contents-link-plate-sample.json` — already match the
  canonical human-readable shape from `DIRECTION.md` verbatim. The
  `01 → 11 → 12 → 09` transaction sequence (shared
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`) is complete.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — `buildDocument()` already produces the documented root shape for both
  `typ + create` link-type and `lnk + create`. Touching it would expand
  scope past the bounded proof.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  (out of OWNED_PATHS anyway) — its query criteria already match the
  materialized roots; the new co-presence test confirms this.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  (out of OWNED_PATHS anyway) — semantic validation of `data.type_id`,
  `data.left`, and `data.right` is OUT_OF_SCOPE for TASK-027 and explicitly
  deferred by the architecture doc.
- `jade-tipi/src/integrationTest/...` — no new end-to-end Kafka spec was
  added. The existing `ContentsHttpReadIntegrationSpec` (run during
  verification, see below) already exercises the full Kafka → Mongo → HTTP
  contents-flow path and stayed green with the new tests in place.
- `docs/orchestrator/tasks/TASK-027-human-readable-kafka-contents-link-submission.md`
  — director owns the task status field; no developer-side edit.

Verification (run from `developers/claude-1/`, with the local Docker stack
already up — `jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`,
`jade-tipi-couchdb` all reporting `Up (healthy)` per `docker ps`):

- `./gradlew :libraries:jade-tipi-dto:test` — `BUILD SUCCESSFUL in 1s`. The
  new "contents transaction example trio shares one txn id and pairs typ
  link-type with a referencing lnk create" feature passed alongside the
  existing `EXAMPLE_PATHS` round-trip and schema-validation loops over
  `11-create-contents-type.json` and
  `12-create-contents-link-plate-sample.json`.
- `./gradlew :jade-tipi:test` — `BUILD SUCCESSFUL in 7s`. The new
  "materialized typ link-type and lnk roots satisfy
  ContentsLinkReadService resolution criteria" feature passed alongside
  the existing `CommittedTransactionMaterializerSpec` typ/lnk root-shape
  cases. `JadetipiApplicationTests.contextLoads` reached the running
  local Mongo at `jade-tipi-mongo`.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'` — `BUILD SUCCESSFUL in 11s`. The
  existing end-to-end Kafka → Mongo → HTTP contents-flow spec exercised
  the canonical contents-link wire shape against the running
  `jade-tipi-kafka` and `jade-tipi-mongo` containers and stayed green.

Setup commands referenced (per CLAUDE.md "Tooling Refresh"; not required
this turn because the stack was already running):

- `docker compose -f docker/docker-compose.yml up -d` — full local stack,
  required when the Kafka/Mongo containers are not already up.
- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` —
  Mongo only, sufficient for `:jade-tipi:test`'s `contextLoads`.
- `./gradlew --stop` — only if a stale Gradle daemon is implicated; not
  needed this turn.

Stay-in-scope check:

This turn edited exactly two source-of-truth files plus this report:

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
- `docs/agents/claude-1-changes.md` (this report; a base developer-owned
  path)

All three are inside TASK-027 `OWNED_PATHS` (the two test paths) or the
base developer-owned set. No HTTP submission endpoint, schema tightening,
materializer source change, read-service source change, `ent`
materialization, property-assignment materialization, permission
enforcement, endpoint projection maintenance, semantic reference
validation, `parent_location_id`, or nested Kafka operation DSL was
introduced.

---

STATUS: COMPLETED
TASK: TASK-026 — Human-readable Kafka loc submission path (implementation)
DATE: 2026-05-03
SUMMARY: Made the human-readable `loc + create` Kafka shape materialize
correctly into a root-shaped Mongo document. Rewrote
`libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
to the explicit `data.properties` / `data.links: {}` form (with `data.id` and
`type_id` omitted as the optional minimum). Extended
`CommittedTransactionMaterializer.buildDocument` so non-`lnk` collections
prefer `data.properties` (Map) and copy `data.links` (Map) verbatim into the
root, while keeping the legacy flat-flatten fallback for messages that put
`name`/`description` directly under `data` (the architecture-doc tolerance).
The `lnk` path is unchanged. Root contract (`_id`, `id`, `collection`,
`type_id`, `properties`, `links`, `_head.{schema_version, document_kind,
root_id, provenance}`) is preserved. Added focused DTO and Spock coverage
proving the new wire shape round-trips, schema-validates, and materializes
into the expected root JSON; existing legacy-shape tests remain green.

Files changed (this turn):

- `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
  — rewritten from the legacy flat shape (`data.{id, name, description}`) to
  the human-readable shape: top-level `collection: "loc"` /
  `action: "create"`, `data.id` (unchanged value to keep IDs stable across
  the existing `01-…` open / `09-…` commit pair), `data.properties.{name,
  description}` (same human values), and `data.links: {}`. `type_id` is
  intentionally omitted to honor "optional for now" per `DIRECTION.md` and
  `kafka-transaction-message-vocabulary.md`.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — `buildDocument` now branches in three cases for `loc`/`grp`/`typ link_type`:
  `lnk` (unchanged: `left`/`right` + `copyProperties(data.properties)`,
  `links: {}`); explicit shape when `data.properties instanceof Map`
  (`copyProperties(data.properties)` for root `properties` and
  `copyProperties(data.links)` for root `links`); legacy flat fallback
  (`buildInlineProperties(data)` and `links: {}`). `links` placement moved
  inside each branch so the explicit shape can carry submitted `data.links`
  through to the root. No change to `_id`, `id`, `collection`, `type_id`,
  duplicate-key handling, `isSamePayload`, or `_head.provenance`.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — added three Spock cases:
  1. "materializes a human-readable loc create with explicit data.properties
     and data.links into the root shape" — asserts `properties == [name:
     'freezer_a', description: '…']` (no doubly-nested `properties.properties`,
     no `properties.links` leak), `captured.links == [:]`, and full
     `_head.provenance` population.
  2. "human-readable loc create with explicit data.type_id sets top-level
     type_id and keeps properties clean" — confirms `captured.type_id` is the
     submitted typeId and `properties` excludes `id`/`type_id`.
  3. "human-readable loc create copies a non-empty data.links map verbatim
     into the root" — confirms `captured.links == linksValue` (e.g. `[contents:
     [endpoint_role: 'container', count: 4]]`) and that `properties` does not
     accidentally carry the `links` key.
  All pre-existing legacy-shape tests (e.g. "materializes a loc create as a
  root document with inline properties and _head.provenance", grp tests, lnk
  tests, duplicate/conflict tests) remain unchanged and pass.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — added "loc create example uses the human-readable data.properties /
  data.links shape" asserting that the rewritten `10-create-location.json`
  parses into a `Message` with `Collection.LOCATION`, `Action.CREATE`,
  `data.id` value preserved, `type_id` absent, `data.properties.{name,
  description}` populated, and `data.links == [:]`. The pre-existing
  `EXAMPLE_PATHS` round-trip + schema-validation loops continue to exercise
  `10-create-location.json` so the new shape also goes through
  `JsonMapper.fromJson` ↔ `toJson` and `message.schema.json`.

Files NOT changed (in OWNED_PATHS but no edit was needed):

- `DIRECTION.md` and `docs/architecture/jade-tipi-object-model-design-brief.md`
  — both already prescribe the new human-readable shape; no wording mismatch
  surfaced during implementation.
- `docs/architecture/kafka-transaction-message-vocabulary.md` — the
  "Human-Readable Authoring Rule" block (lines ~28–68) and the "Reference
  Examples" list (line ~296 referencing `10-create-location.json`) already
  prescribe and point to the rewritten file. No edit needed.
- `docs/OVERVIEW.md` — not load-bearing for this change.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  — out of `OWNED_PATHS`; also intentionally not tightened. The architecture
  doc explicitly tolerates the legacy shape, and the new shape is already
  accepted by the existing `SnakeCaseObject` rule for `data` on
  non-`grp` collections.
- `jade-tipi/src/integrationTest/...` — no new end-to-end Kafka spec was
  added. The pre-work plan defaulted to relying on the existing
  `TransactionMessageKafkaIngestIntegrationSpec` for the wire/ingest path
  and the new unit-level `CommittedTransactionMaterializerSpec` cases for
  the materialization path. The integration spec was rerun (see Verification)
  and remained green.
- `docs/orchestrator/tasks/TASK-026-human-readable-kafka-loc-submission.md`
  — director owns the task status field; no developer-side edit.

Verification (run from `developers/claude-1/`):

- `./gradlew :libraries:jade-tipi-dto:test` — `BUILD SUCCESSFUL in 3s`. All
  `MessageSpec` cases (including the new "loc create example uses the
  human-readable data.properties / data.links shape" and the existing
  `EXAMPLE_PATHS` round-trip + schema-validation loops on the rewritten
  `10-create-location.json`) green.
- `./gradlew :jade-tipi:test` — `BUILD SUCCESSFUL in 7s`. The full
  `CommittedTransactionMaterializerSpec` (legacy + 3 new cases) plus the
  rest of the backend unit suite passed. `JadetipiApplicationTests.contextLoads`
  reached the running local Mongo at `jade-tipi-mongo` (Docker container up).
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'` —
  `BUILD SUCCESSFUL in 9s`. The narrow Kafka ingest spec exercises the
  end-to-end producer → broker → `TransactionMessageKafkaIngestService`
  path against the running `jade-tipi-kafka` and `jade-tipi-mongo` Docker
  containers and stayed green with the materializer change in place.
- Local Docker stack at the time of verification:
  `jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`, and
  `jade-tipi-couchdb` all `Up (healthy)` per `docker ps`. No
  `docker compose up` was needed in this turn.

Setup commands referenced (per CLAUDE.md "Tooling Refresh"; not required
this turn because the stack was already running):

- `docker compose -f docker/docker-compose.yml up -d` — full local stack.
- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` —
  Mongo only, sufficient for `:jade-tipi:test`'s `contextLoads`.

Stay-in-scope check:

This turn edited exactly four files, all inside TASK-026 `OWNED_PATHS`:

- `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`

Plus this report file (`docs/agents/claude-1-changes.md`), which is a base
developer-owned path. No HTTP submission endpoint, schema tightening,
property-definition system, `ent` materialization, permission enforcement,
nested Kafka operation DSL, or `parent_location_id` was added.

---

STATUS: COMPLETED
TASK: TASK-025 — Plan TypeScript 6 frontend upgrade (implementation)
DATE: 2026-05-03
SUMMARY: Bumped only `typescript` from `^5.9.3` to `^6.0.3` in
`frontend/package.json`; `npm install` regenerated `frontend/package-lock.json`
end-to-end. No other source, config, or tsconfig change was required.
`cd frontend && npm run build` passed (Next 16.2.4 + Turbopack, TypeScript
phase 977ms), `cd frontend && npx tsc --noEmit` passed (TypeScript 6.0.3),
and `cd frontend && npx playwright test --project=chromium --timeout=15000`
ran 12/12 green in ~3.0s. The pre-work plan in
`docs/agents/claude-1-next-step.md` captured the smallest-change path; this
turn matched it exactly with no scope expansion.

Files changed (this turn):

- `frontend/package.json` — `"typescript": "^5.9.3"` → `"typescript": "^6.0.3"`.
  One-line dev-dependency bump. No other entry touched.
- `frontend/package-lock.json` — regenerated by
  `npm install --save-dev typescript@6.0.3`. Diff is bounded to the lockfile's
  root `devDependencies` map and the `node_modules/typescript` entry only
  (`5.9.3`/`-5.9.3.tgz`/integrity → `6.0.3`/`-6.0.3.tgz`/integrity). 8-line
  total lockfile change.
- `frontend/tsconfig.json` — **unchanged**. The TS 5.9.x tsconfig
  (`target: ES2017`, `lib: dom/dom.iterable/esnext`, `strict`, `skipLibCheck`,
  `module: esnext`, `moduleResolution: bundler`, `jsx: react-jsx`,
  `plugins: [{ name: next }]`, `.next/dev/types/**/*.ts` include) is fully
  compatible with TypeScript 6.0.3. No removed/deprecated compiler option was
  in use.
- No source files under `frontend/app/`, `frontend/components/`,
  `frontend/lib/`, `frontend/types/`, or `frontend/tests/` were touched.
  TypeScript 6.0.3 produced zero new diagnostics against the existing tree.

Verification:

- `cd frontend && npm install --save-dev typescript@6.0.3` — `changed 1
  package, audited 60 packages in 1s`. No new peer-dependency conflicts.
  Pre-existing 2 moderate audit advisories carried over from TASK-024
  (transitive `postcss <8.5.10` reachable through `next`); not in scope and
  unchanged by this turn.
- `cd frontend && npx tsc --version` → `Version 6.0.3` (confirmed installed
  binary matches the requested pin).
- `cd frontend && npm run build` — Next 16.2.4 (Turbopack) compiled in
  988ms, "Running TypeScript ... Finished TypeScript in 977ms" with no
  errors, generated all 8 static pages, and produced the expected 10-route
  table (`/`, `/_not-found`, `/admin/groups`, `/admin/groups/[id]`,
  `/admin/groups/new`, `/api/auth/[...nextauth]`, `/document/create`,
  `/document/edit/[id]`, `/list`, `/list/[id]`).
- `cd frontend && npx tsc --noEmit` — clean, zero diagnostics; this is
  redundant with `next build`'s TypeScript pass but exercises the standalone
  `tsc` invocation against the same `tsconfig.json`.
- `cd frontend && npx playwright test --project=chromium --timeout=15000` —
  12 passed, 0 failed, 3.0s. Suite covers
  `tests/admin-groups.spec.ts` (unauth + non-admin + admin) and
  `tests/frontend.spec.ts` (homepage, navigation, unauth list/create,
  NextAuth providers endpoint, env wiring). The pre-existing non-blocking
  `⚠ turbopack.root should be absolute, using:
  /Users/duncanscott/orchestrator/jade-tipi/developers/claude-1` warning is
  unchanged from TASK-024 and unrelated to TypeScript.
- `git diff --check origin/director..HEAD` — clean (no whitespace errors).

Why no source / tsconfig changes were needed:

- The TS 5.9 → 6.0 risk classes the pre-work flagged
  (`lib.dom.d.ts` updates, stricter narrowing, removed deprecated compiler
  options, generated `.next/types/**/*.ts` self-incompatibility, `Buffer`
  typing under `@types/node`, NextAuth module augmentation in
  `frontend/types/next-auth.d.ts`) all surfaced zero diagnostics under
  TypeScript 6.0.3 against the existing source. `skipLibCheck: true` plus
  the existing defensive narrowing patterns (`'token' in message` in
  `frontend/auth.ts`, `Array.isArray(...)` in `frontend/lib/admin-groups.ts`,
  awaited `params` in App Router pages) absorbed the bump.
- The `tsconfig.json` did not use any removed/deprecated compiler option
  (`--out`, `--keyofStringsOnly`, `--suppressExcessPropertyErrors`,
  `--suppressImplicitAnyIndexErrors`, `--noStrictGenericChecks`); current
  options remain valid in TS 6.0.
- Next 16.2.4's TypeScript Language Service plugin is built against
  `typescript@5.9.2`. The plugin is loaded by `tsserver` (editors), not by
  `next build`; the compile path uses the user's installed `tsc`. No editor
  warning surfaced via the build CLI; if it surfaces in IDEs, it is a
  developer-experience signal only and is documented as such in
  `docs/agents/claude-1-next-step.md` (Q-25-D).
- The first build under TS 6.0 invalidated and rewrote
  `frontend/tsconfig.tsbuildinfo` automatically; no manual cleanup was
  needed.

Scope check:

- Files touched in this implementation turn:
  `frontend/package.json`, `frontend/package-lock.json`, and the report file
  `docs/agents/claude-1-changes.md`. All three are inside TASK-025
  `OWNED_PATHS` (or the base report path) and inside the agent's owned
  paths. No file outside `frontend/` and `docs/agents/` was edited.
- TASK-025 `OUT_OF_SCOPE` items (Next.js, React, NextAuth/Auth.js, Tailwind,
  Playwright, backend, Docker, Keycloak, frontend UI) were not touched.
- No additional dependencies, no devDependency bumps beyond TypeScript, and
  no removal of any existing dependency.
- Pre-work directives (Q-25-A target `^6.0.3`; Q-25-B leave `target: ES2017`;
  Q-25-G let npm regenerate the lockfile) were applied as-default. The
  fallback paths (Q-25-C broad-churn STOP, Q-25-D plugin-warning tolerance,
  Q-25-E pre-existing-error STOP, Q-25-F Playwright browser install) did
  not need to be exercised.

Backout (if the director chooses to revert):

```sh
cd frontend && npm install --save-dev typescript@5.9.3
```

That single command restores the TASK-024 baseline. No source or
`tsconfig.json` revert is needed because none was changed in this turn.

Open follow-ups (informational, not in scope here):

- Pre-existing 2 moderate `npm audit` advisories on transitive `postcss
  <8.5.10` (reached through `next`) carry over from TASK-024. `npm audit fix
  --force` would downgrade `next` to `9.3.3` and is not the right
  remediation; an upstream `next` patch is the natural path.
- The Turbopack `turbopack.root should be absolute` warning persists from
  TASK-024 / TASK-022 and is unrelated to TypeScript.

---

STATUS: COMPLETED
TASK: TASK-024 — Update Next.js and npm dependencies
DATE: 2026-05-03
SUMMARY: Bumped every entry in `frontend/package.json` to the stable
target identified in pre-work, regenerated `frontend/package-lock.json`,
and patched the admin-groups Playwright tests for two upgrade-induced
behavior changes. `cd frontend && npm install`, `cd frontend && npm run
build`, and `cd frontend && npx playwright test --project=chromium`
all pass; the test suite is stable across three consecutive runs.

Versions applied (matches director-accepted pre-work table):

- `next` 15.5.4 → **16.2.4** (major)
- `react` 19.1.0 → **19.2.5**
- `react-dom` 19.1.0 → **19.2.5**
- `next-auth` `^5.0.0-beta.30` → **`5.0.0-beta.31`** (documented
  prerelease exception per Q-24-B; `latest` is legacy v4)
- `@playwright/test` `^1.56.1` → **`^1.59.1`**
- `@tailwindcss/postcss` `^4` → **`^4.2.4`**
- `tailwindcss` `^4` → **`^4.2.4`**
- `@types/node` `^20` → **`^20.19.39`** (pinned to Node 20 LTS line
  per Q-24-C; `latest` is 25.x)
- `@types/react` `^19` → **`^19.2.14`**
- `@types/react-dom` `^19` → **`^19.2.3`**
- `typescript` `^5` → **`^5.9.3`** (TS 6 deferred per Q-24-A; not
  taken in this turn)

Files changed (this turn):

- `frontend/package.json` — version bumps as above. Caret ranges retained
  for devDependencies, exact pins for the 4 production dependencies.
- `frontend/package-lock.json` — regenerated end-to-end by
  `npm install`, as planned (Q-24-G).
- `frontend/tests/admin-groups.spec.ts` — added an
  `activateClientSession(page)` helper that posts to NextAuth's
  `"next-auth"` `BroadcastChannel` after `page.goto(...)` to force
  `SessionProvider` to re-fetch the mocked `/api/auth/session` body.
  Applied the helper to the three tests that exercise the authenticated
  UI (forbidden-for-non-admin, admin Groups link, admin list view). Also
  tightened one assertion to `getByText('Analytics', { exact: true })`
  to avoid a Playwright-1.59 strict-mode violation against the link's
  three child `<div>` elements ("Analytics" name, the lowercase
  "analytics" inside the id, and "Analytics group" description).
- `frontend/tsconfig.json` — auto-modified by `next build` on Next 16,
  not by hand. The Next 16 toolchain reports the change as
  "mandatory": it sets `"jsx": "react-jsx"` (Next 16 requires the
  React automatic runtime) and adds `.next/dev/types/**/*.ts` to
  `include`. Re-running the build is now idempotent. **Boundary note:**
  `frontend/tsconfig.json` is not literally inside the TASK-024
  `OWNED_PATHS` list, but the change is a forced side-effect of the
  upgrade itself and reverting it would just be re-applied on every
  build. I kept the change to keep the working tree consistent with
  what `next build` produces.

Verification:

- Setup: `frontend/node_modules` was already populated in this worktree;
  `npm install` was run as the lockfile-regeneration step.
- `cd frontend && npm install` — 1 added, 6 removed, 34 changed,
  60 audited. 2 moderate audit advisories surfaced (transitive
  `postcss <8.5.10` reachable through `next`); npm's only suggested
  remediation is `npm audit fix --force` which would downgrade `next`
  to `9.3.3` — not taken. Out of TASK-024 scope.
- `cd frontend && npx --yes @next/codemod@latest upgrade latest` — the
  codemod detected we were already on `next@16.2.4` (because `npm
  install` had already pinned it) and reported "Current Next.js version
  is already on the target version 'v16.2.4'". No transformations were
  applied. Followup attempts to list individual codemods were denied as
  external-execution; build verification passed regardless, and no
  source changes were needed beyond the test patches above.
- `cd frontend && npm run build` — Next.js 16.2.4 (Turbopack), compiled
  successfully (~1.3s cold, ~0.9s warm), TypeScript pass, all 8 static
  pages generated. The Route table reports the expected 10 entries:
  `/`, `/_not-found`, `/admin/groups`, `/admin/groups/[id]`,
  `/admin/groups/new`, `/api/auth/[...nextauth]`, `/document/create`,
  `/document/edit/[id]`, `/list`, `/list/[id]`.
- `cd frontend && npx playwright test --project=chromium --timeout=15000`
  — 12 passed, 0 failed, ~2.7s. Re-ran 3 consecutive times to flake-
  check; all 3 runs were green. Suite covers `tests/frontend.spec.ts`
  (homepage, navigation, unauth list/create, NextAuth providers
  endpoint, env wiring) and `tests/admin-groups.spec.ts` (unauth +
  authenticated non-admin + authenticated admin flows for
  `/admin/groups`).
- The pre-existing non-blocking warning `⚠ turbopack.root should be
  absolute, using: /Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`
  still appears at the start of every `next build`. It existed before
  TASK-024 and is unrelated to this upgrade.

Why the test patch was needed (Next 16 / React 19.2 behavior change):

The mocked `/api/auth/session` route was not being hit on the
authenticated paths. NextAuth's `react.js` is byte-identical between
beta.30 and beta.31 (verified by `diff -r` against the published
beta.30 tarball), and `@auth/core` only changed in unrelated provider
files. The change in observable behavior is in
`SessionProvider`+SSR+React-19.2 hydration: when `await auth()` returns
`null` server-side, `SessionProvider` initializes `_session = null` and
the on-mount `useEffect` calls `_getSession()` with no event arg, which
hits the documented bail-out `(!event || _session === null)` and never
fetches. Posting any message to the `"next-auth"` `BroadcastChannel`
forces the storage-event branch (`_getSession({ event: "storage" })`),
which always re-fetches and consumes the mocked response. The helper
keeps pulsing until the response arrives so it doesn't race the
listener-attachment in `useEffect`.

The Playwright `getByText('Analytics')` strict-mode hit is a
Playwright 1.56 → 1.59 strictness change exposing pre-existing
ambiguity: the same `<a>` contains "Analytics" (name), the id
substring "analytics", and "Analytics group" (description). Adding
`{ exact: true }` resolves to only the name `<div>`.

Stay-in-scope check:

- Implementation edits stayed inside TASK-024 `OWNED_PATHS` for three
  files: `frontend/package.json`, `frontend/package-lock.json`,
  `frontend/tests/admin-groups.spec.ts`. The report file
  `docs/agents/claude-1-changes.md` is in the base ownership boundary.
- `frontend/tsconfig.json` is the one path-boundary exception; it was
  modified by the Next 16 build itself, not by hand, and reverting it
  would be re-applied on every build. Flagged here for director
  acceptance / scope expansion if needed.
- No backend, Gradle, Docker, Keycloak realm, env, frontend `.env.local`,
  or workspace orchestrator file was touched. No new packages were added.
- Preserved: `frontend/auth.ts` Keycloak refresh + `events.signOut`
  narrowing (TASK-023 fix), admin group-management UI under
  `frontend/app/admin/groups/**`, document CRUD pages under
  `frontend/app/document/**`, list pages under `frontend/app/list/**`,
  NextAuth provider config, and the `useSession()`-driven UX.

Open questions / blockers:

- Decision needed on `frontend/tsconfig.json` boundary acceptance. If
  the director wants it out of this turn, the Next 16 toolchain will
  re-apply the same change on the next `npm run build`, so the choice
  is really whether to keep it committed here or to fold it into a
  follow-up "tsconfig refresh" task.
- Decision deferred per Q-24-A: TypeScript 6 (`6.0.3`) is not taken in
  this turn; current TS line is `^5.9.3`.
- npm audit reports 2 moderate transitive `postcss` advisories
  reachable through `next`. Only npm-suggested fix is a destructive
  downgrade of `next` to 9.3.3, which is out of scope. No action taken.
- All other pre-work questions were resolved by their default proposals
  and the director's pre-work review.

---

STATUS: COMPLETED
TASK: TASK-023 — Fix NextAuth sign-out build error
DATE: 2026-05-03
SUMMARY: Applied the director-approved narrow type-safe repair in
`frontend/auth.ts`. The `events.signOut` callback now accepts the message
argument as a single value and narrows the NextAuth discriminated union with
the runtime `'token' in message` check before reading `message.token`.
Behavior is preserved: when the JWT arm carries an `idToken`, the same
Keycloak `protocol/openid-connect/logout` GET runs with the same
`id_token_hint` and `post_logout_redirect_uri` inside the same `try/catch`
with the same `console.error` on failure; for the database-session arm or a
missing token, the handler returns without calling Keycloak — matching the
prior `if (token?.idToken)` no-op path. `cd frontend && npm run build`
completed cleanly: compiled, linted/type-checked, and generated all 9 static
pages with no errors.

Files changed (this turn):

- `frontend/auth.ts` — replaced `async signOut({ token })` destructuring
  with `async signOut(message)` plus an early `if (!('token' in message) ||
  !message.token?.idToken) return` narrowing guard, then rebound `const
  token = message.token` so the existing logout body is byte-equivalent for
  the JWT arm. Added a short comment naming the discriminated-union reason.
  Provider config, JWT/session callbacks, `decodeJwtPayload`,
  `isAdminFromAccessToken`, and the `ADMIN_ROLE` constant are unchanged.
- No edits to `frontend/package.json`, `frontend/package-lock.json`,
  `frontend/tests/`, Keycloak realm import, backend code, or unrelated
  frontend routes.

Verification:

- Setup: `frontend/node_modules` was already present in this worktree;
  `npm install` was not required.
- Ran `cd frontend && npm run build`. Result: `Compiled successfully in
  1202ms`, `Linting and checking validity of types ...` passed, and
  `Generating static pages (9/9)` completed. Final route table reports the
  expected 10 entries (`/`, `/_not-found`, `/admin/groups`,
  `/admin/groups/[id]`, `/admin/groups/new`, `/api/auth/[...nextauth]`,
  `/document/create`, `/document/edit/[id]`, `/list`, `/list/[id]`).
- The previous `frontend/auth.ts:80:21 Property 'token' does not exist on
  type ...` error is gone. No new unrelated TypeScript blockers were
  surfaced by the build.
- One pre-existing non-blocking warning remained in the build output:
  `⚠ turbopack.root should be absolute, using:
  /Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`. This is a
  Next.js Turbopack config warning unrelated to `auth.ts`; the build still
  succeeded.

Stay-in-scope check:

- Implementation edits were limited to `frontend/auth.ts`, which is in
  `TASK-023` `OWNED_PATHS`. The report file
  `docs/agents/claude-1-changes.md` is in the base ownership boundary.
- No NextAuth provider, session/JWT callback, admin-role derivation,
  Keycloak realm import, backend, package, lockfile, frontend test, or
  unrelated frontend route was touched.

Open questions / blockers: None. The frontend build baseline is restored
end-to-end. Ready for director acceptance.

---

STATUS: BLOCKED
TASK: TASK-022 — Restore frontend build baseline
DATE: 2026-05-03
SUMMARY: Applied the director-approved narrow source fix in
`frontend/app/list/[id]/page.tsx`: kept the outer `if (!documentId) return;`
guard, moved the `accessToken` guard into the inner `loadDocument` function
(mirroring the sibling list-load effect just above), preserved the
`useEffect` dependency array, and did not change the `getDocument` API
helper signature. That source error in `frontend/app/list/[id]/page.tsx` is
no longer reported by `npm run build`. However, `cd frontend && npm run
build` then surfaces an unrelated pre-existing TypeScript error in
`frontend/auth.ts`, which is outside this task's `OWNED_PATHS`. Per the
task file's "additional unrelated pre-existing failures → stop and report"
clause and pre-work plan Q-22-B, I stop here and report rather than widen
scope.

Files changed (this turn):

- `frontend/app/list/[id]/page.tsx` — moved the `accessToken` guard from
  the outer `useEffect` body into the inner `loadDocument` async function
  so TypeScript narrows `accessToken` to `string` at the
  `getDocument(documentId, accessToken)` call site. Outer
  `if (!documentId) return;` and the `[documentId, accessToken]`
  dependency array are unchanged. Behavior is preserved: when the token
  is absent the effect still becomes a no-op; when the token arrives the
  effect re-runs via the dependency array.

Verification:

- Setup: `frontend/node_modules` was already present in this worktree;
  `npm install` was not required for this turn.
- Ran `cd frontend && npm run build`. Compilation succeeded. Type-checking
  no longer reports the previous error in `frontend/app/list/[id]/page.tsx`.
- Build now fails at the next pre-existing TypeScript error, in
  `frontend/auth.ts`. Exact failure (verbatim from `next build --turbopack`):

  ```
  ./auth.ts:80:21
  Type error: Property 'token' does not exist on type
  '{ session: void | AdapterSession | null | undefined; } | { token: JWT | null; }'.

      78 |   },
      79 |   events: {
  >   80 |     async signOut({ token }) {
         |                     ^
      81 |       // Construct Keycloak logout URL to terminate the Keycloak session
      82 |       if (token?.idToken) {
      83 |         const issuerUrl = process.env.KEYCLOAK_ISSUER!
  ```

  Root cause (source-only inspection, no fix attempted): NextAuth's
  `events.signOut` callback receives a discriminated union — either
  `{ session }` (database strategy) or `{ token }` (JWT strategy).
  Destructuring `{ token }` directly is not type-safe without first
  narrowing the variant. This file was added under `TASK-021`; it
  predates `TASK-022` and is outside `TASK-022`'s `OWNED_PATHS`
  (`frontend/app/list/[id]/page.tsx`, `frontend/tests/`,
  `docs/orchestrator/tasks/TASK-022-restore-frontend-build-baseline.md`).

Smallest next-step recommendation (for director review, not
implemented in this turn):

- Open a follow-on narrow task that authorizes editing
  `frontend/auth.ts` only, and changes the `events.signOut` callback to
  accept the union and narrow before reading `token`. The minimal shape
  that preserves current behavior:

  ```ts
  events: {
    async signOut(message) {
      if (!('token' in message)) return
      const { token } = message
      if (token?.idToken) {
        // existing Keycloak logout body unchanged
      }
    }
  }
  ```

  No other auth.ts behavior, NextAuth strategy choice, Keycloak realm
  import, or backend code needs to change. After that fix, re-run
  `cd frontend && npm run build` to confirm the full build baseline is
  restored, or to surface any further pre-existing errors one at a time.

Stay-in-scope check for this turn:

- This implementation turn edits exactly:
  - `frontend/app/list/[id]/page.tsx` — listed in `TASK-022`'s
    `OWNED_PATHS`.
  - `docs/agents/claude-1-changes.md` — base owned path; this report.
- `frontend/auth.ts` was inspected (read-only) for the next-step
  recommendation but not modified. No backend, realm import, package
  manifest, lockfile, admin group-management UI, or unrelated frontend
  route is touched.

STOP.

---

STATUS: READY_FOR_REVIEW
TASK: TASK-021 — Add admin group management
DATE: 2026-05-03
SUMMARY: Implemented the accepted pre-work plan for a local-development
admin path against `grp` records using regular Keycloak username/password
authentication. The Keycloak realm import now declares a `jade-tipi-admin`
realm role and a normal `dnscott` / Duncan Scott user (dev-only password
`dnscott`); the existing `testuser` is left non-admin so the 403 fixture is
preserved. The Spring backend gains a WebFlux JWT authentication converter
that pulls `realm_access.roles` and emits `ROLE_jade-tipi-admin`, wired
through `JwtAuthenticationConverter` and
`ReactiveJwtAuthenticationConverterAdapter` per the director's
implementation direction; `/api/admin/**` is now role-protected.
A new `GroupAdminController` exposes `POST/GET/GET/{id}/PUT` under
`/api/admin/groups`, backed by a new `GroupAdminService` that writes
root-shaped `grp` documents directly to MongoDB. Persisted documents follow
the accepted `TASK-020` root contract (`_id == id`, `collection == "grp"`,
`properties.{name, description, permissions}`, `links == {}`,
`_head.{schema_version, document_kind, root_id, provenance}`) with admin
direct writes marked under `_head.provenance.txn_id == commit_id ==
"admin~<uuid>"`. `PUT` fully replaces the editable fields and refreshes
provenance to `action == "update"` while leaving `_id`, `id`, and
`collection` untouched. Validation rejects blank names, descriptions over
4096 chars, blank permission keys, and any permission value other than
`"rw"` or `"r"`; duplicate ids surface as 409. The Next.js frontend gains
an `isAdmin` boolean signal derived in `auth.ts` from a local decode of the
access token's `realm_access.roles`; raw realm claims are never copied
onto the session. The header conditionally renders a Groups link when
`session.isAdmin` is true. New pages under `/admin/groups`, `/admin/groups/new`,
and `/admin/groups/[id]` provide the list/create/edit workflow, backed by a
new `frontend/lib/admin-groups.ts` typed fetch wrapper that reuses the
existing access-token attachment pattern. Backend unit coverage proves
realm-role conversion, controller route surface, and service create/list/
read/update behavior. An opt-in
`JADETIPI_IT_ADMIN_GROUPS=1`-gated integration spec drives the real
Keycloak password grant for `dnscott` / `testuser` and asserts the 401, 403,
and 200 paths plus root-shape persistence. Playwright coverage exercises the
three role flows (signed out, authenticated non-admin via session mocking,
authenticated admin via session mocking + API mocking).

Files changed:

- `docker/jade-tipi-realm.json` — added a `roles.realm` block with one
  realm role `jade-tipi-admin`, and a new normal user `dnscott`
  (firstName `Duncan`, lastName `Scott`, `dnscott@jade-tipi.org`,
  `emailVerified: true`, dev-only password `dnscott` with
  `temporary: false`, `realmRoles: ["jade-tipi-admin"]`). Existing
  `testuser` is unchanged.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/RealmAccessRolesAuthoritiesConverter.groovy`
  — new `Converter<Jwt, Collection<GrantedAuthority>>` that reads
  `realm_access.roles` and emits `ROLE_<name>` authorities. Tolerant of
  missing/wrong-typed claims and blank/null role entries.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/SecurityConfig.groovy`
  — added a `ReactiveJwtAuthenticationConverterAdapter` bean that wraps
  `JwtAuthenticationConverter` with the new authorities converter, wired
  the bean into `oauth2ResourceServer { jwt { ... } }`, and inserted
  `pathMatchers('/api/admin/**').hasRole('jade-tipi-admin')` ahead of the
  existing `anyExchange().authenticated()` rule. All other rules unchanged.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/GroupCreateRequest.groovy`,
  `GroupUpdateRequest.groovy`, `GroupRecord.groovy`, `GroupHead.groovy`,
  `GroupProvenance.groovy` — admin-endpoint DTOs. `GroupRecord` projects
  the stored Mongo doc; `GroupHead` and `GroupProvenance` surface the
  `_head` block with field names mapped from snake_case storage to
  camelCase JSON.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/GroupAdminService.groovy`
  — new service. `create` validates and writes a root-shaped `grp`
  document with `_id == id`, `collection == "grp"`, `properties` carrying
  `name`/optional `description`/`permissions`, `links == {}`, and a
  `_head.provenance` block with `txn_id == commit_id == "admin~<uuid>"`,
  `msgUuid`, `collection: "grp"`, `action: "create"`, and matching
  `committed_at`/`materialized_at`. Synthesizes a world-unique id of the
  form `jade-tipi-org~dev~<uuid>~grp~<slug-of-name>` when the caller does
  not supply one. `list` and `findById` project `GroupRecord` from the
  stored docs. `update` is a full-replacement rewrite of `properties.{name,
  description, permissions}` plus a fresh `_head.provenance` block with
  `action == "update"`; non-existent ids return `Mono.empty()`. Validation
  rejects blank `name`, oversized `name`/`description`, blank permission
  keys, and any permission value other than `"rw"` or `"r"`; duplicate ids
  surface as 409 via `ResponseStatusException`.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/GroupAdminController.groovy`
  — new WebFlux controller mounted at `/api/admin/groups`. Routes:
  `POST` create (201), `GET` list (200, `{ items: [...] }`), `GET /{id}`
  read (200/404), `PUT /{id}` update (200/404). All routes inject
  `@AuthenticationPrincipal Jwt jwt` to mirror the existing controller
  pattern.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/config/RealmAccessRolesAuthoritiesConverterSpec.groovy`
  — six features: emits `ROLE_<name>` for every realm role; missing
  `realm_access`; non-Map `realm_access`; missing `roles`; non-Collection
  `roles`; blank/null role entries are skipped; null `Jwt` returns empty.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/GroupAdminServiceSpec.groovy`
  — twelve features covering create with full and synthesized id,
  rejection of blank name/non-rw-r value/blank permission key, duplicate id
  → 409, list projection, findById hit and miss, update path including the
  `Update` document captured for assertion of properties + provenance, and
  update missing-id returning empty without a redundant `findById`.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/GroupAdminControllerSpec.groovy`
  — eight features using `WebTestClient.bindToController(...)` with the
  project's `AuthenticationPrincipalArgumentResolver` pattern: POST creates
  201; POST surfaces 400 and 409 through `GlobalExceptionHandler`; GET list
  returns `items[]`; GET by id returns 200 / 404; PUT updates 200 and
  returns 404 when the service returns empty.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/admin/GroupAdminAuthIntegrationSpec.groovy`
  — opt-in (`JADETIPI_IT_ADMIN_GROUPS=1` + Keycloak reachable) Spock
  integration spec. Acquires JWTs via direct-access password grant on the
  confidential `jade-tipi` client (its `directAccessGrantsEnabled` was
  already true). Asserts admin token can create + read + list a real
  root-shaped `grp` document; unauthenticated request to
  `/api/admin/groups` returns 401; `testuser` token returns 403. Cleans up
  exactly the records it creates.
- `frontend/auth.ts` — adds a small local JWT-payload decoder (no signature
  verification; the Spring backend remains the authoritative gate) to
  derive `token.isAdmin = realm_access.roles.includes("jade-tipi-admin")`
  on initial sign-in. Forwards only the boolean `session.isAdmin` plus
  the existing `accessToken` and `idToken` strings; raw realm claims are
  never copied onto session/token.
- `frontend/types/next-auth.d.ts` — extends `Session` and `JWT` types with
  optional `isAdmin: boolean`.
- `frontend/lib/admin-groups.ts` — new typed fetch wrapper for the four
  admin endpoints. Reuses the same bearer-token pattern as `lib/api.ts`
  via a local `ensureAccessToken` helper. Normalizes 404 to `null` for
  reads.
- `frontend/components/layout/Header.tsx` — conditionally appends a
  "Groups" nav link when `session.isAdmin === true`. Active-state styling
  preserved; pathname matching extended to nested admin routes via
  `startsWith` so `/admin/groups/[id]` keeps the link highlighted.
- `frontend/components/admin/GroupFormFields.tsx` — new shared form
  component used by both create and edit pages. Renders id (create only),
  name, description, and a dynamic permissions grid keyed by grp id with
  an `rw|r` access selector and add/remove rows.
- `frontend/app/admin/groups/page.tsx`,
  `frontend/app/admin/groups/new/page.tsx`,
  `frontend/app/admin/groups/[id]/page.tsx` — new admin pages. Each page
  short-circuits with a sign-in prompt when unauthenticated and a
  forbidden message when the session does not carry `isAdmin === true`;
  the backend remains the authoritative gate. The list page uses the typed
  fetch wrapper to render a list of cards; new submits to `POST` and
  redirects to the edit page on success; edit hydrates the form from the
  current record, submits a full-replacement `PUT`, and renders an
  expandable provenance block for audit display.
- `frontend/tests/admin-groups.spec.ts` — new Playwright spec. Mocks
  `/api/auth/session` for hermetic role coverage and mocks the backend
  list endpoint for the admin happy path. Verifies the three role flows:
  signed-out user does not see the Groups link and gets the sign-in prompt
  at `/admin/groups`; non-admin user does not see the link and gets the
  forbidden message; admin user sees the link, navigates successfully, and
  sees backend-returned items.
- `docs/user-authentication.md` — added a "Local Admin Group Management
  (TASK-021)" section describing the required Docker services, realm
  roles, realm users (including the dev-only `dnscott` password), the
  Next.js sign-in flow that derives `session.isAdmin`, the four admin
  endpoints with their provenance contract, and the opt-in integration
  spec command.
- `docs/OVERVIEW.md` — extended the Authentication section to mention the
  `dnscott` admin user and the role-protected `/api/admin/**` namespace,
  added an "Admin Group Management" entry to the API Highlights, and
  added a Realm Roles row under the Realm Configuration section.

Verification:

- `./gradlew :jade-tipi:compileGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*GroupAdmin*' --tests '*RealmAccessRoles*'`
  → BUILD SUCCESSFUL; new specs pass.
- `./gradlew :jade-tipi:test` (full suite) → BUILD SUCCESSFUL; no
  regressions.
- `cd frontend && npm install` → completed silently.
- `cd frontend && npm run build` → fails on a pre-existing TypeScript
  error in `frontend/app/list/[id]/page.tsx:52` (a `string | undefined`
  not assignable to `string` argument to `getDocument`) that was not
  introduced by this task. `git diff origin/director -- frontend/app/list/[id]/page.tsx`
  is empty, confirming the file is unchanged. The new admin paths
  (`frontend/auth.ts`, `frontend/types/next-auth.d.ts`,
  `frontend/lib/admin-groups.ts`, `frontend/components/layout/Header.tsx`,
  `frontend/components/admin/GroupFormFields.tsx`,
  `frontend/app/admin/groups/page.tsx`,
  `frontend/app/admin/groups/new/page.tsx`,
  `frontend/app/admin/groups/[id]/page.tsx`) are not flagged by the type
  checker.
- The opt-in integration spec was not executed in this turn because the
  Docker stack was not started here; the documented run command is
  `docker compose -f docker/docker-compose.yml up -d` followed by
  `JADETIPI_IT_ADMIN_GROUPS=1 ./gradlew :jade-tipi:integrationTest --tests '*GroupAdminAuthIntegrationSpec*'`.
- Playwright was not executed in this turn because the dev server was not
  started here; the documented run command is
  `cd frontend && npx playwright test admin-groups.spec.ts` (the spec
  config's `webServer` section will start `npm run dev` automatically).
  Note that the pre-existing `frontend/app/list/[id]/page.tsx` build error
  may also block `npm run dev` in strict mode; if so, that is unrelated to
  the admin path and outside this task's scope.

Stay-in-scope check:

All edits land inside `TASK-021`'s `OWNED_PATHS`. No edits to the accepted
`CommittedTransactionMaterializer`, the existing transaction WAL, the
ingest controllers, contents read service, root-document materializer,
extension-page storage, CouchDB replication, Kafka topic configuration,
OAuth/SASL hardening, the `clients/` or `libraries/` modules,
`frontend/package.json`, or any production account-lifecycle code. ORCID
was not integrated; the Keycloak `master` admin user is not used by the
application; no general object/property/link permission evaluation,
Keycloak group synchronization, object-level overrides,
property-value-level overrides, or password-management features were
added.

---

# Previous reports

STATUS: READY_FOR_REVIEW
TASK: TASK-020 — Define and materialize group records
DATE: 2026-05-02
SUMMARY: Implemented Shape A from the accepted pre-work: `data.permissions`
is a map keyed by world-unique `grp` IDs whose values are exactly `"rw"` or
`"r"`. Added the bounded schema exception so `message.schema.json` validates
the grp-id-keyed map without relaxing snake_case validation for any other
collection, added the canonical `13-create-group.json` example, extended
`MessageSpec` with positive and negative coverage, added `grp + create` to the
`CommittedTransactionMaterializer` whitelist, and added focused materializer
coverage for success, no-permissions, unsupported actions, and missing/blank
ID. Documented the wire and materialized shape in
`docs/architecture/kafka-transaction-message-vocabulary.md`. No permission
enforcement was added on any path; no membership service was introduced; no
object-level or property-value-level overrides were introduced.

Files changed (eight):

- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json` —
  changed top-level `properties.data` to `{ type: object, description: ... }`
  (instead of an unconditional `$ref: SnakeCaseObject`) and added a
  collection-conditional `allOf` entry that selects the data schema by
  collection: `if collection == "grp" then data = GroupData else data =
  SnakeCaseObject`. Added two new `$defs`: `GroupData` (snake_case
  `propertyNames`, explicit `properties.permissions` exception, and
  `additionalProperties: SnakeCaseValue` for everything else) and
  `Permissions` (object whose `additionalProperties` is `string` with
  `enum: ["rw", "r"]`). Because `properties.permissions` and
  `additionalProperties` are adjacent in the same `GroupData` schema, the
  recursive snake_case rule no longer applies to permissions map keys, while
  every non-grp message continues to validate against
  `SnakeCaseObject` exactly as before.
- `libraries/jade-tipi-dto/src/main/resources/example/message/13-create-group.json`
  — new canonical example. World-unique grp id
  `jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics`,
  payload carries `id`, `name`, `description`, and a two-entry `permissions`
  map with one `rw` peer and one `r` peer. UUIDs distinct from the existing
  example messages.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — appended `13-create-group.json` to `EXAMPLE_PATHS` (covers the
  parameterized round-trip and schema-validation features). Added three
  focused features: (a) `grp create example carries a permissions map keyed
  by world-unique grp ids` asserts collection, action, id format, name,
  description, and the two `rw`/`r` map entries; (b) `schema rejects a grp
  create whose permissions value is not 'rw' or 'r'` confirms the new
  `Permissions` enum is enforced; (c) `schema rejects a non-grp message whose
  data has a non-snake_case key` confirms the if/then/else conditional did
  not weaken `SnakeCaseObject` enforcement for non-grp collections.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — added `static final String COLLECTION_GRP = 'grp'`, added `case
  COLLECTION_GRP: return true` to `isSupported(...)` (returns true only when
  `action == ACTION_CREATE`, matching the loc/lnk precedent), and updated the
  class Javadoc bullet list to document `grp + create → grp` plus the
  permissions-pass-through and no-enforcement boundary. No change to
  `buildDocument`, `buildInlineProperties`, `handleInsertError`, or any
  helper. The default branch in `buildDocument` already produces the correct
  root shape with `permissions` copied through `properties.permissions`
  verbatim.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — added `GRP_ID`, `GRP_PEER_RW`, and `GRP_PEER_R` constants, added a
  `groupMessage(...)` helper, and added six focused features:
  `materializes a grp create as a root document with permissions under
  properties and _head.provenance` (success, including verbatim permissions
  pass-through); `grp create that omits permissions still materializes with
  name and description under properties` (optional permissions);
  `skips a grp update message`; `skips a grp delete message`;
  `skips a grp create with missing data.id`;
  `skips a grp create with blank data.id` (parameterized over `''` and
  whitespace). The existing matching/conflicting/insert-error duplicate
  features already exercise the generic `handleInsertError` path through
  `loc` and are not duplicated for `grp`, per the task's "where existing
  coverage does not already cover the generic path" wording.
- `docs/architecture/kafka-transaction-message-vocabulary.md` — added a new
  "Group Records And First-Pass Permissions" section that documents the wire
  payload, the schema exception, the materialized root layout
  (`properties.permissions` verbatim, `links: {}`, `_head.provenance`), and
  the explicit non-enforcement boundary. Updated the
  "Committed Materialization Of Locations And Links" paragraph and the
  bundled examples list to include `grp + create` and
  `13-create-group.json`.

Verification:

- `./gradlew :libraries:jade-tipi-dto:test --tests '*MessageSpec*'` →
  BUILD SUCCESSFUL; 44 tests passed (was 41), 0 failed, 0 skipped.
- `./gradlew :jade-tipi:compileGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
  → BUILD SUCCESSFUL; 30 tests passed (was 24), 0 failed, 0 skipped.

Stay-in-scope check:

All edits land inside TASK-020's `OWNED_PATHS` plus the explicit schema
expansion `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
listed under `Scope Expansion` for TASK-020 in `DIRECTIVES.md`. No edits to
Docker, CouchDB, frontend, Kafka topic ACLs, OAuth/SASL hardening, HTTP read
paths, `ContentsLinkReadService`, `_jt_provenance` fallback, or production
deployment files. No `DIRECTION.md`, `docs/README.md`, `docs/Jade-Tipi.md`,
or `docs/user-authentication.md` edits were necessary because their existing
prose already matches Shape A.

---

STATUS: READY_FOR_REVIEW
TASK: TASK-019 — Prototype Clarity/ESP container materialization
DATE: 2026-05-02
SUMMARY: Delivered the smallest executable prototype for the documented
Clarity/ESP container examples by adding a single Spock unit
specification that drives the existing
`CommittedTransactionMaterializer` against an in-memory snapshot of
seven `create` messages — four `loc` (ESP freezer/bin/plate plus the
Clarity tube), one transaction-local `typ~contents` link-type
declaration, and two `lnk + contents` edges
(Freezer→Bin and Bin→Plate). The new spec is the only file changed
this turn. No production source, materializer, read service, schema,
fixture, Docker, CouchDB, MongoDB, Kafka, security, frontend, HTTP,
or `_jt_provenance` behavior was added or modified, and no `ent`
materialization was introduced. The existing `loc + create`,
`typ + create` (with `data.kind == "link_type"`), and `lnk + create`
materializer paths from `TASK-014` and the existing
`ContentsLinkReadService` resolution path from `TASK-015` are
exercised verbatim.

Files changed (one):

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ClarityEspContainerMappingSpec.groovy`
  — new file. 10 Spock feature methods drive
  `materializer.materialize(snapshot)` against a single committed
  snapshot whose `messages` are the four documented `loc + create`
  payloads, the transaction-local `typ~contents + create` payload,
  and the two `lnk + contents + create` payloads. Open/commit data
  ride on the snapshot header (`openData`/`commitData`) since the
  materializer iterates only `snapshot.messages`. The spec uses a
  `Mock(ReactiveMongoTemplate)` that captures inserts per-collection
  and preserves insert order, mirroring the existing
  `CommittedTransactionMaterializerSpec` pattern.

Coverage in the new spec, mapped back to the accepted design
decisions in `docs/architecture/clarity-esp-container-mapping.md`:

1. `materializes seven prototype roots into loc, typ, and lnk in
   snapshot order` — `MaterializeResult` reports
   `materialized = 7`, `skippedUnsupported = 0`,
   `skippedInvalid = 0`, `duplicateMatching = 0`,
   `conflictingDuplicate = 0`. Per-collection insert counts are
   `loc = 4`, `typ = 1`, `lnk = 2`. Insert order is exactly
   freezer → bin → plate → tube → typ → freezer→bin link →
   bin→plate link, confirming snapshot-order processing across
   collections.
2. `ESP freezer loc has source-traceability properties and no
   parentage fields` — verifies the freezer root's `_id`/`id`,
   `collection`, `type_id == null`, empty `links`,
   `properties.{name, kind, barcode, source_system, source_id,
   source_type_id}`, the absence of `parent_location_id` /
   `parent_loc_id` / `parent_id` / `container` (D6), and that
   `_head` carries `schema_version = 1`, `document_kind = 'root'`,
   `root_id`, plus the expected `provenance` fields including
   `txn_id`, `commit_id`, `msg_uuid`, `collection = 'loc'`,
   `action = 'create'`, `committed_at`, and `materialized_at`.
3. `ESP bin loc preserves source numeric id and ESP type uuid` —
   verifies bin-specific properties including
   `source_numeric_id == 50` and the `019a3a49-3672-...` ESP type
   uuid alongside the `019a3a60-9628-...` source uuid; reconfirms
   no `parent_location_id`.
4. `ESP plate loc carries plate format and dimensions verbatim` —
   verifies the plate root carries `format = '96-well'`, `rows = 8`,
   `columns = 12`, plus its source ids; reconfirms no
   `parent_location_id`.
5. `Clarity tube loc preserves Clarity LIMSID and source state, with
   no contents link` — verifies the Clarity tube root carries
   `source_system = 'clarity'`, `source_id = '27-10000'`,
   `source_state = 'Populated'`; verifies the intentionally-dropped
   Clarity-only fields (`placement`, `field`, `xml`,
   `parent_location_id`) are absent; verifies no materialized `lnk`
   has the tube as `left` or `right`, matching the design's
   intentional omission of sample analytes from the prototype.
6. `typ contents declaration carries assignable_properties:
   ["position"] and directional labels` — verifies the
   transaction-local `typ~contents` root with
   `kind = 'link_type'`, `name = 'contents'`, the description,
   `left_role`, `right_role`, `left_to_right_label = 'contains'`,
   `right_to_left_label = 'contained_by'`, `allowed_left_collections
   = ['loc']`, `allowed_right_collections = ['loc', 'ent']`, and the
   key brief-alignment fact: `assignable_properties == ['position']`
   as a flat list. Asserts the absence of `required`, `optional`,
   and `defaults` to defend the brief's no-required/no-optional/
   no-defaults rule.
7. `freezer→bin lnk references transaction-local typ and carries
   freezer_slot position only on the link` — verifies
   `type_id == TYP_CONTENTS_ID` (the transaction-local id from D5),
   `left == LOC_FREEZER_ID`, `right == LOC_BIN_ID`, and
   `properties.position == { kind: 'freezer_slot', label: '2',
   slot: 2 }`. Asserts the link instance does not carry
   `left_to_right_label`, `right_to_left_label`, or per-instance
   `label` (D7).
8. `bin→plate lnk carries bin_slot position with row/column
   components` — verifies `left == LOC_BIN_ID`,
   `right == LOC_PLATE_ID`, and
   `properties.position == { kind: 'bin_slot', label: 'A1',
   row: 'A', column: 1 }`, mirroring the canonical
   `12-create-contents-link-plate-sample.json` shape applied to a
   bin slot.
9. `every materialized root carries the expected schema metadata
   and provenance shape` — single sweep over all seven roots
   (4 loc + 1 typ + 2 lnk) confirming
   `_head.schema_version == 1`,
   `_head.document_kind == 'root'`,
   `_head.root_id == doc._id`, provenance `txn_id`/`commit_id`/
   `committed_at` constants, `provenance.action == 'create'`,
   `provenance.collection == doc.collection`,
   `materialized_at instanceof Instant`, `links == [:]`, and
   absence of any legacy `_jt_provenance` field.
10. `all four loc roots are free of parent_location_id (D6)` —
    pure D6 invariant: every loc has none of
    `parent_location_id`, `parent_loc_id`, `parent_id`, or
    `container` under `properties`.

Design-decision provenance for the spec:

- D1 (loc + lnk + typ link-type only, no `ent`) — only seven roots
  are produced and the mock template never receives an insert into
  `ent`. Position #1 asserts insert-collection counts.
- D2 (wells as `lnk.properties.position`, not child `loc` rows) —
  position #8 verifies the well/slot shape on the link; no
  per-well `loc` row appears in `insertsByCollection.loc`.
- D3 (per-parent-kind position vocabulary) — positions #7 and #8
  pin `position.kind` to `freezer_slot` and `bin_slot`
  respectively, with parent-kind-specific component fields
  (numeric `slot` for the freezer, `row`/`column` for the bin).
- D4 (id convention with embedded source key plus
  `properties.source_id` / `properties.source_system`) —
  positions #2–#5 verify `source_*` properties on every loc;
  `_id` constants embed the source key (Clarity LIMSID, ESP UUID
  prefix).
- D5 (transaction-local `typ~contents` id) — positions #1 and #7
  pin `lnk.type_id == TYP_CONTENTS_ID` where
  `TYP_CONTENTS_ID == jade-tipi-org~dev~018fd849-c0c0-7000-
  8a01-c1a141e5e500~typ~contents` and `TYP_CONTENTS_ID` shares the
  same `txn-uuid` as the four `loc` and two `lnk` ids.
- D6 (parentage exclusive to `lnk + contents`) — positions #2–#5
  and #10 verify no parentage fields appear on any loc.
- D7 (directional labels live on the `typ` root only) — position
  #6 places the labels on `typ.properties.{left_to_right_label,
  right_to_left_label}` and position #7 verifies they are not
  repeated on `lnk` instances.
- Type-definition shape (flat
  `assignable_properties: ["position"]` plus link-type metadata
  only) — position #6 asserts the flat list and the absence of
  `required` / `optional` / `defaults`.

Verification commands and results:

- `./gradlew :jade-tipi:compileTestGroovy` — `BUILD SUCCESSFUL in 9s`,
  the new spec compiled cleanly. No new compile warnings.
- `./gradlew :jade-tipi:test --tests
  '*ClarityEspContainerMappingSpec*' --rerun-tasks` —
  `BUILD SUCCESSFUL in 4s`. The HTML test report at
  `jade-tipi/build/reports/tests/test/classes/org.jadetipi.jadetipi.service.ClarityEspContainerMappingSpec.html`
  shows all 10 feature methods with `class="success"` (passed).
- `./gradlew :jade-tipi:test` — `122 of 123` passing. The single
  failure is the pre-existing
  `JadetipiApplicationTests.contextLoads()`, which fails with
  `MongoTimeoutException` because the local Docker stack is not up
  in this worktree. Per project `CLAUDE.md`, that test requires
  `docker compose -f docker/docker-compose.yml up` to start
  MongoDB/Keycloak/Kafka before `./gradlew test`. This is a
  documented setup blocker for the existing context-load test, not
  a regression introduced by the new spec — the failure is in
  `JadetipiApplicationTests`, an existing test that does not touch
  any code added or modified by `TASK-019`.
- `./gradlew integrationTest` — not run. Per the accepted pre-work
  Q-19-C default and the task's "narrowest relevant Gradle checks"
  guidance, integration coverage is not part of this prototype.
  The proposed integration spec name reserved for any later
  director-approved integration turn is
  `*ClarityEspContainerMaterializationSpec*` (distinct from this
  unit spec's `*ClarityEspContainerMappingSpec*`), to be gated by
  `JADETIPI_IT_KAFKA=1`.

Setup commands documented (not run, not blockers):

- To run the full unit suite cleanly, including the existing
  Mongo-dependent context-load test:
  `docker compose -f docker/docker-compose.yml up -d` to bring up
  MongoDB/Keycloak/Kafka, then re-run `./gradlew :jade-tipi:test`.
- If a later turn revisits local CouchDB inspection for a
  follow-on sample, the documented commands remain
  `docker compose -f docker/docker-compose.yml up -d couchdb` and
  `docker compose -f docker/docker-compose.yml up -d couchdb-init`,
  with `COUCHDB_USER`/`COUCHDB_PASSWORD` in the orchestrator
  overlay
  `/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`.

Stay-in-scope check:

- Files touched by this turn:
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ClarityEspContainerMappingSpec.groovy`
  (new) and `docs/agents/claude-1-changes.md` (this report).
- Both paths are inside the active TASK-019 owned paths or the
  base claude-1 owned paths.
- `docker/couchdb-bootstrap.sh` is not touched and remains as it
  is on `director` from the separate human commit `8b54a0f`.
- No edits to
  `docs/architecture/clarity-esp-container-mapping.md`,
  `docs/architecture/jade-tipi-object-model-design-brief.md`,
  `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`,
  `docs/agents/claude-1.md`, or
  `docs/agents/claude-1-next-step.md` were made this turn — the
  director's accepted mapping doc and brief already cover the
  design, and the prototype implementation does not surface new
  design questions.
- No Docker, CouchDB, MongoDB, Kafka, frontend, security, DTO/
  schema, HTTP-endpoint, or production source changes.

Out-of-scope items intentionally not added (per task
`OUT_OF_SCOPE`):

- No `ent + create` materialization or fixtures.
- No Spring Boot CouchDB initialization, design-document loaders,
  or startup dependencies on CouchDB.
- No production Clarity/ESP import or sync code.
- No changes to the root-document contract,
  `ContentsLinkReadService` read API, Kafka listener, transaction
  persistence semantics, or production endpoints.
- No edit to `docker/couchdb-bootstrap.sh`.

---

STATUS: READY_FOR_REVIEW
TASK: TASK-017 — Add local CouchDB replication bootstrap
DATE: 2026-05-02
SUMMARY: Implemented the smallest Docker-native bootstrap for a local
single-node CouchDB plus resumable `_replicator`-document replication of
the JGI `clarity` and `esp-entity` databases into same-named local DBs,
matching the accepted `TASK-017` revision-2 pre-work plan. Three files
changed inside the granted owned paths: `docker/docker-compose.yml`,
new `docker/couchdb-bootstrap.sh`, and `.env.example`. Per the
director's revision-2 implementation direction, `couchdb:3.5` is pinned,
the bootstrap sidecar uses `alpine:3.20` with `apk add curl jq`, all
remote credentials and URLs are consumed only as container `env_file:
../.env` (no compose-side `${...}` interpolation), local CouchDB admin
credentials use new `COUCHDB_USER`/`COUCHDB_PASSWORD` placeholders that
are textually separate from the remote
`JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`JADE_TIPI_COUCHDB_ADMIN_PASSWORD`
pair, and `_replicator` documents are rewritten only when meaningful
fields differ to avoid scheduler churn on existing continuous
replications.

`docker/docker-compose.yml` adds:
- `couchdb` service: image `couchdb:3.5`, container
  `jade-tipi-couchdb`, loopback bind `127.0.0.1:5984`, `env_file:
  - ../.env` (no `${...}`), persistent named volumes
  `couchdb_data:/opt/couchdb/data` and
  `couchdb_config:/opt/couchdb/etc/local.d`,
  `restart: unless-stopped`, healthcheck via
  `curl -fsS http://127.0.0.1:5984/_up` (`interval: 10s`, `timeout: 5s`,
  `retries: 18`, `start_period: 30s`).
- `couchdb-init` sidecar: image `alpine:3.20`, container
  `jade-tipi-couchdb-init`,
  `depends_on: { couchdb: { condition: service_healthy } }`,
  `env_file: - ../.env`, literal `environment: COUCH_URL:
  http://couchdb:5984`, mounts the bootstrap script read-only at
  `/usr/local/bin/couchdb-bootstrap.sh`, entrypoint `/bin/sh -c`
  running `apk add --no-cache --quiet curl jq` then
  `exec sh /usr/local/bin/couchdb-bootstrap.sh`, `restart: "no"`.
- Top-level `volumes:` block extended with `couchdb_data:` and
  `couchdb_config:`. Pre-existing `mongodb`, `keycloak`, `kafka`, and
  `kafka-init` services are unchanged.

`docker/couchdb-bootstrap.sh` (new, POSIX `sh`, `set -eu`, no
`set -x`, ~150 lines incl. comments):
- Required-var enforcement via `: "${VAR:?...}"` for `COUCH_URL`,
  `COUCHDB_USER`, `COUCHDB_PASSWORD`,
  `JADE_TIPI_COUCHDB_ADMIN_USERNAME`,
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD`,
  `JADE_TIPI_COUCHDB_CLARITY_URL`, `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`.
  Missing variables fail with the variable name only and no value.
- Bounded `/_up` readiness retry (6 × 5s) tolerates a brief startup
  window after an unclean restart even though the compose
  `service_healthy` gate already covers steady-state.
- System DBs (`_users`, `_replicator`, `_global_changes`) and local
  target DBs (`clarity`, `esp-entity`) are PUT idempotently; HTTP 412
  is treated as already-present. Every other status produces a fatal
  log line with the status code only.
- `_replicator` doc bodies are built with `jq -n --arg ... --arg ...`
  so URLs/usernames/passwords are JSON-escaped against credentials
  containing `"`, `\`, or control bytes. Each doc carries
  structured `source.auth.basic` (preferred over URL userinfo),
  `target`, `continuous: true`, `create_target: false`, and
  `use_checkpoints: true`.
- The doc PUT path treats 201/202 as created; 409 enters a compare-
  then-rewrite branch that GETs the existing doc, projects both
  desired and existing onto exactly `source.url`,
  `source.auth.basic.username`, `source.auth.basic.password`,
  `target`, `continuous`, `create_target`, and `use_checkpoints`,
  compares with `jq -e '. == $o'`, and either logs
  `<id>: already configured (no change)` (no rewrite, no scheduler
  churn) or merges `_rev` and PUTs and logs
  `<id>: updated (prev rev=<7-char prefix>)`. Source URL,
  credentials, generated JSON, and full revs are never echoed.

`.env.example` adds two new local-only placeholders:
- `COUCHDB_USER=admin` and `COUCHDB_PASSWORD=admin` with a comment
  block explaining they are non-secret defaults for the
  loopback-bound local CouchDB single-node admin.
- The four existing `JADE_TIPI_COUCHDB_*` lines are unchanged and
  continue to be used exclusively for remote JGI source URLs and
  basic auth.

Out-of-scope boundaries preserved: no Spring Boot application
dependency on CouchDB, no MongoDB/Kafka/Keycloak service edits, no
Gradle/build/security/frontend/DTO/schema/canonical-example/
materializer/contents-read/Kafka-listener/integration-test changes,
no automated multi-GB remote pull, no writes to remote CouchDB, no
edits to `config/env/project.env.local.example` or any orchestrator
overlay.

Verification observed in this turn:
- `docker compose -f docker/docker-compose.yml config` rendered the
  full merged config without error and without dependence on host
  shell environment for the new services. Both new services
  (`couchdb`, `couchdb-init`) and both new named volumes
  (`couchdb_data`, `couchdb_config`) appear in the rendered output.

Verification deferred and reported as setup actions per the
directive's escape clause:
- The full operator-side matrix from the pre-work plan
  (`up -d couchdb`, `up -d couchdb-init`, `_all_dbs` /
  `_scheduler/jobs` inspection, `down`+`up -d` resumability proof,
  `--force-recreate couchdb-init` idempotency proof, negative-env
  check) was not exercised in this turn because the worktree-root
  `.env` does not yet declare `COUCHDB_USER` and `COUCHDB_PASSWORD`.
  Per the `TASK-017` directive, the documented setup action is to
  add those two variables to
  `/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
  (non-secret placeholder values such as `admin`/`admin` are
  acceptable for the loopback-bound local CouchDB) and
  re-materialize the worktree `.env` through the orchestrator path
  before exercising the bootstrap. No remote replication was
  started; no multi-GB pull was triggered; per `OUT_OF_SCOPE` and the
  directive, multi-GB replication is not part of automated
  acceptance verification.

Operator commands recorded in the task `LATEST_REPORT:` for observing
replication progress and recovering without exposing credentials:
`/_active_tasks`, `/_scheduler/jobs`,
`/_scheduler/docs/_replicator`, `down`+`up -d couchdb` for
checkpoint-resume from `couchdb_data`, and `up -d --force-recreate
couchdb-init` to rerun the bootstrap explicitly. Combined first-pull
data size is approximately 52 GB (~40.6 GB `clarity` static and
~11.1 GB `esp-entity` growing); operators on Docker Desktop should
size the Docker virtual disk before approving the network load.

FILES CHANGED:
- `docker/docker-compose.yml` — added `couchdb` and `couchdb-init`
  services and the two `couchdb_data`/`couchdb_config` named volumes;
  pre-existing services unchanged.
- `docker/couchdb-bootstrap.sh` — new POSIX `sh` script (mode 0755).
- `.env.example` — added `COUCHDB_USER` and `COUCHDB_PASSWORD`
  placeholders plus comment block; existing variables unchanged.
- `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`
  — appended a `LATEST_REPORT:` paragraph describing the changes,
  the verification observed, the deferred verification setup action,
  and the operator-visible progress/recovery commands.
- `docs/agents/claude-1-changes.md` — this status block.

The previous TASK-016 follow-up implementation report is preserved in
full below for review history.

---

PRIOR REPORT — TASK-016 follow-up fix (2026-05-02):

SUMMARY: Applied the director's narrow follow-up fix to the existing
`ContentsHttpReadIntegrationSpec.forward and reverse contents HTTP routes
return the materialized link` reverse-route assertion so it proves the same
expected flat JSON record as the forward route. The reverse-route block at
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy:401-415`
now asserts the missing required fields:
`$[0].properties.position.kind == 'plate_well'`,
`$[0].properties.position.row == 'A'`,
`$[0].properties.position.column == 1`,
`$[0].provenance.commit_id` exists, and
`$[0].provenance.msg_uuid == lnkMsg.uuid()` — in addition to the existing
`linkId`, `typeId`, `left`, `right`, `properties.position.label`, and
`provenance.txn_id` checks. The forward-route block remains unchanged.
Scope was strictly the assertion gap: no production code, test resources,
fixtures, materializer/read-service/controller, Kafka listener, DTO/schema/
canonical examples, Docker/Gradle/security config, frontend, response
envelopes, pagination, endpoint joins, semantic validation, update/delete
replay, or backfill changes were made.

Local verification (Docker stack `docker compose -f docker/docker-compose.yml ps`
showed `jade-tipi-kafka`, `jade-tipi-keycloak`, and `jade-tipi-mongo` healthy):
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'` — BUILD SUCCESSFUL.
  Per `jade-tipi/build/test-results/integrationTest/TEST-org.jadetipi.jadetipi.contents.ContentsHttpReadIntegrationSpec.xml`:
  `tests="2" skipped="0" failures="0" errors="0"` (forward/reverse feature
  4.803s; empty-result feature 0.024s).
- `./gradlew :jade-tipi:compileGroovy` and
  `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --rerun-tasks` — BUILD SUCCESSFUL.

FILES CHANGED:
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
  — added five missing reverse-route JSON-path assertions to match the
  forward-route contract (one feature, one then-block; ~5 lines added).
- `docs/agents/claude-1-changes.md` — this status block.

The previous TASK-016 implementation report (initial spec creation) is
preserved in full below for review history.

---

ORIGINAL TASK-016 IMPLEMENTATION REPORT (2026-05-02):

SUMMARY: Added one narrow opt-in integration spec
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
that proves a single canonical contents transaction can flow through
Kafka ingestion, the accepted `TASK-014` root-shaped committed
materialization, and the accepted `TASK-015` contents HTTP routes
hardened against root-shaped `typ`/`lnk` documents. The spec uses
`@SpringBootTest(webEnvironment = RANDOM_PORT)`,
`@AutoConfigureWebTestClient`, `@ActiveProfiles('test')`, and a class-level
`@IgnoreIf({ !ContentsHttpReadIntegrationSpec.integrationGateOpen() })`
gate that runs before the Spring context loads. The gate composes three
checks: the existing `JADETIPI_IT_KAFKA in ['1','true','TRUE','yes']`
env flag, a 2-second `AdminClient.describeCluster` probe against
`KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`) reused verbatim
from the accepted Kafka ingest spec pattern, and a new inline 2-second
`HttpURLConnection` HEAD-style GET probe against
`${KEYCLOAK_BASE_URL}/realms/jade-tipi/.well-known/openid-configuration`
(`KEYCLOAK_BASE_URL` resolved from `TEST_KEYCLOAK_URL` then `KEYCLOAK_URL`
then default `http://localhost:8484`); when any gate fails the spec is
skipped entirely so the `test` profile's real Keycloak issuer is never
resolved at startup. The Keycloak probe is inline per the director
decision and does not refactor `KeycloakTestHelper` or the accepted
Kafka ingest spec. Per-run isolation mirrors
`TransactionMessageKafkaIngestIntegrationSpec`:
`SHORT_UUID = UUID.randomUUID().toString().substring(0, 8)`,
`TEST_TOPIC = "jdtp-txn-itest-contents-${SHORT_UUID}"`,
`CONSUMER_GROUP = "jadetipi-itest-contents-${SHORT_UUID}"`, the topic is
created up-front in `@DynamicPropertySource overrideProperties(...)`
which also flips `jadetipi.kafka.enabled=true`, narrows
`jadetipi.kafka.txn-topic-pattern` to the per-run topic, sets the
unique consumer group, and shortens
`spring.kafka.consumer.properties.metadata.max.age.ms` to `2000`. A
`@Shared KafkaProducer<String, byte[]>` is created once in `setupSpec()`
and closed in `cleanupSpec()`, which also best-effort deletes the
per-run topic via `AdminClient.deleteTopics(...)`. Per feature, `setup()`
builds a fresh `txn = Transaction.newInstance('jade-itest-org', 'kafka',
'jade-itest-cli', 'itest-user')` plus per-feature object ids
(`containerId`, `typeId`, `linkId`, `contentId`) keyed on a fresh
8-character UUID prefix, and `cleanup()` deletes only this feature's
rows: `txn` rows by `txn_id == txnId` and materialized `loc`/`typ`/`lnk`
rows by exact `_id` — no collection drops, no global query, no shared
state assumption. Messages are constructed with DTO helpers per the
director decision (`Message.newInstance(txn, JtpCollection.<X>,
Action.<Y>, [...])` with inline `Map` payloads that mirror canonical
examples `10-create-location.json`, `11-create-contents-type.json`, and
`12-create-contents-link-plate-sample.json` while substituting per-run
ids), and produced as `byte[]` records keyed by `txnId` over a
`KafkaProducer` configured with `acks=all`, `String` key serializer,
and `byte[]` value serializer; the `send` helper does
`producer.send(record).get(10, SECONDS); producer.flush()` so
ordering of open → loc → typ → lnk → commit reaches the broker per
feature. The first feature `'forward and reverse contents HTTP routes
return the materialized link'` publishes that five-message transaction,
then awaits the committed `txn` header
(`state == 'committed' && commit_id != null`,
`record_type == 'transaction'`, non-empty `commit_id` String, exact
`_id == txnId`, `txn_id == txnId`), the root-shaped materialized `typ`
row by `_id == typeId` (asserting `_id == typeId`, `id == typeId`,
`collection == 'typ'`, `properties.kind == 'link_type'`,
`properties.name == 'contents'`, and `_head.provenance.txn_id == txnId`),
and the root-shaped materialized `lnk` row by `_id == linkId` (asserting
`_id == linkId`, `id == linkId`, `collection == 'lnk'`, top-level
`type_id == typeId`, `left == containerId`, `right == contentId`,
`properties.position.kind == 'plate_well'`,
`properties.position.label == 'A1'`, `properties.position.row == 'A'`,
`properties.position.column == 1`, `_head.provenance.txn_id == txnId`,
and a non-null `_head.provenance.materialized_at`). It then exercises
the forward HTTP route
`GET /api/contents/by-container/{id}` with the per-feature container id
through the autowired `WebTestClient` carrying a real Keycloak bearer
from `KeycloakTestHelper.getAccessToken()` and asserts HTTP 200 plus
`expectBody().jsonPath(...)` on `length() == 1`, `[0].linkId`,
`[0].typeId`, `[0].left`, `[0].right`, `[0].properties.position.kind/
label/row/column`, `[0].provenance.txn_id`,
`[0].provenance.commit_id` exists, and `[0].provenance.msg_uuid` equal
to the published `lnkMsg.uuid()`. Then it exercises the reverse route
`GET /api/contents/by-content/{id}` against the unresolved `ent`
content id and asserts the same materialized link is returned (same
`linkId`, `typeId`, `left`, `right`, `properties.position.label`, and
`provenance.txn_id`); it is acceptable per the director decision that
`right` is an unresolved `ent` id never materialized in this
transaction because endpoint joins and semantic reference validation
remain out of scope. The second feature
`'empty-result contents HTTP routes return 200 with an empty array'`
uses a fresh per-call container/content id that is never written to
either Kafka or Mongo, exercises both
`GET /api/contents/by-container/{id}` and
`GET /api/contents/by-content/{id}`, and asserts HTTP 200 plus
`expectBody().json('[]')` on each, satisfying the directive's
empty-result HTTP 200 `[]` requirement. Polling helpers (`awaitMongo`)
are private to this spec and copied verbatim from
`TransactionMessageKafkaIngestIntegrationSpec` per the directive
forbidding refactors of the accepted Kafka ingest spec; the awaitMongo
window uses `AWAIT_TIMEOUT = 30s`, `POLL_INTERVAL = 250ms`, and
`MONGO_BLOCK_TIMEOUT = 5s`. No production code change was made: the
contents read service, controller, materializer, persistence service,
Kafka listener, DTO/schema/example, Docker Compose, Gradle wiring,
security policy, frontend, response envelope, pagination, endpoint
joins, semantic write-time validation, update/delete replay, backfill,
and plate-shaped UI/API projections all remain unchanged. No
fixture/resource file was added — the integration test resource
directory remains empty for this task. `application-test.yml` was not
edited because the existing `test` profile already points the resource
server at `http://localhost:8484/realms/jade-tipi`, which is exactly
what the new Keycloak readiness probe checks; no source contradiction
required an edit. The `KeycloakTestHelper` is unchanged. No refactor
of `TransactionMessageKafkaIngestIntegrationSpec` was performed.

VERIFICATION:
The Docker stack was already running (`jade-tipi-mongo`,
`jade-tipi-keycloak healthy`, `jade-tipi-kafka healthy`,
`esplims-server-1 healthy` per `docker ps`). All required Gradle
commands passed in the `developers/claude-1` worktree:
- `./gradlew :jade-tipi:compileGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` → BUILD
  SUCCESSFUL with `compileIntegrationTestGroovy` executed (the new
  spec compiled cleanly on the first try).
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'` → BUILD SUCCESSFUL with the
  JUnit XML reporting `tests="2" skipped="0" failures="0"
  errors="0"` for `org.jadetipi.jadetipi.contents.ContentsHttpReadIntegrationSpec`
  (the forward/reverse feature and the empty-result feature both
  passed).
- `./gradlew :jade-tipi:test --rerun-tasks` → BUILD SUCCESSFUL with
  the aggregated unit suite reporting `tests=113 skipped=0 failures=0
  errors=0` across all `:jade-tipi` unit-test XMLs.
- Optional time-permitting check
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'` → BUILD
  SUCCESSFUL with `tests="2" skipped="0" failures="0" errors="0"`,
  confirming the new spec coexists with the accepted Kafka ingest
  integration pattern (per-run topic and consumer group isolated the
  runs).
No setup blocker was encountered; if a future runner hits Gradle/Docker
issues, the documented setup commands remain
`docker compose -f docker/docker-compose.yml up -d` and `./gradlew
--stop`, with the exact blocked command/error reported alongside.

FILES CHANGED:
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
  (new)
- `docs/agents/claude-1-changes.md` (this report)

---

STATUS: READY_FOR_REVIEW
TASK: TASK-015 — Update contents read service for root-shaped documents
DATE: 2026-05-01
SUMMARY: Updated `ContentsLinkReadService` so the contents read path
understands the root-shaped materialized `typ` and `lnk` documents
written by accepted `TASK-014`. Canonical `contents` link-type
declarations are now resolved against root-shaped `typ` documents using
the dotted criteria `properties.kind == "link_type"` AND
`properties.name == "contents"` (Spring Mongo
`Criteria.where("properties.kind").is(...)` /
`Criteria.where("properties.name").is(...)` over the `typ` collection,
sorted by `_id` ASC); the materializer copies every `typ + create`
payload field other than `id` and `type_id` into root `properties`, so a
`typ` declaration with `data.kind: "link_type"` and `data.name:
"contents"` now exposes those facts at `properties.kind` and
`properties.name`. The pre-`TASK-014` top-level `kind` / `name`
filtering would have returned zero declarations against root-shaped
`typ` rows and caused every contents read to short-circuit to empty.
`lnk` reads are unchanged in shape: the service continues to filter
`lnk.type_id $in <resolved type IDs>` and the requested endpoint field
(`left` for `findContents`, `right` for `findLocations`), sorts by `_id`
ASC, and never joins to `loc` or `ent`. Each materialized `lnk` row is
mapped verbatim into `ContentsLinkRecord` carrying the link `_id`,
top-level `type_id`, `left`, `right`, and instance `properties`
sub-document (including plate-well `properties.position`). Provenance
mapping moved off legacy top-level `_jt_provenance` to the new reserved
`_head.provenance` location written by `CommittedTransactionMaterializer`
(carrying `txn_id`, `commit_id`, `msg_uuid`, source `collection`, source
`action`, `committed_at`, and `materialized_at`). A small explicit
fallback to legacy top-level `_jt_provenance` is preserved for documents
materialized before the root shape was adopted, per the accepted
`TASK-013`/`TASK-015` allowance — the fallback is a single private
helper branch (`extractProvenance(Map row)`) that reads
`_head.provenance` first when `_head` is a map containing a `provenance`
sub-map and falls back to top-level `_jt_provenance` only when the
canonical location is missing or has no `provenance` sub-map. When
neither source is present, `provenance` is `null`, preserving the
existing wire contract. No production change was made to
`ContentsLinkReadController` (routes still bind to
`GET /api/contents/by-container/{id}` and
`GET /api/contents/by-content/{id}`, both delegate to the read service,
both return a flat `Flux<ContentsLinkRecord>` JSON array, blank/whitespace
ids still flow through service `Assert.hasText(...)` and
`GlobalExceptionHandler` as 400 `ErrorResponse`, and the controller has
only `ContentsLinkReadService` as a constructor collaborator). No
production change was made to `ContentsLinkRecord` either; the
serialized field set, types, and `provenance` map shape stay the same,
so the controller spec's existing JSON-path assertions on
`provenance.txn_id`, `provenance.commit_id`, `provenance.msg_uuid`,
`provenance.committed_at`, and `provenance.materialized_at` keep
passing — only the source of `provenance` moves inside the service. The
`ContentsLinkRecord` doc comment was refreshed to point at
`_head.provenance` with the legacy fallback note. Internally the
service's static field constants were retargeted: `FIELD_KIND` and
`FIELD_NAME` were removed (no longer used as top-level criteria) and
replaced with dotted constants `FIELD_PROPERTIES_KIND =
"properties.kind"` and `FIELD_PROPERTIES_NAME = "properties.name"`;
`FIELD_PROVENANCE = "_jt_provenance"` was replaced with
`FIELD_HEAD = "_head"`, `HEAD_PROVENANCE = "provenance"`, and
`FIELD_PROVENANCE_LEGACY = "_jt_provenance"` (kept only while the
fallback exists). Field constants for `lnk` mapping (`FIELD_TYPE_ID`,
`FIELD_LEFT`, `FIELD_RIGHT`, `FIELD_PROPERTIES`) and the canonical
`COLLECTION_TYP`, `COLLECTION_LNK`, `LINK_TYPE_KIND`,
`CONTENTS_TYPE_NAME`, and `FIELD_ID` constants are unchanged.
`ContentsLinkReadServiceSpec` was rewritten to drive the service with
root-shaped fixtures: `typRow(...)` now builds a self-describing root
with top-level `_id`, `id`, `collection: 'typ'`, `type_id: null`, root
`properties: [kind: 'link_type', name: 'contents']`, empty `links`, and
`_head.provenance` carrying `txn_id`, `commit_id`, `msg_uuid`,
source `collection`, source `action`, `committed_at`, and
`materialized_at`. `lnkRow(...)` now writes a root-shaped `lnk` with
top-level `_id`, `id`, `collection: 'lnk'`, `type_id`, `left`, `right`,
verbatim `properties.position` plate-well shape, empty `links`, and
`_head.provenance` (no top-level `_jt_provenance` on new root rows). A
new `legacyLnkRow(...)` helper builds the pre-`TASK-014` copied-data
shape (no `_head`, top-level `_jt_provenance`) for fallback coverage.
Captured-criteria assertions were updated to assert the dotted typ
criteria keys: `typQueryDoc.get('properties.kind') == 'link_type'` and
`typQueryDoc.get('properties.name') == 'contents'`; lnk query
assertions (`type_id $in`, endpoint key, `_id` ASC sort) are unchanged.
Provenance coverage was expanded from the old single
"missing-`_jt_provenance` returns null" case to four cases:
(1) canonical provenance read from `_head.provenance` on
forward and reverse lookups including the new source `collection`/
`action` keys; (2) legacy fallback to top-level `_jt_provenance` when
the document has no `_head`; (3) `_head: null` on an otherwise
root-shaped `lnk` (with no `_jt_provenance`) returns `null`
provenance; (4) `_head` present but with no `provenance` sub-map and no
`_jt_provenance` returns `null` provenance. Forward/reverse "verbatim
fields and provenance" features assert provenance fields read from
`_head.provenance`. The remaining features
(empty-typ short-circuit, empty-lnk results, no `loc`/`ent` joins,
unresolved-endpoint pass-through, mixed-collection forward results,
non-matching `typ` rows ignored, blank-id rejection, and
no-write assertions) preserve their behavior on the new root-shaped
fixtures. `ContentsLinkReadController` and
`ContentsLinkReadControllerSpec` are unchanged because the wire shape
is stable. `docs/architecture/kafka-transaction-message-vocabulary.md`
was updated only inside the "Reading `contents` Links" section: the
typ resolution language now describes
`properties.kind == "link_type"` and
`properties.name == "contents"` against root-shaped documents and
explains why dotted-path criteria match the materializer's root
shape; the per-record description now points at the
`_head.provenance` sub-document and notes the explicit narrow
fallback to legacy `_jt_provenance` for pre-`TASK-014` rows. The
adjacent "Committed Materialization Of Locations And Links" section
still describes the pre-`TASK-014` copied-data shape; that
materialization-section currency work was deferred from this task per
director decision and remains a separate `TASK-014` doc follow-up.
No changes were made to `CommittedTransactionMaterializer`,
`TransactionMessagePersistenceService`, Kafka listener behavior,
committed snapshot shape, DTO schemas, canonical examples, Docker
Compose, Gradle files, security, frontend, response envelopes,
pagination, endpoint joins to `loc`/`ent`, semantic reference
validation, endpoint projection maintenance, extension pages, pending
pages, update/delete replay, backfill, transaction-overlay reads,
required properties, default values, or integration coverage.
`TASK-012` was not resumed. Owned-paths boundary was respected: edits
stayed within
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`,
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`,
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy`,
`docs/architecture/kafka-transaction-message-vocabulary.md`, and
`docs/agents/claude-1-changes.md`.
`ContentsLinkReadController.groovy`,
`ContentsLinkReadControllerSpec.groovy`, and
`docs/orchestrator/tasks/TASK-015-root-shaped-contents-read-service.md`
are also on the task's `OWNED_PATHS` and were left untouched in this
turn (no production controller change was needed; the existing
controller spec already exercises the stable wire shape).
VERIFICATION: docker compose stack already healthy
(`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak` all up;
`docker compose -f docker/docker-compose.yml ps` confirmed).
`./gradlew :jade-tipi:compileGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:compileTestGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
BUILD SUCCESSFUL with the rewritten spec at
`tests=20, failures=0, errors=0, skipped=0` (was 14 features yielding
18 tests after `TASK-010`; the rewrite adds the legacy-fallback case
plus the `_head` null and `_head` without `provenance` cases on top of
the dotted-criteria refresh).
`./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
BUILD SUCCESSFUL with the unchanged spec at
`tests=10, failures=0, errors=0, skipped=0`.
`./gradlew :jade-tipi:test` BUILD SUCCESSFUL with the full unit suite
at `tests=113, failures=0, errors=0, skipped=0` (113 = 107 from
post-`TASK-011` baseline plus the +6 `ContentsLinkReadServiceSpec`
delta noted above).
NOTES: The legacy `_jt_provenance` fallback is a single explicit
branch in `ContentsLinkReadService.extractProvenance(Map row)` that
runs only when the canonical `_head.provenance` location is missing
or non-map. It was retained per the accepted pre-work allowance for
the copied-data-shape transition and can be removed in a focused
follow-up once stale legacy `lnk` rows are confirmed gone (one branch
plus one Spock case to delete). The "Committed Materialization Of
Locations And Links" section of
`docs/architecture/kafka-transaction-message-vocabulary.md` still
reflects the pre-`TASK-014` copied-data shape and is now stale; per
director direction this remains a separate doc follow-up to
`TASK-014` rather than in-scope for `TASK-015`.

STATUS: READY_FOR_REVIEW
TASK: TASK-014 — Implement root-shaped materialized documents
DATE: 2026-05-01
SUMMARY: Updated `CommittedTransactionMaterializer` so the currently
supported committed `loc + create`, `typ + create` (only when
`data.kind == "link_type"`), and `lnk + create` messages now write the
accepted `TASK-013` root document shape instead of the provisional
copied-data shape. The supported-message boundary, snapshot input via
`CommittedTransactionReadService`, missing/blank `data.id` skip,
unsupported-message skip, snapshot ordering, and non-duplicate insert
error propagation are unchanged. Each materialized document now has
`_id == data.id`, `id == data.id`, top-level
`collection == message.collection`, top-level `type_id`, explicit
`properties`, denormalized `links`, and a reserved `_head` sub-document.
For `loc` and `typ link_type` the materializer copies every payload
field other than `id` and `type_id` into root `properties` (so the
canonical location example carries `name` and `description` under
`properties`, and the `contents` link-type declaration carries `kind`,
`name`, `description`, `left_role`, `right_role`,
`left_to_right_label`, `right_to_left_label`, `allowed_left_collections`,
and `allowed_right_collections` under `properties`); top-level
`type_id` is taken from `data.type_id` when present and is `null`
otherwise. For `lnk` the materializer puts top-level `type_id`, `left`,
and `right` from the payload at the root and mirrors `data.properties`
under root `properties`, defaulting to `{}` when the payload omits it
(so the plate-well coordinate at `properties.position` is preserved
verbatim). For every supported root in this iteration `links` is an
empty map; per the accepted `TASK-013` decision, no endpoint stubs are
created and no endpoint `links` projections are populated, leaving
endpoint projection maintenance to a later task. `_head` carries
`schema_version: 1`, `document_kind: "root"`, `root_id == _id`, and a
nested `provenance` map with `txn_id`, `commit_id`, `msg_uuid`, source
`collection`, source `action`, `committed_at`, and a fresh
`materialized_at` `Instant`. The legacy reserved `_jt_provenance`
field is no longer written on new roots; provenance moved to
`_head.provenance`. Duplicate-id handling preserves prior behavior:
matching duplicates are idempotent successes (`duplicateMatching++`),
differing duplicates are logged at error and counted
(`conflictingDuplicate++`) without overwrite, a single conflict does
not block subsequent messages in the same snapshot, and non-duplicate
insert failures still propagate the original `Throwable`. Duplicate
comparison now ignores only `_head.provenance.materialized_at` so
retried matching payloads remain idempotent while every other payload
or provenance difference (including `commit_id`, `msg_uuid`, source
`action`, or any `properties` value) still surfaces as a conflict.
Internally, `buildDocument` was rewritten around four root-shape
helpers (`buildInlineProperties`, `copyProperties`, `buildHead`, and
`stripVolatileFields`); the materializer's static field constants were
expanded for the new root namespace (`FIELD_COLLECTION`,
`FIELD_TYPE_ID`, `FIELD_LEFT`, `FIELD_RIGHT`, `FIELD_PROPERTIES`,
`FIELD_LINKS`, `FIELD_HEAD`, `HEAD_*`, `PROV_COLLECTION`,
`PROV_ACTION`); `FIELD_PROVENANCE = '_jt_provenance'` was removed.
`CommittedTransactionMaterializerSpec` was rewritten to assert the
root shape on `loc`, `typ link_type`, and `lnk` (top-level `_id`,
`id`, `collection`, `type_id`, `left`/`right` for `lnk`, explicit
`properties`, empty `links`, and `_head` schema metadata + provenance
keys including `collection` and `action`), to confirm new roots do
not carry `_jt_provenance`, to add a `loc` case where `data.type_id`
is set so it appears at the root and is excluded from `properties`,
to add a `lnk` case with no payload `properties` that defaults root
`properties` to `{}`, to assert `_head.provenance` differences other
than `materialized_at` (e.g. differing `commit_id`) still surface as
conflicts, to assert non-duplicate insert errors propagate without
hitting `findById`, and to reshape the existing duplicate scenarios
(matching, differing, single-conflict-not-blocking) so the existing
documents are stored in the new root shape with full
`_head.provenance`. Identifiers, IDs, and helper messages
(`locMessage`, `linkTypeMessage`, `linkMessage`, `entityTypeMessage`,
`entityCreateMessage`, `propertyCreateMessage`,
`updateLocationMessage`, `springDuplicate`) and unrelated lifecycle
features (`materialize(txnId)` empty/non-empty, blank txnId, null
snapshot, mixed ordering across `loc`/`typ`/`lnk` with `ppy`/`ent`
skips) were preserved. No changes to `ContentsLinkReadService`,
`ContentsLinkRecord`, `ContentsLinkReadController`, integration
tests, DTO schemas, canonical examples, Docker Compose, Gradle files,
Kafka listener behavior, security, frontend, response envelopes,
pagination, endpoint joins, semantic reference validation, endpoint
projection maintenance, extension pages, pending pages, update/delete
replay, backfill, transaction-overlay reads, required properties, or
default values were made; `TASK-012` was not resumed. Owned-paths
boundary was respected: edits stayed within
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
`docs/agents/claude-1-changes.md`, and (left untouched in this turn)
the active task file
`docs/orchestrator/tasks/TASK-014-materialized-root-document-materializer.md`.
VERIFICATION: docker compose stack already healthy
(`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak` all up;
`docker compose -f docker/docker-compose.yml ps` confirmed).
`./gradlew :jade-tipi:compileGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:compileTestGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
BUILD SUCCESSFUL with the rewritten spec at
`tests=23, failures=0, errors=0, skipped=0` (was 16 features yielding
22 tests after TASK-009; the rewrite adds explicit `data.type_id`
loc, lnk-without-properties default, conflicting `_head.provenance`
when only `materialized_at` is allowed to differ, and non-duplicate
insert error propagation while consolidating shape assertions).
`./gradlew :jade-tipi:test` BUILD SUCCESSFUL with the full unit suite
green across all 11 specs at zero failures and zero errors:
`CommittedTransactionMaterializerSpec` (23),
`CommittedTransactionReadControllerSpec` (5),
`ContentsLinkReadControllerSpec` (10), `JadetipiApplicationTests`
(1), `TransactionMessageListenerSpec` (4), `MongoDbInitializerSpec`
(2), `DocumentServiceMongoDbImplSpec` (9),
`CommittedTransactionReadServiceSpec` (12),
`ContentsLinkReadServiceSpec` (18),
`TransactionMessagePersistenceServiceSpec` (15), and
`TransactionServiceSpec` (12) — 111 tests in total. No integration
tests were run because TASK-014 is unit-only and the directives list
no integration verification target.

STATUS: READY_FOR_REVIEW
TASK: TASK-011 — Plan contents location HTTP read adapter
DATE: 2026-05-01
SUMMARY: Implemented the smallest HTTP/WebFlux adapter over the accepted
`ContentsLinkReadService` so callers can answer the two `DIRECTION.md`
contents questions through the backend without changing materialization,
write semantics, or service-level query behavior. New
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
is a thin `@RestController` whose constructor takes only
`ContentsLinkReadService` (no Mongo, no materializer, no write-side
collaborators). It uses `@RequestMapping('/api/contents')` and exposes two
`@GetMapping` methods returning `Flux<ContentsLinkRecord>` directly:
`GET /api/contents/by-container/{id}` delegates to
`ContentsLinkReadService.findContents(id)` (forward, `lnk.left == id`,
"what are the contents of this container?") and
`GET /api/contents/by-content/{id}` delegates to
`ContentsLinkReadService.findLocations(id)` (reverse, `lnk.right == id`,
"where is this object located?"). Both methods take
`@PathVariable('id') String id` and an unused
`@AuthenticationPrincipal Jwt jwt` parameter for parity with
`CommittedTransactionReadController`; controller-side authorization,
scoping policy, pagination, response envelope, and endpoint-resolution
joins to `loc`/`ent` were all kept out. Empty service results map to
HTTP 200 with body `[]` (the WebFlux default for `Flux<T>`); blank or
whitespace-only ids surface the service `Assert.hasText(...)`
`IllegalArgumentException` as a 400 `ErrorResponse` through the existing
`GlobalExceptionHandler`, with `status=400`, `error="Bad Request"`, and
the service's exact `containerId/objectId must not be blank` message.
Neither route returns 404; the empty-array contract intentionally
collapses "no canonical `contents` declaration", "no matching `lnk`
rows", and "endpoint id absent in `loc`/`ent`" into one HTTP status so
HTTP cannot lie about materialization timing or speak about
`loc`/`ent` resolution. New unit spec
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadControllerSpec.groovy`
mirrors `CommittedTransactionReadControllerSpec`: it builds a
`WebTestClient.bindToController(controller)` with the real
`GlobalExceptionHandler` advice and the
`AuthenticationPrincipalArgumentResolver` from
`ReactiveAdapterRegistry.getSharedInstance()`, mocks only the
`ContentsLinkReadService` collaborator, and runs 10 focused features.
Forward coverage: 200 JSON serialization including `linkId`, `typeId`,
`left`, `right`, nested `properties.position` (`kind`, `label`, `row`,
`column`) for plate-well placements, and the full `_jt_provenance`
sub-document (`txn_id`, `commit_id`, `msg_uuid`, `committed_at`,
`materialized_at`); service-order preservation across two records;
single-collaborator delegation with `1 * findContents(...)` and
`0 * _`; 200 `[]` on `Flux.empty()`; whitespace-only id 400 path through
the real `GlobalExceptionHandler` with the service's literal blank
message. Reverse coverage mirrors all four. Structural coverage adds a
reflection assertion that the only constructor argument is
`ContentsLinkReadService` (matching the `CommittedTransactionReadController`
precedent) and a literal route-path assertion that pins
`/api/contents/by-container/{id}` and `/api/contents/by-content/{id}` so
a rename in either direction breaks the spec. A short HTTP-route
paragraph was appended to the existing "Reading `contents` Links"
section of `docs/architecture/kafka-transaction-message-vocabulary.md`,
documenting both routes, the empty-array success contract, and the 400
path through `GlobalExceptionHandler`. No new HTTP DTO, no new exception
handler, no `SecurityConfig` change, no `application.yml` change, no
build-file change, no Docker Compose change, no
DTO/schema/example/frontend change, no `ContentsLinkReadService`
semantic change, no controller-side input validation, and no
integration test were added. `OUT_OF_SCOPE` boundaries from the task
file are preserved in full.
VERIFICATION: docker compose stack already healthy (`jade-tipi-mongo`,
`jade-tipi-kafka`, `jade-tipi-keycloak` all up; `docker compose ps`
confirmed). `./gradlew :jade-tipi:compileGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:compileTestGroovy` BUILD SUCCESSFUL.
`./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
BUILD SUCCESSFUL with the new spec at
`tests=10, failures=0, errors=0, skipped=0`. `./gradlew :jade-tipi:test`
BUILD SUCCESSFUL with the full unit suite at `tests=107, failures=0,
errors=0, skipped=0` across 11 specs (was 97 across 10 specs after
TASK-010; +10 new in `ContentsLinkReadControllerSpec`), including
`CommittedTransactionReadControllerSpec` (5),
`ContentsLinkReadServiceSpec` (18),
`CommittedTransactionMaterializerSpec` (19),
`CommittedTransactionReadServiceSpec` (12),
`TransactionMessagePersistenceServiceSpec` (15), and
`JadetipiApplicationTests.contextLoads` (1).

## TASK-011 — Plan contents location HTTP read adapter

Director moved `TASK-011` to `READY_FOR_IMPLEMENTATION` on 2026-05-01
with implementation directives recorded in the
`TASK-011 Director Pre-work Review` block of `DIRECTIVES.md` (signal
`PROCEED_TO_IMPLEMENTATION`) and reflected in the
`docs/orchestrator/tasks/TASK-011-contents-location-http-read-adapter-prework.md`
`LATEST_REPORT`. Implementation done on 2026-05-01.

### Production source changes

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
  (new) — thin WebFlux read adapter over `ContentsLinkReadService`.
  - `@Slf4j @RestController @RequestMapping('/api/contents')`.
  - Single-arg constructor: `ContentsLinkReadController(ContentsLinkReadService readService)`.
    Stays Kafka-free, Mongo-free, materializer-free, write-side-free at the
    controller layer.
  - `@GetMapping('/by-container/{id}') Flux<ContentsLinkRecord>
    getContentsByContainer(@PathVariable('id') String id,
    @AuthenticationPrincipal Jwt jwt)` — forward lookup, returns
    `readService.findContents(id)` directly.
  - `@GetMapping('/by-content/{id}') Flux<ContentsLinkRecord>
    getContentsByContent(@PathVariable('id') String id,
    @AuthenticationPrincipal Jwt jwt)` — reverse lookup, returns
    `readService.findLocations(id)` directly.
  - One `log.debug` per route mirrors
    `CommittedTransactionReadController.getSnapshot`. The `jwt` parameter is
    unused inside both bodies; it is present for parity with the existing
    authenticated read controller and to keep the
    `AuthenticationPrincipalArgumentResolver` chain identical for all
    `/api/**` reads.
  - No `Mono<ResponseEntity<...>>`, no `block()`, no `collectList()`, no
    re-sort, no dedup, no in-memory filter, no `defaultIfEmpty`. The
    Spring WebFlux runtime serializes `Flux<T>` as a flat JSON array,
    using the empty body `[]` for an empty stream — no controller-side
    code is needed for the empty-result contract.
  - No controller-side input validation; blank/whitespace ids hit the
    service `Assert.hasText(...)` and surface through
    `GlobalExceptionHandler.handleIllegalArgument` as a 400
    `ErrorResponse`.

### Test changes

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadControllerSpec.groovy`
  (new) — pure Spock controller spec.
  - `Mock(ContentsLinkReadService)` is the only collaborator; no Spring
    context, no Mongo, no Kafka, no Keycloak.
  - `WebTestClient.bindToController(controller)` builds the test client,
    `.controllerAdvice(new GlobalExceptionHandler())` wires the real
    advice, and `.argumentResolvers { ... addCustomResolver(new
    AuthenticationPrincipalArgumentResolver(ReactiveAdapterRegistry
    .getSharedInstance())) }` lets `@AuthenticationPrincipal Jwt jwt`
    resolve to `null` without a server, exactly as in
    `CommittedTransactionReadControllerSpec`.
  - Coverage (10 features):
    1. Forward route 200 JSON serialization preserves `linkId`, `typeId`,
       `left`, `right`, `properties.position.{kind,label,row,column}`, and
       `provenance.{txn_id,commit_id,msg_uuid,committed_at,
       materialized_at}` for two records, asserted via `jsonPath`.
    2. Forward route delegates to `findContents(...)` only;
       `1 * readService.findContents(CONTAINER_ID) >> ...` plus `0 * _`
       proves no other collaborator is touched.
    3. Forward route 200 with empty array (`$.length() == 0`) when
       service emits `Flux.empty()`; status is **not** 404.
    4. Forward route blank id (`'   '`) flows through service
       `Assert.hasText` to a 400 `ErrorResponse` whose `status == 400`,
       `error == 'Bad Request'`, and `message ==
       'containerId must not be blank'`.
    5. Reverse route 200 JSON serialization preserves identical fields
       for two records.
    6. Reverse route delegates to `findLocations(...)` only with
       `0 * _`.
    7. Reverse route 200 empty array on `Flux.empty()`.
    8. Reverse route blank id surfaces
       `'objectId must not be blank'` as 400.
    9. Reflection assertion: only constructor parameter is
       `ContentsLinkReadService` (matches the
       `CommittedTransactionReadControllerSpec`
       `controller has no direct Mongo collaborator` precedent).
    10. Literal route-path assertion pinning
        `/api/contents/by-container/{id}` and
        `/api/contents/by-content/{id}` so a rename in either direction
        breaks the spec.
  - No integration spec is added; service-level Mongo-query coverage
    already lives in `ContentsLinkReadServiceSpec` from TASK-010.

### Documentation changes

- `docs/architecture/kafka-transaction-message-vocabulary.md` — appended
  a short HTTP-route paragraph to the existing "Reading `contents` Links"
  section: documents the two routes, the empty-array success contract
  (HTTP 200 with `[]`), the 400 path through `GlobalExceptionHandler` for
  blank ids, and the no-Mongo/no-materializer/no-write-side controller
  collaborator boundary.

### Items intentionally NOT changed

- `ContentsLinkReadService.groovy` and `ContentsLinkRecord.groovy` —
  service query semantics, sort order, type-resolution policy, and value
  object are frozen per `OUT_OF_SCOPE`.
- `ContentsLinkReadServiceSpec.groovy` — service-level coverage from
  TASK-010 is unchanged.
- `SecurityConfig.groovy` — JWT auth on `/api/**` already covers both
  new routes; no permit-list edit needed.
- `GlobalExceptionHandler.groovy` — existing
  `IllegalArgumentException → 400 ErrorResponse` advice already returns
  the desired body; no new handler.
- `CommittedTransactionReadController`, `CommittedTransactionReadService`,
  `CommittedTransactionMaterializer`, `TransactionMessagePersistenceService`,
  `TransactionMessageListener`, and their value objects.
- `MongoDbInitializer` — `lnk` and `typ` are already enum-driven from
  TASK-007.
- `libraries/jade-tipi-dto/...` — no DTO, schema, or message-example
  change.
- `application.yml`, `build.gradle`, `docker-compose.yml`,
  `DIRECTION.md`, frontend, integration-test sources.

### Verification

- Docker stack healthy before verification: `jade-tipi-mongo`,
  `jade-tipi-kafka`, `jade-tipi-keycloak` all up (`docker compose ps`
  confirmed).
- `./gradlew :jade-tipi:compileGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` → BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
  → BUILD SUCCESSFUL; `ContentsLinkReadControllerSpec`
  `tests=10, failures=0, errors=0, skipped=0`.
- `./gradlew :jade-tipi:test` → BUILD SUCCESSFUL; full unit suite
  `tests=107, failures=0, errors=0, skipped=0` across 11 specs (was 97
  across 10 specs after TASK-010; +10 new in
  `ContentsLinkReadControllerSpec`). Suite breakdown:
  `CommittedTransactionReadControllerSpec=5`,
  `ContentsLinkReadControllerSpec=10`,
  `JadetipiApplicationTests=1`,
  `TransactionMessageListenerSpec=4`,
  `MongoDbInitializerSpec=2`,
  `DocumentServiceMongoDbImplSpec=9`,
  `CommittedTransactionMaterializerSpec=19`,
  `CommittedTransactionReadServiceSpec=12`,
  `ContentsLinkReadServiceSpec=18`,
  `TransactionMessagePersistenceServiceSpec=15`,
  `TransactionServiceSpec=12`.

## TASK-010 — Plan contents location query reads

Director moved `TASK-010` to `READY_FOR_IMPLEMENTATION` on 2026-05-01
with implementation directives recorded in the
`TASK-010 Director Pre-work Review` block of `DIRECTIVES.md` (signal
`PROCEED_TO_IMPLEMENTATION`) and reflected in the
`docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md`
`LATEST_REPORT`. Implementation done on 2026-05-01.

### Production source changes

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  (new) — the Kafka-free / HTTP-free reader.
  - Constructor takes only `ReactiveMongoTemplate`; no Kafka, HTTP, or
    listener dependency.
  - Public surface: `Flux<ContentsLinkRecord> findContents(String containerId)`
    and `Flux<ContentsLinkRecord> findLocations(String objectId)`.
  - Private `resolveContentsTypeIds()` queries `typ` for documents with
    `kind == 'link_type' AND name == 'contents'`, sorted ASC by `_id`,
    and emits each non-blank `_id` as a `Flux<String>`.
  - For each public method, the resolved IDs are collected into a list;
    if empty, the method returns `Flux.empty()` and never queries `lnk`.
    Otherwise the service runs one `lnk` query with
    `Criteria.where('type_id').in(typeIds).and(<endpoint>).is(<id>)`,
    sorted ASC by `_id`.
  - `Assert.hasText(containerId|objectId, ...)` rejects blank input
    eagerly at the entry point with `IllegalArgumentException`.
  - Each returned `ContentsLinkRecord` is built by `toRecord(Map row)`
    via field-by-field cast: `linkId = row._id`, `typeId = row.type_id`,
    `left`, `right`, `properties = row.properties`, and
    `provenance = row._jt_provenance` (verbatim; `null` is preserved).
  - Constants for collection names, field names, the link-type kind,
    and the contents type name are exposed as `static final` fields so
    later tests or callers can reuse them without string duplication.

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
  (new) — Groovy `@Immutable` value object carrying `linkId`, `typeId`,
  `left`, `right`, `properties` (`Map<String, Object>`), and `provenance`
  (`Map<String, Object>`). Lives in the same `service` package as
  `CommittedTransactionMessage` for consistency with that precedent;
  Javadoc spells out the verbatim semantics and that endpoint resolution
  is not performed at this boundary.

### Doc changes

- `docs/architecture/kafka-transaction-message-vocabulary.md` — added a
  short "Reading `contents` Links" section between the existing
  "Committed Materialization Of Locations And Links" section and
  "Reference Examples". The new section names the service surface
  (`findContents`/`findLocations`), the canonical-type resolution rule,
  the `Flux.empty()` short-circuit when no `contents` declaration
  exists, the `_id` ASC ordering, the verbatim record fields including
  `provenance`, the explicit non-join semantics for `loc`/`ent`, and the
  `IllegalArgumentException` boundary behavior.

### Tests

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy`
  (new) — pure Spock with `Mock(ReactiveMongoTemplate)`, modeled on
  `CommittedTransactionReadServiceSpec`. 18 features:
  - `findContents` — single-match round-trip with verbatim
    `properties.position` (`{kind: 'plate_well', label: 'A1', row: 'A',
    column: 1}`) and verbatim `_jt_provenance` (`txn_id`, `commit_id`,
    `msg_uuid`, `committed_at`, `materialized_at`).
  - `findContents` — captured `Query` proofs: typ criteria
    `kind=link_type AND name=contents`, typ sort `_id ASC`, lnk
    criteria `type_id $in [id1, id2]`, lnk `left == containerId` (no
    `right` clause), lnk sort `_id ASC`. The order assertion is the
    captured `sortObject`, not mock iteration order.
  - `findContents` — `Flux.empty()` when no contents type declared,
    with `0 * mongoTemplate.find(_, Map.class, 'lnk')` proving the
    `lnk` query is not even built.
  - `findContents` — empty result when contents type exists but no
    matching `lnk`; `0 *` `loc`/`ent`/`findById` calls prove the
    reader does not consult those collections to decide.
  - `findContents` — a `lnk` whose `right` would not resolve in
    `loc`/`ent` is still returned verbatim; `0 *` `loc`/`ent`
    `find`/`findById` calls.
  - `findLocations` — symmetric single-match round-trip and captured
    `Query` proof (`right == objectId`, no `left` clause, `type_id
    $in [id]`, sort `_id ASC`).
  - `findLocations` — `Flux.empty()` when no contents type declared,
    `0 *` `lnk` find.
  - `findLocations` — accepts links whose `left` is a `loc` ID and
    whose `left` is a different `loc` ID (covers
    `allowed_left_collections == ['loc']` reality without forcing a
    cross-collection assumption).
  - Type-discriminator — captured typ `Query` proves both
    `kind=link_type` AND `name=contents` are required, and the lnk
    `$in` only carries the resolved canonical IDs.
  - Materialized `lnk` missing `_jt_provenance` returns
    `provenance == null` rather than dropping the row.
  - Blank inputs — `findContents(input)` and `findLocations(input)`
    each throw `IllegalArgumentException` over a `where:` data table
    (`null`, `''`, `'   '`); `0 * mongoTemplate.find(_, _, _)` and
    `0 * mongoTemplate.findById(_, _, _)` confirm Mongo is not
    touched.
  - No-write proof — after one forward and one reverse query,
    asserts `0 * mongoTemplate.insert(_, _)`,
    `0 * mongoTemplate.insert(_)`, `0 * mongoTemplate.save(_, _)`,
    `0 * mongoTemplate.save(_)`, `0 * mongoTemplate.updateFirst(_, _, _)`,
    `0 * mongoTemplate.updateMulti(_, _, _)`,
    `0 * mongoTemplate.remove(_, _)`, and `0 * mongoTemplate.remove(_)`.

### Out-of-scope items confirmed not changed

- No controller (`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/`
  is unchanged); no HTTP submission rebuild, no security policy edit,
  and no `GlobalExceptionHandler` change.
- `CommittedTransactionMaterializer`, `CommittedTransactionReadService`,
  `TransactionMessagePersistenceService`, `TransactionService`,
  `DocumentService`, `CommittedTransactionSnapshot`,
  `CommittedTransactionMessage`, `KafkaProvenance`, `MaterializeResult`,
  and `PersistResult` are unchanged.
- DTO library (`libraries/jade-tipi-dto`), JSON schema
  (`message.schema.json`), message examples
  (`libraries/jade-tipi-dto/src/main/resources/example/message/*.json`),
  Kafka listener / topic configuration, build files
  (`build.gradle`, `gradle.properties`, `settings.gradle`),
  `docker/docker-compose.yml`, and `application.yml` are unchanged.
- `loc` records still do not carry `parent_location_id`; containment
  remains canonical in `lnk`.
- No update/delete replay, no backfill jobs, no background workers,
  no multi-transaction conflict resolution, no authorization/scoping
  policy, and no integration spec is added in this task.

### Verification

Pre-existing Docker stack already healthy (`docker ps` shows
`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak` running);
the project-documented `docker compose -f docker/docker-compose.yml
--profile mongodb up -d` was therefore not re-issued.

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
  — BUILD SUCCESSFUL. Spec report:
  `tests=18, failures=0, errors=0, skipped=0` (all 18 features green).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. Aggregated unit-suite
  report: `tests=97, failures=0, errors=0, skipped=0` across 10 specs
  (was 79 across 9 specs at the end of TASK-009; the only delta is the
  new `ContentsLinkReadServiceSpec` adding 18 features).

The DTO library was intentionally not rebuilt
(`./gradlew :libraries:jade-tipi-dto:test` is not required for
`TASK-010` because no DTO/schema/example changed). No integration
spec was added or run; integration coverage stays deferred per the
director's pre-work review.

## TASK-009 — Plan committed location/link materialization

Director moved `TASK-009` to `READY_FOR_IMPLEMENTATION` on 2026-05-01 with
implementation directives in
`docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md`
and the `TASK-009 Director Pre-work Review` block in `DIRECTIVES.md`
(signal `PROCEED_TO_IMPLEMENTATION`). Implementation done on 2026-05-01.

### Production source changes

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  (new) — the Kafka-free / HTTP-free `@Service`.
  - Constructor takes `ReactiveMongoTemplate` and
    `CommittedTransactionReadService`. No Kafka, no HTTP, no listener.
  - `Mono<MaterializeResult> materialize(CommittedTransactionSnapshot)` —
    walks `snapshot.messages` (sorted ASC by `_id` upstream by the read
    service) with `Flux.fromIterable(...).concatMap(...)` so insert order
    matches snapshot order. Returns a `MaterializeResult` carrying the five
    counts. Returns `Mono.empty()` for a `null` snapshot.
  - `Mono<MaterializeResult> materialize(String txnId)` — convenience
    overload that delegates to
    `CommittedTransactionReadService.findCommitted(txnId)` and returns
    `Mono.empty()` when the transaction is not visible as committed (so the
    committed-visibility gate is reused, not re-implemented). Validates
    `txnId` via `Assert.hasText`.
  - `processMessage(...)` filters to supported (collection, action, kind)
    triples (see "Supported message set" below); for unsupported messages
    it counts `skippedUnsupported` and returns `Mono.empty()`. For
    supported messages with missing or blank `data.id` it counts
    `skippedInvalid`, logs at ERROR, and returns `Mono.empty()` without
    synthesizing an id. Otherwise it builds the document and inserts via
    `mongoTemplate.insert(doc, message.collection)`, incrementing
    `materialized` on success, or routing to `handleInsertError` on error.
  - `handleInsertError(...)` — for non-`DuplicateKeyException` errors, logs
    and re-emits the error so the caller can retry the snapshot. For
    duplicate key (matching either `org.springframework.dao.DuplicateKeyException`
    or `com.mongodb.DuplicateKeyException`, including via cause chain), it
    re-fetches the existing document by `_id` and compares payloads
    (excluding `_jt_provenance` on both sides via `Objects.equals`).
    Identical payload → `duplicateMatching++` (idempotent success, no
    overwrite). Differing payload → ERROR log + `conflictingDuplicate++`,
    no overwrite, no abort — the snapshot's later messages still get
    processed. `findById` returning empty after a duplicate-key error
    re-emits the original exception (defensive guard).
  - `buildDocument(docId, snapshot, message)` — `LinkedHashMap` with `_id`
    first, then verbatim `data` fields (so the original `id` is preserved
    alongside `_id` per directive), then `_jt_provenance =
    {txn_id, commit_id, msg_uuid, committed_at, materialized_at}`. The
    reserved key uses an underscore prefix so it cannot collide with a
    schema-valid snake_case payload key.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`
  (new) — POGO with public `int` counters: `materialized`,
  `duplicateMatching`, `conflictingDuplicate`, `skippedUnsupported`, and
  `skippedInvalid`. `@ToString(includeNames = true)` for log readability.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  — added a `CommittedTransactionMaterializer` constructor dependency and a
  single `materializeQuietly(txnId)` post-commit step in `commitHeader`.
  - `commitHeader` first-commit branch: after the existing
    `mongoTemplate.updateFirst(...)` chain, `.then(materializeQuietly(txnId))`
    runs the projection, then `.thenReturn(PersistResult.COMMITTED)` keeps
    the outward result unchanged.
  - `commitHeader` re-delivery branch (`state == 'committed'`): now also
    invokes `materializeQuietly(txnId).thenReturn(PersistResult.COMMIT_DUPLICATE)`
    so a retry can self-heal a projection gap. The directive's "preserve
    outward `PersistResult`" rule is honored: the surface result is still
    `COMMITTED` / `COMMIT_DUPLICATE`.
  - `materializeQuietly(txnId)` — invokes the materializer, logs failures
    at WARN with the `txnId`, swallows the error via `onErrorResume(... ->
    Mono.empty())`, and converts to `Mono<Void>` via `.then()`. Failing the
    commit on a downstream projection error would invert WAL durability
    (the `txn` commit is already durable); the materializer is a
    read-after-commit projection.
  - Open, append, rollback, and validation paths are unchanged. The
    materializer is never invoked on those branches.

### Supported message set

Exactly three message families are materialized in this task. All other
combinations — including update/delete on supported collections, txn-control
actions, and `ppy`/`ent`/`uni`/`grp`/`vdn` messages — are counted as
`skippedUnsupported` and not written.

- `collection == 'loc' && action == 'create'` → insert into `loc`.
- `collection == 'typ' && action == 'create' && data.kind == 'link_type'`
  → insert into `typ`. Bare entity-type `typ` records (e.g. the older
  `04-create-entity-type.json` shape with no `kind`) are intentionally
  skipped here.
- `collection == 'lnk' && action == 'create'` → insert into `lnk`.

### Test coverage

`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
(new). Pure Spock with `Mock(ReactiveMongoTemplate)` and
`Mock(CommittedTransactionReadService)`. 19 features:

1. `loc + create` materializes into `loc`, copies `name`/`description`
   verbatim, sets `_id == data.id`, retains the `id` payload field, and
   carries `_jt_provenance` with the snapshot's `txn_id`, `commit_id`,
   `msg_uuid`, `committed_at`, plus a non-null `materialized_at` Instant.
2. Link-type `typ` create materializes into `typ` with all six declarative
   facts and both `allowed_*_collections` values preserved verbatim.
3. `typ + create` without `data.kind == 'link_type'` is **not** written and
   is counted as `skippedUnsupported`.
4. `lnk + create` materializes into `lnk` preserving `type_id`, `left`,
   `right`, and `properties.position` (`kind`, `label`, `row`, `column`).
5. `ppy + create` and `ent + create` are skipped without any insert.
6. `loc + update` is skipped without any insert.
7. Identical-payload duplicate on `loc` (DuplicateKeyException →
   matching existing doc) increments `duplicateMatching`, does not
   `updateFirst` or `save`.
8. Differing-payload duplicate on `loc` (DuplicateKeyException →
   different existing doc) increments `conflictingDuplicate`, does not
   `updateFirst` or `save`.
9. Single conflicting duplicate does not block subsequent messages in the
   same snapshot — a `lnk + create` that follows a conflicting `loc`
   still gets inserted and counted.
10. Missing `data.id` (null) is counted as `skippedInvalid` with no
    insert.
11. Blank/whitespace `data.id` (`''`, `'   '`) is counted as
    `skippedInvalid` with no insert.
12. Mixed-message snapshot (`loc, ppy, typ link-type, ent, lnk`) inserts
    in snapshot order — assertion checks the recorded insert-collection
    sequence equals `['loc', 'typ', 'lnk']` — and produces correct counts
    (`materialized=3, skippedUnsupported=2`).
13. `materialize(txnId)` with non-visible read returns `Mono.empty()`,
    no insert is attempted.
14. `materialize(txnId)` with visible snapshot materializes and increments
    counts.
15. Blank/null/whitespace `txnId` is rejected with
    `IllegalArgumentException` (parameterized over three rows). The call
    is cast to `(String)` to disambiguate from the snapshot overload.
16. `null` snapshot returns `Mono.empty()` without touching
    `mongoTemplate`.

`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
gained four new features pinning the post-commit hook (and updated the
existing two commit-path features to stub the materializer):

- "post-commit hook invokes materializer exactly once on first successful
  commit" — asserts `1 * materializer.materialize(TXN_ID)` and
  `result == PersistResult.COMMITTED`.
- "post-commit hook also invokes materializer on commit re-delivery to
  fill projection gaps" — asserts `1 * materializer.materialize(TXN_ID)`
  on the `state == 'committed'` branch and `result ==
  PersistResult.COMMIT_DUPLICATE`.
- "materializer failure on the commit path is swallowed and surface
  result is COMMITTED" — `materialize(TXN_ID) >> Mono.error(...)` still
  resolves to `COMMITTED`; `noExceptionThrown()`.
- "materializer failure on commit re-delivery is swallowed and surface
  result is COMMIT_DUPLICATE" — same swallow semantics on the duplicate
  branch.

The constructor change required adding `materializer = Mock(...)` to the
spec's `setup()`. Open / append / rollback / null-message tests are
unchanged because those paths never invoke the materializer.

### Documentation

`docs/architecture/kafka-transaction-message-vocabulary.md` gained a new
"Committed Materialization Of Locations And Links" section after the
"Link Types And Concrete Links" section. The section explains:
- The post-commit projection scope (`loc + create`, link-type `typ +
  create`, `lnk + create`) and what is intentionally not materialized.
- That the `txn` write-ahead log remains the durable, authoritative
  record; the projection is read-after-commit.
- The materialized document shape (`_id == data.id`, verbatim `data` copy,
  reserved `_jt_provenance` sub-document with `txn_id`, `commit_id`,
  `msg_uuid`, `committed_at`, `materialized_at`).
- The duplicate / conflict / missing-id semantics.
- The standing semantic-validation-deferred caveat.

### Out of scope (preserved)

- `libraries/jade-tipi-dto` — no DTO enum, schema, or example change.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/...` — listener,
  topic configuration, deserialization semantics unchanged.
- `CommittedTransactionReadService` and its DTOs
  (`CommittedTransactionSnapshot`, `CommittedTransactionMessage`,
  `KafkaProvenance`) — read-side surface unchanged.
- `MongoDbInitializer` — `loc`, `typ`, `lnk` are already created by the
  enum-driven loop from `TASK-007`; no initializer change needed.
- `application.yml`, `application-test.yml`, `build.gradle`,
  `docker-compose.yml`, `DIRECTION.md`, security/auth code, controllers
  — unchanged.
- No semantic reference validation for `lnk.type_id`, `left`, `right`, or
  `allowed_*_collections`.
- No `parent_location_id` on `loc` records (containment stays canonical
  in `lnk`).
- No update/delete replay, backfill jobs, background workers,
  multi-transaction conflict resolution, plate/well read APIs, "what is
  in this plate?" / "where is this sample?" queries, HTTP submission
  rebuilds, or controllers.
- No integration spec in this task per directive.

### Verification

`docker compose -f docker/docker-compose.yml ps` confirmed
`jade-tipi-mongo`, `jade-tipi-kafka`, and `jade-tipi-keycloak` all
healthy. Then:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
  — BUILD SUCCESSFUL. `tests=19, skipped=0, failures=0, errors=0`.
- `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`
  — BUILD SUCCESSFUL. `tests=15, skipped=0, failures=0, errors=0` (was
  11; +4 new post-commit features).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 79 unit tests, 0
  failures, 0 errors:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (12)
  - `CommittedTransactionReadControllerSpec` (5)
  - `CommittedTransactionMaterializerSpec` (19) — new
  - `TransactionMessagePersistenceServiceSpec` (15) — was 11, +4 new
  - `TransactionServiceSpec` (12)
  - `MongoDbInitializerSpec` (2)
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL
  (no integration spec was added in this task).

If a future verification fails because Docker / Mongo is not running,
the documented setup command is `docker compose -f
docker/docker-compose.yml --profile mongodb up -d`; only Mongo is
strictly required for the unit suite because
`JadetipiApplicationTests.contextLoads` opens a Mongo connection.

### Files changed in this turn

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  (added materializer constructor dependency; added
  `materializeQuietly(txnId)` and a single `.then(...)` step on each
  commit branch)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  (new — 19 features)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
  (added `materializer = Mock(...)`; stubbed materializer in two existing
  commit features; added four new features for the post-commit hook and
  swallow semantics)
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  (new "Committed Materialization Of Locations And Links" section)
- `docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md`
  (`STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` rewritten with
  the implementation outcome and verification result)
- `docs/agents/claude-1-changes.md` — this report.

---

# Earlier reports

STATUS: READY_FOR_REVIEW
TASK: TASK-008 — Add contents link vocabulary examples
DATE: 2026-05-01
SUMMARY: Added the smallest canonical vocabulary unit for the `contents`
relationship. Two new example messages —
`libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`
(a `typ + create` carrying `data.kind: "link_type"` and the six
declarative facts: `left_role: "container"`, `right_role: "content"`,
`left_to_right_label: "contains"`, `right_to_left_label: "contained_by"`,
`allowed_left_collections: ["loc"]`,
`allowed_right_collections: ["loc", "ent"]`, with ID segment
`~typ~contents`) and
`libraries/jade-tipi-dto/src/main/resources/example/message/12-create-contents-link-plate-sample.json`
(a `lnk + create` referencing that type via `type_id` and pointing
`left` at a plate `~loc~plate_b1` and `right` at a sample `~ent~sample_x1`
with a snake-case `properties.position` of
`{kind: "plate_well", label: "A1", row: "A", column: 1}`). Both examples
reuse the established canonical batch `txn.uuid`
`018fd849-2a40-7abc-8a45-111111111111` and use the three-letter ID
segments `~typ~`, `~lnk~`, `~loc~`, `~ent~` per `DIRECTION.md`; the
older `04-create-entity-type.json` `~ty~` example is intentionally left
untouched per the director directive. `MessageSpec` got both new paths
appended to `EXAMPLE_PATHS` so the existing `@Unroll` round-trip and
schema-validate features cover them, plus two focused features pinning
the canonical shapes — one asserts the `contents` `typ` declaration's
collection/action and all six declarative facts, and one asserts the
`contents` `lnk` envelope's collection/action, the `~typ~contents` /
`~loc~plate_b1` / `~ent~sample_x1` reference suffixes, and the
`position.kind/label/row/column` values. `docs/architecture/kafka-transaction-message-vocabulary.md`
gained a new "Link Types And Concrete Links" section after the property
assignment section that explains containment is canonical in `lnk`,
that link-type semantics live in `typ` (with `data.kind: "link_type"`
mirroring the `ppy` `definition`/`assignment` discriminator pattern),
that `loc` records still do not carry parentage, and that semantic
reference validation (type/endpoint resolution and
`allowed_*_collections` matching) is not enforced today and is a
follow-up reader/materializer concern. The numbered reference-examples
list was extended to `11-create-contents-type.json` and
`12-create-contents-link-plate-sample.json`. `docs/Jade-Tipi.md`,
`DIRECTION.md`, and the JSON schema are unchanged. No DTO enum, schema,
backend service/listener/controller/initializer, build, Docker Compose,
security policy, HTTP wrapper, materializer, semantic-validation,
plate/well read API, `parent_location_id`, or committed-snapshot change.
VERIFICATION: `./gradlew :libraries:jade-tipi-dto:test` passed
(`MessageSpec` `tests=39, failures=0, errors=0`; `UnitSpec`
`tests=8, failures=0, errors=0`); the two new examples flow through
the existing round-trip and schema-validate `@Unroll` features (12
round-trip features and 12 schema-validate features cover examples
01–12), and the two new focused features for the `contents`
declaration and concrete link both pass.

## TASK-008 — Add contents link vocabulary examples

Director moved `TASK-008` to `READY_FOR_IMPLEMENTATION` on 2026-05-01
with implementation directives in
`docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md`
and the `TASK-008 Director Pre-work Review` block in
`DIRECTIVES.md`. Implementation done on 2026-05-01.

### Example resource changes

- `libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`
  (new) — canonical `typ + create` envelope declaring the `contents`
  link type. Reuses the canonical batch `txn.uuid`
  `018fd849-2a40-7abc-8a45-111111111111`. The `data` payload carries
  `kind: "link_type"` (mirroring the `ppy` `kind` discriminator
  pattern), the canonical ID
  `jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents`,
  human description, and the six declarative facts:
  `left_role: "container"`, `right_role: "content"`,
  `left_to_right_label: "contains"`,
  `right_to_left_label: "contained_by"`,
  `allowed_left_collections: ["loc"]`,
  `allowed_right_collections: ["loc", "ent"]`. All field names are
  snake_case-compliant for the schema's nested
  `propertyNames` rule.
- `libraries/jade-tipi-dto/src/main/resources/example/message/12-create-contents-link-plate-sample.json`
  (new) — canonical `lnk + create` envelope for a concrete plate-well
  containment. Reuses the same canonical batch `txn.uuid` as the
  `typ` example. The `data` payload carries `id` ending in
  `~lnk~plate_b1_sample_x1`, `type_id` pointing at the
  `~typ~contents` ID from the type example, `left` pointing at a
  plate `~loc~plate_b1` and `right` pointing at a sample
  `~ent~sample_x1` (both endpoints are flat string IDs that are not
  themselves created by example messages — semantic reference
  validation is `OUT_OF_SCOPE`), and a `properties.position` object
  `{kind: "plate_well", label: "A1", row: "A", column: 1}`. The
  `"A1"` / `"A"` casing matches `DIRECTION.md` per the director's
  pre-work review (the snake_case rule applies to property names,
  not values).

### MessageSpec coverage

`libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:

- Appended `/example/message/11-create-contents-type.json` and
  `/example/message/12-create-contents-link-plate-sample.json` to
  `EXAMPLE_PATHS`, so the existing `@Unroll` features
  `"example #examplePath round-trips through JsonMapper preserving collection"`
  and `"example #examplePath validates against the schema"` now cover
  examples 01 through 12 (24 generated features in total for those
  two `@Unroll` features).
- Added focused feature
  `"contents typ example declares the canonical link-type facts"`:
  asserts `Collection.TYPE`, `Action.CREATE`, the canonical
  `~typ~contents` ID, `data.kind == 'link_type'`,
  `data.name == 'contents'`, `data.left_role == 'container'`,
  `data.right_role == 'content'`,
  `data.left_to_right_label == 'contains'`,
  `data.right_to_left_label == 'contained_by'`,
  `data.allowed_left_collections == ['loc']`, and
  `data.allowed_right_collections == ['loc', 'ent']`. This pins the
  canonical declaration so a later refactor of the example file fails
  loudly rather than silently dropping a fact.
- Added focused feature
  `"contents lnk example references the contents type and carries a position property"`:
  asserts `Collection.LINK`, `Action.CREATE`, `data.id` ending with
  `~lnk~plate_b1_sample_x1`, `data.type_id` ending with
  `~typ~contents`, `data.left` ending with `~loc~plate_b1`,
  `data.right` ending with `~ent~sample_x1`, and the
  `properties.position` object's `kind`, `label`, `row`, and `column`
  values. This pins the canonical concrete-link shape and the
  `DIRECTION.md` plate-well coordinate casing.

No new spec file was added, no schema-rejection feature was added
(the directive's task is to add canonical examples; existing
`Collection.PROPERTY + Action.OPEN` and `LOCATION` rejection features
already establish the rejection-pattern coverage), and no test was
removed.

### Documentation changes

`docs/architecture/kafka-transaction-message-vocabulary.md`:

- Added new section "Link Types And Concrete Links" after the
  "Property Value Assignment" section. The section records that
  containment lives in `lnk` (not on `loc.parent_location_id`),
  that link semantics live in `typ` as `link_type` declarations
  (mirroring the `ppy` `definition`/`assignment` discriminator),
  and that the first canonical link type is `contents`. It shows
  the canonical `typ` payload shape (snake-case `data` body) and
  the canonical `lnk` payload shape (`type_id`, `left`, `right`,
  `properties.position`) as JSON snippets in the same style used
  for property and entity sections. It explicitly notes that
  semantic reference validation (`type_id` resolution, `left` /
  `right` resolution, and matching `allowed_*_collections`) is
  not enforced today and is a follow-up reader/materializer
  concern, and that property-name values such as `"A1"` are stored
  verbatim because the snake_case rule constrains property keys,
  not values.
- Extended the numbered "Reference Examples" list with
  `11. 11-create-contents-type.json` and
  `12. 12-create-contents-link-plate-sample.json`.

`docs/Jade-Tipi.md` is unchanged — the high-level spec already covers
links and types generically and a `contents`-specific paragraph would
duplicate the architecture doc. `DIRECTION.md` is unchanged — it
already records the `contents` direction.

### Out of scope (preserved)

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`,
  `Action.java`, and `Message.java` — unchanged. `LINK` and `TYPE`
  already exist with the correct data-action whitelist.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json` —
  unchanged. The schema already accepts `lnk + create` and
  `typ + create`; every field name in the new examples is
  snake_case-compliant for the recursive `propertyNames` rule.
- `04-create-entity-type.json` — left untouched per the director
  directive. The older two-letter `~ty~` segment is preserved; the
  new `~typ~` segment establishes the canonical link-type ID form
  going forward without a backward-incompatible rewrite.
- All backend code under `jade-tipi/src/main/groovy/...`
  (Kafka listener, persistence service, committed read service,
  controller, `MongoDbInitializer`, security config). The
  `Collection.values()` startup loop already handles `lnk` and
  `typ`.
- All build files (`build.gradle`, `settings.gradle`,
  `gradle.properties`), `application.yml` profiles, Docker Compose,
  and security policy.
- HTTP submission wrappers, materializers, semantic reference
  validation, plate/well read APIs, and the committed-snapshot
  surface (`CommittedTransactionReadService` /
  `CommittedTransactionReadController`).
- `parent_location_id` is not added to `loc` records; containment
  remains canonical in `lnk` per `DIRECTION.md`.
- The `txn` write-ahead log record shape from `TASK-003` is
  preserved.

### Verification

`./gradlew :libraries:jade-tipi-dto:test --rerun-tasks` —
**BUILD SUCCESSFUL**. The XML report shows
`MessageSpec` `tests=39, failures=0, errors=0, skipped=0` and
`UnitSpec` `tests=8, failures=0, errors=0, skipped=0`. The two new
focused features
(`contents typ example declares the canonical link-type facts` and
`contents lnk example references the contents type and carries a
position property`) and the two new `@Unroll`-generated rows for
each of the round-trip and schema-validate `@Unroll` features all
pass.

`./gradlew :libraries:jade-tipi-dto:compileJava` ran as part of the
test task (`compileJava` was up-to-date / executed with the
re-run; no Java source was changed and compilation succeeded).

No further regression commands were needed for `TASK-008` — there is
no Kafka, HTTP, listener, persistence, controller, initializer, or
materializer change. `:jade-tipi:test` and `:jade-tipi:integrationTest`
were not required by the task directive and were not run.

### Files changed (inside owned paths)

- `libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`
  (new)
- `libraries/jade-tipi-dto/src/main/resources/example/message/12-create-contents-link-plate-sample.json`
  (new)
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  (extended `EXAMPLE_PATHS`; added two focused features)
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  (new "Link Types And Concrete Links" section; extended
  reference-examples list)
- `docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md`
  (`STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` rewritten)
- `docs/agents/claude-1-changes.md` (this report)

---

STATUS: READY_FOR_REVIEW
TASK: TASK-007 — Add location collection
DATE: 2026-05-01
SUMMARY: Added `loc` as a first-class long-term Jade-Tipi collection.
`Collection.LOCATION("location","loc")` was added to the DTO enum and
inherits the data-action set automatically; `message.schema.json` now
admits `"loc"` in the top-level collection enum and in the
non-transaction action-compatibility conditional, so `loc` accepts
`create|update|delete` and rejects `open|commit|rollback`. A canonical
`10-create-location.json` example was added with the `~loc~` ID suffix
per the director directive. `MessageSpec` was extended with enum
round-trip, schema-acceptance for `loc + create`, and `@Unroll`
schema-rejection for each transaction-control action paired with `loc`;
the new example was added to `EXAMPLE_PATHS` so the existing round-trip
and schema-validate features cover it. A new pure-Spock backend spec
`MongoDbInitializerSpec` proves the existing `Collection.values()` loop
in `MongoDbInitializer` calls `createCollection('loc')` exactly once when
`loc` is missing and not at all when `loc` already exists; no change to
`MongoDbInitializer.groovy`. Docs updated: `docs/Jade-Tipi.md` lists
`location (loc)` and clarifies `txn` as the special log/staging
collection, and `docs/architecture/kafka-transaction-message-vocabulary.md`
adds `loc` to the prose enumeration, the action-compatibility bullet, and
the numbered reference-examples list. `DIRECTION.md` is unchanged per
directive. No production code change in `MongoDbInitializer.groovy`,
`TransactionMessagePersistenceService`, `TransactionMessageListener`,
`CommittedTransactionReadService`, `CommittedTransactionReadController`,
`SecurityConfig`, `application.yml`, or `build.gradle`. The `txn`
write-ahead log shape from TASK-003 is preserved.
VERIFICATION: docker compose stack healthy (`jade-tipi-mongo`,
`jade-tipi-kafka`, `jade-tipi-keycloak`). `./gradlew
:libraries:jade-tipi-dto:test` (`MessageSpec` `tests=33, failures=0`),
`./gradlew :jade-tipi:compileGroovy`,
`./gradlew :jade-tipi:compileTestGroovy`,
`./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`
(`tests=2, failures=0`), and `./gradlew :jade-tipi:test` (56 unit tests,
0 failures, 0 errors) all passed.

## TASK-007 — Add location collection

Director moved `TASK-007` to `READY_FOR_IMPLEMENTATION` on 2026-05-01 with
the implementation directives in
`docs/orchestrator/tasks/TASK-007-add-location-collection.md`.
Implementation done on 2026-05-01.

### DTO changes

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  now declares `LOCATION("location", "loc")` between `LINK` and `UNIT`.
  The existing constructor branch on `"transaction".equals(name)` gives
  `LOCATION` the data-mutating action set `[CREATE, UPDATE, DELETE]`
  automatically; `fromJson(String)` and `@JsonValue` already handle the
  new value through the abbreviation/name match path.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  has `"loc"` in `$defs/Collection.enum` (between `"lnk"` and `"ppy"`)
  and in the long-term-collection enum inside the second `allOf`
  conditional. The first conditional (transaction-only actions for
  `txn`) is unchanged, so `loc` paired with `open|commit|rollback`
  fails the second conditional's `action ∈ {create, update, delete}`
  whitelist.
- New canonical example
  `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
  is a `loc + create` envelope with `id` suffix `~loc~freezer_a` (per the
  director directive to use `loc` consistently in example IDs rather than
  introducing a separate `lo` suffix in this task).

### DTO test coverage

`libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:

- Added `/example/message/10-create-location.json` to `EXAMPLE_PATHS`,
  so the existing `@Unroll` round-trip and `validate()` features
  automatically cover the `loc` example.
- Added `Collection.fromJson('loc')` enum-shape feature: asserts
  `fromJson('loc') == LOCATION`, `fromJson('location') == LOCATION`,
  `LOCATION.toJson() == 'loc'`, `LOCATION.abbreviation == 'loc'`,
  `LOCATION.name == 'location'`, and `LOCATION.actions == [CREATE,
  UPDATE, DELETE]`.
- Added `loc + create` schema acceptance feature using
  `Message.newInstance(...)` and `validate()` with `noExceptionThrown()`.
- Added an `@Unroll` schema-rejection feature over the three
  transaction-control actions (`OPEN`, `COMMIT`, `ROLLBACK`) paired
  with `Collection.LOCATION`, asserting `ValidationException` whose
  message contains "action".

`MessageSpec` totals after the change: `tests=33, skipped=0,
failures=0, errors=0` (was 23; +10 = 1 enum-shape + 1 schema-accept +
3 schema-reject @Unroll rows + 2 round-trip + 2 validate from the new
example + 1 added by parameter expansion).

### Backend startup coverage

New `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializerSpec.groovy`
(pure Spock, no Spring context, no live Mongo). Uses `Mock(ReactiveMongoTemplate)`:

1. **`run()` creates the `loc` collection when it does not yet exist.**
   Asserts `1 * collectionExists('loc') >> Mono.just(false)`,
   `1 * createCollection('loc') >> Mono.empty()`,
   `0 * createCollection({ String name -> name != 'loc' })`, and uses
   wildcard catch-alls (`_ * collectionExists(_) >> Mono.just(true)`,
   `_ * getCollection(_) >> Mono.empty()`) for every other collection.
   The system-out shows the existing initializer log line
   `"Creating collection 'loc'"` for each of the eight non-`loc` enum
   values plus `tipi`, while only `loc` reaches `createCollection`.
2. **`run()` does not recreate `loc` when it already exists.**
   Asserts `0 * createCollection(_ as String)` and stubs every existence
   check as `Mono.just(true)`.

`MongoDbInitializer.groovy` is intentionally unchanged. The behavior is
gained for free via `Collection.values()` once `LOCATION` is in the
enum.

### Documentation

- `docs/Jade-Tipi.md` — added `* location     (loc)` between
  `link (lnk)` and `unit (uni)` in the abbreviation list, and a short
  note clarifying that `txn` is the durable transaction log/staging
  collection while the other abbreviations name long-term materialized
  domain collections.
- `docs/architecture/kafka-transaction-message-vocabulary.md` — added
  `loc` to the prose collection enumeration, to the
  long-term-collection bullet (`collection ∈ {ent, ppy, lnk, loc, uni,
  grp, typ, vdn} → action ∈ {create, update, delete}`), and added
  `10. 10-create-location.json` to the numbered reference-examples
  list.
- `DIRECTION.md` is unchanged. The document is already aligned with
  this task's scope (location/link modeling, no `parent_location_id`
  on `loc`, future `contents` link type) and no implementation detail
  contradicted it.

### Files changed in this turn

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
- `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
  (new)
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializerSpec.groovy`
  (new)
- `docs/Jade-Tipi.md`
- `docs/architecture/kafka-transaction-message-vocabulary.md`
- `docs/orchestrator/tasks/TASK-007-add-location-collection.md`
  (`STATUS` flipped from `READY_FOR_IMPLEMENTATION` to
  `READY_FOR_REVIEW`; `LATEST_REPORT` rewritten with the
  implementation outcome)
- `docs/agents/claude-1-changes.md` — this report.

### What did not change

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  (no source change required; the new collection is created via the
  existing `Collection.values()` loop).
- `TransactionMessageListener`, `TransactionMessagePersistenceService`,
  `TransactionService`, `CommittedTransactionReadService`,
  `CommittedTransactionReadController`, `GlobalExceptionHandler`,
  `ErrorResponse`, `SecurityConfig`, `application.yml`,
  `application-test.yml`, `build.gradle`, and the `txn` write-ahead log
  shape from `TASK-003` / `TASK-004` are unchanged.
- No new authentication, authorization, redaction, materialization,
  link-type, plate/well, or HTTP-submission policy was added.
- No `DIRECTION.md` change.

### Verification

`docker compose -f docker/docker-compose.yml ps` showed `jade-tipi-mongo`,
`jade-tipi-kafka`, and `jade-tipi-keycloak` all healthy. Then:

- `./gradlew :libraries:jade-tipi-dto:test` — BUILD SUCCESSFUL.
  `MessageSpec` `tests=33, skipped=0, failures=0, errors=0`.
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'` —
  BUILD SUCCESSFUL. `tests=2, skipped=0, failures=0, errors=0`.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 56 unit tests across:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (12)
  - `CommittedTransactionReadControllerSpec` (5)
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)
  - `MongoDbInitializerSpec` (2) — new
  All green; `failures=0, errors=0`.

If a future verification fails because Docker / Mongo is not running,
the documented setup command is `docker compose -f
docker/docker-compose.yml --profile mongodb up -d` (only Mongo is
strictly required for the unit suite because
`JadetipiApplicationTests.contextLoads` opens a Mongo connection).

STATUS: READY_FOR_REVIEW

## TASK-006 — Add committed transaction snapshot HTTP read adapter

Director moved `TASK-006` to `READY_FOR_IMPLEMENTATION` on 2026-04-30 with
the implementation directives in
`docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`
(route `GET /api/transactions/{id}/snapshot`, return the existing
`CommittedTransactionSnapshot` service-boundary object directly with
default Jackson camelCase, delegate committed visibility entirely to
`CommittedTransactionReadService.findCommitted`, map empty service result
to HTTP 404 with no body, let blank/whitespace ids fall through the
service `Assert.hasText` to the `GlobalExceptionHandler` 400 path, mirror
existing controller/security patterns, and add narrow `WebTestClient`
controller coverage). Implementation done on 2026-04-30.

### Production change

- New `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`.
  - `@RestController`, `@RequestMapping('/api/transactions')`, `@Slf4j`.
  - One method: `Mono<ResponseEntity<CommittedTransactionSnapshot>>
    getSnapshot(@PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt)`
    on `@GetMapping('/{id}/snapshot')`.
  - Body is literally `readService.findCommitted(id) ...
    .map { snapshot -> ResponseEntity.ok(snapshot) }
    .defaultIfEmpty(ResponseEntity.notFound().build())`. No duplicated
    WAL gate, no inline blank-id check, no per-id authorization, no
    redaction, no pagination, no list endpoint, no Mongo collaborator.
  - Logs only the `id` and the message count.

### What did not change

- `CommittedTransactionReadService`, `CommittedTransactionSnapshot`,
  `CommittedTransactionMessage`, and `KafkaProvenance` are unchanged
  (no fields, annotations, or package moves). Per the TASK-005
  director decision the snapshot value objects stay in `service/`;
  this task did not relocate them to `dto/` or add snake_case
  annotations.
- `TransactionController`, `DocumentController`, `TransactionService`,
  `TransactionMessagePersistenceService`,
  `TransactionMessageListener`, `GlobalExceptionHandler`,
  `ErrorResponse`, `SecurityConfig`, `application.yml`,
  `build.gradle`, and the `txn` write-ahead log shape from TASK-003
  are unchanged.
- No new authentication, authorization, redaction, pagination, list,
  or bulk policy was added.

### New test coverage

New `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`.

- Pattern: `WebTestClient.bindToController(controller)` (no Spring
  context, no Mongo/Kafka/Keycloak), wired with the real
  `GlobalExceptionHandler` via `.controllerAdvice(...)` and a
  registered `AuthenticationPrincipalArgumentResolver` so the
  `@AuthenticationPrincipal Jwt jwt` parameter resolves to `null`
  without a security context. The
  `CommittedTransactionReadService` collaborator is `Mock(...)`-ed.
- Five Spock features, all green:
  1. Committed snapshot returns 200 with the JSON body (`txnId`,
     `commitId`, `state`, `openData.hint`, `commitData.reason`,
     `messages.length()=2`, and the first message's `msgUuid`,
     `collection`, `action`, `data.name`, `kafka.topic`,
     `kafka.partition`, `kafka.offset`, `kafka.timestampMs`) plus the
     ordering of the second message's `msgUuid`, exercising the
     actual route and Jackson serialization (incl. `Instant` round-trip
     via `JavaTimeModule`).
  2. Controller delegates exclusively to
     `CommittedTransactionReadService` for committed lookups
     (`1 * readService.findCommitted(TXN_ID) >> Mono.just(...)`,
     `0 * _`).
  3. Missing or non-committed service result returns HTTP 404 with an
     empty body (`expectBody().isEmpty()`).
  4. Whitespace-only id surfaces the service's `Assert.hasText`
     `IllegalArgumentException` as the standard 400 `ErrorResponse`
     through `GlobalExceptionHandler` (`status=400`,
     `error='Bad Request'`, `message='txnId must not be blank'`).
  5. The controller's only constructor argument is
     `CommittedTransactionReadService`, proving no direct Mongo
     collaborator at the type level.

### Verification

`docker compose -f docker/docker-compose.yml ps` showed mongodb, kafka,
and keycloak healthy. Then:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`
  — 5/5 features green (`tests=5 failures=0 errors=0` in the JUnit XML).
- `./gradlew :jade-tipi:test` — full unit suite green; aggregated 54
  tests across all `:jade-tipi` specs, 0 failures, 0 errors.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD
  SUCCESSFUL (no integration spec was added in this task).

### Files changed in this turn

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
  — new file, the WebFlux read adapter described above.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`
  — new file, the controller-level Spock spec described above.
- `docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`
  — STATUS flipped from `READY_FOR_IMPLEMENTATION` to `READY_FOR_REVIEW`,
  `LATEST_REPORT` rewritten to record the implementation outcome.
- `docs/agents/claude-1-changes.md` — this report.

STATUS: READY_FOR_REVIEW

## TASK-005 — Director-review fix: coerce raw Mongo timestamps to Instant

Director implementation review on 2026-04-30 returned `TASK-005` to
`READY_FOR_IMPLEMENTATION` with one blocking finding:
`CommittedTransactionReadService` cast raw Mongo `Map` timestamp fields
directly to `Instant`, but BSON dates read into raw maps may surface as
`java.util.Date`, so a real committed snapshot could fail with
`GroovyCastException` even though the mocked unit tests passed by
injecting `Instant` fixtures.

Resolved on 2026-04-30. `TASK-005` flipped back to `READY_FOR_REVIEW`.

### Fix

- `CommittedTransactionReadService` now routes header `opened_at`,
  header `committed_at`, and message `received_at` through a new
  `private static Instant toInstant(Object)` helper.
- The helper accepts `null` (returns `null`), `Instant` (returned as-is),
  and `java.util.Date` (`.toInstant()`); any other type raises
  `IllegalStateException` so a future schema change cannot silently
  degrade timestamps to `null`.
- The `_id` ASC sort, the committed-visibility gate (`record_type =
  transaction` + `state = committed` + non-blank `commit_id`), and the
  short-circuit on missing/open/old-shape headers are unchanged.
- No write-side change. `TransactionMessagePersistenceService` continues
  to set `Instant` values; the read service now tolerates whichever
  representation the Mongo driver surfaces when documents round-trip
  through a raw `Map`.

### New unit coverage

- New feature in `CommittedTransactionReadServiceSpec`:
  `'java.util.Date timestamps from raw Mongo documents are coerced to
  Instant'`. Seeds the header with `Date.from(Instant.parse(...))` for
  `opened_at` and `committed_at` and the message row with `Date.from(...)`
  for `received_at`, then asserts that `snapshot.openedAt`,
  `snapshot.committedAt`, and `snapshot.messages[0].receivedAt` are all
  `instanceof Instant` and equal the original `Instant` values.
- All previously-required features are retained: committed snapshot
  returns header + messages, open/uncommitted is hidden, missing or
  blank `commit_id` partial-write is hidden, missing header returns
  empty, older `TransactionService`-shape (no `record_type`) is
  hidden, blank/null/whitespace `txnId` raises
  `IllegalArgumentException`, null payload fields are tolerated, and
  the Mongo query is asserted to carry `Sort.by(ASC, '_id')` (proves
  the database is doing the sort).

### Files changed in this turn

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
  — added `import java.util.Date`, added `private static Instant
  toInstant(Object)`, and switched the three timestamp reads to use it.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`
  — added the `Date`-coercion feature.
- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
  — `STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` rewritten
  for the re-submission.

No write-side persistence, controller, DTO-package, integration-test,
build, or resource files changed. The `txn` write-ahead log shape from
`TASK-003` is preserved.

### Verification

Setup (project-documented; already up locally): `docker compose -f
docker/docker-compose.yml up -d`. Containers verified up:
`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`.

- `./gradlew :jade-tipi:test --tests
  '*CommittedTransactionReadServiceSpec*'` — BUILD SUCCESSFUL.
  Spec report: `tests=12, skipped=0, failures=0, errors=0` (was 11; +1
  new Date-coercion feature).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 49 unit tests across:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (12) — was 11
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

If a future verification fails because Docker / Mongo is not running,
the documented setup command is `docker compose -f
docker/docker-compose.yml up -d` (only Mongo is strictly required for
the unit suite because `JadetipiApplicationTests.contextLoads` opens a
Mongo connection).

## TASK-005 — Add committed transaction snapshot read layer

Goal completed. The backend now has a Kafka-free and HTTP-free read-side
layer over the accepted `txn` write-ahead log (TASK-003 / TASK-004) that
returns a single committed transaction snapshot (header + staged messages)
without materializing into long-term collections. The transaction header
remains the authoritative committed-visibility marker; child message
stamping is still not required.

Task lifecycle: `TASK-005` flipped to `READY_FOR_REVIEW` per directive
(no `IMPLEMENTATION_COMPLETE` status used).

### As-built decisions (per pre-work + director directives)

- **Service boundary.** New `service/CommittedTransactionReadService` is a
  Kafka-free / HTTP-free `@Service` with one public method:
  `Mono<CommittedTransactionSnapshot> findCommitted(String txnId)`. It uses
  `ReactiveMongoTemplate` directly (no new dependency) and reuses the
  field-name and state constants exposed on
  `TransactionMessagePersistenceService` so the read and write shapes stay
  in lock-step.
- **No controller.** Per directive, no controller was added for TASK-005;
  service-level coverage is sufficient. A later HTTP task can wrap this
  service into a thin adapter.
- **Committed-visibility gate.** The service emits `Mono.empty()` unless
  the header has `record_type='transaction'`, `state='committed'`, and a
  non-blank `commit_id`. This rejects:
  - missing headers (no document at `_id == txnId`),
  - open / uncommitted headers,
  - committed-but-no-`commit_id` partial-write states (defense in depth;
    today's writer always sets both atomically),
  - the older `TransactionService` document shape (which has no
    `record_type`) coexisting in the same `txn` collection.
  In each rejected case the message-record lookup is short-circuited.
- **Snapshot DTO location.** Per directive, snapshot return classes live
  under `org.jadetipi.jadetipi.service`, not `dto/`. They are internal
  service-boundary types, not HTTP request/response DTOs:
  - `service/CommittedTransactionSnapshot` (`txnId`, `state`, `commitId`,
    `openedAt`, `committedAt`, `openData`, `commitData`, `messages`).
  - `service/CommittedTransactionMessage` (`msgUuid`, `collection`,
    `action`, `data`, `receivedAt`, `kafka`).
- **Read-side Kafka provenance.** Per directive, a service-local
  `service/KafkaProvenance` value object is used instead of reusing the
  write-side `kafka.KafkaSourceMetadata`. Same fields (`topic`,
  `partition`, `offset`, `timestampMs`) but the read service does not
  import the `kafka` package.
- **Deterministic message ordering.** The Mongo query for messages is
  `Criteria.where('record_type').is('message').and('txn_id').is(txnId)`
  with `Sort.by(ASC, '_id')`. Because `_id = txn_id~msg_uuid` and `txn_id`
  is constant within a result set, this collapses to ordered-by-`msg_uuid`
  and uses the implicit `_id` index. The unit spec asserts the issued
  `Query.sortObject == new Document('_id', 1)` per directive (proves the
  database is doing the sort) and verifies the snapshot preserves the
  Mongo-returned order regardless of the mocked `Flux` order.
- **Input validation.** `Assert.hasText` on `txnId` matches the existing
  `TransactionService` convention; blank/null/whitespace inputs raise
  `IllegalArgumentException` and never call Mongo.
- **Null tolerance.** Header timestamps, `open_data`, `commit_data`,
  message `data`, `received_at`, and the `kafka` sub-doc may all be
  absent on real records; the snapshot returns them as `null` without
  NPE.
- **No production-code change to existing files.**
  `TransactionMessagePersistenceService` (writer),
  `TransactionMessageListener`, `TransactionService`, controllers,
  `application.yml`, and `build.gradle` are unchanged. The directive
  required preserving the `TASK-003` write-ahead log shape.

### Files added

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionSnapshot.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMessage.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/KafkaProvenance.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`
  (8 features, 11 test rows once the data-driven `where:` block is
  expanded for blank/null/whitespace `txnId`).

### Files updated (in scope)

- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
  — `STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` updated with
  the as-built shape and verification.

### Verification

Setup (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — Mongo + Kafka +
  Keycloak. Verified containers were already healthy from prior work
  (`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`).

Compilation:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL
  (cross-source-set sanity check; no integration spec added or required).

Targeted unit run:

- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`
  — BUILD SUCCESSFUL. New spec: `tests=11, skipped=0, failures=0,
  errors=0`.

Regression check on the full unit suite:

- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. Now 48 tests pass:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (11) — new
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

No integration spec is added for TASK-005. The TASK-004 integration spec
already proves the Kafka writer path produces the documents this read
service projects, and the read service has no Kafka or HTTP dependency
of its own.

### Out-of-scope items not implemented (per directive)

- No materialization into `ent`, `ppy`, `typ`, `lnk`, or other long-term
  collections.
- No semantic reference validation between properties / types / entities /
  assignments.
- No HTTP submission wrapper or read controller; service-level coverage
  is sufficient for `TASK-005`.
- No change to Kafka ingestion, topic configuration, or message envelope
  semantics. The `txn` write-ahead log shape from `TASK-003` is preserved.
- No new auth/authz policy.

## TASK-004 — Add Kafka transaction ingest integration coverage

Goal completed. The backend Kafka ingestion path now has an end-to-end
integration spec that publishes canonical `Message` records (open + data
+ commit) to a per-spec Kafka topic and asserts the resulting `txn`
header and message documents in MongoDB. The spec is opt-in: it skips
unless `JADETIPI_IT_KAFKA=1` is set and a 2-second `AdminClient` broker
probe to `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`) succeeds.

### As-built decisions (per pre-work)

- **Topic strategy.** Per-test topic `jdtp-txn-itest-${shortUuid}`
  created via Kafka `AdminClient.createTopics` in a static
  `@DynamicPropertySource` method (so the topic exists before the
  Spring listener container starts). Deleted in `cleanupSpec` via
  `AdminClient.deleteTopics`. Failed delete is logged but tolerated —
  each run picks a fresh `shortUuid` so leftovers do not interfere.
- **Listener subscription.** The spec sets
  `jadetipi.kafka.txn-topic-pattern` to a regex matching only its
  per-run topic, so the listener does not pick up records from
  `jdtp_cli_kli` or other developers' topics. The default production
  pattern is unchanged.
- **Test gating.** Spock `@IgnoreIf` runs before the Spring context
  loads. Without the env flag or with no broker reachable, the spec
  cleanly reports `skipped=2` and does not start the Spring context.
- **Consumer-group strategy.** A per-run unique
  `spring.kafka.consumer.group-id` (`jadetipi-itest-${shortUuid}`)
  combined with `auto-offset-reset=earliest` (already the production
  default) gives every run a deterministic offset-0 start.
- **Topic discovery latency.**
  `spring.kafka.consumer.properties.metadata.max.age.ms` is shortened
  to `2000` for this spec, so the pattern subscription notices the
  pre-created test topic within ~2s instead of the 5-minute Kafka
  default.
- **Producer wiring.** Plain `KafkaProducer<String, byte[]>` (acks=all)
  serializing each `Message` with the project's
  `org.jadetipi.dto.util.JsonMapper.toBytes(...)`. No new dependency.
  Records are keyed by `txnId` so they hash to the single test-topic
  partition, preserving open → data → commit order.
- **Wait strategy.** Inline reactive polling helpers
  (`awaitMongo` / `awaitConditionTrue`) with a 30s ceiling and 250ms
  cadence. No `org.awaitility:awaitility` dependency added.
- **Cleanup scope.** Per-feature: `mongoTemplate.remove(...)` keyed by
  `txn_id` so the spec coexists with `TransactionServiceIntegrationSpec`,
  which writes its own (different-shape) documents to `txn`. Per-spec:
  delete the test topic.
- **Number of features.** Two:
  1. **Happy path.** Publish open + data + commit, assert the header
     reaches `state=committed` with a backend `commit_id`, and assert
     the data message document has `_id = txnId~msgUuid`,
     `record_type=message`, `collection=ppy`, `kafka.topic`,
     `kafka.partition`, `kafka.offset`, and `kafka.timestamp_ms`.
     Verify `count(txn_id=...) == 2` (one header + one message).
  2. **Idempotency sanity check.** Re-publish the same data message
     after the commit lands; assert the per-`txn_id` document count
     stays at 2 (the persistence service treats matching duplicates as
     `APPEND_DUPLICATE`).
- **Profile vs `@TestPropertySource`.** Used `@DynamicPropertySource`
  inline; no new `application-integration-test.yml` profile was added.
  `@DynamicPropertySource` also runs the `AdminClient` topic creation
  *before* Spring context startup, which is the ordering hook the
  pre-work flagged as needed for pattern subscription.
- **No production-code change.** `TransactionMessageListener`,
  `TransactionMessagePersistenceService`, `KafkaIngestProperties`,
  `KafkaIngestConfig`, and `application.yml` are unchanged. The
  integration test exposed no bug in the accepted ingestion path.
- **No logback-test.xml.** Kafka-client logs are verbose but readable;
  not enough noise to justify a new test logging config.

### Files added

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  (new) — the spec described above. Includes a static gate method,
  `@DynamicPropertySource` overrides + topic creation, scoped Mongo
  cleanup, and inline polling helpers.

### Files unchanged

- All `jade-tipi/src/main/...` files. Production behavior is preserved.
- `jade-tipi/src/test/resources/application-test.yml`. The test profile
  still sets `jadetipi.kafka.enabled: false` so unit-test
  `@SpringBootTest` contexts (e.g. `JadetipiApplicationTests.contextLoads`)
  do not start a Kafka listener. The new integration spec re-enables
  the listener via `@DynamicPropertySource` for its own context only.
- `jade-tipi/build.gradle`. No new dependencies; `kafka-clients` and
  `spring-kafka` were already on the integration-test classpath via
  `spring-kafka` (added in TASK-003), and the integration-test source
  set already inherits both.

### Verification

Setup (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` brings up
  `mongodb`, `keycloak`, `kafka`, and `kafka-init`.
  Verified containers: `jade-tipi-mongo` healthy, `jade-tipi-keycloak`
  healthy, `jade-tipi-kafka` healthy.

Compilation:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL.

Targeted integration run (env flag set, brokers up):

- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — BUILD SUCCESSFUL. Test report:
  `tests=2, skipped=0, failures=0, errors=0, time=5.347s` on the
  first run, `4.917s` on a `--rerun-tasks` re-run (stable).

Skip behavior (env flag NOT set, brokers up):

- `./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — BUILD SUCCESSFUL. Test report:
  `tests=2, skipped=2, failures=0, errors=0, time=0.0s`
  (Spring context never loaded, both features ignored).

Regression check on unit tests:

- `./gradlew :jade-tipi:test --rerun-tasks` — BUILD SUCCESSFUL.
  37 tests pass:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

### How to run locally

```
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
```

To run the full integration suite (existing Mongo/Keycloak-backed
specs plus this new Kafka spec), drop the `--tests` filter and keep
the env flag set.

### Out-of-scope items not implemented (per directive)

- Materialization of committed records into `ent`/`ppy`/`typ`/`lnk`.
- HTTP submission wrapper.
- Kafka ACLs / OAuth-SASL / Kafka Streams / exactly-once.
- Redesign of the message envelope or persistence record shape.
- No production code changes; the integration test exposed no bug.

## TASK-003 — Persist Kafka transaction messages to txn

Goal completed. The Spring Boot backend now consumes canonical
`org.jadetipi.dto.message.Message` records from Kafka and persists them
into MongoDB's `txn` collection as the durable transaction
write-ahead log. Two record kinds live in `txn`: a header document
(`record_type=transaction`) keyed by the canonical `txn_id`, and one
message document per received `Message` (`record_type=message`) keyed
by `txn_id~msg_uuid`.

### Changes by acceptance criterion

- **Backend Kafka consumer dependencies and configurable topic pattern.**
  - `jade-tipi/build.gradle`: added
    `implementation 'org.springframework.kafka:spring-kafka'` (version
    pinned by the Spring Boot 3.5.6 BOM).
  - `jade-tipi/src/main/resources/application.yml`: added a
    `spring.kafka` block (`group-id` default `jadetipi-txn-ingest`,
    byte-array value deserializer, `enable-auto-commit: false`,
    `listener.ack-mode: MANUAL_IMMEDIATE`) and a `jadetipi.kafka`
    block (`txn-topic-pattern` default
    `jdtp-txn-.*|jdtp_cli_kli`, `enabled` default `true`,
    `persist-timeout-seconds` default `30`). Spring property
    placeholders are escaped (`\${...}`) because the project's
    `processResources` runs Groovy `expand` over `application.yml`.

- **Kafka message deserialization to `Message` via the project mapper.**
  - `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListener.groovy`
    (new): a `@Component` annotated with
    `@KafkaListener(topicPattern = "${jadetipi.kafka.txn-topic-pattern}", autoStartup = "${jadetipi.kafka.enabled}", groupId = "${spring.kafka.consumer.group-id:jadetipi-txn-ingest}")`.
    Receives `ConsumerRecord<String, byte[]>` and `Acknowledgment`,
    deserializes via `org.jadetipi.dto.util.JsonMapper.fromBytes`.

- **Envelope/schema-level validation before persistence.**
  - The listener calls `message.validate()` which runs JSON Schema
    validation against `/schema/message.schema.json`. Malformed JSON
    or schema-invalid messages are logged and acknowledged (poison-pill
    skip per directive) so a single bad record does not stall the
    consumer. Persistence failures are not acknowledged, so the
    listener container retries.

- **Two `txn` record kinds with the documented shapes.**
  - Header: `_id = txnId`, `record_type = "transaction"`, `txn_id`,
    `state` (`open`/`committed`), `opened_at`, optional `commit_id`,
    `committed_at`, plus the open/commit `data` payloads under
    `open_data`/`commit_data`.
  - Message: `_id = txnId~msgUuid`, `record_type = "message"`,
    `txn_id`, `msg_uuid`, `collection`, `action`, `data`,
    `received_at`, and a `kafka` sub-doc with topic/partition/offset/
    `timestamp_ms`.

- **`txn/open` creates or confirms the header.**
  - `service/TransactionMessagePersistenceService.openHeader` upserts
    with `setOnInsert` for `opened_at` so a re-delivered open does
    not rewrite the original timestamp. Re-delivery on an existing
    header (open or already committed) returns
    `OPEN_CONFIRMED_DUPLICATE` and does not modify the document.

- **Idempotent data appends keyed by natural `_id`.**
  - `appendDataMessage` inserts with `_id = txnId~msgUuid`. On
    duplicate-key, the service reads the stored document and compares
    `(collection, action, data)` to the incoming message. Equal
    payload returns `APPEND_DUPLICATE` (success); conflicting payload
    raises `ConflictingDuplicateException` (un-acked, retried).

- **`txn/commit` assigns a backend `commit_id`.**
  - `commitHeader` reads the header and:
    - Errors with `IllegalStateException` if the header is missing
      (commit before open is un-acked, retried).
    - Returns `COMMIT_DUPLICATE` and does not call
      `idGenerator.nextId()` again if the header is already
      committed.
    - Otherwise calls `idGenerator.nextId()` for the orderable
      `commit_id`, sets `state = "committed"`, `committed_at`, and
      `commit_data` in one update, and returns `COMMITTED`.
  - Child message stamping is intentionally not implemented; readers
    are expected to resolve commit visibility via the header.
  - `txn/rollback` is treated as an explicit no-op per directive: the
    service logs and returns `ROLLBACK_NOT_PERSISTED` without writing.

- **Service stays Kafka-free and HTTP-free.**
  - `TransactionMessagePersistenceService` imports neither
    `org.apache.kafka.*` nor any web type. The listener owns all
    Kafka-client and Spring-Kafka imports. Provenance is passed in as
    `KafkaSourceMetadata` (a Groovy `@Immutable` value object), which
    has no Kafka-client dependency.

- **Tests.**
  - `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
    (new, 11 features): open first time, open re-delivered (open and
    committed), data append first time, append duplicate (equal),
    append duplicate (conflicting), commit first time, commit
    duplicate (no second `nextId`), commit before open, rollback,
    and a null-message guard.
  - `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
    (new, 4 features): valid record persisted and acknowledged,
    unparseable bytes acknowledged without forwarding, schema-invalid
    JSON acknowledged without forwarding, persistence error not
    acknowledged.
  - `jade-tipi/src/test/resources/application-test.yml`: existing
    file updated to add `jadetipi.kafka.enabled: false` (per
    directive — no duplicate test profile created) so
    `JadetipiApplicationTests.contextLoads` does not start a
    listener container.

### Verification

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `docker compose -f docker/docker-compose.yml up -d mongodb` to bring
  up MongoDB (the only docker dependency `JadetipiApplicationTests.contextLoads`
  needs).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 37 tests pass,
  including `JadetipiApplicationTests.contextLoads`, the existing
  `TransactionServiceSpec`, and the two new specs above.

### Out-of-scope items deferred (with rationale)

- **Optional Kafka integration test under `integrationTest/`.**
  Per directive, the integration test was deferred because the
  project has no Testcontainers wiring and the docker stack's
  `kafka-init` sidecar pre-creates only `jdtp_cli_kli`. Service and
  listener unit specs cover the acceptance criteria. A follow-up task
  can add Testcontainers and an end-to-end integration spec.
- **Materializing committed records into `ent`/`ppy`/`typ`/`lnk`.**
  Listed in `OUT_OF_SCOPE` and not implemented.
- **HTTP submission wrapper.** Listed in `OUT_OF_SCOPE`. The
  persistence service is intentionally Kafka-free so a future HTTP
  adapter can call it without dragging in Kafka or web types.
- **Topic registration / Kafka ACLs / OAuth-SASL hardening / Kafka
  Streams / exactly-once.** Listed in `OUT_OF_SCOPE` and not
  attempted.

### Files changed

- `jade-tipi/build.gradle` (added `spring-kafka` dependency)
- `jade-tipi/src/main/resources/application.yml` (added
  `spring.kafka` and `jadetipi.kafka` blocks)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/KafkaIngestConfig.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/exception/ConflictingDuplicateException.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaIngestProperties.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaSourceMetadata.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListener.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/PersistResult.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  (new)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
  (new)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
  (new)
- `jade-tipi/src/test/resources/application-test.yml` (added
  `jadetipi.kafka.enabled: false`, plus the matching
  `spring.kafka.bootstrap-servers` placeholder)
- `docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`
  (`STATUS` flipped to `IMPLEMENTATION_COMPLETE`; `LATEST_REPORT`
  updated with the as-built shape and verification result)
