# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

The active bounded unit is `TASK-012`: plan opt-in end-to-end integration coverage for the accepted contents read path, proving canonical location/type/link messages can flow through Kafka ingestion, committed materialization, and the `GET /api/contents/...` HTTP read routes without changing product semantics.

New product direction is recorded in `DIRECTION.md`: add a first-class `loc` collection for laboratory locations, keep containment relationships canonical in `lnk`, define `contents` as a typed link/class through `typ`, and model plate well coordinates as instance properties on `contents` links unless wells need independent lifecycle.

## Active Task

- `TASK-012 - Plan contents HTTP read integration coverage` is READY_FOR_PREWORK and assigned to claude-1.

## Scope Expansion

Pre-work is requested for `TASK-012`. Use `docs/orchestrator/tasks/TASK-012-contents-http-read-integration-prework.md` as the task-specific source of truth and record the pre-work proposal in `docs/agents/claude-1-next-step.md`. Do not implement until the director reviews the pre-work and moves the task to `READY_FOR_IMPLEMENTATION`.

## TASK-011 Director Review

- `TASK-011` is accepted on 2026-05-01. The backend now exposes a thin WebFlux read adapter over `ContentsLinkReadService`: `GET /api/contents/by-container/{id}` delegates to `findContents(id)` and `GET /api/contents/by-content/{id}` delegates to `findLocations(id)`.
- Scope check passed against claude-1's base assignment plus the active task expansion. The implementation changed only `docs/agents/claude-1-changes.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`, the `TASK-011` task file, `ContentsLinkReadController.groovy`, and `ContentsLinkReadControllerSpec.groovy`; code/doc/task edits outside the base report-only paths were inside the explicit `TASK-011` owned paths.
- Required behavior is present: both controller methods return `Flux<ContentsLinkRecord>` directly as a flat JSON array, empty service results are HTTP 200 with `[]`, blank or whitespace-only ids flow through service `Assert.hasText(...)` and `GlobalExceptionHandler` as 400 `ErrorResponse`, and `@AuthenticationPrincipal Jwt jwt` is present for parity with `CommittedTransactionReadController`.
- The implementation honored the directives: no controller-side authorization/scoping policy, integration test, response envelope, pagination policy, controller DTO, endpoint join to `loc`/`ent`, Mongo/materializer/write-side collaborator, service query semantic change, schema/example/build/Docker/frontend change, or semantic write-time validation was added.
- Required assertions are present in `ContentsLinkReadControllerSpec`: route binding, forward/reverse delegation, JSON serialization including nested `properties.position` and provenance content, service-order preservation, empty-array behavior, blank-id 400 handling through the real global handler, and a reflection assertion that the controller has only `ContentsLinkReadService` as a constructor collaborator.
- Director local verification partially passed: `./gradlew :jade-tipi:compileGroovy` succeeded. Further local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:compileTestGroovy` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then run `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: claude-1 reported the Docker stack healthy and all TASK-011 required Gradle commands passing, including the new `ContentsLinkReadControllerSpec` with 10 tests and the full unit suite with 107 tests, 0 failures.
- Follow-up: `TASK-012` was created for pre-work on opt-in end-to-end contents HTTP read integration coverage. Keep product semantics, endpoint joins, semantic validation, authorization/scoping, response envelopes, pagination, UI, Docker/build changes, and write-path changes out of scope unless a later task explicitly opens them.

## TASK-011 Director Pre-work Review

- `TASK-011` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement one thin WebFlux read adapter over `ContentsLinkReadService`: `GET /api/contents/by-container/{id}` delegates to `findContents(id)` and `GET /api/contents/by-content/{id}` delegates to `findLocations(id)`.
- Return `ContentsLinkRecord` directly as a flat JSON array. Empty results are HTTP 200 with `[]`; blank or whitespace-only ids should flow through service `Assert.hasText(...)` and `GlobalExceptionHandler` as 400 `ErrorResponse`.
- Keep `@AuthenticationPrincipal Jwt jwt` on both controller methods for parity with `CommittedTransactionReadController`, but do not add controller-side authorization/scoping policy or security config changes.
- Add focused pure `WebTestClient.bindToController` coverage for route binding, delegation, JSON serialization, service-order preservation, empty-array behavior, blank-id 400 handling, and a reflection assertion that the controller has only `ContentsLinkReadService` as a constructor collaborator.
- Do not add an integration test, response envelope, pagination policy, controller DTO, endpoint joins to `loc`/`ent`, Mongo/materializer/write-side collaborators, service query semantic changes, schema/example/build/Docker/frontend changes, or semantic write-time validation.
- Optional doc scope: append a short HTTP-route paragraph to `docs/architecture/kafka-transaction-message-vocabulary.md` if it stays tightly aligned with the existing "Reading `contents` Links" section.
- Required verification after implementation: `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*ContentsLinkReadControllerSpec*'`, and `./gradlew :jade-tipi:test`. If Mongo-backed tests fail because Mongo is unavailable, use `docker compose -f docker/docker-compose.yml --profile mongodb up -d` and report setup/tooling blockers separately from product failures.

