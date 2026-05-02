# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` signals `REQUEST_NEXT_STEP` and lists `TASK-019 - Prototype
Clarity/ESP container materialization` as `READY_FOR_PREWORK` and the next
prioritized unit. Scope is expanded for this task to:

- `docs/architecture/clarity-esp-container-mapping.md`
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
- `jade-tipi/src/integrationTest/resources/`

Pre-work only is authorized. Implementation must not begin until the
director moves `TASK-019` to `READY_FOR_IMPLEMENTATION`. The directive
constrains the work tightly: read-only access to local CouchDB at
`http://127.0.0.1:5984/clarity` and `http://127.0.0.1:5984/esp-entity`,
sample one or two likely container records (tube and/or plate), propose
the smallest mapping into root-shaped `loc` and `lnk` documents, and
explicitly resolve plate-well representation. No import/sync machinery,
no Spring CouchDB initialization, no broad source-schema coverage.

`TASK-018` is accepted-as-superseded; `TASK-012` is historical context only.
The accepted root-document contract from `TASK-013`/`TASK-014` and the
contents read service from `TASK-015` are the binding shapes for any
proposed mapping.

### Anchored facts that constrain the design

These come from already-accepted artifacts in this worktree; the proposal
below is consistent with them.

1. **Root document shape** (per `CommittedTransactionMaterializer`,
   `TASK-013/014/015`): every materialized root has `_id`, `id`,
   `collection`, top-level `type_id`, explicit `properties`, denormalized
   `links: {}`, and reserved `_head` with
   `schema_version`, `document_kind=root`, `root_id`, and `provenance`
   (`txn_id`, `commit_id`, `msg_uuid`, `collection`, `action`,
   `committed_at`, `materialized_at`). For `lnk` roots, `left`, `right`,
   and instance `properties` are top-level fields. The materializer
   writes `links: {}` and does **not** maintain endpoint projections
   yet.
2. **Contents type resolution** (per `ContentsLinkReadService`): the
   canonical `contents` link type is the `typ` root with
   `properties.kind == "link_type"` and `properties.name == "contents"`.
   Its `allowed_left_collections` is `["loc"]` and
   `allowed_right_collections` is `["loc", "ent"]` (canonical example
   `11-create-contents-type.json`). Containment of one container inside
   another is therefore already valid (`loc → loc`); samples in
   containers are also valid (`loc → ent`).
3. **Position-on-link precedent** (canonical example
   `12-create-contents-link-plate-sample.json`): the canonical contents
   link already carries
   `properties.position = { kind: "plate_well", label: "A1", row: "A",
   column: 1 }`. A plate well as a *link property* is the accepted
   precedent and the materializer copies arbitrary `lnk` properties
   verbatim.
4. **Identifier convention** (every canonical example): root ids use
   `<org>~<grp>~<txn-uuid>~<collection>~<short-name>`. The
   short-name segment is human readable; the txn-uuid is a UUIDv7 from
   the producing transaction. Mapped source records can keep the source
   identifier verbatim in the short-name segment so a Clarity LIMSID
   or ESP key remains traceable in the materialized id.
5. **Materializer scope** (per `CommittedTransactionMaterializer`):
   only `loc + create`, `typ link_type + create`, and `lnk + create`
   are materialized; everything else is `skippedUnsupported`. Update,
   delete, and other actions are intentionally absent. Any prototype
   that needs only `create` messages on these three collections is
   fully covered by the existing materializer with no production
   changes; only fixtures and tests would be added.

### Proposed plan

The deliverables across pre-work and the (later, director-gated)
implementation are scoped narrowly.

#### Pre-work deliverable (this and the next iteration)

A new design document
`docs/architecture/clarity-esp-container-mapping.md` containing:

- **Source samples**: 1–2 redacted Clarity records (preferring a
  `Container` of type `Tube` and a `Container` of type `96 well plate`
  if both exist locally) and 0–1 ESP records that may carry physical
  location or containment for the same containers. Document field
  paths and value examples; redact any internal identifiers if they
  look like project codenames or PII.
- **Mapping table**: each chosen source field → either a Jade-Tipi
  root field, a `properties.*` entry, a `lnk + contents` link
  property, a parent `loc + contents` link, or "intentionally
  dropped". For the chosen sample(s) only.
- **Materialized example documents**: full JSON of every root that
  the prototype would write, in the exact root shape produced by
  `CommittedTransactionMaterializer` (including `_head.provenance`
  scaffold values), per Mongo collection (`loc`, `typ`, `lnk`,
  optionally `ent`).
- **Source Kafka transaction messages**: the create messages whose
  commit would yield those roots, in the canonical message vocabulary
  used under `libraries/jade-tipi-dto/src/main/resources/example/`.
