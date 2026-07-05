# TASK-048 - Procedure and task collections

ID: TASK-048
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_IMPLEMENTATION
OWNER: unassigned
SOURCE_TASK:
  - TASK-047
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-048-procedures-and-tasks.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - clients/kafka-kli/src/main/groovy/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the director-ratified Procedures And Tasks direction
(DIRECTION.md; spec section 1.9): first-class `prc` and `tsk` collections
end to end — wire vocabulary, materialization, typed property values,
canonical examples, and an integration proof of the full task/procedure
provenance loop.

DESIGN (ratified 2026-07-04):
- `prc` = performed procedure (execution event); `tsk` = intention to
  perform a procedure of a given type on a set of inputs.
- Tasks have their own task types; procedure types are never overloaded to
  define both. The task type carries `properties.procedure_type_id` (plus
  optionally the human-readable procedure name); task instances carry no
  per-instance procedure pointer. Type declarations use
  `kind: "task_type"` / `kind: "procedure_type"`, mirroring `link_type`.
- Task inputs (`ent`), task fulfillment (recorded on completion), and each
  output's produced-by relationship are canonical `lnk` records; on-root
  pointers wait for the planned links projection.
- The `prc` root carries a top-level `output_input` map (parallel to
  `lnk`'s `left`/`right`): `{output_ent_id: {input_ent_id:
  <contribution object>}}`; the contribution object is open (e.g.
  `{"volume": 12.5}` for pooling). `vdn`-supplied contribution schemas are
  deferred until `vdn` materializes (UT-4).
- Object identifier suffix charset is `[a-z0-9_-]` (dots removed by
  director ruling 2026-07-04).

ACCEPTANCE_CRITERIA:
- `Collection` enum gains PROCEDURE (`procedure`/`prc`) and TASK
  (`task`/`tsk`) with create/update/delete actions; `message.schema.json`
  gains both in the collection enum, the data-action matrix, and the
  `ObjectId` collection segment set; the materializer's
  `ID_COLLECTION_SEGMENTS` and supported-create set gain both.
- `prc + create` materializes a root document whose top-level
  `output_input` map (when present) is hoisted like `lnk`'s endpoints;
  `tsk + create` materializes a standard typed root.
- Object-targeted property assignments accept `prc` and `tsk` targets
  (materializer `OBJECT_ASSIGNMENT_COLLECTIONS` and the generic read
  service's supported set), with the inheritance-aware gate unchanged.
- Canonical examples cover: a procedure type and a task type (with
  `procedure_type_id`), a task create, input links, a `prc + create`
  carrying `output_input`, the fulfillment link, and a produced-by link —
  all registered in `MessageSpec` (round-trip + schema).
- A Kafka integration spec proves the loop end to end: types → task with
  inputs → procedure with `output_input` → fulfillment and produced-by
  links → typed roots and the map materialized, with
  convention-conformant IDs throughout.
- kli `--collection` help lists `prc` and `tsk`.
- Vocabulary doc gains the Procedures And Tasks message section; the
  specification's section 1.1 rows and section 1.9 flip from Planned to
  Normative for the implemented surface (the `vdn` association stays
  Planned).

OUT_OF_SCOPE:
- No `vdn` materialization or contribution-schema validation (tracked
  with UT-3/UT-4).
- No task lifecycle/status modeling beyond the fulfillment link.
- No links projection onto roots; no read views specific to procedures or
  tasks (the generic property-values reader gains the two collections;
  richer provenance views are follow-on work).

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`
