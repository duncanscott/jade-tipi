# TASK-026 - Human-readable Kafka loc submission path

ID: TASK-026
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-013
  - TASK-014
  - TASK-019
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - DIRECTION.md
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/jade-tipi-object-model-design-brief.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-026-human-readable-kafka-loc-submission.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Make the next domain increment prove that a human-readable Kafka transaction can
create a root-shaped `loc` object in MongoDB using the existing transaction
ingest, commit, and materialization path.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  and the accepted TASK-013/TASK-014/TASK-019 notes before planning.
- Preserve Kafka as the primary submission route for this task. Do not replace
  it with new HTTP create endpoints.
- Document and, if necessary, adjust the accepted `loc + create` message shape
  so it is easy to hand-author:
  - top-level `collection: "loc"` and `action: "create"`;
  - `data.id` is the materialized object ID;
  - `data.type_id` is optional for now;
  - `data.properties` is a plain JSON object;
  - `data.links` is optional or empty on create.
- Add or update example resource JSON showing one complete transaction that
  opens, creates one simple `loc`, and commits.
- Ensure the materialized Mongo document remains root-shaped with `_id`, `id`,
  `collection: "loc"`, `_head`, `properties`, and `links`.
- Add focused automated coverage proving the example/message shape round-trips
  through DTO validation and materializes a `loc` root with the expected JSON
  shape.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not design the full property-definition/type-validation system.
- Do not implement `ent` materialization, property assignment materialization,
  permission enforcement, object extension pages, or full Clarity/ESP import.
- Do not require `parent_location_id` on `loc`; containment remains modeled by
  `lnk` records.
- Do not introduce a complex nested operation DSL for Kafka messages.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify the current materializer behavior for `loc + create` and whether it
  already accepts the proposed human-readable shape.
- Identify the smallest file changes needed for examples, schema/tests, and
  materializer compatibility.
- Stop after pre-work. Do not implement until the director advances this task to
  `READY_FOR_IMPLEMENTATION`.

VERIFICATION:
- Expected implementation-turn commands:
  - `./gradlew :libraries:jade-tipi-dto:test`
  - `./gradlew :jade-tipi:test`
  - the narrowest practical Kafka/Mongo integration test if local Docker is
    running and the project has a documented opt-in flag for it
- If local verification is blocked by Docker, Kafka, Mongo, Gradle cache
  permissions, or missing dependencies, report the exact command and error
  rather than treating setup friction as a product blocker.

DIRECTOR_PREWORK_REVIEW:
- 2026-05-03: claude-1 pre-work accepted. The pre-work stayed within the base
  developer-owned path: only `docs/agents/claude-1-next-step.md` changed.
  `git diff --check origin/director..HEAD` passed.
- Source review confirms the plan's key behavioral finding:
  `CommittedTransactionMaterializer` currently accepts `loc + create`, but for
  non-`lnk` collections it builds root `properties` by copying every `data`
  field except `id` and `type_id`. A human-readable payload with
  `data.properties` and `data.links` would therefore materialize as nested
  `properties.properties` plus `properties.links`, violating the accepted root
  document contract.
- Proceed with the narrow implementation plan: rewrite the location example to
  the explicit `data.properties` / optional-or-empty `data.links` shape, update
  the materializer to prefer explicit `data.properties` when it is a map while
  preserving the legacy flat location payload as documented tolerance, and add
  focused DTO/materializer coverage. Do not add HTTP submission endpoints,
  schema hardening, `ent` materialization, permission enforcement, or a nested
  Kafka operation DSL.
- Verification correction for the implementation turn: use project-documented
  setup commands. For Mongo-backed unit tests, report or run
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` if the
  local stack is missing. For Kafka integration coverage, use the existing
  documented opt-in pattern only when the stack is available:
  `docker compose -f docker/docker-compose.yml up -d` followed by
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`.
