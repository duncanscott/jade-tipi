# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-007 — Add location collection (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-007`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-007-add-location-collection.md`, add `loc`
as a first-class Jade-Tipi long-term collection so:

- The `org.jadetipi.dto.message.Collection` enum exposes
  `LOCATION("location", "loc")` and serializes / deserializes as `loc`.
- `message.schema.json` accepts `collection: "loc"` and applies the same
  data-action (`create`, `update`, `delete`) restriction it already
  applies to the other long-term domain collections; `open`, `commit`,
  and `rollback` remain transaction-only.
- The Spring Boot `MongoDbInitializer` creates the `loc` collection at
  application startup through its existing `Collection.values()` loop —
  no Docker- or Mongo-shell-side initialization is added.
- Documentation lists `location (loc)` alongside the existing
  abbreviations and preserves `txn` as the special transaction
  log/staging collection.
- Tests cover enum round-trip (`Collection.fromJson("loc")` and JSON
  serialization), schema acceptance of a canonical `loc` create message,
  schema rejection of transaction-only actions paired with `loc`, and
  startup/initializer behavior for the new collection.

Director constraints to respect (from the task `OUT_OF_SCOPE` block and
`DIRECTION.md`):

- Do not implement materialization of `loc` records from committed
  `txn` messages into a long-term `loc` collection.
- Do not implement the `contents` link type, link materialization,
  plate/well query APIs, or "what is in this plate?" reads.
- Do not add `parent_location_id` (or any other parentage field) to
  `loc` records — containment stays canonical in `lnk` per
  `DIRECTION.md`.
- Do not change the Kafka listener, transaction persistence record
  shape, commit semantics, or the committed-transaction snapshot API
  (controller / service / DTO classes from TASK-005 / TASK-006) except
  where canonical `loc` examples are needed for tests.
- Do not rebuild HTTP submission wrappers or add new authorization /
  scoping policy.
- If local tooling, Gradle locks, or Docker / Mongo are unavailable
  during verification, report the documented setup command instead of
  treating it as a product blocker.

