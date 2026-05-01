# TASK-006 - Add committed transaction snapshot HTTP read adapter

ID: TASK-006
TYPE: implementation
STATUS: ACCEPTED
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

IMPLEMENTATION_DIRECTIVES:
- Pre-work review passed on 2026-04-30. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Use route `GET /api/transactions/{id}/snapshot`.
- Return the existing `CommittedTransactionSnapshot` service-boundary object directly for this task. Keep the TASK-005 snapshot classes in `service/`; do not relocate them to `dto/`.
- Use default Jackson field names for this task (`txnId`, `commitId`, `msgUuid`, `timestampMs`). Do not add snake_case annotations unless a later task decides the public API should mirror persisted WAL field names.
- Keep the controller thin: delegate committed visibility entirely to `CommittedTransactionReadService.findCommitted(String)`, map a present snapshot to HTTP 200, and map `Mono.empty()` to HTTP 404 with no body. Do not duplicate the WAL gate in the controller.
- Preserve current security policy. Put the route under `/api/transactions/**`; do not add new path allow-lists, per-id authorization, redaction, org/grp scoping, or multi-tenant policy in this task.
- For blank or whitespace-only path ids that reach the controller, rely on the service `Assert.hasText` plus `GlobalExceptionHandler` to produce the standard HTTP 400 `ErrorResponse`. Do not add duplicate inline validation unless needed to match the existing controller pattern.
- Add lightweight WebFlux/controller coverage with `WebTestClient` rather than only direct method calls. A no-server controller binding or narrow `@WebFluxTest` slice is acceptable as long as it verifies the actual route, 200 body serialization, 404 empty response, and 400 error handling through `GlobalExceptionHandler` without requiring Mongo/Kafka/Keycloak. Also assert the controller delegates to `CommittedTransactionReadService` and has no direct Mongo collaborator.
- Verification should include at least `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`, and `./gradlew :jade-tipi:test`. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the project-documented setup command `docker compose -f docker/docker-compose.yml up -d` and the exact verification command that could not run rather than treating setup as a product blocker.

LATEST_REPORT:
Director implementation review on 2026-05-01:
- Accepted claude-1 implementation commit `9447496`.
- Acceptance criteria are satisfied. `CommittedTransactionReadController` exposes `GET /api/transactions/{id}/snapshot`, delegates committed visibility entirely to `CommittedTransactionReadService.findCommitted(String)`, maps a populated snapshot to HTTP 200, and maps `Mono.empty()` to HTTP 404 with no response body.
- The implementation honors the TASK-006 directives. It returns the existing `CommittedTransactionSnapshot` service-boundary object with default Jackson camelCase names, keeps the snapshot value objects in `service/`, relies on the service `Assert.hasText` plus `GlobalExceptionHandler` for blank/whitespace-only IDs, and does not change security, Kafka ingestion, the `txn` record shape, DTO-package files, or materialization behavior.
- Required assertions are present in `CommittedTransactionReadControllerSpec`: actual route coverage with `WebTestClient`, 200 JSON body serialization including header fields, staged messages, collection/action/data/msgUuid, and Kafka provenance; message order; service delegation; 404 empty body for missing/non-committed results; 400 `ErrorResponse` through `GlobalExceptionHandler`; and no direct Mongo collaborator on the controller constructor.
- Scope check passed against the active task expansion. The merge changed only `docs/agents/claude-1-changes.md`, this task file, `CommittedTransactionReadController.groovy`, and `CommittedTransactionReadControllerSpec.groovy`. The code/test edits are inside the task-owned controller and test paths; the only changed developer-owned report path was `docs/agents/claude-1-changes.md`.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'` failed opening `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` (`Operation not permitted`). In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`, `./gradlew :jade-tipi:test`, and `./gradlew :jade-tipi:compileIntegrationTestGroovy` all passing.
- Follow-up: no automatic next task was created. The smallest committed snapshot HTTP read adapter is complete; the next unit should be selected by the human because viable continuations include materialization, HTTP submission rebuild, API response hardening, and authorization/scoping policy.
