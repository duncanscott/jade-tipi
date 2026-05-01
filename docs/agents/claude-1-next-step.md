# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-012 — Plan contents HTTP read integration coverage (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-012`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-012-contents-http-read-integration-prework.md`,
propose the narrowest opt-in end-to-end integration coverage that proves
canonical `loc + create`, `typ + create` (`kind: link_type`, `name: contents`),
and `lnk + create` messages can flow through Kafka ingestion (TASK-003 +
TASK-004), committed materialization (TASK-009), the contents read service
(TASK-010), and the thin HTTP adapter (TASK-011) without changing product
semantics or any out-of-scope surface.

The first integration unit must:

- Run only when explicitly opted in (matching the accepted TASK-004 gate)
  so default `./gradlew :jade-tipi:integrationTest` runs do not require
  Kafka/Keycloak.
- Stand up the project-documented Docker stack (Mongo, Kafka, Keycloak)
  using `docker compose -f docker/docker-compose.yml up -d` rather than
  introducing a new ecosystem-specific harness (Testcontainers,
  embedded Kafka, etc.).
- Publish a single canonical transaction (open + three data messages +
  commit) onto a per-spec Kafka topic whose name is unique per run, then
  poll Mongo for the materialized `lnk` row and exercise both
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` through an authenticated
  `WebTestClient` against a `RANDOM_PORT` Spring context.
- Stay strictly inside `TASK-012`'s `OWNED_PATHS` (one new spec under
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/`,
  optional new fixture under `jade-tipi/src/integrationTest/resources/`,
  no edits to existing services/controllers/materializers/listeners,
  no DTO/schema/example/build/Docker/security/frontend changes).
- Clean up only the documents and topics it created, by exact id, so
  repeated local runs do not depend on a globally empty database and do
  not collide with `TransactionMessageKafkaIngestIntegrationSpec` or
  `TransactionServiceIntegrationSpec`.

Director constraints to respect (from `OUT_OF_SCOPE` plus `TASK-012`'s
task file):

- Do not change `ContentsLinkReadService`, `ContentsLinkReadController`,
  `CommittedTransactionMaterializer`, `TransactionMessagePersistenceService`,
  `TransactionMessageListener`, materialized document semantics, DTO
  schemas, canonical message examples, Docker Compose, security policy,
  or frontend.
- Do not add endpoint joins to `loc` or `ent`, response envelopes,
  pagination, authorization/scoping policy, semantic write-time
  validation, update/delete replay, backfill jobs, or plate-shaped
  UI/API projections.
- Do not make existing integration tests depend on a globally clean
  database or an ungated Docker stack.

