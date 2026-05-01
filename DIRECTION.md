# Jade-Tipi Direction

This document records current product and architecture direction that is not yet
fully implemented. Treat it as guidance for task design, not as a finalized
protocol specification.

## Location Collection

Jade-Tipi should add a first-class `loc` collection for physical and addressable
locations. In a biology laboratory this includes buildings, rooms, freezers,
freezer shelves, racks, boxes, tubes, plates, and possibly individual wells.

`loc` is a long-term materialized collection alongside `ent`, `ppy`, `lnk`,
`uni`, `grp`, `typ`, and `vdn`. The `txn` collection remains special: it is the
durable transaction log and staging collection, not a normal domain collection.

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