## TASK-010 Director Review

- `TASK-010` is accepted on 2026-05-01. The backend now has a Kafka-free, HTTP-free `ContentsLinkReadService` over materialized `typ` and `lnk` for the two initial contents queries: `findContents(containerId)` where `lnk.left` is the container, and `findLocations(objectId)` where `lnk.right` is the object.
- Scope check passed against claude-1's base assignment plus the active task expansion. Against the base report-only paths, only `docs/agents/claude-1-changes.md` changed; the service, value object, service spec, architecture doc, and task-file edits were outside the base report-only paths but inside the explicit TASK-010 owned-path expansion.
- Required behavior is present: canonical `contents` type ids are resolved from `typ` using `kind == "link_type"` and `name == "contents"`, `lnk.type_id` is filtered with all matching ids, results are sorted by `_id` ASC, and each materialized `lnk` becomes one `ContentsLinkRecord` preserving link id, `type_id`, `left`, `right`, `properties`, and `_jt_provenance` verbatim.
- The implementation honored the directives: no controller, integration spec, semantic write validation, endpoint join to `loc` or `ent`, materializer/read-service changes, DTO/schema/example changes, Kafka listener/topic changes, build changes, Docker Compose changes, security changes, `parent_location_id`, update/delete replay, backfill, authorization, or UI work was added.
- Required assertions are present in `ContentsLinkReadServiceSpec`: forward and reverse lookup, typ and lnk query criteria, `_id` ASC sort, no-contents short-circuit without an `lnk` query, empty result behavior, unresolved endpoint pass-through, missing provenance pass-through, blank input rejection before Mongo access, and no write calls.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then run `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: claude-1 reported the Docker stack healthy and all TASK-010 required Gradle commands passing, including the new `ContentsLinkReadServiceSpec` with `tests=18, failures=0, errors=0, skipped=0`.
- Follow-up: `TASK-011` was created for pre-work on a thin HTTP/WebFlux adapter over `ContentsLinkReadService`. Keep service semantics, semantic validation, authorization/scoping, UI, and write-path changes out of scope unless a later task explicitly opens them.

## TASK-010 Director Pre-work Review

- `TASK-010` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement a Kafka-free, HTTP-free `ContentsLinkReadService` over materialized `typ` and `lnk`; do not add a controller in this task.
- Support both first-query directions: `findContents(containerId)` for `contents` links whose `left` is the container, and `findLocations(objectId)` for `contents` links whose `right` is the object.
- Resolve `contents` by querying `typ` for `kind == "link_type"` and `name == "contents"`, then filter `lnk.type_id` by all matching type IDs. Do not hardcode a type ID, require caller-supplied type IDs, or add write-time semantic validation.
- Return one service value object per matching `lnk`, preserving link id, `type_id`, `left`, `right`, `properties`, and `_jt_provenance` verbatim. Sort by `_id` ascending; do not deduplicate, group, or silently hide duplicate materialized links.
- Do not join endpoints to `loc` or `ent` in this task. Missing containers/objects, absent `contents` declarations, no matching links, and unresolved endpoint strings should follow the empty/verbatim behavior from the accepted pre-work.
- Add focused pure Spock service coverage and a short architecture-doc paragraph for reading `contents` links. Keep integration coverage deferred unless the service boundary proves unverifiable.
- Required verification after implementation: `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`, and `./gradlew :jade-tipi:test`. If Mongo-backed tests fail because Mongo is unavailable, use `docker compose -f docker/docker-compose.yml --profile mongodb up -d` and report setup/tooling blockers separately from product failures.
- Preserve out-of-scope boundaries: no transaction persistence, committed snapshot, materializer, Kafka listener/topic, DTO schema/example, build, Docker Compose, security, HTTP submission, UI, update/delete replay, backfill, authorization, semantic write validation, or `parent_location_id` changes.

## TASK-010 Pre-work Direction

Historical context only; implementation is now authorized by the accepted
director decisions above.

- Inspect `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`, `CommittedTransactionMaterializer`, `CommittedTransactionReadService`, materializer tests, existing service/controller tests, and Mongo query helper patterns.
- Propose the narrowest backend boundary for reading materialized contents links. Prefer a Kafka-free service over `loc`, `typ`, and `lnk`; include a thin HTTP adapter only if source inspection shows it is necessary for useful verification.
- Specify whether the first implementation should cover both forward lookup (`contents` links where `left` is the container) and reverse lookup (`contents` links where `right` is the object), or only one of them.
- Specify response shape, ordering, missing-container/object behavior, unresolved endpoint behavior, duplicate/conflicting link behavior, and how to identify the canonical `contents` link type without adding write-time semantic validation.
- Do not change transaction persistence, committed snapshot shape, materialization behavior, Kafka listener/topic configuration, DTO schemas/examples, build files, Docker Compose, security policy, HTTP submission wrappers, `parent_location_id`, backfill jobs, update/delete replay, or UI.
- Verification proposal should include focused `:jade-tipi` service/controller tests and any necessary compile/full-test command. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d` rather than treating setup as a product blocker.

