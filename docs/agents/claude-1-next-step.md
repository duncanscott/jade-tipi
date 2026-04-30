# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-002 — Define Kafka transaction message vocabulary (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`) and
`docs/orchestrator/tasks/TASK-002-kafka-transaction-message-vocabulary.md`
(status `READY_FOR_PREWORK`), define the durable Kafka-first message contract
for transaction-staged Jade-Tipi data submission. The directive expands my
scope to the message DTOs, JSON schema, example messages, the architecture
design note, the Kafka CLI, and the `UnitSpec` test if it blocks DTO
verification.

The pre-work goal is a plan only. Implementation must wait for the director
to flip `TASK-002` to `READY_FOR_IMPLEMENTATION` or set the directive signal
to `PROCEED_TO_IMPLEMENTATION`.

### Current baseline (read, not yet changed)

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Message.java`
  is a record `(txn, uuid, action, data)`. No `collection` field.
- `Action` enum already contains `OPEN`, `ROLLBACK`, `COMMIT`, `CREATE`,
  `UPDATE`, `DELETE`.
- `Collection` enum already contains `ent`, `grp`, `lnk`, `uni`, `ppy`, `typ`,
  `txn`, `vdn`, with per-collection valid actions (`txn` → open/rollback/commit,
  others → create/update/delete).
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  requires `txn`, `uuid`, `action` only; no `collection`.
- `libraries/jade-tipi-dto/src/main/resources/example/message/` contains
  only `open-transaction.json`, which has no `collection` field.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/` contains only
  `collections/UnitSpec.groovy` — no `message/` Spock tests yet. `UnitSpec`
  has the `when:`-followed-by-`expect:` block-label issue noted in
  `DIRECTIVES.md` (lines 47–60: `when: def units = …` then `expect: lines.size() == 812`).
- `clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/KafkaCli.groovy`
  builds messages with `Message.newInstance(txn, action, data)`. Open / rollback /
  commit hardcode the transaction action; create / update / delete pass
  through user data. No collection is set or read anywhere.
- `clients/kafka-kli/src/test/` has only `resources/sample-message.json` (no
  Spock specs). `sample-message.json` lacks `collection`.
- `docs/architecture/kafka-transaction-message-vocabulary.md` already
  describes the intended envelope, transaction record split, property
  definitions vs. assignments, and the property-value object wrapping
  convention. The plan below stays consistent with that note.

### Proposed plan

1. **`Message` DTO — add `collection` as a first-class field.**
   - Add `Collection collection` to the record between `uuid` and `action`,
     producing `(txn, uuid, collection, action, data)`. Jackson order will
     follow JSON property declarations.
   - Update `Message.newInstance(...)` to take `Collection` and add a second
     overload `newInstance(Transaction, Collection, Action, Map)` so
     callers must declare collection explicitly. Remove the no-collection
     overload to force the contract.
   - `getId()` stays `<txn.getId()>~<uuid>~<action>`. Adding collection to
     the ID is out of scope (no acceptance criterion requires it and changing
     ID format risks breaking accepted TASK-001 expectations). Will note this
     decision in the design doc.
   - Equality stays on `(txn, uuid)`; collection does not affect identity.

2. **`message.schema.json` — require and validate `collection`.**
   - Add `collection` to `required`.
   - Add `Collection` `$defs` enum mirroring the eight abbreviations.
   - Encode action/collection compatibility with a top-level
     `allOf`: `if collection == "txn" then action ∈ {open, rollback, commit}`
     and `if collection ∈ {ent, ppy, lnk, uni, grp, typ, vdn} then
     action ∈ {create, update, delete}`. This mirrors `Collection.getActions()`
     and is "valid action/collection combinations where practical" from the
     acceptance criteria.
   - Update the `examples` block at the bottom to a coherent transaction
     flow consistent with the new example files.

3. **Example messages — full early transaction flow under
   `libraries/jade-tipi-dto/src/main/resources/example/message/`.**

   Add seven files using a stable, ordered naming convention so they read
   as a sequence:
   - `01-open-transaction.json` — `collection=txn`, `action=open`,
     `data.description`.
   - `02-create-property-definition.json` — `collection=ppy`,
     `action=create`, `data.kind=definition`, `data.value_schema`
     describing a `text` scalar wrapper.
   - `03-create-property-definition-numeric.json` — second `ppy`
     definition demonstrating the `{number, unit_id}` wrapper.
   - `04-create-entity-type.json` — `collection=typ`, `action=create`.
   - `05-update-entity-type-add-property.json` — `collection=typ`,
     `action=update`, `data.operation=add_property`,
     `data.property_id`, `data.required=true`.
   - `06-create-entity.json` — `collection=ent`, `action=create`,
     `data.type_id`.
   - `07-assign-property-value-text.json` — `collection=ppy`,
     `action=create`, `data.kind=assignment`, `data.value={"text": "barcode-1"}`.
   - `08-assign-property-value-number.json` — companion assignment using
     `{"number": 10, "unit_id": "…"}`.
   - `09-commit-transaction.json` — `collection=txn`, `action=commit`.

   All examples will share the same `txn.uuid` and reference IDs that match
   the design note's `lbl_gov~jgi_pps~…~pp~…` pattern.

