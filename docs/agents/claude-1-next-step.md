# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-016 — Plan root-shaped contents HTTP integration coverage (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-016`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-016-root-shaped-contents-http-integration.md`,
plan one narrow opt-in integration spec that proves a single canonical
contents transaction can flow through Kafka ingestion, the accepted
root-shaped committed materialization (`TASK-014`), and the contents HTTP
read routes hardened against root-shaped `typ`/`lnk` documents (`TASK-015`).
The paused `TASK-012` plan is historical context only — the materialized
shape it targeted no longer exists, so it must be rewritten, not implemented
as-is.

Scope of this pre-work: one Spring Boot integration spec under
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/`,
gated by `JADETIPI_IT_KAFKA` plus a new Keycloak readiness probe, using the
project-documented Docker stack, per-run Kafka topic/consumer-group, per-run
`txn_id`, and per-run materialized `loc`/`typ`/`lnk` ids. No production
behavior change; no broad documentation refresh; no fixture file additions
unless source inspection later proves one is required (it does not).

Out of scope for this task (per `TASK-016` `OUT_OF_SCOPE` and `DIRECTIVES.md`):
read service/controller/materializer semantics, persistence service,
Kafka listener behavior, DTO schemas/canonical examples, Docker Compose,
Gradle files, security policy, frontend, response envelopes, pagination,
endpoint joins to `loc`/`ent`, authorization/scoping policy, semantic
write-time validation, update/delete replay, backfill jobs, plate-shaped
UI/API projections, refactors of `TransactionMessageKafkaIngestIntegrationSpec`,
and any global cleanup of the test database.

### Source inspection (current accepted state)

- **Existing Kafka integration pattern.**
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  is the accepted opt-in template:
  - `@SpringBootTest` + `@ActiveProfiles('test')` + class-level
    `@IgnoreIf({ !TransactionMessageKafkaIngestIntegrationSpec.kafkaIntegrationGateOpen() })`
    so the Spring context is never loaded when the gate is closed.
  - `kafkaIntegrationGateOpen()` checks `JADETIPI_IT_KAFKA in ['1','true','TRUE','yes']`
    plus a 2-second `AdminClient.describeCluster` probe against
    `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).
  - `@DynamicPropertySource overrideProperties(...)` flips
    `jadetipi.kafka.enabled=true`, narrows the listener pattern to a
    per-run topic, sets a unique consumer group, shortens
    `metadata.max.age.ms` to 2s, and creates the topic up front.
  - Per-run `SHORT_UUID = UUID.randomUUID().toString().substring(0, 8)`,
    `TEST_TOPIC = "jdtp-txn-itest-${SHORT_UUID}"`,
    `CONSUMER_GROUP = "jadetipi-itest-${SHORT_UUID}"`. Topic created in
    `@DynamicPropertySource` and deleted best-effort in `cleanupSpec()`.
  - Per-feature `txn = Transaction.newInstance(...)` plus a `cleanup()`
    method that deletes only this run's `txn_id` rows from `txn`. No
    global database cleanup.
  - Private `awaitMongo(Supplier<Mono<T>>, Predicate<T>, String)` helper
    with `AWAIT_TIMEOUT = 30s`, `POLL_INTERVAL = 250ms`,
    `MONGO_BLOCK_TIMEOUT = 5s`.
- **Auth helper.**
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/config/KeycloakTestHelper.groovy`
  obtains a bearer via Keycloak `client_credentials` grant against
  `http://localhost:8484/realms/jade-tipi` with caching. Existing
  `DocumentCreationIntegrationTest` calls it from `@BeforeAll` and uses
  `webTestClient.get().uri(...).header("Authorization", "Bearer ${accessToken}")`.
  The `test` profile sets `oauth2.resourceserver.jwt.issuer-uri` to
  Keycloak; `TestSecurityConfig` is a `@TestConfiguration` (only loaded
  when explicitly imported), so the integration context defers to the
  real Keycloak issuer. We will reuse the helper rather than mocking
  the decoder, mirroring the accepted Document integration tests.
