# TASK-022 - Restore frontend build baseline

ID: TASK-022
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
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