This is pre-work only. No production source, build, schema, example,
test, or non-doc edits beyond `docs/agents/claude-1-next-step.md` until
the director moves `TASK-012` to `READY_FOR_IMPLEMENTATION` (or sets the
global signal to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

Source-derived facts collected during pre-work; nothing below has been
edited.

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  is the controlling precedent for opt-in Kafka integration coverage:
  - Static `kafkaIntegrationGateOpen()` checked from `@IgnoreIf` so
    Spring context never loads when `JADETIPI_IT_KAFKA` is unset or the
    Kafka broker is unreachable (2-second `AdminClient.describeCluster`
    probe).
  - `@DynamicPropertySource` overrides `jadetipi.kafka.enabled=true`,
    rewrites `jadetipi.kafka.txn-topic-pattern` to a strict per-run
    regex, sets a unique `spring.kafka.consumer.group-id`, and shortens
    `metadata.max.age.ms` so pattern subscription discovers the topic
    within ~2s instead of 5 minutes.
  - Per-spec topic created in `@DynamicPropertySource` and deleted
    best-effort in `cleanupSpec` (each run uses a fresh `SHORT_UUID`).
  - Producer is `KafkaProducer<String, byte[]>` with `JsonMapper.toBytes`
    and key set to `txnId`.
  - Polling helpers `awaitMongo` / `awaitConditionTrue` block on
    `Mono` results with a deadline, returning the last value or
    surfacing the last error in an `AssertionError`.
  - Per-feature `cleanup()` removes only documents with the spec's
    `txn_id` so coexistence with other specs that write into `txn`
    is preserved.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/document/DocumentCreationIntegrationTest.groovy`
  is the controlling precedent for authenticated `WebTestClient`
  integration coverage:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` plus
    `@AutoConfigureWebTestClient` so the controller, security chain,
    and JSON pipeline are exercised end-to-end.
  - `KeycloakTestHelper.getAccessToken()` (client-credentials grant
    against `http://localhost:8484/realms/jade-tipi` with
    `tipi-cli` / a default secret overrideable via `TEST_CLIENT_ID`
    and `TEST_CLIENT_SECRET`) supplies the JWT bearer token.
  - Each request adds `Authorization: Bearer <token>` and uses the
    real `ReactiveJwtDecoder` against the Keycloak issuer-uri
    configured in `application-test.yml`.
  - `TestSecurityConfig` (the `@TestConfiguration` mock JWT decoder)
    is **not** imported by this spec; it is an opt-in mock used by
    pure controller specs and is not the integration-test default.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/config/KeycloakTestHelper.groovy`
  is the controlling helper. Caches the bearer token in a static field.
  No teardown is needed — Keycloak issues a short-lived token per run
  and `clearToken()` is available if a feature wants a fresh token.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/service/TransactionServiceIntegrationSpec.groovy`
  drops the entire `txn` collection in cleanup. The new TASK-012 spec
  must **not** drop collections (it would invert TASK-004's coexistence
  guarantee); it removes only the documents it created, by exact `_id`
  for `loc`/`typ`/`lnk` and by `txn_id` for `txn`.
- `jade-tipi/src/test/resources/application-test.yml` already configures
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` to the Keycloak
  realm and `spring.kafka.bootstrap-servers` to `localhost:9092`; the
  integration source set merges this onto the classpath through
  `gradle/scripts/integration-test.gradle` (`resources.srcDirs =
  ['src/integrationTest/resources', 'src/test/resources']`). **No
  edit to `application-test.yml` is required** for TASK-012; the spec
  overrides per-run Kafka properties through `@DynamicPropertySource`,
  exactly mirroring TASK-004.
- `docker/docker-compose.yml` already starts Mongo (`localhost:27017`),
  Keycloak (`localhost:8484`), and Kafka (`localhost:9092`) and creates
  the legacy `jdtp_cli_kli` topic via the `kafka-init` one-shot. Kafka
  has `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`, so a per-run topic
  must be created explicitly through `AdminClient` (mirroring
  TASK-004).
- `libraries/jade-tipi-dto/src/main/resources/example/message/{10,11,12}-create-*.json`
  carry fixed `txn.uuid` (`018fd849-2a40-7abc-8a45-111111111111`),
  fixed `uuid` per message, and fixed `data.id` strings (e.g.
  `jade-tipi-org~dev~018fd849-...~loc~freezer_a`). Reusing those JSON
  payloads verbatim across runs would collide on `_id` in `txn`,
  `loc`, `typ`, and `lnk` and would force either a global pre-clean
  or per-spec `dropCollection` — both forbidden by `TASK-012`'s
  "no globally clean database" rule. The proposal therefore constructs
  messages from DTO helpers with a per-run `SHORT_UUID` segment
  embedded in every id (S3 below). The bundled JSON examples remain
  the **shape and field-set source of truth** — the spec's payloads
  match them field-for-field — but the literal string values vary
  per run.
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Message.java`
  exposes `Message.newInstance(Transaction txn, Collection collection,
  Action action, Map<String, Object> data)` and assigns a fresh
  time-ordered UUID per message, matching how
  `TransactionMessageKafkaIngestIntegrationSpec` already constructs
  open/data/commit messages. `Transaction.newInstance(org, grp, client,
  user)` likewise generates a fresh transaction UUID and is used in
  the existing Kafka spec.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  hooks into `TransactionMessagePersistenceService.commitHeader(...)`
  via `materializeQuietly(txnId)` after the WAL update completes. Once
  the test sees `txn` header `state == 'committed'` with a non-blank
  `commit_id`, the materializer has been **invoked**; the spec must
  still poll `lnk` for the materialized row because materialization
  is a separate `Mono` chained after `updateFirst` and may complete
  microseconds-to-milliseconds after the header update.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
  is the accepted thin HTTP adapter from TASK-011. Forward route
  `GET /api/contents/by-container/{id}` delegates to
  `ContentsLinkReadService.findContents(id)`; reverse route
  `GET /api/contents/by-content/{id}` delegates to
  `findLocations(id)`. Both return `Flux<ContentsLinkRecord>` directly
  as a flat JSON array. **The spec exercises these routes verbatim
  through `WebTestClient`; it does not change them.**
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  resolves canonical `contents` link types by querying `typ` for
  `kind == "link_type" AND name == "contents"` and then filters
  `lnk.type_id` with the matching ids. The spec must therefore
  publish a `typ + create` message **with `data.kind: "link_type"`
  and `data.name: "contents"`** before (or in the same transaction
  as) the `lnk + create` message; otherwise the reader will return
  an empty `Flux` because no canonical type id exists yet.

### Smallest implementation plan (proposal)

#### S1. New integration spec — one file

New class
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
(Spring `@SpringBootTest`, Spock `Specification`):

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
  — needed because we exercise HTTP, unlike the Kafka spec which
  uses the default `MOCK` environment.
- `@AutoConfigureWebTestClient` — supplies the autowired
  `WebTestClient` bound to the random port and the real Spring
  Security chain.
- `@ActiveProfiles('test')` — picks up `application-test.yml` for
  Mongo, Kafka, and Keycloak issuer-uri.
- `@IgnoreIf({ !ContentsHttpReadIntegrationSpec.integrationGateOpen() })`
  — skips the entire spec (Spring context never loads) unless the
  three-part gate succeeds (S2).
- Reuses `KeycloakTestHelper.getAccessToken()` for the JWT bearer
  token, matching `DocumentCreationIntegrationTest`.
- Reuses TASK-004's `awaitMongo` / `awaitConditionTrue` polling
  helpers; either copied as private static methods or factored into
  a sibling test-helper class. **Default proposal: copy them as
  private static helpers**, because the existing TASK-004 spec also
  carries them inline and a shared helper class would add a new
  shared file and a refactor of the existing spec, which is broader
  scope than necessary. (Q1 below offers a shared helper as an
  alternative.)

#### S2. Three-part opt-in gate

The static `integrationGateOpen()` method, evaluated by `@IgnoreIf`
before the Spring context loads, fails fast and silently if any of
the following is not satisfied:

- **Env flag**: `JADETIPI_IT_KAFKA in ['1','true','TRUE','yes']`,
  reusing TASK-004's name so a single env var still gates both
  Kafka-touching specs. (Q2 below offers a separate
  `JADETIPI_IT_CONTENTS_HTTP` flag if the director prefers.)
- **Kafka reachability**: `AdminClient.describeCluster().clusterId()`
  resolves within 2 seconds against
  `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).
- **Keycloak reachability**: a 2-second HTTP HEAD/GET against
  `${KEYCLOAK_URL:http://localhost:8484}/realms/jade-tipi/.well-known/openid-configuration`
  returns `2xx`. Without this check, the spec would otherwise load
  the Spring context, fail at the `KeycloakTestHelper` token call,
  and surface a misleading error. (Q3 below offers "skip the
  Keycloak probe; rely on the Spring resource-server bean to fail
  loudly" as an alternative.)

The Mongo gate is **not** added — `@SpringBootTest` already requires
Mongo via `JadetipiApplicationTests.contextLoads`, and the project
already documents `docker compose -f docker/docker-compose.yml up -d`
as the prerequisite. A separate Mongo probe would duplicate that
contract.

If any gate fails, the spec is skipped via Spock's `@IgnoreIf`; the
Spring context is never loaded.

#### S3. Per-run identifiers, fixtures, and message construction

- `SHORT_UUID = UUID.randomUUID().toString().substring(0, 8)` —
  one short suffix per spec class instance, embedded in every
  Mongo `_id`, Kafka topic, and consumer group so repeated runs do
  not collide.
- Per-run identifiers (constructed inside the spec, **not** read
  from bundled example JSON):
  - `txnUuid` — fresh time-ordered UUID via
    `Transaction.newInstance('jade-itest-org','contents','jade-itest-cli','itest-user')`.
  - `txnId` — derived from `Transaction.getId()`.
  - `containerId = "jade-itest-org~contents~${txnUuid}~loc~plate-${SHORT_UUID}"`.
  - `freezerId   = "jade-itest-org~contents~${txnUuid}~loc~freezer-${SHORT_UUID}"`
    — optional second `loc` to confirm the read service ignores
    unrelated `loc` rows. (Q4 below offers "drop the freezer; one
    `loc` is enough" as an alternative.)
  - `contentsTypeId = "jade-itest-org~contents~${txnUuid}~typ~contents-${SHORT_UUID}"`
    — `typ + create` payload with `kind: "link_type"` and
    `name: "contents"`. The spec deliberately uses the `~typ~contents-<short>`
    suffix so `_id` is unique across runs while the canonical
    `name == "contents"` resolution still works (the read service
    filters by `name`, not `_id`).
  - `sampleId = "jade-itest-org~contents~${txnUuid}~ent~sample-${SHORT_UUID}"`
    — used as `lnk.right`. The spec does not publish an `ent +
    create` message because endpoint resolution is not part of
    the read path; the id is used verbatim as a string.
  - `linkId = "jade-itest-org~contents~${txnUuid}~lnk~plate-${SHORT_UUID}-sample"`
    — `lnk + create` payload with `type_id == contentsTypeId`,
    `left == containerId`, `right == sampleId`, and a
    `properties.position` matching the canonical
    `12-create-contents-link-plate-sample.json` example
    (`kind: "plate_well"`, `label: "A1"`, `row: "A"`, `column: 1`).
- **Fixture choice**: messages are constructed with
  `Message.newInstance(Transaction, Collection, Action, Map)` rather
  than loading bundled JSON.
  - **Justification**: the bundled examples are committed at the
    DTO layer to lock the canonical envelope shape and are validated
    by `MessageSpec` round-trip. Reusing them here would (a) collide
    on `_id` across runs, (b) tie integration coverage to a fixed
    transaction UUID that the DTO library may bump in a later
    canonical-vocabulary task, and (c) require either a global
    pre-clean or `dropCollection`, both forbidden. Building the
    same payload fields with DTO helpers preserves the canonical
    envelope shape (the `Message` constructor enforces it),
    surfaces breaking schema changes through compile/test failures
    in the DTO library rather than through a stale string fixture,
    and yields fresh ids per run.
  - The spec's payload fields are taken **verbatim** from the
    bundled examples (notably `properties.position` for the `lnk`
    message) so the exact plate-well shape that
    `12-create-contents-link-plate-sample.json` codifies is the
    same shape exercised end-to-end. A drift-detection assertion
    is **not** proposed here (the DTO library already round-trips
    the bundled JSON in `MessageSpec`). (Q5 below offers a
    JSON-loading alternative for the director if drift detection
    is preferred.)
- **Optional resource**: a small fixture file
  `jade-tipi/src/integrationTest/resources/contents/expected-position.json`
  could carry the expected plate-well sub-document for assertion
  reuse. The default proposal does **not** add this file because
  inline `Map` literals match the existing TASK-004 precedent and
  add fewer moving parts. (Q6 below.)

#### S4. Kafka topic, consumer group, and dynamic properties

Same shape as TASK-004:

- `TEST_TOPIC = "jdtp-txn-itest-contents-${SHORT_UUID}"` — created
  up-front in `@DynamicPropertySource` and best-effort deleted in
  `cleanupSpec`. Naming preserves the `jdtp-txn-` prefix used by
  the production listener pattern so the topic is also valid for
  the unrestricted production pattern (the per-spec property
  override below tightens it further).
- `CONSUMER_GROUP = "jadetipi-itest-contents-${SHORT_UUID}"` — fresh
  consumer group per run so the listener reads from offset 0
  deterministically.
- `@DynamicPropertySource`:
  - `jadetipi.kafka.enabled` → `'true'` (forces the listener to
    auto-start; the test profile sets this `false` by default).
  - `jadetipi.kafka.txn-topic-pattern` → the literal
    `TEST_TOPIC` string (so the listener subscribes only to this
    spec's topic and never to a sibling spec's topic running
    concurrently in a different module).
  - `spring.kafka.bootstrap-servers` → `BOOTSTRAP_SERVERS` from
    env or `localhost:9092`.
  - `spring.kafka.consumer.group-id` → `CONSUMER_GROUP`.
  - `spring.kafka.consumer.properties.metadata.max.age.ms` →
    `'2000'` (so pattern subscription discovers the topic within
    ~2s rather than the 5-minute default).

#### S5. Producer and message-publishing flow

Single `KafkaProducer<String, byte[]>` shared across features (mirrors
TASK-004), key=`txnId`, value=`JsonMapper.toBytes(message)`, ACKs
`all`, `producer.send(...).get(10s)` then `producer.flush()` per
record.

The spec publishes one canonical transaction, in order:

1. `Message.newInstance(txn, Collection.TRANSACTION, Action.OPEN, [hint: 'contents itest'])` —
   transaction header opened.
2. `Message.newInstance(txn, Collection.LOCATION, Action.CREATE, [
   id: containerId, name: 'plate-itest', description: 'sample container'
   ])` — first `loc` create.
3. **Optional second `loc`**: `Message.newInstance(txn,
   Collection.LOCATION, Action.CREATE, [id: freezerId, ...])` (Q4).
4. `Message.newInstance(txn, Collection.TYPE, Action.CREATE, [
   id: contentsTypeId,
   kind: 'link_type',
   name: 'contents',
   description: 'containment relationship between a container and its contents',
   left_role: 'container',
   right_role: 'content',
   left_to_right_label: 'contains',
   right_to_left_label: 'contained_by',
   allowed_left_collections: ['loc'],
   allowed_right_collections: ['loc','ent']
   ])` — `typ + create` for the canonical `contents` link type.
5. `Message.newInstance(txn, Collection.LINK, Action.CREATE, [
   id: linkId, type_id: contentsTypeId, left: containerId, right: sampleId,
   properties: [position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]]
   ])` — `lnk + create` for the concrete contents link.
6. `Message.newInstance(txn, Collection.TRANSACTION, Action.COMMIT, [
   summary: 'one location, one contents type, one contents link'
   ])` — commit. After commit,
   `TransactionMessagePersistenceService.commitHeader(...)` calls
   `materializeQuietly(txnId)`, which projects `loc`, `typ` (only
   the `link_type` row), and `lnk` records.

The exact `Collection` enum constants are
`Collection.LOCATION` (`loc`), `Collection.TYPE` (`typ`),
`Collection.LINK` (`lnk`), `Collection.TRANSACTION` (`txn`); names
are verified against the DTO source during implementation.

#### S6. Materialization wait

After the commit message is sent, the spec polls in this order:

1. **`txn` header committed** — `awaitMongo(findById(txnId,'txn'),
   { it?.state == 'committed' && it?.commit_id != null }, ...)`.
   This proves the WAL commit completed; materialization is invoked
   on the same chain after this point.
2. **`lnk` materialized** —
   `awaitMongo(findById(linkId, Map, 'lnk'),
   { it?.type_id == contentsTypeId && it?.left == containerId },
   '...')`. Polling continues until the `lnk` row is visible
   because `materializeQuietly` is chained after the commit
   `updateFirst` and may complete after step 1 returns.
3. **`typ` materialized** — `awaitMongo(findById(contentsTypeId,
   Map, 'typ'), { it?.name == 'contents' && it?.kind == 'link_type' }, '...')`.
   Required so the read service can resolve the canonical type id.

`AWAIT_TIMEOUT` = 30s, `POLL_INTERVAL` = 250ms (matching TASK-004);
exact values are tunable during implementation if Kafka subscription
warmup turns out to dominate. No `Thread.sleep` is added before
polling — the helpers already loop with a deadline.

#### S7. HTTP exercise

After steps S6.1–S6.3 succeed:

- **Forward route — happy path**:
  ```
  GET /api/contents/by-container/{containerId}
  Authorization: Bearer <token>
  ```
  Expect:
  - Status 200.
  - Content-Type `application/json`.
  - Body length 1.
  - `[0].linkId == linkId`.
  - `[0].typeId == contentsTypeId`.
  - `[0].left == containerId`.
  - `[0].right == sampleId`.
  - `[0].properties.position.kind == 'plate_well'`.
  - `[0].properties.position.label == 'A1'`.
  - `[0].properties.position.row == 'A'`.
  - `[0].properties.position.column == 1`.
  - `[0].provenance.txn_id == txnId`.
  - `[0].provenance.commit_id` present and non-blank.
  - `[0].provenance.msg_uuid == lnkMsg.uuid()`.
  - `[0].provenance.committed_at` present.
  - `[0].provenance.materialized_at` present.

- **Reverse route — happy path**:
  ```
  GET /api/contents/by-content/{sampleId}
  Authorization: Bearer <token>
  ```
  Same body assertions as forward (the same `lnk` row is the
  single match in the reverse direction).

- **Forward route — empty result**: a separate `GET /api/contents/by-container/${UUID}-not-a-real-container`
  returns 200 with body `[]`. This proves the controller does not
  short-circuit on unknown ids and stays read-only against `lnk`.
  (Q7 below.)

The spec uses the autowired `WebTestClient`; the
`Authorization` header is added per call, the JWT comes from
`KeycloakTestHelper.getAccessToken()` invoked in `setupSpec`. JSON
assertions use `expectBody().jsonPath(...)` consistent with
`DocumentCreationIntegrationTest`.

#### S8. Cleanup and isolation

`cleanupSpec()`:

- Close the producer (5s timeout).
- Best-effort delete `TEST_TOPIC` via `AdminClient.deleteTopics`
  (matching TASK-004; a leftover empty topic does not affect
  future runs because the next run uses a fresh `SHORT_UUID`).

`cleanup()` (per-feature):

- `mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)),'txn')`
  — same idiom as TASK-004 to remove the WAL header and message
  rows for **this run only**.
- `mongoTemplate.remove(Query.query(Criteria.where('_id').is(containerId)),'loc')`
  and `freezerId` (if used).
- `mongoTemplate.remove(Query.query(Criteria.where('_id').is(contentsTypeId)),'typ')`.
- `mongoTemplate.remove(Query.query(Criteria.where('_id').is(linkId)),'lnk')`.

No collection drop, no `txn_id` filter on `loc/typ/lnk` (those are
materialized documents and do not carry `txn_id` as a top-level
field; provenance lives under `_jt_provenance`). The exact `_id`
values come from the spec's `SHORT_UUID`-suffixed identifiers, so
two parallel runs (different processes) cannot collide.

No global Mongo dropCollection, no global Kafka topic delete. The
spec fully owns and removes its own state.

#### S9. Service / collaborator boundary

The spec's only autowired beans are `ReactiveMongoTemplate` (for
polling and cleanup) and `WebTestClient` (for HTTP). It does **not**:

- Autowire `ContentsLinkReadService`, `ContentsLinkReadController`,
  `CommittedTransactionMaterializer`,
  `CommittedTransactionReadService`,
  `TransactionMessagePersistenceService`,
  `TransactionMessageListener`, or any production bean. The whole
  point of the spec is to prove the wired-together graph works
  end-to-end through the HTTP boundary; adding direct collaborator
  references would dilute that.
- Re-implement, mock, or override any production bean. There is no
  `@MockBean`, no `TestConfiguration` import, no `@Primary` override.

#### S10. Verification commands

Pre-requisites (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — starts
  Mongo, Keycloak, Kafka, and the `kafka-init` one-shot. Required
  for the integration spec gates to open and for
  `JadetipiApplicationTests.contextLoads` (which any
  `:jade-tipi:test` run will invoke).
- Verify Keycloak readiness if the gate fails:
  `curl -fsS http://localhost:8484/realms/jade-tipi/.well-known/openid-configuration`.
- Verify Kafka readiness if the gate fails:
  `docker compose -f docker/docker-compose.yml logs kafka` and look
  for the broker startup line.

Targeted integration commands (in order):

- `./gradlew :jade-tipi:compileGroovy` — defensive main compile.
- `./gradlew :jade-tipi:compileTestGroovy` — defensive unit-test
  compile (the integration source set re-uses `src/test/resources`).
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — defensive
  integration-test compile; covers the new spec without running it.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`
  — opt-in run of the new spec only.
- `./gradlew :jade-tipi:test` — full unit-suite regression
  (requires Mongo per `JadetipiApplicationTests.contextLoads`); the
  unit suite is unchanged in scope but should still pass.
- (Optional regression of the existing Kafka spec)
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — proves the new spec did not break TASK-004 coexistence (the
  cleanup boundaries differ, and a regression here would be the
  earliest signal).

`./gradlew :libraries:jade-tipi-dto:test` is **not** required —
the DTO library is unchanged. `./gradlew integrationTest` (root
aggregate) is also not strictly required; the `:jade-tipi:integrationTest`
task is sufficient.

If verification fails because Mongo is not running, Kafka is not
running, Keycloak is not running, the Gradle wrapper lock is held,
the Gradle native dylib will not load, the toolchain is missing, or
`docker compose ps` reports an unhealthy service, I will report:

- the documented setup command
  (`docker compose -f docker/docker-compose.yml up -d` and, if
  needed, `docker compose -f docker/docker-compose.yml ps`),
- `./gradlew --stop` to clear stale daemons,
- the exact Gradle command that could not run,
- and the observed error message,

instead of treating any of those as a product blocker, per directive.

### Proposed file changes (all inside `TASK-012`-owned paths)

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`
  (new) — the opt-in integration spec described in S1–S9.
- (Optional, only if Q6 picks the JSON-fixture alternative)
  `jade-tipi/src/integrationTest/resources/contents/expected-position.json`
  — small fixture file. Default proposal does **not** create it.
- `docs/orchestrator/tasks/TASK-012-contents-http-read-integration-prework.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn, not during
  pre-work.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.
- `docs/agents/claude-1-next-step.md` — this pre-work file (the
  current edit).

No changes proposed to:

- `ContentsLinkReadController.groovy`, `ContentsLinkReadService.groovy`,
  `ContentsLinkRecord.groovy`, or their existing specs — read-side
  surfaces are frozen.
- `CommittedTransactionMaterializer.groovy`,
  `CommittedTransactionReadService.groovy`,
  `TransactionMessagePersistenceService.groovy`, or their specs —
  write/projection surfaces are frozen.
- `TransactionMessageListener.groovy`, `KafkaIngestProperties.groovy` —
  Kafka listener and its config are frozen.
- `libraries/jade-tipi-dto/src/main/...` — no DTO, schema, or
  example changes.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  — `loc`, `typ`, `lnk` collection creation is already enum-driven.
- `SecurityConfig.groovy`, `GlobalExceptionHandler.groovy` —
  security and error-handling surfaces are frozen.
- `application.yml`, `application-test.yml`, `build.gradle`,
  `docker/docker-compose.yml`, `DIRECTION.md`, frontend.
- `TransactionMessageKafkaIngestIntegrationSpec.groovy` (existing) —
  not refactored; helpers are duplicated rather than shared (Q1).

### Blockers / open questions for the director

1. **Q1 — Helper sharing.** Default proposal: copy the `awaitMongo`
   and `awaitConditionTrue` helpers as private static methods inside
   `ContentsHttpReadIntegrationSpec`, mirroring how TASK-004's spec
   carries them inline. **Alternative**: extract them into a new
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/config/IntegrationAwait.groovy`
   helper class and update both specs to use it. This adds one
   refactor of `TransactionMessageKafkaIngestIntegrationSpec`,
   which is currently outside `TASK-012`'s `OWNED_PATHS` umbrella
   `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/`.
   Confirm "duplicate inline," or pick "extract shared helper."

2. **Q2 — Gate flag name.** Default proposal: reuse
   `JADETIPI_IT_KAFKA` so a single env var still gates both
   Kafka-touching specs and operators do not need to remember a
   second name. **Alternative**: introduce `JADETIPI_IT_CONTENTS_HTTP`
   so the contents spec can be run independently and the existing
   Kafka spec is not implicitly opted in. Confirm "reuse
   `JADETIPI_IT_KAFKA`," or pick "new `JADETIPI_IT_CONTENTS_HTTP`."

3. **Q3 — Keycloak probe in the gate.** Default proposal: include
   a 2-second HTTP probe against
   `http://localhost:8484/realms/jade-tipi/.well-known/openid-configuration`
   so missing Keycloak surfaces as `@IgnoreIf` skip rather than as
   a token-acquisition `RuntimeException` mid-feature. **Alternative**:
   omit the Keycloak probe; rely on Spring's resource-server
   bean to fail loudly during context startup if the issuer-uri
   is unreachable, and rely on `KeycloakTestHelper` to fail loudly
   if the token endpoint is unreachable. Confirm "include Keycloak
   probe," or pick "omit Keycloak probe."

4. **Q4 — Second `loc` row.** Default proposal: publish two `loc +
   create` messages (a `freezer-...` plus the `plate-...` container)
   so the read service is exercised against more than one `loc`
   document and the assertion that the response only contains the
   `plate-...` container's contents is non-trivial. **Alternative**:
   publish only the `plate-...` container; one `loc` row is enough
   to materialize the link's `left` endpoint, and the read service's
   filtering is already covered by the unit spec from TASK-010.
   Confirm "two `loc` rows," or pick "one `loc` row."

5. **Q5 — Drift detection vs DTO helpers.** Default proposal: build
   messages with `Message.newInstance(...)` and inline `Map`
   payloads taken from the bundled JSON examples by hand. This is
   the simplest stable path and matches TASK-004's existing pattern.
   **Alternative A**: load the bundled
   `10-create-location.json`/`11-create-contents-type.json`/`12-create-contents-link-plate-sample.json`
   resources from the DTO library, parse them, swap in fresh ids
   per run, and republish. This drift-detects shape changes in the
   DTO examples but adds JSON parsing, id rewriting, and a
   classpath-loading dependency on the DTO library's resource
   layout. **Alternative B**: load the bundled JSON examples
   verbatim (no id swap) and clean up the documents they create.
   Rejected because their fixed `data.id` and `txn.uuid` strings
   would conflict on `_id` if any other test or developer run
   leaves the same ids in the database. Confirm "DTO helpers with
   inline `Map` payloads," or pick **A** or **B**.

6. **Q6 — Inline `Map` literals vs JSON resource for assertion
   shape.** Default proposal: keep the expected `properties.position`
   assertion inline in the spec (`'$.[0].properties.position.label'.isEqualTo('A1')`,
   etc.). **Alternative**: add
   `jade-tipi/src/integrationTest/resources/contents/expected-position.json`
   as a separate fixture file that the spec loads and compares
   field-by-field. Adds one resource file in the owned-paths
   resources umbrella but does not currently add reuse value (no
   second consumer). Confirm "inline literals," or pick "add
   fixture resource."

7. **Q7 — Empty-result assertion in the integration spec.**
   Default proposal: include one extra `WebTestClient` exchange
   asserting that `GET /api/contents/by-container/${non-existent-id}`
   returns 200 with body `[]`. The unit-level controller spec
   already asserts this against a stubbed service; including it at
   the integration level proves the empty-array success contract
   holds end-to-end. **Alternative**: drop this case because the
   controller spec already covers it. Confirm "include empty-result
   assertion," or pick "drop empty-result assertion."

8. **Q8 — Forward + reverse coverage.** Default proposal: assert
   both `GET /api/contents/by-container/{id}` and
   `GET /api/contents/by-content/{id}` against the same materialized
   `lnk` row. They share the same Spring context, the same Kafka
   topic, the same materialized state, and the same JSON shape; one
   feature method asserting both keeps wall-clock cost low.
   **Alternative**: split into two feature methods or two specs.
   Confirm "single feature, both routes," or pick a split.

9. **Q9 — Topic naming.** Default proposal:
   `jdtp-txn-itest-contents-${SHORT_UUID}`, retaining the
   `jdtp-txn-` prefix so the topic name is also a member of the
   default production listener pattern (`jdtp-txn-.*|jdtp_cli_kli`).
   The spec then narrows the listener pattern via
   `@DynamicPropertySource` so only this topic is matched.
   **Alternative**: drop the prefix
   (`itest-contents-${SHORT_UUID}`) since the per-spec property
   override is what actually scopes the listener. Confirm "keep
   `jdtp-txn-` prefix," or pick "drop the prefix."

10. **Q10 — Concurrency assumption.** The default proposal makes
    no attempt to synchronize with concurrent integration runs in
    the same Mongo / Kafka / Keycloak instance: the per-run
    `SHORT_UUID` is the only isolation. Two specs running against
    the same broker will create two topics, two consumer groups,
    and two disjoint sets of Mongo `_id` values, so they should
    not collide. **Alternative**: also serialize integration tests
    with `@Stepwise` or a Gradle `--max-workers=1` flag. Rejected
    as the default because it adds a global serialization cost
    that is not needed for correctness. Confirm "rely on
    `SHORT_UUID` isolation," or pick "serialize integration runs."

11. **Q11 — Out-of-scope reaffirmation.** I am explicitly **not**
    touching: any DTO enum (`Collection`, `Action`, `Message`); the
    JSON schema (`message.schema.json`); message examples; the
    Kafka listener, listener properties, or topic configuration;
    the `txn` write-ahead log shape; the committed-snapshot
    response shape (`CommittedTransactionSnapshot` /
    `CommittedTransactionMessage` / `KafkaProvenance`); the
    materializer or its result type; `ContentsLinkReadService`,
    `ContentsLinkRecord`, or their spec; `ContentsLinkReadController`
    or its spec; semantic reference validation; backfill jobs or
    update/delete replay; `parent_location_id`; build files;
    Docker Compose; security policy (`SecurityConfig`);
    `GlobalExceptionHandler`; or UI. Confirm that no piece of this
    `OUT_OF_SCOPE` block has shifted.

12. **Q12 — Verification expectation.** Default proposal: only the
    Gradle commands in S10 are required (compile, compile-test,
    compile-integration-test, focused integration spec, full unit
    suite, optional regression of the existing Kafka spec).
    **Alternative**: also run `./gradlew :jade-tipi:integrationTest`
    (the full integration aggregate) so any other integration
    spec in the module runs in the same shell. This is broader
    than necessary for TASK-012's scope but proves no integration
    regression. Confirm "focused integration command," or pick
    "full integration aggregate."

STOPPING here per orchestrator pre-work protocol — no implementation,
no source / build / config / schema / example / test / non-doc edits
beyond this file.
