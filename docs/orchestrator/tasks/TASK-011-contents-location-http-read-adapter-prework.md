# TASK-011 - Plan contents location HTTP read adapter

ID: TASK-011
TYPE: implementation
STATUS: READY_FOR_PREWORK
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
Created by director on 2026-05-01 after accepting `TASK-010`. This is the next
bounded location-modeling unit within the current project goal.
