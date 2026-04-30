# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-004 — Add Kafka transaction ingest integration coverage (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`) and
`docs/orchestrator/tasks/TASK-004-kafka-transaction-ingest-integration-test.md`
(status `READY_FOR_PREWORK`), add a practical integration test that
exercises the accepted Kafka-first transaction ingestion path
end-to-end:

- Publish canonical `org.jadetipi.dto.message.Message` records (a
  transaction `open`, one data message, and a `commit`) to Kafka.
- Let the existing `TransactionMessageListener` consume them.
- Assert the resulting `txn` collection in MongoDB contains the
  expected header (`record_type=transaction`, `state=committed`,
  `commit_id` populated) plus one message document
  (`record_type=message`, `_id = txnId~msgUuid`).

Director constraints to respect:

- Prefer the documented Docker Compose services and the existing
  Gradle `integrationTest` wiring over adding Testcontainers, unless
  pre-work shows the documented setup cannot make the test reliable.
- Kafka auto-topic creation is **disabled** in
  `docker/docker-compose.yml` (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`).
  Pre-work must decide whether to use the existing `jdtp_cli_kli` topic
  or create a per-test topic via a documented/admin-client path.
- The test must be opt-in or environment-gated when the Docker stack
  is not available, with the skip/run conditions explicit in the test.
- Do not change production Kafka ingestion behavior unless the test
  exposes a real bug in the accepted implementation.

This is pre-work only. No backend, build, test, or doc edits beyond
this file until the director moves `TASK-004` to
`READY_FOR_IMPLEMENTATION` (or sets the global signal to
`PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `jade-tipi/build.gradle` already pulls `org.springframework.kafka:spring-kafka`
  (added in TASK-003), which transitively brings `org.apache.kafka:kafka-clients`
  on the test classpath. There is no `spring-kafka-test`, no Testcontainers,
  and no `awaitility` dependency.
- `gradle/scripts/integration-test.gradle` is applied via
  `spring-boot.gradle` and registers an `integrationTest` task with the
  source set rooted at `src/integrationTest/`. The Groovy plugin is
  applied at the project level, so `src/integrationTest/groovy` is a
  recognized source dir even though the script only declares
  `java.srcDir 'src/integrationTest/java'` (existing
  `DocumentServiceIntegrationSpec` and `TransactionServiceIntegrationSpec`
  live under `src/integrationTest/groovy/...` and compile fine).
  `src/integrationTest/resources/` does not exist yet but is in scope.
- The `integrationTest` source set declares
  `resources.srcDirs = ['src/integrationTest/resources', 'src/test/resources']`,
  so `src/test/resources/application-test.yml` is on the classpath of
  integration tests by default.
- `src/test/resources/application-test.yml` currently sets
  `jadetipi.kafka.enabled: false` so `JadetipiApplicationTests.contextLoads`
  does not start a Kafka listener. The integration test must override
  this to `true` so the listener actually consumes.
- `KafkaIngestProperties.txnTopicPattern` defaults to
  `jdtp-txn-.*|jdtp_cli_kli`. The default pattern already matches both
  the design topic family and the docker-pre-created `jdtp_cli_kli`
  topic.
- The listener consumer-group default (`jadetipi-txn-ingest`) and
  `auto-offset-reset=earliest` mean a fresh group will read all
  retained records on the topic. To prevent test-to-test offset
  carry-over and to stay isolated from the running dev backend (if any),
  the test must override `spring.kafka.consumer.group-id` to a unique
  value per run.
- `JadetipiApplicationTests.contextLoads` already starts a full
  `@SpringBootTest` context against MongoDB on `localhost:27017`. The
  same context-load mechanism is what the new integration spec will
  reuse.
- The existing integration specs (`TransactionServiceIntegrationSpec`,
  `DocumentServiceIntegrationSpec`, `DocumentCreationIntegrationTest`)
  all rely on the docker Mongo and Keycloak stack being up. None of
  them currently exercise Kafka.
- `clients/kafka-kli` publishes to `jdtp_cli_kli` and is the only
  in-repo Kafka producer. The integration test will produce directly
  via `kafka-clients` (`KafkaProducer`) rather than depend on the kli
  binary.

### Director decision points (proposal + rationale)

The task explicitly asks pre-work to decide each of these. I am
proposing a default for each and listing the alternative for the
director to override before implementation.

#### D1. Topic strategy — proposal: per-test topic via AdminClient

**Proposal.** Create a unique per-test topic at the start of each spec
using `org.apache.kafka.clients.admin.AdminClient.createTopics(...)`
with name `jdtp-txn-itest-${shortUuid}` (1 partition, 1 replica), and
delete it in `cleanupSpec` via `AdminClient.deleteTopics(...)`. The
default `txnTopicPattern` (`jdtp-txn-.*|jdtp_cli_kli`) already matches
this naming, so the listener subscribes without any property override.

**Why over `jdtp_cli_kli`.**

- Test isolation: the existing `jdtp_cli_kli` topic is shared with the
  `kli` CLI. Records sitting there from prior dev usage would be
  re-consumed on every test run (because `auto-offset-reset=earliest`
  with a fresh group), producing unbounded extra documents in `txn`
  and noisy assertions.
- Hygiene: a per-test topic is dropped at the end of the spec, leaving
  no residue across runs and not interfering with manual `kli`
  experimentation.
- Consumer-group isolation: combined with a per-run group id (D3),
  every test run starts from a deterministic empty state.
- Documented path: AdminClient is part of `kafka-clients`, which is
  already on the test classpath via `spring-kafka`. No new dependency.

**Alternative.** Reuse `jdtp_cli_kli`. Cheaper (no AdminClient calls)
but couples the test to whatever else is on the topic. To make this
reliable I would need to consume-and-discard everything currently on
the topic before producing test messages, which is more code than the
AdminClient approach. I recommend declining this alternative unless
the director wants zero changes outside of property overrides.

#### D2. Test gating — proposal: env-flag plus fast broker probe

**Proposal.** Gate the new spec with Spock's `@IgnoreIf` (or a JUnit 5
`@EnabledIfEnvironmentVariable`-equivalent on Spock via
`@Requires`) that checks two things:

