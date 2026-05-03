# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-031 — Human-readable Kafka property-definition materialization path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-031-human-readable-kafka-property-definition-materialization.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. TASK-031 is the deferred
follow-on to accepted TASK-026 → TASK-030 (the `loc`/`typ`/`lnk`/`ent`/
`grp`/`typ-update` Kafka-first projection set). It scopes the smallest
Kafka-first increment that lets a human-readable `ppy + create` message
with `data.kind == "definition"` materialize a root-shaped property-
definition document in the `ppy` MongoDB collection. The bounded proof
covers definition-shaped messages only; `data.kind == "assignment"`
property-value messages (`07-…`, `08-…`) remain `skippedUnsupported` and
are a separate future task.

Acceptance criteria (paraphrased from the task file and `DIRECTIVES.md`
TASK-031 Direction):

- Kafka stays the primary submission route; no HTTP data-submission
  endpoint is added.
- Inspect `02-…`, `03-…`, `07-…`, `08-…`, the DTO validation, the
  `CommittedTransactionMaterializer` `ppy + create` skip behaviour, and
  the existing duplicate handling around materialized roots before
  planning.
- The intended human-readable wire shape is already canonicalized in
  `02-create-property-definition-text.json` and
  `03-create-property-definition-numeric.json`: top-level
  `collection: "ppy"`, `action: "create"`, `data.kind: "definition"`,
  `data.id` (the property-definition ID), `data.name` (the
  human-readable property name), and `data.value_schema` (the submitted
  JSON-object value contract).
- Preserve the current example ID strings (no broad ID-abbreviation
  cleanup); a necessary local synthetic ID may live in tests when
  required.
- Decide the smallest materialized root shape for property definitions
  in the `ppy` collection: which facts live under root `properties`,
  whether `data.kind` remains materialized as a discriminator, and how
  `value_schema` is copied without validating future assignment values.
- Decide whether the bounded proof materializes only
  `data.kind == "definition"` and continues skipping
  `data.kind == "assignment"`, or whether assignment requires a
  separate follow-up task.
- Add or update example resource JSON only if the existing examples do
  not already show the accepted shape and sequence.
- Propose focused automated coverage for DTO validation, materialized
  root shape, skipped-assignment behaviour if assignment remains
  deferred, missing/blank `data.id`, idempotent duplicate handling,
  conflicting duplicate handling, and the narrowest practical Kafka/
  Mongo integration check.
- Report the exact verification commands and any local Docker, Kafka,
  Mongo, or Gradle setup blockers.

Out of scope (per task `OUT_OF_SCOPE` and `DIRECTIVES.md` TASK-031
Direction):

- No HTTP submission endpoints.
- No property-value assignment materialization, required-property
  enforcement, semantic validation that `typ.data.property_id` resolves,
  semantic validation that assignments reference allowed properties,
  value-shape validation against `data.value_schema`, permission
  enforcement, object extension pages, endpoint projection maintenance,
  full Clarity/ESP import, or a nested Kafka operation DSL.
