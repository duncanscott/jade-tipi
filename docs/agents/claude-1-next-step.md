# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 5)

### Directive summary

`DIRECTIVES.md` signals `REQUEST_NEXT_STEP`. `TASK-019` is at
`READY_FOR_PREWORK`. The director's 2026-05-02 review of the
revision-4 mapping doc accepted scope and base structure (D1–D7,
wells alternatives, parentage tradeoffs, design-brief alignment) but
flagged two remaining acceptance blockers that this turn must fix
directly in `docs/architecture/clarity-esp-container-mapping.md`:

1. The doc's "Type definition shape" prose claims the
   `typ~contents` declaration lists assignable instance property
   `position`, but neither the proposed materialized `typ` root nor
   the proposed `typ + create` transaction message carried any field
   that actually declared `position` assignable. Add a simple
   assignable-property declaration to both, with no
   required/optional, defaults, or per-property schema complexity.
2. D5's idempotency claim — "reuse the canonical `typ~contents` id;
   the materializer's idempotent-duplicate path covers re-runs" — is
   true only for an exact replay of the same payload. A pre-existing
   canonical type with different provenance would be a conflicting
   duplicate. The director's preferred correction is to mint a
   transaction-local `typ~contents` id for the self-contained
   `TASK-019` transaction and use it consistently in both `lnk`
   examples.

No implementation, no code, no tests, no Kafka, no Docker, no Gradle
work this turn.

### What this turn produced

This turn updates two files, both within owned-or-task-expanded paths:

1. `docs/architecture/clarity-esp-container-mapping.md` (task-expanded
   path under TASK-019). Six narrow, in-place edits address both
   blockers without touching unrelated decisions:

   - **D5 rewritten** — "Reuse the canonical `contents` `typ` id"
     becomes **"Mint a transaction-local `typ~contents` id"**. The
     prototype now uses
     `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~typ~contents`,
     embedding the same prototype `txn-uuid` as the four `loc` and
     two `lnk` roots. The transaction is therefore self-contained:
     its own `typ + create` message creates the link type that its
     two `lnk + create` messages reference, with no dependency on a
     pre-existing `typ~contents` row. Coexistence with the canonical
     `typ~contents` is documented as safe because
     `ContentsLinkReadService` resolves by `kind`/`name`, not id.
     The earlier idempotency claim is explicitly retracted.

   - **Materialized `typ` root updated** — id changed to the
     transaction-local id; section heading updated to
     "Typ — `contents` link type (transaction-local id)"; new field
     `assignable_properties: ["position"]` added to `properties`; a
     short paragraph beneath the JSON block explains that the field
     is a flat list of names with no schema complexity.

   - **Source `typ + create` message updated** — id and
     `assignable_properties: ["position"]` field updated to match
     the materialized root; section heading updated to
     "Typ — `contents` link type (transaction-local declaration)".

   - **Both `lnk` materialized roots and both `lnk + create`
     messages** — `type_id` references switched to the new
     transaction-local id (4 occurrences total). Verified zero
     remaining references to the old canonical id in the file.

   - **"Type definition shape" subsection rewritten** — prose now
     names the actual field (`assignable_properties: ["position"]`)
     instead of describing it abstractly. Explicitly states the
     field is "names only — no per-name shape (no
     `{ name, type, required, default }` objects)" so a reader
     cannot misread it as a richer-schema slot. Future-extension and
     future-`ent`/`loc`-type-roots paragraphs preserved with the
     same field name.

   - **Design-brief alignment table row 3** — "Decision in this doc"
     cell narrowed from "D5, "Type definition shape" subsection" to
     just "Type definition shape" subsection (D5 is now about id
     scope, not type shape); summary cell updated to call out
     `assignable_properties: ["position"]` explicitly.

   No edits to: D1, D2, D3, D4, D6, D7 prose; the sampled-evidence
   section; the four `loc` materialized roots; the four `loc + create`
   messages; the open/commit messages; expected Mongo collections;
   read-side verification; known ambiguities A1–A6; verification
   commands; alternatives & tradeoffs sections; intro; vocabulary
   paragraph.

2. `docs/agents/claude-1-next-step.md` (base owned path) — this
   pre-work response.

### Files intentionally not touched this turn

- `docs/architecture/jade-tipi-object-model-design-brief.md` — the
  brief is human input and is read-only for this task.
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
  — task-expanded but director-owned; not touched in pre-work.
