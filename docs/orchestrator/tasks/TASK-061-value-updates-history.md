# TASK-061 - Property value updates and the hst history collection

ID: TASK-061
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-045
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-061-value-updates-history.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - DIRECTION.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the ratified value-update model (DIRECTION.md, Property Value
Updates And History): assignments become updatable with newest-commit-wins
current values on roots, every applied assignment preserved in the new
`hst` collection, and the `history: false` opt-out convention in the type
system. This clears the recorded prerequisite for real production import
(esp-over-clarity precedence).

DESIGN (ratified 2026-07-05):
- `hst` documents: `_id = msg_uuid` (an assignment event is uniquely its
  message — idempotent by construction), plus `object_id`,
  `object_collection`, `property_id`, `value`, `txn_id`, `commit_id`,
  `msg_uuid`, `applied_at`; compound index
  `(object_id, property_id, msg_uuid)` for chronological history
  retrieval, ensured at startup. `hst` is a special derived collection
  (like `txn`): no wire messages target it.
- Ordering: "newer" compares message UUIDv7s (time-ordered,
  lexicographically sortable). Discovery flagged for director review:
  `commit_id` is NOT lexicographically orderable (its random 16-letter
  prefix dominates string comparison), contradicting older "orderable
  commit_id" prose.
- Assignment semantics at materialization:
  - same `msg_uuid` as the current entry → idempotent `duplicate` when
    the payload matches, `conflict` when it differs (WAL-corruption
    guard) — plus a duplicate-tolerant `hst` re-insert to self-heal a
    missing history row;
  - different `msg_uuid`, incoming newer → append to `hst`, then replace
    the root's current entry → `applied`;
  - different `msg_uuid`, incoming older than current → append to `hst`
    only, current untouched → new terminal outcome
    `applied_historical` (new MaterializeResult counter, appended to the
    apply_state alignment so existing indices are stable);
  - every `hst` insert is duplicate-key-tolerant (redelivery no-op).
- History gating: enabled unless the nearest `history` declaration in
  the object's type chain says `false` (type root `properties.history`)
  or the registering `property_refs` entry carries `history: false`.
  Resolution rides the existing inheritance-aware registration walk.
  Opted-out assignments still update current; they write no `hst` row.
- `MongoDbInitializer` creates `hst` (backend collection, like `usr`)
  and ensures the compound index.
- No history read API this slice: the generic object-read route shape is
  an open director decision; `hst` is directly inspectable. Recorded as
  follow-on.

ACCEPTANCE_CRITERIA:
- Unit features cover: newer-commit replacement (current replaced, hst
  appended); older-commit arrival (hst only, `applied_historical`);
  same-message redelivery (duplicate, tolerant hst re-insert);
  same-message payload mismatch (conflict, unchanged); object-level and
  property-level `history: false` opt-outs (current updates, no hst
  write); nearest-declaration-wins across the chain; initializer
  creates `hst` + index.
- Existing assignment-conflict features updated to the new semantics.
- A Kafka integration spec proves it end to end: two transactions
  assigning the same property — the second becomes current with its
  provenance, and `hst` holds both assignments retrievable by
  (object, property) in commit order.
- Spec updated (§1.1 special collections + `hst`, §2.3.1 update
  semantics, §2.5 idempotency/consequence rewrite for values, version
  bump); UT-10 narrowed in place (property changes now propagate; root
  facts remain create-only); vocabulary doc assignment section updated;
  DIRECTION.md Bulk Import prerequisite note flipped to implemented.

OUT_OF_SCOPE:
- No root-document update semantics (creates stay create-only).
- No history read API (follow-on, pending the object-route decision).
- No history pruning/TTL/archival (the collection is isolated precisely
  so that can come later if warranted).

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented exactly as designed. Property assignments are now updatable
with newest-message-wins current values, every applied assignment is
preserved in the derived `hst` collection, and the `history: false`
opt-out convention rides the existing inheritance-aware registration
walk. The bulk-import prerequisite (esp-over-clarity precedence) is
cleared.