- No ID-abbreviation scheme redesign.
- No changes to contents-link read semantics, entity materialization,
  location materialization, type-reference update behavior, or group/
  admin behavior except where a finding identifies a direct TASK-031
  dependency. (None identified — see "Cross-cutting impact" below.)

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source
change is made until the director advances TASK-031 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` (this branch):
  - "Objects, Types, And Properties" (lines 17–32): a type defines
    which properties may be assigned to objects of that type; "do not
    implement required properties or default values yet". Properties
    are first-class objects. This is the human-language root motivation
    for `ppy + create kind=definition`: it materializes the property
    object itself. The brief stays explicit that the materializer
    "should not invent property values" (line 31–32) — applied to
    TASK-031, this means the materializer must not fabricate a
    `value_schema`, `name`, or `kind` value not present on the wire.
  - "Logical Objects And Physical Documents" (lines 58–101): the root
    document holds object identity, `collection`, `type_id`, "small
    `properties` and `links` maps when they fit, and reserved
    implementation metadata." Property and link maps are keyed by IDs
    of the property or link objects — but a property *definition*'s own
    facts (its `name`, its declared `value_schema`) live as type-fact
    scalars under root `properties`, not under that ID-keyed sub-map
    convention. This matches how the bare entity-type `typ + create`
    today projects `name`/`description` directly under
    `properties.name`/`properties.description`.
- `docs/architecture/kafka-transaction-message-vocabulary.md`:
  - "Property Definitions" (lines 79–102): canonical property-
    definition shape exactly as 02-…/03-… render it: top-level
    `collection: "ppy"`, `action: "create"`, with `data.kind:
    "definition"`, `data.id`, `data.name`, and `data.value_schema`. The
    paragraph also calls out "Definitions and assignments share
    `collection: "ppy"` and are distinguished by `data.kind`
    (`definition` vs. `assignment`)" — this is the directive's hook for
    the kind-discriminator gate.
  - "Committed Materialization Of Locations And Links" (lines 257–263):
    currently lists the supported projection set as
    `loc + create`, both `typ + create` kinds, `typ + update
    add_property`, `lnk + create`, `ent + create`, `grp + create` and
    explicitly states: "Other collections and other actions … are
    intentionally not materialized in this iteration and are counted as
    `skippedUnsupported` without raising an error." That sentence is
    the live pin of today's accepted `ppy + create` skip behaviour and
    **must be edited** in the implementation turn to surface
    `ppy + create kind=definition`.
  - Lines 102 and 158: "All property values are JSON objects … The
    envelope schema does not yet validate the wrapper shape against the
    registered `value_schema`; that lookup belongs to the transaction
    snapshot/read layer." This is the architecture's pin for the
    TASK-031 `value_schema` decision: copy-verbatim, no validation, no
    walk into nested keys at materializer time.
- `docs/architecture/jade-tipi-object-model-design-brief.md` (lines
  9–17): "A type definition declares the properties that may be
  assigned to objects of that type. … A property may be assigned only
  after the object's type definition permits that property." The brief
  treats property *definitions* as first-class objects; it stays at
  the logical model level and is agnostic to the physical sub-map
  placement chosen by TASK-031.
- `DIRECTIVES.md` TASK-031 Direction (lines 364–374) explicitly pins
  the task to the `data.kind == "definition"` path only and forbids
  HTTP submission, property-value assignment materialization,
  required-property enforcement, semantic `property_id` resolution,
  semantic assignment/type validation, value-shape validation against
  `data.value_schema`, permission enforcement, contents-link read
  changes, and a nested Kafka operation DSL.
- TASK-013/TASK-014 (root-shaped contract), TASK-015 (root-shaped
  contents read), TASK-019 (Clarity/ESP container prototype),
  TASK-020 (root-shaped `grp`), TASK-026 (human-readable `loc`),
  TASK-027 (human-readable contents-link), TASK-028 (human-readable
  `ent`), TASK-029 (bare entity-type `typ + create`), and
  TASK-030 (`typ + update add_property`) are all accepted. They define
  the materializer entry point, the wire conventions, the inline-
  properties fallback used when `data.properties` is absent, the
  duplicate-handling semantics, and the `_head.provenance` reserved
  root metadata that this task must reuse.

### Current example resource state (read of source on `claude-1`)

`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `01-open-transaction.json` — `txn + open` with shared
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`. Sequence anchor.
- `02-create-property-definition-text.json` — `ppy + create` with
  `data.kind == "definition"`,
  `data.id == "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode"`,
  `data.name == "barcode"`,
  `data.value_schema == { type: "object", required: ["text"],
   properties: { text: { type: "string" } } }`. Note the 2-char `~pp~`
  ID segment, not `~ppy~`. **This is exactly the directive's accepted
  human-readable property-definition wire shape.** No file edit is
  required.
- `03-create-property-definition-numeric.json` — `ppy + create` with
  `data.kind == "definition"`,
  `data.id == "jade-tipi-org~dev~018fd849-2a42-7222-8b02-bbbbbbbbbbbb~pp~volume"`,
  `data.name == "volume"`,
  `data.value_schema == { type: "object", required: ["number",
   "unit_id"], properties: { number: { type: "number" }, unit_id:
   { type: "string" } } }`. Same 2-char `~pp~` segment. Exercises the
  multi-key required-array variant of `value_schema`. No file edit
  required.
- `04-create-entity-type.json` — accepted (TASK-029).
- `05-update-entity-type-add-property.json` — accepted (TASK-030).
  References `02-…`'s `data.id` verbatim via
  `data.property_id == "…~pp~barcode"`.
- `06-create-entity.json` — accepted (TASK-028).
- `07-assign-property-value-text.json` — `ppy + create` with
  `data.kind == "assignment"`,
  `data.id == "…~en~plate_a~…~pp~barcode"` (composite of entity ID and
  property-definition ID),
  `data.entity_id == "…~en~plate_a"` (matches `06-…`'s `data.id`),
  `data.property_id == "…~pp~barcode"` (matches `02-…`'s `data.id`),
  `data.value == { text: "barcode-1" }`. **Out of scope** for TASK-031
  (assignment materialization is a separate future task). The wire
  shape is already canonical and `MessageSpec` already round-trips/
  schema-validates it.
- `08-assign-property-value-number.json` — `ppy + create` with
  `data.kind == "assignment"`,
  `data.value == { number: 10, unit_id: "…~liter~milli~ml~SI" }`,
  paralleling `07-…`. Same out-of-scope status.
- `09-commit-transaction.json` — `txn + commit` on the same txn uuid.
- `10-…`, `11-…`, `12-…`, `13-…` — accepted (TASK-026/27/27/20).

**Conclusion:** the existing `02-…` and `03-…` already show the
accepted human-readable wire shape (top-level `collection: "ppy"` +
`action: "create"`, with `data.kind: "definition"`, `data.id`,
`data.name`, `data.value_schema`). **No example file edit is
required** to satisfy the directive's "human-readable shape"
criterion. The cross-message reference (`05-….data.property_id ==
02-….data.id`) is already correct verbatim and is exercised by the
existing TASK-030 DTO co-presence test (`MessageSpec`
`'entity-type-with-property example sequence …'`, lines 568–608).
The cross-message references for assignment (`07-….data.entity_id ==
06-….data.id`, `07-….data.property_id == 02-….data.id`,
`08-….data.property_id == 03-….data.id`) are also correct verbatim
but are not yet exercised by a focused DTO test (Plan §5 may add a
narrow shape-only assignment-example feature without making
assignments executable).

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
on this branch (post-TASK-030):

- `isSupported()` (lines 346–372) accepts `loc`, `lnk`, `grp`, `ent`,
  and `typ` paired with `action == ACTION_CREATE`, plus `typ` paired
  with `action == ACTION_UPDATE` when `data.operation ==
  "add_property"`. **`COLLECTION_PPY` is not declared** as a constant
  and `ppy` is not in the `isSupported` switch. Every `ppy + create`
  message — both `data.kind == "definition"` and `data.kind ==
  "assignment"` — falls through `isSupported` and is counted as
  `skippedUnsupported` at the top of `processMessage` (line 174). No
  Mongo work is attempted.
- The unit spec
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  pins this behaviour today:
  - `propertyCreateMessage()` helper (lines 164–177) builds a
    `collection == "ppy"`, `action == "create"`,
    `data == [kind: 'definition', id:
    'jade-tipi-org~dev~018fd849-2a41-7111-8a01-cccccccccccc~ppy~barcode',
    name: 'barcode']` message. It uses the 3-char `~ppy~` segment
    (Open question #5 below).
  - `'skips a ppy create message'` (lines 1209–1218) currently asserts
    `materialized == 0`, `skippedUnsupported == 1`,
    `0 * mongoTemplate.insert(_, _)` for that helper. **This feature
    flips into a positive materialize feature** in the implementation
    turn (or is replaced with a positive feature plus a focused
    assignment-skip feature; see Plan §4).
  - The mixed-snapshot feature at line 1625 (`'mixed-message snapshot
    inserts in snapshot order with correct counts'`) places the
    `propertyCreateMessage()` definition message between `loc` and the
    two `typ` creates, and pins `materialized == 5,
    skippedUnsupported == 1`. **Counts and the `insertOrder` array
    must change** to `materialized == 6, skippedUnsupported == 0`,
    with `'ppy'` inserted between `'loc'` and the two `'typ'` slots.
- The materializer's create path (`processMessage` lines 187–207 plus
  `buildDocument` lines 386–412) routes every supported create
  through `extractDocId(data)` → `buildDocument(docId, snapshot,
  message)` → `mongoTemplate.insert(doc, message.collection)` →
  `handleInsertError` for duplicate-key handling. The same plumbing
  works for `ppy + create kind=definition` with no special-case code
  beyond the `isSupported` switch and a kind discriminator gate;
  `buildDocument`'s "explicit `data.properties`" vs "inline-properties
  fallback" branch (lines 397–408) automatically copies
  `kind`/`name`/`value_schema` into root `properties` when
  `data.properties` is absent — matching the canonical 02-…/03-… wire
  shape verbatim.
- Inline-properties fallback (lines 414–422) excludes
  `id`/`type_id`/`links`. For 02-… the data keys are `kind`, `id`,
  `name`, `value_schema` — `id` is excluded, the other three become
  `properties.kind == "definition"`, `properties.name == "barcode"`,
  `properties.value_schema == { … the entire JSON-object schema … }`.
  **`value_schema` lands under root `properties` as a copied Map.**
  `copyProperties` (lines 424–429) does a shallow copy — nested maps
  are still shared by reference, but the materializer is the sole
  writer of the materialized doc per snapshot, so shared inner
  references are safe.
