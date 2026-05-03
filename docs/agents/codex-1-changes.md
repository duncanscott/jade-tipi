# codex-1 Changes

STATUS: READY_FOR_REVIEW
TASK: VALIDATE-TASK-030-e5986755 - Validate TASK-030 staging candidate e5986755
DATE: 2026-05-03
VERDICT: PASSED
STAGING_WORKTREE: /Users/duncanscott/orchestrator/jade-tipi/staging
STAGING_COMMIT: e598675514ee82dd24c5e31bb2506f9704cded08
PROMOTION: Staging can be pushed/promoted for TASK-030.

Validation summary:
- Confirmed the frozen staging worktree still points at
  `e598675514ee82dd24c5e31bb2506f9704cded08`.
- Confirmed staging has no local file modifications:
  `git -C /Users/duncanscott/orchestrator/jade-tipi/staging status --short --branch`
  reported `## staging...origin/staging [ahead 9]`.
- Reviewed `DIRECTIVES.md`, the accepted TASK-030 task file, and
  `docs/agents/claude-1-changes.md`; the accepted behavior is the bounded
  `typ + update` `data.operation == "add_property"` path only.
- Reviewed the staging source changes in
  `CommittedTransactionMaterializer.groovy`,
  `MaterializeResult.groovy`, the focused materializer/DTO/integration specs,
  and the updated architecture/overview docs. The implementation matches the
  accepted scope: it writes `properties.property_refs.<property_id>` on an
  existing root-shaped `typ` document, carries only wire-provided metadata,
  counts missing target roots as `skippedMissingTarget`, treats matching
  repeats as `duplicateMatching`, refuses conflicting metadata as
  `conflictingDuplicate`, and does not rewrite `_head.provenance`.
- Scope review passed: the commit changed only the TASK-030 implementation,
  test, report, and documentation paths listed in the accepted task file.
  No HTTP submission endpoint, `ppy + create` materialization, semantic
  property-id resolution, property-value assignment materialization,
  permission enforcement, contents-link read change, broad ID cleanup,
  endpoint projection maintenance, or nested Kafka DSL was added.

Commands and checks:
- `git -C /Users/duncanscott/orchestrator/jade-tipi/staging rev-parse HEAD`
  -> `e598675514ee82dd24c5e31bb2506f9704cded08`.
- `git -C /Users/duncanscott/orchestrator/jade-tipi/staging status --short --branch`
  -> clean worktree on `staging`, ahead of `origin/staging`.
- `git -C /Users/duncanscott/orchestrator/jade-tipi/staging diff --check e598675514ee82dd24c5e31bb2506f9704cded08^ e598675514ee82dd24c5e31bb2506f9704cded08`
  -> passed.
- `git -C /Users/duncanscott/orchestrator/jade-tipi/staging show --format=short --check e598675514ee82dd24c5e31bb2506f9704cded08 --`
  -> passed.
- `./gradlew :libraries:jade-tipi-dto:test` from the staging worktree was
  blocked before product tests by sandbox/tooling permissions opening
  `/Users/duncanscott/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck`
  with `Operation not permitted`. This matches the director-recorded local
  setup friction and is not a product blocker.

Normal-shell verification commands:
- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` if the
  Mongo-backed unit-test stack is missing.
- `docker compose -f docker/docker-compose.yml up -d` if rerunning the full
  Kafka/Mongo integration suite.
- `./gradlew --stop` only if stale Gradle daemons are implicated.
- `./gradlew :libraries:jade-tipi-dto:test`
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*EntityCreateKafkaMaterializeIntegrationSpec*'`

Credited prior verification:
- claude-1 reported the DTO test, backend test, focused Kafka integration test,
  and full `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest` passing
  with the local Docker stack healthy.
- The director accepted TASK-030 after source/scope/assertion review and
  recorded the same Gradle wrapper lock sandbox blocker for director reruns.

Previous report retained below.

---

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
