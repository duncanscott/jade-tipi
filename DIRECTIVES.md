# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

`TASK-017` is accepted. The local Docker stack now has a bounded CouchDB
startup/replication bootstrap for the real JGI `clarity` and `esp-entity`
databases.

Product direction is recorded in `DIRECTION.md`: Jade-Tipi objects are logical
JSON objects; the first materializer should use a root-document-only physical
shape with explicit `properties`, denormalized `links`, and reserved `_head`
metadata; extension property/link pages remain future storage work. New
materialized writes now use the root-shaped contract, and the contents read
service now resolves `typ.properties.kind/name` and `_head.provenance`. The
paused contents HTTP integration coverage has been rewritten around this
accepted root-shaped path and accepted as `TASK-016`.

Infrastructure direction: the local Docker stack can run a local CouchDB and
bootstrap `_replicator` jobs that pull remote production CouchDB datasets into
same-named local databases for development use. Remote credentials must come
from local environment files, not committed project files. No Spring Boot
CouchDB initialization layer is currently needed; Docker-level replication is
the accepted mechanism for keeping the local CouchDB populated.

`TASK-019` is accepted. Jade-Tipi now has a bounded, executable
Clarity/ESP container materialization prototype proving that the documented
Clarity tube and ESP freezer/bin/plate chain can become root-shaped `loc`,
transaction-local `typ~contents`, and `lnk` roots using the existing
materializer.

`TASK-020` is accepted. `grp` records are now concrete first-class root-shaped
Jade-Tipi objects: the canonical wire shape uses a grp-id-keyed permissions
map with `rw`/`r` values, DTO/schema examples validate that shape, and the
committed materializer writes `grp + create` roots into the `grp` collection
with `_head.provenance`. Permission enforcement remains intentionally
unimplemented.

`TASK-021` is accepted. Jade-Tipi now has a narrow local-development admin
path using regular Keycloak username/password authentication: the realm import
defines a normal `dnscott` / Duncan Scott application-admin user, Spring maps
the `jade-tipi-admin` realm role from the user's JWT, `/api/admin/**` is
role-protected, and the Next.js admin UI can list, create, and edit root-shaped
`grp` records. The application authorizes from the user's Jade-Tipi admin role,
not from the Keycloak `master` realm admin user. Broader permission
enforcement, Keycloak group synchronization, and production account lifecycle
behavior remain future work.

`TASK-022` is accepted. It applied the narrow frontend build-baseline repair
for the pre-existing TypeScript error in `frontend/app/list/[id]/page.tsx`.
Build verification then surfaced the next unrelated pre-existing TypeScript
blocker in `frontend/auth.ts`, which was tracked and fixed by accepted
`TASK-023`.

`TASK-023` is accepted. The NextAuth `events.signOut` callback now narrows the
sign-out message union before reading the JWT token, preserving the existing
Keycloak logout behavior and restoring the frontend production build baseline.
A director hotfix after `TASK-023` added Keycloak access-token refresh handling
for the frontend so the admin group UI does not keep sending expired bearer
tokens.

`TASK-024` is accepted. The frontend dependency refresh moved Next.js to
16.2.4, React/React DOM to 19.2.5, Auth.js/NextAuth to the documented v5 beta
exception, Playwright/Tailwind/type packages to the accepted current lines,
and TypeScript to the director-approved 5.x line. The Next 16 toolchain
required the committed `frontend/tsconfig.json` migration to `jsx:
react-jsx` and `.next/dev/types/**/*.ts`.

`TASK-025` is accepted. The frontend now uses TypeScript 6.0.3 with the
accepted Next.js 16.2.4 / React 19.2 stack. The package and lockfile changed
only for the TypeScript bump; the existing `tsconfig.json` and frontend source
remain compatible.

`TASK-026` is accepted. The domain write path now proves that a human-readable
Kafka transaction can create a root-shaped `loc` document in MongoDB using the
existing ingest, commit, and materialization path. The accepted location shape
uses `data.id`, optional `data.type_id`, explicit `data.properties`, and
optional-or-empty `data.links`; the materializer preserves legacy flat
location payload tolerance only as documented compatibility.

`TASK-027` is accepted. The existing human-readable `01 -> 11 -> 12 -> 09`
contents-flow examples now have focused DTO and materializer assertions proving
that the authored `typ + create` contents link type and `lnk + create` contents
link share a transaction, materialize as root-shaped `typ`/`lnk` documents, and
line up with `ContentsLinkReadService`'s `typ.properties.kind/name` and
`lnk.type_id` read criteria. No production code, schema, example, HTTP
submission endpoint, semantic reference validation, endpoint projection
maintenance, `ent` materialization, or nested Kafka operation DSL was added.

`TASK-028` is accepted. The Kafka-first domain write path can now materialize a
human-readable `ent + create` transaction as a root-shaped `ent` document while
preserving the existing entity example IDs. Semantic type-reference validation
and ID-abbreviation cleanup remain future work; bare entity-type `typ + create`
is now accepted through `TASK-029`.

`TASK-029` is accepted. The Kafka-first domain write path can now materialize
bare entity-type `typ + create` records as root-shaped `typ` documents while
preserving link-type `typ + create` behavior. The accepted shape handles absent
or empty `data.links` without leaking reserved fields into root `properties`;
`typ + update` property-reference materialization remains deferred to
`TASK-030`.

## Active Task

- `TASK-030 - Human-readable Kafka entity-type property-reference update path`
  is `READY_FOR_PREWORK`. claude-1 should plan only the deferred
  `typ + update` `operation: "add_property"` path: determine the smallest
  materialized root update shape for property references, whether `ppy`
  materialization or semantic `property_id` validation is required for this
  bounded proof, and the focused DTO/materializer/integration verification.
- `TASK-029 - Human-readable Kafka entity-type submission path` is accepted.
  Bare entity-type `typ + create` now materializes as a root-shaped `typ`
  document, including the accepted flat payload variant with empty
  `data.links: {}` and no `data.properties`.
- `TASK-028 - Human-readable Kafka entity submission path` is accepted.
  `ent + create` now materializes as a root-shaped `ent` document, the
  canonical `06-create-entity.json` example exposes explicit empty
  `data.properties` and `data.links`, and the existing `~en~plate_a` /
  `~ty~plate_96` IDs were preserved. Bare entity-type `typ + create` was out
  of scope for TASK-028 and is now accepted through TASK-029; type-property
  update materialization, semantic type-reference validation, broad
  ID-abbreviation cleanup, HTTP submission endpoints, property assignment
  materialization, permission enforcement, contents-link read changes, and a
  nested Kafka operation DSL remain out of scope.
- `TASK-027 - Human-readable Kafka contents link submission path` is accepted.
  The existing contents examples already express the intended human-readable
  shape and transaction sequence; focused tests now prove the DTO co-presence
  and materialized `typ`/`lnk` roots used by the contents read path.
- `TASK-026 - Human-readable Kafka loc submission path` is accepted. The
  canonical `10-create-location.json` example now uses explicit
  `data.properties` and `data.links: {}`; the materializer projects that shape
  into root `properties` and root `links` without nesting while preserving the
  legacy flat-location fallback.
- `TASK-025 - Plan TypeScript 6 frontend upgrade` is accepted. The
  implementation bumped TypeScript to `^6.0.3`, regenerated the lockfile for
  that package only, and required no source or `tsconfig.json` migration.
