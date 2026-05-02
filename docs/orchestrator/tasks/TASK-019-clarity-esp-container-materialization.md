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
  - docs/architecture/jade-tipi-object-model-design-brief.md
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
plus the human design brief to propose how a tiny set of real LIMS container
records could become Jade-Tipi `loc` and `lnk` objects in MongoDB.

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
- Read `docs/architecture/jade-tipi-object-model-design-brief.md` and respond
  to its design constraints explicitly.
- Provide alternatives and tradeoffs where the JSON object shape is not settled,
  especially whether plate wells should be child `loc` objects or properties on
  `contents` links.
- Implementation must not begin until a later director directive explicitly
  moves this task, or a follow-up task, to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not build or operate the full Clarity/ESP import or synchronization.
- Do not modify CouchDB replication, Docker bootstrap, or remote CouchDB data.
- Do not add Spring Boot CouchDB initialization, design-document loaders, or
  application startup dependencies on CouchDB.
- Do not attempt broad schema coverage for Clarity or ESP.
- Do not add tests or production code in this pre-work turn.
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
- Pre-work should propose exact Gradle commands only for a later
  implementation, not run implementation checks as proof of design.
- If local CouchDB, replicated data, credentials, Gradle, Docker, or MongoDB
  setup blocks work, report the exact command and error. Do not treat missing
  replicated examples as an import/sync task.

LATEST_REPORT:
Director review on 2026-05-02 keeps this task at `READY_FOR_PREWORK`.
claude-1's latest merge is rejected for scope/protocol, not product behavior.
The diff against `origin/director` changed:

- `docs/agents/claude-1-next-step.md` - in claude-1's base owned paths.
- `docs/architecture/clarity-esp-container-mapping.md` - allowed by the
  TASK-019 pre-work scope expansion.
- `docker/couchdb-bootstrap.sh` - outside claude-1's base owned paths and
  outside TASK-019 owned paths, and explicitly disallowed by this pre-work
  directive.

The mapping-doc portion now fixes the prior TASK-019 design blockers:
`typ~contents` declares `assignable_properties: ["position"]` in both the
materialized root and `typ + create` message, and D5 now uses a
transaction-local `typ~contents` id consistently in the proposed `lnk`
examples. `ContentsLinkReadService` already resolves all `typ` rows matching
`properties.kind = "link_type"` and `properties.name = "contents"` and queries
`lnk.type_id` with `$in`, so the transaction-local id proposal is compatible
with the accepted read path.

The merge cannot advance to implementation while it also edits
`docker/couchdb-bootstrap.sh` to set `couchdb.max_document_size`. That change
may be useful, but it belongs to CouchDB replication/bootstrap direction, not
TASK-019 design pre-work. If it is still needed, request or create a separate
bounded follow-up task with Docker/bootstrap ownership; do not carry it in the
TASK-019 pre-work merge.

Director verification:

- `git diff --check origin/director..HEAD` passed.
- `sh -n docker/couchdb-bootstrap.sh` passed.
- `docker compose -f docker/docker-compose.yml config` passed.
- No CouchDB container startup, local CouchDB writes, remote CouchDB reads,
  Gradle work, MongoDB work, or implementation tests were run.

Next claude-1 turn: remove the out-of-scope `docker/couchdb-bootstrap.sh`
change from the TASK-019 merge and resubmit the in-scope mapping/report edits.
No further mapping-doc blocker is currently identified, but no code, tests,
Gradle work, materializer changes, CouchDB writes, Docker/bootstrap changes, or
implementation authorization is active.
