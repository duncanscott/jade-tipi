# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-029 — Human-readable Kafka entity-type submission path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-029-human-readable-kafka-entity-type-submission.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. TASK-029 continues the
Kafka-first domain write path with the smallest increment that lets a
human-readable Kafka transaction create a root-shaped bare entity-type
`typ` document from the existing `04-create-entity-type.json` example.
Acceptance criteria (paraphrased):

- Top-level `collection: "typ"`, `action: "create"`.
- **No** `data.kind: "link_type"` marker (entity-type vs link-type
  is distinguished by the absence of `data.kind`).
- `data.id` is the materialized type object ID.
- `data.name` and optional descriptive facts materialize under root
  `properties`.
- `data.links` is absent or empty (canonical relationships remain
  separate `lnk` messages).
- Preserve the current example ID strings (do not perform broad
  ID-abbreviation cleanup).
- Decide whether implementation materializes only bare entity-type
  `typ + create` while leaving `typ + update` property-reference
  changes deferred, or whether the property-reference update needs a
  separate follow-up task.
- Add or update example resource JSON only if the existing examples do
  not already show the accepted human-readable shape and sequence.
- Propose focused automated coverage: DTO validation, materialized
  root shape, skipped `typ + update` behaviour if deferred, idempotent
  duplicate handling, conflicting-duplicate handling, and the narrowest
  practical Kafka/Mongo integration check.
- Report exact verification commands and any local Docker/Kafka/Mongo/
  Gradle setup blockers.

