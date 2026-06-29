# Object property model - drift analysis and migration plan

Status: analysis / proposal. Not accepted. Written 2026-06-28 in response to a
human observation that `loc` records store domain properties as plain strings
instead of as type-gated, `ppy`-keyed property values.

This note (1) confirms the intended model against the foundation documents,
(2) states target vs. current state per collection, (3) maps the blast radius
of the current name-keyed assumption, and (4) proposes a migration order.

## 1. The documented foundation

The intended model is explicit in the foundation documents and matches the
human description that prompted this note.

- `DIRECTION.md`, "Objects, Types, And Properties": "each object" is a typed
  collection of explicit property-value assignments. The object's `type_id`
  points to a `typ` record that defines which properties may be assigned to
  objects of that type.
- `DIRECTION.md`, "Logical Objects And Physical Documents": property and link
  maps should be keyed by the IDs of the property or link objects.
- `docs/architecture/jade-tipi-object-model-design-brief.md`, Core Terms:
  objects are collections of property-value assignments; a type declares the
  properties that may be assigned to objects of that type; a property may be
  assigned only after the object's type definition permits that property.

So the target is: every domain object carries a `type_id`; the `typ` declares
its assignable properties; the object holds property values keyed by `ppy` ID;
and an assignment is permitted only if the type lists that property.

## 2. The sanctioned first pass - and how it became drift

The same documents deliberately allowed a temporary simpler shape, which is
exactly what `loc` still uses today:

- `DIRECTION.md`, "Human-Readable Kafka Submission": `data.type_id` may be
  absent while type modeling is immature, and `data.properties` may be a plain
  JSON object keyed by property name for the initial human-authored path.

The drift is not a violation of the docs; it is an incomplete migration. The
`ppy` property model was built for `ent` only (TASK-031 definitions, TASK-032
assignments, TASK-033 read), and even there it took a transitional storage
shape that differs from the foundation model: standalone assignment roots keyed
by `entity_id`, rather than `ppy`-keyed property values on the object root.
`loc` never advanced past the first pass, and the recent container/seed/read
work (TASK-026, TASK-034-037) all built on the first-pass `loc` shape,
entrenching it.

A second subtlety: every root (`ent` included) still gets a name-keyed inline
`properties` bag copied verbatim from `data.properties` by the materializer. So
entities today carry two property representations - the inline name-keyed bag
on the root and separate `ppy` assignment roots - while locations carry only
the inline bag.

## 3. Target vs. current state, per collection

| Collection | Target | Current state |
|---|---|---|
| `txn` | Durable transaction objects that last forever. A transaction records who submitted/wrote, the local `usr` writer, a writer snapshot, commit state, commit identity, timestamps, and materialization state. | The collection currently stores both transaction headers and message records. This was a useful first pass, but conflates permanent transaction facts with transient payload staging. Current headers do not yet persist a local `user_id` / writer snapshot. |
| `msg` | Transient transaction-message staging collection. Holds submitted object/property/link messages while they are open, committed-but-unapplied, or being applied. Once all messages for a transaction have been written to object documents, the staged messages are cleared. | Does not exist yet. Current message records live in `txn`. |
| `usr` | Local Jade-Tipi identity/audit records for people and service identities. A `usr` record should hold stable local identity plus external identity keys such as ORCID and OIDC issuer/subject. | Not implemented. Current message examples carry `txn.user` as an ORCID-style string, but there is no local user collection and no durable transaction header `user_id`. |
| `typ` | Declares assignable properties for its objects as property references keyed by `ppy` ID. | Entity types do this for entity properties via `properties.property_refs.<ppy_id>` (TASK-030). No location type exists; link types carry an unenforced `assignable_properties` list. |
| `ppy` | Property definitions and policy: name, value schema, owner, and write policy. It defines who owns the property and who can write values for it. It is not the assignment-value store. | Definitions exist (TASK-031). Assignment values also exist as root-shaped `ppy` records (TASK-032), but only for entities. Treat those assignment roots as transitional, not target architecture. No definitions exist for container fields (`name`, `barcode`, `kind`, `format`, source facts). |
| `ent` | `type_id` plus property values keyed by `ppy` ID on the object document, gated by the type. Each property-value write carries transaction provenance. | Has a typed path, but assignments are standalone `ppy` roots keyed by `entity_id`, not projected onto the entity root; the root also keeps a name-keyed inline `properties` bag. The TASK-036 seed entity skipped assignments entirely and used the inline bag. |
| `loc` | `type_id` plus property values keyed by `ppy` ID on the location document, gated by the location type. | `type_id: null`; `properties` is a plain name-keyed string bag (`name`, `kind`, `barcode`, `format`, `source_*`). No location `typ`, no `ppy` definitions, and the current assignment mechanism rejects non-entities. |
| `grp` | Group/ownership objects with group-to-group permission grants. Group membership should be locally available, but `grp` is not a collection of ORCID IDs. | `grp` roots exist with a permissions map keyed by other `grp` IDs. Membership remains outside the model and is not synchronized locally. |
| `lnk` | Link instances may also have property values keyed by `ppy` ID, or a consciously documented relationship-specific exception. Link semantics live in `typ`. | Position-on-link is the right modeling direction, but instance `properties.position` is still name-keyed and the link type's `assignable_properties` is an unenforced name list. `lnk` is first-pass/deferred, not fully aligned. |