- `TASK-024 - Update Next.js and npm dependencies` is accepted. The
  implementation updated the frontend dependency set and lockfile, accepted
  the required Next 16 `tsconfig` migration, and preserved the admin group,
  Keycloak login, token refresh, document CRUD, and test-route workflows.
- `TASK-023 - Fix NextAuth sign-out build error` is accepted. The
  implementation narrowed the `frontend/auth.ts` `events.signOut` callback
  message union with `'token' in message`, preserved Keycloak logout behavior
  for the JWT token variant, and restored `cd frontend && npm run build`.
- `TASK-022 - Restore frontend build baseline` is accepted. The implementation
  fixed the `frontend/app/list/[id]/page.tsx` type narrowing issue and stopped
  at the next unrelated build blocker in `frontend/auth.ts`, as directed.
- `TASK-021 - Add admin group management` is accepted. The implementation
  adds the local Keycloak `dnscott` application-admin user, JWT role mapping,
  admin-only group CRUD endpoints, and a minimal Next.js group-management UI.
- `TASK-020 - Define and materialize group records` is accepted. Do not add
  general permission enforcement, membership synchronization, object-level
  overrides, or property-value-level overrides beyond the narrow admin endpoint
  authorization explicitly scoped by `TASK-021`.
- `TASK-013 - Define materialized root document contract` is accepted.
- `TASK-014 - Implement root-shaped materialized documents` is
  accepted.
- `TASK-015 - Update contents read service for root-shaped documents` is
  accepted.
- `TASK-016 - Plan root-shaped contents HTTP integration coverage` is
  accepted.
- `TASK-017 - Add local CouchDB replication bootstrap` is
  accepted.
- `TASK-018 - Plan Spring CouchDB initialization` is accepted as superseded by
  human direction. Do not route it.
- `TASK-019 - Prototype Clarity/ESP container materialization` is accepted.
- `TASK-012 - Plan contents HTTP read integration coverage` is accepted
  historical context only. Do not implement `TASK-012` as-is.

`TASK-030` is the active bounded task. Broader authentication redesign,
Keycloak changes, admin group-management changes, permission
evaluation/enforcement semantics, and unrelated product increments remain
future work unless the human selects one as a later bounded goal.

## Orchestrator Protocol Direction

- Developers must not broaden task ownership, acceptance criteria, or scope
  during implementation. If an upgrade tool or build step requires edits
  outside the active task's `OWNED_PATHS`, report the exact file and reason
  for director review rather than self-expanding the task file.

## TASK-027 Direction

- Director implementation review on 2026-05-03 accepts `TASK-027` with
  `SIGNAL: REQUEST_NEXT_STEP` and creates `TASK-028` for entity-submission
  pre-work. Scope check passed: claude-1 changed only
  `docs/agents/claude-1-changes.md`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`.
  The implementation honored the task and directive limits: no HTTP submission
  endpoint, schema hardening, materializer source change, read-service source
  change, `ent` materialization, property-assignment materialization,
  permission enforcement, endpoint projection maintenance, semantic reference
  validation, `parent_location_id`, or nested Kafka operation DSL was added.
- Behavior review accepted the narrow proof that the canonical contents
  examples and materialized roots already line up: the example transaction
  shares one `txn.uuid`, `12-create-contents-link-plate-sample.json`
  references `11-create-contents-type.json` by `type_id`, the materialized
  `typ` root exposes `properties.kind == "link_type"` and
  `properties.name == "contents"`, and the materialized `lnk` root exposes the
  top-level `type_id`, raw endpoints, `properties.position`, and
  `_head.provenance` that the contents read service expects.
- Director static verification passed `git diff --check origin/director..HEAD`.
  Director Gradle reruns were blocked before product tests by sandbox/tooling
  permissions opening the Gradle wrapper cache lock under
  `/Users/duncanscott/.gradle` with `Operation not permitted`. In a normal
  developer shell, use `docker compose -f docker/docker-compose.yml up -d` if
  the local stack is missing, run `./gradlew --stop` if stale Gradle daemons are
  implicated, then rerun `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'` passing with the local Docker stack
  healthy.
- Director created `TASK-027` on 2026-05-03 after accepting `TASK-026`.
- Pre-work should evaluate the existing human-readable contents link path only:
  `typ + create` link-type examples/materialization, `lnk + create` examples
  and materialization, and whether the existing contents read service already
  consumes the materialized link root without further product behavior.
- Do not add HTTP submission endpoints, `ent` materialization, property
  assignment materialization, permission enforcement, endpoint projection
  maintenance, semantic reference validation, `parent_location_id`, or a nested
  Kafka operation DSL.
- Verification proposal should include `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and the existing Kafka/Mongo integration opt-in
  command when the local stack is available. If local setup blocks verification,
  report `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop`
  when stale Gradle daemons are implicated, and the exact blocked command/error
  rather than treating setup as a product blocker.

## TASK-028 Direction

- Director implementation review on 2026-05-03 accepts `TASK-028` with
  `SIGNAL: REQUEST_NEXT_STEP` and creates `TASK-029` for entity-type
  submission pre-work. Scope check passed: claude-1 changed only
  `docs/agents/claude-1-changes.md`,
  `libraries/jade-tipi-dto/src/main/resources/example/message/06-create-entity.json`,
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/EntityCreateKafkaMaterializeIntegrationSpec.groovy`.
  The implementation honored the task and directive limits: no HTTP
  submission endpoint, bare entity-type materialization, `entity_type`
  discriminator, semantic `type_id` resolution, property-assignment
  materialization, permission enforcement, contents-read change, broad
  ID-abbreviation cleanup, or nested Kafka operation DSL was added.
- Behavior review accepted the narrow `ent + create` path: root-shaped `ent`
  materialization now preserves submitted `data.id`, surfaces submitted
  `data.type_id` as root `type_id`, writes root `properties` and `links`, and
  records `_head.provenance`. Focused DTO, materializer, and opt-in Kafka/Mongo
  integration coverage were added for the entity path.
- Director static verification passed `git diff --check HEAD^..HEAD`.
  Director Gradle reruns were blocked before product tests by sandbox/tooling
  permissions opening the Gradle wrapper cache lock under
  `/Users/duncanscott/.gradle` with `Operation not permitted`. In a normal
  developer shell, use `docker compose -f docker/docker-compose.yml up -d` if
  the full local stack is missing, run `./gradlew --stop` if stale Gradle
  daemons are implicated, then rerun `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*' --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'` passing with the local
  Docker stack healthy.
- Director created `TASK-028` on 2026-05-03 after accepting `TASK-027`.
- Director pre-work review on 2026-05-03 accepts the plan with narrowed
  implementation constraints and advances `TASK-028` to
  `READY_FOR_IMPLEMENTATION`. Scope check passed: claude-1 changed only
  `docs/agents/claude-1-next-step.md`.
- Source correction for the implementation turn: current
  `06-create-entity.json` uses `~en~plate_a`, not `~ent~plate_a`; current
  `04-create-entity-type.json` / `06-create-entity.json` also use `~ty~` for
  the entity-type ID/reference. Preserve those IDs for this task and record any
  desired ID-abbreviation cleanup as follow-up.
- Implement only `ent + create` root materialization. Leave bare entity-type
  `typ + create` unsupported, do not add `data.kind: "entity_type"`, and do
  not add semantic validation that `data.type_id` resolves.
- It is acceptable to add explicit empty `data.properties` and `data.links`
  blocks to `06-create-entity.json` to make the human-readable no-inline-facts
  entity shape visible.
