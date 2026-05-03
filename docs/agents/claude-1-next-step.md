# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-021 — Add admin group management (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task
file `docs/orchestrator/tasks/TASK-021-admin-group-management.md` is
`READY_FOR_PREWORK` with `OWNER: claude-1`. The director recorded the
new product direction from Duncan on 2026-05-03: do not integrate
ORCID for this step; create a normal `jade-tipi` realm Keycloak user
`dnscott` / `Duncan Scott` (`dnscott@jade-tipi.org`), grant a Jade-Tipi
application admin role, and add JWT-protected group-management
endpoints plus a minimal Next.js admin UI that can create and edit
`grp` records. Authorization must come from the user's application JWT
and not from the Keycloak `master` realm admin user. `TASK-020`
remains accepted; this task narrowly authorizes admin endpoint
authorization and intentionally skips broader permission evaluation,
membership sync, and password-management features.

This pre-work turn produces a plan only. No realm import, security
config, controller, service, schema, fixture, frontend, or test edit
is made on this turn — no `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION` signal is present yet.

### Survey of current state (sources only, no proposed edits)

The following observations are from a focused source survey. They
ground the answers in the next section.

- Keycloak realm import `docker/jade-tipi-realm.json` defines the
  `jade-tipi` realm with two clients: a confidential backend client
  `jade-tipi` (direct-access grants enabled, audience and tipi-org/
  tipi-group hardcoded mappers) and a public SPA client
  `jade-tipi-frontend` (PKCE, redirect `http://localhost:3000/*`).
  The realm currently has no realm or client roles defined and one
  user `testuser` / `test@jade-tipi.org` with password `testpassword`
  and no role mapping.
