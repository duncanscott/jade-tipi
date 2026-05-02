# TASK-020 - Define and materialize group records

ID: TASK-020
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-014
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - DIRECTION.md
  - docs/Jade-Tipi.md
  - docs/README.md
  - docs/user-authentication.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy
  - docs/orchestrator/tasks/TASK-020-group-record-model-and-materialization.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Make `grp` records concrete as first-class Jade-Tipi objects without
implementing permission enforcement yet.

ACCEPTANCE_CRITERIA:
- Update the relevant architecture documentation to define the first-pass
  `grp` root-document shape.
- Add one canonical `grp + create` Kafka message example under
  `libraries/jade-tipi-dto/src/main/resources/example/message/`.
- The example must use a world-unique `grp` object ID, `collection: "grp"`,
  `action: "create"`, and a payload that can be materialized as a root-shaped
  Jade-Tipi object.
- Include a simple group permissions map that grants other group IDs either
  `rw` or `r`. Do not introduce more permission values in this task.
- Preserve the current model that users belong to groups through Keycloak claims
  or a future membership service; do not implement membership synchronization.
- Extend focused DTO/example tests so the new `grp + create` example
  round-trips and validates against `message.schema.json`.
- Extend `CommittedTransactionMaterializer` so committed `grp + create`
  messages materialize into the `grp` MongoDB collection using the same
  root-document shape as the existing supported roots.
- Add or update focused materializer tests for successful `grp` materialization,
  unsupported `grp` actions, missing or blank IDs, and duplicate behavior where
  existing coverage does not already cover the generic path.
- Keep `_head.provenance` behavior aligned with `TASK-014`.

OUT_OF_SCOPE:
- Do not enforce read or write permissions on HTTP, Kafka, materializer, or read
  service paths.
- Do not implement object-level permission overrides.
- Do not implement property-value-level permission overrides.
- Do not add a membership collection, Keycloak group synchronization, admin UI,
  or endpoint for group management.
- Do not redesign the transaction WAL, committed snapshot API, root-document
  contract, extension pages, pending pages, or contents-link read behavior.
- Do not change Docker Compose, CouchDB replication, frontend, Kafka topic ACLs,
  OAuth/SASL hardening, or production deployment security.

DESIGN_NOTES:
- Current direction: `grp` records are first-class Jade-Tipi objects with
  world-unique IDs, `type_id`, properties, possible links, and a permissions
  map.
- Members of the owning group have read/write access to objects and property
  assignments owned by that group.
- A `grp.permissions` map grants other groups either read/write (`rw`) or
  read-only (`r`) access to objects owned by the group.
- Because property assignments are owned by groups, future effective-permission
  evaluation will need property-scope checks. This task should only persist the
  data needed for that later work.
- Finer-grained object or property-value overrides are intentionally deferred
  until concrete use cases justify the complexity.

DEPENDENCIES:
- `TASK-014` is accepted and defines the current root-shaped materialized
  document contract.
- Human direction on 2026-05-02 established the first-pass group permission
  model and deferred finer-grained overrides.

VERIFICATION:
- Run the narrowest relevant Gradle checks, at minimum:
  - `./gradlew :libraries:jade-tipi-dto:test --tests '*MessageSpec*'`
  - `./gradlew :jade-tipi:compileGroovy`
  - `./gradlew :jade-tipi:compileTestGroovy`
  - `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
- If local tooling, Gradle locks, Docker, or MongoDB setup blocks verification,
  report the project-documented setup command
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
  `./gradlew --stop` when stale Gradle daemons are implicated, and the exact
  blocked command/error rather than treating setup as a product blocker.

