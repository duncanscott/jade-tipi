# TASK-011 - Plan contents location HTTP read adapter

ID: TASK-011
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
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
Director pre-work review passed on 2026-05-01. Scope check passed: claude-1's
latest pre-work changed only `docs/agents/claude-1-next-step.md`, inside the
developer-owned pre-work paths. Implement the default proposal from the pre-work:
add one thin `ContentsLinkReadController` under `/api/contents` with
`GET /api/contents/by-container/{id}` delegating to
`ContentsLinkReadService.findContents(id)` and
`GET /api/contents/by-content/{id}` delegating to
`ContentsLinkReadService.findLocations(id)`.

Return `Flux<ContentsLinkRecord>` directly as a flat JSON array. Empty service
results must be HTTP 200 with `[]`; blank or whitespace-only ids should rely on
the service `Assert.hasText(...)` plus `GlobalExceptionHandler` to return 400
`ErrorResponse`. Keep `@AuthenticationPrincipal Jwt jwt` for parity with the
existing authenticated read controller, but do not add controller-side
authorization/scoping policy.

Add focused pure WebFlux controller coverage modeled on
`CommittedTransactionReadControllerSpec`: route binding, service delegation in
both directions, JSON serialization including nested `properties` and
`provenance`, service-order preservation, empty-array behavior, blank-id 400 via
the real global handler, and a reflection/no-collaborator assertion proving the
controller constructor takes only `ContentsLinkReadService`. Do not add an
integration test in this task.

Implementation may optionally append a short HTTP-route paragraph to
`docs/architecture/kafka-transaction-message-vocabulary.md`; do not add a new
HTTP DTO unless implementation reveals a concrete serialization problem.
Preserve all out-of-scope boundaries: no service query semantic changes, no
Mongo/materializer/write-side collaborators in the controller, no joins to
`loc` or `ent`, no schema/example/build/Docker/security/frontend changes, no
pagination policy, and no semantic write-time validation.

Required verification after implementation:
`./gradlew :jade-tipi:compileGroovy`,
`./gradlew :jade-tipi:compileTestGroovy`,
`./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`, and
`./gradlew :jade-tipi:test`. If Mongo-backed tests fail because Mongo is
unavailable, use the project-documented setup command
`docker compose -f docker/docker-compose.yml --profile mongodb up -d` and report
setup/tooling blockers separately from product failures.
