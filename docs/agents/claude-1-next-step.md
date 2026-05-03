# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-030 — Human-readable Kafka entity-type property-reference update path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-030-human-readable-kafka-entity-type-property-reference-update.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. TASK-030 is the deferred
follow-on to accepted TASK-029 (bare entity-type `typ + create`). It scopes
the smallest Kafka-first increment that lets a human-readable
`typ + update` `operation: "add_property"` message attach a property
reference to an existing root-shaped bare entity-type `typ` document.

Acceptance criteria (paraphrased from the task file):

- Preserve Kafka as the primary submission route (no HTTP data submission).
- Inspect `02-…`, `03-…`, `04-…`, `05-…`, the DTO validation, the
  `CommittedTransactionMaterializer` `typ + update` skip behaviour, and the
  current Mongo update/duplicate handling around materialized roots before
  planning.
- The intended human-readable wire shape is already canonicalized in
  `05-update-entity-type-add-property.json`: top-level `collection: "typ"`,
  `action: "update"`, `data.id` (target type ID), `data.operation:
  "add_property"`, `data.property_id` (referenced property-definition ID),
  optional `data.required` (reference metadata).
- Preserve existing example IDs (no broad ID-abbreviation cleanup).
- Decide the smallest materialized root shape for property references on a
  `typ` document — under root `properties`, root `links`, or another
  already-documented root field. Reference-only; no embedded property
  definition.
- Decide whether `ppy + create` materialization or semantic `property_id`
  resolution is required for this bounded proof, or whether `typ + update`
  may record the reference without resolution.
- Decide materializer behaviour for the missing-target `typ` root, the
  same property reference added twice (idempotency), and a reference whose
  metadata (e.g. `required`) conflicts with the existing entry.
- Add or update example resource JSON only if existing examples do not
  already show the accepted shape and sequence.
- Propose focused automated coverage: DTO validation, materialized root
  update shape, skipped unsupported `typ + update` operations,
  missing-target behaviour, idempotent repeated property-reference
  handling, conflicting property-reference handling, and the narrowest
  practical Kafka/Mongo integration check.
- Report exact verification commands and any local Docker/Kafka/Mongo/
  Gradle setup blockers.

Out of scope (per task file `OUT_OF_SCOPE` and `DIRECTIVES.md` TASK-030
Direction):

- No HTTP submission endpoints.
- No property-value assignment materialization, required-property
  enforcement, or semantic validation that `data.property_id` resolves.
- No permission enforcement, object extension pages, endpoint projection
  maintenance, full Clarity/ESP import, broad ID-abbreviation cleanup, or
  nested Kafka operation DSL.
