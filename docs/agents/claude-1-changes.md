# claude-1 Changes

The developer writes completed work reports here.

STATUS: READY_FOR_REVIEW
TASK: TASK-006 — Add committed transaction snapshot HTTP read adapter
DATE: 2026-04-30
SUMMARY: New thin WebFlux read adapter `CommittedTransactionReadController`
exposes `GET /api/transactions/{id}/snapshot`, delegates committed
visibility entirely to `CommittedTransactionReadService.findCommitted`,
maps a present snapshot to HTTP 200 and `Mono.empty()` to HTTP 404 (empty
body), and lets the service `Assert.hasText` plus `GlobalExceptionHandler`
produce the standard 400 `ErrorResponse` for blank/whitespace-only ids.
No service, DTO-package, or security changes. New
`CommittedTransactionReadControllerSpec` uses
`WebTestClient.bindToController` with the real `GlobalExceptionHandler`
advice and a registered `AuthenticationPrincipalArgumentResolver` (no
Spring context, no Mongo/Kafka/Keycloak) and exercises the actual route,
JSON body serialization, the 404 empty-body path, the 400
`ErrorResponse`, service delegation, and the absence of a Mongo
collaborator.
VERIFICATION: docker compose stack healthy. `./gradlew
:jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`,
`./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`
(5/5 features green), `./gradlew :jade-tipi:test` (full unit suite,
0 failures, 0 errors), and `./gradlew :jade-tipi:compileIntegrationTestGroovy`
all passed.

## TASK-006 — Add committed transaction snapshot HTTP read adapter

Director moved `TASK-006` to `READY_FOR_IMPLEMENTATION` on 2026-04-30 with
the implementation directives in
`docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`
(route `GET /api/transactions/{id}/snapshot`, return the existing
`CommittedTransactionSnapshot` service-boundary object directly with
default Jackson camelCase, delegate committed visibility entirely to
`CommittedTransactionReadService.findCommitted`, map empty service result
to HTTP 404 with no body, let blank/whitespace ids fall through the
service `Assert.hasText` to the `GlobalExceptionHandler` 400 path, mirror
existing controller/security patterns, and add narrow `WebTestClient`
controller coverage). Implementation done on 2026-04-30.

### Production change

- New `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`.
  - `@RestController`, `@RequestMapping('/api/transactions')`, `@Slf4j`.
  - One method: `Mono<ResponseEntity<CommittedTransactionSnapshot>>
    getSnapshot(@PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt)`
    on `@GetMapping('/{id}/snapshot')`.
  - Body is literally `readService.findCommitted(id) ...
    .map { snapshot -> ResponseEntity.ok(snapshot) }
    .defaultIfEmpty(ResponseEntity.notFound().build())`. No duplicated
    WAL gate, no inline blank-id check, no per-id authorization, no
    redaction, no pagination, no list endpoint, no Mongo collaborator.
  - Logs only the `id` and the message count.

### What did not change

- `CommittedTransactionReadService`, `CommittedTransactionSnapshot`,
  `CommittedTransactionMessage`, and `KafkaProvenance` are unchanged
  (no fields, annotations, or package moves). Per the TASK-005
  director decision the snapshot value objects stay in `service/`;
  this task did not relocate them to `dto/` or add snake_case
  annotations.
- `TransactionController`, `DocumentController`, `TransactionService`,
  `TransactionMessagePersistenceService`,
  `TransactionMessageListener`, `GlobalExceptionHandler`,
  `ErrorResponse`, `SecurityConfig`, `application.yml`,
  `build.gradle`, and the `txn` write-ahead log shape from TASK-003
  are unchanged.
- No new authentication, authorization, redaction, pagination, list,
  or bulk policy was added.

### New test coverage

New `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`.

