# TASK-007 - Add location collection

ID: TASK-007
TYPE: implementation
STATUS: ACCEPTED
OWNER: claude-1
NEXT_TASK:
  - TASK-008
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
Director implementation review on 2026-05-01:
- Accepted claude-1 implementation commit `40aec2e`.
- Acceptance criteria are satisfied. The DTO enum now has `LOCATION("location", "loc")`; the message schema admits `loc` in the collection enum and in the long-term data-action conditional; the canonical `10-create-location.json` example uses `collection: "loc"` and an ID suffix containing `~loc~`; and Spring Boot startup will create `loc` through the existing `MongoDbInitializer` `Collection.values()` loop.
- Required assertions are present. `MessageSpec` covers `Collection.fromJson("loc")`, JSON serialization as `loc`, schema acceptance for `loc + create`, schema rejection for `loc + open|commit|rollback`, and the new example through the existing round-trip/schema-validation rows. `MongoDbInitializerSpec` uses a pure Spock mock to prove `createCollection("loc")` is called when `loc` is missing and not called when it already exists.
- Scope check passed against the active task expansion in this file and `DIRECTIVES.md`. The merge changed only `docs/agents/claude-1-changes.md`, this task file, the approved DTO enum/schema/example/test paths, approved documentation paths, and the approved backend initializer test path. These edits are outside claude-1's base assignment-owned report files, but they are inside the explicit TASK-007 owned-path expansion authorized by `DIRECTIVES.md` and this task file.
- The implementation honored the out-of-scope boundaries: no `loc` materialization from committed `txn` messages, no `contents` link type implementation, no link materializer, no plate/well APIs, no `parent_location_id`, no Kafka listener or transaction persistence shape changes, no committed snapshot API changes, and no HTTP submission/security work.
- Director local verification was blocked before product tests by sandbox/tooling permissions, not by an observed product failure. `./gradlew :libraries:jade-tipi-dto:test` failed opening `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` (`Operation not permitted`), and `docker compose -f docker/docker-compose.yml --profile mongodb ps` could not access the Docker socket. In a normal developer shell, use the documented setup command `docker compose -f docker/docker-compose.yml --profile mongodb up -d`, then run `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test`.
- Credited developer verification: with the Docker stack up, claude-1 reported `./gradlew :libraries:jade-tipi-dto:test`, `./gradlew :jade-tipi:compileGroovy`, `./gradlew :jade-tipi:compileTestGroovy`, `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'`, and `./gradlew :jade-tipi:test` all passing.
- Follow-up: `TASK-008` was created for pre-work on the next bounded location-modeling unit: canonical `contents` type/link vocabulary examples and tests. This keeps materialization, semantic link validation, and read APIs out of scope until the example vocabulary is reviewed.

Implementation report on 2026-05-01 (claude-1):

- DTO enum: `org.jadetipi.dto.message.Collection` now declares
  `LOCATION("location", "loc")` (placed alphabetically between `LINK` and
  `UNIT`). The existing constructor branch on `"transaction".equals(name)` gives
  `LOCATION` the data-mutating action set `[CREATE, UPDATE, DELETE]` with no
  other code change. `fromJson` and `@JsonValue` handle the new value with no
  annotation churn.
- JSON schema: `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  now has `"loc"` in `$defs/Collection.enum` and in the long-term-collection
  enum inside the second `allOf` conditional. The first conditional
  (`collection: "txn"` → transaction-control actions) is unchanged, so a
  `loc + open|commit|rollback` envelope fails the second conditional's
  data-action whitelist.
- Canonical example:
  `libraries/jade-tipi-dto/src/main/resources/example/message/10-create-location.json`
  is a `loc + create` envelope with `id` suffix `~loc~freezer_a` per the
  director directive to use `loc` consistently in example IDs.
- DTO tests: `MessageSpec` now includes
  `/example/message/10-create-location.json` in `EXAMPLE_PATHS` (so the
  existing `@Unroll` round-trip and schema-validate features cover it),
  asserts `Collection.fromJson('loc') == LOCATION` plus the expected name,
  abbreviation, and action set, asserts schema acceptance for a canonical
  `loc + create` envelope, and asserts `@Unroll` schema rejection of
  `loc` paired with each transaction-control action (`open`, `commit`,
  `rollback`). Total `MessageSpec` rows after the change: 33, all green.
- Backend startup coverage: new
  `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/mongo/config/MongoDbInitializerSpec.groovy`
  uses a pure Spock mock of `ReactiveMongoTemplate` to assert (1) the
  initializer calls `createCollection('loc')` exactly once when the `loc`
  collection does not exist while leaving every other collection untouched
  (`0 * createCollection({ name -> name != 'loc' })`), and (2) when `loc`
  already exists no `createCollection` call is issued at all. No change to
  `MongoDbInitializer.groovy`; the new behavior rides on the existing
  `Collection.values()` loop.
- Documentation: `docs/Jade-Tipi.md` lists `location (loc)` alongside the
  other base collection abbreviations and clarifies that `txn` is the
  transaction log/staging collection. `docs/architecture/kafka-transaction-message-vocabulary.md`
  adds `loc` to the prose collection enumeration, to the
  long-term-collection action-compatibility bullet, and to the numbered
  reference-examples list. `DIRECTION.md` is unchanged, per directive.
- Out-of-scope items respected: no `loc` materialization from committed
  `txn` messages, no `contents` link type or link materializer, no
  plate/well APIs, no `parent_location_id`, no Kafka listener or
  transaction persistence shape changes, no committed-snapshot API
  changes, no HTTP submission or security work.

Verification (Docker stack up: `jade-tipi-mongo`, `jade-tipi-kafka`,
`jade-tipi-keycloak` all healthy):

- `./gradlew :libraries:jade-tipi-dto:test` — BUILD SUCCESSFUL.
  `MessageSpec` reports `tests=33, skipped=0, failures=0, errors=0`.
- `./gradlew :jade-tipi:compileGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:compileTestGroovy` — BUILD SUCCESSFUL.
- `./gradlew :jade-tipi:test --tests '*MongoDbInitializerSpec*'` — BUILD
  SUCCESSFUL. `MongoDbInitializerSpec` reports `tests=2, skipped=0,
  failures=0, errors=0`.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL. 56 unit tests across:
  `JadetipiApplicationTests` (1), `TransactionMessageListenerSpec` (4),
  `DocumentServiceMongoDbImplSpec` (9),
  `CommittedTransactionReadServiceSpec` (12),
  `CommittedTransactionReadControllerSpec` (5),
  `TransactionMessagePersistenceServiceSpec` (11),
  `TransactionServiceSpec` (12), `MongoDbInitializerSpec` (2 — new). 0
  failures, 0 errors.

If a future verification fails because Docker / Mongo is not running, the
documented setup command is `docker compose -f docker/docker-compose.yml
--profile mongodb up -d` (only Mongo is strictly required for the unit
suite because `JadetipiApplicationTests.contextLoads` opens a Mongo
connection).
