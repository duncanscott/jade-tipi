# Kafka Transaction Message Vocabulary

This note captures the first backend-facing vocabulary for Kafka-submitted Jade-Tipi transaction messages. The goal is to make transaction records in MongoDB self-describing enough that a later consumer, reader, and materializer do not need to infer domain intent from arbitrary payload conventions.

## Message Envelope

Every submitted message uses the DTO `Message` envelope and carries a first-class target collection:

```json
{
  "txn": { "uuid": "...", "group": { "org": "lbl_gov", "grp": "jgi_pps" }, "client": "kafka-kli" },
  "uuid": "...",
  "collection": "ppy",
  "action": "create",
  "data": {}
}
```

Current schema note: `collection` is one of the Jade-Tipi collection
abbreviations currently accepted by `message.schema.json`: `ent`, `fil`,
`ppy`, `lnk`, `loc`, `prc`, `tsk`, `uni`, `grp`, `typ`, `vdn`, or `txn`.
The backend stores it explicitly in transaction message documents.

Target direction: `usr` should be added as the local user/identity collection,
and `msg` should be added as transient transaction-message staging. `txn`
remains special durable transaction metadata, not a normal domain collection.

`txn`, `uuid`, `collection`, and `action` are all required by `message.schema.json`. The schema also enforces action/collection compatibility:

- `collection: txn` → `action ∈ {open, rollback, commit}`.
- `collection ∈ {ent, fil, ppy, lnk, loc, prc, tsk, uni, grp, typ, vdn}` → `action ∈ {create, update, delete}`.

`Message.getId()` is `<txn.getId()>~<uuid>~<action>` and intentionally does not include the collection. The collection is stored as a first-class field on the message and (later) on the persisted `txn` message record, so it does not need to round-trip through the ID.

## Human-Readable Authoring Rule

The Kafka submission format should be boring JSON. A person should be able to
write a small transaction in a text editor, publish it with `kafka-kli`, and
understand the resulting Mongo root document without decoding an embedded DSL.

Use these rules for early domain messages:

- Keep intent at the top level: `collection` names the target collection and
  `action` names the operation.
- Put the submitted object or relationship in `data`.
- Use `data.id` for the long-term object ID to materialize.
- Prefer plain JSON objects for `data.properties` and relationship
  `properties`.
- Keep `data.links` empty or absent on simple creates; canonical relationships
  should be separate `lnk` messages.
- Do not infer the collection from payload shape.
- Do not use arrays of nested operations for the first implementation.

## Object Identifier Convention

Every submitted `data.id` follows the world-unique object identifier
convention (TASK-044, restoring the 2026-02-02 Kafka design decision to use
UUID version 7 for all ID generation):

```text
<org>~<grp>~<uuidv7>~<collection>~<suffix>
```

- The UUIDv7 segment is either the creating transaction's UUID (the
  D4/seed convention — all roots in the transaction share it, and the
  client must keep suffixes unique within that transaction) or the creating
  message's UUID (the canonical-examples convention — uniqueness is
  automatic). Both forms are sanctioned.
- The `<collection>` segment is the target collection abbreviation; the
  `<suffix>` is a human-readable label and is not the uniqueness carrier.
- The single sanctioned non-UUID segment is the literal `genesis` in the
  reserved bootstrap `usr` ID (`...~genesis~usr~jdtp-admin`), which must be
  constructible before any transaction exists.
- Legacy composite assignment IDs (`<object_id>~<property_id>`, ten
  segments) must conform in both halves; they retire with the
  standalone-assignment-root cleanup.

Enforcement is two-layered (TASK-046): `message.schema.json` rejects any
submitted top-level `data.id` that does not match the `ObjectId` pattern
(org/grp segments, UUIDv7-or-`genesis` third segment, known collection
abbreviation fourth, `[a-z0-9_-]+` suffix, optional second conforming
block for the deprecated legacy composite alias id), so nonconforming
messages never reach the WAL; and `CommittedTransactionMaterializer` keeps
its structural warning as defense in depth for non-Kafka writers. Nested
`id` keys inside `properties` bags are not constrained.

A simple location creation should look like this inside the normal message
envelope:

```json
{
  "collection": "loc",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_01",
    "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~freezer",
    "properties": {
      "name": "Freezer 01",
      "description": "Minus 80 freezer in room 214"
    },
    "links": {}
  }
}
```

The early materializer may also tolerate older examples that put `name` and
`description` directly under `data`, but new examples should prefer the explicit
`data.properties` object because it mirrors the root-document shape.

## Transaction Records

Current implementation note: the first materializer stores two record kinds in
the `txn` MongoDB collection:

- Transaction header: `_id = txn_id`, `record_type = "transaction"`. The
  header state is `open`, then terminally `committed` (with the orderable
  backend `commit_id`, `committed_at`, `commit_data`) **or** `rolled_back`
  (with `rolled_back_at` and the rollback message's `data` kept as
  `rollback_data` — the audit fact; UT-2/TASK-050). The terminal states
  are mutually exclusive: a commit arriving after a rollback is refused
  (no `commit_id`, no materialization), a rollback arriving after a
  commit is refused, rollback re-delivery is an idempotent duplicate, and
  both transitions carry a `state: "open"` guard on the update query as
  write-time defense in depth. A committed header additionally gains a
  `materialized_at` watermark once the background materialization worker
  completes a projection pass (UT-7/TASK-052); commit handling itself
  never projects — it marks the header and nudges the worker, whose
  periodic sweep over committed-but-unwatermarked headers guarantees
  projection with no dependence on transport redelivery. Commit
  re-delivery does not trigger projection.