- **Decisions and ambiguities**: explicit resolutions for the four
  decision points below, marked either *resolved* with the chosen
  shape or *deferred* with the reason and what evidence would
  resolve it.
- **Verification commands**: read-only `curl` commands the operator
  can run against `http://127.0.0.1:5984/{clarity,esp-entity}` to
  reproduce the sample selection (with credentials passed via
  `-u "$COUCHDB_USER:$COUCHDB_PASSWORD"`, never echoed). Also
  proposed Gradle commands for any later prototype tests
  (`./gradlew :jade-tipi:compileGroovy`,
  `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:test --tests
  '*ClarityEspContainerMapping*'`).

#### Implementation deliverable (only after `READY_FOR_IMPLEMENTATION`)

The smallest test-only artifacts that prove the mapping holds against the
existing materializer:

- One Spock unit test in
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/` (e.g.
  `ClarityEspContainerMappingSpec.groovy`) that constructs the
  proposed canonical messages, drives them through
  `CommittedTransactionMaterializer.materialize(...)` (or its
  document-build helper), and asserts the resulting root documents
  match the design doc's example JSON exactly (modulo the volatile
  `_head.provenance.materialized_at`).
- Optionally one integration spec under
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
  that publishes the canonical messages through the existing Kafka /
  txn pipeline against a real Mongo (gated by the existing
  `JADETIPI_IT_KAFKA` env switch) and reads them back via
  `ContentsLinkReadService`. This is **only** added if the unit-test
  evidence is judged insufficient by the director; default proposal
  is to skip the integration spec for this prototype.
- Optional fixtures under
  `jade-tipi/src/integrationTest/resources/` if the integration spec
  is included; otherwise no fixture file is needed.

No edits to `CommittedTransactionMaterializer`,
`ContentsLinkReadService`, the Kafka listener, DTOs, schemas,
canonical-example messages, Docker, Gradle, security, or frontend.
The whole point of this prototype is to *use* the accepted machinery
without changing it.

### Source sampling strategy (read-only, requires harness allowance)

I will sample with the following commands. None write; all use HTTP
basic auth against `127.0.0.1:5984` with the local
`COUCHDB_USER`/`COUCHDB_PASSWORD` from the materialized worktree
`.env`. Remote source credentials
(`JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`PASSWORD`) are not used here —
this reads only the locally replicated databases.

```sh
set -a; . .env; set +a
AUTH="$COUCHDB_USER:$COUCHDB_PASSWORD"
BASE=http://127.0.0.1:5984

# 1. Sanity: replicated databases exist.
curl -fsS -u "$AUTH" "$BASE/_all_dbs" | jq -r '.[]' | grep -E '^(clarity|esp-entity)$'

# 2. Replication progress (informational, no docs read).
curl -fsS -u "$AUTH" "$BASE/_scheduler/jobs"  | jq '.jobs   | map({id,target,state,history:(.history|.[0:1])})'
curl -fsS -u "$AUTH" "$BASE/_scheduler/docs"  | jq '.docs   | map({id,target,state,info:.info})'
curl -fsS -u "$AUTH" "$BASE/clarity"          | jq '{db_name, doc_count, update_seq:(.update_seq|tostring|.[0:32])}'
curl -fsS -u "$AUTH" "$BASE/esp-entity"       | jq '{db_name, doc_count, update_seq:(.update_seq|tostring|.[0:32])}'

# 3. Discover container-shaped Clarity docs by sampling top-level keys.
#    Clarity JSON-from-XML typically nests under e.g. "container" or
#    "art:artifact" at the root; we cannot assume the exact key without
#    looking. Use a small _all_docs page and inspect top-level keys.
curl -fsS -u "$AUTH" "$BASE/clarity/_all_docs?limit=20&include_docs=true" \
  | jq '.rows | map({id, top_keys: (.doc | keys), inner_keys: (.doc | to_entries | map({(.key): (if (.value|type)=="object" then (.value|keys) else (.value|type) end)}) | add) }) | .[0:5]'

# 4. Once container-shaped keys are seen, narrow to a tube and a plate
#    using $regex on a likely field. Plug in observed key paths only
#    after step 3. Example skeleton with placeholders TUBE_PATH and
#    PLATE_PATH derived from step 3 evidence:
#       curl -fsS -u "$AUTH" "$BASE/clarity/_find" -H 'content-type: application/json' \
#         -d "{\"selector\":{\"$TUBE_PATH\":{\"\$regex\":\"(?i)tube\"}},\"limit\":2,\"fields\":[\"_id\",\"$TUBE_PATH\",\"name\",\"limsid\"]}"

# 5. Same shape against esp-entity for likely location/containment carriers.
curl -fsS -u "$AUTH" "$BASE/esp-entity/_all_docs?limit=20&include_docs=true" \
  | jq '.rows | map({id, top_keys: (.doc | keys)}) | .[0:5]'
```

