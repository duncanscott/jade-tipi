# Object property model - drift analysis and migration plan

Status: TASK-038 implementation plan, ratified by director review 2026-07-03
with one amendment: the durable transaction header carries a single `writer`
sub-document that contains `user_id`, rather than separate top-level
`user_id` and `writer` fields. Sections 1-7 were written 2026-06-28 in
response to a human observation that `loc` records store domain properties as
plain strings instead of as type-gated, `ppy`-keyed property values. Section 8
was added 2026-07-03 as the TASK-038 prework artifact; implementation
proceeds in the section 8.5 order.

This note (1) confirms the intended model against the foundation documents,
(2) states target vs. current state per collection, (3) maps the blast radius
of the current name-keyed assumption, (4) proposes a migration order, and
(5) records the TASK-038 implementation plan and follow-on task breakdown.

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
| `usr` | Local Jade-Tipi identity/audit records for people and service identities. A `usr` record should hold stable local identity plus external identity keys such as ORCID and OIDC issuer/subject. One reserved bootstrap user (`jdtp-admin`) exists as a genesis storage fact so initial transactions can be audited. | Not implemented. Current message examples carry `txn.user` as an ORCID-style string, but there is no local user collection, no durable transaction header `user_id`, and no bootstrap user. |
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
- `writer`: a sub-document that carries `user_id` (the reference to a local
  `usr` record) together with an immutable transaction-time identity
  snapshot, such as ORCID iD, OIDC issuer/subject, display name when known,
  client, and authentication source
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
should point to the `usr` record with `writer.user_id`, while the other
`writer` fields preserve a snapshot so old transactions remain meaningful if
the `usr` record changes.

One reserved bootstrap `usr` is needed to avoid a circular dependency between
user creation and transaction creation. Working name: `jdtp-admin`, with a
stable world-unique ID such as `...~usr~jdtp-admin`. It is a system/audit
identity, not a human login account. Its own existence is a genesis storage
fact, and it may author the first durable transactions that create the initial
`usr`, `grp`, `typ`, `ppy`, policy, and membership records. After that, normal
transactions should use real local `usr` records projected from ORCID/Keycloak
or another authentication source.

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
   identity collection, define the reserved `jdtp-admin` bootstrap user and
   genesis provenance rule, define how ORCID/OIDC identities are projected into
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
- Define the `jdtp-admin` bootstrap identity: exact stable ID convention,
  reserved/system markers, genesis creation rule, and allowed use for initial
  transactions.
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

## 8. TASK-038 implementation plan

This section is the TASK-038 prework artifact. It turns sections 5-6 into an
implementation plan grounded in the code as of TASK-037 acceptance. It defines
target contracts, the materialization lifecycle, the read-overlay boundary,
and a bounded follow-on task breakdown. It changes no production code. Where a
decision from section 7 is consumed, section 8.6 records a recommendation and
marks it open for director review.

### 8.1 Code-boundary inventory

The plan builds on these verified current behaviors:

- `TransactionMessagePersistenceService.openHeader` persists only `_id`,
  `txn_id`, `record_type`, `state`, `opened_at`, and `open_data`. The envelope
  `message.txn().user()` is discarded at persistence: writer identity exists
  nowhere in MongoDB today, only on the Kafka wire. `org`, `grp`, and `client`
  survive only inside the `txn_id` string
  (`<uuid>~<org>~<grp>~<client>`), not as queryable fields.
- `TransactionMessagePersistenceService.appendDataMessage` inserts message
  records into `txn` with `_id = txn_id~msg_uuid` plus `record_type`,
  `txn_id`, `msg_uuid`, `collection`, `action`, `data`, `received_at`, and
  the `kafka` source sub-document. It performs no header-state check: messages
  are accepted before open and after commit.
- `TransactionMessagePersistenceService.commitHeader` sets `state`,
  `commit_id` (orderable, from `IdGenerator.nextId()`), `committed_at`, and
  `commit_data`, then calls `materializeQuietly`, which swallows projection
  failures. A projection gap self-heals only when a commit is re-delivered
  (the `COMMIT_DUPLICATE` branch also materializes). Rollback is logged and
  not persisted.