- **Materializer (TASK-014 accepted).**
  `CommittedTransactionMaterializer` writes root-shaped documents for
  `loc + create`, `typ + create` with `data.kind == "link_type"`, and
  `lnk + create`. Each root has top-level `_id == id == data.id`,
  `collection`, `type_id`, an inline `properties` map (`data` minus `id`
  and `type_id` for non-`lnk` roots; `data.properties` verbatim for
  `lnk`), `links: {}`, and a reserved
  `_head: { schema_version: 1, document_kind: 'root', root_id, provenance: { txn_id, commit_id, msg_uuid, collection, action, committed_at, materialized_at } }`.
  No top-level `_jt_provenance` is written for new roots. Materialization
  runs on the post-commit hook in `TransactionMessagePersistenceService`,
  so committing the canonical transaction publishes the root-shaped rows
  asynchronously.
- **Contents read service + controller (TASK-015 accepted).**
  `ContentsLinkReadService.resolveContentsTypeIds()` queries `typ` for
  `properties.kind == 'link_type'` and `properties.name == 'contents'`,
  then filters `lnk.type_id $in <typeIds>` and `lnk.<endpointField> == id`,
  sorted by `_id` ASC. `toRecord(...)` reads top-level `type_id`,
  `left`, `right`, and `properties` from the `lnk` root, and provenance
  from `_head.provenance` with a narrow legacy `_jt_provenance` fallback.
  `ContentsLinkReadController` exposes
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}`, both returning a flat
  `Flux<ContentsLinkRecord>` (`linkId`, `typeId`, `left`, `right`,
  `properties`, `provenance`) over JSON. Empty results are HTTP 200 `[]`;
  blank ids are 400 `ErrorResponse` via the global handler. Both routes
  require an authenticated `Jwt`.
- **Canonical message examples** (`libraries/jade-tipi-dto/src/main/resources/example/message/`):
  - `10-create-location.json` — `collection: 'loc'`, `data.id`
    `jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_a`,
    plus `name`/`description`.
  - `11-create-contents-type.json` — `collection: 'typ'`,
    `data.kind: 'link_type'`, `data.id`
    `jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents`,
    `data.name: 'contents'`, plus the six declarative facts
    (`left_role`, `right_role`, `left_to_right_label`,
    `right_to_left_label`, `allowed_left_collections`,
    `allowed_right_collections`).
  - `12-create-contents-link-plate-sample.json` — `collection: 'lnk'`,
    `data.id`
    `jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1`,
    `data.type_id` matching the contents `typ`, `data.left` a `loc` id,
    `data.right` an `ent` id, `data.properties.position` =
    `{kind: 'plate_well', label: 'A1', row: 'A', column: 1}`. (Note:
    `data.right` references an `ent` that is not created in this
    transaction; semantic resolution is out of scope for both the
    materializer and the contents read path, so this is fine.)
- **Docker Compose service names.** `docker/docker-compose.yml` ships
  `mongodb` (`localhost:27017`), `keycloak` (`localhost:8484`, with a
  health probe), `kafka` (`localhost:9092`, brought up after Keycloak
  is healthy), and `kafka-init` (creates the global `jdtp_cli_kli`
  topic). The new spec only needs `mongodb` + `keycloak` + `kafka`
  reachable; the global topic is irrelevant because the spec uses its
  own per-run `jdtp-txn-itest-…` topic.
- **Integration-test Gradle wiring.**
  `gradle/scripts/integration-test.gradle` creates the `integrationTest`
  source set with `resources.srcDirs = ['src/integrationTest/resources',
  'src/test/resources']`, so `application-test.yml` already participates
  in integration tests. The Groovy plugin auto-includes
  `src/integrationTest/groovy`. The `integrationTest` task uses JUnit
  Platform; Spock + Spock-Spring are available transitively via the
  unit `test` configuration. No build wiring change is needed.

### Proposed plan (smallest viable opt-in spec)

#### File and shape

Add one file:

```
jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy
```

Shape:

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
- `@AutoConfigureWebTestClient`
- `@ActiveProfiles('test')`
- Class-level `@IgnoreIf({ !ContentsHttpReadIntegrationSpec.integrationGateOpen() })`
- Extends `Specification`
- One `@Autowired ReactiveMongoTemplate mongoTemplate`
- One `@Autowired WebTestClient webTestClient`
- Per-run constants identical in spirit to `TransactionMessageKafkaIngestIntegrationSpec`:
  `SHORT_UUID = UUID.randomUUID().toString().substring(0, 8)`,
  `TEST_TOPIC = "jdtp-txn-itest-contents-${SHORT_UUID}"`,
  `CONSUMER_GROUP = "jadetipi-itest-contents-${SHORT_UUID}"`.
- `@DynamicPropertySource overrideProperties(...)` mirrors the accepted
  spec: enable the listener, narrow the topic pattern to `TEST_TOPIC`,
  set a unique consumer group, shorten `metadata.max.age.ms` to `2000`,
  and call a private `ensureTestTopic()` helper.

The spec stays Kafka-driven (no HTTP submission rebuild) because Kafka
ingest is already the only accepted submission path and `TASK-016`
explicitly says "publish or otherwise submit" — Kafka publish is the
existing accepted submission and matches `TASK-012`'s accepted direction.

#### Gates

`integrationGateOpen()` is a single static method that runs **before**
Spring loads (via `@IgnoreIf`):

1. Env flag: `JADETIPI_IT_KAFKA in ['1','true','TRUE','yes']` (re-uses
   the existing flag — no new env var).
2. Kafka readiness: same 2-second `AdminClient.describeCluster` probe
   against `KAFKA_BOOTSTRAP_SERVERS` as the accepted spec.
3. Keycloak readiness: a new 2-second probe via `WebClient`/`HttpURLConnection`
   to `KEYCLOAK_URL/realms/jade-tipi/.well-known/openid-configuration`
   (default `http://localhost:8484`, env override
   `KEYCLOAK_URL`/`TEST_KEYCLOAK_URL`). If the probe fails, the spec is
   skipped — Spring would otherwise fail to start because the JWT
   resource server resolves the issuer at startup.

