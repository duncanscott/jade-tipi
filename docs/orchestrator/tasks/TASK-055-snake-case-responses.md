# TASK-055 - snake_case JSON responses

ID: TASK-055
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-054
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-055-snake-case-responses.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - jade-tipi/src/main/resources/application.yml
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - frontend/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - frontend-nextjs
  - gradle-verification

GOAL:
Director ruling 2026-07-05: HTTP JSON response bodies switch from
camelCase to snake_case, so an object fetched through the API carries the
same field conventions as the wire messages and the stored root documents
— the document-state principle applied to the read surface. Now is the
cheapest moment: the only JSON consumers are the frontend client and the
HTTP integration assertions, which move in the same change.

DESIGN:
- One global Jackson property naming strategy
  (`spring.jackson.property-naming-strategy: SNAKE_CASE`) rather than
  per-record annotations, so every current and future endpoint complies
  automatically and cannot drift. Groovy property access inside the
  backend is unaffected (the strategy applies at serialization).
- Map payloads are untouched by the strategy: raw document reads
  (`/api/documents`), inline `properties` bags, `property_values`
  sub-documents, and user-supplied keys pass through verbatim — they were
  already snake_case or user-owned.
- Single-word fields (`items`, `page`, `size`, `total`, `name`, `value`,
  `wells`, ...) are identical under both conventions; the change lands on
  the multi-word fields (`objectId` → `object_id`, `propertyValues` →
  `property_values`, `chainComplete` → `chain_complete`, ...).
- The legacy `/api/transactions` open/commit token responses also become
  snake_case; they have no known JSON consumers (Kafka is the write
  path), which is recorded rather than special-cased.

ACCEPTANCE_CRITERIA:
- The global naming strategy is set; no per-record Jackson annotations.
- All HTTP integration assertions move to snake_case jsonPaths and the
  full suite passes (proving the strategy end to end against real
  responses).
- The frontend client interfaces and pages move to snake_case fields;
  `npm run build` and Playwright stay green.
- The vocabulary doc's read sections and route-convention note, and the
  specification's §3 contract wording, use the snake_case field names
  (version bump).

OUT_OF_SCOPE:
- No change to wire messages or stored documents (already snake_case).
- No renaming of Groovy record classes or properties — code identifiers
  keep Groovy conventions; only JSON serialization changes.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `cd frontend && npm run build && npx playwright test`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- Config: `spring.jackson.property-naming-strategy: SNAKE_CASE` in the
  base application.yml — one global setting, no per-record annotations.
- **Latent bug found and fixed**: the property had no effect at first —
  responses stayed camelCase even though a probe spec proved the
  context's auto-configured ObjectMapper honored the strategy. Root
  cause: `config/WebConfig.groovy` carried `@EnableWebFlux`, which
  disables Spring Boot's `WebFluxAutoConfiguration` — the wiring that
  installs the Boot-customized ObjectMapper into the HTTP codecs. The
  server had been serializing with a plain default mapper all along,
  silently ignoring every `spring.jackson.*` and Boot WebFlux property.
  The class did nothing else (its CORS duty had already moved to
  SecurityConfig), so it is deleted; `JacksonSnakeCaseProbeSpec` stays
  as the mapper-level regression pin and the four HTTP specs pin the
  codec level.
- HTTP integration assertions moved to snake_case via a scoped transform
  (dot-prefixed field tokens on jsonPath lines only, so same-line Groovy
  variables were untouched) plus the one `valuePath` literal; the full
  suite proves the strategy against real responses (all four read specs
  plus browse, with real Keycloak tokens).
- Frontend: `lib/containers.ts` interfaces and both container pages
  moved to snake_case fields (`object_id`, `property_values`,
  `chain_complete`, `row_labels`, ...); single-word fields were already
  convention-neutral.
- Docs: vocabulary doc's read sections and route-convention note now
  state snake_case everywhere (wire, documents, and responses alike);
  spec 0.4.3-draft records the ruling. The legacy `/api/transactions`
  token responses also became snake_case; they have no known JSON
  consumers (recorded, not special-cased).
- Verification results: `:jade-tipi:test` green (incl. the probe); full
  `JADETIPI_IT_KAFKA=1 :jade-tipi:integrationTest` BUILD SUCCESSFUL
  (1m56s); frontend `npm run build` green, Playwright 15/15;
  `git diff --check` clean.
