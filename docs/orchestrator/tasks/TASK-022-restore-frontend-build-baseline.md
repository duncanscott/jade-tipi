# TASK-022 - Restore frontend build baseline

ID: TASK-022
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_IMPLEMENTATION
OWNER: claude-1
SOURCE_TASK:
  - TASK-021
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