- Spring backend reads
  `spring.security.oauth2.resourceserver.jwt.issuer-uri =
  http://localhost:8484/realms/jade-tipi` from
  `jade-tipi/src/main/resources/application.yml` and configures a
  default-converter resource server in
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/SecurityConfig.groovy`
  via `.oauth2ResourceServer { oauth2 -> oauth2.jwt { } }`. There is
  no custom `JwtAuthenticationConverter` and no parsing of
  `realm_access.roles` or `resource_access.<client>.roles` today.
- The accepted `TASK-020` materializer in
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  supports `grp + create` only and writes a root-shaped doc with
  `_id == data.id`, `id`, `collection: "grp"`, optional `type_id`,
  `properties` (which carries `name`, `description`, and a
  grp-id-keyed `permissions` map of `"rw"|"r"`), `links: {}`, and
  `_head.provenance` with `txn_id`, `commit_id`, `msg_uuid`,
  `collection`, `action`, `committed_at`, `materialized_at`. There is
  no `grp + update` handling in the materializer today, no
  `GroupController`, no `GroupAdminService`, and no `GrpRepository`.
- Existing controllers under
  `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/`
  (`TransactionController`, `CommittedTransactionReadController`,
  `ContentsLinkReadController`, `DocumentController`) inject
  `@AuthenticationPrincipal Jwt jwt` and use `@RequestMapping('/api/...')`.
- The Next.js Auth.js setup in `frontend/auth.ts` already uses the
  Keycloak provider with `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`,
  `KEYCLOAK_CLIENT_SECRET`, persists `access_token` and `id_token`
  through the `jwt` callback into `token`, and exposes
  `session.accessToken` / `session.idToken` to client components.
  `frontend/lib/api.ts` attaches `Authorization: Bearer <token>` to
  fetch calls via `ensureAccessToken(accessToken)`. The header
  navigation in `frontend/components/layout/Header.tsx` lists Home,
  Documents, Create — no Groups link yet. Playwright tests live in
  `frontend/tests/frontend.spec.ts`.
- Backend test pattern: `WebTestClient.bindToController(...)` with a
  custom `AuthenticationPrincipalArgumentResolver` for unit specs (see
  `CommittedTransactionReadControllerSpec`); a real-Keycloak
  `client_credentials` token helper for integration tests
  (`KeycloakTestHelper`); and a mock `ReactiveJwtDecoder` returning
  a synthetic `Jwt` for slice tests (`TestSecurityConfig`).

### Concrete answers to the PREWORK_REQUIREMENTS questions

**Q1 — Where will `jade-tipi-admin` appear in the JWT, and how will
Spring map it to an authority?**

Recommended placement: a **realm role** named `jade-tipi-admin`,
assigned to the `dnscott` user via realm role mapping. Realm roles
ride on the access token as `realm_access.roles: [..., "jade-tipi-admin"]`
without any per-client wiring, which is the smallest viable change for
a local development admin path. A client role on the `jade-tipi`
backend client would require parsing `resource_access["jade-tipi"].roles`
and a separate scope/audience setup; that complexity is unnecessary
for a single dev admin user.

Spring mapping: replace the default authorities converter with a
narrow custom `Converter<Jwt, Collection<GrantedAuthority>>` that
reads `realm_access.roles` (a `List<String>`), prefixes each with
`ROLE_`, and returns a `List<SimpleGrantedAuthority>`. Wrap it in a
`ReactiveJwtAuthenticationConverterAdapter` and set it on the resource
server via `.oauth2ResourceServer { oauth2 -> oauth2.jwt {
jwt.jwtAuthenticationConverter(...) } }`. Existing scope-based
authorities mapped from the `scope`/`scp` claim are not needed for
this task, so the converter only emits realm-role authorities.

The new admin path becomes:

```groovy
.pathMatchers('/api/admin/**').hasRole('jade-tipi-admin')
```

while everything else keeps its current `permitAll` /
`authenticated()` rules. Other controllers continue to receive
`@AuthenticationPrincipal Jwt jwt` unchanged because the converter
returns a `JwtAuthenticationToken` whose principal is still the `Jwt`.

**Q2 — Direct MongoDB write or synthesized Kafka transaction?**

Two paths:

- **Path A — Direct MongoDB write from a new `GroupAdminService`
  (RECOMMENDED first pass).** The service writes root-shaped `grp`
  documents directly via `ReactiveMongoOperations` (or
  `ReactiveMongoTemplate`), reusing the same field layout as the
  materializer (`_id`, `id`, `collection: "grp"`, `properties`,
  `links: {}`, `_head.provenance`). For provenance on admin writes,
  generate a sentinel `txn_id == "admin~<uuidv7>"` and matching
  `commit_id == txn_id`, a fresh `msg_uuid` per write, action
  `"create"` or `"update"`, and `committed_at == materialized_at`
  set to write time. This keeps the persisted documents schema-
  compatible with the `TASK-020` root-shape contract while clearly
  marking admin-origin records.
- **Path B — Synthesize a `txn open + grp + create/update + commit`
  Kafka transaction and let `CommittedTransactionMaterializer` do
  the write.** Preserves the single-writer invariant for root
  documents but requires (1) the materializer to learn `grp + update`,
  which `TASK-020` deliberately did not cover, (2) a Kafka producer
  in the admin controller path, and (3) the admin endpoint to wait
  for materialization before responding. That re-opens
  materializer scope and introduces synchronous coupling between an
  admin click and Kafka.

Tradeoff: Path A loses the "one writer creates root docs" invariant
for the narrow admin-grp surface only, but it stays inside what
`TASK-021` actually authorizes (admin endpoints, narrow authorization
scope) and avoids re-opening the `TASK-020`-frozen materializer for
`grp + update`. Path B is principled but pushes the change footprint
well beyond this task's stated scope.

Chosen first-pass approach: **Path A.** The persisted documents stay
shape-compatible with the materializer's `grp + create` output, the
`_head.provenance.txn_id` sentinel form makes admin writes
identifiable for a future task, and update semantics live in one new
service rather than spreading into the materializer.

**Q3 — Exact REST routes and request/response DTOs.**

All routes guarded by `hasRole('jade-tipi-admin')`. Request and
response bodies are JSON. Numbers in parentheses are HTTP status
codes for the success path; common error responses are `401`
(unauthenticated), `403` (authenticated but missing the admin role),
`400` (validation), and `404` (read/update of a missing id).

| Method | Path                              | Request body                                                                              | Success response (status, body)            |
| ------ | --------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------ |
| POST   | `/api/admin/groups`               | `GroupCreateRequest { id?, name, description?, permissions: { [grpId: string]: "rw"|"r" } }` | `201` + `GroupRecord`                      |
| GET    | `/api/admin/groups`               | (none)                                                                                    | `200` + `{ items: GroupRecord[] }`         |
| GET    | `/api/admin/groups/{id}`          | (none)                                                                                    | `200` + `GroupRecord`                      |
| PUT    | `/api/admin/groups/{id}`          | `GroupUpdateRequest { name, description, permissions: { [grpId: string]: "rw"|"r" } }`    | `200` + `GroupRecord`                      |

DTOs (all in `org.jadetipi.jadetipi.dto`, kept narrow and
admin-endpoint-specific so they do not collide with existing wire
DTOs):

```groovy
record GroupCreateRequest(String id, String name, String description,
                          Map<String, String> permissions) {}
