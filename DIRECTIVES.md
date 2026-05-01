# Director Directives

SIGNAL: PROCEED_TO_IMPLEMENTATION

## Active Focus

The active bounded unit is `TASK-008`: implement canonical `contents` link vocabulary examples. The goal is to add the smallest DTO/example/documentation unit for declaring a `contents` link type in `typ` and a concrete containment relationship in `lnk`, building on the accepted `loc` collection from `TASK-007`.

New product direction is recorded in `DIRECTION.md`: add a first-class `loc` collection for laboratory locations, keep containment relationships canonical in `lnk`, define `contents` as a typed link/class through `typ`, and model plate well coordinates as instance properties on `contents` links unless wells need independent lifecycle.

## Active Task

- `TASK-008 - Add contents link vocabulary examples` is READY_FOR_IMPLEMENTATION and assigned to claude-1.

## Scope Expansion

claude-1 may implement inside the paths owned by `docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md` and may append the implementation report to `docs/agents/claude-1-changes.md`. Follow the implementation decisions in the active task file.

## TASK-008 Director Pre-work Review

- `TASK-008` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement exactly two canonical examples: `11-create-contents-type.json` for the `contents` `typ + create` declaration and `12-create-contents-link-plate-sample.json` for a concrete `lnk + create` containment relationship.
- Use `~typ~contents`, `~lnk~...`, and `~loc~...` ID segments. Do not rewrite the older `04-create-entity-type.json` `~ty~` example.
- Reuse the current canonical example transaction UUID for the two new examples, matching `10-create-location.json`.
- Use the `DIRECTION.md` plate-well value casing (`"A1"` and `"A"`); the snake_case rule applies to property names, not values.
- Put the `contents` explanation in `docs/architecture/kafka-transaction-message-vocabulary.md`; leave `docs/Jade-Tipi.md` unchanged unless implementation reveals a direct contradiction.
- Do not add supporting endpoint create examples. Unresolved `left` and `right` references are acceptable in this task because semantic reference validation remains out of scope.
- Include `data.kind: "link_type"` and all six declarative facts from `DIRECTION.md`: `left_role`, `right_role`, `left_to_right_label`, `right_to_left_label`, `allowed_left_collections`, and `allowed_right_collections`.
- Extend `MessageSpec` through the existing example path coverage and add focused assertions for the `contents` `typ` declaration and concrete `lnk` shape.
- Do not change `Collection`, `Action`, `Message`, `message.schema.json`, backend services/listeners/controllers/initializers, build files, Docker Compose, security policy, HTTP wrappers, materialization, semantic validation, plate/well read APIs, `parent_location_id`, or the committed-snapshot surface.
- Required verification after implementation includes `./gradlew :libraries:jade-tipi-dto:test`. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the documented setup command and exact command that could not run instead of treating setup as a product blocker.

## TASK-008 Pre-work Direction

Historical context only; implementation is now authorized by the accepted
director decisions above.

- Inspect `DIRECTION.md`, `docs/Jade-Tipi.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`, existing message examples, `MessageSpec`, and `message.schema.json`.
- Propose the smallest concrete example set for `contents`, including whether to add one `typ` message for the link type declaration and one `lnk` message for a concrete containment relationship.
- Keep the proposal at the DTO/vocabulary/example layer unless source inspection reveals a narrow schema gap. Do not propose materialization, semantic endpoint validation, link materializers, plate/well read APIs, `parent_location_id`, Kafka listener changes, transaction persistence shape changes, committed snapshot API changes, HTTP submission wrappers, security policy, Docker Compose changes, or build changes.
- Use existing project ID conventions, keep location IDs using `~loc~`, and keep parentage canonical in `lnk`.
- Verification proposal should include at least `./gradlew :libraries:jade-tipi-dto:test`. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the documented setup command rather than treating setup as a product blocker.

## TASK-007 Director Review

