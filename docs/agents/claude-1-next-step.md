# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 3)

### Directive summary

`DIRECTIVES.md` signals `REQUEST_NEXT_STEP` and lists `TASK-019` at
`READY_FOR_PREWORK`. Director correction on 2026-05-02 moved the task
back from implementation because the Jade-Tipi object JSON format is
still a design question. The new authoritative input is
`docs/architecture/jade-tipi-object-model-design-brief.md`, which
captures human direction that was not available to the revision-2
pre-work pass.

The director's explicit design points to address in this turn:

1. Collection members are **objects**; not every object is an `ent`.
2. Object roots separate `_head`, `properties`, and `links`.
3. Type definitions declare assignable properties only — no
   required/optional complexity, no defaults.
4. `parent_location_id` should not be duplicated on `loc` if the
   canonical parent/child relationship belongs in `lnk`.
5. `contents` is a link type whose directional labels (`contains`,
   `contained_by`) belong on the type, not on each instance.
6. Well position is a candidate property on a `contents` link, but
   modeling each well as a child `loc` is a documented alternative
   that needs explicit tradeoffs.

The directive authorizes pre-work only. No code, tests, materializer
changes, DTO/schema changes, HTTP endpoints, or CouchDB integration
this turn. Treat `TASK-012` and `TASK-018` as historical context.

### What changed since revision 2

- A **human design brief** was committed at
  `docs/architecture/jade-tipi-object-model-design-brief.md` (commit
  `8182203`). It establishes the broader object-model framing and
  expects agent proposals to surface alternatives and tradeoffs.
- Revision-2's mapping doc was treated as a single accepted answer.
  The director clarified it is **evidence**, not the final shape, and
  the revision-3 mapping doc must respond to the brief explicitly,
  with tradeoffs.
- No CouchDB writes, remote credentials, or production reads occurred
  in revision 2 and none will occur in revision 3. The local
  `clarity` and `esp-entity` databases sampled in revision 2 remain
  the evidence base; no re-sampling is needed unless the director
  asks for additional examples.

### Gap analysis — current mapping doc vs. brief

The revision-2 mapping doc at
`docs/architecture/clarity-esp-container-mapping.md` already covers
much of what the brief requires, but does not respond to it
explicitly. Mapping each brief point to the doc's current state:

| Brief point | Current doc state | Gap to close in revision 3 |
|---|---|---|
| 1. Collection members are objects; not every object is `ent`. | Implicit: prototype is `loc`/`lnk` only; `Illumina Library`/`Nucleic Acid` are deliberately excluded as future `ent` candidates (D1). | Add an explicit framing paragraph that distinguishes "object" (any collection member) from "ent" (a specific kind), and call out that `loc`, `lnk`, `typ` are equally first-class objects. |
| 2. `_head` / `properties` / `links` separation. | Anchored constraints section already requires `_head`, top-level `properties`, denormalized `links: {}` per `TASK-013/014`. | Promote this from an inherited constraint to an explicit design alignment, and clarify that `_head` carries only functional metadata (`schema_version`, `document_kind`, `root_id`, `provenance`) and never user-domain fields. |
| 3. Type definitions: no required/optional, no defaults. | The `contents` `typ` declaration lists allowed left/right collections and directional labels but says nothing about per-property required/optional or defaults. | Add a "Type definition shape" subsection stating that the prototype's `typ` declarations carry only assignable property names (and link-specific role/label/allowed-collection metadata), with no required-property markers and no defaults — matching the brief. Note future extension as out-of-scope. |
| 4. No duplicated `parent_location_id` on `loc`. | The four proposed `loc` roots already lack any `parent_location_id` field; parentage lives only in `lnk`. | Make this an explicit design decision (D6) with a one-paragraph justification: parentage is single-sourced in the `contents` link, avoiding split-brain when a child moves. |
| 5. `contents` directional labels on the type. | The `typ~contents` declaration carries `left_to_right_label = "contains"` and `right_to_left_label = "contained_by"`; the `lnk` instances do not duplicate these. | Call this out as a deliberate decision (D7) and remove any temptation to put labels on instance `properties`. |
| 6. Wells: link property vs child `loc`. | D2 declares wells as `lnk.properties.position`; child-`loc` is briefly mentioned as the alternative considered. | Expand into a full "Alternatives & tradeoffs" section comparing **A. wells-as-link-property** (current proposal), **B. wells-as-child-`loc`-objects**, and **C. hybrid (child `loc` only when a well has independent identity, e.g. a barcoded position)**. Score each against source-data fit, query ergonomics, identity stability, link-count growth, and round-trip with extension documents. |

