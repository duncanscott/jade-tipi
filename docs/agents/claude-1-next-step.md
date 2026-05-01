# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-010 — Plan contents location query reads (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-010`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md`,
propose the smallest read/query path over the materialized `loc`, `typ`,
and `lnk` collections so callers can answer the two `DIRECTION.md`
contents questions: **"what are the contents of this plate/location?"**
(forward lookup over `lnk` where `left == containerId`) and **"where is
this sample or object located?"** (reverse lookup over `lnk` where
`right == objectId`).

The first read unit must:

- Identify the canonical `contents` link type without adding write-time
  semantic validation (DIRECTION.md uses `typ` records with
  `kind=link_type` and `name=contents` as the type declaration).
- Return matching `lnk` records in a deterministic order with verbatim
  endpoints and instance properties.
- Behave correctly when the container, object, or `contents` type does
  not exist; when endpoint references are unresolved; and when more
  than one materialized `lnk` matches.

Director constraints to respect (from `OUT_OF_SCOPE` plus the
`TASK-010 Pre-work Direction` block in `DIRECTIVES.md`):

- Do not change transaction persistence, the committed snapshot shape,
  or `TASK-009` materialization behavior.
- Do not add semantic write-time validation for `lnk.type_id`, `left`,
  `right`, or `allowed_*_collections`.
- Do not add update/delete replay, backfill, background workers,
  multi-transaction conflict resolution, or authorization/scoping
  policy.
- Do not rebuild HTTP submission wrappers, Kafka listener/topic
  configuration, DTO schemas/examples, Docker Compose, or build
  configuration.
- Do not add `parent_location_id` to `loc` records; containment stays
  canonical in `lnk`.
- Do not implement UI work.