- Do not add HTTP submission endpoints, property assignment materialization,
  required property enforcement, semantic type-reference validation, permission
  enforcement, contents-link read changes, or a nested Kafka operation DSL.
- Verification proposal should include `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and the narrowest practical Kafka/Mongo
  integration opt-in command when the local stack is available. If local setup
  blocks verification, report
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
  `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop` when
  stale Gradle daemons are implicated, and the exact blocked command/error
  rather than treating setup as a product blocker.

## TASK-030 Direction

- Director created `TASK-030` on 2026-05-03 after accepting `TASK-029`.
- Pre-work should evaluate only the deferred human-readable `typ + update`
  `operation: "add_property"` path from `05-update-entity-type-add-property.json`.
  Decide the smallest materialized root update shape for property references,
  whether this proof requires `ppy + create` materialization or semantic
  `property_id` validation, and how missing-target, idempotent duplicate, and
  conflicting-reference cases should behave.
- Do not add HTTP submission endpoints, property-value assignment
  materialization, required property enforcement, semantic validation that
  `data.property_id` resolves, permission enforcement, object extension pages,
  endpoint projection maintenance, broad ID-abbreviation cleanup, or a nested
  Kafka operation DSL.
- Verification proposal should include `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and the narrowest practical Kafka/Mongo
  integration opt-in command when the local stack is available. If local setup
  blocks verification, report
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
  `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop` when
  stale Gradle daemons are implicated, and the exact blocked command/error
  rather than treating setup as a product blocker.

## TASK-029 Direction

- Director implementation review on 2026-05-03 accepts `TASK-029` with
  `SIGNAL: REQUEST_NEXT_STEP` and creates `TASK-030` for entity-type
  property-reference update pre-work. Scope check passed for the follow-up:
  claude-1 changed only `docs/agents/claude-1-changes.md`,
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`,
  and
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`.
  The source/test edits are outside claude-1's three base assignment paths but
  inside TASK-029's explicitly expanded implementation-owned paths.
- Behavior review accepted the completed bare entity-type path: root-shaped
  `typ + create` materialization now covers both absent and empty `data.links`
  while preserving link-type `typ + create`, duplicate handling, `_head`
  provenance, and unsupported `typ + update` behavior.
- Director static verification passed `git diff --check HEAD^..HEAD`.
  Director Gradle reruns were blocked before product tests by sandbox/tooling
  permissions opening the Gradle wrapper cache lock under
  `/Users/duncanscott/.gradle` with `Operation not permitted`. In a normal
  developer shell, use `docker compose -f docker/docker-compose.yml up -d` if
  the full local stack is missing, run `./gradlew --stop` if stale Gradle
  daemons are implicated, then rerun `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :jade-tipi:test --tests
  'org.jadetipi.jadetipi.service.CommittedTransactionMaterializerSpec'
  --console=plain`, `./gradlew :jade-tipi:test --console=plain`, and
  `./gradlew :libraries:jade-tipi-dto:test --console=plain` passing.
- Director created `TASK-029` on 2026-05-03 after accepting `TASK-028`.
- Pre-work should evaluate the existing human-readable bare entity-type
  `typ + create` path only: `04-create-entity-type.json`, the current
  materializer's `typ` support for `data.kind == "link_type"`, and the current
  skip behavior for bare entity-type `typ` messages.
- The likely implementation unit is root-shaped materialization for bare
  entity-type `typ + create`, preserving existing link-type materialization
  behavior and leaving `05-update-entity-type-add-property.json` /
  `typ + update` property-reference materialization as deferred unless the
  pre-work finds an unavoidable dependency.
- Do not add HTTP submission endpoints, property assignment materialization,
  required property enforcement, semantic validation that `ent.data.type_id`
  resolves, permission enforcement, object extension pages, contents-read
  changes, broad ID-abbreviation cleanup, or a nested Kafka operation DSL.
- Verification proposal should include `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and the narrowest practical Kafka/Mongo
  integration opt-in command when the local stack is available. If local setup
  blocks verification, report
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
  `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop` when
  stale Gradle daemons are implicated, and the exact blocked command/error
  rather than treating setup as a product blocker.

## TASK-026 Direction

- Director implementation review on 2026-05-03 accepts `TASK-026` with
  `SIGNAL: REQUEST_NEXT_STEP` and creates `TASK-027` for contents-link
  pre-work. Scope check passed: claude-1 changed only the canonical location
  example, focused DTO/materializer tests, the materializer service, and the
  base report file. The implementation honored the task and directive limits:
  no HTTP submission endpoint, schema hardening, `ent` materialization,
  permission enforcement, `parent_location_id`, or nested Kafka operation DSL
  was added.
- Behavior review accepted the explicit human-readable `loc + create` shape:
  `data.properties` now materializes as root `properties`, `data.links`
  materializes as root `links`, and the legacy flat-location payload remains a
  documented compatibility fallback.
- Director static verification passed `git diff --check origin/director..HEAD`.
  Director Gradle reruns were blocked before product tests by sandbox/tooling
  permissions opening the Gradle wrapper cache lock under
  `/Users/duncanscott/.gradle` with `Operation not permitted`. In a normal
  developer shell, use `docker compose -f docker/docker-compose.yml up -d` if
  the local stack is missing, run `./gradlew --stop` if stale Gradle daemons are
  implicated, then rerun `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'` passing with the local
  Docker stack healthy.

## TASK-025 Direction

- Director implementation review on 2026-05-03 accepts `TASK-025` with
  `SIGNAL: HUMAN_REQUIRED` because the bounded frontend dependency/TypeScript
  upgrade sequence is complete and the next unit is not obvious without human
  product direction. Scope check passed: claude-1 changed only
  `frontend/package.json`, `frontend/package-lock.json`, and
  `docs/agents/claude-1-changes.md`. Director verification passed
  `git diff --check origin/director..HEAD`, `cd frontend && npm install`,
  `cd frontend && npm run build`, and `cd frontend && npx tsc --noEmit`.
  `cd frontend && npx playwright test --project=chromium --timeout=15000`
  was blocked by the Codex sandbox port-bind restriction
  (`listen EPERM 0.0.0.0:3000`) and should be rerun in a normal developer
  shell; if Chromium binaries are missing, run
  `cd frontend && npx playwright install chromium`.
- Director created `TASK-025` on 2026-05-03 after accepting `TASK-024`.
- Pre-work should evaluate the deferred TypeScript 6 migration only. Do not
  update Next.js, React, NextAuth/Auth.js, Tailwind, Playwright, backend code,
  Docker, Keycloak, or frontend UI behavior.
- Verification for any later implementation remains `cd frontend && npm
  install`, `cd frontend && npm run build`, and `cd frontend && npm test` or
  the narrowest practical Playwright command.

## TASK-024 Direction

- Director implementation review on 2026-05-03 accepts `TASK-024` with
  `SIGNAL: REQUEST_NEXT_STEP` and creates `TASK-025` for TypeScript 6
  pre-work. Director verification passed `git diff --check
  origin/director..HEAD`, `cd frontend && npm install`, and `cd frontend &&
  npm run build`; `cd frontend && npm test` was blocked by sandbox port-bind
  permissions (`listen EPERM 0.0.0.0:3000`) and should be rerun in a normal
  developer shell.
- Ownership review: the original implementation handoff did not include
  `frontend/tsconfig.json`, but Next 16 reapplies the committed
  `jsx: react-jsx` / `.next/dev/types/**/*.ts` migration on build. Director
  accepts that scope exception for `TASK-024` and records the stricter
  protocol direction above for future tasks.
