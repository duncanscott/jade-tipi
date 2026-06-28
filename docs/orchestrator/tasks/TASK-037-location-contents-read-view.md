# TASK-037 - Location contents read view

ID: TASK-037
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_REVIEW
OWNER: direct-codex
SOURCE_TASK:
  - TASK-036
  - TASK-035
  - TASK-034
  - TASK-033
  - TASK-027
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-036-kafka-container-review-seed.md
  - docs/orchestrator/tasks/TASK-037-location-contents-read-view.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification

GOAL:
Add the first backend read view that answers "show me this container/location
and its immediate contents" from materialized roots. This gives the seeded
TASK-036 container/sample data a reviewable API shape and creates the backend
surface a later UI can call before any broad CouchDB import or update workflow
is built.

ACCEPTANCE_CRITERIA:
- Add a read service that composes existing readers rather than querying all
  collections directly:
  - `LocationRootReadService.findLocation(locationId)` for the subject `loc`
    root.
  - `ContentsLinkReadService.findContents(locationId)` for immediate outgoing
    `contents` links.
  - `LocationRootReadService.findLocation(rightId)` and
    `EntityPropertyValuesReadService.findPropertyValues(rightId)` to resolve
    each link's `right` endpoint when the child is materialized as `loc` or
    `ent`.
- Return `Mono.empty()` when the subject `loc` root is missing; the HTTP
  adapter maps that to 404. This view differs from flat contents reads because
  its subject is explicitly a location record, not just an arbitrary endpoint
  id.
- Preserve each outgoing link even when the `right` endpoint is blank, missing,
  or not materialized in either `loc` or `ent`. The response must keep the raw
  `contentId` and link metadata with unresolved child fields set to null.
- Preserve `ContentsLinkReadService.findContents` ordering for the contents
  list.
- Include the subject location root, each link id/type id, raw `left` and
  `right` ids, verbatim `properties.position`, link provenance, and optional
  resolved child record:
  - `contentLocation` when the child resolves as `loc`.
  - `contentEntity` when the child resolves as `ent`.
- Expose the view through a thin WebFlux controller route:
  `GET /api/locations/{id}/contents`.
- Keep the controller free of Mongo/materializer/write-side collaborators.
- Add focused service and controller specs covering subject lookup, empty/missing
  subject behavior, loc child resolution, ent child resolution, unresolved child
  tolerance, ordering, provenance pass-through, blank-id rejection, route
  serialization, and controller delegation.

OUT_OF_SCOPE:
- No frontend UI.
- No Kafka submission, seed-data, DTO schema, materializer, or Mongo write
  changes.
- No recursive location-path or whole-container-tree walking; this is immediate
  children only.
- No child type inference beyond trying accepted `loc` and `ent` readers.
- No semantic validation of `contents` allowed endpoint collections.
- No authorization, pagination, conflict policy, or location move/update
  workflow.
- No integration test in this first bounded slice; the view composes
  existing integration-tested read services and the TASK-036 seed remains the
  manual JSON review point.

SELECTION_REASON:
TASK-036 proved that representative Clarity/ESP container and sample data can
be persisted through Kafka into MongoDB. The next useful backend step is not a
general importer yet; it is a stable read shape that lets a reviewer or UI ask
for one seeded container/location and see its immediate contained objects with
the materialized `loc`, `lnk`, and `ent` JSON reconciled into one response.

IMPLEMENTATION_SUMMARY:
- Added `LocationContentsReadService`, which composes
  `LocationRootReadService.findLocation(locationId)`,
  `ContentsLinkReadService.findContents(locationId)`, and child resolution via
  `LocationRootReadService` / `EntityPropertyValuesReadService`.
- Added `LocationContentsRecord` and `LocationContentsEntryRecord` for the
  response shape.
- Added `LocationContentsReadController` with
  `GET /api/locations/{id}/contents`, mapping a missing subject `loc` root to
  HTTP 404 while returning existing empty locations as `contents: []`.
- Preserved outgoing contents-link order, raw endpoint ids, verbatim
  object-shaped `properties.position`, link provenance, and unresolved child
  links.
- Updated the architecture vocabulary with the location-contents read contract.
- Addressed review feedback by adding a contents read-surface map documenting
  route placement and the intentional 200-vs-404 subject-existence asymmetry.

VERIFICATION_RESULTS:
- `./gradlew :jade-tipi:test --tests '*LocationContentsReadServiceSpec*' --tests '*LocationContentsReadControllerSpec*'`
  passed.
- `./gradlew :jade-tipi:test` passed.
- `git diff --check` passed.
