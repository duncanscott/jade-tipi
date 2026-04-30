# TASK-003 - Persist Kafka transaction messages to txn

ID: TASK-003
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/build.gradle
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/
  - jade-tipi/src/main/resources/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Implement the first backend Kafka ingestion path for canonical Jade-Tipi `Message` DTOs. Received Kafka messages should be validated and persisted into MongoDB's `txn` collection as the durable transaction write-ahead log.

ACCEPTANCE_CRITERIA:
- `jade-tipi` has backend Kafka consumer dependencies and configuration for a configurable topic pattern. Prefer the design target `jdtp-txn-.*`, but account for the current Docker/local topic naming in pre-work before implementation.
- Kafka message values are deserialized into `org.jadetipi.dto.message.Message` using the project DTO mapper/schema path where practical.
- Each received message is validated at the envelope/schema level before persistence. Full semantic validation of property/type/entity references is out of scope.
- MongoDB `txn` stores transaction headers and transaction messages as separate record kinds:
  - Header document: `_id = txn_id`, `record_type = "transaction"`, `txn_id`, `state`, optional `commit_id`, timestamps.
  - Message document: `_id = txn_id + "~" + msg_uuid`, `record_type = "message"`, `txn_id`, `msg_uuid`, `collection`, `action`, `data`, `received_at`, and Kafka topic/partition/offset metadata.
- `collection=txn/action=open` creates or confirms the transaction header.
- Data messages append idempotently by natural `_id`. Duplicate Kafka delivery with the same message content should be treated as success; conflicting duplicates should be reported clearly.
- `collection=txn/action=commit` assigns a backend-generated, orderable `commit_id` to the transaction header and marks the transaction committed. Child message stamping is optional and should not be required for acceptance.
- Tests cover the persistence service behavior for open, data append, duplicate delivery, and commit stamping. Add a Kafka integration test only if it is practical with the existing Docker/Testcontainers setup.
- Verification includes `./gradlew :jade-tipi:compileGroovy` and the most relevant `:jade-tipi` tests. If Spring Boot integration tests are run, start the Docker stack first with `docker compose -f docker/docker-compose.yml up`.

OUT_OF_SCOPE:
- Do not materialize committed records into `ent`, `ppy`, `typ`, `lnk`, or other long-term collections.
- Do not implement snapshot read APIs or union reads over `txn` and materialized collections.
- Do not build the HTTP submission wrapper in this task.
- Do not delete or refactor the legacy HTTP `TransactionController`, `TransactionService`, or `IdGenerator`.
- Do not implement topic registration, Kafka ACLs, OAuth/SASL hardening, Kafka Streams, or exactly-once processing.

DESIGN_NOTES:
- Kafka-first is intentional. HTTP submission should later become a thin adapter over the same persistence service used by the Kafka listener.
- The `txn` collection is the durable write-ahead log. Submission should be efficient: validate the message envelope and append one Mongo document.
- Transaction IDs are client-composed and carried by `Message.txn`. Do not call `IdGenerator` for transaction IDs.
- The transaction header's `commit_id` is authoritative. Readers that encounter child messages without `commit_id` should resolve the header to determine committed visibility.
- Prefer a small service boundary with no Kafka or HTTP imports, for example a transaction message persistence service called by the Kafka listener now and by HTTP later.

DEPENDENCIES:
- `TASK-002` is accepted and provides the first-class `collection` field, property/type/entity message examples, and kafka-kli support for the canonical message shape.
- Spring Boot app/integration-test work requires the Docker stack first: `docker compose -f docker/docker-compose.yml up` from the project checkout.

LATEST_REPORT:
No developer work has started. Director opened this task from the accepted `TASK-002` message vocabulary and the human's Kafka-first direction.
