# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-023 — Fix NextAuth sign-out build error (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task
file `docs/orchestrator/tasks/TASK-023-fix-nextauth-signout-build-error.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. `TASK-022` is accepted; its
`npm run build` verification surfaced the next pre-existing TypeScript
blocker in `frontend/auth.ts` around the `events.signOut` callback. The
director scoped `TASK-023` narrowly to a NextAuth callback union-narrowing
repair — no provider, session/JWT callback, admin-role derivation,
Keycloak realm, backend, dependency, or unrelated route changes. This
pre-work turn produces a plan only; no source change is made until the
director advances the task to `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION`.

### Diagnosis (sources only, no proposed edits)

`frontend/auth.ts:79-97` declares the `events.signOut` callback by
destructuring `{ token }` directly from the message argument:

```ts
events: {
  async signOut({ token }) {
    if (token?.idToken) {
      const issuerUrl = process.env.KEYCLOAK_ISSUER!
      const logoutUrl = `${issuerUrl}/protocol/openid-connect/logout`
      const params = new URLSearchParams({
        id_token_hint: token.idToken as string,
        post_logout_redirect_uri: process.env.NEXTAUTH_URL || 'http://localhost:3000'
      })
      try {
        await fetch(`${logoutUrl}?${params.toString()}`, { method: 'GET' })
      } catch (error) {
        console.error('Error during Keycloak logout:', error)
      }
    }
  }
}
```

Auth.js / NextAuth v5 declares the `signOut` event message as a
**discriminated union** of two object shapes (one per session strategy)
rather than a single object that always carries `token`. From
`frontend/node_modules/@auth/core/index.d.ts:362-366`:

```ts
signOut?: (message: {
    session: Awaited<ReturnType<Required<Adapter>["deleteSession"]>>;
} | {
    token: Awaited<ReturnType<JWTOptions["decode"]>>;
}) => Awaited<unknown>;
```

The two arms are disjoint — the database-session arm has `session` and
**no** `token` property; the JWT arm has `token` and **no** `session`
property. TypeScript only allows direct property access on a union when
the property exists in **every** arm, so `({ token })` destructuring
asks for a property the database-session arm does not declare, and the
compiler reports the exact error captured in `TASK-023.DESIGN_NOTES`:

```
frontend/auth.ts:80:21 Property 'token' does not exist on type
'{ session: void | AdapterSession | null | undefined; } | { token: JWT | null; }'.
```

The Keycloak provider in this app uses the JWT session strategy (the
default — there is no `Adapter` configured at `frontend/auth.ts:50-57`),
so at runtime only the `token` arm is ever delivered. The current
`token?.idToken` guard already correctly handles the `JWT | null` case;
the bug is purely at the type level: the destructure does not narrow the
union before reading `token`.

### Proposed change (smallest type-safe repair)

Mirror the narrowing pattern recommended in `TASK-023.DESIGN_NOTES`:
accept the `signOut` message as a single value, narrow with the JS `in`
operator (`'token' in message`), and keep the existing logout body
unchanged for the JWT arm. Return early without attempting logout for
the database-session arm. No NextAuth config, provider, or callback
behavior changes; only the destructure-vs-narrow shape of this one
event handler.

Diff sketch (`frontend/auth.ts`, lines 79-98 region; final wording
finalized in the implementation turn):

```ts
events: {
  async signOut(message) {
    // The signOut event message is a discriminated union: { session }
    // for database-session strategies and { token } for JWT. This app
    // uses the JWT strategy via Keycloak, but narrowing with 'in'
    // keeps the type checker honest if a future adapter is added.
    if (!('token' in message) || !message.token?.idToken) {
      return
    }
    const token = message.token
    const issuerUrl = process.env.KEYCLOAK_ISSUER!
    const logoutUrl = `${issuerUrl}/protocol/openid-connect/logout`
    const params = new URLSearchParams({
      id_token_hint: token.idToken as string,
      post_logout_redirect_uri: process.env.NEXTAUTH_URL || 'http://localhost:3000'
    })

    try {
      await fetch(`${logoutUrl}?${params.toString()}`, { method: 'GET' })
    } catch (error) {
      console.error('Error during Keycloak logout:', error)
    }
  }
}
```

This is the minimal change:

- One file (`frontend/auth.ts`).
- Behavior preserved for the JWT arm: when `token?.idToken` is truthy,
  the same `id_token_hint` + `post_logout_redirect_uri` GET against
  `${issuerUrl}/protocol/openid-connect/logout` runs inside the same
  `try/catch` with the same `console.error` on failure. When no token
  variant is present, the handler returns without calling Keycloak,
  matching the prior `if (token?.idToken)` no-op path.
- The Keycloak realm import, provider clientId/clientSecret/issuer
  wiring, JWT/session callbacks, `decodeJwtPayload` /
  `isAdminFromAccessToken` helpers, and `ADMIN_ROLE` constant are not
  touched. No package, lockfile, or test edit.
- The existing `as string` cast on `token.idToken` is preserved
  intentionally because the project's `JWT` typing for `idToken` has
  not been declared elsewhere; widening that contract is `TASK-023`
  out-of-scope.

Considered alternatives, rejected as larger or weaker:

- **Cast the parameter** (`signOut(message: { token: JWT | null })`) —
  works but lies about the upstream union. The next time someone adds
  a database adapter, type-checking silently passes while runtime
  receives a `session` payload.
- **Destructure with default** (`async signOut({ token } = {})`) —
  does not satisfy TS narrowing on a discriminated union; the property
  still must exist on every arm.
- **`as` cast on the message** (`(message as { token: JWT | null })`) —
  same objection as the parameter cast plus extra noise.
- **Switch to `in` narrowing inline at the access site** without
  extracting a local — would require repeating `message.token` instead
  of the existing `token` local. The proposed `const token = message.token`
  after narrowing keeps the rest of the body byte-identical to today.
- **Loosen NextAuth's `signOut` event signature** (e.g. via module
  augmentation) — out of scope; widens contract for unrelated future
  callers.
- **Drop the Keycloak logout fetch entirely and rely on cookie clear** —
  out of scope; explicitly forbidden by the task's
  `ACCEPTANCE_CRITERIA` ("preserve existing Keycloak sign-out
  behavior").

### Verification plan

For this pre-work turn:

- Static review only. No `npm install`, `npm run build`, Gradle, or
  Docker commands executed.

For the implementation turn (only after the director advances the
task to `READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`):

```sh
cd frontend && npm install   # only if node_modules is absent or stale
cd frontend && npm run build
```

Per the task file: `cd frontend && npm install` is the documented
setup command and is run before `npm run build` only if the local
worktree's `node_modules` is missing. If `npm run build` exposes
**additional unrelated** pre-existing failures elsewhere in the
frontend, the developer reports the exact file/line/error and stops
with the smallest next-step recommendation rather than widening
`TASK-023` without director approval (mirroring how `TASK-022`
surfaced this very task).

If the build environment itself blocks the check (for example a
sandbox/Turbopack process-spawn restriction, as encountered during
`TASK-022` director verification), the developer reports the exact
command and error and treats it as a tooling/environment issue, not a
product blocker.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths are modified. The implementation turn (gated on a
director signal) will edit only `frontend/auth.ts`, which is listed
in `TASK-023`'s `OWNED_PATHS`. No NextAuth provider, session/JWT
callback, admin-role derivation, Keycloak realm import, backend,
package, lockfile, frontend test, or unrelated frontend route is
touched.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect
with one signal change.

- **Q-23-A — Narrowing operator: `'token' in message` vs explicit
  cast.** Default: `'token' in message` runtime-`in` narrowing, which
  is type-safe across both union arms and survives a future database
  adapter. Backup: parameter-level type assertion, smaller diff but
  hides the union from the compiler. Default is safer and matches the
  pattern recommended in `TASK-023.DESIGN_NOTES`.
- **Q-23-B — Local rebinding (`const token = message.token`) vs
  inline `message.token` access.** Default: introduce `const token`
  after narrowing so the rest of the handler body is byte-identical
  to today and the existing `token.idToken as string` cast is
  preserved verbatim. Backup: rewrite call sites to read
  `message.token.idToken` directly — slightly noisier, equivalent
  type-checking outcome.
- **Q-23-C — Treatment of additional pre-existing build errors.**
  Default: if `npm run build` after the patch surfaces unrelated
  pre-existing TypeScript errors elsewhere in `frontend/`, stop with
  STATUS: BLOCKED (or HUMAN_REQUIRED) and report the exact file,
  line, and message rather than widening `TASK-023` to chase them.
  Backup: roll the next discovery into this same task only if the
  director explicitly expands scope.
- **Q-23-D — Frontend test harness scope.** Default: do not add or
  modify Playwright tests for this build-baseline repair. The fix is
  type-only and the existing JWT-arm Keycloak logout fetch is
  preserved. Backup: add a unit-style test of the narrowing only if
  the director asks for one.

STOP.
