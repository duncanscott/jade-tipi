# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-017 — Add local CouchDB replication bootstrap (pre-work, revision 2)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-017`,
status `READY_FOR_PREWORK`) and the `TASK-017` task file's most recent
`LATEST_REPORT:`, the prior pre-work was directionally aligned but
contained six design blockers that must be resolved before
`READY_FOR_IMPLEMENTATION`. This revision is scoped to those blockers
and updates the plan accordingly. All `OWNED_PATHS`, `OUT_OF_SCOPE`,
and `DESIGN_NOTES` constraints from the task file remain authoritative.

The implementation goal is unchanged: extend `docker/docker-compose.yml`
so the local stack can start a single-node CouchDB with persistent
storage, create local databases named exactly `clarity` and
`esp-entity`, and bootstrap resumable replication from
`JADE_TIPI_COUCHDB_CLARITY_URL` and `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`.
Pre-work only is authorized.

### Resolution of director-flagged blockers

#### B1 — Drop compose-side interpolation entirely

The prior plan combined `${JADE_TIPI_COUCHDB_*:?...}` compose-side
interpolation with `env_file: ["../.env"]`. As the director's local
verification confirmed, service `env_file:` values do **not** satisfy
compose-side interpolation; only the shell environment, `--env-file`,
or a `.env` file in the compose project directory do. The
orchestrator materializes `.env` at the worktree root, not at
`docker/.env`, and the documented invocation is plain
`docker compose -f docker/docker-compose.yml ...` with no
`--env-file` flag. Compose-side interpolation would therefore fail
`docker compose -f docker/docker-compose.yml config` in an
orchestrator worktree.

Resolution: **remove all `${VAR:?...}` interpolation for these
variables from the compose file.** Both new services receive the
worktree-root `.env` exclusively via container-side `env_file:
- ../.env`. The compose file becomes static text with no environment
references for the new services, so `docker compose ... config` parses
without any host shell setup. Required-var enforcement moves into the
bootstrap script, where `: "${VAR:?...}"` early-exit checks fail
loudly with the variable name only, no value, when a credential is
missing.

The CouchDB image expects exactly `COUCHDB_USER` and `COUCHDB_PASSWORD`
to enable single-node admin mode (not the `JADE_TIPI_*` names). Since
we cannot rename via interpolation under this resolution, the
`.env` and `.env.example` files declare `COUCHDB_USER` and
`COUCHDB_PASSWORD` directly as additional non-secret variable names
(see B5). The CouchDB container reads them via `env_file: ../.env`
without any compose-side mapping. If the operator has not populated
them, the bootstrap script will detect admin-party mode by attempting
an authenticated `GET /_node/_local/_config/admins` and fail loudly
with a message that names the missing variables — without echoing
their values.

This makes the documented commands work as the directive specifies.
No change to the documented `docker compose -f docker/docker-compose.yml
...` invocation is needed.

#### B2 — Update CouchDB image tag to `couchdb:3.5`

The prior plan pinned `couchdb:3.4`. Director feedback confirms the
current stable line is 3.5.x. The `couchdb` Docker Hub repository
publishes tags `latest`, `3.5.1`, `3.5`, `3`, and `3.4.3`/`3.4`.

Resolution: pin to **`couchdb:3.5`**. Rationale:

- 3.5.x is the current stable line; 3.4.x is the previous line and
  receives only critical fixes.
- Pinning at major.minor (`3.5`) matches existing project conventions
  (`mongo:8.0`, `quay.io/keycloak/keycloak:26.0` are major.minor
  pins; `apache/kafka:4.1.1` is the lone major.minor.patch pin).
- All design surfaces this plan depends on — `_cluster_setup`,
  `_replicator`, structured `auth.basic`, `_active_tasks`,
  `_scheduler/jobs`, `/_up` health probe — are present and stable in
  3.5.x.

If the director instead wants a deterministic patch pin, change to
`couchdb:3.5.1`. Either choice is a one-line change in the compose
file; I will await direction before implementation.

#### B3 — Build replicator JSON with jq, not raw printf

