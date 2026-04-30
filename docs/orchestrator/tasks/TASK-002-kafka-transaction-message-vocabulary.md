# TASK-002 - Define Kafka transaction message vocabulary

ID: TASK-002
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
OWNED_PATHS:
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/
  - clients/kafka-kli/src/test/groovy/org/jadetipi/kafka/cli/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/UnitSpec.groovy
REQUIRED_CAPABILITIES:
  - code-implementation
GOAL:
Define the Kafka-first message contract for transaction-staged Jade-Tipi data submission. Add a first-class target collection to the `Message` envelope and document/test the initial property/type/entity vocabulary needed to create properties, associate properties with entity types, create entities, and assign property values as JSON objects.

ACCEPTANCE_CRITERIA:
- `Message` includes a first-class `collection` field using the existing Jade-Tipi collection abbreviations (`ent`, `ppy`, `lnk`, `uni`, `grp`, `typ`, `vdn`, `txn`).
- `message.schema.json` requires and validates `collection`, including valid action/collection combinations where practical for this task.
- Example messages cover a complete early transaction flow: open transaction, create property definition, create or update entity type with property reference, create entity, assign property value, and commit transaction.
- Property values are represented as JSON objects, including scalar wrapper examples such as `{ "text": "barcode-1" }` or `{ "number": 10, "unit_id": "..." }`.
- The Kafka CLI can produce or pass through the updated message shape without dropping `collection`.
- Tests validate the updated DTO schema and examples.
- If `./gradlew :libraries:jade-tipi-dto:test` exposes the existing `UnitSpec` block-label compile failure, fix that narrow test issue as a prerequisite and report it separately from the message-vocabulary work.

OUT_OF_SCOPE:
- Do not implement the backend Kafka listener or Spring Kafka dependency in this task.
- Do not implement MongoDB `txn` persistence, snapshot reads, materialization into `ent`/`ppy`/`typ`, or HTTP submission wrappers.
- Do not delete or refactor the legacy HTTP `TransactionController`, `TransactionService`, or `IdGenerator`.
- Do not implement full semantic reference validation between property definitions, types, entities, and assignments.

DESIGN_NOTES:
- Use `docs/architecture/kafka-transaction-message-vocabulary.md` as the starting design note.
- Transaction IDs are client-composed. Do not use `IdGenerator` for Kafka transaction IDs.
- The future `txn` collection will use transaction headers and message records as separate `record_type` values. Header `commit_id` is authoritative; child message `commit_id` stamping is optional denormalization for later work.
- Store `collection` explicitly in future `txn` message records so materializers and readers do not need to infer target collection from arbitrary payload fields.

DEPENDENCIES:
- `TASK-001` is accepted but direct local verification previously exposed a narrow `UnitSpec` Spock block-label issue. Treat that as a test-harness prerequisite only if it blocks this task's DTO verification.
- Spring Boot application and integration-test work requires the Docker stack first: `docker compose -f docker/docker-compose.yml up` from the project checkout. This task should not need Spring Boot integration tests.

LATEST_REPORT:
Director pre-work review on 2026-04-29:
- Accepted claude-1 pre-work commit `6a22292`.
- Scope check passed: the pre-work changed only `docs/agents/claude-1-next-step.md`.
- The proposed plan is clear enough for implementation: add `collection` to `Message`, require it in `message.schema.json`, update message examples, add DTO tests, update kafka-kli message creation/pass-through, and fix only the narrow `UnitSpec` Spock block-label issue if it blocks DTO verification.
- Director decisions for implementation:
  - Encode the clear action/collection compatibility rules in JSON Schema: `txn` uses `open`, `rollback`, `commit`; non-transaction collections use `create`, `update`, `delete`.
  - Keep property value assignments in `ppy` with `data.kind = "assignment"`; do not introduce a separate assignment collection in this task.
  - Leave `Message.getId()` unchanged for this task.
  - Require `--collection` for kafka-kli `create`, `update`, and `delete`; parse through the existing `Collection` enum and fail clearly on unknown values.
- Known limitation accepted for this task: full semantic validation of `data.value` as a JSON object and reference validation between properties, types, and entities can wait for the transaction snapshot/read layer.