- No changes to contents-link read semantics, entity materialization,
  location materialization, or group/admin behavior except where pre-work
  identifies a direct TASK-030 dependency. (None identified — see
  "Cross-cutting impact" below.)

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source
change is made until the director advances TASK-030 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` (this branch):
  - "Objects, Types, And Properties" (lines 17–32): a type defines which
    properties may be assigned to objects of that type; "a property must
    be added to the type before clients may assign that property to an
    object of the type." This is the human-language root motivation for
    the `typ + update add_property` shape: it adds a property reference to
    the type definition, not a value assignment to an instance.
  - "Logical Objects And Physical Documents" (lines 58–101): the root
    document holds object identity, `collection`, `type_id`, "small
    `properties` and `links` maps when they fit, and reserved
    implementation metadata." Property and link maps "should be keyed by
    the IDs of the property or link objects" (line 83–84). Extension
    pages exist only as future work; the first iteration uses the
    root-only case.
- `docs/architecture/kafka-transaction-message-vocabulary.md`:
  - "Types And Properties" (lines 104–121) shows the canonical
    `typ + update add_property` example exactly as it appears in
    `05-update-entity-type-add-property.json` and notes: "The materialized
    type document should eventually include property references, not
    embedded property definitions." TASK-030 is the increment that takes
    that "eventually" off the page.
  - Lines 257–263 currently document the post-commit projection set
    (`loc + create`, both `typ + create` kinds, `lnk + create`,
    `ent + create`, `grp + create`) and explicitly state: "Other
    collections, other actions — including `typ + update`
    property-reference changes — are intentionally not materialized in
    this iteration." This sentence is the live pin of today's accepted
    skip behaviour and **must be edited** in the implementation turn.
- `docs/architecture/jade-tipi-object-model-design-brief.md`: "A type
  definition declares the properties that may be assigned to objects of
  that type. For now, avoid required/optional property complexity and
  avoid default values. A property may be assigned only after the
  object's type definition permits that property." (Lines 13–17.) The
  design brief de-emphasizes `required` semantics; TASK-030 should
  preserve `required` as opaque reference metadata only, not enforce it.
- `DIRECTIVES.md` TASK-030 Direction (lines 303–325) explicitly pins the
  task to the `typ + update add_property` path only and forbids semantic
  `property_id` resolution, broad ID-abbreviation cleanup, HTTP
  submission, property-value assignment materialization, required-property
  enforcement, contents-link read changes, and a nested Kafka operation
  DSL.
- TASK-013/TASK-014 (root-shaped contract), TASK-015 (root-shaped
  contents read), TASK-019 (Clarity/ESP container prototype),
  TASK-020 (root-shaped `grp`), TASK-026 (human-readable `loc`),
  TASK-027 (human-readable contents-link), TASK-028 (human-readable
  `ent`), and TASK-029 (bare entity-type `typ + create`) are all
  accepted. They define the materializer entry point, the wire
  conventions, and the `_head.provenance` reserved root metadata that
  this task must reuse.

### Current example resource state (read of source on `claude-1`)

`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `01-open-transaction.json` — `txn + open` with shared
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`. Sequence anchor.
- `02-create-property-definition-text.json` — `ppy + create`
  (`data.kind = "definition"`,
  `data.id = "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode"`,
  `data.name = "barcode"`,
  `data.value_schema = {type: object, required: [text], properties:
   {text: {type: string}}}`). Note the 2-char `~pp~` ID segment, not
  `~ppy~`. The `ppy` collection is **not currently materialized** by
  `CommittedTransactionMaterializer`; this remains out of scope per the
  TASK-030 directive (no semantic `property_id` resolution).
- `03-create-property-definition-numeric.json` — `ppy + create` for the
  `volume` property; same 2-char `~pp~` segment. Same
  not-currently-materialized status as `02-…`.
- `04-create-entity-type.json` — `typ + create` (bare entity-type) with
  `data.id = "…~ty~plate_96"`, `data.name = "plate_96"`,
  `data.description = "96-well sample plate"`. No `data.kind`. No
  `data.links`. Now accepted as TASK-029.
- `05-update-entity-type-add-property.json` — `typ + update` with
  `data.id = "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96"`
  (matches `04-…`'s `data.id` verbatim),
  `data.operation = "add_property"`,
  `data.property_id = "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode"`
  (matches `02-…`'s `data.id` verbatim),
  `data.required = true`. **This is exactly the directive's accepted
  human-readable wire shape.** No file edit is required.
- `06-create-entity.json` — `ent + create`. Already accepted (TASK-028).
- `07-…`, `08-…` — `ppy + create` property-assignment examples
  (not the same as a `ppy` definition). Skipped today as unsupported
  collection. Out of scope for TASK-030.
- `09-commit-transaction.json` — `txn + commit` on the same txn uuid.
- `10-…`, `11-…`, `12-…`, `13-…` — `loc`, contents-type `typ`
  (`data.kind == "link_type"`), `lnk`, `grp`. All accepted post
  TASK-026/27/20.

**Conclusion:** the existing `05-update-entity-type-add-property.json`
already shows the accepted human-readable wire shape (top-level
`collection: "typ"` + `action: "update"`, with the four `data` fields
listed in the directive). **No example file edit is required** to satisfy
the directive's "human-readable shape" criterion. The cross-message
references (`05-….data.id == 04-….data.id`,
`05-….data.property_id == 02-….data.id`) are already correct verbatim
and will be exercised by the new DTO co-presence test (see Plan §4
below).

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
on this branch (post-TASK-029):

- `isSupported()` (lines 212–233) — accepts only `loc`, `lnk`, `grp`,
  `ent`, and `typ` paired with `action == ACTION_CREATE` (line 216:
  `if (message.action != ACTION_CREATE) return false`). **Every `update`
  action — including `typ + update add_property` — falls through the
  early return and is counted as `skippedUnsupported` without raising an
  error.**
- The unit spec
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  pins this behaviour today:
  - `updateEntityTypeMessage()` helper (lines 125–139) builds the exact
    wire shape from `05-…`: `collection: 'typ'`, `action: 'update'`,
    `data: [id: ENT_TYPE_ID, operation: 'add_property',
     property_id: '…~ppy~barcode', required: true]`. (Helper uses
    `~ppy~` 3-char; `05-…` JSON uses `~pp~` 2-char. Both are accepted
    today — neither is materialized — but the spec helper and the JSON
    example currently disagree on segment length. **This is a small
    inconsistency, not a blocker; flagged in Open question #6 below.**
    The helper continues to work because nothing in production resolves
    the ID semantically.)
  - `'skips a typ + update message even after dropping the kind guard'`
    (lines 561–570) currently asserts `materialized == 0`,
    `skippedUnsupported == 1`, `0 * mongoTemplate.insert(_, _)` for
    that helper. **This feature flips into a positive materialize
    feature** in the implementation turn (or is replaced with several
    new positive-and-negative features; see Plan §3).
  - `updateLocationMessage()` helper (lines 217–226) and feature
    `'skips update and delete actions on supported collections'` (line
    1090) preserve `loc + update` skip behavior. TASK-030 must keep
    that behavior unchanged for `loc + update`, `lnk + update`,
    `grp + update`, `ent + update`, every `*+ delete`, and any
    `typ + update` whose `data.operation` is not `add_property`.
- The materializer has **no `update`-action code path for any
  collection today**. Every supported message goes through `insert(doc,
  collection)` (line 170). The duplicate-key handler (lines 182–210)
  uses `mongoTemplate.findById` plus `isSamePayload()` after a Mongo
  `DuplicateKeyException` — that flow is for `insert` retries, not for
  partial document updates.
- Other Mongo update precedent in the project:
  - `TransactionMessagePersistenceService` line 192:
    `mongoTemplate.updateFirst(query, update, COLLECTION_NAME)` with
    `org.springframework.data.mongodb.core.query.Update#set(...)`.
  - `GroupAdminService` line 234: `mongoTemplate.findAndModify(query,
    mongoUpdate, Map.class, COLLECTION_GRP)` with `.set(FIELD_PROPERTIES,
    properties)` plus `.set(FIELD_HEAD + '.' + HEAD_PROVENANCE,
    provenance)` etc.
  Both patterns are well-precedented and use Spring Data's `Update.set`
  for partial sets (including dotted-path sets into sub-documents). The
  smallest TASK-030 implementation can reuse the same idioms.
- `MaterializeResult` (separate file `MaterializeResult.groovy`) carries
  the existing counters: `materialized`, `duplicateMatching`,
  `conflictingDuplicate`, `skippedUnsupported`, `skippedInvalid`. **No
  counter exists today for "supported message whose target root does not
  yet exist."** See Open question #2 for whether to add a new counter or
  reuse one of the existing two skip counters.

### Current automated coverage (read of source on `claude-1`)

DTO library (`MessageSpec.groovy`):

- `EXAMPLE_PATHS` (lines 24–38) round-trips and schema-validates every
  example resource, including `05-…`. `05-…` already passes today.
- There is **no focused feature** yet that asserts `05-…`'s wire shape
  beyond the round-trip / schema-validate loop. The contents-type
  (lines 330–352), bare entity-type (460–483), entity (485–507), and
  group (354–376) examples each have an analogous focused feature; the
  type-update-add-property example does not.
- The "entity transaction example sequence" feature (lines 509–540)
  proves the `01 → 04 → 06 → 09` chain. There is **no analogous
  feature** today proving the `01 → 02 → 04 → 05 → 09` chain (or a
  focused subset proving `04 → 05` cross-references on `data.id` and
  `05 → 02` cross-references on `data.property_id`).

Backend unit (`CommittedTransactionMaterializerSpec.groovy`):

