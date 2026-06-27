# TASK-033 - Entity property-values read service

ID: TASK-033
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_REVIEW
OWNER: claude-1
SOURCE_TASK:
  - TASK-032
  - TASK-031
  - TASK-016
  - TASK-015
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/resources/application-test.yml
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/resources/
  - docs/orchestrator/tasks/TASK-033-entity-property-values-read-service.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Plan the smallest read-side increment that answers "what is this entity and
which property values are assigned to it?" from the materialized root
documents: a read service that joins one `ent` root with its `ppy`
assignment roots, plus a thin HTTP read adapter, following the accepted
TASK-015/TASK-016 contents read-service and HTTP-integration pattern.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, the accepted
  TASK-013/TASK-014 root-document notes, the accepted TASK-015/TASK-016
  contents read path, and the accepted TASK-031/TASK-032 materialized `ppy`
  shapes before planning.
- Preserve Kafka as the primary submission route for domain data. This task
  adds read-only surface; HTTP reads are established precedent
  (`/api/contents`, transaction reads). Do not add HTTP data submission
  endpoints.
- Inspect the materialized shapes this read must consume: `ent` roots
  (TASK-028); `ppy` assignment roots with `properties.kind == "assignment"`,
  `properties.entity_id`, `properties.property_id`, and object-shaped
  `properties.value` (TASK-032); `ppy` definition roots with
  `properties.kind == "definition"`, `properties.name`, and
  `properties.value_schema` (TASK-031); and `typ` roots'
  `properties.property_refs` (TASK-030).
- Propose the smallest service query shape: fetch the `ent` root by id, query
  the `ppy` collection for `properties.kind == "assignment"` and
  `properties.entity_id == <id>`, and map assignment values keyed by
  `properties.property_id`.
- Decide whether the response also resolves each assignment's
  property-definition root for the human-readable `properties.name`, or
  whether the definition join is deferred to a later bounded unit. If joined,
  the read must tolerate a dangling `property_id` (definition root absent)
  without failing the request; semantic reference enforcement stays out of
  scope.
- Decide the route and response envelope following the
  `ContentsLinkReadService` / `ContentsLinkRecord` /
  `ContentsLinkReadController` precedent, including provenance mapping from
  `_head.provenance`, behavior for a missing `ent` root versus an existing
  entity with zero assignments versus a blank/missing id, and a deterministic
  ordering for assignment entries (for example `properties.property_id` ASC).
- Decide the behavior when multiple assignment roots share the same
  `properties.entity_id` and `properties.property_id`. That state is
  reachable: the materializer enforces uniqueness only on `_id` and does not
  enforce the conventional `<entity_id>~<property_id>` ID format, so a second
  assignment with a different `data.id` but the same entity/property pair
  passes every gate and inserts. Options include a deterministic winner,
  list-shaped entries per property id, or a surfaced anomaly.
- The service performs no writes, no projection maintenance onto `ent` roots,
  no value-shape validation against `value_schema`, and no permission
  enforcement.
- Propose focused automated coverage: service spec (query criteria, record
  mapping, definition-join behavior if in scope, missing-root behavior,
  zero-assignment behavior, blank-id behavior, dangling-reference tolerance,
  duplicate entity/property-pair behavior per the chosen decision above,
  ordering, provenance mapping, no writes), controller spec for the route and
  serialization, and the narrowest practical opt-in integration check built
  on the existing Kafka-materialized `ent`/`ppy` fixtures.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- No HTTP data submission endpoints and no Kafka submission changes.
- No `CommittedTransactionMaterializer` or `MaterializeResult` changes.
- No assignment projection onto `ent` roots (explicitly deferred by
  TASK-032).
- No value-shape validation against `value_schema`, no required-property
  enforcement, no semantic reference enforcement (reads tolerate dangling
  references; they do not reject or repair them).
- No permission enforcement, no pagination, no object extension pages, no
  transaction-overlay reads of committed-but-unmaterialized messages.
- No plate-shaped contents composition or other multi-entity views (a later
  bounded unit per DIRECTION.md Query Direction).
- No frontend UI work and no broad ID-abbreviation cleanup.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify the exact Mongo query criteria and record mapping against the
  materialized shapes produced by the accepted TASK-028/031/032 paths,
  including how `_head.provenance` maps onto the response records.