The mapping doc is otherwise sound: sampled-evidence section, four
`loc` roots, one `typ`, two `lnk` roots, identifier convention,
position vocabulary, source Kafka messages, expected Mongo
collections, ambiguities A1–A6, and read-only verification commands.

### Proposed plan

#### Pre-work deliverable (this turn)

Only this file (`docs/agents/claude-1-next-step.md`). The orchestrator
asked for the pre-work response; no other paths are touched until
the director accepts the proposed revisions or moves the task to
`READY_FOR_IMPLEMENTATION`.

#### Pre-work deliverable (next turn, after director accepts this plan)

Update `docs/architecture/clarity-esp-container-mapping.md` with:

1. **New section** "Design-brief alignment" near the top, mapping
   each brief point to a specific decision (D1–D7) in the doc.
2. **New decisions D6 and D7**:
   - D6 — Parent/child containment lives in `lnk + contents` only.
     `loc.properties` carries no `parent_location_id`,
     `parent_loc_id`, or equivalent. ESP's `container.uuid` is read
     from the source record but materialized only as the `left`
     pointer of the corresponding `lnk`.
   - D7 — Directional labels (`contains`, `contained_by`) live on
     the `typ~contents` declaration. `lnk` instances carry only
     `left`, `right`, `type_id`, and instance properties (currently
     `position`).
3. **Type-definition shape subsection**: clarify that `typ` roots in
   this prototype declare assignable property names plus link-type
   metadata (`left_role`, `right_role`, directional labels, allowed
   collections) and do not encode required/optional flags or
   defaults. Reference the brief's "no required/optional complexity,
   no defaults" position.
4. **Alternatives & tradeoffs section** for wells:
   - **A. Wells as `lnk.properties.position`** (current proposal).
     - Pros: zero new `loc` rows per plate; mirrors ESP's source
       encoding (well key in `contents` map); already supported by
       the canonical example and the existing materializer +
       `ContentsLinkReadService` without change; the position
       vocabulary travels with the link, so a future plate move
       carries the slot info inherently.
     - Cons: wells have no independent identity, so a query like
       "what is in plate X's well A1?" is a link-filter rather than
       a direct `loc` lookup; per-well metadata (e.g. a per-well QC
       flag) would need to live on the `contents` link or be deferred
       to a future extension document.
   - **B. Wells as child `loc` objects.**
     - Pros: every well has a stable `loc` id, queryable directly;
       per-well metadata fits naturally as `loc.properties`; uniform
       parent/child treatment with bins and freezers.
     - Cons: 96 (or 384) `loc` rows per plate — multiplied across
       thousands of plates that is the dominant write/read cost,
       and most wells are empty placeholders; identity stability
       across re-imports is fragile (does a re-imported plate get
       the same well ids?); ESP source data does not separately
       identify wells, so well ids would have to be synthesized;
       a contents link would still be needed plate→well and
       well→entity, doubling the link-graph.
   - **C. Hybrid.** Wells become child `loc` only when the source
     record gives the well its own identity (e.g. a barcoded well or
     an ESP record with `class_name == "Container"` and a parent
     reference into the plate). For ESP plates as currently sampled,
     this collapses to A. For future LIMS data with addressed wells,
     it admits B per-record without forcing it everywhere.
     - Pros: matches source identity; no synthetic well ids.
     - Cons: heterogeneous reads — same plate's wells may be a mix
       of `loc` rows and link properties; downstream code must
       handle both shapes.
   - **Recommendation**: ship A in the prototype; document C as the
     forward path if/when source data carries addressable well
     identity; reject B as the default because it pays a 96×–384×
     row-multiplier cost up front for a query benefit that the
     current samples do not require.