record GroupUpdateRequest(String name, String description,
                          Map<String, String> permissions) {}
record GroupRecord(String id, String collection, String name,
                   String description, Map<String, String> permissions,
                   GroupHead head) {}
record GroupHead(GroupProvenance provenance) {}
record GroupProvenance(String txnId, String commitId, String msgUuid,
                       String collection, String action,
                       String committedAt, String materializedAt) {}
```

Validation rules enforced server-side in `GroupAdminService`:

- `name` is non-blank, ≤ 255 chars.
- `description` is optional, ≤ 4096 chars.
- `permissions` map values are exactly `"rw"` or `"r"`. Any other
  value yields `400`.
- `permissions` keys must be non-blank. (We do not in this task
  validate that the keys reference existing `grp` records — that
  belongs in a later permission-evaluation task.)
- For `POST` without an `id`, the service synthesizes a world-unique
  grp id of the form
  `jade-tipi-org~dev~<uuidv7>~grp~<slug-of-name>` matching the
  shape used by canonical examples; for `POST` with an `id`, the
  service rejects malformed ids and rejects duplicates with `409`.
- `PUT` semantics: the body fully replaces the editable fields
  (`name`, `description`, `permissions`); `_id`, `id`, `collection`
  are immutable; `_head.provenance` is rewritten with action
  `"update"` and a fresh `msg_uuid` while preserving an audit field
  `_head.provenance.txn_id` per the chosen sentinel scheme.

`GroupRecord` projects the stored Mongo document. Mongo `_id` is not
exposed (only `id`); `permissions` is the same map persisted under
`properties.permissions`; the `head.provenance` block is the same map
persisted under `_head.provenance` with field names mapped from
snake_case storage to camelCase response.

**Q4 — How will the Next.js session expose the access token and admin
signal without leaking secrets?**

Current state already exposes `session.accessToken` to the client via
`auth.ts`; the bearer-token attachment in `frontend/lib/api.ts`
depends on that. We keep that path, scoped to dev-only behavior, and
add an admin-role boolean signal that does **not** expose raw realm
claims.

Plan:

- In `frontend/auth.ts`, decode the access token once inside the
  `jwt({ token, account })` callback when `account` is present
  (initial sign-in). Use `jose`'s `decodeJwt` (no signature
  verification on the frontend; the Spring backend still re-validates
  every call). Read `realm_access.roles`, compute
  `isAdmin = roles.includes('jade-tipi-admin')`, and store the
  boolean on `token.isAdmin`. Forward to the session as
  `session.isAdmin`.
- Do **not** copy the raw `realm_access` object or the decoded
  payload onto the session. Only the boolean flag and the existing
  `accessToken` / `idToken` strings cross to the client.
- Update `frontend/types/next-auth.d.ts` (creating it if it does not
  exist) to type `Session.isAdmin: boolean` and confirm
  `Session.accessToken: string | undefined` is already typed.
- Server-side guard: a new `frontend/app/admin/groups/page.tsx`
  layout calls `await auth()` and redirects unauthenticated users to
  the home page; the page also returns a 403-style empty state when
  `session.isAdmin` is false.
- Client-side guard: `frontend/components/layout/Header.tsx`
  conditionally renders the "Groups" nav link only when
  `session.isAdmin` is true. This is convenience UX, not a security
  boundary — the backend remains the authoritative gate.
- Secret hygiene: `KEYCLOAK_CLIENT_SECRET` continues to live in the
  server-only environment and is never copied onto session or token
  fields. The realm import keeps the dev-only password documented as
  dev-only.

Bearer attachment: a new `frontend/lib/admin-groups.ts` (or extension
of `frontend/lib/api.ts`) reuses the existing `ensureAccessToken()`
helper for all four admin endpoints (`POST`, `GET list`, `GET one`,
`PUT`) so the bearer-token flow is one helper, not four.

**Q5 — What minimal tests will prove unauthenticated, authenticated-
non-admin, and admin flows?**

Backend unit (Spock under `jade-tipi/src/test/groovy/...`):

1. `RealmAccessRolesAuthoritiesConverterSpec` — converter pulls
   `realm_access.roles`, prefixes with `ROLE_`, ignores empty/missing
   `realm_access`, and returns an empty authority list when the
   claim is absent or wrong-typed.
2. `GroupAdminControllerSpec` (using
   `WebTestClient.bindToController(...)` with the project's
   `AuthenticationPrincipalArgumentResolver` pattern, mocking
   `GroupAdminService`):
   - `GET /api/admin/groups` returns `200` + items when called with
     a `JwtAuthenticationToken` carrying `ROLE_jade-tipi-admin`.
   - `POST` validates request shape (rejects non-`rw`/`r` permissions
     values, rejects blank `name`).
   - `PUT` returns `404` when the service reports a missing id.
3. `GroupAdminServiceSpec` (mocking `ReactiveMongoOperations`):
   - `create` writes a root-shaped doc with `_id == id`,
     `collection == "grp"`, `properties.{name,description,
     permissions}` populated, `links == [:]`,
     `_head.provenance.{collection: "grp", action: "create"}`, and a
     sentinel `txn_id` of the form `admin~<uuid>`.
   - `update` rewrites `properties.{name,description,permissions}`
     and refreshes `_head.provenance.action == "update"` while
     keeping `_id` and `id` unchanged.
   - `update` of a missing id returns `Mono.empty()` and writes
     nothing.
   - `permissions` validation rejects values other than `rw`/`r`.

Backend integration (Spock under
`jade-tipi/src/integrationTest/groovy/...`, gated like the existing
opt-in pattern, e.g. `JADETIPI_IT_ADMIN_GROUPS=1` to keep CI hermetic):

4. `GroupAdminAuthIntegrationSpec`:
   - Acquires a real JWT via Keycloak password grant for `dnscott`
     using `directAccessGrantsEnabled` on the backend client. Uses
     `KeycloakTestHelper`-style flow.
   - Asserts `POST /api/admin/groups` returns `201`, the persisted
     Mongo doc is root-shaped, and round-trip
     `GET /api/admin/groups/{id}` returns the same record.
   - Asserts `GET /api/admin/groups` without an `Authorization`
     header returns `401`.
   - Asserts `GET /api/admin/groups` with a `testuser` token (no
     admin role) returns `403`.
   - Cleans up exactly the records it created.

Frontend (Playwright under `frontend/tests/`):

5. `frontend.spec.ts` extension or a new `admin-groups.spec.ts`:
   - Signed-out user does not see the "Groups" nav link.
   - Authenticated non-admin user does not see the link and is
     redirected when navigating directly to `/admin/groups`.
   - Authenticated admin user (driven via a session-mocking helper
     that injects `session.isAdmin = true` and a fake
     `accessToken`) sees the nav link and reaches the page.

Frontend unit/component tests where the existing harness supports
them (the project currently uses Playwright; if `vitest` /
`@testing-library/react` is not already wired, we will skip
component-level tests and rely on the Playwright path rather than
introducing a new harness in this task).

### Proposed file changes (to be made only after
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`)