1. Env var `JADETIPI_IT_KAFKA` is set to a truthy value (`1`, `true`).
2. A 2-second `AdminClient.describeCluster()` probe to
   `localhost:9092` (or `KAFKA_BOOTSTRAP_SERVERS` if set) succeeds.

If either check fails, the spec is skipped with a clear message
("Kafka integration test skipped: set JADETIPI_IT_KAFKA=1 and start
docker compose -f docker/docker-compose.yml up -d to run").

**Why both.** The env flag matches the project pattern of opt-in
heavy tests (consistent with how integration tests are already not run
by `./gradlew test`) and avoids surprising a developer who runs
`./gradlew check`. The broker probe protects against the case where
the env flag is set but the docker stack happens to be down — instead
of a 30s consumer-startup timeout we report skip immediately.

**Alternative A.** Skip the broker probe and rely on the env flag
alone. Simpler but loses the helpful skip-on-broker-down behavior.

**Alternative B.** Always run when docker is detected (no env flag).
Risks slowing down unrelated `./gradlew :jade-tipi:integrationTest`
runs and conflicts with the director's "opt-in or environment-gated"
acceptance criterion.

#### D3. Consumer-group strategy — proposal: per-run unique group id

**Proposal.** Use `@DynamicPropertySource` (or
`@TestPropertySource(properties = "spring.kafka.consumer.group-id=jadetipi-itest-${random.uuid}")`)
to set a fresh group id on every test run. With
`auto-offset-reset=earliest` (already the default) the listener will
read every record on the per-test topic from offset 0.

**Why.** A fixed group id would either (a) skip already-consumed
records on a re-run if the test left committed offsets (we use
`MANUAL_IMMEDIATE` ack so this is real), or (b) collide with a
running dev backend that uses the same group id and steal its
partitions.

#### D4. Wait strategy — proposal: bounded reactive polling on `txn`

**Proposal.** After producing the three records and `flush()`-ing the
producer, poll MongoDB for the expected header and message documents
using a small reactive helper:

```groovy
private static <T> T awaitNonNull(Mono<T> mono, Duration timeout) {
    return mono.repeatWhenEmpty { Flux.range(1, 60).delayElements(Duration.ofMillis(250)) }
            .blockOptional(timeout)
            .orElseThrow { new AssertionError("did not appear within ${timeout}") }
}
```

Used as `awaitNonNull(mongoTemplate.findById(txnId, Map, 'txn').filter { ... committed }, Duration.ofSeconds(20))`.

**Why this approach.**

- No new dependency. Awaitility would be cleaner but adds a library.
- The 20s ceiling is a generous upper bound on Kafka consumer startup
  + first-poll latency; in practice the listener container produces
  records within ~2–3s of test start.
- Per-record polling on `_id` lookup is cheap (single Mongo read).

**Alternative.** Add `org.awaitility:awaitility` to
`testImplementation` (also picked up by `integrationTestImplementation`
via `extendsFrom`). Cleaner DSL, but a new dep just for one spec.
Open question for the director.

#### D5. Producer wiring — proposal: raw `KafkaProducer<String, byte[]>`

**Proposal.** Construct a `KafkaProducer<String, byte[]>` directly in
the spec (with `StringSerializer` for keys and `ByteArraySerializer`
for values) and serialize each `Message` with the project's
`org.jadetipi.dto.util.JsonMapper.toBytes(...)`. Produce in order:
open → data → commit, all to the same partition (key set to the
`txn_id` so they hash to the same partition; the topic has a single
partition anyway).

**Why.** Mirrors what production producers will do (kli already uses
`KafkaProducer`). Avoids needing to import Spring's `KafkaTemplate` or
auto-configuring a producer factory at test time. No need for
`spring-kafka-test`'s embedded broker — we use the real docker broker.

**Alternative.** Use the already-auto-configured `KafkaTemplate`. Also
fine, but requires wiring (`spring.kafka.producer.*` properties) that
are not currently set.

#### D6. Cleanup — proposal: drop test topic + scoped Mongo cleanup

**Proposal.** In `cleanupSpec`:

1. Stop the consumer container for the test topic (or simply close it
   indirectly by ending the Spring context — the framework handles
   this).
2. Delete the per-test topic via `AdminClient.deleteTopics(...)`.
3. Drop only the documents created by the test from `txn`:
   `mongoTemplate.remove(Query.query(Criteria.where('txn_id').is(txnId)), 'txn').block()`,
   plus the header by `_id`.

**Why scoped.** Other integration specs (`TransactionServiceIntegrationSpec`)
also write to `txn` with their own ID format and would break if the
new spec dropped the whole collection. A targeted delete by `txn_id`
keeps them coexistable.

#### D7. Number of test cases — proposal: one happy-path spec, one structural sanity check

**Proposal.** Two `Specification` features in one new spec file:

1. **Happy path (required by acceptance criteria).** Produce open +
   data + commit to a per-test topic, await the `txn` header
   reaching `state=committed` with `commit_id` populated, then assert:
   - Header `_id` matches the canonical `txn_id` (`uuid~org~grp~client`).
   - Header `record_type == 'transaction'`, `state == 'committed'`,
     `opened_at` and `committed_at` present, `commit_id` non-null,
     `open_data` and `commit_data` round-tripped.
   - Exactly one message document with
     `_id == "${txnId}~${msgUuid}"`,
     `record_type == 'message'`,
     `collection == 'ppy'` (or whichever non-`txn` collection we pick),
     `kafka.topic`, `kafka.partition`, `kafka.offset`, and
     `kafka.timestamp_ms` populated.
2. **Idempotency sanity check (small extra confidence).** Re-publish
   the same data message a second time and assert the `txn` document
   count for that `txn_id` does not grow (still exactly one message
   document).

I propose deferring conflict-detection and rollback scenarios to unit
tests (already covered in `TransactionMessagePersistenceServiceSpec`)
to keep the integration test fast and focused.

### Proposed file changes (all inside expanded scope)

- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/TransactionMessageKafkaIngestIntegrationSpec.groovy`
  (new) — the spec described in D1–D7.
- `jade-tipi/src/integrationTest/resources/application-integration-test.yml`
  (new, optional) — sets `jadetipi.kafka.enabled: true` for the
  integration profile so the listener actually starts. If the director
  prefers `@TestPropertySource` over a profile file, I will use that
  instead and not add this file.
- `jade-tipi/src/integrationTest/resources/logback-test.xml`
  (new, optional) — quiet down `org.apache.kafka` and
  `org.springframework.kafka` to WARN so test output is readable. Only
  if logs are noisy enough to matter; otherwise drop.
- `jade-tipi/build.gradle` — only if D4-Alternative is chosen by the
  director: add `testImplementation 'org.awaitility:awaitility'`.
  Otherwise no change.
- `docs/orchestrator/tasks/TASK-004-kafka-transaction-ingest-integration-test.md`
  — update `LATEST_REPORT` and `STATUS` only at the end of the
  implementation turn.

No production code change is planned. `TransactionMessageListener`,
`TransactionMessagePersistenceService`, `KafkaIngestProperties`,
`KafkaIngestConfig`, and `application.yml` are left as-is unless the
test exposes a bug.

### Verification I plan to run after implementation

Pre-req setup (documented project commands):

- `docker compose -f docker/docker-compose.yml up -d` from the project
  checkout. Brings up `mongodb`, `keycloak`, `kafka`, and the
  `kafka-init` sidecar (which we do not depend on for this test, but
  it is part of the documented stack).

Targeted commands:

- `./gradlew :jade-tipi:compileGroovy` — must pass.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — must compile
  the new spec (verifies the integration source set picks up Groovy
  files, which existing specs already prove).
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`
  — runs only the new spec end-to-end.
- `./gradlew :jade-tipi:integrationTest` — full integration suite,
  including the new spec when the env flag is set, otherwise
  documented as skipped. I will run this at least once to confirm the
  spec coexists with the existing Mongo/Keycloak-backed integration
  specs.
- `./gradlew :jade-tipi:test` — must still pass (the new spec is in
  the integration source set, not the unit source set, so this is a
  regression check only).

If any of these fail because docker, the gradle wrapper, or the
keycloak/mongo containers are unavailable in my sandbox, I will report
the exact documented setup command rather than treating it as a
product blocker, per `DIRECTIVES.md`.

### Blockers / open questions for the director

1. **D1 — Topic strategy.** Confirm I should create a per-test topic
   via `AdminClient` (default proposal). Alternative: reuse
   `jdtp_cli_kli` and consume-and-discard pre-existing records first.
2. **D2 — Gating mechanism.** Confirm env var
   `JADETIPI_IT_KAFKA=1` plus a 2-second `AdminClient` probe is
   acceptable. Alternative names for the env var (e.g. `IT_KAFKA`,
   `JADETIPI_KAFKA_IT`) are fine — please name your preference.
3. **D3 — Consumer-group strategy.** Confirm a per-run unique group
   id (`jadetipi-itest-${uuid}`) is acceptable. The alternative is to
   keep the default `jadetipi-txn-ingest` and rely solely on the
   per-test topic for isolation.
4. **D4 — Awaitility dependency.** Should I add
   `org.awaitility:awaitility` to `testImplementation` for cleaner
   polling, or roll the small reactive polling helper inline (no new
   dep)? I default to inline.
5. **D5 — Producer wiring.** Confirm a raw `KafkaProducer` over an
   auto-configured `KafkaTemplate`. The latter would need new
   `spring.kafka.producer.*` defaults in `application.yml`.
6. **D6 — Mongo cleanup scope.** Confirm scoped delete by `txn_id`
   over `dropCollection('txn')`. The latter would conflict with
   `TransactionServiceIntegrationSpec` if the two are ever interleaved
   in the same JVM.
7. **D7 — Number of features.** Confirm one happy-path feature plus
   one idempotency feature is the right size, or ask for additional
   end-to-end cases (e.g. conflicting duplicate, commit-before-open
   propagated as a Kafka retry).
8. **Integration profile vs `@TestPropertySource`.** Should the
   `jadetipi.kafka.enabled=true` override come from a new
   `application-integration-test.yml` (and `@ActiveProfiles("integration-test")`)
   or inline via `@TestPropertySource(properties = ...)`? Both are
   in-scope; I default to the inline `@TestPropertySource` to avoid
   adding a new profile.
9. **Spring Kafka container start-up.** The default
   `concurrency=1` and the Spring Kafka container will subscribe to
   the topic-pattern by polling Kafka's metadata at a 5-minute
   interval (`metadata.max.age.ms`). For a per-test topic created
   *after* the consumer container starts, the consumer may not see
   the topic for several minutes. Two mitigations:
   (a) create the test topic **before** the Spring context loads
   (e.g. in a static `setupSpec` that runs prior to `@SpringBootTest`
   bootstrapping — possible by keying on a `@DynamicPropertySource`
   that triggers topic creation), or
   (b) override `spring.kafka.consumer.properties.metadata.max.age.ms`
   to ~2000ms in the integration test profile so the consumer notices
   the new topic quickly.
   I default to **both**: create the topic in a static
   `setupSpec`-equivalent ordering hook *and* shorten
   `metadata.max.age.ms` to 2s. Confirm this combined approach is
   acceptable — if not, we may need to switch to a fixed topic name
   (`jdtp-txn-itest`) created by `kafka-init` in
   `docker/docker-compose.yml`, but that would expand scope to
   `docker/`.

STOPPING here per orchestrator pre-work protocol — no implementation,
no build/config/source/test/doc edits beyond this file.
