# TASK-010 - Plan contents location query reads

ID: TASK-010
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-011
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Plan the smallest read/query path over the materialized `loc`, `typ`, and `lnk`
collections so callers can answer the two `DIRECTION.md` contents questions:
"what are the contents of this plate/location?" and "where is this sample or
object located?"

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `CommittedTransactionMaterializer`, `CommittedTransactionReadService`, the
  materializer tests, existing controller/service test patterns, and any
  existing Mongo query helpers before proposing implementation.
- Pre-work proposes the narrowest backend boundary for the first query unit:
  a Kafka-free service over materialized collections, a thin HTTP adapter over
  that service, or a smaller existing integration point if source inspection
  shows one is better.
- Pre-work defines the first supported query shapes, including whether the
  initial unit should cover both forward contents lookup (`lnk.type_id` for
  `contents` and `left == containerId`) and reverse location lookup
  (`right == objectId`) or only one of them.
- Pre-work specifies the response shape, ordering, missing-container/object
  behavior, unresolved endpoint behavior, duplicate/conflicting link behavior,
  and how to resolve the canonical `contents` link type without adding new
  semantic validation on writes.
- Pre-work proposes focused unit/integration assertions and exact Gradle
  verification commands. If local tooling, Gradle locks, or Docker/Mongo are
  unavailable, report the documented setup command rather than treating setup
  as a product blocker.
- Implementation must not begin until the director reviews the pre-work and
  moves the task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not change transaction persistence, committed snapshot shape, or
  materialization behavior from `TASK-009`.
- Do not add semantic write-time validation for `lnk.type_id`, `left`, `right`,
  or `allowed_*_collections`.
- Do not add update/delete replay, backfill jobs, background workers,
  multi-transaction conflict resolution, or authorization/scoping policy.
- Do not rebuild HTTP submission wrappers, Kafka listener/topic configuration,
  DTO schemas/examples, Docker Compose, or build configuration.
- Do not add `parent_location_id` to `loc` records; containment remains
  canonical in `lnk`.
- Do not implement UI work.

DESIGN_NOTES:
- `TASK-009` materializes committed `loc`, `contents` link-type `typ`, and
  `lnk` creates into long-term collections. This task should read those
  projections; it should not mutate or re-materialize them.
- `DIRECTION.md` defines the intended query model: plate contents are found by
  looking up `contents` links whose `left` is the container location, and
  reverse location lookup uses `contents` links whose `right` is the object.
- The first implementation should prefer a service-level boundary unless
  pre-work shows a thin controller is necessary for useful verification.

DEPENDENCIES:
- `TASK-009` is accepted and provides committed materialization for `loc`,
  `typ` link-type declarations, and `lnk` records.

LATEST_REPORT:
Director implementation review on 2026-05-01:
- Accepted claude-1 implementation commit `aead1f3`.
- Findings: no blocking bugs, regressions, or missing assertions found.
- Acceptance criteria are satisfied. The implementation adds a Kafka-free,
  HTTP-free `ContentsLinkReadService` over materialized `typ` and `lnk`,
  with `findContents(containerId)` for `contents` links whose `left` is the
  container and `findLocations(objectId)` for `contents` links whose `right`
  is the object.
- The implementation honors the TASK-010 directives. It resolves canonical
  `contents` type ids by querying `typ` for `kind == "link_type"` and
  `name == "contents"`, filters `lnk.type_id` with all matching ids, sorts
  both query surfaces by `_id` ASC, returns one `ContentsLinkRecord` per
  materialized link, and preserves link id, `type_id`, `left`, `right`,
  `properties`, and `_jt_provenance` verbatim.
- Missing declarations and no matching links produce empty results. Endpoint
  strings are returned without joining to `loc` or `ent`; duplicates are not
  deduplicated or hidden; blank inputs fail at the service boundary with
  `IllegalArgumentException`.
- Required assertions are present in `ContentsLinkReadServiceSpec`: forward
  and reverse query behavior, typ criteria, lnk `$in` and endpoint criteria,
  `_id` ASC sort objects, no-contents short-circuit without an `lnk` query,
  no endpoint join, unresolved endpoint pass-through, missing provenance
  pass-through, blank input rejection before Mongo access, and no write calls.
- Scope check passed against claude-1's base assignment plus the active
  TASK-010 expansion. Against the base report-only paths
  (`docs/agents/claude-1.md`, `docs/agents/claude-1-next-step.md`,
  `docs/agents/claude-1-changes.md`), only
  `docs/agents/claude-1-changes.md` changed. The source, test, architecture
  doc, and task-file edits are outside the base report-only paths but inside
  the explicit TASK-010 owned-path expansion authorized by this file and
  `DIRECTIVES.md`.
- Out-of-scope boundaries were preserved: no controller, integration spec,
  semantic write validation, endpoint join, materializer/read-service
  changes, DTO/schema/example changes, Kafka listener/topic changes, build
  changes, Docker Compose changes, security changes, `parent_location_id`,
  update/delete replay, backfill, authorization, or UI work.
