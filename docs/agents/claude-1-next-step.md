# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-026 — Human-readable Kafka loc submission path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-026-human-readable-kafka-loc-submission.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. The director scoped TASK-026
to prove that a human-readable Kafka transaction can create a root-shaped
`loc` document in MongoDB through the existing `txn` ingest, commit, and
materialization path. The bounded outcome is:

- Document and, if necessary, adjust the accepted `loc + create` message
  shape so it is easy to hand-author:
  - top-level `collection: "loc"` and `action: "create"`,
  - `data.id` is the materialized object ID,
  - `data.type_id` is optional,
  - `data.properties` is a plain JSON object,
  - `data.links` is optional or empty on create.
- Add or update example resource JSON showing one complete transaction
  (open → create one `loc` → commit) under
  `libraries/jade-tipi-dto/src/main/resources/example/message/`.
- Ensure the materialized Mongo document remains root-shaped with `_id`,
  `id`, `collection: "loc"`, `_head`, `properties`, and `links`.
- Add focused automated coverage proving the example/message shape
  round-trips through DTO validation and materializes a `loc` root with
  the expected JSON shape.

Out of scope (per task file): no new HTTP submission endpoints, no full
property-definition / type-validation system, no `ent` materialization,
no permission enforcement, no canonical `parent_location_id` on `loc`,
no nested-operation Kafka DSL.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source change
is made until the director advances TASK-026 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` "Human-Readable Kafka Submission" (lines 103–137)
  prescribes the new shape, including `data.type_id` optional,
  `data.properties` plain JSON object keyed by property name, and
  `data.links` normally empty on create.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  "Human-Readable Authoring Rule" (lines 28–68) repeats the shape in the
  vocabulary doc and explicitly notes that "the early materializer may
  also tolerate older examples that put `name` and `description`
  directly under `data`, but new examples should prefer the explicit
  `data.properties` object." Back-compat is therefore a stated tolerance,
  not a long-term contract.
- `docs/architecture/kafka-transaction-message-vocabulary.md` lines
  257–263 ("Committed Materialization Of Locations And Links") confirms
  the materialized root-document contract: top-level `_id`, `id`,
  `collection`, `type_id`, explicit `properties`, denormalized `links`,
  and `_head.provenance`. The new shape must preserve those fields
  unchanged.
- TASK-013 (root-shaped contract), TASK-014 (root-shaped materializer),
  and TASK-019 (Clarity/ESP container prototype) are all accepted; they
  define the root-document shape and the existing materializer entry
  point that this task must reuse rather than replace.

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:

- `isSupported()` (lines 206–226) accepts `loc + create` unconditionally.
- `buildDocument()` (lines 240–262) for non-`lnk` collections (so for
  `loc`, `grp`, `typ` link-type) calls `buildInlineProperties(data)`,
  which copies every `data` entry except `id` and `type_id` directly into
  root `properties`. There is no recognition of `data.properties` or
  `data.links` sub-objects for `loc`.
- For `lnk` only, the materializer already does the right thing: it
  reads `data.properties` and copies it via `copyProperties()` into root
  `properties`. The existing `lnk` path is the model the `loc` path
  should match.
- Root `links` is hard-coded to an empty `LinkedHashMap` for every
  supported collection; submitted `data.links` is currently ignored.

Implication: the proposed human-readable shape

```json
{
  "collection": "loc",
  "action": "create",
  "data": {
    "id": "...",
    "type_id": "...",
    "properties": { "name": "Freezer 01", "description": "..." },
    "links": {}
  }
}
```

does **not** materialize correctly today. The current code would write
root `properties = { "properties": { "name": "...", ... }, "links": {} }`
— a doubly-nested `properties` map and `links` mistakenly stored inside
`properties`. The schema accepts the payload (snake-case keys), the
ingest persists it, and the commit projection runs, but the projected
shape violates the root-document contract and breaks
`ContentsLinkReadService` / contents read service consumers that expect
the documented root shape.

### Schema status (`message.schema.json`)

`libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:

- For `collection != "grp"`, `data` follows `SnakeCaseObject`
  (lines 189–199), which permits any snake_case-keyed object (recursive).
  `data.properties` and `data.links` (both snake-case) are valid keys
  and may carry nested snake-case objects. Property values such as
  `"Freezer 01"` and `"Minus 80 freezer in room 214"` are allowed string
  `SnakeCaseValue`s; the snake_case rule applies to keys, not to string
  values.