Read layer: `EntityPropertyValuesReadService` reconstructs a `ppy`-keyed view
(`valuesByPropertyId`) at read time via a join. That target-shaped API response
exists for entities only; it is not the canonical storage shape and does not
exist for `loc`.

## 4. Blast radius - where the first-pass assumptions are baked in

Every place that would change (or block) a move to typed, `ppy`-keyed object
property values:

**Write / materialization**

- `CommittedTransactionMaterializer.buildDocument` /
  `buildInlineProperties` / `copyProperties` copies `data.properties`
  verbatim (or inline-builds from `data`) into the root `properties` for every
  non-`lnk` collection; `type_id` is taken from `data.type_id` and is `null`
  when absent. No `ppy` resolution or type gating happens at root-create time.
- The `ppy + create` assignment path requires non-blank `data.entity_id`,
  requires the target `ent` root to exist, and gates on the entity type's
  `properties.property_refs`. Locations cannot receive a property assignment
  at all.
- Transaction payload records currently share the `txn` collection with
  transaction headers, so no code boundary yet distinguishes durable
  transaction facts from transient unapplied messages.

**Message vocabulary / schema**

- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:
  `data` is validated as a snake_case object for every collection except the
  `grp` permissions map. A `ppy`-ID-keyed value map needs a defined exception,
  because `ppy` IDs contain `~` and are not snake_case.
- Assignment payload field name `entity_id` is entity-specific.
- The canonical `Transaction` envelope allows `txn.user`, but the current
  durable transaction header does not persist a local `user_id` or immutable
  writer snapshot.
- `usr` is not in the current collection/schema allow-list.
- No `msg` collection exists in the message vocabulary or materialized
  collection allow-list.

**Canonical examples**

- `10-create-location.json`: name-keyed `properties` (`name`, `description`),
  no `type_id` - encodes `loc` as first-pass.
- `04/05/05a/06/07/08`: the full entity typed path (type -> add_property ->
  entity -> assignment). There is no location equivalent (no location type, no
  location property definitions, no location assignment).

**Seed / fixtures**

- `ClarityEspContainerReviewSeedKafkaIntegrationSpec` (TASK-036): all `loc`
  messages are name-keyed with no `type_id`; the `LHCPOT` entity also uses an
  inline name-keyed bag.

**Read views**

- `LocationRootReadService` carries `typeId` and passes `properties` through
  as an opaque map. It tolerates either shape, but does not resolve typed
  property values.
- `PlateContentsReadService`, `ObjectLocationsReadService`, and
  `LocationContentsReadService` embed `LocationRootRecord` /
  `EntityPropertyValuesRecord` and pass properties through opaquely. They need
  a generic object property-values reader before containers can expose typed
  values instead of raw strings.
- Overlay reads from committed-but-unapplied messages are not implemented.

**Docs**

