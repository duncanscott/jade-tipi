# claude-1 Changes

The developer writes completed work reports here.

STATUS: COMPLETED

## TASK-004 — Add Kafka transaction ingest integration coverage

Goal completed. The backend Kafka ingestion path now has an end-to-end
integration spec that publishes canonical `Message` records (open + data
+ commit) to a per-spec Kafka topic and asserts the resulting `txn`
header and message documents in MongoDB. The spec is opt-in: it skips
unless `JADETIPI_IT_KAFKA=1` is set and a 2-second `AdminClient` broker
probe to `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`) succeeds.

### As-built decisions (per pre-work)

- **Topic strategy.** Per-test topic `jdtp-txn-itest-${shortUuid}`
  created via Kafka `AdminClient.createTopics` in a static
  `@DynamicPropertySource` method (so the topic exists before the
  Spring listener container starts). Deleted in `cleanupSpec` via
  `AdminClient.deleteTopics`. Failed delete is logged but tolerated —
  each run picks a fresh `shortUuid` so leftovers do not interfere.
- **Listener subscription.** The spec sets
  `jadetipi.kafka.txn-topic-pattern` to a regex matching only its
  per-run topic, so the listener does not pick up records from
  `jdtp_cli_kli` or other developers' topics. The default production
  pattern is unchanged.
- **Test gating.** Spock `@IgnoreIf` runs before the Spring context
  loads. Without the env flag or with no broker reachable, the spec
  cleanly reports `skipped=2` and does not start the Spring context.
- **Consumer-group strategy.** A per-run unique
  `spring.kafka.consumer.group-id` (`jadetipi-itest-${shortUuid}`)
  combined with `auto-offset-reset=earliest` (already the production
  default) gives every run a deterministic offset-0 start.
- **Topic discovery latency.**
  `spring.kafka.consumer.properties.metadata.max.age.ms` is shortened
  to `2000` for this spec, so the pattern subscription notices the
  pre-created test topic within ~2s instead of the 5-minute Kafka
  default.
- **Producer wiring.** Plain `KafkaProducer<String, byte[]>` (acks=all)
  serializing each `Message` with the project's
  `org.jadetipi.dto.util.JsonMapper.toBytes(...)`. No new dependency.
  Records are keyed by `txnId` so they hash to the single test-topic
  partition, preserving open → data → commit order.
- **Wait strategy.** Inline reactive polling helpers
  (`awaitMongo` / `awaitConditionTrue`) with a 30s ceiling and 250ms
  cadence. No `org.awaitility:awaitility` dependency added.
- **Cleanup scope.** Per-feature: `mongoTemplate.remove(...)` keyed by
  `txn_id` so the spec coexists with `TransactionServiceIntegrationSpec`,
  which writes its own (different-shape) documents to `txn`. Per-spec:
  delete the test topic.
- **Number of features.** Two:
  1. **Happy path.** Publish open + data + commit, assert the header
     reaches `state=committed` with a backend `commit_id`, and assert
     the data message document has `_id = txnId~msgUuid`,
     `record_type=message`, `collection=ppy`, `kafka.topic`,
     `kafka.partition`, `kafka.offset`, and `kafka.timestamp_ms`.
     Verify `count(txn_id=...) == 2` (one header + one message).
  2. **Idempotency sanity check.** Re-publish the same data message
     after the commit lands; assert the per-`txn_id` document count
     stays at 2 (the persistence service treats matching duplicates as
     `APPEND_DUPLICATE`).
- **Profile vs `@TestPropertySource`.** Used `@DynamicPropertySource`
  inline; no new `application-integration-test.yml` profile was added.
  `@DynamicPropertySource` also runs the `AdminClient` topic creation
  *before* Spring context startup, which is the ordering hook the
  pre-work flagged as needed for pattern subscription.
- **No production-code change.** `TransactionMessageListener`,
  `TransactionMessagePersistenceService`, `KafkaIngestProperties`,
  `KafkaIngestConfig`, and `application.yml` are unchanged. The
  integration test exposed no bug in the accepted ingestion path.
- **No logback-test.xml.** Kafka-client logs are verbose but readable;
  not enough noise to justify a new test logging config.

### Files added

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  (new) — the spec described above. Includes a static gate method,
  `@DynamicPropertySource` overrides + topic creation, scoped Mongo
  cleanup, and inline polling helpers.

### Files unchanged

- All `jade-tipi/src/main/...` files. Production behavior is preserved.
- `jade-tipi/src/test/resources/application-test.yml`. The test profile
  still sets `jadetipi.kafka.enabled: false` so unit-test
  `@SpringBootTest` contexts (e.g. `JadetipiApplicationTests.contextLoads`)
  do not start a Kafka listener. The new integration spec re-enables
  the listener via `@DynamicPropertySource` for its own context only.