- Director created `TASK-024` on 2026-05-03 after Duncan requested a frontend
  dependency refresh.
- Pre-work should inspect `frontend/package.json` and npm metadata to identify
  the latest stable compatible versions for all dependencies and
  devDependencies, including Next.js, React, React DOM, NextAuth/Auth.js,
  Playwright, Tailwind, TypeScript, and type packages.
- Prefer stable npm dist-tags. Do not intentionally upgrade to prerelease,
  canary, beta, or release-candidate packages unless the existing package has
  no stable replacement and the tradeoff is documented for director review.
- The implementation, once authorized, may update `frontend/package.json`,
  `frontend/package-lock.json`, and frontend source/tests needed for
  compatibility. Preserve the existing admin group workflow, Keycloak login,
  access-token refresh behavior, document CRUD pages, and test routes.
- Verification should include `cd frontend && npm install`,
  `cd frontend && npm run build`, and the narrowest practical frontend test
  command. If Playwright browser setup or npm network access blocks
  verification, report the exact setup command rather than treating local
  tooling as a product blocker.

## TASK-023 Direction

- Director implementation review on 2026-05-03 accepts `TASK-023` with
  `SIGNAL: HUMAN_REQUIRED` because the current frontend build-baseline goal is
  complete and the next bounded project task is not obvious without human
  direction. Scope check passed: claude-1 changed only `frontend/auth.ts`
  plus the base report file `docs/agents/claude-1-changes.md`.
- The accepted implementation keeps the existing Keycloak logout body for the
  JWT token variant and returns without logout for the `{ session }` variant
  or a missing `idToken`. Director verification passed with
  `git diff --check origin/director..HEAD` and
  `cd frontend && npm run build`; the only build warning was the pre-existing
  non-blocking Turbopack root warning.
- Historical pre-work and implementation direction for the accepted sign-out
  fix is retained below for context; do not route additional `TASK-023` work.
- Director pre-work review on 2026-05-03 advances `TASK-023` to
  `READY_FOR_IMPLEMENTATION` with `SIGNAL: PROCEED_TO_IMPLEMENTATION`.
  claude-1's latest pre-work commit stayed inside the base ownership boundary:
  it changed only `docs/agents/claude-1-next-step.md`.
- Proceed with the proposed narrow fix in `frontend/auth.ts`: accept the
  `events.signOut` callback message as a single value, narrow with
  `'token' in message`, return without attempting Keycloak logout for the
  `{ session }` variant or a missing `idToken`, and keep the existing
  Keycloak logout URL, `id_token_hint`, `post_logout_redirect_uri`,
  `fetch`, and `try/catch` behavior unchanged for the token variant.
- Verification for the director pre-work review was source inspection plus
  `git diff --check HEAD..origin/claude-1`; `frontend/node_modules` is present
  in this worktree. In the implementation turn, run
  `cd frontend && npm run build`. If frontend dependencies are missing or
  stale in the developer worktree, use the documented setup command
  `cd frontend && npm install` before the build.
- Director review on 2026-05-03 accepts `TASK-022` and creates `TASK-023` as
  `READY_FOR_PREWORK` with `SIGNAL: REQUEST_NEXT_STEP`.
- Pre-work should inspect `frontend/auth.ts` around `events.signOut` and
  propose the smallest type-safe NextAuth callback union narrowing. Preserve
  current Keycloak logout behavior for the token variant and return without
  logout for non-token variants.
- Do not change the NextAuth provider setup, session/JWT callbacks,
  admin-role derivation, Keycloak realm import, backend code, package
  dependencies, or unrelated frontend routes.
- Use the project-documented frontend setup path if local dependencies are
  absent: `cd frontend && npm install`, then `cd frontend && npm run build`.

## TASK-022 Direction

- Director implementation review on 2026-05-03 accepts `TASK-022`. The
  implementation stayed within `frontend/app/list/[id]/page.tsx` plus the
  base developer report file. It moved the `accessToken` guard into
  `loadDocument`, kept the `documentId` guard, left the dependency array
  unchanged, and preserved the `getDocument` helper signature.
- Director verification: `git diff --check HEAD~1..HEAD` passed. The
  director worktree needed the documented setup command
  `cd frontend && npm install`; after that, `cd frontend && npm run build`
  was blocked by the sandbox/Turbopack environment while creating a process
  and binding to a port. A local no-emit TypeScript check confirmed the next
  blocker is `frontend/auth.ts(80,21)` and the original list-page error is no
  longer reported.
- Historical pre-work direction for the accepted list-page fix is retained
  below for context; do not route additional `TASK-022` work.
- Director review on 2026-05-03 advances `TASK-022` to
  `READY_FOR_IMPLEMENTATION` with `SIGNAL: PROCEED_TO_IMPLEMENTATION`.
  claude-1's latest pre-work commit stayed inside the base ownership boundary:
  it changed only `docs/agents/claude-1-next-step.md`.
- Proceed with the proposed narrow fix in `frontend/app/list/[id]/page.tsx`:
  keep the `documentId` fast-return guard, move the `accessToken` guard into
  the inner `loadDocument` function, leave the dependency array unchanged, and
  do not change the `getDocument` helper signature.
- Verification for the director pre-work review was static/source inspection
  only. `frontend/node_modules` is absent in this worktree; use the documented
  setup path `cd frontend && npm install`, then run
  `cd frontend && npm run build` in the implementation turn.
- Director review on 2026-05-03 created `TASK-022` after accepting
  `TASK-021`. `TASK-021` frontend verification exposed a pre-existing build
  blocker in `frontend/app/list/[id]/page.tsx` around
  `getDocument(documentId, accessToken)`.
- Pre-work should identify the TypeScript narrowing issue and propose the
  smallest source-compatible fix. Do not modify admin group management,
  backend code, Keycloak realm import, dependencies, or unrelated routes.
- Use the project-documented frontend setup path if local dependencies are
  absent: `cd frontend && npm install`, then `cd frontend && npm run build`.

## TASK-021 Director Acceptance Review

- Accepted on 2026-05-03. The implementation stayed within the task-expanded
  ownership boundary and satisfied the narrow admin group-management task:
  realm role/user setup, JWT role mapping, admin-only group CRUD endpoints,
  root-shaped `grp` persistence, frontend admin gating, and focused docs/tests
  were added.
- Director static checks passed with `git diff --check HEAD~1..HEAD` and
  `jq empty docker/jade-tipi-realm.json`.
- Director Gradle verification was blocked by sandbox permissions for the
  Gradle wrapper cache under `~/.gradle`; rerun
  `./gradlew :jade-tipi:test --tests '*GroupAdmin*' --tests
  '*RealmAccessRoles*'` in a normal developer shell.
- Director frontend verification was blocked because `frontend/node_modules`
  is absent in this worktree; run `cd frontend && npm install` before frontend
  checks. The developer-reported pre-existing build failure is now tracked by
  `TASK-022`.

## TASK-021 Director Pre-work Review

- Director review on 2026-05-03 advances `TASK-021` to
  `READY_FOR_IMPLEMENTATION` with `SIGNAL: PROCEED_TO_IMPLEMENTATION`.
  claude-1's latest developer commit stayed inside the base pre-work ownership
  boundary: it changed only `docs/agents/claude-1-next-step.md`.
