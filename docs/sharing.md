# Sharing Jade-Tipi

Director direction (2026-07-05): if this project is shared, the
JGI-specific importer code is **not** shared. The importer lives in its
own excisable Gradle module so that sharing is a mechanical procedure,
not a judgment call.

## What is JGI-internal

- `importers/jgi-import/` — the entire module: importer code (mappers,
  planner, import queue), its tests, real LIMS fixture documents, its
  integration specs, and its docs (including
  `importers/jgi-import/docs/bulk-import-design.md`, which describes the
  Clarity/ESP source schemas in detail).
- One line in `settings.gradle`: `include 'importers:jgi-import'`.
- A curation concern, not a deletion target: the orchestrator task files
  (`docs/orchestrator/tasks/`) are the project's process record and some
  reference JGI systems in prose (e.g. TASK-042/043/059). Any share event
  includes a curation pass over them.

The application itself never depends on the importer module — the
dependency points only from the module to the app (its integration tests
boot the application context). Deleting the module changes no application
behavior.

## Why a plain branch is NOT the mechanism

Git branches share the repository's commit history. A `public` branch
created from `develop` with the importer directory deleted still carries
every importer commit in its reachable history — pushing that branch
publishes them. Removing code retroactively requires history surgery
(`git filter-repo`), which is easy to get wrong.

## The share procedure

Share via a **fresh repository seeded from a single snapshot commit**,
never by pushing an existing branch:

1. Copy a clean checkout of the desired commit (no `.git`).
2. Delete `importers/` and remove the `include 'importers:jgi-import'`
   line from `settings.gradle`.
3. Curate `docs/orchestrator/tasks/` (and any other prose) for internal
   references.
4. Verify the build: `./gradlew build` and the frontend build must pass
   without the module.
5. `git init`, single initial commit, push to the public remote.

The result has no importer code and — by construction — no importer
history.