- The schema does **not** enforce the new shape (it would also accept
  the old flat shape, plus arbitrary extra snake_case keys at the `data`
  root). Documenting the new shape is product/example-driven; tightening
  the schema to require `data.properties` is intentionally **out of
  scope** for this task because the back-compat tolerance is explicitly
  stated by the architecture doc.

Conclusion: no schema-file change is required to accept the new
human-readable shape. A future task may layer in a stricter
`LocationData` definition once the rest of the contract stabilizes; this
task should not pre-empt that decision.

### Example resource status

`libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
currently uses the **old flat shape** with `name` and `description`
directly under `data` and no `properties`/`links`/`type_id`. This file
is exercised by `MessageSpec` (lines 24–38) for round-trip and
`message.schema.json` validation but does not currently exercise the
new shape.

The transaction envelope examples already cover open and commit:

- `01-open-transaction.json` (txn + open, txn uuid `018fd849-2a40-7abc-8a45-111111111111`)
- `09-commit-transaction.json` (txn + commit on the same txn uuid)

So a "complete transaction that opens, creates one simple `loc`, and
commits" can be expressed as the trio `01-…` → `10-…` (rewritten) →
`09-…`, all sharing `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`.
That mapping is consistent with how the existing examples already chain.

### Smallest implementation plan

Goal: smallest set of changes that makes the new human-readable
`loc + create` shape ingest, commit, and materialize into a root-shaped
Mongo `loc` document, with focused automated coverage and updated
examples. Back-compat for the legacy flat shape is preserved per the
architecture doc's stated tolerance.

#### File changes

1. `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
   — rewrite to the new human-readable shape:

   - Top-level: `txn` (reuse `018fd849-2a40-7abc-8a45-111111111111`),
     `uuid`, `collection: "loc"`, `action: "create"`.
   - `data.id` = the existing `jade-tipi-org~dev~…~loc~freezer_a` ID
     (same value already used today, to keep `MessageSpec` deterministic).
   - `data.type_id` = `null` or omitted (the directive marks it optional;
     `null` makes the optionality explicit in the round-trip example).
     **Default proposal:** omit the key entirely so the example shows the
     minimum payload; the materializer already treats a missing
     `type_id` as `null` (see `data.get(FIELD_TYPE_ID)` in the
     materializer).
   - `data.properties = { "name": "freezer_a", "description": "minus-80
     freezer in room 110" }` — keep the same human values that exist in
     the current flat example so the change is purely structural.
   - `data.links = {}` — present as an explicit empty map so the example
     documents the canonical "no links on create" convention.

2. `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
   — extend `buildDocument()` / `buildInlineProperties()` so that for
   non-`lnk` supported collections (`loc`, `grp`, `typ` link-type), the
   materializer chooses one of two branches based on payload shape:

   - **Explicit shape (preferred):** if `data` contains a `properties`
     key whose value is a `Map`, use that map directly (via the existing
     `copyProperties()` helper) for root `properties`. If `data` also
     contains a `links` key whose value is a `Map`, copy that map into
     root `links` (via a parallel `copyLinks()` helper). Otherwise
     default `links` to `[:]` as today.
   - **Legacy flat shape (back-compat):** if `data.properties` is
     missing or not a `Map`, fall back to the current behaviour: copy
     every `data` entry except `id` and `type_id` directly into root
     `properties`, and leave root `links` as `[:]`.

   Detection rule chosen so that the legacy shape (which currently
   carries `name` and `description` at `data` root and no `properties`
   key) keeps working unchanged. The explicit shape is preferred when
   `data.properties` is present and Map-typed.

   Implementation notes:
   - Add a small helper `copyLinks(Object linksValue)` mirroring
     `copyProperties()`.
   - Keep `lnk`-specific branch unchanged (it already reads
     `data.properties` correctly).
   - Do **not** touch `_head.provenance`, `_id`, `id`, `collection`, or
     `type_id` handling. The contract from TASK-013 / TASK-014 stays.
   - Keep the duplicate-key idempotency / conflicting-payload paths
     unchanged. `isSamePayload()` already strips
     `_head.provenance.materialized_at` only.

3. `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
   — add focused Spock cases (Mock-driven, matches the existing style):

   - "materializes a loc create with explicit data.properties /
     data.links into the root shape" — submits the new human-readable
     shape and asserts:
     - `captured._id == LOC_ID`, `captured.id == LOC_ID`,
       `captured.collection == 'loc'`, `captured.type_id == null`.
     - `captured.properties == [name: 'freezer_a', description:
       'minus-80 freezer in room 110']` (no doubly-nested
       `properties.properties`).
     - `captured.links == [:]` (or the submitted `data.links` value if
       non-empty).
     - `_head.schema_version`, `_head.document_kind`, `_head.root_id`,
       and `_head.provenance.{txn_id, commit_id, msg_uuid, collection,
       action, committed_at, materialized_at}` all populated as today.
   - "materializes a loc create with explicit data.type_id and
     data.properties" — confirms `type_id` is set at root and excluded
     from `properties`.
   - "preserves legacy flat loc create back-compat" — keeps the existing
     "materializes a loc create as a root document with inline
     properties" assertions intact (the current first test already
     covers this path; the assertion is that it remains green).

