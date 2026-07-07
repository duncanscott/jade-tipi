# TASK-064 - Generic object read route: /api/objects/{id}/property-values

ID: TASK-064
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-041
  - TASK-057
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-064-generic-object-read-route.md
  - docs/jdtp-specification.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - DIRECTION.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - frontend/
REQUIRED_CAPABILITIES:
  - http-read-adapters
  - gradle-verification

GOAL:
Implement the director-ratified object read route (2026-07-07): one
generic `GET /api/objects/{id}/property-values` for every
property-value-bearing collection, dereferencing the Mongo collection
from the ID itself — no collection in the path. This unblocks the
history read API and the prc/tsk/fil object views as follow-ons.

DESIGN (ratified 2026-07-07):
- The object ID is the complete address. Every object ID is exactly five
  tilde-separated segments (`<org>~<grp>~<uuidv7>~<collection>~<suffix>`,
  schema-enforced since TASK-046; the `genesis` exception is irregular in
  the UUID position only), and every segment charset is URL-path-safe, so
  the route dereferences by the ID's collection segment. Stating the
  collection in the path would say it twice and invent a mismatch error
  case; the single-segment route has nothing to adjudicate.
- Dereference rule: split on `~`; exactly five segments; the fourth must
  be one of the property-value-bearing collections
  (`ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS`: ent, loc,
  prc, tsk, fil). Anything else — wrong segment count, unknown or
  non-value-bearing collection — is **404, never a guess** (and never a
  500 from the service's own assertion).
- Spec reconciliation: §2.3.1's "MUST NOT infer a collection by parsing
  `object_id`" is a WRITE-path rule — assignment messages carry
  `object_collection` explicitly, and inference must not sneak in as a
  fallback. For resource READS the URL carries only the ID, and
  segment-parsing is the sanctioned dereference mechanism. The spec §3
  states this distinction explicitly so the rules do not read as a
  contradiction.
- The per-collection routes `GET /api/entities/{id}/property-values` and
  `GET /api/locations/{id}/property-values` are retired in the same
  change (nothing external consumes them); the frontend's two call
  sites switch to the generic route via one `getObjectPropertyValues`
  client function.

ACCEPTANCE_CRITERIA:
- New `ObjectPropertyValuesReadController` at
  `/api/objects/{id}/property-values`: 200 with the generic record for
  each of the five collections (delegation with the parsed collection),
  404 for a missing root, 404 for malformed IDs (wrong segment count,
  unknown collection segment, non-value-bearing collection such as
  `typ`) without ever touching the read service.
- `EntityPropertyValuesReadController` and
  `LocationPropertyValuesReadController` (and their unit specs) are
  deleted; the two Keycloak-gated HTTP integration specs exercise the
  generic route instead, and one of them covers the malformed-ID 404.
- Frontend `lib/containers.ts` exposes `getObjectPropertyValues`; the
  container and object pages use it; the retired functions are gone;
  `npm run build` (and the Playwright gate suite) stay green.
- Spec: §3 resource-reads updated with the object-ID dereference rule
  and its relationship to §2.3.1's write-path rule; version bumped to
  0.7.1-draft with a change note; "ratified through TASK-064".
- Vocabulary doc read-route mentions updated.
- DIRECTION.md records the ratified route decision.

OUT_OF_SCOPE:
- The history read API (`/api/objects/{id}/history` sibling) — next
  slice, now unblocked.
- prc/tsk/fil UI views (unblocked, separate task).
- Any change to the write path or the wire vocabulary
  (`object_collection` stays explicit on assignment messages).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `cd frontend && npm run build && npx playwright test`
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as ratified. `GET /api/objects/{id}/property-values` is the
one object property-values route; the collection is dereferenced from
the ID's collection segment (exactly five tilde-separated segments,
fourth validated against
`ObjectPropertyValuesReadService.SUPPORTED_COLLECTIONS`), and anything
malformed or unserved is 404 before the read service is ever touched —
no mismatch case exists because the collection is stated exactly once.
The per-collection entity/location controllers are deleted. All suites
green: backend unit + integration (`JADETIPI_IT_KAFKA=1`, both
Keycloak-gated HTTP itests ran live against the new route), frontend
`npm run build`, and the 16-test Playwright gate suite; `git diff
--check` clean.

CHANGES:
- New `ObjectPropertyValuesReadController` (`/api/objects`), a thin
  adapter over the existing generic read service with the dereference
  helper; `EntityPropertyValuesReadController` and
  `LocationPropertyValuesReadController` (and their two unit specs)
  deleted.
- New `ObjectPropertyValuesReadControllerSpec`: 200 with the generic
  record; delegation with the parsed collection for each of ent, loc,
  prc, tsk, fil (where-table, `0 * _` beyond the reader); 404 for a
  missing root; 404 for malformed/non-dereferenceable IDs (segment
  counts, `typ`, unknown segment, no separators) with the read service
  never invoked.
- HTTP integration specs repointed to the generic route; the plate spec
  now covers both 404 flavors (well-formed ID with no root, malformed
  ID).
- Frontend: one `getObjectPropertyValues` in `lib/containers.ts`
  replaces the two per-collection functions; the container and object
  pages updated; nothing else consumed the old routes.
- Spec → 0.7.1-draft: §3 gains the Normative object-ID dereference rule
  (with the explicit non-contradiction note against §2.3.1's write-path
  rule, and a matching pointer added at §2.3.1); "ratified through
  TASK-064". Vocabulary doc read-adapter passages and route table
  updated. DIRECTION.md records the ruling under "Object Read Routes".

NOTES:
- prc/tsk/fil property values are now readable over HTTP for the first
  time (the reader always supported them; no route did).
- Unblocked follow-ons: the history read API as the sibling route
  (`/api/objects/{id}/history` over `hst`, msg-UUID order), and
  prc/tsk/fil object views in the UI.
