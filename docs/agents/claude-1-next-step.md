# claude-1 Next Step

Pre-work response for TASK-033.

STATUS: PREWORK_COMPLETE
TASK: TASK-033 - Entity property-values read service
DATE: 2026-06-19

## Directive Summary

- Latest `DIRECTIVES.md` signal is `REQUEST_NEXT_STEP`.
- Active task is `TASK-033`, an implementation task in `READY_FOR_PREWORK`.
- Current phase is pre-work only. This turn updates
  `docs/agents/claude-1-next-step.md` and stops.
- Do not implement until the director advances the task to
  `READY_FOR_IMPLEMENTATION` or changes the signal to
  `PROCEED_TO_IMPLEMENTATION`.
- The task follows the accepted `TASK-015` / `TASK-016` read-service and HTTP
  adapter pattern, but for an entity's property values instead of contents
  links.

## Source Inspection

Read for this plan:

- `docs/agents/claude-1.md`
- `DIRECTIVES.md`, especially the active `TASK-033` section and accepted
  `TASK-030` through `TASK-032` summaries
- `docs/orchestrator/tasks/TASK-013-materialized-root-document-contract.md`
- `docs/orchestrator/tasks/TASK-014-materialized-root-document-materializer.md`
- `docs/orchestrator/tasks/TASK-015-root-shaped-contents-read-service.md`
- `docs/orchestrator/tasks/TASK-016-root-shaped-contents-http-integration.md`
- `docs/orchestrator/tasks/TASK-031-human-readable-kafka-property-definition-materialization.md`
- `docs/orchestrator/tasks/TASK-032-human-readable-kafka-property-assignment-materialization.md`
- `docs/orchestrator/tasks/TASK-033-entity-property-values-read-service.md`
- `DIRECTION.md`
- `docs/architecture/kafka-transaction-message-vocabulary.md`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadControllerSpec.groovy`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/PropertyAssignmentKafkaMaterializeIntegrationSpec.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/exception/GlobalExceptionHandler.groovy`

## Current Shape Facts

Accepted write-side state:

- `TASK-028` materializes `ent + create` as an `ent` root with `_id`, `id`,
  `collection`, top-level `type_id`, `properties`, `links`, and
  `_head.provenance`.
- `TASK-030` materializes `typ + update add_property` as
  `properties.property_refs.<property_id>` on the target `typ` root.
- `TASK-031` materializes `ppy + create` with
  `data.kind == "definition"` as a `ppy` root whose `properties` include
  `kind`, `name`, and opaque verbatim `value_schema`.
- `TASK-032` materializes `ppy + create` with
  `data.kind == "assignment"` as its own `ppy` root whose `properties`
  include `kind`, `entity_id`, `property_id`, and verbatim object-shaped
  `value`.
- `TASK-032` deliberately does not rewrite the target `ent` root's
  `properties` map. Therefore the read service must join materialized roots at
  read time instead of depending on an entity-root projection.

## Proposed Read Contract

Add a read service that returns one entity root plus a map of assigned property
values keyed by property id.

Default route:

```text
GET /api/entities/{id}/property-values
```

Default response shape:

```json
{
  "entityId": "<ent id>",
  "typeId": "<typ id or null>",
  "properties": {},
  "links": {},
  "provenance": {},
  "valuesByPropertyId": {
    "<property id>": [
      {
        "assignmentId": "<ppy assignment id>",
        "propertyId": "<property id>",
        "propertyName": "barcode",
        "value": { "text": "barcode-1" },
        "provenance": {}
      }
    ]
  }
}
```

Recommended class names:

- `EntityPropertyValuesReadService`
- `EntityPropertyValuesRecord`
- `EntityPropertyValueRecord`
- `EntityPropertyValuesReadController`

`EntityPropertyValuesRecord` should carry the materialized entity root fields
that callers need now: `entityId`, `typeId`, root `properties`, root `links`,
entity root `provenance`, and `valuesByPropertyId`.