- `updateEntityTypeMessage()` helper (lines 125–139) — already wires
  the exact `data.operation == 'add_property'` shape. Reusable for the
  new positive features.
- `entityTypeMessage()` helper (lines 105–123) — already builds the
  bare entity-type root that the property reference will land on.
  Reusable as the "preceding root" fixture for missing-target /
  idempotent / conflicting features.
- `'skips a typ + update message even after dropping the kind guard'`
  (line 561) — flipped or replaced in implementation turn.
- `'skips update and delete actions on supported collections'`
  (line 1090) — kept as-is; covers `loc + update`. TASK-030 must add
  parallel coverage proving non-`add_property` `typ + update`
  operations and `typ + delete` still skip.
- The mixed-snapshot feature (line 1131 area, with insert order
  `['loc', 'typ', 'typ', 'ent', 'lnk']` and counts `materialized == 5`,
  `skippedUnsupported == 1` after TASK-029 extension) currently does
  **not** include any `typ + update`. TASK-030 may extend it again to
  prove order/count preservation when both `typ + create` (bare and
  link-type) and `typ + update add_property` co-occur in one snapshot.

Backend integration (opt-in):

- `EntityCreateKafkaMaterializeIntegrationSpec` publishes
  `open + 04-typ + 06-ent + commit` and asserts both materialized
  roots. **It does not currently exercise `typ + update`.**
  TASK-030 either extends it (default proposal, smallest delta) or adds
  a dedicated spec.
- `TransactionMessageKafkaIngestIntegrationSpec` is generic and
  unaffected.

### Schema status (`message.schema.json`)

For `collection != "grp"`, `data` follows `SnakeCaseObject`. The `05-…`
example already validates today (covered by the EXAMPLE_PATHS loop).
The directive's accepted wire shape is already permitted (no schema
edit needed):

- `data.operation` is a snake_case-keyed string value ("add_property") —
  permitted.
- `data.property_id` is a snake_case-keyed string ID — permitted.
- `data.required` is a boolean — permitted by `SnakeCaseValue`.
- `data.id` is the existing target ID — permitted.

Tightening the schema to require `typ.data.operation` for `update`
actions, or to enumerate the allowed `data.operation` values, would be
a real semantic schema change. Per TASK-030's `OUT_OF_SCOPE` (no
semantic reference validation, no required-property enforcement) **no
schema-file change is required or in-scope** for this task.

### Decisions required by the directive

#### 1. Smallest materialized root shape for property references (the central design choice)

The directive lists three placement options: under root `properties`,
root `links`, or another already-documented root field.

