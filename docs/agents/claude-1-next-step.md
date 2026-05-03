# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-028 — Human-readable Kafka entity submission path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-028-human-readable-kafka-ent-submission.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. TASK-028 continues the
Kafka-first domain write path with the smallest increment that lets a
human-readable Kafka transaction create a root-shaped `ent` document
from the existing `04-create-entity-type.json` and `06-create-entity.json`
examples. Acceptance criteria:

- Top-level `collection: "ent"`, `action: "create"`.
- `data.id` is the materialized entity object ID.
- `data.type_id` references a `typ` entity-type record.
- `data.properties` is optional or an explicit empty/plain JSON object;
  `data.links` is optional or empty on create (canonical relationships
  are separate `lnk` messages).
- Decide whether the bounded proof materializes only `ent + create` or
  also expands materializer support to entity-type `typ + create` for
  the example transaction sequence.
- Add or update example resource JSON only if the existing examples do
  not already match the accepted human-readable shape and sequence.
- Propose focused automated coverage for DTO validation, materialized
  root shape, idempotency/skipping behavior, and the narrowest practical
  Kafka/Mongo integration check.
- Report exact verification commands and any local Docker/Kafka/Mongo/
  Gradle setup blockers.

Out of scope (per task file and `DIRECTIVES.md` TASK-028 Direction): no
HTTP submission endpoints, no property-assignment materialization, no
required-property enforcement, no semantic resolution of `data.type_id`,
no permission enforcement, no contents-link read changes, no nested
Kafka operation DSL, no redesign of entity typing, property-definition
validation, or the transaction write-ahead log.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source
change is made until the director advances TASK-028 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` "Objects, Types, And Properties" (line 17 onward) and
  "Logical Objects And Physical Documents" (line 58 onward) prescribe
  that `ent` is a long-term materialized collection alongside `loc`,
  `lnk`, `typ`, `grp`, `ppy`, `uni`, `vdn`, and that the first
  materializer should write a root-document-only physical shape with
  explicit `properties`, denormalized `links`, and reserved `_head`
  metadata.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  "Entity Creation" (lines 123–136) documents the canonical
  human-readable `ent + create` shape: `collection: "ent"`,
  `action: "create"`, `data.id`, `data.type_id`. The example payload
  in the doc uses ID segments `~en~plate_a` and `~ty~plate_96` (2-char
  abbreviations).
- The same doc's "Link Types And Concrete Links" (lines 161–209) and
  "Reference Examples" (lines 283–301) use ID segments `~typ~`,
  `~lnk~`, `~loc~`, `~ent~` (3-char abbreviations matching the
  collection abbreviation). The materializer spec test
  `entityCreateMessage()` (`CommittedTransactionMaterializerSpec.groovy`
  lines 154–163) and the contents-link example (`12-…`) likewise use
  `~ent~sample_x1` (3-char).
- TASK-013/TASK-014 (root-shaped contract), TASK-015 (root-shaped
  contents read), TASK-019 (Clarity/ESP container prototype),
  TASK-020 (root-shaped `grp`), TASK-026 (human-readable `loc + create`
  path), and TASK-027 (human-readable contents-link path) are all
  accepted; they define the materializer entry point and the
  human-readable wire conventions this task must reuse.

### Current example resource state (read of source on `claude-1`)

`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `01-open-transaction.json` — `txn + open` with shared
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`. Sequence anchor.
- `09-commit-transaction.json` — `txn + commit` on the same txn uuid.
- `04-create-entity-type.json` — `typ + create` with
  `data.id = …~ty~plate_96` (2-char `~ty~` segment), `data.name`,
  `data.description`. **No `data.kind` field.** The materializer
  currently treats this as `skippedUnsupported` because
  `isSupported()` only accepts `typ + create` when
  `data.kind == "link_type"`.
- `05-update-entity-type-add-property.json` — `typ + update`
  (operation `add_property`). Skipped today as `update` action.
- `06-create-entity.json` — `ent + create` with
  `data.id = "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~plate_a"`
  and `data.type_id = "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96"`
  (verbatim from the committed file). No `data.properties`, no
  `data.links` blocks. **Note the within-file asymmetry:** the
  entity's own `data.id` segment is `~ent~` (3-char, matches the
  collection abbreviation), but the entity-type reference in
  `data.type_id` is `~ty~plate_96` (2-char). The companion `04-…`
  file uses `~ty~plate_96` for its own `data.id`, so the
  `06-…` reference is internally consistent with the `04-…`
  declaration even though the abbreviation segments do not match
  the post-TASK-026 wire convention used by the `loc`/`typ`/`lnk`/
  `grp` examples.
- `07-assign-property-value-text.json`, `08-assign-property-value-number.json` —
  `ppy + create` property assignments. Skipped today as
  unsupported collection (`ppy`).
- `10-create-location.json` — `loc + create` (post-TASK-026 shape) —
  `data.id = …~loc~freezer_a`, explicit `data.properties` and
  `data.links: {}`.
- `11-create-contents-type.json` — `typ + create` with
  `data.kind: "link_type"`, `data.id = …~typ~contents` (3-char),
  declarative role/label/allowed-collection facts at flat `data` root.
- `12-create-contents-link-plate-sample.json` — `lnk + create` with
  `data.left = …~loc~plate_b1`, `data.right = …~ent~sample_x1`
  (3-char), `data.properties.position`.
- `13-create-group.json` — `grp + create` with permissions map.

So the `~ent~` 3-char convention is already in the wire shape that the
recently accepted TASK-026/TASK-027 path produces and the `06-…` entity
ID already uses `~ent~`. The mismatch is in the entity-type reference
segment `~ty~` (2 chars) inside `04-…` and `06-…`, plus the optional
`data.properties` / `data.links` blocks being absent on `06-…`.

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:

- `isSupported()` (lines 206–226) — accepts only
  `loc + create`, `lnk + create`, `grp + create`, and
  `typ + create` where `data.kind == "link_type"`. Every other
  collection/action — including `ent + create` and entity-type
  `typ + create` (no `data.kind` set) — falls through to
  `default → false` and is counted as `skippedUnsupported` without
  raising an error. This is exactly what the spec test
  `'skips ppy and ent create messages'` (lines 701–710) and
  `'skips a typ create whose data.kind is not link_type'` (lines
  450–458) assert today.
- `extractDocId()` (lines 228–238) — already handles missing/blank
  `data.id` by returning `null`, which leads to
  `result.skippedInvalid++` and an error log without an exception.
- `buildDocument()` (lines 240–266) — for non-`lnk` supported
  collections, prefers `data.properties` when it is a Map and copies
  it verbatim into root `properties`; otherwise falls back to
  `buildInlineProperties(data)`, which copies every `data` entry
  except `id` and `type_id` into root `properties`. This is exactly
  the shape the new `ent` materialization should produce.
- `_head` / provenance (lines 285–303) — already populates
  `schema_version`, `document_kind: "root"`, `root_id`, and a
  `provenance` sub-document with `txn_id`, `commit_id`, `msg_uuid`,
  `collection`, `action`, `committed_at`, `materialized_at`. This
  contract is reused by `loc`, `typ`, `lnk`, and `grp`, so an `ent`
  root will reuse it verbatim.
- Duplicate-key handling (lines 176–204) — supported via
  `findById` plus `isSamePayload()` ignoring only
  `_head.provenance.materialized_at`. New `ent` inserts gain the same
  idempotency for free once `ent` is added to `isSupported()`.

**Implication:** the smallest implementation change is to expand
`isSupported()` to include `ent + create` (and, depending on the
director decision below, entity-type `typ + create`); no other
materializer code path needs to change. The constants section
(lines 93–96) needs a new `COLLECTION_ENT = 'ent'` for symmetry, plus
a new `ENTITY_TYPE_KIND` constant if the entity-type discriminator
direction lands.

### Current automated coverage (read of source on `claude-1`)

DTO library:

- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:
  - `EXAMPLE_PATHS` (lines 24–38) loops every example resource through
    `JsonMapper` round-trip and `Message.validate()` schema validation.
    `04-…`, `05-…`, `06-…` are all covered in that loop today.
  - "Message JSON includes the collection field" (lines 66–83) builds
    a synthetic `Collection.ENTITY` `create` message and asserts
    `parsed.collection == 'ent'`.
  - No focused feature spec yet exists for the `06-create-entity.json`
    shape (`data.id`, `data.type_id`, optional `data.properties`,
    optional `data.links`). The recent `loc` and `lnk` examples have
    such focused features; `ent` does not.

Backend unit:

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`:
  - `entityTypeMessage()` (lines 104–116) and `entityCreateMessage()`
    (lines 154–163) helpers exist. Both use 3-char ID segments
    (`~typ~plate_96`, `~ent~plate_a`).
  - "skips a typ create whose data.kind is not link_type" (lines
    450–458) and "skips ppy and ent create messages" (lines 701–710)
    pin the current skip-behavior contract. Either new materializer
    support changes these specs or new feature specs replace their
    intent.
  - "mixed-message snapshot inserts in snapshot order with correct
    counts" (lines 951–967) currently asserts
    `insertOrder == ['loc', 'typ', 'lnk']` and counts
    `materialized == 3, skippedUnsupported == 2` (the 2 skips being
    `ppy` and `ent`). Adding `ent + create` materializer support
    flips one of those skip counts and adds an `'ent'` insert; this
    spec needs to be updated as part of the implementation turn.

