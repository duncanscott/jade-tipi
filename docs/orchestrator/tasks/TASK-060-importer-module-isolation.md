# TASK-060 - Importer module isolation

ID: TASK-060
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-059
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-060-importer-module-isolation.md
  - docs/sharing.md
  - DIRECTION.md
  - settings.gradle
  - importers/jgi-import/
  - jade-tipi/
REQUIRED_CAPABILITIES:
  - gradle-verification
  - kafka-integration

GOAL:
Director direction 2026-07-05: the JGI-specific importer code must not be
shared if the project is shared. Isolate everything JGI-specific into an
excisable Gradle module (`importers/jgi-import`) so a shared snapshot
drops one directory and one settings.gradle line — and record the share
procedure, including why a plain branch cannot be the mechanism (branches
share history; excision needs a fresh snapshot-seeded repository).

DESIGN:
- New module `importers/jgi-import` holding: the whole
  `org.jadetipi.jadetipi.importer` package (TASK-043 container mappers +
  reader, TASK-059 queue/planner/mapper), their unit specs (including
  the container mapping spec that exercises the mapper against the
  materializer), all `importfixtures` (real LIMS documents), the three
  JGI integration specs (aliquot import, CouchDB import loop, review
  seed), and the bulk-import design doc under the module's own `docs/`.
- Dependency direction: module → app only, never the reverse. The
  module's unit tests may depend on the app (plain jar); its integration
  specs boot the application context explicitly
  (`@SpringBootTest(classes = JadetipiApplication)` — cross-module specs
  cannot auto-discover the boot configuration) and carry their own copy
  of the test profile. The app's runtime no longer contains importer
  beans at all (they were import-time tooling; nothing in the app
  referenced them).
- `docs/sharing.md` records what is JGI-internal, the branch-history
  caveat, and the snapshot-based share procedure (fresh repo, module
  deleted, task-file curation pass, build verification).

ACCEPTANCE_CRITERIA:
- The app module contains no importer code, fixtures, or JGI-specific
  specs; `:jade-tipi:test` and the app integration suite pass unchanged.
- `:importers:jgi-import:test` passes (queue, planner, both mappers,
  container mapping) and the module's integration specs compile and run
  under the same gates as before (live aliquot import proven
  end to end).
- Deleting `importers/` plus its settings.gradle include leaves a
  building application (the excision property).
- `docs/sharing.md` exists; DIRECTION.md's Bulk Import section points at
  the design doc's new location.

OUT_OF_SCOPE:
- No separate repository (director: complexity not warranted).
- No history surgery on existing commits — the share procedure produces
  history-clean sharing by construction instead.
- No production import trigger (still follow-on to TASK-059).

VERIFICATION:
- `./gradlew test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest`
- excision check: build the app with the module directory and include
  removed
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- Everything JGI-specific moved (git mv, history preserved) into
  `importers/jgi-import`: the nine importer main classes, four importer
  unit specs plus the container mapping spec (formerly in the app's
  service test package — it exercises the mapper against the
  materializer, so it repackaged into the module with explicit service
  imports), all importfixtures (real LIMS documents), the three JGI
  integration specs (aliquot import, CouchDB import loop, review seed),
  and the bulk-import design doc under `importers/jgi-import/docs/`.
- Module build: java+groovy with the shared repository and
  integration-test scripts; Boot BOM via platform; unit tests may
  depend on the app's plain jar (module → app, never the reverse — the
  app has zero importer references and its runtime no longer contains
  importer beans). The moved integration specs declare
  `@SpringBootTest(classes = JadetipiApplication)` (cross-module specs
  cannot auto-discover the boot configuration) and the module carries
  its own copy of the test profile yaml. Two classpath lessons pinned
  in the build file: spock-spring without spring-test breaks Spock
  discovery (scoped to integrationTest, where starter-test provides
  spring-test), and Spock class mocking needs byte-buddy/objenesis
  explicitly outside starter-test.
- `docs/sharing.md`: what is JGI-internal (module + one settings line +
  a task-file curation concern), why a plain branch cannot be the
  mechanism (branches share history), and the snapshot-seeded fresh-repo
  share procedure. DIRECTION.md's Bulk Import section points at the
  design doc's new home and the sharing note.
- Verification results: `:jade-tipi:test`, `:importers:jgi-import:test`
  (30 tests), dto tests, and both integration suites green in one run
  (1m51s) — the live aliquot import passing from its new home, the
  env-gated legacy import specs skipping exactly as before, the app
  suite unchanged. Excision check performed literally: with
  `importers/` moved away and the include removed, the app built
  successfully; module restored. Frontend Playwright 16/16;
  `git diff --check` clean.
