# codex-1 Next Step

Pre-work response for TASK-001.

STATUS: PREWORK_COMPLETE

## Directive Summary

- Latest `DIRECTIVES.md` signal is `REQUEST_NEXT_STEP`.
- Active task is `TASK-001 - Restore DTO units test baseline`, owned by `codex-1`, currently `READY_FOR_PREWORK`.
- This routed action is pre-work only. Do not implement until the task moves to `READY_FOR_IMPLEMENTATION` or the global directive changes to `PROCEED_TO_IMPLEMENTATION`.
- Scope is expanded for TASK-001 to inspect and propose changes in the DTO unit model, unit schema, imported unit resources, and DTO collection tests.
- Known baseline: `./gradlew :libraries:jade-tipi-dto:test` fails because `UnitSpec` references removed resource `/units/jade_tipi_si_units.jsonl`; full `./gradlew test` has a separate MongoDB dependency outside this task.

## Proposed Plan

When implementation is authorized:

1. Inspect the current JSONL resources under `libraries/jade-tipi-dto/src/main/resources/units/jsonl/` and confirm the intended replacement for the removed SI units fixture. The narrow candidate is `si_units.jsonl`, which currently has 812 rows.
2. Compare that imported row shape with `Unit.java` and `unit.schema.json`. Current `si_units.jsonl` rows include `unit`, `prefix`, `symbol`, `property`, and `system`, while the current schema permits only `unit`, `prefix`, `symbol`, `system`, and read-only `id`.
3. Make the smallest DTO/test/schema adjustment that validates the imported data shape. Likely changes are to point `UnitSpec` at `/units/jsonl/si_units.jsonl`, update the row-count/assertions to the current corpus, and add schema/model support for `property` only if validation confirms it is required by the chosen fixture.
4. Keep changes limited to TASK-001 paths and avoid frontend, Kafka CLI, backend controller, and orchestrator files.
5. Verify from the repository root with `./gradlew :libraries:jade-tipi-dto:test`. If tooling is missing or stale, report the documented setup command rather than treating that as a product blocker.

## Blockers And Open Questions

- No blocker for pre-work.
- Open question for director review: should `UnitSpec` validate the focused SI corpus in `units/jsonl/si_units.jsonl`, or should it validate a broader imported corpus such as `units_of_measurement.jsonl` / `units_with_ontologies.jsonl`? My proposed default is `si_units.jsonl` because it is the closest replacement for the removed SI-only fixture and matches the acceptance criterion requiring an existing `units/jsonl/` path.
