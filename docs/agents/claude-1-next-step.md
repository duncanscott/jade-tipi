# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-027 — Human-readable Kafka contents link submission path (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-027-human-readable-kafka-contents-link-submission.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. The director scoped TASK-027
to prove that a human-readable Kafka transaction can create a root-shaped
`contents` `lnk` document and that the existing contents read path can
consume that materialized link shape. The bounded outcome is:

- Document and, if necessary, adjust the accepted human-readable `lnk + create`
  shape so it is easy to hand-author:
  - top-level `collection: "lnk"` and `action: "create"`,
  - `data.id` is the materialized link object ID,
  - `data.type_id` references a `typ` link-type record,
  - `data.left` and `data.right` are raw endpoint IDs,
  - `data.properties` is a plain JSON object for instance facts such as
    plate-well position.
- Add or update example resource JSON only if the existing examples do not
  already show a complete open, contents-type/link create, and commit flow.
- Ensure the materialized Mongo `lnk` document remains root-shaped with
  `_id`, `id`, `collection: "lnk"`, top-level `type_id`, `left`, `right`,
  `_head`, `properties`, and `links`.
- Add focused automated coverage proving the example/message shape
  round-trips through DTO validation, materializes a `lnk` root with the
  expected JSON shape, and remains readable by the existing contents read
  service where that can be covered without adding new product behavior.

Out of scope (per task file and directive): no HTTP submission endpoints,
no `ent` materialization, no property-assignment materialization, no
permission enforcement, no object extension pages, no endpoint projection
maintenance, no full Clarity/ESP import, no semantic resolution of
`data.type_id` / `data.left` / `data.right`, no `parent_location_id` on
`loc`, no nested-operation Kafka DSL.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source change
is made until the director advances TASK-027 to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`.

### Authoritative product direction (read first)

- `DIRECTION.md` "Link-Centric Relationships" (lines 139–156),
  "Contents Link Type" (lines 158–175), "Plates And Wells" (lines 177–209),
  and "Query Direction" (lines 211–222) prescribe the canonical
  contents-link shape: `type_id`, `left`, `right`, instance `properties`
  (e.g. `position`), with link-type semantics living in `typ`. Concrete
  links should not repeat the labels.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  "Link Types And Concrete Links" (lines 161–209) repeats the human-readable
  shape and is explicit that semantic checks (resolution of `type_id`,
  `left`, `right`, and `allowed_*_collections`) are not enforced by
  `message.schema.json` and remain a follow-up reader/materializer
  concern.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  "Committed Materialization Of Locations And Links" (lines 257–263) and
  "Reading `contents` Links" (lines 266–281) document the existing
  materializer and `ContentsLinkReadService` contract that this task must
  exercise rather than replace. The reader resolves the canonical
  `contents` type by querying root-shaped `typ` for
  `properties.kind == "link_type"` AND `properties.name == "contents"`,
  then filters `lnk.type_id` with `$in` against every matching declaration.
- TASK-008 (link-type vocabulary), TASK-015 (contents read over root
  shape), TASK-019 (Clarity/ESP container prototype), and TASK-026
  (human-readable `loc + create` path) are all accepted; they define
  the materializer entry point and the human-readable wire conventions
  that this task must reuse.

### Current example resource state (read of source on `claude-1`)

`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `01-open-transaction.json` — `txn + open` with
  `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`. Sequence anchor.
- `09-commit-transaction.json` — `txn + commit` on the same txn uuid.
- `11-create-contents-type.json` — `typ + create` with `data.kind:
  "link_type"`, `data.name: "contents"`, `data.left_role: "container"`,
  `data.right_role: "content"`, `data.left_to_right_label: "contains"`,
  `data.right_to_left_label: "contained_by"`,
  `data.allowed_left_collections: ["loc"]`,
  `data.allowed_right_collections: ["loc", "ent"]`. Six declarative
  facts at the flat `data` root, no nested `properties` map. This is
  the intended shape for link-type declarations (typ link-types do not
  carry instance-style properties; their facts are the type definition).