- Proceed with the pre-work defaults: `jade-tipi-admin` is a realm role read
  from `realm_access.roles`; only `/api/admin/**` is role-protected; the admin
  group workflow writes root-shaped `grp` documents directly to MongoDB for
  this narrow local-development path; `testuser` remains non-admin; `PUT`
  fully replaces editable group fields; admin provenance uses an
  `admin~<uuid>` sentinel.
- Implement the Spring WebFlux JWT converter with the API shape that compiles:
  adapt the realm-role authorities through `JwtAuthenticationConverter` /
  `ReactiveJwtAuthenticationConverterAdapter` or an equivalent
  `Converter<Jwt, Mono<AbstractAuthenticationToken>>`. Do not wire a
  `Converter<Jwt, Collection<GrantedAuthority>>` directly where WebFlux
  expects an authentication-token converter.
- Use a clearly documented dev-only local password for `dnscott`; the literal
  password `dnscott` is acceptable for this local realm import if documented as
  development-only. Do not add ORCID/federated login, Keycloak admin-user
  authorization, Keycloak group synchronization, general permission
  enforcement, or production account lifecycle behavior.
- Keep frontend implementation inside the task-owned paths. Do not edit
  `frontend/package.json` or add a new frontend test harness unless the task is
  explicitly expanded. Use existing project tooling and the documented setup
  path if verification is blocked by local services or stale tooling:
  `docker compose -f docker/docker-compose.yml up -d`,
  `./gradlew generateFrontendEnv` when frontend env regeneration is needed,
  and `cd frontend && npm install` before frontend build/test commands.

## TASK-020 Director Acceptance Review

- Accepted on 2026-05-02. The implementation stayed within claude-1's base
  report path plus the explicit `TASK-020` owned paths.
- The accepted Shape A is now implemented: `data.permissions` is a map keyed
  by world-unique `grp` IDs, values are exactly `rw` or `r`, non-`grp`
  message payloads retain recursive snake_case validation, and the canonical
  `13-create-group.json` example round-trips through the DTO tests according
  to the developer report.
- `CommittedTransactionMaterializer` supports `grp + create` and reuses the
  root-document `_head.provenance` contract. Unsupported `grp` actions,
  missing/blank ids, and the success path have focused tests; duplicate
  behavior remains covered by the existing generic insert-path tests.
- Director static checks passed for whitespace and JSON syntax. Gradle
  verification was blocked by local sandbox/tooling permissions around the
  Gradle wrapper cache and local socket creation, so the documented commands
  should be rerun in a normal developer shell before any release gate.

## Scope Expansion

For `TASK-020`, claude-1 may implement within the task-owned paths plus this
explicit schema expansion:

- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`

For `TASK-019`, claude-1 may inspect and propose changes within:

- `docs/architecture/clarity-esp-container-mapping.md`
- `docs/architecture/jade-tipi-object-model-design-brief.md`
- `docs/orchestrator/tasks/TASK-019-clarity-esp-container-materialization.md`
- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/`
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/containers/`
- `jade-tipi/src/integrationTest/resources/`

`TASK-019` is accepted; the scope expansion above is retained as accepted
history, not as active authorization for additional implementation. Treat
`TASK-012` and `TASK-018` as historical context only; do not route or implement
them as-is.

## TASK-019 Implementation Direction

Accepted on 2026-05-02. The following direction is retained as accepted
implementation history for `TASK-019`; it is not an active authorization to
continue editing.

- Implement the accepted mapping from
  `docs/architecture/clarity-esp-container-mapping.md` as a narrow prototype.
- Use only the sampled container shapes already documented there: the Clarity
  tube and the ESP freezer/bin/plate chain. Do not broaden this into a full
  Clarity/ESP importer.
- Exercise the existing root-document materialization path for `loc`,
  `typ + link_type`, and `lnk`; do not add `ent` materialization yet.
- Preserve the human object model direction: object roots separate `_head`,
  `properties`, and `links`; parentage is represented by `lnk` records, not
  duplicated as `parent_location_id` on `loc`; `contents` labels belong on the
  `typ` root; well/slot position is an instance property on the `contents`
  link for this prototype.
- Keep Docker/CouchDB bootstrap behavior out of this task. The
  `docker/couchdb-bootstrap.sh` max-document-size change is already present on
  `director` as separate human-directed infrastructure work and is not a
  TASK-019 blocker.
- Provide focused unit coverage for the transformation/materialization behavior
  and run the relevant Gradle commands if local tooling permits.

## TASK-019 Director Pre-work Review

- Director review on 2026-05-02 advances `TASK-019` to
  `READY_FOR_IMPLEMENTATION` with `SIGNAL: PROCEED_TO_IMPLEMENTATION`.
- The prior Docker/bootstrap objection is resolved. The
  `docker/couchdb-bootstrap.sh` change exists on `director` as separate
  human-directed infrastructure work (`8b54a0f`), not as a pending TASK-019
  implementation diff. Do not revert or edit it under TASK-019.
- The accepted mapping pre-work fixes the earlier design blockers:
  `typ~contents` declares `assignable_properties: ["position"]` in both the
  materialized root and `typ + create` message, and the examples use a
  transaction-local `typ~contents` id consistently. `ContentsLinkReadService`
  resolves matching contents type ids by `properties.kind/name`, so that
  transaction-local type id remains compatible with the accepted read path.
- Implementation should now build the smallest executable prototype from that
  mapping. Keep it bounded to transformation/materialization behavior for the
  documented examples and do not add broad import, sync, endpoint, `ent`, or
  Docker/bootstrap work.

## TASK-019 Historical Pre-work Review

- Director review on 2026-05-02 keeps `TASK-019` at `READY_FOR_PREWORK` with
  `SIGNAL: REQUEST_NEXT_STEP`. claude-1 stayed within its base owned paths for
  the latest pre-work commit: only `docs/agents/claude-1-next-step.md` changed.
- Do not move to implementation until pre-work samples local CouchDB records
  from `clarity` and `esp-entity` and writes
  `docs/architecture/clarity-esp-container-mapping.md` with redacted source
  snippets, field paths, selected tube/plate examples where available,
  materialized root examples, transaction messages, and read-only reproduction
  commands.
- The next pre-work turn may run the local read-only CouchDB inspection
  commands from the plan. If CouchDB is unavailable, report the documented
  setup commands rather than a product blocker:
  `docker compose -f docker/docker-compose.yml up -d couchdb` and
  `docker compose -f docker/docker-compose.yml up -d couchdb-init`.
- Resolve `ent` before implementation. The accepted materializer currently
  supports only `loc + create`, `typ + create` for `data.kind == "link_type"`,
  and `lnk + create`; it does not materialize `ent + create`. Either choose a
  prototype that proves the sampled container mapping with `loc` and `lnk`
  roots only, or stop and request a separate materializer-expansion task before
  including sample entities as `ent` roots.

## TASK-018 Director Acceptance Review

- `TASK-018` is accepted as superseded on 2026-05-02 before developer work
  began. Human direction clarified that no Spring Boot CouchDB initialization
  is needed because Docker-level CouchDB replication is working.
- Preserve the accepted `TASK-017` behavior: CouchDB startup, database
  creation, and remote dataset replication remain Docker concerns. Do not add
  Spring dependencies, startup initializers, design-document loaders, or tests
  for CouchDB unless a future task explicitly reopens that direction.
- The next useful unit requires a fresh human product decision, likely around
  how Jade-Tipi should use the replicated CouchDB datasets or the next MongoDB
  materialization/read capability.

## TASK-017 Director Acceptance Review

- `TASK-017` implementation review is accepted on 2026-05-02. Scope check
  passed against claude-1's base assignment plus the explicit `TASK-017`/
  `DIRECTIVES.md` implementation expansion. The latest merge changed only
  `.env.example`, `docker/docker-compose.yml`, new
  `docker/couchdb-bootstrap.sh`,
  `docs/orchestrator/tasks/TASK-017-local-couchdb-remote-replication.md`, and
  `docs/agents/claude-1-changes.md`.
- The accepted implementation adds a loopback-bound `couchdb:3.5` service with
  persistent `couchdb_data`/`couchdb_config` volumes and a one-shot
  `alpine:3.20` bootstrap sidecar that consumes the worktree-root `.env`
  without compose-side credential interpolation.
- `.env.example` now separates local CouchDB admin placeholders
  `COUCHDB_USER`/`COUCHDB_PASSWORD` from the remote JGI source credential
  variables. Remote values remain local-env only and must not be committed.
- The bootstrap script creates `_users`, `_replicator`, `_global_changes`,
  `clarity`, and `esp-entity` idempotently; builds replication JSON with `jq`;
  uses structured `source.auth.basic`; preserves checkpoints; and rewrites
  existing `_replicator` documents only when meaningful replication fields
  differ.
- Director static verification passed with
  `git diff --check origin/director..HEAD`, `docker compose -f
  docker/docker-compose.yml config`, and
  `sh -n docker/couchdb-bootstrap.sh`. A local `jq` projection/compare smoke
  check passed for JSON-special password characters.
- Director container-level verification was blocked by local Docker socket
  permissions, and the current worktree `.env` lacks the new local
  `COUCHDB_USER`/`COUCHDB_PASSWORD` variables. In a normal developer shell,
  first add those two non-secret local variables to
  `/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local` and
  re-materialize the worktree `.env`, then run the task verification commands.
- No automatic next task was created. The next useful unit requires human
  approval for credentialed remote replication/network load or human product
  selection for the next application feature.

## TASK-017 Historical Implementation Direction

The following direction is retained as accepted implementation history for
`TASK-017`; it is not an active authorization to continue editing.

- Director review accepted claude-1's revision 2 pre-work. Proceed with the
  bounded implementation in `docker/`, `.env.example`, the task file, and the
  developer report file.
- Use `couchdb:3.5` and an `alpine:3.20` bootstrap sidecar with `jq` for JSON
  construction. Avoid compose-side interpolation for the remote credentials;
  consume the worktree-root `.env` as container environment and enforce
  required variables inside the bootstrap script.
- Add separate local-only `COUCHDB_USER` and `COUCHDB_PASSWORD` placeholders to
  `.env.example`; keep `JADE_TIPI_COUCHDB_ADMIN_USERNAME` and
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD` only for authenticating to the remote JGI
  CouchDB sources.
