# TASK-008 - Add contents link vocabulary examples

ID: TASK-008
TYPE: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
OWNED_PATHS:
  - docs/Jade-Tipi.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - docs/orchestrator/tasks/TASK-008-contents-link-vocabulary-examples.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Define the next canonical location-modeling vocabulary unit: example Kafka/DTO
messages for declaring a `contents` link type and creating a `contents` link
between location/domain objects, so later materializers and readers have stable
fixtures before semantic validation or APIs are added.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`, `docs/Jade-Tipi.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, existing message
  examples, `MessageSpec`, and `message.schema.json` before implementation.
- Pre-work proposes the smallest concrete example set, including whether the
  canonical examples should add one `typ` message for the `contents` link type
  and one `lnk` message that references that type.
- Any approved implementation uses existing DTO/message schema behavior unless
  pre-work identifies a narrow schema gap and the director explicitly approves
  it.
- Canonical IDs use existing project conventions and keep `loc` location IDs
  with the `~loc~` suffix established by `TASK-007`.
- Tests exercise any new examples through the existing `MessageSpec`
  round-trip and schema-validation coverage, plus focused assertions if needed
  to make the `contents` type/link shape explicit.
- Documentation explains that `contents` semantics are represented by a `typ`
  declaration and concrete relationships are represented by `lnk` records; `loc`
  records still do not carry canonical parentage.
- Verification includes at least `./gradlew :libraries:jade-tipi-dto:test`. If
  local tooling, Gradle locks, or Docker/Mongo are unavailable, report the
  documented setup command rather than treating setup as a product blocker.

OUT_OF_SCOPE:
- Do not implement materialization from committed `txn` messages into `loc`,
  `lnk`, `typ`, or other long-term collections.
- Do not add semantic reference validation that checks a `lnk.type_id` exists or
  enforces allowed endpoint collections.
- Do not implement link materializers, plate/well read APIs, "what is in this
  plate?" queries, or "where is this sample?" queries.
- Do not add `parent_location_id` to `loc` records.
- Do not change the Kafka listener, transaction persistence record shape,
  committed transaction snapshot API, HTTP submission wrappers, security policy,
  Docker Compose configuration, or build configuration.

DESIGN_NOTES:
- `DIRECTION.md` records the current product direction: containment is canonical
  in `lnk`, the `contents` semantics live in `typ`, and plate well coordinates
  can be instance properties on `contents` links unless wells need their own
  lifecycle.
- This task should keep the unit at the DTO/vocabulary/example layer. Runtime
  enforcement and materialization can follow only after the canonical example
  shape is reviewed.

DEPENDENCIES:
- `TASK-007` is accepted and adds `loc` as a first-class collection.

LATEST_REPORT:
Created by the director on 2026-05-01 after accepting `TASK-007`. Ready for
pre-work only; do not implement until the director moves this task to
`READY_FOR_IMPLEMENTATION`.