- `docs/agents/claude-1.md`, `docs/agents/claude-1-changes.md` —
  base owned, not touched (no implementation outcome to record).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`,
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`,
  `jade-tipi/src/integrationTest/resources/` — task-expanded for
  implementation; not touched in pre-work.

No edits to `CommittedTransactionMaterializer`,
`ContentsLinkReadService`, the Kafka listener, DTOs, schemas,
canonical-example messages, Docker, Gradle, security, or frontend.

### Decisions in the revised mapping doc (D1–D7)

- D1 — Prototype materializes only `loc`, `typ`, and `lnk` roots; no
  `ent`. Unchanged.
- D2 — Plate wells stay as `lnk.properties.position`. Unchanged.
- D3 — Per-parent-kind position vocabulary
  (`freezer_slot`, `bin_slot`, `plate_well`, `tube_position`).
  Unchanged.
- D4 — Identifier convention embeds source key in short-name plus
  `properties.source_id` and `properties.source_system`. Unchanged.
- **D5 (rewritten)** — Mint a transaction-local `typ~contents` id
  (`...~018fd849-c0c0-7000-8a01-c1a141e5e500~typ~contents`) so the
  TASK-019 transaction is self-contained and does not collide with a
  canonical `typ~contents`. Read service resolves by `kind`/`name`,
  so coexistence is safe.
- D6 — Parentage is single-sourced in `lnk + contents`. No
  `parent_location_id` on `loc`. Unchanged.
- D7 — Directional labels live on the `typ~contents` declaration
  only. Unchanged.

Plus the "Type definition shape" subsection: the `typ~contents` root
declares `assignable_properties: ["position"]` (flat list of names)
plus link-type metadata. No required/optional, no defaults, no
per-name schema.

### Blockers

None. The mapping doc is in scope (task-expanded path); the brief is
in scope (read-only). The two director-flagged blockers from
revision 4 are addressed directly in the mapping doc this turn. No
sampling, credentials, or remote reads were required.

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

Carrying revision-4 questions; all answered per the director's
defaults. Listed so the director can override quickly if any default
was unintended.

- **Q-19-J — Field name `assignable_properties`.** Revision 5 picks
  `assignable_properties` as the field that lists assignable
  property names on a `typ` root. Alternatives the director might
  prefer: `instance_properties`, `assignable`, `properties_declared`,
  or a different shape entirely. The chosen name is descriptive and
  avoids overloading the existing `properties` keyword. Confirm or
  override.
- **Q-19-K — Transaction-local typ id format.** Revision 5 mints
  `jade-tipi-org~dev~<txn-uuid>~typ~contents` using the same
  `txn-uuid` as the four `loc` and two `lnk` roots. This matches the
  D4 identifier convention without modification. Confirm or override.
- **Q-19-E — Wells recommendation.** Wells stay as
  `lnk.properties.position` (option A); B and C documented with
  tradeoffs. Recommendation: ship A, document C as forward path.
- **Q-19-F — Type-definition shape.** `typ~contents` declares only
  assignable property names plus link-type metadata. No
  required/optional, no defaults. Now expressed as the concrete
  field `assignable_properties: ["position"]`.
- **Q-19-G — Parentage exclusivity.** D6 makes parentage exclusive
  to `lnk`. Both alternatives documented and rejected.
- **Q-19-H — Brief-vs-mapping authority.** Intro explicitly states
  the brief wins. Mapping doc has been narrowed in place to match.
- **Q-19-I — Scope of doc edits.** Append-style and narrow in-place;
  no decision other than D5 is rewritten this turn.
- **Q-19-A — Prototype acceptance.** Loc/lnk-only with the four
  sampled containers (Clarity tube + ESP freezer/bin/plate), or
  drop the Clarity tube for a single ESP chain.
- **Q-19-C — Integration spec inclusion.** Default proposal: unit
  tests only; confirm whether a `JADETIPI_IT_KAFKA`-gated
  integration spec is wanted.
- **Q-19-D — Position vocabulary names.** Per-parent-kind names
  (`freezer_slot`, `bin_slot`, `plate_well`, `tube_position`) vs a
  single neutral `slot` key.

### Verification

For this pre-work turn:

- Static review only. The expected diff against `origin/director` is
  exactly two paths:
  - `docs/architecture/clarity-esp-container-mapping.md`
  - `docs/agents/claude-1-next-step.md`
- After all edits, a grep for the prior canonical typ id
  `018fd849-2a49-7999-8a09-aaaaaaaaaaab` against the mapping doc
  returned zero hits, confirming no stale id references remain.
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
