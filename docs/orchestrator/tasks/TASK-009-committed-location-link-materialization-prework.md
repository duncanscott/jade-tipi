# TASK-009 - Plan committed location/link materialization

ID: TASK-009
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
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
Director reviewed claude-1's pre-work on 2026-05-01 and accepted it for
implementation. Scope check passed: claude-1's latest pre-work commit changed
only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work
paths. Source spot-checks confirmed the relevant committed-read,
transaction-persistence, Mongo initializer, enum, example, and service-test
patterns described by the proposal.

Implement the Kafka-free, HTTP-free committed materializer service over
`CommittedTransactionSnapshot`, with a convenience `materialize(String txnId)`
path that delegates committed visibility to `CommittedTransactionReadService`.
Materialize only committed `create` messages for `loc`, `typ` records with
`data.kind == "link_type"`, and `lnk`; skip other collections/actions and bare
entity-type `typ` records. Do not add semantic reference validation, readers,
controllers, DTO/schema/example changes, Kafka listener/topic changes, build
changes, `parent_location_id`, or update/delete replay.

Director decisions for the open pre-work questions:
- Use the post-commit hook in `TransactionMessagePersistenceService`, not a
  caller-only trigger. Invoke the materializer after the first successful
  commit and also on commit re-delivery when the header is already committed,
  so a retry can fill a projection gap. Preserve the existing outward
  `PersistResult` (`COMMITTED` for the first commit, `COMMIT_DUPLICATE` for a
  duplicate commit).
- Materializer failures on the commit path should be logged and swallowed; the
  `txn` commit remains durable and authoritative. Log enough txn/commit context
  to diagnose projection failures.
- Use `data.id` as Mongo `_id` and keep the payload `id` field in the
  materialized document. The materialized document should copy the committed
  `data` payload verbatim, plus provenance, rather than silently dropping a
  user-visible payload field.
- Store projection metadata under a reserved `_jt_provenance` sub-document
  (`txn_id`, `commit_id`, `msg_uuid`, `committed_at`, `materialized_at`) to
  avoid collision with schema-valid payload keys. Do not validate or resolve
  `type_id`, `left`, `right`, or `allowed_*_collections`.
- For duplicate `_id` writes, treat an identical payload as idempotent success
  without overwrite. For a differing payload, log/count the conflict and do not
  overwrite; continue processing later snapshot messages.
- Missing or blank `data.id` should be counted separately from unsupported
  messages if the result object has room for it (for example `skippedInvalid`);
  otherwise log it clearly and include it in the skipped count. Never synthesize
  an id.
- Ship focused unit coverage in this task. Do not add the optional Docker/Kafka
  integration spec yet.
- Add only a short architecture note if it helps future readers understand the
  projection; keep it narrowly about committed `loc` / link-type `typ` / `lnk`
  materialization and preserve the semantic-validation caveat.

Required verification after implementation:
- `./gradlew :jade-tipi:compileGroovy`
- `./gradlew :jade-tipi:compileTestGroovy`
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
- `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`
- `./gradlew :jade-tipi:test`

If verification is blocked by local setup, report the documented setup command
`docker compose -f docker/docker-compose.yml --profile mongodb up -d` and the
exact Gradle command that could not run rather than treating it as a product
blocker.
