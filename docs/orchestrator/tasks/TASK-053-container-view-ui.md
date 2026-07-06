# TASK-053 - Track-1 container view UI

ID: TASK-053
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-041
  - TASK-042
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-053-container-view-ui.md
  - frontend/
REQUIRED_CAPABILITIES:
  - frontend-nextjs
  - gradle-verification

GOAL:
The first Track-1 UI surface: a read-only container view over the existing
TASK-041/042 read APIs. Given a `loc` ID, the page shows the typed root,
its projected property values **with provenance** (the transparency
promise made visible), its effective type properties across the
inheritance chain, and its contents — a plate-shaped grid for plate-typed
containers, a flat list otherwise. This makes the whole pipeline (Kafka →
WAL → background worker → Mongo roots → HTTP reads) demonstrable end to
end without reading MongoDB by hand.

DESIGN:
- Read-only; consumes only existing endpoints, all Keycloak
  bearer-authenticated like the rest of the frontend:
  `GET /api/locations/{id}/property-values`,
  `GET /api/types/{id}/effective-properties`,
  `GET /api/contents/plate/{id}`,
  `GET /api/locations/{id}/contents`,
  `GET /api/entities/{id}/property-values` (drill-down labels come from
  the records already embedded in the contents responses).
- Follows the established frontend conventions: client components with
  `useSession` access tokens, the `useTools` sidebar, CSS-variable
  styling, sign-in gate when unauthenticated.
- `/containers` — entry page with an ID input (and the demo/runbook IDs
  as guidance); `/containers/[id]` — the container view. Content entries
  that are themselves locations link onward to their own container view
  (drill-down navigation).
- Grid-vs-list: the plate endpoint's record carries `rowCount` /
  `columnCount`; when present the grid renders (with unplaced contents
  listed beneath), otherwise the flat location-contents list renders.
- Provenance is first-class in the UI: each property value shows its
  `txn_id`, `commit_id`, and `applied_at`; each effective property shows
  the type that registered it; a broken type chain
  (`chainComplete: false`) is surfaced as a warning, not hidden.
- No backend changes expected (CORS already configured; endpoints
  authenticated).

ACCEPTANCE_CRITERIA:
- A typed API module for the read endpoints (TypeScript interfaces
  mirroring the read records; 404 resolves to null rather than throwing).
- `/containers` and `/containers/[id]` pages following the existing
  auth-gate and layout conventions, with a `Containers` link in the
  header nav.
- The container view renders: root identity (name, ID, collection, type),
  property values with provenance, effective type properties with source
  attribution and the chain-complete warning, and contents as plate grid
  or flat list with drill-down links to contained locations.
- Playwright coverage in the existing unauthenticated style: the nav link
  is present, `/containers` and `/containers/[id]` show the sign-in gate
  when unauthenticated.
- `npm run build` and the Playwright suite pass; the backend build is
  untouched.

OUT_OF_SCOPE:
- No writes, no transaction submission from the UI.
- No search/browse index of containers (entry is by ID; the read APIs
  have no list-all endpoint yet).
- No entity-centric or procedure/task views (follow-on screens).
- No visual design system work beyond the established inline-style
  conventions.

VERIFICATION:
- `cd frontend && npm run build`
- `cd frontend && npx playwright test`
- `./gradlew :jade-tipi:test` (backend untouched — sanity only)
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- `frontend/lib/containers.ts`: typed client for the five read endpoints
  with TypeScript interfaces mirroring the backend read records
  (ObjectPropertyValues, TypeEffectiveProperties, PlateContents + wells
  and entries, LocationContents + entries, LocationRoot). Field casing
  verified against the HTTP integration specs' jsonPath assertions
  (camelCase: `objectId`, `propertyValues`, `txnId`, `chainComplete`,
  ...), which ran green in today's full suite. 404 resolves to null
  ("not materialized"), other non-2xx throws; IDs are
  URI-encoded into paths. `displayName` falls back from
  `properties.name` to the ID suffix.
- `/containers` entry page: ID input with the object identifier
  convention as placeholder, pointers to the kli plate runbook and the
  Clarity/ESP review seed as sources of openable IDs; standard sign-in
  gate.
- `/containers/[id]` container view: header (name, full ID, collection,
  resolved type name); property-values table with provenance columns
  (`txn_id`, `commit_id`, `applied_at`) — the transparency promise on
  screen; type panel with the inheritance chain (root → subtype), a
  visible warning when `chainComplete` is false, and effective
  properties attributed to the registering type; contents rendered as a
  plate grid (row/column labels from the plate record, occupant chips in
  wells, unplaced contents with reasons listed beneath) when
  `rowCount`/`columnCount` are present, else a flat contents table with
  drill-down links to contained locations (loc entries navigate to their
  own container view; ent entries are labeled from their embedded
  property-values record). Root fetch, plate fetch, and flat-contents
  fetch run in parallel; the type fetch follows from the root's type_id.
- Header nav gains `Containers`; new `tests/containers.spec.ts` follows
  the established unauthenticated style (nav link visible; entry and
  detail pages show the sign-in gate). Authenticated rendering rides on
  the backend HTTP read specs for contract correctness; a live
  end-to-end demo needs the docker stack + Keycloak sign-in (out of
  automated scope, consistent with the existing suites).
- No backend changes: all endpoints, auth, and CORS already existed.
- Verification results: `npm run build` succeeds with both routes;
  Playwright 15/15 passed (12 existing + 3 new, 4.7s);
  `:jade-tipi:test` untouched-green; `git diff --check` clean.