- Message record: `_id = txn_id + "~" + msg_uuid`, `record_type = "message"`.
  Each message record stores the submitted envelope, including `collection`, so
  materializers and readers do not have to infer the target collection from
  payload fields. Rows appended before a rollback remain stored (audit);
  the committed-visibility gate keeps them from ever materializing. A row
  appended **after** the header reached a terminal state is stored flagged
  `late_append: true` (UT-6/TASK-051) — never discarded, but excluded from
  the committed snapshot, so a commit re-delivery can never materialize
  it. Appends before open (no header yet) remain allowed and unflagged;
  full staging and cleanup remain the ratified lifecycle work (plan task
  F).

That shape is transitional. The target direction is to split durable
transaction metadata from transient message staging:

- `txn` contains durable transaction objects that last forever. The transaction
  is the authoritative source for commit state and for who wrote a property
  value. It should carry a `writer` sub-document — the local `user_id`
  reference plus an immutable identity snapshot — not only a group/client
  identifier.
- `msg` contains transient transaction-message payloads while they are open,
  committed-but-unapplied, or being applied. Once every message in a committed
  transaction has been written to the target object documents, the staged
  messages for that transaction are cleared from `msg`.

Once the durable `txn` header has a `commit_id`, the transaction is committed.
Readers must be able to resolve visibility through the durable transaction
record. A later object-property reader should overlay committed `msg` records
that have not yet been projected onto object roots; after projection and
message cleanup, the object roots plus transaction provenance on the value
entries are sufficient for ordinary reads.

## User Records And Writer Audit

Current implementation note: the `Transaction` envelope allows a `user` field,
and canonical examples use an ORCID-style identifier there. That field is
message metadata today. Durable transaction headers do not yet persist a local
`user_id` or immutable writer snapshot.

Target direction: Jade-Tipi should add a first-class `usr` collection for local
identity and audit. ORCID and Keycloak remain authentication sources, but
Jade-Tipi must persist enough identity locally to explain transactions without
querying an external identity provider. A `usr` record is not a password store
or token store; it is a local object for a person or service identity known to
Jade-Tipi.

A minimal `usr` record should carry:

- a world-unique `usr` ID;
- external identity keys such as ORCID iD, OIDC issuer, and OIDC subject;
- display facts such as display name and email when available;
- provenance for how the identity was observed or verified.

A durable `txn` record should carry one `writer` sub-document that contains:

- `user_id`: the local `usr` ID for the writer;
- an immutable transaction-time snapshot, such as ORCID iD, issuer, subject,
  display name, client, and authentication source.

The `writer.user_id` reference supports current joins to richer local
identity data. The snapshot fields preserve audit meaning if the `usr` record
is later renamed, merged, disabled, or enriched; they are never mutated.

### Bootstrap `usr`

The local `usr` model needs one reserved bootstrap identity so the first normal
transactions can be represented without a circular dependency. Working name:
`jdtp-admin`, with a stable world-unique ID such as `...~usr~jdtp-admin`.

`jdtp-admin` is a system/audit identity, not a login account. It should be
created as a genesis storage fact before ordinary transaction validation
requires `txn.writer.user_id`. It may author genesis transactions that create the
initial local users, groups, types, properties, policies, and membership facts.
Those transactions should still carry a normal durable writer shape:

```json
{
  "writer": {
    "user_id": "...~usr~jdtp-admin",
    "kind": "system",
    "name": "JDTP Bootstrap Admin",
    "source": "bootstrap"
  }
}
```

After bootstrap, new transactions should use real `usr` records projected from
ORCID/Keycloak or another configured authentication source.

Current implementation note (TASK-039): the backend ensures this root at
startup with an idempotent insert-if-absent (`UsrGenesisService`, gated by
`jadetipi.genesis.enabled`, default `true`). The ID is
`<jadetipi.instance.org>~<jadetipi.instance.grp>~genesis~usr~jdtp-admin`
(local development default `jade-tipi-org~dev~genesis~usr~jdtp-admin`), and
`_head.provenance.txn_id`/`commit_id` carry the `genesis~jdtp-admin`
sentinel, mirroring the accepted `admin~<uuid>` sentinel. The bootstrap root
carries no external identity keys, so identity resolution can never match
it. `usr` remains outside the wire `Collection` enum.

## Property Definitions

Properties are first-class documents in `ppy`. A property definition names the
property and defines the JSON object shape expected for assigned values. In the
target model, `ppy` also owns property policy: the owning group and the rules
for who may write values for that property.