## TASK-009 Director Review

- `TASK-009` is accepted on 2026-05-01. The backend now has a Kafka-free, HTTP-free `CommittedTransactionMaterializer` over committed snapshots and a post-commit hook in `TransactionMessagePersistenceService` that runs after first commit and on committed duplicate delivery.
- Scope check passed against claude-1's base assignment plus the active task expansion. Against the base report-only paths, only `docs/agents/claude-1-changes.md` changed; all code/docs/task edits were outside the base report-only paths but inside the explicit TASK-009 owned-path expansion.
- Required behavior is present: committed `loc + create`, link-type `typ + create`, and `lnk + create` messages are projected into long-term collections with `_id == data.id`, retained payload `id`, and `_jt_provenance`; unsupported messages are skipped; missing/blank ids are skipped; matching duplicates are idempotent; conflicting duplicates are logged/counted without overwrite and do not block later messages.
- The implementation honored the directives: no semantic reference validation, readers/controllers, HTTP submission rebuilds, DTO/schema/example changes, Kafka listener/topic changes, build changes, Docker Compose changes, security changes, `parent_location_id`, update/delete replay, or integration spec was added.
- Director verification partially passed: `./gradlew :jade-tipi:compileGroovy` succeeded. Further verification was blocked before product tests by sandbox/tooling permissions: `./gradlew :jade-tipi:compileTestGroovy` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then run the TASK-009 required Gradle commands.
- Credited developer verification: claude-1 reported `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`, `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`, `./gradlew :jade-tipi:test`, and `./gradlew :jade-tipi:compileIntegrationTestGroovy` passing with the Docker stack healthy.
- Follow-up: `TASK-010` was created for pre-work on the smallest contents/location read-query path over the materialized collections.

## TASK-009 Director Pre-work Review