All paths below are inside `TASK-021`'s `OWNED_PATHS`.

1. **Realm import** — `docker/jade-tipi-realm.json`:
   - Add a realm role `jade-tipi-admin` (one entry under a new
     `roles.realm` array).
   - Add a normal enabled user `dnscott` with first name `Duncan`,
     last name `Scott`, email `dnscott@jade-tipi.org`,
     `emailVerified: true`, a single dev-only credential
     (`type: "password"`, `temporary: false`, value documented as
     dev-only), and `realmRoles: ["jade-tipi-admin"]`. Leave
     `testuser` unchanged so a non-admin fixture remains available
     for the 403 test.

2. **Spring security** —
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/config/`:
   - New `RealmAccessRolesAuthoritiesConverter.groovy` implementing
     the JWT → authorities mapping described above.
   - Edit `SecurityConfig.groovy` to wire the converter onto the
     resource server and add `pathMatchers('/api/admin/**')
     .hasRole('jade-tipi-admin')` ahead of the existing
     `authenticated()` rule. Keep all other `permitAll` / auth
     rules unchanged.

3. **Admin controller and service** —
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/GroupAdminController.groovy`
   and `.../service/GroupAdminService.groovy`:
   - Implement the four routes and DTOs from Q3.
   - Service writes root-shaped `grp` documents directly to MongoDB
     using `ReactiveMongoOperations` for create/update; `find` for
     list/read.

