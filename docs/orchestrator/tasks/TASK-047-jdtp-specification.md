# TASK-047 - JDTP specification document

ID: TASK-047
TYPE: documentation
ARTIFACT_INTENT: specification
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-04)
SOURCE_TASK:
  - TASK-038
  - TASK-040
  - TASK-044
  - TASK-045
  - TASK-046
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-047-jdtp-specification.md
  - docs/jdtp-specification.md
  - docs/README.md
  - docs/ROADMAP.md
  - README.md
REQUIRED_CAPABILITIES:
  - architecture-prework

GOAL:
Deliver roadmap Track 2: extract the accepted root-document contract,
transaction envelope, collection vocabulary, identifier rules, type system,
and read-model behavior into one versioned, implementation-independent
specification document that can stand apart from the reference
implementation — so the protocol's authoritative statement stops living
scattered across a partially historical manifesto, task files, and
implementation notes (the failure mode behind the identifier drift the
director corrected in TASK-044..046).

CONTEXT:
- Sources: the manifesto (`docs/Jade-Tipi.md`, narrative), `DIRECTION.md`,
  `docs/architecture/kafka-transaction-message-vocabulary.md`, the ratified
  drift-note section 8 contracts, `message.schema.json`, and the TASK-039
  through TASK-046 outcomes.
- Every statement is tagged **Normative** (implemented and enforced or
  contractually relied on today) or **Planned** (ratified direction not yet
  implemented: writer persistence, `msg` staging, applied watermark and
  cleanup, overlay reads, value updates, permission enforcement, extension
  pages, provider seams).
- The manifesto keeps its narrative role; where it describes superseded
  mechanics (the `timestamp~increment` identifier scheme, two-letter
  collection segments, the transaction ID server), the specification is
  authoritative and says so.

ACCEPTANCE_CRITERIA:
- `docs/jdtp-specification.md` exists with a version header
  (0.1.0-draft), a conformance-language note, and numbered sections
  covering: collections and objects; object identifiers; the root-document
  contract; types and inheritance; properties and values; links; groups
  and permissions; users and writer audit; transaction identity, message
  envelope, and per-collection message vocabulary; the transaction
  lifecycle (current and planned); idempotency and duplicate semantics;
  read-model contracts; an enforcement summary; and planned extensions.
- Statements match the implementation as of TASK-046 exactly (including
  deliberately surprising facts: rollback is not persisted; `required`
  metadata on property references is recorded but not enforced; `uni` and
  `vdn` are wire-accepted but not materialized).
- `README.md`, `docs/README.md`, and `docs/ROADMAP.md` (Track 2 current
  state) point at the specification.

OUT_OF_SCOPE:
- No manifesto rewrite; no code, schema, or example changes.
- No new protocol decisions — the spec records ratified state only; open
  questions are listed as such.

VERIFICATION:
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- `docs/jdtp-specification.md` written at version 0.1.0-draft: five parts
  (data model; transactions and messages; read models; enforcement
  summary; planned extensions) with per-section Normative/Planned tags and
  a closing open-decisions list mirroring the drift-note tracker.
- Deliberately-surprising current behaviors are stated plainly rather than
  idealized: rollback acknowledged but not persisted; `required` reference
  metadata recorded but unenforced; `uni`/`vdn` wire-accepted but not
  materialized; envelope `txn.user` not persisted to the durable record
  (the writer-persistence gap, with the null-user serialization quirk);
  no state guard on message append; no background re-drive of committed
  transactions.
- The specification claims authority over superseded manifesto mechanics
  (timestamp~increment IDs, two-letter segments, the transaction ID
  server) while leaving the manifesto's narrative untouched.
- Pointers wired: root `README.md` (objectives paragraph and learning
  path), `docs/README.md` (overview cross-reference), and
  `docs/ROADMAP.md` Track 2 (current state plus a keep-current next
  slice).
- Director-requested addendum (2026-07-04): the specification's
  unflattering truths are broken out as a TODO ledger with stable IDs in
  `docs/uncomfortable-truths.md` (UT-1..UT-11: status
  OPEN/PLANNED/DELIBERATE, failure permitted, disposition, revisit
  triggers; entries are resolved-in-place, never deleted). The spec's
  enforcement summary links to it. Director ratified that these issues
  should be addressed; the manifesto discrepancy is deliberately left in
  place as a record of how the ideas evolved on contact with the real
  world.
- Verification: docs-only change; `git diff --check` clean.
