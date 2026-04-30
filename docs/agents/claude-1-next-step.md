# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-005 — Add committed transaction snapshot read layer (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`) and
`docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
(status `READY_FOR_PREWORK`), add the smallest backend read-side layer
over the accepted Kafka-to-`txn` write-ahead log so callers can fetch a
single committed transaction snapshot (header + staged messages)
without materializing into long-term collections.

Director constraints to respect:

- Start Kafka-free **and** HTTP-free: the read service is the core; add
  or adjust a controller only if pre-work shows a minimal API surface
  is needed for useful verification.
- Committed visibility is governed by the header alone — the header
  must have `state == 'committed'` *and* a backend-generated
  `commit_id`. Child message stamping is still not required.
- Do not change the `txn` write-ahead log shape from `TASK-003`.
- Do not materialize into `ent`, `ppy`, `typ`, `lnk`, or other
  long-term collections.
- Preserve enough message-record fields for a later materializer or
  HTTP layer to know `collection`, `action`, `data`, `msg_uuid`, and
  Kafka provenance.
- If Docker / Gradle is unavailable during verification, report the
  documented setup command rather than treating it as a product
  blocker.

This is pre-work only. No backend, build, test, or doc edits beyond
this file until the director moves `TASK-005` to
`READY_FOR_IMPLEMENTATION` (or sets the global signal to
`PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `service/TransactionMessagePersistenceService` (TASK-003) is the
  authoritative writer into `txn` and exposes the field-name and
  state constants the read layer must reuse:
  - Header: `_id = txn_id`, `record_type = "transaction"`,
    `state ∈ {open, committed}`, `opened_at`, `committed_at`,
    `open_data`, `commit_data`, `commit_id` (set only on commit).
  - Message: `_id = txn_id + "~" + msg_uuid`,
    `record_type = "message"`, `txn_id`, `msg_uuid`, `collection`
    (abbreviation, e.g. `ppy`), `action` (lower-case enum string),
    `data`, `received_at`, optional `kafka` sub-doc with
    `topic`/`partition`/`offset`/`timestamp_ms`.
- The collection name constant lives in
  `util/Constants.COLLECTION_TRANSACTIONS` (= `"txn"`); the txn-id
  separator constant is `Constants.TRANSACTION_ID_SEPARATOR` (= `"~"`).
- The `txn` collection is **shared** with the older
  `service/TransactionService` (`TransactionService.openTransaction` /
  `commitTransaction`), which writes a different document shape
  (`_id = transactionId`, top-level `txn` sub-document with
  `secret`, `commit`, `commit_seq`, `committed`, etc.) and does
  *not* set `record_type` at all. Both shapes must coexist; the new
  read service must filter by `record_type` so it never confuses the
  older HTTP-style header for a WAL header. (Directly relevant: a
  future caller could otherwise read garbage when both services have
  written rows for unrelated `txn_id`s.)
- `controller/TransactionController` exposes only `/api/transactions/open`
  and `/api/transactions/commit` over the older `TransactionService`.
  No read endpoint exists today over the WAL. (Adding one would be a
  net-new HTTP surface.)
- Existing tests:
  - `service/TransactionMessagePersistenceServiceSpec` (Mockito-style
    mocks for `ReactiveMongoTemplate` + `IdGenerator`) — proven
    pattern for unit-level spec'ing this area.
  - `kafka/TransactionMessageKafkaIngestIntegrationSpec` (TASK-004)
    — opt-in (`JADETIPI_IT_KAFKA=1`) Kafka-to-Mongo round-trip that
    leaves real `txn` header + message documents and cleans up by
    `txn_id`. Useful as a structural reference but **not** a
    dependency of TASK-005's tests.
- `dto/ErrorResponse.groovy` is the only file currently in
  `org/jadetipi/jadetipi/dto/`; if a snapshot DTO is introduced, it
  fits naturally next to it.

### Smallest implementation plan (proposal)

#### S1. New service: `CommittedTransactionReadService`

