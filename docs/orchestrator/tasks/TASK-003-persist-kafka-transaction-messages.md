# TASK-003 - Persist Kafka transaction messages to txn

ID: TASK-003
TYPE: implementation
STATUS: IMPLEMENTATION_COMPLETE
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/build.gradle
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/exception/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/
  - jade-tipi/src/main/resources/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/resources/application-test.yml
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
Implementation completed by `claude-1` on branch `claude-1`.

As-built shape:
- Build dependency: `org.springframework.kafka:spring-kafka` added to `jade-tipi/build.gradle`. Spring Boot's BOM pins the version.
- Kafka package (`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/`):
  - `KafkaIngestProperties` (`@ConfigurationProperties("jadetipi.kafka")`) with `txnTopicPattern`, `enabled`, and `persistTimeoutSeconds`. Registered via `config/KafkaIngestConfig.groovy` (`@EnableConfigurationProperties`).
  - `KafkaSourceMetadata` is a small Groovy `@Immutable` value object holding `(topic, partition, offset, timestampMs)` so the persistence service stays Kafka-free.
  - `TransactionMessageListener` (`@KafkaListener`) deserializes `byte[]` to `Message` via `JsonMapper.fromBytes`, calls `Message.validate()`, and delegates to the persistence service. Acks the record on success or on parse/validate failure (poison-pill skip per directive). Persistence errors propagate, leaving the record un-acked for retry.
- Persistence service (`service/TransactionMessagePersistenceService`) is Kafka-free and HTTP-free:
  - Header doc: `_id = txn.getId()`, `record_type = "transaction"`, `state`, `opened_at`, `open_data`; on commit, `state = "committed"`, `commit_id = idGenerator.nextId()`, `committed_at`, `commit_data`.
  - Message doc: `_id = txnId~msgUuid`, `record_type = "message"`, `txn_id`, `msg_uuid`, `collection`, `action`, `data`, `received_at`, and a `kafka` sub-doc `(topic, partition, offset, timestamp_ms)`.
  - Open uses upsert with `setOnInsert` for `opened_at` (re-delivered open does not rewrite the timestamp). Re-delivered open on an existing header (open or committed) returns `OPEN_CONFIRMED_DUPLICATE`.
  - Data inserts are idempotent by natural `_id`. Duplicate-key with matching `(collection, action, data)` returns `APPEND_DUPLICATE`; conflicting payload raises `ConflictingDuplicateException` (record is not acked).
  - Commit before open raises `IllegalStateException` (record is not acked). Commit duplicate returns `COMMIT_DUPLICATE` and does not call `idGenerator.nextId()` again.
  - `txn/rollback` is logged and returns `ROLLBACK_NOT_PERSISTED` (no write).
- Configuration:
  - `application.yml` adds a `spring.kafka.*` block (group-id `jadetipi-txn-ingest`, byte-array value deserializer, `enable-auto-commit: false`, `listener.ack-mode: MANUAL_IMMEDIATE`) and a `jadetipi.kafka.*` block. `${...}` placeholders are escaped as `\${...}` because `processResources` runs `expand`.
  - Default topic pattern is `jdtp-txn-.*|jdtp_cli_kli` so the existing local Docker/KLI traffic is consumed without changing `docker/docker-compose.yml`.
  - `src/test/resources/application-test.yml` adds `jadetipi.kafka.enabled: false` so `JadetipiApplicationTests.contextLoads` does not start a listener that would attempt a broker connection.

Verification:
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL with `docker compose -f docker/docker-compose.yml up -d mongodb`. 37 tests pass, including the new `TransactionMessagePersistenceServiceSpec` (open/open-duplicate/append/append-duplicate/conflicting-duplicate/commit/commit-duplicate/commit-before-open/rollback/null-message) and `TransactionMessageListenerSpec` (parse-success/parse-failure/schema-invalid/persistence-error). The optional Kafka integration test was not added (Testcontainers not wired); deferred per directive.
