# TASK-034 - Plate-shaped contents read view

ID: TASK-034
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_REVIEW
OWNER: direct-codex
SOURCE_TASK:
  - TASK-033
  - TASK-016
  - TASK-015
  - TASK-027
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-033-entity-property-values-read-service.md
  - docs/orchestrator/tasks/TASK-034-plate-shaped-contents-read-view.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification

GOAL:
Add the first backend-only plate-shaped contents read model described by
`DIRECTION.md` Query Direction: answer "what are the contents of this 96-well
plate?" by composing the accepted flat `contents` link read with the accepted
entity property-values read.

ACCEPTANCE_CRITERIA:
- Reuse `ContentsLinkReadService.findContents(containerId)` for the canonical
  `contents` link lookup; do not query `lnk` directly in the plate view.
- Reuse `EntityPropertyValuesReadService.findPropertyValues(rightId)` to
  resolve each contained entity's materialized property values.
- Return a fixed 96-well view with row labels `A` through `H`, column labels
  `1` through `12`, and row-major `wells` output.
- Use each link's `properties.position` object when
  `kind == "plate_well"` and the row/column are in range. Place multiple links
  in the same well as a list in service order.
- Preserve links that cannot be placed under `unplacedContents`; do not drop
  invalid, missing, or out-of-range position data. Include an `unplacedReason`
  enum on those entries.
- Tolerate a missing entity root for a link's `right` endpoint by returning
  the content entry with `entity == null`.
- Expose the view through a thin WebFlux controller route and keep the
  controller free of Mongo/materializer/write-side collaborators.
- Add focused service and controller specs for grid construction, placement,
  duplicates, missing entities, unplaced links, blank id rejection, route
  serialization, and delegation.

OUT_OF_SCOPE:
- No frontend UI.
- No Kafka submission, DTO schema, materializer, or Mongo write changes.
- No generalized geometry model beyond fixed 96-well plates.
- No lookup or validation of the container `loc` root; `200` with an empty
  grid means no contents links were found, not that the plate root exists.
- No authorization, pagination, plate conflict policy, or semantic repair of
  malformed links.
- No integration test in this first bounded slice; the service composes
  existing integration-tested read services.

IMPLEMENTATION_SUMMARY:
- Added `PlateContentsReadService`, which composes `ContentsLinkReadService`
  and `EntityPropertyValuesReadService` to build a fixed 96-well read model.
- Added `PlateContentsRecord`, `PlateContentsWellRecord`, and
  `PlateContentsEntryRecord`, including explicit column labels and per-entry
  unplaced reasons.
- Added `PlateContentsReadController` with
  `GET /api/contents/plate/{id}`.
- Added focused service and controller specs covering the accepted behavior.
- Updated the architecture vocabulary with the plate-shaped contents contract.
- Addressed review feedback by documenting the no-`loc`-lookup `200` semantics,
  adding `columnLabels`, reporting distinct `unplacedReason` values, and
  simplifying the link stream processing.
- Marked `TASK-033` accepted and linked this task as its follow-up.

VERIFICATION_RESULTS:
- `./gradlew :jade-tipi:test --tests '*PlateContentsReadServiceSpec*' --tests '*PlateContentsReadControllerSpec*'`
  passed.
- `./gradlew :jade-tipi:test` passed.
- `git diff --check` passed.
