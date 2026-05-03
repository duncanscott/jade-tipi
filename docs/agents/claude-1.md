# Developer Assignment - claude-1

This file is owned by the director. It defines the current purpose and
ownership boundary for one developer worktree.

Developer: claude-1
Branch: claude-1
Agent profile: claude
Pre-work file: docs/agents/claude-1-next-step.md
Report file: docs/agents/claude-1-changes.md

## Current Assignment

- Active task: `TASK-023 - Fix NextAuth sign-out build error`.
- Current phase: pre-work. Read `DIRECTIVES.md` and
  `docs/orchestrator/tasks/TASK-023-fix-nextauth-signout-build-error.md`, then
  propose the smallest type-safe repair for the pre-existing
  `frontend/auth.ts` `events.signOut` callback error reported after
  `TASK-022` verification.
- Do not implement yet. Record the pre-work plan in
  `docs/agents/claude-1-next-step.md`, commit, push, and stop.
- Use `DIRECTIVES.md` as the current source of truth.

## Owned Paths

- docs/agents/claude-1.md
- docs/agents/claude-1-next-step.md
- docs/agents/claude-1-changes.md

## Boundaries

- Stay inside the owned paths unless DIRECTIVES.md, ORCHESTRATOR.md, or an
  active task file explicitly expands scope.
- Record implementation outcomes in docs/agents/claude-1-changes.md.
- Use developer notes for recurring friction or non-blocking questions, not for
  completion status.

## Stop And Ask

Stop with STATUS: BLOCKED or STATUS: HUMAN_REQUIRED when the assignment requires
new credentials, external writes, unclear product decisions, or edits outside
the owned path boundary.
