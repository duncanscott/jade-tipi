# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-008 — Add contents link vocabulary examples (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-008`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md`,
propose the smallest DTO/example/documentation unit that establishes the
canonical `contents` link vocabulary on top of the accepted `loc`
collection from `TASK-007`:

- One `typ` create message that declares the `contents` link type with
  the role/label/allowed-endpoint facts described in `DIRECTION.md`
  ("Contents Link Type").
- One `lnk` create message that references that type and represents a
  concrete plate→sample containment, with the well coordinate stored
  as a snake_case `properties.position` value on the `lnk` instance
  (matching `DIRECTION.md` "Plates And Wells").
- `MessageSpec` round-trip and `message.schema.json` validation
  coverage for both new examples (via the existing `EXAMPLE_PATHS`
  list), plus focused assertions that pin the canonical `contents`
  shape.
- Documentation in
  `docs/architecture/kafka-transaction-message-vocabulary.md` that
  records: containment is canonical in `lnk`, `contents` semantics
  live in `typ`, `loc` records still do not carry parentage.

Director constraints to respect (from `OUT_OF_SCOPE` and `DIRECTION.md`):

- Do not materialize from committed `txn` messages into `loc`, `lnk`,
  `typ`, or any other long-term collection.
- Do not add semantic reference validation that checks
  `lnk.type_id` exists, that `lnk.left` / `lnk.right` resolve, or that
  endpoint collections match the type's `allowed_left_collections` /
  `allowed_right_collections`.
- Do not implement link materializers, plate/well read APIs, "what is
  in this plate?" queries, or "where is this sample?" queries.
- Do not add `parent_location_id` (or any other parentage field) to
  `loc` records — containment stays canonical in `lnk`.
- Do not change the Kafka listener, transaction persistence record
  shape, committed-snapshot API, HTTP submission wrappers, security
  policy, Docker Compose, or build configuration.
- Use existing project ID conventions and keep `loc` IDs with the
  `~loc~` suffix established by `TASK-007`.
- Verification must include `./gradlew :libraries:jade-tipi-dto:test`
  at minimum; if local tooling, Gradle locks, or Docker/Mongo are
  unavailable, report the documented setup command rather than
  treating setup as a product blocker.

