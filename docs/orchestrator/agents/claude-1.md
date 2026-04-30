# Agent Profile - claude-1

ID: claude-1

CAPABILITIES:
  - browser-ui
  - code-implementation
  - docker-stack
  - gradle-verification
  - kafka-integration
  - local-builds
  - long-running-implementation

BLOCKED_CAPABILITIES:


EVIDENCE:
  - Bootstrap default for browser-ui, code-implementation, and
    long-running-implementation.
  - TASK-002 implementation reported successful local verification with
    `./gradlew :libraries:jade-tipi-dto:test` and
    `./gradlew :clients:kafka-kli:build`, while Codex director verification
    was blocked by sandbox Gradle file-lock socket restrictions.
  - Claude launch mode is better suited for normal local build execution,
    Docker-backed checks, and Kafka integration smoke tests.

NOTES:
Keep this file director-owned. Add or remove capabilities only when a review
cycle gives concrete evidence. Use BLOCKED_CAPABILITIES for launch-mode limits
or repeated failures that should prevent automatic routing until a human or
director changes the profile.
