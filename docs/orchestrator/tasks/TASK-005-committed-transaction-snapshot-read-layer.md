# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-005-committed-transaction-snapshot-read-layer.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Add the smallest backend read-side layer over the accepted `txn` write-ahead log so callers can retrieve a committed transaction snapshot without materializing long-term domain collections yet.

ACCEPTANCE_CRITERIA:
- Pre-work inspects the existing `TransactionService`, `TransactionMessagePersistenceService`, controllers, tests, and `docs/architecture/kafka-transaction-message-vocabulary.md` before implementation.
- Pre-work proposes the smallest useful service boundary and return shape for reading one committed transaction from `txn`.
- The implementation must only expose committed visibility when the transaction header has `state=committed` and a backend-generated `commit_id`.
- The read result should include the transaction header and the staged message records for that `txn_id`, preserving enough fields for a later materializer or API layer to know `collection`, `action`, `data`, `msg_uuid`, and Kafka provenance.
- Tests cover at least: committed transaction returns header plus messages, open/uncommitted transaction is not exposed as committed, missing transaction returns a clear empty/not-found result, and message ordering is deterministic.
- Verification includes the narrow `:jade-tipi` unit or integration command selected during pre-work. If local tooling or Docker is unavailable, report the documented setup command instead of treating that as a product blocker.

OUT_OF_SCOPE:
- Do not materialize messages into `ent`, `ppy`, `typ`, `lnk`, or other long-term collections.
- Do not implement semantic reference validation between properties, types, entities, and assignments.
- Do not rebuild the HTTP submission wrapper.
- Do not change Kafka ingestion, topic configuration, or message envelope semantics.
- Do not add authentication or authorization policy beyond matching existing controller/test patterns if a read endpoint is proposed.

DESIGN_NOTES:
- `TASK-003` accepted `txn` as the durable write-ahead log. `TASK-004` accepted Docker-backed integration coverage for Kafka-to-`txn` ingestion.
- The transaction header's `commit_id` is authoritative for committed visibility; child messages may still lack their own commit marker.
- `docs/architecture/kafka-transaction-message-vocabulary.md` notes that later readers and materializers should not infer domain intent from arbitrary payloads because `collection` is stored explicitly on each message record.
- Prefer a Kafka-free and HTTP-free service for the core read logic. Add or adjust a controller only if pre-work shows a minimal API surface is needed for useful verification.

DEPENDENCIES:
- `TASK-004` is accepted and proves the Kafka listener can populate the `txn` collection through the documented local stack.

LATEST_REPORT:
Created by director on 2026-04-30 after accepting `TASK-004`. Start with pre-work only: inspect the existing backend read/write services and tests, then propose the smallest implementation plan in `docs/agents/claude-1-next-step.md`. Do not implement until the director moves this task to `READY_FOR_IMPLEMENTATION`.