- `12-create-contents-link-plate-sample.json` — `lnk + create` with
  `data.id = …~lnk~plate_b1_sample_x1`,
  `data.type_id = …~typ~contents`,
  `data.left = …~loc~plate_b1`,
  `data.right = …~ent~sample_x1`,
  `data.properties.position = { kind: 'plate_well', label: 'A1', row:
  'A', column: 1 }`. Matches the canonical contents-link shape from
  the architecture doc verbatim.

The shared `txn.uuid` across `01`, `11`, `12`, and `09` already chains
them as one transaction. `12` references endpoint IDs (`loc~plate_b1`,
`ent~sample_x1`) that are not created by earlier examples; per the task
scope ("Do not add semantic validation that `data.type_id`, `data.left`,
or `data.right` resolve to committed records"), this is intentional and
out of scope to "fix". `10-create-location.json` creates `loc~freezer_a`,
which is a separate location example and not the plate referenced by 12.

Conclusion: **the existing example resources already express the
human-readable contents-type/contents-link shape** prescribed by
`DIRECTION.md` and the vocabulary doc. No example file rewrite is
required. The "complete open, contents-type/link create, and commit
flow" is expressible as `01 → 11 → 12 → 09`, all sharing the same
`txn.uuid`.

### Current materializer behaviour (read of source on `claude-1`)

`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`:

- `isSupported()` (lines 206–226) accepts:
  - `lnk + create` unconditionally,
  - `typ + create` only when `data.kind == "link_type"` (entity-type
    `typ` records are skipped as `skippedUnsupported`).
- `buildDocument()` (lines 240–266) for `lnk` (lines 251–255) reads
  `data.left`, `data.right`, copies `data.properties` verbatim into
  root `properties` (or `[:]` when missing/non-Map), and forces root
  `links = [:]`. Top-level `_id`, `id`, `collection: "lnk"`, and
  `type_id` are populated from the message. Result root shape:
  `_id, id, collection, type_id, left, right, properties, links: {},
  _head{ schema_version, document_kind, root_id, provenance{...} }`.
- For `typ + create` link-type, `buildDocument()` falls into the
  non-`lnk`/`lnk`-vs-`loc-grp` branch. After TASK-026 the branch
  prefers `data.properties` when it is a Map; if absent it falls back
  to `buildInlineProperties(data)` which copies every `data` entry
  except `id` and `type_id` into root `properties`. The current
  link-type example carries flat `kind`, `name`, `left_role`,
  `right_role`, etc. with no nested `properties`, so the legacy
  inline-properties branch runs and produces root `properties.kind ==
  'link_type'`, `properties.name == 'contents'`, plus the role and
  label facts. That shape exactly matches the dotted-path criteria
  used by `ContentsLinkReadService.resolveContentsTypeIds()` (lines
  134–142 of `ContentsLinkReadService.groovy`):
  `properties.kind == "link_type"` AND `properties.name == "contents"`.

**Implication: the materializer already produces the documented
root-shaped `lnk` and `typ` documents from the existing examples.**
No materializer code change is required for this task. The acceptance
criteria for "materialized Mongo `lnk` document remains root-shaped
with `_id`, `id`, `collection: 'lnk'`, top-level `type_id`, `left`,
`right`, `_head`, `properties`, and `links`" is already met by
`CommittedTransactionMaterializer.buildDocument()`.

### Current automated coverage (read of source on `claude-1`)

DTO library:

- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`:
  - `EXAMPLE_PATHS` (lines 24–38) loops every example resource through
    `JsonMapper` round-trip (lines 86–109) and `Message.validate()`
    schema validation (lines 111–125). `11-create-contents-type.json`
    and `12-create-contents-link-plate-sample.json` are both covered.
  - "contents typ example declares the canonical link-type facts"
    (lines 330–352) reads `11-…` and asserts `data.kind`, `data.id`,
    `data.name`, `data.left_role`, `data.right_role`,
    `data.left_to_right_label`, `data.right_to_left_label`,
    `data.allowed_left_collections`, `data.allowed_right_collections`.
  - "contents lnk example references the contents type and carries a
    position property" (lines 434–458) reads `12-…` and asserts
    `data.id`, `data.type_id`, `data.left`, `data.right`, and
    `data.properties.position`.

Backend unit:

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`:
  - "materializes a link-type typ create root with all six declarative
    facts under properties" (lines 407–448) asserts `_id`, `id`,
    `collection == 'typ'`, `type_id == null`, the six declarative facts
    + `allowed_*_collections` under `properties`, `links == [:]`, and
    `_head.provenance` with `collection == 'typ'`, `action == 'create'`.
  - "skips a typ create whose data.kind is not link_type" (lines
    450–458) confirms entity-type `typ` records remain skipped.
  - "materializes a lnk create as a root with top-level type_id, left,
    right, and properties.position" (lines 460–497) asserts the full
    `lnk` root shape — `_id`, `id`, `collection == 'lnk'`, top-level
    `type_id`, `left`, `right`, `properties.position` verbatim,
    `links == [:]`, `_head.provenance.collection == 'lnk'`,
    `_head.provenance.msg_uuid` populated, no `_jt_provenance`.
  - "lnk create without payload properties defaults root properties to
    an empty map" (lines 499–517) covers the missing-properties branch.
  - "mixed-message snapshot inserts in snapshot order with correct
    counts" (lines 888–920) covers an end-to-end snapshot with
    `loc + ppy(skip) + typ link-type + ent(skip) + lnk` and asserts
    insert order `['loc', 'typ', 'lnk']` and counts.

- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy`
  builds root-shaped `typ` and `lnk` docs (lines 58–125) that exactly
  mirror what the materializer writes, then asserts the reader's
  Mongo criteria: `typ` filter on `properties.kind == 'link_type'` AND
  `properties.name == 'contents'` (lines 207–212), `lnk` filter on
  `type_id $in <resolved>` and `left == containerId` (lines 214–223)
  or `right == objectId` (lines 304–330), with deterministic `_id ASC`
  sort. Provenance is read from `_head.provenance` with legacy
  `_jt_provenance` fallback.

Backend integration (opt-in, gated on `JADETIPI_IT_KAFKA=1` + Kafka +
Keycloak reachable):

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
  publishes `open + loc + typ link-type + lnk + commit` to a per-spec
  Kafka topic, waits for the committed `txn` header and the root-shaped
  materialized `typ` and `lnk` rows, then exercises both
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` and asserts the same flat JSON
  record. This is the end-to-end Kafka → Mongo → HTTP proof of the
  human-readable contents-link path. The wire shape it publishes is
  exactly the `12-…` example shape (lines 302–315) with a
  per-feature `containerId/typeId/linkId/contentId`.

**Conclusion: existing coverage already proves the wire-shape DTO
round-trip, the materialized root shape, the reader's Mongo criteria,
and the Kafka end-to-end integration.** The remaining gap is a unit-level
proof that the materializer's outputs (taken straight from a
`typ + create` link-type and `lnk + create` snapshot) satisfy the
exact criteria `ContentsLinkReadService` uses — i.e. that the materialized
`typ.properties.kind`, `typ.properties.name`, and `lnk.type_id` line up
without an integration test. That proof currently exists piecewise in
two specs but not as one focused round-trip assertion.

