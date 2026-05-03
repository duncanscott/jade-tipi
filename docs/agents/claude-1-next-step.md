# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-025 — Plan TypeScript 6 frontend upgrade (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task file
`docs/orchestrator/tasks/TASK-025-typescript-6-upgrade-prework.md` is
`READY_FOR_PREWORK` with `OWNER: claude-1`. The director scoped TASK-025 to
evaluate, and only after director pre-work review possibly upgrade, the
frontend from the accepted TypeScript 5.x line to the latest stable
TypeScript 6 line. Pre-work must:

- Inspect the current `frontend/package.json`, `frontend/package-lock.json`,
  `frontend/tsconfig.json`, and the Next.js / React type-check surface.
- Determine the latest stable TypeScript 6 version on npm and identify
  whether Next.js 16.2.4 and the installed React/Node `@types` packages
  officially support it.
- Document expected migration risks (compiler option changes, stricter
  checks, DOM/lib typing changes, generated `.next` types, App Router type
  generation).
- Propose the smallest implementation plan and exact verification commands.

Out of scope for this task (per the task file): updating Next.js, React,
NextAuth/Auth.js, Tailwind, Playwright, backend code, Docker, Keycloak,
frontend UI, or applying TS 6 source migrations during pre-work.

This pre-work turn produces a plan only and edits exactly
`docs/agents/claude-1-next-step.md` (a base owned path). No source change is
made until the director advances TASK-025 to `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION`.

### Current TypeScript surface (HEAD of `claude-1`)

`frontend/package.json` (devDependencies excerpt):

```json
"devDependencies": {
  "@playwright/test": "^1.59.1",
  "@tailwindcss/postcss": "^4.2.4",
  "@types/node": "^20.19.39",
  "@types/react": "^19.2.14",
  "@types/react-dom": "^19.2.3",
  "tailwindcss": "^4.2.4",
  "typescript": "^5.9.3"
}
```

`frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2017",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "react-jsx",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": { "@/*": ["./*"] }
  },
  "include": [
    "next-env.d.ts",
    "**/*.ts",
    "**/*.tsx",
    ".next/types/**/*.ts",
    ".next/dev/types/**/*.ts"
  ],
  "exclude": ["node_modules"]
}
```

Type-check surface in scope:

- `frontend/auth.ts` — NextAuth v5 beta, Keycloak provider, JWT strategy,
  `events.signOut` `'token' in message` narrowing (TASK-023), access-token
  refresh.
- `frontend/app/**/*.tsx` — App Router pages: `layout.tsx`, `page.tsx`,
  `admin/groups/**`, `document/**`, `list/[id]/page.tsx` (already on
  awaited `params`), and `api/auth/[...nextauth]/route.ts`.
- `frontend/components/layout/Header.tsx`.
- `frontend/lib/{admin-groups,api,uuid}.ts`.
- `frontend/types/next-auth.d.ts` — module augmentation for the NextAuth
  `Session` / `JWT` types.
- `frontend/tests/*.spec.ts` — Playwright tests.
- Generated `.next/types/**/*.ts` and `.next/dev/types/**/*.ts` — emitted
  by Next.js at build time; included in the `include` list. Next 16's TS
  plugin/type generator is built against TypeScript 5.9.

### TypeScript 6 npm metadata

From `npm view typescript dist-tags --json`:

```
{
  "dev": "3.9.4",
  "tag-for-publishing-older-releases": "4.1.6",
  "insiders": "4.6.2-insiders.20220225",
  "beta": "6.0.0-beta",
  "rc": "6.0.1-rc",
  "latest": "6.0.3",
  "next": "6.0.0-dev.20260416"
}
```

Released 6.x stable line and dates (from `npm view typescript --json | .time`):

| Version | Released |
| --- | --- |
| `6.0.1-rc` | 2026-03-06 |
| `6.0.2` | 2026-03-23 |
| `6.0.3` | 2026-04-16 |

Latest stable npm dist-tag is `latest=6.0.3`. `6.0.3` is a stable patch on
the 6.0 line and is the proposed implementation target.

`npm view typescript@6.0.3 engines` reports `node: '>=14.17'`, which is
compatible with the project Node 20 baseline (`CLAUDE.md`).

### Compatibility evidence