- Do not expand this task into the orchestrator overlay
  `config/env/project.env.local.example`. If verification is blocked because
  the materialized worktree `.env` lacks the new local CouchDB admin variables,
  report the documented setup action of adding them to
  `/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`.
- Preserve idempotency by comparing existing `_replicator` documents and
  rewriting them only when meaningful replication fields differ. Do not log
  URLs with credentials, credential values, or generated replication JSON.
- Document progress/recovery commands and the approximate 52 GB local data-size
  expectation in the implementation report. Do not pull the multi-GB datasets
  during automated verification unless the human explicitly approves it.

- Implement the smallest Docker-native way to start local CouchDB with persistent
  storage, create local databases named `clarity` and `esp-entity`, and
  bootstrap resumable replication from the remote URLs in
  `JADE_TIPI_COUCHDB_CLARITY_URL` and
  `JADE_TIPI_COUCHDB_ESP_ENTITY_URL`.
- Use `JADE_TIPI_COUCHDB_ADMIN_USERNAME` and
  `JADE_TIPI_COUCHDB_ADMIN_PASSWORD` from local environment only. Do not commit
  credentials and do not print them in logs or docs.
- Account for the dataset sizes: `clarity` is about 40.6 GB and 4,052,016
  documents and is not growing; `esp-entity` is about 11.1 GB and 1,713,401
  documents and is growing.
- Keep this task limited to Docker/local environment bootstrap. Do not add
  Spring Boot application dependencies on CouchDB, change Mongo/Kafka/Keycloak,
  or make integration tests require the remote CouchDBs.
- Verification should include `docker compose -f
  docker/docker-compose.yml config`, starting the CouchDB service, confirming
  the local `clarity` and `esp-entity` databases exist, and showing how to
  observe replication progress without exposing credentials.

## TASK-016 Director Acceptance Review

- `TASK-016` follow-up implementation review is accepted on 2026-05-02.
  claude-1 changed only `docs/agents/claude-1-changes.md` and
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`.
  The report file is inside claude-1's base owned paths, and the spec is
  inside the task-expanded implementation-owned paths.
- The requested reverse-route assertion fix is present:
  `GET /api/contents/by-content/{id}` now asserts the missing
  `properties.position.kind`, `properties.position.row`,
  `properties.position.column`, `provenance.commit_id` existence, and
  `provenance.msg_uuid == lnkMsg.uuid()` fields, matching the forward-route
  flat JSON contract.
- The accepted spec remains opt-in and uses the expected `RANDOM_PORT` WebFlux
  context, `JADETIPI_IT_KAFKA` gate, inline Keycloak reachability probe,
  per-run Kafka topic/consumer group, per-feature ids, DTO helper messages,
  bounded Mongo polling, exact local cleanup, authenticated `WebTestClient`,
  both HTTP routes, and HTTP 200 `[]` empty-result coverage.
- No production service/controller/materializer, Kafka listener, DTO/schema,
  canonical example, Docker, Gradle, security, frontend, fixture/resource,
  response-envelope, pagination, endpoint-join, semantic-validation,
  update/delete replay, or backfill change was made.
- Director static verification passed with
  `git diff --check origin/director..HEAD`. Director local Gradle verification
  was blocked before product compilation by sandbox/tooling permissions
  opening the Gradle wrapper cache lock under `/Users/duncanscott/.gradle`
  with `Operation not permitted`; Docker status was also blocked by Docker
  socket permissions. In a normal developer shell, use
  `docker compose -f docker/docker-compose.yml up -d` for the local stack and
  `./gradlew --stop` when stale Gradle daemons are implicated, then rerun the
  required Gradle verification commands.
- Credited developer verification: claude-1 reported
  `./gradlew :jade-tipi:compileIntegrationTestGroovy`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'`, `./gradlew :jade-tipi:compileGroovy`,
  `./gradlew :jade-tipi:compileTestGroovy`, and
  `./gradlew :jade-tipi:test --rerun-tasks` all passing.

## TASK-016 Director Implementation Review

- `TASK-016` implementation review on 2026-05-02 requested one narrow fix
  before acceptance. Scope and ownership are otherwise acceptable: claude-1
  changed only `docs/agents/claude-1-changes.md` and the new task-owned
  `ContentsHttpReadIntegrationSpec.groovy`; no production, Docker, Gradle,
  security, frontend, fixture, schema, controller, read-service, materializer,
  Kafka listener, endpoint-join, semantic-validation, pagination, response
  envelope, update/delete replay, or backfill change was made.
