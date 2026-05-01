# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-015 — Update contents read service for root-shaped documents (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-015`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-015-root-shaped-contents-read-service.md`,
plan the smallest update to `ContentsLinkReadService`, `ContentsLinkRecord`,
and the focused service/controller specs so the contents read path
understands the root-shaped materialized `typ` and `lnk` documents written
by accepted `TASK-014`.

Required behavior shifts:

- `typ` resolution must move from top-level `kind == "link_type"` /
  `name == "contents"` to **root-shaped** `properties.kind == "link_type"` /
  `properties.name == "contents"`. The materializer's `buildInlineProperties`
  excludes only `id` and `type_id`, so for a `typ + create` with
  `data.kind: "link_type"` and `data.name: "contents"` it now writes
  `properties.kind` and `properties.name` instead of top-level fields.
- `lnk` mapping continues to read top-level `type_id`, `left`, `right`, and
  `properties` — `CommittedTransactionMaterializer` keeps these at the root of
  the `lnk` document (it explicitly copies them into the root before
  `properties`).
- `ContentsLinkRecord.provenance` must be populated from `_head.provenance`
  (the new reserved provenance location written by `TASK-014`) instead of
  top-level `_jt_provenance`. A short, explicit fallback to legacy
  `_jt_provenance` is permitted by `TASK-013`/`TASK-015` and is recommended
  here for safe transition (see plan below).

Out-of-scope (do not touch in this task): `CommittedTransactionMaterializer`,
`TransactionMessagePersistenceService`, Kafka listener/topic, committed
snapshot shape, DTO schemas/examples, Docker/Gradle, security, frontend,
response envelopes, pagination, endpoint joins to `loc`/`ent`, semantic
reference validation, endpoint projection maintenance, extension/pending
pages, update/delete replay, backfill, transaction-overlay reads, required
properties, default values, `TASK-012`, and any new integration coverage.

### Source inspection (TASK-013, TASK-014, current read path)

- `CommittedTransactionMaterializer` writes a self-describing root for
  supported `loc + create`, `typ + create` (`data.kind == "link_type"`), and
  `lnk + create` messages. Each root has top-level `_id`, `id`, `collection`,
  `type_id`, `properties`, `links: {}`, and `_head`. For non-`lnk` roots
  (`loc`, `typ link_type`), `properties` is built by copying `data` minus the
  reserved `id` and `type_id` keys — so a contents link-type declaration
  yields `properties.kind == "link_type"`, `properties.name == "contents"`,
  plus the role/label/allowed-collection facts. For `lnk` roots, top-level
  `type_id`, `left`, `right`, and `properties` are written verbatim from
  `data`.
- `_head.provenance` contains `txn_id`, `commit_id`, `msg_uuid`, source
  `collection`, source `action`, `committed_at`, and `materialized_at`. New
  roots no longer carry top-level `_jt_provenance`. `TASK-014` accepted this
  contract.
- `ContentsLinkReadService.resolveContentsTypeIds()` currently builds
  `Criteria.where("kind").is("link_type").and("name").is("contents")` against
  `typ` — this no longer matches root-shaped `typ` rows and will return zero
  declarations, causing every contents read to short-circuit to empty.
- `ContentsLinkReadService.toRecord(Map row)` currently reads
  `row.get('_jt_provenance')` for provenance — this returns `null` for new
  root-shaped `lnk` rows.
- `ContentsLinkReadService.findByEndpoint(...)` already filters
  `lnk.type_id $in <typeIds>` and `lnk.<endpointField> == id` and sorts by
  `_id` ASC; that logic is unchanged because `lnk` keeps top-level
  `type_id`/`left`/`right`. No production controller change is needed.
- `ContentsLinkRecord` carries `linkId`, `typeId`, `left`, `right`,
  `properties`, and `provenance: Map<String, Object>`. Wire shape stays
  stable — only the *source* of `provenance` moves.
- The "Reading `contents` Links" section of
  `docs/architecture/kafka-transaction-message-vocabulary.md` documents the
  current pre-TASK-014 mapping (top-level `kind`/`name` and verbatim
  `_jt_provenance`) and needs a small narrative update to match the new
  read.

### Proposed plan (smallest viable update)

