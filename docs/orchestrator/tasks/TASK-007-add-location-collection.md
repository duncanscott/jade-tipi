# TASK-007 - Add location collection

ID: TASK-007
TYPE: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
OWNED_PATHS:
  - DIRECTION.md
  - docs/Jade-Tipi.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/mongo/config/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/
  - docs/orchestrator/tasks/TASK-007-add-location-collection.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - gradle-verification
GOAL:
Add `loc` as the first-class Jade-Tipi location collection so the Spring Boot application creates the MongoDB `loc` collection at startup and Kafka/DTO messages can target location records.

ACCEPTANCE_CRITERIA:
- Pre-work inspects `DIRECTION.md`, `Collection.java`, `message.schema.json`, `MongoDbInitializer`, existing DTO tests, and any existing initializer/startup tests before implementation.
- `org.jadetipi.dto.message.Collection` includes a `LOCATION("location", "loc")` value that serializes/deserializes as `loc`.
- `message.schema.json` accepts `collection: "loc"` and enforces the same non-transaction action compatibility as the other long-term domain collections: `create`, `update`, and `delete`; `open`, `commit`, and `rollback` remain transaction-only.
- The Spring Boot Mongo startup path creates `loc` through the existing `MongoDbInitializer` collection-enum loop. Do not add Docker or Mongo shell initialization for this.
- Tests cover `Collection.fromJson("loc")`, JSON serialization of `loc`, schema acceptance of a canonical `loc` create message, schema rejection of transaction-only actions with `loc`, and startup/initializer behavior for the new collection if practical.
- Documentation that lists collection abbreviations is updated to include `location (loc)` and to preserve the distinction that `txn` is the transaction log/staging collection.
- Verification includes at least `./gradlew :libraries:jade-tipi-dto:test` and a narrow `:jade-tipi` compile/test command selected during pre-work. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the documented setup command instead of treating setup as a product blocker.

OUT_OF_SCOPE:
- Do not implement `loc` materialization from committed `txn` messages into the long-term `loc` collection.
- Do not implement the `contents` link type, link materialization, plate/well query APIs, or "what is in this plate?" reads.
- Do not add `parent_location_id` to `loc` records; containment remains canonical in `lnk` per `DIRECTION.md`.
- Do not change the Kafka listener, transaction persistence record shape, commit semantics, or committed transaction snapshot API except where tests need canonical examples.
- Do not rebuild HTTP submission wrappers or add new authorization/scoping policy.

DESIGN_NOTES:
- Current `MongoDbInitializer` creates `tipi` and then iterates over `Collection.values()` to create each collection by abbreviation. Adding `LOCATION("location", "loc")` to the enum should be sufficient for application startup to create MongoDB collection `loc`.
- `loc` is a long-term domain collection alongside `ent`, `ppy`, `lnk`, `uni`, `grp`, `typ`, and `vdn`. `txn` is still special and remains the durable transaction write-ahead log/staging collection.
- Early location records should describe physical/addressable nodes such as buildings, rooms, freezers, shelves, racks, boxes, tubes, plates, and possibly wells. Relationship semantics such as containment belong to `lnk` and typed link definitions in `typ`, not to this task.

DEPENDENCIES:
- `TASK-006` is accepted.
- Human direction on location/link modeling is recorded in `DIRECTION.md`.

IMPLEMENTATION_DIRECTIVES:
- Pre-work review passed on 2026-05-01. Scope check passed: claude-1 changed only `docs/agents/claude-1-next-step.md`, inside the developer-owned pre-work paths.
- Implement the small enum/schema/startup path proposed in pre-work: add `LOCATION("location", "loc")` to `Collection`, add `"loc"` to `message.schema.json`'s collection enum and non-transaction data-action conditional, and rely on the existing `MongoDbInitializer` `Collection.values()` loop for startup collection creation. Do not change `MongoDbInitializer.groovy` unless implementation reveals a narrow testability issue.
- Use a canonical example file named `10-create-location.json`. In the example payload, use the `loc` collection abbreviation consistently, including the example object ID suffix (`...~loc~...`) to match `DIRECTION.md`; do not introduce a separate `lo` suffix in this task.
- Extend `MessageSpec` for `Collection.fromJson("loc")`, JSON serialization as `"loc"`, schema acceptance for a canonical `loc` create message, and schema rejection for each transaction-control action with `loc`.
- Add practical startup/initializer coverage under `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/`, preferably as a pure Spock spec that mocks `ReactiveMongoTemplate` and proves `loc` is created through the enum-driven initializer loop. Keep enum-shape assertions primarily in DTO tests; backend coverage should focus on initializer behavior.
- Update documentation that lists collection abbreviations: add `location (loc)` and keep `transaction (txn)` clearly described as the special transaction log/staging collection. Leave `DIRECTION.md` unchanged unless an implementation detail actually contradicts it.
- Preserve out-of-scope boundaries: no `loc` materialization from committed `txn` messages, no `contents` link type implementation, no link materializer, no plate/well read APIs, no `parent_location_id`, no Kafka listener or transaction persistence shape changes, no committed snapshot API changes, and no HTTP submission/security work.
- Verification should include at least `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test`. If local tooling, Gradle locks, or Docker/Mongo are unavailable, report the project-documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d` and the exact verification command that could not run rather than treating setup as a product blocker.

LATEST_REPORT:
Director pre-work review on 2026-05-01:
- Accepted claude-1 pre-work commit `0217921`.
- The plan satisfies the TASK-007 pre-work acceptance criteria. It inspected `DIRECTION.md`, `Collection.java`, `message.schema.json`, `MongoDbInitializer`, existing DTO tests, current examples, documentation, and startup-test options before proposing implementation.
- Scope check passed for the pre-work turn. The latest claude-1 commit changed only `docs/agents/claude-1-next-step.md`, which is inside the developer-owned path set from `docs/agents/claude-1.md`.
- Source spot-check confirms the plan's main assumptions: `Collection` already serializes with `@JsonValue`, `fromJson` accepts name or abbreviation, non-transaction actions are assigned by the enum constructor, `message.schema.json` has one non-transaction action conditional to extend, and `MongoDbInitializer` creates collections by iterating `Collection.values()`.
- The director resolved the optional pre-work questions inline: use `loc` rather than `lo` as the example ID suffix, use `10-create-location.json`, keep backend startup coverage focused on initializer behavior, do not require a live-Mongo bootRun/mongosh check for this task, leave `DIRECTION.md` unchanged, and keep all out-of-scope boundaries unchanged.
