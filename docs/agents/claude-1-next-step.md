# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-017 — Add local CouchDB replication bootstrap (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-017`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`,
extend `docker/docker-compose.yml` so the local development stack can
start a single-node CouchDB instance with persistent storage, create
local databases named exactly `clarity` and `esp-entity`, and
bootstrap resumable replication from the remote JGI CouchDB URLs in
`JADE_TIPI_COUCHDB_CLARITY_URL` and `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`
into those local databases. Remote credentials come from the
orchestrator-materialized worktree `.env` file via
`JADE_TIPI_COUCHDB_ADMIN_USERNAME` and `JADE_TIPI_COUCHDB_ADMIN_PASSWORD`
and must not be committed to the repository, written into images, or
printed by the bootstrap.

The directive explicitly scopes this task to Docker/local environment
bootstrap. Pre-work only is authorized — no implementation until the
director moves `TASK-017` to `READY_FOR_IMPLEMENTATION`.

Scope expansion granted by `DIRECTIVES.md` for `TASK-017`:

- `docker/`
- `.env.example`
- `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`

Plus the developer-owned report file `docs/agents/claude-1-changes.md`
and this pre-work file (the always-owned developer paths).

Out of scope per `TASK-017` `OUT_OF_SCOPE` and `DIRECTIVES.md`:

- Copying remote data during the task implementation/tests (the
  human will run the local stack and approve the multi-GB pull
  separately).
- Writing to the remote CouchDB databases.
- Any application code that depends on the replicated CouchDBs
  (no Spring Boot/Kafka/listener/DTO/materializer/contents read
  changes).
- Any change to MongoDB, Kafka, Keycloak, Spring Boot, frontend, or
  Gradle/build wiring.