- `TASK-007` is accepted on 2026-05-01. The DTO/schema layer now supports `loc`, the canonical `10-create-location.json` example uses `collection: "loc"` with a `~loc~` ID suffix, documentation lists `location (loc)` while preserving `txn` as the special transaction log/staging collection, and backend startup will create `loc` through the existing `MongoDbInitializer` `Collection.values()` loop.
- Scope check passed against the active task expansion. The implementation changed only `docs/agents/claude-1-changes.md`, `docs/Jade-Tipi.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`, the `TASK-007` task file, the approved DTO enum/schema/example/test paths, and the approved backend initializer test path.
- The base assignment file `docs/agents/claude-1.md` was stale and still described `TASK-006`, but it also delegates scope expansion to `DIRECTIVES.md` and the active task file. The `TASK-007` implementation stayed inside that explicit expansion.
- Required assertions are present: `Collection.fromJson("loc")`, JSON serialization as `loc`, schema acceptance for `loc + create`, schema rejection for `loc + open|commit|rollback`, example round-trip/schema validation, and pure Spock initializer behavior for missing/existing `loc`.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :libraries:jade-tipi-dto:test` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`, and Docker inspection could not access the Docker socket. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test` passing.
- Follow-up: `TASK-008` was created for pre-work on canonical `contents` type/link vocabulary examples. This is the next bounded location-modeling step and keeps materialization, semantic validation, and read APIs out of scope.

## TASK-007 Director Pre-work Review

- `TASK-007` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement the small enum/schema/startup path: add `LOCATION("location", "loc")` to `Collection`, add `loc` to the message schema enum and non-transaction action conditional, and rely on the existing `MongoDbInitializer` enum loop for startup collection creation.
- Use `10-create-location.json` for the canonical example and use `loc` consistently in example IDs (`...~loc~...`), matching `DIRECTION.md`.
- Add DTO coverage for `Collection.fromJson("loc")`, JSON serialization as `loc`, schema acceptance for a canonical `loc` create message, and schema rejection for transaction-control actions with `loc`.
- Add practical initializer coverage under `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/`, preferably with a pure Spock mock of `ReactiveMongoTemplate` proving the enum-driven initializer creates `loc`.
- Leave `DIRECTION.md` unchanged for this task unless implementation reveals a contradiction. Do not implement materialization, `contents` links, link materializers, plate/well APIs, `parent_location_id`, Kafka/persistence shape changes, committed-snapshot API changes, or HTTP submission/security work.
- Required verification: `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test`. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report `docker compose -f docker/docker-compose.yml --profile mongodb up -d` and the exact command that could not run instead of treating setup as a product blocker.

## TASK-006 Director Review

- `TASK-006` is accepted on 2026-05-01. The backend now exposes `GET /api/transactions/{id}/snapshot` as a thin WebFlux adapter over `CommittedTransactionReadService`.
- The controller delegates committed visibility entirely to `CommittedTransactionReadService.findCommitted(String)`, returns a populated `CommittedTransactionSnapshot` as HTTP 200, maps `Mono.empty()` to HTTP 404 with no body, and relies on the service `Assert.hasText` plus `GlobalExceptionHandler` for blank/whitespace-only IDs.
- Scope check passed. The merge changed only `docs/agents/claude-1-changes.md`, the `TASK-006` task file, `CommittedTransactionReadController.groovy`, and `CommittedTransactionReadControllerSpec.groovy`; code and tests stayed within the task-owned controller/test paths and the only developer-owned report path changed was `docs/agents/claude-1-changes.md`.
- Required controller assertions are present: actual route coverage, 200 JSON serialization with header/message/Kafka provenance fields, message order, 404 empty body, 400 `ErrorResponse` through the real global handler, service delegation, and no direct Mongo collaborator.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'` and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadControllerSpec*'`, `./gradlew :jade-tipi:test`, and `./gradlew :jade-tipi:compileIntegrationTestGroovy` passing.
- No automatic next task was created because the next bounded unit is not singularly obvious. Human direction is needed to choose among materialization, HTTP submission rebuild, API response hardening, and authorization/scoping policy.

## TASK-005 Director Decisions

- Pre-work review passed on 2026-04-30. The latest claude-1 pre-work commit changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Start from a Kafka-free and HTTP-free read service over the `txn` collection. Add or change a controller only if pre-work shows a minimal API surface is needed for useful verification.
- Do not add a controller for `TASK-005`; service-level coverage is sufficient.
- The transaction header's `commit_id` remains the authoritative committed-visibility marker. Child message stamping is still not required.
- Require `record_type=transaction`, `state=committed`, and a non-blank `commit_id`; ignore older `TransactionService`-shape records in `txn`.
- Keep snapshot return classes in the `service` package for now. Use a service-local Kafka provenance value object instead of reusing the write-side `kafka.KafkaSourceMetadata` type.
- Order messages by `_id` ascending. In unit tests, assert the Mongo query's sort rather than relying on mocked result order to prove database sorting.
- After implementation, set `TASK-005` to `READY_FOR_REVIEW`; `IMPLEMENTATION_COMPLETE` is not a valid lifecycle status.
- Preserve the current `txn` write-ahead log shape from `TASK-003`; do not redesign the message envelope or persistence record shape.
- Do not materialize to `ent`, `ppy`, `typ`, `lnk`, or other long-term collections in `TASK-005`.
- If Docker or local Gradle tooling is unavailable during verification, report the exact documented setup command rather than treating it as a product blocker.