- The duplicate-key handler (`handleInsertError` lines 316–344) reuses
  `mongoTemplate.findById` plus `isSamePayload` after a Mongo
  `DuplicateKeyException`. `isSamePayload` strips
  `_head.provenance.materialized_at` only, so `ppy + create
  kind=definition` resends with identical wire payloads will count as
  `duplicateMatching` (idempotent), and resends with a different
  `value_schema` or `name` will count as `conflictingDuplicate` and
  not overwrite.
- `MaterializeResult` (separate file `MaterializeResult.groovy`)
  carries the existing counters: `materialized`, `duplicateMatching`,
  `conflictingDuplicate`, `skippedUnsupported`, `skippedInvalid`,
  `skippedMissingTarget`. **No new counter is required for TASK-031**
  because the bounded `ppy + create kind=definition` proof needs only
  the existing five counters: `materialized` for the success path,
  `duplicateMatching` for idempotent re-apply, `conflictingDuplicate`
  for differing payloads, `skippedUnsupported` for `kind ==
  "assignment"` / missing `kind` / unsupported `kind`, and
  `skippedInvalid` for missing/blank `data.id`.

### Current automated coverage (read of source on `claude-1`)

DTO library (`MessageSpec.groovy`):

- `EXAMPLE_PATHS` (lines 24–38) round-trips and schema-validates every
  example resource, including `02-…`, `03-…`, `07-…`, and `08-…`. All
  four already pass today.
- `'newInstance constructs a Message with the given collection and
  action'` (line 46) already constructs a `Collection.PROPERTY +
  Action.CREATE + [kind: 'definition']` message and asserts the
  envelope shape.
- `'schema rejects collection=ppy paired with a transaction-control
  action'` (line 192) already proves the envelope-level allow-list
  rejects e.g. `ppy + open`.
- `'entity-type-with-property example sequence shares one txn id and
  the type-update references the property-definition by id'` (line
  568) already reads `02-…` and asserts `02-…` is `Collection.PROPERTY
  + Action.CREATE` plus the cross-reference
  `05-….data.property_id == 02-….data.id`. **The `data.kind`,
  `data.name`, and `data.value_schema` content of `02-…`/`03-…` is
  not yet asserted by a focused feature**, and there is no focused
  feature at all for `07-…`/`08-…` shape (just the round-trip /
  schema-validate loop).

Backend unit (`CommittedTransactionMaterializerSpec.groovy`):

- `propertyCreateMessage()` helper (lines 164–177) — already wires a
  `kind: 'definition'` shape. Reusable for the new positive
  definition-materializes feature; **must be renamed** (Plan §4) to
  `propertyDefinitionCreateMessage()` and given a parallel
  `propertyAssignmentCreateMessage()` helper that builds the
  `kind: 'assignment'` skip-shape for the new
  assignment-still-skipped feature.
- `'skips a ppy create message'` (lines 1209–1218) — flipped or
  replaced in implementation turn.
- The mixed-snapshot feature (lines 1625–1665) — **must be updated**
  to expect the definition message materializes (and optionally
  extended to demonstrate that an assignment message in the same
  snapshot is still `skippedUnsupported`).
- No existing unit test covers `value_schema` round-trip under root
  `properties`; the new positive feature (Plan §4) adds it.
- No existing unit test covers idempotent duplicate / conflicting
  duplicate for `ppy`; the new features (Plan §4) add them.

Backend integration (opt-in):

- `EntityCreateKafkaMaterializeIntegrationSpec` publishes
  `open + 04-typ + 05-typ-update + 06-ent + commit` and asserts the
  materialized typ + ent roots. **It does not publish
  `02-ppy-definition`.** Plan §6 adds a dedicated
  `PropertyDefinitionCreateKafkaMaterializeIntegrationSpec` rather
  than overloading the entity spec.
- `TransactionMessageKafkaIngestIntegrationSpec` is generic and
  unaffected by TASK-031.

### Schema status (`message.schema.json`)

For `collection != "grp"`, `data` follows `SnakeCaseObject`. The
`02-…`, `03-…`, `07-…`, and `08-…` examples already validate today
(covered by the EXAMPLE_PATHS loop). The directive's accepted wire
shape is already permitted (no schema edit needed):

- `data.kind` is a snake_case-keyed string ("definition" /
  "assignment") — permitted by `SnakeCaseObject` + `SnakeCaseValue`.
- `data.id`, `data.name`, `data.entity_id`, `data.property_id` are
  snake_case-keyed string IDs — permitted.
- `data.value_schema` is a snake_case-keyed object whose nested keys
  (`type`, `required`, `properties`, `text`, `number`, `unit_id`) are
  all snake_case-compatible. The recursive `SnakeCaseObject` rule
  applies but does not reject any 02-…/03-… nested key.
- `data.value` (assignment-only) is a snake_case-keyed object —
  permitted.

Tightening the schema to require `ppy.data.kind` for `create`
actions, to enumerate the allowed `data.kind` values
(`"definition"` / `"assignment"`), or to assert that
`kind == "definition"` requires `data.value_schema` would be a real
semantic schema change. Per TASK-031's `OUT_OF_SCOPE` (no semantic
reference validation, no value-shape validation against
`data.value_schema`) **no schema-file change is required or in-scope**
for this task. The focused DTO tests (Plan §5) are the right place to
pin the wire-shape expectations rather than the schema file.

### Decisions required by the directive

#### 1. Smallest materialized root shape for property definitions (the central design choice)

The directive asks for the smallest materialized root shape, including
which facts live under root `properties`, whether `data.kind` remains
materialized as a discriminator, and how `value_schema` is copied.