- Making the unit `:jade-tipi:test` or
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` paths require
  remote CouchDB availability.

### Source inspection (current accepted state)

- **`docker/docker-compose.yml`** currently defines four services:
  - `mongodb` (`mongo:8.0`) on `127.0.0.1:27017`, named volume
    `mongodb_data` mounted at `/data/db`.
  - `keycloak` (`quay.io/keycloak/keycloak:26.0`) on
    `127.0.0.1:8484`, with a TCP-based `/health/ready` healthcheck on
    container port 9000.
  - `kafka` (`apache/kafka:4.1.1`) on `127.0.0.1:9092` (PLAINTEXT
    host) and internal `kafka:9093`, KRaft single-node, named volume
    `kafka_data` mounted at `/var/lib/kafka/data`,
    `depends_on.keycloak: service_healthy`, healthcheck via
    `kafka-broker-api-versions.sh`.
  - `kafka-init` (one-shot, `restart: "no"`) creates the global
    `jdtp_cli_kli` topic if absent, via `entrypoint: ["/bin/bash",
    "-c"]` + a heredoc-style `command:`. This is the existing precedent
    for an idempotent bootstrap sidecar in this stack.
  - Volumes section declares `mongodb_data` and `kafka_data` as named
    volumes (no driver, default local).
  - No `version:` key (Compose v2 implicit), no top-level `networks:`
    block, no `env_file:` on any service. Compose's default
    behavior reads variables from a `.env` file in the directory that
    holds `docker-compose.yml` (here: `docker/`), but the orchestrator
    materializes the worktree's `.env` at the worktree root, **not**
    inside `docker/`. Variable interpolation in compose only finds the
    project root `.env` automatically.
- **`.env.example`** at the worktree root currently lists exactly the
  four CouchDB-relevant variables — no others:
  ```
  JADE_TIPI_COUCHDB_CLARITY_URL=https://pps-couch-prd.jgi.lbl.gov:6984/clarity
  JADE_TIPI_COUCHDB_ESP_ENTITY_URL=https://pps-couch-prd.jgi.lbl.gov:6984/esp-entity
  JADE_TIPI_COUCHDB_ADMIN_USERNAME=
  JADE_TIPI_COUCHDB_ADMIN_PASSWORD=
  ```
  The remote URLs already include the source database name as the
  path component (`/clarity`, `/esp-entity`), which is the URL form
  the CouchDB `_replicator` `source` field expects.
- **`config/env/project.env.local.example`** mirrors the four
  variables above; the orchestrator already ships this overlay
  (confirmed against `config/orchestrator.local.json` `env.shared`
  pointing at `config/env/project.env.local` and `materializeAs:
  ".env"` placing the file at the worktree root). The orchestrator is
  the source of truth for materializing `.env` into worktrees;
  `TASK-017` does not need to change orchestrator wiring.
- **Worktree `.gitignore`** ignores `.env`, `.env.local`,
  `.env.development.local`, `.env.test.local`, and
  `.env.production.local` (lines 137–141). The materialized `.env` is
  therefore not at risk of accidental commit, but a `docker/.env`
  symlink/copy would NOT be covered by this ignore unless the
  ignore is broadened. Recommendation below avoids creating a
  `docker/.env` for that reason.
- **Docker variable interpolation behavior.** `docker compose -f
  docker/docker-compose.yml ...` resolves variable references in the
  compose file by looking, in order, at:
  1. The shell environment of the invoking process.
  2. A `--env-file` flag value if passed.
  3. A `.env` file located next to the compose file
     (`docker/.env`), if no `--env-file` is passed and no shell var
     is set.
  Compose does **not** automatically read the worktree-root `.env`
  for variable interpolation when `-f docker/docker-compose.yml` is
  used. The materialized `.env` lives at the worktree root, so the
  task's expected invocation
  `docker compose -f docker/docker-compose.yml up -d` will only see
  those variables if the operator already exported them in the
  shell, or if compose is told where to find them. We have to choose
  one of: (a) shipping a project-root or `--project-directory`
  declaration, (b) requiring `--env-file` on the documented command,
  or (c) referencing the variables only via container-side `env_file:`
  rather than via compose interpolation.
  - **Container env vars** (`environment:` / `env_file:` on a
    service) are read by Docker Compose from a different lookup
    path: `env_file:` paths are relative to the compose file
    location (`docker/`), and unresolved `${VAR}` references inside
    `environment:` values still go through the interpolation rules
    above. So pulling the variables in via `env_file:` requires
    pointing at a path the orchestrator does materialize.
  - The orchestrator materializes `.env` at the worktree root (one
    level above `docker/`). The cleanest, least-invasive answer is
    to set `env_file: ["../.env"]` on the new `couchdb-init`
    sidecar; the path is relative to the compose file location, so
    `../.env` resolves to the worktree-root `.env` that the
    orchestrator already writes. The `couchdb` service itself
    (which needs `COUCHDB_USER`/`COUCHDB_PASSWORD` for first-run
    admin creation) gets the same `env_file:` so the same materialized
    file feeds both.
- **CouchDB official image behavior** (`couchdb:3.x`, current series
  3.4 as of 2026-05; image name on Docker Hub is `couchdb`, the
  `latest` tag tracks 3.x):
  - On first start, if `COUCHDB_USER` and `COUCHDB_PASSWORD` env vars
    are set, the entrypoint writes them into
    `/opt/couchdb/etc/local.d/docker.ini` as a server admin (single
    node). Subsequent restarts do **not** overwrite that file; the
    admin persists with the data. This is the recommended way to
    avoid CouchDB's "admin party" insecure default mode.
  - Default config opens HTTP API on port `5984`. There is no
    separate HTTPS listener in the default image; we will bind to
    `127.0.0.1:5984` on the host and not expose it publicly.
  - Persistent paths inside the container:
    - `/opt/couchdb/data` — database files (the actual `clarity` and
      `esp-entity` shards/data lives here, plus `_replicator`,
      `_users`, `_global_changes`).
    - `/opt/couchdb/etc/local.d` — runtime-injected config and the
      first-run admin docker.ini.
    Both must live on a named Docker volume (or two named volumes)
    for restart resilience and replication checkpoint persistence.
    Replication checkpoints are persisted as documents in the
    source/target databases by CouchDB itself, so persistence comes
    "for free" once `/opt/couchdb/data` is on a volume.
  - Single-node initialization: in 3.x single-node mode (default
    when `COUCHDB_USER`/`COUCHDB_PASSWORD` are set), the special
    system databases `_users`, `_replicator`, and `_global_changes`
    are NOT auto-created. The recommended bootstrap is to call
    `POST /_cluster_setup` with `{"action":"enable_single_node",
    "bind_address":"0.0.0.0", "username":"...", "password":"...",
    "port":5984, "singlenode":true}`, which is idempotent and
    creates all three system DBs in one call. Re-calling it on an
    already-set-up node is safe and returns a known status; the
    init sidecar can swallow the second-run response.
  - Healthcheck: `GET /_up` returns 200 with `{"status":"ok"}` once
    the node is fully started (HTTP listener is up and the local
    Erlang node is healthy). This is the documented liveness probe
    and is suitable for a Compose `healthcheck:` block.
- **CouchDB replication mechanism.**
  - Replication state is persisted as documents in the special
    `_replicator` database. The recommended pattern is to PUT a
    document into `_replicator` with a deterministic id (e.g.
    `bootstrap-clarity`) describing the source URL, target DB name,
    `continuous: true`, and (optionally) auth.
  - When the document exists, CouchDB starts the replication job
    automatically. Restarting the CouchDB process resumes from the
    last persisted checkpoint without restarting from sequence 0,
    because replication uses `_local/<replication-id>` checkpoint
    documents written into both source and target.
  - Modern (3.2+) replicator doc shape supports inline auth via an
    `auth.basic.username/password` object on `source` (and on
    `target` if needed), avoiding embedding credentials directly in
    the URL string. The doc body looks like:
    ```json
    {
      "_id": "bootstrap-clarity",
      "source": {
        "url": "https://pps-couch-prd.jgi.lbl.gov:6984/clarity",
        "auth": { "basic": { "username": "...", "password": "..." } }
      },
      "target": "clarity",
      "continuous": true,
      "create_target": false,
      "use_checkpoints": true
    }
    ```
    (Credentials remain inside the local `_replicator` DB on the
    persistent volume; they never leave the developer machine and
    are never committed.)
  - Idempotency of the PUT: a deterministic doc id makes the PUT
    return 201 on first run and 409 conflict on subsequent runs.
    The init script handles both: 201 OK, 409 → the script then
    fetches the existing `_rev` and either updates the doc (if
    fields differ) or treats no-op as success. Replication state
    survives this update because the new doc just becomes another
    revision driving the same logical job; checkpoints in the
    target DB are still used.
  - Replication progress can be observed without printing
    credentials by querying `GET /_active_tasks` (lists running
    replication tasks with `docs_read`, `docs_written`,
    `changes_pending`, `progress`) or
    `GET /_scheduler/jobs` and `GET /_scheduler/docs/_replicator`
    (3.x scheduler API). None of these endpoints print the source
    URL credentials when auth is provided via the structured
    `auth.basic` object — the source URL is reported without
    embedded `user:pass`. (If credentials were embedded directly in
    the URL string instead, `_active_tasks` would echo them; that
    is the second reason to prefer the structured `auth` form.)
- **Datasets (per directive).** `clarity` ≈ 40.6 GB, ≈ 4,052,016
  documents, not growing. `esp-entity` ≈ 11.1 GB, ≈ 1,713,401
  documents, growing. Combined ≈ 52 GB just for data files; CouchDB
  will additionally store view indexes (none requested here) and
  internal metadata. The persistent volume must be sized
  accordingly. We do not pre-allocate — Docker named volumes grow on
  the host filesystem as CouchDB writes — but the documentation
  block needs to call this out.
- **Existing `.env` materialization.** The current materialized
  `developers/claude-1/.env` already contains a non-blank
  `JADE_TIPI_COUCHDB_ADMIN_USERNAME` and
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD` (verified by reading the file —
  values are not reproduced here per the "do not print credentials"
  directive). So once the proposed compose changes are merged, the
  bootstrap will have everything it needs in this worktree without
  any further env edits.

