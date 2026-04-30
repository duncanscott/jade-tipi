# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-006 — Add committed transaction snapshot HTTP read adapter (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task
`TASK-006`, status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`,
add the smallest WebFlux/HTTP read adapter over the accepted
`CommittedTransactionReadService` (TASK-005) so callers can fetch one
committed transaction snapshot from the `txn` write-ahead log without
changing write-side ingestion or materializing into long-term
collections.

Director constraints to respect:

- Start from a thin WebFlux adapter; do not change the Kafka write
  path, the `txn` record shape, or the committed-snapshot service
  semantics accepted in TASK-005.
- Delegate committed visibility entirely to
  `CommittedTransactionReadService` — do not duplicate the
  `record_type`/`state`/`commit_id` gate in the controller.
- A committed snapshot returns HTTP 200 with header data, staged
  messages, `collection`, `action`, `data`, `msg_uuid`, and Kafka
  provenance.
- Missing / open / older-shape / otherwise non-committed transactions
  return a clear not-found response without exposing uncommitted
  message rows.
- Mirror existing controller / security patterns; do not introduce new
  authentication, authorization, or redaction policy.
- Keep materialization into `ent`/`ppy`/`typ`/`lnk` out of scope.
- If Docker / Gradle is unavailable during verification, report the
  documented setup command rather than treating it as a product
  blocker.

This is pre-work only. No production source, build, config, test,
DTO-package, or non-doc edits beyond this file until the director
moves `TASK-006` to `READY_FOR_IMPLEMENTATION` (or sets the global
signal to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `service/CommittedTransactionReadService.findCommitted(String txnId)
  : Mono<CommittedTransactionSnapshot>` is the accepted boundary. It
  already enforces every gate that TASK-006 needs:
  - `Assert.hasText(txnId, ...)` → `IllegalArgumentException` on
    null/blank/whitespace ids.
  - `Mono.empty()` when the header is missing, has no
    `record_type='transaction'` (older `TransactionService` shape),
    has `state != 'committed'`, or has a null/blank `commit_id`.
  - On a committed WAL header, returns the populated snapshot with
    `_id`-ASC-ordered messages and Kafka provenance.
  - Coerces raw Mongo `Date`/`Instant`/`null` timestamps via the
    `toInstant(...)` helper added in the TASK-005 review fix.
- Snapshot return classes live in `service/`:
  - `service/CommittedTransactionSnapshot` (`txnId`, `state`,
    `commitId`, `openedAt`, `committedAt`, `openData`, `commitData`,
    `messages`).
  - `service/CommittedTransactionMessage` (`msgUuid`, `collection`,
    `action`, `data`, `receivedAt`, `kafka`).
  - `service/KafkaProvenance` (`topic`, `partition`, `offset`,
    `timestampMs`).
  - All three are Groovy `@Immutable` value objects with default
    public-field accessors. Jackson on the WebFlux serializer can
    serialize them without further annotations; field names will be
    emitted in camelCase by default (see Q2 about snake_case).
- `controller/TransactionController` exposes only
  `POST /api/transactions/open` and `POST /api/transactions/commit`
  over the older `TransactionService`. There is no GET on
  `/api/transactions/...` today, so a new GET cannot collide with an
  existing handler. (Path-collision note: `GET /api/transactions/open`
  with a literal "open" segment would still resolve to the `/open`
  POST mapping with a 405 rather than be misrouted to a `{id}` GET if
  we used the bare-id path. See Q1 for the route choice.)
- `controller/DocumentController.getDocument` is the project's
  established not-found pattern:
  `service.findById(...).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build())`.
  We will mirror it.
- `exception/GlobalExceptionHandler` is registered as
  `@RestControllerAdvice` and already maps:
  - `IllegalArgumentException` → 400 with
    `dto/ErrorResponse` body (`message`, `status=400`, `error="Bad
    Request"`, `timestamp`). This means the service's `Assert.hasText`
    failure is already wrapped into a clean 400 — the controller does
    not need to duplicate the check.
  - `ResponseStatusException` → its declared status with a populated
    `ErrorResponse` body.
  - `IllegalStateException` → 409.
  - `WebExchangeBindException` → 400 with field-level errors.
  - `Exception` (catch-all) → 500.
- `config/SecurityConfig` is `@EnableWebFluxSecurity`; every path not
  in the small allow-list (`/`, `/hello`, `/version`, `/docs`,
  `/swagger-ui/**`, `/swagger-ui.html`, `/webjars/**`,
  `/v3/api-docs/**`, `/actuator/**`, `/error`, `/css/**`) requires
  authentication via JWT. `/api/transactions/**` is therefore already
  authenticated. No security change is required to put a new GET under
  `/api/transactions/...`.
