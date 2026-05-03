# TASK-027 - Human-readable Kafka contents link submission path

ID: TASK-027
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-026
  - TASK-008
  - TASK-015
  - TASK-019
NEXT_TASK:
  - TASK-028
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - DIRECTION.md
  - docs/OVERVIEW.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/jade-tipi-object-model-design-brief.md
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - docs/orchestrator/tasks/TASK-027-human-readable-kafka-contents-link-submission.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - kafka-integration
  - gradle-verification
GOAL:
Make the next domain increment prove that a human-readable Kafka transaction can
create a root-shaped `contents` `lnk` document and that the existing contents
read path can consume that materialized link shape.

ACCEPTANCE_CRITERIA:
- Read `DIRECTION.md`, `docs/architecture/kafka-transaction-message-vocabulary.md`,
  the accepted TASK-008/TASK-015/TASK-019 notes, and TASK-026's accepted loc
  submission review before planning.
- Preserve Kafka as the primary submission route for this task. Do not add HTTP
  data-submission endpoints.
- Inspect the current `typ + create` link-type and `lnk + create`
  materializer behavior, especially `11-create-contents-type.json` and
  `12-create-contents-link-plate-sample.json`.
- Document and, if necessary, adjust the accepted human-readable `lnk + create`
  shape so it is easy to hand-author:
  - top-level `collection: "lnk"` and `action: "create"`;
  - `data.id` is the materialized link object ID;
  - `data.type_id` references a `typ` link-type record;
  - `data.left` and `data.right` are raw endpoint IDs;
  - `data.properties` is a plain JSON object for link-instance facts such as
    plate-well position.
- Add or update example resource JSON only if the existing examples do not
  already show a complete open, contents-type/link create, and commit flow.
- Ensure the materialized Mongo `lnk` document remains root-shaped with `_id`,
  `id`, `collection: "lnk"`, top-level `type_id`, `left`, `right`, `_head`,
  `properties`, and `links`.
- Add focused automated coverage proving the example/message shape round-trips
  through DTO validation, materializes a `lnk` root with the expected JSON
  shape, and remains readable by the existing contents read service where that
  can be covered without adding new product behavior.
- Report the exact commands run and any local Docker, Kafka, Mongo, or Gradle
  setup blockers.

OUT_OF_SCOPE:
- Do not add HTTP data submission endpoints.
- Do not implement `ent` materialization, property assignment materialization,
  permission enforcement, object extension pages, endpoint projection
  maintenance, or full Clarity/ESP import.
- Do not add semantic validation that `data.type_id`, `data.left`, or
  `data.right` resolve to committed records.
- Do not add `parent_location_id` to `loc` or make containment canonical on
  location records.
- Do not introduce a complex nested operation DSL for Kafka messages.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Identify the current materializer and reader behavior for `typ + create`
  link types and `lnk + create` contents links.
- Identify whether the existing examples already provide the human-readable
  shape and complete transaction sequence, or whether a narrow resource/doc
  update is needed.
- Identify the smallest file changes needed for examples, DTO tests,
  materializer/read coverage, and any integration verification.
- Stop after pre-work. Do not implement until the director advances this task
  to `READY_FOR_IMPLEMENTATION`.

VERIFICATION:
- Expected implementation-turn commands:
  - `./gradlew :libraries:jade-tipi-dto:test`
  - `./gradlew :jade-tipi:test`
  - the narrowest practical Kafka/Mongo integration test if local Docker is
    running and the project has a documented opt-in flag for it
- If local verification is blocked by Docker, Kafka, Mongo, Gradle cache
  permissions, or missing dependencies, report the exact command and error
  rather than treating setup friction as a product blocker.

DIRECTOR_PREWORK_REVIEW:
- 2026-05-03: claude-1 pre-work accepted. The work stayed within claude-1's
  base developer-owned pre-work path: the branch delta before director edits
  was only `docs/agents/claude-1-next-step.md`.
