# TASK-039 - Bootstrap usr jdtp-admin genesis ensure

ID: TASK-039
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-038
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-039-bootstrap-jdtp-admin-genesis.md
  - docs/user-authentication.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/resources/application.yml
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - gradle-verification

GOAL:
Implement plan task A from `docs/architecture/object-property-model-drift.md`
section 8.5: ensure the reserved bootstrap `usr` root (`jdtp-admin`) exists as
an idempotent genesis storage fact so later tasks (usr projection, durable
transaction writer persistence) have an attributable system identity before a
user/transaction creation cycle can occur.

CONTEXT:
- Contract: drift note section 8.2.5 (ratified 2026-07-03). Stable ID
  `<instance_org>~<instance_grp>~genesis~usr~jdtp-admin` with org/grp from new
  application configuration; the literal `genesis` segment sits in the
  timestamp position of the world-unique ID convention.
- The root is written directly (not via a Kafka transaction) with a
  `genesis~jdtp-admin` sentinel under `_head.provenance.txn_id`/`commit_id`,
  mirroring the accepted `admin~<uuid>` sentinel from `GroupAdminService`.
- Startup-hook precedent: `MongoDbInitializer` (CommandLineRunner with
  blocking reactive calls). The genesis ensure follows the same pattern but is
  non-fatal: an unreachable MongoDB logs an error and startup continues,
  because the bootstrap identity is only required once transactions reference
  it (Task C).
- `usr` is NOT added to the wire `Collection` enum or `message.schema.json`;
  genesis creation is backend-internal per section 8.2.3.

ACCEPTANCE_CRITERIA:
- New `UsrGenesisService` (service package) implements `CommandLineRunner`,
  gated by `jadetipi.genesis.enabled` (default `true`), reading
  `jadetipi.instance.org` / `jadetipi.instance.grp` (local development
  defaults `jade-tipi-org` / `dev`).
- Ensure is insert-if-absent by `_id` and idempotent across restarts;
  a concurrent duplicate insert resolves as already-present, not an error.
- The root matches the accepted root-document contract: `_id == id`,
  `collection == "usr"`, `type_id: null`, `properties` carrying
  `kind: "system"`, `display_name: "JDTP Bootstrap Admin"`,
  `status: "reserved"`, and `identity_provenance.source: "bootstrap"`;
  `links: {}`; `_head` with `schema_version`, `document_kind: "root"`,
  `root_id`, and the sentinel provenance.
- The bootstrap root carries no external identity keys (no `orcid`, no
  OIDC issuer/subject) so identity resolution can never match it.
- Startup failure of the ensure is logged and does not abort the
  application.
- Unit spec covers: ID composition, insert-when-absent document shape,
  no-op-when-present, duplicate-race tolerance, disabled gate, and
  non-fatal run failure.
- Integration spec (MongoDB, test profile, ungated) proves the runner
  created the root at context startup, re-ensure returns already-present
  with exactly one root, and the ensure recreates a deleted root.
- Docs: `docs/user-authentication.md` and
  `docs/architecture/kafka-transaction-message-vocabulary.md` bootstrap
  sections gain a current-implementation note (config keys, exact ID,
  sentinel).

OUT_OF_SCOPE:
- No `usr` identity resolution/projection from ORCID/OIDC (plan task B).
- No durable transaction writer persistence (plan task C).
- No `usr` in the wire `Collection` enum, schema, or `kli`.
- No login support, credentials, tokens, or Keycloak changes.
- No membership modeling, permissions, or admin UI.
- No MongoDB index management (first index work lands with plan task B).

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy :jade-tipi:compileTestGroovy :jade-tipi:compileIntegrationTestGroovy`
- `./gradlew :jade-tipi:test`
- `./gradlew :jade-tipi:integrationTest --tests '*UsrGenesisBootstrapIntegrationSpec*'`
  (requires `docker compose -f docker/docker-compose.yml up -d`)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-03):
- New production sources:
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/UsrGenesisService.groovy`
  (CommandLineRunner ensure, non-fatal on failure, `MongoDbInitializer`
  precedent) and `UsrGenesisResult.groovy` (CREATED / ALREADY_PRESENT).
- Configuration: `jadetipi.instance.org` / `jadetipi.instance.grp` /
  `jadetipi.genesis.enabled` added to `jade-tipi/src/main/resources/application.yml`
  with env-var overrides (`JADETIPI_INSTANCE_ORG`, `JADETIPI_INSTANCE_GRP`,
  `JADETIPI_GENESIS_ENABLED`).
- Tests: `UsrGenesisServiceSpec` (7 features: ID composition, insert-shape
  capture, no-op-when-present, duplicate-race, error propagation, non-fatal
  run, disabled gate) and `UsrGenesisBootstrapIntegrationSpec` (2 features:
  startup creation + idempotent re-ensure with single-root count, recreate
  after delete).
- Docs: current-implementation notes added to the bootstrap sections of
  `docs/user-authentication.md` and
  `docs/architecture/kafka-transaction-message-vocabulary.md`; one stale
  `txn.user_id` reference in the vocabulary bootstrap section corrected to
  `txn.writer.user_id` per the ratified writer amendment.
- Verification results: all three compile targets BUILD SUCCESSFUL;
  `:jade-tipi:test` green (UsrGenesisServiceSpec 7/7); targeted
  integration spec green (2/2) against the local Docker stack; full
  `:jade-tipi:integrationTest` green as a coexistence check (new
  CommandLineRunner active in every booted context); `git diff --check`
  clean. Materialized root inspected directly in MongoDB `test_db.usr`
  and matches the section 8.2.5 contract byte-for-byte.
- Note for local builds: Gradle requires `ARTIFACTORY_URL`/`ARTIFACTORY_USER`/
  `ARTIFACTORY_PASSWORD`, sourced from `~/.env` in interactive shells;
  non-interactive shells must source it explicitly.