Out of scope (per task file and `DIRECTIVES.md` TASK-029 Direction): no
HTTP submission endpoints, no property-assignment materialization, no
required-property enforcement, no semantic validation that
`ent.data.type_id` resolves, no permission enforcement, no object
extension pages, no contents-read changes, no broad ID-abbreviation
cleanup, no nested Kafka operation DSL, no endpoint projection
maintenance, no full Clarity/ESP import.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source
change is made until the director advances TASK-029 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` "Objects, Types, And Properties" and "Logical Objects
  And Physical Documents" prescribe `typ` as a long-term materialized
  collection alongside `loc`, `lnk`, `ent`, `grp`, `ppy`, etc., with
  the first materializer writing a root-document-only physical shape
  carrying explicit `properties`, denormalized `links`, and reserved
  `_head` metadata.
- `docs/architecture/kafka-transaction-message-vocabulary.md`:
  - "Types And Properties" (lines 104–121) shows the `typ + update`
    `add_property` example and notes the materialized type document
    "should eventually include property references". This is the
    `05-update-entity-type-add-property.json` shape.
  - "Entity Creation" (lines 123–136) shows the canonical entity
    shape `~en~plate_a` referencing `~ty~plate_96` (2-char ID
    segments).
  - "Link Types And Concrete Links" (lines 160–209) documents
    `data.kind: "link_type"` as the discriminator that distinguishes
    a link-type record from an entity-type record in `typ`.
  - Lines 259–260 currently state: "a post-commit projection currently
    materializes `loc + create`, `typ + create` (where
    `data.kind == "link_type"`), `lnk + create`, `ent + create`, and
    `grp + create`… Other collections, other actions, and bare
    entity-type `typ` records are intentionally not materialized in
    this iteration." This sentence is the live pin of today's
    accepted skip behaviour and **will need a one-line edit** in the
    implementation turn.
- TASK-013/TASK-014 (root-shaped contract), TASK-015 (root-shaped
  contents read), TASK-019 (Clarity/ESP container prototype),
  TASK-020 (root-shaped `grp`), TASK-026 (human-readable `loc`),
  TASK-027 (human-readable contents-link), and TASK-028 (human-readable
  `ent`) are all accepted; they define the materializer entry point
  and the wire conventions this task must reuse.
- `DIRECTIVES.md` TASK-029 Direction (lines 295–319) explicitly
  pins this task to bare entity-type `typ + create` only, leaving
  `typ + update` property-reference materialization deferred unless
  pre-work finds an unavoidable dependency.

### Current example resource state (read of source on `claude-1`)

`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `01-open-transaction.json` — `txn + open` with shared
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`. Sequence anchor.
- `09-commit-transaction.json` — `txn + commit` on the same txn uuid.
- `04-create-entity-type.json` — `typ + create` with
  `data.id = "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96"`,
  `data.name = "plate_96"`, `data.description = "96-well sample plate"`.
  **No `data.kind`.** **No `data.links`.** **No `data.properties`.**
  This already matches the directive's accepted human-readable
  bare-entity-type shape:
  - `collection: "typ"`, `action: "create"` — yes.
  - no `data.kind: "link_type"` — yes (absent entirely).
  - `data.id` set — yes.
  - `data.name` and a descriptive `data.description` at the flat
    `data` root — yes (these will materialize under root `properties`
    via the materializer's existing inline-properties fallback).
  - `data.links` absent — yes.
- `05-update-entity-type-add-property.json` — `typ + update`
  (`operation: "add_property"`). Skipped today as `update` action.
  TASK-029 explicitly leaves this unsupported unless pre-work finds
  an unavoidable dependency. None found (see "typ + update
  decision" below).
- `06-create-entity.json` — `ent + create` with
  `data.id = "…~ent~plate_a"`,
  `data.type_id = "…~ty~plate_96"` (3-char `~ent~` plus 2-char
  `~ty~` reference matching `04-…`'s ID), explicit empty
  `data.properties: {}` and `data.links: {}`. Post-TASK-028 shape.
- `07-…`, `08-…` — `ppy + create` property-assignment examples.
  Skipped today as unsupported collection. Out of scope.
- `10-…`, `11-…`, `12-…`, `13-…` — `loc + create`, contents-type
  `typ + create` (`data.kind == "link_type"`),
  `lnk + create`, `grp + create`. All accepted post-TASK-026/27/20.

**Conclusion:** the existing `04-create-entity-type.json` already
shows the accepted human-readable bare entity-type shape. **No
example file edit is required** to satisfy the directive's "data.id /
data.name / no data.kind / no data.links" criteria.

There is one optional symmetry question (see "Open questions" #1
below): TASK-026/27/28 each landed on an explicit `data.properties` /
`data.links` block at the wire layer for visibility. The bare
entity-type does not technically need either block — the directive
permits `data.links` absent and the materializer's inline-properties
fallback already lifts `name`/`description` into root `properties`.
Leaving `04-…` as-is is the smallest delta and stays consistent with
the directive's "Add or update example resource JSON only if the
existing examples do not already show the accepted shape" guidance.

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:

- `isSupported()` (lines 211–232) — accepts only `loc + create`,
  `lnk + create`, `grp + create`, `ent + create`, and `typ + create`
  where `data.kind == "link_type"` (lines 227–229). Bare entity-type
  `typ + create` (no `data.kind`, or any non-`link_type` value) and
  every `typ + update` / `typ + delete` falls through and is
  counted as `skippedUnsupported` without raising an error. This is
  exactly what the spec test
  `'skips a typ create whose data.kind is not link_type'` (line 460)
  asserts today using the `entityTypeMessage()` helper (lines
  107–119) — a `typ + create` with `id` and `name`, no `kind`.
- `extractDocId()` (lines 235–245) — already handles
  missing/blank `data.id` by returning `null`, which leads to
  `result.skippedInvalid++` and an error log without an exception.
- `buildDocument()` (lines 247–273) — for non-`lnk` supported
  collections, prefers `data.properties` when it is a Map and copies
  it verbatim into root `properties`; otherwise falls back to
  `buildInlineProperties(data)`, which copies every `data` entry
  except `id` and `type_id` into root `properties`. For the bare
  entity-type `04-…` payload this fallback yields
  `properties: {name: "plate_96", description: "96-well sample plate"}`
  and `links: {}`, exactly matching the directive's accepted
  materialized shape.
- `_head` / provenance (lines 292–310) — already populates
  `schema_version`, `document_kind: "root"`, `root_id`, and a
  `provenance` sub-document with `txn_id`, `commit_id`, `msg_uuid`,
  `collection: "typ"`, `action: "create"`, `committed_at`,
  `materialized_at`. Reused verbatim for the bare entity-type root.
- Duplicate-key handling (lines 181–209) — supported via
  `findById` plus `isSamePayload()` ignoring only
  `_head.provenance.materialized_at`. Bare entity-type inserts
  inherit the same idempotency for free once `isSupported()` accepts
  them.
- Constants (lines 104–105) — `DATA_KIND = 'kind'` and
  `LINK_TYPE_KIND = 'link_type'` are referenced **only** by the
  `case COLLECTION_TYP` arm of `isSupported()`. Once that arm becomes
  a flat `return true`, both constants become unused inside this
  class. `ContentsLinkReadService` declares its own private
  `LINK_TYPE_KIND` constant (line 85), so removing the
  materializer-side constants does not touch the read path.
- Class Javadoc (lines 36–38) currently states "Bare entity-type
  `typ` records are intentionally skipped here." — this sentence
  must be inverted/removed in the implementation turn.

**Implication:** the smallest implementation change is to drop the
`data.kind == "link_type"` guard from the `case COLLECTION_TYP` arm
of `isSupported()` (i.e., make it `return true` like `loc`, `lnk`,
`grp`, `ent`). No other materializer code path needs to change for
bare entity-type `typ + create` to materialize as a root-shaped
document with the directive's accepted properties/links/`_head`
shape.

### Current automated coverage (read of source on `claude-1`)

DTO library:

- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:
  - `EXAMPLE_PATHS` (lines 24–38) loops every example resource
    through `JsonMapper` round-trip and `Message.validate()` schema
    validation; `04-…` and `05-…` are both in the loop today.
  - `'entity transaction example sequence shares one txn id and the
     entity references the entity-type by id'` (lines 484–515) reads
    `01-…`, `04-…`, `06-…`, `09-…` and proves the txn-uuid chain
    plus `entCreate.data.type_id == typCreate.data.id`. This already
    covers the entity-type ↔ entity sequence at the DTO layer; it
    remains correct after TASK-029.
  - There is **no focused feature** yet asserting the bare
    entity-type shape on `04-…` itself (no `data.kind`, flat
    `data.name`, optional `data.description`). The contents-type
    spec (lines 517–552) and the entity spec (lines 460–482)
    each have an analogous focused feature; the bare entity-type
    does not.