| Consumer | Declares TS in `peerDependencies`? | Pinned/internal TS? | Effect |
| --- | --- | --- | --- |
| `next@16.2.4` | No (`npm view next@16.2.4 peerDependencies` does not list `typescript`) | Internal `devDependencies.typescript: '5.9.2'` | Next ships its own TS plugin built against 5.9; nothing forces the consumer to stay on 5.x. |
| `next-auth@5.0.0-beta.31` | No (`peerDependencies` lists only `next`, `react`, `nodemailer`, `@simplewebauthn/*`) | None | Agnostic to TS major. |
| `@types/react@19.2.14` | No (`peerDependencies` is `{}`) | None | Agnostic. |
| `@types/react-dom@19.2.3` | No (matched pair to `@types/react`) | None | Agnostic. |
| `@types/node@20.19.39` | No | None | Agnostic. |
| `@playwright/test@1.59.1` | No TS pin | None | Agnostic. |
| `@tailwindcss/postcss@4.2.4`, `tailwindcss@4.2.4` | No TS pin | None | Agnostic; not in TS compile path. |

No package in the accepted dependency set declares a `peerDependencies`
entry for TypeScript that would forbid `6.x`. The only "soft" coupling is
that Next.js bundles a TypeScript Language Service plugin built against
`typescript@5.9.2`. In practice, Next's TS plugin is loaded only by editors
(via `tsserver`); the build path uses the user's installed `tsc` (or
Next's own type checker invocation through the user's TS), so a 6.x TS
should still type-check the project, but the editor LSP plugin may print a
"plugin built against older TypeScript" notice or, in the worst case, fail
to attach. That is a developer-experience risk, not a build-correctness
risk, and is only observable in IDEs. Verification at implementation time
is `npm run build`.

There is no upstream Next.js or Vercel announcement guaranteeing TS 6
support for Next 16.2.4; treat the support as "compatible by absence of a
peer constraint, pending build verification" rather than "officially
supported". Default proposal recommends proceeding with `READY_FOR_PREWORK`
review and a small implementation, with a clear backout path.

### Migration risks (TypeScript 5.9 → 6.0)

Because TS 6.0 is brand new, expect the following risk classes; mitigations
note where the project already tolerates them.

1. **`lib.dom.d.ts` updates.** TS major releases roll forward DOM lib
   typings, which can surface stricter typings on `Response`, `Request`,
   `URLSearchParams`, `Buffer` interop, `Headers`, and event handlers.
   Mitigation: `skipLibCheck: true` is already set in
   `frontend/tsconfig.json`, which suppresses errors inside `.d.ts` files
   themselves. App-code call sites that pass DOM values still get checked.
   Likely surface area: `frontend/auth.ts` (`fetch`, `Buffer.from(...)`,
   `Response`), `frontend/lib/api.ts` (HTTP fetch wrappers), and route
   handlers in `frontend/app/api/**`.

2. **Stricter narrowing / control-flow analysis.** TS 6 typically tightens
   inference for `unknown`, discriminated unions, and `in` narrowing.
   Mitigation: the codebase already uses defensive `typeof x === 'string'`,
   `Array.isArray(...)`, and explicit `'token' in message` narrowing
   patterns (see `frontend/auth.ts`, `frontend/lib/admin-groups.ts`).
   Likely surface area: any place doing `as Record<string, unknown>` on
   parsed JWT payloads (`frontend/auth.ts`).

3. **Removed deprecated compiler options.** TS 6 removes deprecation flags
   that were warned in TS 5.x (e.g. `--out`, `--keyofStringsOnly`,
   `--suppressExcessPropertyErrors`, `--suppressImplicitAnyIndexErrors`,
   `--noStrictGenericChecks` — exact removal list is documented in the
   official TS 6 release notes; verify against the runtime build error
   list). Mitigation: `frontend/tsconfig.json` does not set any of these
   deprecated flags; current options are `target`, `lib`, `allowJs`,
   `skipLibCheck`, `strict`, `noEmit`, `esModuleInterop`, `module`,
   `moduleResolution`, `resolveJsonModule`, `isolatedModules`, `jsx`,
   `incremental`, `plugins`, `paths` — none of which are deprecated as of
   TS 5.9.

4. **Module resolution / `.tsbuildinfo` invalidation.** TS bumps the
   incremental file format, so `frontend/tsconfig.tsbuildinfo` is stale on
   the first 6.x build. Mitigation: `tsc` deletes/rewrites it
   automatically on version mismatch; no manual cleanup required, but the
   first build will be a full re-check (longer than incremental).

5. **Generated `.next/types/**/*.ts` and `.next/dev/types/**/*.ts`.**
   These are emitted by Next 16's TS-aware route-type generator (built
   against TS 5.9.x). If TS 6 added new strictness around route-shape
   inference (e.g. `params: Promise<...>` typing patterns), the generated
   files could fail compile. Mitigation: the build script is
   `next build --turbopack`, which regenerates these files. If they fail,
   the right response is to report the file/line of the error rather than
   editing generated output. `.next/` is already in `.gitignore`.

