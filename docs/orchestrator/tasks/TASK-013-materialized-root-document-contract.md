# TASK-013 - Define materialized root document contract

ID: TASK-013
TYPE: research
ARTIFACT_INTENT: design-spec
STATUS: READY_FOR_PREWORK
OWNER: codex-1
SOURCE_TASK:
  - TASK-012
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/agents/codex-1.md
  - docs/agents/codex-1-next-step.md
  - docs/agents/codex-1-changes.md
  - DIRECTION.md
  - docs/Jade-Tipi.md
  - docs/README.md
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/materialized-object-shape.md
  - docs/orchestrator/tasks/TASK-013-materialized-root-document-contract.md
REQUIRED_CAPABILITIES:
  - docs
  - source-analysis
  - schema-review
  - task-design
GOAL:
Define the canonical materialized root document contract before additional
integration work hardens the current provisional copied-data shape.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`, `docs/Jade-Tipi.md`,
  `docs/README.md`, `docs/OVERVIEW.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, canonical
  message examples, `message.schema.json`, `CommittedTransactionMaterializer`,
  `ContentsLinkReadService`, `ContentsLinkReadController`, and the accepted
  TASK-009 through TASK-012 task reports.
- Pre-work proposes the smallest durable root document contract for the first
  materializer revision: shared root fields, `_head` fields, `type_id`,
  explicit `properties`, denormalized `links`, provenance, and how current
  `_jt_provenance` should be reconciled.
- Pre-work specifies example root documents for at least `loc`, `lnk`, `typ`
  link-type declarations, and one ordinary `ent` object. Include how `lnk`
  remains canonical while endpoint objects receive denormalized link
  projections.
- Pre-work specifies map key policy. The current direction is that property and
  link maps are keyed by the IDs of the property or link objects; identify any
  problems with that choice and propose a concrete alternative only if needed.
- Pre-work defines what is intentionally absent from the initial contract:
  required properties, default values, extension pages, pending pages,
  background compaction, semantic validation, update/delete replay, and
  transaction-overlay reads.
- Pre-work proposes how `TASK-012` should be resumed, replaced, or rewritten
  after the root document contract is accepted.
- Pre-work proposes one or more follow-up implementation tasks, including the
  likely first task to update `CommittedTransactionMaterializer`.
- Do not implement code or tests in this task unless the director explicitly
  changes the task status and scope after reviewing the design.

OUT_OF_SCOPE:
- Do not change production code, tests, DTO schemas, canonical message
  examples, Docker Compose, Gradle, security, Kafka listener behavior, HTTP
  controllers, or frontend.
- Do not implement extension pages, pending unordered pages, compact page
  indexes, background compaction, multi-threaded reads, or transaction-overlay
  query execution.
- Do not revive required/default property semantics. For the initial contract,
  property values exist only when explicitly assigned.
- Do not run Gradle or Docker verification; this is a design/documentation
  task.

DESIGN_NOTES:
- `TASK-012` is intentionally paused while this research task is active. It was
  prepared against the provisional materialized shape from `TASK-009`; running
  it now could turn that provisional shape into an accidental contract.
- The intended near-term direction is root-document-only. Extension property
  and link pages are an escape hatch for future high-cardinality objects, not a
  requirement for the first materializer revision.
- `_head` is the working name for reserved storage metadata. Keep user-visible
  data in `properties` and `links`; keep physical/page/provenance metadata in
  `_head` or another explicitly reserved namespace.

DEPENDENCIES:
- `TASK-009`, `TASK-010`, and `TASK-011` are accepted.
- `TASK-012` has accepted pre-work but is paused by this research task.

LATEST_REPORT:
Created on 2026-05-01 after product-direction discussion clarified that
materialized Mongo documents should be canonical root documents, not simple
copies of transaction message payloads.
