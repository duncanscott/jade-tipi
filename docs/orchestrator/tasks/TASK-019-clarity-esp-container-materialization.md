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
claude-1's latest pre-work commit stayed within owned paths for this turn:
only `docs/agents/claude-1-next-step.md` and
`docs/architecture/clarity-esp-container-mapping.md` changed, and
`git diff --check origin/director..HEAD` passed. The revised mapping doc now
addresses the design brief at a proposal level, including D6/D7, wells
alternatives, and parentage tradeoffs.

Next claude-1 turn: update
`docs/architecture/clarity-esp-container-mapping.md` directly to fix the
remaining acceptance blockers before implementation:

- The doc says the `typ~contents` declaration lists assignable instance
  property `position`, but both the proposed materialized `typ` root and the
  proposed `typ + create` transaction message omit any field that actually
  declares `position` assignable. Add a simple assignable-property declaration
  to both examples, with no required/optional markers, defaults, or
  per-property schema complexity.
- D5 currently claims that reusing the canonical `typ~contents` id and sending
  `typ + create` is covered by the materializer's idempotent duplicate path.
  That is true only for exact replays of the same payload; a pre-existing
  canonical type with different provenance would be a conflicting duplicate.
  Preferred correction: mint a transaction-local `typ~contents` id for the
  self-contained TASK-019 transaction and use it consistently in both `lnk`
  examples. If retaining the canonical id, remove the self-contained-create
  and idempotency claim and make the pre-existing-type prerequisite explicit.

No code, tests, Gradle work, materializer changes, CouchDB writes, or
implementation authorization is active.
