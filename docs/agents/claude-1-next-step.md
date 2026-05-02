# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-019 — Prototype Clarity/ESP container materialization (pre-work, revision 6)

### Directive summary

`DIRECTIVES.md` signal is `REQUEST_NEXT_STEP`. `TASK-019` remains at
`READY_FOR_PREWORK`. The director's most recent review (commit
`8d601fd`, recorded in `DIRECTIVES.md` and in the `TASK-019`
`LATEST_REPORT`) accepted the in-scope mapping-doc design fixes from
revision 5:

- `typ~contents` declares `assignable_properties: ["position"]` in
  both the materialized root and the `typ + create` source message.
- D5 mints a transaction-local `typ~contents` id and uses it
  consistently in both `lnk` examples.
- `ContentsLinkReadService` resolves all `typ` rows matching
  `properties.kind = "link_type"` and `properties.name = "contents"`
  and queries `lnk.type_id` with `$in`, so the transaction-local id
  is compatible with the accepted contents read path.

The merge that delivered those revision-5 edits was nonetheless
rejected for **scope/protocol** because it also modified
`docker/couchdb-bootstrap.sh` (setting `couchdb.max_document_size`)
— a path outside claude-1's base owned paths and outside the
`TASK-019` scope expansion in `DIRECTIVES.md`. The director's
instruction for the next claude-1 turn:

> remove the out-of-scope `docker/couchdb-bootstrap.sh` change from
> the TASK-019 merge and resubmit the in-scope mapping/report edits.

No further mapping-doc blocker is currently identified. No code,
tests, Gradle work, materializer changes, CouchDB writes,
Docker/bootstrap changes, or implementation authorization is active.

### Branch-state observation (the directive has already been satisfied by other means)

State of the `claude-1` worktree at the start of this turn:

```
git rev-parse HEAD == git rev-parse origin/director  # equal
git diff --stat origin/director..HEAD                # empty
```

The recent commit chain on `claude-1` / `origin/director` is:

```
8d601fd Record director agent turn          (director, revision-5 review)
8b54a0f Set CouchDB max document size during bootstrap   (direct human commit)
deec62b Record claude-1 agent turn          (mapping rev 5: assignable_properties + tx-local typ id)
7e73e79 Record director agent turn          (revision-4 review, flagged D5 + assignable_properties)
0915401 Record claude-1 agent turn          (mapping rev 4: created mapping doc + initial design)
```

Two relevant facts follow:

1. **The in-scope mapping/report edits are already on `director`.**
   Commits `0915401` and `deec62b` ("Record claude-1 agent turn")
   touched only `docs/agents/claude-1-next-step.md` and
   `docs/architecture/clarity-esp-container-mapping.md`, both inside
   claude-1's base owned paths or the `TASK-019` scope expansion.
   They are merged into `director` and `claude-1` and contain the
   accepted design fixes (`assignable_properties: ["position"]`,
   transaction-local `typ~contents` id).

2. **The `docker/couchdb-bootstrap.sh` change is on the branch via a
   separate, direct human commit, not a claude-1 merge.** Commit
   `8b54a0f` ("Set CouchDB max document size during bootstrap",
   authored Fri May 1 22:19 PT by the human) modified only
   `docker/couchdb-bootstrap.sh`. It is not a "Record claude-1 agent
   turn" commit; it is independent of any TASK-019 merge from
   claude-1. The director's instruction to remove the docker change
   "from the TASK-019 merge" is therefore already satisfied by
   construction: there is no pending TASK-019 merge from claude-1
   that bundles docker, and the docker edit currently lives on
   `director` only via the human's own authored commit.

This pre-work turn cannot meaningfully "remove" `8b54a0f` from
claude-1's branch without rewriting or reverting a commit authored
directly by the human, which would be destructive and outside both
base owned paths and `TASK-019` scope. Any such revert would need
explicit director authorization and an explicit scope expansion that
covers `docker/couchdb-bootstrap.sh`.

