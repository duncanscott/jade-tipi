# JDTP Specification

**Version:** 0.10.0-draft · **Date:** 2026-07-20 · **Status:** Draft for
director review

*Changes in 0.10.0: identifiers gain size limits (director ruling
2026-07-20) — `org` and `grp` at most 32 characters, the suffix (and the
transaction ID's client) at most 128, so a single-form ID never exceeds
235 characters, inside the 250-byte key limit of the most restrictive
portability target (Couchbase document keys; the manifesto's
usable-as-a-key-in-popular-databases promise, now quantified). An
overlong ID is rejected at the wire, never truncated — under the
transaction-UUID form the suffix is the within-transaction uniqueness
carrier, so silent truncation could collide distinct IDs. The deprecated
legacy composite assignment ID is exempt (two blocks; retired with the
legacy path) (§1.2, §2.1).*

*Changes in 0.9.0: identifiers unify on one leading shape (director
rulings 2026-07-19). Object IDs lead with the UUID — the segment order
becomes `<uuidv7>~<org>~<grp>~<collection>~<suffix>`, so identifiers sort
chronologically across organizations and groups — and transaction IDs
gain the literal `txn` collection segment
(`<uuidv7>~<org>~<grp>~txn~<client>`), so EVERY Jade-Tipi ID begins
`<uuidv7>~<org>~<grp>~<collection>` and the fourth segment names what the
ID identifies. The suffix additionally admits no leading, multiple, or
trailing underscores or dashes (`[a-z0-9]+([_-][a-z0-9]+)*`). A breaking
identifier change: previously materialized stores must be rebuilt (§1.2,
§2.1).*

*Changes in 0.8.0: the procedure root gains the top-level `inputs` map
(director-ratified 2026-07-08; TASK-072) — the canonical structured
record of a procedure's inputs, keyed by input object IDs, each value an
open object whose optional `task_id` back-references the delivering
task. Either tasks or other objects may be procedure inputs; a task's
carried object is an input independent of the task; there is no separate
`tasks` map. Like `output_input`, the map is hoisted onto the `prc` root
and schema-admitted only on `prc` payloads (§1.9).*

*Changes in 0.7.2: the object assignment history (TASK-061's `hst`
collection) becomes readable (TASK-065) — a resource read beside the
generic property-values route, same object-ID dereference rule,
returning applied assignments in message-UUID (chronological) order,
optionally narrowed to one property, paged (§3).*

*Changes in 0.7.1: object resource reads take only the object ID
(director-ratified 2026-07-07; TASK-064) — one generic route serves
property values for `ent`/`loc`/`prc`/`tsk`/`fil`, dereferencing the
collection from the ID's collection segment (the ID is the complete
address; malformed or unserved IDs are 404, never a guess). §3 records
the dereference rule and its relationship to §2.3.1's write-path
non-inference rule; the per-collection entity/location routes are
retired.*

*Changes in 0.7.0: **snapshot isolation** becomes Normative
(director-ratified 2026-07-06; TASK-063) — a transaction reads nothing
newer than its snapshot point. The backend mints a `snapshot_id`
(UUIDv7) at open-processing and materializes a committed transaction
only when no open transaction has an older snapshot (the
oldest-open-transaction watermark), so roots always hold the floor
state; open transactions carry a lease (durable auto-rollback on
expiry) so an abandoned open cannot stall materialization forever; every
terminal outcome nudges the worker, and a nudge now triggers a full
sweep pass. Per-reader overlay reads remain Planned (§2.4).*

*Changes in 0.6.1: the 0.6.0 orderability flag is answered by director
ruling (2026-07-06) — `commit_id` is a backend-minted **UUIDv7**,
generated the same way as transaction UUIDs, so commit IDs are orderable
and directly comparable with transaction IDs (§2.4; TASK-062).*

*Changes in 0.6.0: property values become updatable (director-ratified
2026-07-05) — the root keeps one current entry per property, newest
message wins (ordered by the assignment's message UUIDv7), and every
applied assignment is preserved in the new derived `hst` history
collection, with a `history: false` opt-out on type roots or
`property_refs` entries (§1.1, §2.3.1, §2.5; UT-10 narrowed
accordingly). Note for director review: ordering deliberately uses the
message UUIDv7, not `commit_id` — the current commit-ID generator's
output is **not** lexicographically orderable, contradicting earlier
prose; §2.4's "orderable commit_id" language should be revisited.*

*Changes in 0.5.2: the staged-payload question is decided (director
ruling 2026-07-05) — cleanly-applied staged payloads are deleted, not
archived, with an optional future output feed if wanted; §2.4 Planned and
the §5 open-decisions list updated.*

*Changes in 0.5.1: link references gain a warn-only validation layer
(UT-9 resolved) — `lnk + create` materialization resolves the type, the
endpoints, `allowed_*_collections`, and `assignable_properties`, warning
and counting per issue without ever blocking; enforcement stays an open
director decision. `value_schema` validation recorded as deferred by
director ruling (schemas follow real data).*

*Changes in 0.5.0: the first lifecycle slice flips from Planned to
Normative — commit fixes `message_count` on the header, and the
projection stamps each message row's terminal `apply_state` (first
outcome wins), making a committed transaction's application auditable
row by row. The `msg` staging split, cleanup, and overlay reads remain
Planned.*

