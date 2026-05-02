# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-020 — Define and materialize group records (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `HUMAN_REQUIRED`, but the active task
file `docs/orchestrator/tasks/TASK-020-group-record-model-and-materialization.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. The orchestrator turn
prompt explicitly asks claude-1 for pre-work on TASK-020 and
authorizes nothing beyond writing this file. No
`READY_FOR_IMPLEMENTATION` or `PROCEED_TO_IMPLEMENTATION` signal is
present, so this turn produces a plan only — no schema, example,
materializer, test, or doc edit will be made.

The directive itself, distilled:

- Make `grp` records first-class Jade-Tipi root-document objects with
  world-unique IDs, optional `type_id`, properties, possible links,
  and a permissions map that grants other groups either read/write
  (`rw`) or read-only (`r`) access. No other permission values.
- Persist the data needed for later property-scope permission checks,
  but do not enforce reads or writes anywhere yet (HTTP, Kafka,
  materializer, read services).
- Preserve the existing model where users belong to groups through
  Keycloak claims or a future membership service. Do not add a
  membership collection, sync layer, admin UI, or group-management
  endpoint.
- Keep `_head.provenance` aligned with the `TASK-014` root contract
  (already implemented in `CommittedTransactionMaterializer`).
- Add one canonical `grp + create` Kafka message example, extend
  focused DTO/example tests so the example round-trips and validates
  against `message.schema.json`, and extend
  `CommittedTransactionMaterializer` (plus its focused Spock spec) so
  committed `grp + create` messages materialize into the `grp`
  MongoDB collection using the same root shape as the existing
  supported roots.

### Source survey driving the plan

- `DIRECTION.md` §"Groups And Permissions" already states the
  first-pass model: world-unique grp IDs, optional `type_id`,
  properties, possible links, permissions map keyed by other groups,
  with values restricted to `rw` or `r`. Finer-grained overrides are
  deferred. This task implements the first physical projection of
  that model and the canonical message example.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  already accepts `collection: "grp"` paired with
  `action ∈ {create, update, delete}` (the second `allOf` rule lists
  `grp` explicitly). No schema edit is required to accept a
  `grp + create` envelope. The schema's `SnakeCaseObject` rule
  enforces `propertyNames` matching `^[a-z][a-z0-9_]*$` for every
  object key inside `data`. Group IDs contain `~` separators
  (`<org>~<grp>~<txn-uuid>~grp~<short>`), so they cannot be used
  directly as map keys inside `data.permissions` without violating
  the schema. This shapes decision D-20-A below.
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  already declares `GROUP("group", "grp")` with the standard
  `[CREATE, UPDATE, DELETE]` action list, so the DTO enum side is
  ready.
- `CommittedTransactionMaterializer`'s `isSupported(...)` switch only
  whitelists `loc`, `lnk`, and link-type `typ`. The `default` branch
  in `buildDocument` (anything other than `lnk`) routes everything
  except `id` and `type_id` into `properties` via
  `buildInlineProperties`, then writes `links: {}` and
  `_head.provenance`. This default path is exactly the shape the
  task asks for `grp`. Adding `grp` requires a single new switch arm
  (`case COLLECTION_GRP: return true`) plus a constant; no new
  formatting code is needed.
- `CommittedTransactionMaterializerSpec` already covers the generic
  duplicate paths (matching duplicate, conflicting duplicate,
  non-duplicate insert failure) for `loc + create`. Generic
  duplicate behavior for `grp` flows through the same
  `handleInsertError` code, so per the task wording ("where existing
  coverage does not already cover the generic path") only
  grp-specific assertions are needed: success, unsupported actions,
  and missing/blank id.
- `docs/architecture/kafka-transaction-message-vocabulary.md` lacks a
  `grp` section today. The task acceptance asks for "the first-pass
  `grp` root-document shape" in architecture documentation, which
  fits naturally next to the existing "Committed Materialization Of
  Locations And Links" and "Reading `contents` Links" sections.
- `docs/Jade-Tipi.md`, `docs/README.md`, and
  `docs/user-authentication.md` are all listed as owned paths but do
  not need substantive edits; the architecture-vocabulary doc is the
  right home for the first-pass shape, and `DIRECTION.md` already
  carries the product direction. I will leave those three untouched
  unless review of their current text reveals a contradiction during
  implementation.

### Decisions for the implementation

D-20-A — **Permissions payload shape: list of objects, not a map.**
`data.permissions` will be a JSON array of
`{ "grp_id": "<other-group-id>", "level": "rw" | "r" }` entries.
Rationale: the schema's snake_case `propertyNames` rule rejects
group IDs as direct map keys, and a list keeps the example
round-trippable through the existing schema with no schema change.
The materializer copies `data.permissions` verbatim into root
`properties.permissions`, so future readers can index it as a list
or transform it into a map at query time without re-materialization.
The level value is constrained to `rw` or `r` in this task's example
and documentation; no broader enumeration is introduced. (See
Q-20-A if the director prefers a map-shaped payload behind a
schema-key relaxation, but I propose the list form as the default.)

D-20-B — **Permissions live under root `properties`, not as a
top-level reserved field.** The materializer's existing default
branch already routes non-`id`/non-`type_id` fields into
`properties`, matching the existing `loc + create` and
link-type `typ + create` behavior. Adding a top-level reserved field
for permissions would require new materializer code and would
diverge from the accepted root contract. Future enforcement code
can read `properties.permissions` directly; if a later task wants
to lift it under `_head` for tighter access-control isolation, that
move is a small follow-up.

D-20-C — **No `type_id` in the canonical example.** The first-pass
example matches the existing `10-create-location.json` and
`11-create-contents-type.json` style, both of which omit `type_id`.
The materializer treats missing `data.type_id` by writing top-level
`type_id: null`. A `grp` typing taxonomy is not needed for the
first canonical example; if/when groups have subtypes (`tenant`,
`team`, `service-account`, etc.), a follow-up task can add a
`grp` `type_id` example.

D-20-D — **No materialized links for the example.** The example
omits a `links` payload. Per the accepted contract, the
materializer always writes `links: {}` for new roots and leaves
endpoint-projection maintenance to a later task.

D-20-E — **Two-entry permissions list using world-unique grp IDs.**
The example will grant `rw` to one peer group and `r` to another so
both first-pass values appear in one canonical fixture and so any
future enforcement test can read both rows from a single example.

D-20-F — **No materializer behavior change beyond the
whitelist.** I will not introduce permission validation, level
normalization, sort/dedupe of the permissions list, or
`grp_id`-format checks at the materializer boundary. The data is
copied verbatim. Validation is explicitly out of scope per the task
file's `OUT_OF_SCOPE` block.

### Proposed file changes

All paths below are inside the `TASK-020` `OWNED_PATHS` block:

1. `libraries/jade-tipi-dto/src/main/resources/example/message/13-create-group.json`
   — new canonical example. Uses transaction `txn` shape from the
   existing examples. World-unique grp id of the form
   `jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics`
   (UUIDv7 distinct from the existing example messages). Payload
   carries `id`, `name`, `description`, and a two-entry
   `permissions` list as defined in D-20-A and D-20-E.

2. `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
   — append `13-create-group.json` to the `EXAMPLE_PATHS` constant
   (covers the existing parameterized round-trip and schema
   validation features). Add one focused feature method (mirroring
   the existing "contents typ example declares the canonical
   link-type facts" and "contents lnk example references the
   contents type and carries a position property" features) that
   asserts `grp + create` semantics: collection, action, id format,
   `permissions` list shape with snake_case `grp_id`/`level` keys,
   and the two `rw`/`r` rows. No other changes to this file.

3. `docs/architecture/kafka-transaction-message-vocabulary.md`
   — append a new section ("Group Records" or "Groups And First-Pass
   Permissions") that documents the canonical `grp + create`
   payload, the `permissions` list shape, the materialized root-
   document layout (`_id`, `id`, `collection: "grp"`, `type_id`,
   `properties` including `permissions`, `links: {}`,
   `_head.provenance`), and the explicit non-enforcement boundary
   (no HTTP/Kafka/materializer/read-side permission checks yet).
   Reference the canonical example `13-create-group.json` and link
   to `DIRECTION.md` for the product motivation.

4. `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
   — single targeted edit:
   - Add a `static final String COLLECTION_GRP = 'grp'` constant
     beside the existing collection constants.
   - Add `case COLLECTION_GRP: return true` in `isSupported(...)`'s
     switch on `message.collection`.
   - Update the class Javadoc bullet list of supported messages to
     include `grp + create` → `grp` collection.
   - No change to `buildDocument`, `buildInlineProperties`,
     `handleInsertError`, or any helper. The `default` branch
     already produces the correct root shape with permissions
     copied through `properties.permissions`.

5. `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
   — add focused features:
   - "materializes a grp create as a root document with permissions
     under properties and `_head.provenance`" — asserts shared
     root fields (`_id`, `id`, `collection == 'grp'`,
     `type_id == null` when omitted), `properties.name`,
     `properties.description`, `properties.permissions` list shape
     (two entries with `grp_id` + `level` `rw`/`r`),
     `links == [:]`, `_head.provenance.collection == 'grp'`,
     `_head.provenance.action == 'create'`, no `_jt_provenance`.
   - "skips a grp update message" — asserts
     `result.skippedUnsupported == 1` and zero Mongo writes when a
     `grp + update` message is supplied.
   - "skips a grp delete message" — same shape for `grp + delete`.
   - "skips a grp create with missing data.id" — asserts
     `result.skippedInvalid == 1` and zero Mongo writes.
   - "skips a grp create with blank data.id" — same with whitespace
     id, asserting trimmed-blank handling.
   - I will rely on the existing matching/conflicting/insert-error
     duplicate features (which already exercise the generic
     `handleInsertError` path through `loc`) and will not duplicate
     them for `grp` unless review identifies a `grp`-specific
     branch I have missed.

6. Optional minor touch-up in `DIRECTION.md` — append one
   clarifying sentence under the "Groups And Permissions" section
   noting that the first-pass payload uses a permissions list of
   `{grp_id, level}` rows for schema compatibility (D-20-A). This
   is non-essential; if the director prefers `DIRECTION.md` to
   stay product-focused and not record physical-shape decisions, I
   will instead document the list shape solely in
   `kafka-transaction-message-vocabulary.md`. Marked as
   conditional on Q-20-B below.

### Files intentionally not touched

- `docs/Jade-Tipi.md`, `docs/README.md`, `docs/user-authentication.md`
  — no current sentence in these conflicts with the first-pass `grp`
  shape, and the architecture-vocabulary doc is a better home for
  the on-the-wire and materialized shape. Skipping unless review
  reveals a contradiction during implementation.
- `docs/orchestrator/tasks/TASK-020-group-record-model-and-materialization.md`
  — listed in `OWNED_PATHS` but director-owned in practice. I will
  not edit it; the report file is `docs/agents/claude-1-changes.md`.
- Anything in `clients/`, `frontend/`, `docker/`, `jade-tipi/...service/`
  beyond the materializer, integration tests, security policy,
  Kafka listener, `ContentsLinkReadService`, controllers, schema,
  or `_jt_provenance` migration — explicitly out of scope by the
  task file.

### Verification plan

For this pre-work turn:

- Static review only. No Gradle, Docker, MongoDB, or Kafka commands
  executed.
- Branch-state check: working tree clean at start (`git status`
  reported clean per the orchestrator pre-amble).

For the implementation turn (only after
`READY_FOR_IMPLEMENTATION` or `PROCEED_TO_IMPLEMENTATION`):

```sh
./gradlew :libraries:jade-tipi-dto:test --tests '*MessageSpec*'
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'
./gradlew :jade-tipi:test
./gradlew :libraries:jade-tipi-dto:test
```

If local tooling, Gradle wrapper-cache locks under
`/Users/duncanscott/.gradle`, Docker, or MongoDB block verification,
the documented setup commands are:

```sh
docker compose -f docker/docker-compose.yml --profile mongodb up -d
./gradlew --stop
```

These are setup steps, not product blockers. The task file's
`VERIFICATION` block lists the same commands and the same setup
fallback.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths are modified. No code, tests, Gradle, Docker,
MongoDB, CouchDB, Kafka, frontend, security, DTO/schema,
HTTP-endpoint, `_jt_provenance`, or example changes are made or
proposed for this turn.

### Open questions / blockers

None of these block the proposed plan; they are surfaced so the
director can confirm or redirect before implementation.

- **Q-20-A — Permissions payload shape (list vs map).**
  Default proposal D-20-A: list of `{grp_id, level}` entries.
  Reason: the existing `message.schema.json` `SnakeCaseObject` rule
  rejects `~`-bearing group IDs as map keys; using a list keeps the
  schema unchanged. Alternative: relax `propertyNames` for a
  dedicated `permissions` sub-object (would require a schema edit
  outside the listed `OWNED_PATHS`, so not proposed unless
  explicitly authorized).
- **Q-20-B — `DIRECTION.md` mention of physical shape.** Default
  proposal: leave `DIRECTION.md` product-focused and document the
  list shape only in `kafka-transaction-message-vocabulary.md`.
  Optional alternative: append one clarifying sentence to the
  existing "Groups And Permissions" section. Either path stays in
  scope.
- **Q-20-C — `grp` `type_id` example.** Default proposal: omit
  `type_id` to mirror `loc`/`typ` examples and avoid introducing a
  group-typing taxonomy in this task. If the director wants the
  first canonical example to demonstrate a group `type_id`, I would
  need direction on whether to also add a companion `typ + create`
  example for that group type or to reference an unresolved string
  id.
- **Q-20-D — Coverage of `grp + create` end-to-end.** The task
  scope expansion is implementation-side only. No HTTP/Kafka
  integration test is requested. Default proposal: focused unit
  tests on `MessageSpec` and `CommittedTransactionMaterializerSpec`
  only. Confirming because TASK-016 had a parallel
  `JADETIPI_IT_KAFKA`-gated integration spec; my read of the
  TASK-020 acceptance criteria is that one is **not** wanted here.
- **Q-20-E — Owner of `_id` shape for `grp` records.** The
  generic materializer sets `_id == data.id`; that is consistent
  with `loc` and `typ`. No change requested. Calling this out
  because the permissions list keys reference grp `data.id` values,
  so the `_id` choice matters for any future reverse lookup. The
  current plan does not introduce a reverse-lookup index; that is
  future work.

STOP.