- Source review confirms the plan's main behavioral finding: the existing
  examples already provide a complete `01 -> 11 -> 12 -> 09` human-readable
  transaction flow for opening a transaction, declaring the `contents`
  link-type, creating a `lnk` contents link, and committing the transaction.
  The `lnk + create` example uses top-level `collection: "lnk"`,
  `action: "create"`, `data.id`, `data.type_id`, raw `data.left` and
  `data.right` endpoint IDs, and plain `data.properties.position`.
- Source review also confirms `CommittedTransactionMaterializer` already
  accepts `typ + create` link-types and `lnk + create`, materializing `lnk`
  roots with top-level `type_id`, `left`, `right`, `properties`, `links`, and
  `_head`; `ContentsLinkReadService` resolves canonical contents types through
  `typ.properties.kind == "link_type"` and `typ.properties.name == "contents"`
  before filtering `lnk.type_id`.
- Proceed with the narrow implementation plan: no example, schema,
  materializer, read-service, HTTP endpoint, or documentation rewrite is
  required by default. Add focused test coverage proving the existing
  human-readable examples and materialized `typ`/`lnk` roots line up with the
  contents read criteria. Include the DTO co-presence assertion by default
  because it cheaply proves the example transaction's `typ.data.id` and
  `lnk.data.type_id` relationship at the authored wire layer.
- Verification correction for the implementation turn: prefer documented setup
  commands. For Mongo-backed unit tests when the local stack is missing, report
  or run `docker compose -f docker/docker-compose.yml --profile mongodb up -d`.
  For the Kafka/Mongo integration check, use the existing documented opt-in
  only when the stack is available: run
  `docker compose -f docker/docker-compose.yml up -d` followed by
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`.

DIRECTOR_IMPLEMENTATION_REVIEW:
- Accepted on 2026-05-03. claude-1 followed the accepted narrow implementation
  plan: no production code or example resources changed because source review
  already showed the human-readable `01 -> 11 -> 12 -> 09` contents flow, the
  `typ + create` link-type materialization, the `lnk + create` materialization,
  and `ContentsLinkReadService` criteria already line up.
- Scope review passed. The implementation changed only
  `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`,
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`,
  and `docs/agents/claude-1-changes.md`. The two test files are inside
  TASK-027 `OWNED_PATHS`; the report is inside claude-1's base assignment
  paths. No HTTP submission endpoint, schema tightening, materializer source
  change, read-service source change, `ent` materialization, property-assignment
  materialization, permission enforcement, endpoint projection maintenance,
  semantic reference validation, `parent_location_id`, or nested Kafka
  operation DSL was introduced.
- Behavior and assertion review passed. `MessageSpec` now asserts the example
  transaction chain shares one `txn.uuid`, has transaction open/commit control
  messages, pairs the contents `typ.data.id` with the `lnk.data.type_id`, and
  keeps the authored endpoints consistent with the declared endpoint
  collections. `CommittedTransactionMaterializerSpec` now captures the real
  materialized `typ` and `lnk` roots from the canonical message helpers and
  asserts the `typ.properties.kind/name` dotted-path facts plus the `lnk`
  top-level `type_id`, endpoints, `properties.position`, and
  `_head.provenance` source collection that the contents reader depends on.
- Director static verification passed `git diff --check origin/director..HEAD`.
  Director Gradle reruns were blocked before product tests by sandbox tooling
  permissions opening
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck`
  with `Operation not permitted` while running
  `./gradlew :libraries:jade-tipi-dto:test`. Docker was already running locally
  with Mongo, Kafka, Keycloak, and CouchDB containers up. In a normal developer
  shell, use `docker compose -f docker/docker-compose.yml up -d` if the full
  local stack is missing, run `./gradlew --stop` if stale Gradle daemons are
  implicated, then rerun `./gradlew :libraries:jade-tipi-dto:test`,
  `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ContentsHttpReadIntegrationSpec*'`.
- Credited developer verification: claude-1 reported
  `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:test`, and
  `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*ContentsHttpReadIntegrationSpec*'` all passing with the local Docker stack
  already healthy.