4. **DTOs** —
   `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/dto/GroupRecord.groovy`,
   `GroupCreateRequest.groovy`, `GroupUpdateRequest.groovy`. These
   are admin-endpoint shapes only and do not replace existing wire
   DTOs.

5. **Backend tests** — under
   `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/`:
   - `config/RealmAccessRolesAuthoritiesConverterSpec.groovy`
   - `controller/GroupAdminControllerSpec.groovy`
   - `service/GroupAdminServiceSpec.groovy`
   - Optional opt-in
     `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/admin/GroupAdminAuthIntegrationSpec.groovy`
     gated on `JADETIPI_IT_ADMIN_GROUPS`.

6. **Frontend**:
   - `frontend/auth.ts` — decode the access token once on initial
     sign-in, set `token.isAdmin`, expose `session.isAdmin`.
   - `frontend/types/next-auth.d.ts` — extend `Session` (and
     possibly `JWT`) types with `isAdmin: boolean`.
   - `frontend/components/layout/Header.tsx` — render a "Groups"
     nav link conditional on `session.isAdmin`.
   - `frontend/app/admin/groups/page.tsx`,
     `frontend/app/admin/groups/[id]/page.tsx`,
     `frontend/app/admin/groups/new/page.tsx` — minimal admin UI
     with list, create, and edit forms. Use existing form
     primitives where they exist; otherwise plain HTML inputs
     consistent with `frontend/app/document/create/page.tsx`.
   - `frontend/lib/admin-groups.ts` — typed fetch wrappers for the
     four endpoints, reusing `ensureAccessToken`.
   - `frontend/tests/admin-groups.spec.ts` — Playwright coverage of
     the three role flows.

7. **Documentation** — `docs/user-authentication.md` and
   `docs/OVERVIEW.md`:
   - Document the dev-only `dnscott` user and its dev password.
   - Document the `jade-tipi-admin` realm role and that the
     application authorizes from the user's JWT (not the Keycloak
     `master` admin).
   - List the Docker services that must be running for local sign-in
     and for the integration spec (Keycloak, MongoDB).
   - State explicitly that ORCID/federated login is not part of
     this task and that this is a development bootstrap admin
     workflow.

8. **Task and developer report files** (administrative only):
   - `docs/orchestrator/tasks/TASK-021-admin-group-management.md` —
     listed in `OWNED_PATHS` but director-owned in practice; will
     not be edited unless the director requests it.
   - `docs/agents/claude-1-changes.md` — updated only after
     implementation is approved, recording what was actually done.

### Files intentionally not touched

- The accepted `CommittedTransactionMaterializer.groovy` — admin
  writes deliberately bypass it (Path A) so that `grp + update`
  semantics do not re-open `TASK-020`.
- Anything under `clients/`, `libraries/`, the existing
  `DocumentController`/`TransactionController`/contents read paths,
  Kafka topic configuration, OAuth / SASL hardening, or production
  account-lifecycle code. Out of scope by the task file.