6. **Next.js TS Language Service plugin compatibility.** The project pins
   `plugins: [{ "name": "next" }]` in `tsconfig.json`. Editors may emit a
   "plugin built against older TypeScript" warning under 6.x. This is an
   IDE-only signal and does not affect `tsc`/`next build`.

7. **`@types/react@19.2` and `@types/node@20.19` under TS 6.** Both
   packages have empty `peerDependencies`. `skipLibCheck: true` further
   reduces drift. Risk is contained to call-site type errors that already
   show up in normal compilation. No type-package upgrade is in scope.

8. **Module augmentation in `frontend/types/next-auth.d.ts`.** TS major
   versions have, in the past, tightened how `declare module` augmentation
   is required to be exported. Mitigation: review the file shape during
   implementation; it is a single small `.d.ts` in OWNED_PATHS.

9. **`auth.ts` JWT decode path.** Uses `Buffer.from(base64, 'base64')` and
   casts `JSON.parse(...)` to `Record<string, unknown>`. TS 6 may tighten
   `Buffer` typings via `@types/node` interplay; `skipLibCheck` mitigates
   inside `.d.ts`, but call sites are checked. No expected source change,
   but a candidate for build-surfaced errors.

Risk summary: TS 5.9 → 6.0 is a controlled minor-major-style bump. The
project already runs in `strict` mode, has `skipLibCheck` on, and is
already defensively narrowed. Expected churn is small (likely 0–3 files);
the realistic worst case is a handful of explicit type assertions or
`as never`/`satisfies` adjustments in `auth.ts`, `lib/api.ts`, and any
admin/document route handlers.

### Expected file touch list (implementation turn, only after director approval)

In-scope per TASK-025 `OWNED_PATHS`:

- `frontend/package.json` — bump `"typescript": "^5.9.3"` →
  `"typescript": "^6.0.3"`. One-line change.
- `frontend/package-lock.json` — regenerated by `npm install`. Mechanical.
- `frontend/tsconfig.json` — only if a TS 6 build error explicitly demands
  a config change. Default expectation: no change. If `target: "ES2017"`
  becomes deprecated in TS 6 (it is not deprecated as of TS 5.9), bump to
  `"ES2022"` only with explicit notation.
- `frontend/auth.ts`, `frontend/lib/**`, `frontend/app/**`,
  `frontend/components/**`, `frontend/types/next-auth.d.ts`,
  `frontend/tests/**` — only if `npm run build` reports a TS 6 strictness
  error. Each touch is justified in the implementation report
  (`docs/agents/claude-1-changes.md`) with the file/line of the build
  error that drove it.

Expected size: 1 line in `package.json`, lockfile regen, 0–3 small source
edits driven by build errors. No re-architecture, no flag flips, no API
surface change.

If the build surfaces churn larger than ~10 unrelated files, the
implementation turn stops with `STATUS: BLOCKED` and reports the error
list rather than self-expanding TASK-025; the director can then choose to
back out (revert to TS 5.9.x) or expand scope.

### Exact update / verification commands

Implementation turn (only after director advances task to
`READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`):

```sh
cd /Users/duncanscott/orchestrator/jade-tipi/developers/claude-1/frontend

# 1. Bump only TypeScript to the newest stable 6.x. Stays inside
#    OWNED_PATHS; no other dependency changes.
npm install --save-dev typescript@6.0.3

# 2. Idempotent install to confirm the lockfile is clean.
npm install

# 3. Production build (the project's primary verification path; this is
#    what regenerates .next/types/**/*.ts and exercises the TS plugin).
npm run build

# 4. Narrowest practical frontend test command. If Playwright browser
#    binaries are missing or stale, the documented setup command is:
#      npx playwright install chromium
#    Per CLAUDE.md "Tooling Refresh", missing browser binaries are a
#    setup issue, not a product blocker; the implementation report names
#    the setup command and the result.
npm test
```

If `npm run build` reports new TS 6 errors that are local to a small
number of files (≤3) and are mechanical (e.g. `'X' is possibly null`
narrowings, missing `as const`, `satisfies` adjustments), the
implementation turn applies the smallest fix per file and re-runs
`npm run build`.

If `npm run build` reports broad TS 6 errors (>10 files, generated
`.next/types/**/*.ts` self-incompatibility, or compiler-option removals
that require a `tsconfig.json` redesign), the implementation turn stops
with `STATUS: BLOCKED` (or `HUMAN_REQUIRED` for compiler-option
redesign), reports the list, and recommends backing out the
`typescript@6.0.3` bump to `5.9.3`.