**Recommendation: project `02-…` (and `03-…`) through the existing
inline-properties fallback so the materialized `ppy` document is:**

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode",
  "id":   "jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode",
  "collection": "ppy",
  "type_id": null,
  "properties": {
    "kind": "definition",
    "name": "barcode",
    "value_schema": {
      "type": "object",
      "required": ["text"],
      "properties": { "text": { "type": "string" } }
    }
  },
  "links": {},
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id":  "…~pp~barcode",
    "provenance": {
      "txn_id": "…",
      "commit_id": "…",
      "msg_uuid": "018fd849-2a41-7111-8a01-aaaaaaaaaaaa",
      "collection": "ppy",
      "action": "create",
      "committed_at": "…",
      "materialized_at": "…"
    }
  }
}
```

Why this shape:

- **Reuses every accepted root-shape invariant from TASK-013/14/26/29.**
  Top-level `_id`, `id`, `collection`, `type_id` (null because the wire
  has no `data.type_id`), explicit `properties`, denormalized `links:
  {}` (canonical relationships go through `lnk`, not nested under the
  property definition itself), and reserved `_head.provenance`. No new
  reserved root field, no new sub-shape, no new schema convention.
- **`data.kind` lives under root `properties.kind` as a materialized
  discriminator.** This mirrors how `typ + create` link-type records
  carry `properties.kind == "link_type"` and bare entity-type `typ`
  records carry no `kind`. For `ppy`, the discriminator is meaningful
  because it lets a future reader filter the `ppy` collection by
  `properties.kind == "definition"` without re-reading the original
  wire envelope. The materializer does not invent the value; it copies
  exactly what the wire said.
- **`data.value_schema` is copied verbatim into
  `properties.value_schema` as an opaque JSON object.** No walking
  into nested keys at materializer time, no validation that nested
  keys are snake_case (the schema layer already does that for
  ingestion), and no enforcement that the schema is valid JSON Schema
  Draft 2020-12. Future readers and the future assignment-validator
  layer interpret it; the materializer treats it as opaque metadata.
  This matches the kafka-vocabulary doc's "the envelope schema does
  not yet validate the wrapper shape against the registered
  `value_schema`" pin (line 102).
- **`type_id: null`** because the wire example does not declare a
  property *type* (the property-definition is itself a kind of type;
  there is no parent-type relationship to record). Matches how the
  bare entity-type `typ + create` materialized doc carries
  `type_id == null` today (per
  `EntityCreateKafkaMaterializeIntegrationSpec` line 274) and how the
  `grp + create` materialized doc carries `type_id == null`.
- **Achieved with zero new code** in `buildDocument`: the existing
  inline-properties fallback already copies all `data` keys other than
  `id`/`type_id`/`links` into root `properties`. For 02-… that is
  exactly `kind`, `name`, `value_schema`. The implementation only
  needs the new `COLLECTION_PPY` constant, the kind-discriminator gate
  in `isSupported`, and a doc edit. No new branch in `buildDocument`.
- **Survives later iterations**: when `ppy + update` definition edits,
  `ppy + create` assignment materialization, or richer
  property-definition metadata (e.g. `description`, `unit`) arrive,
  they are additive and do not require a re-shape of the root.

**Alternatives** (flagged for director ruling):

- **Alternative A — drop `data.kind` from root `properties` (don't
  materialize the discriminator).** Slightly smaller materialized
  document; the wire still carries `kind`, and a reader could go back
  to the `txn` log to recover it. But this loses the cheap
  `properties.kind == "definition"` MongoDB filter on the long-term
  `ppy` collection and forces every consumer to either re-read the
  source message or know-out-of-band that a `ppy` doc is necessarily
  a definition. **Default proposal rejects this**; the directive
  explicitly asks "whether `data.kind` remains materialized as a
  discriminator", and the cheap-filter argument is the load-bearing
  reason to keep it.
- **Alternative B — promote `data.kind` to a top-level reserved root
  field next to `_id`/`id`/`collection`/`type_id`.** Cleaner separation
  from user-facing `properties` scalars, but it introduces a new
  reserved root field that no current reader expects. Diverges from
  how `typ + create` link-type discrimination is materialized today
  (under root `properties.kind`). Rejected as inconsistent with the
  established `typ` precedent.
- **Alternative C — promote `data.value_schema` to a top-level
  reserved root field** (e.g., `value_schema:` next to
  `_id`/`id`/...). Would let downstream readers index it without
  walking into `properties.*`, but again diverges from the established
  pattern that domain-fact JSON lives under root `properties`. The
  property-definition's `value_schema` is a domain fact (a contract
  for assigned values), not a materializer-implementation detail.
  Rejected.
- **Alternative D — explicit `data.properties` block on the wire.**
  Would make the canonical 02-…/03-… messages match the
  `loc`/`ent`/`lnk` convention (`data: { id, type_id?, properties:
  {...}, links: {} }` envelope) more strictly. But the directive
  explicitly says "Add or update example resource JSON only if the
  existing examples do not already show the accepted human-readable
  shape and transaction sequence" — and the existing flat shape
  matches the bare entity-type `typ + create` precedent (TASK-029
  lines 460–483 of `MessageSpec` accept exactly the flat
  `data.id/name/description` shape on `04-…` with no `data.properties`
  block). The inline-properties fallback was added for exactly this
  case. **Default proposal preserves the flat wire shape** verbatim;
  no `02-…`/`03-…` edit.

#### 2. Whether `data.kind == "assignment"` is materialized in this iteration

**Recommendation: NO. Defer to a separate follow-up task.**

Rationale:

1. **The directive explicitly asks**: "Decide whether this bounded
   proof should materialize only `data.kind == "definition"` and
   continue skipping `data.kind == "assignment"` property-value
   examples, or whether a separate follow-up task is required for
   assignments." The bounded-proof framing nudges toward only one
   sub-shape per task, mirroring the TASK-027 → 28 → 29 → 30 rhythm
   (one human-readable wire shape per task).
2. **Different wire shape**: assignment carries `entity_id`,
   `property_id`, and `value` instead of `kind == "definition"`'s
   `name` and `value_schema`. Materializing both in one task would
   require either two `processMessage`-level branches (one for each
   `kind`) or a single branch with kind-specific buildDocument logic.
   Either is a broader change than the bounded-proof framing.
3. **Different semantic ownership**: an assignment is a property *value*
   for a specific entity. Open product questions (not for TASK-031 to
   resolve): should the assignment materialize as a separate `ppy`
   document, as a sub-document on the entity's `ent` root, or both?
   How do later assignment edits and deletes interact? How do
   permission scopes attach? These belong in a dedicated task.
4. **Out-of-scope guardrails**: TASK-031 OUT_OF_SCOPE explicitly
   forbids "value-shape validation against `data.value_schema`" — that
   forbid is consistent with the assumption that assignments are not
   yet materialized (otherwise the materializer would have to choose
   whether or not to validate the value shape on every assignment).
5. **Implementation cost**: skipping the assignment kind keeps the
   `isSupported` change to one switch case plus one nested condition
   (`data.kind == "definition"`); the assignment shape continues to
   fall through `isSupported` and is counted as `skippedUnsupported`.
   Zero code, zero risk for the assignment path.

**Implication:** the materializer's new `ppy + create` branch is gated
on `data.kind == "definition"`. Anything else — `kind == "assignment"`,
missing `kind`, unknown `kind` value — falls through to
`skippedUnsupported`. The existing `'skips a ppy create message'` unit
test is replaced by **two** features: positive `definition`
materializes, and negative `assignment`/missing/other still skips.

#### 3. How `value_schema` is copied without validating future assignment values

**Recommendation: copy verbatim as an opaque JSON object** under root
`properties.value_schema`.

Rationale:

1. The directive forbids "value-shape validation against
   `data.value_schema`" (TASK-031 OUT_OF_SCOPE). That forbid applies to
   future assignment-value validation; the materializer is not the
   place for that.
2. The wire `value_schema` is conceptually a JSON Schema for assigned
   values. Treating it as opaque keeps the materializer free of any
   JSON-Schema interpretation library or version pin, which would be a
   substantial cross-cutting addition.
3. The existing `copyProperties` helper does a shallow `LinkedHashMap`
   copy. Nested maps inside `value_schema` (e.g. the `properties`
   sub-map and each property's `{ type: "string" }` sub-map) remain
   shared by reference with the source `data.value_schema`. This is
   safe because (a) the materializer is the sole writer of the
   materialized doc per snapshot, and (b) the source `data` map comes
   from a per-message Jackson parse that is not retained beyond the
   `processMessage` reactive chain.
4. Mongo's BSON encoding accepts the nested object structure verbatim;
   no schema-file change, no codec registration, no JSON-Schema
   metaschema pinning required.
5. The existing `isSamePayload` already deep-equals the entire
   materialized doc (minus `_head.provenance.materialized_at`), so the
   nested `value_schema` participates in idempotent-vs-conflicting
   duplicate detection out of the box.

**Implication:** future readers (or a future assignment-validator
layer) walk into `properties.value_schema` to interpret it. The
materializer does nothing with it. No special-case code in
`buildDocument` is required.

#### 4. Materializer behaviour for missing/blank `data.id`, idempotent duplicate, conflicting duplicate

The directive asks for focused coverage on these cases.

- **Missing/blank `data.id`** — `extractDocId` already returns `null`
  for missing or whitespace-only `data.id`. `processMessage` then
  counts `result.skippedInvalid++` and returns without touching Mongo
  (existing behaviour, lines 188–195). New code path: none. New
  feature: `'skips a ppy + create kind=definition with missing or
  blank data.id as skippedInvalid'` (Plan §4).