Backend integration (opt-in, gated on `JADETIPI_IT_KAFKA=1` + Kafka
reachable):

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  publishes `open + data + commit` to a Kafka topic and asserts the
  header and message documents persist into MongoDB. It does not
  assert materialized roots; that lives in
  `ContentsHttpReadIntegrationSpec` for the contents-flow case.
- No existing integration spec drives an end-to-end Kafka → Mongo
  `ent` materialization. The narrowest practical opt-in spec for
  this task can be a new lightweight integration that publishes
  `open + (typ entity-type if in scope) + ent + commit` and asserts
  the materialized `ent` root on MongoDB.

### Schema status (`message.schema.json`)

For `collection != "grp"`, `data` follows `SnakeCaseObject`, which
permits any snake_case-keyed object recursively. The `06-…` and
`04-…` examples already validate today (covered by the loop in
`MessageSpec`). Tightening the schema to require `ent.data.id`,
`ent.data.type_id`, or to forbid unknown fields would be a semantic
change; per the task's `OUT_OF_SCOPE` (no semantic reference
validation, no required-property enforcement) **no schema-file change
is required or in-scope** for this task. Same as TASK-026/TASK-027.

### Smallest implementation plan

Goal: smallest set of changes that proves the existing human-readable
`ent + create` example materializes into a root-shaped `ent` document
with the documented `_id`, `id`, `collection: "ent"`, top-level
`type_id`, explicit `properties`, denormalized `links`, and `_head`
fields, while keeping idempotency and the existing skip-behavior
contracts working.

