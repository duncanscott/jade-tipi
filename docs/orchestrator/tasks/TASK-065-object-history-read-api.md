# TASK-065 - Object assignment-history read API: /api/objects/{id}/history

ID: TASK-065
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-061
  - TASK-064
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-065-object-history-read-api.md
  - docs/jdtp-specification.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - DIRECTION.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - http-read-adapters
  - kafka-integration
  - gradle-verification

GOAL:
Expose the `hst` history collection (TASK-061) over HTTP as the sibling
of the generic property-values route: `GET /api/objects/{id}/history`
returns the object's applied assignments in message-UUID (chronological)
order, optionally narrowed to one property, paged. This was the recorded
follow-on deferred from TASK-061 pending the object-route decision,
which TASK-064 ratified.

DESIGN:
- Route: `GET /api/objects/{id}/history?property_id=&page=&size=` under
  the same TASK-064 dereference rule — the collection comes from the
  ID's collection segment (served set: ent, loc, prc, tsk, fil);
  malformed or unserved IDs are 404 with the read service untouched.
  `property_id` is optional and narrows to one property; `page`/`size`
  page the result (page floors at 0, size clamps to 1..100, defaults
  0/25 — the location-browse discipline), echoed in the envelope.
- Resource-read convention: a missing subject root is 404. An existing
  root with no history — including a type-system `history: false`
  opt-out — is 200 with an empty page, never an error.
- Response envelope: `object_id`, `collection`, `property_id` (the
  filter, null when absent), `items`, `page`, `size`, `total` (of the
  filtered set). Each item: `msg_uuid` (the entry's identity and
  ordering key), `property_id`, `property_name` (resolved from the
  definition, null when missing — the current-values reader's
  tolerance), `value` verbatim, `txn_id`, `commit_id`, `applied_at`.
- Ordering is ascending `msg_uuid` — assignment time, by UUIDv7
  construction. The startup initializer ensures a second `hst` index
  `(object_id, msg_uuid)` so object-wide pages sort on the index (the
  TASK-061 index `(object_id, property_id, msg_uuid)` serves the
  property-filtered form).
- The dereference helper is extracted to a shared
  `ObjectIdDereference` used by both `/api/objects` controllers.

ACCEPTANCE_CRITERIA:
- Unit: service features cover missing root → empty Mono; criteria and
  ascending-msg_uuid sort (with and without the property filter);
  paging skip/limit/clamping and the filtered total; name resolution
  incl. a missing definition → null name; stale-row tolerance (non-map
  value → empty map). Controller features cover 200 envelope, 404
  malformed/unserved (service untouched), 404 missing root, and
  parameter passthrough.
- A Keycloak+Kafka HTTP integration spec proves it live: two
  transactions assign the same property (TASK-061 update model); the
  history route returns BOTH assignments in msg-UUID order with values,
  provenance, and resolved names; the `property_id` filter returns the
  same rows and a foreign property filter returns an empty page with
  total 0; a malformed ID and a missing root both 404.
- Docs: spec §3 resource reads gain the history read (0.7.2-draft +
  change note, "ratified through TASK-065"); vocabulary doc gains the
  history-read section and route-table row; DIRECTION.md Object Read
  Routes notes the history API as implemented.

OUT_OF_SCOPE:
- UI history view (rides with the prc/tsk/fil object-view work).
- History pruning/TTL (deliberately isolated in `hst`).
- Point-in-time value reconstruction ("value as of commit X") — a
  future read once a need exists; the raw trail is the contract here.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as designed. `GET /api/objects/{id}/history` serves the
`hst` trail under the TASK-064 dereference rule, chronological by
message UUID, with the optional `property_id` filter and browse-style
defensive paging. All suites green in both modules (unit + integration
with `JADETIPI_IT_KAFKA=1`; the new Keycloak+Kafka HTTP spec ran live,
no skips); `git diff --check` clean.

CHANGES:
- New `ObjectHistoryReadService`: root-presence gate (empty Mono → 404
  at the route), object-scoped criteria (+ optional property), ascending
  `msg_uuid` sort, skip/limit with page≥0 and size clamped 1..100
  echoed in the envelope, filtered total, and the current-values
  reader's tolerant name resolution (missing definition → null name;
  non-map value → empty map). New `ObjectHistoryRecord` /
  `ObjectHistoryEntryRecord` (snake_case on the wire via the global
  Jackson strategy).
- New `ObjectHistoryReadController` under `/api/objects`; the TASK-064
  dereference logic is extracted to a shared `ObjectIdDereference`
  helper now used by both `/api/objects` controllers (behavior
  unchanged; the property-values controller spec passes untouched).
- `MongoDbInitializer` ensures a second `hst` index
  `(object_id, msg_uuid)` so object-wide pages sort on an index (the
  TASK-061 three-field index serves the property-filtered form).

TESTS:
- `ObjectHistoryReadServiceSpec` (6 features): missing root → empty and
  hst untouched; chronological entries with names/provenance and the
  captured query's criteria/sort/skip/limit; property filter narrows
  items and total; paging where-table (floor/clamp/echo); null name +
  empty-map value tolerance; unsupported collection rejected.
- `ObjectHistoryReadControllerSpec` (4 features): 200 envelope;
  filter/paging passthrough (`0 * _` beyond the reader); 404 missing
  root; 404 malformed/unserved with the service untouched.
- `ObjectHistoryHttpReadIntegrationSpec` (live, Keycloak+Kafka): two
  transactions assign the same property; the unfiltered history returns
  BOTH assignments in msg-UUID order with resolved names, values, and
  per-transaction provenance; the property filter echoes and matches; a
  foreign-property filter is an empty page with total 0; a missing root
  and a malformed ID both 404.

DOCS:
- Spec → 0.7.2-draft: §3 resource reads gain the history read and its
  contract-of-note; change note; "ratified through TASK-065".
- Vocabulary doc: "Reading Object Assignment History" section and
  route-table row.
- DIRECTION.md Object Read Routes: history API recorded as implemented;
  remaining follow-on is the prc/tsk/fil UI views.

NOTES:
- Point-in-time reconstruction ("value as of commit X") stays out of
  scope until a need exists; the raw chronological trail is the
  contract. With UUIDv7 commit ids (TASK-062) it would be a
  straightforward filter later.
