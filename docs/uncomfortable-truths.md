# Uncomfortable Truths

A ledger of known gaps between what JDTP promises and what the reference
implementation does today, stated plainly so none of them can quietly
re-drift. Director-ratified 2026-07-04: **these issues are TODO items and
should be addressed** — each entry records what the truth is, the failure
it permits, and its disposition.

Rules for this document:

- Every item has a stable ID (`UT-n`) so tasks and commits can reference
  it. IDs are never reused.
- An item leaves this list only when a task resolves it; record the
  resolving task inline rather than deleting the entry, so the history of
  what was uncomfortable — and when it stopped being — is preserved.
- Statuses: **OPEN** (needs a task or a director decision that is not yet
  scheduled), **PLANNED** (covered by a ratified drift-note plan task),
  **DELIBERATE** (a scoping decision, kept with an explicit revisit
  trigger).

The specification's enforcement summary
([`jdtp-specification.md`](jdtp-specification.md) §4) is the normative
statement of what is and is not enforced; this document is the work
ledger behind it.

---

## UT-1 — Writer identity dies with transport retention

**Status: PLANNED** (drift-note plan tasks B–C, currently deferred by
director decision) · **Priority: highest before multi-user or external
use**

The message envelope carries a verified writer identity (`txn.user`, an
ORCID iD), but persistence drops it: durable transaction records carry no
user at all. Once the transport's retention window expires, "who wrote
this value?" is unanswerable — which contradicts the protocol's core
provenance promise.

- Failure permitted: irrecoverable loss of write attribution for all
  history older than transport retention.
- Why deferral is currently safe: a single operator; system transactions
  carry the `jdtp-admin` convention in the envelope, so the data is right
  even though it is not yet persisted.
- Revisit trigger: **before** any second writer, any shared deployment,
  or any externally reviewable provenance claim.

## UT-2 — Rollback is not persisted

**Status: OPEN — recommend a small dedicated task** · **Priority: high
(integrity)**

`txn + rollback` is acknowledged and logged, then forgotten. The
transaction header stays `open` forever, its appended messages remain
stored, and there is no audit record that a rollback was requested.

- Failure permitted: a `commit` arriving **after** a rollback (replay,
  bug, or malice) commits and materializes the transaction as if the
  rollback never happened.
- Fix shape (small): persist a `rolled_back` header state; refuse
  commit-after-rollback; keep the rollback as an audit fact. Fits
  naturally with the lifecycle work (plan task F) but is cheap enough to
  do standalone first.

## UT-3 — `value_schema` never validates submitted values

**Status: OPEN — needs a director decision on where validation lives** ·
**Priority: high before bulk import**

A property defined as `{ "text": string }` will happily accept
`{ "number": 7 }` as a value; the garbage materializes onto the object
root with full provenance. Value typing is convention-only.

- Failure permitted: silently ill-typed values at scale; hand-mapped
  seeds are clean, millions of real CouchDB records will not be.
- Decision needed: write-time validation at the materializer gate
  (stronger; recommended) vs. the documented read-time-validator
  direction. Relates to the eventual purpose of the `vdn` collection.
- Revisit trigger: **before** the bulk-import selection strategy turns
  the narrow import loop into volume ingestion.

## UT-4 — `uni` and `vdn` are wire-accepted and silently ignored

**Status: OPEN — small task** · **Priority: medium**

Messages targeting `uni` (units) and `vdn` (validation) pass schema
validation, land on the transaction record, and are skipped at
materialization as unsupported — without error. A client can submit units
and reasonably believe they exist.

- Failure permitted: silent data loss from the client's point of view.
- Fix shape: either materialize them (the root-create path is
  collection-agnostic; near-trivial) or reject them at the wire until
  supported. Accept-and-ignore is the worst of the three options.

## UT-5 — Null-user envelopes are rejected by accident, not decision

**Status: OPEN — fold into plan task C** · **Priority: low (recorded)**

A `Transaction` built without a user serializes `"user": null`, which the
wire schema rejects (`string expected`). The wire therefore *requires* a
writer identity today by serialization quirk rather than by ratified
rule. All real clients send a user.

- Decision needed (in the writer-persistence task): formally require
  `user` (probably right — it aligns with the provenance goals) or omit
  the field when absent and define the unattributed-writer policy.

## UT-6 — No state guard on message append; late messages can surprise

**Status: PLANNED** (plan tasks D/F) · **Priority: medium**

Messages append to a transaction regardless of header state — before
open, even after commit. A message arriving after commit sits inert
unless a commit **redelivery** later occurs, at which point it suddenly
materializes.

- Failure permitted: nondeterministic late-effect semantics dependent on
  transport redelivery behavior.
- Covered by: the ratified lifecycle — staging, `message_count` at
  commit, per-message `apply_state`, and the `applied` watermark.

## UT-7 — No re-drive of committed-but-unmaterialized transactions

**Status: PLANNED** (plan task F decides the sweep) · **Priority:
medium**

Commit is durable before projection, and a projection failure self-heals
only if the commit is redelivered. If no redelivery arrives, committed
data stays invisible forever, with nothing watching for it.

- Failure permitted: committed-but-invisible data after a crash between
  commit and projection, silent absent transport redelivery.

## UT-8 — No permission enforcement anywhere

**Status: PLANNED** (after the identity chain) · **Priority: gate for
shared deployment**

The group model exists (`grp` roots, `rw`/`r` grants) but no read or
write path checks it: anyone with wire or HTTP access writes and reads
everything. Property-scope evaluation, object ownership checks, and
membership are all future work.

- Revisit trigger: same as UT-1 — any second user.

## UT-9 — Link endpoints and declared constraints are never validated

**Status: OPEN — reader/validator concern, unscheduled** · **Priority:
medium-low**

A `lnk` may reference a nonexistent `type_id`, nonexistent endpoints, or
endpoint collections the link type forbids; the link type's
`assignable_properties` list is likewise unenforced. Read views tolerate
dangling references by design, so the damage is contained but real.

- Fix shape: semantic resolution at materialization (or a validation
  pass), sharing machinery with UT-3.

## UT-10 — Ingestion is create-only; source changes never propagate

**Status: DELIBERATE** · **Priority: revisit with synchronization
requirements**

Updates do not materialize (except `typ add_property`), property values
are set-once (conflicts are counted, never overwritten), and re-importing
changed upstream records surfaces as conflicts rather than updates. The
CouchDB import loop documents this boundary honestly.

- Why deliberate: value updates need the last-committed-wins semantics
  the orderable `commit_id` enables, and true synchronization needs the
  staged lifecycle; both are ratified planned work.
- Revisit trigger: the first requirement to reflect upstream changes
  rather than snapshot them.

## UT-11 — `required` property references are recorded but unenforced

**Status: DELIBERATE** (explicit DIRECTION.md scoping: no required
properties or defaults yet) · **Priority: revisit with validation work**

`typ + update add_property` may carry `required: true`; the reference
metadata is preserved verbatim and nothing checks it at assignment or
read time. Kept deliberately so enforcement can arrive later without
remodeling.

- Revisit trigger: alongside UT-3's validation decision.

---

*Related but tracked elsewhere (design decisions, not behavior gaps): the
inline-`properties`-bag endgame, `lnk` property alignment, plate
`format`/`rows`/`columns` instance-vs-type, the bulk-import selection
strategy, and the payload-archive question — see the drift note's open
decisions (8.6) and section 8.5 ledger.*