### Proposed plan

#### 1. Compose changes (`docker/docker-compose.yml`)

Add one new service and one new sidecar, plus two new named volumes.

##### 1a. `couchdb` service

Image: `couchdb:3.4` (pin to a 3.4 minor; that line is the current
LTS-grade release as of 2026-05 and matches the documented
`_cluster_setup` and structured `auth.basic` replicator behavior
this plan relies on).

```yaml
  couchdb:
    image: couchdb:3.4
    container_name: jade-tipi-couchdb
    ports:
      - "127.0.0.1:5984:5984"
    environment:
      COUCHDB_USER: ${JADE_TIPI_COUCHDB_ADMIN_USERNAME:?JADE_TIPI_COUCHDB_ADMIN_USERNAME is required}
      COUCHDB_PASSWORD: ${JADE_TIPI_COUCHDB_ADMIN_PASSWORD:?JADE_TIPI_COUCHDB_ADMIN_PASSWORD is required}
    env_file:
      - ../.env
    volumes:
      - couchdb_data:/opt/couchdb/data
      - couchdb_config:/opt/couchdb/etc/local.d
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:5984/_up >/dev/null"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
```

Key choices and why:

- `env_file: ["../.env"]` — relative to the compose file (`docker/`),
  resolves to the orchestrator-materialized worktree-root `.env`.
  Means the `${...}` interpolation also has the values, so the
  `:?` fallback message fires loudly on a real missing-env shell
  invocation rather than silently substituting empty strings.