Definitions and assignments share `collection: "ppy"` and are distinguished
by `data.kind` (`definition` vs. `assignment`). Since TASK-045, `ppy` holds
definitions only: assignment messages project their values onto the target
object document keyed by `ppy` ID (see Object-Targeted Property Assignment),
and the transitional standalone assignment-root path is retired. Rows written
by that path remain in MongoDB as historical data only. Staging property-value
writes in transient `msg` remains the drift-note target.

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "definition",
    "id": "lbl_gov~jgi_pps~...~ppy~barcode",
    "name": "barcode",
    "value_schema": {
      "type": "object",
      "required": ["text"],
      "properties": {
        "text": { "type": "string" }
      }
    }
  }
}
```

All property values are JSON objects. Scalar values are wrapped, for example `{ "text": "barcode-1" }`, `{ "number": 10, "unit_id": "..." }`, or `{ "boolean": true }`. The envelope schema does not yet validate the wrapper shape against the registered `value_schema`; that lookup belongs to the transaction snapshot/read layer.

The committed materializer projects `ppy + create` messages whose
`data.kind == "definition"` into the `ppy` MongoDB collection using the same
root-document shape as the other supported roots: top-level `_id == data.id`,
`id == data.id`, `collection: "ppy"`, `type_id: null` (a property definition
has no parent type), inline `properties.kind`, `properties.name`, and
`properties.value_schema` (copied verbatim as an opaque JSON object), an empty
`links` map, and the reserved `_head` block with
`provenance.collection == "ppy"` and `provenance.action == "create"`. The
materializer does not validate `value_schema` against future assignment values;
that lookup remains a future read-time validator concern.

## Types And Properties

Entity types live in `typ`. A type can be created independently and then updated to include a property reference.

```json
{
  "collection": "typ",
  "action": "update",
  "data": {
    "id": "lbl_gov~jgi_pps~...~typ~plate_96",
    "operation": "add_property",
    "property_id": "lbl_gov~jgi_pps~...~ppy~barcode",
    "required": true
  }
}
```

The materialized type document records property references, not embedded property definitions. The committed materializer writes each `typ + update add_property` message as a `$set` on `properties.property_refs.<data.property_id>` of the existing target `typ` root. The reference value carries only the wire-shape metadata that is present (currently `required` when supplied); when `data.required` is omitted the materialized entry is an empty object rather than a synthesized `{ "required": false }`. The materializer does not resolve `data.property_id` against the `ppy` collection in this iteration; the reference is recorded verbatim, and semantic resolution remains a future reader/validator concern.

### Type Inheritance

A type may extend another type by declaring an inline `parent_type_id` on its
`typ + create` message (TASK-040). Inheritance is single-parent and
deliberately minimal: no property overriding, no shadowing, no multiple
inheritance.

```json
{
  "collection": "typ",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~...~typ~plate",
    "name": "plate",
    "parent_type_id": "jade-tipi-org~dev~...~typ~container"
  }
}
```

A subtype inherits all the properties of its parent type. The parent
reference needs no dedicated materializer support: like other inline type
facts it lands under root `properties`, so the materialized subtype root
carries `properties.parent_type_id`. The inheritance semantics live in the
assignment registration gate: a property is assignable to an object when it
is listed under `properties.property_refs` on the object's own `typ` root or
on any ancestor reached by following `properties.parent_type_id`. The walk is
bounded (depth 10), cycle-safe, and fails closed — a missing ancestor root, a
cycle, or an exceeded depth counts the assignment as
`skippedUnregisteredProperty`. The canonical example is
`14-create-plate-type-extends-container.json`.

The type hierarchy never changes an instance's collection: container
instances — including instances of container subtypes such as
`plate_96_well` — are `loc` records, created with `loc + create` and
targeted by assignments with `object_collection: "loc"`.

## Entity Creation

Entities live in `ent` and reference a type.

```json
{
  "collection": "ent",
  "action": "create",
  "data": {
    "id": "lbl_gov~jgi_pps~...~ent~plate_a",
    "type_id": "lbl_gov~jgi_pps~...~typ~plate_96"
  }
}
```

## Property Value Assignment

Every property assignment is object-targeted (TASK-045: one property model).
The canonical form names the target explicitly; the value projects onto the
target object root under `property_values` — see the next section for the
full contract. A payload carrying only the deprecated `entity_id` (the
pre-TASK-045 wire form, shown below) resolves as
`object_collection: "ent"` / `object_id: <entity_id>` with a deprecation
warning, and any legacy composite `data.id` is ignored:

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "assignment",
    "entity_id": "lbl_gov~jgi_pps~...~ent~plate_a",
    "property_id": "lbl_gov~jgi_pps~...~ppy~barcode",
    "value": {
      "text": "barcode-1"
    }
  }
}
```

Early backend validation verifies required envelope fields, known
collection/action pairs, and object-shaped property values. Value-shape
validation against the registered property `value_schema` remains a future
read-time validator concern. `ppy + create` messages with missing, blank, or
unknown `data.kind` values remain `skippedUnsupported`.

### Object-Targeted Property Assignment