*Changes in 0.4.3: HTTP JSON response bodies are snake_case (director
ruling 2026-07-05) — the read surface carries the same field conventions
as wire messages and stored root documents, applying the document-state
principle end to end. URLs remain lowercase/kebab-case.*

*Changes in 0.4.2: §3 query reads gain the paged location browse
(discovery over materialized `loc` roots).*

*Changes in 0.4.1: recorded the director-ratified document-state
principle — the JSON document structures are normative protocol surface
(not implementation detail, unlike Kafka/MongoDB); the complete
collection of root documents describes current system state, and
document-level interchange is an anticipated data-sharing route (intro,
§1.3).*

*Changes in 0.4.0: materialization is owned by a background worker (UT-7
resolved; director-ratified 2026-07-05) — commit handling only marks the
header and nudges; the worker projects committed transactions lacking the
`materialized_at` watermark and stamps it, and a periodic sweep guarantees
projection with no dependence on transport redelivery. Commit re-delivery
no longer triggers projection.*

*Changes in 0.3.3: late appends are guarded (UT-6 resolved) — a data
message appended after a transaction reaches a terminal state is stored
flagged `late_append: true` and excluded from the committed snapshot, so
it can never materialize on a commit re-delivery; before-open appends
remain allowed and deterministic.*

*Changes in 0.3.2: task-input and procedure-output collections are
explicitly unconstrained by the protocol (director ruling 2026-07-05) —
earlier §1.9 prose implied `ent`-only inputs and outputs; typically they
are `ent` or `fil`, and only per-deployment link-type
`allowed_*_collections` declarations may constrain them.*

*Changes in 0.3.1: rollback is durable (UT-2 resolved) — `txn + rollback`
terminally marks the header `rolled_back` with audit data; commit and
rollback are mutually exclusive, refusals never overwrite; §2.3, §2.4,
and §4 updated accordingly.*

*Changes in 0.3.0: §1.10 Files added as Normative — `fil` is a
wire-accepted, materialized collection for retrievable electronic assets,
implemented as a standard typed root; the file property set,
content-identity hoisting, and the dedup policy are deliberately deferred
director rulings, recorded in §1.10 and the open-decisions list.*

*Changes in 0.2.0: §1.9 Procedures and tasks implemented and flipped to
Normative — `prc`/`tsk` are wire-accepted, materialized collections; task
types carry `procedure_type_id`; the `prc` root hoists `output_input`
(schema-valid only on `prc` payloads, mirroring the `grp` permissions
escape). The `vdn`-supplied contribution-schema association stays Planned.*

*Changes in 0.1.1: identifier suffix charset tightened to `[a-z0-9_-]`
(dots removed by director ruling — they were never authorized); §1.9
Procedures and tasks added as Planned.*

JDTP (JSON Data Transparency Protocol) is a technology-agnostic protocol for
world-mergeable, provenance-preserving scientific metadata. This document is
the authoritative statement of the protocol as ratified through TASK-065 of
the reference implementation. It stands apart from any one database, queue,
or search product: the reference implementation currently uses Kafka and
MongoDB, but those are adapters, not the definition. What is **not** an
implementation detail is the JSON itself: the message envelope (§2.2) and
the root document contract (§1.3) are normative structures, whether the
documents are physically stored in one piece or virtually assembled. The
complete collection of root JSON documents describes the current state of
a repository, and sharing that final stage of documents — rather than
replaying any particular transport — is an anticipated route for moving
data between systems.

Relationship to other documents: [`docs/Jade-Tipi.md`](Jade-Tipi.md) is the
narrative manifesto (vision, motivation, worked stories); `DIRECTION.md`
records direction not yet ratified; the drift note
([`architecture/object-property-model-drift.md`](architecture/object-property-model-drift.md))
records the migration plan. Where the manifesto describes superseded
mechanics — the `timestamp~increment` identifier scheme, two-letter
collection segments, a transaction ID server — **this specification is
authoritative**.

