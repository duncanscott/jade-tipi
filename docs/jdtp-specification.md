# JDTP Specification

**Version:** 0.1.1-draft · **Date:** 2026-07-04 · **Status:** Draft for
director review

*Changes in 0.1.1: identifier suffix charset tightened to `[a-z0-9_-]`
(dots removed by director ruling — they were never authorized); §1.9
Procedures and tasks added as Planned.*

JDTP (JSON Data Transparency Protocol) is a technology-agnostic protocol for
world-mergeable, provenance-preserving scientific metadata. This document is
the authoritative statement of the protocol as ratified through TASK-046 of
the reference implementation. It stands apart from any one database, queue,
or search product: the reference implementation currently uses Kafka and
MongoDB, but those are adapters, not the definition.

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
| property | `ppy` | Property definitions (and, later, property policy) |
| link | `lnk` | Relationships between objects |
| location | `loc` | Physical/addressable locations and containers |
| type | `typ` | Type definitions: entity types and link types |
| group | `grp` | Ownership groups and group-to-group permission grants |
| procedure | `prc` | Performed procedures: execution events turning inputs into outputs *(planned; see §1.9)* |
| task | `tsk` | Intentions to perform a procedure of a given type on a set of inputs *(planned; see §1.9)* |
| unit | `uni` | Measurement units *(wire-accepted; not yet materialized)* |
| validation | `vdn` | Validation rules *(wire-accepted; not yet materialized)* |
| user | `usr` | Local identity/audit records *(backend-internal today; not in the wire vocabulary)* |

Two collections are special:

