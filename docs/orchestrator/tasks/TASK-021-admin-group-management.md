# TASK-021 - Add admin group management

ID: TASK-021
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_PREWORK
OWNER: claude-1
SOURCE_TASK:
  - TASK-020
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docker/jade-tipi-realm.json
  - docs/user-authentication.md
  - docs/OVERVIEW.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/resources/
  - frontend/auth.ts
  - frontend/lib/
  - frontend/app/
  - frontend/components/
  - frontend/tests/
  - frontend/types/
  - docs/orchestrator/tasks/TASK-021-admin-group-management.md
REQUIRED_CAPABILITIES:
  - browser-ui
  - code-implementation
  - docker-stack
  - gradle-verification
  - local-builds
GOAL:
Add a local-development admin path for creating and editing first-class
Jade-Tipi `grp` records through standard Keycloak username/password login.

ACCEPTANCE_CRITERIA:
- Update the local `jade-tipi` Keycloak realm import to include:
  - a realm or client role named `jade-tipi-admin`;
  - a normal enabled user `dnscott` with first name `Duncan`, last name
    `Scott`, email `dnscott@jade-tipi.org`, verified email, and the
    `jade-tipi-admin` role;
  - a clearly dev-only local password or documented local reset path. Do not
    introduce ORCID/federated login in this task.
- Spring Boot must expose admin-only group-management endpoints guarded by a
  valid `jade-tipi` realm JWT whose claims include the `jade-tipi-admin` role.
  Do not depend on the Keycloak `master` realm or Keycloak bootstrap `admin`
  account for application authorization.
- Provide at least create, list, read, and update behavior for `grp` records.
  The persisted documents must use the accepted TASK-020 root-shaped `grp`
  contract: `_id`/`id`, `collection: "grp"`, `properties`, `links`, and
  `_head` metadata. Preserve the `permissions` map shape where values are
  exactly `rw` or `r`.
- Keep permission enforcement narrow. Only protect the new admin group
  management endpoints. Do not add general object/property permission
  evaluation, Keycloak group synchronization, object-level overrides, or
  property-value-level overrides.
- Update the Next.js UI to use standard Keycloak login and provide a practical
  group-management screen for an authenticated admin user:
  - sign in/out remains available;
  - users without an access token or without the admin role cannot access the
    group editor workflow;
  - an admin can list groups, create a group, and edit group name,
    description, and permissions map entries.
- Update focused backend and frontend documentation so local developers know
  how to sign in as `dnscott`, which role authorizes the endpoints, and which
  Docker services must be running.
- Add focused backend tests for role extraction/authorization and group CRUD
  behavior. Add focused frontend tests or component/API tests for the admin
  group workflow where the existing frontend test harness supports it.

OUT_OF_SCOPE:
- Do not integrate ORCID or another external identity provider.
- Do not use the Keycloak `master` realm admin user as an application user.
- Do not expose Keycloak admin credentials or client secrets to the browser.
- Do not implement full read/write permission evaluation across existing
  object, property, link, transaction, or Kafka paths.
- Do not implement Keycloak group synchronization, membership import, user
  administration, password reset UI, or production account lifecycle features.
- Do not redesign the transaction WAL, Kafka ingest path, root-document
  materializer, extension-page storage model, CouchDB replication, or contents
  read service.

PREWORK_REQUIREMENTS:
- Before implementation, write a concise plan in
  `docs/agents/claude-1-next-step.md` that answers:
  - Where will `jade-tipi-admin` appear in the JWT, and how will Spring map it
    to an authority?
  - Will the group-management service write root-shaped `grp` documents
    directly to MongoDB for this local admin workflow, or synthesize existing
    transaction/message/materialization paths? State the tradeoff and the
    chosen first-pass approach.
  - What exact REST routes and request/response DTOs will be used?
  - How will the Next.js session expose the access token and admin-role signal
    without leaking secrets?
  - What minimal tests will prove unauthenticated, authenticated-non-admin, and
    admin flows?
- Stop after committing and pushing pre-work. Do not implement until the
  director advances this task to `READY_FOR_IMPLEMENTATION`.

DESIGN_NOTES:
- This task implements the simple local-development path selected by Duncan on
  2026-05-03: create a normal Keycloak user `dnscott` / `Duncan Scott`
  (`dnscott@jade-tipi.org`) and use regular Keycloak authentication machinery.
- Keycloak remains the authentication provider and coarse authorization source.
  Jade-Tipi `grp` records remain domain objects stored in MongoDB.
- The application admin role is intentionally distinct from the Keycloak
  administrative user. The Spring API should authorize from the user's
  application JWT, not by holding or using Keycloak admin privileges.
- This is a development bootstrap/admin workflow. Future tasks can refine group
  membership, property-scope permission evaluation, object overrides, and
  production account management after concrete use cases justify them.

DEPENDENCIES:
- `TASK-020` is accepted and defines the first-pass `grp` root shape and
  permissions map.
- Local integration verification requires the Docker stack services used by
  the app, especially Keycloak and MongoDB.

VERIFICATION:
- Run the narrowest relevant backend checks, expected to include:
  - `./gradlew :jade-tipi:compileGroovy`
  - `./gradlew :jade-tipi:compileTestGroovy`
  - focused unit/integration tests added or changed by this task
- Run the narrowest relevant frontend checks available in `frontend/`, expected
  to include the existing package test/build command or a focused Playwright
  check if the task adds one.
- If local Docker, Keycloak, MongoDB, Gradle, Node, or browser tooling blocks
  verification, report the exact command/error and the documented setup command
  rather than treating local setup as a product blocker.
