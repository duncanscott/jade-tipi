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

`collection` is the Jade-Tipi collection abbreviation: `ent`, `ppy`, `lnk`, `loc`, `uni`, `grp`, `typ`, `vdn`, or `txn`. The backend stores it explicitly in `txn` message documents.

`txn`, `uuid`, `collection`, and `action` are all required by `message.schema.json`. The schema also enforces action/collection compatibility:

- `collection: txn` → `action ∈ {open, rollback, commit}`.
- `collection ∈ {ent, ppy, lnk, loc, uni, grp, typ, vdn}` → `action ∈ {create, update, delete}`.

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

The `txn` MongoDB collection should contain two record kinds:

- Transaction header: `_id = txn_id`, `record_type = "transaction"`.
- Message record: `_id = txn_id + "~" + msg_uuid`, `record_type = "message"`. Each message record stores the submitted envelope, including `collection`, so materializers and readers do not have to infer the target collection from payload fields.

The header is the authoritative source for commit state. Once the header has a `commit_id`, the transaction is committed. Message-level `commit_id` may be added later as a read optimization, but readers must be able to resolve visibility through the header.

## Property Definitions

Properties are first-class documents in `ppy`. A property definition names the property and defines the JSON object shape expected for assigned values. Definitions and assignments share `collection: "ppy"` and are distinguished by `data.kind` (`definition` vs. `assignment`); a separate assignment collection is intentionally not introduced in this vocabulary.

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "definition",
    "id": "lbl_gov~jgi_pps~...~pp~barcode",
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

## Types And Properties

Entity types live in `typ`. A type can be created independently and then updated to include a property reference.

```json
{
  "collection": "typ",
  "action": "update",
  "data": {
    "id": "lbl_gov~jgi_pps~...~ty~plate_96",
    "operation": "add_property",
    "property_id": "lbl_gov~jgi_pps~...~pp~barcode",
    "required": true
  }
}
```

The materialized type document should eventually include property references, not embedded property definitions.

## Entity Creation

Entities live in `ent` and reference a type.

```json
{
  "collection": "ent",
  "action": "create",
  "data": {
    "id": "lbl_gov~jgi_pps~...~en~plate_a",
    "type_id": "lbl_gov~jgi_pps~...~ty~plate_96"
  }
}
```

## Property Value Assignment

A property assignment is stored as a property record whose ID is the entity ID plus the property ID. The assignment payload references both sides and stores the value object.

```json
{
  "collection": "ppy",
  "action": "create",
  "data": {
    "kind": "assignment",
    "id": "lbl_gov~jgi_pps~...~en~plate_a~lbl_gov~jgi_pps~...~pp~barcode",
    "entity_id": "lbl_gov~jgi_pps~...~en~plate_a",
    "property_id": "lbl_gov~jgi_pps~...~pp~barcode",
    "value": {
      "text": "barcode-1"
    }
  }
}
```

Early backend validation should verify required envelope fields, known collection/action pairs, and object-shaped property values. Full reference validation between properties, types, entities, and assignments — and value-shape validation against the registered property `value_schema` — can follow once snapshot reads over `txn` exist.

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

## Group Records And First-Pass Permissions

`grp` records are first-class Jade-Tipi objects with world-unique IDs. The
canonical wire payload carries `id`, `name`, an optional `description`, and an
optional `permissions` map keyed by world-unique grp IDs. Each permission value
is exactly `"rw"` (read/write) or `"r"` (read-only). The map names other
groups; ownership-group access is implicit and not represented as a self-entry.

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
membership from Keycloak or any other identity provider, and does not
introduce object-level or property-value-level permission overrides.

## Committed Materialization Of Locations And Links

Once a transaction commits in `txn`, a post-commit projection currently materializes `loc + create`, `typ + create` (where `data.kind == "link_type"`), `lnk + create`, and `grp + create` messages into their long-term collections (`loc`, `typ`, `lnk`, `grp`). The projection is a read-after-commit step over the existing committed-snapshot read service; the `txn` write-ahead log remains the durable, authoritative record. Other collections, other actions, and bare entity-type `typ` records are intentionally not materialized in this iteration.

The current materializer writes the accepted root-document shape from `DIRECTION.md`: one logical Jade-Tipi object normally stored as one root document with top-level `_id`, `id`, `collection`, `type_id`, explicit `properties`, denormalized `links`, and reserved `_head.provenance` metadata. Duplicate `_id` writes with an identical payload are idempotent successes; differing-payload duplicates are logged and counted but not overwritten, and missing or blank `data.id` is logged and skipped without synthesizing an id. Semantic reference validation (`type_id`, `left`, `right`, and `allowed_*_collections`) is still not enforced; that remains a follow-up reader/validator concern.

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

## Reference Examples

A complete early transaction flow is bundled as resources under `libraries/jade-tipi-dto/src/main/resources/example/message/`:

1. `01-open-transaction.json`
2. `02-create-property-definition-text.json`
3. `03-create-property-definition-numeric.json`
4. `04-create-entity-type.json`
5. `05-update-entity-type-add-property.json`
6. `06-create-entity.json`
7. `07-assign-property-value-text.json`
8. `08-assign-property-value-number.json`
9. `09-commit-transaction.json`
10. `10-create-location.json`
11. `11-create-contents-type.json`
12. `12-create-contents-link-plate-sample.json`
13. `13-create-group.json`

These examples are exercised by `MessageSpec` to round-trip through `JsonMapper` and pass `Message.validate()` against `message.schema.json`.

## CLI Surface

`kli` (`clients/kafka-kli`) threads `collection` through all transaction-message commands:

- `kli open`, `kli rollback`, `kli commit` hardcode `collection = txn`.
- `kli create`, `kli update`, `kli delete` require `--collection <abbr>` (alias `-c`). The value is parsed via the `Collection` enum; unknown abbreviations and the reserved `txn` value fail with a clear error, as does any action/collection combination that violates the per-collection action whitelist.
- `kli publish --file …` deserializes the envelope unchanged and warns when the file omits `collection`, so missing fields are surfaced rather than silently dropped on the wire.