- Pattern: `WebTestClient.bindToController(controller)` (no Spring
  context, no Mongo/Kafka/Keycloak), wired with the real
  `GlobalExceptionHandler` via `.controllerAdvice(...)` and a
  registered `AuthenticationPrincipalArgumentResolver` so the
  `@AuthenticationPrincipal Jwt jwt` parameter resolves to `null`
  without a security context. The
  `CommittedTransactionReadService` collaborator is `Mock(...)`-ed.
- Five Spock features, all green:
  1. Committed snapshot returns 200 with the JSON body (`txnId`,
     `commitId`, `state`, `openData.hint`, `commitData.reason`,
     `messages.length()=2`, and the first message's `msgUuid`,
     `collection`, `action`, `data.name`, `kafka.topic`,
     `kafka.partition`, `kafka.offset`, `kafka.timestampMs`) plus the
     ordering of the second message's `msgUuid`, exercising the
     actual route and Jackson serialization (incl. `Instant` round-trip
     via `JavaTimeModule`).
  2. Controller delegates exclusively to
     `CommittedTransactionReadService` for committed lookups
     (`1 * readService.findCommitted(TXN_ID) >> Mono.just(...)`,
     `0 * _`).
  3. Missing or non-committed service result returns HTTP 404 with an
     empty body (`expectBody().isEmpty()`).
  4. Whitespace-only id surfaces the service's `Assert.hasText`
     `IllegalArgumentException` as the standard 400 `ErrorResponse`
     through `GlobalExceptionHandler` (`status=400`,
     `error='Bad Request'`, `message='txnId must not be blank'`).
  5. The controller's only constructor argument is
     `CommittedTransactionReadService`, proving no direct Mongo
     collaborator at the type level.

### Verification

`docker compose -f docker/docker-compose.yml ps` showed mongodb, kafka,
and keycloak healthy. Then:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`
  — 5/5 features green (`tests=5 failures=0 errors=0` in the JUnit XML).
- `./gradlew :jade-tipi:test` — full unit suite green; aggregated 54
  tests across all `:jade-tipi` specs, 0 failures, 0 errors.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD
  SUCCESSFUL (no integration spec was added in this task).

### Files changed in this turn

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
  — new file, the WebFlux read adapter described above.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`
  — new file, the controller-level Spock spec described above.
- `docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`
  — STATUS flipped from `READY_FOR_IMPLEMENTATION` to `READY_FOR_REVIEW`,
  `LATEST_REPORT` rewritten to record the implementation outcome.
- `docs/agents/claude-1-changes.md` — this report.

STATUS: READY_FOR_REVIEW

## TASK-005 — Director-review fix: coerce raw Mongo timestamps to Instant

Director implementation review on 2026-04-30 returned `TASK-005` to
`READY_FOR_IMPLEMENTATION` with one blocking finding:
`CommittedTransactionReadService` cast raw Mongo `Map` timestamp fields
directly to `Instant`, but BSON dates read into raw maps may surface as
`java.util.Date`, so a real committed snapshot could fail with
`GroovyCastException` even though the mocked unit tests passed by
injecting `Instant` fixtures.

Resolved on 2026-04-30. `TASK-005` flipped back to `READY_FOR_REVIEW`.

### Fix

- `CommittedTransactionReadService` now routes header `opened_at`,
  header `committed_at`, and message `received_at` through a new
  `private static Instant toInstant(Object)` helper.
- The helper accepts `null` (returns `null`), `Instant` (returned as-is),
  and `java.util.Date` (`.toInstant()`); any other type raises
  `IllegalStateException` so a future schema change cannot silently
  degrade timestamps to `null`.
- The `_id` ASC sort, the committed-visibility gate (`record_type =
  transaction` + `state = committed` + non-blank `commit_id`), and the
  short-circuit on missing/open/old-shape headers are unchanged.
- No write-side change. `TransactionMessagePersistenceService` continues
  to set `Instant` values; the read service now tolerates whichever
  representation the Mongo driver surfaces when documents round-trip
  through a raw `Map`.

