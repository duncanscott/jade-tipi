# TASK-038 - Transaction-staged object property projection prework

ID: TASK-038
TYPE: implementation
ARTIFACT_INTENT: implementation-plan
STATUS: ACCEPTED
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-032
  - TASK-031
  - TASK-030
  - TASK-026
  - TASK-036
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - DIRECTION.md
  - README.md
  - docs/README.md
  - docs/user-authentication.md
  - docs/architecture/object-property-model-drift.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/orchestrator/tasks/TASK-038-object-scoped-property-assignment.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/
REQUIRED_CAPABILITIES:
  - architecture-prework
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Turn the object-property-model drift note into an implementation plan for the
corrected target architecture: durable transaction records in `txn`, transient
transaction-message staging in `msg`, local writer identities in `usr`,
property definitions and write policy in `ppy`, and materialized object
property values keyed by `ppy` ID on object documents (`ent`, `loc`, and later
`lnk`/others).

This task intentionally replaces the prior TASK-038 direction. The rejected
direction was to generalize the current standalone `ppy` assignment-root model
from entities to all objects. That would make the current drift generic instead
of returning to the foundation model.

CONTEXT:
- `DIRECTION.md` says each object is a typed collection of explicit
  property-value assignments, and that property maps should be keyed by the IDs
  of property objects.
- `docs/architecture/object-property-model-drift.md` now states the corrected
  target: `txn` is permanent transaction metadata; `msg` is transient message
  staging; `usr` is local identity/audit state; `ppy` is definitions/policy;
  object roots hold materialized property values keyed by `ppy` ID with
  transaction provenance.
- The current code stores transaction headers and message records in `txn`, and
  stores entity property assignments as standalone `ppy` roots. Those are
  transitional implementation details, not the long-term model.
- The current message envelope allows `txn.user`, but durable transaction
  headers do not yet persist a local `user_id` or immutable writer snapshot.
  TASK-038 should plan that gap before property-value provenance depends on
  transaction records.
- The near-term product goal is still to publish Kafka messages that represent
  Clarity/ESP container and sample data, then inspect the resulting MongoDB
  JSON. This prework protects that goal from entrenching the wrong storage
  shape.

ACCEPTANCE_CRITERIA:
- Document the intended collection responsibilities in enough detail for the
  next implementer:
  - `txn`: durable transaction object/header that remains forever and records
    local `user_id`, immutable writer snapshot, ownership/group, commit state,
    commit identity, timestamps, and materialization state.
  - `msg`: transient transaction-message staging records keyed by
    `txn_id` plus message UUID, cleared only after all messages in a committed
    transaction have been projected to object documents.
  - `usr`: local identity/audit records for people and service identities,
    usually projected from ORCID/Keycloak or another authentication source.
  - `ppy`: property definitions, value schema, owner, and write policy; not
    long-lived assignment-value records.
  - object collections: current materialized object state with property values
    keyed by `ppy` ID and carrying transaction provenance.
- Define the minimal `usr` root shape and transaction writer contract:
  external identity keys (for example ORCID iD, OIDC issuer, and OIDC subject),
  display facts, service-account representation, `txn.user_id`, and immutable
  `txn.writer` snapshot. The design must make transaction audit possible
  without querying Keycloak, ORCID, or another identity provider.
- Define the bootstrap identity needed to avoid a user/transaction creation
  cycle. The expected direction is a reserved local `usr` named `jdtp-admin`,
  created as a genesis storage fact before normal transaction validation, used
  only as a system/audit identity for initial transactions, and not treated as
  a human login account or external identity-provider user.
- Define the local membership boundary. `grp` records are group/permission
  objects, not collections of ORCID IDs. Group membership should be planned as
  local Jade-Tipi state, such as `usr` properties, membership `lnk` records, or
  a later dedicated membership projection.
- Propose the minimal object property-value entry shape, including where
  `value`, `txn_id`, `commit_id`, `msg_uuid`, and `applied_at` live. Identify
  any fields that must remain open for human/director decision.
- Define the materialization lifecycle at the design level:
  submit messages -> stage in `msg` -> commit transaction in `txn` -> project
  committed messages onto object roots -> mark the durable transaction applied
  -> clear staged messages from `msg`.
- Define the read-overlay boundary: readers should eventually overlay
  committed-but-unapplied `msg` records on top of materialized object roots,
  with optional read-your-own-open-transaction support deferred.
- Identify the idempotency/crash-safety decision that must be made before
  implementation: whether staged messages are deleted before or after marking
  the durable transaction applied, and what guard prevents payload loss.
- Produce a follow-on task list with implementation order. At minimum it must
  include bootstrap `usr~jdtp-admin` genesis setup, `usr`
  materialization/projection, durable transaction writer persistence, splitting
  `txn`/`msg`, object-targeted property assignment messages, projection to
  object roots, typed location definitions, container seed migration, generic
  object property reads, and cleanup of transitional shapes.
