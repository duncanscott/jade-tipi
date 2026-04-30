# Agent Profile - codex-1

ID: codex-1

CAPABILITIES:
  - code-review
  - docs
  - orchestrator-direction
  - schema-review
  - source-analysis
  - task-design
  - typescript-refactor

BLOCKED_CAPABILITIES:
  - browser-ui
  - docker-stack
  - gradle-verification
  - playwright-mcp
  - sandboxed-gradle-verification

EVIDENCE:
  - Bootstrap default for docs, source-analysis, and typescript-refactor.
  - Codex has been effective as director for source review, task design,
    schema/design discussion, and orchestrator state management.
  - Direct Gradle verification from the Codex sandbox failed with Gradle
    file-lock/socket restrictions (`Operation not permitted` /
    `FileLockContentionHandler`), so Gradle and Docker-backed verification
    should route to a less sandboxed developer unless explicitly approved.

NOTES:
Keep this file director-owned. Add or remove capabilities only when a review
cycle gives concrete evidence. Use BLOCKED_CAPABILITIES for launch-mode limits
or repeated failures that should prevent automatic routing until a human or
director changes the profile.
