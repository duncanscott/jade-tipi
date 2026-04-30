# Kafka Transaction Message Vocabulary

This note captures the first backend-facing vocabulary for Kafka-submitted Jade-Tipi transaction messages. The goal is to make transaction records in MongoDB self-describing enough that a later consumer, reader, and materializer do not need to infer domain intent from arbitrary payload conventions.

## Message Envelope

Every submitted message uses the DTO `Message` envelope and should carry a first-class target collection:

```json
{
  "txn": { "uuid": "...", "group": { "org": "lbl_gov", "grp": "jgi_pps" }, "client": "kafka-kli" },
  "uuid": "...",
  "collection": "ppy",
  "action": "create",
  "data": {}
}
```

`collection` is the Jade-Tipi collection abbreviation: `ent`, `ppy`, `lnk`, `uni`, `grp`, `typ`, `vdn`, or `txn`. The backend stores it explicitly in `txn` message documents.

## Transaction Records

The `txn` MongoDB collection should contain two record kinds:

- Transaction header: `_id = txn_id`, `record_type = "transaction"`.
- Message record: `_id = txn_id + "~" + msg_uuid`, `record_type = "message"`.

The header is the authoritative source for commit state. Once the header has a `commit_id`, the transaction is committed. Message-level `commit_id` may be added later as a read optimization, but readers must be able to resolve visibility through the header.

## Property Definitions

Properties are first-class documents in `ppy`. A property definition names the property and defines the JSON object shape expected for assigned values.

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

All property values are JSON objects. Scalar values should be wrapped, for example `{ "text": "barcode-1" }`, `{ "number": 10, "unit_id": "..." }`, or `{ "boolean": true }`.

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

Early backend validation should verify required envelope fields, known collection/action pairs, and object-shaped property values. Full reference validation can follow once snapshot reads over `txn` exist.