1. **`ContentsLinkReadService` — typ resolution.** Change
   `resolveContentsTypeIds()` so the `typ` `Criteria` queries
   `properties.kind == "link_type"` and `properties.name == "contents"`. Keep
   `_id` ASC sort and the existing `$in` strategy on `lnk.type_id`. Update
   the field constants to `FIELD_PROPERTIES_KIND = "properties.kind"` and
   `FIELD_PROPERTIES_NAME = "properties.name"` (or replace the two constants
   with a single `FIELD_PROPERTIES = "properties"` and reuse the existing
   `FIELD_KIND` / `FIELD_NAME` via dotted strings — pick whichever keeps the
   diff smallest; recommendation: introduce two new dotted constants and
   delete the old `FIELD_KIND` / `FIELD_NAME` constants since they are no
   longer used).
2. **`ContentsLinkReadService` — `lnk` mapping.** Leave `findByEndpoint` and
   the `lnk` query untouched. Top-level `lnk.type_id`, `lnk.left`,
   `lnk.right`, and `lnk.properties` continue to source `ContentsLinkRecord`
   fields verbatim.
3. **`ContentsLinkReadService` — provenance source.** Replace
   `row.get('_jt_provenance')` with a small private helper
   `extractProvenance(Map row)` that:
   - Reads `_head.provenance` (the new canonical location) when `_head` is a
     `Map` and contains a `Map` `provenance` value, returning a copy.
   - Falls back to top-level `_jt_provenance` only when `_head` is missing,
     null, or has no `provenance` sub-map. Keep this fallback explicitly
     scoped to the copied-data-shape transition allowed by `TASK-013` and
     `TASK-015`.
   - Returns `null` when neither source is present (preserves the current
     "missing provenance is null" contract).
   Add new constants
   `FIELD_HEAD = "_head"` and `HEAD_PROVENANCE = "provenance"`. Keep
   `FIELD_PROVENANCE_LEGACY = "_jt_provenance"` only as long as the fallback
   exists. The fallback is small (one branch + one new test row), explicit,
   and easy to remove once stale rows are confirmed gone.
4. **`ContentsLinkRecord`** — no production change. Field set, types, and
   serialization stay the same; only the source of `provenance` moves
   inside the service. Existing `@Immutable` generated equals/hashCode and
   the controller's JSON wire shape are unaffected.
5. **`ContentsLinkReadController`** — no production change. Routes stay
   `GET /api/contents/by-container/{id}` and `GET /api/contents/by-content/{id}`,
   delegation to the service is unchanged, and the JSON array contract is
   unchanged.

### Proposed spec updates

`ContentsLinkReadServiceSpec.groovy`:

- Update `typRow(...)` helper to build a root-shaped `typ` document:
  ```
  [_id: id, id: id, collection: 'typ', type_id: null,
   properties: [kind: 'link_type', name: 'contents', /* declarative facts optional */],
   links: [:],
   _head: [schema_version: 1, document_kind: 'root', root_id: id,
           provenance: [txn_id: '...', commit_id: '...', msg_uuid: '...',
                        collection: 'typ', action: 'create',
                        committed_at: ..., materialized_at: ...]]]
  ```
- Update `lnkRow(...)` helper to drop top-level `_jt_provenance` and instead
  attach `_head.provenance` with the same field set used today (`txn_id`,
  `commit_id`, `msg_uuid`, `committed_at`, `materialized_at`, plus the new
  source `collection`/`action` keys). The top-level `type_id`, `left`,
  `right`, and `properties.position` keys remain.
- The "queries lnk with type_id $in resolved IDs ..." features change their
  captured `typ` query assertions from
  `typQueryDoc.get('kind') == 'link_type'` /
  `typQueryDoc.get('name') == 'contents'` to
  `typQueryDoc.get('properties.kind') == 'link_type'` /
  `typQueryDoc.get('properties.name') == 'contents'` (Spring Mongo
  `Criteria.where("properties.kind").is(...)` serializes the path as a
  dotted key in the query object). The `lnk` query assertions are
  unchanged.
- The "typ records that are not link_type..." feature similarly asserts on
  `properties.kind` / `properties.name`.
- Replace the existing "missing `_jt_provenance` returns null provenance"
  case with two cases:
  1. **Canonical provenance source**: a `lnk` row with `_head.provenance`
     and no top-level `_jt_provenance` produces a `ContentsLinkRecord`
     whose `provenance` carries `txn_id`, `commit_id`, `msg_uuid`,
     `committed_at`, and `materialized_at` from `_head.provenance`.
  2. **Legacy fallback**: a `lnk` row with no `_head` (or `_head` lacking
     `provenance`) but a top-level `_jt_provenance` map still maps that
     legacy map verbatim into `ContentsLinkRecord.provenance`. Add a third
     case asserting `provenance == null` when both sources are absent.