`EntityPropertyValueRecord` should carry each assignment row's
`assignmentId`, `propertyId`, optional `propertyName`, object-shaped `value`,
and assignment root `provenance`.

## Mongo Query Plan

Service entry point:

```text
Mono<EntityPropertyValuesRecord> findPropertyValues(String entityId)
```

Validation:

- `Assert.hasText(entityId, 'entityId must not be blank')`
- As with the current controller specs, `IllegalArgumentException` should flow
  through `GlobalExceptionHandler` as HTTP 400.

Step 1: fetch the entity root.

```text
mongoTemplate.findById(entityId, Map.class, 'ent')
```

- If no `ent` root exists, return `Mono.empty()`.
- The controller maps that empty result to HTTP 404 with an empty body,
  matching the committed-transaction and admin read behavior for missing
  single-resource reads.

Step 2: query assignment roots for an existing entity.

```text
collection: ppy
criteria:
  properties.kind == "assignment"
  properties.entity_id == <entityId>
sort:
  properties.property_id ASC
  _id ASC
```

Use the sort to make the returned map/list ordering deterministic even when
multiple assignment roots share one property id.

Step 3: resolve property-definition names.

Default proposal: include the definition-name join in this bounded unit.

```text
collection: ppy
criteria:
  _id in <assignment property ids>
  properties.kind == "definition"
```

Only use this join to populate `propertyName` from
`definition.properties.name`. Do not validate the assignment against the
definition, do not validate `value` against `value_schema`, and do not fail the
request when the definition root is missing.

Dangling `property_id` behavior:

- Keep the assignment in the response.
- Set `propertyName` to `null`.
- Do not reject, repair, or hide the row.

Zero-assignment behavior:

- Return HTTP 200 with the entity record and `valuesByPropertyId: {}`.
- Do not run a definition lookup when the assignment query returns no rows.

Provenance mapping:

- Entity provenance comes from `ent._head.provenance`.
- Assignment provenance comes from `ppy assignment._head.provenance`.
- Missing provenance maps to `null`.
- Do not add a legacy `_jt_provenance` fallback for this new reader unless the
  director explicitly requests it. Unlike the contents-link reader, this task
  consumes accepted `ent` and `ppy` roots created after the root-document
  contract, not old copied-data rows.

Duplicate entity/property-pair behavior:

- Use list-shaped entries per property id:

```json
"valuesByPropertyId": {
  "<property id>": [
    { "assignmentId": "<first assignment id>", "value": {} },
    { "assignmentId": "<second assignment id>", "value": {} }
  ]
}
```

Rationale:

- The materializer enforces uniqueness only on `_id`, not on
  `(properties.entity_id, properties.property_id)`.
- Choosing a winner would hide committed materialized data.
- Failing the read would turn a reachable data shape into an availability
  problem.
- Preserving all rows, sorted by `properties.property_id` then `_id`, gives a
  deterministic and inspectable read model. A later validator/anomaly task can
  decide whether duplicate entity/property pairs should be reported separately.

## Controller Plan

Add a thin WebFlux adapter:

```text
@RequestMapping('/api/entities')
@GetMapping('/{id}/property-values')
```

Behavior:

- Delegate only to `EntityPropertyValuesReadService.findPropertyValues(id)`.
- Return `ResponseEntity.ok(record)` when the service emits a record.
- Return `ResponseEntity.notFound().build()` when the service returns empty.
- Let `IllegalArgumentException` surface through `GlobalExceptionHandler` as
  HTTP 400.
- Keep the controller Mongo-free, Kafka-free, and materializer-free.
- Add no controller-side authorization, pagination, projection, or write
  policy in this task.

## Implementation Steps

