# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-003 — Persist Kafka transaction messages to txn (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`) and
`docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`
(status `READY_FOR_PREWORK`), build the first backend Kafka ingestion
path. The Spring Boot application should consume canonical
`org.jadetipi.dto.message.Message` records from a configurable Kafka
topic pattern, validate them at the envelope/schema level, and persist
them into MongoDB's `txn` collection as the durable transaction
write-ahead log. Two record kinds live in `txn`:

- A header document keyed by `txn_id` (`record_type=transaction`).
- One message document per received `Message` keyed by
  `txn_id~msg_uuid` (`record_type=message`).

`collection=txn/action=open` opens the header. `collection=txn/action=commit`
stamps the header with a backend-generated orderable `commit_id` and
marks it committed. All other valid `(collection, action)` pairs append
data messages idempotently.

This is pre-work only. No backend, build, or test code may change until
the director moves `TASK-003` to `READY_FOR_IMPLEMENTATION` (or sets the
global signal to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `jade-tipi/build.gradle` has no Kafka dependency. It pulls
  `spring-boot-starter-webflux`, `spring-boot-starter-data-mongodb-reactive`,
  the project DTO/id libraries, Spock, and reactor-test. No
  `spring-kafka`, no `reactor-kafka`, no Testcontainers.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/` does not contain a
  `kafka/` package yet. Existing leaf packages are
  `config`, `controller`, `dto`, `endpoints`, `exception`, `filter`,
  `mongo`, `service`, `util`.
- `service/TransactionService.groovy` is the legacy HTTP-driven service.
  It writes header documents into the `txn` collection with shape
  `{ _id: <tipiId~org~grp>, grp:{organization,group}, txn:{id, secret, commit, open_seq, opened, commit_seq, committed} }`.
  The legacy ID format (`tipiId~org~grp` produced by `IdGenerator.nextId()`
  joined with org/grp) is structurally different from the canonical
  `Transaction.getId()` (`uuid~org~grp~client`), so legacy headers and
  new Kafka-produced headers will not collide on `_id`. The task says
  do not refactor or delete this service.
- `mongo/config/MongoDbInitializer.groovy` already creates the `txn`
  collection (it iterates `Collection.values()` and creates each
  abbreviation-named collection). No new collection creation needed.
- `util/Constants.groovy` already exposes
  `COLLECTION_TRANSACTIONS = 'txn'` and `TRANSACTION_ID_SEPARATOR = '~'`.
  These are the only two constants new code needs from this file; the
  others are document-collection-specific.
- `application.yml` activates the `mongodb` profile by default and runs
  on `${backendPort}`. There is no Kafka configuration block.
- `application-mongodb.yml` configures the local Mongo connection
  (`localhost:27017`, db `jdtp`). No Kafka section.
- `docker/docker-compose.yml` defines a single Kafka broker
  (`apache/kafka:4.1.1`) on `localhost:9092` with
  `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`. The `kafka-init` sidecar
  pre-creates exactly one topic: `jdtp_cli_kli` (single underscore form,
  one partition). No `jdtp-txn-*` topic is pre-created locally.
- `clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/KafkaCli.groovy`
  publishes to `DEFAULT_TOPIC = 'jdtp_cli_kli'`. This is the only Kafka
  producer in the repo. Any local end-to-end test that uses `kli` to
  produce will land messages on `jdtp_cli_kli` unless a different
  `--topic` is passed.
- `Message` is `(txn, uuid, collection, action, data)`. `Message.getId()`
  returns `<txn.getId()>~<uuid>~<action>` (annotated `@JsonIgnore`).
  `Message.validate()` runs JSON Schema validation against
  `/schema/message.schema.json`.
- `Transaction.getId()` is `<uuid>~<org>~<grp>~<client>` and is also
  `@JsonIgnore`. This is the value the task calls `txn_id`.
- Existing Mongo persistence patterns use `ReactiveMongoTemplate`
  (`DocumentServiceMongoDbImpl`, `TransactionService`). The codebase is
  WebFlux/reactive end-to-end.
- `JadetipiApplicationTests.contextLoads` opens a Mongo connection, so
  any new Spring-context test will require the Docker stack.

### Proposed plan

The minimum implementation to satisfy acceptance criteria has four
seams: build dependency, configuration, Kafka listener, and
persistence service (plus tests). I propose the layout below.

#### 1. Build & dependency (`jade-tipi/build.gradle`)

- Add a single new runtime/compile dependency:
  `implementation 'org.springframework.kafka:spring-kafka'`. Spring
  Boot's BOM (3.5.6) pins a compatible version, so no explicit version
  is needed. Spring Kafka brings the `kafka-clients` it needs.
- Reason for `spring-kafka` (not `reactor-kafka`): the rest of the
  codebase already uses Spring annotations (`@Service`, `@Component`,
  `@KafkaListener` is the natural counterpart). The listener thread can
  call `ReactiveMongoTemplate` operations and `.block()` to integrate
  with the reactive persistence layer, since the Spring Kafka container
  thread is not a Reactor Netty event loop. This keeps the surface
  small and matches existing Spock-with-mocks test style. I will
  surface `reactor-kafka` as an alternative in the open questions.
- No test dependency on Testcontainers or `spring-kafka-test` for this
  task — see verification section.

#### 2. Configuration

- Add a new `application.yml` block:

  ```yaml
  spring:
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      consumer:
        group-id: ${KAFKA_CONSUMER_GROUP:jadetipi-txn-ingest}
        auto-offset-reset: earliest
        enable-auto-commit: false
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      listener:
        ack-mode: RECORD
  jadetipi:
    kafka:
      txn-topic-pattern: ${KAFKA_TXN_TOPIC_PATTERN:jdtp-txn-.*|jdtp_cli_kli}
      enabled: ${KAFKA_INGEST_ENABLED:true}
  ```

  - Default pattern accepts the design target (`jdtp-txn-.*`) **and**
    the only topic the local docker stack creates (`jdtp_cli_kli`).
    This is the explicit "account for current Docker/local topic
    naming" call from the acceptance criteria. The pattern can be
    narrowed in production via env var.
  - `enabled` is a kill switch so the existing
    `JadetipiApplicationTests.contextLoads` test (which already needs
    Mongo running) does not also start hitting Kafka. The test profile
    will set `jadetipi.kafka.enabled=false` (see §6).
  - `ack-mode: RECORD` plus `enable-auto-commit: false` means the
    listener container only commits the offset after the listener
    method returns successfully, giving us at-least-once semantics
    paired with idempotent Mongo writes.
  - Manual offset commit on a per-record basis is the simplest model
    that satisfies "duplicate Kafka delivery with the same message
    content should be treated as success" without exactly-once.
- New file
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaIngestProperties.groovy`
  (`@ConfigurationProperties("jadetipi.kafka")`) with two fields:
  `String txnTopicPattern` and `boolean enabled` (default true). This
  binds cleanly with `@EnableConfigurationProperties` and avoids
  `@Value` strings sprinkled in the listener.

#### 3. Kafka listener (`jade-tipi/.../kafka/`)

New package
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/`:

- `TransactionMessageListener.groovy` — `@Component` with a single
  `@KafkaListener(topicPattern = "#{jadetipiKafkaProperties.txnTopicPattern}", autoStartup = "#{jadetipiKafkaProperties.enabled}")`
  method. Signature:

  ```groovy
  void onMessage(ConsumerRecord<String, byte[]> record, Acknowledgment ack)
  ```

  Steps:
  1. Deserialize `record.value()` to `Message` via the project's
     `org.jadetipi.dto.util.JsonMapper`. On parse failure, log error
     with topic/partition/offset/key, ack the record (poison-pill
     skip), and return. This avoids stalling the consumer on a single
     bad message; alternative behavior — DLQ or fail-stop — is an open
     question for the director.
  2. Call `message.validate()`. On `ValidationException`, log the
     validation result and ack-skip the record (same rationale).
  3. Build a `KafkaSourceMetadata` value object
     (`{ topic, partition, offset, timestamp }`).
  4. Call `transactionMessagePersistenceService.persist(message, metadata)`
     and `.block(Duration)` for the result. The block timeout
     (`txn.persist.timeout`, default 30s) is configurable.
  5. On success, `ack.acknowledge()`.
  6. On failure, log and **do not** ack; let the listener container
     re-deliver. (Spring Kafka with `ack-mode: RECORD` and a thrown
     exception triggers default error handler retries; we will rely on
     Spring's `DefaultErrorHandler` in this first cut and not customize
     the back-off in this task.)
- `KafkaSourceMetadata.groovy` — small Groovy record-style class
  capturing `topic`, `partition`, `offset`, `timestampMs`. Lives next
  to the listener so the persistence service does not depend on Kafka
  client classes.

The listener is the only file in `kafka/` that imports
`org.apache.kafka.*` or `org.springframework.kafka.*`. The persistence
service stays Kafka-free, satisfying the design note: "Prefer a small
service boundary with no Kafka or HTTP imports".

#### 4. Persistence service (`jade-tipi/.../service/`)

New `service/TransactionMessagePersistenceService.groovy`. Public API:

```groovy
Mono<PersistResult> persist(Message message, KafkaSourceMetadata source)
```

`PersistResult` enum: `OPENED`, `OPEN_CONFIRMED_DUPLICATE`, `APPENDED`,
`APPEND_DUPLICATE`, `COMMITTED`, `COMMIT_DUPLICATE`. (Plain enum, no
Kafka/HTTP types.)

Internal flow:

- Compute `txnId = message.txn().getId()`.
- Compute `recordId = txnId + "~" + message.uuid()` for data messages.
- Branch by `(collection, action)`:
  - **`txn`/`open`**: upsert a header document with `_id = txnId`,
    `record_type = "transaction"`, `txn_id = txnId`, `state = "open"`,
    `opened_at = now()` (server time on insert), and the same
    `data` payload from the open message under `data`. Use
    `mongoTemplate.upsert(query, update, COLLECTION_TXN)` with
    `setOnInsert` for `opened_at` so a re-delivered open does not
    rewrite the original timestamp. If a header already has
    `state = "committed"`, return `OPEN_CONFIRMED_DUPLICATE` (no
    error — operator already saw a commit) and log a warning.
    Otherwise return `OPENED` for first-write or
    `OPEN_CONFIRMED_DUPLICATE` for an existing open header.
  - **`txn`/`commit`**: read existing header by `_id = txnId`. If
    missing, return error (commit before open is a hard envelope-level
    failure that should be reported). If `state == "committed"`,
    treat as duplicate — return `COMMIT_DUPLICATE` and reuse the
    stored `commit_id`. Otherwise generate a new `commit_id` via
    `idGenerator.nextId()` (already a Spring bean in
    `IdGeneratorConfig`; it produces an orderable, sortable ID — same
    approach the legacy `TransactionService` uses for its commit_seq).
    Update the header in one Mongo call with `state = "committed"`,
    `commit_id`, `committed_at`, and the commit message's `data`
    payload under `commit_data`. Return `COMMITTED`.
  - **`txn`/`rollback`**: explicitly out of the acceptance criteria.
    For first cut: log info, write nothing, return a new
    `ROLLBACK_NOT_PERSISTED` result. Acknowledged in OOS already; I
    will not invent rollback semantics here.
  - **All non-`txn` collections** (`ent`, `ppy`, `typ`, `lnk`, `uni`,
    `grp`, `vdn` × `create|update|delete`): build a message document
    with `_id = recordId`, `record_type = "message"`, `txn_id`,
    `msg_uuid`, `collection`, `action`, `data`, `received_at`, and
    a `kafka` sub-doc `{ topic, partition, offset, timestamp_ms }`.
    Insert with `mongoTemplate.insert(...)`. On
    `DuplicateKeyException`:
      1. `findById(recordId)` to retrieve the existing document.
      2. Compare `collection`, `action`, and `data` against incoming
         values. If equal, return `APPEND_DUPLICATE` (success).
      3. If different, return error `ConflictingDuplicateException`
         with both stored and incoming summaries in the message. The
         listener will surface this clearly in the log; we do not
         ack-skip on conflicts.
- The service does **not** call `IdGenerator` for `txn_id` (per design
  note), only for `commit_id`.

Reactive shape: every step returns `Mono`, no `.block()` inside the
service. The listener does the single bounded `.block()` so we do not
mix Reactor and Spring-Kafka threading models inside the service.

#### 5. Tests (mocked, no Docker, no live Kafka)

`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
— Spock spec with mocked `ReactiveMongoTemplate` and mocked
`IdGenerator`. Cases (matches acceptance criteria one-for-one):

1. `txn/open` first time → `OPENED`, upsert called with insert-only
   `opened_at`, `state=open`.
2. `txn/open` re-delivery on existing open header →
   `OPEN_CONFIRMED_DUPLICATE`, no `opened_at` rewrite.
3. `txn/open` after commit → `OPEN_CONFIRMED_DUPLICATE` with
   warning-level log assertion (optional).
4. Data message first time → `APPENDED`, insert called with
   `record_type=message`, `_id = txnId~msgUuid`, kafka metadata.
5. Data message duplicate, equal payload → `APPEND_DUPLICATE` (no
   error).
6. Data message duplicate, different payload → fails the `Mono` with
   `ConflictingDuplicateException`.
7. `txn/commit` first time → `COMMITTED`, header updated with
   `commit_id` from mocked `IdGenerator.nextId()`, `state=committed`,
   `committed_at`.
8. `txn/commit` duplicate → `COMMIT_DUPLICATE`, no second
   `idGenerator.nextId()` call.
9. `txn/commit` before open → `Mono.error(IllegalStateException)`.

Listener-level test (lighter):
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
— mocks the persistence service and the `Acknowledgment`, feeds
synthetic `ConsumerRecord<String, byte[]>` instances. Cases:

1. Valid message → service called with parsed `Message` plus
   metadata, ack acknowledged once, no exception.
2. Unparseable bytes → service not called, ack acknowledged
   (poison-pill skip), warning logged.
3. Schema-invalid message → service not called, ack acknowledged.
4. Service returns error → ack **not** called; exception propagates.

No Kafka broker, no embedded Kafka, no `spring-kafka-test`. These are
unit-style Spock specs constructing `ConsumerRecord` directly.

#### 6. Test-profile behavior

The existing `application-test.*` profile is implicit (no file). To
prevent `JadetipiApplicationTests.contextLoads` from booting a
listener that immediately fails on a missing broker:

- Add `jade-tipi/src/test/resources/application-test.yml` with:

  ```yaml
  jadetipi:
    kafka:
      enabled: false
  spring:
    kafka:
      bootstrap-servers: localhost:9092
  ```

  The `enabled=false` flag flips `autoStartup` on the `@KafkaListener`,
  so the consumer container is created but does not poll. The Mongo
  context-load behavior of the test stays unchanged.
- Confirm `@ActiveProfiles("test")` is already on
  `JadetipiApplicationTests` (it is). This is the only change needed.

#### 7. Optional: Kafka integration test

The acceptance criteria say a Kafka integration test should be added
"only if it is practical with the existing Docker/Testcontainers
setup." The repo has no Testcontainers wiring and the docker stack is
operator-launched. I will add an opt-in integration test under
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageIngestIntegrationSpec.groovy`
that:

- Is annotated `@EnabledIfEnvironmentVariable(named = "KAFKA_BOOTSTRAP_SERVERS", matches = ".+")`
  so it stays skipped in CI / on developer machines without the docker
  stack up.
- Produces synthetic open + data + commit messages to a per-test
  topic name (`jdtp-txn-itest-${uuid}`) using the
  `kafka-clients` `KafkaProducer` directly.
- Asserts the resulting `txn` collection contains the expected header
  and message documents using `ReactiveMongoTemplate`.

This single optional integration test is the only file I plan to add
under `integrationTest/`. If the director would prefer to defer the
integration test entirely until a Testcontainers wiring exists, I will
drop it.

#### 8. Documentation

- Update
  `docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`
  `LATEST_REPORT` (allowed by the task's own `OWNED_PATHS`) at the end
  of the implementation turn to record the as-built shape, the
  effective topic pattern default, and any deferred items.

### Proposed file changes (all inside expanded scope)

- `jade-tipi/build.gradle` — add `spring-kafka` dependency.
- `jade-tipi/src/main/resources/application.yml` — add `spring.kafka`
  block and `jadetipi.kafka.*` properties.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaIngestProperties.groovy`
  (new).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/KafkaSourceMetadata.groovy`
  (new).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListener.groovy`
  (new).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  (new).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/PersistResult.groovy`
  (new enum) and a small
  `ConflictingDuplicateException.groovy` under `service/` or
  `exception/`. (Will pick `exception/` if the existing convention
  goes there — to confirm during implementation, no scope question.)
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
  (new).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageListenerSpec.groovy`
  (new).
- `jade-tipi/src/test/resources/application-test.yml` (new, only sets
  `jadetipi.kafka.enabled=false`).
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageIngestIntegrationSpec.groovy`
  (new, env-gated; optional and droppable).
- `docs/orchestrator/tasks/TASK-003-persist-kafka-transaction-messages.md`
  — update `LATEST_REPORT` only at end of implementation.

No legacy code is touched: `TransactionService`, `TransactionController`,
`IdGenerator`, and the rest of the existing service/mongo packages are
left exactly as-is.

### Verification I plan to run after implementation

- `./gradlew :jade-tipi:compileGroovy` (acceptance-criterion-required)
  — must pass.
- `./gradlew :jade-tipi:test` — must pass. This will run the new Spock
  specs **and** `JadetipiApplicationTests.contextLoads`. The
  `contextLoads` test still requires `docker compose -f
  docker/docker-compose.yml up` (Mongo); I will start the docker stack
  before running it, per `DIRECTIVES.md` and `CLAUDE.md`. If the
  director would prefer that I run only the new specs (e.g.
  `./gradlew :jade-tipi:test --tests '*TransactionMessage*'`) to avoid
  the Mongo dependency for the verification report, please say so.
- `./gradlew :jade-tipi:integrationTest` — only if the Kafka
  integration test is kept and the docker stack is up. The new test is
  env-gated so it skips silently when `KAFKA_BOOTSTRAP_SERVERS` is
  unset.
- A short manual end-to-end check using the existing `kli` CLI: open
  → create message → commit against a local broker, then inspect the
  `txn` collection to confirm the documents (`record_type=transaction`
  header with `state=committed` and a `commit_id`, plus one
  `record_type=message` document with kafka metadata). This is for my
  own verification — I will not require it to be reproducible by the
  director and I will skip it if the local stack is not available.

### Blockers / open questions for the director

1. **Topic pattern default.** I propose
   `jdtp-txn-.*|jdtp_cli_kli` so the existing `kli`-produced traffic
   on `jdtp_cli_kli` flows into the consumer without any docker
   change. The alternative is to leave the default as the strict
   design target `jdtp-txn-.*` and update the docker `kafka-init`
   sidecar to also pre-create `jdtp-txn-default`. The docker
   directory is **not** in TASK-003's owned paths, so I cannot do the
   second approach unilaterally. Confirm whether the
   "include `jdtp_cli_kli` in the default pattern" approach is
   acceptable, or expand scope to let me touch
   `docker/docker-compose.yml`.

2. **`spring-kafka` vs `reactor-kafka`.** I default to `spring-kafka`
   for the reasons in §3. `reactor-kafka` would let us stay
   non-blocking end-to-end (no `.block()` between listener and
   persistence service) but adds a dependency the project has never
   used and a steeper learning curve in tests. Confirm the choice
   before implementation.

3. **`commit_id` generator.** The task says transaction IDs are
   client-composed and "do not call `IdGenerator` for transaction
   IDs". I read that as scoped to `txn_id`. For the backend-generated,
   orderable `commit_id` I plan to call `IdGenerator.nextId()` (the
   same orderable IDs the legacy `TransactionService` uses for
   commit_seq). Please confirm; if the director wants a different
   commit-id strategy (e.g. a monotonic Mongo `findAndModify` counter
   or a UUIDv7), I will switch.

4. **Poison-pill behavior on parse/validate failure.** I propose
   ack-and-skip with a warning log. The alternative — fail-stop the
   consumer or DLQ — is more conservative but more code and not
   requested by acceptance criteria. Confirm ack-and-skip.

5. **Conflicting duplicate behavior.** When a Mongo `_id` collision
   carries a different payload than the existing record, I propose
   surfacing a `ConflictingDuplicateException` and leaving the record
   un-acked (so the listener container retries). The alternative —
   ack and only log — silently accepts data loss. Confirm error-out
   semantics.

6. **`txn/commit` arriving before `txn/open`.** I propose erroring
   out (the listener does not ack, retries kick in). The alternative
   — open the header lazily on commit — masks ordering bugs. Confirm
   error-out.

7. **`txn/rollback`.** Acceptance criteria do not mention rollback.
   I plan to log+ignore (return `ROLLBACK_NOT_PERSISTED`) rather than
   delete or mark the header. Confirm log-and-ignore is acceptable.

8. **Optional Kafka integration test under `integrationTest/`.**
   Should I include the env-gated Kafka integration spec described in
   §7, or defer all integration coverage until Testcontainers wiring
   is added in a later task?

STOPPING here per orchestrator pre-work protocol — no implementation,
no build/config/source/test edits, no doc edits beyond this file.
