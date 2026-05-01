# TASK-014 - Implement root-shaped materialized documents

ID: TASK-014
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-012
  - TASK-013
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
Created on 2026-05-01 from accepted `TASK-013` pre-work.