I will not log full document bodies into the design doc; only redacted
snippets that show *which* fields exist on the chosen records. No
internal LIMSID or container barcode is committed unless the director
confirms it is acceptable; if any look sensitive I will substitute
`REDACTED-LIMSID-1` etc. and keep a private mapping in my session
only.

### Tentative mapping rules (subject to source evidence)

These are the working rules. The design doc will record the version
that survives contact with the actual sampled records. Each rule is
justified against the constraints in
"Anchored facts that constrain the design" above.

- **Tube container** → one root in `loc` collection.
  - `id`: `<org>~<grp>~<txn-uuid>~loc~<source-id>` where `<source-id>`
    embeds the Clarity LIMSID (or ESP key, whichever is the
    canonical source). Example:
    `jade-tipi-org~dev~018fd849-3a40-7000-8b00-cccccccccccc~loc~clarity-27-1234`.
  - `type_id`: null in the prototype (no `typ entity_type` for
    "tube" yet; matches the accepted `loc` example which also has
    null `type_id`).
  - `properties`: minimal — `name`, optional `kind` (`tube`),
    optional `barcode` if present, optional `volume_uL` if present.
    No required fields are synthesized.
  - No physical location material if Clarity doesn't carry it. ESP
    parent/location, if found for the same source object during
    sampling, becomes a separate parent `loc` plus a
    `lnk + contents` from parent → tube (rule below).
- **Plate container** → one root in `loc` collection, same shape as
  tube but `kind = "plate"` and (if known) `format = "96-well"`,
  `rows = 8`, `columns = 12`. Wells are *not* separate `loc` roots
  in the prototype (see well-representation decision below).
- **Sample inside a container** → one root in `ent` collection
  *only when* the source record clearly represents a biological
  sample (e.g. Clarity `Sample` or `Artifact` of analyte type, or an
  ESP entity document that names a sample distinct from its
  container). Otherwise the proposal models containment between two
  `loc` roots and skips `ent` entirely. The contents link type
  already permits both `loc → loc` and `loc → ent`.
- **Containment** → one root in `lnk` collection per containment
  edge.
  - `type_id`: the existing canonical contents `typ` id
    (`jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents`
    from the canonical example). The prototype may either reuse
    that exact id (recommended, since the materializer + read
    service already key off
    `properties.kind=link_type ∧ properties.name=contents` regardless
    of id) or mint a fresh `typ link_type` row with the same
    `properties.name=contents` and a different id; both work with
    the existing read service. Default proposal: reuse the canonical
    id to keep test fixtures small.
  - `left`: container `loc` id; `right`: contained `loc` or `ent`
    id.
  - `properties` for plate→sample/well: the
    `position = { kind: "plate_well", label, row, column }`
    sub-document already established by canonical example
    `12-create-contents-link-plate-sample.json`. For tube→sample
    or `loc → loc` containment, `properties` is `{}`.
- **Provenance**: written by the materializer as
  `_head.provenance.{txn_id, commit_id, msg_uuid, collection,
  action, committed_at, materialized_at}` from the producing
  transaction. The mapping doc shows realistic placeholder values.

### Plate-well representation — proposed default and rationale

Three options were on the table per the directive: child `loc` per
well, well as a contents-link property, or deferred decision.

**Recommendation: keep wells as link properties** in the prototype
(`lnk.properties.position`). Justifications:

- The accepted canonical example
  `12-create-contents-link-plate-sample.json` already does it this
  way; the materializer copies `properties` verbatim and
  `ContentsLinkReadService` returns it as
  `ContentsLinkRecord.properties`. Zero new code is needed.
- Wells are not independent physical locations addressable outside
  their parent plate; promoting each well to a `loc` root would
  produce 96 (or 384) extra rows per plate, all with effectively
  the same identity as `parent_plate + position`. That is more
  than the prototype warrants and would force a follow-up question
  about how a well loc relates to its plate (a second contents
  link?), which compounds rather than reduces ambiguity.
- The contents `typ` already permits
  `allowed_right_collections = ["loc", "ent"]`, but elevating
  wells to `loc` would push `loc → loc` containment into the
  prototype just for the well case, and then `loc → ent` for
  the well→sample case, producing a two-hop containment edge for
  what is conceptually one position assignment.
- Source evidence to override this default would be: ESP records
  that already model wells as separate addressable entities with
  their own ids, persistent metadata, and lifecycle independent of
  the plate. If sampling shows that, the design doc will switch to
  child `loc` wells and document the tradeoff. Until then,
  link-property is the smaller change.

