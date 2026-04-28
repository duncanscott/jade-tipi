# Director Directives

SIGNAL: REQUEST_NEXT_STEP

## Active Focus

Restore the Jade Tipi DTO units test baseline before resuming larger Kafka transaction work.

## Active Task

- `TASK-001`: Restore DTO units test baseline
- Owner: `codex-1`
- Current status: `READY_FOR_PREWORK`

## Scope Expansion

For `TASK-001`, `codex-1` may inspect and propose changes within:

- `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/collections/`
- `libraries/jade-tipi-dto/src/main/resources/schema/`
- `libraries/jade-tipi-dto/src/main/resources/units/`
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/collections/`

The first routed action should produce pre-work only in `docs/agents/codex-1-next-step.md`. Do not begin implementation until the task status is advanced to `READY_FOR_IMPLEMENTATION`.

## Known Baseline

- `./gradlew :libraries:jade-tipi-dto:test` currently fails because `UnitSpec` references removed resource path `/units/jade_tipi_si_units.jsonl`.
- `./gradlew test` also fails without MongoDB because `JadetipiApplicationTests.contextLoads` attempts a Mongo connection; that environment issue is not part of `TASK-001`.
- `./gradlew :clients:kafka-kli:build` currently passes.