- `docs/architecture/clarity-esp-container-mapping.md`: the ESP/Clarity
  mapping maps source fields to name-keyed `loc.properties`.
- `docs/architecture/kafka-transaction-message-vocabulary.md`: the current
  property assignment section documents entity-keyed standalone assignment
  roots. That is a first-pass implementation note, not the target model.

## 5. Target transaction/message/object projection model

The target model separates durable transaction history from transient message
staging and materialized object state.

### Durable `txn`

`txn` stores permanent transaction objects. These records last forever and are
the source for who wrote a value and when the write became committed/applied.
A transaction object should carry, at minimum:

- `txn_id`
- `user_id`, referencing a local `usr` record
- immutable `writer` snapshot, such as ORCID iD, OIDC issuer/subject, display
  name when known, client, and authentication source
- submitting client and owning/writing group
- open/commit/rollback/applied state
- `commit_id`
- submitted/committed/applied timestamps
- materializer version or projection watermark, once materialization exists
- counts or hashes needed to know whether every message was applied

The transaction object should not be deleted just because its payload messages
have been applied. The transaction audit must remain understandable without
querying Keycloak, ORCID, or any other external identity provider.

### Local `usr`

`usr` stores local Jade-Tipi identity/audit records. A `usr` object represents
a person or service identity known to Jade-Tipi. It is not an authentication
provider and should not store passwords or bearer tokens.

A minimal `usr` root should carry a stable local ID plus external identity keys
such as ORCID iD, OIDC issuer, OIDC subject, display name, email when
available, and provenance describing how the identity was observed. A `txn`
should point to the `usr` record with `user_id`, while also preserving a writer
snapshot so old transactions remain meaningful if the `usr` record changes.

`grp` remains the group/ownership/permission object. It is not a list of ORCID
IDs. Group membership should become local Jade-Tipi state, represented by `usr`
properties, `lnk` relationships, or a later membership projection, possibly
seeded from Keycloak/ORCID claims.

### Transient `msg`

`msg` stores transaction messages. It is the staging area for message payloads
that are open, committed but not materialized, or in the process of being
materialized. A `msg` record is keyed by transaction ID plus message UUID and
carries the submitted envelope (`collection`, `action`, `data`, and message
metadata).

After a transaction's committed messages have all been written to the relevant
object documents, the materializer clears those messages from `msg`. The
durable `txn` record remains as the permanent transaction/audit object.

If complete historical payload retention is required later, it should be a
separate archive decision. The target `msg` collection is a staging collection,
not the permanent audit log.

### Property definitions in `ppy`

`ppy` stores property definitions and property policy. A property definition
owns the meaning of a property ID: name, value schema, ownership, and write
policy. Assignment values should not be stored as long-lived `ppy` roots.

The write path for property values should consult the target object's `typ`
and the referenced `ppy` definition/policy. The exact permission check is
future work, but the collection responsibility is clear: `ppy` defines
properties; object documents hold property values; `txn` records who wrote the
value.

### Materialized object documents

Object collections (`ent`, `loc`, `lnk`, `grp`, `usr`, and eventually other domain
collections) hold the current materialized object state. Each object root has a
`type_id`, and its property values are keyed by `ppy` ID.

A property-value entry should be structured, not a bare scalar, so provenance
survives after `msg` cleanup. A working shape is:

```json
{
  "properties": {
    "jade-tipi-org~dev~...~ppy~barcode": {
      "value": { "text": "BC123" },
      "txn_id": "jade-tipi-org~dev~...~txn~abc",
      "commit_id": "commit-001",
      "msg_uuid": "018fd849-2a47-7777-8f01-aaaaaaaaaaaa",
      "applied_at": "2026-06-28T00:00:00Z"
    }
  }
}
```

The exact field names remain open, but each materialized property value must
point back to enough transaction provenance to answer "who wrote this value?"
without retaining the transient message forever.

### Read semantics

Reads should eventually be an overlay:

1. Read the materialized object document and its extension pages, if any.
2. Overlay committed `msg` records for the object that have not yet been
   materialized.
3. Later, optionally overlay the caller's own open/uncommitted transaction to
   support read-your-own-writes.