1. Add immutable read DTOs under
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`.
2. Add `EntityPropertyValuesReadService` using `ReactiveMongoTemplate`.
3. Add `EntityPropertyValuesReadController` under
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/`.
4. Add focused service specs under
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`.
5. Add focused controller specs under
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/`.
6. Add the narrowest practical opt-in HTTP integration spec under
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/entity/`,
   reusing the accepted Kafka/Mongo/Keycloak pattern from
   `ContentsHttpReadIntegrationSpec`.
7. Update `docs/architecture/kafka-transaction-message-vocabulary.md` with a
   short read-side section if needed to document the new HTTP read contract.
   `docs/OVERVIEW.md` can be left unchanged unless implementation reveals that
   a concise roadmap/status note is needed.

No `CommittedTransactionMaterializer`, `MaterializeResult`, Kafka listener,
DTO schema, example resource, Docker, Gradle, frontend, security, or admin
group-management change is needed for the default plan.

## Service Spec Coverage

Add `EntityPropertyValuesReadServiceSpec` with focused mock-based coverage
similar to `ContentsLinkReadServiceSpec`:

- Rejects `null`, empty, and whitespace entity ids with
  `IllegalArgumentException` and no Mongo calls.
- Missing `ent` root returns `Mono.empty()` and does not query assignments.
- Existing `ent` root with zero assignments returns a record with
  `valuesByPropertyId == [:]` and does not query definitions.
- Captures and asserts the assignment query:
  `properties.kind == "assignment"`,
  `properties.entity_id == entityId`, sorted by `properties.property_id` ASC
  and `_id` ASC.
- Maps entity root `type_id`, `properties`, `links`, and `_head.provenance`
  into the response.
- Maps assignment root `_id`, `properties.property_id`,
  `properties.value`, and `_head.provenance`.
- Captures and asserts the definition query when assignments exist:
  `_id in propertyIds` and `properties.kind == "definition"`.
- Populates `propertyName` from `definition.properties.name` when a matching
  definition root exists.
- Tolerates a dangling `property_id` by preserving the assignment with
  `propertyName == null`.
- Preserves duplicate same-entity/same-property assignments as a list under
  one `valuesByPropertyId[propertyId]` key, sorted by assignment `_id`.
- Preserves multiple property ids in deterministic sorted key order.
- Maps missing `_head.provenance` to `null`.
- Performs no Mongo writes.

## Controller Spec Coverage

Add `EntityPropertyValuesReadControllerSpec` using `WebTestClient.bindToController`
and `GlobalExceptionHandler`, mirroring the existing contents and transaction
controller specs:

- `GET /api/entities/{id}/property-values` returns HTTP 200 with serialized
  entity fields, `valuesByPropertyId`, assignment value object, optional
  `propertyName`, and provenance fields.
- The route delegates to `EntityPropertyValuesReadService.findPropertyValues`
  and has no other collaborator.
- Missing service result returns HTTP 404 with an empty body.
- Existing entity with no assignments returns HTTP 200 and
  `valuesByPropertyId == {}`.
- Whitespace-only id surfaces the service `Assert.hasText(...)` error as HTTP
  400 through `GlobalExceptionHandler`.
- Constructor structure proves the controller has only the read service as a
  collaborator.
- Route path is pinned exactly as `/api/entities/{id}/property-values`.

## Integration Plan

Add an opt-in integration spec, tentatively:

```text
jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/entity/EntityPropertyValuesHttpReadIntegrationSpec.groovy
```

Reuse the accepted `ContentsHttpReadIntegrationSpec` pattern:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@AutoConfigureWebTestClient`
- `@ActiveProfiles('test')`
- `@IgnoreIf` gate requiring `JADETIPI_IT_KAFKA` plus reachable Kafka and
  reachable Keycloak
- Per-spec Kafka topic and consumer group
- Per-feature ids
- DTO-built messages
- Authenticated `WebTestClient`
- Bounded Mongo polling
- Exact cleanup of this spec's `txn`, `typ`, `ent`, and `ppy` rows

The integration transaction should publish:

