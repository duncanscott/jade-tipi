# TASK-014 - Implement root-shaped materialized documents

ID: TASK-014
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-012
  - TASK-013
NEXT_TASK:
  - TASK-015
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy
  - docs/orchestrator/tasks/TASK-014-materialized-root-document-materializer.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Update the committed transaction materializer so the currently supported
committed `loc + create`, `typ link_type + create`, and `lnk + create` messages
write the accepted `TASK-013` root-document shape instead of the provisional
copied-data shape.

ACCEPTANCE_CRITERIA:
- `CommittedTransactionMaterializer` still processes only committed snapshot
  messages supplied by `CommittedTransactionReadService`; keep the existing
  supported-message boundary: `loc + create`, `lnk + create`, and
  `typ + create` only when `data.kind == "link_type"`.
- Each materialized document has `_id == data.id`, `id == data.id`,
  `collection == message.collection`, top-level `type_id`, `properties`,
  `links`, and `_head`.
- For `loc`, set `type_id` from `data.type_id` when present, otherwise `null`.
  Put explicit payload fields other than `id` and `type_id` under
  `properties`; the accepted location example should therefore carry `name`
  and `description` under `properties`.
- For `typ link_type`, set `type_id` from `data.type_id` when present,
  otherwise `null`. Put explicit payload fields other than `id` and `type_id`
  under `properties`, including `kind`, `name`, labels, roles, and
  `allowed_*_collections`.
- For `lnk`, set top-level `type_id`, `left`, and `right` from the payload.
  Put payload `properties` under root `properties`, defaulting to `{}` when it
  is absent.
- For this task, write `links: {}` for all supported roots. Do not create
  endpoint stubs and do not update endpoint roots with denormalized link
  projections.
- `_head` must include `schema_version: 1`, `document_kind: "root"`,
  `root_id`, and `provenance`. Provenance must include `txn_id`, `commit_id`,
  `msg_uuid`, source `collection`, source `action`, `committed_at`, and
  `materialized_at`.
- New materialized documents must not write `_jt_provenance`.
- Preserve missing/blank `data.id` skip behavior, unsupported-message skip
  behavior, duplicate matching behavior, conflicting duplicate behavior, and
  non-duplicate insert failure behavior.
- Duplicate comparison should ignore only `_head.provenance.materialized_at`
  so retried matching payloads remain idempotent while real payload/provenance
  differences still surface as conflicts.
- Update focused unit coverage in `CommittedTransactionMaterializerSpec` for
  root-shaped `loc`, `typ link_type`, and `lnk`; unsupported messages;
  missing/blank IDs; matching duplicate; conflicting duplicate; and no
  `_jt_provenance` on new documents.

OUT_OF_SCOPE:
- Do not update `ContentsLinkReadService`, `ContentsLinkRecord`,
  `ContentsLinkReadController`, integration tests, DTO schemas, canonical
  examples, Docker Compose, Gradle files, Kafka listener behavior, security,
  frontend, response envelopes, pagination, endpoint joins, semantic reference
  validation, endpoint projection maintenance, extension pages, pending pages,
  update/delete replay, backfill, transaction-overlay reads, required
  properties, or default values.
- Do not resume or implement `TASK-012` in this task.

DESIGN_NOTES:
- `TASK-013` accepted the root-document contract and explicitly chose not to
  create endpoint stubs or maintain endpoint projections in the first
  materializer update.
- `TASK-012` remains paused because its accepted integration plan targets the
  provisional copied-data shape.
- The contents read service will need a follow-up task to query
  `typ.properties.kind/name` and read provenance from `_head.provenance`.

DEPENDENCIES:
- `TASK-013` is accepted.
- `TASK-009`, `TASK-010`, and `TASK-011` are accepted.
- `TASK-012` has accepted pre-work but is paused and must be rewritten or
  replaced after the root-shaped materializer and read-service follow-up land.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy`
- `./gradlew :jade-tipi:compileTestGroovy`
- `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`
- `./gradlew :jade-tipi:test`

If local tooling, Gradle locks, Docker, or Mongo setup blocks verification,
report the project-documented setup command
`docker compose -f docker/docker-compose.yml --profile mongodb up -d`,
`./gradlew --stop` when stale Gradle daemons are implicated, and the exact
blocked command/error rather than treating setup as a product blocker.

LATEST_REPORT:
Director implementation review accepted on 2026-05-01:
- Accepted claude-1 implementation commit `fc456c5`.
- Scope check passed against claude-1's base assignment plus the explicit
  `TASK-014` and `DIRECTIVES.md` implementation expansion. The commit changed
  only `docs/agents/claude-1-changes.md`,
  `CommittedTransactionMaterializer.groovy`, and
  `CommittedTransactionMaterializerSpec.groovy`. The code/test edits are
  outside claude-1's base report-only paths but inside the active task's owned
  implementation paths; no `TASK-012` implementation or read-service change was
  included.
- Acceptance criteria are satisfied. New supported roots are written with
  `_id == id == data.id`, source `collection`, top-level `type_id`,
  root `properties`, empty `links`, and `_head` metadata. `loc` and
  `typ link_type` payload fields other than `id` and `type_id` move under
  `properties`; `lnk` writes top-level `type_id`, `left`, and `right`, and
  copies payload `properties` or defaults it to `{}`.
- `_head.provenance` now carries `txn_id`, `commit_id`, `msg_uuid`, source
  `collection`, source `action`, `committed_at`, and `materialized_at`; new
  roots do not write `_jt_provenance`.
- Existing materializer boundaries are preserved: snapshot reads still flow
  through `CommittedTransactionReadService`; only `loc + create`,
  `lnk + create`, and `typ + create` with `data.kind == "link_type"` are
  supported; unsupported messages, missing/blank ids, matching duplicates,
  conflicting duplicates, and non-duplicate insert failures keep the expected
  behavior. Duplicate comparison now ignores only
  `_head.provenance.materialized_at`.
- Focused unit coverage was updated for root-shaped `loc`, explicit
  `loc.type_id`, `typ link_type`, `lnk`, absent `lnk.properties`,
  unsupported messages/actions, missing/blank ids, matching and conflicting
  duplicates, provenance differences, non-duplicate insert errors, and absence
  of `_jt_provenance` on new roots.
- Director local verification partially passed: `./gradlew
  :jade-tipi:compileGroovy` succeeded. Further local verification was blocked
  before product tests by sandbox/tooling permissions, not by an observed
  product failure: `./gradlew :jade-tipi:compileTestGroovy` failed opening the
  Gradle wrapper cache lock at
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck`
  with `Operation not permitted`. In a normal developer shell, use the
  project-documented setup command
  `docker compose -f docker/docker-compose.yml --profile mongodb up -d` if the
  local stack is not already running, run `./gradlew --stop` when stale Gradle
  daemons are implicated, then run `./gradlew :jade-tipi:compileTestGroovy`,
  `./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'`,
  and `./gradlew :jade-tipi:test`.
- Credited developer verification: claude-1 reported the Docker stack healthy
  and `./gradlew :jade-tipi:compileGroovy`, `./gradlew
  :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests
  '*CommittedTransactionMaterializerSpec*'`, and `./gradlew :jade-tipi:test`
  all passing, with the focused materializer spec at 23 tests and the full
  unit suite at 111 tests.
- Follow-up: `TASK-015` was created for pre-work on updating
  `ContentsLinkReadService`, `ContentsLinkRecord`, and focused HTTP/service
  assertions to read the accepted root-shaped `typ` and `lnk` documents.