- `${VAR:?msg}` interpolation — this is compose-side interpolation,
  not container-side. It fails the `docker compose ...` invocation
  immediately and prints the named message **without printing the
  value** (compose's `:?` only prints the variable name and the
  custom message; it never echoes the value). This satisfies the
  "fail clearly when required env vars are missing" criterion in
  the task.
- Two named volumes (`couchdb_data` for the actual DB data,
  `couchdb_config` for the runtime-written `local.d/docker.ini` admin
  password file). Splitting them makes "wipe data only" possible
  later without losing the admin config, and avoids the gotcha where
  recreating the data volume but keeping config leaves a stale admin
  reference. We keep them paired in normal use.
- `127.0.0.1:5984` only — no remote exposure of the local CouchDB.
- `/_up` health probe — documented CouchDB readiness signal; safe to
  poll without auth.
- No `depends_on:` on Mongo/Kafka/Keycloak. CouchDB has no
  dependency on the other services, and we do not want to slow the
  rest of the stack on first run while CouchDB initializes.
- No new top-level `networks:`. Compose's default network is fine;
  the init sidecar will reach `http://couchdb:5984` over it.

##### 1b. `couchdb-init` sidecar

Image: `curlimages/curl:8.10.1` (small, has `curl`, no shell — but
we override the entrypoint with `/bin/sh -c` since the curl image
ships busybox sh). Alternative considered: `couchdb:3.4` itself (it
has bash and curl), but pulling a second copy of a ~250 MB image
just for an init script is wasteful. `curlimages/curl` is ~10 MB.

```yaml
  couchdb-init:
    image: curlimages/curl:8.10.1
    container_name: jade-tipi-couchdb-init
    depends_on:
      couchdb:
        condition: service_healthy
    env_file:
      - ../.env
    environment:
      COUCH_URL: http://couchdb:5984
      LOCAL_USER: ${JADE_TIPI_COUCHDB_ADMIN_USERNAME:?JADE_TIPI_COUCHDB_ADMIN_USERNAME is required}
      LOCAL_PASS: ${JADE_TIPI_COUCHDB_ADMIN_PASSWORD:?JADE_TIPI_COUCHDB_ADMIN_PASSWORD is required}
    volumes:
      - ./couchdb-bootstrap.sh:/usr/local/bin/couchdb-bootstrap.sh:ro
    entrypoint: ["/bin/sh", "/usr/local/bin/couchdb-bootstrap.sh"]
    restart: "no"
```