### Proposed plan for this turn

1. Produce no edits beyond this file. The in-scope mapping-doc
   revision-5 fixes are already accepted and merged; the
   `docker/couchdb-bootstrap.sh` edit is detached from any pending
   claude-1 TASK-019 merge.
2. Surface the situation to the director with a concrete open
   question (Q-19-L below) so the director can either:
   - confirm the docker concern is resolved by the separate human
     commit and advance `TASK-019` to `READY_FOR_IMPLEMENTATION` (or
     to `ACCEPTED` if the director judges the pre-work proposal
     itself as the deliverable for this design task), or
   - authorize a follow-up turn that explicitly reverts
     `docker/couchdb-bootstrap.sh` under a separate Docker/bootstrap
     task with corresponding scope expansion, or
   - issue a different mapping-doc edit if a new blocker is
     identified.
3. Carry forward all other open questions from revision 5
   (Q-19-A through Q-19-K) so they remain visible.

### Files this turn will touch

- `docs/agents/claude-1-next-step.md` — base owned path; this file.

### Files intentionally not touched this turn

- `docs/architecture/clarity-esp-container-mapping.md` — already
  carries the accepted revision-5 fixes; no further design blocker
  identified.
- `docs/architecture/jade-tipi-object-model-design-brief.md` — human
  input; read-only for this task.
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
  — task-expanded but director-owned; no edit warranted.
- `docs/agents/claude-1.md`, `docs/agents/claude-1-changes.md` —
  base owned; no implementation outcome to record.
- `docker/couchdb-bootstrap.sh` — outside claude-1's base owned
  paths and outside the `TASK-019` scope expansion; touching it
  (including a revert) requires explicit director authorization.
- `jade-tipi/src/main/groovy/...service/`,
  `jade-tipi/src/test/groovy/...service/`,
  `jade-tipi/src/integrationTest/groovy/...containers/`,
  `jade-tipi/src/integrationTest/resources/` — task-expanded for a
  later implementation turn only; pre-work is not implementation.

### Decisions in the accepted mapping doc (D1–D7), unchanged

- D1 — Prototype materializes only `loc`, `typ`, and `lnk` roots; no
  `ent`. The accepted materializer supports `loc + create`,
  `typ + create` for `data.kind == "link_type"`, and `lnk + create`,
  matching this prototype.
- D2 — Plate wells stay as `lnk.properties.position` (option A);
  child-`loc` and hybrid alternatives documented with tradeoffs.
- D3 — Per-parent-kind position vocabulary (`freezer_slot`,
  `bin_slot`, `plate_well`, `tube_position`).
- D4 — Identifier convention embeds source key in short-name plus
  `properties.source_id` and `properties.source_system`.
- D5 — Transaction-local `typ~contents` id
  (`...~018fd849-c0c0-7000-8a01-c1a141e5e500~typ~contents`) so the
  TASK-019 transaction is self-contained; coexistence with any
  canonical `typ~contents` is safe because `ContentsLinkReadService`
  resolves by `kind`/`name`, not by id.
- D6 — Parentage is single-sourced in `lnk + contents`. No
  `parent_location_id` on `loc`.
- D7 — Directional labels live on the `typ~contents` declaration
  only.
- Type-definition shape — `typ~contents` root declares
  `assignable_properties: ["position"]` (flat list of names) plus
  link-type metadata. No required/optional, no defaults, no
  per-name schema.

### Blockers and open questions

**New this turn:**