Backend unit:

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`:
  - `entityTypeMessage()` helper (lines 107–119) builds a bare
    entity-type `typ + create` with `data.id = "…~typ~plate_96"`
    (3-char `~typ~`) and `data.name = "plate_96"`, no `data.kind`.
  - `'skips a typ create whose data.kind is not link_type'`
    (lines 460–468) currently pins the skip-behavior contract:
    `materialized == 0`, `skippedUnsupported == 1`,
    `0 * mongoTemplate.insert(_, _)`. **This feature flips into a
    positive materialize feature** in the implementation turn.
  - `'skips update and delete actions on supported collections'`
    (line 904) covers `loc + update` skip; an analogous spec or
    `where:` extension is the natural home for a `typ + update`
    skip assertion.
  - `'mixed-message snapshot inserts in snapshot order with correct
     counts'` (lines 1127–1163) currently builds a snapshot
    `[loc, ppy, link-type-typ, ent, lnk]` with insert order
    `['loc', 'typ', 'ent', 'lnk']`, `materialized == 4`,
    `skippedUnsupported == 1`. After TASK-029 the snapshot can be
    extended with a bare entity-type create to prove both `typ`
    kinds materialize and the count moves to `materialized == 5`,
    `skippedUnsupported == 1`. Alternatively a dedicated mixed
    feature can cover both-kind co-presence and leave the existing
    feature unchanged.