### New unit coverage

- New feature in `CommittedTransactionReadServiceSpec`:
  `'java.util.Date timestamps from raw Mongo documents are coerced to
  Instant'`. Seeds the header with `Date.from(Instant.parse(...))` for
  `opened_at` and `committed_at` and the message row with `Date.from(...)`
  for `received_at`, then asserts that `snapshot.openedAt`,
  `snapshot.committedAt`, and `snapshot.messages[0].receivedAt` are all
  `instanceof Instant` and equal the original `Instant` values.
- All previously-required features are retained: committed snapshot
  returns header + messages, open/uncommitted is hidden, missing or
  blank `commit_id` partial-write is hidden, missing header returns
  empty, older `TransactionService`-shape (no `record_type`) is
  hidden, blank/null/whitespace `txnId` raises
  `IllegalArgumentException`, null payload fields are tolerated, and
  the Mongo query is asserted to carry `Sort.by(ASC, '_id')` (proves
  the database is doing the sort).

### Files changed in this turn

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
  — added `import java.util.Date`, added `private static Instant
  toInstant(Object)`, and switched the three timestamp reads to use it.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`
  — added the `Date`-coercion feature.
- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
  — `STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` rewritten
  for the re-submission.

No write-side persistence, controller, DTO-package, integration-test,
build, or resource files changed. The `txn` write-ahead log shape from
`TASK-003` is preserved.

### Verification

Setup (project-documented; already up locally): `docker compose -f
docker/docker-compose.yml up -d`. Containers verified up:
`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`.

- `./gradlew :jade-tipi:test --tests
  '*CommittedTransactionReadServiceSpec*'` — BUILD SUCCESSFUL.
  Spec report: `tests=12, skipped=0, failures=0, errors=0` (was 11; +1
  new Date-coercion feature).
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 49 unit tests across:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (12) — was 11
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

If a future verification fails because Docker / Mongo is not running,
the documented setup command is `docker compose -f
docker/docker-compose.yml up -d` (only Mongo is strictly required for
the unit suite because `JadetipiApplicationTests.contextLoads` opens a
Mongo connection).

## TASK-005 — Add committed transaction snapshot read layer

Goal completed. The backend now has a Kafka-free and HTTP-free read-side
layer over the accepted `txn` write-ahead log (TASK-003 / TASK-004) that
returns a single committed transaction snapshot (header + staged messages)
without materializing into long-term collections. The transaction header
remains the authoritative committed-visibility marker; child message
stamping is still not required.

Task lifecycle: `TASK-005` flipped to `READY_FOR_REVIEW` per directive
(no `IMPLEMENTATION_COMPLETE` status used).

### As-built decisions (per pre-work + director directives)

- **Service boundary.** New `service/CommittedTransactionReadService` is a
  Kafka-free / HTTP-free `@Service` with one public method:
  `Mono<CommittedTransactionSnapshot> findCommitted(String txnId)`. It uses
  `ReactiveMongoTemplate` directly (no new dependency) and reuses the
  field-name and state constants exposed on
  `TransactionMessagePersistenceService` so the read and write shapes stay
  in lock-step.
- **No controller.** Per directive, no controller was added for TASK-005;
  service-level coverage is sufficient. A later HTTP task can wrap this
  service into a thin adapter.
- **Committed-visibility gate.** The service emits `Mono.empty()` unless
  the header has `record_type='transaction'`, `state='committed'`, and a
  non-blank `commit_id`. This rejects:
  - missing headers (no document at `_id == txnId`),
  - open / uncommitted headers,
  - committed-but-no-`commit_id` partial-write states (defense in depth;
    today's writer always sets both atomically),
  - the older `TransactionService` document shape (which has no
    `record_type`) coexisting in the same `txn` collection.
  In each rejected case the message-record lookup is short-circuited.
- **Snapshot DTO location.** Per directive, snapshot return classes live
  under `org.jadetipi.jadetipi.service`, not `dto/`. They are internal
  service-boundary types, not HTTP request/response DTOs:
  - `service/CommittedTransactionSnapshot` (`txnId`, `state`, `commitId`,
    `openedAt`, `committedAt`, `openData`, `commitData`, `messages`).
  - `service/CommittedTransactionMessage` (`msgUuid`, `collection`,
    `action`, `data`, `receivedAt`, `kafka`).
- **Read-side Kafka provenance.** Per directive, a service-local
  `service/KafkaProvenance` value object is used instead of reusing the
  write-side `kafka.KafkaSourceMetadata`. Same fields (`topic`,
  `partition`, `offset`, `timestampMs`) but the read service does not
  import the `kafka` package.
- **Deterministic message ordering.** The Mongo query for messages is
  `Criteria.where('record_type').is('message').and('txn_id').is(txnId)`
  with `Sort.by(ASC, '_id')`. Because `_id = txn_id~msg_uuid` and `txn_id`
  is constant within a result set, this collapses to ordered-by-`msg_uuid`
  and uses the implicit `_id` index. The unit spec asserts the issued
  `Query.sortObject == new Document('_id', 1)` per directive (proves the
  database is doing the sort) and verifies the snapshot preserves the
  Mongo-returned order regardless of the mocked `Flux` order.
- **Input validation.** `Assert.hasText` on `txnId` matches the existing
  `TransactionService` convention; blank/null/whitespace inputs raise
  `IllegalArgumentException` and never call Mongo.
- **Null tolerance.** Header timestamps, `open_data`, `commit_data`,
  message `data`, `received_at`, and the `kafka` sub-doc may all be
  absent on real records; the snapshot returns them as `null` without
  NPE.
- **No production-code change to existing files.**
  `TransactionMessagePersistenceService` (writer),
  `TransactionMessageListener`, `TransactionService`, controllers,
  `application.yml`, and `build.gradle` are unchanged. The directive
  required preserving the `TASK-003` write-ahead log shape.

### Files added

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionSnapshot.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMessage.groovy`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/KafkaProvenance.groovy`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`
  (8 features, 11 test rows once the data-driven `where:` block is
  expanded for blank/null/whitespace `txnId`).