**FLAGGED FOR DIRECTOR REVIEW — commit_id is not orderable.** The
ratified model said "newest-commit-wins" and older spec prose calls
`commit_id` "orderable", but the current `IdGenerator` emits
`<16 random letters>~<epoch millis>~<3-letter sequence>` — the random
prefix makes the commit ID as a whole NOT lexicographically orderable
(the `<epoch>~<seq>` tail alone would order, but nothing extracts or
stores it on the Kafka-path header).
Ordering therefore compares the assignment's **message UUIDv7**
(time-ordered and lexicographically sortable by construction, and more
precise anyway: it orders assignments *within* a transaction too). The
spec 0.6.0 change note flags the stale "orderable commit_id" language
(§2.4 and the commit table) for your ruling: either fix the generator to
match the prose or retire the orderability claim. Nothing in the
implemented slice depends on commit-ID order.
*Update: ruled 2026-07-06 — commit IDs become UUIDv7, minted the same
way as transaction UUIDs, as the primitive for snapshot isolation (see
DIRECTION.md, Snapshot Isolation And Orderable Commit IDs; TASK-062
implements the ID change, TASK-063 the visibility rule, materialization
watermark, and transaction leases).*

PRODUCTION CHANGES:
- `CommittedTransactionMaterializer`: `projectPropertyValue` rewritten to
  the update model — same `msg_uuid` keeps the duplicate/conflict guard
  (payload match ignoring `applied_at` → `duplicateMatching` with a
  tolerant `hst` re-insert to self-heal a missing history row; mismatch →
  `conflictingDuplicate`, never overwritten); different `msg_uuid` and
  newer → `hst` append then current-entry replacement (`materialized`);
  different `msg_uuid` and older → `hst` append only, new counter
  `appliedHistorical` / apply_state `applied_historical`. History gating
  resolves along the registration chain walk via the new
  `PropertyRegistrationResolution` (property-level `history` on the
  registering `property_refs` entry is most specific, then the nearest
  object-level `properties.history` at or below the registering type,
  then enabled). `insertHistory` writes `_id = msg_uuid` +
  `object_id`/`object_collection`/`property_id`/`value` + full
  provenance, duplicate-key-tolerant.
- `MaterializeResult`: `appliedHistorical` counter, appended to
  `counters()` so existing apply_state indices are stable.
- `MongoDbInitializer`: `hst` added to backend collections; startup
  ensures the `(object_id, property_id, msg_uuid)` compound index.

TESTS (all green; full suites both modules, unit + integration,
`JADETIPI_IT_KAFKA=1`; `git diff --check` clean):
- New `CommittedTransactionMaterializerValueUpdateSpec` (6 features):
  newer replaces current + hst append; older → hst only
  (`applied_historical`); same-message redelivery duplicate with
  self-healing hst re-insert; first assignment; opt-out where-table
  (property-level false, object-level false, property true overriding
  object false, default on); nearest-declaration-wins across the chain
  (subtype `history: false` beats the registering ancestor's `true`).
- `CommittedTransactionMaterializerObjectPropertySpec`: conflict feature
  rewritten to the same-message semantics; insert assertions scoped to
  ignore `hst`.
- `MongoDbInitializerSpec`: exact-set feature includes `hst`; index
  ensure stubbed.
- New `PropertyValueUpdateKafkaIntegrationSpec` (live Kafka + Mongo):
  txn1 registers + assigns `received`; txn2 assigns `in_process` — the
  root's current entry carries the second value with txn2's provenance
  and `msg_uuid`, and `hst` holds BOTH assignments retrievable by
  `(object_id, property_id)` in `msg_uuid` order with correct values and
  transaction attribution.

DOCS:
- Spec → 0.6.0-draft: §1.1 three special collections (+`hst`), §2.3.1
  update semantics + opt-out convention, §2.5 value idempotency rewrite
  and re-import consequence (values propagate; roots stay create-only),
  §2.4 apply_state enumeration gains `applied_historical`, change note
  carries the commit_id-orderability flag, "ratified through TASK-061".
- UT-10 narrowed in place: "Structural ingestion is create-only; only
  property values propagate".
- Vocabulary doc: assignment-semantics section rewritten (update model,
  `hst` shape, gating); apply_state enumeration updated.
- DIRECTION.md: Bulk Import prerequisite note flipped to implemented.

DEFERRED (as scoped): history read API awaits the object-route director
decision; no pruning/TTL; root updates remain create-only.