### Schema status (`message.schema.json`)

`libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:

- For `collection != "grp"`, `data` follows `SnakeCaseObject`, which
  permits any snake_case-keyed object recursively. Both `11-…` and
  `12-…` validate today (covered by the loop in `MessageSpec`); the
  schema does not enforce a `LinkData` or `LinkTypeData` shape.
- Tightening the schema to require `lnk.data.type_id`, `lnk.data.left`,
  `lnk.data.right`, or `typ.data.kind == "link_type"` would be a
  semantic-validation change. Per the task's `OUT_OF_SCOPE` (no semantic
  reference validation) and per the architecture doc's explicit "schema
  accepts this envelope today on the strength of `lnk + create` and the
  snake_case property-name rule. Semantic checks … are not enforced by
  `message.schema.json` and remain a follow-up reader/materializer
  concern", **no schema-file change is required or in-scope** for this
  task.

### Smallest implementation plan

Goal: smallest set of changes that proves the existing human-readable
contents-type/contents-link examples round-trip end-to-end into the
documented root-shaped `typ` and `lnk` documents and remain consumable
by the existing contents read service, without adding new product
behavior.

#### File changes

1. **No example resource change required.**
   `11-create-contents-type.json` and
   `12-create-contents-link-plate-sample.json` already express the
   canonical human-readable shape per `DIRECTION.md` and the vocabulary
   doc. The `01 → 11 → 12 → 09` transaction sequence (shared
   `txn.uuid = 018fd849-2a40-7abc-8a45-111111111111`) is already
   complete. **Default proposal:** leave example files untouched.

2. **No materializer code change required.**
   `CommittedTransactionMaterializer.buildDocument()` already produces
   the documented root shape for both `typ + create` link-type
   (`properties.kind == 'link_type'`, `properties.name == 'contents'`,
   plus role and label facts) and `lnk + create` (top-level `type_id`,
   `left`, `right`, verbatim `properties`, `links: {}`, `_head.provenance`).
   **Default proposal:** leave the materializer untouched.

3. **No schema or DTO type change required** (see Schema status above).

4. **Add focused automated coverage proving the round-trip.**
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
   — add one Spock feature, `'materialized typ link-type and lnk roots
   satisfy ContentsLinkReadService resolution criteria'`, that:
   - Builds a snapshot with the `linkTypeMessage()` and `linkMessage()`
     helpers (already present in the spec file).
   - Captures the `Map` documents passed to `mongoTemplate.insert(_, 'typ')`
     and `mongoTemplate.insert(_, 'lnk')`.
   - Asserts on the captured `typ` doc: `properties.kind == 'link_type'`
     AND `properties.name == 'contents'` (the exact dotted-path criteria
     the reader uses), plus `_id`, `id`, `collection == 'typ'`, and the
     remaining role and label facts under `properties`.
   - Asserts on the captured `lnk` doc: `_id`, `id`,
     `collection == 'lnk'`, `type_id` equal to the captured `typ` doc's
     `_id` (proving the materialized cross-document reference), `left`,
     `right`, `properties.position` verbatim, `links == [:]`, and
     `_head.provenance.collection == 'lnk'`.
   - Asserts the materialize counts: `materialized == 2`,
     `skippedUnsupported == 0`, `skippedInvalid == 0`,
     `duplicateMatching == 0`, `conflictingDuplicate == 0`.

   This narrow co-presence test is the missing link: it proves the
   materializer's outputs satisfy the reader's Mongo criteria without
   requiring a Mongo, Kafka, or HTTP roundtrip. ~40 lines.

