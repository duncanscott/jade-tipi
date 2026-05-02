# TASK-019 - Prototype Clarity/ESP container materialization

ID: TASK-019
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
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
Implement a tiny prototype that turns the documented Clarity/ESP container
examples into Jade-Tipi root-shaped `loc`, `typ`, and `lnk` MongoDB documents
using the accepted materialization model.

ACCEPTANCE_CRITERIA:
- Implement the accepted mapping from
  `docs/architecture/clarity-esp-container-mapping.md` for the documented
  examples only: the Clarity tube and ESP freezer/bin/plate chain.
- Exercise the existing root-document materialization behavior from
  `TASK-014` and contents-link behavior from `TASK-015`; do not redesign the
  root-document contract.
- Materialize only supported collections for this prototype: `loc`,
  `typ + link_type`, and `lnk`. Do not add `ent` materialization.
- Preserve parentage only in `lnk` records. Do not add
  `parent_location_id` or equivalent duplicated relationship fields to `loc`.
- Represent freezer/bin/plate slot or well position as a `position` property on
  the `contents` link instances for this prototype.
- Ensure the `typ~contents` example declares
  `assignable_properties: ["position"]`, carries directional labels on the
  type, and uses a transaction-local id consistently in the prototype messages.
- Add focused unit coverage for the transformation/materialization behavior and
  the expected `loc`, `typ`, and `lnk` root shapes.
- Update `docs/architecture/clarity-esp-container-mapping.md` and
  `docs/agents/claude-1-changes.md` only as needed to reflect implementation
  details and verification results.

OUT_OF_SCOPE:
- Do not build or operate the full Clarity/ESP import or synchronization.
- Do not modify CouchDB replication, Docker bootstrap, or remote CouchDB data.
- Do not add Spring Boot CouchDB initialization, design-document loaders, or
  application startup dependencies on CouchDB.
- Do not attempt broad schema coverage for Clarity or ESP.
- Do not add production CouchDB import/synchronization code.
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
- Run the narrowest relevant Gradle checks, at minimum the new/updated service
  unit tests and compile tasks, if local tooling permits.
- Report exact commands and results in `docs/agents/claude-1-changes.md`.
- If local CouchDB, replicated data, credentials, Gradle, Docker, or MongoDB
  setup blocks work, report the exact command and error. Do not treat missing
  replicated examples as an import/sync task.

LATEST_REPORT:
Director implementation review on 2026-05-02 accepts `TASK-019`.
claude-1's latest implementation changed only
`docs/agents/claude-1-changes.md` and
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ClarityEspContainerMappingSpec.groovy`.
The report file is inside claude-1's base owned paths, and the new spec is
inside the task-expanded service test ownership granted here and in
`DIRECTIVES.md`.

The accepted prototype is intentionally test-only, matching the mapping doc's
implementation intent. It exercises the existing
`CommittedTransactionMaterializer` against the documented Clarity tube and ESP
freezer/bin/plate examples: four `loc + create` messages, one
transaction-local `typ + create` `contents` link-type declaration with
`assignable_properties: ["position"]`, and two `lnk + create` contents edges.
The assertions pin the expected root-shaped `loc`, `typ`, and `lnk` documents,
source traceability fields, `_head.provenance`, directional labels on the type
root only, slot/well position on link properties, and the absence of
`parent_location_id`-style fields on `loc`. No production source, `ent`
materialization, Docker/CouchDB, import/sync, endpoint, frontend, security, or
root-contract change was made.

Director static verification passed with
`git diff --check origin/director..HEAD`. Director Gradle verification was
blocked before project code ran by sandbox permissions opening the Gradle
wrapper cache lock under `/Users/duncanscott/.gradle` (`Operation not
permitted`) for both `./gradlew :jade-tipi:compileTestGroovy` and
`./gradlew :jade-tipi:test --tests '*ClarityEspContainerMappingSpec*'
--rerun-tasks`. In a normal developer shell, use the project-documented setup
path if needed: `docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop` for stale Gradle daemons, then rerun the two Gradle
verification commands above. Credited developer verification reported
`./gradlew :jade-tipi:compileTestGroovy` and the targeted
`ClarityEspContainerMappingSpec` test passing; the full unit suite was blocked
only by the existing Mongo-dependent `JadetipiApplicationTests.contextLoads`
when the Docker stack was not up.

No follow-up task is created automatically. The accepted active goal now has a
bounded Clarity/ESP container materialization prototype, and the next useful
unit requires human product direction: e.g. whether to deepen this into
application import/synchronization, add `ent` materialization for sampled
analytes, or shift to another read/materialization capability.

Historical report:
Director review on 2026-05-02 advances this task to
`READY_FOR_IMPLEMENTATION`.

The prior Docker/bootstrap objection is resolved. The
`docker/couchdb-bootstrap.sh` max-document-size edit is already present on
`director` as separate human-directed infrastructure work (`8b54a0f`), not as a
pending TASK-019 implementation diff. Do not revert or modify it under this
task.

The accepted mapping pre-work fixes the earlier design blockers:
`typ~contents` declares `assignable_properties: ["position"]` in both the
materialized root and `typ + create` message, and the examples use a
transaction-local `typ~contents` id consistently. `ContentsLinkReadService`
already resolves matching contents type ids by `properties.kind/name`, so the
transaction-local id remains compatible with the accepted read path.

Next claude-1 turn: implement the smallest executable prototype for the
documented Clarity/ESP container examples. Keep the work bounded to the owned
service/test/documentation paths and avoid broad import, sync, endpoint,
`ent`, or Docker/bootstrap work.
