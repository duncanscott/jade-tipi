# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-009 — Plan committed location/link materialization (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-009`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md`,
propose the smallest backend unit that copies committed transaction
messages for the accepted location/link vocabulary into their long-term
MongoDB collections, so `loc`, `typ` link-type declarations, and `lnk`
relationship records can exist outside the `txn` write-ahead log before
reader/query APIs are designed. The supported message set defaults to
committed `create` messages for `loc`, for `typ` with
`data.kind == "link_type"`, and for `lnk` matching the TASK-008
vocabulary.

Director constraints to respect (from `OUT_OF_SCOPE` and the
`TASK-009 Pre-work Direction` block in `DIRECTIVES.md`):

- Do not implement semantic reference validation for `lnk.type_id`,
  `left`, `right`, or `allowed_*_collections`.
- Do not add plate/well read APIs, "what is in this plate?" queries,
  "where is this sample?" queries, controllers, HTTP submission
  rebuilds, or UI work.
- Do not add `parent_location_id` to `loc` records.
- Do not change Kafka topic configuration, listener deserialization
  semantics, the `txn` write-ahead log record shape, the
  committed-snapshot response shape, security policy, Docker Compose,
  or build configuration.
- Do not implement update/delete replay, backfill jobs, background
  workers, multi-transaction conflict resolution, or
  authorization/scoping policy.