- **Idempotent duplicate** (same wire payload, same `data.id`,
  arrives committed twice; or the existing materialized `ppy` doc
  already carries the identical payload) — `mongoTemplate.insert`
  raises `DuplicateKeyException`; `handleInsertError` calls
  `mongoTemplate.findById(docId, Map.class, "ppy")`,
  `isSamePayload(existing, incoming)` deep-equals (minus
  `materialized_at`), result is `duplicateMatching++`. No
  `_head.provenance` perturbation, no overwrite, idempotent. New
  feature: `'idempotent ppy + create kind=definition with matching
  payload is duplicateMatching and does not overwrite'` (Plan §4).
- **Conflicting duplicate** (same `data.id` but different `name`, or
  different `value_schema`) — same `findById` + `isSamePayload` path;
  result is `conflictingDuplicate++`, log line, no overwrite. New
  feature: `'conflicting ppy + create kind=definition with differing
  payload is conflictingDuplicate and does not overwrite'` (Plan §4).
- **Cross-collection ID confusion**: `02-…`'s `data.id` is
  `"…~pp~barcode"`. If a future producer accidentally publishes a
  `loc + create` with `data.id == "…~pp~barcode"`, those documents
  land in the `loc` collection, not `ppy`, so the duplicate-key index
  on `ppy._id` is unaffected. No new code or test required.

All three cases reuse the existing duplicate-handling and skip-counting
plumbing. **No new `MaterializeResult` field is required** for
TASK-031.

### Smallest implementation plan

Goal: smallest set of changes that (1) lets the existing `02-…` and
`03-…` materialize as root-shaped `ppy` documents through the existing
insert pipeline, (2) preserves the existing `skippedUnsupported`
behaviour for `ppy + create kind=assignment` and every other
collection/action skip, and (3) keeps the existing duplicate-handling,
provenance, and read-service contracts intact.

#### File changes

1. **No example resource edits** in
   `libraries/jade-tipi-dto/src/main/resources/example/message/`.
   - `02-create-property-definition-text.json` and
     `03-create-property-definition-numeric.json` already show the
     accepted human-readable wire shape verbatim (top-level
     `collection: "ppy"` + `action: "create"`, with `data.kind ==
     "definition"`, `data.id`, `data.name`, `data.value_schema`). The
     cross-references (`05-….data.property_id == 02-….data.id`,
     `07-….data.property_id == 02-….data.id`,
     `08-….data.property_id == 03-….data.id`) are already correct
     verbatim.
   - `07-…` and `08-…` continue to declare the canonical assignment
     shape (`kind: "assignment"`, `entity_id`, `property_id`, `value`).
     They remain `skippedUnsupported` — no edit required.
   - The directive forbids broad ID-abbreviation cleanup, so the
     `~pp~` 2-char segments are preserved verbatim.

2. **Materializer code changes** in
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:
   - Add new constants near the existing collection / action / field
     constants:
     ```groovy
     static final String COLLECTION_PPY = 'ppy'
     static final String FIELD_KIND     = 'kind'
     static final String KIND_DEFINITION = 'definition'
     ```
   - Extend `isSupported(...)` (lines 346–372) to accept
     `collection == COLLECTION_PPY` paired with `action ==
     ACTION_CREATE` paired with `data.kind == KIND_DEFINITION`. Every
     other `ppy` action and every other `data.kind` value (including
     `KIND_ASSIGNMENT == 'assignment'`, missing `kind`, and any other
     value) continues to fall through to `skippedUnsupported`.
     Concretely:
     ```groovy
     if (message.action == ACTION_CREATE) {
         switch (message.collection) {
             case COLLECTION_LOC: return true
             case COLLECTION_LNK: return true
             case COLLECTION_GRP: return true
             case COLLECTION_ENT: return true
             case COLLECTION_TYP: return true
             case COLLECTION_PPY:
                 return KIND_DEFINITION == message.data?.get(FIELD_KIND)
             default: return false
         }
     }
     // (existing typ + update add_property branch unchanged)
     ```
     The `KIND_DEFINITION` constant declaration above intentionally
     omits a `KIND_ASSIGNMENT` constant — TASK-031 does not need to
     name `'assignment'` in code because the gate is "definition or
     skip" and the skip path is the default.
   - **No change to `processMessage`, `buildDocument`,
     `buildInlineProperties`, `copyProperties`, `buildHead`,
     `handleInsertError`, `isSamePayload`, `extractDocId`, or
     `MaterializeResult`.** The new `ppy + create kind=definition`
     branch flows through the same pipeline as `loc + create` /
     `ent + create` / `grp + create`. Because the canonical wire shape
     for 02-…/03-… does not include a `data.properties` block, the
     `buildDocument` "inline-properties fallback" branch (lines
     402–408) handles the projection automatically: `kind`, `name`,
     `value_schema` all land under root `properties` after `id` is
     excluded.
   - Update the class Javadoc (lines 29–84) to add a `ppy + create`
     bullet next to the existing `typ + create` description, to call
     out the kind-discriminator gate, and to amend the "Every other
     collection/action combination" sentence to read "Every other
     collection/action combination — including delete and txn-control
     actions, every `typ + update` whose `data.operation` is not
     `"add_property"`, and every `ppy + create` whose `data.kind` is
     not `"definition"` — is counted as `skippedUnsupported` without
     raising an error."

3. **`MaterializeResult` change**: **none.** All five existing
   counters cover the new code paths: `materialized` (success),
   `duplicateMatching` (idempotent), `conflictingDuplicate` (differing
   payload), `skippedUnsupported` (kind != definition), `skippedInvalid`
   (missing/blank `data.id`).