- `CommittedTransactionReadService.findCommitted` is the single committed
  visibility gate (`record_type = "transaction"`, `state = "committed"`,
  non-blank `commit_id`) and collects message records sorted by `_id` ASC.
  Because `msg_uuid` is UUIDv7, that sort is submission-time order within a
  transaction. Legacy `TransactionService`-shaped documents in `txn` (no
  `record_type`) are already tolerated and ignored.
- `CommittedTransactionMaterializer` is insert-only with a
  matching-vs-conflicting duplicate rule that ignores
  `_head.provenance.materialized_at` (`stripVolatileFields`). The
  `typ + update add_property` path shows the dotted-path update pattern
  (`$set properties.property_refs.<property_id>`) that property-value
  projection will reuse. `processPpyAssignmentCreate` hardcodes the target
  collection to `ent`; its gate sequence (target root exists, non-blank
  `type_id`, `typ` root exists, `properties.property_refs` lists the
  property) is otherwise collection-generic.
- MongoDB field names forbid `.` and `$` but allow `~`. Jade-Tipi IDs contain
  `~` and never `.`, so `ppy`-ID-keyed maps and dotted-path `$set` writes
  against them are safe.
- `message.schema.json` validates non-`grp` `data` as `SnakeCaseObject` with
  open `additionalProperties`. New snake_case payload fields such as
  `object_collection` and `object_id` require no schema change. The
  `GroupData`/`Permissions` pair is the precedent for a keyed-map exception if
  one is ever needed on the wire.
- `GroupAdminService` marks non-transactional writes with an `admin~<uuid>`
  sentinel under `_head.provenance.txn_id`/`commit_id`; the genesis bootstrap
  reuses this sentinel pattern.
- No code declares MongoDB indexes today; message reads scan on
  `record_type` + `txn_id`, and `usr` external-key lookups will be the first
  queries that need explicit index management.

### 8.2 Target contracts

#### 8.2.1 Durable `txn` header

Target header document. Fields marked (new) are added by the follow-on tasks
noted in parentheses; all others already exist.

```json
{
  "_id": "<uuid>~<org>~<grp>~<client>",
  "record_type": "transaction",
  "txn_id": "<same as _id>",
  "org": "lbl_gov",
  "grp": "jgi_pps",
  "client": "kafka-kli",
  "writer": {
    "user_id": "...~usr~...",
    "kind": "person",
    "orcid": "0000-0002-1825-0097",
    "oidc_issuer": "http://localhost:8484/realms/jade-tipi",
    "oidc_subject": "...",
    "display_name": "...",
    "client": "kafka-kli",
    "auth_source": "orcid_keycloak_device_flow"
  },
  "state": "open | committed | applied",
  "opened_at": "...", "open_data": {},
  "commit_id": "...", "committed_at": "...", "commit_data": {},
  "message_count": 7,
  "applied_at": "...",
  "apply_counts": { "materialized": 6, "duplicate_matching": 1 }
}
```

- `org`, `grp`, `client`, and `writer` (new: Task C) are set once at open.
  `writer` is a single sub-document answering who wrote the transaction:
  `writer.user_id` is the join reference to a local `usr` record, and every
  other `writer` field is an immutable transaction-time snapshot. Backfilling
  `writer.user_id` on an `unresolved` writer once the person's `usr` record
  exists later is the one permitted amendment; the snapshot fields are never
  mutated.
- `message_count` (new: Task D) is the count of staged messages observed at
  commit time and is the completeness denominator for `applied`.
- `state = "applied"`, `applied_at`, and `apply_counts` (new: Task F) form the
  projection watermark. A header is `applied` only when every staged message
  has a recorded terminal outcome. The header is never deleted.

#### 8.2.2 Transient `msg` staging record

`msg` is a storage collection only. It never appears in the wire `Collection`
enum, `kli` cannot target it, and no document in it is a Jade-Tipi object.
Message records move verbatim from `txn` to `msg` (Task D) keeping
`_id = txn_id~msg_uuid` and all current fields, plus per-message apply
bookkeeping (Task F):

```json
{
  "_id": "<txn_id>~<msg_uuid>",
  "record_type": "message",
  "txn_id": "...", "msg_uuid": "...",
  "collection": "ppy", "action": "create",
  "data": {}, "received_at": "...", "kafka": {},
  "apply_state": "pending | applied | skipped_invalid | skipped_missing_target | skipped_unregistered_property | skipped_unsupported | conflicting_duplicate",
  "applied_at": "..."
}
```