Backend integration (opt-in, gated on `JADETIPI_IT_KAFKA=1` + Kafka
reachable):

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`
  publishes `open + ent + commit` and asserts the materialized
  `ent` root. It does **not** publish `04-…` ahead of `06-…`
  today; the entity references a `data.type_id` that isn't actually
  materialized in the test — semantic resolution is deliberately
  out of scope.
- `TransactionMessageKafkaIngestIntegrationSpec` exercises the
  ingest header/message persist path generically and is unaffected.

### Schema status (`message.schema.json`)

For `collection != "grp"`, `data` follows `SnakeCaseObject`, which
permits any snake_case-keyed object recursively. The `04-…` example
already validates today (covered by the EXAMPLE_PATHS loop in
`MessageSpec`). Tightening the schema to require `typ.data.id` or
`typ.data.name`, or to forbid unknown fields, would be a semantic
change; per the task's `OUT_OF_SCOPE` (no semantic reference
validation, no required-property enforcement) **no schema-file change
is required or in-scope** for this task. Same as TASK-026/27/28.

### `typ + update` decision

The directive asks: should the implementation also materialize
`typ + update` property-reference changes (the
`05-update-entity-type-add-property.json` shape), or defer that to
a separate follow-up task?

**Recommendation: defer.**

Rationale:

1. The materializer today has **no `update`-action code path** for
   any collection. `loc + update` is explicitly skipped by the
   existing test at line 904. Adding `update` semantics requires a
   new path (read-existing → mutate → save with optimistic-version
   handling) that does not exist for any other collection. That is
   a significantly broader change than the bare-create increment
   TASK-029 is shaped around.
2. `typ + update add_property` is also semantically richer than a
   field-set update: it implies a property-reference list inside
   the materialized type root. The vocabulary doc explicitly notes
   "the materialized type document should eventually include
   property references, not embedded property definitions" — the
   property-reference shape on the materialized root is itself an
   open product question.
3. The existing skip behaviour (every `update` action, including
   `typ + update`, counts as `skippedUnsupported`) is preserved
   transparently if we change only the `case COLLECTION_TYP` create
   arm. No regression risk.
4. The task file's `ACCEPTANCE_CRITERIA` permits this: "Decide
   whether the implementation should materialize only bare
   entity-type `typ + create` while leaving `typ + update`
   property-reference changes unsupported, or whether the
   property-reference update needs a separate follow-up task."
5. The task's `PREWORK_REQUIREMENTS` includes "Identify the
   smallest implementation/test changes needed for examples, DTO
   tests, materializer coverage, and integration verification"
   — deferral is strictly smaller than inclusion.

I recommend a follow-on task (e.g., `TASK-030 - Plan typ + update
property-reference materialization`) be created after TASK-029 is
accepted, scoped to define the materialized property-reference
shape and an `update`-action materializer path. **No new task is
created from this pre-work turn.**

### Smallest implementation plan

Goal: smallest set of changes that proves the existing human-readable
bare-entity-type `typ + create` example materializes into a
root-shaped `typ` document with `_id`, `id`, `collection: "typ"`,
top-level `type_id` (null), root `properties: {name, description}`,
empty `links: {}`, and `_head` fields, while preserving idempotency,
`typ + update` skip behaviour, and the existing
`data.kind == "link_type"` link-type materialization.

#### File changes

1. **No example resource edits in
   `libraries/jade-tipi-dto/src/main/resources/example/message/`.**
   The existing `04-create-entity-type.json` already shows the
   accepted human-readable bare-entity-type shape (no `data.kind`,
   flat `data.name`/`data.description`, no `data.links`). The
   existing `05-update-entity-type-add-property.json` is also
   unchanged; it remains the canonical example for the deferred
   `typ + update` follow-up.
   - **Optional symmetry edit** (see Open question #1): add explicit
     empty `data.properties: {}` and `data.links: {}` blocks to
     `04-…` to mirror the `loc`/`ent`/`grp` post-task shape. Default
     proposal is to skip this; flag for director ruling.

2. **Materializer code change (in
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`):**
   - Replace lines 227–229
     ```
     case COLLECTION_TYP:
         Object kind = message.data?.get(DATA_KIND)
         return LINK_TYPE_KIND == kind
     ```
     with
     ```
     case COLLECTION_TYP:
         return true
     ```
   - **Default proposal:** also remove the now-dead constants
     `DATA_KIND` and `LINK_TYPE_KIND` (lines 104–105). They are
     used only in the replaced block. `ContentsLinkReadService`
     keeps its own private `LINK_TYPE_KIND` constant unaffected.
     **Alternative:** keep both constants in the materializer for
     future reference. See Open question #2.
   - Update the class Javadoc (lines 36–38) to read approximately:
     ```
     <li>{@code typ + create} → {@code typ} collection. Both
         link-type ({@code data.kind == "link_type"}) and bare
         entity-type ({@code data.kind} absent) records materialize
         as root-shaped {@code typ} documents; the materializer
         does not enforce a kind discriminator.</li>
     ```
     Remove "Bare entity-type `typ` records are intentionally
     skipped here." Keep the "every other collection/action
     combination — including update, delete, and txn-control
     actions — is counted as `skippedUnsupported`" sentence
     unchanged.
   - **No other code change** is required. `buildDocument()`
     already routes `typ` through the inline-properties fallback
     when `data.properties` is absent, yielding `properties:
     {name, description}` and `links: {}` exactly as the directive
     specifies. The existing `lnk` carve-out at line 258 is
     untouched. Duplicate-key handling, head/provenance, and
     missing-id handling all apply transparently.

