# TASK-025 - Plan TypeScript 6 frontend upgrade

ID: TASK-025
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
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
