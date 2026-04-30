# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
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
Director pre-work review on 2026-04-30: claude-1's plan in `docs/agents/claude-1-next-step.md` is sufficient to proceed. Scope check passed: the latest claude-1 commit changed only `docs/agents/claude-1-next-step.md`, which is inside the developer-owned pre-work paths.

Implementation directives:
- Build a Kafka-free and HTTP-free `CommittedTransactionReadService` over `txn`; do not add a controller for `TASK-005`.
- Put snapshot return classes under `org.jadetipi.jadetipi.service` for this service boundary. A later HTTP task can introduce public API DTOs if needed.
- Require a WAL header with `record_type=transaction`, `state=committed`, and non-blank `commit_id`; return empty/not-found for missing, open, uncommitted, or older `TransactionService`-shape documents.
- Preserve header fields plus message fields needed by later materializers: `collection`, `action`, `data`, `msg_uuid`, and Kafka provenance.
- Use deterministic message ordering by `_id` ascending. In unit tests, assert the issued Mongo query carries that sort; do not rely on mocked Flux ordering to prove Mongo sorting.
- Use a small service-local Kafka provenance value object rather than reusing the write-side `kafka.KafkaSourceMetadata` type.
- At the end of implementation, set this task to `READY_FOR_REVIEW`, not `IMPLEMENTATION_COMPLETE`.

Verification target: run the narrow `:jade-tipi` compile/test commands selected in pre-work. If Mongo is unavailable for the broader unit suite, report the project-documented setup command: `docker compose -f docker/docker-compose.yml --profile mongodb up -d`.
