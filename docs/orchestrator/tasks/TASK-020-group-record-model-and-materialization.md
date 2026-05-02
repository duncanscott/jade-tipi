# TASK-020 - Define and materialize group records

ID: TASK-020
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
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
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
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

PREWORK_REVIEW:
- 2026-05-02 director review of claude-1 revision 2 advances this task to
  `READY_FOR_IMPLEMENTATION`. The latest developer commit stayed inside the
  base pre-work ownership boundary: it changed only
  `docs/agents/claude-1-next-step.md`.
- Implement Shape A from the revised pre-work: `data.permissions` is a map
  keyed by world-unique `grp` IDs, and each value is exactly `rw` or `r`.
  This is the accepted product shape because it matches the task acceptance
  criteria and the existing direction docs. Do not implement the earlier
  list-shaped `{grp_id, level}` representation.
- Scope is expanded to include
  `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json` so
  the DTO schema can validate the accepted grp-id-keyed permissions map.
- Correct the proposed schema sketch during implementation. Adding a separate
  `allOf/then/properties/data/properties/permissions` branch while leaving the
  top-level `data` property as `$ref: #/$defs/SnakeCaseObject` is not
  sufficient: the existing `SnakeCaseObject.additionalProperties` validation
  still applies to `data.permissions` and would recurse into the grp-id-keyed
  map. Implement the schema exception in a test-proven way, for example by
  making root `data` choose a `grp`-specific data object schema whose
  `properties.permissions` exception lives in the same object schema as its
  `additionalProperties`, while non-`grp` messages continue to validate
  ordinary `SnakeCaseObject` payloads.
- Keep the implementation otherwise aligned with the revised plan: add the
  canonical `13-create-group.json`, update `MessageSpec`, document the
  canonical grp wire and materialized root shape, add `grp + create` support to
  `CommittedTransactionMaterializer`, and add focused materializer coverage for
  success, unsupported `grp` actions, missing/blank IDs, and only duplicate
  behavior not already covered by the generic insert path.
- Verification for this review was static only; no Gradle, Docker, MongoDB, or
  Kafka command was required for the pre-work review. If implementation
  verification is blocked by local tooling or services, report the documented
  setup commands already listed in this task instead of treating setup as a
  product blocker.
- 2026-05-02 director review keeps this task at `READY_FOR_PREWORK`.
  claude-1 stayed within its base pre-work ownership boundary: the latest
  developer commit changed only `docs/agents/claude-1-next-step.md`.
- Do not proceed to implementation from the revision-1 plan as written. The
  proposed `data.permissions` list of `{grp_id, level}` rows does not satisfy
  this task's accepted requirement for a group permissions map. Existing
  project direction in `DIRECTION.md`, `docs/README.md`,
  `docs/Jade-Tipi.md`, and `docs/user-authentication.md` also describes a
  permissions map, so an implementation that documents only a list would create
  model drift.
- Next pre-work must resolve the map/schema compatibility issue before
  implementation. Answer these concrete questions:
  - What exact canonical `grp + create` payload shape will preserve a
    permissions map granting other group IDs `rw` or `r` while still passing
    `MessageSpec` validation against `message.schema.json`?
  - If the existing `SnakeCaseObject.propertyNames` rule makes the accepted map
    shape impossible for world-unique group IDs, should this task request an
    explicit owned-path expansion to update `message.schema.json` and its
    focused tests, or should the task be revised by the director/human to accept
    a list-shaped permissions representation?
  - Which relevant docs will be updated so `DIRECTION.md`, `docs/README.md`,
    `docs/Jade-Tipi.md`, `docs/user-authentication.md`, and
    `docs/architecture/kafka-transaction-message-vocabulary.md` do not
    contradict each other on map versus list semantics?
- The rest of the proposed materializer approach is directionally sound:
  adding `grp + create` to the existing root-document materializer whitelist
  should reuse the accepted TASK-014 root shape and `_head.provenance` behavior,
  with focused tests for success, unsupported actions, missing/blank IDs, and
  any duplicate behavior not already covered by the generic insert path.
