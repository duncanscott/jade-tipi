# TASK-023 - Fix NextAuth sign-out build error

ID: TASK-023
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
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
