# Jade-Tipi object model design brief

This brief captures human design direction from the May 2026 discussion. It is
not a final schema. Agents should use it to propose concrete alternatives and
surface tradeoffs before implementation.

## Core Terms

- A member of a Jade-Tipi collection is an **object**. Not every object is an
  `ent`.
- Objects are collections of property-value assignments plus relationships to
  other objects.
- A type definition declares the properties that may be assigned to objects of
  that type. For now, avoid required/optional property complexity and avoid
  default values.
- A property may be assigned only after the object's type definition permits
  that property.

## Persistence Direction

- JSON documents persisted in MongoDB collections are not simple copies of
  transaction messages.
- Start with a simple root-document shape: `_head`, `properties`, and `links`.
- `_head` should group functional metadata separate from user/domain
  properties and links.
- Extension documents for unbounded properties/links remain future work. The
  design may mention them, but implementation should assume most examples fit
  in a root document.
- Transaction-overlay semantics remain important: reads eventually need to
  consider committed data in long-term collections plus committed transaction
  records not yet materialized.

## Kafka Submission Direction

- Kafka remains the preferred first submission path because it exercises
  ordering, replay, idempotency, and transaction-log durability directly.
- HTTP submission should later be a thin adapter over the same service behavior,
  not an alternate object model.
- Messages must stay easy for humans to read and create. Use top-level
  `collection` and `action`, keep the submitted object in `data`, and avoid
  nested operation DSLs.
- A first-pass `loc + create` should allow
  `data: { id, type_id?, properties, links? }`, where `properties` is a plain
  JSON object and `links` is usually empty.
- Relationship creation should use explicit `lnk + create` messages rather than
  hidden side effects in a `loc` message.

## Location and Link Modeling

- `loc` should represent physical or logical locations and containers: tubes,
  plates, wells, boxes, shelves, freezers, rooms, buildings, etc.
- Parent/contents relationships should primarily live in `lnk`, not as a
  duplicated `parent_location_id` on each `loc`.
- A `lnk` object should stay conceptually simple: left pointer, right pointer,
  type, and instance properties.
- A `contents` link type can define directional meaning such as `contains` and
  `contained_by`; those labels belong to the link type, not repeated on every
  link instance.
- For plates, well position is a strong candidate for a property on a
  `contents` link, for example `position: { "kind": "well", "label": "A1" }`.
  Agents may compare this with modeling each well as a child `loc`, but should
  justify the choice.

## Clarity and ESP Context

- Clarity LIMS entities are available in the local `clarity` CouchDB database.
  Clarity exposed XML APIs; the local JSON is a translation of those XML
  entities.
- Clarity appears to contain containers such as tubes and plates, but may not
  model physical locations for those containers.
- Some Clarity entities were migrated to ESP and may appear in local
  `esp-entity`. ESP container records may include locations or containment.
- Full Clarity/ESP import and synchronization are already underway elsewhere
  and are not part of Jade-Tipi task work here. Use local replicated data only
  as source evidence for object-model proposals.

## Proposal Expectations

Agent proposals should include:

- A recommended JSON shape for `loc`, `lnk`, and link type objects.
- Alternatives where the model is not obvious, especially plate wells.
- Example root documents for one or two real container cases.
- Clear separation between canonical Jade-Tipi fields and source-system
  provenance fields.
- Known ambiguities and decisions requiring human review.
- No implementation until the object model direction is accepted.