4. **Backend unit tests** in
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`:
   - **Rename** the existing `propertyCreateMessage()` helper (lines
     164–177) to `propertyDefinitionCreateMessage()` and add a wire
     shape that carries the canonical `data.value_schema` (mirroring
     `02-…`'s `{ type: "object", required: ["text"], properties:
     { text: { type: "string" } } }`), so the new positive feature can
     assert the materialized `properties.value_schema` round-trips.
     Keep the helper's synthetic ID — see Open question #5 for the
     `~pp~` vs `~ppy~` segment choice.
   - **Add** a new `propertyAssignmentCreateMessage()` helper (or
     overload the renamed helper with a `kind = 'assignment'` flag)
     that builds the canonical `kind: "assignment"` shape:
     `[id: SOME_ASSIGNMENT_ID, kind: 'assignment', entity_id: ENT_ID,
     property_id: PPY_ID, value: [text: 'barcode-1']]`. Used only by
     the new "assignment is still skippedUnsupported" feature.
   - **Replace** the existing `'skips a ppy create message'` feature
     (lines 1209–1218) with positive feature `'materializes a ppy +
     create kind=definition as a root document with kind, name, and
     value_schema in root properties'`:
     - Mock `mongoTemplate.insert(_ as Map, 'ppy')` to capture the
       inserted document.
     - Snapshot `[propertyDefinitionCreateMessage()]`.
     - Assert `result.materialized == 1`,
       `result.duplicateMatching == 0`,
       `result.conflictingDuplicate == 0`,
       `result.skippedUnsupported == 0`,
       `result.skippedInvalid == 0`,
       `result.skippedMissingTarget == 0`.
     - Assert captured root: `_id == PPY_ID`, `id == PPY_ID`,
       `collection == 'ppy'`, `type_id == null`,
       `properties.kind == 'definition'`,
       `properties.name == 'barcode'`,
       `properties.value_schema == [type: 'object',
        required: ['text'], properties: [text: [type: 'string']]]`,
       `links == [:]`.
     - Assert `_head` carries `schema_version == 1`,
       `document_kind == 'root'`, `root_id == PPY_ID`, and
       `provenance.txn_id`/`commit_id`/`msg_uuid`/`collection`/
       `action`/`committed_at`/`materialized_at` set correctly.
     - Assert `!captured.containsKey('_jt_provenance')` (no legacy
       sub-doc leak).
   - **Add** `'skips a ppy + create kind=assignment as
     skippedUnsupported'`:
     - Snapshot `[propertyAssignmentCreateMessage()]`.
     - Assert `result.materialized == 0`,
       `result.skippedUnsupported == 1`,
       `0 * mongoTemplate.insert(_, _)`,
       `0 * mongoTemplate.findById(_, _, _)`.
   - **Add** `'skips a ppy + create with missing or other data.kind as
     skippedUnsupported'` (where-block over
     `[null, '', '   ', 'unknown', 'something_else']`):
     - Build a `propertyDefinitionCreateMessage()` variant with the
       chosen kind value.
     - Assert `result.skippedUnsupported == 1`,
       `result.materialized == 0`,
       `0 * mongoTemplate.insert(_, _)`.
   - **Add** `'skips a ppy + create kind=definition with missing or
     blank data.id as skippedInvalid'` (where-block over `[null, '',
     '   ']`):
     - Build a `propertyDefinitionCreateMessage()` variant with
       `id: input`.
     - Assert `result.skippedInvalid == 1`,
       `result.materialized == 0`,
       `0 * mongoTemplate.insert(_, _)`.
   - **Add** `'idempotent ppy + create kind=definition with matching
     payload is duplicateMatching and does not overwrite'`:
     - Mock `mongoTemplate.insert(_ as Map, 'ppy')` to throw
       `DuplicateKeyException`.
     - Mock `mongoTemplate.findById(PPY_ID, Map.class, 'ppy')` to
       return the same materialized doc the materializer would have
       written (computed via the same `propertyDefinitionCreateMessage()`).
     - Snapshot `[propertyDefinitionCreateMessage()]`.
     - Assert `result.duplicateMatching == 1`,
       `result.materialized == 0`,
       `result.conflictingDuplicate == 0`.
     - Mirrors the existing `loc`/`ent`/`grp` matching-duplicate
       features.
   - **Add** `'conflicting ppy + create kind=definition with differing
     payload is conflictingDuplicate and does not overwrite'`:
     - Mock `mongoTemplate.insert(_ as Map, 'ppy')` to throw
       `DuplicateKeyException`.
     - Mock `mongoTemplate.findById(PPY_ID, Map.class, 'ppy')` to
       return a materialized doc whose
       `properties.value_schema.required` differs from the incoming
       (e.g., `['name']` vs `['text']`).
     - Snapshot `[propertyDefinitionCreateMessage()]`.
     - Assert `result.conflictingDuplicate == 1`,
       `result.materialized == 0`,
       `result.duplicateMatching == 0`.
     - Mirrors the existing `loc`/`ent`/`grp` conflicting-duplicate
       features.
   - **Update** the mixed-snapshot feature (lines 1625–1665):
     - Use `propertyDefinitionCreateMessage()` (via the renamed
       helper) instead of `propertyCreateMessage()`.
     - Add `mongoTemplate.insert(_ as Map, 'ppy') >>` block that
       captures `'ppy'` into `insertOrder`.
     - Change the when-block label to `'snapshot has loc, ppy
       definition, typ link-type, typ bare entity-type, ent, lnk in
       that order'`.
     - Change the then-block expectations to
       `insertOrder == ['loc', 'ppy', 'typ', 'typ', 'ent', 'lnk']`,
       `result.materialized == 6`,
       `result.skippedUnsupported == 0`,
       `result.duplicateMatching == 0`,
       `result.conflictingDuplicate == 0`,
       `result.skippedInvalid == 0`.
   - **Optionally add** a sibling mixed-snapshot feature
     `'mixed snapshot with both ppy definition and ppy assignment
     materializes only the definition'` that pushes both messages and
     asserts `materialized == 6` (or 7 if other messages are also
     present), `skippedUnsupported == 1` (the assignment). Default
     proposal: skip this addition; the dedicated assignment-skip
     feature plus the definition-only mixed-snapshot already cover the
     codepaths.