**Recommendation: place property references under root
`properties.property_refs` as an ID-keyed sub-map.** Concretely, after
`05-…` materializes against the `04-…` root, the `typ` document looks
like:

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96",
  "id":   "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96",
  "collection": "typ",
  "type_id": null,
  "properties": {
    "name": "plate_96",
    "description": "96-well sample plate",
    "property_refs": {
      "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode": {
        "required": true
      }
    }
  },
  "links": {},
  "_head": { ...preserved from create, with provenance.action == "create"... }
}
```

Why this shape:

- `properties` already carries the type's own facts (`name`,
  `description`, and for link-type `typ` records `kind`,
  `allowed_left_collections`, etc.). "Which property definitions apply
  to instances of this type" is also a fact of the type, so it fits
  conceptually under `properties` rather than under `links` (which the
  vocabulary doc and `loc`/`lnk` materialization treat as
  inter-object-instance relationships).
- A dedicated `properties.property_refs` sub-map keeps type-fact scalars
  (`name`, `description`) cleanly separated from ID-keyed reference
  entries. This avoids commingling snake_case scalar keys with
  `~`-separated world-unique IDs in the same map.
- ID-keyed: matches `DIRECTION.md` line 83–84 ("Property and link maps
  should be keyed by the IDs of the property or link objects").
- Reference-only: the value is a tiny object carrying just the
  reference metadata listed in the directive — `required`. No copy of
  the property-definition `value_schema` or `name` is embedded; that
  satisfies "Keep the shape reference-only; do not embed property
  definitions."
- Additive on update: `add_property` is a `$set` of one dotted-path
  sub-key (`properties.property_refs.<property_id>`), atomic in Mongo,
  doesn't touch `name`/`description`/`type_id`/`links`/`_head` other
  than the head-provenance touch (Open question #4).
- Survives later iterations: when `remove_property`,
  `update_property_required`, or richer property-reference metadata
  arrive, they are single-key `$set` / `$unset` operations on the same
  sub-map. No global rewrite required.

**Alternatives** (flagged for director ruling):

- **Alternative A — under root `links`** (`links.<property_id>:
  {required: bool}`). The vocabulary doc currently treats `links` as
  the denormalized inverse-link projection slot for inter-object
  relationships (e.g., the `loc → contents → ent` direction).
  Putting type-to-property-definition references under `links`
  conflates reference semantics with denormalized inverse-link
  projection semantics. Smallest delta to `buildDocument` (no new
  sub-map structure), but semantically muddies `links`.
- **Alternative B — new top-level root field** `property_refs:
  {<id>: {required: bool}}`. Clearest separation of concerns but
  introduces a new reserved root field; the directive's wording
  ("another already-documented root field") nudges against this. No
  doc precedent for `property_refs` at the root yet.
- **Alternative C — under `properties.<property_id>`** (no
  intervening sub-map). Mixes scalar facts (`name`, `description`)
  with `~`-keyed references in one map. Smallest delta to existing
  shape but ugliest read.

**If the director prefers an Alternative**, only the `Update.set(...)`
dotted path in the materializer changes:

- Recommendation: `properties.property_refs.<property_id>` (or, as a
  whole-map replace, `properties.property_refs`).
- Alternative A: `links.<property_id>`.
- Alternative B: `property_refs.<property_id>`.
- Alternative C: `properties.<property_id>`.

Tests assert against the chosen path; everything else is path-agnostic.

#### 2. Whether `ppy + create` materialization or semantic `property_id` validation is required for this bounded proof

**Recommendation: NO.**

Rationale:

1. The materializer currently does not resolve any cross-document
   reference: not `ent.type_id → typ`, not `lnk.type_id → typ`, not
   `lnk.left/right → loc/ent`, not `typ.allowed_*_collections`. Adding
   resolution for `typ + update add_property → ppy` here would be the
   first such resolver and would create a load-bearing precedent for
   "the materializer enforces references", which the established
   architecture explicitly defers to a future read-time validator
   (`vdn`-collection scope). Out of scope per the directive.
2. The directive forbids it: "Do not add … semantic validation that
   `data.property_id` resolves." (TASK-030 OUT_OF_SCOPE.)
3. Materializing `ppy + create` is also out of scope for the same
   no-broadening reason. `02-…` and `03-…` continue to be ingested
   into the `txn` write-ahead log, validated by the schema, and stored
   as committed messages — they just don't produce a long-term `ppy`
   collection document yet.
4. The `add_property` reference can therefore be recorded
   verbatim from `data.property_id` without confirming that a
   committed `ppy` definition with that ID exists. This matches the
   directive's "record the reference without semantic validation that
   `data.property_id` resolves" option.

**Implication:** the materializer's new `processTypUpdate(...)` helper
may not query the `ppy` collection. It only writes (or no-ops) on the
`typ` collection.

#### 3. Materializer behaviour for missing target, idempotent repeat, and conflicting metadata

**Missing target** (`typ + update add_property` arrives committed but
no `typ` document with `data.id` exists in the long-term `typ`
collection):

- **Recommendation:** count it as a skip without raising. Use either a
  new `skippedMissingTarget` counter (preferred for clarity) or reuse
  the existing `skippedUnsupported` counter with a specific log line
  ("missing target typ root").
- **Default proposal:** add a new `int skippedMissingTarget = 0` field
  to `MaterializeResult` (purely additive; no breaking API change to
  existing callers/tests). The new counter mirrors the existing
  `skippedInvalid` counter style and is purpose-specific.
- Why not raise: every other materializer non-fatal outcome (matching
  duplicate, conflicting duplicate, missing `data.id`) is a counted
  skip rather than an exception. Raising on missing-target would break
  the read-after-commit projection invariant that "non-fatal product
  state outcomes do not poison the projection run."
- Why not "create the target on the fly": the directive forbids
  inventing IDs (preserves the existing `extractDocId` invariant) and
  forbids broadening scope. A missing target indicates the producer
  ordered messages incorrectly, or the target was deleted, or the
  target lives in a different transaction snapshot. The materializer's
  single-snapshot-at-a-time contract means the right behaviour is to
  skip the message and surface it via the counter and log line; a
  follow-on producer can reorder.
- See Open question #2 for the counter shape.

**Idempotent repeat** (the same `add_property` message — same target,
same `property_id`, same `required` value — arrives committed twice;
or the existing `properties.property_refs.<property_id>` sub-document
already equals the incoming reference metadata):

- **Recommendation:** count as `duplicateMatching++` (reuse existing
  counter) and skip the `$set` so the document's `_head.provenance` is
  not perturbed. This mirrors the existing
  `loc + create` / `ent + create` / `typ + create` /
  `grp + create` / `lnk + create` "duplicate matching is idempotent"
  semantic, with the same counter.
- Implementation: the new `processTypUpdate` helper does
  `mongoTemplate.findById(targetId, Map.class, "typ")`, projects out
  the existing `properties.property_refs.<property_id>` value, and
  compares to the incoming reference metadata using the same
  null-safe `Objects.equals` style as `isSamePayload`. If equal, no
  `$set`, just `result.duplicateMatching++`.
- Why preserve `_head.provenance` on a no-op: a duplicate-matching
  re-application carries no new information; the existing provenance
  already reflects the prior `add_property` apply (or the original
  `create` if `add_property` has never run). Re-touching provenance
  on a no-op would invent change history.

**Conflicting metadata** (an `add_property` arrives with
`required: false` while the existing
`properties.property_refs.<property_id>.required` is `true`, or vice
versa; or with any other field-level mismatch):

- **Recommendation:** count as `conflictingDuplicate++` (reuse
  existing counter) and **do not overwrite**. Mirrors the existing
  `*+create` "differing-payload duplicate" semantic.
- Implementation: same `findById` + diff path as idempotent above. If
  unequal, no `$set`; `result.conflictingDuplicate++`. Log line
  identifies `collection`, `id`, `property_id`, `txnId`, `commitId`,
  `msgUuid` so an operator can manually reconcile.
- Rationale: "last write wins" silently mutates a load-bearing type
  fact. The architecture's read-after-commit projection invariants
  prefer to expose the conflict (counter + log + manual review) rather
  than silently mutate. Same posture as
  `isSamePayload`/`conflictingDuplicate` for create paths.

### Smallest implementation plan

Goal: smallest set of changes that (1) lets the existing
`05-update-entity-type-add-property.json` materialize against an
existing `typ` root by setting one dotted-path sub-key, and (2)
preserves every other materializer skip semantic — including
non-`add_property` `typ + update` operations and every other
collection's `update`/`delete`.

#### File changes

1. **No example resource edits** in
   `libraries/jade-tipi-dto/src/main/resources/example/message/`.
   - `05-update-entity-type-add-property.json` already shows the
     accepted human-readable wire shape verbatim. The cross-references
     (`05-….data.id == 04-….data.id`, `05-….data.property_id ==
     02-….data.id`) are already correct and are exercised in the new
     DTO test (Plan §4). The directive forbids broad ID-abbreviation
     cleanup, so the `~ty~`/`~pp~` 2-char segments are preserved
     verbatim.

2. **Materializer code changes** in
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:
   - Extend `isSupported(...)` to accept
     `collection == COLLECTION_TYP` paired with
     `action == "update"` paired with
     `data.get("operation") == "add_property"`. Every other
     `action != ACTION_CREATE` continues to fall through to
     `skippedUnsupported` exactly as today; this preserves
     `loc + update`, `lnk + update`, `grp + update`, `ent + update`,
     `typ + update` with a different `operation`, and every
     `*+ delete` skip behaviour. Concretely:
     ```groovy
     private static boolean isSupported(CommittedTransactionMessage message) {
         if (message == null) return false
         if (message.action == ACTION_CREATE) {
             switch (message.collection) {
                 case COLLECTION_LOC: return true
                 case COLLECTION_LNK: return true
                 case COLLECTION_GRP: return true
                 case COLLECTION_ENT: return true
                 case COLLECTION_TYP: return true
                 default: return false
             }
         }
         if (message.action == 'update'
                 && message.collection == COLLECTION_TYP
                 && 'add_property' == message.data?.get('operation')) {
             return true
         }
         return false
     }
     ```
     Add `static final String ACTION_UPDATE = 'update'` and
     `static final String OPERATION_ADD_PROPERTY = 'add_property'`
     plus `static final String FIELD_OPERATION = 'operation'`,
     `static final String FIELD_PROPERTY_ID = 'property_id'`,
     `static final String FIELD_REQUIRED = 'required'`,
     `static final String FIELD_PROPERTY_REFS = 'property_refs'` near
     the existing `ACTION_CREATE` declaration.
   - Split `processMessage(...)` into a thin dispatcher that routes
     create paths to the existing insert flow and routes
     `typ + update add_property` to a new
     `processTypUpdateAddProperty(snapshot, message, result)` helper.
     The existing `extractDocId` / `buildDocument` / `insert`
     pipeline is unchanged for create paths.
   - The new `processTypUpdateAddProperty` helper:
     - Read `targetId` from `data.id` via `extractDocId`. Missing/
       blank id → `result.skippedInvalid++`, log error, return
       `Mono.empty()`.
     - Read `propertyId` from `data.property_id`. Missing/blank →
       `result.skippedInvalid++`, log error, return. (Treats this
       symmetrically with `data.id` because both are required for the
       message to be actionable.)
     - Build `Map<String, Object> referenceEntry` containing exactly
       the reference metadata fields the wire shape carries other
       than `id`/`operation`/`property_id`. For TASK-030 that is the
       single field `required` if present; if `data.required` is
       absent, the entry is an empty map (not `{required: false}`,
       to avoid inventing a value). See Open question #3.
     - `mongoTemplate.findById(targetId, Map.class, COLLECTION_TYP)`:
       - If empty → `result.skippedMissingTarget++` (Open question
         #2 may rename this), log warning, return.
       - Else → read existing
         `properties.property_refs.<propertyId>` sub-map. If absent
         → execute
         `mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(targetId)),
         new Update().set("properties.property_refs." + propertyId,
         referenceEntry)
         .set("_head.provenance.materialized_at", Instant.now()),
         COLLECTION_TYP)` and
         `result.materialized++`. Provenance handling: see Open
         question #4.
       - If present and equal to `referenceEntry` →
         `result.duplicateMatching++`, no `$set`.
       - If present and unequal →
         `result.conflictingDuplicate++`, no `$set`, log error.
   - Update the class Javadoc (lines 26–70) to add the supported
     `typ + update add_property` bullet next to the existing
     `typ + create` description, and to amend the "every other
     collection/action combination — including update, delete, and
     txn-control actions — is counted as `skippedUnsupported`"
     sentence to read "every other update/delete and every
     unsupported `typ + update` operation."

3. **`MaterializeResult` change** in
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`:
   - **Default proposal:** add one purely-additive field
     `int skippedMissingTarget = 0` with a one-line javadoc:
     "Supported messages whose target root document does not yet
     exist in the long-term collection." Existing fields and existing
     callers/tests are unaffected.
   - **Alternative:** reuse `skippedUnsupported` for the
     missing-target case (no API change). Less precise but smaller
     diff. See Open question #2.