Backout path: `npm install --save-dev typescript@5.9.3` reverts the only
in-scope source change other than the lockfile. The pre-existing baseline
remains the accepted TS 5.9.x state from TASK-024.

If npm registry / network is unreachable, the implementation turn reports
the exact failing `npm install` command and treats it as a tooling /
environment issue, not a product blocker.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other files are touched. The implementation turn (gated on a director
signal) will edit only paths inside TASK-025 `OWNED_PATHS`:
`frontend/package.json`, `frontend/package-lock.json`,
`frontend/tsconfig.json`, `frontend/app/`, `frontend/components/`,
`frontend/lib/`, `frontend/types/`, `frontend/tests/`, plus the report
file `docs/agents/claude-1-changes.md`. No backend, Gradle, Docker,
Keycloak realm, frontend `.env.local`, or workspace orchestrator file is
touched.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect with
one signal change.

- **Q-25-A — Target `typescript@6.0.3` vs hold on `5.9.3`.** TS `latest`
  is `6.0.3`, released 2026-04-16, two patches into the 6.0 line. No
  consumer in the accepted dependency set pins TypeScript via
  `peerDependencies`. **Default proposal:** target `^6.0.3` and verify
  via `npm run build`. **Backup:** hold on `5.9.3` and re-evaluate after
  Next.js publishes a release that explicitly enumerates TS 6 support;
  this is the lower-risk path if the director wants to wait for upstream
  confirmation. Default favors moving forward because the project's
  `skipLibCheck`, `strict`, and existing defensive narrowing reduce
  expected churn to a small number of files.

- **Q-25-B — Bump `tsconfig.json` `target` from `ES2017` to `ES2022`.**
  TS 6 still supports `ES2017` as a valid target as of TS 5.9 docs;
  there is no deprecation signal yet. **Default proposal:** leave
  `target: "ES2017"` unchanged for this task. **Backup:** if TS 6
  release notes (verify at implementation time) deprecate `ES2017` as a
  warning, bump to `ES2022` (the project's runtime is Node 20, which
  supports ES2022 natively) as a one-line change inside OWNED_PATHS and
  document it in the report.

- **Q-25-C — Behaviour on broad TS 6 type churn.** **Default proposal:**
  if `npm run build` surfaces TS 6 errors in >10 files or in generated
  `.next/types/**/*.ts`, stop with `STATUS: BLOCKED`, list the errors,
  and recommend backing out to `typescript@5.9.3`. **Backup:** absorb
  the churn within TASK-025 only if the director explicitly expands
  scope, mirroring how TASK-022/023 propagated.

- **Q-25-D — Next.js TypeScript Language Service plugin warning.** Next
  16.2.4 ships a TS plugin built against `typescript@5.9.2`. Editors may
  log a "plugin built against older TypeScript" notice under 6.x.
  **Default proposal:** treat this as a non-blocking IDE signal; do not
  remove the `plugins: [{ "name": "next" }]` entry from
  `tsconfig.json`, and do not gate the upgrade on a Next.js TS plugin
  release. **Backup:** if the plugin actively breaks `tsserver`, file a
  follow-up task and consider deferring TS 6 until Next.js publishes a
  matched plugin.

- **Q-25-E — Treatment of pre-existing build/test errors unrelated to
  the TS bump.** **Default proposal:** if `npm run build` surfaces
  errors that reproduce on the pre-bump tree as well, report them with
  file/line and stop with `STATUS: BLOCKED` rather than widening
  TASK-025. **Backup:** fold a small fix into TASK-025 only if the
  director explicitly expands scope.

- **Q-25-F — Playwright browser-binary install in the verification
  turn.** `npm test` requires a chromium browser binary; if absent,
  Playwright errors at test launch. **Default proposal:** in the
  implementation turn, run `npx playwright install chromium` once
  before `npm test` if binaries are missing (per CLAUDE.md "Tooling
  Refresh"). If install fails (sandboxed network, missing GTK on macOS,
  etc.), the developer reports the exact error and command and treats
  it as a setup issue, not a product blocker. **Backup:** restrict
  verification to `npm run build` only, document Playwright as
  out-of-reach, and stop.

- **Q-25-G — Lockfile regeneration vs targeted edits.** **Default
  proposal:** let `npm install --save-dev typescript@6.0.3` rewrite
  `frontend/package-lock.json` end-to-end as the natural product of
  the version pin; do not hand-edit the lockfile. The diff will be
  small (typescript dependency tree only). **Backup:** none — lockfile
  regeneration is npm-canonical.

STOP.