This is pre-work only. No production source, build, schema, example,
test, or non-doc edits beyond this file until the director moves
`TASK-009` to `READY_FOR_IMPLEMENTATION` (or sets the global signal
to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
  already returns `CommittedTransactionSnapshot` only when the `txn`
  header has `record_type=transaction`, `state=committed`, and a
  non-blank `commit_id`. Messages inside the snapshot are sorted by
  `_id` ASC (deterministic receive order) and carry `collection`,
  `action`, `data`, `receivedAt`, and `kafka` provenance. The service
  is Kafka-free and HTTP-free. **A materializer can reuse it as the
  committed-visibility gate without re-implementing the rule.**
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  is the only writer to `txn`. `commitHeader(txnId, message)` is the
  single point at which a transaction transitions to
  `state=committed` with a backend-generated `commit_id`. The method
  composes `findById → updateFirst → thenReturn(PersistResult.COMMITTED)`
  and is a clean place to add a single post-commit `flatMap` if we
  decide to auto-trigger materialization (see Q1).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  already creates the `loc`, `typ`, `lnk`, `ppy`, `ent`, `uni`, `grp`,
  `vdn`, and `txn` collections at startup via `Collection.values()`.
  **No initializer change is needed for `TASK-009`** — the destination
  collections already exist.
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  defines `LOCATION("location","loc")`, `TYPE("type","typ")`, and
  `LINK("link","lnk")`. The materializer can match `Collection.LOCATION`
  / `Collection.TYPE` / `Collection.LINK` via `Collection.fromJson(abbr)`
  on the snapshot's `collection` string (or by abbreviation directly)
  rather than carrying its own enum.
- The canonical examples
  `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json` already pin the
  envelope shape this materializer must accept. Each one carries
  `data.id` as a fully-namespaced string ID. None of the supported
  payloads expect nested objects under `id`.
- Existing service test pattern is pure Spock with
  `Mock(ReactiveMongoTemplate)` and `Mono`/`Flux` stubs (see
  `CommittedTransactionReadServiceSpec`,
  `TransactionMessagePersistenceServiceSpec`, and
  `MongoDbInitializerSpec`). The same pattern fits the new
  materializer. Integration coverage exists as opt-in env-gated
  Kafka specs (`TransactionMessageKafkaIngestIntegrationSpec`); the
  same opt-in style would suit a future end-to-end materialization
  spec.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/util/Constants.groovy`
  already exports `COLLECTION_TRANSACTIONS = 'txn'` and
  `TRANSACTION_ID_SEPARATOR = '~'`. No new constants are required if
  the materializer routes by `Collection.abbreviation`.

### Smallest implementation plan (proposal)

#### S1. Materialization boundary

**Default proposal: a single Kafka-free, HTTP-free service that
consumes one `CommittedTransactionSnapshot` and writes one document
per supported message into the matching long-term collection.**

- New class
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  (Spring `@Service`).
- Constructor takes only `ReactiveMongoTemplate`. Stays Kafka-free
  and HTTP-free.
- Public surface:
  `Mono<MaterializeResult> materialize(CommittedTransactionSnapshot snapshot)`.
  Optional convenience overload
  `Mono<MaterializeResult> materialize(String txnId)` that delegates
  through `CommittedTransactionReadService.findCommitted(txnId)` and
  `Mono.empty()`s when the snapshot is not visible (this preserves
  the committed-visibility gate without duplicating the rule).
- New value object
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`
  (Groovy `@Immutable`) carrying counts only for now:
  `int materialized, int duplicateMatching, int conflictingDuplicate,
  int skippedUnsupported`. No HTTP exposure; intended for tests and
  logs.

The first implementation only **reads** committed snapshots, which
honours the directive's "Kafka-free service over committed snapshots
first" guidance. It does not redesign `txn`, the listener, or the
read-side response shape.

#### S2. Trigger / integration point

Two viable narrow integration points exist; I recommend (b) and
flag (a) as the explicit alternative.

(a) **Caller-driven (no auto trigger)**: do not modify
`TransactionMessagePersistenceService`. Materialization is a service
that other turns can call (a future HTTP/CLI replay, a future
TASK-N controller test, or the next pre-work task). Pros: zero blast
radius on the existing commit path; pure additive surface. Cons: the
canonical Kafka commit path does not actually populate `loc` / `typ`
/ `lnk` until a follow-up task adds the trigger, so downstream tasks
that want to query these collections will have to invoke the service
explicitly.

(b) **Post-commit trigger inside `commitHeader`** (recommended):
add a single `.flatMap` to
`TransactionMessagePersistenceService.commitHeader(...)` after the
`mongoTemplate.updateFirst(...)` succeeds:
`.flatMap { _ -> materializer.materialize(txnId).thenReturn(PersistResult.COMMITTED) }`.
Materializer failures are logged at WARN with the txnId and
`commit_id`, swallowed, and the result still resolves to
`PersistResult.COMMITTED`. **Rationale:** the commit is already
durable in `txn` once `updateFirst` returns; the materializer is a
read-after-commit projection. Failing the commit on a materializer
error would invert that ordering and conflate WAL durability with
projection durability. Re-materialization on retry is safe because
S4's idempotency rule treats matching duplicates as a no-op.

I lean toward (b) because the directive asks for the smallest unit
that actually moves committed records out of `txn`, and (a) would
require a separate trigger task. **Decision deferred to Q1.** The
service in S1 is identical under either choice; only one composition
line in `TransactionMessagePersistenceService` differs.

This trigger is **not** a Kafka listener and not a background
worker — it is a synchronous post-commit projection on the existing
write path. No Kafka/listener/topic changes are introduced.

Out-of-band re-materialization (e.g. for a future repair turn) is
not implemented in this task; the caller-driven overload from S1 is
sufficient if a future turn needs it.

#### S3. Supported message set

Exactly three message families are materialized in this task; every
other message is pass-through skipped (counted as
`skippedUnsupported`):

- `collection == loc && action == create` →
  insert into Mongo collection `loc`.
- `collection == typ && action == create && data.kind == "link_type"` →
  insert into Mongo collection `typ`.
- `collection == lnk && action == create` →
  insert into Mongo collection `lnk`.

Explicitly **skipped** (no error, just counted):

- Any `txn`/`open`/`commit`/`rollback` message (already lives in `txn`).
- `ppy`, `ent`, `uni`, `grp`, `vdn` messages (no materialization
  scope yet).
- `typ` records without `data.kind == "link_type"` (entity types and
  any future `kind` value remain out of scope until a separate task
  adds them).
- Any `update` or `delete` action across all collections (replay
  semantics deferred per `OUT_OF_SCOPE`).

#### S4. Materialized document shape

For all three supported families, the materialized document uses
`data.id` as its `_id` and copies the committed `data` payload
verbatim plus a small provenance sub-document. **No semantic
reference validation runs**; `lnk.type_id`, `left`, `right`, and
`allowed_*_collections` are not resolved.

Schematic (Groovy `Map<String, Object>` written through
`mongoTemplate.insert(map, abbreviation)`):

```
_id           = data.id                   // fully-namespaced canonical ID
... data ...                              // all snake_case fields from data, verbatim
provenance    = {
    txn_id        : snapshot.txnId,
    commit_id     : snapshot.commitId,
    msg_uuid      : message.msgUuid,
    committed_at  : snapshot.committedAt, // Instant
    materialized_at: Instant.now()
}
```

`provenance` is a single namespaced sub-document so the materialized
record stays self-describing without colliding with payload fields.
`provenance` is a snake_case key and is reserved by the materializer;
the `data` payloads we have do not currently carry a top-level
`provenance` key, and if a future payload does, the directive can
rename it (see Q4).

Per-collection notes:

- **`loc`**: copies `name`, `description`, and any future `loc`
  payload fields verbatim. **Does not** synthesize
  `parent_location_id` or any other parentage field — directive
  forbids this.
- **`typ`** (link_type only): copies `kind`, `name`, `description`,
  `left_role`, `right_role`, `left_to_right_label`,
  `right_to_left_label`, `allowed_left_collections`,
  `allowed_right_collections`. The materializer does **not** validate
  or normalise these facts; it is a verbatim projection.
- **`lnk`**: copies `type_id`, `left`, `right`, and `properties`
  (including `properties.position`). The materializer does **not**
  resolve `type_id`, `left`, or `right`.

`data.id` is **not** removed when copying; the materialized document
ends up with `_id == data.id == _id` (Mongo will collapse a duplicate
`id` if `data.id` is mapped onto `_id` only — concretely, the writer
strips `data.id` from the copied payload before insert and sets
`_id` to its value, to avoid storing the same string twice; both
representations are equivalent for read use). This is a small
implementation detail surfaced as Q3.

#### S5. Idempotency, ordering, and conflict behavior

The directive requires the proposal to specify these explicitly.

- **Ordering within a snapshot**: messages are processed in the
  order they arrive from `CommittedTransactionReadService`, which is
  Mongo `_id` ASC (already enforced by that service's `Sort.by(ASC,
  _id)`). `_id == txnId~msgUuid`, so this is the receive order. The
  materializer therefore preserves intra-transaction declaration
  order without needing its own sort.
- **Idempotency on retry**: the materializer attempts
  `mongoTemplate.insert(doc, abbreviation)` and on
  `DuplicateKeyException` (or Spring's `DuplicateKeyException`,
  matching the persistence service's existing duplicate-detection
  pattern) re-fetches the existing document and compares the
  copied `data` payload (excluding `_id` and `provenance`).
    - **Identical payload** → treat as success
      (`MaterializeResult.duplicateMatching++`); leave the existing
      document and its existing `provenance` untouched. This is
      analogous to `PersistResult.APPEND_DUPLICATE` in
      `TransactionMessagePersistenceService`.
    - **Differing payload** → log ERROR with both summaries, count
      `MaterializeResult.conflictingDuplicate++`, and **do not
      overwrite**. The materializer continues with the next message
      so a single conflicting record does not silently block other
      records in the same snapshot. Behavior of "raise the count to
      caller, but do not throw" matches the read-after-commit
      projection model: the committed transaction is durable in
      `txn`; conflicts are visible in logs and counts. Q5 surfaces
      the alternative (throw) for director decision.
- **Missing `data.id`**: the materializer treats this as an
  unrecoverable per-message error: log ERROR with `txn_id`,
  `commit_id`, `collection`, `msg_uuid`, count
  `MaterializeResult.skippedUnsupported++`, and continue. It does
  **not** synthesize an ID. The current schema does not require
  `data.id` (the existing `SnakeCaseObject` rule is only on field
  names), so this defensive branch matters even though the canonical
  examples all carry a non-blank `data.id`.
- **Duplicate `data.id` inside one snapshot**: handled by the same
  `DuplicateKeyException` branch as cross-snapshot duplicates. Two
  identical-payload messages count as one materialization plus one
  `duplicateMatching`; differing payloads count one materialization
  plus one `conflictingDuplicate`.
- **Committed-visibility gate**: the materializer is reachable only
  through (a) `materialize(snapshot)` from the post-commit hook, or
  (b) `materialize(txnId)` which delegates to
  `CommittedTransactionReadService.findCommitted(txnId)`. Both paths
  enforce the existing `record_type=transaction`,
  `state=committed`, non-blank `commit_id` rule. The materializer
  itself does not reach into the `txn` collection for visibility
  decisions. **The accepted gate from TASK-005/006 is preserved
  unchanged.**

#### S6. Tests

All under
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`,
following the existing pure-Spock `Mock(ReactiveMongoTemplate)`
pattern.

- `CommittedTransactionMaterializerSpec`:
    - `materialize(snapshot)` writes one `loc` doc for a `loc+create`
      message and copies `name`/`description` verbatim with `_id ==
      data.id` and a populated `provenance` sub-document.
    - `materialize(snapshot)` writes one `typ` doc for a `typ+create`
      message with `data.kind == "link_type"`, including all six
      declarative facts plus `allowed_*_collections`.
    - `typ + create` without `data.kind == "link_type"` is **not**
      written to `typ`; counted as `skippedUnsupported`. Asserts no
      `mongoTemplate.insert` to `typ` for that message.
    - `lnk + create` writes one `lnk` doc with `type_id`, `left`,
      `right`, and `properties.position` preserved.
    - `ppy + create` and `ent + create` are skipped without writes.
    - `update`/`delete` actions on supported collections are
      skipped without writes.
    - Identical-payload duplicate (`DuplicateKeyException` →
      matching existing doc) returns success with
      `duplicateMatching == 1` and does not overwrite.
    - Differing-payload duplicate (`DuplicateKeyException` →
      different existing doc) returns success with
      `conflictingDuplicate == 1`, logs at ERROR, and does not
      overwrite.
    - Missing `data.id` is skipped with
      `skippedUnsupported == 1`.
    - Multi-message snapshot with mixed supported and unsupported
      messages produces correct counts and writes in the snapshot's
      order (asserted via the order of recorded `mongoTemplate.insert`
      calls).
    - `materialize(txnId)` returns `Mono.empty()` when
      `CommittedTransactionReadService.findCommitted(...)` returns
      `Mono.empty()` (no insert is attempted). Asserted with a
      `Mock(CommittedTransactionReadService)` collaborator.
- If S2(b) is chosen: extend
  `TransactionMessagePersistenceServiceSpec`:
    - After a successful commit, the materializer collaborator's
      `materialize(txnId)` is invoked exactly once with the just-
      committed `txnId`; the surface result is still
      `PersistResult.COMMITTED`.
    - Materializer failure is logged and **does not** fail the
      commit; the surface result is still `PersistResult.COMMITTED`.

Optional integration test (deferred to a follow-up task unless
director asks otherwise — Q6). A new
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializeIntegrationSpec.groovy`
opts-in via env (mirroring `JADETIPI_IT_KAFKA`) and asserts that
after publishing open + create-loc + create-typ-link-type +
create-lnk + commit, documents exist in `loc`, `typ`, and `lnk` with
the expected `_id` and `provenance.txn_id` / `provenance.commit_id`.

#### S7. Verification commands

Pre-req (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — starts
  Mongo + Kafka + Keycloak. Mongo is required for
  `JadetipiApplicationTests.contextLoads`.

Targeted commands (in order):

- `./gradlew :jade-tipi:compileGroovy` — defensive compile.
- `./gradlew :jade-tipi:compileTestGroovy` — defensive test compile.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
  — focused new spec.
- `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`
  — only if S2(b) is chosen, to catch the post-commit hook regression.
- `./gradlew :jade-tipi:test` — full unit-suite regression
  (requires Mongo per `JadetipiApplicationTests.contextLoads`).
- Optional, deferred unless Q6 says otherwise:
  `JADETIPI_IT_MATERIALIZE=1 ./gradlew :jade-tipi:integrationTest
  --tests '*CommittedTransactionMaterializeIntegrationSpec*'`.

`./gradlew :libraries:jade-tipi-dto:test` is **not** required for
`TASK-009` — the DTO library is unchanged.

If verification fails because Mongo is not running, the Gradle
wrapper lock is held, the Gradle native dylib will not load, or the
toolchain is missing, I will report the documented setup command
(`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop`, etc.) and the exact command that could not run,
instead of treating it as a product blocker, per directive.

### Proposed file changes (all inside `TASK-009`-owned paths)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  (new) — the Kafka-free / HTTP-free materializer service (S1, S3, S4, S5).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/MaterializeResult.groovy`
  (new) — small `@Immutable` count result (S1).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceService.groovy`
  — only if Q1 selects (b): one new dependency, one new
  `.flatMap` line in `commitHeader`, no other behavior change.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  (new) — pure Spock spec (S6).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/TransactionMessagePersistenceServiceSpec.groovy`
  — only if Q1 selects (b): two new features for the post-commit
  hook (S6).
- `docs/architecture/kafka-transaction-message-vocabulary.md` —
  optional short note that committed `loc`, `typ` link-type, and
  `lnk` records are projected into their long-term collections by
  the post-commit materializer; semantic validation remains a
  follow-up. Director can decline this edit (Q7).
- `docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No changes proposed to:

- `libraries/jade-tipi-dto/src/main/...` (no DTO, schema, or example
  changes; the materializer routes by abbreviation strings).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/...` (no
  listener, topic, or Kafka-property change).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  (`loc`, `typ`, `lnk` are already enum-driven creates).
- `CommittedTransactionReadService` and its DTOs
  (`CommittedTransactionSnapshot`, `CommittedTransactionMessage`,
  `KafkaProvenance`) — read-side surface stays unchanged.
- `application.yml`, `build.gradle`, `docker-compose.yml`,
  `DIRECTION.md`, security/auth code, controllers.

### Blockers / open questions for the director

1. **Q1 — Trigger model.** Default proposal: post-commit hook (S2(b))
   inside `TransactionMessagePersistenceService.commitHeader` so the
   canonical Kafka commit path actually populates `loc` / `typ` /
   `lnk` and downstream tasks have data to query. Alternative:
   caller-driven only (S2(a)), leaving the trigger to a future
   task. Confirm "post-commit hook", or pick "caller-driven only".
2. **Q2 — Materializer failure semantics on the commit path.**
   Default proposal (under S2(b)): log materializer errors, swallow
   them, return `PersistResult.COMMITTED`. Rationale: the commit is
   already durable in `txn`; failing the surface result on a
   downstream projection error would invert that ordering and
   complicate retry semantics (the next consumer redelivery would
   re-attempt commit on a record that is already committed and hit
   `COMMIT_DUPLICATE`, leaving the materialization gap unfixed).
   Alternative: surface materializer errors as a new
   `PersistResult.COMMITTED_PROJECTION_FAILED`, or fail the call so
   the listener leaves the record un-acked and a retry recomputes
   the projection. Confirm "log + swallow", or pick a stricter
   surface.
3. **Q3 — `data.id` handling on the materialized document.**
   Default proposal: use `data.id` as `_id` and **strip `id` from
   the copied payload** before insert so the document does not
   carry both `_id` and `id` with the same value. Alternative: keep
   both (Mongo permits duplicates of `_id` and `id` since they are
   different field names; some downstream readers might expect `id`
   as a payload field). Confirm "strip `data.id` from payload", or
   pick "keep `data.id` alongside `_id`".
4. **Q4 — Provenance sub-document key name.** Default proposal:
   `provenance` (snake_case, not currently used in any committed
   `data` payload). Alternative: a more namespaced key like
   `_provenance` (leading underscore to avoid colliding with any
   future payload field), or split provenance fields onto top-level
   `txn_id` / `commit_id` / `msg_uuid` / `committed_at` /
   `materialized_at` siblings. Confirm `provenance`, or pick a
   different name / shape.
5. **Q5 — Conflicting-duplicate behavior.** Default proposal:
   log + count + skip (mirrors the projection model, lets the
   snapshot finish). Alternative: raise a domain exception
   (analogous to `ConflictingDuplicateException` in the persistence
   service) so the materializer aborts on the first conflict and
   the snapshot's later messages are not projected. Confirm
   "log + skip + count", or pick "abort on first conflict".
6. **Q6 — Integration test scope.** Default proposal: ship only
   the unit spec(s) in `TASK-009`; defer the opt-in
   `JADETIPI_IT_MATERIALIZE` integration spec to a separate task.
   Rationale: keeps `TASK-009` the smallest possible unit and
   avoids piling new env-gated integration coverage onto the same
   turn that introduces the service. Alternative: include the
   opt-in integration spec now so end-to-end coverage lands with
   the service. Confirm "unit spec only", or pick "include opt-in
   integration spec".
7. **Q7 — Documentation edit scope.** Default proposal: add a
   short paragraph to
   `docs/architecture/kafka-transaction-message-vocabulary.md`
   noting the post-commit projection so a later reader is not
   surprised to find `loc` / `typ` / `lnk` documents alongside
   `txn`. Alternative: leave the architecture doc unchanged and
   record the projection in
   `docs/agents/claude-1-changes.md` only. Confirm "add short
   paragraph", or pick "no architecture-doc edit".
8. **Q8 — `typ` discriminator strictness.** Default proposal:
   require `data.kind == "link_type"` on `typ + create` for
   materialization, leaving any other `typ` shape (including the
   older `04-create-entity-type.json` which has no `kind` and is
   already accepted on the wire) explicitly skipped. Alternative:
   also materialize bare entity-type `typ` records into `typ` so
   `typ` is uniformly populated for both kinds. Confirm
   "link_type only for now", or pick "materialize all `typ +
   create`".
9. **Q9 — Out-of-scope reaffirmation.** I am explicitly **not**
   touching: any DTO enum (`Collection`, `Action`, `Message`); the
   JSON schema (`message.schema.json`); the Kafka listener,
   listener properties, or topic configuration; the `txn`
   write-ahead log shape; the committed-snapshot response shape
   (`CommittedTransactionSnapshot` /
   `CommittedTransactionMessage` / `KafkaProvenance`); any
   controller; HTTP submission wrappers; build files; Docker
   Compose; security policy; semantic reference validation;
   plate/well read APIs; `parent_location_id`; or update/delete
   replay. Confirm that no piece of this `OUT_OF_SCOPE` block has
   shifted.

STOPPING here per orchestrator pre-work protocol — no
implementation, no source / build / config / schema / example /
test / non-doc edits beyond this file.