The prior plan built `_replicator` doc bodies via `printf` against a
template containing `${JADE_TIPI_COUCHDB_*}` substitutions. That is
not safe for credentials containing JSON-special characters (`"`,
`\`, control bytes). The CouchDB image's HTTP layer would reject the
malformed JSON or, worse, accept a doc with mis-quoted credentials
that fail authentication silently against the remote source.

Resolution: **build all JSON bodies with `jq -n --arg ... --arg
... '<template>'`.** `jq` produces correctly escaped JSON for any
input string. Switching the bootstrap sidecar's image to
`alpine:3.20` — minimal Alpine — and installing `curl` and `jq` via
`apk add --no-cache --quiet curl jq` in the entrypoint gives both
tools at ~10 MB extra disk per first run. Subsequent runs reuse the
image layer cache.

Concretely, each replicator doc is built as:

```sh
DESIRED_JSON=$(jq -n \
  --arg id "bootstrap-clarity" \
  --arg url "$JADE_TIPI_COUCHDB_CLARITY_URL" \
  --arg user "$JADE_TIPI_COUCHDB_ADMIN_USERNAME" \
  --arg pass "$JADE_TIPI_COUCHDB_ADMIN_PASSWORD" \
  --arg target "clarity" \
  '{
    _id: $id,
    source: { url: $url, auth: { basic: { username: $user, password: $pass } } },
    target: $target,
    continuous: true,
    create_target: false,
    use_checkpoints: true
  }')
```

`jq` also reads existing docs from CouchDB for B4's compare step and
merges `_rev` into a desired doc before re-PUT, all without any
manual string concatenation. The script never sets `-x` and never
echoes `$DESIRED_JSON` to stdout/stderr. `curl --user "$U:$P"`
continues to pass auth via the HTTP header, never as URL userinfo.

#### B4 — Tighten 409 handling to avoid scheduler churn

The prior plan rewrote the `_replicator` document on every 409
conflict (fetch existing `_rev`, merge into desired, PUT). That risks
pushing a new document revision on every `up -d` even when the
replication intent is unchanged, which causes the CouchDB scheduler to
re-evaluate the job and may briefly interrupt an in-progress
continuous replication. That conflicts with the directive's
"must not restart replication from scratch unnecessarily."

Resolution: **rewrite only when meaningful fields differ.** Concretely:

1. PUT desired doc → 201 → done.
2. 409 → GET existing doc body.
3. With `jq`, build a normalized projection of both desired and
   existing on exactly these fields:
   `source.url`, `source.auth.basic.username`,
   `source.auth.basic.password`, `target`, `continuous`,
   `create_target`, `use_checkpoints`.
   (Excluded: `_id`, `_rev`, `_replication_*` runtime fields,
   `owner`, and any other server-managed fields the scheduler sets.)
4. Compare the two normalized projections with `jq -e '. == $other'`
   (no echoing of the projection content; only the boolean exit
   status is observed). If equal → log
   `bootstrap-<id>: already configured (no change)` and continue.
5. If different → merge `_rev` from the existing doc into the
   desired doc and PUT. Log
   `bootstrap-<id>: updated (rev=<short-rev-id>)` — `<short-rev-id>`
   is the safe `_rev` value, which is server-issued, not credential
   material. Source URL and credentials are not logged.

This makes steady-state `up -d` runs read-only against `_replicator`
and leaves the scheduler undisturbed. Credential rotation, URL
change, or option change still triggers a clean update, which is the
intended escape hatch.

#### B5 — Separate local CouchDB admin from remote source credentials

The directive says
`JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`JADE_TIPI_COUCHDB_ADMIN_PASSWORD`
must come from the local environment file. Director Q1 review asked
whether reusing those same credentials as the local CouchDB server
admin is justified. Reusing them broadens where the JGI credential
pair is stored (now in two roles: HTTP basic auth presented to the
remote source URL, AND the admin credential of the local CouchDB
container) and ties an unrelated local admin to a real JGI account.

Resolution: **introduce two new non-secret local-admin variables in
`.env.example` with placeholder default values** and use them as the
local CouchDB server admin only. The remote-source credentials keep
their existing variable names and role.

`.env.example` becomes (added lines marked `+`, existing kept):

```
# Local CouchDB server admin (used by docker compose only).
# These are container-only credentials for the local single-node
# CouchDB. Defaults below are non-secret placeholders suitable for
# loopback-only local development; override in the worktree .env if
# desired.
+ COUCHDB_USER=admin
+ COUCHDB_PASSWORD=admin

# Remote JGI CouchDB sources for replication into local databases.
# Provide real values in the worktree .env (never commit).
JADE_TIPI_COUCHDB_CLARITY_URL=https://pps-couch-prd.jgi.lbl.gov:6984/clarity
JADE_TIPI_COUCHDB_ESP_ENTITY_URL=https://pps-couch-prd.jgi.lbl.gov:6984/esp-entity
JADE_TIPI_COUCHDB_ADMIN_USERNAME=
JADE_TIPI_COUCHDB_ADMIN_PASSWORD=
```

`.env.example` already declares only non-secret items, and the
`COUCHDB_USER=admin`/`COUCHDB_PASSWORD=admin` defaults are
non-secret placeholders the operator overrides for any non-trivial
setup. The local CouchDB binds to `127.0.0.1:5984` only, so the
default placeholder is acceptable for local development; the
loopback bind is the security boundary.

The CouchDB image consumes `COUCHDB_USER`/`COUCHDB_PASSWORD` as the
single-node admin via the standard image entrypoint behavior. The
bootstrap script also consumes the same two variables to authenticate
admin calls to the local node, and consumes
`JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`JADE_TIPI_COUCHDB_ADMIN_PASSWORD`
exclusively for the remote-source `auth.basic` block in replicator
documents. The two roles are now textually separate.

This addresses Q1 in line with the alternative the director offered.
The shared orchestrator overlay
`config/env/project.env.local.example` is outside this task's
`OWNED_PATHS`; flagged below as Q-r2-1 if the orchestrator overlay
should also be updated.

#### B6 — Verification matches Compose one-shot semantics

The prior plan claimed a second `docker compose -f
docker/docker-compose.yml up -d` would rerun `couchdb-init`. With
`restart: "no"` and the container already in exited-success state,
plain `up -d` does **not** recreate exited one-shot services; it
leaves them as-is. This matches `kafka-init` precedent. The previous
verification step contradicted itself.

Resolution: split the resumability check into two separate proofs.

- **Container/volume persistence.** Run `docker compose -f
  docker/docker-compose.yml up -d couchdb` a second time. Expected:
  the existing `couchdb` container reports `running (healthy)`, no
  recreation, the `couchdb_data` and `couchdb_config` volumes are
  reused. After `docker compose -f docker/docker-compose.yml down`
  followed by `up -d couchdb`, the named volumes still hold the
  data; CouchDB on restart reads the persisted `_replicator` docs
  and resumes each replication from the per-target checkpoint
  written by previous runs (no full sequence-0 restart).
- **Bootstrap-script idempotency.** Re-run the script explicitly:
  `docker compose -f docker/docker-compose.yml up -d
  --force-recreate couchdb-init`. Expected: the container is
  recreated, the script reruns, the `_cluster_setup`/system-DB
  creation calls take their already-set-up branches (200 / 412),
  the local DBs already exist (412), and the replicator-doc PUTs
  hit the 409 → "already configured (no change)" branch from B4.
  Exit status 0; no scheduler churn; no credential strings printed.

I will document both forms in the implementation `LATEST_REPORT:`
addendum so future operators understand which command exercises
which property.

### Updated proposed plan (delta from prior pre-work)

Files that will change (smallest set), unchanged from prior pre-work
except as noted by B1–B6:

- `docker/docker-compose.yml` — add `couchdb` service (image
  `couchdb:3.5`, B2; loopback `127.0.0.1:5984`; `env_file: ../.env`,
  no `${...}` interpolation, B1; `couchdb_data` and `couchdb_config`
  named volumes; `/_up` healthcheck; no `depends_on`). Add
  `couchdb-init` sidecar (image `alpine:3.20`, B3; `env_file:
  ../.env`; `depends_on: { couchdb: { condition: service_healthy } }`;
  `restart: "no"`; volume-mounts the bootstrap script read-only;
  entrypoint `apk add` then `exec sh /usr/local/bin/couchdb-bootstrap.sh`).
  Add `couchdb_data` and `couchdb_config` to the top-level `volumes:`
  block.
- `docker/couchdb-bootstrap.sh` — new committed POSIX `sh` script.
  Required-var enforcement via `: "${VAR:?msg}"` for `COUCHDB_USER`,
  `COUCHDB_PASSWORD`, `JADE_TIPI_COUCHDB_ADMIN_USERNAME`,
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD`,
  `JADE_TIPI_COUCHDB_CLARITY_URL`, `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`,
  and `COUCH_URL` (set in compose `environment:` to
  `http://couchdb:5984` — this is a literal, not interpolated).
  System-DB creation via direct `PUT /_users`, `PUT /_replicator`,
  `PUT /_global_changes` (treats 412 as success); replaces the
  prior `_cluster_setup` call which is unnecessary when
  `COUCHDB_USER`/`COUCHDB_PASSWORD` are baked in by the image.
  Local DB creation via `PUT /clarity` and `PUT /esp-entity` (treats
  412 as success). Replicator-doc creation/update per B3+B4.
  `set -eu`, no `set -x`. ≤120 lines including comments.
- `.env.example` — add the two `COUCHDB_USER`/`COUCHDB_PASSWORD`
  lines with placeholder default values per B5, plus the short
  comment block. No change to the four existing variables.
- `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`
  — append a `LATEST_REPORT:` paragraph during implementation
  describing the new services, the script path, the volumes, and the
  developer-visible commands for observing replication progress
  (`/_active_tasks`, `/_scheduler/jobs`, `/_scheduler/docs/_replicator`).
- `docs/agents/claude-1-changes.md` — append a short report after
  acceptance.

No edit to `README.md`, `application.yml`, `build.gradle`, frontend,
or any source file. No change to `mongodb`, `keycloak`, `kafka`, or
`kafka-init` blocks. No new top-level `networks:` block.

### Updated verification proposal

After implementation, with the worktree-root `.env` containing
non-empty values for all six variables
(`COUCHDB_USER`, `COUCHDB_PASSWORD`,
`JADE_TIPI_COUCHDB_CLARITY_URL`, `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`,
`JADE_TIPI_COUCHDB_ADMIN_USERNAME`,
`JADE_TIPI_COUCHDB_ADMIN_PASSWORD`):

1. `docker compose -f docker/docker-compose.yml config` — confirms
   the file parses with **no** dependency on the host shell
   environment (per B1: no compose-side interpolation). Expected:
   full rendered config printed without error.
2. `docker compose -f docker/docker-compose.yml up -d couchdb` —
   starts only the new service. Expected: `couchdb` container reaches
   `(healthy)` within ~30s based on the `/_up` probe.
3. `docker compose -f docker/docker-compose.yml up -d couchdb-init`
   — runs the bootstrap once. Expected: container exits with
   status `0`; logs show
   `bootstrap-clarity: created` (or `: updated`) and
   `bootstrap-esp-entity: created` (or `: updated`); no
   credential strings present.
4. `docker compose -f docker/docker-compose.yml exec couchdb \
   curl -fsS -u "$COUCHDB_USER:$COUCHDB_PASSWORD" \
   http://127.0.0.1:5984/_all_dbs` — operator-side check (vars
   exported in the operator shell from the `.env` they materialized).
   Expected output includes `clarity`, `esp-entity`, `_users`,
   `_replicator`, `_global_changes`.
5. `... /_scheduler/jobs` and `... /_active_tasks` — expected: two
   replication entries (one per DB) with non-zero
   `docs_read`/`changes_pending` once replication has progressed.
   The structured `auth.basic` form means CouchDB does not echo
   credentials in the `source` URL field of these endpoints.
6. **Volume persistence (resumability part 1, per B6).** Run `docker
   compose -f docker/docker-compose.yml up -d couchdb` a second time.
   Expected: existing `couchdb` container still `running (healthy)`,
   no recreation. Then run `down` followed by `up -d couchdb`.
   Expected: container starts fresh against persisted
   `couchdb_data`/`couchdb_config` volumes, `_replicator` docs are
   still present, and replication resumes from per-target
   checkpoints rather than sequence 0 (visible by stable `progress`
   percentage in `/_active_tasks` or low-and-rising
   `changes_pending`).
7. **Bootstrap-script idempotency (resumability part 2, per B6).**
   Run `docker compose -f docker/docker-compose.yml up -d
   --force-recreate couchdb-init`. Expected: container reruns, takes
   the "already created" branches for system DBs (412), local DBs
   (412), and "already configured (no change)" branch for replicator
   docs (B4). Exit status 0; no scheduler churn observable in
   `/_scheduler/jobs` (no `last_updated` timestamp change on either
   replicator doc).
8. **Negative env check** (no real network load). Comment out
   `JADE_TIPI_COUCHDB_ADMIN_USERNAME` in the worktree `.env` and
   re-run `docker compose -f docker/docker-compose.yml up -d
   --force-recreate couchdb-init`. Expected: bootstrap script exits
   non-zero with the named message
   `JADE_TIPI_COUCHDB_ADMIN_USERNAME is required` (no value
   printed). Compose `config` itself still parses (per B1, no
   interpolation). Restore the var afterward.
9. **Remote replication itself is not part of automated
   verification.** Per the directive's
   `OUT_OF_SCOPE`/`VERIFICATION` clauses, no multi-GB pull is
   triggered as part of acceptance verification.

If local Docker is unavailable, report the documented setup command
(start Docker Desktop, then `docker compose -f
docker/docker-compose.yml config`) and the exact blocked command's
error verbatim, instead of treating Docker unavailability as a
product blocker. The unit `:jade-tipi:test` and
`JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` paths are
unchanged and do not require CouchDB; no Gradle verification is
proposed for this task.

### Open questions (revision 2 — narrowed)

- **Q-r2-1 — Orchestrator overlay update.** Resolution B5 adds
  `COUCHDB_USER` and `COUCHDB_PASSWORD` to `.env.example`. The
  shared orchestrator overlay
  `config/env/project.env.local.example` (outside this task's
  `OWNED_PATHS`) currently mirrors the four CouchDB-relevant
  variables. Should the orchestrator overlay also be updated to
  include `COUCHDB_USER`/`COUCHDB_PASSWORD` for ergonomics across
  all developer worktrees? **Recommendation:** yes, but not in this
  task. Either (a) request scope expansion to add the overlay file
  to `TASK-017` `OWNED_PATHS`, or (b) leave it for a follow-up
  orchestrator task. Defaulting to (b) so this task stays
  Docker-stack-only.
- **Q-r2-2 — Image tag granularity.** B2 pins to `couchdb:3.5`
  (major.minor, matches `mongo:8.0` style). If the director instead
  wants `couchdb:3.5.1` (major.minor.patch, matches
  `apache/kafka:4.1.1` style), confirm and I will use that. Both
  are one-line changes.
- **Q-r2-3 — Sidecar image choice.** B3 switches the bootstrap
  sidecar to `alpine:3.20` so we can `apk add curl jq`. An
  alternative is to use the `couchdb:3.5` image itself (already
  pulled, has `bash` and `curl`, but does not ship `jq`) and add
  `jq` via `apt-get install -y jq` in the entrypoint (slower,
  larger). **Recommendation:** keep `alpine:3.20`; it is ~5 MB,
  installs fast, and isolates the init from the database image.
  Flag if the director prefers reusing the `couchdb:3.5` image.
- **Q-r2-4 — Disk-space prefligh** check. Q9 from prior pre-work
  remains unresolved. The bootstrap script does not check available
  disk before starting replication; CouchDB will fail later if
  ~52 GB is unavailable. **Recommendation:** document the
  ~52 GB figure in the task `LATEST_REPORT:` and skip the brittle
  precheck (the Docker volume sits on the host filesystem mounted
  by Docker Desktop, which has its own VM disk on macOS). Flag if
  the director wants the precheck regardless.

All other prior open questions (Q1, Q2, Q3, Q4, Q5, Q6, Q7, Q8 from
revision 1) are resolved by B1–B6 above:

- Q1 → B5 (separate local admin credentials).
- Q2 → B2 (`couchdb:3.5`).
- Q3 → unchanged: keep doc surface in `LATEST_REPORT:`, do not
  edit `README.md`.
- Q4 → B1 (no compose-side interpolation; `env_file: ../.env`
  consumed only as container env).
- Q5 → B6 (do not claim plain `up -d` reruns the sidecar).
- Q6 → B4 (compare-then-rewrite, with no-op on equal projections).
- Q7 → unchanged: a small in-script retry loop (3 attempts, 5s)
  guards the system-DB-creation calls, which under B1's
  restructuring replaces the prior `_cluster_setup` call.
- Q8 → unchanged: keep `127.0.0.1:5984:5984` loopback bind.

### Blockers

None at this point. Implementation is ready once the director moves
`TASK-017` to `READY_FOR_IMPLEMENTATION`. Implementation will stay
inside the granted `OWNED_PATHS` (`docker/`, `.env.example`, the
task file) plus the always-owned developer report file
`docs/agents/claude-1-changes.md`.

STOP.