This is pre-work only. No production source, build, schema, example,
test, or non-doc edits beyond this file until the director moves
`TASK-008` to `READY_FOR_IMPLEMENTATION` (or sets the global signal
to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  already declares `LINK("link","lnk")` and `TYPE("type","typ")`. Both
  inherit the data-mutating action set `[CREATE, UPDATE, DELETE]` via
  the constructor's `"transaction".equals(name)` branch, so the
  schema already accepts `lnk + create` and `typ + create` envelopes
  today. **No enum change is needed for `TASK-008`.**
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  already lists `lnk` and `typ` in the top-level
  `$defs/Collection.enum` and in the long-term-collection conditional
  enum that whitelists `action ∈ {create, update, delete}`. The
  schema enforces snake_case property names for everything under
  `data` (recursively) via the `SnakeCaseObject` `propertyNames`
  pattern `^[a-z][a-z0-9_]*$`. **No schema change is needed for
  `TASK-008`** — every field name proposed below
  (`type_id`, `left`, `right`, `properties`, `position`, `kind`,
  `label`, `row`, `column`, `left_role`, `right_role`,
  `left_to_right_label`, `right_to_left_label`,
  `allowed_left_collections`, `allowed_right_collections`) is
  snake_case-compliant.
- `libraries/jade-tipi-dto/src/main/resources/example/message/`
  currently has 10 canonical messages (`01-open-transaction.json`
  through `10-create-location.json`). There is no canonical
  `contents` `typ` example and no canonical `lnk` example yet.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  iterates `EXAMPLE_PATHS` and runs `JsonMapper` round-trip plus
  `Message.validate()` against `message.schema.json` for every
  example. Adding entries to `EXAMPLE_PATHS` extends those `@Unroll`
  features automatically.
- `DIRECTION.md` "Contents Link Type" specifies the `typ` payload
  facts (`left role: "container"`, `right role: "content"`,
  left-to-right label `contains`, right-to-left label `contained_by`,
  allowed left collections `[loc]`, allowed right collections
  `[loc, ent]`). "Plates And Wells" specifies the `lnk` payload shape
  (plate as one `loc`, sample as `ent`, well coordinate as a
  `properties.position` object on the `lnk`). The `DIRECTION.md`
  example uses ID forms `~typ~contents`, `~lnk~plate_123_sample_456`,
  `~loc~plate_123`, `~ent~sample_456`. `~loc~` matches the convention
  used by `10-create-location.json` after the `TASK-007` directive.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  documents the message envelope, the `txn` record kinds, and the
  current shapes for properties, types, entities, property assignment,
  and a numbered reference-example list (1 through 10). It does **not**
  yet describe link types in `typ` or concrete links in `lnk`.
- `docs/Jade-Tipi.md` already lists `link (lnk)` and `type (typ)` and
  describes types generally; it does not single out `contents` as the
  initial canonical link type. `TASK-008` does not require a
  `Jade-Tipi.md` edit (Q4 below confirms).

### Smallest implementation plan (proposal)

#### S1. Canonical `contents` link-type declaration

Add one new file:
`libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`

A `typ + create` envelope. Proposed body (snake_case, ID conventions
match `DIRECTION.md`):

```json
{
  "txn": {
    "uuid": "018fd849-2a40-7abc-8a45-111111111111",
    "group": { "org": "jade-tipi-org", "grp": "dev" },
    "client": "kli",
    "user": "0000-0002-1825-0097"
  },
  "uuid": "018fd849-2a49-7999-8a09-aaaaaaaaaaab",
  "collection": "typ",
  "action": "create",
  "data": {
    "kind": "link_type",
    "id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "name": "contents",
    "description": "containment relationship between a container location and its contents",
    "left_role": "container",
    "right_role": "content",
    "left_to_right_label": "contains",
    "right_to_left_label": "contained_by",
    "allowed_left_collections": ["loc"],
    "allowed_right_collections": ["loc", "ent"]
  }
}
```

Notes:

- `data.kind: "link_type"` distinguishes this `typ` record from
  entity types (`04-create-entity-type.json`'s `data` has no
  `kind`). The existing `ppy` examples already use `data.kind`
  (`definition` vs. `assignment`) to distinguish record sub-shapes
  inside one collection, so re-using a `kind` discriminator on `typ`
  is consistent with the established pattern. `MessageSpec` does not
  inspect `data.kind`; the value is preserved by JSON round-trip
  alongside the rest of `data`.
- The proposed ID uses the three-letter `~typ~` form (matching
  `DIRECTION.md`). The existing entity-type example
  (`04-create-entity-type.json`) uses the older two-letter `~ty~`
  form. **This is the largest open question (Q1 below).** Defaulting
  to `~typ~` matches `DIRECTION.md` and is consistent with the
  three-letter `~loc~` precedent set by `TASK-007`. The director can
  pick `~ty~` to match the existing entity-type example instead; the
  schema does not constrain ID shapes today.
- The `txn` envelope reuses the existing batch's `txn.uuid`
  (`018fd849-2a40-7abc-8a45-111111111111`), matching what
  `10-create-location.json` did. This keeps the example fixture
  surface tight; logical "after commit" inconsistency already exists
  in the canonical example set and `MessageSpec` validates each
  example independently. (See Q2 if a fresh txn UUID is preferred.)

#### S2. Canonical concrete `contents` link

Add one new file:
`libraries/jade-tipi-dto/src/main/resources/example/message/12-create-contents-link-plate-sample.json`

A `lnk + create` envelope referencing the type from S1. Proposed body:

```json
{
  "txn": {
    "uuid": "018fd849-2a40-7abc-8a45-111111111111",
    "group": { "org": "jade-tipi-org", "grp": "dev" },
    "client": "kli",
    "user": "0000-0002-1825-0097"
  },
  "uuid": "018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb",
  "collection": "lnk",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
    "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "left": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
    "right": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
    "properties": {
      "position": {
        "kind": "plate_well",
        "label": "a1",
        "row": "a",
        "column": 1
      }
    }
  }
}
```

Notes:

- Field shape (`type_id`, `left`, `right`, `properties.position`)
  matches `DIRECTION.md`.
- `position.label`, `position.row`, and `position.kind` are lowercase
  (`a1`, `a`, `plate_well`) because `data` values are JSON strings
  but property *names* must be `^[a-z][a-z0-9_]*$`. The values can
  be any case — `"A1"` is valid as a value — but the `DIRECTION.md`
  example uses upper-case `A1` for human readability. **I propose
  using `"a1"` / `"a"` here so the canonical example does not
  inadvertently set a precedent for case-sensitive plate
  coordinates** that a future reader would have to honor; the
  director can override (Q3) if upper-case `A1` is preferred for
  the canonical example.
- The `left` and `right` IDs reference IDs that are not separately
  created in the example set. This is intentional — semantic
  reference validation is `OUT_OF_SCOPE` for `TASK-008`, so the
  schema does not require those endpoints to exist. (See Q5 if you
  want supporting `loc` / `ent` create examples added too.)
- The `lnk` ID encodes the `~lnk~` segment per `DIRECTION.md` (and
  parallels `~loc~` from `TASK-007`); existing examples have no
  prior `~lnk~` precedent in the repo so this sets the canonical
  form for link IDs.
- `id`, `type_id`, `left`, and `right` are flat strings (not nested
  objects); this matches `DIRECTION.md` and the existing
  property-assignment example pattern (`07-assign-property-value-text.json`
  uses `entity_id` / `property_id` as flat strings).

#### S3. DTO tests — extend `MessageSpec`

Edit
`libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:

- Append both new paths to `EXAMPLE_PATHS`:
  - `'/example/message/11-create-contents-type.json'`
  - `'/example/message/12-create-contents-link-plate-sample.json'`
  The existing `@Unroll` features ("round-trips through `JsonMapper`
  preserving collection" and "validates against the schema")
  automatically cover both new examples; this is the directive's
  required `MessageSpec` round-trip and schema-validation coverage.
- Add a focused feature: **the `contents` `typ` example carries the
  declarative facts `DIRECTION.md` requires.** After
  `JsonMapper.fromJson` of `11-create-contents-type.json`, assert
  `message.collection() == Collection.TYPE`, `message.action() ==
  Action.CREATE`, and the `data` map's `kind == 'link_type'`,
  `name == 'contents'`, `left_role == 'container'`,
  `right_role == 'content'`,
  `left_to_right_label == 'contains'`,
  `right_to_left_label == 'contained_by'`,
  `allowed_left_collections == ['loc']`,
  `allowed_right_collections == ['loc', 'ent']`. This pins the
  canonical declaration shape so a later refactor of the example
  file fails loudly.
- Add a focused feature: **the `contents` `lnk` example carries
  `type_id`, `left`, `right`, and a `position` property.** After
  `JsonMapper.fromJson` of
  `12-create-contents-link-plate-sample.json`, assert
  `message.collection() == Collection.LINK`,
  `message.action() == Action.CREATE`, and the `data` map's
  `type_id` ends with `~typ~contents`, `left` ends with
  `~loc~plate_b1`, `right` ends with `~ent~sample_x1`,
  `properties.position.kind == 'plate_well'`,
  `properties.position.label == 'a1'`,
  `properties.position.row == 'a'`,
  `properties.position.column == 1`.

No new test file is needed; both features extend `MessageSpec`. No
schema-rejection feature is needed because the directive is to add
canonical examples, not to introduce new rejection cases — `lnk` and
`typ` already have schema-acceptance and schema-rejection coverage
through the existing per-collection rules and the existing
`Collection.PROPERTY + Action.OPEN` rejection pattern.

#### S4. Documentation

Edit
`docs/architecture/kafka-transaction-message-vocabulary.md`:

- Add a new section "Link Types And Concrete Links" (after the
  existing "Property Value Assignment" section) that records:
  - `lnk` carries concrete relationships; `typ` declarations carry
    the relationship semantics. `loc` records do not carry
    parentage.
  - The first declared link type is `contents`. Show the canonical
    `typ` payload shape (the snake_case `data` body from S1) as a
    JSON snippet.
  - Show the canonical `lnk` payload shape (`type_id`, `left`,
    `right`, `properties.position`) as a JSON snippet, mirroring
    the snippet style already used for property and entity sections.
  - Note that semantic validation (checking
    `lnk.type_id` exists, that `left` / `right` resolve, that the
    endpoint collections match the type's
    `allowed_*_collections`) is not enforced today and is a
    follow-up reader/materializer concern.
- Extend the numbered "Reference Examples" list to include:
  `11. 11-create-contents-type.json`,
  `12. 12-create-contents-link-plate-sample.json`.

No edit to `docs/Jade-Tipi.md` is proposed for `TASK-008` — the
high-level spec already lists `link (lnk)` and `type (typ)` and
already explains that types are sets of properties and that links
are relationships between entities. Adding a `contents`-specific
paragraph there would duplicate the architecture doc and is outside
the smallest unit. (Confirm in Q4.)

No edit to `DIRECTION.md` is proposed — the document already
records the `contents` direction this task is implementing.

#### S5. Verification commands (post-implementation)

Pre-req (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — starts
  Mongo + Kafka + Keycloak. Only Mongo is strictly required for the
  full `:jade-tipi:test` regression run because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection.
  The DTO test changes proposed here do not need Mongo, Kafka, or
  Keycloak themselves; they exercise pure JSON / schema fixtures.

Targeted commands (in order):

- `./gradlew :libraries:jade-tipi-dto:compileJava` — defensive (no
  Java source change is proposed, but the resources change touches
  the DTO library).
- `./gradlew :libraries:jade-tipi-dto:test` — runs `MessageSpec`
  including the two extended `EXAMPLE_PATHS` rows and the two new
  focused features. **This is the task's required command.**
- `./gradlew :jade-tipi:compileGroovy` and
  `./gradlew :jade-tipi:compileTestGroovy` — defensive regression
  check; the `:jade-tipi` module transitively depends on the DTO
  library through `Collection` / `Action` / `Message` and on the
  example resources only through tests, but a compile sanity-check
  catches any unexpected ripple.
- `./gradlew :jade-tipi:test` — full unit-suite regression check
  (requires Mongo per the existing
  `JadetipiApplicationTests.contextLoads` constraint).

`./gradlew :jade-tipi:integrationTest` is **not** required for
`TASK-008` — there is no new Kafka or HTTP surface, no
materializer, and no semantic validation. The existing integration
suites already cover the Kafka ingest / committed snapshot read
paths that consume these envelopes.

If verification fails because Mongo is not running, the Gradle
wrapper lock is held, the Gradle native dylib won't load, or the
toolchain is missing, I will report the documented setup command
(`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop`, etc.) and the exact command that could not run,
instead of treating it as a product blocker, per directive.

### Proposed file changes (all inside `TASK-008`-owned paths)

- `libraries/jade-tipi-dto/src/main/resources/example/message/11-create-contents-type.json`
  (new) — canonical `contents` link-type declaration (S1).
- `libraries/jade-tipi-dto/src/main/resources/example/message/12-create-contents-link-plate-sample.json`
  (new) — canonical concrete `contents` link from a plate `loc` to a
  sample `ent` (S2).
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — extend `EXAMPLE_PATHS` with both new paths and add the two
  focused shape-assertion features (S3).
- `docs/architecture/kafka-transaction-message-vocabulary.md` — add
  the "Link Types And Concrete Links" section and extend the
  numbered reference-examples list (S4).
- `docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No changes proposed to:

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  (already has `LINK` and `TYPE`).
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  (already accepts `lnk + create` and `typ + create`; no schema gap).
- `jade-tipi/src/main/groovy/...` (no listener, persistence,
  read-service, controller, or initializer change).
- `jade-tipi/src/test/groovy/...` (no new backend test; the
  initializer already creates `lnk` and `typ` collections via
  `Collection.values()`, covered by `MongoDbInitializerSpec`).
- `application.yml`, `build.gradle`, `docs/Jade-Tipi.md`,
  `DIRECTION.md`, `docker-compose.yml`.

### Blockers / open questions for the director

1. **Q1 — Type-suffix convention in IDs.** `DIRECTION.md` examples
   use the three-letter `~typ~` and `~lnk~` segments. The existing
   `04-create-entity-type.json` example uses the older two-letter
   `~ty~` segment. **Default proposal: use `~typ~contents` and
   `~lnk~plate_b1_sample_x1`** to match `DIRECTION.md` and to align
   with the three-letter `~loc~` precedent set by `TASK-007`. This
   sets the new canonical convention going forward without
   retroactively changing `04-create-entity-type.json`. Alternative:
   keep the two-letter form (`~ty~contents`, `~ln~...`) to match the
   existing entity-type example. Confirm `~typ~` / `~lnk~`, or
   pick the two-letter form.
2. **Q2 — Reuse the existing batch's `txn.uuid`?** Default
   proposal: reuse `018fd849-2a40-7abc-8a45-111111111111`, matching
   `10-create-location.json`. The example set is already
   logically "after commit" because `09-commit-transaction.json`
   commits the same `txn.uuid`; `MessageSpec` validates each example
   independently so the inconsistency is cosmetic. Alternative: use
   a fresh `txn.uuid` for the second batch (11 and 12 together) so
   the example set splits cleanly into two logical transactions.
   Confirm reuse, or pick a fresh UUID.
3. **Q3 — Plate-coordinate case in the example.** Default proposal:
   use lowercase `"a1"` / `"a"` for the canonical example so the
   convention does not silently encourage mixed-case plate
   coordinates (the schema does not constrain value case;
   only property names). Alternative: use `"A1"` / `"A"` to match
   `DIRECTION.md`'s human-readable example. Confirm `"a1"`, or
   pick `"A1"`.
4. **Q4 — `docs/Jade-Tipi.md` edit scope.** Default proposal:
   leave `docs/Jade-Tipi.md` unchanged for `TASK-008`; the
   high-level spec already covers links and types generically and a
   `contents`-specific paragraph would duplicate the architecture
   doc. Alternative: append a one-line cross-reference (e.g. "The
   first canonical link type is `contents`; see
   `docs/architecture/kafka-transaction-message-vocabulary.md`").
   Confirm "no edit", or ask for the cross-reference line.
5. **Q5 — Supporting `loc` / `ent` create examples.** The proposed
   `lnk` example references a plate `loc` and a sample `ent` whose
   create messages do not exist in the canonical example set. Today
   the schema does not require the references to resolve, and
   semantic validation is `OUT_OF_SCOPE`. Default proposal: do not
   add supporting `loc` / `ent` create examples in `TASK-008` — the
   contents-link example stands alone. Alternative: also add
   `13-create-location-plate-b1.json` and
   `14-create-entity-sample-x1.json` so the contents link's
   endpoints exist as canonical creates. Confirm "no supporting
   examples", or ask me to add them.
6. **Q6 — `data.kind` discriminator on the `typ` record.** Default
   proposal: include `"kind": "link_type"` on the `typ` `data`
   payload to mirror the `ppy` `kind: "definition"` /
   `kind: "assignment"` discriminator pattern, which keeps the
   later "is this a link type or an entity type?" reader logic
   deterministic. Alternative: omit `kind` on `typ` and let later
   readers infer link-type-ness from the presence of role / label /
   `allowed_*_collections` fields. Confirm the `kind: "link_type"`
   default, or ask me to drop it.
7. **Q7 — Declarative facts on the `contents` `typ` payload.**
   Default proposal: include all six declarative facts from
   `DIRECTION.md` (`left_role`, `right_role`,
   `left_to_right_label`, `right_to_left_label`,
   `allowed_left_collections`, `allowed_right_collections`) on the
   `typ` `data` payload, so a future materializer / reader does not
   need a follow-up `typ + update` message to enrich the type.
   Alternative: declare only the labels and add allowed-collections
   constraints later when semantic validation arrives. Confirm "all
   six facts now", or ask me to defer the `allowed_*_collections`
   pair.
8. **Q8 — Out-of-scope reaffirmation.** I am explicitly **not**
   touching: any DTO enum (`Collection`, `Action`, `Message`); the
   JSON schema (`message.schema.json`); any backend service,
   listener, controller, or initializer; build files; Docker
   Compose; security policy; HTTP submission wrappers;
   materialization; semantic validation; plate/well read APIs;
   `parent_location_id`; the `txn` write-ahead log shape; or the
   committed-snapshot service / controller. Confirm that no piece
   of this `OUT_OF_SCOPE` block has shifted.

STOPPING here per orchestrator pre-work protocol — no
implementation, no build / config / source / schema / example /
test / non-doc edits beyond this file.
