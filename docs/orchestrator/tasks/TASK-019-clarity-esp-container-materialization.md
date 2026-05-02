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