This is pre-work only. No production source, build, schema, example,
test, or non-doc edits beyond this file until the director moves
`TASK-010` to `READY_FOR_IMPLEMENTATION` (or sets the global signal
to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  projects committed `loc + create`, `typ + create` (with
  `data.kind == "link_type"`), and `lnk + create` messages into the
  `loc`, `typ`, and `lnk` Mongo collections respectively. Each
  materialized document has `_id == data.id`, copies the original
  `data` payload verbatim (including the original payload `id`), and
  carries a reserved `_jt_provenance` sub-document with `txn_id`,
  `commit_id`, `msg_uuid`, `committed_at`, and `materialized_at`.
  **The reader can rely on this shape** without re-validating it.
- `CommittedTransactionMaterializerSpec` confirms the materialized
  shapes the reader will see (e.g. `linkMessage()` materializes a
  `lnk` doc carrying `_id`, `id`, `type_id`, `left`, `right`,
  `properties.position`, plus `_jt_provenance`).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
  is the existing pattern for a Kafka-free / HTTP-free read service:
  takes `ReactiveMongoTemplate`, uses `Assert.hasText` for blank
  inputs, uses `Query.query(Criteria.where(...).is(...))` with an
  explicit `Sort.by(Sort.Direction.ASC, _id)`, and is verified by a
  pure Spock spec that asserts the Mongo `Query` sort. **The new
  reader should follow this pattern.**
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
  is the existing pattern for a thin WebFlux adapter when one is
  needed (single GET, delegates to a service, maps `Mono.empty()` to
  404 with no body, defers `Assert.hasText` 400 mapping to
  `GlobalExceptionHandler`). **The new reader does not need a
  controller for verification** (see S2 below); a service-only
  implementation can be fully verified by pure-Spock unit specs that
  mock `ReactiveMongoTemplate`, matching `CommittedTransactionReadServiceSpec`.
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Collection.java`
  defines `LOCATION("location","loc")`, `TYPE("type","typ")`, and
  `LINK("link","lnk")`. The reader matches Mongo collection names by
  the abbreviation strings (`'loc'`, `'typ'`, `'lnk'`), the same way
  the materializer does.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/util/Constants.groovy`
  carries `COLLECTION_TRANSACTIONS = 'txn'`. No new constant is
  required for this task; the new reader can route on the
  `'loc'` / `'typ'` / `'lnk'` abbreviation literals (or share
  constants from the materializer if the director prefers).
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  describes the `contents` link type and the materialized projection
  but does not yet describe a reader. A short "Reading `contents`
  links" paragraph is in scope of this task's owned paths and may be
  proposed (Q9 below).
- Existing service test pattern is pure Spock with
  `Mock(ReactiveMongoTemplate)` and `Mono`/`Flux` stubs. The same
  pattern fits the new reader.

### Smallest implementation plan (proposal)

#### S1. Read boundary

**Default proposal: a single Kafka-free, HTTP-free service that
answers both forward and reverse `contents` queries by reading `lnk`
(filtered by canonical `contents` `type_id`s resolved from `typ`).**

- New class
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  (Spring `@Service`).
- Constructor takes only `ReactiveMongoTemplate`. Stays Kafka-free
  and HTTP-free.
- Public surface (default proposal):
  - `Flux<ContentsLinkRecord> findContents(String containerId)` —
    forward lookup (`lnk.left == containerId`).
  - `Flux<ContentsLinkRecord> findLocations(String objectId)` —
    reverse lookup (`lnk.right == objectId`).
  - Internal helper `Flux<String> resolveContentsTypeIds()` — queries
    `typ` for documents with `kind == 'link_type' AND name ==
    'contents'` and emits each matching `_id`. The literal
    `'contents'` matches the `DIRECTION.md` and `TASK-008` example
    convention. The literal lives only inside the read service; no
    write path changes.
- New value object
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
  (Groovy `@Immutable`, modeled on `CommittedTransactionMessage`)
  carrying:
    - `String linkId` (Mongo `_id`).
    - `String typeId` (`type_id`).
    - `String left`.
    - `String right`.
    - `Map<String, Object> properties` (verbatim `lnk.properties`,
      including `properties.position` when present).
    - `Map<String, Object> provenance` (verbatim `_jt_provenance`,
      so a caller can correlate the link back to the originating
      transaction without an extra query).

The reader is read-only; it does not mutate `lnk`, `loc`, or `typ`,
does not re-materialize, and does not change committed-snapshot
behavior.

#### S2. HTTP adapter — deferred

**Default proposal: do not add a controller in `TASK-010`.** Pure
Spock unit specs against the new service (with `Mock(ReactiveMongoTemplate)`)
exercise the actual Mongo query criteria, sort order, and response
shape — exactly the verification level director-accepted for
`CommittedTransactionReadService` in `TASK-005`. Adding a controller
in this task would expand scope to security wiring, route shape, and
HTTP error mapping that the directive explicitly defers ("include a
thin HTTP adapter only if source inspection shows it is necessary
for useful verification").

A future task can wrap this service in a thin
`/api/locations/{id}/contents` and `/api/objects/{id}/locations`
controller, mirroring `CommittedTransactionReadController`.
**Decision deferred to Q1 below**; the service in S1 is identical
under either choice.

#### S3. Forward + reverse, or forward only?

**Default proposal: ship both `findContents` and `findLocations` in
`TASK-010`.** Rationale:

- They are the two `DIRECTION.md` "contents" questions; shipping
  only one would leave the other still unanswered.
- They share the same infrastructure: the same
  `resolveContentsTypeIds()` helper, the same `Mongo` collection
  name `'lnk'`, the same response DTO, the same sort, the same
  error semantics. Splitting into two tasks would duplicate the
  setup cost without producing two independent units of value.
- The diff size stays small: one extra query method plus one extra
  pair of unit-spec features.

Forward-only is the alternative if the director prefers an even
smaller first unit (Q2).

#### S4. Query construction (default proposal)

For each public method, the service first resolves `contents`
type IDs and then runs a single `lnk` query.

##### S4a. Resolve `contents` type IDs

```
typ filter:
    record_type-equivalent fields are not present in the materialized
    `typ` collection (the materializer copies the data payload verbatim);
    use the data shape directly:
        kind == 'link_type'
        name == 'contents'
sort: ASC by _id (deterministic, but not user-visible)
project: _id only
```

Result: `Flux<String>` of matching link-type IDs. Typically a
single-element flux (one canonical declaration), but we tolerate
multiple matches because materialization permits multiple
type-bearing transactions over time and idempotent re-creation.

##### S4b. Forward lookup `findContents(containerId)`

After resolving `typeIds`:

- If `typeIds` is empty → emit `Flux.empty()`. **No `contents` link
  type has been declared yet, so by definition no contents links
  exist that this reader should return.**
- Else build:

```
lnk filter:
    type_id $in {typeIds}
    left == containerId
sort: ASC by _id  (deterministic; matches receive order because
                   _id == fully-namespaced lnk data.id, which the
                   producer writes before publication)
```

##### S4c. Reverse lookup `findLocations(objectId)`

Identical to forward, except `left == containerId` becomes
`right == objectId`.

##### S4d. Mongo collection routing

The service reads only from `'lnk'` and `'typ'`. It never reads from
`'loc'`, `'ent'`, `'ppy'`, `'txn'`, or any other collection in this
task. **Endpoint resolution against `loc` / `ent` / `loc` is
explicitly deferred** (see S6 and Q4).

#### S5. Response shape and ordering

- Each emitted element is one `ContentsLinkRecord` per matching
  `lnk` document.
- Field mapping (verbatim, no transformation):
    - `linkId` ← `_id` (the materialized Mongo `_id`, equal to the
      original `data.id`).
    - `typeId` ← `type_id`.
    - `left` ← `left`.
    - `right` ← `right`.
    - `properties` ← `properties` (or `null`/empty when absent).
    - `provenance` ← `_jt_provenance` (or `null` if absent for any
      reason; the materializer always sets it, so production data
      always has it).
- Ordering: `_id` ASC. The reader sorts in Mongo, not in memory; the
  unit spec asserts the `Query`'s `Sort` value (matching the
  `CommittedTransactionReadServiceSpec` style of asserting the
  query's sort rather than relying on mock ordering).
- The reader does not deduplicate or group. If two distinct
  materialized `lnk` records both have the same
  (`type_id`, `left`, `right`) tuple — possible because the
  materializer writes one document per `data.id` and a producer can
  legitimately write multiple containment links for the same pair
  with different per-link properties — both are returned.

#### S6. Edge-case behavior (specified per director ask)

- **Missing container or object** (no `loc`/`ent` document with that
  ID): the reader does not look at `loc` or `ent`; it queries `lnk`
  by the raw ID string. Result is `Flux.empty()` if no `lnk`
  records reference the ID. Behaviorally indistinguishable from
  "container exists but has no contents," which is correct under
  the directive's "no semantic write-time validation" rule.
- **Unresolved endpoint references** (a `lnk.right` points at an
  ID that has no corresponding `loc` or `ent` row, e.g. because the
  `right` record has not been materialized yet): the reader
  **still returns the `lnk` record** with the unresolved string
  intact. Endpoint resolution is a future read concern; surfacing
  an unresolved string is preferable to silently dropping the
  record because the read should not lie about the materialized
  state of `lnk`.
- **No `contents` type declared yet**: `resolveContentsTypeIds()`
  emits empty → both forward and reverse lookups emit `Flux.empty()`.
  The reader does not treat this as an error.
- **Multiple `contents` types declared** (e.g. one declaration in
  the production transaction history and one in a tenant-specific
  retest): the `$in` filter accepts all of them. The reader does
  not pick a "canonical" subset; the director can layer that policy
  in a future task. **The intent is "find all `lnk` whose type
  semantically declares itself as `contents`"**, not "find `lnk`
  whose type matches one specific declaration."
- **Duplicate / conflicting `lnk` records**: the materializer skips
  conflicting duplicates without overwrite, so each matching
  `lnk._id` corresponds to one materialized payload. The reader
  returns each materialized `lnk._id` once, in `_id` ASC order. It
  does not flag conflicts; the materializer's `MaterializeResult`
  counters are the conflict-visibility surface.
- **Blank `containerId` or `objectId`**: `Assert.hasText(...)`
  throws `IllegalArgumentException` with a clear message. The
  reader does not catch it. (No controller is added in this task;
  if a future controller wraps the service, the existing
  `GlobalExceptionHandler` already maps `IllegalArgumentException`
  to a 400 `ErrorResponse`.)
- **`null` arguments**: same — `Assert.hasText(null, ...)` throws
  `IllegalArgumentException`.
- **Materialized `_jt_provenance` missing** (defensive — should not
  happen under normal operation): the reader emits the record with
  `provenance: null` rather than skipping it. The link is real even
  when its provenance metadata is missing.
- **Type discriminator strictness**: the resolver requires
  `kind == 'link_type' AND name == 'contents'`. A bare entity-type
  `typ` (no `kind`) named `'contents'` will **not** match, because
  the materializer only writes `link_type` records into `typ` for
  this iteration. Even if the materializer later widens, the
  `kind == 'link_type'` filter keeps the reader honest.

#### S7. Tests

All under
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`,
following the existing pure-Spock `Mock(ReactiveMongoTemplate)`
pattern.

`ContentsLinkReadServiceSpec`:

- **Forward — single match**: one materialized `lnk` row whose
  `type_id` matches a single `contents` `typ._id` and `left ==
  containerId` is returned with `linkId`, `typeId`, `left`,
  `right`, `properties.position`, and `provenance` preserved.
- **Forward — multiple matches**: returned in `_id` ASC order
  matching the order in the simulated Mongo response. Asserts the
  Mongo `Query.sort` value, not just the result order, so the
  proof of database-side sort is independent of the mock's
  iteration order.
- **Forward — `$in` filter on multiple type IDs**: when
  `resolveContentsTypeIds()` emits two type IDs, the `lnk` query
  uses `Criteria.where('type_id').in(typeIds)`; assert this on the
  captured `Query`. Two `lnk` rows with different `type_id` values
  (both contents-class) both come back.
- **Forward — no `contents` type declared**: `typ` query returns
  empty → service emits `Flux.empty()`; `lnk` is **not** queried.
  Asserted with `0 * mongoTemplate.find(_, _, 'lnk')` (matching
  the materializer-spec idiom).
- **Forward — no matching `lnk` (container exists in `loc` but has
  no contents links)**: returns `Flux.empty()` without throwing.
  The reader does not consult `loc` to decide.
- **Forward — unresolved `right` reference**: a `lnk` whose
  `right` is a string with no matching `loc`/`ent` is still
  returned verbatim. The reader does not consult `loc`/`ent`.
- **Reverse — symmetric coverage**: mirrors each forward case for
  `findLocations(objectId)` (single match, multiple matches,
  ordering, no `contents` type, no matching `lnk`, unresolved
  `left`).
- **Reverse — `right` collision across collections**: a `lnk` whose
  `right` is a `loc` ID and another `lnk` whose `right` is an
  `ent` ID are both returned (mirrors
  `allowed_right_collections == ['loc', 'ent']` in the type
  declaration).
- **Type discriminator**: a `typ` record with `kind != 'link_type'`
  but `name == 'contents'` is **not** picked up by
  `resolveContentsTypeIds()`. Likewise a `link_type` named
  something other than `'contents'` is ignored.
- **Bad inputs**:
    - `findContents(null)`, `findContents('')`, `findContents('   ')`
      throw `IllegalArgumentException`. Likewise for
      `findLocations`. Asserted via `where:` data table.
- **No write-side coupling**: a Spock `0 * _ ` (or equivalent
  per-mock checks) confirms the service does not invoke
  `mongoTemplate.insert`, `update`, `save`, or `remove`.
- **Provenance preservation**: the response carries
  `provenance.txn_id`, `provenance.commit_id`, `provenance.msg_uuid`,
  `provenance.committed_at`, and `provenance.materialized_at`
  unchanged from the materializer-written sub-document.

Optional integration test (deferred to a follow-up task unless
director asks otherwise — Q5). A new
`jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadIntegrationSpec.groovy`
opts-in via env (mirroring the existing `JADETIPI_IT_KAFKA` pattern)
and asserts that after the existing TASK-009 commit-and-materialize
flow runs against a real Mongo, `findContents(plateId)` and
`findLocations(sampleId)` return the materialized link.

#### S8. Verification commands

Pre-req (project-documented; run only if `bootRun`/`test` would
otherwise fail because Mongo is unavailable):

- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` —
  starts Mongo. Required for `JadetipiApplicationTests.contextLoads`,
  which runs as part of any full `:jade-tipi:test`.

Targeted commands (in order):

- `./gradlew :jade-tipi:compileGroovy` — defensive main compile.
- `./gradlew :jade-tipi:compileTestGroovy` — defensive test compile.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
  — focused new spec.
- `./gradlew :jade-tipi:test` — full unit-suite regression
  (requires Mongo per `JadetipiApplicationTests.contextLoads`).
- Optional, deferred unless Q5 says otherwise:
  `./gradlew :jade-tipi:compileIntegrationTestGroovy` plus an
  `JADETIPI_IT_CONTENTS=1`-gated
  `./gradlew :jade-tipi:integrationTest --tests
  '*ContentsLinkReadIntegrationSpec*'`.

`./gradlew :libraries:jade-tipi-dto:test` is **not** required for
`TASK-010` — the DTO library is unchanged.

If verification fails because Mongo is not running, the Gradle
wrapper lock is held, the Gradle native dylib will not load, or the
toolchain is missing, I will report the documented setup command
(`docker compose -f docker/docker-compose.yml --profile mongodb up
-d`, `./gradlew --stop`, etc.) plus the exact command that could
not run, instead of treating it as a product blocker, per
directive.

### Proposed file changes (all inside `TASK-010`-owned paths)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  (new) — the Kafka-free / HTTP-free reader (S1, S4, S5, S6).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
  (new) — small `@Immutable` value object for one returned
  containment relationship (S5). Lives in `service` package matching
  `CommittedTransactionMessage` precedent (Q6).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadServiceSpec.groovy`
  (new) — pure Spock spec (S7).
- `docs/architecture/kafka-transaction-message-vocabulary.md` —
  optional short "Reading `contents` links" paragraph noting the
  forward and reverse query patterns and that endpoint resolution
  remains a follow-up (Q9). Director can decline.
- `docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No changes proposed to:

- `libraries/jade-tipi-dto/src/main/...` (no DTO, schema, or example
  changes).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/...` (no
  listener, topic, or Kafka-property change).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  (`loc`, `typ`, `lnk` are already enum-driven creates from
  `TASK-007`).
- `CommittedTransactionMaterializer`, `CommittedTransactionReadService`,
  `TransactionMessagePersistenceService`, and their value objects
  (`CommittedTransactionSnapshot`, `CommittedTransactionMessage`,
  `KafkaProvenance`, `MaterializeResult`, `PersistResult`) — read
  shapes and write paths stay unchanged.
- Any controller (no HTTP adapter in this task — see S2).
- `application.yml`, `build.gradle`, `docker-compose.yml`,
  `DIRECTION.md`, security/auth code, frontend.

### Blockers / open questions for the director

1. **Q1 — Boundary: service-only vs. service + thin controller.**
   Default proposal: service-only. Pure Spock specs against the
   captured Mongo `Query` (sort, criteria) verify the read behavior
   without expanding scope to HTTP wiring. Alternative: also add a
   thin `GET /api/locations/{id}/contents` and
   `GET /api/objects/{id}/locations` adapter modeled on
   `CommittedTransactionReadController` so an end-to-end HTTP smoke
   test is possible in this task. Confirm "service-only," or pick
   "service + thin controller."

2. **Q2 — Direction coverage in the first unit.** Default proposal:
   ship both `findContents` (forward, `left == containerId`) and
   `findLocations` (reverse, `right == objectId`) in `TASK-010`,
   sharing one `resolveContentsTypeIds()` helper. They are the two
   `DIRECTION.md` contents questions and add a single extra method
   plus mirrored tests. Alternative: ship forward only and defer
   reverse to a follow-up. Confirm "both directions," or pick
   "forward only."

3. **Q3 — Canonical `contents` type resolution.** Default proposal:
   resolve via `typ` query with `kind == 'link_type' AND name ==
   'contents'`, gather all matching `_id`s, and filter `lnk` with
   `type_id $in {ids}`. Alternative A: require the caller to pass
   an explicit `linkTypeId` (most precise, but pushes the resolution
   policy outside the service). Alternative B: hardcode a single
   well-known type ID literal (rejected; namespaced IDs are
   environment-specific). Confirm "resolve by name from `typ`," or
   pick alternative A.

4. **Q4 — Endpoint resolution scope.** Default proposal: do **not**
   join `lnk` to `loc`/`ent` in `TASK-010`. Returned records carry
   the `left` / `right` ID strings verbatim and the caller can
   resolve them with a future task's read APIs. Alternative: also
   fetch the matching `loc`/`ent` document for each `right` (forward)
   or `left` (reverse) and bundle it into the response. Rationale to
   defer: the join needs collection-aware dispatch
   (`right` may be a `loc` ID or an `ent` ID per the type's
   `allowed_right_collections == ['loc', 'ent']`), which is a much
   larger reader; shipping the unjoined reader first matches the
   directive's "narrowest backend boundary" ask. Confirm "no endpoint
   join," or pick "join on read."

5. **Q5 — Integration test scope.** Default proposal: ship only
   the unit spec in `TASK-010`; defer the opt-in
   `JADETIPI_IT_CONTENTS` integration spec to a separate task.
   Alternative: include the opt-in integration spec now.
   Confirm "unit spec only," or pick "include opt-in integration
   spec."

6. **Q6 — Response DTO location and shape.** Default proposal:
   `org.jadetipi.jadetipi.service.ContentsLinkRecord` (Groovy
   `@Immutable`) with `linkId`, `typeId`, `left`, `right`,
   `properties` (`Map<String, Object>` verbatim from the materialized
   `lnk`), and `provenance` (`Map<String, Object>` verbatim from
   `_jt_provenance`). Lives in `service` to match
   `CommittedTransactionMessage` precedent. Alternatives: (a) drop
   `provenance` from the response, (b) replace the `properties` and
   `provenance` `Map` types with named DTOs, (c) move both new
   classes to a new `org.jadetipi.jadetipi.contents` package.
   Confirm the default, or pick an alternative.

7. **Q7 — Sort field.** Default proposal: sort by `lnk._id` ASC.
   Rationale: matches the `CommittedTransactionReadServiceSpec`
   precedent, is deterministic, and exists on every materialized
   record. Alternative: sort by a `properties.position` value (e.g.
   `properties.position.row`, `properties.position.column`) for
   plate-shaped views. Rejected as a default because not every
   `contents` link has a `plate_well` position (DIRECTION.md allows
   `loc → loc` containment without well coordinates), and adding
   shape-specific sorts is a higher-level read concern. Confirm
   "_id ASC," or pick a different sort.

8. **Q8 — Ambiguous `contents` type declarations.** Default
   proposal: `$in` over all matching declarations. Alternative:
   pick a deterministic single declaration (e.g. lowest `_id` ASC,
   or most-recently-materialized) and only filter on that. Rejected
   as a default because it would silently hide some contents links
   from the reader. Confirm "match all declarations," or pick a
   tie-break rule.

9. **Q9 — Documentation edit scope.** Default proposal: add a short
   "Reading `contents` links" paragraph to
   `docs/architecture/kafka-transaction-message-vocabulary.md`
   noting the service surface, both query directions, the verbatim
   response shape, and that endpoint resolution against `loc`/`ent`
   is a follow-up. Alternative: leave the architecture doc unchanged
   and record the reader in `docs/agents/claude-1-changes.md` only.
   Confirm "add short paragraph," or pick "no architecture-doc
   edit."

10. **Q10 — Out-of-scope reaffirmation.** I am explicitly **not**
    touching: any DTO enum (`Collection`, `Action`, `Message`); the
    JSON schema (`message.schema.json`); message examples; the
    Kafka listener, listener properties, or topic configuration;
    the `txn` write-ahead log shape; the committed-snapshot
    response shape (`CommittedTransactionSnapshot` /
    `CommittedTransactionMessage` / `KafkaProvenance`); the
    `TASK-009` materializer or its result type; semantic reference
    validation; backfill jobs or update/delete replay;
    `parent_location_id`; build files; Docker Compose; security
    policy; or UI. Confirm that no piece of this `OUT_OF_SCOPE`
    block has shifted.

STOPPING here per orchestrator pre-work protocol — no
implementation, no source / build / config / schema / example /
test / non-doc edits beyond this file.