5. **Tradeoffs for parentage location**:
   - Putting parentage in `lnk` (current) lets a child be moved with
     a single new `lnk` row plus an end-of-life on the previous
     `lnk` once link versioning lands; putting it on `loc` would
     require an update to the child plus the link table.
   - Putting parentage on `loc.properties.parent_location_id` only
     would split the truth across two storage shapes and lose the
     symmetry with non-containment relationships.
6. **Cross-reference the brief**: a single explicit reference at the
   top of the mapping doc that this design responds to
   `docs/architecture/jade-tipi-object-model-design-brief.md` and
   that the brief is the authoritative human direction.

No edits to the brief itself; the brief is the human's input and is
read-only for this task. No edits to canonical example messages,
`CommittedTransactionMaterializer`, `ContentsLinkReadService`,
schemas, frontend, Docker, or Gradle.

#### Implementation deliverable (only after `READY_FOR_IMPLEMENTATION`)

Unchanged from revision 2:

- One Spock unit test under
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`
  (proposed name `ClarityEspContainerMappingSpec.groovy`) that
  builds the eight transaction messages from the design doc,
  constructs a `CommittedTransactionSnapshot`, invokes
  `CommittedTransactionMaterializer.materialize(snapshot)`, and
  asserts the seven materialized roots match the design-doc JSON
  exactly modulo `_head.provenance.materialized_at`.
- Default proposal: no integration spec. The materializer already
  has integration coverage; the prototype's only novelty is the
  example documents themselves.

### Decisions carried forward from revision 2 (still valid)

- D1 — Stay at `loc + lnk` for the prototype; no `ent` materialization
  required. Backed by the sample selection.
- D2 — Plate wells as `lnk.properties.position` (current default,
  to be reframed as one of three alternatives in revision 3).
- D3 — Position vocabulary by parent kind: `freezer_slot`,
  `bin_slot`, `plate_well`, `tube_position`.
- D4 — Identifier convention embeds source key in short-name plus
  `properties.source_id` and `properties.source_system`.
- D5 — Reuse the canonical `contents` `typ` id; the `typ + create`
  message is included in the prototype transaction for self-
  containment, and the materializer's idempotent path covers re-runs.

### New decisions to add in revision 3

- D6 — Parent/child containment is single-sourced in `lnk + contents`.
  `loc.properties` never carries a `parent_location_id`-style field.
- D7 — Directional labels (`contains`, `contained_by`) are properties
  of the `typ~contents` link-type declaration only; `lnk` instances
  do not repeat them. Backed by `ContentsLinkReadService`'s current
  resolution path, which reads labels from the type and applies them
  on the read side.

### Blockers

None. The brief is in scope; the mapping doc is in scope; the local
CouchDB evidence from revision 2 already covers tubes (Clarity) and
freezer/bin/plate (ESP). No new sampling or credentials are required.

If a future re-run finds CouchDB stopped, the documented setup
commands remain:

```sh
docker compose -f docker/docker-compose.yml up -d couchdb
docker compose -f docker/docker-compose.yml up -d couchdb-init
```

If the local `.env` lacks `COUCHDB_USER`/`COUCHDB_PASSWORD`, add them
to the orchestrator overlay
`/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
and re-materialize the worktree. These are setup steps, not product
blockers.

### Open questions for director review

- **Q-19-E — Wells recommendation.** Revision 3 keeps wells as
  `lnk.properties.position` (option A) and documents B and C as
  alternatives. Confirm A is the chosen prototype direction, or
  redirect to B/C before the mapping doc is rewritten.
