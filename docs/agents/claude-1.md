# Developer Assignment - claude-1

This file is owned by the director. It defines the current purpose and
ownership boundary for one developer worktree.

Developer: claude-1
Branch: claude-1
Agent profile: claude
Pre-work file: docs/agents/claude-1-next-step.md
Report file: docs/agents/claude-1-changes.md

## Current Assignment

- Active task: `TASK-004 - Add Kafka transaction ingest integration coverage`.
- Current phase: pre-work. Read `DIRECTIVES.md` and the task file, then
  propose the smallest reliable Docker-backed integration-test strategy for the
  accepted Kafka ingestion path. Do not implement until the director moves the
  task to `READY_FOR_IMPLEMENTATION`.
- Record pre-work in `docs/agents/claude-1-next-step.md`; record implementation
  outcomes in `docs/agents/claude-1-changes.md` only after implementation is
  approved.
- Use `docs/orchestrator/tasks/TASK-004-kafka-transaction-ingest-integration-test.md`
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
