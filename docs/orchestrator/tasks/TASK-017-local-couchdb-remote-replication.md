# TASK-017 - Add local CouchDB replication bootstrap

ID: TASK-017
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-016
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docker/
  - .env.example
  - docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - docker-stack
  - local-builds
GOAL:
Modify the local Docker startup configuration so the development stack can
start a local CouchDB instance and bootstrap replication from the two remote
JGI CouchDB databases into same-named local databases: `clarity` and
`esp-entity`.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `docker/docker-compose.yml`, current project environment
  handling, `.env.example`, CouchDB Docker image behavior, and CouchDB
  replication options before proposing implementation.
- The implementation adds a local CouchDB service with persistent storage and
  same-named local databases `clarity` and `esp-entity`.
- The implementation bootstraps replication from:
  `JADE_TIPI_COUCHDB_CLARITY_URL` to local `clarity`, and
  `JADE_TIPI_COUCHDB_ESP_ENTITY_URL` to local `esp-entity`.
- Remote credentials must be read from environment variables already
  documented in `.env.example`: `JADE_TIPI_COUCHDB_ADMIN_USERNAME` and
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD`. Do not commit credentials.
- The startup path must be idempotent and resumable. Re-running
  `docker compose -f docker/docker-compose.yml up -d` must not delete local
  CouchDB data or restart replication from scratch unnecessarily.
- The task must account for scale: `clarity` is approximately 40.6 GB and
  4,052,016 documents and is not growing; `esp-entity` is approximately
  11.1 GB and 1,713,401 documents and is growing.
- Prefer a Docker-native solution such as an init/replication sidecar or
  `_replicator` documents over host-specific manual commands. If a one-shot
  sidecar is used, it should fail clearly when required env vars are missing.
- Add or update local developer documentation only as needed to explain how to
  start the stack, watch replication progress, and recover from interrupted
  replication.
- Update `.env.example` only with non-secret variable names and placeholder
  values if additional variables are needed.

OUT_OF_SCOPE:
- Do not copy remote data during task implementation or tests unless the human
  explicitly runs the local stack and approves the network load.
- Do not write to the remote CouchDB databases.
- Do not add application code that depends on the replicated CouchDB databases.
- Do not change MongoDB, Kafka, Keycloak, Spring Boot, frontend, DTO schema,
  materializer, or contents read semantics.
- Do not make the main Spring Boot or Kafka integration test path require
  remote CouchDB availability.

DESIGN_NOTES:
- This is a prioritized infrastructure task. It supports local development
  against real CouchDB datasets without changing Jade-Tipi persistence
  semantics yet.
- The local database names must be exactly `clarity` and `esp-entity`.
- The remote CouchDB URLs and credentials are expected to be available through
  the orchestrator-materialized `.env` file, not committed project files.
- The large static `clarity` dataset and growing `esp-entity` dataset make
  resumability, checkpointing, and persistent Docker volumes more important
  than fast first-run completion.

DEPENDENCIES:
- `TASK-016` is accepted.
- The shared local env file should provide the CouchDB remote URLs and
  credentials before running the Docker stack.

VERIFICATION:
- `docker compose -f docker/docker-compose.yml config`
- `docker compose -f docker/docker-compose.yml up -d couchdb`
- The chosen init/replication bootstrap command or service starts without
  missing-env failures when `.env` contains the required variables.
- Local CouchDB exposes databases named `clarity` and `esp-entity`.
- Replication status can be observed through CouchDB APIs or logs without
  printing credentials.
- If network access, credentials, Docker permissions, or remote CouchDB
  availability block verification, report the exact command and error. Do not
  treat inability to complete a multi-GB replication as a product failure.

LATEST_REPORT:
Created by director on 2026-05-02 from human direction. This task is the next
prioritized bounded implementation unit after accepted `TASK-016`.

Director pre-work review on 2026-05-02: REQUEST_NEXT_STEP. Do not move to
implementation yet.

Scope check passed: claude-1 changed only
`docs/agents/claude-1-next-step.md`, which is inside the developer-owned
pre-work paths. Static whitespace verification passed with
`git diff --check origin/director..HEAD`.

The plan is directionally aligned with the task: it inspected the compose
stack, existing environment files, CouchDB initialization, persistent volumes,
`_replicator` documents, structured `auth.basic`, large dataset scale, and
non-secret verification boundaries. However, revise the pre-work before
implementation for these blockers:

- Compose environment handling is not sound. The proposed compose snippets use
  `${JADE_TIPI_COUCHDB_*:?}` interpolation while also claiming
  `env_file: ["../.env"]` will provide those values. Local verification with
  `docker compose` v5.1.2 showed service `env_file` values do not satisfy
  compose interpolation; `--env-file .env` does. The task's documented
  commands remain `docker compose -f docker/docker-compose.yml ...`, so revise
  the design to either avoid compose-side interpolation for these variables or
  explicitly justify and document a command change. The current plan would fail
  `docker compose -f docker/docker-compose.yml config` in an orchestrator
  worktree where the credentials are materialized only in the worktree-root
  `.env` and not exported in the shell.
- Re-check the CouchDB image/version choice. Current official CouchDB
  materials show the stable release line is 3.5.x and Docker tags include
  `latest`, `3.5.1`, `3.5`, `3`, and `3.4.3`/`3.4`. If pinning to
  `couchdb:3.4` is still intentional, explain why 3.4 is preferred over
  `3.5`/`3.5.1` for this local development bootstrap. Otherwise update the
  proposed tag.
- The bootstrap script must JSON-escape URLs, usernames, and passwords before
  writing `_replicator` documents. A raw shell `printf` JSON template is not
  robust for credentials containing quotes, backslashes, or other JSON-special
  characters, even though structured `auth.basic` correctly avoids URL
  userinfo.
- Tighten idempotency for existing `_replicator` documents. Do not rewrite the
  document on every 409 conflict unless the existing non-secret fields differ
  or credentials/source URLs were intentionally changed. Rewriting an otherwise
  equivalent continuous replication document risks unnecessary scheduler churn,
  which conflicts with the "must not restart replication from scratch
  unnecessarily" criterion.
- Resolve the local admin credential decision. Reusing the remote JGI
  credential pair as the local CouchDB server admin is not clearly required by
  the task and broadens where those credentials are stored. Either justify the
  reuse or propose separate non-secret local admin variable names in
  `.env.example` while continuing to use
  `JADE_TIPI_COUCHDB_ADMIN_USERNAME`/`JADE_TIPI_COUCHDB_ADMIN_PASSWORD` only
  for the remote sources.
- Align verification with Compose one-shot behavior. The plan says a second
  plain `docker compose -f docker/docker-compose.yml up -d` should rerun
  `couchdb-init`, but later notes that `restart: "no"` one-shot services do
  not normally restart on plain `up -d`. Update the verification plan so it
  matches actual Compose behavior and still proves resumability.

Keep the next pre-work response scoped to resolving those design issues. Do
not implement until this task is moved to `READY_FOR_IMPLEMENTATION`.

Director pre-work revision 2 review on 2026-05-02: READY_FOR_IMPLEMENTATION.

Scope check passed: claude-1 changed only
`docs/agents/claude-1-next-step.md`, which is inside the developer-owned
pre-work paths. Static whitespace verification passed with
`git diff --check origin/director..origin/claude-1`. Director local compose
tooling is present: `docker compose version` reported v5.1.2, and the current
pre-implementation compose file renders with
`docker compose -f docker/docker-compose.yml config`.

The revised plan resolves the prior blockers well enough for implementation:
it avoids compose-side interpolation for remote credentials, uses container-side
`env_file: ../.env` plus bootstrap-script required-var checks, separates local
CouchDB admin credentials (`COUCHDB_USER`/`COUCHDB_PASSWORD`) from remote JGI
credentials, builds replication JSON with `jq`, rewrites `_replicator`
documents only when meaningful fields differ, pins CouchDB to the current 3.5
line, and aligns verification with Compose one-shot service behavior. Official
CouchDB materials confirm the Docker image uses `COUCHDB_USER` and
`COUCHDB_PASSWORD` for the local admin user, CouchDB 3.0+ requires an admin user
at startup, current Docker tags include `3.5.1` and `3.5`, and structured
`auth.basic` replication endpoint credentials are supported and preferred over
URL userinfo.

Implementation direction:

- Proceed with `couchdb:3.5` rather than a patch pin unless implementation
  discovers a concrete compatibility issue.
- Proceed with the `alpine:3.20` bootstrap sidecar and `jq` JSON construction.
- Do not expand `TASK-017` ownership to the orchestrator overlay
  `config/env/project.env.local.example`; keep source changes inside
  `docker/`, `.env.example`, this task file, and the developer report file.
- Add `COUCHDB_USER` and `COUCHDB_PASSWORD` to `.env.example` as non-secret
  local-only placeholders. If this orchestrator worktree's materialized `.env`
  lacks those new variables during verification, report the documented setup
  action of adding them to
  `/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
  and rerunning the orchestrator/materialization path, rather than treating the
  missing local setup as a product blocker.
- Document the approximate 52 GB data-size expectation and recovery/progress
  commands in this task file's implementation report; skip a brittle
  host-disk preflight unless implementation finds a simple Docker-native check.
- Ensure the CouchDB service healthcheck and operator verification commands use
  tools actually available in the selected container image, or run checks from
  the bootstrap sidecar/host when appropriate.

Required verification after implementation remains the task `VERIFICATION`
section plus the revised pre-work's idempotency checks. Do not trigger a
multi-GB remote replication beyond creating/persisting the replication jobs
unless the human explicitly approves the network load.
