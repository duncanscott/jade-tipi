# TASK-022 - Restore frontend build baseline

ID: TASK-022
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: ACCEPTED
OWNER: claude-1
SOURCE_TASK:
  - TASK-021
NEXT_TASK:
  - TASK-023
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - frontend/app/list/[id]/page.tsx
  - frontend/tests/
  - docs/orchestrator/tasks/TASK-022-restore-frontend-build-baseline.md
REQUIRED_CAPABILITIES:
  - browser-ui
  - code-implementation
  - local-builds
GOAL:
Restore the frontend build baseline by fixing the pre-existing TypeScript
error in `frontend/app/list/[id]/page.tsx` that blocks `npm run build` after
the admin group-management implementation.

ACCEPTANCE_CRITERIA:
- Inspect the reported build failure in `frontend/app/list/[id]/page.tsx`
  around the `getDocument(documentId, accessToken)` call and identify the
  narrowest type-safe fix.
- Preserve existing document-list/detail behavior, route shape, authentication
  behavior, and API helper signatures unless source inspection proves one must
  change.
- Keep the implementation focused on the build-baseline repair. Do not modify
  the admin group-management implementation, backend code, Keycloak realm
  import, package dependencies, or unrelated frontend routes.
- Run `cd frontend && npm run build` after dependencies are installed. If
  `node_modules` is absent, use the documented setup command
  `cd frontend && npm install` before the build check.
- If the build reveals additional unrelated pre-existing failures, document
  the exact file/error and stop with the smallest next-step recommendation
  instead of widening this task without director approval.

OUT_OF_SCOPE:
- Do not redesign the document detail UI, data model, auth/session handling,
  admin group-management UI, backend API, or test harness.
- Do not add frontend dependencies or edit `frontend/package.json` /
  `frontend/package-lock.json` unless the director explicitly expands scope.

PREWORK_REQUIREMENTS:
- Before implementation, write a concise plan in
  `docs/agents/claude-1-next-step.md` that states the exact TypeScript
  narrowing problem, the proposed code change, and the verification commands.
- Stop after committing and pushing pre-work. Do not implement until the
  director advances this task to `READY_FOR_IMPLEMENTATION`.

DESIGN_NOTES:
- This task follows from `TASK-021` verification. The admin implementation is
  accepted, but frontend build verification cannot be treated as restored
  until the existing list-page TypeScript error is fixed.

PREWORK_REVIEW:
- 2026-05-03 director review advances this task to
  `READY_FOR_IMPLEMENTATION`. claude-1's latest pre-work commit stayed inside
  the base pre-work ownership boundary: it changed only
  `docs/agents/claude-1-next-step.md`.
- Proceed with the proposed narrow source fix in
  `frontend/app/list/[id]/page.tsx`: keep the `documentId` fast-return guard,
  move the `accessToken` guard into the inner `loadDocument` function, and
  leave the `getDocument` API helper signature unchanged.
- The plan's core diagnosis matches source inspection:
  `session?.accessToken` is `string | undefined`, `getDocument` requires a
  `string`, and the current outer guard is not preserved across the nested
  async function boundary at the call site. One rejected alternative in the
  plan is overstated: capturing a post-guard `const token = accessToken`
  would also be type-safe. That does not block implementation because the
  selected in-function guard is smaller and matches the sibling list-load
  effect.
- Verification for this review was static/source inspection only. The director
  worktree currently has no `frontend/node_modules`; the documented setup
  command before implementation build verification is
  `cd frontend && npm install`, followed by `cd frontend && npm run build`.
  If that build reveals unrelated pre-existing failures, report the exact
  file/error and stop rather than widening this task without director approval.

IMPLEMENTATION_REVIEW:
- 2026-05-03 director implementation review accepts `TASK-022` as the scoped
  list-page build repair. The implementation stayed within the active
  ownership boundary: `frontend/app/list/[id]/page.tsx` plus the base
  developer report file `docs/agents/claude-1-changes.md`.
- The source change matches the approved narrow fix: the `documentId`
  fast-return guard remains outside the effect's inner async function, the
  `accessToken` guard moved inside `loadDocument`, the
  `[documentId, accessToken]` dependency array is unchanged, and the
  `getDocument` helper signature remains `getDocument(id, accessToken)`.
  This preserves the prior unauthenticated no-op behavior while giving
  TypeScript a local `string` narrowing at the API call site.
- Director static verification passed with `git diff --check HEAD~1..HEAD`.
  The director worktree initially lacked frontend dependencies, so the
  documented setup command `cd frontend && npm install` was run before
  verification. `cd frontend && npm run build` was then blocked by the local
  sandbox/Turbopack environment before type checking, with
  `Operation not permitted (os error 1)` while creating a process and binding
  to a port. Treat that as a local verification environment limit, not a
  product blocker.
- A targeted no-emit TypeScript check in `frontend/` confirmed the reported
  next blocker and did not report the prior list-page error:
  `auth.ts(80,21): error TS2339: Property 'token' does not exist on type
  '{ session: void | AdapterSession | null | undefined; } | { token: JWT |
  null; }'.` This matches claude-1's developer report that full build
  verification now stops at `frontend/auth.ts`, outside `TASK-022`'s
  implementation source path.
- Created `TASK-023` as the next bounded pre-work task to address the
  `frontend/auth.ts` NextAuth `events.signOut` union narrowing issue without
  widening this accepted task.
