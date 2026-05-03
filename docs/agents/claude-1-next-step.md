# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-024 — Update Next.js and npm dependencies (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-024-update-nextjs-and-npm-dependencies.md` is
`READY_FOR_PREWORK` with `OWNER: claude-1`. The director scoped TASK-024 to:
update the Next.js frontend to the latest stable Next.js release available at
implementation time, and update all npm `dependencies` / `devDependencies` in
`frontend/package.json` to current stable compatible versions, applying the
small source migrations required by those upgrades. Stable npm dist-tags are
preferred; intentional moves to prerelease/beta/RC/canary are only allowed
when no stable replacement exists and the tradeoff is documented in pre-work.
The upgrade must preserve the admin group-management workflow, Keycloak
login, access-token refresh behavior, document CRUD pages, and test routes.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source change is
made until the director advances TASK-024 to `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION`.

### Current frontend dependency set

From `frontend/package.json` at HEAD of `claude-1`:

```json
"dependencies": {
  "next": "15.5.4",
  "next-auth": "^5.0.0-beta.30",
  "react": "19.1.0",
  "react-dom": "19.1.0"
},
"devDependencies": {
  "@playwright/test": "^1.56.1",
  "@tailwindcss/postcss": "^4",
  "@types/node": "^20",
  "@types/react": "^19",
  "@types/react-dom": "^19",
  "tailwindcss": "^4",
  "typescript": "^5"
}
```

Surrounding configuration that constrains target ranges:

- `frontend/next.config.ts` enables Turbopack with `root: "../"` (already on
  the Next 15 Turbopack path; production `next build --turbopack` is the
  configured build script).
- `frontend/tsconfig.json` uses `"target": "ES2017"`, `"strict": true`,
  `"moduleResolution": "bundler"`, includes a `"plugins": [{ "name": "next" }]`
  Next plugin entry.
- `CLAUDE.md` pins the project's Node version at **Node 20** (root project
  CLAUDE.md, "Java 21, Node 20"). Local Node in this worktree is `v25.9.0`,
  which satisfies the runtime, but the `@types/node` major must continue to
  track the project-declared LTS.
- The frontend uses `next/server`, `next/headers` (implicit via API routes),
  `next/navigation`, `next/image`, `next/link`, `next/font`, and the App
  Router across `app/admin/groups/**`, `app/list/**`, `app/document/**`,
  `app/page.tsx`, `app/layout.tsx`, `app/api/auth/[...nextauth]/route.ts`,
  and `components/layout/Header.tsx`. NextAuth/Auth.js is consumed only via
  `frontend/auth.ts` (Keycloak provider, JWT strategy, `events.signOut` with
  `'token' in message` narrowing already in place from accepted TASK-023).

### Latest stable npm versions (queried via `npm view <pkg> dist-tags`)

