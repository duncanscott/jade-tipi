# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 2)

### Directive summary

`DIRECTIVES.md` signals `REQUEST_NEXT_STEP` and lists `TASK-019` as
`READY_FOR_PREWORK`. The director's prior pre-work review on
2026-05-02 kept the task at `READY_FOR_PREWORK` because the prior
plan was hypothetical and did not actually sample local CouchDB. The
review explicitly asked the next pre-work turn to run the documented
read-only CouchDB inspection and write
`docs/architecture/clarity-esp-container-mapping.md`, and to resolve
the `ent` materializer gap before implementation by either choosing
a `loc + lnk` only prototype or stopping to request a separate
materializer-expansion task.

This revision does both: it samples real records from the locally
replicated `clarity` and `esp-entity` databases, picks representative
containers, resolves the `ent` issue by **staying within `loc + lnk`
only**, and writes a complete mapping design at
`docs/architecture/clarity-esp-container-mapping.md` with redacted
source samples, materialized-root JSON, source Kafka transaction
messages, and operator-runnable verification commands.

### What changed in this turn

- **CouchDB sampling actually executed.** Local CouchDB at
  `http://127.0.0.1:5984` was reachable using
  `COUCHDB_USER`/`COUCHDB_PASSWORD` from the materialized worktree
  `.env`. `clarity` (2,608,824 docs, ~40.3 GB active) and
  `esp-entity` (1,510,835 docs, ~10.1 GB active) are populated. All
  HTTP calls were `GET` and `POST /_find` (read-only); no write verbs
  were used. No remote CouchDB credentials were used.
- **Representative records identified.** A Clarity tube
  (`containers_27-10000`, LIMSID `27-10000`, name `27-170230`, type
  `Tube`, no physical location) and an ESP three-level
  Freezer/Bin/Plate chain (`Illumina 130-32` →
  `PP050` (Bin 9x3, slot `2`) →
  `27-474501` (96W Plate, well `A1`)) — all `class_name=Container`
  in ESP. Plates contain `Illumina Library` / `Nucleic Acid`
  entities at well-keyed positions; those are intentionally excluded
  from the prototype as `ent` candidates.
- **`ent` decision resolved without expansion.** Every materialized
  root in the prototype is a `loc` or `lnk`, plus a reused
  `typ link_type contents` declaration. The accepted
  `CommittedTransactionMaterializer` already supports all three; no
  new collection-action support is needed. No separate
  materializer-expansion task is requested.
- **Plate-well representation resolved.** ESP itself encodes well
  position as a **map key** in the parent's `contents` object, with
  no separate well-document identity. The prototype follows the
  canonical `12-create-contents-link-plate-sample.json` precedent and
  models position as `lnk.properties.position`. Wells are not
  promoted to `loc` roots.
- **Design doc written.** `docs/architecture/clarity-esp-container-mapping.md`
  now contains anchored constraints, redacted source skeletons,
  per-example mapping tables, full materialized root JSON for all
  seven proposed roots (4 `loc`, 1 `typ`, 2 `lnk`), the eight
  source Kafka transaction messages, expected Mongo collection
  contents, known ambiguities, and reproducible read-only `curl`
  commands.

### Proposed plan

#### Pre-work deliverable (this turn)

Already in this commit:

- `docs/architecture/clarity-esp-container-mapping.md` — design doc
  with sampled evidence, mapping decisions, materialized-root
  examples, source messages, expected collections, ambiguities, and
  read-only verification commands.
- `docs/agents/claude-1-next-step.md` — this revision-2 pre-work
  response.

No production source, schema, or canonical example was edited; no
CouchDB writes; no remote-CouchDB reads; no Spring CouchDB
initialization; no Docker/Gradle/security/frontend changes.

#### Implementation deliverable (only after `READY_FOR_IMPLEMENTATION`)

The smallest test-only artifact that proves the mapping survives the
existing materializer:

- One Spock unit test under
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`
  (proposed name `ClarityEspContainerMappingSpec.groovy`) that
  builds the eight transaction messages from the design doc,
  constructs a `CommittedTransactionSnapshot`, invokes
  `CommittedTransactionMaterializer.materialize(snapshot)`, and
  asserts the seven materialized roots match the design-doc JSON
  exactly modulo `_head.provenance.materialized_at`.

Default proposal: **no integration spec** in this prototype. The
materializer already has integration coverage; the prototype's only
novelty is the example documents themselves. If the director wants
real-Mongo coverage of the prototype as well, a single opt-in
integration spec under
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
gated by `JADETIPI_IT_KAFKA` is the proposed shape; otherwise the
`integrationTest` directories are not touched.

No edits to `CommittedTransactionMaterializer`,
`ContentsLinkReadService`, the Kafka listener, DTOs, schemas,
canonical-example messages, Docker, Gradle, security, or frontend.
The prototype's purpose is to *use* the accepted machinery without
changing it.

### Source sampling — what was run this turn

All commands ran from the worktree root and used local credentials
only:

```sh
set -a; . ./.env; set +a
AUTH="$COUCHDB_USER:$COUCHDB_PASSWORD"
BASE=http://127.0.0.1:5984

# Sanity / sizes
curl -fsS -u "$AUTH" "$BASE/_all_dbs"
curl -fsS -u "$AUTH" "$BASE/clarity"
curl -fsS -u "$AUTH" "$BASE/esp-entity"

