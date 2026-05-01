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

## Objects, Types, And Properties

A member of a long-term collection is a Jade-Tipi object, not necessarily an
`ent` entity. `ent`, `loc`, `lnk`, `ppy`, `typ`, `uni`, `grp`, and `vdn` are
peer domain collections. `txn` contains transaction records rather than normal
domain objects.

For initial implementation, model each object as a typed collection of explicit
property-value assignments. The object's `type_id` points to a `typ` record that
defines which properties may be assigned to objects of that type. A property
must be added to the type before clients may assign that property to an object
of the type.

Do not implement required properties or default values yet. If a property value
is not explicitly assigned in a create or update message, it is absent. The
materializer should not invent property values.

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
pages, pending pages, and committed transaction records that have not yet been
materialized. That overlay model is future work; the first implementation should
make the root-only case correct and easy to replace.

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