| Package | Current pin | Stable `latest` | Stable target proposal | Tag notes |
| --- | --- | --- | --- | --- |
| `next` | `15.5.4` | `16.2.4` | **`16.2.4`** | `latest=16.2.4`, `canary=16.3.0-canary.9`, `beta=16.0.0-beta.0`. Major bump 15 → 16. |
| `react` | `19.1.0` | `19.2.5` | **`19.2.5`** | Minor bump within React 19. `canary`/`next` are not stable. |
| `react-dom` | `19.1.0` | `19.2.5` | **`19.2.5`** | Move in lockstep with `react`. |
| `next-auth` | `^5.0.0-beta.30` | `4.24.14` (legacy v4) | **`5.0.0-beta.31`** (documented prerelease exception) | `latest=4.24.14` is the legacy v4 line; Auth.js v5 still ships only as `beta` (`5.0.0-beta.31`). Project is already on the v5 beta and depends on v5-only APIs (`signIn`, `signOut`, `auth`, `handlers` exports; JWT `events.signOut` discriminated union). Moving to `latest` (v4) would be a regression and re-architect, not an upgrade. Bumping within the v5 beta line is the only forward-compatible option. |
| `@playwright/test` | `^1.56.1` | `1.59.1` | **`1.59.1`** | Minor bump. `latest=1.59.1`. |
| `@tailwindcss/postcss` | `^4` | `4.2.4` | **`4.2.4`** | Already on Tailwind v4 line. `next=4.0.0` predates `latest`. |
| `tailwindcss` | `^4` | `4.2.4` | **`4.2.4`** | Same as above; matched pair. |
| `@types/node` | `^20` | `25.6.0` (latest tag) | **`^20.19.39`** (stay on Node 20 LTS) | The `latest` dist-tag tracks the newest @types/node major (currently 25.x), but the project pins runtime Node to 20 in `CLAUDE.md`. Bumping types past Node 20 LTS would surface global-typings drift unrelated to this task. Latest 20.x patch from `npm view @types/node@20 version` is `20.19.39`. |
| `@types/react` | `^19` | `19.2.14` | **`19.2.14`** | Track React 19.2. |
| `@types/react-dom` | `^19` | `19.2.3` | **`19.2.3`** | Track React 19.2. |
| `typescript` | `^5` | `6.0.3` | **`5.9.3`** (defer TS 6 as follow-up) — see Q-24-A | `latest=6.0.3` is a brand-new TypeScript major. The directive allows major migrations but tells the developer to "keep the task bounded by preserving existing behavior." TS 6 is a separate, broad migration risk (libcheck/lib.dom changes, stricter narrowing, deprecated flag removals) on top of Next 16 + React 19.2 + Tailwind 4.2 + Playwright 1.59 + NextAuth beta. Default is to upgrade to the newest TS 5.x stable (`5.9.3`) and propose TS 6 as a follow-up TASK. |

Stable-vs-prerelease summary: every entry above is a stable npm `latest`
release except `next-auth`, where `latest` points to legacy v4. Pre-work
documents the next-auth beta exception per the directive.

### Migration risks and required source changes

Most likely areas of breakage, ranked by impact:

1. **Next.js 15.5.4 → 16.2.4 (major).** Highest risk. Known Next 16 themes
   from upstream changelogs (subject to verification during implementation):
   - `next/font` packaging consolidation; the project uses `next/font` indirectly
     via App Router conventions. Any direct `@next/font` import (none found in
     this worktree) would need to move to `next/font`.
   - Async dynamic APIs — `cookies()`, `headers()`, `params`, `searchParams`
     are `Promise`-typed in Next 15.x and remain so in 16; project pages such
     as `frontend/app/list/[id]/page.tsx` and `frontend/app/admin/groups/[id]/page.tsx`
     already `await params`, so they are forward-compatible.
   - Turbopack production-build defaults; project already opts in via
     `"build": "next build --turbopack"` and `next.config.ts` `turbopack`
     block, so this should be a no-op.
   - Caching defaults / `fetch` cache behavior — App Router fetches default
     to `no-store` in Next 15+ already; project does not depend on implicit
     `force-cache`, so risk is low but worth a build-time scan.
   - Codemod path: `npx @next/codemod@latest upgrade` is the documented
     upgrade entrypoint. Default plan is to run it once non-interactively
     and review the diff; if the codemod proposes large refactors outside
     `frontend/`, stop and report.
   - Minimum Node engine for Next 16 is `>=20.9.0` (`npm view next engines`),
     compatible with the project's Node 20 baseline.
   - Verification: `cd frontend && npm run build` is the source of truth.

2. **NextAuth/Auth.js `5.0.0-beta.30 → 5.0.0-beta.31` (patch within beta).**
   Lower risk because the v5 surface used by this app is small and stable
   between betas: `NextAuth({...providers, callbacks, events})`,
   `Keycloak` provider, `JWT` type, `events.signOut(message)` with
   `'token' in message` narrowing (already applied in TASK-023). Risk:
   incidental type drift on the `signOut` message union or on
   `JWT`/`Account`. Mitigation: the build is already type-strict; any
   beta drift will surface in `npm run build` before reaching runtime.

3. **TypeScript `^5` → `5.9.3` (minor).** Low risk; project already
   accepts `^5`. Newest 5.x patch likely compiles unchanged. TS 6 (`6.0.3`)
   is intentionally deferred — see Q-24-A.

4. **React 19.1 → 19.2 + matching `@types/react` 19.2.x.** Low risk; React
   19.2 is a minor with no known breaking semantics. The `@types/react`
   bump is the place where surface drift typically appears (event-handler
   typings, ref typings); mitigated by `npm run build` running in strict TS.