5. **Optionally add a DTO-level round-trip assertion across the
   contents-flow examples.**
   `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
   — add one Spock feature, `'contents transaction example trio shares
   one txn id and pairs typ link-type with a referencing lnk create'`,
   that:
   - Reads `01-open-transaction.json`, `11-create-contents-type.json`,
     `12-create-contents-link-plate-sample.json`, and
     `09-commit-transaction.json`.
   - Asserts all four share the same `txn.uuid`.
   - Asserts `11-…` declares `data.id` matching `12-…`'s `data.type_id`
     (the in-resource cross-reference), and that `12-…` declares a
     `loc` `data.left` and an `ent` `data.right` per the
     `allowed_*_collections` declared on `11-…`.
   - This is a wire-shape co-presence assertion only; it does not call
     into any materializer or reader. ~25 lines.

   **Default proposal:** include this DTO-level co-presence test
   because the task acceptance criteria explicitly call out
   "round-trips through DTO validation", and this is the cheapest way
   to nail down the example trio's authored cross-references at the
   DTO layer. If director review prefers an even narrower delta, this
   step can be dropped without affecting the materializer round-trip
   proof in step 4.

6. **No documentation-only change required.**
   `docs/architecture/kafka-transaction-message-vocabulary.md` and
   `DIRECTION.md` already prescribe the human-readable contents-link
   shape. `docs/OVERVIEW.md` and
   `docs/architecture/jade-tipi-object-model-design-brief.md` (both in
   OWNED_PATHS) already reference the `contents` link-type and
   well-position pattern. **Default proposal:** no doc edits. If
   director review wants an explicit "see `01 → 11 → 12 → 09` for the
   canonical contents-link transaction" sentence under "Reference
   Examples" (vocabulary doc, lines 283–301), I can add a single
   sentence; flagging here so that decision is explicit.