`apply_state` values mirror the existing `MaterializeResult` counters so the
decision table in the materializer Javadoc carries over unchanged. Cleanup
(Task G) may delete only rows whose `apply_state` is `applied` (which includes
idempotent `duplicate_matching` re-applies) and whose header is `applied`;
skipped and conflicting rows are retained as the quarantine record of
submitted-but-never-applied payloads.

#### 8.2.3 Local `usr` root

`usr` roots use the accepted root-document shape. Proposed minimal shape:

```json
{
  "_id": "<org>~<grp>~<uuid>~usr~<suffix>",
  "id": "<same>", "collection": "usr", "type_id": null,
  "properties": {
    "kind": "person | service | system",
    "orcid": "0000-0002-1825-0097",
    "oidc_issuer": "...", "oidc_subject": "...",
    "display_name": "...", "email": "...",
    "status": "active",
    "identity_provenance": {
      "source": "orcid_keycloak_device_flow | keycloak_login | bootstrap | admin_import",
      "first_seen_at": "...", "last_seen_at": "..."
    }
  },
  "links": {},
  "_head": { "...": "root shape as elsewhere" }
}
```

- A `usr` record carries at least one external identity key (`orcid`, or the
  `oidc_issuer`/`oidc_subject` pair) unless `kind == "system"`.
- Uniqueness is enforced with unique sparse indexes on `properties.orcid` and
  on the compound (`properties.oidc_issuer`, `properties.oidc_subject`).
  Resolution races are settled by the unique index plus retry-on-duplicate.
- `usr` is not an authentication provider: no passwords, no tokens. It is not
  added to the wire `Collection` enum yet; genesis creation and identity
  projection are backend-internal writes, and externally-authored `usr`
  transactions are deferred until a concrete need exists.

#### 8.2.4 Writer resolution at open

At `openHeader` time (Task C), the envelope identity resolves to local state:

1. `message.txn().user()` non-blank → treat as ORCID iD (the `kli` device
   flow verifies it) → `UsrIdentityService.resolveOrCreate` → set a full
   `writer` sub-document (`user_id` plus the identity snapshot) with
   `auth_source` describing how the identity was observed.
2. `message.txn().user()` blank/absent →
   `writer: { "kind": "unresolved", "user_id": null, "client": <client>, "auth_source": "envelope_missing_user" }`,
   log a warning. Rejection of user-less opens is a later enforcement flag,
   after `kli` and all examples reliably send `txn.user`.

The bootstrap `usr` is not a fallback for unattributed traffic; `jdtp-admin`
attribution is reserved for system-origin writes (genesis, seeds, admin
paths) that explicitly declare it.

#### 8.2.5 `jdtp-admin` genesis contract

- Recommended stable ID: `<instance_org>~<instance_grp>~genesis~usr~jdtp-admin`,
  with `instance_org`/`instance_grp` from new application configuration and
  the literal `genesis` segment in the timestamp position marking it as a
  genesis fact rather than a generated ID. Exact convention is director
  decision 8.6/5.
- Created by an idempotent startup ensure (insert-if-absent by `_id`), not by
  a Kafka transaction. Provenance uses a `genesis~jdtp-admin` sentinel under
  `_head.provenance.txn_id`/`commit_id`, mirroring the accepted
  `admin~<uuid>` sentinel.
- Properties mark it reserved: `kind: "system"`, `status: "reserved"`,
  `identity_provenance.source: "bootstrap"`. It has no external identity keys
  and never matches identity resolution. It is not a login account.
- It may be named as the transaction writer
  (`writer: { "user_id": "...~usr~jdtp-admin", "kind": "system", "name": "JDTP Bootstrap Admin", "source": "bootstrap" }`)
  by genesis/system transactions that create initial `usr`, `grp`, `typ`,
  `ppy`, policy, and membership records, and by the container review seed
  until real users author it.

#### 8.2.6 Object property-value entry

Materialized property values live on the object root in a new top-level map
`property_values`, keyed by `ppy` ID:

```json
{
  "property_values": {
    "<ppy_id>": {
      "value": { "text": "BC123" },
      "txn_id": "...", "commit_id": "...", "msg_uuid": "...",
      "applied_at": "..."
    }
  }
}
```