- Identify whether the definition-name join belongs in this bounded unit or a
  follow-up, with the tradeoff stated for director ruling.
- Identify the smallest implementation/test changes needed for the service,
  controller, focused specs, and integration verification.
- Identify any shape or envelope ambiguity that needs director or human
  review before implementation.
- Stop after pre-work. Do not implement until the director advances this task
  to `READY_FOR_IMPLEMENTATION`.

VERIFICATION:
- Expected implementation-turn commands:
  - `./gradlew :jade-tipi:test`
  - the narrowest practical opt-in integration test if local Docker is
    running and the project has a documented opt-in flag for it
- If local verification is blocked by Docker, Kafka, Mongo, Gradle cache
  permissions, or missing dependencies, report the exact command and error
  rather than treating setup friction as a product blocker. Prefer documented
  setup commands: `docker compose -f docker/docker-compose.yml --profile mongodb up -d`
  for Mongo-backed unit tests, `docker compose -f docker/docker-compose.yml up -d`
  for the full Kafka/Mongo stack, and `./gradlew --stop` only when stale Gradle
  daemons are implicated.

DESIGN_NOTES:
- TASK-032 deliberately stored assignments as their own root-shaped `ppy`
  records and left the entity root's `properties` map untouched. This task is
  the read-time join that delivers entity-with-values to consumers without
  opening the dual-source-of-truth question that root projection would
  create.
- Keep `ppy` assignment roots canonical for values. The service reads
  `properties.entity_id`/`properties.property_id`/`properties.value` from
  assignment roots; it must not depend on any future denormalized projection.
- DIRECTION.md's overlay read model (root documents plus committed
  not-yet-materialized messages) remains future work; this task reads
  materialized roots only.

DEPENDENCIES:
- TASK-031 and TASK-032 are accepted and provide materialized `ppy`
  definition and assignment roots.
- TASK-028 is accepted and provides materialized `ent` roots.
- TASK-015 and TASK-016 are accepted and provide the read-service and HTTP
  read-integration pattern this task should follow.

IMPLEMENTATION_SUMMARY:
- Human approval on 2026-06-22 accepted the TASK-033 pre-work defaults and
  authorized implementation from the `READY_FOR_PREWORK` plan.
- Added `EntityPropertyValuesReadService` to read one materialized `ent` root
  by `_id`, query materialized `ppy` assignment roots by
  `properties.kind == "assignment"` and `properties.entity_id == <entity_id>`,
  sort by `properties.property_id` then `_id`, and group assignment values by
  `properties.property_id`.
- Added `EntityPropertyValuesRecord` and `EntityPropertyValueRecord` response
  records. Assignment values preserve duplicate entity/property assignments as
  lists, copy object-shaped `properties.value` verbatim, map `_head.provenance`,
  and tolerate dangling property definitions by returning `propertyName ==
  null`.
- Added `EntityPropertyValuesReadController` at
  `GET /api/entities/{id}/property-values`. Missing `ent` roots return HTTP
  404; existing entities with no assignments return HTTP 200 with
  `valuesByPropertyId: {}`.
- Added focused service and controller specs plus an opt-in Kafka/Mongo/
  Keycloak integration spec that publishes the canonical property loop and
  verifies the HTTP route for assigned, unassigned, and missing entities.
- Updated `docs/architecture/kafka-transaction-message-vocabulary.md` with the
  read-side entity property-values contract and explicit non-goals.

VERIFICATION_RESULTS:
- `./gradlew :jade-tipi:test --tests '*EntityPropertyValuesReadServiceSpec*' --tests '*EntityPropertyValuesReadControllerSpec*'`
  initially failed during test compilation because one new Spock feature used
  an interaction inside an `expect:` block; the spec was corrected to
  `when:`/`then:` and the command then passed.
- `./gradlew :jade-tipi:test` passed.
- `./gradlew :jade-tipi:integrationTest --tests '*EntityPropertyValuesHttpReadIntegrationSpec*'`
  passed with `JADETIPI_IT_KAFKA` unset, compiling integration-test sources and
  skipping the opt-in Kafka/Keycloak test.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*EntityPropertyValuesHttpReadIntegrationSpec*'`
  passed against the already-running local `jade-tipi-kafka`,
  `jade-tipi-mongo`, and `jade-tipi-keycloak` containers.
- `git diff --check` passed.
