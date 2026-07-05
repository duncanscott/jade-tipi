# Jade-Tipi Direction

This document records current product and architecture direction that is not yet
fully implemented. Treat it as guidance for task design, not as a finalized
protocol specification.

## Location Collection

Jade-Tipi should add a first-class `loc` collection for physical and addressable
locations. In a biology laboratory this includes buildings, rooms, freezers,
freezer shelves, racks, boxes, tubes, plates, and possibly individual wells.

`loc` is a long-term materialized collection alongside `ent`, `ppy`, `lnk`,
`uni`, `grp`, `usr`, `typ`, `vdn`, `prc`, and `tsk`. The `txn` collection
remains special:
it stores durable transaction objects, not normal domain objects.
Transaction-message payloads may use a separate transient staging collection,
tentatively `msg`, until they have been applied to the domain object documents.

## Object Identifiers

Object IDs are world-unique text strings of the form
`<org>~<grp>~<uuidv7>~<collection>~<suffix>`. The UUID version 7 segment is
the creating transaction's or the creating message's UUID — both forms are
sanctioned — giving world-uniqueness and chronological sortability without a
central registry. When the transaction's UUID is used, clients must keep
suffixes unique within that transaction. The single sanctioned non-UUID
segment is the literal `genesis` in the reserved bootstrap `usr` ID
(`...~genesis~usr~jdtp-admin`), which must be well-known before any
transaction exists. Demo, test, and documentation IDs follow the same rule.

## Objects, Types, And Properties

A member of a long-term collection is a Jade-Tipi object, not necessarily an
`ent` entity. `ent`, `loc`, `lnk`, `ppy`, `typ`, `uni`, `grp`, `usr`, and
`vdn` are peer domain collections. `txn` contains transaction records rather
than normal domain objects.

For initial implementation, model each object as a typed collection of explicit
property-value assignments. The object's `type_id` points to a `typ` record that
defines which properties may be assigned to objects of that type. A property
must be added to the type before clients may assign that property to an object
of the type.

Do not implement required properties or default values yet. If a property value
is not explicitly assigned in a create or update message, it is absent. The
materializer should not invent property values.

Property definitions live in `ppy`; object property values live on the object
document, keyed by `ppy` ID, once their transaction messages are materialized.
Each property-value write is associated with a durable `txn` record so the
system can explain who wrote the value even after transient staged messages are
cleared. Any standalone `ppy` assignment records are a transitional
implementation detail, not the target storage model.

Types support simple single inheritance. A type may extend a parent type by
declaring a `parent_type_id` pointing at its supertype; the subtype inherits
all the properties of the parent type. A property is therefore assignable to
an object when it is registered on the object's own type or on any ancestor
reached through the `parent_type_id` chain. Keep the mechanism minimal:
single parent, no property overriding or shadowing, no multiple inheritance,
and bounded, cycle-safe resolution. An unresolvable chain (missing ancestor,
cycle, or excessive depth) means the property is not assignable.

The type hierarchy never changes which collection an instance belongs to.
Container instances — including instances of container subtypes such as
`plate_96_well` — are `loc` records.

## Procedures And Tasks

JDTP adds two further first-class collections: `prc` (procedure) and `tsk`
(task). A procedure is a performed procedure — the execution event that
turns inputs into outputs. A task is the intention to perform a procedure
of a given type on a set of inputs.

Every object has exactly one type, and type definitions are not
overloaded: procedure types define `prc` objects and task types define
`tsk` objects. A task type carries its associated procedure type as a
property of the task type (working shape: `properties.procedure_type_id`,
optionally with the human-readable procedure name), so task instances need
no per-instance procedure pointer. Working proposal, mirroring the
`link_type` discriminator: task and procedure type declarations in `typ`
carry `kind: "task_type"` and `kind: "procedure_type"`.

A task's inputs are `ent` objects, represented canonically as `lnk`
records. When a task is completed, the procedure that fulfilled it is
recorded as a `lnk` between the task and the `prc` record.