3. **Backend unit tests (in
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`):**
   - **Replace** `'skips a typ create whose data.kind is not
     link_type'` (lines 460–468) with a positive feature
     `'materializes a bare entity-type typ create as a root document
      with name/description under properties and null type_id'`:
     - Capture `mongoTemplate.insert(_, 'typ')`.
     - Snapshot `[entityTypeMessage()]`.
     - Assert: `result.materialized == 1`,
       `result.skippedUnsupported == 0`,
       `captured._id` and `captured.id` equal the entity-type ID,
       `captured.collection == 'typ'`,
       `captured.type_id == null`,
       `(captured.properties as Map).name == 'plate_96'`,
       `(captured.properties as Map).description ==
        '96-well sample plate'` (use the value from the helper —
       today the helper omits description; extend the helper or
       provide an in-feature override),
       `!captured.properties.containsKey('id')`,
       `!captured.properties.containsKey('type_id')`,
       `!captured.properties.containsKey('kind')`,
       `captured.links == [:]`,
       `(captured._head as Map).provenance.collection == 'typ'`,
       `(captured._head as Map).provenance.action == 'create'`.
   - **Extend the `entityTypeMessage()` helper** (lines 107–119)
     to include `description: '96-well sample plate'` for symmetry
     with the `04-…` example, or accept a `dataOverrides` map like
     the other helpers. This change is local to the spec's helper.
   - **Add** a feature
     `'preserves link-type typ + create materialization for
       data.kind == "link_type" after dropping the kind guard'`
     OR rely on the existing
     `'materializes a typ link-type create…'` feature (line 415)
     to prove the link-type path still materializes correctly.
     Default proposal: rely on the existing feature; no new test
     is required for the kept-behavior case.
   - **Add** a feature
     `'skips a typ + update message even after dropping the kind
       guard'` mirroring the existing
     `'skips update and delete actions on supported collections'`
     feature at line 904. Builds an `updateEntityTypeMessage()`
     local helper from `05-update-entity-type-add-property.json`'s
     shape (`collection: 'typ'`, `action: 'update'`, `data:
     [id: …, operation: 'add_property', property_id: …, required:
     true]`) and asserts `result.skippedUnsupported == 1`,
     `result.materialized == 0`, `0 * mongoTemplate.insert(_, _)`.
   - **Add** an idempotent-duplicate feature
     `'identical-payload entity-type typ duplicate is matching
       even when materialized_at differs'` mirroring the existing
     `ent` and `loc` duplicate-matching features (lines 826–862,
     915–958).
   - **Add** a conflicting-duplicate feature
     `'differing-payload entity-type typ duplicate is conflicting
       and not overwritten'` mirroring the `ent` and `loc`
     conflicting-duplicate features (lines 864–902, 960–1003).
   - **Update** the mixed-message snapshot feature (lines
     1127–1163) to also include an entity-type `typ + create`,
     yielding insert order
     `['loc', 'typ' (link-type), 'typ' (entity-type), 'ent', 'lnk']`
     and counts `materialized == 5`, `skippedUnsupported == 1`
     (only `ppy` skips). Alternatively leave the existing feature
     unchanged and add a new
     `'mixed entity-flow snapshot materializes link-type and
       bare-entity-type typ records together with ent'` feature.
     Default proposal: extend the existing feature in place.

4. **DTO library tests (in
   `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`):**
   - **Add** a Spock feature
     `'bare entity-type typ create example uses the human-readable
       data.id, data.name, optional data.description, and no
       data.kind/data.links shape'`:
     - Reads `04-create-entity-type.json`.
     - Asserts `message.collection() == Collection.TYPE`,
       `message.action() == Action.CREATE`,
       `data.id` ends with `~ty~plate_96`,
       `data.name == 'plate_96'`,
       `data.description == '96-well sample plate'`,
       `!data.containsKey('kind')`,
       `!data.containsKey('links')`,
       `data.keySet() == ['id', 'name', 'description'] as Set`.
   - The existing
     `'entity transaction example sequence shares one txn id and
       the entity references the entity-type by id'` feature
     (lines 484–515) already proves the `04-…` ↔ `06-…` cross-
     reference. **No additional cross-reference test is needed.**

5. **Backend integration test (in
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/`):**
   - **Default proposal:** extend
     `EntityCreateKafkaMaterializeIntegrationSpec.groovy` to
     publish `open + 04-typ + 06-ent + commit` instead of
     `open + 06-ent + commit`, and add an additional assertion
     waiting for the materialized entity-type `typ` root before
     asserting the materialized `ent` root. This proves end-to-end
     that the bare entity-type Kafka payload lands as a root-shaped
     `typ` document in MongoDB and that the entity's
     `data.type_id` references an actually-materialized entity-type
     record. The spec stays opt-in
     (`JADETIPI_IT_KAFKA=1` + Kafka reachable).
   - **Alternative:** add a dedicated
     `EntityTypeCreateKafkaMaterializeIntegrationSpec.groovy`
     for the bare entity-type case alone, and leave the existing
     `EntityCreateKafkaMaterializeIntegrationSpec` unchanged. This
     is cleaner spec-by-collection but adds ~120–150 lines of
     boilerplate that overlap heavily with the existing entity
     spec.
   - **If director prefers no integration change** because the
     `Kafka → Mongo` path is already proven by the entity spec and
     the materializer is unit-tested above, drop step 5 and rely
     on the unit-level coverage. Flagging here so the choice is
     explicit. See Open question #3.

6. **Documentation updates:**
   - `docs/architecture/kafka-transaction-message-vocabulary.md`
     line 259 currently states "a post-commit projection currently
     materializes `loc + create`, `typ + create` (where
     `data.kind == "link_type"`), `lnk + create`, `ent + create`,
     and `grp + create`… Other collections, other actions, and
     bare entity-type `typ` records are intentionally not
     materialized in this iteration." Replace with approximately:
     "a post-commit projection currently materializes
     `loc + create`, `typ + create` (both link-type and bare
     entity-type), `lnk + create`, `ent + create`, and
     `grp + create`. Other collections, other actions —
     including `typ + update` property-reference changes — are
     intentionally not materialized in this iteration."
   - `docs/OVERVIEW.md` — no change anticipated; it does not pin
     the entity-type discriminator behavior.
   - `docs/architecture/jade-tipi-object-model-design-brief.md` —
     no change anticipated; the brief describes the logical model
     and is agnostic to the materializer's `kind` filtering.

#### Expected total surface (with default proposals selected)

- **0 example resource edits.**
- ~3 lines of materializer code change (drop kind guard) + 2 lines
  of constants removal + ~6 lines of Javadoc edit ≈ **≤ 12
  source lines** in `CommittedTransactionMaterializer.groovy`.
- ~80–110 lines of new/changed Spock features in the materializer
  spec (1 feature flipped, 3 new features, 1 feature extended,
  1 helper extended).
- ~25–40 lines of new feature in `MessageSpec`.
- Extended `EntityCreateKafkaMaterializeIntegrationSpec` adds
  ~30–50 lines (one extra publish step, one extra wait/assert
  block).
- ~3 lines of doc edit in
  `kafka-transaction-message-vocabulary.md`.

#### Out-of-scope guardrails (will **not** edit unless director ruling
expands scope)

- `clients/kafka-kli/**` — kli already accepts `--collection typ`
  and passes `data` through unchanged; no CLI change is needed.
- `frontend/**` — no UI surface for raw entity-type submission.
- `jade-tipi/src/main/groovy/.../service/ContentsLinkReadService.groovy`,
  `TransactionService.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy` — they treat `data`
  as opaque or do not interact with bare entity-type `typ` records;
  no change needed.
- `message.schema.json` — see Schema status above.
- `frontend/.env.local` — generated; never hand-edited (per
  `CLAUDE.md`).
- `05-update-entity-type-add-property.json` — `typ + update`
  materialization is explicitly deferred per the decision above.
- `07-…`, `08-…` — `ppy + create` property-assignment
  materialization remains out of scope.
- `06-create-entity.json` — TASK-028 already landed the accepted
  shape; do not edit.

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    entity-type-flow examples plus the new focused feature in
#    MessageSpec.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new bare-entity-type root shape, idempotent duplicate,
#    conflicting duplicate, typ + update skip, and updated
#    mixed-snapshot features.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running). The extended
#    EntityCreateKafkaMaterializeIntegrationSpec drives an end-to-end
#    open + 04-typ + 06-ent + commit -> materialized typ + ent roots
#    proof. The existing contents and ingest specs are also rerun to
#    confirm no regression.
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'

# Optional regression checks:
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*ContentsHttpReadIntegrationSpec*'
```

Setup commands (per `CLAUDE.md` "Tooling Refresh"; if local tooling
is missing they are reported, not treated as product blockers):

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
  before retrying (per `DIRECTIVES.md` TASK-026/27/28 review notes).
- If integration tests cannot run because Docker is not available
  in the sandbox, report the exact `docker compose ... up` command
  and stop rather than treating it as a product blocker.

### Open questions / blockers (for director review)

These are product-judgment questions that match the directive's
"If the ID convention or type contract needs product judgment, stop
with `STATUS: HUMAN_REQUIRED` rather than guessing." I'm flagging
them here in pre-work rather than stopping, so the director can rule
once and then advance the task. If the director prefers I stop with
`STATUS: HUMAN_REQUIRED` on any of these instead of proposing a
default, please redirect.

1. **Optional explicit `data.properties: {}` / `data.links: {}`
   blocks on `04-create-entity-type.json`.** The existing example
   uses flat `data.name`/`data.description` with no explicit
   `data.properties` or `data.links` block. The materializer's
   inline-properties fallback already lifts `name`/`description`
   into root `properties`, so the existing example fully satisfies
   the directive. TASK-026/27/28 each landed an explicit
   `data.properties` / `data.links` block on the canonical example
   for visibility.
   - **Default proposal:** leave `04-…` as-is (smallest delta;
     directive permits absent `data.links` and the materializer
     handles flat `data.name`/`description` correctly).
   - **Alternative:** add explicit
     `"properties": {"name": "plate_96", "description":
     "96-well sample plate"}` (and remove `name`/`description` from
     the flat root) and `"links": {}` to mirror the `loc`/`ent`
     wire shape. Larger delta and a small wire-shape change for
     human consumers, but consistent with the recently-accepted
     pattern.

2. **Removing the now-dead constants `DATA_KIND` and
   `LINK_TYPE_KIND` from the materializer.** After the
   `case COLLECTION_TYP` arm becomes a flat `return true`, both
   constants become unused inside the materializer class.
   - **Default proposal:** remove them (per the project's
     "no half-finished implementations" / no-dead-code preference).
     `ContentsLinkReadService` keeps its own private
     `LINK_TYPE_KIND` declaration unaffected.
   - **Alternative:** leave them for future reference. Two-line
     diff difference; no behavior impact.

3. **Integration spec — extend
   `EntityCreateKafkaMaterializeIntegrationSpec`, add a new
   `EntityTypeCreateKafkaMaterializeIntegrationSpec`, or skip
   integration coverage altogether?**
   - **Default proposal:** extend the existing entity spec to
     publish `04-…` ahead of `06-…`. Smallest delta, narrowest
     end-to-end proof, single boilerplate copy.
   - **Alternative A:** add a dedicated
     `EntityTypeCreateKafkaMaterializeIntegrationSpec`. Cleaner
     per-collection but ~120–150 lines of boilerplate duplicated.
   - **Alternative B:** rely on the materializer unit tests plus
     the existing ingest spec, do not change integration coverage.
     Cheapest diff but loses the end-to-end Kafka → Mongo bare
     entity-type proof.

4. **Mixed-message snapshot test — extend in place or add a new
   feature?** The current feature already covers
   `[loc, ppy (skip), typ (link-type), ent, lnk]`. Adding the bare
   entity-type means inserting a 6th message and updating the
   counts.
   - **Default proposal:** extend in place to keep one canonical
     mixed-snapshot fixture.
   - **Alternative:** keep the existing feature unchanged and add
     a new `'mixed entity-flow snapshot materializes link-type and
     bare-entity-type typ together'` feature.

5. **Follow-up task creation for `typ + update`
   property-reference materialization.** The decision above defers
   `typ + update` to a future task. Should the director create that
   follow-on task (`TASK-030 - Plan typ + update property-reference
   materialization` or similar) now during TASK-029 acceptance, or
   wait until after the bare-entity-type implementation lands?
   - **Default proposal:** wait until TASK-029 is accepted; create
     the follow-on then. Mirrors the
     `TASK-026 → TASK-027 → TASK-028 → TASK-029` rhythm.

6. **ID-segment cleanup (`~ty~` vs `~typ~`).** The `04-…` example
   ID uses `~ty~plate_96` (2-char), and `06-…` references it
   verbatim. The contents-type `11-…` example uses `~typ~contents`
   (3-char). The directive explicitly forbids broad ID-abbreviation
   cleanup for this task and says to "Preserve the current example
   ID strings." I am preserving both verbatim. **No action needed
   from the director here** — flagged only for visibility / future
   follow-up after the bounded entity/type write path is complete.

If the director rules on items 1–5 in the next direction update, I
can proceed with the implementation turn directly. If any of these
are unclear or there is no time to rule, please advance with a "use
the default proposals" signal and I will follow them.

### Stay-in-scope check (this pre-work turn)

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` (a base owned path, this
  file).

It does **not** touch:

- the active task file or any other task file.
- `docs/agents/claude-1-changes.md` (reserved for the implementation
  report after the director advances the task).
- any source under `jade-tipi/`, `libraries/`, `clients/`,
  `frontend/`, `docker/`, or `docs/` other than this file.
- `DIRECTIVES.md`, `ORCHESTRATOR.md`, `AGENTS.md`, or
  `claude-1.md`.

Per the orchestrator protocol, I stop here and wait for the director
to set TASK-029 to `READY_FOR_IMPLEMENTATION` (or change the global
signal to `PROCEED_TO_IMPLEMENTATION`) before making any of the
implementation-turn changes listed in the plan above.

STATUS: AWAITING_DIRECTOR_REVIEW