A Kafka-free / HTTP-free `@Service` in
`org/jadetipi/jadetipi/service/` with a single public method:

```groovy
Mono<CommittedTransactionSnapshot> findCommitted(String txnId)
```

Behavior:

- Validates `txnId` is non-blank (`Assert.hasText`); blank → `Mono.error(IllegalArgumentException)` to match
  `TransactionService`'s existing convention. Already-handled by
  `GlobalExceptionHandler` if a controller is later added.
- Reads the header by `_id == txnId` from collection `txn`.
- **Authoritative committed gate** — emit `Mono.empty()` (= "no
  committed snapshot for that id") when any of:
  - the header document is missing,
  - `record_type != "transaction"` (defense against the older
    `TransactionService` document shape coexisting in `txn`),
  - `state != "committed"`,
  - `commit_id` is null or blank.
- On committed header, queries message records via:
  `Criteria.where('record_type').is('message').and('txn_id').is(txnId)`,
  ordered ascending by `_id` (= `txn_id~msg_uuid` lexicographic). See
  S3 for ordering rationale.
- Maps the raw Mongo `Map` rows into the snapshot DTO and returns it.

Implementation uses `ReactiveMongoTemplate` (already injected
elsewhere); no new dependency.

The service does **not** validate references between properties /
types / entities / assignments (explicit `OUT_OF_SCOPE`).

#### S2. New DTO: `CommittedTransactionSnapshot` (+ `CommittedTransactionMessage`)

Two read-only Groovy classes (Records or final classes with
`@JsonInclude(NON_NULL)`), located at:

- `service/CommittedTransactionSnapshot.groovy`
- `service/CommittedTransactionMessage.groovy`

Rationale for putting the snapshot DTOs in `service/` rather than
`dto/`: today these are pure return shapes for an internal service
boundary, not HTTP request/response DTOs. `dto/ErrorResponse` is the
only thing currently in `dto/` and it is HTTP-shaped. Co-locating
with the service keeps the HTTP-free boundary obvious. Open question
for the director — see Q1.

Fields on `CommittedTransactionSnapshot`:

| Field          | Type                              | Source field on header                   |
|----------------|-----------------------------------|------------------------------------------|
| `txnId`        | `String`                          | `txn_id` (also `_id`)                    |
| `state`        | `String`                          | `state` (always `"committed"` if returned) |
| `commitId`     | `String`                          | `commit_id`                              |
| `openedAt`     | `Instant` (nullable)              | `opened_at`                              |
| `committedAt`  | `Instant` (nullable)              | `committed_at`                           |
| `openData`     | `Map<String, Object>` (nullable)  | `open_data`                              |
| `commitData`   | `Map<String, Object>` (nullable)  | `commit_data`                            |
| `messages`     | `List<CommittedTransactionMessage>` | from message-record query              |

Fields on `CommittedTransactionMessage`:

| Field         | Type                              | Source field on message record |
|---------------|-----------------------------------|--------------------------------|
| `msgUuid`     | `String`                          | `msg_uuid`                     |
| `collection`  | `String` (abbrev: `ppy`, `ent`, …) | `collection`                  |
| `action`      | `String` (lower-case enum string) | `action`                       |
| `data`        | `Map<String, Object>` (nullable)  | `data`                         |
| `receivedAt`  | `Instant` (nullable)              | `received_at`                  |
| `kafka`       | `KafkaProvenance` (nullable)      | `kafka` sub-doc                |

`KafkaProvenance` (small Groovy `@Immutable` value object): `topic`,
`partition (int)`, `offset (long)`, `timestampMs (long)`. Mirrors
`KafkaSourceMetadata` shape so a later materializer or HTTP layer
sees the same provenance vocabulary on read as on write, without
dragging `org.apache.kafka.*` in.

#### S3. Deterministic message ordering

- Sort by `_id` ascending in the Mongo query
  (`Sort.by(Sort.Direction.ASC, '_id')`).
- `_id` is `txn_id + "~" + msg_uuid` and `txn_id` is constant within
  the result set, so this collapses to "ordered by `msg_uuid`".
- Rationale: `_id` is automatically indexed (no extra index needed),
  is stable across re-deliveries (same `msg_uuid` → same `_id`), and
  is independent of Kafka provenance (so HTTP-injected messages, if
  ever added, sort the same way).
- I considered ordering by `received_at` (insertion time on the
  backend) and by `kafka.offset` (broker order). Both are useful but
  not guaranteed to exist on every record once a non-Kafka caller is
  added. `_id` ascending is the smallest deterministic choice that
  works for every record kind we already store.
- Open question Q3: confirm `_id` ascending is acceptable, or pick
  `received_at` (then `_id` as tiebreak) as the canonical order.

#### S4. Tests (unit-only, narrow `:jade-tipi:test` target)

New file:
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`

Spock spec, mocking `ReactiveMongoTemplate` (same pattern as
`TransactionMessagePersistenceServiceSpec`). Features:

1. **Committed transaction returns header + messages snapshot.**
   Header doc returned with `record_type='transaction'`,
   `state='committed'`, `commit_id='COMMIT-001'`, plus three message
   rows. Assert all snapshot fields populated and provenance
   round-tripped, and that `state == 'committed'`.
2. **Open (uncommitted) header returns empty.**
   Header has `state='open'`, no `commit_id`. Assert `Mono.empty()`
   and `0 *` interactions on the message-find query (short-circuit).
3. **Header with `state='committed'` but no `commit_id` returns
   empty.** Belt-and-braces guard against partial-write states; even
   though writers always set both atomically today, the read layer
   must require both.
4. **Missing header (Mongo returns `Mono.empty()`) returns empty.**
   Clear "not found" signal without throwing.
5. **Header with `record_type` other than `'transaction'`
   (or absent) returns empty.** Guards against confusing the older
   `TransactionService`-shape document for a WAL header.
6. **Deterministic ordering.** Mock returns the three message docs in
   `[c, a, b]` order; assert the returned snapshot lists them
   alphabetically by `_id` regardless of mock order. (Verifies the
   query uses `Sort.by(ASC, '_id')` rather than relying on insertion
   order from the mock.)
7. **Blank `txnId` rejected with `IllegalArgumentException`.**
   Matches existing `TransactionService` convention.
8. **Null payload fields tolerated.** `open_data`, `commit_data`,
   `received_at`, and `kafka` may all be null on real records; the
   snapshot returns them as null without NPE.

I am **not** proposing an integration spec under
`src/integrationTest/groovy/...` for TASK-005:

- The unit spec mocks at the same boundary
  `TransactionMessagePersistenceServiceSpec` already mocks at, which
  the director accepted in TASK-003.
- The TASK-004 integration spec already proves the writer path
  produces the documents the read service is reading; re-asserting
  that round trip via the read service would not exercise new
  production code beyond a pure projection of fields.

If the director wants integration coverage, see Q2 — I can add a
minimal Mongo-only integration spec (no Kafka) that seeds
hand-written documents into `txn` and reads them back via the new
service.

#### S5. No controller, no HTTP surface (default)

Per directive, a controller is added "only if pre-work shows a
minimal API surface is needed for useful verification". My current
read is **no**:

- The unit spec verifies the service behavior fully.
- The existing integration spec verifies writer → Mongo, which is
  the only piece needing Kafka.
- Adding a `GET /api/transactions/{id}` endpoint would also raise
  policy questions (auth, redaction of `secret` from the older
  `TransactionService` shape if a caller sends a non-WAL id) that
  the directive explicitly defers.

If the director wants a thin read endpoint anyway, see Q4 below.

### Verification plan after implementation

Pre-req setup (documented project commands):

- `docker compose -f docker/docker-compose.yml up -d mongodb` — only
  Mongo is needed for `:jade-tipi:test` because
  `JadetipiApplicationTests.contextLoads` opens a Mongo connection
  on context load. Kafka is not required for the planned unit tests.

Targeted commands:

- `./gradlew :jade-tipi:compileGroovy` — must pass.
- `./gradlew :jade-tipi:compileTestGroovy` — must compile the new
  spec.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'`
  — runs only the new spec.
- `./gradlew :jade-tipi:test` — full unit suite as a regression
  check. Expected new total: existing 37 + 8 features = 45 (subject
  to the final feature count after director Q1/Q3 decisions).
- `./gradlew :jade-tipi:integrationTest` is **not** required for
  TASK-005 because no integration spec is planned (S4). I will
  confirm it still compiles via
  `./gradlew :jade-tipi:compileIntegrationTestGroovy` to catch any
  cross-source-set fallout.

If any of these fail because `mongodb` is not running, the wrapper
lock is held, or `node_modules` / browser binaries are stale, I will
report the exact documented setup command (e.g.
`docker compose -f docker/docker-compose.yml up -d mongodb`,
`./gradlew --stop` to release the wrapper lock) rather than
declaring a product blocker, per `DIRECTIVES.md`.

### Proposed file changes (all inside expanded TASK-005 scope)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadService.groovy`
  (new) — the service in S1.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionSnapshot.groovy`
  (new) — DTO in S2 (subject to Q1 about `dto/` vs `service/`).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMessage.groovy`
  (new) — DTO in S2.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/KafkaProvenance.groovy`
  (new, optional — could reuse `kafka/KafkaSourceMetadata` directly
  if the director prefers, but that lives in the `kafka` package and
  was conceived as a write-time provenance carrier; see Q5).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionReadServiceSpec.groovy`
  (new) — Spock spec from S4.
- `docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md`
  — `STATUS` flipped to `IMPLEMENTATION_COMPLETE` and `LATEST_REPORT`
  updated only at the end of the implementation turn.

No production code is changed in:
`TransactionMessagePersistenceService`, `TransactionMessageListener`,
`TransactionService`, `TransactionController`, any controller,
`application.yml`, or `build.gradle`.

### Blockers / open questions for the director

1. **Q1 — Snapshot DTO location.** Place
   `CommittedTransactionSnapshot` / `CommittedTransactionMessage` in
   `service/` (proposed: keeps the HTTP-free boundary obvious) or in
   `dto/` (closer to the existing `ErrorResponse`)? Either is in
   scope.
2. **Q2 — Integration spec.** Default plan is unit-only (S4
   rationale). Confirm, or ask for a minimal Mongo-seeded integration
   spec under `src/integrationTest/groovy/...` that hand-writes
   header + message documents and reads them back.
3. **Q3 — Message ordering.** Default is `_id` ascending (=
   `msg_uuid` ascending within a single `txn_id`). Alternative:
   `received_at` ascending with `_id` as tiebreak. I prefer `_id` for
   index-friendliness and source-independence; please confirm.
4. **Q4 — Controller.** Default is **no** controller for TASK-005.
   If a thin `GET /api/transactions/{id}/snapshot` (or similar) is
   useful for verification, confirm and name the path; I will mirror
   the existing controller's auth pattern
   (`@AuthenticationPrincipal Jwt jwt`) and add no extra policy.
5. **Q5 — Reuse `KafkaSourceMetadata` for read provenance?** It
   currently lives in `kafka/` and is a write-time carrier. Reusing
   it on read pulls a `kafka`-package type into a Kafka-free service
   return. Default: introduce a small parallel
   `service/KafkaProvenance` (no `kafka/` import). Confirm or ask me
   to reuse.
6. **Q6 — Guard against the older `TransactionService` document
   shape coexisting in `txn`.** S1 short-circuits when
   `record_type != 'transaction'`. Is that the right policy, or
   should the read service ignore the field (riskier — it could mis-
   read an older HTTP-style row that happens to have a `commit`
   sub-field)? Default is the strict guard.

STOPPING here per orchestrator pre-work protocol — no implementation,
no build/config/source/test/doc edits beyond this file.
