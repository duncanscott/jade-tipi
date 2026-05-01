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

These examples are exercised by `MessageSpec` to round-trip through `JsonMapper` and pass `Message.validate()` against `message.schema.json`.

## CLI Surface

`kli` (`clients/kafka-kli`) threads `collection` through all transaction-message commands:

- `kli open`, `kli rollback`, `kli commit` hardcode `collection = txn`.
- `kli create`, `kli update`, `kli delete` require `--collection <abbr>` (alias `-c`). The value is parsed via the `Collection` enum; unknown abbreviations and the reserved `txn` value fail with a clear error, as does any action/collection combination that violates the per-collection action whitelist.
- `kli publish --file …` deserializes the envelope unchanged and warns when the file omits `collection`, so missing fields are surfaced rather than silently dropped on the wire.