After all committed messages for a transaction have been projected and cleared
from `msg`, ordinary reads can be served from object documents plus transaction
provenance embedded in the property-value entries.

The overlay requires an applied watermark or equivalent state on `txn` so
readers can distinguish committed-but-unapplied messages from messages already
represented on the object root.

## 6. Proposed migration order

Each step is independently shippable and should keep existing TASK-031/032/033
behavior working until a deliberate cleanup.

1. **Ratify this target model.** The important decision is that standalone
   `ppy` assignment roots are transitional. The target is durable `txn`
   objects, transient `msg` records, and object-root property values keyed by
   `ppy` ID.
2. **TASK-038 - transaction-staged object property projection prework.**
   Define the exact `txn`/`msg` split, the local `usr` audit model, the
   materialized property-value entry shape, the read overlay boundary, and the
   implementation task breakdown. Stop before code changes.
3. **Introduce local user/audit records.** Add `usr` as a first-class local
   identity collection, define how ORCID/OIDC identities are projected into
   `usr`, and persist `txn.user_id` plus immutable writer snapshots on durable
   transaction records.
4. **Split durable transaction state from message staging.** Introduce `msg`
   as the transient message collection while preserving the current read/write
   behavior. Existing message records in `txn` can remain as legacy fixtures
   until migration/cleanup is explicitly scheduled.
5. **Define object property assignment messages.** Replace the
   entity-specific `entity_id` assignment shape with an object-targeted shape
   that can address at least `ent` and `loc`. Keep `entity_id` as a
   backward-compatible alias while legacy examples/tests exist.
6. **Project committed property assignments onto object roots.** The
   materializer should write property values to the target object document
   under `properties.<ppy_id>` (or a later agreed property-values map), with
   transaction provenance. This is the keystone implementation step.
7. **Type the locations and define container properties.** Add location `typ`
   records and `ppy` definitions for container fields (`name`, `barcode`,
   `kind`, `format`, source provenance, and similar fields). Submit `loc`
   creates with real `type_id` values.
8. **Move the container seed from name-keyed root properties to property
   assignments.** The Kafka-backed Clarity/ESP review seed should produce
   inspectable MongoDB `loc` roots with typed, `ppy`-keyed property values.
9. **Add generic object property-value reads and overlays.** Generalize
   `EntityPropertyValuesReadService` or replace it with an object-generic
   reader that can return typed values for `loc`, `ent`, and eventually `lnk`.
10. **Clean up transitional shapes.** Retire or narrowly reserve the
   name-keyed inline `properties` bag for source/provenance facts, delete the
   transitional standalone `ppy` assignment-root path, and reconcile link
   instance properties with the same model or a documented exception.

## 7. Open decisions for human/director review

- Confirm the collection name `msg` for transient transaction-message staging.
- Decide whether `msg` deletion happens before or after marking the `txn`
  applied, and what idempotency guard prevents message loss on crashes.
- Define the exact object property-value entry shape: field names, timestamp
  format, whether `commit_id` is required, and whether `msg_uuid` is enough to
  trace back to payload history after `msg` cleanup.
- Define the minimal `usr` root shape and the transaction writer snapshot:
  whether ORCID is the primary external key, whether OIDC issuer/subject are
  required, and how service accounts are represented.
- Decide how group membership is represented locally: `usr` properties,
  membership `lnk` records, or a later dedicated projection. Avoid making
  `grp` a list of ORCID IDs.
- Decide whether a payload archive is needed after `msg` cleanup. If full
  payload audit is required, it should not turn `msg` into another permanent
  source of truth by accident.
- Decide the object-targeted assignment message shape: explicit
  `object_collection` plus `object_id`, or another form. The materializer
  should not infer collection from ID parsing.
- Decide where permission checks live in the first implementation. `ppy`
  defines owner/write policy, but the current materializer does not enforce it.
- Decide whether materialized property values live under the existing
  `properties` map or under a separate `property_values` map to avoid confusion
  with the first-pass name-keyed bag.
- Decide when to align `lnk` instance properties with the same typed,
  `ppy`-keyed model.