1. `txn + open`
2. `ppy + create kind=definition` for `barcode`
3. `typ + create` for an entity type
4. `typ + update add_property` registering `barcode`
5. `ent + create` for the entity
6. `ppy + create kind=assignment` for `barcode`
7. `txn + commit`

Assertions:

- The transaction header becomes committed.
- The `ent` root exists with the expected `type_id`.
- The assignment `ppy` root exists with `properties.kind == "assignment"`,
  `properties.entity_id`, `properties.property_id`, object-shaped
  `properties.value`, and `_head.provenance`.
- `GET /api/entities/{entityId}/property-values` returns HTTP 200 with the
  entity fields, one `valuesByPropertyId[propertyId]` list entry, the
  `propertyName == "barcode"` definition join, `value.text == "barcode-1"`,
  and assignment provenance `txn_id` / `msg_uuid`.
- A fresh entity id that has never been materialized returns HTTP 404, not
  200 with an empty values map.
- A materialized entity with no assignments returns HTTP 200 with
  `valuesByPropertyId == {}`. This can be the same transaction with a second
  `ent + create` of the same type and no assignment, or a second lightweight
  transaction if source inspection during implementation makes that cleaner.

## Verification Commands

Implementation-turn verification should run:

```bash
./gradlew :jade-tipi:test --tests '*EntityPropertyValuesReadServiceSpec*'
./gradlew :jade-tipi:test --tests '*EntityPropertyValuesReadControllerSpec*'
./gradlew :jade-tipi:test
./gradlew :jade-tipi:compileIntegrationTestGroovy
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*EntityPropertyValuesHttpReadIntegrationSpec*'
```

If Docker/Kafka/Mongo/Keycloak is not already running, use the project-documented
setup command before the opt-in integration run:

```bash
docker compose -f docker/docker-compose.yml up -d
```

If Gradle cache locks or stale daemons interfere, use:

```bash
./gradlew --stop
```

## Commands Run During Pre-work

No Gradle, Docker, Kafka, Mongo, or Keycloak verification was run in this
pre-work turn. Source inspection used only read-only shell commands, including:

```bash
git status --short --branch
rg -n "TASK-033|Entity property-values|ppy|assignment" DIRECTIVES.md docs/orchestrator/tasks
rg -n "@RequestMapping|@GetMapping" jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller
rg --files jade-tipi/src/main/groovy/org/jadetipi/jadetipi jade-tipi/src/test/groovy/org/jadetipi/jadetipi
nl -ba <task/doc/source/test files> | sed -n '<ranges>'
```

No setup blocker was encountered because this turn did not require starting
local services or compiling code.

## Director Decisions Requested

Default proposals for director acceptance:

1. Route: `GET /api/entities/{id}/property-values`.
2. Missing entity: service returns empty and controller returns 404 with an
   empty body.
3. Existing entity with zero assignments: return 200 with entity fields and
   `valuesByPropertyId: {}`.
4. Include the property-definition name join now, but only to populate
   optional `propertyName`; dangling `property_id` remains non-fatal.
5. Represent duplicate assignments for one property id as a list under that
   property id, preserving all materialized rows rather than choosing a winner.
6. Use `_head.provenance` only, with missing provenance mapped to `null` and no
   legacy `_jt_provenance` fallback for this new reader.
7. Keep `value_schema`, validation, permissions, pagination, frontend, and
   entity-root projection out of scope.

## Stay-in-scope Check

This pre-work turn edits exactly:

- `docs/agents/claude-1-next-step.md`

It does not edit:

- `docs/agents/claude-1-changes.md`
- `DIRECTIVES.md`
- any active task file
- any production source, tests, integration tests, docs outside this file,
  Docker, Gradle, frontend, or configuration files

I stop here. Implementation should wait for the director to advance
`TASK-033` to `READY_FOR_IMPLEMENTATION` or explicitly accept the default
proposals and set `SIGNAL: PROCEED_TO_IMPLEMENTATION`.
