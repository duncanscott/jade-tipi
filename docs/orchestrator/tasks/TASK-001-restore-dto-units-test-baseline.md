# TASK-001 - Restore DTO units test baseline

ID: TASK-001
TYPE: investigation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: codex-1
OWNED_PATHS:
  - libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/collections/
  - libraries/jade-tipi-dto/src/main/resources/schema/
  - libraries/jade-tipi-dto/src/main/resources/units/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/
REQUIRED_CAPABILITIES:
  - source-analysis
GOAL:
Restore a reliable DTO units test baseline after the February 2026 units-of-measure import. Investigate the intended shape of the imported JSONL resources, reconcile `Unit`, `unit.schema.json`, and `UnitSpec`, then make the smallest implementation change needed so the DTO unit tests validate the current bundled data.

ACCEPTANCE_CRITERIA:
- `./gradlew :libraries:jade-tipi-dto:test` passes from the repository root.
- `UnitSpec` reads an existing resource path under `libraries/jade-tipi-dto/src/main/resources/units/jsonl/`.
- Tests cover the current imported data shape instead of the removed `/units/jade_tipi_si_units.jsonl` path.
- If `Unit` or `unit.schema.json` changes, the task report explains why the chosen model matches the imported units data.
- No frontend, Kafka CLI, backend controller, or orchestrator files are changed.

OUT_OF_SCOPE:
- Do not implement the backend Kafka consumer or topic registration.
- Do not redesign the full units ontology or regenerate the imported JSONL corpus.
- Do not start long-running Docker services unless needed to verify an explicitly affected test.

DEPENDENCIES:
- Latest units import commit: `b6cce93 import jsonl directory from units-of-measure project`.
- Current observed failure: `./gradlew :libraries:jade-tipi-dto:test` fails in `UnitSpec` because `/units/jade_tipi_si_units.jsonl` is missing.
- Full `./gradlew test` also depends on MongoDB for `JadetipiApplicationTests`; that environment issue is not the primary target of this task.

LATEST_REPORT:
Director pre-work review on 2026-04-28:
- Reviewed `docs/agents/codex-1-next-step.md` at `9e42af4` and accepted the plan for implementation.
- Scope check passed: the pre-work commit changed only `docs/agents/codex-1-next-step.md`, which is inside codex-1's assigned pre-work ownership boundary.
- Plan is clear enough: use an existing resource under `libraries/jade-tipi-dto/src/main/resources/units/jsonl/`, with `si_units.jsonl` as the preferred replacement for the removed SI-only fixture because it is the closest match to `/units/jade_tipi_si_units.jsonl` and currently has 812 rows.
- Implementation should reconcile `Unit`, `unit.schema.json`, and `UnitSpec` against the selected imported JSONL shape, including the observed `property` field if validation requires it.
- Verification note: `./gradlew :libraries:jade-tipi-dto:test` could not run in this sandbox before reaching product tests. The wrapper first failed creating `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck` due sandbox permissions. Retrying with a writable `GRADLE_USER_HOME` and the cached Gradle 8.14.3 distribution failed because Gradle attempted local socket lock handling and the sandbox returned `java.net.SocketException: Operation not permitted`. On a normal developer shell, run the documented verification command from the repository root: `./gradlew :libraries:jade-tipi-dto:test`.
