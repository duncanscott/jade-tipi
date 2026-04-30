# TASK-006 - Add committed transaction snapshot HTTP read adapter

ID: TASK-006
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-006-committed-transaction-snapshot-http-read-adapter.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Add the smallest HTTP read adapter over the accepted `CommittedTransactionReadService` so callers can retrieve one committed transaction snapshot from the `txn` write-ahead log without changing write-side ingestion or materializing long-term domain collections.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `CommittedTransactionReadService`, existing controllers, exception handling, security/test patterns, and current `TASK-005` snapshot return types before implementation.
- Pre-work proposes the route, response shape, not-found behavior, blank/invalid `txnId` behavior, and the narrow WebFlux/controller test strategy.
- The implementation, once approved, should delegate committed visibility entirely to `CommittedTransactionReadService`; do not duplicate the `record_type`/`state`/`commit_id` gate in the controller.
- A committed snapshot should return HTTP 200 with enough fields for a caller to see header data, staged messages, `collection`, `action`, `data`, `msg_uuid`, and Kafka provenance.
- Missing, open, older-shape, or otherwise non-committed transactions should return a clear not-found response without exposing uncommitted message rows.
- Tests cover at least: committed snapshot returns 200, missing/non-committed snapshot returns 404, blank/invalid id handling follows the chosen controller pattern, and the controller delegates to the service rather than querying Mongo directly.
- Verification includes the narrow `:jade-tipi` unit or integration command selected during pre-work. If local tooling or Docker is unavailable, report the documented setup command instead of treating that as a product blocker.

OUT_OF_SCOPE:
- Do not change Kafka ingestion, topic configuration, message envelope semantics, or `txn` persistence record shape.
- Do not materialize messages into `ent`, `ppy`, `typ`, `lnk`, or other long-term collections.
- Do not implement semantic reference validation between properties, types, entities, and assignments.
- Do not rebuild the HTTP submission wrapper or replace the existing open/commit endpoints.
- Do not introduce new authentication, authorization, redaction, pagination, or bulk/list policy beyond matching existing controller/test patterns.

DESIGN_NOTES:
- `TASK-005` accepted a Kafka-free and HTTP-free service boundary for committed snapshots. This task should wrap that boundary; it should not change read-service semantics unless pre-work identifies a specific bug and the director approves implementation scope.
- Existing `TransactionController` currently exposes only older HTTP-style open/commit endpoints. Existing `DocumentController` shows the repository's simple `ResponseEntity` not-found pattern.
- Because this creates a new HTTP surface, pre-work must stop with a concrete proposal before implementation.

DEPENDENCIES:
- `TASK-005` is accepted and provides `CommittedTransactionReadService` plus focused service-level coverage.

LATEST_REPORT:
Created by director on 2026-04-30 after accepting `TASK-005`. Ready for claude-1 pre-work only.