Expected total surface: ~40–65 lines of Spock test added across one
backend spec (required) and one DTO spec (optional). No example
resource rewrite, no materializer source change, no schema change, no
DTO type change, no Kafka topic change, no integration test
modification.

#### Out-of-scope guardrails (will **not** edit)

- `clients/kafka-kli/**` — kli already accepts `--collection lnk` and
  `--collection typ` and passes `data` through unchanged; no CLI
  change is needed to publish the canonical contents-flow messages.
- `frontend/**` — no UI surface for contents links.
- `jade-tipi/src/main/groovy/.../service/CommittedTransactionMaterializer.groovy`
  — out-of-the-box behavior already correct (see "Current materializer
  behaviour"). Touching it would expand scope beyond the bounded
  proof.
- `jade-tipi/src/main/groovy/.../service/ContentsLinkReadService.groovy`
  — its query criteria already match the materializer's outputs.
- `jade-tipi/src/main/groovy/.../service/TransactionService.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy` — they treat `data`
  as opaque; the contents-flow shape is preserved verbatim through
  ingest, commit, and snapshot read.
- `message.schema.json` — see Schema status above.
- `jade-tipi/src/integrationTest/groovy/.../ContentsHttpReadIntegrationSpec.groovy`
  — already exercises the full Kafka → Mongo → HTTP path with the
  contents-flow shape; the bounded turn does not need to change it.
- `jade-tipi/src/integrationTest/groovy/.../TransactionMessageKafkaIngestIntegrationSpec.groovy`
  — already exercises Kafka ingest persistence; out of scope to
  expand.
- `frontend/.env.local` — generated; never hand-edited (per CLAUDE.md).

### Verification plan (implementation turn)

Per task `VERIFICATION` section. Run inside the developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`).

```sh
# 1. DTO library tests — round-trips and schema validation for the
#    contents-flow example trio plus the optional new DTO co-presence
#    feature.
./gradlew :libraries:jade-tipi-dto:test

# 2. Backend unit tests — CommittedTransactionMaterializerSpec covers
#    the new round-trip co-presence feature plus the existing typ/lnk
#    root-shape and skip cases.
./gradlew :jade-tipi:test

# 3. Narrowest practical Kafka/Mongo integration test (only if local
#    Docker is running and the project documents an opt-in flag).
#    The existing ContentsHttpReadIntegrationSpec exercises the full
#    contents-flow path end-to-end.
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*ContentsHttpReadIntegrationSpec*'

# Optional: also rerun the existing ingest spec to confirm no
# regression in the broader Kafka path.
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
```

Setup commands (per CLAUDE.md "Tooling Refresh"; if local tooling is
missing they are reported, not treated as product blockers):

- Docker stack required for `:jade-tipi:test` because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection
  per project CLAUDE.md. Run
  `docker compose -f docker/docker-compose.yml up -d` first.
- If the Gradle wrapper cache is missing, the first `./gradlew`
  invocation bootstraps it; that is normal first-run behaviour, not
  a blocker.
- If a stale Gradle daemon is implicated, run `./gradlew --stop`
  before retrying (per `DIRECTIVES.md` TASK-026 review note).
- If integration tests cannot run because Docker is not available in
  the sandbox, report the exact `docker compose ... up` command and
  stop rather than treating it as a product blocker.

### Open questions / blockers

None at pre-work time. One soft decision for director review:

- **Step 5 (DTO co-presence test) — keep or drop?** The acceptance
  criterion "round-trips through DTO validation" is already met
  piecewise by the existing `MessageSpec` example loop and the focused
  typ/lnk feature specs (`MessageSpec` lines 330–352, 434–458). The
  proposed step 5 adds an explicit cross-reference assertion (typ
  `data.id` == lnk `data.type_id` within the example trio) which is
  not currently asserted anywhere. I'll include step 5 by default; if
  director review prefers strict minimalism, I'll drop it and rely on
  the existing per-example specs.

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
to set TASK-027 to `READY_FOR_IMPLEMENTATION` (or change the global
signal to `PROCEED_TO_IMPLEMENTATION`) before making any of the
implementation-turn changes listed in the plan above.

STATUS: AWAITING_DIRECTOR_REVIEW
