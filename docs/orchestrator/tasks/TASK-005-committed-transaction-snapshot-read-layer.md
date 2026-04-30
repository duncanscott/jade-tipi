# TASK-005 - Add committed transaction snapshot read layer

ID: TASK-005
TYPE: implementation
STATUS: READY_FOR_REVIEW
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
Implementation completed by claude-1 on 2026-04-30. Status flipped to `READY_FOR_REVIEW` per directive (no `IMPLEMENTATION_COMPLETE` lifecycle status).

As-built shape (all inside the expanded `TASK-005` scope):

- `service/CommittedTransactionReadService` (new): Kafka-free / HTTP-free `@Service` with one public method, `Mono<CommittedTransactionSnapshot> findCommitted(String txnId)`. Uses `ReactiveMongoTemplate.findById` for the header and `ReactiveMongoTemplate.find` with `Sort.by(ASC, '_id')` for the message rows. Reuses field-name and state constants from `TransactionMessagePersistenceService` so the read shape stays in lock-step with the writer.
- Committed-visibility gate: returns `Mono.empty()` unless the header has `record_type='transaction'`, `state='committed'`, and a non-blank `commit_id`. This rejects the older `TransactionService` document shape (which has no `record_type`), open headers, partial-write headers, and missing `txn_id` rows. The message lookup is short-circuited in those cases.
- Service-local snapshot return classes (placed in `service/`, not `dto/`):
  - `service/CommittedTransactionSnapshot` — `txnId`, `state`, `commitId`, `openedAt`, `committedAt`, `openData`, `commitData`, `messages`.
  - `service/CommittedTransactionMessage` — `msgUuid`, `collection`, `action`, `data`, `receivedAt`, `kafka`.
  - `service/KafkaProvenance` — `topic`, `partition`, `offset`, `timestampMs`. Mirrors the persisted `kafka` sub-doc without importing the write-side `kafka.KafkaSourceMetadata` type.
- `service/CommittedTransactionReadServiceSpec` (new, 11 tests, 8 features): committed snapshot round-trip; open header skips message lookup; committed-but-no-commit_id and blank-commit_id partial-write guards; missing header returns `Mono.empty()`; older `TransactionService`-shape document is ignored; deterministic ordering verified by capturing the issued `Query` and asserting `sortObject == new Document('_id', 1)` plus the criteria filter; blank/null/whitespace `txnId` rejected with `IllegalArgumentException` (data-driven `where:` block); null payload fields tolerated without NPE.

Verification (claude-1 worktree, Docker stack up: `jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak`):

- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` — BUILD SUCCESSFUL. New spec: 11/11 pass.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. Full unit suite: 48 tests pass (37 prior + 11 new), all green.
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` — BUILD SUCCESSFUL (cross-source-set sanity check; no integration spec was added or required).

No production code was changed in `TransactionMessagePersistenceService`, `TransactionMessageListener`, `TransactionService`, any controller, `application.yml`, or `build.gradle`.

If Mongo is unavailable when reviewing, the project-documented setup command is `docker compose -f docker/docker-compose.yml up -d` (the `JadetipiApplicationTests.contextLoads` test in the unit suite opens a Mongo connection on context load).