- `value` is the verbatim object-shaped value. `txn_id` and `commit_id` are
  required (they exist by definition after commit); `msg_uuid` is retained as
  the idempotency key for re-projection and the pointer into payload history
  while staging rows exist; `applied_at` is a BSON date like `committed_at`.
- A separate `property_values` map (not the existing `properties` map) is
  recommended because the name-keyed inline bag and typed values must coexist
  on `ent`/`loc` roots for the whole transition, and because root
  `properties` currently mirrors `data.properties` snake_case. Whether
  `property_values` is renamed to `properties` at cleanup is director
  decision 8.6/10.
- Projection uses dotted `$set property_values.<ppy_id>` (safe: IDs contain
  `~`, never `.`). Equality for idempotent re-projection ignores `applied_at`,
  mirroring the `stripVolatileFields` precedent.
- First implementation keeps create semantics: absent entry → set; present
  and equal (ignoring `applied_at`) → `duplicate_matching`; present and
  different → `conflicting_duplicate`, never overwritten. Value update
  semantics (last-committed-wins ordered by the orderable `commit_id`) are
  explicitly deferred; the orderable `commit_id` is the primitive that makes
  them possible later.

#### 8.2.7 Object-targeted assignment message

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "assignment",
    "object_collection": "loc",
    "object_id": "...~loc~plate_b1",
    "property_id": "...~ppy~barcode",
    "value": { "text": "BC123" }
  }
}
```

- `object_collection` is explicit (`ent` or `loc` initially); the
  materializer must not parse a collection out of `object_id`.
- No wire-schema change is required: the new keys are snake_case and
  `SnakeCaseObject` admits them. Only docs and canonical examples change.
- `data.id` is not required in the object-targeted form because no standalone
  assignment root is created; `msg_uuid` identifies the message.
- Routing is shape-determined, not dual-written: a message carrying
  `object_collection` + `object_id` projects onto the object root only
  (Task E); a message carrying only the legacy `entity_id` stays on the
  current standalone-`ppy`-root path, byte-for-byte unchanged until cleanup
  (Task L). The materializer never silently translates `entity_id` into the
  object form; keeping the legacy path intact is what lets TASK-032/033
  examples and tests keep passing verbatim while both forms are documented.
- The type gate is unchanged in meaning and generalized in target: target
  root exists in `object_collection` (else `skipped_missing_target`), root
  has non-blank `type_id`, `typ` root exists and lists `property_id` under
  `properties.property_refs` (else `skipped_unregistered_property`).

#### 8.2.8 Membership boundary

`grp` stays a group/permission object with no member lists. Membership is
local Jade-Tipi state (`usr` properties, membership `lnk` records, or a later
projection), possibly seeded from Keycloak claims. No follow-on task in this
plan implements membership: the property-projection milestone does not need
it, and permission enforcement remains out of scope. The mechanism choice is
director decision 8.6/6, deferred until permission enforcement is scheduled.

### 8.3 Materialization lifecycle

Design-level state machine:

```
open ──► staged ──► committed ──► projected ──► applied ──► cleaned
 txn        msg         txn        object roots     txn        msg
 header    records    commit_id    + outcomes     watermark   deletion
