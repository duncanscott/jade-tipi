# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-022 — Restore frontend build baseline (pre-work, revision 1)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task
file `docs/orchestrator/tasks/TASK-022-restore-frontend-build-baseline.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. `TASK-021` is accepted;
its frontend verification surfaced a pre-existing TypeScript build
error in `frontend/app/list/[id]/page.tsx` around the
`getDocument(documentId, accessToken)` call. The director scoped
`TASK-022` narrowly to repairing that build baseline — no admin UI,
backend, realm import, dependency, or unrelated route edits. This
pre-work turn produces a plan only; no source change is made until
the director advances the task to `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION`.

### Diagnosis (sources only, no proposed edits)

`frontend/app/list/[id]/page.tsx:19-20` derives the token from the
next-auth session:

```ts
const { data: session, status: authStatus } = useSession();
const accessToken = session?.accessToken;
```

so `accessToken` is typed as `string | undefined`.

`frontend/lib/api.ts:30-46` defines:

```ts
export async function getDocument(id: string, accessToken: string) { ... }
```

i.e. the second parameter is `string`, not `string | undefined`.

The list-load `useEffect` (lines 25–43) places its guard **inside**
the inner async function:

```ts
async function loadDocumentList() {
  if (!accessToken) {
    return;
  }
  // accessToken is narrowed to string here
  const docs = await listDocuments(accessToken);
  ...
}
```

so TS narrows `accessToken` to `string` at the call site and the
`listDocuments(accessToken)` call type-checks.

The detail-load `useEffect` (lines 46–63) places its guard **outside**
the inner async function:

```ts
useEffect(() => {
  if (!documentId || !accessToken) return;

  async function loadDocument() {
    try {
      setLoading(true);
      const doc = await getDocument(documentId, accessToken); // <-- error
      ...
    }
  }
  loadDocument();
}, [documentId, accessToken]);
```

TypeScript does not carry the outer `if (!accessToken) return;`
narrowing across the function-expression boundary into the body of
`loadDocument`, so inside the closure `accessToken` is read as
`string | undefined`. Passing it to `getDocument`, whose second
parameter is `string`, fails type-checking with

```
Argument of type 'string | undefined' is not assignable to parameter of type 'string'.
```

This is exactly the narrowing pattern that already works in the
list-load effect a few lines above; the detail-load effect is the
asymmetric one and is the file's only build-blocker.

### Proposed change (smallest type-safe repair)

Mirror the existing list-load pattern: move the `accessToken` guard
**into** the inner `loadDocument` function so TS narrows the local
read to `string` at the `getDocument` call site. Keep the outer
`if (!documentId) return;` guard so the effect still bails out fast
when the route id is missing, and keep the dependency array
unchanged.

Diff sketch (`frontend/app/list/[id]/page.tsx`, lines 46–63 region;
final wording finalized in the implementation turn):

```ts
useEffect(() => {
  if (!documentId) return;

  async function loadDocument() {
    if (!accessToken) {
      return;
    }
    try {
      setLoading(true);
      const doc = await getDocument(documentId, accessToken);
      setDocument(doc);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load document');
    } finally {
      setLoading(false);
    }
  }

  loadDocument();
}, [documentId, accessToken]);
```

This is the minimal change:

- One file (`frontend/app/list/[id]/page.tsx`).
- Behavior preserved: when `accessToken` is missing the effect still
  becomes a no-op (it just returns from inside `loadDocument`
  instead of from the effect body) and `loading` is left in its
  prior state until a token arrives, exactly as today. Re-run on
  token arrival is preserved by the unchanged dependency array.
- API helper signatures (`getDocument`, `listDocuments`) are not
  touched — `TASK-022` explicitly disallows changing them unless
  source inspection proves it necessary, and inspection here proves
  the opposite.

Considered alternatives, rejected as larger or weaker:

- **Non-null assertion** (`getDocument(documentId, accessToken!)`) —
  works but lies to the type system at a call boundary that the
  helper itself uses to enforce a real invariant; we'd lose the
  same diagnostic again the next time someone refactors.
- **Cast `accessToken as string`** — same objection as `!`, plus
  noisier.
- **Capture `const token = accessToken;` after the outer guard** —
  doesn't help, because the inner function still reads `token` from
  closure with the same narrowing limitation; we'd need either an
  argument-pass or an in-function check.
- **Pass `accessToken` as a parameter to `loadDocument`** — works,
  but enlarges the diff and diverges from the sibling effect's
  pattern just above. The in-function guard is closer to the
  existing local convention.
- **Loosen `getDocument`'s signature to accept
  `string | undefined`** — out of scope. The helper enforces the
  bearer-token invariant via `ensureAccessToken`; the contract is
  correct.

### Verification plan

For this pre-work turn:

- Static review only. No `npm install`, `npm run build`, Gradle, or
  Docker commands executed.

For the implementation turn (only after the director advances the
task to `READY_FOR_IMPLEMENTATION` / `PROCEED_TO_IMPLEMENTATION`):

```sh
cd frontend && npm install
cd frontend && npm run build
```

Per the task file: if `node_modules` is absent, `npm install` is the
documented setup command and is run before the build. If `npm run
build` exposes additional unrelated pre-existing failures elsewhere
in the frontend, the developer reports the exact file and error and
stops with the smallest next-step recommendation rather than
widening this task without director approval.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths are modified. The implementation turn (gated on a
director signal) will edit only `frontend/app/list/[id]/page.tsx`,
which is listed in `TASK-022`'s `OWNED_PATHS`. No backend, realm
import, dependency, package-lock, admin group-management, or
unrelated frontend route is touched.

### Open questions / blockers

Each has a default proposal so the director can accept or redirect
with one signal change.

- **Q-22-A — Guard placement: in-function check vs parameter
  pass.** Default: in-function `if (!accessToken) return;` mirroring
  the list-load effect immediately above for local consistency.
  Backup: pass `accessToken` as a parameter to `loadDocument` for an
  even tighter narrow at the call site. Either is type-safe and
  behavior-preserving; default is smaller.
- **Q-22-B — Treatment of additional pre-existing build errors.**
  Default: if `npm run build` after the patch surfaces unrelated
  pre-existing TypeScript errors elsewhere in `frontend/`, stop with
  STATUS: BLOCKED (or HUMAN_REQUIRED) and report the exact file,
  line, and message rather than widening `TASK-022` to chase them.
  Backup: roll the next discovery into this same task only if the
  director explicitly expands scope.
- **Q-22-C — Frontend test harness scope.** Default: do not add or
  modify Playwright tests for this build-baseline repair. The fix
  is type-only and the existing list-detail behavior is preserved;
  no new test coverage is needed. Backup: add a Playwright smoke
  test for the list-detail route only if the director asks for it.

STOP.
