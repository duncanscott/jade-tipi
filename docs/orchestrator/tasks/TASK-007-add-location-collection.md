# TASK-007 - Add location collection

ID: TASK-007
TYPE: implementation
STATUS: READY_FOR_PREWORK
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
