# TASK-009 - Plan committed location/link materialization

ID: TASK-009
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-009-committed-location-link-materialization-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Prepare the smallest backend materialization unit that can copy committed
transaction messages for the accepted location/link vocabulary into their
long-term MongoDB collections, so `loc`, `typ` link-type declarations, and
`lnk` relationship records can exist outside the `txn` write-ahead log before
reader/query APIs are designed.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, the canonical
  examples `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json`,
  `CommittedTransactionReadService`, `TransactionMessagePersistenceService`,
  Mongo collection/initializer code, and existing backend service/test patterns.
- Pre-work proposes a narrow materialization boundary, including whether the
  first implementation should be a Kafka-free service over committed snapshots,
  a commit-time hook, or another minimal integration point already present in
  the codebase.
- Pre-work proposes the first supported message set. The default target is
  committed `create` messages for `loc`, `typ` records with
  `data.kind: "link_type"`, and `lnk` records matching the TASK-008
  vocabulary. Broader generic materialization is allowed only if inspection
  shows it is simpler and does not expand behavior risk.
- Pre-work proposes the materialized document shape, idempotency behavior,
  ordering expectations, missing/duplicate/conflicting `data.id` behavior, and
  how committed visibility remains tied to the accepted `txn` header gate.
- Pre-work proposes focused unit/integration assertions and the exact Gradle
  verification commands for the chosen boundary.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not implement semantic reference validation for `lnk.type_id`, `left`,
  `right`, or `allowed_*_collections` in pre-work or implementation unless the
  director explicitly approves a later task for it.
- Do not add plate/well read APIs, "what is in this plate?" queries, "where is
  this sample?" queries, controllers, HTTP submission rebuilds, or UI work.
- Do not add `parent_location_id` to `loc` records.
- Do not change Kafka topic configuration, listener deserialization semantics,
  the `txn` write-ahead log record shape, committed snapshot response shape,
  security policy, Docker Compose, or build configuration.
- Do not implement update/delete replay, backfill jobs, background workers,
  multi-transaction conflict resolution, or authorization/scoping policy in this
  task unless pre-work identifies a narrow correctness prerequisite and the
  director explicitly approves it.

DESIGN_NOTES:
- `TASK-007` established `loc` as a first-class collection and startup-created
  MongoDB collection.
- `TASK-008` established canonical `contents` examples: a `typ` link-type
  declaration and a concrete `lnk` relationship using plate-well position as an
  instance property.
- `DIRECTION.md` says containment parentage is canonical in `lnk`, not on
  `loc`, and that query readers should eventually resolve plate contents by
  looking up `contents` links.
- A materializer should preserve the accepted committed-visibility rule:
  records become eligible only when the `txn` header is committed with a
  non-blank backend `commit_id`.

DEPENDENCIES:
- `TASK-005` is accepted and provides committed snapshot reads over `txn`.
- `TASK-006` is accepted and exposes the committed snapshot HTTP adapter.
- `TASK-007` is accepted and adds `loc`.
- `TASK-008` is accepted and adds canonical `contents` link vocabulary
  examples.

LATEST_REPORT:
Created by the director on 2026-05-01 after accepting `TASK-008`. This is a
pre-work task only; claude-1 should write the proposal in
`docs/agents/claude-1-next-step.md` and stop for director review.
