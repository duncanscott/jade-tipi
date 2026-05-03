# TASK-025 - Plan TypeScript 6 frontend upgrade

ID: TASK-025
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-024
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - frontend/package.json
  - frontend/package-lock.json
  - frontend/tsconfig.json
  - frontend/app/
  - frontend/components/
  - frontend/lib/
  - frontend/types/
  - frontend/tests/
  - docs/orchestrator/tasks/TASK-025-typescript-6-upgrade-prework.md
REQUIRED_CAPABILITIES:
  - code-implementation
  - local-builds
GOAL:
Evaluate and, if safe after director pre-work review, upgrade the frontend
from the accepted TypeScript 5.x line to the latest stable TypeScript 6 line.

ACCEPTANCE_CRITERIA:
- Inspect current `frontend/package.json`, `frontend/package-lock.json`,
  `frontend/tsconfig.json`, and the Next.js/React type-check surface.
- Determine the latest stable TypeScript 6 version available at pre-work time
  using npm metadata, and identify whether Next.js 16.2.4 and the installed
  React/Node type packages officially support it.
- Document expected migration risks, especially compiler option changes,
  stricter checks, DOM/lib typing changes, generated `.next` types, and
  App Router type generation.
- Propose the smallest implementation plan and exact verification commands.
- Stop after pre-work. Do not update TypeScript or source files until the
  director advances this task to `READY_FOR_IMPLEMENTATION`.

OUT_OF_SCOPE:
- Do not update Next.js, React, NextAuth/Auth.js, Tailwind, Playwright, backend
  code, Docker, Keycloak, or Jade-Tipi domain behavior.
- Do not redesign frontend UI or authentication behavior.
- Do not apply TypeScript 6 source migrations during pre-work.

PREWORK_REQUIREMENTS:
- Write the plan in `docs/agents/claude-1-next-step.md`.
- Include current and target TypeScript versions, compatibility evidence,
  migration risks, expected file touch list, and verification commands.
- If TypeScript 6 is not a stable npm release or is not compatible with the
  accepted Next.js/React stack, recommend `HUMAN_REQUIRED` or a narrower
  follow-up instead of implementation.

VERIFICATION:
- Expected implementation-turn commands, after director approval:
  - `cd frontend && npm install`
  - `cd frontend && npm run build`
  - `cd frontend && npm test` or the narrowest practical Playwright command
- If npm registry access, local Node setup, or Playwright browser/server setup
  blocks verification, report the exact command and setup issue rather than
  treating it as a product blocker.

DIRECTOR_PREWORK_REVIEW:
- Reviewed claude-1 pre-work in `docs/agents/claude-1-next-step.md`.
- Ownership check passed: the pre-work commit changed only
  `docs/agents/claude-1-next-step.md`, which is within claude-1's base owned
  paths for this phase.
- The plan satisfies the TASK-025 pre-work requirements: it identifies the
  current TypeScript surface, target `typescript@6.0.3`, compatibility evidence,
  migration risks, expected file touch list, verification commands, and a
  backout path.
- Local npm metadata verification from the director sandbox failed with
  `getaddrinfo ENOTFOUND registry.npmjs.org` for `npm view typescript`,
  `npm view next@16.2.4`, and `npm view @types/*`. Treat this as registry/DNS
  setup friction, not a product blocker. Re-run the same `npm view ...`
  commands or the implementation `npm install --save-dev typescript@6.0.3`
  when registry access is available.
- External cross-checks found TypeScript 6.0 is stable and Next.js 16 documents
  TypeScript 5.1+ as the minimum, not an upper bound. Snyk's npm package mirror
  currently reports `typescript@6.0.3` as the latest TypeScript package version.
- Implementation caveat: Next.js documentation says the custom TypeScript
  plugin/type checker is used by `next build`, so do not treat plugin
  compatibility as IDE-only. The required `npm run build` gate is the right
  place to prove this with the project's installed TypeScript.
- Implementation caveat: TypeScript 6.0 introduces default/deprecation changes
  beyond deprecated option removals, including `types` behavior, `rootDir`,
  `noUncheckedSideEffectImports`, `libReplacement`, DOM/lib updates, and command
  line behavior. The project already pins several relevant compiler options, but
  any build error from those areas should be reported or fixed narrowly inside
  TASK-025 owned paths.
- Proceed with the smallest implementation: bump only TypeScript and the
  lockfile first, run `npm run build`, then apply only build-driven source or
  `tsconfig.json` adjustments inside owned paths. If errors fan out broadly or
  generated `.next` types are incompatible, stop with `STATUS: BLOCKED` and
  include the exact errors and backout command.

DIRECTOR_IMPLEMENTATION_REVIEW:
- Accepted on 2026-05-03. claude-1 implemented the smallest approved change:
  `frontend/package.json` now targets `typescript` `^6.0.3`, and
  `frontend/package-lock.json` updates only the root dev dependency entry and
  `node_modules/typescript` package metadata.
- Scope review passed. The implementation commit changed only
  `frontend/package.json`, `frontend/package-lock.json`, and
  `docs/agents/claude-1-changes.md`. The first two are inside TASK-025
  `OWNED_PATHS`; the report file is inside claude-1's base assignment paths.
  No Next.js, React, NextAuth/Auth.js, Tailwind, Playwright, backend, Docker,
  Keycloak, frontend UI, source, or `tsconfig.json` change was made.
- Director verification passed `git diff --check origin/director..HEAD`,
  `cd frontend && npm install`, `cd frontend && npm run build`, and
  `cd frontend && npx tsc --noEmit`. The build ran Next.js 16.2.4 with
  TypeScript 6.0.3 and completed the TypeScript phase with no diagnostics.
- `cd frontend && npx playwright test --project=chromium --timeout=15000`
  was blocked in the Codex sandbox by the known local port-bind restriction:
  `listen EPERM: operation not permitted 0.0.0.0:3000`. This is local sandbox
  setup friction, not a product blocker. Re-run the same Playwright command in
  a normal developer shell; if browser binaries are missing, use the documented
  setup command `cd frontend && npx playwright install chromium`.
- Director verification used the available local Node `v25.9.0`, while project
  guidance expects Node 20. Because the TypeScript package declares
  `node: >=14.17` and the frontend build/type checks passed, this is not a
  blocker, but a Node 20 shell remains the preferred final environment check.
- No follow-on task is created from this acceptance. The bounded frontend
  dependency refresh and deferred TypeScript 6 migration are complete; the next
  project unit is a product/direction choice rather than an obvious continuation
  of TASK-025.