5. **DTO library tests** in
   `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:
   - **Add** `'ppy + create definition example uses the human-readable
     kind, id, name, and value_schema shape'`:
     - Reads `02-create-property-definition-text.json`.
     - Asserts `message.collection() == Collection.PROPERTY`,
       `message.action() == Action.CREATE`,
       `data.kind == 'definition'`,
       `data.id == 'jade-tipi-org~dev~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode'`,
       `data.name == 'barcode'`,
       `data.value_schema.type == 'object'`,
       `data.value_schema.required == ['text']`,
       `data.value_schema.properties.text.type == 'string'`,
       `data.keySet() == ['kind', 'id', 'name', 'value_schema'] as Set`.
   - **Add** `'ppy + create numeric definition example uses
     kind=definition with a multi-key value_schema'`:
     - Reads `03-create-property-definition-numeric.json`.
     - Asserts `data.kind == 'definition'`,
       `data.id.endsWith('~pp~volume')`, `data.name == 'volume'`,
       `data.value_schema.required == ['number', 'unit_id']`,
       `data.value_schema.properties.number.type == 'number'`,
       `data.value_schema.properties.unit_id.type == 'string'`.
   - **Add** `'ppy + create assignment example uses the human-readable
     kind, id, entity_id, property_id, and value shape'`:
     - Reads `07-assign-property-value-text.json`.
     - Asserts `message.collection() == Collection.PROPERTY`,
       `message.action() == Action.CREATE`,
       `data.kind == 'assignment'`,
       `data.entity_id.endsWith('~en~plate_a')`,
       `data.property_id.endsWith('~pp~barcode')`,
       `data.value == [text: 'barcode-1']`,
       `data.keySet() == ['kind', 'id', 'entity_id', 'property_id',
        'value'] as Set`.
     - Documents the canonical assignment shape so a future
       assignment-materialization task does not have to rediscover it.
   - **Add** `'property-definition transaction example sequence shares
     one txn id and the assignment example references the
     property-definition and entity by id'`:
     - Reads `01-…`, `02-…`, `04-…`, `06-…`, `07-…`, `09-…`.
     - Asserts all six share the same `txn.uuid`.
     - Asserts the assignment cross-references hold verbatim:
       `07-….data.entity_id == 06-….data.id` and
       `07-….data.property_id == 02-….data.id`.
     - Documents the full author flow even though `07-…` is not yet
       materialized.
   - The existing `'entity-type-with-property example sequence …'`
     feature (lines 568–608) already covers `01 + 02 + 04 + 05 + 09`
     and remains unchanged.

