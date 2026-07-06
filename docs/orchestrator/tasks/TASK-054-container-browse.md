# TASK-054 - Paged container browse

ID: TASK-054
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: ACCEPTED
OWNER: claude
SOURCE_TASK:
  - TASK-053
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-054-container-browse.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - frontend/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - frontend-nextjs
  - gradle-verification

GOAL:
Make the TASK-053 container view self-serve: a paged browse over
materialized `loc` roots (`GET /api/locations`) and a browse list on the
`/containers` entry page, so users can discover containers instead of
needing an ID in hand.

DESIGN:
- Query-read convention: the browse is a query over the `loc` collection —
  HTTP 200 with an empty item list when nothing is materialized, never
  404. Deterministic ordering by `_id` ascending; `page` (0-based,
  clamped to >= 0) and `size` (clamped to 1..100, default 25) parameters;
  the response carries `items`, `page`, `size`, and the collection
  `total` so clients can page.
- Item summaries are tolerant of sparse roots, like the other readers:
  `locationId`, `typeId`, and the inline `name`/`description` when
  present (null otherwise). No property-values join — the detail view
  owns depth; browse owns discovery.
- Same security posture as every read route (bearer-authenticated, CORS
  already configured).
- Frontend: the `/containers` entry page lists the first page of
  materialized containers beneath the ID input, with pagination and
  links into the container view.

ACCEPTANCE_CRITERIA:
- `LocationBrowseReadService` with clamped paging, `_id`-ascending sort,
  tolerant summary mapping, and a total count;
  `GET /api/locations?page=&size=` controller following the existing
  thin-adapter convention.
- Unit features pin the query shape (sort, skip, limit), the clamping,
  the tolerant mapping of a sparse root, and the page envelope.
- An HTTP integration spec (Keycloak-gated, no Kafka needed) plants loc
  roots directly in MongoDB and proves: 200 with the planted summaries
  and page envelope via a bearer token, and 401 unauthenticated.
- The `/containers` entry page renders the browse list with pagination
  and links to `/containers/[id]`; `npm run build` and the Playwright
  suite stay green.
- Vocabulary doc's read-surface map gains the browse row; the
  specification's §3 query-read list mentions the paged browse (version
  bump).

OUT_OF_SCOPE:
- No filtering or search (name substring, type filter) — follow-on once
  browse exists.
- No browse endpoints for other collections (ent/prc/tsk/fil) — same
  recipe when needed.
- No cursor-based pagination; skip/limit is adequate at current scale.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `cd frontend && npm run build && npx playwright test`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- Backend: `LocationBrowseReadService` (defensive clamps — page floors
  at 0, size 1..100 with the clamped values echoed in the envelope;
  `_id`-ascending sort with skip/limit; `Mono.zip` of the item page and
  the collection count; summary mapping tolerates sparse/legacy roots by
  surfacing nulls) with `LocationSummaryRecord`/`LocationBrowseRecord`
  and the thin `LocationBrowseReadController`
  (`GET /api/locations?page=&size=`). No security or CORS changes — the
  route inherits the authenticated-by-default posture.
- Unit spec pins the query shape, the clamping table, the tolerant
  sparse-root mapping, and the envelope.
- Integration: `LocationBrowseHttpReadIntegrationSpec` — the first
  Keycloak-gated-but-Kafka-free HTTP spec (docker-stack opt-in flag +
  Keycloak probe; roots planted directly in Mongo). Proves the
  authenticated 200 with planted summaries and envelope, the 5000→100
  size clamp echo, and the unauthenticated 401 — all against real
  Keycloak tokens (3 tests, 0 failures in the full suite run).
- Frontend: `listLocations` + `LocationSummary`/`LocationBrowsePage`
  types in `lib/containers.ts` (query-read: a 404 here is an error, not
  null); the `/containers` entry page gains a "Materialized containers"
  browse list beneath the ID input — name/description summaries with
  monospace IDs linking into the container view, and Previous/Next
  pagination driven by the envelope's total.
- Docs: read-surface map gains the browse row (query-read: empty page,
  never 404); spec §3 query-read list mentions the paged browse
  (0.4.2-draft).
- Verification results: `:jade-tipi:test` green; full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` BUILD SUCCESSFUL
  (1m57s) with the new HTTP spec running live; frontend `npm run build`
  green and Playwright 15/15; `git diff --check` clean.

REVIEW_NOTE (2026-07-05, director): URLs must be lowercase/kebab-case,
never camelCase. A full audit of every mapped route and query parameter
(backend controllers, frontend fetch paths, frontend page routes) found
the surface already compliant — `/property-values`, `/by-container`,
`?page=&size=`, etc.; the convention is now recorded in the vocabulary
doc's read-surface section so future endpoints cannot drift. The
camelCase that prompted the review lives in JSON response *bodies*
(`locationId`, `propertyValues`, ...), a separate convention space
flagged to the director as an open casing decision (view-model camelCase
vs document snake_case).