- `txn` is the durable transaction record store, not a domain collection.
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
<org>~<grp>~<uuidv7>~<collection>~<suffix>
```

- `org` and `grp` identify the owning organization and group
  (`[a-z][a-z0-9_-]*` each). Organizations are responsible for issuing
  unique names beneath themselves; no central registry is required.
- The third segment is a **UUID version 7** (RFC 9562): either the creating
  **transaction's** UUID (all roots in the transaction share it, and the
  client MUST keep suffixes unique within that transaction) or the creating
  **message's** UUID (uniqueness is automatic). Both forms are sanctioned.
  UUIDv7 supplies world-uniqueness plus chronological sortability with no
  ID server.
- The fourth segment is the collection abbreviation from §1.1 (three-letter
  forms; `usr` included).
- The suffix is a human-readable label (`[a-z0-9_-]+` — lowercase letters,
  digits, underscore, hyphen; no dots); it is **not** the uniqueness
  carrier.

**Sanctioned exceptions.**

- The literal `genesis` may appear in the UUID position only in the
  reserved bootstrap user ID `<org>~<grp>~genesis~usr~jdtp-admin`, which
  must be constructible before any transaction exists.
- A **composite** ID — two conforming IDs joined
  (`<object_id>~<property_id>`) — is tolerated on deprecated legacy
  assignment payloads (§3.3.5), where it is ignored.

**Enforcement.** The wire schema rejects any submitted top-level `data.id`
that does not match this convention; the materializer additionally logs a
warning for nonconforming create IDs as defense in depth for non-wire
writers. Nested `id` keys inside property bags are not constrained.

### 1.3 Root document contract [Normative]

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
the partial chain with `chainComplete: false` so a broken hierarchy is
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

Value semantics are currently **create-only**: an absent entry is set; an
existing entry equal to the incoming one (ignoring `applied_at`) is an
idempotent duplicate; a differing entry is a conflict and is never
overwritten. **[Planned]** Value updates with last-committed-wins ordering;
the orderable `commit_id` is the primitive that enables them.

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

### 1.9 Procedures and tasks [Planned]

Two further collections realize the manifesto's process-tracing extension
(director-ratified 2026-07-04):

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
- Task inputs are `ent` objects; the input relationships, the
  task-fulfilled-by-procedure relationship (recorded on completion), and
  each output's produced-by relationship are **canonical `lnk` records**.
  On-root pointers arrive with the planned `links` projection.
- The procedure root carries a top-level `output_input` map — the
  canonical execution provenance: keys are output `ent` IDs; each value
  maps contributing input `ent` IDs to an open **contribution object**
  (e.g. `{ "volume": 12.5 }` for pooling). Contribution weights live only
  here; the schema for contribution objects will be supplied by a `vdn`
  record associated with the procedure type once `vdn` materializes
  (UT-4).

---

## 2. Transactions and Messages

### 2.1 Transaction identity [Normative]

A transaction is identified by
`<uuidv7>~<org>~<grp>~<client>`. The UUIDv7 provides uniqueness and
chronological sortability; `org`/`grp` name the writing group; `client`
names the submitting application. Transactions are opened, then carry data
messages, then commit (or roll back).

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
- `data` property names are `snake_case`, recursively, with one exception:
  the `grp` `permissions` map is keyed by world-unique group IDs.
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
| `txn + commit` | Commits: the backend assigns an opaque, **orderable** `commit_id` and triggers materialization. |
| `txn + rollback` | Acknowledged and logged; **not persisted** in the current implementation. |
| `loc/ent/grp + create` | Root document per §1.3; `data.type_id` surfaces as the root `type_id` (unresolved references are not checked). |
| `typ + create` | Entity type (optionally with `parent_type_id`) or, with `kind: "link_type"`, a link type declaration. |
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

- `object_collection` (currently `ent` or `loc`) is explicit; the
  implementation MUST NOT infer a collection by parsing `object_id`.
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

### 2.4 Transaction lifecycle

**[Normative — current]** Submitted messages are appended to the durable
transaction store as they arrive (header records and message records; no
state guard on append). Commit durably marks the header with the orderable
`commit_id` **before** any materialization; the post-commit projection then
materializes supported messages in message-UUID order. A projection failure
never un-commits: re-delivery of the commit re-runs materialization, and
all projections are idempotent (§2.5), so gaps self-heal on redelivery.
There is no background sweep for committed-but-unmaterialized transactions
yet.

**[Planned]** The ratified lifecycle separates durable facts from staging:
messages stage in transient `msg`; commit records a `message_count`;
projection records a per-message `apply_state`; the header reaches an
`applied` watermark only when every staged message has a terminal outcome;
cleanup deletes only cleanly-applied staged messages **after** the
watermark is durable (skipped/conflicting payloads are retained as
quarantine). Readers then overlay committed-but-unapplied messages over
root documents; read-your-own-open-transaction support is deferred.

### 2.5 Idempotency and duplicates [Normative]

- **Message append:** a re-delivered message with an identical payload is
  an idempotent duplicate; a differing payload under the same
  transaction+message identity is a conflict and is rejected.
- **Root creation:** insert-only; an existing document with an identical
  payload (ignoring `_head.provenance.materialized_at`) is an idempotent
  duplicate; a differing payload — including differing provenance — is a
  conflict and is **never overwritten**.
- **Property registration and property values:** the same
  matching-vs-conflicting rule, ignoring `applied_at` on value entries.
- Consequence (re-import boundary): replaying the *same* transaction is
  idempotent end to end; a *new* transaction re-submitting the same IDs
  surfaces as conflicts (provenance differs) and changes nothing. JDTP
  ingestion is therefore create-only today; synchronization of upstream
  changes awaits the planned lifecycle and value-update semantics.

---

## 3. Read Models [Normative]

Read views are projections over materialized roots; they perform no writes
and add no hidden semantics. Two route styles are deliberate:

- **Resource reads** require the subject root and answer 404 when it is
  missing: object property values (`loc`, and `ent` via the same generic
  contract), effective type properties, resolved location contents.
- **Query reads** over link rows answer 200 with empty results and cannot
  prove the subject exists: flat contents by container/content, the
  plate-shaped grid view.

Contracts of note:

- **Object property values:** the root's identity, `type_id`, the inline
  `properties` bag (clearly separated), and `propertyValues` keyed by `ppy`
  ID with value, provenance, and the resolved property name (null when the
  definition is missing). Stale or malformed stored entries are tolerated,
  never fatal.
- **Effective type properties:** the union of property references across
  the subject type and its ancestors (most-derived registration wins),
  each attributed to the registering type, with the ordered type chain and
  `chainComplete: false` on a broken chain.
- **Contents views** resolve the `contents` link type by its declared
  `kind`/`name`, not by ID, so independently minted `contents` declarations
  coexist.

---

## 4. Enforcement Summary

| Rule | Enforced by | Status |
|---|---|---|
| Envelope shape, UUIDv7s, action/collection matrix, snake_case payloads | wire schema | Normative |
| Object identifier convention on `data.id` | wire schema (+ materializer warning) | Normative |
| Type-registration gate for property values (inheritance-aware) | materializer | Normative |
| Duplicate/conflict semantics (§2.5) | store + materializer | Normative |
| `value_schema` validation of submitted values | — | Planned (read-time validator) |
| `required` property references | — | Deliberately not implemented |
| Link endpoint/collection resolution | — | Planned |
| Group permissions (read/write, property scope) | — | Planned |
| Writer persistence (`writer.user_id` + snapshot) | — | Planned |

The gaps in this table — and several sharper ones (unpersisted rollback,
silently ignored `uni`/`vdn` submissions, unguarded late message appends)
— are tracked as director-ratified TODO items with stable IDs in
[`uncomfortable-truths.md`](uncomfortable-truths.md).

---

## 5. Planned Extensions

Ratified direction, in the migration plan's order: local `usr` identity
resolution and durable writer persistence; the `txn`/`msg` split with the
applied watermark and guarded cleanup; overlay reads; value updates;
permission enforcement; retirement or formal reservation of the inline
`properties` bag; link-property alignment; the procedure/task provenance
model (§1.9); materialization of `uni` and
`vdn`; an HTTP submission adapter over the same message vocabulary;
extension pages for oversized objects; and derived-capability seams
(`SearchProvider`, `GraphProvider`, `VectorProvider`, `ArchiveProvider`)
that project from — and never replace — the canonical transaction and
repository state.

**Open director decisions** (tracked in the drift note): the inline-bag
endgame; link alignment; whether plate `format`/`rows`/`columns` are
instance values or type facts; the bulk-import selection strategy; the
payload-archive question that gates staged-message cleanup; rollback
persistence semantics; null-user envelope handling.

---

*Reference implementation notes (non-normative): the current stack is
Spring Boot/WebFlux + Kafka + MongoDB; canonical example messages 01–15
live in the DTO library and are exercised against the wire schema in CI;
the typed container review seed and the live CouchDB import loop
demonstrate the protocol against real laboratory records.*