### Files updated (in scope)

- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
  — `STATUS` flipped to `READY_FOR_REVIEW`; `LATEST_REPORT` updated with
  the as-built shape and verification.

### Verification

Setup (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — Mongo + Kafka +
  Keycloak. Verified containers were already healthy from prior work
  (`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`).

Compilation:

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL
  (cross-source-set sanity check; no integration spec added or required).

Targeted unit run:

- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`
  — BUILD SUCCESSFUL. New spec: `tests=11, skipped=0, failures=0,
  errors=0`.

Regression check on the full unit suite:

- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. Now 48 tests pass:
  - `JadetipiApplicationTests` (1)
  - `TransactionMessageListenerSpec` (4)
  - `DocumentServiceMongoDbImplSpec` (9)
  - `CommittedTransactionReadServiceSpec` (11) — new
  - `TransactionMessagePersistenceServiceSpec` (11)
  - `TransactionServiceSpec` (12)

No integration spec is added for TASK-005. The TASK-004 integration spec
already proves the Kafka writer path produces the documents this read
service projects, and the read service has no Kafka or HTTP dependency
of its own.

### Out-of-scope items not implemented (per directive)

- No materialization into `ent`, `ppy`, `typ`, `lnk`, or other long-term
  collections.
- No semantic reference validation between properties / types / entities /
  assignments.
- No HTTP submission wrapper or read controller; service-level coverage
  is sufficient for `TASK-005`.
- No change to Kafka ingestion, topic configuration, or message envelope
  semantics. The `txn` write-ahead log shape from `TASK-003` is preserved.
- No new auth/authz policy.

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