- Forward/reverse "verbatim fields and provenance" features assert
  provenance fields read from `_head.provenance`.
- Blank-id (`null`, empty, whitespace) rejection, no-Mongo-on-blank, and
  no-write assertions are unchanged.
- The unresolved-endpoint and no-`contents`-declared cases are unchanged
  (typ query short-circuit when no rows, no `lnk` join to `loc`/`ent`).

`ContentsLinkReadControllerSpec.groovy`:

- No production controller change is needed and the spec already builds
  `ContentsLinkRecord` instances directly with a `provenance` map. The
  existing JSON-path assertions on `provenance.txn_id`, `provenance.commit_id`,
  etc. continue to pass because the wire shape is stable. **No spec change is
  required**, but a trivial refresh (test data comments referring to
  `_head.provenance` instead of `_jt_provenance`) is acceptable as a
  no-behavior cleanup if it stays inside this task's owned paths.

### Proposed doc update

Edit only the "Reading `contents` Links" section of
`docs/architecture/kafka-transaction-message-vocabulary.md`:

- Change "first query `typ` for documents with `kind == "link_type"` and
  `name == "contents"`" to "first query `typ` for documents with
  `properties.kind == "link_type"` and `properties.name == "contents"`".
- Change "verbatim `_jt_provenance` sub-document" to "verbatim
  `_head.provenance` sub-document, with a short transitional fallback to
  the legacy top-level `_jt_provenance` for documents materialized before
  TASK-014".
- Leave the `lnk` field-mapping language unchanged (`type_id`, `left`,
  `right`, `properties`).

The existing "Committed Materialization Of Locations And Links" section
still describes the pre-TASK-014 copied-data shape with `_jt_provenance`
and is now stale, but updating it is materializer-shape doc work that
belongs to a TASK-014 doc follow-up rather than this read-service task.
**Open question (Q1 below) flags this for the director's call.**

### Verification proposal

After implementation, run the task's required Gradle verification chain
inside the `developers/claude-1` worktree:

1. `./gradlew :jade-tipi:compileGroovy`
2. `./gradlew :jade-tipi:compileTestGroovy`
3. `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
4. `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
5. `./gradlew :jade-tipi:test`

If local tooling, Gradle locks, Docker, or Mongo setup blocks any of the
above, report the documented setup commands rather than treating setup as
a product blocker:

- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` to
  bring up Mongo when `:jade-tipi:test` requires it (the unit-suite Spring
  context still tries to open a Mongo connection during `contextLoads`).
- `./gradlew --stop` when stale Gradle daemons are implicated (e.g. a
  Gradle wrapper cache lock under `/Users/duncanscott/.gradle`), then
  re-run the blocked command.
- Always report the exact blocked command and its error verbatim alongside
  the setup command.

### Blockers and open questions

- **Q1 — Materialization doc currency.** The "Committed Materialization Of
  Locations And Links" section of
  `docs/architecture/kafka-transaction-message-vocabulary.md` still
  describes the pre-TASK-014 copied-data shape with `_jt_provenance`. The
  doc is on `TASK-015`'s `OWNED_PATHS`. Do you want this task to refresh
  that section to match TASK-014's accepted root-shape contract, or keep
  TASK-015's doc edits scoped strictly to the "Reading `contents` Links"
  section and defer the materialization-section refresh to a separate
  TASK-014 doc follow-up? Recommendation: defer (keep TASK-015 narrow).
- **Q2 — Legacy `_jt_provenance` fallback.** Recommendation is to
  implement an explicit, covered fallback per `TASK-013`/`TASK-015`'s
  allowance, since pre-TASK-014 dev/integration runs may have left rows
  with top-level `_jt_provenance` in `lnk`. If you would rather drop the
  fallback now and rely on cleared collections, the implementation
  shrinks by one branch and one Spock case. No blocker; awaiting director
  decision.
- **Q3 — Field-constant churn.** The current service has
  `FIELD_KIND`/`FIELD_NAME`/`FIELD_PROVENANCE` constants used only by the
  to-be-removed query/mapping branches. Recommendation: delete them when
  introducing the new `properties.kind`, `properties.name`, `_head`, and
  `_head.provenance` constants. No behavior impact; flagged so a strict
  scope reviewer sees the constant set will narrow.
- No external blockers identified. Implementation is straightforward
  once `TASK-015` moves to `READY_FOR_IMPLEMENTATION`.

STOP.