- **Q-19-L — Disposition of `8b54a0f` (`docker/couchdb-bootstrap.sh`).**
  The director's revision-5 review instructed claude-1 to remove the
  docker change "from the TASK-019 merge". That edit is no longer
  bundled with any pending claude-1 merge — it lives on `director`
  via a separate human-authored commit. Two paths forward; please
  confirm which the director wants:
  - **L-1 (proposed default).** Treat the docker concern as resolved
    by `8b54a0f` and advance `TASK-019`. Claude-1 makes no further
    edits to `docker/couchdb-bootstrap.sh`.
  - **L-2.** Open a separate bounded Docker/bootstrap task with
    explicit ownership over `docker/couchdb-bootstrap.sh`, and
    decide there whether the `couchdb.max_document_size` setting
    stays, is parameterized, or is reverted. `TASK-019` does not
    block on this.
  - **L-3.** Explicitly authorize a revert of `8b54a0f` under
    claude-1 with a one-turn scope expansion to
    `docker/couchdb-bootstrap.sh`. Requires director confirmation
    that overwriting a direct human commit is intended.

**Carried forward (still open from revision 5):**

- Q-19-A — Prototype acceptance: loc/lnk-only with the four sampled
  containers (Clarity tube + ESP freezer/bin/plate), or drop the
  Clarity tube for a single ESP chain.
- Q-19-C — Integration-spec inclusion. Default proposal: unit tests
  only; confirm whether a `JADETIPI_IT_KAFKA`-gated integration spec
  is wanted.
- Q-19-D — Position vocabulary names: per-parent-kind
  (`freezer_slot`, `bin_slot`, `plate_well`, `tube_position`) vs a
  single neutral `slot` key.
- Q-19-E — Wells recommendation: ship A
  (`lnk.properties.position`); document C (child-`loc` wells) as
  forward path.
- Q-19-F — Type-definition shape: `typ~contents` declares
  `assignable_properties: ["position"]` plus link-type metadata
  only.
- Q-19-G — Parentage exclusivity: D6 makes parentage exclusive to
  `lnk`; alternatives documented and rejected.
- Q-19-H — Brief-vs-mapping authority: brief wins; mapping intro
  states this.
- Q-19-I — Scope of doc edits: append/narrow in-place only.
- Q-19-J — Field name `assignable_properties` vs alternatives
  (`instance_properties`, `assignable`, `properties_declared`).
- Q-19-K — Transaction-local typ id format
  (`jade-tipi-org~dev~<txn-uuid>~typ~contents`) reusing the same
  `txn-uuid` as the four `loc` and two `lnk` roots.

### Stay-in-scope check

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths modified. No code, tests, Gradle, Docker, MongoDB,
CouchDB, Kafka, frontend, security, DTO/schema, or HTTP-endpoint
changes were made or proposed for this turn.

### Verification

For this pre-work turn:

- Static review only.
- Branch-state checks performed:
  - `git rev-parse HEAD == git rev-parse origin/director` → equal.
  - `git diff --stat origin/director..HEAD` → empty.
  - `git log --oneline -10` confirms the recent commit ordering
    cited above.
  - `git show --stat 0915401`, `git show --stat deec62b` confirm
    those "Record claude-1 agent turn" commits touched only
    `docs/agents/claude-1-next-step.md` and
    `docs/architecture/clarity-esp-container-mapping.md` — never
    `docker/couchdb-bootstrap.sh`.
  - `git show --stat 8b54a0f` confirms the docker edit is a
    standalone human commit, not part of any "Record claude-1 agent
    turn" merge.
- No CouchDB, Docker, Gradle, MongoDB, Kafka, or HTTP commands were
  executed this turn.

For a later implementation turn (only after
`READY_FOR_IMPLEMENTATION` or `PROCEED_TO_IMPLEMENTATION`):

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

Documented setup (only if implementation iteration is blocked by
local stack state):

```sh
docker compose -f docker/docker-compose.yml up -d
./gradlew --stop
```

If local CouchDB read inspection is requested in a later turn, the
documented setup remains:

```sh
docker compose -f docker/docker-compose.yml up -d couchdb
docker compose -f docker/docker-compose.yml up -d couchdb-init
```

Local CouchDB credentials, if missing, belong in the orchestrator
overlay
`/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
as `COUCHDB_USER` / `COUCHDB_PASSWORD`. These are setup steps, not
product blockers.

STOP.
