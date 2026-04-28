# codex-1 Changes

STATUS: READY_FOR_REVIEW

## TASK-001 - Restore DTO Units Test Baseline

Implementation:
- Updated `UnitSpec` to read the existing `/units/jsonl/si_units.jsonl` resource and assert the current 812-row SI corpus.
- Added coverage for imported unit metadata: `property` is present on every row, and `alternate_unit` is present on 100 rows including the base `meter` row.
- Reconciled `Unit` with the imported SI JSONL shape by adding optional `property` and `alternate_unit` fields while retaining the existing 4-argument constructor for current callers.
- Updated `unit.schema.json` to allow the optional imported metadata fields. They are optional and nullable so existing DTO construction still validates, while imported JSONL rows with populated metadata validate against the DTO schema.

Verification:
- `jq empty libraries/jade-tipi-dto/src/main/resources/schema/unit.schema.json` passed.
- `jq empty libraries/jade-tipi-dto/src/main/resources/units/jsonl/si_units.jsonl` passed.
- `jq -r 'keys_unsorted[]' libraries/jade-tipi-dto/src/main/resources/units/jsonl/si_units.jsonl | sort | uniq -c` confirmed `property` on 812 rows and `alternate_unit` on 100 rows.
- `GRADLE_USER_HOME=/tmp/codex-gradle ./gradlew :libraries:jade-tipi-dto:test --no-daemon` could not complete in this sandbox. First attempt needed to download the Gradle wrapper distribution but network DNS for `services.gradle.org` is unavailable; after staging the cached Gradle 8.14.3 distribution under `/tmp/codex-gradle`, Gradle still failed before build configuration with `java.net.SocketException: Operation not permitted` from `FileLockContentionHandler`.

Setup command to run in a normal developer shell:
- `./gradlew :libraries:jade-tipi-dto:test`
