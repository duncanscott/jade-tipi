# TASK-035 - Resolved object locations read view

ID: TASK-035
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_REVIEW
OWNER: direct-codex
SOURCE_TASK:
  - TASK-034
  - TASK-016
  - TASK-015
  - TASK-026
NEXT_TASK:
  - TASK-036
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-034-plate-shaped-contents-read-view.md
  - docs/orchestrator/tasks/TASK-035-resolved-object-locations-read-view.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification

GOAL:
Add the resolved reverse read model described by `DIRECTION.md` Query
Direction: answer "where is this sample/object located?" by composing the
accepted flat reverse `contents` link read with a bounded materialized `loc`
root reader.

ACCEPTANCE_CRITERIA:
- Reuse `ContentsLinkReadService.findLocations(objectId)` for the canonical
  reverse `contents` link lookup; do not query `lnk` directly in the resolved
  view.
- Add a reusable `LocationRootReadService.findLocation(locationId)` that reads
  one materialized `loc` root by `_id` and maps `_head.provenance`.
- Resolve each reverse contents link's `left` endpoint as a containing `loc`
  root when present.
- Return a deterministic object-locations response preserving the flat
  reverse-link service order.
- Preserve links whose `left` endpoint is missing, blank, or points at a
  missing `loc` root; do not drop the link. Return those entries with
  `container == null`.
- Preserve each link's source id, type id, `left` endpoint as `containerId`,
  verbatim `properties.position` object when present, and link provenance.
- Expose the view through a thin WebFlux controller route and keep the
  controller free of Mongo/materializer/write-side collaborators.
- Add focused service and controller specs for location-root mapping,
  provenance mapping, empty-result behavior, missing-container tolerance,
  missing-left behavior, blank-id rejection, route serialization, and
  delegation.

OUT_OF_SCOPE:
- No frontend UI.
- No Kafka submission, DTO schema, materializer, or Mongo write changes.
- No recursive location path walking beyond the immediate containing `loc`.
- No lookup or validation of the content object's own root; `200` with
  `locations: []` means no contents links were found, not that the object
  exists.
- No semantic validation of `contents` allowed endpoint collections, no
  location conflict policy, no authorization, and no pagination.
- No integration test in this first bounded slice; the service composes
  existing integration-tested read services and a narrow `loc` root reader.

IMPLEMENTATION_SUMMARY:
- Added `LocationRootReadService` and `LocationRootRecord` for reusable
  materialized `loc` root reads.
- Added `ObjectLocationsReadService`, which composes
  `ContentsLinkReadService.findLocations(objectId)` and
  `LocationRootReadService.findLocation(leftId)`.
- Added `ObjectLocationsRecord` and `ObjectLocationEntryRecord`.
- Added `ObjectLocationsReadController` with
  `GET /api/contents/by-content/{id}/locations`.
- Updated the architecture vocabulary with the resolved object-locations
  contract.
- Marked `TASK-034` accepted and linked this task as its follow-up.
- Addressed review feedback by renaming the route before client adoption so
  `{id}` clearly remains the content/object id from the existing flat
  `/api/contents/by-content/{id}` route, and by documenting why `locations[]`
  entries expose `containerId` and `container`.

VERIFICATION_RESULTS:
- `./gradlew :jade-tipi:test --tests '*LocationRootReadServiceSpec*' --tests '*ObjectLocationsReadServiceSpec*' --tests '*ObjectLocationsReadControllerSpec*'`
  passed.
- `./gradlew :jade-tipi:test` passed.
- `git diff --check` passed.