This is pre-work only. No production source, build, config, schema,
example, test, or non-doc edits beyond this file until the director
moves `TASK-007` to `READY_FOR_IMPLEMENTATION` (or sets the global
signal to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  declares eight values today: `ENTITY("entity","ent")`,
  `GROUP("group","grp")`, `LINK("link","lnk")`,
  `UNIT("unit","uni")`, `PROPERTY("property","ppy")`,
  `TYPE("type","typ")`, `TRANSACTION("transaction","txn")`,
  `VALIDATION("validation","vdn")`. The constructor branches on
  `"transaction".equals(name)` to assign `[OPEN, ROLLBACK, COMMIT]`
  and assigns `[CREATE, UPDATE, DELETE]` to every other value, so
  adding `LOCATION("location","loc")` automatically gives `loc` the
  data-mutating action set without further branching.
  `fromJson(String)` matches by either the `abbreviation` or the
  `name`, and `@JsonValue` returns the abbreviation, so the new value
  rides the existing serialization path with no annotation churn.
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  declares the `Collection` enum at `$defs/Collection.enum` as
  `["ent","grp","lnk","ppy","typ","txn","uni","vdn"]` and gates
  action compatibility through two `allOf` conditionals:
  - `collection == "txn"` → `action ∈ {open, rollback, commit}`.
  - `collection ∈ ["ent","ppy","lnk","uni","grp","typ","vdn"]` →
    `action ∈ {create, update, delete}`.
  Adding `"loc"` to both the top-level enum and the second
  conditional's enum is the smallest change that gives `loc` parity
  with the other long-term domain collections; transaction-only
  actions then naturally fail the second conditional's `then` branch
  for any `collection: "loc"` envelope.
- `libraries/jade-tipi-dto/src/main/resources/example/message/`
  currently has nine canonical messages (`01-open-transaction.json`
  through `09-commit-transaction.json`) covering txn / ppy / typ /
  ent flows. There is no `loc` example yet; one canonical
  `loc` create example is in scope for this task to make the
  schema-acceptance test deterministic and to give `MessageSpec`'s
  `EXAMPLE_PATHS` list a concrete `loc` round-trip.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  already exercises:
  - JSON round-trip and schema validation across every example in
    `EXAMPLE_PATHS` (currently 9 entries — adding the new `loc`
    example extends this to 10).
  - `fromJson` rejecting unknown collection abbreviations.
  - The schema rejecting `txn`-paired data actions
    (`Collection.TRANSACTION + Action.CREATE`).
  - The schema rejecting non-txn collections paired with
    transaction-control actions
    (`Collection.PROPERTY + Action.OPEN`).
  This existing pattern is the one I will extend for the `loc`
  cases — no new test file is needed in the DTO library.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  is a `CommandLineRunner` that always ensures the `tipi` collection
  exists and then iterates `Collection.values()`, calling
  `mongoTemplate.collectionExists(abbreviation)` and
  `mongoTemplate.createCollection(abbreviation)` for any that are
  missing. Adding `LOCATION("location","loc")` to the enum is by
  itself sufficient for application startup to create `loc` — the
  initializer needs no code change.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/`
  does not exist yet. A new `MongoDbInitializerSpec.groovy` is the
  natural home for startup-loop coverage; the existing
  `DocumentServiceMongoDbImplSpec` shows the mock pattern
  (`Mock(ReactiveMongoTemplate)`, return `Mono`s from the mocked
  methods, assert via Spock interaction counts and `StepVerifier` /
  `block()`).
- `docs/Jade-Tipi.md` lists the abbreviated collection names in
  the "Jade-Tipi Specification" section (lines ~80–90):
  `entity (ent)`, `property (ppy)`, `link (lnk)`, `unit (uni)`,
  `group (grp)`, `type (typ)`, `validation (vdn)`,
  `transaction (txn)`. `loc` is not listed.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  describes the message envelope and explicitly states the
  `collection` enum and the action-compatibility rule on lines
  ~17–24:
  `collection ∈ {ent, ppy, lnk, uni, grp, typ, vdn} → action ∈
  {create, update, delete}` and `collection: txn → action ∈
  {open, rollback, commit}`. Both lines need to mention `loc` once
  the schema accepts it.
- `DIRECTION.md` already records the location and link-modeling
  direction, including the deliberate choice to keep
  `parent_location_id` out of `loc` records and to declare a
  `contents` link type later. No `DIRECTION.md` edit is required for
  TASK-007 itself; the doc is already aligned with this task's
  scope.

### Smallest implementation plan (proposal)

#### S1. DTO enum — add `LOCATION("location","loc")`

Edit
`libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`:

- Add `LOCATION("location", "loc")` to the enum value list. I propose
  inserting it alphabetically by name between `LINK` and `PROPERTY` so
  the visible ordering stays consistent (and also because `loc` sits
  alphabetically between `lnk` and `ppy`). The enum order is not
  load-bearing — `MongoDbInitializer` iterates `values()` regardless,
  and JSON serialization rides `@JsonValue` — but a stable ordering
  helps reviewers.
- No other changes to `Collection.java`. The constructor's
  `"transaction".equals(name)` branch leaves `LOCATION` with
  `[CREATE, UPDATE, DELETE]` automatically. `fromJson(String)` and
  `@JsonValue` already cover the new value.

#### S2. JSON schema — admit `loc` as a long-term collection

Edit
`libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:

- Add `"loc"` to `$defs/Collection.enum` (currently
  `["ent","grp","lnk","ppy","typ","txn","uni","vdn"]`). I propose
  inserting it in alphabetical order: between `"lnk"` and `"ppy"`,
  yielding `["ent","grp","lnk","loc","ppy","typ","txn","uni","vdn"]`.
- Add `"loc"` to the enum inside the second `allOf` conditional that
  whitelists data-mutating actions for non-transaction collections.
  The current list there is
  `["ent", "ppy", "lnk", "uni", "grp", "typ", "vdn"]`; this becomes
  `["ent", "ppy", "lnk", "loc", "uni", "grp", "typ", "vdn"]` (same
  `loc`-after-`lnk` ordering as the top-level enum).
- No change to the first `allOf` conditional (transaction-only
  actions). `loc` stays out of that branch, which means an envelope
  like `collection: "loc", action: "open"` will not satisfy the
  transaction branch's `action ∈ {open, rollback, commit}` constraint
  *and* will fail the second branch's `action ∈ {create, update,
  delete}` constraint, surfacing a clear schema-validation error.

#### S3. Canonical example — `loc` create message

Add one new file under
`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- Filename: `10-create-location.json` — fits the existing two-digit
  ordered naming. (The existing 09-commit closes the canonical txn
  flow, so adding 10 after the commit is fine; the examples are
  iterated as a list, not as a single transaction.)
