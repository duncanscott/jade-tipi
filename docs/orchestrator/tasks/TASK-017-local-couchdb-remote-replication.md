# TASK-017 - Add local CouchDB replication bootstrap

ID: TASK-017
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
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