# Clarity ID prefix discovery and tube fetch
curl -fsS -u "$AUTH" "$BASE/clarity/_all_docs?limit=12&include_docs=true"
curl -fsS -u "$AUTH" "$BASE/clarity/_all_docs?startkey=%22containers%22&endkey=%22containers_zzz%22&limit=10"
curl -fsS -u "$AUTH" "$BASE/clarity/containers_27-10000"

# ESP container/freezer/bin/plate chain discovery
curl -fsS -u "$AUTH" "$BASE/esp-entity/_all_docs?limit=12&include_docs=true"
curl -fsS -u "$AUTH" "$BASE/esp-entity/_find" \
  -H 'content-type: application/json' \
  -d '{"selector":{"class_name":"Container"},"fields":["_id","name","type_name","class_name","barcode","numeric_id","container","contents","parents","children"],"limit":5}'
curl -fsS -u "$AUTH" "$BASE/esp-entity/019a420c-728d-7f4c-a817-cd8ba13a1e36"
curl -fsS -u "$AUTH" "$BASE/esp-entity/_find" \
  -H 'content-type: application/json' \
  -d '{"selector":{"class_name":"Container","type_name":"96W Plate"},"fields":["_id","name","type_name","class_name","barcode","container","contents"],"limit":2}'
```

No request body contained any write verb. Credentials were passed only
via `-u "$AUTH"`; values are never echoed into the design doc, this
file, the developer report, or the progress mailbox.

### Decisions resolved (carried into the design doc)

- **D1 — Stay at `loc` + `lnk` for the prototype, no `ent`.**
  Backed by source evidence: the chosen samples are all
  `class_name=Container` in ESP and `containers_*` in Clarity, so the
  whole prototype fits the existing materializer with no expansion.
- **D2 — Plate wells as `lnk.properties.position`.** ESP encodes
  positions as map keys in the parent's `contents` object; wells have
  no independent ESP-document identity. The prototype mirrors the
  canonical example.
- **D3 — Position vocabulary** per parent kind: `freezer_slot`,
  `bin_slot`, `plate_well`, `tube_position`. Casing matches the
  canonical example and Clarity's literal `1:1` placement value.
- **D4 — Identifier convention** embeds the source key in the
  short-name segment plus `properties.source_id` and
  `properties.source_system` for queryability.
- **D5 — Reuse the canonical `contents` `typ` id**
  (`...~typ~contents` from `11-create-contents-type.json`). The
  `typ + create` message is included in the prototype transaction so
  the test is self-contained; the materializer's idempotent-duplicate
  path covers a re-run.

### Blockers

None. Local CouchDB was reachable, both replicated databases are
populated, and the design doc is self-contained.

If a future re-run finds CouchDB stopped, the documented setup
commands are:

```sh
docker compose -f docker/docker-compose.yml up -d couchdb
docker compose -f docker/docker-compose.yml up -d couchdb-init
```

If the local `.env` lacks `COUCHDB_USER`/`COUCHDB_PASSWORD`, add them
to the orchestrator overlay
`/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
and re-materialize the worktree.

### Open questions for director review

- **Q-19-A — Prototype acceptance.** Does the loc/lnk-only mapping
  with the four sampled containers (Clarity tube + ESP freezer/bin/
  plate) match what the director wants from "a tiny set of real
  LIMS container records"? If a single chain is preferred, the
  Clarity tube can be dropped without loss to the freezer/bin/plate
  evidence.
- **Q-19-B — Identifier convention.** Embed the source key in the
  short-name segment (current proposal, more legible) versus mint
  fresh UUIDv7 short-names with a `properties.source_id` field
  (more uniform, less legible). Either is local to the prototype's
  example messages and changes nothing in the materializer.
- **Q-19-C — Integration spec inclusion.** Default proposal is
  unit-test-only because the materializer's integration coverage
  already exists. Confirm whether to add one
  `JADETIPI_IT_KAFKA`-gated integration spec under
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
  for the prototype too.
- **Q-19-D — Position vocabulary names.** `freezer_slot`,
  `bin_slot`, `plate_well`, `tube_position` chosen for clarity. If
  the director prefers a single neutral key (e.g. `slot` for all),
  the prototype switches with zero source code change.

### Verification proposal

For this pre-work turn:

- Static review only. `git diff --check` and
  `git diff origin/director..HEAD --stat` should show only
  `docs/architecture/clarity-esp-container-mapping.md` and
  `docs/agents/claude-1-next-step.md` (plus a later
  `docs/agents/claude-1-changes.md` after director acceptance).
- No Gradle, Docker, or MongoDB commands were run.

For the implementation turn (only after `READY_FOR_IMPLEMENTATION`):

```sh
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*ClarityEspContainerMappingSpec*'
./gradlew :jade-tipi:test
```

Optional, only if Q-19-C flips:

```sh
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
  --tests '*ClarityEspContainerMaterializationSpec*'
```

If implementation-iteration setup blocks any of these,
`docker compose -f docker/docker-compose.yml up -d` and
`./gradlew --stop` are the documented setup steps; blockers are
reported with the exact command and error rather than treated as
product blockers.

### Stay-in-scope check

All edits in this turn sit inside this task's expanded `OWNED_PATHS`
plus claude-1's base owned paths:

- `docs/architecture/clarity-esp-container-mapping.md` — task-expanded
  path; created in this commit.
- `docs/agents/claude-1-next-step.md` — base owned path; this file.
- `docs/agents/claude-1.md`, `docs/agents/claude-1-changes.md` —
  base owned, not touched in this turn.
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
  — task-expanded but not touched in this pre-work turn.
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