6. **Backend integration test** in
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/`:
   - **Default proposal:** add a new dedicated spec
     `PropertyDefinitionCreateKafkaMaterializeIntegrationSpec.groovy`,
     modeled after `EntityCreateKafkaMaterializeIntegrationSpec` but
     publishing exactly `open + 02-ppy-definition + commit` (three
     messages) and asserting:
     - The committed `txn` header reaches `state == 'committed'` and
       carries a `commit_id`.
     - The materialized `ppy` document at `_id == propertyDefinitionId`
       carries `collection == 'ppy'`, `type_id == null`,
       `properties.kind == 'definition'`,
       `properties.name == 'barcode'`,
       `properties.value_schema == [type: 'object', required: ['text'],
        properties: [text: [type: 'string']]]`, and the expected
       `_head.provenance.collection == 'ppy'` /
       `_head.provenance.action == 'create'` / etc.
     - **Optionally** also publishes `07-ppy-assignment` and asserts
       no `ppy` document exists at the assignment ID, proving the
       skip-still-skips behaviour end-to-end.
   - The new spec keeps the same opt-in gate as the existing kafka
     specs (`JADETIPI_IT_KAFKA=1` + Kafka reachable). ~150 lines of
     mostly-boilerplate (producer / consumer / topic setup), ~50 lines
     of the actual feature.
   - **Alternative:** extend the existing
     `EntityCreateKafkaMaterializeIntegrationSpec` to publish
     `open + 02-ppy + 04-typ + 05-typ-update + 06-ent + commit` and
     add the `ppy` assertion block. Smaller diff; more crowded
     existing spec. **Default rejected** because the entity spec is
     already large after TASK-030's add_property block, and a
     dedicated property-definition spec gives the future
     property-assignment task a clear extension point.
   - **If** the director prefers no integration change, the
     materializer behaviour is fully covered by the unit features
     above. The end-to-end Kafka → Mongo proof for property
     definitions would then be deferred. See Open question #4.

7. **Documentation updates:**
   - `docs/architecture/kafka-transaction-message-vocabulary.md`:
     - Lines 79–102 ("Property Definitions"): append a paragraph
       describing the materialized root shape: `_id == data.id`,
       `collection: "ppy"`, `type_id: null`, root `properties.kind ==
       "definition"`, `properties.name`, `properties.value_schema`
       (verbatim opaque copy), `links: {}`, `_head.provenance.collection
       == "ppy"`. Note the materializer does not validate
       `value_schema` against future assignment values; that lookup
       still belongs to a future read-time validator.
     - Lines 257–263 ("Committed Materialization Of Locations And
       Links"): expand the supported set to add `ppy + create` whose
       `data.kind == "definition"`, and update the
       `skippedUnsupported` sentence to call out `ppy + create` whose
       `data.kind != "definition"` (including `"assignment"`) as
       still skipped.
     - Lines 138–158 ("Property Value Assignment"): add a one-sentence
       note that assignment materialization remains a separate future
       task; the canonical assignment wire shape is preserved
       verbatim.
   - `docs/OVERVIEW.md`: lines 323–328 — add `ppy + create
     kind=definition` to the materialization next-steps bullet next
     to the existing `loc + create` / `typ + create` /
     `typ + update add_property` mention. Anticipated 1–2 line edit;
     no structural change.
   - `docs/architecture/jade-tipi-object-model-design-brief.md`: no
     change anticipated; the brief stays at the logical model level
     and is agnostic to the physical sub-map placement.
   - `DIRECTION.md`: no change anticipated; the document already
     describes property definitions as first-class objects at the
     human-language level.

#### Expected total surface (with default proposals selected)

- **0 example resource edits.**
- ~15–25 lines of materializer source change in
  `CommittedTransactionMaterializer.groovy` (3 new constants, one
  switch case branch, one nested `data.kind` check, doc edits). No
  refactoring of existing create / insert / duplicate-handling paths.
- 0 lines in `MaterializeResult.groovy` (no new counter required).
- ~150–220 lines of new/changed Spock features in the materializer
  spec (1 feature flipped, 5–6 new features, 1 mixed-snapshot
  update, helper rename, optional sibling mixed-snapshot).
- ~80–120 lines of three new features in `MessageSpec` (text
  definition, numeric definition, assignment shape, optional 6-message
  txn chain).
- ~150–220 lines of new
  `PropertyDefinitionCreateKafkaMaterializeIntegrationSpec.groovy`
  (mostly boilerplate; the actual feature is ~50 lines).
- ~20–30 lines of doc edit across
  `kafka-transaction-message-vocabulary.md` + `docs/OVERVIEW.md`.

#### Cross-cutting impact (read of source on `claude-1`)

- `ContentsLinkReadService` queries `typ` for `properties.kind ==
  "link_type"` and `properties.name == "contents"`. Materializing
  `ppy` documents with `properties.kind == "definition"` does not
  affect those criteria (different collection, and `kind` value is
  scoped to the `typ` filter). No read-service change required.
- `GroupAdminService` writes only `grp` documents. Unaffected.
- `TransactionService` / `CommittedTransactionReadService` /
  persistence paths treat message `data` as opaque. Unaffected.
- `kli` CLI and frontend already accept `--collection ppy --action
  create` and pass `data` through unchanged. No CLI / UI surface
  change is needed for human submitters.
- TASK-030's `typ + update add_property` branch records
  `properties.property_refs.<property_id>` *references* on the type
  root by ID. TASK-031 produces the actual `ppy` documents that those
  IDs would resolve to, but the materializer still does not perform
  semantic resolution between the two collections — the directive
  forbids it (TASK-031 OUT_OF_SCOPE: "no semantic validation that
  `typ.data.property_id` resolves").
- `06-create-entity.json`'s `ent` materialization is unaffected; it
  references the type by `data.type_id`, not the property definition.
- `07-…` and `08-…` continue to be ingested into the `txn` write-
  ahead log, validated by the schema, and stored as committed
  messages — they just don't produce a long-term `ppy` collection
  document yet.

#### Out-of-scope guardrails (will **not** edit unless director ruling expands scope)

- `clients/kafka-kli/**` — no CLI change is needed.
- `frontend/**` — no UI surface for raw property-definition submission.
- `message.schema.json` — see Schema status above.
- `frontend/.env.local` — generated; never hand-edited.
- `02-…`, `03-…`, `07-…`, `08-…` — wire shapes are already canonical
  and are exercised by the existing schema/round-trip loop. Plan §5
  adds focused asserts without editing the JSON files.
- `04-…`, `05-…`, `06-…` — TASK-028 / TASK-029 / TASK-030 already
  landed the accepted shapes; do not edit.
- Authentication, Keycloak, admin group-management, permission
  enforcement — out of scope per active focus.

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    property-definition examples plus the new focused MessageSpec
#    features.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new ppy + create kind=definition root materialization, the
#    kind=assignment / missing-kind / unknown-kind skip paths, the
#    missing-id skipped-invalid case, and the duplicate-matching /
#    conflicting-duplicate paths. Other unit tests (group, contents
#    read, transaction service, etc.) are unaffected and rerun for
#    regression confirmation.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running). The new dedicated
#    PropertyDefinitionCreateKafkaMaterializeIntegrationSpec drives an
#    end-to-end open + 02-ppy + commit -> materialized ppy proof.
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*PropertyDefinitionCreateKafkaMaterializeIntegrationSpec*'

# Optional regression checks (rerun if local stack is healthy):
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'
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
  before retrying (per `DIRECTIVES.md` TASK-026/27/28/29/30 review
  notes that consistently recommend `./gradlew --stop` when the
  wrapper cache lock fails with "Operation not permitted").
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

1. **Whether to materialize `data.kind` as `properties.kind ==
   "definition"` on the `ppy` root.** Default proposal: YES (mirrors
   `typ` link-type discrimination via `properties.kind == "link_type"`;
   gives downstream readers a cheap MongoDB filter). Alternative: drop
   `kind` from the materialized `properties` map. See Decision §1.
2. **Whether to materialize `ppy + create kind=assignment` in this
   iteration.** Default proposal: NO; defer to a separate follow-up
   task. The bounded proof covers only definition. See Decision §2.
3. **`value_schema` placement and treatment.** Default proposal: copy
   verbatim under root `properties.value_schema` as an opaque JSON
   object; no validation, no walk into nested keys at materializer
   time. Alternatives: promote to a top-level reserved field, or
   normalize/parse it at materializer time. See Decision §3.
4. **Integration spec scope.** Default proposal: add a dedicated
   `PropertyDefinitionCreateKafkaMaterializeIntegrationSpec` (~150
   lines mostly boilerplate). Alternatives: extend
   `EntityCreateKafkaMaterializeIntegrationSpec` in place; or rely on
   unit tests only and defer integration coverage. See Plan §6.
5. **`propertyCreateMessage()` Spock helper's `~ppy~` segment vs.
   `02-…` JSON's `~pp~` segment.** The Spock helper at
   `CommittedTransactionMaterializerSpec.groovy:171` uses
   `'…~018fd849-2a41-7111-8a01-cccccccccccc~ppy~barcode'` while
   `02-create-property-definition-text.json` uses
   `'…~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~pp~barcode'`. Both are
   accepted today because the materializer treats `data.id` as opaque
   and nothing resolves the property ID semantically. The helper's
   value is internal to the spec; the JSON example's value is the
   canonical wire shape. This pre-existing discrepancy was previously
   flagged by TASK-030 pre-work (Open question #6) and remains
   unaddressed. **Default proposal:** preserve both verbatim — do not
   "fix" either side per the directive's "Preserve the current example
   ID strings" guidance and the `OUT_OF_SCOPE` "Do not redesign the
   ID abbreviation scheme." The renamed
   `propertyDefinitionCreateMessage()` helper inherits whatever ID the
   director rules. Flagged here for visibility / future ID-cleanup
   follow-up; an option here is to rename the helper's synthetic ID
   from `~ppy~` to `~pp~` *only* in the helper-rename diff so the new
   tests align with the canonical wire shape, without touching any
   non-test source.
6. **Mixed-snapshot polish.** Default proposal: update the existing
   mixed-snapshot feature to expect the definition message
   materializes (counts move from `5,1` to `6,0`). Optional addition:
   a sibling mixed-snapshot feature also pushing an assignment
   message and asserting it is the only `skippedUnsupported`. Default:
   skip the addition unless the director asks for it; the dedicated
   assignment-skip feature plus the updated mixed-snapshot already
   cover the codepaths.
7. **Whether to add a 6-message DTO chain test (`01 + 02 + 04 + 06 +
   07 + 09`)** documenting the canonical author flow even though
   `07-…` is not yet materialized. Default proposal: YES (Plan §5);
   the test asserts cross-reference IDs only, never invokes the
   materializer, and gives the future assignment-materialization task
   a starting point. Alternative: defer the assignment chain test to
   that future task.
8. **Follow-up task creation.** TASK-031 covers `ppy + create
   kind=definition` only. Likely follow-on units (separate tasks):
   - `ppy + create kind=assignment` — property-value materialization,
     including whether assignments project as separate `ppy` docs,
     into the entity's `ent` root, or both.
   - `ppy + update` — definition edits (renaming, value_schema
     evolution, deprecation).
   - Semantic resolution of `typ.data.property_id → ppy` and
     `ent.data.type_id → typ` at materializer or `vdn` validator
     time.
   - Value-shape validation against the registered
     `value_schema` (a `vdn`-collection scope).
   - **Default proposal:** create no follow-on task in this pre-work
     turn; let the director sequence those after TASK-031 acceptance,
     mirroring the TASK-026 → TASK-027 → TASK-028 → TASK-029 →
     TASK-030 → TASK-031 rhythm.

If the director rules on items 1–4 (and optionally 5–7) in the next
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
to set TASK-031 to `READY_FOR_IMPLEMENTATION` (or change the global
signal to `PROCEED_TO_IMPLEMENTATION`) before making any of the
implementation-turn changes listed in the plan above.
