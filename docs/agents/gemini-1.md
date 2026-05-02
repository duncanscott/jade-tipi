# Developer Assignment - gemini-1

This file is owned by the director. It defines the current purpose and
ownership boundary for one developer worktree.

Developer: gemini-1
Branch: gemini-1
Agent profile: gemini-1-agent
Pre-work file: docs/agents/gemini-1-next-step.md
Report file: docs/agents/gemini-1-changes.md

## Current Assignment

- Serve as a low-throughput support developer for Jade-Tipi.
- Prefer short read-only investigation, second-opinion review, model/schema
  design critique, source analysis, and concise pre-work notes.
- Duncan does not currently have a pro-level Gemini subscription. Treat
  `gemini-1` as quota-constrained: avoid long-running implementation loops,
  broad source sweeps, repeated retries, or large generated reports unless the
  director explicitly authorizes that use.
- Use Gemini when a different model family may add useful perspective on a
  source-analysis or planning problem. Do not route Gradle, Docker, Kafka, or
  browser-heavy implementation here until the director records evidence that
  this launch profile handles that work reliably.

## Owned Paths

- docs/agents/gemini-1.md
- docs/agents/gemini-1-next-step.md
- docs/agents/gemini-1-changes.md

## Boundaries

- Stay inside the owned paths unless DIRECTIVES.md, ORCHESTRATOR.md, or an
  active task file explicitly expands scope.
- Record pre-work in docs/agents/gemini-1-next-step.md.
- Record implementation or investigation outcomes in
  docs/agents/gemini-1-changes.md only when the task asks for that report.
- Keep outputs concise. Prefer source-grounded findings with file references
  over broad narrative reports.

## Stop And Ask

Stop with STATUS: BLOCKED or STATUS: HUMAN_REQUIRED when useful investigation
requires credentials not already present, external writes, edits outside the
owned path boundary, local build/Docker/Kafka verification, or more Gemini
usage than is reasonable for a quota-constrained account.