- **Q-19-F — Type-definition shape.** Confirm that `typ` declarations
  in the prototype should list only assignable property names plus
  link-type metadata (no required/optional, no defaults). The brief
  reads that way; this question gives the director a single yes/no
  before the doc encodes it as decision D7.
- **Q-19-G — Parentage exclusivity.** Confirm D6 — parentage lives
  only in `lnk`. The alternative (allow a denormalized
  `parent_location_id` on `loc` for read ergonomics) is not in the
  brief but is a frequent LIMS shortcut; rejecting it explicitly in
  the mapping doc avoids drift later.
- **Q-19-H — Brief-vs-mapping authority.** When the brief and the
  revision-2 mapping doc disagree on phrasing, the brief wins by
  default in revision 3 (it is the human direction). Confirm or
  invert.
- **Q-19-I — Scope of revision-3 doc edits.** Should revision 3
  rewrite-in-place or append a "Design-brief alignment" addendum
  while leaving the existing decisions untouched? Append is less
  risky for review; rewrite is cleaner. Default proposal: append
  the alignment section + alternatives section, edit existing D1–D5
  prose only where it conflicts with the brief.

Carrying forward from revision 2 (still relevant if the director has
not implicitly resolved them):

- **Q-19-A — Prototype acceptance.** Loc/lnk-only mapping with the
  four sampled containers (Clarity tube + ESP freezer/bin/plate),
  or drop the Clarity tube for a single ESP chain.
- **Q-19-C — Integration spec inclusion.** Default unit-test-only
  for the implementation turn; confirm whether a
  `JADETIPI_IT_KAFKA`-gated integration spec is wanted.
- **Q-19-D — Position vocabulary names.** Per-parent-kind names
  (`freezer_slot`, `bin_slot`, `plate_well`, `tube_position`) vs a
  single neutral `slot` key.

### Verification proposal

For this pre-work turn:

- Static review only. `git diff --check` and
  `git diff origin/director..HEAD --stat` should show only
  `docs/agents/claude-1-next-step.md` for this commit. No CouchDB,
  Docker, Gradle, or MongoDB commands were run this turn.

For the next pre-work turn (mapping-doc revision):

- Static review only. The expected diff is
  `docs/architecture/clarity-esp-container-mapping.md` plus another
  `docs/agents/claude-1-next-step.md` update.

For the implementation turn (only after `READY_FOR_IMPLEMENTATION`):

```sh
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*ClarityEspContainerMappingSpec*'
./gradlew :jade-tipi:test
```

If implementation-iteration setup blocks any of these,
`docker compose -f docker/docker-compose.yml up -d` and
`./gradlew --stop` are the documented setup steps; blockers are
reported with the exact command and error rather than treated as
product blockers.

### Stay-in-scope check

This turn edits only `docs/agents/claude-1-next-step.md`, a base
owned path. The next-turn revision (after director acceptance) would
edit `docs/architecture/clarity-esp-container-mapping.md`, a
task-expanded owned path under TASK-019.

Paths intentionally not touched this turn:

- `docs/architecture/jade-tipi-object-model-design-brief.md` — read
  only; the human owns this doc.
- `docs/architecture/clarity-esp-container-mapping.md` — read only
  this turn; revision deferred to the next pre-work turn pending
  director acceptance of this plan.
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
  — task-expanded but not touched in this pre-work turn.
- `docs/agents/claude-1.md`, `docs/agents/claude-1-changes.md` —
  base owned, not touched in this turn (no implementation outcome
  to record).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`,
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`,
  `jade-tipi/src/integrationTest/resources/` — task-expanded for
  implementation; not touched in this pre-work turn.

No edits to `CommittedTransactionMaterializer`,
`ContentsLinkReadService`, the Kafka listener, DTOs, schemas,
canonical-example messages, Docker, Gradle, security, frontend, or
any path outside the union above.

STOP.