- Content shape (matches the existing example envelope and snake_case
  data conventions; ID format follows the existing
  `04-create-entity-type.json` /
  `06-create-entity.json` pattern):

```json
{
  "txn": {
    "uuid": "018fd849-2a40-7abc-8a45-111111111111",
    "group": { "org": "jade-tipi-org", "grp": "dev" },
    "client": "kli",
    "user": "0000-0002-1825-0097"
  },
  "uuid": "018fd849-2a47-7777-8f01-aaaaaaaaaaaa",
  "collection": "loc",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~lo~freezer_a",
    "name": "freezer_a",
    "description": "minus-80 freezer in room 110"
  }
}
```

Notes on the example (asking the director to confirm in Q1 below):

- I propose the type-suffix `lo` in the ID (mirroring `en`/`ty`/`pp`
  style). The repo's spec mentions short type suffixes
  `(en,pp,ln,ty,va,gp,tx)` in `docs/Jade-Tipi.md`; `lo` is the
  natural extension for `location` and parallels `ln` for `link`.
  This is example data only — TASK-007 is not introducing an
  ID-format validator.
- No `parent_location_id` is included, in line with `DIRECTION.md`.

#### S4. DTO tests — extend `MessageSpec`

Edit
`libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:

- Append `'/example/message/10-create-location.json'` to
  `EXAMPLE_PATHS` so the existing
  "round-trips through `JsonMapper` preserving collection" and
  "validates against the schema" `@Unroll` features automatically
  cover the new `loc` example.
- Add a feature: **`Collection.fromJson("loc")` returns `LOCATION`
  and serializes back as `"loc"`.**
  - Asserts `Collection.fromJson('loc') == Collection.LOCATION`,
    `Collection.LOCATION.toJson() == 'loc'`,
    `Collection.LOCATION.abbreviation == 'loc'`,
    `Collection.LOCATION.name == 'location'` (note: the enum's
    `getName()` field is the lowercase domain name, distinct from
    `enum.name()`),
    and `Collection.LOCATION.actions == [Action.CREATE,
    Action.UPDATE, Action.DELETE]`.
- Add a feature: **a `Message` with
  `Collection.LOCATION + Action.CREATE` validates** (mirrors the
  existing pattern; minimal in-line `data` map; uses
  `Message.newInstance(...)` and `validate()`; expects
  `noExceptionThrown()`).
- Add a feature: **the schema rejects `Collection.LOCATION` paired
  with `Action.OPEN` / `COMMIT` / `ROLLBACK`** (mirrors the existing
  `Collection.PROPERTY + Action.OPEN` rejection feature). One
  `@Unroll` over `[OPEN, COMMIT, ROLLBACK]` keeps it tight.
- Add a feature: **the schema rejects `Collection.TRANSACTION +
  Action.CREATE` is already covered**; no change there. (Listed for
  completeness — no edit.)

No new test file in the DTO library is needed; the existing
`MessageSpec` already covers the round-trip / validate path for every
example, so extending `EXAMPLE_PATHS` plus three small features is
sufficient.

#### S5. Backend startup test — `MongoDbInitializerSpec`

Add a new file:
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializerSpec.groovy`

