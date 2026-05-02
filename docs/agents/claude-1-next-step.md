# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 4)

### Directive summary

`DIRECTIVES.md` signals `REQUEST_NEXT_STEP`. `TASK-019` is at
`READY_FOR_PREWORK`. The director's 2026-05-02 review of the
revision-3 plan accepted that scope stayed inside owned paths but
flagged that the previous turn produced only a plan for a later
mapping-doc revision instead of producing the required design
proposal. The directive is explicit:

> Next claude-1 turn: update
> `docs/architecture/clarity-esp-container-mapping.md` directly with
> the design-brief alignment, D6/D7 parentage and directional-label
> decisions, type-definition shape, and explicit wells
> alternatives/tradeoffs.

The director's default answers for the previously-open questions are
applied verbatim:

- The brief wins over revision-2 phrasing where they differ.
- Wells stay as `lnk.properties.position` for the prototype; child
  `loc` and hybrid alternatives are documented with tradeoffs.
- `typ` roots declare only assignable property names plus link-type
  metadata. No required/optional, no defaults.
- Parentage is single-sourced in `lnk`. No `parent_location_id` on
  `loc`.
- Append alignment + tradeoff sections; make narrow consistency edits
  to existing decisions.
- No implementation, no code, no tests, no Kafka, no Docker, no
  Gradle work this turn.

### What this turn produced

This turn updates two files within owned paths:

1. `docs/architecture/clarity-esp-container-mapping.md` (task-expanded
   path under TASK-019). Edits are additive plus one narrow
   consistency tweak to D2:
   - **New paragraph** in the intro that names
     `docs/architecture/jade-tipi-object-model-design-brief.md` as
     the authoritative human direction and states "the brief wins"
     where the prior text disagrees.
   - **New section "Design-brief alignment"** between the intro and
     the existing "Anchored constraints" section. It is a 6-row
     table that maps each brief point to a specific decision (D1,
     anchored constraint #1, D5/Type shape, D6, D7, D2 +
     alternatives) and a one-paragraph framing that distinguishes
     "object" (any collection member) from `ent` (one specific kind)
     and notes that `loc`, `lnk`, `typ` are equally first-class.
   - **D2 narrow edit**: appends a one-paragraph cross-reference to
     the new "Alternatives & tradeoffs — wells representation"
     section. Existing wording is unchanged.
   - **New decision D6** under "Mapping decisions": parentage is
     single-sourced in `lnk + contents`; `loc.properties` carries no
     `parent_location_id`. Justified from the ESP `container.{uuid,
     slot}` shape (parent identity and position both already on the
     parent edge, so they belong on the link).
   - **New decision D7** under "Mapping decisions": directional
     labels (`contains`, `contained_by`) live on the `typ~contents`
     declaration only; `lnk` instances do not repeat them. Justified
     from the existing `ContentsLinkReadService` resolution path
     that already joins to the type at read time.
   - **New "Type definition shape" subsection** under "Mapping
     decisions": the prototype's `typ` root declares only assignable
     property names (`position`) plus link-type metadata
     (`left_role`, `right_role`, directional labels, allowed
     collections). No required/optional, no defaults. Future
     extensions are anticipated but out of scope.
   - **New section "Alternatives & tradeoffs"** between "Mapping
     decisions" and "Selected prototype":
     - "Wells representation" — full A/B/C comparison
       (link-property, child-loc, hybrid) with pros/cons each and
       the recommendation to ship A and document C as the forward
       path. Quantitative argument against B (96×–384× row
       multiplier) is grounded in the sampled plate data.
     - "Parentage location" — A/B/C comparison
       (`lnk`-only, `loc.parent_id`-only, both) with pros/cons each
       and the recommendation to ship A and reject B and C.

   No edits to: `D1`, `D3`, `D4`, `D5` text; the sampled-evidence
   section; identifier conventions; the four `loc` roots; the `typ`
   root; the two `lnk` roots; the source Kafka transaction messages;
   expected Mongo collections; known ambiguities A1–A6; or
   verification commands.

2. `docs/agents/claude-1-next-step.md` (base owned path). This file
   — the developer's pre-work response.

### Files intentionally not touched this turn

- `docs/architecture/jade-tipi-object-model-design-brief.md` — the
  brief is the human's input and is read-only for this task.
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
canonical-example messages, Docker, Gradle, security, or frontend.

### Decisions in the revised mapping doc (D1–D7)

- D1 — Prototype materializes only `loc`, `typ`, and `lnk` roots; no
  `ent`. Sample selection backs this — every chosen source record is
  a container, not a biological entity.
