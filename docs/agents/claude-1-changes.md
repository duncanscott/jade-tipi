# claude-1 Changes

The developer writes completed work reports here.

STATUS: COMPLETED

## TASK-002 — Define Kafka transaction message vocabulary

Goal completed. The Kafka-first transaction message envelope now carries a
first-class `collection` field, the JSON Schema enforces it (and constrains
action/collection pairs), example messages cover the full open → property
definitions → type → entity → property assignments → commit flow, the DTO
is round-trip and validation tested, and the `kli` CLI threads `collection`
through every command without dropping it on pass-through.

### Changes by acceptance criterion

- **`Message` includes a first-class `collection` field.**
  - `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Message.java`:
    Record now `(txn, uuid, collection, action, data)`. The single
    `newInstance` factory now takes `Collection` so callers must declare
    the target collection explicitly. `getId()` is unchanged
    (`<txn>~<uuid>~<action>`) and is now annotated `@JsonIgnore` to keep
    the synthetic id out of round-tripped JSON. Equality is still
    `(txn, uuid)`. Class-level Javadoc updated to match.

- **`message.schema.json` requires and validates `collection`.**
  - `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:
    `collection` added to top-level `required` and `properties`; new
    `$defs/Collection` enum mirrors the eight abbreviations
    (`ent`, `grp`, `lnk`, `ppy`, `typ`, `txn`, `uni`, `vdn`).
    Two top-level `allOf/if/then` clauses encode action/collection
    compatibility: `txn` accepts only `open|rollback|commit`; the data
    collections accept only `create|update|delete`.
  - The `examples` block at the bottom of the schema was refreshed to
    coherent post-collection messages.
  - Side fix in the same file: `$defs/SnakeCaseValue` was changed from
    `oneOf` to `anyOf`, removing the `integer` branch. The previous
    `oneOf` was unsatisfiable for any whole number (matched both the
    `number` and `integer` branches), which blocked the explicit
    acceptance-criterion example `{ "number": 10, "unit_id": "..." }`.

- **Example messages cover the full early transaction flow.**
  - `libraries/jade-tipi-dto/src/main/resources/example/message/`:
    Removed the legacy single `open-transaction.json`. Added the full
    nine-file ordered sequence
    (`01-open-transaction.json` through `09-commit-transaction.json`)
    covering open → text and numeric property definitions → entity type
    → type-property attachment → entity → text and numeric value
    assignments → commit. Property values use the wrapper convention,
    including `{ "text": "barcode-1" }` and
    `{ "number": 10, "unit_id": "..." }`.

- **CLI produces or passes through the updated shape without dropping
  `collection`.**
  - `clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/KafkaCli.groovy`:
    Imports `org.jadetipi.dto.message.Collection`. `open`, `rollback`,
    and `commit` build messages with `Collection.TRANSACTION`. The
    shared `handleEntityAction` (used by `create`, `update`, `delete`)
    requires `--collection`/`-c`, parses through `Collection.fromJson`,
    rejects unknown values, rejects the reserved `txn` collection for
    these commands, and rejects action/collection combinations that
    violate `Collection.getActions()`. `publish` now warns when the
    parsed file omits `collection`, surfacing missing fields rather
    than silently dropping them, and the verbose log includes the
    collection.

- **Tests validate the updated DTO schema and examples.**
  - `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
    (new): nine bundled examples are parsed, round-tripped through
    `JsonMapper`, and run through `Message.validate()`; the spec also
    asserts that `newInstance` populates `collection`, that emitted
    JSON includes `collection`, that the schema rejects messages
    missing `collection`, that `Collection.fromJson` rejects unknown
    abbreviations, that `txn` paired with a data action and a data
    collection paired with `open` both fail validation, and that
    `getId()` and `equals/hashCode` keep their pre-task semantics.

- **`UnitSpec` block-label prerequisite (test-harness, reported
  separately).**
  - `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/UnitSpec.groovy`:
    The `all SI units from JSONL validate successfully` feature
    previously had `expect:` after `when:`, which Spock rejects at
    compile time and which blocked
    `./gradlew :libraries:jade-tipi-dto:test`. Restructured to a
    `given:`/`expect:`/`and:` shape with no behavior change. No other
    feature methods were touched.

### Verification

- `./gradlew :libraries:jade-tipi-dto:test` — BUILD SUCCESSFUL.
  The new `MessageSpec` (round-trip × 9, schema validate × 9,
  newInstance, JSON shape, missing-collection rejection,
  unknown-abbreviation rejection, two action/collection mismatches,
  ID format, equality) and the existing `UnitSpec` all pass after the
  block-label restructuring.
- `./gradlew :clients:kafka-kli:build` — BUILD SUCCESSFUL with the new
  `Collection` import and `--collection` plumbing. No new Kafka
  producer was exercised (no Kafka broker required).
- Full repository test runs (`./gradlew test` /
  `./gradlew integrationTest`) were not run because they require the
  Docker stack (`docker compose -f docker/docker-compose.yml up`) per
  `DIRECTIVES.md` and `CLAUDE.md`, and TASK-002 acceptance criteria
  explicitly do not cover Spring Boot integration.

### Out-of-scope items deferred (with rationale)

- **`clients/kafka-kli/src/test/resources/sample-message.json` not
  updated.** This file lacks `collection` and would publish
  `"collection": null` through the new `kli publish` warning path. The
  `clients/kafka-kli/src/test/resources/` directory is not in the
  TASK-002 scope expansion (only `clients/kafka-kli/src/main/groovy/...`
  and `clients/kafka-kli/src/test/groovy/...` are listed). The
  `publish` command still functions; it simply warns. A follow-up
  scope expansion can refresh the sample.
- **No Spock spec added under
  `clients/kafka-kli/src/test/groovy/org/jadetipi/kafka/cli/`.**
  The pre-work plan proposed a `KafkaCliMessageSpec`. `clients/kafka-kli/build.gradle`
  has no Spock or JUnit test dependency declarations and no
  `useJUnitPlatform()`, so no Spock spec can be compiled or run there
  without modifying that build file. `build.gradle` is not in the
  TASK-002 scope expansion. The DTO `MessageSpec` already exercises
  every message-shape concern that spec would have covered (round
  trip through `JsonMapper`, `collection` preservation, schema
  validation). A follow-up scope expansion to the
  `clients/kafka-kli/build.gradle` would unlock CLI-level Spock
  coverage if desired.
- **`Transaction.user` null-handling.** `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/collections/Transaction.java`
  is not in the TASK-002 scope expansion. Jackson serializes a null
  `user` component as `"user": null`, which the existing schema
  rejects (`type: string, minLength: 1`). The bundled example
  messages all set `user` to a representative ORCID iD, sidestepping
  the issue. A follow-up may either add `@JsonInclude(NON_NULL)` on
  `Transaction.user` or relax the schema; either change is broader
  than this task asked for.
- **No semantic reference validation.** Property assignments are not
  yet checked against the registered `value_schema` of the referenced
  property definition, and entity references are not yet checked
  against the type's declared property attachments. These were
  explicitly listed in `OUT_OF_SCOPE` and remain there.

### Files changed

- `docs/architecture/kafka-transaction-message-vocabulary.md`
- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/Message.java`
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
- `libraries/jade-tipi-dto/src/main/resources/example/message/` (removed
  `open-transaction.json`, added `01-…` through `09-…` files)
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy` (new)
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/UnitSpec.groovy`
- `clients/kafka-kli/src/main/groovy/org/jadetipi/kafka/cli/KafkaCli.groovy`
