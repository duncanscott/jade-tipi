# TASK-011 - Plan contents location HTTP read adapter

ID: TASK-011
TYPE: implementation
STATUS: READY_FOR_REVIEW
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-011-contents-location-http-read-adapter-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Plan the smallest HTTP/WebFlux adapter over the accepted
`ContentsLinkReadService` so callers can answer the two contents questions
through the backend without changing materialization, write semantics, or the
service-level query behavior.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `ContentsLinkReadService`, `ContentsLinkRecord`, existing controllers,
  `GlobalExceptionHandler`, security configuration, and controller/service
  test patterns before proposing implementation.
- Pre-work proposes the narrowest route shape for both query directions:
  forward contents lookup (`findContents(containerId)`) and reverse location
  lookup (`findLocations(objectId)`). Reuse existing controller conventions
  where they are clear.
- Pre-work specifies the HTTP response shape, including whether to return the
  service value object directly or introduce a thin DTO, how empty results map
  to HTTP status/body, and how blank or malformed ids should follow existing
  exception-handling patterns.
- Pre-work proposes focused WebFlux/controller assertions that prove route
  binding, service delegation, response serialization, empty-result behavior,
  blank-id handling, and the absence of direct Mongo/materializer/write-side
  collaborators in the controller.
- Pre-work proposes exact Gradle verification commands. If local tooling,
  Gradle locks, Docker, or Mongo are unavailable, report the documented setup
  command rather than treating setup as a product blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change `ContentsLinkReadService` query semantics unless pre-work
  identifies a blocking bug and the director explicitly approves the fix.
- Do not change transaction persistence, committed snapshot shape,
  materialization behavior, Kafka listener/topic configuration, DTO schemas or
  message examples, build files, Docker Compose, security policy, or UI.
- Do not add semantic write-time validation for `lnk.type_id`, `left`, `right`,
  or `allowed_*_collections`.
- Do not join `lnk` endpoints to `loc` or `ent`, add `parent_location_id`,
  implement update/delete replay, backfill jobs, multi-transaction conflict
  resolution, authorization/scoping policy, or pagination/bulk policy.

DESIGN_NOTES:
- `TASK-010` accepted a Kafka-free, HTTP-free `ContentsLinkReadService` whose
  public methods already define the two initial query directions.
- Existing `CommittedTransactionReadController` is the closest precedent for a
  thin WebFlux read adapter over a service-boundary object.
- Because this creates a public HTTP surface, pre-work should stop with a
  concrete route and response proposal before implementation.

DEPENDENCIES:
- `TASK-010` is accepted and provides `ContentsLinkReadService` plus focused
  service-level coverage.

LATEST_REPORT:
Implementation landed on 2026-05-01 by claude-1.

`ContentsLinkReadController` was added under
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/` as a thin
WebFlux read adapter over `ContentsLinkReadService` with
`@RequestMapping('/api/contents')`, single-arg constructor injection, and two
`@GetMapping` methods returning `Flux<ContentsLinkRecord>` directly:

- `GET /api/contents/by-container/{id}` →
  `ContentsLinkReadService.findContents(id)` (forward, `lnk.left == id`).
- `GET /api/contents/by-content/{id}` →
  `ContentsLinkReadService.findLocations(id)` (reverse, `lnk.right == id`).

Both methods take `@PathVariable('id') String id` and unused
`@AuthenticationPrincipal Jwt jwt` for parity with
`CommittedTransactionReadController`. Empty service results map to HTTP 200
with `[]` (Spring WebFlux default for `Flux<T>`); blank or whitespace-only ids
flow through the service `Assert.hasText(...)` plus `GlobalExceptionHandler`
as 400 `ErrorResponse`. The controller has no Mongo/materializer/write-side
collaborator and adds no controller-side authorization, pagination, or
endpoint resolution policy.

`ContentsLinkReadControllerSpec` was added under
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/` using
`WebTestClient.bindToController(controller)` with the real
`GlobalExceptionHandler` and the
`AuthenticationPrincipalArgumentResolver`. It mocks only the
`ContentsLinkReadService` collaborator. Coverage mirrors
`CommittedTransactionReadControllerSpec` and adds the empty-array success
contract: forward and reverse JSON serialization (linkId, typeId, left, right,
nested `properties.position` plate-well coords, full `_jt_provenance`),
service-order preservation, service delegation with `0 * _`, 200-`[]` on
`Flux.empty()`, 400 `ErrorResponse` for whitespace-only ids in both
directions, a reflection assertion that the only constructor argument is
`ContentsLinkReadService`, and explicit literal route-path constants. Spec
runs 10 tests, all passing.

A short HTTP-route paragraph was appended to the existing
"Reading `contents` Links" section of
`docs/architecture/kafka-transaction-message-vocabulary.md` documenting the
two routes, the empty-array success contract, and the 400 path through
`GlobalExceptionHandler`. No new DTO, schema/example, build, Docker, security,
or frontend changes were made; service query semantics are unchanged and no
join to `loc`/`ent` was added.

Verification (Docker stack healthy with Mongo, Kafka, Keycloak):

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'` —
  BUILD SUCCESSFUL; 10/10 spec features pass.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL; 11 test suites, 107 tests,
  0 failures, 0 errors, 0 skipped, including the existing
  `CommittedTransactionReadControllerSpec` (5), `ContentsLinkReadServiceSpec`
  (18), `CommittedTransactionMaterializerSpec` (19),
  `CommittedTransactionReadServiceSpec` (12),
  `TransactionMessagePersistenceServiceSpec` (15), and
  `JadetipiApplicationTests.contextLoads`.