- Director local verification was blocked before product tests by sandbox
  tooling permissions, not by an observed product failure. The command
  `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'` failed
  opening
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck`
  (`Operation not permitted`). In a normal developer shell, use the
  project-documented setup command
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then
  run the required verification commands:
  `./gradlew :jade-tipi:compileGroovy`,
  `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`,
  and `./gradlew :jade-tipi:test`.
- Credited developer verification: claude-1 reported the Docker stack healthy
  and `./gradlew :jade-tipi:compileGroovy`,
  `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`, and
  `./gradlew :jade-tipi:test` all passing, with the new spec at
  `tests=18, failures=0, errors=0, skipped=0`.
- Follow-up: `TASK-011` was created for pre-work on the next bounded unit:
  the smallest WebFlux/HTTP read adapter over `ContentsLinkReadService`.
  Service semantics, semantic validation, authorization/scoping, UI, and
  write-path changes remain out of scope until separately directed.

Implementation done by claude-1 on 2026-05-01 against the director's
accepted pre-work decisions; awaiting review.

Production source changes (all inside the owned-path expansion):
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkReadService.groovy`
  (new) — Kafka-free / HTTP-free `@Service` taking only
  `ReactiveMongoTemplate`. Public surface:
  `Flux<ContentsLinkRecord> findContents(String containerId)` (forward
  lookup, `lnk.left == containerId`) and
  `Flux<ContentsLinkRecord> findLocations(String objectId)` (reverse
  lookup, `lnk.right == objectId`). Both paths first resolve the
  canonical `contents` link type by querying `typ` for documents with
  `kind == 'link_type' AND name == 'contents'` (sorted ASC by `_id`),
  collect the matching `_id` strings, and — when the list is non-empty
  — query `lnk` with `Criteria.where('type_id').in(typeIds)
  .and(<endpoint>).is(<id>)` sorted ASC by `_id`. When no `contents`
  declaration exists, both methods short-circuit to `Flux.empty()` and
  never query `lnk`. `Assert.hasText(...)` rejects blank/whitespace
  input with `IllegalArgumentException` at the entry point.
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/ContentsLinkRecord.groovy`
  (new) — Groovy `@Immutable` value object. Fields: `linkId` (Mongo
  `_id`), `typeId`, `left`, `right`, `properties` (`Map<String, Object>`
  verbatim), and `provenance` (`Map<String, Object>` verbatim from the
  materialized `_jt_provenance` sub-document, including `txn_id`,
  `commit_id`, `msg_uuid`, `committed_at`, `materialized_at`).

Doc changes:
- `docs/architecture/kafka-transaction-message-vocabulary.md` — added a
  short "Reading `contents` Links" section between
  "Committed Materialization Of Locations And Links" and
  "Reference Examples". Names the service surface, the canonical-type
  resolution rule, the `Flux.empty()` short-circuit, the `_id` ASC
  ordering, the verbatim record fields including `provenance`, the
  explicit non-join semantics for `loc`/`ent`, and the
  `IllegalArgumentException` boundary behavior.

Test changes (all under
`jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`):
- `ContentsLinkReadServiceSpec.groovy` (new) — 18 pure Spock features
  with `Mock(ReactiveMongoTemplate)`. Covers: forward and reverse
  single-match round-trips with verbatim properties and
  `_jt_provenance`; captured-`Query` proofs of typ criteria
  (`kind=link_type AND name=contents`), typ sort `_id ASC`, lnk
  `type_id $in [..]` (single and multiple resolved IDs), lnk endpoint
  is-clause (`left == containerId` only on forward, `right == objectId`
  only on reverse), and lnk sort `_id ASC` (asserted via captured
  `sortObject`, not mock iteration order); `Flux.empty()` short-circuit
  with `0 *` `lnk` find when no `contents` type is declared; empty
  result without consulting `loc`/`ent` when no matching `lnk`;
  verbatim return of a `lnk` whose `right` would not resolve in
  `loc`/`ent`; tolerance for a materialized `lnk` missing
  `_jt_provenance` (returned with `provenance == null`);
  `IllegalArgumentException` for blank/whitespace inputs on both
  methods (`where:` data table); and a no-write proof asserting zero
  `insert`/`save`/`updateFirst`/`updateMulti`/`remove` calls on either
  query path.

Out-of-scope items confirmed not changed: no controller (no
`jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/` edit),
no integration spec, no semantic write-time validation, no endpoint
join, no `CommittedTransactionMaterializer` /
`CommittedTransactionReadService` /
`TransactionMessagePersistenceService` change, no
`CommittedTransactionSnapshot` / `CommittedTransactionMessage` /
`KafkaProvenance` / `MaterializeResult` / `PersistResult` change, no
DTO / JSON-schema / message-example / Kafka listener / topic / build /
Docker Compose / security / `loc.parent_location_id` / update or delete
replay / backfill / authorization change.

Verification (with the existing Docker stack already healthy —
`jade-tipi-mongo`, `jade-tipi-kafka`, `jade-tipi-keycloak` containers
all up; `docker compose -f docker/docker-compose.yml --profile mongodb
up -d` was therefore not re-issued):
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*ContentsLinkReadServiceSpec*'`
  — BUILD SUCCESSFUL. Spec report:
  `tests=18, failures=0, errors=0, skipped=0`.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. Aggregated unit-suite
  report: `tests=97, failures=0, errors=0, skipped=0` across 10 specs
  (was 79 across 9 specs at the end of TASK-009; the only delta is the
  new `ContentsLinkReadServiceSpec` adding 18 features).
The DTO library was intentionally not rebuilt because no DTO/schema/
example changed in this task.