5. **Tailwind 4.x → 4.2.4 + `@tailwindcss/postcss` 4.2.4.** Low risk;
   project already on the v4 line via `^4`. PostCSS plugin and core stay in
   lockstep. No `tailwind.config.*` file in the worktree (`postcss.config.mjs`
   is the only PostCSS surface), so risk of config-format breakage is small.

6. **Playwright `1.56.1 → 1.59.1`.** Low risk; minor bump. Risk areas:
   browser-binary mismatch (handled by `npx playwright install`), test
   timeouts/heuristics. Two tests (`tests/admin-groups.spec.ts`,
   `tests/frontend.spec.ts`) and a small `playwright.config.ts` are the
   total surface.

7. **`@types/node` stays on `^20.19.x`.** Low risk; explicitly pinned
   to LTS 20 to match the project's `CLAUDE.md` Node baseline.

Source migrations expected (small, mechanical):

- `frontend/package.json` and `frontend/package-lock.json` updated together.
- Any small touchups required by Next 16 codemod output, restricted to
  the OWNED_PATHS (`frontend/app/`, `frontend/components/`, `frontend/lib/`,
  `frontend/types/`, `frontend/tests/`, `frontend/package.json`,
  `frontend/package-lock.json`).
- No new packages added unless a Next 16 / Auth.js migration explicitly
  introduces one (e.g. an extracted `@next/...` peer); any such addition
  is documented in the implementation report.
- No backend, Gradle, Docker, Keycloak realm, or admin-API change.
- Preserve `frontend/auth.ts` Keycloak refresh and `events.signOut`
  narrowing exactly as TASK-023 left them, except for type-only
  adjustments forced by NextAuth beta.31.
- Preserve `frontend/app/admin/groups/**` (group-management UI),
  `frontend/app/document/**` (document CRUD), `frontend/app/list/**`
  (test routes), and the access-token refresh flow.

### Exact update / verification commands