Current implementation (TASK-040/TASK-048/TASK-049): an assignment may
target any supported object root directly with explicit
`object_collection` (`ent`, `loc`, `prc`, `tsk`, or `fil`) plus
`object_id`; the materializer never infers a collection from ID parsing.
`data.id` is not required in this form because no standalone assignment root
is created. The canonical example is `15-assign-object-property-value.json`.

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "assignment",
    "object_collection": "loc",
    "object_id": "jade-tipi-org~dev~...~loc~plate_0001",
    "property_id": "jade-tipi-org~dev~...~ppy~barcode",
    "value": { "text": "PLATE-BC-0001" }
  }
}
```

All assignments take this path (TASK-045); the deprecated `entity_id`-only
payload is a compatibility alias for `object_collection: "ent"`. The
materializer projects the committed value onto the target object root under
a `property_values` map keyed by `ppy` ID, via a dotted-path `$set`:

```json
{
  "property_values": {
    "jade-tipi-org~dev~...~ppy~barcode": {
      "value": { "text": "PLATE-BC-0001" },
      "txn_id": "018fd849-...~jade-tipi-org~dev~kli",
      "commit_id": "commit-001",
      "msg_uuid": "018fd849-2a59-7999-8a09-efefefefefef",
      "applied_at": "2026-07-03T00:00:00Z"
    }
  }
}
```

The registration gate is inheritance-aware (see Type Inheritance): the
property must be listed under `properties.property_refs` on the target's
`typ` root or on an ancestor via `parent_type_id`. Gate outcomes mirror the
shared decision table: unknown `object_collection`, blank `object_id` or
`property_id`, or a non-object `value` are `skippedInvalid`; a missing target
root is `skippedMissingTarget`; a blank target `type_id`, missing `typ`
root, or unregistered property is `skippedUnregisteredProperty`. An existing
entry equal to the incoming one ignoring `applied_at` is `duplicateMatching`;
a differing entry is `conflictingDuplicate` and never overwritten. Value
updates (last-committed-wins) are deferred; the orderable `commit_id` is the
primitive that will enable them.

Remaining target direction: property-value messages should eventually stage
in transient `msg` rather than `txn`, and the drift-note lifecycle (per-message
apply outcomes, `applied` watermark, guarded cleanup) still follows as plan
tasks D/F/G.

## Link Types And Concrete Links

Concrete relationships between domain objects are recorded in `lnk`. The semantics of each relationship — its endpoint roles, human-readable labels, and the collections allowed on each side — live in `typ` as a `link_type` declaration. `loc` records do not carry parentage; containment is represented as a `lnk` with the appropriate type. A link type should exist before any `lnk` records reference it.

The first canonical link type is `contents`. Its `typ` declaration carries the role and label facts plus the allowed endpoint collections:

```json
{
  "collection": "typ",
  "action": "create",
  "data": {
    "kind": "link_type",
    "id": "jade-tipi-org~dev~...~typ~contents",
    "name": "contents",
    "left_role": "container",
    "right_role": "content",
    "left_to_right_label": "contains",
    "right_to_left_label": "contained_by",
    "allowed_left_collections": ["loc"],
    "allowed_right_collections": ["loc", "ent"]
  }
}
```

`data.kind: "link_type"` distinguishes a link-type record from an entity-type record in `typ`, mirroring the `definition`/`assignment` discriminator used for `ppy` records.

A concrete `contents` link references the type and the two endpoints, and stores instance-specific properties — for a sample placed in a plate well, the well coordinate is a `position` property on the link itself rather than on the plate or sample:

```json
{
  "collection": "lnk",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~...~lnk~plate_b1_sample_x1",
    "type_id": "jade-tipi-org~dev~...~typ~contents",
    "left": "jade-tipi-org~dev~...~loc~plate_b1",
    "right": "jade-tipi-org~dev~...~ent~sample_x1",
    "properties": {
      "position": {
        "kind": "plate_well",
        "label": "A1",
        "row": "A",
        "column": 1
      }
    }
  }
}
```

The schema accepts this envelope today on the strength of `lnk + create` and the snake_case property-name rule. Semantic checks — that `lnk.type_id` resolves to a committed `typ` record, that `left` and `right` resolve, and that the endpoint collections match the type's `allowed_left_collections` / `allowed_right_collections` — are not enforced by `message.schema.json` and remain a follow-up reader/materializer concern. Property-name values such as `position.label` ("A1") are stored verbatim; the snake_case rule applies to property keys, not to their string values.

## Procedures And Tasks

`prc` (procedure) and `tsk` (task) are first-class collections (TASK-048).
A procedure is a performed procedure — the execution event that turns
inputs into outputs. A task is the intention to perform a procedure of a
given type on a set of inputs. Both accept `create`/`update`/`delete` in
the schema's action matrix; the committed materializer supports
`prc + create` and `tsk + create`.

Type definitions are never overloaded: procedure types define `prc`
objects and task types define `tsk` objects. Mirroring the `link_type`
discriminator, the type declarations carry `kind: "procedure_type"` and
`kind: "task_type"`, and the task type carries its associated procedure
type as `procedure_type_id` (optionally with the human-readable
`procedure_name`), so task instances need no per-instance procedure
pointer:

```json
{
  "collection": "typ",
  "action": "create",
  "data": {
    "kind": "task_type",
    "id": "jade-tipi-org~dev~018fd849-3c11-7222-8a02-171717171717~typ~dna_pooling_task",
    "name": "dna_pooling_task",
    "procedure_type_id": "jade-tipi-org~dev~018fd849-3c10-7111-8a01-161616161616~typ~dna_pooling",
    "procedure_name": "dna_pooling"
  }
}
```

The materializer treats both kinds as ordinary `typ + create` roots (the
kind discriminator is stored verbatim and not enforced, matching the
`link_type` behavior).

A `tsk + create` materializes a standard typed root. A `prc + create`
materializes a typed root whose optional `data.output_input` map is
hoisted to the top level of the root document — parallel to `lnk`'s
`left`/`right` — and excluded from the inline `properties` bag:

```json
{
  "collection": "prc",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-3c15-7666-8a06-202020202020~prc~pool_run_1",
    "type_id": "jade-tipi-org~dev~018fd849-3c10-7111-8a01-161616161616~typ~dna_pooling",
    "name": "pool_run_1",
    "output_input": {
      "<output id>": {
        "<input id>": { "volume": 5.0 }
      }
    }
  }
}
```

`output_input` keys are output object IDs; each value maps the input
object IDs used to generate that output to an open contribution object
(for a pooling procedure, typically the contributed volume). Input and
output collections are not constrained by the protocol (director ruling
2026-07-05) — inputs and outputs are typically `ent` or `fil` objects; a
deployment may constrain them through its link-type
`allowed_*_collections` declarations. Because `output_input` keys are
object IDs rather than snake_case names, `message.schema.json` gives
`prc` payloads their own `ProcedureData` branch (mirroring the grp
`permissions` escape): `output_input` is schema-valid only on `prc`
messages, and each contribution must be an object. Contribution-object
schemas will later be supplied by a `vdn` record associated with the
procedure type; that association is deferred (UT-4).

The coarse relationships are canonical `lnk` records under ordinary
link types — task inputs (e.g. `task_input`: `tsk` → `ent` or `fil`),
task fulfillment recorded on completion (e.g. `fulfills`: `prc` → `tsk`),
and each output's produced-by pointer (e.g. `produced_by`: `ent` or
`fil` → `prc`).
The fine-grained contribution weights live only in the procedure's
`output_input` map: execution-owned data, not a duplicate of the links.
Both `prc` and `tsk` roots accept object-targeted property assignments
and are readable through the generic property-values read surface.
`ProcedureTaskProvenanceKafkaMaterializeIntegrationSpec` proves the full
loop end to end against Kafka and MongoDB.

## Files

`fil` (file) is a first-class collection for retrievable electronic
assets (TASK-049; DIRECTION.md, Files) — including assets that are no
longer retrievable (deleted) or only retrievable locally. Aggregates of
files (datasets, run folders) remain `ent` records with membership links
to their `fil` members. Files are expected to become the highest-volume
object class; the dedicated collection keeps them out of `ent` scans and
gives implementations a natural home for file-specific indexing.

A `fil + create` materializes a **standard typed root** — deliberately
nothing file-specific is hoisted. File types are ordinary `typ` records
with ordinary inheritance, and file facts arrive as ordinary
object-targeted property values:

```json
{
  "collection": "fil",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-3d04-7444-8a04-262626262626~fil~run42_r1_fastq",
    "type_id": "jade-tipi-org~dev~018fd849-3d01-7111-8a01-232323232323~typ~fastq",
    "name": "run42_r1.fastq",
    "description": "forward reads for sequencing run 42"
  }
}
```

A retrieval URL is one candidate property (the canonical examples
register and assign `retrieval_url` on the file type), but not every
file has a URL — some files have a retrieval protocol that is not a URL.
By director ruling, the file property set is left to emerge from real
imports; content-identity hoisting (checksums, sizes, locators), a
`fil`-specific schema payload branch, and the deduplication policy for
identical bytes are all deliberately deferred.

Files slot into the procedure/task provenance model unchanged: link
types admit `fil` endpoints through their ordinary
`allowed_*_collections` declarations (e.g. `produced_by` with
`allowed_left_collections: ["ent", "fil"]`), and `fil` roots accept
object-targeted property assignments and the generic property-values
read surface. `FileProvenanceKafkaMaterializeIntegrationSpec` proves the
sequence — typed file, projected `retrieval_url`, produced_by link back
to the creating `prc` — end to end against Kafka and MongoDB.

## Group Records And First-Pass Permissions

`grp` records are first-class Jade-Tipi objects with world-unique IDs. The
canonical wire payload carries `id`, `name`, an optional `description`, and an
optional `permissions` map keyed by world-unique grp IDs. Each permission value
is exactly `"rw"` (read/write) or `"r"` (read-only). The map names other
groups; ownership-group access is implicit and not represented as a self-entry.
`grp` records are group/permission objects, not collections of ORCID IDs.
Membership should be represented locally as `usr` properties, membership `lnk`
records, or a later dedicated membership projection, possibly seeded from
Keycloak/ORCID claims.

```json
{
  "collection": "grp",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics",
    "name": "analytics",
    "description": "analytics team",
    "permissions": {
      "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops": "rw",
      "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers": "r"
    }
  }
}
```

`message.schema.json` validates this shape through a collection-conditional
`data` schema: when `collection == "grp"`, `data` follows the `GroupData`
definition (snake_case top-level keys, with a `Permissions` exception for the
inner map). When `collection != "grp"`, `data` continues to follow
`SnakeCaseObject` and the snake_case `propertyNames` rule applies recursively
as before. The canonical example bundled with the schema is
`13-create-group.json`.

The committed materializer projects `grp + create` into the `grp` MongoDB
collection using the same root-document shape as the other supported roots:
top-level `_id`, `id`, `collection: "grp"`, `type_id` (currently `null` because
the canonical example does not declare a group type), inline `properties`
(`name`, `description`, and the verbatim `permissions` map), an empty `links`
map, and the reserved `_head` block with `provenance.collection == "grp"` and
`provenance.action == "create"`. Other `grp` actions and `grp + create`
payloads with missing or blank `data.id` are skipped without error.

This iteration intentionally does not enforce read or write permissions on
HTTP, Kafka, materializer, or read-service paths, does not synchronize group
membership from Keycloak or any other identity provider, does not materialize
`usr`, and does not introduce object-level or property-value-level permission
overrides.

## Committed Materialization Of Locations And Links

Once a transaction commits in `txn`, the background materialization worker (UT-7/TASK-052 — nudged by commit handling, guaranteed by its periodic sweep over committed headers lacking the `materialized_at` watermark) materializes `loc + create`, `typ + create` (both link-type records where `data.kind == "link_type"` and bare entity-type records where `data.kind` is absent, optionally carrying `parent_type_id`), `typ + update` messages whose `data.operation == "add_property"`, `lnk + create`, `ent + create`, `grp + create`, `prc + create`, `tsk + create`, `fil + create`, and `ppy + create` messages whose `data.kind` is `"definition"` or `"assignment"` (assignments project onto `ent`/`loc`/`prc`/`tsk`/`fil` roots under `property_values` with inheritance-aware gating; the `entity_id`-only form is a deprecated alias and standalone assignment roots are no longer written) into their long-term collections. The projection reads the committed snapshot through the existing read service and stamps the header watermark when the pass completes; current code uses `txn` for both durable transaction facts and staged message payloads. The target split is described above: durable transaction facts stay in `txn`, while transient message payloads move to `msg`. Other collections and other actions — including every `typ + update` whose `data.operation` is not `add_property`, every `ppy + create` whose `data.kind` is neither `"definition"` nor `"assignment"`, every `*+ delete`, and other update actions — are intentionally not materialized in this iteration and are counted as `skippedUnsupported` without raising an error.

The current materializer writes the accepted root-document shape from `DIRECTION.md`: one logical Jade-Tipi object normally stored as one root document with top-level `_id`, `id`, `collection`, `type_id`, explicit `properties`, denormalized `links`, and reserved `_head.provenance` metadata. Duplicate `_id` writes with an identical payload are idempotent successes; differing-payload duplicates are logged and counted but not overwritten, and missing or blank `data.id` is logged and skipped without synthesizing an id. Semantic reference validation (`type_id`, `left`, `right`, and `allowed_*_collections`) is still not enforced; that remains a follow-up reader/validator concern.

For supported `typ + update add_property` messages the materializer reads the target `typ` root by `_id` and either issues a single dotted-path `$set` on `properties.property_refs.<property_id>`, no-ops idempotently when the existing entry already matches the incoming reference metadata (counted as `duplicateMatching`), refuses to overwrite a conflicting existing entry (counted as `conflictingDuplicate`), or skips when the target `typ` root is missing (counted as `skippedMissingTarget`). Missing or blank `data.id` or `data.property_id` is counted as `skippedInvalid` before any Mongo read. The successful update writes only the property-reference sub-key; `_head.provenance` is intentionally left at the create-time values for this iteration so the projection-provenance sub-document does not mix create-message fields with update-time timestamps.

Rows materialized before the root-document contract may still contain the legacy copied-data shape with top-level `_jt_provenance`. New materialized writes should use `_head.provenance`; legacy fallback behavior exists only where explicitly documented by readers that still need to tolerate stale rows.

## Reading `contents` Links

`ContentsLinkReadService` answers the two contents questions over the materialized `lnk` collection without requiring callers to know the canonical `contents` `type_id`:

- `findContents(containerId)` returns the materialized `lnk` records whose `left` is `containerId` ("what are the contents of this container?").
- `findLocations(objectId)` returns the materialized `lnk` records whose `right` is `objectId` ("where is this object located?").

Both methods first query `typ` for documents with `properties.kind == "link_type"` and `properties.name == "contents"` and then filter `lnk.type_id` with `$in` against every matching declaration, so a tenant or environment that has more than one `contents` link-type declaration still surfaces all matching links. The dotted-path criteria match the root-shaped `typ` documents written by the materializer, where the `link_type` declaration facts (including `kind` and `name`) live under root `properties`. When no `contents` declaration exists yet, both methods return an empty result and never query `lnk`.

The service returns one `ContentsLinkRecord` per matching `lnk`, sorted by `_id` ASC. Each record carries the link `_id`, `type_id`, `left`, `right`, the verbatim `properties` map (including instance-only data such as `properties.position` for plate-well placements), and the verbatim `_head.provenance` sub-document written by the materializer. For documents materialized before the root shape was adopted, the service falls back to the legacy top-level `_jt_provenance` sub-document; this fallback is intentional and narrow and can be removed once stale legacy rows are confirmed gone. Endpoints are returned as raw id strings; this iteration does not join `lnk` to `loc` or `ent`, does not deduplicate, and does not flag conflicting materialized rows. Blank or whitespace-only ids are rejected at the service boundary with `IllegalArgumentException`.

`ContentsLinkReadController` exposes the same two questions over HTTP as a thin WebFlux adapter under `/api/contents`:

- `GET /api/contents/by-container/{id}` delegates to `findContents(id)` (forward: `lnk.left == id`).
- `GET /api/contents/by-content/{id}` delegates to `findLocations(id)` (reverse: `lnk.right == id`).

Both routes return a flat JSON array of `ContentsLinkRecord` preserving service order. An empty service result maps to HTTP 200 with body `[]`; the routes do not return 404 for "no matching link" and do not surface materialization timing through the HTTP status. Blank or whitespace-only ids surface the service `Assert.hasText(...)` `IllegalArgumentException` as a 400 `ErrorResponse` through `GlobalExceptionHandler`. The controller has no Mongo, materializer, or write-side collaborators and adds no controller-side authorization, pagination, or endpoint resolution policy.

## Reading Entity Property Values

`EntityPropertyValuesReadService` answers "what is this entity and which
property values are assigned to it?" over already materialized root documents.
It reads the `ent` root by `_id`, then queries `ppy` roots with
`properties.kind == "assignment"` and `properties.entity_id == <entity_id>`,
sorted by `properties.property_id` ASC and `_id` ASC. Missing entity roots map
to an empty service result and HTTP 404; existing entities with no assignments
return the entity record with `valuesByPropertyId: {}`.

In the current implementation, assignment values remain on standalone `ppy`
roots. The response groups every matching assignment under
`valuesByPropertyId.<property_id>` as a list, so multiple materialized
assignments for the same entity/property pair are preserved deterministically
rather than overwritten. Each value entry carries the assignment root `_id`,
`properties.property_id`, the verbatim object-shaped `properties.value`, and
`_head.provenance`. The service optionally resolves human-readable
`propertyName` by joining the referenced `ppy` definition roots where
`_id in <property_ids>` and `properties.kind == "definition"`, but a dangling
`property_id` is tolerated and leaves `propertyName == null`.
Assignment rows with missing or blank `properties.property_id` cannot be keyed
under `valuesByPropertyId` and are ignored by this reader; the current
materializer already treats newly submitted assignments with missing or blank
`data.property_id` as invalid, so this is tolerance for stale or drifted rows.
The reader expects the materializer's object-shaped `properties.value`; if a
stale or drifted row contains a non-object value, that value is returned as an
empty map rather than failing the whole entity read.

Target direction: a generic object property-values reader should read the
materialized object-root values keyed by `ppy` ID, with an overlay of
committed-but-unapplied `msg` records. This entity-only reader documents the
current transitional storage shape, not the final property-value contract.

`EntityPropertyValuesReadController` exposes the read as
`GET /api/entities/{id}/property-values`. This is a thin WebFlux adapter over
the service only; it does not add HTTP data submission, does not write Mongo,
does not update entity roots, does not validate assignment values against
`value_schema`, and does not add permission enforcement or pagination in this
iteration.

## Reading Plate-Shaped Contents

`PlateContentsReadService` answers the first plate-shaped query from
`DIRECTION.md`: "what are the contents of this 96-well plate?" It composes
existing read services rather than introducing a new projection. The service
calls `ContentsLinkReadService.findContents(containerId)` to retrieve
materialized `contents` links, then resolves each link's `right` endpoint with
`EntityPropertyValuesReadService.findPropertyValues(rightId)` so placed entries
can include the contained entity's materialized property values.

The response is a fixed 96-well shape: `rowCount == 8`,
`columnCount == 12`, `rowLabels == ["A", "B", "C", "D", "E", "F", "G",
"H"]`, `columnLabels == [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]`, and
`wells` in row-major order from `A1` through `H12`. A link is placed when its
`properties.position` is an object with
`kind == "plate_well"` plus an in-range row and column. Multiple links in the
same well are preserved as a `contents` list in service order. Links whose
position is missing, malformed, or outside the fixed 96-well range are returned
under `unplacedContents` rather than silently dropped. Each unplaced entry
carries an `unplacedReason` enum value: `POSITION_MISSING`,
`POSITION_KIND_UNSUPPORTED`, `ROW_MISSING`, `ROW_INVALID`,
`COLUMN_MISSING`, `COLUMN_MALFORMED`, or `COLUMN_OUT_OF_RANGE`. Columns are
numeric, so `COLUMN_MALFORMED` (not a number) is distinguished from
`COLUMN_OUT_OF_RANGE` (a number outside 1..12); rows are a fixed `A`..`H` label
set, so any present-but-unusable row collapses into `ROW_INVALID`. Placed
entries carry `unplacedReason == null`.

Each placed or unplaced entry carries the source link id, link type id, raw
`right` endpoint id as `objectId`, verbatim position object, link provenance,
and optional resolved `entity` record. Missing entity roots are tolerated and
leave `entity == null`; the link itself remains visible. The HTTP adapter is
`GET /api/contents/plate/{id}` under the existing `/api/contents` read surface.
The endpoint returns `200` with a fixed empty grid when no matching contents
links exist, because this view does not look up or validate the container `loc`
root. Clients must not treat `200` as proof that the plate/location exists.
This iteration does not add frontend UI, generalized plate geometry, container
`loc` validation, Kafka submission changes, materializer changes, permission
enforcement, pagination, or conflict repair for malformed links.

## Reading Resolved Object Locations

`ObjectLocationsReadService` answers the reverse composed query from
`DIRECTION.md`: "where is this sample/object located?" It composes existing
read-side pieces rather than introducing a projection. The service calls
`ContentsLinkReadService.findLocations(objectId)` to retrieve materialized
`contents` links whose `right` endpoint is the object, then resolves each
link's `left` endpoint with `LocationRootReadService.findLocation(locationId)`
so entries can include the containing `loc` root when present.

`LocationRootReadService` is the narrow reusable reader for materialized `loc`
roots. It reads one row by `_id` from the `loc` collection and maps `type_id`,
root `properties`, root `links`, and provenance from `_head.provenance`. It
keeps the same narrow legacy `_jt_provenance` fallback used by the flat
contents reader for stale pre-root-shape rows. Missing `loc` roots return an
empty service result.

The HTTP adapter is `GET /api/contents/by-content/{id}/locations` under the
existing `/api/contents` read surface. The route keeps the same subject id as
the flat reverse route (`by-content/{id}`) and exposes resolved locations as a
sub-view. The response is an object with `objectId` and a `locations` list
preserving the flat reverse-link service order. Each list entry is a location
answer, but the link role remains `container`: entries carry the source link
id, link type id, the raw `left` endpoint id as `containerId`, the verbatim
`properties.position` object when present, link provenance, and optional
resolved `container` root. Links whose `left` endpoint is missing, blank, or
points at no materialized `loc` root remain visible with `container == null`.

This route is distinct from `GET /api/contents/by-content/{id}`: the existing
route returns the flat `ContentsLinkRecord` array, while
`/api/contents/by-content/{id}/locations` returns the resolved object-location
view. The endpoint returns `200` with `locations: []` when no matching contents
links exist because this view does not look up or validate the content object's
own root. Clients must not treat `200` as proof that the object exists. This
iteration does not add recursive location-path walking, frontend UI, semantic
endpoint validation, materializer changes, permission enforcement, pagination,
or location conflict repair.

## Reading Location Contents

`LocationContentsReadService` answers the forward composed query: "what does
this container/location contain?" It composes accepted readers rather than
introducing a new projection or direct all-collection Mongo query. The service
first resolves the subject with `LocationRootReadService.findLocation(id)`,
then reads immediate outgoing `contents` links with
`ContentsLinkReadService.findContents(id)`. Each link's `right` endpoint is
resolved as a materialized `loc` root when present, otherwise as a materialized
`ent` root with assigned property values when present.

The HTTP adapter is `GET /api/locations/{id}/contents`. Unlike the flat
contents routes and the plate-shaped read view, this route requires the subject
location root to exist: a missing `loc` root returns HTTP 404. An existing
location with no outgoing `contents` links returns HTTP 200 with the subject
`location` record and `contents: []`.

The response object carries `locationId`, the resolved subject `location`, and
a `contents` list preserving `ContentsLinkReadService.findContents` order. Each
entry carries the source link id, link type id, raw `left` endpoint as
`containerId`, raw `right` endpoint as `contentId`, verbatim
`properties.position` when object-shaped, link provenance, and one optional
resolved child record: `contentLocation` for a child `loc` root or
`contentEntity` for a child `ent` root with property values. Links remain
visible when `right` is blank, missing, or not materialized in either `loc` or
`ent`; unresolved child fields are null.

This iteration is immediate children only. It does not walk nested containers,
validate endpoint collections against the `contents` link-type declaration,
repair conflicting locations, write MongoDB, submit Kafka messages, add
frontend UI, enforce authorization, paginate results, or infer child types
beyond the accepted `loc` and `ent` read services.

## Reading Object Property Values

`ObjectPropertyValuesReadService` answers "which typed property values are
projected onto this object root?" over the TASK-040 `property_values`
contract (TASK-041, root-only — no overlay of committed-but-unapplied
messages yet). It reads one object root by `_id` from a supported collection
(`ent`, `loc`, `prc`, `tsk`, or `fil`), extracts the `property_values` entries sorted by property
ID, and resolves human-readable `propertyName` values by joining the
referenced `ppy` definition roots; a dangling `property_id` leaves
`propertyName == null`. The legacy first-pass inline `properties` bag is
returned verbatim and deliberately separated from the typed `propertyValues`
map so both representations are reviewable during the transition. Stale
tolerance mirrors the accepted readers: a non-map `property_values`
sub-document or entry is ignored, and a non-map entry `value` surfaces as an
empty map.

The HTTP adapter is `GET /api/locations/{id}/property-values`, a resource
read: a missing `loc` root returns 404; an existing root with no projected
values returns 200 with an empty `propertyValues` map. Since TASK-045 the
entity route `GET /api/entities/{id}/property-values` delegates to this same
generic reader with the fixed `ent` collection and returns the same response
shape; the transitional entity-only reader over standalone assignment roots
is deleted.

## Reading Effective Type Properties

`TypeEffectivePropertiesReadService` answers the read-side consequence of
type inheritance: "which properties may objects of this type carry?" It
walks the subject `typ` root's `properties.parent_type_id` chain (same
bounds as the TASK-040 registration gate: single parent, depth 10,
cycle-safe) and unions `properties.property_refs` across the chain. The
most-derived registration wins when a property is registered at multiple
levels; each effective property carries `sourceTypeId` (the type that
registered it), the verbatim reference metadata, and the resolved
`propertyName` from the `ppy` definition when present.

Where the write gate fails closed on a broken chain, this read surfaces the
partial result for inspection: the response carries the ordered `typeChain`
(subject first) and `chainComplete: false` when the walk stopped early on a
missing ancestor, a cycle, or the depth bound.

The HTTP adapter is `GET /api/types/{id}/effective-properties`, a resource
read: a missing subject `typ` root returns 404.

## Contents Read Surface Map

The contents read surface intentionally mixes query-style routes under
`/api/contents` with resource-style routes under `/api/locations` and
`/api/entities`. Clients should choose the route by the subject they need to
prove or query:

| View | Route | Subject lookup | Missing or absent subject |
| --- | --- | --- | --- |
| Flat forward links | `GET /api/contents/by-container/{id}` | No `loc` lookup; queries `lnk.left` only | HTTP 200 with `[]` |
| Flat reverse links | `GET /api/contents/by-content/{id}` | No `ent`/`loc` lookup; queries `lnk.right` only | HTTP 200 with `[]` |
| Resolved reverse locations | `GET /api/contents/by-content/{id}/locations` | No lookup of the content object's own root | HTTP 200 with `locations: []` |
| Plate-shaped forward contents | `GET /api/contents/plate/{id}` | No `loc` lookup; treats the id as a plate-shaped query key | HTTP 200 with an empty fixed grid |
| Generic resolved forward contents | `GET /api/locations/{id}/contents` | Requires the subject `loc` root | HTTP 404 when the `loc` root is missing |
| Entity property values | `GET /api/entities/{id}/property-values` | Requires the subject `ent` root | HTTP 404 when the `ent` root is missing |
| Location property values | `GET /api/locations/{id}/property-values` | Requires the subject `loc` root | HTTP 404 when the `loc` root is missing |
| Effective type properties | `GET /api/types/{id}/effective-properties` | Requires the subject `typ` root | HTTP 404 when the `typ` root is missing |

The asymmetry is deliberate. Flat contents routes and the plate-shaped view are
queries over materialized `lnk` rows and cannot prove that the submitted id
names an existing object. The generic location-contents and entity
property-values views are resource reads whose subject is a first-class root
record, so a missing root is exposed as 404. The plate-shaped route remains
`200` with an empty grid for a nonexistent id because it intentionally does not
validate or resolve the container `loc` root in that bounded slice.

## Reference Examples

A complete early transaction flow is bundled as resources under `libraries/jade-tipi-dto/src/main/resources/example/message/`:

1. `01-open-transaction.json`
2. `02-create-property-definition-text.json`
3. `03-create-property-definition-numeric.json`
4. `04-create-entity-type.json`
5. `05-update-entity-type-add-property.json`
6. `05a-update-entity-type-add-property-volume.json`
7. `06-create-entity.json`
8. `07-assign-property-value-text.json`
9. `08-assign-property-value-number.json`
10. `09-commit-transaction.json`
11. `10-create-location.json`
12. `11-create-contents-type.json`
13. `12-create-contents-link-plate-sample.json`
14. `13-create-group.json`
15. `14-create-plate-type-extends-container.json`
16. `15-assign-object-property-value.json`
17. `16-create-procedure-type.json`
18. `17-create-task-type.json`
19. `18-create-task.json`
20. `19-create-task-input-link-type.json`
21. `19a-create-task-input-link.json`
22. `20-create-procedure-with-output-input.json`
23. `21-create-fulfills-link-type.json`
24. `21a-create-fulfills-link.json`
25. `22-create-produced-by-link-type.json`
26. `22a-create-produced-by-link.json`
27. `23-create-file-type.json`
28. `24-create-property-definition-retrieval-url.json`
29. `25-update-file-type-add-property.json`
30. `26-create-file.json`
31. `27-assign-file-property-value.json`

`05a` registers the numeric `volume` property-definition on the same entity type as `05` registers `barcode`, so both canonical assignments (`07` and `08`) satisfy the materializer's type-registration gate within the one example transaction. `16`–`22a` walk the procedure/task provenance loop: the paired type declarations, the task and its input link, the procedure carrying `output_input`, and the fulfillment and produced-by links, cross-referencing one another by ID within the shared example transaction. `23`–`27` walk the file sequence: the file type, the `retrieval_url` definition and its registration on the file type, the typed `fil` create, and the object-targeted assignment onto the `fil` root.

These examples are exercised by `MessageSpec` to round-trip through `JsonMapper` and pass `Message.validate()` against `message.schema.json`.

## CLI Surface

`kli` (`clients/kafka-kli`) threads `collection` through all transaction-message commands:

- `kli open`, `kli rollback`, `kli commit` hardcode `collection = txn`.
- `kli create`, `kli update`, `kli delete` require `--collection <abbr>` (alias `-c`). The value is parsed via the `Collection` enum; unknown abbreviations and the reserved `txn` value fail with a clear error, as does any action/collection combination that violates the per-collection action whitelist.
- `kli publish --file …` deserializes the envelope unchanged and warns when the file omits `collection`, so missing fields are surfaced rather than silently dropped on the wire.