- Stop after prework. Do not implement production code in this task.

OUT_OF_SCOPE:
- No production code changes.
- No MongoDB migration of existing local data.
- No deletion of existing standalone `ppy` assignment-root behavior.
- No UI work.
- No permission enforcement beyond documenting that `ppy` owns property policy
  and `txn` records who wrote a value.
- No full user-administration workflow, password handling, external identity
  provider administration, or production account lifecycle implementation.
- No login support for `jdtp-admin`; it is a bootstrap audit identity only.
- No full payload archive design beyond identifying whether one is required.

PREWORK_REQUIREMENTS:
- Re-read `DIRECTION.md`, `docs/architecture/jade-tipi-object-model-design-brief.md`,
  and `docs/architecture/kafka-transaction-message-vocabulary.md` before
  finalizing the plan.
- Compare the plan against current materializer behavior so the follow-on tasks
  are grounded in real code boundaries, especially transaction persistence,
  committed transaction reads, `ppy + create` assignment materialization, and
  root document construction.
- Inspect the existing `Transaction` DTO and transaction-message persistence
  behavior so the plan distinguishes current `txn.user` envelope metadata from
  the target durable `txn.user_id` / `txn.writer` audit fields.
- Keep backward compatibility explicit. Existing TASK-031/032/033 examples and
  tests should continue to work until a later cleanup task deliberately removes
  transitional shapes.
- Call out any terminology changes needed in docs and schemas. In particular,
  `msg` must be introduced as a staging collection without making it sound like
  a permanent domain collection, and `usr` must be introduced as local audit
  identity without making it the authentication provider.

VERIFICATION:
- `git diff --check`
- No Gradle test run is required for docs-only prework. If the prework edits
  schema examples or production code despite this task's scope, run the
  narrowest affected Gradle tasks and document why the scope changed.

DESIGN_NOTES:
- The immediate review point the project is heading toward is still Kafka-backed
  persistence of container/sample data into MongoDB so the JSON structures can
  be reviewed.
- This task exists because that review point should inspect the intended object
  property model, not a generalized version of the first-pass string-property
  shape.
- The same review point should also show durable transaction writer identity.
  `txn.user` in messages is not enough if the eventual staged `msg` rows are
  cleared after materialization.
- The implementation sequence should stay additive. First introduce the new
  staging/projection path beside the current path; remove or migrate the
  transitional standalone `ppy` assignment roots only after typed location data
  is represented and reviewed.

PREWORK_RESULT (2026-07-03):
- Artifact: `docs/architecture/object-property-model-drift.md` section 8,
  "TASK-038 implementation plan". Sections 1-7 of that note are unchanged.
- The plan covers every acceptance criterion: collection responsibilities
  with target document shapes grounded in current code (8.1-8.2), the minimal
  `usr` root and writer contract (8.2.3-8.2.4), the `jdtp-admin` genesis
  contract (8.2.5), the membership boundary (8.2.8), the property-value entry
  shape (8.2.6), the materialization lifecycle with the crash-safety ordering
  identified and resolved as a recommendation (8.3), the read-overlay
  boundary (8.4), and a twelve-task follow-on breakdown A-L with dependencies
  and acceptance sketches (8.5).
- Open items reserved for director decision are consolidated in the 8.6
  table, keyed to section 7's numbering, each with a recommendation. Decision
  7 (payload archive) is flagged as a hard gate before the `msg` cleanup task
  ships deletion.
- Notable grounding facts surfaced during prework: `openHeader` currently
  discards `message.txn().user()` entirely, so writer identity today exists
  only on the Kafka wire; `appendDataMessage` performs no header-state check;
  MongoDB permits `~` in field names, so `ppy`-ID-keyed dotted `$set` writes
  are safe; the object-targeted assignment message requires no
  `message.schema.json` change because its new fields are snake_case.
- Scope respected: no production code, no schema/example edits, no MongoDB
  migration, no new task files minted (follow-on tasks are proposed in 8.5
  for the director to number and create).
- VERIFICATION: `git diff --check` clean (docs-only change; no Gradle run
  required per task scope).

DIRECTOR_REVIEW_CLOSURE (2026-07-03):
- Plan ratified with one amendment: the durable `txn` header stores a single
  `writer` sub-document containing `user_id` plus the immutable identity
  snapshot, instead of separate top-level `user_id` and `writer` fields.
  `writer.user_id` is the join reference to the local `usr` record; every
  other `writer` field is an immutable transaction-time snapshot. The
  amendment is applied consistently across `DIRECTION.md`,
  `docs/user-authentication.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, and section 8
  of `docs/architecture/object-property-model-drift.md`.
- TASK-038 is ACCEPTED. Development proceeds with plan task A as TASK-039
  (bootstrap `usr~jdtp-admin` genesis ensure).
