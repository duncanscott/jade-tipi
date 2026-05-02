# TASK-018 - Plan Spring CouchDB initialization

ID: TASK-018
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-017
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - jade-tipi/build.gradle
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/couchdb/
  - jade-tipi/src/main/resources/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/config/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/couchdb/
  - docs/orchestrator/tasks/TASK-018-spring-couchdb-initialization.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - docker-stack
  - gradle-verification
GOAL:
Plan the smallest Spring Boot application-level CouchDB initialization layer
for Jade-Tipi, now that `TASK-017` has accepted the local Docker CouchDB and
remote replication bootstrap.

ACCEPTANCE_CRITERIA:
- Pre-work inspects Jade-Tipi's current Spring Boot configuration style,
  dependencies, test patterns, accepted `TASK-017` Docker files, and the
  reference `pps-esp-entity` project at
  `/Users/duncanscott/git-code/pps/services/pps-esp-entity`.
- Specifically inspect the reference Docker stack and Spring components:
  `docker-compose.yml`,
  `CouchDbInitializer.groovy`,
  `CouchDbDesignDocumentLoader.groovy`,
  `RestTemplateConfig.groovy`, and the design-document resources under
  `src/main/resources/couch/`.
- Propose a bounded Jade-Tipi implementation for optional CouchDB support:
  configuration properties, authenticated client wiring, startup database
  initialization, and an idempotent design-document/view loader if needed.
- The proposal must decide whether Jade-Tipi should install only basic system
  artifacts now or also add initial design documents/views for `clarity` and
  `esp-entity`. If views are proposed, name the initial views and justify them
  from current Jade-Tipi needs.
- The application must not require CouchDB for normal startup unless explicitly
  enabled by configuration.
- The plan must preserve the accepted Docker replication behavior: the local
  CouchDB should contain the remote datasets and stay up to date through
  CouchDB `_replicator`; Spring should not reimplement replication.
- The plan must keep credentials in local environment/config only and avoid
  logging secrets.
- Pre-work must propose focused unit tests and any optional integration check
  that can run against the local Docker CouchDB without requiring a clean
  database or completing multi-GB replication.
- Implementation must not begin until the director reviews the pre-work and
  moves this task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change Docker replication behavior except to document how Spring
  configuration relates to it.
- Do not pull or reset remote CouchDB data.
- Do not add Jade-Tipi domain reads from CouchDB yet.
- Do not replace MongoDB, Kafka, transaction persistence, materialization, or
  contents-read behavior.
- Do not create broad production CouchDB query APIs in this task.

DESIGN_NOTES:
- `TASK-017` accepted Docker-level CouchDB startup and `_replicator` bootstrap.
  The missing next layer is application awareness: optional Spring
  configuration, local database initialization, and any design-document/view
  installation Jade-Tipi needs.
- The `pps-esp-entity` project is a reference, not a copy target. Adapt its
  patterns while avoiding known rough edges and keeping Jade-Tipi's current
  architecture minimal.
- The local CouchDB databases of interest are `clarity` and `esp-entity`.

DEPENDENCIES:
- `TASK-017` is accepted.
- Local `.env` files must include `COUCHDB_USER`, `COUCHDB_PASSWORD`, and the
  remote CouchDB variables before running the full Docker bootstrap.

VERIFICATION:
- Pre-work should propose exact Gradle commands, at minimum compile/test
  checks for any new config/client/loader classes.
- If integration verification is proposed, gate it behind an explicit
  environment flag and use the documented local Docker stack.

LATEST_REPORT:
Created by director on 2026-05-02 after the human confirmed CouchDB
replication is working and requested the project be made ready for another
development loop.
