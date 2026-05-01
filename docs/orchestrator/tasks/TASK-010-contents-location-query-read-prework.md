# TASK-010 - Plan contents location query reads

ID: TASK-010
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Plan the smallest read/query path over the materialized `loc`, `typ`, and `lnk`
collections so callers can answer the two `DIRECTION.md` contents questions:
"what are the contents of this plate/location?" and "where is this sample or
object located?"

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `CommittedTransactionMaterializer`, `CommittedTransactionReadService`, the
  materializer tests, existing controller/service test patterns, and any
  existing Mongo query helpers before proposing implementation.
- Pre-work proposes the narrowest backend boundary for the first query unit:
  a Kafka-free service over materialized collections, a thin HTTP adapter over
  that service, or a smaller existing integration point if source inspection
  shows one is better.
- Pre-work defines the first supported query shapes, including whether the
  initial unit should cover both forward contents lookup (`lnk.type_id` for
  `contents` and `left == containerId`) and reverse location lookup
  (`right == objectId`) or only one of them.
- Pre-work specifies the response shape, ordering, missing-container/object
  behavior, unresolved endpoint behavior, duplicate/conflicting link behavior,
  and how to resolve the canonical `contents` link type without adding new
  semantic validation on writes.
- Pre-work proposes focused unit/integration assertions and exact Gradle
  verification commands. If local tooling, Gradle locks, or Docker/Mongo are
  unavailable, report the documented setup command rather than treating setup
  as a product blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change transaction persistence, committed snapshot shape, or
  materialization behavior from `TASK-009`.
- Do not add semantic write-time validation for `lnk.type_id`, `left`, `right`,
  or `allowed_*_collections`.
- Do not add update/delete replay, backfill jobs, background workers,
  multi-transaction conflict resolution, or authorization/scoping policy.
- Do not rebuild HTTP submission wrappers, Kafka listener/topic configuration,
  DTO schemas/examples, Docker Compose, or build configuration.
- Do not add `parent_location_id` to `loc` records; containment remains
  canonical in `lnk`.
- Do not implement UI work.

DESIGN_NOTES:
- `TASK-009` materializes committed `loc`, `contents` link-type `typ`, and
  `lnk` creates into long-term collections. This task should read those
  projections; it should not mutate or re-materialize them.
- `DIRECTION.md` defines the intended query model: plate contents are found by
  looking up `contents` links whose `left` is the container location, and
  reverse location lookup uses `contents` links whose `right` is the object.
- The first implementation should prefer a service-level boundary unless
  pre-work shows a thin controller is necessary for useful verification.

DEPENDENCIES:
- `TASK-009` is accepted and provides committed materialization for `loc`,
  `typ` link-type declarations, and `lnk` records.

LATEST_REPORT:
Created by the director on 2026-05-01 after accepting `TASK-009`. Ready for
developer pre-work only; implementation is not yet authorized.