4. **Backend unit tests** in
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`:
   - **Replace** the existing
     `'skips a typ + update message even after dropping the kind
       guard'` feature (lines 561–570) with a positive feature
     `'materializes a typ + update add_property as a $set onto
       root.properties.property_refs.<property_id>'`:
     - Mock `mongoTemplate.findById(ENT_TYPE_ID, Map.class, 'typ')`
       to return an existing `typ` root that mirrors the post-create
       shape (`_id`, `id`, `collection: 'typ'`, `type_id: null`,
       `properties: {name: 'plate_96', description: '96-well sample
       plate'}`, `links: {}`, `_head` with provenance.action ==
       'create').
     - Capture `mongoTemplate.updateFirst` arguments (query, update,
       collection).
     - Snapshot `[updateEntityTypeMessage()]`.
     - Assert `result.materialized == 1`,
       `result.duplicateMatching == 0`,
       `result.conflictingDuplicate == 0`,
       `result.skippedMissingTarget == 0`,
       `result.skippedUnsupported == 0`,
       `result.skippedInvalid == 0`.
     - Assert the captured update has a `$set` whose key is
       `properties.property_refs.<the helper's property_id>` and
       whose value is `[required: true]`. Asserts the captured
       collection is `'typ'` and the captured query targets
       `_id == ENT_TYPE_ID`.
     - Assert `0 * mongoTemplate.insert(_, _)` to prove the
       `add_property` path no longer goes through the create-only
       `insert` flow.
   - **Add** `'skips a typ + update with operation other than
       add_property as skippedUnsupported'`:
     - Build a message identical to `updateEntityTypeMessage()` but
       with `data.operation = 'remove_property'` (or
       `'update_required'`, or absent).
     - Assert `result.skippedUnsupported == 1`,
       `result.materialized == 0`,
       `0 * mongoTemplate.insert(_, _)`,
       `0 * mongoTemplate.updateFirst(_, _, _)`,
       `0 * mongoTemplate.findById(_, _, _)`.
     - Where: `where: operation << ['remove_property',
       'update_required', '', null]`.
   - **Add** `'skips a typ + update add_property whose target typ root
       does not exist as skippedMissingTarget'`:
     - Mock `mongoTemplate.findById(ENT_TYPE_ID, Map.class, 'typ')`
       to return `Mono.empty()`.
     - Snapshot `[updateEntityTypeMessage()]`.
     - Assert `result.skippedMissingTarget == 1`,
       `result.materialized == 0`, `result.skippedInvalid == 0`,
       `result.skippedUnsupported == 0`,
       `0 * mongoTemplate.updateFirst(_, _, _)`,
       `0 * mongoTemplate.insert(_, _)`.
     - **If** the director rules to reuse `skippedUnsupported`
       (Open question #2), substitute that counter; everything else
       is identical.
   - **Add** `'idempotent typ + update add_property with matching
       reference metadata is duplicate-matching and does not
       re-write'`:
     - Mock `findById` to return an existing root whose
       `properties.property_refs.<the helper's property_id>` already
       equals `[required: true]`.
     - Snapshot `[updateEntityTypeMessage()]`.
     - Assert `result.duplicateMatching == 1`,
       `result.materialized == 0`,
       `result.conflictingDuplicate == 0`,
       `result.skippedUnsupported == 0`,
       `result.skippedMissingTarget == 0`,
       `0 * mongoTemplate.updateFirst(_, _, _)`.
   - **Add** `'conflicting typ + update add_property is
       conflicting-duplicate and not overwritten'`:
     - Mock `findById` to return an existing root whose
       `properties.property_refs.<the helper's property_id>` exists
       but equals `[required: false]` (disagrees with the incoming
       `[required: true]`).
     - Snapshot `[updateEntityTypeMessage()]`.
     - Assert `result.conflictingDuplicate == 1`,
       `result.materialized == 0`, `result.duplicateMatching == 0`,
       `result.skippedUnsupported == 0`,
       `result.skippedMissingTarget == 0`,
       `0 * mongoTemplate.updateFirst(_, _, _)`.
   - **Add** `'skips a typ + update add_property with missing
       data.property_id as skippedInvalid'`:
     - Build `updateEntityTypeMessage()` with `property_id: null`
       (or the key removed).
     - Assert `result.skippedInvalid == 1`,
       `result.materialized == 0`,
       `0 * mongoTemplate.findById(_, _, _)`,
       `0 * mongoTemplate.updateFirst(_, _, _)`.
   - **Optionally extend** the mixed-snapshot feature (current
     `[loc + create, ppy + create (skip), typ + create (link-type),
       typ + create (bare entity-type), ent + create, lnk + create]`
     after TASK-029) to add `updateEntityTypeMessage()` immediately
     after the bare-entity-type create. Counts move to
     `materialized == 6`, `skippedUnsupported == 1`. This is optional
     polish; the dedicated features above already cover the new
     code paths. Default proposal: skip the mixed-snapshot extension
     unless the director asks for it.

5. **DTO library tests** in
   `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:
   - **Add** `'typ + update add_property example uses the human-readable
       data.id, data.operation, data.property_id, and optional
       data.required shape'`:
     - Reads `05-update-entity-type-add-property.json`.
     - Asserts `message.collection() == Collection.TYPE`,
       `message.action() == Action.UPDATE`,
       `data.id == 'jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~ty~plate_96'`,
       `data.operation == 'add_property'`,
       `data.property_id == 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode'`,
       `data.required == true`,
       `data.keySet() == ['id', 'operation', 'property_id',
         'required'] as Set`.
   - **Add** `'entity-type-with-property example sequence shares one txn
       id and the type-update references the property-definition by
       id'`:
     - Reads `01-…`, `02-…`, `04-…`, `05-…`, `09-…`.
     - Asserts all five share the same `txn.uuid`.
     - Asserts `02-…` is `Collection.PROPERTY` + `Action.CREATE`,
       `04-…` is `Collection.TYPE` + `Action.CREATE`,
       `05-…` is `Collection.TYPE` + `Action.UPDATE`.
     - Asserts the cross-references hold verbatim:
       `05-….data.id == 04-….data.id` (the type-update targets the
       freshly-created bare entity-type) and
       `05-….data.property_id == 02-….data.id` (the type-update
       references the freshly-created text property definition).
   - The existing
     `'entity transaction example sequence shares one txn id and the
       entity references the entity-type by id'` feature (lines
     509–540) already covers `01 + 04 + 06 + 09` and is unchanged.

6. **Backend integration test** in
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/`:
   - **Default proposal:** extend the existing
     `EntityCreateKafkaMaterializeIntegrationSpec.groovy` to publish
     `open + 04-typ + 05-typ-update-add-property + 06-ent + commit`
     instead of `open + 04-typ + 06-ent + commit`. Add one new
     `awaitMongo` block proving the materialized `typ` root now
     carries `properties.property_refs.<the test's property_id> ==
     [required: true]`, with everything else (`name`, `description`,
     empty `links`, `_head.provenance.collection == 'typ'`,
     `_head.provenance.action == 'create'` — the create-time
     provenance, not the update-time one — see Open question #4 for
     whether `add_property` touches `materialized_at`) preserved.
   - The spec stays opt-in (`JADETIPI_IT_KAFKA=1` + Kafka reachable).
   - **Alternative:** add a dedicated
     `EntityTypeUpdateAddPropertyKafkaMaterializeIntegrationSpec.groovy`
     (~150 lines of boilerplate). Cleaner per-feature spec but
     duplicates Kafka topic / producer / Mongo cleanup setup. Default
     is to extend the existing spec.
   - **If** the director prefers no integration change, the
     `processTypUpdateAddProperty` materializer behaviour is fully
     covered by the unit features above. The end-to-end Kafka → Mongo
     proof for `add_property` would then be deferred. See Open
     question #5.

7. **Documentation updates:**
   - `docs/architecture/kafka-transaction-message-vocabulary.md`:
     - Lines 104–121 ("Types And Properties"): replace "The
       materialized type document should eventually include property
       references, not embedded property definitions." with a
       concrete description of the materialized
       `properties.property_refs.<property_id>` sub-map shape (or
       Alternative A/B/C if the director rules differently). Note
       that the reference value carries only the wire-shape
       `required` metadata and that the materializer does not
       resolve `data.property_id` against the `ppy` collection.
     - Lines 257–263 ("Committed Materialization Of Locations And
       Links"): expand the supported set from
       "(both link-type and bare entity-type) for `typ + create`" to
       also include "`typ + update add_property`". Replace the
       sentence that currently says `typ + update` is not
       materialized with the narrower scope: every other
       collection/action and every `typ + update` whose
       `data.operation` is not `add_property` remains
       `skippedUnsupported`.
     - One sentence on missing-target / idempotent / conflicting
       behaviour, mirroring the existing `*+create` paragraph.
   - `docs/OVERVIEW.md`: a single sentence in the materializer-scope
     section noting that `typ + update add_property` is now
     materialized as a property reference on the materialized type
     root. (Anticipated 1–3 line edit; no structural change.)
   - `docs/architecture/jade-tipi-object-model-design-brief.md`: no
     change anticipated; the brief stays at the logical model level
     and is agnostic to the physical sub-map placement.
   - `DIRECTION.md`: no change anticipated; lines 24–32 already
     describe the type-defines-permitted-properties direction at the
     human-language level.

#### Expected total surface (with default proposals selected)

- **0 example resource edits.**
- ~25–40 lines of materializer source change in
  `CommittedTransactionMaterializer.groovy` (new constants, `isSupported`
  branch, `processTypUpdateAddProperty` helper, doc edits). No
  refactoring of existing create/insert paths.
- 1 new line in `MaterializeResult.groovy` (additive
  `skippedMissingTarget` field).
- ~150–220 lines of new/changed Spock features in the materializer spec
  (1 feature flipped, 5–6 new features, optional 1 mixed-snapshot
  extension).
- ~50–80 lines of two new features in `MessageSpec` (DTO co-presence
  + 5-message txn chain).
- ~40–60 lines of new publish/wait/assert block in
  `EntityCreateKafkaMaterializeIntegrationSpec.groovy`.
- ~10–20 lines of doc edit across
  `kafka-transaction-message-vocabulary.md` + `docs/OVERVIEW.md`.

#### Cross-cutting impact (read of source on `claude-1`)

- `ContentsLinkReadService` queries `typ` for documents with
  `properties.kind == "link_type"` and `properties.name == "contents"`.
  Adding `properties.property_refs` to bare entity-type roots does not
  affect those criteria (link-type roots do not currently carry
  `property_refs`, and the dotted-path match is unaffected by other
  sibling keys). No read-service change required.
- `GroupAdminService` writes only `grp` documents. Unaffected.
- `TransactionService` / `CommittedTransactionReadService` / persistence
  paths treat message `data` as opaque. Unaffected.
- `kli` CLI and frontend already accept `--collection typ --action
  update` and pass `data` through unchanged. No CLI / UI surface
  change is needed for human submitters.
- `06-create-entity.json` references the type by `data.type_id`; that
  reference does not depend on whether the type carries property refs.
  No `ent` materialization change required.

#### Out-of-scope guardrails (will **not** edit unless director ruling expands scope)

- `clients/kafka-kli/**` — no CLI change is needed.
- `frontend/**` — no UI surface for raw type-update submission.
- `message.schema.json` — see Schema status above.
- `frontend/.env.local` — generated; never hand-edited.
- `02-…` and `03-…` — `ppy + create` materialization remains out of
  scope (no semantic resolution of `data.property_id`).
- `07-…`, `08-…` — `ppy + create` property-assignment materialization
  remains out of scope.
- `04-create-entity-type.json` and `06-create-entity.json` — TASK-029
  and TASK-028 already landed the accepted shapes; do not edit.
- Authentication, Keycloak, admin group-management, permission
  enforcement — out of scope per active focus.

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    type-update example plus the new focused MessageSpec features.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new typ + update add_property root-update shape, the
#    missing-target skip, the idempotent duplicate, the conflicting
#    duplicate, the missing-property_id skipped-invalid case, and the
#    other-operation skipped-unsupported case. Other unit tests
#    (group, contents read, transaction service, etc.) are unaffected
#    and rerun for regression confirmation.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running). The extended
#    EntityCreateKafkaMaterializeIntegrationSpec drives an end-to-end
#    open + 04-typ + 05-typ-update-add-property + 06-ent + commit ->
#    materialized typ (with properties.property_refs) + ent roots
#    proof.
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'

# Optional regression checks (rerun if local stack is healthy):
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*ContentsHttpReadIntegrationSpec*'
```

Setup commands (per `CLAUDE.md` "Tooling Refresh"; if local tooling is
missing they are reported, not treated as product blockers):

- The Docker stack is required for `:jade-tipi:test` because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection
  (per project `CLAUDE.md`). Run
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`
  for Mongo-backed unit tests, or
  `docker compose -f docker/docker-compose.yml up -d` for the full
  Kafka/Mongo stack.
- If the Gradle wrapper cache is missing, the first `./gradlew`
  invocation bootstraps it; that is normal first-run behaviour, not
  a blocker.
- If a stale Gradle daemon is implicated, run `./gradlew --stop`
  before retrying (per `DIRECTIVES.md` TASK-026/27/28/29 review notes
  that consistently recommend `./gradlew --stop` when the wrapper
  cache lock fails with "Operation not permitted").
- If integration tests cannot run because Docker is not available
  in the sandbox, report the exact `docker compose ... up` command
  and stop rather than treating it as a product blocker.

### Open questions / blockers (for director review)

These are product-judgment questions that match the directive's "Stop
And Ask" guidance for unclear product decisions. I am flagging them in
pre-work rather than stopping, so the director can rule once and then
advance the task. If the director prefers I stop with `STATUS:
HUMAN_REQUIRED` on any of these instead of proposing a default, please
redirect.

1. **Where to place property references on the materialized `typ` root
   document.** This is the central design choice the directive asks for.
   - **Default proposal:** root
     `properties.property_refs.<property_id>: {required: bool}` —
     ID-keyed sub-map under `properties`, separated from the type's
     scalar facts (`name`, `description`).
   - **Alternative A:** root `links.<property_id>: {required: bool}`.
     Smallest delta to existing `links: {}` initialization, but
     conflates type-to-property-definition references with the
     denormalized inverse-link projection semantics that `lnk` /
     `loc` materialization use today.
   - **Alternative B:** new top-level field
     `property_refs: {<property_id>: {required: bool}}`. Clearest
     separation of concerns but introduces a new reserved root field;
     the directive's "another already-documented root field" wording
     reads as a soft preference against a brand-new field.
   - **Alternative C:** `properties.<property_id>: {required: bool}`
     (no intervening sub-map). Mixes scalar facts with `~`-keyed
     references in one map. Smallest schema delta, ugliest read.

2. **`MaterializeResult` field for the missing-target case.**
   - **Default proposal:** add a new `int skippedMissingTarget = 0`
     field. Purely additive; existing callers/tests untouched.
     Clear, purpose-specific.
   - **Alternative:** reuse the existing `int skippedUnsupported`
     counter with a specific log line. Smaller diff (no
     `MaterializeResult.groovy` edit) but conflates "unsupported by
     this materializer" with "supported but the target root does not
     yet exist."

3. **Default value for `required` when `data.required` is absent on
   the wire.**
   - **Default proposal:** **omit** `required` from the materialized
     reference entry. The materialized sub-map then carries
     `{<property_id>: {}}` (an empty reference object). The
     materializer does not invent a value, mirroring the
     `DIRECTION.md` "the materializer should not invent property
     values" directive.
   - **Alternative:** default to `false`. Simpler downstream reads
     (always present, always bool) but invents a value the producer
     did not state.

4. **Whether `add_property` touches `_head.provenance` on the target
   root.**
   - **Default proposal:** on a successful `$set`, also `$set`
     `_head.provenance.materialized_at` to `Instant.now()` so future
     readers can tell the root was last touched after the
     `add_property` apply. Leave `_head.provenance.txn_id`,
     `commit_id`, `msg_uuid`, `collection`, `action`, and
     `committed_at` **unchanged** — those still describe the
     create-time provenance. Document this as a deliberate single-
     field touch, not a full provenance rewrite.
   - **Alternative A:** rewrite `_head.provenance` in full on
     `add_property` apply (txn_id/commit_id/msg_uuid/action all
     point at the `update` message). Cleaner "last writer wins"
     provenance but loses the create-time history visible at the
     root level.
   - **Alternative B:** maintain a small `_head.provenance.history`
     array carrying one entry per applied message
     (`txn_id`/`commit_id`/`msg_uuid`/`action`/`materialized_at`).
     Richest history but introduces a new sub-shape that no current
     reader uses; out of scope for the bounded TASK-030 proof.
   - **Alternative C:** do not touch `_head` on `add_property`
     apply. Smallest diff; no field invention. But future readers
     have no signal that the root was updated post-create.
   - I'm proposing the Default; flagging because none of the
     accepted prior tasks have written a partial-update path and
     this is a precedent decision.

5. **Integration spec scope.**
   - **Default proposal:** extend `EntityCreateKafkaMaterializeIntegrationSpec`
     in place to publish 4 → 5 → 6 → commit and assert the
     materialized type carries the `add_property` reference.
     Smallest delta; reuses the existing producer/consumer/cleanup.
   - **Alternative A:** add a dedicated
     `EntityTypeUpdateAddPropertyKafkaMaterializeIntegrationSpec`.
     Cleaner per-feature spec; ~150 lines of duplicated boilerplate.
   - **Alternative B:** rely on materializer unit tests; do not
     change integration coverage. Cheapest diff; loses the end-to-end
     Kafka → Mongo `add_property` proof.

6. **`updateEntityTypeMessage()` helper's `~ppy~` segment vs.
   `05-…` JSON's `~pp~` segment.** The Spock helper at
   `CommittedTransactionMaterializerSpec.groovy:133` uses
   `'…~ppy~barcode'` while `05-update-entity-type-add-property.json`
   uses `'…~pp~barcode'`. Both are accepted today because nothing
   resolves the property ID semantically. The helper's value is
   internal to the spec; the JSON example's value is the canonical
   wire shape. **No action is required of the director here** — I
   am not proposing to "fix" either side per the directive's
   "Preserve the current example ID strings" guidance, and per the
   `OUT_OF_SCOPE` "Do not redesign the ID abbreviation scheme."
   Flagged here only for director visibility / future ID-cleanup
   follow-up.

7. **Should the bounded proof also exercise `add_property` against a
   link-type `typ` root** (the `11-create-contents-type.json` shape
   with `data.kind == "link_type"`)?
   - **Default proposal:** no. The directive's wire example
     `05-…` targets the bare-entity-type root from `04-…`. Adding
     link-type coverage broadens the bounded proof. The materializer
     code path is uniform across both `typ` kinds (they both use the
     same root shape), so unit-level coverage of the bare-entity-type
     case is sufficient for the bounded proof.
   - **Alternative:** add one Spock feature confirming `add_property`
     also lands on a link-type `typ` root. Tiny addition; acceptable
     if the director wants it.

8. **Follow-up task creation.** TASK-030 covers `typ + update
   add_property` only. Likely follow-on units (separate tasks):
   - `typ + update remove_property` (mirror `add_property`).
   - `typ + update update_required` or richer reference metadata
     edits.
   - `ppy + create` materialization (the property-definition itself).
   - Semantic resolution of `data.property_id → ppy` at materializer
     time, or at a separate `vdn` validator pass.
   - Property-value-assignment materialization (`07-…`, `08-…`).
   - **Default proposal:** create no follow-on task in this pre-work
     turn; let the director sequence those after TASK-030 acceptance,
     mirroring the TASK-026 → TASK-027 → TASK-028 → TASK-029 →
     TASK-030 rhythm.

If the director rules on items 1–5 (and optionally 7) in the next
direction update, I can proceed with the implementation turn directly.
If any are unclear or need more time, please advance with a "use the
default proposals" signal and I will follow them.

### Stay-in-scope check (this pre-work turn)

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` (a base owned path, this file).

It does **not** touch:

- The active task file or any other task file.
- `docs/agents/claude-1-changes.md` (reserved for the implementation
  report after the director advances the task).
- Any source under `jade-tipi/`, `libraries/`, `clients/`, `frontend/`,
  `docker/`, or `docs/` other than this file.
- `DIRECTIVES.md`, `ORCHESTRATOR.md`, `AGENTS.md`, or `claude-1.md`.

Per the orchestrator protocol, I stop here and wait for the director
to set TASK-030 to `READY_FOR_IMPLEMENTATION` (or change the global
signal to `PROCEED_TO_IMPLEMENTATION`) before making any of the
implementation-turn changes listed in the plan above.