## TASK-005 Director Review

- `TASK-005` is accepted on 2026-04-30. The read service now returns committed `txn` snapshots only for WAL-shaped headers with `record_type=transaction`, `state=committed`, and non-blank `commit_id`; it preserves message fields and Kafka provenance, orders message queries by `_id` ASC, and stays Kafka-free and HTTP-free.
- The director's previous blocking timestamp issue is resolved. `CommittedTransactionReadService` now coerces raw `Instant`, `java.util.Date`, and `null` timestamp values for header `opened_at`, header `committed_at`, and message `received_at`; unexpected timestamp types fail loudly instead of silently degrading.
- Scope check passed. The latest claude-1 commit changed only `docs/agents/claude-1-changes.md`, the `TASK-005` task file, `CommittedTransactionReadService.groovy`, and `CommittedTransactionReadServiceSpec.groovy`, all within the active task expansion plus the developer report path. The full `TASK-005` implementation stayed inside service/test/task scope and did not add controllers, DTO-package files, write-side persistence changes, build changes, or resource changes.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`; `gradle --version` failed loading the native-platform dylib; `docker compose -f docker/docker-compose.yml ps` could not access the Docker socket. In a normal developer shell, use the documented setup and verification sequence: `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :jade-tipi:test --tests '*CommittedTransactionReadServiceSpec*'` and `./gradlew :jade-tipi:test` passing.

## TASK-004 Director Review

- `TASK-004` is accepted. The backend now has opt-in Docker-backed Kafka integration coverage that publishes canonical open/data/commit messages, consumes them through `TransactionMessageListener`, and asserts the committed `txn` header plus message document and Kafka provenance in MongoDB.
- Scope check passed. The implementation changed only claude-1's report file, the `TASK-004` task file, and the new integration spec under the approved integration-test path.
- Director verification was blocked by local sandbox permissions on the Gradle wrapper lock and Docker socket, not by an observed product failure. In a normal developer shell, use `docker compose -f docker/docker-compose.yml up -d`, then `./gradlew :jade-tipi:compileIntegrationTestGroovy` and `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`.
- Non-blocking note: the optional duplicate-delivery integration feature can pass before the duplicate record is proven consumed because the document count is already `2`; keep this in mind if idempotency becomes a required integration-level assertion.

## TASK-003 Director Review

- `TASK-003` is accepted. The backend now has a Spring Kafka listener that deserializes canonical `Message` records, validates them, and persists transaction headers/messages into MongoDB's `txn` collection through a Kafka-free/HTTP-free persistence service.
- Director verification passed for `./gradlew :jade-tipi:compileGroovy`; targeted test reruns were blocked by local Gradle wrapper lock-file permissions and Docker socket access, not by an observed product failure. The developer reported `./gradlew :jade-tipi:test` passing with MongoDB started via Docker.

## Known Baseline

- `TASK-002` is accepted. The canonical Kafka message envelope now has a first-class `collection` field and examples for transaction, property, type, entity, and property-assignment messages.
- Kafka-first remains the project direction. HTTP submission should later be rebuilt as a thin adapter over the same transaction persistence service.
- The MongoDB `txn` collection is intended to be the durable write-ahead log. A commit is authoritative when the transaction header receives a backend-generated `commit_id`; child message stamping can be deferred.
- Previous TASK-001 baseline failure was `UnitSpec` referencing removed resource path `/units/jade_tipi_si_units.jsonl`; codex-1 updated the test to the bundled `/units/jsonl/si_units.jsonl` resource.
- Direct director verification later exposed a narrow Spock block-label compile issue in `UnitSpec` (`expect` after `when`). If `TASK-002` DTO verification is blocked by that existing issue, fix only that narrow test-harness problem and report it separately.
- The project has a `docker/` directory. Run `docker compose -f docker/docker-compose.yml up` from the project checkout before starting the Spring Boot application or running Spring Boot integration tests.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; start the Docker stack first for Spring Boot app/integration-test work. This environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