#### File changes

1. **Example resource updates (in `libraries/jade-tipi-dto/src/main/resources/example/message/`):**
   - `06-create-entity.json`: add explicit `data.properties: {}` and
     `data.links: {}` to mirror the post-TASK-026 `loc` shape and make
     the human-readable empty-properties / empty-links case visible at
     the wire layer. Keep `data.id = …~ent~plate_a` and
     `data.type_id = …~ty~plate_96` if the director keeps the
     2-char `~ty~` convention; otherwise rewrite the type-id segment
     to `~typ~plate_96` as part of step 2 below.
   - `04-create-entity-type.json`: only edit if the director decides
     entity-type `typ + create` materialization is in scope for this
     task **and** decides to introduce an explicit `data.kind:
     "entity_type"` discriminator. See "Open questions" below.
   - **Default proposal (smallest delta):** edit only `06-…` to add
     the explicit empty `data.properties` and `data.links` blocks;
     leave `04-…` untouched.

2. **Materializer code change (in
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`):**
   - Add `static final String COLLECTION_ENT = 'ent'` to the constants
     block.
   - Extend `isSupported()` with a new case: `case COLLECTION_ENT:
     return true` (mirroring `loc`, `lnk`, `grp`).
   - **No other code change is required** for `ent`. `buildDocument()`
     already routes non-`lnk` collections through the
     `data.properties`-Map preferred path (post-TASK-026), so
     `ent + create` with explicit `data.properties: {}` materializes
     as `properties: {}, links: {}` automatically. Without explicit
     properties the inline-properties fallback runs, which is also
     acceptable (it would copy any unknown extra `data` fields under
     `properties` while keeping `id` and `type_id` at the top).
   - **Director decision (see Open questions):** if entity-type
     `typ + create` should also materialize, the Javadoc on
     `CommittedTransactionMaterializer` at lines 36–38 needs the
     "bare entity-type `typ` records are intentionally skipped here"
     note removed/inverted, plus an explicit branch in `isSupported()`
     for `typ + create` with `data.kind == "entity_type"` (or with
     missing `data.kind`, depending on the chosen discriminator
     contract). I recommend the explicit `entity_type` discriminator
     for symmetry with the `link_type` / `definition` / `assignment`
     pattern.

3. **Backend unit tests (in
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`):**
   - Replace the existing "skips ppy and ent create messages" feature
     (lines 701–710) with "skips ppy create messages" (since `ent` is
     no longer skipped). The intent — proving `ppy` remains skipped —
     is preserved.
   - Add a new feature
     `'materializes an ent create as a root document with top-level
      type_id, empty properties, and empty links'`:
     - Capture `mongoTemplate.insert(_, 'ent')`.
     - Build a snapshot from `entityCreateMessage()` extended to carry
       `data.type_id` and explicit `data.properties: [:]`,
       `data.links: [:]`. (The current helper omits `type_id`; add an
       overload or in-feature `dataOverrides` map.)
     - Assert: `result.materialized == 1`, captured `_id == ENT_ID`,
       `id == ENT_ID`, `collection == 'ent'`, `type_id` matches the
       message's type_id, `properties == [:]`, `links == [:]`,
       `_head.schema_version == 1`, `_head.document_kind == 'root'`,
       `_head.root_id == ENT_ID`,
       `_head.provenance.collection == 'ent'`,
       `_head.provenance.action == 'create'`,
       `_head.provenance.txn_id == TXN_ID`,
       `_head.provenance.msg_uuid` populated,
       no legacy `_jt_provenance`.
   - Add a complementary feature
     `'ent create without payload properties or links defaults root
      properties and links to empty maps'`:
     - Same shape as above but with `properties` and `links` omitted
       from the message data.
     - Assert root `properties == [:]` and `links == [:]` via the
       inline-properties fallback (because `id`/`type_id` are the
       only `data` keys, the fallback yields `[:]`).
   - Add a feature
     `'ent create with missing or blank data.id is counted as
      skippedInvalid'`:
     - Snapshot of `entityCreateMessage([id: ''])` and `[id: '   ']`
       (Spock `where:` block).
     - Assert `result.skippedInvalid == 1`, `result.materialized == 0`,
       `0 * mongoTemplate.insert(_, _)`.
   - Add a feature
     `'ent create with identical-payload duplicate is matching, not
      conflicting'`:
     - Mirrors the existing `loc`-side duplicate-matching feature
       (lines 723–766). Asserts `result.duplicateMatching == 1`,
       `result.conflictingDuplicate == 0`, ignoring
       `_head.provenance.materialized_at`.
   - Add a feature
     `'ent create with conflicting payload is conflictingDuplicate
      and is not overwritten'`:
     - Mirrors the existing `loc`-side conflicting-duplicate feature
       (lines 768–811). Asserts
       `result.conflictingDuplicate == 1`, no overwrite path taken.
   - Update the "mixed-message snapshot" feature (lines 951–967):
     - The snapshot is currently
       `[loc, ppy, typ link-type, ent, lnk]` with insert order
       `['loc', 'typ', 'lnk']` and `materialized == 3,
        skippedUnsupported == 2`.
     - With `ent + create` now supported the expected insert order
       becomes `['loc', 'typ', 'ent', 'lnk']` and counts become
       `materialized == 4, skippedUnsupported == 1`. (`ppy` remains
       the lone skip.)
   - **Director decision spillover:** if entity-type `typ + create`
     also becomes supported, the existing "skips a typ create whose
     data.kind is not link_type" feature (lines 450–458) flips into
     a new positive feature `'materializes an entity-type typ create
     as a root document with properties.kind == "entity_type"'`.
     Otherwise that feature stays as-is.

