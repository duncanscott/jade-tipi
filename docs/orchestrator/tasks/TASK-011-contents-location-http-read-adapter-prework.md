# TASK-011 - Plan contents location HTTP read adapter

ID: TASK-011
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-012
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
Director implementation review on 2026-05-01:
- Accepted claude-1 implementation commit `24f1fcb`.
- Scope check passed against claude-1's base assignment plus the active task
  expansion. The implementation changed only `docs/agents/claude-1-changes.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, this task file,
  `ContentsLinkReadController.groovy`, and
  `ContentsLinkReadControllerSpec.groovy`. The code/doc/task edits are outside
  the base report-only assignment paths but inside the explicit `TASK-011`
  owned-path expansion in this task and `DIRECTIVES.md`.
- Acceptance criteria are satisfied. `ContentsLinkReadController` exposes
  `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` under `/api/contents`, delegates directly
  to `ContentsLinkReadService.findContents(id)` and `findLocations(id)`, returns
  `Flux<ContentsLinkRecord>` as a flat JSON array, preserves service order, and
  relies on service `Assert.hasText(...)` plus `GlobalExceptionHandler` for
  blank-id 400 `ErrorResponse` handling.
- The implementation honored `DIRECTIVES.md`: both routes keep
  `@AuthenticationPrincipal Jwt jwt`, no controller-side authorization/scoping
  policy was added, no response envelope, pagination policy, controller DTO,
  integration test, endpoint join to `loc`/`ent`, Mongo/materializer/write-side
  collaborator, service semantic change, schema/example/build/Docker/frontend
  change, or semantic write-time validation was introduced.
- Required assertions are present in `ContentsLinkReadControllerSpec`: route
  binding, forward and reverse service delegation with `0 * _`, JSON
  serialization of link fields plus nested `properties.position` and provenance
  content, order preservation, `Flux.empty()` as HTTP 200 with `[]`, blank-id
  400 responses through the real global handler in both directions, and a
  reflection assertion that the controller constructor takes only
  `ContentsLinkReadService`.
- The short architecture-doc addition is tightly scoped to the existing
  "Reading `contents` Links" section and documents only the two HTTP routes,
  empty-array success contract, blank-id 400 path, and thin-controller boundary.
- Director local verification partially passed: `./gradlew
  :jade-tipi:compileGroovy` succeeded. Further local verification was blocked
  before product tests by sandbox/tooling permissions, not by an observed
  product failure: `./gradlew :jade-tipi:compileTestGroovy` failed opening the
  Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal
  developer shell, use the project-documented setup command
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then
  run `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew
  :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`, and `./gradlew
  :jade-tipi:test`.
- Credited developer verification: claude-1 reported the Docker stack healthy
  and `./gradlew :jade-tipi:compileGroovy`, `./gradlew
  :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests
  '*ContentsLinkReadControllerSpec*'`, and `./gradlew :jade-tipi:test` all
  passing with the full unit suite at 107 tests, 0 failures.
- Follow-up: `TASK-012` was created for pre-work on opt-in end-to-end
  integration coverage proving canonical contents messages can flow through
  Kafka ingestion, commit materialization, and the new HTTP read routes.