The script lives at `docker/couchdb-bootstrap.sh` (committed,
non-secret) and is mounted read-only. The script never echoes
credentials, never `set -x`s, and uses `curl --silent --show-error
--fail-with-body --user "$LOCAL_USER:$LOCAL_PASS" -H 'Content-Type:
application/json'` for all admin calls. The local admin
user/password are passed via `-u` (header form, not URL form), so
they never appear in the script's stdout/stderr.

##### 1c. `docker/couchdb-bootstrap.sh` (new file, idempotent)

Behavior:

1. `set -eu` (no `-x`). `: "${COUCH_URL:?}"` and `: "${LOCAL_USER:?}"`
   and `: "${LOCAL_PASS:?}"` and `: "${JADE_TIPI_COUCHDB_CLARITY_URL:?}"`
   and `: "${JADE_TIPI_COUCHDB_ESP_ENTITY_URL:?}"` early-exit checks
   (the `:?` form prints the name only, not the value).
2. The remote credentials may be empty in some dev environments.
   Treat them as required for this bootstrap (the script is run
   only when the operator wants to start replication). Use the same
   `:?` early-exit pattern on
   `JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`JADE_TIPI_COUCHDB_ADMIN_PASSWORD`
   — they are already required for the local admin.
3. POST `/_cluster_setup` with `{"action":"enable_single_node",
   "bind_address":"0.0.0.0", "username":"$LOCAL_USER",
   "password":"$LOCAL_PASS", "port":5984, "singlenode":true,
   "ensure_dbs_created":["_users","_replicator","_global_changes"]}`.
   Accept HTTP 201 on first run; on second run CouchDB returns 200
   with a status string and the script accepts that too. Any other
   non-2xx prints the response body (which does not contain
   credentials) and exits non-zero.
4. PUT `/clarity` and `/esp-entity` against the local node. CouchDB
   returns 201 on first creation, 412 (file_exists) on subsequent
   runs. Treat both as success. Any other status → fail loudly.
5. Build the replicator doc body for each DB (using `printf` with
   single-quoted format strings so credentials are never expanded
   into `set -x` output even if `set -x` is later added):
   ```json
   {
     "_id": "bootstrap-clarity",
     "source": {
       "url": "${JADE_TIPI_COUCHDB_CLARITY_URL}",
       "auth": {
         "basic": {
           "username": "${JADE_TIPI_COUCHDB_ADMIN_USERNAME}",
           "password": "${JADE_TIPI_COUCHDB_ADMIN_PASSWORD}"
         }
       }
     },
     "target": "clarity",
     "continuous": true,
     "create_target": false,
     "use_checkpoints": true
   }
   ```
   And the analogous doc for `esp-entity`.
6. PUT each doc to `/_replicator/<id>`. On 201 → done. On 409 →
   `GET /_replicator/<id>`, extract `_rev` (with a small POSIX
   `sed`/`grep` match — no jq dependency), inject `_rev` into the
   doc, PUT again. The contents of the doc are deterministic, so
   second-run updates are no-ops in steady state but still recover
   from a partial first run that wrote a malformed doc.
7. Print one line per DB to stdout: `bootstrap-clarity: ready` /
   `bootstrap-esp-entity: ready`. Do not print URLs (they contain
   no credentials in the structured-auth form, but printing them
   is unnecessary noise) and do not print credentials.

The script is short (≈70 lines including comments), POSIX `sh`
compatible (busybox-friendly), and uses only `curl` + busybox
`sed`/`grep`/`printf`. No `jq` is required.

##### 1d. New named volumes

```yaml
volumes:
  mongodb_data:
  kafka_data:
  couchdb_data:
  couchdb_config:
