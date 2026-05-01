# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-011 — Plan contents location HTTP read adapter (pre-work)

### Directive summary

Per `DIRECTIVES.md` (signal `REQUEST_NEXT_STEP`, active task `TASK-011`,
status `READY_FOR_PREWORK`) and
`docs/orchestrator/tasks/TASK-011-contents-location-http-read-adapter-prework.md`,
propose the smallest HTTP/WebFlux adapter over the accepted
`ContentsLinkReadService` so callers can answer the two `DIRECTION.md`
contents questions through the backend without changing materialization,
write semantics, or the service-level query behavior.

The first HTTP unit must:

- Expose both query directions delivered by `TASK-010`:
  `findContents(containerId)` (forward, `lnk.left == containerId`) and
  `findLocations(objectId)` (reverse, `lnk.right == objectId`).
- Stay strictly thin over the existing service: no semantic validation,
  no endpoint join to `loc`/`ent`, no extra Mongo lookups, no caching,
  no pagination policy, no authorization beyond what the existing
  Spring Security chain already provides for `/api/**`.
- Map service `Flux.empty()` to a deterministic, well-defined success
  body (empty JSON array) and surface blank/whitespace-only ids through
  the existing `GlobalExceptionHandler` advice as 400 `ErrorResponse`.
- Be verifiable through pure controller specs that mock only the
  service collaborator, mirroring `CommittedTransactionReadControllerSpec`.

Director constraints to respect (from `OUT_OF_SCOPE` plus
`TASK-011`'s task file):

- Do not change `ContentsLinkReadService` query semantics, the value
  object, or its tests unless pre-work identifies a blocking bug and
  the director explicitly approves the fix. (Pre-work has identified
  none.)
- Do not change transaction persistence, committed snapshot shape,
  materialization behavior, Kafka listener/topic configuration, DTO
  schemas/examples, build files, Docker Compose, security policy,
  or UI.
- Do not add semantic write-time validation for `lnk.type_id`, `left`,
  `right`, or `allowed_*_collections`.
- Do not join `lnk` endpoints to `loc` or `ent`, add
  `parent_location_id`, implement update/delete replay, backfill jobs,
  multi-transaction conflict resolution, authorization/scoping policy,
  or pagination/bulk policy.

This is pre-work only. No production source, build, schema, example,
test, or non-doc edits beyond this file until the director moves
`TASK-011` to `READY_FOR_IMPLEMENTATION` (or sets the global signal
to `PROCEED_TO_IMPLEMENTATION`).

### Current baseline (read, not yet changed)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  is the accepted `TASK-010` service. Public surface:
  `Flux<ContentsLinkRecord> findContents(String containerId)` and
  `Flux<ContentsLinkRecord> findLocations(String objectId)`. Both
  validate input with `Assert.hasText(...)` (so blank/whitespace/null
  ids throw `IllegalArgumentException` at the service boundary), both
  resolve canonical `contents` `type_id`s from `typ` via
  `kind == "link_type" AND name == "contents"`, and both stream `lnk`
  rows verbatim sorted by `_id` ASC. **The HTTP adapter never needs
  to duplicate any of this.**
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
  is a Groovy `@Immutable` carrying `linkId`, `typeId`, `left`, `right`,
  `properties` (`Map<String, Object>`), and `provenance`
  (`Map<String, Object>`). **It already serializes cleanly through
  the project's default Jackson configuration** (string fields plus
  two `Map<String, Object>` payloads), matching how
  `CommittedTransactionMessage` is serialized by
  `CommittedTransactionReadController`.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadController.groovy`
  is the controlling precedent for "thin WebFlux read adapter over a
  service-boundary value object": single `@RestController`,
  `@RequestMapping('/api/<area>')`, `@GetMapping(...)`, constructor
  injection of one service, `@PathVariable` id, `@AuthenticationPrincipal Jwt jwt`
  for parity with the other controllers (the JWT is required by the
  security chain anyway), and one or two `log.debug`/`log.info`
  observability lines. The new controller should mirror this shape
  exactly except for differences forced by `Flux` vs `Mono`.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/exception/GlobalExceptionHandler.groovy`
  already maps `IllegalArgumentException` to a 400 `ErrorResponse`
  body. The new controller relies on that mapping for blank
  `containerId` / `objectId`, identical to the
  `CommittedTransactionReadController` precedent. **No new exception
  handlers, no new error DTO, no controller-side input validation.**
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/SecurityConfig.groovy`
  permits the standard set of doc/swagger/actuator paths and gates
  `anyExchange().authenticated()` with JWT. The two new routes both
  live under `/api/...` and inherit JWT authentication from this
  chain unchanged. **No security config change.**
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/CommittedTransactionReadControllerSpec.groovy`
  is the controlling precedent for controller-spec coverage:
  `WebTestClient.bindToController(controller)`,
  `.controllerAdvice(new GlobalExceptionHandler())`, an
  `AuthenticationPrincipalArgumentResolver` registered as a custom
  argument resolver so `@AuthenticationPrincipal Jwt jwt` resolves to
  `null` without a server, and a mocked service collaborator. The new
  spec reuses this pattern verbatim.