A procedure optionally produces outputs of type `ent`. The procedure root
carries the canonical execution provenance as a top-level `output_input`
map (parallel to `lnk`'s top-level `left`/`right`):

```json
{
  "output_input": {
    "<output ent id>": {
      "<input ent id>": { "volume": 12.5 }
    }
  }
}
```

Keys are output IDs; each value maps the IDs of the inputs used to
generate that output to a contribution object. The contribution object is
deliberately open — for a pooling procedure it would typically record the
volume each input contributed to each pool. The schema for contribution
objects will later be supplied by a `vdn` record associated with the
procedure type; that association is deferred until `vdn` materialization
exists.

The coarse relationships — task inputs, task fulfillment, and each
output's produced-by pointer to its creating procedure — are canonical
`lnk` records. On-root pointers (for example an output entity's reference
to the procedure that created it) arrive with the planned denormalized
`links` projection, never as duplicated source-of-truth fields. The
fine-grained contribution weights live only in the procedure's
`output_input` map: execution-owned data, not a duplicate of the links.

Instances stay in their own collections regardless of the type hierarchy:
tasks are `tsk` records and procedures are `prc` records.

## Files

JDTP adds a first-class `fil` (file) collection for retrievable
electronic assets. If it is a retrievable electronic asset, it is a file
— including assets that are no longer retrievable (deleted) or only
retrievable locally. Aggregates of files (datasets, run folders) remain
`ent` records with membership links to their `fil` members.

Files are the boundary objects between metadata and data: a `fil` record
is metadata about bytes stored elsewhere. Files are expected to become
the highest-volume object class, and their properties are highly regular
across instances; a dedicated peer collection keeps them out of `ent`
scans and gives implementations a natural home for file-specific
indexing.

A `fil` record is a standard typed root: file types live in `typ` with
ordinary inheritance, and file facts arrive as ordinary typed property
values. No file-specific root structure is hoisted yet — the property
set will become evident as real file objects are imported. A retrieval
URL is one candidate property, but not every file has a URL; some files
have a retrieval protocol that is not a URL. Content-identity hoisting
(checksums, sizes, locators), a schema-shaped `fil` payload branch, and
the deduplication policy for identical bytes are all deliberately
deferred until usage reveals what they should be.

Files slot into the procedure/task provenance model unchanged: a file is
typically the output of a `prc` (`produced_by`) and an input to a `tsk`
(`task_input`); link types admit `fil` endpoints through their ordinary
`allowed_*_collections` declarations.

## Users, Groups, And Permissions

`usr` records are first-class Jade-Tipi identity/audit objects. They represent
people or service identities known to Jade-Tipi, usually projected from an
external authentication source such as ORCID through Keycloak. A `usr` record
is not the authentication provider and should not store passwords or bearer
tokens. It is the local, durable record that lets Jade-Tipi explain who
performed work without querying an external identity provider later.

Every durable `txn` record should be associated with a `usr` record. The `txn`
record should store one `writer` sub-document that carries a stable `user_id`
reference together with an immutable identity snapshot, such as the ORCID iD,
OIDC issuer/subject, display name when known, client, and authentication
source. The snapshot fields preserve the audit meaning of the transaction even
if the `usr` record is later merged, renamed, disabled, or enriched.

The system needs one reserved bootstrap user so the first normal transactions
can be audited without a user/transaction creation cycle. Working name:
`jdtp-admin`, with a stable `usr` ID such as `...~usr~jdtp-admin`. This is a
system/bootstrap identity, not a login account and not an external identity
provider user. It may author genesis transactions that create the first local
`usr`, `grp`, `typ`, `ppy`, and policy records. The creation of the bootstrap
`usr` itself is a genesis storage fact, not a normal user-authored
transaction.

`grp` records are first-class Jade-Tipi objects. They should have world-unique
IDs, `type_id`, explicit properties, possible links, and the same root-document
storage shape as other long-term collection objects.

The initial permission model should be group-owned and deliberately simple:

- Users are members of one or more groups through local Jade-Tipi membership
  facts, which may initially be projected from identity-provider claims.
- A group has read/write permission on objects and property assignments owned by
  that group.
- A `grp` record may carry a permissions map for other groups. Each entry grants
  either read/write (`rw`) or read-only (`r`) access to objects owned by the
  group.
- Properties and property-value assignments are owned by groups, so permission
  checks must eventually operate at property scope, not only at whole-object
  scope.

`grp` records are not collections of ORCID IDs. They describe groups and
group-to-group access. User membership in groups should be represented locally,
either as `usr` properties, `lnk` relationships, or a later dedicated
membership projection. Authentication providers can establish or refresh those
facts, but durable audit and authorization reads should not depend on a live
identity-provider query.

Avoid implementing finer-grained overrides in the first pass. Individual objects
may eventually carry a group-permissions override map, and individual
property-value assignments may eventually carry permissive or restrictive
overrides. Those mechanisms should wait for concrete use cases because they add
substantial complexity to reads, writes, and explanation of effective access.

## Logical Objects And Physical Documents

A Jade-Tipi object is a logical JSON object. Its physical representation in
MongoDB or another document store is not required to be a single physical
document forever. The root document should contain the object's identity,
`collection`, `type_id`, small `properties` and `links` maps when they fit, and
reserved implementation metadata.

Start with the simple implementation: store each materialized object in one root
document and keep `properties` and denormalized `links` directly on that root
document. This is the common and ideal case. Do not implement page chains,
pending pages, background compaction, or parallel overlay reads in the initial
materializer.

Longer term, properties and links are unbounded, so an object may need extension
documents. Treat those as physical pages belonging to the root object, not as
separate Jade-Tipi objects. The intended shape is:

- root document: object identity, small inline property/link maps, and pointers
  to the first property and link extension pages when needed.
- property pages: overflow entries for the object's property map.
- link pages: overflow entries for the object's denormalized link map.
- pending pages: unordered append-friendly pages for new property/link entries
  waiting for a background process to merge them into sorted pages.

Property and link maps should be keyed by the IDs of the property or link
objects. Extension pages may use document IDs derived from the root object ID
with a page suffix such as `~ppy~0`, `~ppy~1`, `~lnk~0`, or `~lnk~1`; this is a
working convention, not yet final. Pages should keep `root_id`, `left` and
`right` neighbor pointers when linked-list traversal is useful, plus `min_id`
and `max_id` bounds so readers can skip irrelevant pages. The root document may
later carry a compact page index to jump near the relevant page rather than
always traversing from the beginning.

Use a reserved header object on root and page documents to keep storage metadata
separate from user-visible property and link maps. Working name: `_head`. The
project currently has `_jt_provenance` in early materializer output; future
materializer work should reconcile that with the `_head` direction rather than
mixing implementation metadata into `properties` or `links`.

Read semantics should eventually layer data from the root document, extension
pages, pending pages, and committed transaction messages that have not yet been
materialized. Committed-but-unapplied messages are expected to live in transient
message staging (`msg`), while durable transaction facts remain in `txn`. That
overlay model is future work; the first implementation should make the
root-only case correct and easy to replace.

## Human-Readable Kafka Submission

Kafka is the preferred first write path for domain data because it imposes the
harder ordering, replay, and idempotency constraints. HTTP submission can later
be a thin adapter that builds the same messages and calls the same services.

Kafka messages should remain easy for humans to read, write, and debug. Avoid a
clever nested operation language. The top-level message should say what is being
changed with `collection` and `action`, and `data` should look like the object
or relationship being submitted.

For a first-pass `loc + create` message, the intended shape is:

```json
{
  "collection": "loc",
  "action": "create",
  "data": {
    "id": "jade-tipi-org~dev~...~loc~freezer_01",
    "type_id": "jade-tipi-org~dev~...~typ~freezer",
    "properties": {
      "name": "Freezer 01",
      "description": "Minus 80 freezer in room 214"
    },
    "links": {}
  }
}
```

`data.id` is the object ID to materialize. `data.type_id` may be absent while
type modeling is still immature. `data.properties` is a plain JSON object keyed
by property name for the initial human-authored path; stricter property-ID-keyed
maps and property-definition validation can be layered in after the submission
route is proven. `data.links` should normally be empty on create because
canonical relationships are submitted as separate `lnk` messages.

That plain `data.properties` form is first-pass only. The intended follow-on is
to submit property-value writes as transaction messages, validate them against
`typ`/`ppy`, and project them onto object documents keyed by `ppy` ID.

## Link-Centric Relationships

Do not make `parent_location_id` canonical on `loc` records. Parentage and
containment belong in `lnk` records. Storing the same relationship on both `loc`
and `lnk` would duplicate source-of-truth data and make updates ambiguous.

A `loc` record should describe the location node itself. A `lnk` record should
describe relationships between nodes. The intended simple shape for link
instances is:

- `type_id`: the declared link type or class.
- `left`: the ID of one linked object.
- `right`: the ID of the other linked object.
- instance properties: relationship-specific values such as well position,
  volume at placement, timestamp, or provenance.

The semantics of a link type should live in the `typ` collection rather than be
repeated on every `lnk` instance.

## Contents Link Type

The initial location relationship should be a declared `contents` link type.
Conceptually, `contents` is a subclass or specialization of the generic `lnk`
class. The `contents` type should be created before any `lnk` records of that
type are accepted.

The `contents` type should define facts such as:

- left role: `container`.
- right role: `content`.
- left-to-right label: `contains`.
- right-to-left label: `contained_by`.
- allowed left collections: usually `loc`.
- allowed right collections: initially `loc` and `ent`.

Concrete `contents` links should not repeat those labels. They should only store
the actual endpoints and instance-specific properties.

## Plates And Wells

For early development, model a plate as one `loc` record. A sample in a plate can
be represented by a `contents` link from the plate `loc` to the sample `ent`.
The well coordinate, such as `A1`, can be a property value on that `lnk` record
because it describes where the relationship holds within the container.

Example direction:

```json
{
  "collection": "lnk",
  "action": "create",
  "data": {
    "id": "jgi~pps~...~lnk~plate_123_sample_456",
    "type_id": "jgi~pps~...~typ~contents",
    "left": "jgi~pps~...~loc~plate_123",
    "right": "jgi~pps~...~ent~sample_456",
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

Create individual well `loc` records only when wells need their own lifecycle,
history, state, or independent references. Otherwise, well position can remain a
property of the `contents` link.

## Query Direction

To ask "what are the contents of this 96-well plate?", a reader should:

1. Find the plate's `loc` ID.
2. Find `lnk` records with `type_id` pointing to `contents` and `left` equal to
   the plate ID.
3. Resolve the `right` IDs as contained objects.
4. Use each link's `position` property to build a plate-shaped view.

The reverse query, "where is this sample located?", should query `contents`
links where `right` is the sample ID and resolve `left` as the container.