- The new opt-in spec follows the accepted root-shaped path and uses the
  expected `RANDOM_PORT` WebFlux context, `JADETIPI_IT_KAFKA` gate, inline
  Keycloak reachability probe, per-run Kafka topic/consumer group,
  per-feature ids, DTO helper messages, bounded Mongo polling, exact local
  cleanup, and authenticated `WebTestClient`.
- Required fix: make the reverse route assertion prove the same expected flat
  JSON record as the forward route. In
  `ContentsHttpReadIntegrationSpec.groovy`, the
  `GET /api/contents/by-content/{id}` response currently checks identity,
  `properties.position.label`, and `provenance.txn_id` only. Add assertions for
  the remaining required `properties.position` fields (`kind`, `row`,
  `column`) and provenance fields (`commit_id` exists and `msg_uuid` equals
  the published `lnkMsg.uuid()`), matching the forward-route contract.
- Keep the follow-up limited to this assertion gap and rerun at least
  `./gradlew :jade-tipi:compileIntegrationTestGroovy` and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'`. Reuse the documented setup guidance
  below if local Docker, Keycloak, Kafka, Mongo, or Gradle locks block
  verification.
- Director local verification was blocked before product compilation by
  sandbox/tooling permissions opening the Gradle wrapper cache lock under
  `/Users/duncanscott/.gradle` with `Operation not permitted` while running
  `./gradlew :jade-tipi:compileIntegrationTestGroovy`; this is not a product
  failure. In a normal developer shell, use
  `docker compose -f docker/docker-compose.yml up -d` for the local stack and
  `./gradlew --stop` when stale Gradle daemons are implicated.

## TASK-016 Director Pre-work Review

- `TASK-016` pre-work is accepted on 2026-05-01. Scope check passed:
  claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the
  developer-owned pre-work paths.
- Implement one narrow opt-in
  `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/ContentsHttpReadIntegrationSpec.groovy`.
  Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  `@AutoConfigureWebTestClient`, `@ActiveProfiles("test")`, the existing
  `JADETIPI_IT_KAFKA` opt-in flag, an isolated per-run Kafka topic and
  consumer group, per-run transaction/materialized ids, authenticated
  `WebTestClient`, and bounded Mongo/HTTP polling.
- Publish one Kafka transaction with `open`, one `loc + create`, one
  canonical `typ + create` declaration where `properties.kind/name` will
  resolve as `link_type`/`contents`, one `lnk + create` with
  `properties.position`, and `commit`. Wait for committed `txn` visibility and
  root-shaped materialized `typ`/`lnk` rows before exercising HTTP.
- Assert both `GET /api/contents/by-container/{id}` and
  `GET /api/contents/by-content/{id}` return the same expected flat JSON
  record, including `linkId`, `typeId`, `left`, `right`,
  `properties.position`, and `provenance` fields mapped from
  `_head.provenance` through the existing response shape. Include an
  empty-result HTTP 200 `[]` assertion.
- Director decisions: keep the Keycloak reachability probe inline in the new
  spec; do not refactor `KeycloakTestHelper`. Build messages with DTO helpers
  and inline payload maps instead of loading fixed-id bundled JSON. Do not edit
  `application-test.yml` unless implementation discovers a blocking source
  contradiction. It is acceptable that the `lnk.right` content id is an
  unresolved `ent` id; endpoint joins and semantic validation remain out of
  scope.
- Cleanup must be exact and local: delete only this spec's Kafka topic, `txn`
  rows by `txn_id`, and materialized `loc`/`typ`/`lnk` rows by exact `_id`.
  Do not require a globally clean database.
- Preserve out-of-scope boundaries: no production service/controller/
  materializer, Kafka listener, DTO/schema/example, Docker, Gradle, security,
  frontend, response-envelope, pagination, endpoint-join,
  semantic-validation, update/delete replay, or backfill changes.
- Required verification after implementation: `./gradlew
  :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:compileIntegrationTestGroovy`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'`, and `./gradlew :jade-tipi:test`. If
  time permits, also run `JADETIPI_IT_KAFKA=1 ./gradlew
  :jade-tipi:integrationTest --tests
  '*TransactionMessageKafkaIngestIntegrationSpec*'`. If setup/tooling blocks
  verification, report `docker compose -f docker/docker-compose.yml up -d`,
  `./gradlew --stop` when stale daemons are implicated, and the exact blocked
  command/error instead of treating setup as a product blocker.

## TASK-016 Pre-work Direction

- Inspect accepted `TASK-014` and `TASK-015`, the historical `TASK-012`
  pre-work, `TransactionMessageKafkaIngestIntegrationSpec`,
  `KeycloakTestHelper`, existing WebTestClient integration tests, canonical
  examples `10-create-location.json`, `11-create-contents-type.json`, and
  `12-create-contents-link-plate-sample.json`,
  `CommittedTransactionMaterializer`, `ContentsLinkReadService`,
  `ContentsLinkReadController`, Docker Compose service names, and
  integration-test Gradle wiring.
- Propose one narrow opt-in integration spec using the project-documented
  Docker stack and existing `JADETIPI_IT_KAFKA` flag. Use isolated per-run
  Kafka topic/consumer group, transaction id, and materialized object ids.
- The eventual implementation should publish one canonical transaction with
  one container `loc`, one `typ + create` `contents` declaration, and one
  `lnk + create` with `properties.position`; wait for committed `txn`
  visibility and root-shaped materialized `typ`/`lnk` rows; then assert both
  existing HTTP routes return the expected flat JSON array and an empty-result
  request returns HTTP 200 `[]`.
- Keep cleanup exact and local to this spec's Kafka topic, `txn` rows, and
  materialized `loc`/`typ`/`lnk` ids. Do not require a globally clean database.
- Do not change read service/controller/materializer semantics, Kafka listener
  behavior, DTO schemas, canonical examples, Docker/Gradle files, security,
  frontend, response envelopes, pagination, endpoint joins, authorization,
  semantic validation, update/delete replay, backfill, UI/API projections, or
  broad architecture documentation.
- Required verification proposal should include `./gradlew
  :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:compileIntegrationTestGroovy`,
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'`, and `./gradlew :jade-tipi:test`. If
  local setup blocks verification, report the documented setup command
  `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop`
  when stale Gradle daemons are implicated, and the exact blocked
  command/error.

## TASK-015 Director Review

- `TASK-015` is accepted on 2026-05-01. Scope check passed against claude-1's
  base assignment plus the active task expansion. The implementation changed
  only `docs/agents/claude-1-changes.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`,
  `ContentsLinkReadService.groovy`, `ContentsLinkRecord.groovy`, and
  `ContentsLinkReadServiceSpec.groovy`.
- Required behavior is present: canonical `contents` type ids are resolved from
  root-shaped `typ` documents using `properties.kind == "link_type"` and
  `properties.name == "contents"`, while `lnk` reads still use top-level
  `type_id`, `left`, `right`, and `properties`.
- Provenance now reads `_head.provenance`, with a narrow covered fallback to
  legacy top-level `_jt_provenance` when the canonical location is absent.
  Missing provenance still maps to `null`.
- Existing HTTP route shape and flat JSON response shape are preserved. No
  controller production change, materializer change, Kafka listener change,
  integration coverage, semantic validation, endpoint join, response envelope,
  pagination, Docker/Gradle, security, frontend, or `TASK-012` implementation
  was added.
- Required service assertions are present for root-shaped `typ` criteria,
  unchanged `lnk` query/mapping, `_head.provenance`, legacy fallback, missing
  provenance, empty-result behavior, blank-id behavior, ordering, unresolved
  endpoint pass-through, and no writes.