**Conformance language.** *MUST*/*MUST NOT* mark requirements an
implementation has to satisfy; *SHOULD* marks strong recommendations. Every
section is tagged:

- **[Normative]** — implemented and enforced (or contractually relied on)
  today.
- **[Planned]** — ratified direction, not yet implemented; recorded so the
  specification and the migration plan cannot drift apart.

---

## 1. Data Model

### 1.1 Objects and collections [Normative]

A JDTP repository stores **objects**: logical JSON documents belonging to
exactly one collection. The peer domain collections are:

| Collection | Abbrev. | Contents |
|---|---|---|
| entity | `ent` | Things, real or conceptual (samples, organisms, libraries) |
| file | `fil` | Retrievable electronic assets: metadata about bytes stored elsewhere (§1.10) |
| property | `ppy` | Property definitions (and, later, property policy) |
| link | `lnk` | Relationships between objects |
| location | `loc` | Physical/addressable locations and containers |
| type | `typ` | Type definitions: entity types and link types |
| group | `grp` | Ownership groups and group-to-group permission grants |
| procedure | `prc` | Performed procedures: execution events turning inputs into outputs (§1.9) |
| task | `tsk` | Intentions to perform a procedure of a given type on a set of inputs (§1.9) |
| unit | `uni` | Measurement units *(wire-accepted; not yet materialized)* |
| validation | `vdn` | Validation rules *(wire-accepted; not yet materialized)* |
| user | `usr` | Local identity/audit records *(backend-internal today; not in the wire vocabulary)* |

Three collections are special:

- `txn` is the durable transaction record store, not a domain collection.
- `hst` is the derived property-assignment history (§2.3.1): one document
  per applied assignment, keyed by the assignment's message UUID. It is
  written only by materialization, never by wire messages, and never
  appears in the wire vocabulary.
- `msg` **[Planned]** is transient transaction-message staging; it never
  becomes a domain collection and never appears in the wire vocabulary.

Not every object is an entity: members of `loc`, `typ`, `grp`, `usr`, and
the rest are first-class objects with the same document contract. The type
hierarchy never changes an instance's collection — container instances,
including instances of container subtypes such as `plate_96_well`, are
`loc` records.

### 1.2 Object identifiers [Normative]

Every object ID is a world-unique text string:

```text
<uuidv7>~<org>~<grp>~<collection>~<suffix>
```

- The **leading** segment is a **UUID version 7** (RFC 9562): either the
  creating **transaction's** UUID (all roots in the transaction share it,
  and the client MUST keep suffixes unique within that transaction) or the
  creating **message's** UUID (uniqueness is automatic). Both forms are
  sanctioned. UUIDv7 supplies world-uniqueness plus chronological
  sortability with no ID server — and because it leads the ID, identifiers
  sort chronologically **across organizations and groups** (director
  ruling 2026-07-19; the transaction ID convention already led with its
  UUID).
- `org` and `grp` (second and third segments) identify the owning
  organization and group (`[a-z][a-z0-9_-]*` each). Organizations are
  responsible for issuing unique names beneath themselves; no central
  registry is required.
- The fourth segment is the collection abbreviation from §1.1 (three-letter
  forms; `usr` included).
- The suffix is a human-readable label over `[a-z0-9_-]` (lowercase
  letters, digits, underscore, hyphen; no dots) with **no leading,
  multiple, or trailing underscores or dashes** — structurally,
  `[a-z0-9]+([_-][a-z0-9]+)*` (director ruling 2026-07-19). It does not
  carry world-uniqueness (the UUID does); under the transaction-UUID
  form it IS the within-transaction disambiguator among the
  transaction's roots.
- **Size limits** (director ruling 2026-07-20): `org` and `grp` are at
  most **32** characters each; the suffix is at most **128**. With the
  36-character UUID, four separators, and the 3-character collection
  segment, a single-form ID never exceeds **235** characters — inside the
  250-byte key limit of the most restrictive popular-database portability
  target (Couchbase document keys), quantifying the manifesto's promise
  that IDs are usable directly as database keys. The charset is pure
  ASCII, so characters equal bytes. **An overlong ID is rejected at the
  wire, never truncated**: under the transaction-UUID form the suffix
  carries within-transaction uniqueness, so silent truncation could
  collide two distinct IDs. Composing a unique suffix within the limit
  is the client's responsibility, exactly as the charset rules are. The
  deprecated legacy composite assignment ID (two blocks) is exempt from
  the single-form total and retires with the legacy path.

**One leading shape for every ID.** Transaction IDs follow the same
convention with collection `txn` and the client in the suffix position
(`<uuidv7>~<org>~<grp>~txn~<client>`, §2.1). Every Jade-Tipi identifier —
object or transaction — therefore begins
`<uuidv7>~<org>~<grp>~<collection>`, and the fourth segment always says
what kind of thing the ID names. An object created with the
transaction-UUID form shares its first three segments verbatim with its
creating transaction's ID.

**Sanctioned exceptions.**

- The literal `genesis` may appear in the UUID position only in the
  reserved bootstrap user ID `genesis~<org>~<grp>~usr~jdtp-admin`, which
  must be constructible before any transaction exists.
- A **composite** ID — two conforming IDs joined
  (`<object_id>~<property_id>`) — is tolerated on deprecated legacy
  assignment payloads (§3.3.5), where it is ignored.

**Enforcement.** The wire schema rejects any submitted top-level `data.id`
that does not match this convention; the materializer additionally logs a
warning for nonconforming create IDs as defense in depth for non-wire
writers. Nested `id` keys inside property bags are not constrained.

### 1.3 Root document contract [Normative]

The document structure below is protocol surface, not an implementation
convenience: whether a document is physically stored in one piece or
virtually assembled, its JSON shape is the contract, and the complete
collection of root documents describes the current state of a repository
(director-ratified 2026-07-05). Document-level interchange — sharing this
final stage of the documents that constitute a system — is an anticipated
data-sharing route between JDTP systems.

One logical object is normally stored as one **root document**:

```json
{
  "_id":  "<object id>",
  "id":   "<object id>",
  "collection": "<abbrev>",
  "type_id": "<typ id or null>",
  "properties": { "...": "first-pass inline facts (see §1.5)" },
  "links": {},
  "property_values": {
    "<ppy id>": {
      "value": { "text": "..." },
      "txn_id": "...", "commit_id": "...", "msg_uuid": "...",
      "applied_at": "..."
    }
  },
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "<object id>",
    "provenance": {
      "txn_id": "...", "commit_id": "...", "msg_uuid": "...",
      "collection": "...", "action": "create",
      "committed_at": "...", "materialized_at": "..."
    }
  }
}
```

- `_head` is the reserved implementation-metadata header; user/domain data
  MUST NOT live under it. `provenance` ties the document to the transaction
  and message that produced it. Direct (non-transactional) writes carry a
  sentinel in `txn_id`/`commit_id`: `admin~<uuid>` for the local admin
  path, `genesis~jdtp-admin` for the bootstrap user.
- `links` is the denormalized link map; the current materializer writes
  `{}` and endpoint projection is **[Planned]**.
- Rows materialized before this contract may carry a legacy top-level
  `_jt_provenance`; readers tolerate it narrowly.
- **[Planned]** Extension pages (property/link overflow documents, pending
  pages, page indexes) for objects that outgrow one root document.

### 1.4 Types and inheritance [Normative]

Type definitions live in `typ`. An **entity type** declares which
properties may be assigned to objects of that type as **property
references** keyed by `ppy` ID under `properties.property_refs`; references
record wire metadata verbatim (currently `required`, when supplied) and the
implementation does **not** yet enforce required-ness or defaults.

A type may extend a parent type by declaring `parent_type_id` (stored under
`properties.parent_type_id`). Inheritance is **single-parent** and minimal:
no property overriding, no shadowing, no multiple inheritance. **The
subtype inherits all the properties of the parent type**: a property is
assignable to an object when it is registered on the object's own type or
on any ancestor reached through the `parent_type_id` chain. Resolution is
bounded (depth 10) and cycle-safe; on the write path an unresolvable chain
fails closed (the assignment is not applied), while the read path surfaces
the partial chain with `chain_complete: false` so a broken hierarchy is
inspectable.

**Link types** are `typ` records with `kind: "link_type"`, declaring
endpoint roles, directional labels, and allowed endpoint collections
(§1.6). A link type SHOULD exist before links of that type are submitted;
this is not yet enforced.

### 1.5 Properties and values [Normative]

Property **definitions** are `ppy` roots carrying `kind: "definition"`,
`name`, and an opaque `value_schema` (a JSON Schema for the value object).
The implementation records `value_schema` verbatim and does **not** yet
validate submitted values against it.

Property **values** are always JSON objects, wrapping scalars:
`{ "text": "..." }`, `{ "number": 10, "unit_id": "..." }`,
`{ "boolean": true }`.

Materialized values live **on the object root**, keyed by `ppy` ID, in the
`property_values` map. Each entry carries the verbatim `value` plus enough
transaction provenance to answer *who wrote this value* through the durable
transaction record: `txn_id`, `commit_id` (required), `msg_uuid` (the
idempotency key and pointer into message history), and `applied_at`.

Value semantics are **updatable, newest message wins**: the root keeps
exactly one current entry per property, replaced when a newer assignment
arrives, and every applied assignment is preserved in the `hst` history
collection — see §2.3.1 for the full rules (ordering, history opt-out,
duplicates and conflicts).

The first-pass inline `properties` bag on root documents remains as a
transitional representation; by convention since the typed container work
it carries **source-system traceability facts** (`source_system`,
`source_id`, `source_kind`, …) rather than domain values. Its endgame
(retire, or formally reserve for source facts) is an open director
decision.

### 1.6 Links [Normative]

A link is a `lnk` root with `type_id` (the link type), `left` and `right`
endpoint object IDs, and instance `properties` — relationship-specific
facts such as position. Relationship semantics (roles, labels, allowed
collections) live on the link type, never repeated per instance, and
parentage/containment MUST NOT be duplicated as canonical fields on the
endpoint objects (no `parent_location_id`).

The first canonical link type is `contents` (`left_role: container`,
`right_role: content`, labels `contains`/`contained_by`, allowed left
`[loc]`, allowed right `[loc, ent]`). Positions on containment links use a
kind-tagged vocabulary keyed by the parent container kind:
`freezer_slot` (`label`, `slot`), `bin_slot` and `plate_well` (`label`,
`row`, `column`), `tube_position` (`label`, `row`, `column`).

Not yet enforced: semantic endpoint resolution (`type_id`, `left`, `right`,
allowed collections) and the link type's `assignable_properties` list.
Aligning link instance properties with typed `property_values` (or
documenting a permanent exception) is an open director decision.

### 1.7 Groups and permissions [Normative shape; enforcement Planned]

`grp` roots are first-class objects carrying `name`, optional
`description`, and a `permissions` map keyed by other groups' world-unique
IDs with values exactly `"rw"` or `"r"`. Ownership-group access is implicit
(no self-entry). Groups are **not** collections of user identifiers;
membership becomes local JDTP state (user properties, membership links, or
a projection) — mechanism deferred. Objects and property assignments are
owned by groups, so effective access will eventually be evaluated at
property scope. No read/write permission enforcement exists yet on any
path.

### 1.8 Users and writer audit

**[Normative]** `usr` records are local identity/audit objects — never an
authentication provider, never storing passwords or tokens. One reserved
bootstrap user exists as a genesis storage fact so first transactions can
be attributed without a user/transaction creation cycle:
`<org>~<grp>~genesis~usr~jdtp-admin`, created by an idempotent startup
ensure, marked `kind: "system"`, `status: "reserved"`,
`identity_provenance.source: "bootstrap"`, carrying **no** external
identity keys so identity resolution can never match it. It is not a login
account.

**[Planned]** Every durable transaction record carries one `writer`
sub-document containing `user_id` (the join reference to a local `usr`
record) plus an immutable transaction-time identity snapshot (ORCID iD,
OIDC issuer/subject, display name, client, authentication source). Ordinary
`usr` records are projected from authenticated external identities.
*Current state:* the message envelope's `txn.user` (an ORCID-style string,
verified client-side) is the only writer identity on the wire, and the
current implementation does not persist it to the durable transaction
record — writer identity today survives only as long as the transport
retains the message. Closing that gap is the ratified writer-persistence
work. (Known quirk for that work: an envelope serializing `"user": null`
fails schema validation; all real clients send a user.)

### 1.9 Procedures and tasks [Normative; contribution schemas Planned]

Two further collections realize the manifesto's process-tracing extension
(director-ratified 2026-07-04; implemented in TASK-048):

- A **procedure** (`prc`) is a *performed* procedure: the execution event
  that turns inputs into outputs. Its `type_id` references a procedure
  type in `typ`.
- A **task** (`tsk`) is the *intention* to perform a procedure of a given
  type on a set of inputs. Tasks have their own task types; type
  definitions are never overloaded across the two collections. A task
  type carries its associated procedure type as a property of the task
  type (`properties.procedure_type_id`), so task instances need no
  per-instance procedure pointer. Task and procedure type declarations
  carry `kind: "task_type"` / `kind: "procedure_type"` discriminators,
  mirroring `link_type`.
- Task inputs and procedure outputs may be objects of any collection —
  typically `ent` or `fil`. The protocol does not constrain input or
  output collections; a deployment may constrain them through link-type
  `allowed_*_collections` declarations (§1.6). The input relationships,
  the task-fulfilled-by-procedure relationship (recorded on completion),
  and each output's produced-by relationship are **canonical `lnk`
  records**. On-root pointers arrive with the planned `links` projection.
- The procedure root carries a top-level `output_input` map — the
  canonical execution provenance: keys are output object IDs; each value
  maps contributing input object IDs to an open **contribution object**
  (e.g. `{ "volume": 12.5 }` for pooling). Contribution weights live only
  here. The map is hoisted onto the `prc` root top level, parallel to
  `lnk`'s `left`/`right`, and excluded from the inline `properties` bag.
  Because its keys are object IDs, the wire schema admits `output_input`
  only on `prc` payloads (each contribution MUST be an object), mirroring
  the `grp` permissions escape from the snake_case rule.
- The procedure root also carries a top-level `inputs` map — the
  canonical structured record of the procedure's inputs (director-ratified
  2026-07-08; implemented in TASK-072): keys are input object IDs; each
  value is an open object whose optional `task_id` references the task
  that delivered that input to the procedure. **Either tasks or other
  objects may be inputs to a procedure.** When a task carried an object
  into the procedure, the object is modeled as an input *independent of
  the task*, back-referencing it via `task_id`; a directly supplied input
  omits `task_id`. There is no separate `tasks` map — task relationships
  ride the `fulfills` links and these `task_id` back-references. Like
  `output_input`, the map is hoisted onto the `prc` root top level,
  excluded from the inline `properties` bag, and admitted by the wire
  schema only on `prc` payloads. The maps and the `lnk` records are
  complementary, not a source-of-truth duplication: the maps are
  execution-owned aggregate state; the links are the traversable graph
  edges.
- **[Planned]** The schema for contribution objects will be supplied by a
  `vdn` record associated with the procedure type once `vdn` materializes
  (UT-4).

### 1.10 Files [Normative]

A **file** (`fil`) is a retrievable electronic asset — including assets
that are no longer retrievable (deleted) or only retrievable locally.
Files are the boundary objects between metadata and data: a `fil` record
is metadata about bytes stored elsewhere. Aggregates of files (datasets,
run folders) remain `ent` records with membership links to their `fil`
members.

A `fil` record is a standard typed root (§1.3): file types are ordinary
`typ` records with ordinary inheritance (§1.4), and file facts are
ordinary object-targeted property values (§2.3.1). A retrieval URL is
one candidate property, but not every file has a URL — some files have a
retrieval protocol that is not a URL.

By director ruling, the file property set is left to emerge from real
imports. Content-identity hoisting (checksums, sizes, locators), a
file-specific payload schema, and the deduplication policy for identical
bytes are **deliberately unspecified** — open decisions, not ratified
direction — and are revisited when file imports reveal their shape.

Files participate in the provenance model (§1.9) unchanged: link types
admit `fil` endpoints through their ordinary `allowed_*_collections`
declarations (a file is typically the output of a `prc` via
`produced_by` and an input to a `tsk` via `task_input`).

---

## 2. Transactions and Messages

### 2.1 Transaction identity [Normative]

A transaction is identified by
`<uuidv7>~<org>~<grp>~txn~<client>`. The UUIDv7 provides uniqueness and
chronological sortability; `org`/`grp` name the writing group; the literal
`txn` collection segment marks the ID as a transaction ID — giving every
Jade-Tipi ID the same leading shape,
`<uuidv7>~<org>~<grp>~<collection>~<tail>` (§1.2) — and `client` (in the
suffix position) names the submitting application. The §1.2 size limits
apply: `org`/`grp` at most 32 characters and `client`, sitting in the
suffix position, at most 128 — so a transaction ID is likewise bounded by
235 characters. Transactions are opened, then carry data messages, then
commit (or roll back).

### 2.2 Message envelope [Normative]

Every submitted message is one JSON document:

```json
{
  "txn": {
    "uuid": "<transaction uuidv7>",
    "group": { "org": "...", "grp": "..." },
    "client": "...",
    "user": "<verified external identity, e.g. ORCID iD>"
  },
  "uuid": "<message uuidv7>",
  "collection": "<abbrev>",
  "action": "<action>",
  "data": { }
}
```

Schema-enforced constraints:

- `txn`, `uuid`, `collection`, `action` are required; both UUIDs MUST be
  version 7.
- Action/collection compatibility: `txn` takes only
  `open|commit|rollback`; the domain collections take only
  `create|update|delete`.
- `data` property names are `snake_case`, recursively, with two
  exceptions: the `grp` `permissions` map is keyed by world-unique group
  IDs, and the `prc` `output_input` map is keyed by object IDs (its
  contribution values MUST be objects).
- A top-level `data.id`, when present, MUST match the object identifier
  convention (§1.2).
- Collection MUST be stated explicitly; it is never inferred from payload
  shape. Payloads are flat, human-readable JSON — no nested operation DSL.

### 2.3 Message vocabulary [Normative]

Supported message forms (everything else is accepted onto the transaction
record but skipped as unsupported at materialization, without error):

| Message | Effect |
|---|---|
| `txn + open` | Opens the transaction (idempotent re-delivery confirmed). |
| `txn + commit` | Commits: the backend mints a **UUIDv7** `commit_id` — orderable, and comparable with transaction UUIDs as points on one timeline — and marks the header for materialization. |
| `txn + rollback` | Durably marks the header `rolled_back` (terminal), keeping the message's `data` as `rollback_data` — the audit fact. Re-delivery is idempotent; rollback-after-commit is refused; rollback before open is an error. |
| `loc/ent/grp/tsk/fil + create` | Root document per §1.3; `data.type_id` surfaces as the root `type_id` (unresolved references are not checked). |
| `prc + create` | Root document per §1.3 whose optional top-level `output_input` and `inputs` maps are hoisted onto the root (§1.9), parallel to `lnk` endpoints. |
| `typ + create` | Entity type (optionally with `parent_type_id`), or a declaration with a `kind` discriminator: `link_type`, `task_type` (carrying `procedure_type_id`), `procedure_type`. |
| `typ + update`, `operation: "add_property"` | Registers `data.property_id` under the target type's `properties.property_refs` (verbatim metadata; no `ppy` resolution). |
| `ppy + create`, `kind: "definition"` | Property definition root. |
| `ppy + create`, `kind: "assignment"` | Object-targeted property value (§2.3.1). |
| `lnk + create` | Link instance with top-level `left`/`right`. |

#### 2.3.1 Property assignment [Normative]

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "assignment",
    "object_collection": "loc",
    "object_id": "<target object id>",
    "property_id": "<ppy id>",
    "value": { "text": "..." }
  }
}
```

- `object_collection` (currently `ent`, `loc`, `prc`, `tsk`, or `fil`)
  is explicit; the implementation MUST NOT infer a collection by parsing
  `object_id`. (A write-path rule: the message is self-describing and
  inference must not sneak in as a fallback. Resource READS, whose only
  input is the ID, dereference by the ID's collection segment — §3.)
- `data.id` is not required and is ignored.
- **Deprecated alias:** a payload carrying only `entity_id` resolves as
  (`ent`, `entity_id`) with a deprecation warning; any legacy composite
  `data.id` on such payloads is ignored. Standalone assignment-root
  documents are no longer written (historical rows remain readable data
  only).
- The registration gate applies §1.4: target root must exist
  (`skippedMissingTarget`), must carry a `type_id` whose type — or an
  ancestor — registers the property (`skippedUnregisteredProperty`);
  malformed payloads are `skippedInvalid`. Gate outcomes are counted, never
  errors.
- **Values are updatable, and nothing is destroyed** (director-ratified
  2026-07-05). The root carries exactly one CURRENT entry per property;
  an assignment newer than the current entry replaces it, ordered by the
  assignment's **message UUIDv7** (time-ordered and lexicographically
  sortable by construction). Every applied assignment is preserved in the
  **`hst`** history collection — `_id` is the assignment's message UUID
  (idempotent by construction), with `object_id`, `object_collection`,
  `property_id`, `value`, and full provenance, indexed by
  `(object_id, property_id, msg_uuid)` for chronological retrieval. An
  assignment older than current lands in `hst` without displacing
  current (`applied_historical`). A same-message redelivery is an
  idempotent duplicate; a same-message payload mismatch remains a
  conflict and changes nothing.
- **History opt-out** (`history: false`): declared on a type root (the
  whole object) or on a `property_refs` entry (one property), resolved
  along the §1.4 registration walk — the property-level declaration is
  most specific, then the nearest object-level declaration at or below
  the registering type, then the default (enabled). Opted-out
  assignments still update the current value; they write no history
  document.

### 2.4 Transaction lifecycle

**[Normative — current]** Submitted messages are appended to the durable
transaction store as they arrive (header records and message records). The
header has one non-terminal state (`open`) and two mutually exclusive
terminal states: `committed` and `rolled_back`. A data message appended
**after** the header reached a terminal state is stored — submitted facts
are never discarded — but flagged `late_append: true` and excluded from
the committed snapshot, so it can never materialize on any commit
re-delivery. Appends before open (no header yet) remain allowed and
unflagged; they materialize at the explicit commit like any other row.
Commit durably marks the header with the `commit_id` **before** any
materialization. The `commit_id` is a backend-minted **UUIDv7** — the
same construction as transaction UUIDs (§1.2) — so commit IDs are
orderable among themselves and comparable with transaction UUIDs: the
canonical comparison is between UUIDv7 values (a transaction ID's
leading UUID segment against a commit ID), placing every open and every
commit on one timeline. This is the primitive **snapshot isolation**
builds on.

**Snapshot isolation [Normative]** (director-ratified 2026-07-06;
TASK-063). A transaction sees the world as of its birth: it cannot read
any entity created or property value set by a commit whose `commit_id`
is newer than the transaction's **snapshot point**; any transaction
newer than a commit may read that commit's effects. The snapshot point
is the header's `snapshot_id` — a UUIDv7 minted by the *backend* when
the open message is processed, from the same generator as commit IDs.
(The transaction's own UUID remains its identity, but it is minted by
the client; because the single ingest partition serializes opens and
commits through one consumer, backend-minted `snapshot_id` and
`commit_id` values order totally and correctly with no trust in client
clocks — an open processed after a commit always receives a larger ID.
Headers predating `snapshot_id` fall back to the transaction UUID.)

The rule is enforced structurally by the **materialization watermark**:
old values must stay readable while an open transaction is entitled to
them, so the worker materializes a committed transaction only when no
open transaction has a `snapshot_id` older than its `commit_id` —
equivalently, only commits below the minimum open `snapshot_id` (no
bound when nothing is open; a legacy non-UUID `commit_id` predates
every live open and is always eligible). Root documents therefore hold
the *floor state* — nothing any open transaction may not see — and the
read surface serves every open transaction a consistent snapshot with
no per-query filtering. Transactions newer than a blocked commit see
bounded staleness until the watermark advances; the per-reader overlay
(committed-but-unmaterialized transactions with `commit_id` older than
the reader's `snapshot_id`) remains **[Planned]**.

Liveness: every terminal outcome — commit, rollback, or lease expiry —
advances the watermark and nudges the worker. Open transactions carry a
**lease** (`jadetipi.transaction.lease`, default PT1H): the sweep
durably rolls back opens older than the lease through the normal
guarded rollback path, recording the expiry as `rollback_data` (audit),
so an abandoned open cannot hold the watermark back forever; a late
commit then meets the ordinary refused-after-rollback semantics.

Projection is owned by a **background
worker**, not
the commit path: terminal handling nudges the worker (a best-effort
in-process signal — a nudge triggers a full sweep pass, since any
terminal outcome can release transactions other than its own) and
returns, so ingest is never blocked by projection cost; the worker
materializes watermark-eligible committed transactions whose header
lacks the `materialized_at` stamp, projecting supported messages in
message-UUID order and stamping the header when a pass completes. A
periodic sweep over committed-but-unwatermarked headers — plus one sweep
at startup — guarantees every committed transaction is eventually
projected once eligible, with no dependence on transport redelivery. A
projection
failure never un-commits: the header stays unstamped and the next
sweep retries; all projections are idempotent (§2.5). Commit re-delivery
does not trigger projection.
Commit also fixes the committed set's size on the header as
`message_count` — the transaction's non-late message rows at commit time —
and the projection stamps each row's terminal `apply_state` (`applied`,
`duplicate`, `conflict`, a `skipped_*` reason, or `applied_historical` —
an assignment preserved in history without displacing a newer current
value, §2.3.1) with `apply_state_at`,
guarded so the **first** terminal outcome wins: an idempotent re-run,
whose repeats naturally resolve `duplicate`, cannot overwrite the original
truth. A mismatch between `message_count` and the committed snapshot's
size logs a warning (they agree by construction; a difference signals
tampering or a bug).
Rollback durably marks the header `rolled_back` with `rolled_back_at` and
`rollback_data`; a commit arriving after a rollback is **refused** (no
`commit_id`, no materialization), as is a rollback arriving after a
commit — terminal states are never overwritten, and both transitions are
additionally guarded on the `open` state at write time. Message rows
appended before a rollback remain stored as audit; the
committed-visibility gate keeps them from materializing.

**[Planned]** The remaining ratified lifecycle work separates durable
facts from staging: messages stage in transient `msg`; the header reaches
an `applied` watermark only when every staged message has a terminal
outcome (with today's synchronous projection, `materialized_at` plays
that role); cleanup **deletes** cleanly-applied staged messages **after**
the watermark is durable — no archiving (director ruling 2026-07-05;
applied payloads may optionally be emitted to a Kafka topic or other
output queue if a feed is ever wanted). Skipped/conflicting payloads are
retained as quarantine. Readers then overlay committed-but-unapplied
messages over root documents; read-your-own-open-transaction support is
deferred.

### 2.5 Idempotency and duplicates [Normative]

- **Message append:** a re-delivered message with an identical payload is
  an idempotent duplicate; a differing payload under the same
  transaction+message identity is a conflict and is rejected.
- **Root creation:** insert-only; an existing document with an identical
  payload (ignoring `_head.provenance.materialized_at`) is an idempotent
  duplicate; a differing payload — including differing provenance — is a
  conflict and is **never overwritten**.
- **Property registration:** the same matching-vs-conflicting rule.
- **Property values:** duplicate/conflict identity is the assignment
  *message* — a redelivery of the same message is an idempotent duplicate
  (its history write is idempotent by `_id`); a differing payload under
  the same message identity is a conflict. A *different* message
  assigning the same property is never a conflict: newer-than-current
  replaces the current entry, older-than-current is preserved in `hst`
  without touching current (§2.3.1).
- Consequence (re-import boundary): replaying the *same* transaction is
  idempotent end to end; a *new* transaction re-submitting the same root
  IDs surfaces as conflicts (provenance differs) and changes nothing —
  root creation stays create-only. Property assignments in a new
  transaction, however, DO propagate: re-importing an upstream record
  updates its property values (newest message wins) while `hst` retains
  the full assignment trail. Synchronization of upstream *structural*
  changes (new roots aside) remains future work.

---

## 3. Read Models [Normative]

Read views are projections over materialized roots; they perform no writes
and add no hidden semantics. Two route styles are deliberate:

- **Resource reads** require the subject root and answer 404 when it is
  missing: object property values (`ent`, `loc`, `prc`, `tsk`, and `fil`
  via ONE generic route and contract), object assignment history (the
  same collections, over `hst`), effective type properties, resolved
  location contents.
- **Query reads** answer 200 with empty results and cannot prove a
  subject exists: flat contents by container/content, the plate-shaped
  grid view, and the paged location browse (discovery over materialized
  `loc` roots; summaries only — depth belongs to the resource reads).

**Object-ID dereference [Normative]** (director-ratified 2026-07-07;
TASK-064). An object ID is the complete address: object resource reads
take only the ID, and the implementation dereferences the collection
from the ID's fourth segment (§1.2 makes that segment normative and the
wire schema enforces the five-segment shape). The segment is validated
against the collection vocabulary; a malformed ID or an unserved
collection is 404, never a guess. Stating the collection in the route as
well would say it twice and create a mismatch case to adjudicate. This
is deliberately the READ-path counterpart of §2.3.1's write-path rule:
assignment *messages* carry `object_collection` explicitly and the
materializer MUST NOT fall back to parsing — on the wire the payload is
self-describing; at the read surface the ID is the whole input, and
segment dereference is the sanctioned mechanism.

Contracts of note:

- **Object property values:** the root's identity, `type_id`, the inline
  `properties` bag (clearly separated), and `property_values` keyed by `ppy`
  ID with value, provenance, and the resolved property name (null when the
  definition is missing). Stale or malformed stored entries are tolerated,
  never fatal.
- **Object assignment history:** every applied assignment for the
  subject (optionally narrowed to one property) from `hst`, in
  message-UUID (chronological) order, paged with the effective paging
  echoed and the filtered total. Each entry carries `msg_uuid` (its
  identity and ordering key), the property with its resolved name (null
  when the definition is missing), the `value` verbatim, and full
  transaction provenance. A root with no history — including a
  `history: false` opt-out — is an empty page, never an error.
- **Effective type properties:** the union of property references across
  the subject type and its ancestors (most-derived registration wins),
  each attributed to the registering type, with the ordered type chain and
  `chain_complete: false` on a broken chain.
- **Contents views** resolve the `contents` link type by its declared
  `kind`/`name`, not by ID, so independently minted `contents` declarations
  coexist.

---

## 4. Enforcement Summary

| Rule | Enforced by | Status |
|---|---|---|
| Envelope shape, UUIDv7s, action/collection matrix, snake_case payloads | wire schema | Normative |
| Object identifier convention on `data.id` | wire schema (+ materializer warning) | Normative |
| `prc` `output_input` shape (ID-keyed map; object contributions; `prc`-only) | wire schema | Normative |
| `prc` `inputs` shape (ID-keyed map; optional `task_id` back-reference; `prc`-only) | wire schema | Normative |
| Type-registration gate for property values (inheritance-aware) | materializer | Normative |
| Duplicate/conflict semantics (§2.5) | store + materializer | Normative |
| `value_schema` validation of submitted values | — | Deferred by director ruling (2026-07-05): schemas follow real data; not required before bulk import |
| `required` property references | — | Deliberately not implemented |
| Link endpoint/collection resolution and `assignable_properties` | materializer warning (warn-only; one count per issue) | Normative warn layer; enforcement is an open director decision |
| Group permissions (read/write, property scope) | — | Planned |
| Writer persistence (`writer.user_id` + snapshot) | — | Planned |

The gaps in this table — and the sharper one (silently ignored
`uni`/`vdn` submissions) — are tracked as director-ratified TODO items
with stable IDs in
[`uncomfortable-truths.md`](uncomfortable-truths.md).

---

## 5. Planned Extensions

Ratified direction, in the migration plan's order: local `usr` identity
resolution and durable writer persistence; the `txn`/`msg` split with the
applied watermark and guarded cleanup; overlay reads; value updates;
permission enforcement; retirement or formal reservation of the inline
`properties` bag; link-property alignment; materialization of `uni` and
`vdn` (including the `vdn`-supplied contribution schemas of §1.9); an HTTP
submission adapter over the same message vocabulary;
extension pages for oversized objects; and derived-capability seams
(`SearchProvider`, `GraphProvider`, `VectorProvider`, `ArchiveProvider`)
that project from — and never replace — the canonical transaction and
repository state.

**Open director decisions** (tracked in the drift note): the inline-bag
endgame; link alignment; whether plate `format`/`rows`/`columns` are
instance values or type facts; the bulk-import selection strategy; the
null-user envelope handling; file content-identity hoisting and the file
dedup policy (§1.10). The payload-archive question is decided:
cleanly-applied staged payloads are deleted, not archived (§2.4).

---

*Reference implementation notes (non-normative): the current stack is
Spring Boot/WebFlux + Kafka + MongoDB; canonical example messages 01–27
live in the DTO library and are exercised against the wire schema in CI;
the typed container review seed and the live CouchDB import loop
demonstrate the protocol against real laboratory records.*