- `test/groovy/.../config/TestSecurityConfig` provides a `@Primary
  ReactiveJwtDecoder` that mints a valid JWT for the `test` profile —
  the integration suite uses this via Keycloak, while `@WebFluxTest`
  slices can simply not import the production `SecurityConfig` (slices
  don't load arbitrary `@Configuration` beans by default).
- Existing test patterns:
  - Service-side: Spock `Specification` with `Mock(...)` boundaries
    (`TransactionMessagePersistenceServiceSpec`,
    `CommittedTransactionReadServiceSpec`). No Spring context.
  - Integration: `@SpringBootTest(webEnvironment=RANDOM_PORT)` +
    `@AutoConfigureWebTestClient` + `@ActiveProfiles("test")` +
    Keycloak helper for `Authorization` header
    (`document/DocumentListIntegrationTest`,
    `document/DocumentCreationIntegrationTest`).
  - There is no `controller/` test sub-tree under `src/test` today —
    this task would create one, which is in scope per
    `OWNED_PATHS`.
- `JadetipiApplicationTests.contextLoads` opens a Mongo connection on
  startup, so `./gradlew :jade-tipi:test` already requires Mongo
  running. No new Spring context will be added by the controller spec
  (per S3 below) so the unit-suite Mongo requirement stays the same.

### Smallest implementation plan (proposal)

#### S1. New controller: `CommittedTransactionReadController`

A `@RestController` at
`org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
with one public method:

```groovy
@RestController
@RequestMapping('/api/transactions')
class CommittedTransactionReadController {

    private final CommittedTransactionReadService readService

    CommittedTransactionReadController(CommittedTransactionReadService readService) {
        this.readService = readService
    }

    @GetMapping('/{id}/snapshot')
    Mono<ResponseEntity<CommittedTransactionSnapshot>> getSnapshot(
            @PathVariable('id') String id, @AuthenticationPrincipal Jwt jwt) {

        log.debug('Retrieving committed transaction snapshot: id={}', id)
        return readService.findCommitted(id)
                .doOnNext { snapshot -> log.info(
                        'Committed snapshot returned: id={}, messages={}',
                        id, snapshot.messages?.size() ?: 0) }
                .map { snapshot -> ResponseEntity.ok(snapshot) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
```

Behavior, per directive:

- **Delegation only.** No duplicated WAL-shape gate; the controller
  trusts `findCommitted` to return `Mono.empty()` for every flavor of
  "not committed" (missing, open, partial-write without `commit_id`,
  older `TransactionService` shape).
- **Not-found shape.** `ResponseEntity.notFound().build()` (HTTP 404,
  empty body) — same as `DocumentController.getDocument`. This avoids
  echoing the uncommitted state into a body that could leak the
  difference between "no such id" and "id exists but is not yet
  committed".
- **Blank/invalid id.** No inline check. The service's existing
  `Assert.hasText` raises `IllegalArgumentException`, and
  `GlobalExceptionHandler` already converts that into a 400 with the
  standard `ErrorResponse` body. (Note: a Spring path variable that
  matches `/{id}/snapshot` cannot be empty because the segment must
  be non-empty for the route to match; whitespace-only ids would be
  decoded literally and reach the service. The 400 path is the
  defensive case.)
- **Auth.** `@AuthenticationPrincipal Jwt jwt` to mirror
  `TransactionController`. The principal is not used inside the
  method (no per-id authorization is in scope for this task), but the
  parameter ensures the JWT is materialized and stays consistent
  with the existing controller signatures.
- **Logging.** Log only the `id` and the message count, never the
  snapshot body. Matches the existing `TransactionController` style.
- **Slf4j.** `@Slf4j` for log access, mirroring both existing
  controllers.

The controller does **not** add a new auth filter, redaction layer,
pagination, list endpoint, idempotency token, or cross-resource read.
Those are all listed `OUT_OF_SCOPE`.

#### S2. No service or DTO changes

- `CommittedTransactionReadService` is unchanged.
- `CommittedTransactionSnapshot`, `CommittedTransactionMessage`, and
  `KafkaProvenance` remain `@Immutable` value objects in
  `service/`. Per the TASK-005 director decision, snapshot return
  classes stay in the `service` package; making this a serialization
  boundary does not require a relocation. Default Jackson serialization
  via field-level access is sufficient (see Q2 about snake_case
  rendering).
- No new exception types. `IllegalArgumentException` already routes
  to a 400 via `GlobalExceptionHandler`.
- No new auth or redaction policy.
- No write-side change.

#### S3. Tests (narrow controller-level Spock spec, no Spring slice by default)

New file:
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`

Pattern: pure Spock `Specification` (same approach as the service
spec). Mock `CommittedTransactionReadService`, instantiate the
controller directly, call methods, assert via `Mono.block()` and
`StepVerifier`. No Spring context, no `@WebFluxTest` — keeping the
unit suite cheap and matching the precedent already accepted in
TASK-003 / TASK-005.

Features:

1. **Committed snapshot returns 200 with snapshot body and delegates
   to the service.**
   Mock returns a fully-populated `CommittedTransactionSnapshot`.
   Assert:
   - `1 * readService.findCommitted(TXN_ID) >> Mono.just(snapshot)`
   - the controller returns `ResponseEntity` with status 200 and the
     same snapshot instance as body
   - the body's `txnId`, `commitId`, `messages*.msgUuid`,
     `messages[0].collection`, `messages[0].action`,
     `messages[0].data`, and `messages[0].kafka` round-trip the
     mocked snapshot.
2. **Missing / not-yet-committed snapshot returns 404 with no body.**
   Mock returns `Mono.empty()`. Assert:
   - `1 * readService.findCommitted(TXN_ID) >> Mono.empty()`
   - status 404, body null.
3. **Older `TransactionService`-shape (covered by service) returns
   404 to the caller.** Same as feature 2 but framed as the
   delegation contract: for any case the service treats as
   "not committed", the controller emits 404 without a body.
4. **Service-side `IllegalArgumentException` propagates so the global
   handler can render 400.** Mock throws
   `IllegalArgumentException('txnId must not be blank')` (matching
   how `Assert.hasText` fails). Assert that `getSnapshot('   ', jwt)`
   propagates the exception (i.e. is *not* swallowed into 200/404).
   The 400 mapping itself is verified by `GlobalExceptionHandler`'s
   pre-existing test surface (already exercised in production for
   `DocumentController` payload validation); we do not need to spin
   up a `@WebFluxTest` slice just to re-verify the advice.
5. **Controller does not query Mongo directly.** No injected Mongo
   template; the controller's only collaborator is
   `CommittedTransactionReadService`. Verified by:
   - constructor signature (single arg of type `CommittedTransactionReadService`)
   - `0 * _._` on any other mock interactions in features 1–4.
6. **Snapshot body preserves message order from the service.** If the
   service emits messages in `[a, b, c]` order, the controller returns
   them in `[a, b, c]` order. (Already covered by feature 1's
   assertions on `messages*.msgUuid`; this is the explicit
   delegation-of-ordering contract.)

I am **not** proposing a `@WebFluxTest` slice or a full
`src/integrationTest` controller spec for TASK-006 by default. The
service already has full mock-level coverage for committed visibility
and ordering; the controller is a literal three-line delegation. A
Spring slice would add boot-time cost and a second fixture to
maintain without testing new behavior beyond JSON serialization. If
the director wants HTTP-level coverage anyway, see Q3.

#### S4. Verification commands (post-implementation)

Pre-req (project-documented):

- `docker compose -f docker/docker-compose.yml up -d` — Mongo +
  Kafka + Keycloak. Only Mongo is strictly required for
  `:jade-tipi:test` because `JadetipiApplicationTests.contextLoads`
  opens a Mongo connection. The new controller spec does not need
  Mongo or Kafka because it pure-mocks the service.

Targeted commands:

- `./gradlew :jade-tipi:compileGroovy` — must pass.
- `./gradlew :jade-tipi:compileTestGroovy` — must compile the new
  spec.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`
  — runs only the new controller spec.
- `./gradlew :jade-tipi:test` — full unit suite as a regression
  check. Expected new total: existing 49 + 6 features = ~55
  (subject to final feature count after director Q1/Q3).
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — sanity
  check that no integration source-set was broken (no spec added
  there by default).
- `./gradlew :jade-tipi:integrationTest` is **not** required for
  TASK-006 unless Q3 promotes the Spring slice into an integration
  test.

If verification fails because Mongo is not running, the wrapper lock
is held, or the toolchain is missing, I will report the documented
setup command (`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop`, etc.) instead of treating it as a product
blocker, per directive.

### Proposed file changes (all inside expanded TASK-006 scope)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
  (new) — the WebFlux adapter in S1.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`
  (new) — Spock spec from S3.
- `docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No production code is changed in:
`CommittedTransactionReadService`, `CommittedTransactionSnapshot`,
`CommittedTransactionMessage`, `KafkaProvenance`,
`TransactionMessagePersistenceService`, `TransactionMessageListener`,
`TransactionService`, `TransactionController`, `DocumentController`,
`GlobalExceptionHandler`, `ErrorResponse`, `SecurityConfig`,
`application.yml`, `build.gradle`, or any resource. The `txn`
write-ahead log shape from TASK-003 is preserved.

### Blockers / open questions for the director

1. **Q1 — Route choice.** Three reasonable options:
   - **Default (proposed):** `GET /api/transactions/{id}/snapshot`.
     Reads naturally as "the committed snapshot for this
     transaction id"; cannot be confused with a future bare-id
     endpoint that exposes only header status; cannot shadow the
     existing literal `/open` and `/commit` POST mappings under
     `/api/transactions`.
   - **Alternative:** `GET /api/transactions/{id}` — shorter, but
     a future "header-only status" GET would either need a different
     path or break existing callers.
   - **Alternative:** `GET /api/transactions/snapshot/{id}` — also
     unambiguous but reads less naturally as a sub-resource.
   Confirm `/api/transactions/{id}/snapshot`, or pick one of the
   alternatives.
2. **Q2 — JSON key style.** Default Jackson serialization on the
   `@Immutable` snapshot classes will emit camelCase keys
   (`txnId`, `commitId`, `messages[*].msgUuid`,
   `messages[*].kafka.timestampMs`). The persisted Mongo records and
   the Kafka message envelope use snake_case (`txn_id`, `commit_id`,
   `msg_uuid`, `timestamp_ms`). For an HTTP read API, either is
   defensible:
   - **Default (proposed):** camelCase (no annotations), which is
     consistent with `TransactionToken`/`CommitToken` shapes the
     existing `TransactionController` already returns over the wire.
   - **Alternative:** snake_case via
     `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)` on
     the three snapshot classes (or `@JsonProperty` per field), so
     the HTTP body matches the storage / message-envelope vocabulary.
     This is a one-annotation change with no semantic change to the
     service.
   Confirm camelCase, or ask for snake_case.
3. **Q3 — Test strategy.** Default plan is the controller-level
   Spock unit spec described in S3 (no Spring context). Two
   alternatives if you want HTTP-layer coverage:
   - **`@WebFluxTest(CommittedTransactionReadController)` slice**
     under `src/test/groovy/.../controller/` with `@MockBean
     CommittedTransactionReadService` and `WebTestClient`. Slices
     don't load `SecurityConfig` by default, so the route can be
     exercised without auth; alternatively `@Import(TestSecurityConfig)`
     to engage the test JWT decoder.
   - **Full integration spec** under
     `src/integrationTest/groovy/.../controller/` using
     `@SpringBootTest(webEnvironment=RANDOM_PORT)` +
     `@AutoConfigureWebTestClient` + `KeycloakTestHelper.getAccessToken()`,
     mirroring `DocumentListIntegrationTest`. This would exercise the
     real security chain end-to-end against Mongo + Kafka + Keycloak,
     at the cost of dragging in the full Docker stack.
   Confirm the unit-only default, or pick one of the alternatives.
4. **Q4 — Snapshot DTO location.** Per the accepted TASK-005
   decision, snapshot return classes live in `service/`. Now that the
   snapshot is also an HTTP response body, should they relocate to
   `dto/`? Default: **no** (TASK-005 decision still applies; the
   service is the producer and Jackson can serialize the existing
   classes without relocation). If the director prefers to colocate
   HTTP-shaped types in `dto/`, please confirm so I move them in the
   same implementation turn.
5. **Q5 — Per-id authorization / multi-tenant scoping.** The existing
   `TransactionController` accepts the JWT but does not currently
   gate writes by `org`/`grp`. The directive explicitly defers
   authorization changes for TASK-006. Default: do not add
   per-id/org/grp authorization — any authenticated principal can
   read any committed snapshot. Confirm, or flag as a follow-up
   task.
6. **Q6 — Response on whitespace-only id.** When the path literally
   matches (e.g. `/api/transactions/%20%20%20/snapshot`), the service
   raises `IllegalArgumentException` and the global handler returns
   400 with `ErrorResponse`. Default: keep that behavior; do not add
   a duplicate inline check. Confirm.

STOPPING here per orchestrator pre-work protocol — no implementation,
no build/config/source/test/doc edits beyond this file.
