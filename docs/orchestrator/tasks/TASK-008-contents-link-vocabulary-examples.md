# TASK-008 - Add contents link vocabulary examples

ID: TASK-008
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-009
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
Director implementation review on 2026-05-01:
- Accepted claude-1 implementation commit `9d43439`.
- Findings: no blocking bugs, regressions, or missing assertions found.
- Acceptance criteria are satisfied. The implementation adds exactly two canonical examples: `11-create-contents-type.json` for the `contents` `typ + create` declaration and `12-create-contents-link-plate-sample.json` for a concrete `lnk + create` containment relationship. Both reuse the canonical transaction UUID from `10-create-location.json`.
- The implementation honors the TASK-008 directives. The new examples use `~typ~contents`, `~lnk~plate_b1_sample_x1`, and `~loc~plate_b1`; preserve the older `04-create-entity-type.json` `~ty~` fixture; use `DIRECTION.md` value casing (`"A1"` and `"A"`); add `data.kind: "link_type"`; and include the six required declarative facts: `left_role`, `right_role`, `left_to_right_label`, `right_to_left_label`, `allowed_left_collections`, and `allowed_right_collections`.
- Documentation now explains that `contents` semantics are declared in `typ`, concrete containment lives in `lnk`, `loc` records do not carry canonical parentage, and semantic reference validation remains a follow-up reader/materializer concern. `docs/Jade-Tipi.md` was correctly left unchanged.
- Required assertions are present. `MessageSpec` includes both new examples in the existing example round-trip and schema-validation paths, and adds focused assertions for the canonical `contents` `typ` declaration and the concrete `lnk` shape including the position property.
- Scope check passed against claude-1's base assignment plus the active task expansion in this file and `DIRECTIVES.md`. The merge changed only `docs/agents/claude-1-changes.md`, this task file, `docs/architecture/kafka-transaction-message-vocabulary.md`, the two approved example resources, and `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`. These edits are outside claude-1's base report-only paths, but inside the explicit TASK-008 owned-path expansion authorized for implementation.
- Out-of-scope boundaries were preserved: no changes to `Collection`, `Action`, `Message`, `message.schema.json`, backend services/listeners/controllers/initializers, build files, Docker Compose, security policy, HTTP wrappers, materialization, semantic validation, plate/well read APIs, `parent_location_id`, the `txn` WAL shape, or the committed-snapshot surface.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :libraries:jade-tipi-dto:test --rerun-tasks` failed opening `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` (`Operation not permitted`). In a normal developer shell, use the project-documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d` if Mongo-backed tests are needed, then run the required DTO verification command `./gradlew :libraries:jade-tipi-dto:test`.
- Credited developer verification: claude-1 reported `./gradlew :libraries:jade-tipi-dto:test --rerun-tasks` passing with `MessageSpec` `tests=39, failures=0, errors=0, skipped=0` and `UnitSpec` `tests=8, failures=0, errors=0, skipped=0`.
- Additional director static checks passed: `git diff --check HEAD~1..HEAD` produced no output, both new JSON resources parsed successfully with Node, and changed-file ownership was confined to task-authorized paths plus the developer report.
- Follow-up: `TASK-009` was created for pre-work on the next bounded unit: the smallest committed-transaction materialization path for `loc`, `typ` link-type declarations, and `lnk` links. Semantic reference validation, read/query APIs, HTTP submission rebuilds, and policy changes remain out of scope until separately directed.

Implementation complete on 2026-05-01. Status moved to `READY_FOR_REVIEW`.

Two canonical example messages were added under
`libraries/jade-tipi-dto/src/main/resources/example/message/`:

- `11-create-contents-type.json` — `typ + create` envelope declaring the
  `contents` link type. Reuses the canonical batch `txn.uuid`
  `018fd849-2a40-7abc-8a45-111111111111` (matching
  `10-create-location.json`). The `data` payload has
  `kind: "link_type"`, ID
  `jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents`,
  and the six declarative facts from `DIRECTION.md`:
  `left_role: "container"`, `right_role: "content"`,
  `left_to_right_label: "contains"`,
  `right_to_left_label: "contained_by"`,
  `allowed_left_collections: ["loc"]`,
  `allowed_right_collections: ["loc", "ent"]`.
- `12-create-contents-link-plate-sample.json` — `lnk + create` envelope
  for a concrete plate→sample containment, reusing the same canonical
  `txn.uuid`. The `data` payload has ID
  `jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1`,
  `type_id` pointing at the `~typ~contents` declaration, `left` pointing
  at a plate `~loc~plate_b1` and `right` pointing at a sample
  `~ent~sample_x1`, and a `properties.position` object
  `{kind: "plate_well", label: "A1", row: "A", column: 1}`. Per the
  director's pre-work review, the `"A1"` / `"A"` value casing matches
  `DIRECTION.md`; the snake_case rule applies to property keys, not
  values.

Three-letter ID segments (`~typ~`, `~lnk~`, `~loc~`, `~ent~`) per
`DIRECTION.md` and `TASK-007`. The older `04-create-entity-type.json`
`~ty~` example is intentionally left unchanged.

`MessageSpec` was extended:

- `EXAMPLE_PATHS` now includes both new paths, so the existing
  `@Unroll` round-trip and schema-validate features cover examples 01
  through 12.
- New focused feature
  `"contents typ example declares the canonical link-type facts"`
  asserts `Collection.TYPE`, `Action.CREATE`, the canonical
  `~typ~contents` ID, and all six declarative `data` facts.
- New focused feature
  `"contents lnk example references the contents type and carries a position property"`
  asserts `Collection.LINK`, `Action.CREATE`, the
  `~lnk~plate_b1_sample_x1` / `~typ~contents` / `~loc~plate_b1` /
  `~ent~sample_x1` reference suffixes, and the `position`
  `kind`/`label`/`row`/`column` values.

`docs/architecture/kafka-transaction-message-vocabulary.md` got a new
"Link Types And Concrete Links" section after "Property Value
Assignment" that records: containment is canonical in `lnk`, link-type
semantics live in `typ` as `link_type` declarations (mirroring the
`ppy` `definition`/`assignment` discriminator), `loc` records do not
carry parentage, and semantic reference validation
(`type_id` resolution, `left`/`right` resolution,
`allowed_*_collections` matching) is not enforced today and is a
follow-up reader/materializer concern. The numbered reference-examples
list now includes `11-create-contents-type.json` and
`12-create-contents-link-plate-sample.json`.

Out of scope and unchanged: `docs/Jade-Tipi.md`, `DIRECTION.md`,
`Collection`, `Action`, `Message`, `message.schema.json`, every
backend service/listener/controller/initializer, build files, Docker
Compose, security policy, HTTP wrappers, materialization, semantic
reference validation, plate/well read APIs, `parent_location_id`, the
`txn` write-ahead log shape, and the committed-snapshot service /
controller. No supporting endpoint create examples were added —
unresolved `left`/`right` references are acceptable because semantic
reference validation is out of scope.

Verification: `./gradlew :libraries:jade-tipi-dto:test --rerun-tasks`
ran with **BUILD SUCCESSFUL**. The XML test report shows `MessageSpec`
`tests=39, failures=0, errors=0, skipped=0` (33 prior features + the
two new `EXAMPLE_PATHS` rows in each `@Unroll` feature + the two new
focused features) and `UnitSpec`
`tests=8, failures=0, errors=0, skipped=0`. Defensive
`./gradlew :libraries:jade-tipi-dto:compileJava` runs as part of the
test task and succeeded. No backend tests were required by the task
directive and none were run.