- General object/property/link permission evaluation, Keycloak group
  synchronization, object-level overrides, property-value-level
  overrides, password reset UI — explicitly excluded.

### Verification plan

For this pre-work turn:

- Static review only. No Gradle, Docker, MongoDB, Keycloak, or Kafka
  commands executed.
- Branch state at start of turn: clean per the orchestrator pre-amble
  `git status` snapshot.

For the implementation turn (only after the director advances the
task to `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION`):

```sh
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*GroupAdmin*' --tests '*RealmAccessRoles*'
JADETIPI_IT_ADMIN_GROUPS=1 ./gradlew :jade-tipi:integrationTest --tests '*GroupAdminAuthIntegrationSpec*'
cd frontend && npm install && npm run build
cd frontend && npx playwright test admin-groups.spec.ts
```

If local Docker, Keycloak, Mongo, Gradle wrapper-cache locks under
`/Users/duncanscott/.gradle`, Node, or browser tooling block
verification, the documented setup commands are:

```sh
docker compose -f docker/docker-compose.yml up -d
./gradlew --stop
./gradlew generateFrontendEnv
cd frontend && npx playwright install --with-deps
```

These are setup steps, not product blockers. Per the orchestrator
preamble, blocked verification is reported with the documented setup
command rather than treated as a feature failure.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths are modified. No realm import, security config,
controller, service, DTO, schema, fixture, frontend, or test changes
are made or proposed for this turn.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect
with one signal change.

- **Q-21-A — Realm role vs client role for `jade-tipi-admin`.**
  Default: realm role (`realm_access.roles`). Backup: client role on
  the `jade-tipi` backend client (`resource_access["jade-tipi"].roles`),
  which would require an additional audience/scope mapper and a
  matching converter branch. Realm role is the smaller change and
  preferred unless the director wants per-client scoping for a
  future production migration.
- **Q-21-B — Direct MongoDB write vs synthesized Kafka transaction.**
  Default: Path A (direct write) with a sentinel `_head.provenance.txn_id
  == "admin~<uuid>"`. Backup: Path B requires extending the
  materializer to support `grp + update` and adding a Kafka producer
  to the admin path, which goes beyond this task's stated scope.
- **Q-21-C — `dnscott` dev password.** Default: commit a clearly
  dev-only static password into `docker/jade-tipi-realm.json` (e.g.,
  `dnscott`), with `temporary: false` so login works without a
  password-change step, and document the value in
  `docs/user-authentication.md` as dev-only. Backup: set
  `temporary: true` and document the local reset path. Please
  confirm whether the dev password should be the literal string
  `dnscott` or another value.
- **Q-21-D — Provenance shape for admin direct-writes.** Default:
  set `_head.provenance.txn_id == _head.provenance.commit_id ==
  "admin~<uuidv7>"`, fresh `msg_uuid` per write, action `"create"`
  or `"update"`, `committed_at == materialized_at` set to write time,
  `collection: "grp"`. Backup: `txn_id == null` and `commit_id ==
  null`. Default preserves the field shape while marking origin.
- **Q-21-E — `testuser` role mapping.** Default: leave `testuser`
  with no roles so the integration spec has a built-in non-admin
  fixture for the 403 test.
- **Q-21-F — `PUT` semantics.** Default: full replacement of the
  editable fields (`name`, `description`, `permissions`). Backup:
  PATCH-style partial diff. The frontend admin form sends a full
  body either way, so PUT is the smaller surface.
- **Q-21-G — Frontend test harness scope.** Default: Playwright
  only, mirroring the existing `frontend/tests/frontend.spec.ts`
  pattern. Component tests via `vitest` /
  `@testing-library/react` are not introduced in this task because
  the harness is not currently wired. If the director wants
  component-level coverage, we should add the harness as a separate
  task.
- **Q-21-H — `GroupRecord.head.provenance` field exposure.**
  Default: include the full provenance block in admin GET responses
  so the admin UI can show audit details. Backup: hide
  `provenance` from the response and keep it server-only. Default
  is fine for a dev admin tool.

STOP.
