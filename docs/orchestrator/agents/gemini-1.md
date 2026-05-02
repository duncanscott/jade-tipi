# Agent Profile - gemini-1

ID: gemini-1

CAPABILITIES:
  - docs
  - long-context-analysis
  - planning-review
  - second-opinion
  - source-analysis
  - structured-review

BLOCKED_CAPABILITIES:
  - browser-ui
  - docker-stack
  - gradle-verification
  - kafka-integration
  - local-builds
  - long-running-implementation
  - playwright-mcp

EVIDENCE:
  - 2026-05-02: Added as a Gemini CLI developer with headless invocation
    available through `gemini --prompt "" --output-format text`.
  - 2026-05-02: `agent_vs_agent.md` suggests Gemini is most useful for
    large-context source/doc analysis, alternative design perspectives, and
    secondary review rather than primary autonomous implementation.
  - 2026-05-02: Duncan reported that the account is not a pro-level Gemini
    subscription, so usage should be conserved and routed only where a
    different model family is likely to add value.

NOTES:
Use gemini-1 sparingly for concise source reading, design review, schema/model
second opinions, and independent planning checks. Avoid long-running
implementation, Gradle/Docker/Kafka verification, broad repo sweeps, repeated
retries, or large generated reports unless the director explicitly decides the
quota cost is worth it.