A pure Spock `Specification` that mocks `ReactiveMongoTemplate` and
runs `MongoDbInitializer.run()` directly (no Spring context, matches
the project's existing unit-test pattern in `service/` specs). The
collaborator surface is:

- `mongoTemplate.collectionExists(name) → Mono<Boolean>`
- `mongoTemplate.createCollection(name) → Mono<MongoCollection<Document>>`
- `mongoTemplate.getCollection(name) → Mono<MongoCollection<Document>>` (used in the existing-collection branch)

Features:

1. **`run()` creates `loc` when it does not exist.**
   Stub `collectionExists('loc') >> Mono.just(false)` and assert
   `1 * mongoTemplate.createCollection('loc') >> Mono.just(...)`.
   (To keep the spec resilient to *any* startup-iteration order and
   to other collections being added later, stub
   `collectionExists(_) >> Mono.just(true)` for everything except
   `'loc'` so they short-circuit through the existing-collection
   branch, and assert `0 * mongoTemplate.createCollection({ it !=
   'loc' && it != 'tipi' })`.)
2. **`run()` skips creation when `loc` already exists.**
   Stub `collectionExists('loc') >> Mono.just(true)` (and
   `getCollection('loc') >> Mono.just(...)`) and assert
   `0 * mongoTemplate.createCollection('loc')`.
3. **`Collection.values()` includes a `LOCATION` value with
   abbreviation `loc` and the data-mutating action set.** This is a
   tiny enum-shape sanity check that lives in the *backend* test
   (not just the DTO test) because the initializer's behavior depends
   on the enum, and a future accidental enum rename would otherwise
   surface as a confusing initializer failure rather than a clear
   enum failure. (Optional but cheap; happy to drop if the director
   prefers strict separation in Q3 below.)

Notes:

- This spec uses pure Spock mocking; it does not require Mongo /
  Docker / Spring to be running. It is therefore safe to run as part
  of `:jade-tipi:test` even without the project Docker stack — but
  the existing `JadetipiApplicationTests.contextLoads` in the same
  source set still requires Mongo, so the documented setup command
  remains a prerequisite for the full test target.
- The initializer's `mongoTemplate.collectionExists(...).flatMap {
  ... }.block()` style means each iteration is synchronously awaited
  inside `run()`, so feature interactions can be asserted with
  ordinary Spock cardinality (`1 *`, `0 *`) without needing
  `StepVerifier`.

#### S6. Documentation

Edit two files:

- `docs/Jade-Tipi.md` — add `* location     (loc)` to the bullet
  list of base collections (currently lines ~83–90). Insert it in a
  consistent position (proposal: alphabetically after `link (lnk)`).
  The "transaction (txn)" entry stays unchanged so `txn` continues
  to read as the special transaction collection.
- `docs/architecture/kafka-transaction-message-vocabulary.md` — in
  the "Message Envelope" section, extend the two action-compatibility
  bullets so they read:
  - "`collection ∈ {ent, ppy, lnk, loc, uni, grp, typ, vdn}` →
    `action ∈ {create, update, delete}`."
  - "`collection: txn` → `action ∈ {open, rollback, commit}`." (no
    change.)
  Also update the prose immediately above ("`collection` is the
  Jade-Tipi collection abbreviation: `ent`, `ppy`, `lnk`, `uni`,
  `grp`, `typ`, `vdn`, or `txn`.") to include `loc` in the list.

No edit to `DIRECTION.md` for TASK-007 itself; the document is
already aligned with this task and any further refinement (e.g.,
adopting a final type-suffix convention for `loc` IDs, or formalizing
the `contents` link type) belongs to future tasks.

#### S7. Verification commands (post-implementation)

Pre-req (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — starts
  Mongo + Kafka + Keycloak. Only Mongo is strictly required for the
  `:jade-tipi:test` run because `JadetipiApplicationTests.contextLoads`
  opens a Mongo connection. Neither the DTO test changes nor the new
  `MongoDbInitializerSpec` need Mongo, Kafka, or Keycloak themselves.

Targeted commands (in order):

- `./gradlew :libraries:jade-tipi-dto:compileJava` — must compile the
  enum change.
- `./gradlew :libraries:jade-tipi-dto:test` — runs `MessageSpec`
  including the new enum / example / schema features and re-validates
  every existing example against the updated schema. This is the
  task's per-spec required command.
- `./gradlew :jade-tipi:compileGroovy` — must still compile (the
  initializer has no source change but transitively depends on the
  DTO enum).
- `./gradlew :jade-tipi:compileTestGroovy` — must compile the new
  `MongoDbInitializerSpec`.
- `./gradlew :jade-tipi:test --tests
  '*MongoDbInitializerSpec*'` — runs only the new spec.
- `./gradlew :jade-tipi:test` — full `:jade-tipi` unit suite as a
  regression check (requires Mongo running per the
  `JadetipiApplicationTests.contextLoads` constraint).

`./gradlew :jade-tipi:integrationTest` is **not** required for
TASK-007 — there is no new Kafka or HTTP surface, and the existing
integration suites cover Kafka ingest / committed snapshot read.
The director may still ask for a one-shot integration check that
`MongoDbInitializer` actually creates `loc` against a live Mongo
(`docker compose ... up -d`, then `./gradlew :jade-tipi:bootRun`,
then `mongosh` listing collections); see Q4 below.

If verification fails because Mongo is not running, the Gradle
wrapper lock is held, the Gradle native dylib won't load, or the
toolchain is missing, I will report the documented setup command
(`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop`, etc.) instead of treating it as a product
blocker, per directive.

### Proposed file changes (all inside TASK-007 owned paths)

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  — add `LOCATION("location","loc")` (S1).
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  — add `"loc"` to the `Collection.enum` and to the long-term
  collections enum inside the second `allOf` conditional (S2).
- `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
  (new) — canonical `loc` create envelope (S3).
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — extend `EXAMPLE_PATHS` and add the three new features (S4).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializerSpec.groovy`
  (new) — startup-loop coverage for `loc` (S5).
- `docs/Jade-Tipi.md` — add `location (loc)` to the abbreviation list
  (S6).
- `docs/architecture/kafka-transaction-message-vocabulary.md` — add
  `loc` to the prose enumeration and the long-term-collection
  conditional bullet (S6).
- `docs/orchestrator/tasks/TASK-007-add-location-collection.md` —
  `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT` rewritten
  only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No production code is changed in: `MongoDbInitializer.groovy`
(behavior is gained for free via the enum), `TransactionService`,
`TransactionMessagePersistenceService`, `TransactionMessageListener`,
`CommittedTransactionReadService`, `CommittedTransactionReadController`,
`DocumentService`, `DocumentController`, `GlobalExceptionHandler`,
`SecurityConfig`, `application.yml`, `build.gradle`, or any other
resource. The `txn` write-ahead log shape from TASK-003 is preserved.
The committed-snapshot service / controller from TASK-005 / TASK-006
is untouched.

### Blockers / open questions for the director

1. **Q1 — ID type suffix for `loc` example.** The repo spec (`docs/
   Jade-Tipi.md`) lists short type suffixes
   `(en, pp, ln, ty, va, gp, tx)`. There is no canonical suffix yet
   for `location`. Default proposal: `lo` (parallels `ln` for
   `link`, parallels two-letter style). Alternative: `lc`. Either
   works for the example because TASK-007 does not introduce an
   ID-format validator; this is purely an example-data convention
   that future tasks can refine. Confirm `lo`, or pick another
   suffix.
2. **Q2 — Naming of the canonical example file.** Default proposal:
   `10-create-location.json`, continuing the existing two-digit
   ordering after `09-commit-transaction.json`. Alternative: a
   non-numeric name (e.g. `loc-create-location.json`) to signal that
   it is not part of the canonical txn open→commit walkthrough. The
   existing examples are iterated as a list, not as a single
   transaction, so the numeric continuation is fine — but happy to
   rename if you prefer the non-numeric form.
3. **Q3 — Enum-shape sanity check in the backend spec.** Default
   proposal: include feature 3 in `MongoDbInitializerSpec` (a small
   `Collection.LOCATION` shape assertion) so an accidental enum
   rename surfaces as a clear enum failure rather than as a confusing
   initializer failure. Alternative: keep the backend spec strictly
   focused on initializer behavior and leave all enum coverage in
   `MessageSpec`. Confirm the inclusion, or ask me to drop it.
4. **Q4 — Live-Mongo startup verification.** Default proposal:
   `:jade-tipi:test` (mock-level) is sufficient evidence that the
   initializer creates `loc`. Alternative: I bring up the Docker
   stack, run `./gradlew :jade-tipi:bootRun` once, run
   `mongosh tipi --eval 'db.getCollectionNames()'`, and report that
   `loc` is present. This is a one-shot live check, not a new
   automated test. Confirm whether you want this in addition to the
   mock-level coverage.
5. **Q5 — `DIRECTION.md` edit scope.** Default proposal: leave
   `DIRECTION.md` unchanged for TASK-007 (the doc already records
   the `loc`/`lnk`/`contents`/`typ` direction this task aligns
   with). Alternative: append a short "TASK-007 status" note to
   `DIRECTION.md` once the work lands. Confirm "no edit", or ask for
   the status note.
6. **Q6 — Out-of-scope reaffirmation.** I am explicitly *not*
   touching: materialization from `txn` to `loc`; the `contents`
   link type definition or its `typ` record; any `lnk` materializer;
   plate/well APIs; any change to `MongoDbInitializer.run()` body;
   and the committed-snapshot controller / service from TASK-006.
   Confirm that no piece of this `OUT_OF_SCOPE` block has shifted.

STOPPING here per orchestrator pre-work protocol — no implementation,
no build/config/source/schema/test/example/doc edits beyond this file.