```

##### 1e. No changes to other services

`mongodb`, `keycloak`, `kafka`, and `kafka-init` blocks stay
byte-identical. No new `depends_on` is added between CouchDB and the
other services — they are independent.

#### 2. `.env.example` update (worktree root)

The four CouchDB variables are already present and correctly named.
**No `.env.example` change is required.** The directive's
"Update `.env.example` only with non-secret variable names and
placeholder values if additional variables are needed" leaves room
for additions; the current proposal does not introduce any new
variable. A small comment block could be added to clarify that the
same credentials feed both the local admin and the remote-source
auth (see open question Q1 below); I will defer that comment until
the director confirms the credential-reuse decision.

#### 3. `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`

Append a `LATEST_REPORT:` follow-up paragraph during implementation
(not now) recording: the new service/sidecar names, the bootstrap
script path, the two named volumes, and the developer-visible
commands for observing replication progress
(`curl -fsS -u "$U:$P" http://127.0.0.1:5984/_active_tasks` and
`curl -fsS -u "$U:$P" http://127.0.0.1:5984/_scheduler/jobs`). The
task file's `OWNED_PATHS` already covers this file. No change to
`TYPE`, `STATUS`, `OWNER`, or `ACCEPTANCE_CRITERIA` from the
developer side.

#### 4. Optional developer-facing notes