The design doc will record the rule as: *plate→sample contents
links carry `properties.position` per the canonical example; wells
are not first-class `loc` roots in this prototype; revisit if
later evidence shows wells need independent identity.*

### Updated proposed verification

For the design-doc pre-work iteration:

1. Run the sampling commands above; redact and inline 1–2 source
   record skeletons into the design doc.
2. Static review only: `git diff --check`,
   `git diff origin/director..HEAD --stat` to confirm only
   `docs/architecture/clarity-esp-container-mapping.md`,
   `docs/agents/claude-1-next-step.md`, and
   `docs/agents/claude-1-changes.md` (after acceptance) move.
3. No Gradle, Docker, or MongoDB commands are run during pre-work.

For the implementation iteration (after director approval):

1. `./gradlew :jade-tipi:compileGroovy` and
   `./gradlew :jade-tipi:compileTestGroovy` — confirm new test
   compiles.
2. `./gradlew :jade-tipi:test --tests
   '*ClarityEspContainerMappingSpec'` — run the new unit spec.
3. If the integration spec is included (default: not included),
   `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest
   --tests '*ClarityEspContainerMaterializationSpec'`, requiring
   the local Mongo/Kafka/Keycloak Docker stack.

### Blockers

- **B-19-1 — Sampling not allowed in this turn.** Running the
  CouchDB sampling commands above was denied by the harness this
  turn (treated as scope creep beyond writing the next-step doc).
  The directive does authorize read-only local CouchDB sampling
  during pre-work, so this is a per-turn permission issue, not a
  product blocker. **Proposed resolution:** the director either
  (a) approves a follow-up pre-work iteration whose only purpose is
  running the redacted sampling commands above and folding the
  evidence into the design doc, or (b) explicitly authorizes
  sampling in `DIRECTIVES.md` so the next routed turn may execute
  the commands. Without sampled evidence, the design doc would
  have to remain hypothetical, which the directive specifically
  asks against.

### Open questions

- **Q-19-1 — Identifier convention for source-mapped roots.** The
  proposal embeds the Clarity LIMSID or ESP key as the short-name
  segment of the Jade-Tipi id. This keeps source traceability
  visible in the id but makes the id stable across re-imports
  only if the source key is itself stable. Should the prototype
  instead mint fresh UUIDv7 short-names and carry the source key
  as a `properties.source_id` field? **Recommendation:** embed
  for the prototype because it makes the materialized examples
  legible; switch to a `properties.source_id` field once any real
  import/sync is built. Either choice is local to the prototype's
  example messages and changes nothing in the materializer.
- **Q-19-2 — Whether to mint a new `typ link_type` for `contents`
  or reuse the canonical example id.** Reuse keeps fixtures small
  and matches `ContentsLinkReadService`'s name-based lookup;
  minting fresh ids exercises the multi-id `$in` branch of that
  service. **Recommendation:** reuse for the prototype. Flag if
  the director wants the fresh-id branch covered here.
- **Q-19-3 — Whether to include a real-Mongo integration spec.**
  Default proposal is unit-test-only because the materializer
  already has integration coverage and the prototype's only
  novelty is the example documents themselves. **Recommendation:**
  unit-only; add integration coverage in a later task if the
  prototype's mapping decisions are accepted as the basis for
  real import work.
- **Q-19-4 — Whether `ent` participation is in scope at all.**
  The directive permits `ent` "only if the sampled source clearly
  represents a biological/sample entity". If sampling shows no
  such record, the prototype reduces to two `loc` roots and one
  `lnk + contents` between them. **Recommendation:** decide after
  sampling; if ESP carries an explicit biological-sample document
  the prototype will include one `ent` root, otherwise it will not.

### Stay-in-scope check

All proposed file edits sit inside this task's expanded
`OWNED_PATHS` plus claude-1's base
`docs/agents/claude-1-{md,next-step.md,changes.md}` paths:

- `docs/architecture/clarity-esp-container-mapping.md` (expanded)
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
  (expanded — only `LATEST_REPORT:` appended after director review)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
  (expanded — *no* edits planned in pre-work; would only see new
  test files in implementation if Q-19-3 changes default)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`
  (expanded — implementation only)
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
  (expanded — only used if Q-19-3 flips)
- `jade-tipi/src/integrationTest/resources/` (expanded — same)
- `docs/agents/claude-1-next-step.md` (this file, base owned)
- `docs/agents/claude-1-changes.md` (base owned, written only
  after acceptance)

No edits to `CommittedTransactionMaterializer`,
`ContentsLinkReadService`, the Kafka listener, DTOs, schemas,
canonical-example messages, Docker, Gradle, security, frontend, or
any path outside the union above.

STOP.