4. `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
   — the existing example loop at lines 24–38 already round-trips and
   schema-validates `10-create-location.json`. After the example file is
   rewritten, the same loop validates the new shape against
   `message.schema.json` for free. Add one focused case that asserts:

   - `Message` parsed from `10-create-location.json` exposes
     `collection() == LOCATION`, `action() == CREATE`,
     `data().properties` is a `Map` with the expected entries, and
     `data().links` is an empty `Map`.

5. `docs/architecture/kafka-transaction-message-vocabulary.md` —
   confirm the human-readable example block (lines 50–64) still matches
   the rewritten resource and that the back-compat sentence on lines
   65–68 stays accurate after the example file flips. **Default
   proposal:** no edit needed; the doc already prescribes the new
   shape and notes legacy tolerance. Confirm during implementation; if
   a wording clarification is needed (e.g. "see
   `10-create-location.json` for the canonical shape"), add a single
   sentence under "Reference Examples" on line 285.

6. `docs/OVERVIEW.md` — within OWNED_PATHS but not load-bearing for
   this change. **Default proposal:** no edit. If the OVERVIEW currently
   describes the legacy `loc` shape, update one sentence; otherwise
   leave it untouched.

Expected total surface: 1 example resource rewritten, ~30 lines of
materializer logic added (one helper + branching), ~50–80 lines of
Spock test added across two specs. No public API change, no schema
change, no DTO type change, no Kafka topic change.

#### Out-of-scope guardrails (will **not** edit)

- `clients/kafka-kli/**` — kli already accepts `--collection loc` and
  passes `data` through unchanged; no CLI change is needed to publish
  the new shape.
- `frontend/**` — no UI surface for `loc` create yet.
- `src/main/groovy/.../service/TransactionService.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy` — they treat `data` as
  an opaque map; the new shape is preserved verbatim through ingest,
  commit, and snapshot read.
- `message.schema.json` — see Schema status above.
- `frontend/.env.local` — generated; never hand-edited (per CLAUDE.md).

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    rewritten 10-create-location.json example.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new + legacy loc shapes and the round-shape assertions.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running and the project documents an opt-in flag).
#    The existing TransactionMessageKafkaIngestIntegrationSpec is
#    gated on KAFKA_BOOTSTRAP_SERVERS / docker-compose. If running:
docker compose -f docker/docker-compose.yml up -d mongo kafka zookeeper keycloak
./gradlew :jade-tipi:integrationTest \
  --tests org.jadetipi.jadetipi.kafka.TransactionMessageKafkaIngestIntegrationSpec
```

Setup commands (per CLAUDE.md "Tooling Refresh"; if local tooling is
missing they are reported, not treated as product blockers):

- Docker-compose stack required for `:jade-tipi:test` because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection per
  the project CLAUDE.md. Run
  `docker compose -f docker/docker-compose.yml up -d` first.
- If the Gradle wrapper cache is missing, the first `./gradlew` invocation
  bootstraps it; that is normal first-run behaviour, not a blocker.
- If integration tests cannot run because Docker is not available in the
  sandbox, report the exact `docker compose ... up` command and stop
  rather than treating it as a product blocker.

### Stay-in-scope check (this pre-work turn)

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other files are touched. The implementation turn (gated on a director
signal) will edit only paths inside TASK-026 `OWNED_PATHS`:

- `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/`
  (`MessageSpec.groovy` extension)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`
  (only if a narrow integration test is added and Docker is available)
- `docs/architecture/kafka-transaction-message-vocabulary.md` —
  clarification only, if needed
- `docs/orchestrator/tasks/TASK-026-human-readable-kafka-loc-submission.md`
  — if status update is required
- `docs/agents/claude-1-changes.md` — the implementation report

`DIRECTION.md` and `docs/architecture/jade-tipi-object-model-design-brief.md`
are within TASK-026 `OWNED_PATHS` but the **Default proposal** is to
leave them untouched. Both already describe the new direction; no edit
is needed unless the implementation surfaces a wording mismatch.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect with
one signal change.

- **Q-26-A — Detection rule for the new vs legacy loc shape.**
  **Default proposal:** treat a payload as "explicit shape" only when
  `data.properties instanceof Map`. Otherwise fall back to the existing
  flat-flatten behaviour. This keeps the existing
  `10-create-location.json`-style payload working on stale clients
  without flag-day coordination, matches the architecture-doc tolerance
  sentence, and avoids brittle "is this object empty" heuristics. **Backup:**
  drop legacy back-compat and require `data.properties` always — only if
  the director wants to harden the contract earlier than the architecture
  doc currently states.

- **Q-26-B — Treatment of submitted `data.links` on a `loc + create`.**
  The directive says `data.links` is "optional or empty on create".
  **Default proposal:** if `data.links` is a `Map`, copy it verbatim
  into root `links` (mirrors how `lnk` already handles
  `data.properties`). If absent or non-Map, default to `[:]`. This keeps
  endpoint-projection maintenance out of scope and matches the architecture
  doc's "links is empty or absent on create" wording. **Backup:** ignore
  any submitted `data.links` and always write `[:]`, deferring the
  contract decision; rejected because it would silently drop submitted
  data for callers that follow the human-readable example literally.

- **Q-26-C — Schema tightening (`LocationData`).** Mirroring the existing
  `GroupData` pattern, the schema could declare a `LocationData`
  definition that pins `properties: object` and `links: object`. **Default
  proposal:** **defer** — this task documents and materializes the new
  shape, but the architecture doc explicitly tolerates the legacy shape.
  A schema tightening would force a flag-day across any client still
  emitting the flat shape (today, including the bundled example before
  the rewrite). **Backup:** accept a schema-only follow-up task once the
  ecosystem is on the new shape.

- **Q-26-D — `data.type_id` representation in the new example.** The
  directive marks `type_id` as optional. **Default proposal:** omit the
  `type_id` key entirely from `10-create-location.json` to keep the
  minimal-payload example honest; the materializer already reads
  `data.get(FIELD_TYPE_ID)` and stores `null` when absent (covered by
  the existing test "materializes a loc create … type_id == null").
  **Backup:** include `type_id: null` so the canonical example shows the
  field name; rejected because that conflates "absent" with "explicitly
  null", and the architecture doc shows the field omitted.

- **Q-26-E — Integration test coverage.** The directive lists "the
  narrowest practical Kafka/Mongo integration test if local Docker is
  running and the project has a documented opt-in flag for it" as a
  verification-time expectation. **Default proposal:** the
  implementation turn adds focused unit-level Spock coverage in
  `CommittedTransactionMaterializerSpec` and reuses the existing
  `TransactionMessageKafkaIngestIntegrationSpec` for the ingest path;
  it does **not** add a new full end-to-end ingest→commit→materialize
  Kafka spec unless the director explicitly requests one, because the
  matrix coverage is already provided by unit tests for the
  materializer and an existing integration spec for the ingest path.
  **Backup:** add a new
  `LocCreateMaterializationIntegrationSpec` in
  `jade-tipi/src/integrationTest/...` that publishes the rewritten
  example via `kli`-equivalent producer, awaits commit, and asserts the
  resulting Mongo `loc` root document; only if Docker is available in
  the sandbox and the director expands scope.

- **Q-26-F — Architecture doc wording update.** **Default proposal:**
  no edit to `kafka-transaction-message-vocabulary.md`; the document
  already prescribes the new shape and notes legacy tolerance. The
  rewritten example file will be the canonical exemplar referenced from
  the doc's "Reference Examples" section. **Backup:** add one sentence
  to the "Reference Examples" block explicitly calling out
  `10-create-location.json` as the canonical human-readable shape.

- **Q-26-G — Pre-existing setup blockers.** `:jade-tipi:test` requires
  Docker-compose services to be running because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection. If
  Docker is unavailable in the implementation sandbox, **default
  proposal:** report the exact `docker compose -f
  docker/docker-compose.yml up -d` setup command and the resulting
  Gradle test failure, then stop with `STATUS: BLOCKED`. **Backup:**
  none; spinning up Docker, mutating ports, or reconfiguring
  Mongo/Kafka is outside TASK-026 OWNED_PATHS and CLAUDE.md
  "Tooling Refresh" guidance.

STOP.
