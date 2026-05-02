# TASK-019 - Prototype Clarity/ESP container materialization

ID: TASK-019
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-017
  - TASK-014
  - TASK-015
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/clarity-esp-container-mapping.md
  - docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/
  - jade-tipi/src/integrationTest/resources/
REQUIRED_CAPABILITIES:
  - code-implementation
  - docker-stack
  - gradle-verification
GOAL:
Use already-replicated local CouchDB records from `clarity` and `esp-entity`
to design, then prototype in a later implementation step, how a tiny set of
real LIMS container records become Jade-Tipi `loc` and `lnk` objects in MongoDB.

ACCEPTANCE_CRITERIA:
- Pre-work inspects the accepted root-document materialization behavior from
  `TASK-014`, contents-link read behavior from `TASK-015`, and Docker CouchDB
  replication behavior from `TASK-017`.
- Pre-work samples a small number of local CouchDB documents from `clarity` and
  `esp-entity` using local CouchDB only. Do not write to local CouchDB, remote
  CouchDB, or any production service.
- Identify one or two representative container examples, preferably including
  a tube and/or a plate. If ESP has location or containment information for the
  chosen records, include it; if Clarity lacks location fields, state that.
- Propose a concrete mapping to Jade-Tipi root-shaped Mongo documents:
  `loc` for physical containers/positions, `lnk` of type `contents` for
  containment, and `ent` only when the source record clearly represents a
  biological/sample entity rather than a container/location.
- Decide how plate wells should be represented in the prototype: as child
  `loc` objects, as `contents` link properties such as `well: "A1"`, or as a
  documented deferred decision. Justify the choice from the sampled source data.
- Define example IDs, transaction messages, materialized root documents, and
  expected Mongo collections for the selected examples.
- Write or update `docs/architecture/clarity-esp-container-mapping.md` with
  the source samples, mapping decisions, known ambiguities, and proposed
  verification commands.
- Implementation must not begin until the director reviews the pre-work and
  moves this task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not build or operate the full Clarity/ESP import or synchronization.
- Do not modify CouchDB replication, Docker bootstrap, or remote CouchDB data.
- Do not add Spring Boot CouchDB initialization, design-document loaders, or
  application startup dependencies on CouchDB.
- Do not attempt broad schema coverage for Clarity or ESP.
- Do not change the root-document contract, contents read API, Kafka listener,
  transaction persistence semantics, security, frontend, or production
  endpoints unless a later implementation directive explicitly expands scope.

DESIGN_NOTES:
- Full Clarity and ESP import/synchronization are already underway elsewhere
  and are not a Jade-Tipi development concern for this task.
- Clarity LIMS exposed XML APIs; the local `clarity` CouchDB documents are JSON
  translations of those XML entities. Containers are expected to exist there,
  but may not carry physical locations.
- Some Clarity entities were migrated to ESP and may appear in the local
  `esp-entity` CouchDB database. ESP container records may include physical
  location or containment information.
- The product question is whether real LIMS container data can become
  Jade-Tipi `loc` and `lnk` documents without distorting the current model.

DEPENDENCIES:
- `TASK-017` is accepted and local CouchDB replication is working.
- `TASK-014` and `TASK-015` are accepted and define the current root-shaped
  materialized document and contents-link read behavior.
- Local `.env` files must contain local CouchDB credentials for read-only
  inspection of `http://127.0.0.1:5984`.

VERIFICATION:
- Pre-work should report the exact CouchDB read commands or queries used,
  redacting credentials.
- Pre-work should propose exact Gradle commands for any later implementation,
  such as `./gradlew :jade-tipi:compileGroovy`,
  `./gradlew :jade-tipi:compileTestGroovy`, and focused tests.
- If local CouchDB, replicated data, credentials, Gradle, Docker, or MongoDB
  setup blocks work, report the exact command and error. Do not treat missing
  replicated examples as an import/sync task.

LATEST_REPORT:
Created by director on 2026-05-02 from human direction. Full Clarity/ESP import
and synchronization are outside this task; Jade-Tipi should consume a tiny
sample of already-replicated local CouchDB data and map it to `loc`/`lnk`
materialized MongoDB documents.

Director pre-work review on 2026-05-02 keeps this task at
`READY_FOR_PREWORK`. Scope check passed for claude-1's latest pre-work commit:
only `docs/agents/claude-1-next-step.md` changed, which is inside the
developer's base owned paths.

The plan is not ready for implementation yet. The active task requires
pre-work to sample local CouchDB records from `clarity` and `esp-entity`, but
the latest plan reports that sampling was not performed. The mapping remains
hypothetical until the design doc includes one or two redacted source samples,
field paths, selected representative container examples, and the exact
read-only commands used to reproduce the selection.

The next pre-work turn should run only the documented local CouchDB read
inspection and write `docs/architecture/clarity-esp-container-mapping.md`.
If CouchDB is not running, report the documented setup commands rather than a
product blocker:
`docker compose -f docker/docker-compose.yml up -d couchdb` and then
`docker compose -f docker/docker-compose.yml up -d couchdb-init`. In this
director review, local `.env` contained `COUCHDB_USER` and `COUCHDB_PASSWORD`,
but `curl http://127.0.0.1:5984/_all_dbs` failed because no CouchDB server was
listening on port 5984.

One design issue must be resolved before implementation: the current accepted
materializer supports only `loc + create`, `typ + create` for
`data.kind == "link_type"`, and `lnk + create`. It does not materialize
`ent + create`. The next plan must either choose examples that can be proven
with `loc` and `lnk` roots only, or explicitly stop and ask for a separate task
to add `ent` materialization before including sample entities in the prototype.
