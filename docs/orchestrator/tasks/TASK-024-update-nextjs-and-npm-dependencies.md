# TASK-024 - Update Next.js and npm dependencies

ID: TASK-024
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-023
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - frontend/package.json
  - frontend/package-lock.json
  - frontend/app/
  - frontend/components/
  - frontend/lib/
  - frontend/types/
  - frontend/tests/
  - frontend/tsconfig.json
  - frontend/next.config.ts
  - frontend/postcss.config.mjs
  - frontend/playwright.config.ts
  - docs/orchestrator/tasks/TASK-024-update-nextjs-and-npm-dependencies.md
REQUIRED_CAPABILITIES:
  - browser-ui
  - code-implementation
  - local-builds
GOAL:
Update the Next.js frontend to the latest stable Next.js release available at
implementation time and update all npm dependencies/devDependencies in
`frontend/package.json` to current stable compatible versions.

ACCEPTANCE_CRITERIA:
- Inspect the current frontend dependency set with npm tooling, including
  `next`, `react`, `react-dom`, `next-auth`, Playwright, Tailwind,
  TypeScript, and React/Node type packages.
- Determine the latest stable npm versions available at implementation time.
  Prefer npm stable dist-tags. Do not intentionally move to prerelease,
  canary, beta, or release-candidate versions unless the existing dependency
  has no stable replacement and the tradeoff is documented in pre-work.
- Update `frontend/package.json` and `frontend/package-lock.json` together.
  Keep the dependency set minimal; do not add new packages unless a documented
  migration requirement from the upgraded packages makes one necessary.
- Apply any small source migrations required by the dependency upgrade,
  especially for Next.js, React, NextAuth/Auth.js, Tailwind, or Playwright
  API changes.
- Allow small frontend configuration-file migrations required or automatically
  applied by the upgraded toolchain, including TypeScript, Next.js, PostCSS,
  and Playwright config files listed in `OWNED_PATHS`.
- Preserve the existing admin group-management workflow, Keycloak login,
  access-token refresh behavior, document CRUD pages, and test routes.
- Run `cd frontend && npm install`, `cd frontend && npm run build`, and the
  narrowest practical frontend test command. If Playwright browser binaries
  are missing, report the setup command rather than treating that as a product
  failure.
- If the latest stable update reveals a broad framework migration that is too
  large for one task, stop after pre-work with a proposed split and do not
  begin implementation without director approval.

OUT_OF_SCOPE:
- Do not change backend Gradle dependencies, Spring Boot code, Docker stack,
  Keycloak realm import, Mongo/CouchDB/Kafka behavior, or Jade-Tipi domain
  model semantics.
- Do not redesign the UI or authentication model beyond compatibility changes
  required by dependency updates.
- Do not replace NextAuth/Auth.js, Next.js App Router, React, Playwright, or
  Tailwind with a different framework/tool unless the director explicitly
  expands scope.

PREWORK_REQUIREMENTS:
- Before implementation, write a concise plan in
  `docs/agents/claude-1-next-step.md` that lists:
  - current frontend dependency versions;
  - target latest stable versions and any prerelease/stability caveats;
  - expected migration risks, especially for Next.js, React, NextAuth/Auth.js,
    Tailwind, and Playwright;
  - exact commands to update dependencies and verify the result.
- Stop after committing and pushing pre-work. Do not implement until the
  director advances this task to `READY_FOR_IMPLEMENTATION`.

DESIGN_NOTES:
- `TASK-023` restored the frontend build baseline and a director hotfix commit
  added Keycloak access-token refresh handling. Start from that working
  baseline and keep the upgrade mechanical where possible.
- The project is early-stage development, so dependency updates may include
  major-version migrations if npm's latest stable releases require them.
  Keep the task bounded by preserving existing behavior and documenting any
  follow-up repairs separately.

VERIFICATION:
- Expected local commands:
  - `cd frontend && npm install`
  - `cd frontend && npm run build`
  - `cd frontend && npm test` or a narrower documented Playwright command if
    browser setup is available
- If npm network access, local Node version, Playwright browser installation,
  or other local tooling blocks verification, report the exact command and
  error plus the setup command needed to continue.

DIRECTOR_PREWORK_REVIEW:
- 2026-05-03: claude-1's pre-work in
  `docs/agents/claude-1-next-step.md` is accepted for implementation.
  Ownership check passed: the pre-work commit changed only
  `docs/agents/claude-1-next-step.md`, which is within claude-1's base
  owned paths.
- Proceed with the pre-work default plan: update `next`, `react`,
  `react-dom`, Playwright, Tailwind, React types, and compatible supporting
  packages to the latest stable versions identified at implementation time;
  keep `next-auth` on the existing Auth.js v5 beta line only because the
  stable npm `latest` tag is the legacy v4 line and moving backward would
  be a regression; keep `@types/node` on the latest Node 20-compatible patch
  to match the project Node 20 baseline.
- For this task, use the newest stable TypeScript 5.x line proposed in
  pre-work rather than taking the TypeScript 6 major in the same turn. If
  TypeScript 6 is desired later or turns out to be required by another
  upgraded package, split it into a follow-up task or stop for director
  review before broadening scope.
- Run the official Next.js upgrade codemod once, review its diff, and keep
  committed edits inside this task's `OWNED_PATHS`. If it proposes edits
  outside the owned paths, stop and report rather than applying them.
- Verification remains `cd frontend && npm install`,
  `cd frontend && npm run build`, and `cd frontend && npm test` or the
  narrowest practical Playwright command. If Playwright browser binaries are
  missing, use/report `npx playwright install chromium`; if npm registry
  access is unavailable, report the exact failing npm command and the setup
  issue rather than treating it as a product blocker.