4. **DTO library tests (in
   `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`):**
   - Add one Spock feature
     `'ent create example uses the human-readable data.id, data.type_id,
      and explicit empty data.properties / data.links shape'`:
     - Reads `06-create-entity.json` after the proposed example edit.
     - Asserts `message.collection() == Collection.ENTITY`,
       `message.action() == Action.CREATE`,
       `data.id` ends with `~ent~plate_a`,
       `data.type_id` is the entity-type id from `04-…`,
       `data.properties == [:]`, `data.links == [:]`.
   - Add one feature
     `'entity transaction example sequence shares one txn uuid and
      lines up entity-type id with entity type_id'`:
     - Reads `01-…`, `04-…`, `06-…`, `09-…`.
     - Asserts all four share the same `txn.uuid`.
     - Asserts `06-…` `data.type_id == 04-…` `data.id` (in-resource
       cross-reference).
     - Mirrors the analogous TASK-027 test for the contents-link trio.
   - **Default proposal:** include both. Drop the second if director
     review prefers strict minimalism, since the first already covers
     the wire-shape acceptance criterion.

5. **Backend integration test (in
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`):**
   - **Default proposal:** add a narrow opt-in spec
     `EntityCreateKafkaMaterializeIntegrationSpec.groovy` modeled on
     `ContentsHttpReadIntegrationSpec` (without the HTTP layer):
     publishes `open + (typ entity-type if in scope) + ent + commit`
     to a per-spec Kafka topic, waits for the committed `txn` header
     and the materialized `ent` root in MongoDB, and asserts
     top-level `_id`, `id`, `collection == 'ent'`, `type_id`,
     `properties == [:]`, `links == [:]`, and `_head.provenance`
     pointing at the same `txn.uuid` and `msg_uuid`. Gated on
     `JADETIPI_IT_KAFKA=1` + Kafka/Keycloak reachable, same as the
     existing contents/ingest integration specs.
   - Alternatively, extend `ContentsHttpReadIntegrationSpec` with an
     `ent` materialize-and-read assertion. **Not preferred** —
     mixes contents-link concerns with entity concerns and broadens
     the spec's scope.
   - **If director prefers no new integration spec** because the
     `Kafka → Mongo` path is already proven by
     `TransactionMessageKafkaIngestIntegrationSpec` and the
     materializer is unit-tested above, drop step 5 and rely on the
     unit-level coverage. Flagging here so that decision is explicit.

6. **Documentation updates (only if director decision changes the
   convention):**
   - `docs/architecture/kafka-transaction-message-vocabulary.md`
     "Entity Creation" (lines 123–136) currently shows ID segments
     `~en~plate_a` and `~ty~plate_96`. If the director rules in
     favor of 3-char ID segments matching the collection abbreviation,
     update this code block to `~ent~plate_a` and `~typ~plate_96`.
     Otherwise leave the doc alone.
   - `docs/OVERVIEW.md` — no change anticipated; it does not pin the
     entity ID convention.
   - `docs/architecture/jade-tipi-object-model-design-brief.md` — only
     touch if the entity-type discriminator decision changes
     (`data.kind: "entity_type"` is a vocabulary expansion that this
     doc may want to record).
   - **Default proposal:** no doc changes if the director keeps the
     existing 2-char `~ty~`/`~en~` convention in the vocabulary doc
     and chooses not to introduce an explicit `entity_type` kind. If
     either decision flips, the corresponding doc edit lands in the
     implementation turn.

Expected total surface (with default proposals selected):
- 1 example resource edit (`06-create-entity.json`).
- ~5 lines of materializer code (constant + isSupported branch).
- ~80–120 lines of new Spock features in the materializer spec
  (5 new features + 1 updated feature) and ~30–50 lines in
  `MessageSpec`.
- 1 new integration spec (~120–150 lines), gated on
  `JADETIPI_IT_KAFKA=1`.

#### Out-of-scope guardrails (will **not** edit unless director ruling
expands scope)

- `clients/kafka-kli/**` — kli already accepts `--collection ent` and
  passes `data` through unchanged; no CLI change is needed.
- `frontend/**` — no UI surface for raw entity submission.
- `jade-tipi/src/main/groovy/.../service/ContentsLinkReadService.groovy`,
  `TransactionService.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy` — they treat `data`
  as opaque or do not interact with `ent`; no change needed.
- `message.schema.json` — see Schema status above.
- `frontend/.env.local` — generated; never hand-edited (per
  `CLAUDE.md`).
- All `07-…`, `08-…` property-assignment examples — `ppy` materialization
  is explicitly out of scope.

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    entity-flow examples plus the new focused features in MessageSpec.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new ent root-shape, idempotency, missing-id, and mixed-snapshot
#    features.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running). The new EntityCreateKafkaMaterializeIntegrationSpec
#    drives an end-to-end open + (typ?) + ent + commit -> materialized
#    ent root proof. The existing contents and ingest specs are also
#    rerun to confirm no regression.
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'

# Optional regression checks:
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*ContentsHttpReadIntegrationSpec*'
```

Setup commands (per `CLAUDE.md` "Tooling Refresh"; if local tooling is
missing they are reported, not treated as product blockers):

- Docker stack required for `:jade-tipi:test` because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection
  per project `CLAUDE.md`. Run
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`
  for Mongo-backed unit tests, or
  `docker compose -f docker/docker-compose.yml up -d` for the full
  Kafka/Mongo stack.
- If the Gradle wrapper cache is missing, the first `./gradlew`
  invocation bootstraps it; that is normal first-run behaviour, not
  a blocker.
- If a stale Gradle daemon is implicated, run `./gradlew --stop`
  before retrying (per `DIRECTIVES.md` TASK-026/TASK-027 review notes).
- If integration tests cannot run because Docker is not available in
  the sandbox, report the exact `docker compose ... up` command and
  stop rather than treating it as a product blocker.

### Open questions / blockers (for director review)

These are product-judgment questions that match the directive's
"If the ID convention or type contract needs product judgment, stop
with `STATUS: HUMAN_REQUIRED` rather than guessing." I'm flagging them
here in pre-work rather than stopping, so the director can rule once
and then advance the task. If the director prefers I stop with
`STATUS: HUMAN_REQUIRED` on either of these instead of proposing a
default, please redirect.

1. **Entity-type ID segment convention — `~ty~` vs `~typ~`.**
   The vocabulary doc and the existing `04-create-entity-type.json` /
   `06-create-entity.json` examples use the 2-char `~ty~` segment for
   entity-type IDs. The newer `11-…`, `12-…`, `13-…` examples and the
   `loc`/`grp`/`lnk` chains use 3-char segments matching the
   collection abbreviation. The materializer is indifferent — it
   uses `data.id` verbatim — but the wire shape is human-facing.
   - **Default proposal:** keep the existing 2-char `~ty~` segment in
     `06-…`'s `data.type_id` to avoid a user-facing convention change
     mid-task; flag the inconsistency for a follow-on cleanup task.
   - **Alternative:** rewrite both `04-…` `data.id` and `06-…`
     `data.type_id` to `~typ~plate_96`, and update the vocabulary doc
     "Entity Creation" example accordingly. This is a one-line edit
     per file but it is technically a vocabulary change.

2. **Entity ID segment convention — `~en~` vs `~ent~`.**
   The vocabulary doc "Entity Creation" example uses `~en~plate_a`
   (2-char). The committed `06-create-entity.json` already uses
   `~ent~plate_a` (3-char). The materializer spec helpers use
   `~ent~`. So the example file already matches the 3-char
   convention.
   - **Default proposal:** no example change for the entity ID
     segment; update the vocabulary doc "Entity Creation" example to
     match (`~ent~plate_a`) only if the director wants the doc and
     example to agree.

3. **Entity-type `typ + create` materialization — in scope or
   deferred?**
   The bounded proof can either (a) materialize only `ent + create`
   and leave entity-type `typ + create` skipped (current behaviour),
   or (b) also materialize entity-type `typ + create` so the example
   transaction `01 → 04 → 06 → 09` produces both a `typ` and an `ent`
   root. The task explicitly defers semantic resolution of
   `data.type_id` either way.
   - **Default proposal:** include entity-type `typ + create`
     materialization in this task, gated on a new `data.kind:
     "entity_type"` discriminator (see #4). Rationale: the contents
     trio TASK-027 already proved the `typ` + `lnk` co-presence;
     symmetry with that proof argues for a `typ` + `ent` co-presence
     here. It also lets a new integration spec build a self-contained
     entity-flow without referring to externally-created `typ` rows.
   - **Alternative:** materialize only `ent + create`, leave the
     entity-type `typ` skipped, and rely on a synthetic
     externally-seeded `typ` row in the integration spec. This is
     narrower but more brittle.

4. **Entity-type discriminator — explicit `data.kind: "entity_type"`
   or absence of `data.kind`?**
   Today the materializer treats any `typ + create` without
   `data.kind == "link_type"` as `skippedUnsupported`. To support
   entity-type creates we need either (a) an explicit
   `data.kind: "entity_type"` on `04-…` (and on the materializer
   `isSupported()` predicate), or (b) treat
   `data.kind` missing as entity-type by convention.
   - **Default proposal:** explicit
     `data.kind: "entity_type"` on `04-…` and an explicit case in
     `isSupported()`. Rationale: mirrors the existing
     `link_type` / `definition` / `assignment` discriminator pattern
     across `typ` and `ppy`. Makes the wire shape self-describing and
     keeps "missing kind" available as a future error case.
   - **Alternative:** treat missing `data.kind` on `typ + create` as
     entity-type. Smaller wire-shape diff but encodes a default by
     convention.

5. **Optional `data.properties` / `data.links` blocks on
   `06-create-entity.json`.**
   The task acceptance criterion says
   `data.properties` / `data.links` are "optional or empty". The
   existing `06-…` example carries neither.
   - **Default proposal:** add explicit empty `data.properties: {}`
     and `data.links: {}` blocks for visibility and symmetry with the
     post-TASK-026 `loc` shape. This makes the human-readable
     "no properties yet, no inline links" contract explicit.
   - **Alternative:** leave `06-…` unchanged. Smaller diff but the
     human-readable shape stays implicit.

6. **Integration spec — keep step 5 or drop it?**
   The Kafka → Mongo path is already exercised by
   `TransactionMessageKafkaIngestIntegrationSpec` and
   `ContentsHttpReadIntegrationSpec`. A new
   `EntityCreateKafkaMaterializeIntegrationSpec` is the narrowest
   end-to-end proof for the `ent` increment but adds ~120–150 lines.
   - **Default proposal:** include it (TASK-026 and TASK-027 each
     leaned on a focused integration spec).
   - **Alternative:** drop the integration spec; rely on the
     materializer unit tests plus the existing ingest spec. Cheaper
     diff but less end-to-end confidence.

If the director rules on items 1–6 in the next direction update, I can
proceed with the implementation turn directly. If any of these are
unclear or there is no time to rule, please advance with a "use the
default proposals" signal and I will follow them.

### Stay-in-scope check (this pre-work turn)

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` (a base owned path, this file).

It does **not** touch:

- the active task file or any other task file.
- `docs/agents/claude-1-changes.md` (reserved for the implementation
  report after the director advances the task).
- any source under `jade-tipi/`, `libraries/`, `clients/`,
  `frontend/`, `docker/`, or `docs/` other than this file.
- `DIRECTIVES.md`, `ORCHESTRATOR.md`, `AGENTS.md`, or
  `claude-1.md`.

Per the orchestrator protocol, I stop here and wait for the director
to set TASK-028 to `READY_FOR_IMPLEMENTATION` (or change the global
signal to `PROCEED_TO_IMPLEMENTATION`) before making any of the
implementation-turn changes listed in the plan above.

STATUS: AWAITING_DIRECTOR_REVIEW