The task says: "Add or update local developer documentation only as
needed to explain how to start the stack, watch replication
progress, and recover from interrupted replication." Recommendation:
keep this scope-minimal. Add at most a short paragraph to
`README.md` (which is not in claude-1's owned paths and not in the
task's expanded `OWNED_PATHS`). Therefore: do **not** edit
`README.md`. Instead, fold the developer guidance into the
`LATEST_REPORT:` of the task file itself, where it lives next to
the implementation. If the director wants the guidance in
`README.md`, request that scope expansion explicitly. Flagged
under Q3 below.

#### 5. Files that will change (smallest set)

- `docker/docker-compose.yml` — add `couchdb` service, add
  `couchdb-init` sidecar, add two volumes.
- `docker/couchdb-bootstrap.sh` — new file (POSIX sh,
  ~70 lines, no secrets).
- `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`
  — append `LATEST_REPORT:` paragraph after acceptance.
- `docs/agents/claude-1-changes.md` — append a short report
  describing the change (claude-1's standing report file).

No edit to `.env.example`, `README.md`, `application.yml`,
`build.gradle`, frontend, or any source file.

### Verification proposal

After implementation, with the worktree-root `.env` containing
real values for the four `JADE_TIPI_COUCHDB_*` variables:

1. `docker compose -f docker/docker-compose.yml config` — confirms
   the file parses, all `${VAR:?...}` placeholders resolve, and
   `env_file: ["../.env"]` is honored. Expected: full rendered
   config printed without error. (Per directive criterion 1.)
2. `docker compose -f docker/docker-compose.yml up -d couchdb` —
   starts only the new service. Expected: `couchdb` container
   reaches `(healthy)` within ~30s based on the `/_up` probe.
3. `docker compose -f docker/docker-compose.yml up -d couchdb-init`
   — runs the bootstrap once. Expected: container exits with status
   `0`, logs show `bootstrap-clarity: ready` and
   `bootstrap-esp-entity: ready`, no credential strings present.
4. `curl -fsS -u "$JADE_TIPI_COUCHDB_ADMIN_USERNAME:$JADE_TIPI_COUCHDB_ADMIN_PASSWORD" \
   http://127.0.0.1:5984/_all_dbs` (operator runs in their own
   shell, with vars exported from `.env`) — expected output includes
   `clarity` and `esp-entity` (alongside `_users`, `_replicator`,
   `_global_changes`).
5. `curl -fsS -u "$U:$P" http://127.0.0.1:5984/_scheduler/jobs` and
   `curl -fsS -u "$U:$P" http://127.0.0.1:5984/_active_tasks` —
   expected: two replication entries (one per DB) with non-zero
   `docs_read`/`changes_pending` once replication has progressed,
   no embedded credentials in the source URL field. The directive
   explicitly says replication progress should be observable
   without exposing credentials; the structured-auth replicator
   doc form satisfies that.
6. **Idempotency check.** Run `docker compose -f
   docker/docker-compose.yml up -d` a second time. Expected: the
   `couchdb` service stays running (no recreation), the
   `couchdb-init` sidecar reruns and exits 0 with the
   `bootstrap-{clarity,esp-entity}: ready` lines (since the local
   DBs and replicator docs already exist, the script takes the
   "already created" / "409 → fetch _rev → no-op PUT" branches).
   The persistent volume is untouched; replication checkpoints in
   the target DB make resumed replication seek to the last
   processed sequence rather than restart from 0.
7. **Negative env check** (no real network load). Temporarily unset
   `JADE_TIPI_COUCHDB_ADMIN_USERNAME` in the shell and re-run
   `docker compose -f docker/docker-compose.yml config`. Expected:
   compose exits non-zero with the named message
   `JADE_TIPI_COUCHDB_ADMIN_USERNAME is required` (no value
   printed). Restore the var afterward.
8. **Remote replication itself is not part of automated
   verification.** The task explicitly says "Do not copy remote
   data during task implementation or tests unless the human
   explicitly runs the local stack and approves the network load."
   We will document that the human can verify pull progress on
   their own machine with the `_scheduler/jobs` query above, but
   we will not start a multi-GB copy in any verification step.

If local Docker is unavailable, report the documented setup command
(start Docker Desktop, then `docker compose -f
docker/docker-compose.yml config`) and the exact blocked command's
error verbatim, instead of treating Docker unavailability as a
product blocker. If `curl` is unavailable on the host shell, use
`docker compose -f docker/docker-compose.yml exec couchdb curl
-fsS -u "$U:$P" http://127.0.0.1:5984/_all_dbs` instead — the
container has `curl` available. The unit `:jade-tipi:test` and
`JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` paths are
unchanged and do not require CouchDB; no Gradle verification is
proposed for this task.

### Blockers and open questions

- **Q1 — Credential reuse for local admin and remote source.**
  `.env.example` lists `JADE_TIPI_COUCHDB_ADMIN_USERNAME`/
  `_PASSWORD` as a single credential pair. The most ergonomic
  reading is that these credentials belong to the operator's JGI
  account and are reused as: (a) the local CouchDB admin
  (`COUCHDB_USER`/`COUCHDB_PASSWORD` on the container), and (b) the
  basic-auth credentials presented to the remote JGI source URL
  inside the replicator doc. The directive says "Use
  `JADE_TIPI_COUCHDB_ADMIN_USERNAME` and ..._PASSWORD from local
  environment only", which I read as "from the local env file, not
  committed", not as "for the local CouchDB admin only".
  - **Recommendation:** reuse the same pair for both roles. It
    matches the existing variable naming, requires no `.env.example`
    changes, and avoids forcing the operator to maintain two
    distinct admin credentials.
  - **Alternative if the director prefers separation:** introduce
    `JADE_TIPI_LOCAL_COUCHDB_USER` and
    `JADE_TIPI_LOCAL_COUCHDB_PASSWORD` for the container admin,
    keep `JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`_PASSWORD` for the
    remote source. This adds two `.env.example` lines (non-secret
    placeholders) and a small change in the bootstrap script. I
    will not implement this unless the director directs.
  - Flagged so the director can confirm the reuse before
    `READY_FOR_IMPLEMENTATION`.
- **Q2 — `couchdb:3.4` vs `couchdb:3` vs `couchdb:latest`.** Pinning
  to `couchdb:3.4` is the safest middle ground (track the LTS
  series, get patch updates, avoid surprise major bumps). The
  current image series is 3.4.x as of 2026-05. The
  `kafka-init`/`kafka` services are also pinned at minor
  (`apache/kafka:4.1.1`), so this matches the project's pinning
  style. **Recommendation:** `couchdb:3.4`. Flag if the director
  prefers a specific patch (e.g. `couchdb:3.4.2`) or the rolling
  `couchdb:3` tag.
- **Q3 — Documentation surface.** I am proposing to keep operator
  guidance inside the `LATEST_REPORT:` section of the task file
  rather than touching `README.md`. The task file is in the granted
  `OWNED_PATHS`; `README.md` is not. The task says "Add or update
  local developer documentation **only as needed**" — the
  task-file paragraph is the smallest viable surface. If the
  director wants a `README.md` Quick Start update, please add
  `README.md` to `OWNED_PATHS` for `TASK-017` explicitly.
- **Q4 — Compose file `env_file: ["../.env"]`.** This works because
  the orchestrator materializes `.env` at the worktree root and the
  `.gitignore` already excludes it. It does mean a developer who
  runs the stack from a non-orchestrator checkout (i.e. a plain
  source clone with no orchestrator setup) needs to copy
  `.env.example` to `.env` themselves before `docker compose ...`.
  That matches the existing project convention and is what
  `.env.example` is for. **Recommendation:** ship `env_file:
  ["../.env"]` as proposed. Flag in case the director wants to
  rely on `--env-file ../.env` on the documented command line
  instead (less ergonomic — the operator must remember the flag).
- **Q5 — `couchdb-init` rerun semantics.** A Compose `up -d` does
  not, by default, restart a `restart: "no"` exited sidecar; the
  operator has to run `docker compose up -d couchdb-init` (or `up
  -d --force-recreate couchdb-init`) to re-trigger the bootstrap.
  This matches `kafka-init` exactly. **Recommendation:** mirror
  `kafka-init` precedent. Flag in case the director prefers an
  always-on `restart: on-failure` policy with the script `exit 0`
  on success (it would still only run once per `up`, but would
  re-run if the script crashed).
- **Q6 — Replicator doc on conflict.** The proposed script handles
  `409 conflict` by fetching the existing `_rev` and writing it
  back. An alternative is to skip the update entirely on 409
  ("doc exists, leave it alone"), which is simpler but means a
  malformed first-run doc would be sticky. **Recommendation:** do
  the fetch-and-rewrite path; it is ~5 extra lines of shell and
  makes the bootstrap self-healing. Flag if the director prefers
  the simpler skip-on-409 form.
- **Q7 — Bootstrap retry/wait semantics.** The init sidecar
  `depends_on: { couchdb: { condition: service_healthy } }` already
  waits for `/_up`. CouchDB's `/_up` returns OK before
  `_cluster_setup` can succeed in some edge cases (a brief window
  during first-startup). **Recommendation:** add a small in-script
  retry loop (3 attempts, 5s each) around the
  `/_cluster_setup` POST only. This is a few lines and avoids
  spurious first-run failures. Flag if the director wants the loop
  removed in favor of a longer Compose `start_period`.
- **Q8 — Should we expose `5984` to the host?** The proposal binds
  to `127.0.0.1:5984:5984`, matching the loopback-binding pattern
  used by `mongodb`, `keycloak`, and `kafka`. This lets the
  operator run `curl http://127.0.0.1:5984/...` and (later) point
  application code at `http://localhost:5984` without going
  through Compose. **Recommendation:** keep the loopback bind. Flag
  if the director wants CouchDB reachable only from inside the
  Compose network.
- **Q9 — Disk usage warning.** ~52 GB of data is significant for
  developer laptops. The bootstrap does not refuse to run if
  available disk is low; CouchDB will simply fail later. We could
  add a `df`/`stat` precheck in the script, but it is fragile (the
  Docker volume is on the host filesystem mounted by Docker
  Desktop, which has its own VM disk on macOS). **Recommendation:**
  document the ~52 GB figure in the task `LATEST_REPORT:` and the
  Q3 doc location, do not add a brittle precheck. Flag in case the
  director wants the precheck.
- No external blockers. Implementation is one new compose service +
  one new sidecar + one new committed shell script + one named
  volume pair, all inside the granted `OWNED_PATHS`. No
  application code change, no Gradle change, no Spring Boot
  configuration change. The change is fully reversible by removing
  the `couchdb` and `couchdb-init` services and the two volumes
  (the persistent volumes can be deleted by the operator with
  `docker volume rm jade-tipi_couchdb_data
  jade-tipi_couchdb_config` if they want a clean slate).

STOP.