- `TASK-009` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement a Kafka-free, HTTP-free `CommittedTransactionMaterializer` over `CommittedTransactionSnapshot`, plus a convenience `materialize(String txnId)` path that delegates committed visibility to `CommittedTransactionReadService`.
- Add the post-commit hook in `TransactionMessagePersistenceService`. Invoke materialization after the first successful commit and on committed duplicate delivery so retry can fill a projection gap. Keep outward persistence results unchanged (`COMMITTED` and `COMMIT_DUPLICATE` respectively).
- Log and swallow materializer failures on the commit path; the committed `txn` header remains authoritative.
- Materialize only committed `create` messages for `loc`, `typ` records with `data.kind == "link_type"`, and `lnk`. Skip unsupported collections/actions and bare entity-type `typ` records.
- Use `data.id` as Mongo `_id` and keep the payload `id` field. Add reserved `_jt_provenance` with `txn_id`, `commit_id`, `msg_uuid`, `committed_at`, and `materialized_at`.
- Duplicate `_id` with identical payload is idempotent success. Duplicate `_id` with a differing payload is logged/counted, not overwritten, and does not stop later messages. Missing or blank `data.id` is logged and skipped without synthesizing an id.
- Keep semantic reference validation out of scope: do not enforce resolution of `lnk.type_id`, `left`, or `right`, and do not enforce `allowed_*_collections`.
- Do not add readers, controllers, HTTP submission rebuilds, DTO/schema/example changes, Kafka listener/topic changes, build changes, Docker Compose changes, security changes, `parent_location_id`, update/delete replay, or an integration spec in this task.
- Required verification after implementation: `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`, `./gradlew :jade-tipi:test --tests '*TransactionMessagePersistenceServiceSpec*'`, and `./gradlew :jade-tipi:test`.
- If verification is blocked by local setup, report the documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d` and the exact Gradle command that could not run rather than treating it as a product blocker.

## TASK-009 Pre-work Direction

Historical context only; implementation is now authorized by the accepted
director decisions above.

- Inspect `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`, canonical examples `10-create-location.json`, `11-create-contents-type.json`, and `12-create-contents-link-plate-sample.json`, `CommittedTransactionReadService`, `TransactionMessagePersistenceService`, Mongo collection/initializer code, and existing backend service/test patterns.
- Propose the smallest committed materialization boundary for `loc`, `typ` records with `data.kind: "link_type"`, and `lnk` records using the accepted `contents` vocabulary. Consider a Kafka-free service over committed snapshots first unless source inspection shows a narrower existing integration point.
- Specify the materialized document shape, idempotency behavior, ordering expectations, missing/duplicate/conflicting `data.id` behavior, and how the materializer preserves the accepted committed-visibility gate (`record_type=transaction`, `state=committed`, non-blank backend `commit_id`).
- Keep semantic reference validation out of scope: do not propose enforcement that `lnk.type_id`, `left`, or `right` resolve, and do not enforce `allowed_*_collections` yet.
- Do not propose plate/well read APIs, "what is in this plate?" queries, "where is this sample?" queries, controllers, HTTP submission rebuilds, `parent_location_id`, Kafka listener/topic changes, `txn` WAL shape changes, committed snapshot response changes, security policy, Docker Compose, or build changes.
- Verification proposal should include focused `:jade-tipi` service tests and any necessary compile/full-test command. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the documented setup command rather than treating setup as a product blocker.

## TASK-008 Director Review

- `TASK-008` is accepted on 2026-05-01. The canonical DTO/example vocabulary now includes `11-create-contents-type.json` for the `contents` `typ + create` declaration and `12-create-contents-link-plate-sample.json` for a concrete `lnk + create` containment relationship.
- Scope check passed against claude-1's base assignment plus the active task expansion. The merge changed only `docs/agents/claude-1-changes.md`, the `TASK-008` task file, `docs/architecture/kafka-transaction-message-vocabulary.md`, the two approved example resources, and `MessageSpec`.
- Required assertions are present: both examples run through the existing `MessageSpec` round-trip/schema-validation rows, and focused features assert the `contents` `typ` declaration facts plus the concrete `lnk` shape and plate-well position values.
- The implementation honored the directives: it used `~typ~contents`, `~lnk~...`, and `~loc~...` ID segments; preserved the older `04-create-entity-type.json` `~ty~` example; reused the `10-create-location.json` transaction UUID; kept `"A1"`/`"A"` value casing; left `docs/Jade-Tipi.md` unchanged; and did not add supporting endpoint create examples.
- Out-of-scope boundaries were preserved: no enum/schema/backend/build/Docker/security/HTTP/materialization/semantic-validation/read-API/`parent_location_id`/committed-snapshot changes.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :libraries:jade-tipi-dto:test --rerun-tasks` failed opening the Gradle wrapper cache lock in `/Users/duncanscott/.gradle`. In a normal developer shell, use the documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d` if Mongo-backed tests are needed, then run `./gradlew :libraries:jade-tipi-dto:test`.
- Credited developer verification: claude-1 reported `./gradlew :libraries:jade-tipi-dto:test --rerun-tasks` passing with `MessageSpec` `tests=39, failures=0, errors=0, skipped=0` and `UnitSpec` `tests=8, failures=0, errors=0, skipped=0`.
- Follow-up: `TASK-009` was created for pre-work on the smallest committed location/link materialization path. Semantic link validation and read/query APIs remain out of scope.

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
