# TASK-057 - Entity object view and drill-through

ID: TASK-057
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-053
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-057-entity-object-view.md
  - frontend/
REQUIRED_CAPABILITIES:
  - frontend-nextjs
  - gradle-verification

GOAL:
Close the drill-through dead end in the Track-1 UI: entities shown in a
container's plate wells and contents table are labels only — they link
nowhere. Add an entity object view (`/objects/[id]`) showing the typed
`ent` root, its property values with provenance, its effective type
properties, and where it is located (with links back into the container
view), and make every entity reference in the container view a link. The
navigation loop closes in both directions: container → entity → its
containers.

DESIGN:
- Read-only; consumes only existing routes:
  `GET /api/entities/{id}/property-values`,
  `GET /api/types/{id}/effective-properties`,
  `GET /api/contents/by-content/{id}/locations` (resolved containers with
  positions — query-read, 200 with empty `locations`).
- `/objects/[id]` follows the established page conventions (sign-in gate,
  panels, provenance columns, chain-complete warning). The "Located in"
  section lists each containing location with its position and links to
  `/containers/[containerId]`.
- The container view's well chips and flat-content entity rows become
  links to `/objects/[id]`; location rows keep linking to
  `/containers/[id]`.
- No backend changes. Entity-only for now: `prc`/`tsk`/`fil` have no
  per-collection HTTP read routes yet (the generic reader supports them;
  exposing routes and views is follow-on work, recorded here).

ACCEPTANCE_CRITERIA:
- `/objects/[id]` renders identity, property values with provenance,
  the type panel, and resolved locations with container links; a missing
  `ent` root renders "not materialized" distinctly from errors.
- Every entity reference in the container view links to the object view.
- Typed client additions mirror the backend records (`ObjectLocations`,
  `ObjectLocationEntry`); 404 → null convention unchanged.
- Playwright coverage in the unauthenticated style; `npm run build` and
  the suite pass; the backend build is untouched.

OUT_OF_SCOPE:
- No prc/tsk/fil object views or routes (follow-on; needs either
  per-collection routes or a generic `/api/objects/...` route decision).
- No link-type-generic relationship browser (locations only — the
  `contents`-kind resolution the backend already provides).
- No search.

VERIFICATION:
- `cd frontend && npm run build && npx playwright test`
- `./gradlew :jade-tipi:test` (backend untouched — sanity only)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- `lib/containers.ts` gains `ObjectLocations`/`ObjectLocationEntry`
  (snake_case, mirroring the backend records) and
  `getObjectLocations` over
  `GET /api/contents/by-content/{id}/locations` (query-read: always
  200; an unknown object simply has no locations).
- New `/objects/[id]` page following the established conventions:
  identity header (name, full ID, collection, resolved type name),
  property values with provenance columns, the type panel with
  inheritance chain and the chain-complete warning, and a "Located in"
  panel listing each containing location with its position — linking
  back to `/containers/[containerId]`. A missing `ent` root renders
  "Object not materialized" distinctly from errors.
- Drill-through wired in the container view: plate well chips became
  links to `/objects/[id]` (unchanged visual, now navigable), and
  flat-content entity rows link to the object view while location rows
  keep linking into the container view. The navigation loop closes:
  freezer → rack → plate → well entity → its containers.
- New `tests/objects.spec.ts` in the unauthenticated sign-in-gate style.
- No backend changes. Recorded follow-on: prc/tsk/fil object views need
  either per-collection read routes or a generic
  `/api/objects/{collection}/{id}/property-values` route — an API-shape
  decision for the director when those views are wanted.
- Verification results: `npm run build` green; Playwright 16/16;
  `:jade-tipi:test` untouched-green; `git diff --check` clean.