- D2 — Plate wells stay as `lnk.properties.position`. Cross-references
  the new "Alternatives & tradeoffs — wells representation" section.
- D3 — Per-parent-kind position vocabulary (`freezer_slot`,
  `bin_slot`, `plate_well`, `tube_position`).
- D4 — Identifier convention embeds source key in short-name plus
  `properties.source_id` and `properties.source_system`.
- D5 — Reuse the canonical `contents` `typ` id; the `typ + create`
  message is included in the prototype transaction for
  self-containment.
- **D6 (new)** — Parentage is single-sourced in `lnk + contents`. No
  `parent_location_id` on `loc`.
- **D7 (new)** — Directional labels live on the `typ~contents`
  declaration only; `lnk` instances do not repeat them.

Plus the new "Type definition shape" subsection that records the
brief's "no required/optional, no defaults" rule as the prototype's
`typ` shape.

### Blockers

None. The mapping doc is in scope (task-expanded path); the brief is
in scope (read-only); the local CouchDB evidence from the prior
sampling pass already covers the tube + freezer/bin/plate chain. No
new sampling, no new credentials, and no remote reads were required
this turn.

If a future re-run finds local CouchDB stopped, the documented setup
commands remain:

```sh
docker compose -f docker/docker-compose.yml up -d couchdb
docker compose -f docker/docker-compose.yml up -d couchdb-init
```

If the local `.env` lacks `COUCHDB_USER` / `COUCHDB_PASSWORD`, add
them to the orchestrator overlay
`/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
and re-materialize the worktree. These are setup steps, not product
blockers.

### Open questions for director review

These were raised in revision 3 and are answered here per the
director's defaults; this list is kept short so the director can
override quickly if any default was unintended.

- **Q-19-E — Wells recommendation.** Revision 4 keeps wells as
  `lnk.properties.position` (option A) and documents B and C with
  tradeoffs. Recommendation in the mapping doc is to ship A and
  document C as the forward path. Confirm or override.
- **Q-19-F — Type-definition shape.** Revision 4 declares only
  assignable property names (`position`) plus link-type metadata in
  the `typ~contents` root. No required/optional, no defaults.
  Confirm or override.
- **Q-19-G — Parentage exclusivity.** Revision 4 makes D6 explicit:
  parentage lives only in `lnk`. Both alternatives (B inline-only,
  C both) are documented and rejected with reasons. Confirm or
  override.
- **Q-19-H — Brief-vs-mapping authority.** The intro now states the
  brief wins where they conflict; the mapping doc has been narrowed
  in place to match. Confirm or override.
- **Q-19-I — Scope of revision-4 doc edits.** Append-style; existing
  D1–D5 prose unchanged except for the one-paragraph cross-reference
  on D2. Confirm or override.

Carrying forward from revision 2 (still relevant if the director has
not implicitly resolved them):

- **Q-19-A — Prototype acceptance.** Loc/lnk-only mapping with the
  four sampled containers (Clarity tube + ESP freezer/bin/plate),
  or drop the Clarity tube for a single ESP chain.
- **Q-19-C — Integration spec inclusion.** Default proposal for the
  later implementation turn is unit-test-only; confirm whether a
  `JADETIPI_IT_KAFKA`-gated integration spec is wanted.
- **Q-19-D — Position vocabulary names.** Per-parent-kind names
  (`freezer_slot`, `bin_slot`, `plate_well`, `tube_position`) vs a
  single neutral `slot` key.

### Verification

For this pre-work turn:

- Static review only. The expected diff against `origin/director` is
  exactly two paths:
  - `docs/architecture/clarity-esp-container-mapping.md`
  - `docs/agents/claude-1-next-step.md`
- `git diff --check` was run and reported no whitespace issues.
- No CouchDB, Docker, Gradle, or MongoDB commands were executed
  this turn.

For the implementation turn (only after `READY_FOR_IMPLEMENTATION`):

```sh
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*ClarityEspContainerMappingSpec*'
./gradlew :jade-tipi:test
```

Optional integration coverage (default proposal: skip unless the
director judges unit-test evidence insufficient):

```sh
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ClarityEspContainerMaterializationSpec*'
```

If implementation-iteration setup blocks any of these,
`docker compose -f docker/docker-compose.yml up -d` and
`./gradlew --stop` are the documented setup steps; blockers will be
reported with the exact command and error rather than treated as
product blockers.

### Stay-in-scope check

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.
- `docs/architecture/clarity-esp-container-mapping.md` —
  task-expanded owned path under TASK-019.

Both are inside the union of base owned paths plus the TASK-019
scope expansion in `DIRECTIVES.md`. No paths outside that union were
modified.

STOP.
