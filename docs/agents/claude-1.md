# Developer Assignment - claude-1

This file is owned by the director. It defines the current purpose and
ownership boundary for one developer worktree.

Developer: claude-1
Branch: claude-1
Agent profile: claude
Pre-work file: docs/agents/claude-1-next-step.md
Report file: docs/agents/claude-1-changes.md

## Current Assignment

- Active task: `TASK-010 - Plan contents location query reads`.
- Current phase: pre-work. Read `DIRECTIVES.md` and the task file, then
  propose the smallest read/query path over the materialized `loc`, `typ`, and
  `lnk` collections for the accepted `contents` link vocabulary. The proposal
  should identify the backend boundary, supported query shapes, response shape,
  edge-case behavior, tests, and verification commands.
  Do not implement until the director moves the task to
  `READY_FOR_IMPLEMENTATION`.
- Record pre-work in `docs/agents/claude-1-next-step.md`; record implementation
  outcomes in `docs/agents/claude-1-changes.md` only after implementation is
  approved.
- Use `docs/orchestrator/tasks/TASK-010-contents-location-query-read-prework.md`
  as the task-specific source of truth for pre-work paths and acceptance
  criteria.

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