The Keycloak probe is local to this spec and does not refactor the
accepted Kafka spec or `KeycloakTestHelper`.

#### Per-run isolation

- **Kafka topic/group:** per-run `TEST_TOPIC` and `CONSUMER_GROUP`,
  identical to the accepted pattern.
- **Transaction id:** `txn = Transaction.newInstance('jade-itest-org', 'kafka', 'jade-itest-cli', 'itest-user')` per feature; `txnId = txn.id`.
- **Materialized object ids:** built from the per-run `txn` id so they
  are unique per run:
  - `containerId = "jade-itest-org~kafka~${txnId}~loc~plate_${SHORT_UUID}"`
  - `typeId = "jade-itest-org~kafka~${txnId}~typ~contents"`
  - `linkId = "jade-itest-org~kafka~${txnId}~lnk~plate_${SHORT_UUID}_well_A1"`
  - `contentId = "jade-itest-org~kafka~${txnId}~ent~sample_${SHORT_UUID}"`
    (string only — `ent` is intentionally not materialized in this task,
    matching the canonical example `12`'s unresolved-`right` shape).
- **MongoDB cleanup** in `cleanup()` per feature, scoped strictly to
  the per-run ids:
  - `txn` rows by `txn_id == txnId`.
  - `loc` rows by `_id in [containerId]`.
  - `typ` rows by `_id in [typeId]`.
  - `lnk` rows by `_id in [linkId]`.
  No collection drops, no global query. Each `remove(...)` is a single
  `Query` with the id criteria and is `.block(Duration.ofSeconds(10))`.

#### Message construction strategy

Construct each message with the DTO helper
`Message.newInstance(txn, JtpCollection.<X>, Action.<Y>, [...])` plus an
inline `Map` payload that mirrors the canonical `10`/`11`/`12` field
shapes. **Do not load the bundled JSON examples verbatim** — the
canonical files use a fixed `txn` UUID and fixed object ids, which
cannot coexist with per-run isolation in `txn`/`loc`/`typ`/`lnk`. This
matches the prior accepted `TASK-012` direction and the accepted
pattern in `TransactionMessageKafkaIngestIntegrationSpec`. No new
fixture file is required, and `OWNED_PATHS` for
`jade-tipi/src/integrationTest/resources/` will not be used in the
narrow plan; it is listed only because it might be needed if the
director later asks for a JSON fixture.

The transaction is exactly five messages, sent in this order via the
existing per-spec Kafka producer (`String` key = `txnId`, `byte[]`
value via `JsonMapper.toBytes(message)`):

1. `Action.OPEN` on `JtpCollection.TRANSACTION` with a small `hint`.
2. `Action.CREATE` on `JtpCollection.LOCATION`:
   `[id: containerId, name: "plate_${SHORT_UUID}", description: 'integration plate']`.
3. `Action.CREATE` on `JtpCollection.TYPE`:
   ```
   [kind: 'link_type',
    id: typeId,
    name: 'contents',
    description: 'containment relationship between a container location and its contents',
    left_role: 'container',
    right_role: 'content',
    left_to_right_label: 'contains',
    right_to_left_label: 'contained_by',
    allowed_left_collections: ['loc'],
    allowed_right_collections: ['loc', 'ent']]
   ```
4. `Action.CREATE` on `JtpCollection.LINK`:
   ```
   [id: linkId,
    type_id: typeId,
    left: containerId,
    right: contentId,
    properties: [position: [kind: 'plate_well', label: 'A1', row: 'A', column: 1]]]
   ```
5. `Action.COMMIT` on `JtpCollection.TRANSACTION` with a small `summary`.

#### Wait/assert sequence (one main feature)

Inside one `def 'forward and reverse contents HTTP routes return the materialized link'() { ... }`:

1. `send(openMsg); send(locMsg); send(typMsg); send(lnkMsg); send(commitMsg)`.
2. `awaitMongo(...)` for the committed `txn` header:
   condition: `header.state == 'committed' && header.commit_id != null`.
3. `awaitMongo(...)` for the materialized `typ` row by `_id == typeId`,
   asserting root-shape: `_id == typeId`, `id == typeId`,
   `collection == 'typ'`, `properties.kind == 'link_type'`,
   `properties.name == 'contents'`, `_head.provenance.txn_id == txnId`.
4. `awaitMongo(...)` for the materialized `lnk` row by `_id == linkId`,
   asserting root-shape: `_id == linkId`, `id == linkId`,
   `collection == 'lnk'`, top-level `type_id == typeId`,
   `left == containerId`, `right == contentId`,
   `properties.position.label == 'A1'`,
   `_head.provenance.txn_id == txnId`,
   `_head.provenance.materialized_at != null`.
5. Forward HTTP read:
   `webTestClient.get().uri('/api/contents/by-container/{id}', containerId)
       .header('Authorization', "Bearer ${KeycloakTestHelper.accessToken}")
       .exchange()
       .expectStatus().isOk()
       .expectBody()
       .jsonPath('$.length()').isEqualTo(1)
       .jsonPath('$[0].linkId').isEqualTo(linkId)
       .jsonPath('$[0].typeId').isEqualTo(typeId)
       .jsonPath('$[0].left').isEqualTo(containerId)
       .jsonPath('$[0].right').isEqualTo(contentId)
       .jsonPath('$[0].properties.position.label').isEqualTo('A1')
       .jsonPath('$[0].properties.position.row').isEqualTo('A')
       .jsonPath('$[0].properties.position.column').isEqualTo(1)
       .jsonPath('$[0].provenance.txn_id').isEqualTo(txnId)
       .jsonPath('$[0].provenance.commit_id').exists()
       .jsonPath('$[0].provenance.msg_uuid').isEqualTo(lnkMsg.uuid())`.
6. Reverse HTTP read against the same materialized link:
   `webTestClient.get().uri('/api/contents/by-content/{id}', contentId)
       ...
       .jsonPath('$[0].linkId').isEqualTo(linkId)
       .jsonPath('$[0].right').isEqualTo(contentId)`.

A second feature
`def 'empty-result contents HTTP route returns 200 with empty array'() { ... }`
covers the empty-result HTTP 200 `[]` requirement using a fresh
unrelated id (e.g. `"jade-itest-org~kafka~${txnId}~loc~empty_${SHORT_UUID}"`)
that is never written, against `/api/contents/by-container/{id}`. It
asserts `expectStatus().isOk()` and `jsonPath('$.length()').isEqualTo(0)`
and `expectBody().json('[]')` (or equivalent — `expectBodyList(...).hasSize(0)`).
This feature does not need to publish any Kafka messages, so it is a
fast path and stays inside the same spec to avoid duplicating the gate.

> Note: I will choose `expectBodyList(ContentsLinkRecord).hasSize(0)`
> for the empty case if WebTestClient deserialization through Spring's
> default Jackson works cleanly with `@Immutable Groovy`. If it
> doesn't, I will fall back to `.expectBody().json('[]')`. Either way
> the assertion is HTTP 200 + empty array.

Polling helpers (`awaitMongo`, `awaitConditionTrue`) are private to this
spec — the directive explicitly forbids refactoring
`TransactionMessageKafkaIngestIntegrationSpec`. I will copy the helpers
verbatim rather than extract a new shared helper class.

#### Negative coverage (intentionally limited)

The directive asks for two HTTP shapes (forward/reverse) plus the
empty-result `[]` case. I do **not** plan to add 400 blank-id coverage,
404 unknown-route coverage, unauthorized-token coverage, or
duplicate/conflict materialization coverage in this spec — those are
already covered by existing controller and materializer unit tests
(`ContentsLinkReadControllerSpec`, `CommittedTransactionMaterializerSpec`)
and adding them here would broaden scope without adding integration
signal.

### Verification proposal

After implementation, run the `TASK-016` required Gradle chain inside the
`developers/claude-1` worktree, with the documented Docker stack up:

1. `./gradlew :jade-tipi:compileGroovy`
2. `./gradlew :jade-tipi:compileTestGroovy`
3. `./gradlew :jade-tipi:compileIntegrationTestGroovy`
4. `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`
5. `./gradlew :jade-tipi:test`
6. (If time permits) `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
   to confirm coexistence with the accepted Kafka-ingest integration
   pattern (per-run topic and consumer group should isolate the runs).

If local tooling, Gradle locks, Docker, Kafka, Mongo, or Keycloak setup
blocks any of the above, report the documented setup commands rather
than treating setup as a product blocker:

- `docker compose -f docker/docker-compose.yml up -d` — brings up
  `mongodb`, `keycloak`, and `kafka` (Kafka `depends_on` Keycloak healthy,
  per `docker/docker-compose.yml`). The new spec gate covers the Kafka
  and Keycloak readiness checks; the `test` profile covers Mongo at
  `localhost:27017`.
- `./gradlew --stop` — drop stale Gradle daemons (e.g. when the wrapper
  cache lock at `/Users/duncanscott/.gradle/...` is held), then re-run
  the blocked command.
- Always report the exact blocked command and its error verbatim
  alongside the proposed setup command.

### Blockers and open questions

- **Q1 — `expectBodyList` vs `expectBody().json('[]')` for the empty
  case.** Recommendation: try `expectBodyList(ContentsLinkRecord).hasSize(0)`
  first because it exercises the deserialization shape, and fall back
  to `expectBody().json('[]')` if Groovy `@Immutable` plus Jackson
  default mapping needs hand-tuning. Either form satisfies the
  directive's "HTTP 200 `[]`" requirement. Flagged so the director can
  veto the deserializer-touching variant if they prefer the pure JSON
  shape assertion.
- **Q2 — Unresolved `right` endpoint (`ent` id never materialized).**
  Canonical example `12` already references an `ent` that no
  transaction creates in this task. The materializer and contents read
  service intentionally do not validate semantic resolution, so the
  reverse-route assertion against a never-materialized `contentId`
  exercises the documented "verbatim id passthrough" contract. No
  change requested; flagged so a strict reviewer sees this is by
  design and matches the canonical example shape.
- **Q3 — `application-test.yml` JWT issuer.** The `test` profile
  points the resource server at `http://localhost:8484/realms/jade-tipi`,
  which is exactly what the new Keycloak readiness probe checks. No
  edit to `application-test.yml` is planned. The directive lists
  `jade-tipi/src/test/resources/application-test.yml` in
  `OWNED_PATHS`, but I do not currently see a reason to modify it.
  Flagged so the director can confirm "no edit" is acceptable.
- **Q4 — Keycloak probe location.** Plan is to keep the probe inline in
  the new spec (small static method, no new shared helper), to avoid
  refactoring `KeycloakTestHelper`. If the director prefers to extend
  `KeycloakTestHelper` with a `static boolean isReachable()` method, I
  can do that instead, but it adds a touch outside the new spec file
  and the directive explicitly says "do not refactor existing
  integration tests". Recommendation: keep inline.
- **Q5 — Token caching across features.** `KeycloakTestHelper` caches
  the bearer in a static field. The cached value is reused across
  features inside this spec and across other integration tests in the
  same JVM run, which is fine because the token is valid for an hour.
  No change requested.
- No external blockers identified. Implementation is a single new
  Groovy spec file plus per-run cleanup, mirroring an accepted pattern;
  it should land cleanly once `TASK-016` moves to
  `READY_FOR_IMPLEMENTATION`.

STOP.