- `jade-tipi/build.gradle`. No new dependencies; `kafka-clients` and
  `spring-kafka` were already on the integration-test classpath via
  `spring-kafka` (added in TASK-003), and the integration-test source
  set already inherits both.

### Verification

Setup (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` brings up
  `mongodb`, `keycloak`, `kafka`, and `kafka-init`.
  Verified containers: `jade-tipi-mongo` healthy, `jade-tipi-keycloak`
  healthy, `jade-tipi-kafka` healthy.

Compilation:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL.

Targeted integration run (env flag set, brokers up):

- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — BUILD SUCCESSFUL. Test report:
  `tests=2, skipped=0, failures=0, errors=0, time=5.347s` on the
  first run, `4.917s` on a `--rerun-tasks` re-run (stable).

Skip behavior (env flag NOT set, brokers up):

- `./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — BUILD SUCCESSFUL. Test report:
  `tests=2, skipped=2, failures=0, errors=0, time=0.0s`
  (Spring context never loaded, both features ignored).

Regression check on unit tests:

- `./gradlew :jade-tipi:test --rerun-tasks` — BUILD SUCCESSFUL.
  37 tests pass:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

### How to run locally

```
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*TransactionMessageKafkaIngestIntegrationSpec*'
```

To run the full integration suite (existing Mongo/Keycloak-backed
specs plus this new Kafka spec), drop the `--tests` filter and keep
the env flag set.

### Out-of-scope items not implemented (per directive)

- Materialization of committed records into `ent`/`ppy`/`typ`/`lnk`.
- HTTP submission wrapper.
- Kafka ACLs / OAuth-SASL / Kafka Streams / exactly-once.
- Redesign of the message envelope or persistence record shape.
- No production code changes; the integration test exposed no bug.

## TASK-003 — Persist Kafka transaction messages to txn

Goal completed. The Spring Boot backend now consumes canonical
`org.jadetipi.dto.message.Message` records from Kafka and persists them
into MongoDB's `txn` collection as the durable transaction
write-ahead log. Two record kinds live in `txn`: a header document
(`record_type=transaction`) keyed by the canonical `txn_id`, and one
message document per received `Message` (`record_type=message`) keyed
by `txn_id~msg_uuid`.

### Changes by acceptance criterion

- **Backend Kafka consumer dependencies and configurable topic pattern.**
  - `jade-tipi/build.gradle`: added
    `implementation 'org.springframework.kafka:spring-kafka'` (version
    pinned by the Spring Boot 3.5.6 BOM).
  - `jade-tipi/src/main/resources/application.yml`: added a
    `spring.kafka` block (`group-id` default `jadetipi-txn-ingest`,
    byte-array value deserializer, `enable-auto-commit: false`,
    `listener.ack-mode: MANUAL_IMMEDIATE`) and a `jadetipi.kafka`
    block (`txn-topic-pattern` default
    `jdtp-txn-.*|jdtp_cli_kli`, `enabled` default `true`,
    `persist-timeout-seconds` default `30`). Spring property
    placeholders are escaped (`\${...}`) because the project's
    `processResources` runs Groovy `expand` over `application.yml`.

- **Kafka message deserialization to `Message` via the project mapper.**
  - `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListener.groovy`
    (new): a `@Component` annotated with
    `@KafkaListener(topicPattern = "${jadetipi.kafka.txn-topic-pattern}", autoStartup = "${jadetipi.kafka.enabled}", groupId = "${spring.kafka.consumer.group-id:jadetipi-txn-ingest}")`.
    Receives `ConsumerRecord<String, byte[]>` and `Acknowledgment`,
    deserializes via `org.jadetipi.dto.util.JsonMapper.fromBytes`.

- **Envelope/schema-level validation before persistence.**
  - The listener calls `message.validate()` which runs JSON Schema
    validation against `/schema/message.schema.json`. Malformed JSON
    or schema-invalid messages are logged and acknowledged (poison-pill
    skip per directive) so a single bad record does not stall the
    consumer. Persistence failures are not acknowledged, so the
    listener container retries.

- **Two `txn` record kinds with the documented shapes.**
  - Header: `_id = txnId`, `record_type = "transaction"`, `txn_id`,
    `state` (`open`/`committed`), `opened_at`, optional `commit_id`,
    `committed_at`, plus the open/commit `data` payloads under
    `open_data`/`commit_data`.
  - Message: `_id = txnId~msgUuid`, `record_type = "message"`,
    `txn_id`, `msg_uuid`, `collection`, `action`, `data`,
    `received_at`, and a `kafka` sub-doc with topic/partition/offset/
    `timestamp_ms`.

- **`txn/open` creates or confirms the header.**
  - `service/TransactionMessagePersistenceService.openHeader` upserts
    with `setOnInsert` for `opened_at` so a re-delivered open does
    not rewrite the original timestamp. Re-delivery on an existing
    header (open or already committed) returns
    `OPEN_CONFIRMED_DUPLICATE` and does not modify the document.

- **Idempotent data appends keyed by natural `_id`.**
  - `appendDataMessage` inserts with `_id = txnId~msgUuid`. On
    duplicate-key, the service reads the stored document and compares
    `(collection, action, data)` to the incoming message. Equal
    payload returns `APPEND_DUPLICATE` (success); conflicting payload
    raises `ConflictingDuplicateException` (un-acked, retried).

- **`txn/commit` assigns a backend `commit_id`.**
  - `commitHeader` reads the header and:
    - Errors with `IllegalStateException` if the header is missing
      (commit before open is un-acked, retried).
    - Returns `COMMIT_DUPLICATE` and does not call
      `idGenerator.nextId()` again if the header is already
      committed.
    - Otherwise calls `idGenerator.nextId()` for the orderable
      `commit_id`, sets `state = "committed"`, `committed_at`, and
      `commit_data` in one update, and returns `COMMITTED`.
  - Child message stamping is intentionally not implemented; readers
    are expected to resolve commit visibility via the header.
  - `txn/rollback` is treated as an explicit no-op per directive: the
    service logs and returns `ROLLBACK_NOT_PERSISTED` without writing.

- **Service stays Kafka-free and HTTP-free.**
  - `TransactionMessagePersistenceService` imports neither
    `org.apache.kafka.*` nor any web type. The listener owns all
    Kafka-client and Spring-Kafka imports. Provenance is passed in as
    `KafkaSourceMetadata` (a Groovy `@Immutable` value object), which
    has no Kafka-client dependency.

- **Tests.**
  - `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
    (new, 11 features): open first time, open re-delivered (open and
    committed), data append first time, append duplicate (equal),
    append duplicate (conflicting), commit first time, commit
    duplicate (no second `nextId`), commit before open, rollback,
    and a null-message guard.
  - `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
    (new, 4 features): valid record persisted and acknowledged,
    unparseable bytes acknowledged without forwarding, schema-invalid
    JSON acknowledged without forwarding, persistence error not
    acknowledged.
  - `jade-tipi/src/test/resources/application-test.yml`: existing
    file updated to add `jadetipi.kafka.enabled: false` (per
    directive — no duplicate test profile created) so
    `JadetipiApplicationTests.contextLoads` does not start a
    listener container.

### Verification

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `docker compose -f docker/docker-compose.yml up -d mongodb` to bring
  up MongoDB (the only docker dependency `JadetipiApplicationTests.contextLoads`
  needs).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 37 tests pass,
  including `JadetipiApplicationTests.contextLoads`, the existing
  `TransactionServiceSpec`, and the two new specs above.

### Out-of-scope items deferred (with rationale)

- **Optional Kafka integration test under `integrationTest/`.**
  Per directive, the integration test was deferred because the
  project has no Testcontainers wiring and the docker stack's
  `kafka-init` sidecar pre-creates only `jdtp_cli_kli`. Service and
  listener unit specs cover the acceptance criteria. A follow-up task
  can add Testcontainers and an end-to-end integration spec.
- **Materializing committed records into `ent`/`ppy`/`typ`/`lnk`.**
  Listed in `OUT_OF_SCOPE` and not implemented.
- **HTTP submission wrapper.** Listed in `OUT_OF_SCOPE`. The
  persistence service is intentionally Kafka-free so a future HTTP
  adapter can call it without dragging in Kafka or web types.
- **Topic registration / Kafka ACLs / OAuth-SASL hardening / Kafka
  Streams / exactly-once.** Listed in `OUT_OF_SCOPE` and not
  attempted.

### Files changed

- `jade-tipi/build.gradle` (added `spring-kafka` dependency)
- `jade-tipi/src/main/resources/application.yml` (added
  `spring.kafka` and `jadetipi.kafka` blocks)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/KafkaIngestConfig.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/exception/ConflictingDuplicateException.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaIngestProperties.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaSourceMetadata.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListener.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/PersistResult.groovy`
  (new)
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  (new)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
  (new)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
  (new)
- `jade-tipi/src/test/resources/application-test.yml` (added
  `jadetipi.kafka.enabled: false`, plus the matching
  `spring.kafka.bootstrap-servers` placeholder)
- `docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`
  (`STATUS` flipped to `IMPLEMENTATION_COMPLETE`; `LATEST_REPORT`
  updated with the as-built shape and verification result)