- Director local verification was blocked before product compilation by
  sandbox/tooling permissions, not by an observed product failure:
  `./gradlew :jade-tipi:compileGroovy` failed opening the Gradle wrapper cache
  lock under `/Users/duncanscott/.gradle` with `Operation not permitted`. In a
  normal developer shell, use
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` when
  the local stack is needed, `./gradlew --stop` when stale Gradle daemons are
  implicated, then run the remaining `TASK-015` Gradle verification commands.
- Credited developer verification: claude-1 reported the required compile,
  focused service/controller spec, and full unit suite commands passing.
- Follow-up: `TASK-016` was created for pre-work on root-shaped contents HTTP
  integration coverage. Keep `TASK-012` paused as historical context.

## TASK-014 Director Review

- `TASK-014` is accepted on 2026-05-01. Scope check passed against claude-1's
  base assignment plus the active task expansion. The implementation changed
  only `docs/agents/claude-1-changes.md`,
  `CommittedTransactionMaterializer.groovy`, and
  `CommittedTransactionMaterializerSpec.groovy`.
- Required behavior is present: supported committed `loc + create`,
  `typ link_type + create`, and `lnk + create` messages now write
  root-shaped documents with `_id`, `id`, `collection`, top-level `type_id`,
  `properties`, `links: {}`, and `_head.provenance`.
- New roots do not write `_jt_provenance`. Provenance moved to
  `_head.provenance` with `txn_id`, `commit_id`, `msg_uuid`, source
  `collection`, source `action`, `committed_at`, and `materialized_at`.
- Skip behavior, unsupported-message behavior, matching and conflicting
  duplicate behavior, and non-duplicate insert failure behavior are preserved.
  Duplicate comparison ignores only `_head.provenance.materialized_at`.
- Director local verification partially passed: `./gradlew
  :jade-tipi:compileGroovy` succeeded. `./gradlew
  :jade-tipi:compileTestGroovy` was blocked by sandbox/tooling permissions
  opening the Gradle wrapper cache lock under `/Users/duncanscott/.gradle`, not
  by an observed product failure. In a normal developer shell, use
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` when
  the local stack is needed, `./gradlew --stop` when stale Gradle daemons are
  implicated, then run the remaining `TASK-014` Gradle verification commands.
- Credited developer verification: claude-1 reported the required compile,
  focused materializer spec, and full unit suite commands passing.
- Follow-up: `TASK-015` was created for pre-work on moving the contents read
  path to root-shaped `typ` and `lnk` documents.

## TASK-013 Pre-work Direction

- Inspect `DIRECTION.md`, `docs/Jade-Tipi.md`, `docs/README.md`,
  `docs/OVERVIEW.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  canonical message examples, `message.schema.json`,
  `CommittedTransactionMaterializer`, `ContentsLinkReadService`,
  `ContentsLinkReadController`, and the accepted TASK-009 through TASK-012 task
  reports.
- Propose a concrete root document contract for materialized Jade-Tipi objects:
  shared fields, `_head`, `type_id`, explicit `properties`, denormalized
  `links`, provenance, and how to migrate away from `_jt_provenance`.
- Include example root documents for `loc`, `lnk`, a `typ` link-type
  declaration, and an ordinary `ent`.
- Clarify map key policy, especially whether `properties` and `links` should be
  keyed only by property/link object IDs.
- Keep required properties, default values, extension pages, pending pages,
  compaction, semantic validation, update/delete replay, and transaction-overlay
  reads out of the first implementation contract unless the pre-work identifies
  a blocking contradiction.
- Recommend whether `TASK-012` should resume as-is, be rewritten, or be replaced
  after this design is accepted.

## TASK-013 Director Pre-work Review

- `TASK-013` pre-work is accepted on 2026-05-01. Scope check passed: codex-1
  changed only `docs/agents/codex-1-next-step.md`, inside the developer-owned
  pre-work paths.
- Source-reference check passed. The pre-work aligns with `DIRECTION.md` and
  `docs/architecture/kafka-transaction-message-vocabulary.md`: root documents
  should carry identity, `collection`, `type_id`, explicit `properties`,
  denormalized `links`, and `_head`; the current `_jt_provenance` copied-data
  materializer shape is provisional; `contents` semantics live in `typ`, while
  `lnk` remains canonical for relationships.
- The accepted contract is root-document-only for the first implementation:
  `_id == id == data.id`, top-level `collection`, top-level `type_id`,
  `properties`, `links`, and `_head.provenance`.
- Director decision: do not create endpoint stubs and do not populate endpoint
  `links` projections in the first materializer update. Write canonical root
  documents for supported `loc`, `typ link_type`, and `lnk` messages with
  `links: {}`. Endpoint projection maintenance waits for a later task.
- Director decision: transitional inline property keys are acceptable for the
  current accepted examples because they do not carry property IDs for `name`,
  link-type declaration facts, or `properties.position`.
- `TASK-012` must not resume as-is. It should be rewritten or replaced after
  root-shaped materialization and the contents read service are updated.

## TASK-012 Director Pre-work Review

- `TASK-012` implementation is paused on 2026-05-01 by active implementation
  task `TASK-014`. The pre-work below remains historical context, but it must
  not be implemented as-is. Rewrite or replace this integration task after the
  root-shaped materializer and contents read service are updated.
- `TASK-012` pre-work is accepted on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement one opt-in integration spec under `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/contents/`, using the accepted `TASK-004` Kafka integration pattern plus authenticated `WebTestClient` against a real `RANDOM_PORT` Spring context.
- Reuse the project-documented Docker stack and the existing `JADETIPI_IT_KAFKA` opt-in flag. Keep the Kafka probe and add the proposed Keycloak readiness probe so missing services skip before Spring context load.
- Use per-run topic, consumer group, transaction id, and materialized document ids. Cleanup must delete only this spec's Kafka topic, `txn` rows by `txn_id`, and materialized `loc`/`typ`/`lnk` rows by exact `_id`; do not drop collections or require a globally clean database.
- Build messages with DTO helpers and inline payload maps matching the canonical examples' field shapes. Use one container `loc`, one canonical `typ + create` with `kind: "link_type"` and `name: "contents"`, and one `lnk + create` with the plate-well `properties.position` shape. Do not add an extra unrelated `loc` row or a new fixture resource.
- Publish one transaction through Kafka, wait for committed `txn` visibility, then wait for materialized `typ` and `lnk` rows before exercising HTTP.
- Assert both `GET /api/contents/by-container/{id}` and `GET /api/contents/by-content/{id}` in one feature against the same materialized link. Include an empty-result HTTP 200 `[]` assertion.
- Keep polling helpers private in the new spec for this task; do not refactor `TransactionMessageKafkaIngestIntegrationSpec`.
- Preserve all out-of-scope boundaries: no changes to read services/controllers, materializer, persistence service, Kafka listener/config, DTO schema/examples, Docker Compose, security policy, frontend, endpoint joins, semantic validation, response envelopes, pagination, backfill, or update/delete replay.
- Required verification after implementation: `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:compileIntegrationTestGroovy`, `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`, and `./gradlew :jade-tipi:test`. If time permits, also run `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*TransactionMessageKafkaIngestIntegrationSpec*'`. If setup/tooling blocks verification, report `docker compose -f docker/docker-compose.yml up -d`, `./gradlew --stop` when stale daemons are implicated, and the exact blocked command/error instead of treating setup as a product blocker.

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