Implementation turn (only after director advances task to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`):

```sh
cd frontend
# 1. Bump dependencies in one transactional install. Pinned versions match
#    the table above. `--save-exact` is intentionally NOT used; project
#    convention is caret ranges where the current package.json uses them.
npm install \
  next@16.2.4 \
  react@19.2.5 \
  react-dom@19.2.5 \
  next-auth@5.0.0-beta.31

npm install --save-dev \
  @playwright/test@1.59.1 \
  @tailwindcss/postcss@4.2.4 \
  tailwindcss@4.2.4 \
  @types/node@20.19.39 \
  @types/react@19.2.14 \
  @types/react-dom@19.2.3 \
  typescript@5.9.3

# 2. Run the official Next.js upgrade codemod once and review its diff.
#    If it proposes changes outside frontend/ or the OWNED_PATHS, stop and
#    report rather than apply silently.
npx --yes @next/codemod@latest upgrade latest

# 3. Verify the install is clean and the lockfile is regenerated correctly.
cd /Users/duncanscott/orchestrator/jade-tipi/developers/claude-1/frontend
npm install   # idempotent re-resolve; should be a no-op after step 1

# 4. Production build (the project's primary verification path).
npm run build

# 5. Narrowest practical frontend test command. If Playwright browser
#    binaries are missing or stale, the documented setup command is:
#      npx playwright install chromium
#    Per CLAUDE.md "Tooling Refresh", missing browser binaries are a setup
#    issue, not a product blocker; the implementation report names the
#    setup command and result.
npm test
```

If `npm run build` exposes pre-existing failures unrelated to dependency
upgrades, the developer reports the exact file/line/error and stops with
the smallest next-step recommendation rather than widening TASK-024.

If the npm registry / network is unreachable, the developer reports the
exact failing `npm install` command and treats it as a tooling/environment
issue, not a product blocker.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other files are touched. The implementation turn (gated on a director
signal) will edit only paths inside TASK-024 `OWNED_PATHS`:
`frontend/package.json`, `frontend/package-lock.json`, and any small
compatibility edits inside `frontend/app/`, `frontend/components/`,
`frontend/lib/`, `frontend/types/`, `frontend/tests/`, plus the report
file `docs/agents/claude-1-changes.md`. No backend, Gradle, Docker,
Keycloak realm, frontend `.env.local`, or workspace orchestrator file
is touched.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect with
one signal change.

- **Q-24-A — TypeScript 6 vs stay on TypeScript 5.9.x.** TS `latest` is
  `6.0.3`. TS 6 is a brand-new major release that lands stricter checks
  and removes deprecated flags. The TASK-024 directive allows major
  migrations but instructs the developer to "keep the task bounded by
  preserving existing behavior" and to split if the migration is too
  large. **Default proposal:** upgrade `typescript` to `5.9.3` (newest 5.x
  stable) for this task, leaving TS 6 as a follow-up TASK so the Next 16
  upgrade is not entangled with broad type-checker churn. **Backup:**
  upgrade `typescript` to `^6.0.3` and absorb whatever migration the
  build surfaces, accepting that TASK-024 may grow. Director picks.

- **Q-24-B — `next-auth` stays on the v5 beta line.** Auth.js v5 has not
  shipped a stable release as of 2026-05-02; `npm view next-auth dist-tags`
  shows `latest=4.24.14` (legacy v4) and `beta=5.0.0-beta.31`. The
  project is currently on `5.0.0-beta.30` and uses v5-only exports
  (`handlers`, `signIn`, `signOut`, `auth`) and v5-only event signatures
  (the `events.signOut` discriminated union narrowed in TASK-023). Moving
  to `latest` would mean a v4 rewrite, not an upgrade. **Default
  proposal:** bump within the v5 beta line to `5.0.0-beta.31` and note
  the prerelease exception per the directive. **Backup:** hold next-auth
  at `5.0.0-beta.30` and exclude it from this task. Default is the
  project-spirit choice; backup is stricter on the "no prereleases" rule.

- **Q-24-C — `@types/node` stays on `^20.x` even though `latest` is 25.x.**
  Project Node baseline is 20 (`CLAUDE.md`). Bumping `@types/node` to a
  newer major than the runtime causes type-only drift in `Buffer`, `URL`,
  `process`, etc. that does not match the deployed Node version. **Default
  proposal:** pin to the latest 20.x patch (`20.19.39`). **Backup:** if
  the director also wants to advance the runtime Node baseline, that is
  a separate concern outside `frontend/package.json` (`CLAUDE.md`,
  `gradle.properties`, deploy configs) and should be its own TASK.

- **Q-24-D — Run `@next/codemod@latest upgrade` automatically vs hand-edit.**
  The codemod is the official Next 16 upgrade tool. **Default proposal:**
  run it once non-interactively, review the diff, and only commit changes
  that fall inside `frontend/` OWNED_PATHS. If it proposes edits outside
  the owned scope (e.g. a workspace `package.json`), stop and report.
  **Backup:** skip the codemod and apply migrations by hand. Codemod
  default is more reliable for App Router conventions.

- **Q-24-E — Playwright browser-binary install in the verification turn.**
  `npm test` requires a chromium browser binary; if absent, Playwright
  errors at test launch. **Default proposal:** in the implementation
  turn, run `npx playwright install chromium` once before `npm test` if
  binaries are missing (per CLAUDE.md "Tooling Refresh"). If install
  fails (sandboxed network, missing GTK on macOS, etc.), the developer
  reports the exact error and command and treats it as a setup issue,
  not a product blocker. **Backup:** restrict verification to
  `npm run build` only, document Playwright as out-of-reach, and stop.

- **Q-24-F — Treatment of additional pre-existing build/test errors
  unrelated to upgrade churn.** **Default proposal:** if `npm run build`
  surfaces pre-existing TypeScript or runtime errors that exist on the
  pre-upgrade tree as well, the developer reports them with file/line
  and stops with STATUS: BLOCKED rather than widening TASK-024. **Backup:**
  fold a small fix into TASK-024 only if the director explicitly expands
  scope, mirroring how TASK-022/023 propagated.

- **Q-24-G — Lockfile regeneration vs targeted edits.** **Default
  proposal:** let `npm install` rewrite `frontend/package-lock.json`
  end-to-end as the natural product of the version pins above; do not
  hand-edit the lockfile. The diff will be large but mechanical.
  **Backup:** none — lockfile regeneration is npm-canonical.

STOP.