- `docs/architecture/kafka-transaction-message-vocabulary.md` already
  has a "Reading `contents` Links" section documenting the service
  surface. A short follow-up paragraph documenting the HTTP routes and
  the empty-array success contract is in scope of `TASK-011`'s owned
  paths and may be proposed (Q9 below). The director can decline.
- `docs/agents/claude-1.md` already names `TASK-011` and points to
  the pre-work file; it does not need an edit during pre-work.

### Smallest implementation plan (proposal)

#### S1. HTTP boundary — one new controller

New class
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
(Spring `@RestController`):

- Constructor takes only `ContentsLinkReadService`. Stays Kafka-free,
  Mongo-free, and materializer-free at the controller layer; the
  service is the single collaborator.
- `@RequestMapping('/api/contents')` — base path consistent with
  existing `/api/transactions`, `/api/documents`, etc. The plural
  noun matches the materialized concept (a `contents`-typed link).
- Two `@GetMapping` methods, both returning `Flux<ContentsLinkRecord>`
  (the controller does not need `Mono<ResponseEntity<...>>` because
  there is no 404-vs-200 ambiguity — empty results are 200 `[]` per
  S5, and the success status code is the WebFlux default 200).
- Each method takes `@PathVariable('id') String id` and
  `@AuthenticationPrincipal Jwt jwt`, mirroring
  `CommittedTransactionReadController.getSnapshot(...)`. The `jwt`
  parameter is unused inside the method body; it exists so the
  argument resolver path is identical to other authenticated
  endpoints. (Removable if the director prefers — Q11 below.)

##### Default route shape

```
GET /api/contents/by-container/{id}   → Flux<ContentsLinkRecord>
                                          forward (lnk.left == id)
GET /api/contents/by-content/{id}     → Flux<ContentsLinkRecord>
                                          reverse (lnk.right == id)
```

Rationale: the link type's `left_role: container` and
`right_role: content` are explicit in
`docs/architecture/kafka-transaction-message-vocabulary.md` and the
`11-create-contents-type.json` example, so `by-container` and
`by-content` are the lowest-ambiguity names and read naturally as
"contents whose container is X" and "contents whose content is X."
They avoid `forward`/`reverse` (asymmetric jargon) and
`/locations` (which would imply joining to `loc`, deferred per
`OUT_OF_SCOPE`).

Two route alternatives are listed for the director in Q1 below.

#### S2. Response shape

- Return type: `Flux<ContentsLinkRecord>` directly. Spring WebFlux
  serializes it as a JSON array streamed under
  `application/json`, the same media type used by
  `CommittedTransactionReadController` for its single `Mono` result.
- Each emitted JSON object preserves the materialized fields
  verbatim, exactly as the value object is shaped today:
  - `linkId` — the materialized Mongo `_id` (and original
    `data.id`).
  - `typeId` — `lnk.type_id`.
  - `left` — `lnk.left` (raw id string; not joined to `loc`/`ent`).
  - `right` — `lnk.right` (raw id string; not joined to
    `loc`/`ent`).
  - `properties` — the verbatim `lnk.properties` map, including
    `properties.position` (`kind`/`label`/`row`/`column`) when
    present for plate-well placements.
  - `provenance` — the verbatim `_jt_provenance` sub-document
    (`txn_id`, `commit_id`, `msg_uuid`, `committed_at`,
    `materialized_at`).
- No envelope, no totals, no pagination links: the directive's
  "narrowest backend boundary" call and the `OUT_OF_SCOPE` no-pagination
  rule both push back against an envelope. A flat array is also what
  the existing controllers do (e.g. `DocumentController.listDocuments`
  returns `Flux<ObjectNode>` directly).
- No new DTO. The service already emits a value object with the
  appropriate verbatim shape and the `_jt_provenance` sub-document;
  introducing a thin HTTP DTO would duplicate fields without adding
  value. Q6 below offers two alternatives if the director wants a
  controller-only DTO.