4. **DTO tests — `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`.**
   - Round-trip each bundled example through `JsonMapper` → `Message` → JSON
     and assert `collection` survives.
   - Call `message.validate()` on each example and assert no exception.
   - Negative cases: missing `collection`, unknown collection abbreviation,
     mismatched pair (`collection=txn` with `action=create`,
     `collection=ppy` with `action=open`) — assert `ValidationException`.
   - Property-value-shape sanity: an example assignment whose `data.value`
     is a string (not an object) is allowed by the generic `SnakeCaseValue`
     today; the schema does not constrain `data` shape at the top level.
     Document this as a known gap rather than tightening here (full
     reference validation is `OUT_OF_SCOPE`).

5. **Kafka CLI — pass `collection` through and never drop it.**
   - `open`, `rollback`, `commit` handlers: hardcode
     `Collection.TRANSACTION` when constructing the `Message`.
   - `create`, `update`, `delete` handlers (`handleEntityAction`): add a
     required `--collection` (`-c`) option accepting any of the eight
     abbreviations; parse via `Collection.fromJson` and fail with a clear
     error on unknown values. Adjust help text accordingly.
   - `publish` handler: rely on Jackson to deserialize `collection` from
     the input file. Add an explicit check that the parsed `Message.collection()`
     is non-null before publishing so we surface a missing field instead
     of silently dropping it.
   - Update `clients/kafka-kli/src/test/resources/sample-message.json` to
     include `"collection": "txn"` (it represents an `open` action).
   - Add `clients/kafka-kli/src/test/groovy/org/jadetipi/kafka/cli/KafkaCliMessageSpec.groovy`
     covering: parsing `sample-message.json` preserves `collection`;
     `Message.newInstance(txn, Collection.PROPERTY, Action.CREATE, [:])`
     serializes with the field set. No live Kafka producer in the test —
     spec only exercises the message-shape side.

6. **`UnitSpec` block-label fix — only if it blocks DTO verification.**
   - Current code uses `given: … when: units = lines.collect { … } expect: … and: units.each { it.validate() }`. Spock disallows `expect:` after
     `when:`. Restructure to `given: <setup including the collect step> expect:
     <assertions> and: <more> and: <validate-each>`. Touch only this single
     test method; keep the other test methods untouched.
   - Will only land this fix as part of TASK-002 if running
     `./gradlew :libraries:jade-tipi-dto:test` actually fails on this issue;
     otherwise I will leave it alone and report it separately as the
     directive instructs.

7. **Architecture doc updates.**
   - Edit `docs/architecture/kafka-transaction-message-vocabulary.md` to
     reflect the final decisions: collection is a top-level required field,
     `getId()` does not include collection, the schema constrains
     action/collection pairings, and the example file set is the canonical
     reference flow.

### Verification I plan to run after implementation

- `./gradlew :libraries:jade-tipi-dto:test` — must compile and pass with the
  new `MessageSpec` and any restructured `UnitSpec`.
- `./gradlew :clients:kafka-kli:test` (and `:build`) — must compile and
  pass the new CLI message spec.
- No Docker stack required for these targets; both modules avoid the
  Mongo `contextLoads` path that bites the full `./gradlew test` run.

### Blockers / open questions for the director

1. **Action–collection compatibility encoded in the schema.** Acceptance
   criteria call this out "where practical." My plan encodes the two clear
   buckets (`txn` vs. everything else) using JSON Schema `if/then/allOf`.
   If the director would rather keep the schema permissive and rely solely
   on Java-side `Collection.getActions()` for enforcement, please say so
   before implementation.
2. **`collection` for property assignment messages.** The design note keeps
   assignments under `ppy` and distinguishes definition vs. assignment via
   `data.kind`. The plan follows that. Please confirm this is still
   preferred over (for example) introducing a dedicated assignment
   collection now.
3. **Should `Message.getId()` change?** I plan to leave it alone. Adding
   collection to the ID would be a wider compatibility break than this
   task asks for, and TASK-001 was accepted with the current shape. Flag
   if you want it included.
4. **`--collection` UX in the CLI.** I plan to require `--collection` on
   `create`, `update`, and `delete`, accepting only the eight abbreviations
   (no full names) for a tight contract. Confirm before I land it; the
   alternative is making it optional with a sensible default
   (e.g., `ent`), which I think is worse but is reversible.

STOPPING here per orchestrator pre-work protocol — no implementation,
no DTO/schema/CLI/test edits, no doc edits beyond this file.
