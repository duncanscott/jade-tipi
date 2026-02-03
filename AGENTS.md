# Repository Guidelines

## Project Structure & Module Organization
- `jade-tipi/` holds the Spring Boot WebFlux service (Groovy); controllers and configs live under `src/main/groovy/org/jadetipi/jadetipi`.
- `jade-tipi/src/test/groovy` and `jade-tipi/src/integrationTest/groovy` store unit and integration suites alongside their resource folders.
- `frontend/` contains the Next.js 15 app: feature routes in `app/`, shared UI in `components/`, utilities in `lib/`.
- `clients/` carries Gradle CLI prototypes (`clients/jade`, `clients/tipi`); keep them compiling when touching shared code.
- Version catalogs reside in `libraries/jade-tipi-id`; adjust them before bumping dependencies.

## Build, Test, and Development Commands
- `docker compose -f docker/docker-compose.yml --profile mongodb up -d` starts MongoDB locally; pair with `docker compose -f docker/docker-compose.yml down` when finished.
- `./gradlew bootRun` launches the Groovy backend on port 8765 and auto-generates `frontend/.env.local`.
- `./gradlew test` runs the JUnit 5 suite; `./gradlew integrationTest` exercises the WebFlux HTTP flows (requires Mongo running).
- `cd frontend && npm install && npm run dev` starts the React app on port 3000; use `npm run build` before packaging.

## Coding Style & Naming Conventions
- Use 4-space indentation for Groovy/Java and TypeScript files; keep imports grouped logically and avoid wildcard imports.
- Preserve the dual-license header block at the top of all Groovy sources and copy it onto new files.
- Packages follow `org.jadetipi.<domain>`; React components live in `PascalCase` files, hooks and helpers in `camelCase`.
- Keep JSON fixtures and static assets under `frontend/public` or `jade-tipi/src/main/resources/tipi` to match current access paths.

## Testing Guidelines
- Default to JUnit 5 with WebTestClient for HTTP assertions; use Spock specs (`*Spec.groovy`) when behavior-driven structure helps.
- Mirror existing naming: `*Tests.groovy` for unit coverage, `*IntegrationTest.groovy` for profile-backed flows.
- Integration tests assume the `test` profile and a live Mongo instance; document extra setup directly in the class.
- Prefer helper utilities over ad-hoc `Thread.sleep` so retries are explicit.

## Commit & Pull Request Guidelines
- Follow the repository pattern of short, imperative commit subjects (e.g., `remove express from docker-compose`); keep the summary under ~72 characters.
- In PRs, include: a concise problem statement, the approach, verification steps (`./gradlew test`, `npm run build`), and UI screenshots when frontend changes affect visuals.
- Link related issues via closing keywords and note any config changes impacting `docker/docker-compose.yml` or `.env` generation.

## Configuration Tips
- Local development expects Java 21 and Node 20; verify via `java -version` and `node --version` before running builds.
- Never hand-edit `frontend/.env.local`; regenerate with `./gradlew generateFrontendEnv` if the backend port changes.