#### S3. Empty-result behavior

- When the service emits `Flux.empty()` (no `contents` declaration,
  no matching `lnk`, container/object not present), the HTTP response
  is **200 OK with body `[]`**, content type `application/json`.
- This intentionally collapses three internal states into one
  HTTP status:
  - "no `contents` link type has been materialized yet,"
  - "container/object id has no matching `lnk` rows,"
  - "container/object id is not in `loc`/`ent` (yet)."
  Distinguishing them at the HTTP layer would require querying
  `loc`/`ent` (forbidden — see `OUT_OF_SCOPE`'s "do not join `lnk`
  endpoints to `loc` or `ent`") or surfacing materialization timing
  through a public API (out of scope). 200 `[]` is the safest
  contract: reads do not lie about the materialized state of `lnk`
  and do not silently 404 a perfectly valid query that simply has
  no answer yet.
- This is the Spring WebFlux default behavior for `Flux<T>`; no
  explicit `defaultIfEmpty` or `Mono.fromIterable` is needed.

#### S4. Blank / malformed / null id handling

- `Assert.hasText(id, '...')` already lives inside the service. The
  controller does **not** re-validate.
- A blank or whitespace-only `id` triggers `IllegalArgumentException`
  inside the service. The exception propagates out of the
  `Flux<ContentsLinkRecord>`. `GlobalExceptionHandler.handleIllegalArgument`
  maps that to **400 `ErrorResponse`** carrying the service's
  `'containerId must not be blank'` (or `'objectId must not be blank'`)
  message, status `400`, and the `error: 'Bad Request'` label. This
  matches the verified behavior asserted in
  `CommittedTransactionReadControllerSpec` (the
  `whitespace-only id surfaces service Assert.hasText as 400 ErrorResponse`
  feature).
- A path that genuinely cannot bind a `{id}` (e.g. trailing-slash
  truncation) is a Spring routing concern that already returns 404,
  and is not exercised by this controller's owned coverage.
- A `null` id cannot reach the controller for a `@PathVariable` route;
  if it did, `Assert.hasText` would still fail with
  `IllegalArgumentException`. Service-level coverage already proves
  that path; the controller spec does not need to retest it.

#### S5. Logging and observability

- Two `log.debug` lines (one per route, mirroring
  `CommittedTransactionReadController.getSnapshot`'s `log.debug`):
  `'Listing contents by container: id={}'` and
  `'Listing contents by content: id={}'`.
- One `log.info` per route on completion (via `doOnComplete`) or
  per emitted record (via `doOnNext`). I will pick whichever matches
  the existing precedent in `CommittedTransactionReadController` —
  which uses `doOnNext` to log `'Committed snapshot returned: id={}, messages={}'`.
  For a `Flux`, `doOnComplete` is the correct equivalent because
  there is no single returned record. The exact log line will be
  decided during implementation; nothing in the spec asserts log
  output, so this is observability-only and does not change behavior.
- No new metrics. No new audit event. No new `@PreAuthorize` (JWT auth
  is already enforced by the security chain).

#### S6. Service / collaborator boundary

- The controller's only collaborator is `ContentsLinkReadService`.
  No `ReactiveMongoTemplate`, no `CommittedTransactionMaterializer`,
  no `CommittedTransactionReadService`, no `KafkaTemplate`, no
  `TransactionService`, no Spring Data repository. This is asserted
  by reflection in S7 (matching the
  `CommittedTransactionReadControllerSpec`
  `controller has no direct Mongo collaborator` feature).
- The service's reactive contract (`Flux<ContentsLinkRecord>`) is
  returned to the WebFlux runtime unchanged; the controller adds no
  `block()`, `collectList()`, in-memory sort, dedup, or filter.

#### S7. Tests

All under
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/`,
following the existing pure-Spock `WebTestClient.bindToController`
pattern.

`ContentsLinkReadControllerSpec`:

- **Setup mirrors `CommittedTransactionReadControllerSpec`**:
  `Mock(ContentsLinkReadService)`, controller bound via
  `WebTestClient.bindToController(controller).controllerAdvice(new GlobalExceptionHandler()).argumentResolvers { configurer -> configurer.addCustomResolver(new AuthenticationPrincipalArgumentResolver(ReactiveAdapterRegistry.getSharedInstance())) }.build()`.
- **Forward — populated body**: a stub returning two
  `ContentsLinkRecord` rows for `findContents('plate-1')` produces a
  200 JSON array of length 2 with the expected `linkId`, `typeId`,
  `left`, `right`, `properties.position.kind`,
  `properties.position.label`, `properties.position.row`,
  `properties.position.column`, and
  `provenance.txn_id`/`commit_id`/`msg_uuid`/`committed_at`/`materialized_at`
  values, asserted via `jsonPath`.
- **Forward — preserves service order**: when the stub emits records
  in `_id` ASC order, the JSON array preserves that order. (The
  controller does not re-sort.)
- **Forward — empty body**: a stub returning `Flux.empty()` produces
  a 200 response whose body is an empty JSON array (`[]`), asserted
  with `expectBody().jsonPath('$.length()').isEqualTo(0)` (or
  equivalent). The status is **not** 404.
- **Forward — blank id surfaces service `Assert.hasText` as 400
  `ErrorResponse` via `GlobalExceptionHandler`**: a stub configured
  to throw `IllegalArgumentException('containerId must not be blank')`
  for `findContents('   ')` produces a 400 response whose JSON body
  has `status == 400`, `error == 'Bad Request'`, and
  `message == 'containerId must not be blank'`, asserted via
  `jsonPath`. (Also asserts that the spec-driven 400 path goes through
  the real `GlobalExceptionHandler` advice rather than a controller
  short-circuit.)
- **Forward — service delegation**: `1 * readService.findContents('plate-1') >> Flux.just(...)` and `0 * _` together prove that
  the controller calls only `findContents` and no other collaborator.
- **Reverse — symmetric coverage**: mirror each of the four forward
  feature methods for `findLocations(objectId)` against the
  `/api/contents/by-content/{id}` route.
- **Route binding**: implicit in the four `WebTestClient.get().uri(...)` exchanges,
  but explicitly assert the literal path strings as constants so a
  rename in either direction breaks the spec.
- **Controller has no direct Mongo collaborator**: a reflection feature
  matching `CommittedTransactionReadControllerSpec` —
  `Constructor<?>` parameter count is 1 and parameter type is
  `ContentsLinkReadService`.

No integration test is proposed. The service-level coverage from
`TASK-010` already exercises the Mongo `Query` shape, sort order,
and empty-result paths against a mocked `ReactiveMongoTemplate`; the
controller spec exercises HTTP routing, JSON serialization, and the
`GlobalExceptionHandler` integration without a server. An opt-in
end-to-end integration test (mirroring
`TransactionMessageKafkaIngestIntegrationSpec`) would expand scope
to standing up Kafka + Mongo to verify a thin HTTP adapter, which
the directive's "narrowest" framing pushes back against. Q5 below
offers it as an alternative if the director disagrees.

#### S8. Verification commands

Pre-req (project-documented; run only if `bootRun`/`test` would
otherwise fail because Mongo is unavailable):

- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` —
  starts Mongo. Required for `JadetipiApplicationTests.contextLoads`,
  which runs as part of any full `:jade-tipi:test`.

Targeted commands (in order):

- `./gradlew :jade-tipi:compileGroovy` — defensive main compile.
- `./gradlew :jade-tipi:compileTestGroovy` — defensive test compile.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`
  — focused new spec.
- `./gradlew :jade-tipi:test` — full unit-suite regression
  (requires Mongo per `JadetipiApplicationTests.contextLoads`).

`./gradlew :libraries:jade-tipi-dto:test` is **not** required —
the DTO library is unchanged.
`./gradlew :jade-tipi:integrationTest` is **not** required — no
integration spec is proposed (see S7 and Q5).

If verification fails because Mongo is not running, the Gradle
wrapper lock is held, the Gradle native dylib will not load, or the
toolchain is missing, I will report the documented setup command
(`docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
`./gradlew --stop`, etc.) plus the exact command that could not run,
instead of treating it as a product blocker, per directive.

### Proposed file changes (all inside `TASK-011`-owned paths)

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadController.groovy`
  (new) — the thin WebFlux adapter (S1, S2, S3, S5, S6).
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/ContentsLinkReadControllerSpec.groovy`
  (new) — pure controller-level Spock spec (S7).
- `docs/architecture/kafka-transaction-message-vocabulary.md` —
  optional short paragraph appended to the existing "Reading
  `contents` Links" section noting the two HTTP routes, the empty
  array success contract, and the 400 path through
  `GlobalExceptionHandler`. Director can decline (Q9).
- `docs/orchestrator/tasks/TASK-011-contents-location-http-read-adapter-prework.md`
  — `STATUS` flipped to `READY_FOR_REVIEW` and `LATEST_REPORT`
  rewritten only at the end of the implementation turn.
- `docs/agents/claude-1-changes.md` — append the implementation
  report after the work lands.

No changes proposed to:

- `ContentsLinkReadService.groovy`, `ContentsLinkRecord.groovy`, or
  their existing spec — read-side query semantics are frozen
  (`OUT_OF_SCOPE`).
- `libraries/jade-tipi-dto/src/main/...` (no DTO, schema, or example
  changes).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/kafka/...` (no
  listener, topic, or Kafka-property change).
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializer.groovy`
  (`loc`, `typ`, `lnk` are already enum-driven creates from
  `TASK-007`).
- `CommittedTransactionMaterializer`, `CommittedTransactionReadService`,
  `CommittedTransactionReadController`, `TransactionMessagePersistenceService`,
  and their value objects.
- `SecurityConfig.groovy` — JWT auth on `/api/**` already covers the
  two new routes; no permit-list edit is needed.
- `GlobalExceptionHandler.groovy` — the existing
  `IllegalArgumentException` advice already returns the desired 400
  `ErrorResponse`.
- `application.yml`, `build.gradle`, `docker-compose.yml`,
  `DIRECTION.md`, frontend, integration-test sources.

### Blockers / open questions for the director

1. **Q1 — Route shape.** Default proposal:
   `GET /api/contents/by-container/{id}` (forward, `lnk.left == id`)
   and `GET /api/contents/by-content/{id}` (reverse, `lnk.right == id`).
   These directly mirror the `left_role: container` and
   `right_role: content` facts in the canonical `contents` link-type
   declaration. **Alternatives**:
   - **A.** `GET /api/locations/{id}/contents` (forward) and
     `GET /api/objects/{id}/locations` (reverse). Reads naturally as
     "what's inside this location?" and "where is this object?",
     matching `DIRECTION.md`'s wording. Drawback: implies a join to
     `loc`/`ent` that is explicitly out of scope, and "objects" is
     a broad noun.
   - **B.** `GET /api/lnk/by-left/{id}` and
     `GET /api/lnk/by-right/{id}`, with a `?type=contents` query param
     so the same routes can later support other link types. Drawback:
     pushes the canonical-`contents` resolution policy to the caller,
     and `?type=contents` would be ignored by the current service.
   - **C.** Merged: `GET /api/contents?container={id}` and
     `GET /api/contents?content={id}`. Drawback: query-param routing
     makes path-based caching/security less natural and breaks
     parity with `/api/transactions/{id}/snapshot`.
   Confirm the default, or pick **A**, **B**, or **C**.

2. **Q2 — Direction coverage in the first unit.** Default proposal:
   ship both routes (forward and reverse) in `TASK-011`, matching
   how `TASK-010` shipped both service methods. They share the same
   controller class, the same value object, the same logging
   pattern, and mirrored test rows. **Alternative**: ship the
   forward route only and defer the reverse route to a follow-up.
   Confirm "both directions," or pick "forward only."

3. **Q3 — `@AuthenticationPrincipal Jwt jwt` parameter.** Default
   proposal: keep `@AuthenticationPrincipal Jwt jwt` on both methods,
   matching `CommittedTransactionReadController.getSnapshot`. The
   parameter is unused inside the body but keeps the argument-resolver
   chain identical for all authenticated `/api` reads, which the
   controller spec already wires through
   `AuthenticationPrincipalArgumentResolver`. **Alternative**: drop
   the parameter, since the security chain still gates the route.
   Confirm "keep `Jwt jwt`," or pick "drop the parameter."

4. **Q4 — Empty-result HTTP status.** Default proposal: 200 with
   body `[]`. Rationale and trade-offs are in S3. **Alternatives**:
   - **A.** 404 when no records match (treats "no contents" as
     "resource missing"). Rejected as the default because it
     conflates "no link type" with "no matching links" and pushes
     callers to interpret 404 as a domain answer rather than a
     missing route.
   - **B.** 200 with a `{ "items": [] }` envelope. Rejected as the
     default to match `DocumentController.listDocuments`'s flat
     `Flux` precedent and keep the response narrow.
   Confirm "200 `[]`," or pick **A** or **B**.

5. **Q5 — Integration test scope.** Default proposal: ship only the
   controller spec in `TASK-011`. The service-level Mongo spec from
   `TASK-010` plus the controller-level WebFlux spec from `TASK-011`
   together cover routing, JSON, query construction, and Mongo sort.
   **Alternative**: also add an opt-in `JADETIPI_IT_CONTENTS_HTTP=1`
   integration spec under `jade-tipi/src/integrationTest/...` that
   stands up the Spring Boot context, materializes a `contents` link
   from canonical examples through Kafka + Mongo, and hits the new
   HTTP routes via `WebTestClient.bindToServer`. This grows the
   verification surface and re-uses the Kafka/Mongo Docker stack.
   Confirm "controller spec only," or pick "include opt-in
   integration spec."

6. **Q6 — Response DTO shape.** Default proposal: return
   `ContentsLinkRecord` directly, no controller-side DTO. The value
   object is already shaped for the consumer and lives in the
   `service` package per the `CommittedTransactionMessage`
   precedent. **Alternatives**:
   - **A.** Add a thin
     `org.jadetipi.jadetipi.dto.ContentsLinkResponse` (or similar)
     to give the HTTP surface a stable name independent of the
     service value object. Adds one new class and one mapping
     function for no current behavior change.
   - **B.** Drop `provenance` from the HTTP response (return only
     `linkId`, `typeId`, `left`, `right`, `properties`). Hides
     materialization metadata from external callers. Trade-off: the
     service value object already exposes it, so excluding it would
     require an HTTP-only DTO anyway (see **A**).
   Confirm the default, or pick **A** or **B**.

7. **Q7 — Path placement and base.** Default proposal:
   `@RequestMapping('/api/contents')`. **Alternative**: nest under
   `/api/links/contents/...` so future link types (e.g. `parent`,
   `derived_from`) can sit alongside under `/api/links/<type>/...`.
   Rejected as the default because it speculates about future link
   types not yet declared in `DIRECTION.md`. Confirm
   "/api/contents," or pick "/api/links/contents."

8. **Q8 — Path-variable name.** Default proposal: `{id}` (as in
   `CommittedTransactionReadController`). The full meaning
   (`containerId` vs `objectId`) is carried by the route. The Java
   parameter is named `id` and forwarded into the service. The
   service method name carries the role distinction and the spec
   asserts the right service method is called. **Alternatives**:
   - **A.** Rename to `{containerId}` / `{contentId}` for self-documenting
     paths; minor cosmetic gain.
   - **B.** Keep `{id}` and document the role distinction in the
     architecture doc paragraph (Q9).
   Confirm "{id}," or pick **A** or **B**.

9. **Q9 — Documentation edit scope.** Default proposal: append a
   short paragraph to the existing "Reading `contents` Links"
   section in
   `docs/architecture/kafka-transaction-message-vocabulary.md`
   stating the two HTTP routes, the 200-with-`[]` success contract,
   and the 400 path. **Alternative**: leave the architecture doc
   unchanged and record the HTTP surface only in
   `docs/agents/claude-1-changes.md`. Confirm "add short paragraph,"
   or pick "no architecture-doc edit."

10. **Q10 — Out-of-scope reaffirmation.** I am explicitly **not**
    touching: any DTO enum (`Collection`, `Action`, `Message`); the
    JSON schema (`message.schema.json`); message examples; the
    Kafka listener, listener properties, or topic configuration;
    the `txn` write-ahead log shape; the committed-snapshot
    response shape (`CommittedTransactionSnapshot` /
    `CommittedTransactionMessage` / `KafkaProvenance`); the
    `TASK-009` materializer or its result type;
    `ContentsLinkReadService`, `ContentsLinkRecord`, or their spec;
    semantic reference validation; backfill jobs or update/delete
    replay; `parent_location_id`; build files; Docker Compose;
    security policy (`SecurityConfig`); `GlobalExceptionHandler`;
    or UI. Confirm that no piece of this `OUT_OF_SCOPE` block has
    shifted.

11. **Q11 — Smoke verification expectation.** Default proposal: only
    the four Gradle commands in S8 (compile, compile-test, focused
    spec, full unit suite) are required. **Alternative**: also start
    `./gradlew :jade-tipi:bootRun`, exercise both routes with `curl`
    against a JWT obtained from the Docker Keycloak, and capture the
    response bodies in the report. This adds a manual end-to-end
    smoke step but does not change implementation scope. Confirm
    "Gradle-only verification," or pick "include manual `curl`
    smoke."

STOPPING here per orchestrator pre-work protocol — no
implementation, no source / build / config / schema / example /
test / non-doc edits beyond this file.