```

1. **Open**: header upserted with identity fields (8.2.4). Idempotent
   (`setOnInsert`).
2. **Stage**: data messages insert into `msg` with `apply_state: "pending"`.
   Duplicate-key handling keeps today's matching/conflicting rule. No
   header-state guard is added at the split (behavior-preserving move);
   staging guards, if wanted, arrive with outcome recording.
3. **Commit**: header gains `commit_id`, `committed_at`, and
   `message_count` = count of staged messages at commit time. Commit remains
   durable before any projection, and `materializeQuietly` keeps swallowing
   projection failures.
4. **Project**: the materializer processes messages in `_id` order and records
   a terminal `apply_state` and `applied_at` on each `msg` row as it lands.
   Re-projection is idempotent per message: inserts tolerate matching
   duplicates; `property_values` writes compare ignoring `applied_at`.
5. **Mark applied**: when every staged message for the transaction has a
   terminal `apply_state` and the terminal count equals `message_count`, the
   header is `$set` to `state: "applied"` with `applied_at` and
   `apply_counts`. Commit re-delivery for an `applied` header short-circuits
   without re-reading messages.
6. **Clean**: rows with `apply_state: "applied"` under an `applied` header may
   be deleted. Skipped/conflicting rows are retained (quarantine) so the
   header can be `applied` while the evidence of never-applied payloads
   survives.

Crash-safety ordering (director decision 8.6/2, recommended resolution):
**mark applied before deleting staged messages.** A crash between projection
and `applied` re-projects idempotently on the next commit delivery; a crash
between `applied` and deletion leaves deletable rows that any later cleanup
pass removes. Deleting before `applied` is durable is the one ordering that
can lose payloads (committed-not-applied header with missing messages) and is
prohibited. The payload-loss guard is therefore two rules: never delete a row
without a recorded clean `apply_state`, and never delete under a header that
is not `applied`.

After a clean apply, no information is lost by deletion: the value survives
verbatim in `property_values.<ppy_id>.value` with `txn_id`/`commit_id`/
`msg_uuid` provenance, and the durable header retains writer identity,
timestamps, and counts. A full payload archive (retaining even applied
message envelopes) remains a separate decision (8.6/7) that must be taken
before Task G ships deletion.

Known gap carried forward deliberately: nothing re-drives projection for a
committed transaction if no commit re-delivery arrives. A background sweep
over `committed`-not-`applied` headers is noted as an optional hardening task
(Task F acceptance records the decision) rather than a blocking part of this
plan.

### 8.4 Read-overlay boundary

- First implementation of generic object property reads (Task J) is
  root-only: read the object root, return `property_values` (and the legacy
  inline `properties` bag clearly separated). Correct-but-replaceable, per
  `DIRECTION.md`.
- Overlay (Task K) adds committed-but-unapplied visibility: for a subject
  object, overlay `msg` rows whose header state is `committed` (not
  `applied`), matching `data.object_id` (dotted-path query; denormalizing
  `object_id` onto `msg` rows is an optimization to take only if the dotted
  query proves insufficient). Overlay entries are marked as pending so
  callers can distinguish them from applied values.
- The `applied` watermark is what makes the overlay filter cheap and correct:
  `applied` headers contribute nothing; `committed` headers contribute their
  staged messages.
- Read-your-own-open-transaction support is explicitly deferred.

### 8.5 Follow-on task breakdown

Bounded tasks in implementation order. Letters are provisional; the director
assigns TASK numbers at creation. Every task keeps TASK-031/032/033 examples
and tests passing; nothing deletes or migrates existing data before Task L.

Director resequencing (2026-07-03): plan task E was pulled forward and
implemented as TASK-040, extended with single-inheritance type hierarchy
(`parent_type_id` on `typ` roots; the object-assignment registration gate
walks the chain, bounded and cycle-safe). Tasks A (TASK-039) and E (TASK-040)
are done; tasks B-D (usr resolution, writer persistence, txn/msg split) are
deferred, not canceled; F-L remain in the stated order.

Director resequencing (2026-07-04): the first slice of task L landed as
TASK-045 — the standalone `ppy` assignment-root write path and the
entity-only reader are retired, all assignments project onto object roots
(`entity_id` remains a deprecated wire alias for `ent` targets), and
canonical examples 07/08 use the object-targeted form. TASK-046 then completed the
identifier arc: canonical examples and remaining fixtures normalized to
three-letter collection segments and conformant IDs, and
`message.schema.json` hard-enforces the `ObjectId` pattern on submitted
`data.id` (the materializer warning stays as defense in depth). Remaining
task-L items: the inline-`properties`-bag endgame (decision 8.6/10),
`lnk` alignment (8.6/11), legacy message rows still in `txn`, and
eventual deletion/migration of historical standalone assignment roots.

| # | Task | Depends on |
|---|---|---|
| A | Bootstrap `usr~jdtp-admin` genesis ensure | — |
| B | `usr` identity resolution service + indexes | A |
| C | Durable transaction writer persistence | B |
| D | Split `txn` durable state from `msg` staging | — (C recommended first) |
| E | Object-targeted assignment projection onto object roots | D |
| F | Per-message outcomes + `applied` watermark | E |
| G | Guarded `msg` cleanup | F, decision 8.6/7 |
| H | Location types + container property definitions | E |
| I | Container review seed migration to typed assignments | H |
| J | Generic object property-value reads (root-only) | E (H/I for data) |
| K | Committed-but-unapplied overlay reads | F, J |
| L | Cleanup of transitional shapes | I, J (K recommended) |

- **A - Bootstrap `usr~jdtp-admin` genesis ensure.** New startup ensure
  service creates the reserved `usr` root (8.2.5) idempotently; new
  `instance_org`/`instance_grp` configuration; `genesis~jdtp-admin`
  provenance sentinel. No wire changes, no enum changes. Acceptance: ensure
  is idempotent across restarts; root matches the accepted root shape;
  integration spec proves insert-once semantics against MongoDB.
- **B - `usr` identity resolution service.** New
  `UsrIdentityService.resolveOrCreate(observedIdentity)` creating minimal
  `usr` roots (8.2.3); unique sparse indexes on `properties.orcid` and
  (`properties.oidc_issuer`,`properties.oidc_subject`); race-safe via
  duplicate-key retry. This is the project's first explicit index
  management; the ensure runs at startup beside Task A's. Acceptance: same
  ORCID resolves to one root under concurrent calls; system kind requires no
  external key.
- **C - Durable transaction writer persistence.** `openHeader` gains the
  8.2.4 resolution: `setOnInsert` `writer` (containing `user_id`), `org`,
  `grp`, `client`. `CommittedTransactionSnapshot`/read service surface the
  new fields additively. Confirm `kli` sends `txn.user` from the session
  ORCID; update `01-open-transaction.json` to carry `user` (schema already
  allows it). Acceptance: open with user → `writer.user_id` resolved plus
  snapshot; open without user → `unresolved` writer with null `user_id`,
  warning logged; existing headers without the fields still read.
- **D - Split `txn`/`msg`.** `appendDataMessage` writes to new `msg`
  collection (new constant); `CommittedTransactionReadService` reads the
  union of `msg` rows and legacy `txn` message rows (same sort, `msg`
  first); `commitHeader` records `message_count`; index `msg` on `txn_id`.
  Pure storage move: no state guards, no cleanup, legacy rows stay put.
  Acceptance: Kafka ingest spec passes with messages landing in `msg`;
  a pre-split transaction with messages still in `txn` still materializes.
- **E - Object-targeted assignment projection (keystone).** Vocabulary,
  examples, and materializer support for 8.2.7: shape-determined routing,
  generalized gate (`ent` + `loc`), dotted `$set
  property_values.<ppy_id>` with the 8.2.6 entry, duplicate rules mirroring
  `stripVolatileFields`. Legacy `entity_id`-only messages keep the
  standalone-root path byte-for-byte. New canonical example
  (`14-assign-property-value-object-form.json`) round-trips in
  `MessageSpec`. Acceptance: end-to-end Kafka spec materializes a `loc`
  property value with provenance; TASK-032 spec unchanged and green.
- **F - Outcomes + applied watermark.** Materializer records per-message
  `apply_state`/`applied_at` on `msg` rows; completeness check against
  `message_count`; header `state: "applied"`, `applied_at`, `apply_counts`;
  commit re-delivery short-circuits on `applied`. Decide (and record) whether
  a background sweep for committed-not-applied headers ships here or is
  deferred. Acceptance: a fully-applied transaction reaches `applied` with
  correct counts; a transaction with one skipped message reaches `applied`
  with the skip recorded and retained.
- **G - Guarded `msg` cleanup.** Deletion of `apply_state: "applied"` rows
  under `applied` headers only, per 8.3 rule; skips/conflicts retained.
  Blocked on director decision 8.6/7 (payload archive). Acceptance: crash
  simulation between applied-marking and deletion converges; no deletion
  path exists for non-applied rows.
- **H - Location types + container property definitions.** Genesis-authored
  transaction examples (writer = `jdtp-admin`) creating a `loc` type per
  container kind and `ppy` definitions for the container fields mapped in
  `clarity-esp-container-mapping.md` (`name`, `barcode`, `kind`, `format`,
  source facts); `typ + update add_property` registers them. Uses only
  existing message forms. Acceptance: examples validate and materialize;
  gate accepts a `loc`-targeted assignment for a registered property.
- **I - Container review seed migration.** TASK-036 seed emits typed `loc`
  creates (`type_id` set) plus object-targeted assignment messages instead
  of name-keyed bags; source-system provenance facts stay in the inline
  `properties` bag pending decision 8.6/10's endgame. Reconcile
  `clarity-esp-container-mapping.md`. Acceptance: seeded roots show
  `property_values` keyed by `ppy` ID; the container review inspection point
  displays the intended target shape.
- **J - Generic object property-value reads.** `ObjectPropertyValuesReadService`
  reading any supported object root's `property_values` (+ definition-name
  join like the entity reader); thin routes per existing style (e.g.
  `GET /api/locations/{id}/property-values`). The entity-specific reader and
  route stay untouched until Task L. Acceptance: `loc` and `ent` reads
  return typed values with provenance; missing root → 404, matching the
  resource-read convention.
- **K - Overlay reads.** Extend Task J's service with the 8.4 overlay of
  committed-but-unapplied `msg` rows, marked pending in the response.
  Acceptance: a committed-not-yet-applied assignment is visible via overlay
  and disappears from the overlay (persisting on the root) once applied.
- **L - Cleanup of transitional shapes.** Deliberate removals once typed
  container data is reviewed: retire the legacy `entity_id` standalone-root
  assignment path and its examples; swap or remove the entity-only reader;
  decide the endgame for the name-keyed inline bag (retire, or narrowly
  reserve for source/provenance facts); migrate or drop legacy message rows
  still in `txn`; reconcile `lnk` instance properties or document the
  exception (8.6/11); update vocabulary docs and materializer Javadoc.
  Acceptance: one documented list of removed behaviors; full Gradle suite
  green without legacy fixtures.

### 8.6 Decision table for director review

Numbers match section 7's open decisions. "Recommended" means the plan
proceeds with this answer unless the director overrides at task creation.
Director review on 2026-07-03 ratified the table as written; the only review
amendment was structural (nest `user_id` inside `writer`, reflected in 8.2.1
and 8.2.4).

| # | Decision | Recommendation | Consumed by |
|---|---|---|---|
| 1 | Staging collection name | `msg`; storage-only, never in the wire enum | D |
| 2 | Delete before/after `applied` | After; two-rule guard in 8.3 | F, G |
| 3 | Value-entry shape | 8.2.6: `value` + required `txn_id`/`commit_id`, `msg_uuid` retained, `applied_at` BSON date | E |
| 4 | Minimal `usr` shape / keys | 8.2.3: ORCID primary when present, issuer+subject pair otherwise; `kind` for service/system | B, C |
| 5 | `jdtp-admin` ID + genesis rule | 8.2.5: `<org>~<grp>~genesis~usr~jdtp-admin`, startup ensure, `genesis~` sentinel | A |
| 6 | Membership representation | Defer mechanism; nothing in A-L needs it | (future) |
| 7 | Payload archive after cleanup | None in first pass; skips/conflicts retained is the partial archive; MUST be ratified before G ships | G |
| 8 | Assignment message shape | 8.2.7: explicit `object_collection` + `object_id`; no ID parsing; `data.id` optional in object form | E |
| 9 | Where permission checks live | Not in first implementation; `ppy` owns policy, `txn` records writer; enforcement unscheduled | (future) |
| 10 | `properties` vs `property_values` | New `property_values` map; endgame naming decided at L | E, I, J, L |
| 11 | `lnk` alignment timing | Documented exception until after L | L |

### 8.7 Terminology and documentation checklist

Wording rules for every doc/schema touched by A-L:

- `msg` is introduced as "transient transaction-message staging"; never
  listed among domain collections; never added to the wire `Collection` enum
  or `kli --collection` values.
- `usr` is introduced as "local identity/audit records"; never described as
  an authentication provider; explicitly no passwords or tokens. Adding
  `usr` to the wire enum is deferred until externally-authored `usr`
  transactions are needed.
- The `TransactionMessagePersistenceService` Javadoc's "write-ahead log"
  phrasing becomes "durable transaction record plus staged messages" at
  Task D.
- Per-task doc touches: A/B/C update `docs/user-authentication.md` and the
  vocabulary doc's user/writer sections from target-direction to
  current-behavior; D-G update the vocabulary doc's Transaction Records
  section; E updates the Property Value Assignment section and examples;
  H/I update `clarity-esp-container-mapping.md`; J/K extend the read-surface
  map table; L sweeps remaining "current implementation note" paragraphs.
- `DIRECTION.md` and the design brief already state the target model and
  need no changes from this prework.
