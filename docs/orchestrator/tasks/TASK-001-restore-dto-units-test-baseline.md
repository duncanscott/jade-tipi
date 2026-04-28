# TASK-001 - Restore DTO units test baseline

ID: TASK-001
TYPE: investigation
STATUS: READY_FOR_PREWORK
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
Pending.
