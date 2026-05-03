# TASK-023 - Fix NextAuth sign-out build error

ID: TASK-023
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-022
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - frontend/auth.ts
  - frontend/tests/
  - docs/orchestrator/tasks/TASK-023-fix-nextauth-signout-build-error.md
REQUIRED_CAPABILITIES:
  - browser-ui
  - code-implementation
  - local-builds
GOAL:
Continue restoring the frontend build baseline by fixing the next TypeScript
error in `frontend/auth.ts` reported after the accepted `TASK-022` list-page
repair.

ACCEPTANCE_CRITERIA:
- Inspect the build/type failure at `frontend/auth.ts` around
  `events.signOut` and identify the narrowest type-safe fix for NextAuth's
  sign-out callback message union.
- Preserve existing Keycloak sign-out behavior: when a JWT `token` with
  `idToken` is present, continue calling the Keycloak logout endpoint with the
  same `id_token_hint` and `post_logout_redirect_uri`; when no token variant
  is present, return without attempting logout.
- Do not change the NextAuth provider setup, session/JWT callbacks, admin-role
  derivation, Keycloak realm import, backend code, package dependencies, or
  unrelated frontend routes.
- Run `cd frontend && npm run build` after dependencies are installed. If
  `node_modules` is absent, use the documented setup command
  `cd frontend && npm install` before the build check.
- If the build reveals additional unrelated pre-existing failures, document
  the exact file/error and stop with the smallest next-step recommendation
  instead of widening this task without director approval.

OUT_OF_SCOPE:
- Do not redesign authentication, authorization, session storage, admin group
  management, Keycloak configuration, backend APIs, or the frontend test
  harness.
- Do not edit `frontend/package.json` or `frontend/package-lock.json` unless
  the director explicitly expands scope.

PREWORK_REQUIREMENTS:
- Before implementation, write a concise plan in
  `docs/agents/claude-1-next-step.md` that states the exact NextAuth
  callback union-narrowing problem, the proposed code change, and the
  verification commands.
- Stop after committing and pushing pre-work. Do not implement until the
  director advances this task to `READY_FOR_IMPLEMENTATION`.

DESIGN_NOTES:
- `TASK-022` restored the list-page type narrowing and surfaced this next
  pre-existing build blocker. The developer-reported error was:
  `frontend/auth.ts:80:21 Property 'token' does not exist on type
  '{ session: void | AdapterSession | null | undefined; } | { token: JWT |
  null; }'.`
- The likely minimal fix is to accept the `signOut` callback message as a
  single value, narrow with `'token' in message`, and keep the existing
  logout body unchanged for the token variant.

VERIFICATION:
- Run the narrowest frontend baseline check:
  - `cd frontend && npm run build`
- If local tooling blocks the build, report the exact command/error and the
  documented setup command `cd frontend && npm install` rather than treating
  missing/stale frontend dependencies as a product blocker.

PREWORK_REVIEW:
- 2026-05-03 director review advances this task to
  `READY_FOR_IMPLEMENTATION`. claude-1's latest pre-work commit stayed inside
  the base pre-work ownership boundary: it changed only
  `docs/agents/claude-1-next-step.md`.
- Proceed with the proposed narrow fix in `frontend/auth.ts`: accept the
  `events.signOut` callback message as a single value, narrow with
  `'token' in message`, return without attempting Keycloak logout for the
  `{ session }` variant or a missing `idToken`, and keep the existing
  Keycloak logout URL, `id_token_hint`, `post_logout_redirect_uri`, `fetch`,
  and `try/catch` behavior unchanged for the token variant.
- Verification for this review was source inspection plus
  `git diff --check HEAD..origin/claude-1`. `frontend/node_modules` was
  present in the director worktree. In the implementation turn, run
  `cd frontend && npm run build`. If frontend dependencies are missing or
  stale in the developer worktree, use the documented setup command
  `cd frontend && npm install` before the build.

IMPLEMENTATION_REVIEW:
- 2026-05-03 director implementation review accepts `TASK-023` as the scoped
  NextAuth sign-out build repair. No blocking bugs, behavioral regressions, or
  missing required assertions were found.
- Scope check passed. The implementation changed `frontend/auth.ts`, which is
  in this task's `OWNED_PATHS`, and the base developer report file
  `docs/agents/claude-1-changes.md`. It did not edit provider setup,
  session/JWT callbacks, admin-role derivation, Keycloak realm import,
  backend code, package manifests, lockfiles, frontend tests, or unrelated
  routes.
- The source change matches the accepted task direction: `events.signOut`
  now accepts the message union, narrows with `'token' in message`, returns
  without logout for the `{ session }` variant or a missing `idToken`, and
  preserves the existing Keycloak logout URL, `id_token_hint`,
  `post_logout_redirect_uri`, GET `fetch`, and `try/catch` behavior for the
  JWT token variant.
- Director verification passed with `git diff --check origin/director..HEAD`
  and `cd frontend && npm run build`. The build compiled, linted/type-checked,
  and generated all 9 static pages successfully. The only warning was the
  pre-existing non-blocking Turbopack root warning:
  `turbopack.root should be absolute`.
- No follow-on task is created from this acceptance. The frontend build
  baseline repair chain started by `TASK-021` verification is complete, and
  the next bounded project task is not obvious without human direction.
