# Clarity / ESP container materialization mapping

Design pre-work for `TASK-019`. Maps a tiny number of real, locally
replicated `clarity` and `esp-entity` CouchDB records to root-shaped
Jade-Tipi `loc`/`lnk` MongoDB documents. Sample selection, mapping
decisions, and verification commands live here so the prototype can be
implemented in a later, director-gated step without re-deriving them.

This document is design-only. No CouchDB writes, no production reads,
no implementation changes accompany it. The prototype implementation
contemplated by `TASK-019` would add only test-only artifacts that
exercise the existing `CommittedTransactionMaterializer` against the
canonical messages shown below.

## Anchored constraints

These come from already-accepted artifacts in the worktree and bound
the proposed mapping.

1. **Root document shape** (per `CommittedTransactionMaterializer`,
   `TASK-013/014`). Every materialized root has `_id`, `id`,
   `collection`, top-level `type_id`, explicit `properties`,
   denormalized `links: {}`, and `_head` with `schema_version`,
   `document_kind=root`, `root_id`, and `provenance` (`txn_id`,
   `commit_id`, `msg_uuid`, `collection`, `action`, `committed_at`,
   `materialized_at`). For `lnk` roots, `left`, `right`, and instance
   `properties` are top-level. For non-`lnk` roots, all `data` fields
   except `id`/`type_id` are copied to `properties`.
2. **Materializer surface**. Only `loc + create`,
   `typ + create` with `data.kind == "link_type"`, and `lnk + create`
   are materialized. Everything else is `skippedUnsupported`. There is
   no `ent + create` materialization yet, no update/delete replay, and
   no semantic reference validation.
3. **Contents type resolution** (per `ContentsLinkReadService` and
   canonical `11-create-contents-type.json`). The `contents` link type
   is the `typ` root with `properties.kind == "link_type"` and
   `properties.name == "contents"`, with
   `allowed_left_collections = ["loc"]` and
   `allowed_right_collections = ["loc", "ent"]`. The read service
   resolves type id by name, so reusing the canonical id or minting a
   fresh one with the same `name` both work.
4. **Position-on-link precedent** (canonical
   `12-create-contents-link-plate-sample.json`). The accepted contents
   link carries `properties.position = { kind, label, row, column }`
   and the materializer copies `lnk.properties` verbatim.
5. **Identifier convention** (every canonical example). Root ids use
   `<org>~<grp>~<txn-uuid>~<collection>~<short-name>`. The short-name
   segment is human-readable and may embed the source key.

## Source sampling — what is in local CouchDB right now

Local CouchDB at `http://127.0.0.1:5984` was reachable on
`2026-05-02` for this worktree. Both replicated databases were
populated:

| db          | doc_count | active bytes |
|-------------|-----------|--------------|
| `clarity`   | 2,608,824 | ~40.3 GB     |
| `esp-entity`| 1,510,835 | ~10.1 GB     |

ID conventions observed in the first scanned pages:

- `clarity`: `_design/*`, `artifactgroups_<n>`, `artifacts_<n>`,
  `containers_27-<lims-numeric-id>`, all using
  `<resource_plural>_<lims-id>` keys.
- `esp-entity`: UUIDv7 keys with rich top-level documents whose
  `class_name` distinguishes containers from operating procedures,
  samples, etc.

Two representative records were chosen for the prototype.

### Sample C1 — Clarity tube `27-10000`

CouchDB id: `containers_27-10000`. Fetched via
`GET /clarity/containers_27-10000`. Redacted skeleton (UDF values
omitted):

```json
{
  "_id": "containers_27-10000",
  "limsid": "27-10000",
  "uri":  "https://jgi-prd.claritylims.com/api/v2/containers/27-10000",
  "url":  "https://jgi-prd.claritylims.com/api/v2/containers/27-10000",
  "path": "containers/27-10000",
  "xml":  "<con:container limsid=\"27-10000\" uri=\"...\">\n    <name>27-170230</name>\n    <type name=\"Tube\" uri=\".../containertypes/2\"/>\n    <occupied-wells>1</occupied-wells>\n    <placement limsid=\"NPR578A22PA1\" uri=\".../artifacts/NPR578A22PA1\">\n      <value>1:1</value>\n    </placement>\n    <udf:field name=\"Sample QC Result\" type=\"String\">REDACTED</udf:field>\n    <udf:field name=\"Label\" type=\"String\">REDACTED</udf:field>\n    <udf:field name=\"Location\" type=\"String\">REDACTED</udf:field>\n    <udf:field name=\"Location Date\" type=\"Date\">REDACTED</udf:field>\n    <state>Populated</state>\n  </con:container>",
  "json": {
    "limsid": "27-10000",
    "name":   "27-170230",
    "type":   { "name": "Tube" },
    "occupied-wells": "1",
    "placement": [ { "limsid": "NPR578A22PA1", "value": "1:1" } ],
    "state": "Populated",
    "field": [ /* same UDFs as <udf:field> elements in xml */ ]
  }
}
```

Field-path takeaways:

- `json.type.name` = `"Tube"` — container kind.
- `json.name` = `"27-170230"` — human-readable container name.
- `json.limsid` (and the surrounding `_id`) = `"27-10000"` — stable
  Clarity LIMSID; embed in Jade-Tipi id and keep as `properties.source_id`.
- `json.state` = `"Populated"` — Clarity-specific occupancy hint.
- `json.placement[0]` references an artifact (`limsid` =
  `"NPR578A22PA1"`, position `1:1`). Clarity's `placement` is the
  per-tube position of an artifact (a sample/library), not a physical
  location of the tube itself.
- **No physical location anywhere.** Clarity tubes do not record what
  freezer/shelf/bin holds them. ESP is the source for that.

### Sample E1 — ESP freezer / bin / plate chain

ESP records have a uniform shape. Each `class_name == "Container"` doc
carries a parent reference at `container` (with the parent's `slot`
for this container) and a children-by-position map at `contents`. The
chain selected here was discovered by following one ESP plate up to
its parent bin and then to that bin's parent freezer.

Three records, redacted to the shape relevant to mapping.

**E1a — Freezer**, derived from the `container` reference of the bin
record below. Identified ESP UUID:
`019a3a62-8fa8-74d8-ad5c-c7f294c9a331`. Inferred summary fields from
the embedded reference seen on the bin (`name = "Illumina 130-32"`,
`barcode = "FREEZE012"`, `type_name = "Freezer (6-shelf)"`,
`type_uuid = "019a3a49-6dd8-7dcc-af68-130207d9a1de"`). The freezer's
own ESP record was not pulled in this pre-work because the bin
already carries the embedded reference; the prototype treats the
freezer as a top-level `loc` with `container = null`.

**E1b — Bin** (`PP050`). CouchDB id:
`019a3a60-9628-7c90-bc47-f40518a12127`. Skeleton:

```json
{
  "_id": "019a3a60-9628-7c90-bc47-f40518a12127",
  "uuid": "019a3a60-9628-7c90-bc47-f40518a12127",
  "name": "PP050",
  "type_name": "Bin 9x3",
  "type_uuid": "019a3a49-3672-73ec-842d-6c21c5ad9be7",
  "class_name": "Container",
  "barcode": "BIN057",
  "numeric_id": 50,
  "container": {
    "uuid":      "019a3a62-8fa8-74d8-ad5c-c7f294c9a331",
    "barcode":   "FREEZE012",
    "name":      "Illumina 130-32",
    "type_uuid": "019a3a49-6dd8-7dcc-af68-130207d9a1de",
    "type_name": "Freezer (6-shelf)",
    "label":     null,
    "slot":      "2"
  },
  "contents": {
    "A1": { "uuid": "019a420c-728d-7f4c-a817-cd8ba13a1e36",
            "name": "27-474501",  "barcode": "27-474501",
            "type_uuid": "019a3ac2-b494-71ac-82cc-fadc028be18f",
            "type_name": "96W Plate" },
    "A2": { "uuid": "...", "name": "...", "barcode": "...",
            "type_name": "96W Plate" },
    "...": "twelve total entries A1..D3"
  },
  "parents":  [],
  "children": []
}
```

**E1c — Plate at A1 of PP050** (`27-474501`). CouchDB id:
`019a420c-728d-7f4c-a817-cd8ba13a1e36`. Skeleton:

```json
{
  "_id": "019a420c-728d-7f4c-a817-cd8ba13a1e36",
  "uuid": "019a420c-728d-7f4c-a817-cd8ba13a1e36",
  "name": "27-474501",
  "type_name": "96W Plate",
  "class_name": "Container",
  "barcode": "27-474501",
  "numeric_id": 474501,
  "container": {
    "uuid":      "019a3a60-9628-7c90-bc47-f40518a12127",
    "barcode":   "BIN057",
    "name":      "PP050",
    "type_uuid": "019a3a49-3672-73ec-842d-6c21c5ad9be7",
    "type_name": "Bin 9x3",
    "label":     null,
    "slot":      "A1"
  },
  "contents": {
    "A2":  { "uuid": "019a420b-a021-7332-a375-e348af611ac8",
             "name": "LHCPOT", "barcode": "27-474501",
             "type_uuid": "019a3a49-4aa2-72f7-889e-17f1c58e2224",
             "type_name": "Illumina Library" },
    "A10": { "uuid": "...", "name": "LHCPUY", "type_name": "Illumina Library" },
    "...": "80 entries A2..H10, all Illumina Library entities"
  },
  "parents":  [],
  "children": []
}
```

Field-path takeaways:

- ESP `container.uuid` and `container.slot` together encode the
  parent's identity and the position of *this* container within that
  parent. This is the canonical place to read parent → child
  containment.
- ESP `contents` is a positional map keyed by row/column labels
  (`A1`, `A2`, ..., `H12` for plates; `A1..D3` for 9×3 bins). Each
  value carries `uuid`, `name`, `barcode`, `type_uuid`, `type_name`.
- Items in `Plate.contents` are not necessarily containers. The
  `type_name` field includes `Illumina Library` and `Nucleic Acid` —
  these are biological/analyte entities, natural future `ent` roots.
- `parents` and `children` arrays were both empty for the records
  sampled. The authoritative parent edge is `container`; the
  authoritative child edges are the keys of `contents`.

## Mapping decisions

Decisions resolved against the sampled records, not the abstract
schema. Each decision lists its reason from the source data.

### D1 — Stay at `loc` + `lnk` for the prototype, no `ent`

The director's pre-work review highlighted that the materializer
supports `loc`, `typ link_type`, and `lnk` roots only. The selected
prototype is built from records that are all `class_name == "Container"`
in ESP or `containers_*` in Clarity, so every materialized root in the
prototype is a `loc` or `lnk` and the existing materializer is
sufficient. **No `ent` root is part of the prototype**, and no
materializer-expansion task is required.

The biological/analyte items observed in `Plate.contents` (Illumina
Library, Nucleic Acid) are intentionally **excluded** from this
prototype. Including them would either need (a) a separate task to
add `ent + create` materialization and then a follow-up sampling pass
on ESP analyte records, or (b) modeling them as `loc` roots, which
would distort the model since they are samples, not locations. Both
are out of scope here.

### D2 — Plate wells stay as `lnk.properties.position`, not as child `loc` roots

The directive listed three options. The sampled ESP plate (`PP050`'s
A1 plate `27-474501`) shows wells encoded as **map keys in the
parent's `contents` object**, not as independent ESP documents with
their own UUIDs and lifecycle. The 80 wells observed in plate
`27-474501.contents` are `class_name == null` and have no
container-shaped fields; they are the contained items, not addressable
locations.

Recommendation: model the position on the `lnk + contents` link as
`properties.position = { kind, label, ... }`, mirroring the canonical
example `12-create-contents-link-plate-sample.json`. ESP itself
encodes well position the same way (key in the parent's `contents`
map), so the mapping is one-to-one.

Bin slots (`A1..D3` for `Bin 9x3`) and freezer shelves
(`slot: "2"` on the bin's parent reference) follow the same rule:
position is a property of the containment link, not a separate root.

### D3 — Position vocabulary

`properties.position.kind` is set per parent-container kind so that
later read-side consumers can render or validate position labels per
plate/bin/freezer shape:

| Parent container kind     | `position.kind`   | Label format      | Extra fields                         |
|---------------------------|-------------------|-------------------|--------------------------------------|
| `Freezer (6-shelf)` (ESP) | `freezer_slot`    | numeric string    | `slot: <int>`                        |
| `Bin 9x3` (ESP)           | `bin_slot`        | `<row><column>`   | `row: "A".."I"`, `column: 1..3`      |
| `96W Plate` (ESP)         | `plate_well`      | `<row><column>`   | `row: "A".."H"`, `column: 1..12`     |
| `Tube` (Clarity)          | `tube_position`   | `<row>:<column>`  | `row: 1`, `column: 1` (Clarity uses `1:1`) |

The plate-well casing (`"A1"`, `"A"`, `1`) follows the canonical
example. The tube-position label `"1:1"` mirrors Clarity's
`<placement><value>` literal and parses to `row: 1, column: 1`.

### D4 — Identifier convention for source-mapped roots

Embed the source key in the short-name segment so the materialized id
is traceable to source. Also keep it as `properties.source_id` for
queryability and as `properties.source_system` for disambiguation.

Examples (using a single fabricated transaction UUIDv7
`018fd849-c0c0-7000-8a01-c1a141e5e500`):

- ESP freezer:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8`
- ESP bin:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628`
- ESP plate:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d`
- Clarity tube:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~clarity_tube_27-10000`
- Bin → Plate contents link:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_bin_pp050_to_plate_27-474501_a1`
- Freezer → Bin contents link:
  `jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2`

Short-name segments are truncated UUID prefixes for ESP (full UUID
embedded inside `properties.source_id`); Clarity uses the LIMSID
verbatim.

### D5 — Reuse the canonical `contents` `typ` id

The prototype reuses
`jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents`
from the canonical example so the `typ` row does not need to be
re-asserted in prototype fixtures and `ContentsLinkReadService` keys
off `properties.kind = "link_type"` and `properties.name = "contents"`
regardless of id. The `typ + create` message is included in the
prototype transaction below for self-containment; on a duplicate id
the materializer's idempotent-duplicate path covers re-runs.

## Selected prototype — examples and source-to-Mongo mapping

Two examples are proposed:

- **Example A (ESP chain).** Three `loc` roots (Freezer, Bin, Plate)
  with two `lnk + contents` edges (Freezer→Bin, Bin→Plate). Exercises
  multi-level containment and the position-on-link rule for both
  freezer slots and bin slots.
- **Example B (Clarity tube).** One `loc` root for the Clarity tube,
  no contents link. Exercises the "Clarity has no physical location"
  case and demonstrates source-traceability via embedded LIMSID.
  Sample analytes inside the tube (artifact `NPR578A22PA1`) are
  intentionally not materialized in this prototype because they are
  `ent`-class biological objects.

### Mapping table for Example A — ESP Freezer / Bin / Plate

| Source field (ESP)                    | Jade-Tipi root field            | Notes |
|---------------------------------------|---------------------------------|-------|
| Bin `_id`/`uuid` `019a3a60-9628-...`  | `loc.id` short-name + `properties.source_id` | UUIDv7; keep both for traceability. |
| Bin `name` `"PP050"`                  | `loc.properties.name`           | Human label. |
| Bin `barcode` `"BIN057"`              | `loc.properties.barcode`        | |
| Bin `type_name` `"Bin 9x3"`           | `loc.properties.kind`           | Mapped from ESP type label. |
| Bin `type_uuid`                       | `loc.properties.source_type_id` | Optional; preserves ESP type ref. |
| Bin `numeric_id` `50`                 | `loc.properties.source_numeric_id` | Optional; preserves ESP numeric id. |
| Bin `container.uuid`                  | parent `loc.id` (Freezer)       | Drives the Freezer→Bin link. |
| Bin `container.slot` `"2"`            | `lnk.properties.position`       | `kind: "freezer_slot", label: "2", slot: 2`. |
| Bin `contents."A1".uuid`              | child `loc.id` (Plate)          | Drives the Bin→Plate link. |
| Bin `contents."A1"` map key `"A1"`    | `lnk.properties.position`       | `kind: "bin_slot", label: "A1", row: "A", column: 1`. |
| Bin `parents`/`children`              | intentionally dropped           | Empty in samples; authoritative edge is `container`/`contents`. |
| Plate `contents.*` (Illumina Library) | intentionally dropped           | `ent` candidates; out of scope per D1. |
| ESP `app_*`, `enrichment_*`, sheets   | intentionally dropped           | Pipeline metadata, not container facts. |

### Mapping table for Example B — Clarity Tube

| Source field (Clarity)                | Jade-Tipi root field            | Notes |
|---------------------------------------|---------------------------------|-------|
| `_id` `containers_27-10000` / `limsid` `"27-10000"` | `loc.id` short-name + `properties.source_id` | Stable Clarity LIMSID. |
| `json.name` `"27-170230"`             | `loc.properties.name`           | Tube label. |
| `json.type.name` `"Tube"`             | `loc.properties.kind`           | Container kind. |
| `json.state` `"Populated"`            | `loc.properties.source_state`   | Clarity-specific occupancy hint; preserved verbatim. |
| `json.placement[*]`                   | intentionally dropped           | Points to `ent`-class artifacts (samples/libraries); out of scope per D1. |
| `json.field[*]` (UDFs)                | intentionally dropped           | Not container-shape facts; out of scope for the prototype. |
| `xml`, `uri`, `url`, `path`, `index`, `start`, `update`, `initialized` | intentionally dropped | Replication/source metadata; not part of the container model. |
| Physical location                     | absent in source                | Clarity does not record where the tube is physically. |

## Materialized root documents (proposed prototype output)

All five proposed prototype roots, in the exact shape that
`CommittedTransactionMaterializer` would produce. Provenance fields
are realistic placeholders — the producing transaction's UUID,
`commit_id`, and `committed_at` would be supplied by Kafka/Mongo at
materialization time. The volatile `_head.provenance.materialized_at`
is shown as `<materialized_at>` because it is the only field the
duplicate-detection comparison ignores.

Common provenance shorthand used below:

```json
"_head": {
  "schema_version": 1,
  "document_kind": "root",
  "root_id": "<id>",
  "provenance": {
    "txn_id":         "018fd849-c0c0-7000-8a01-c1a141e5e500",
    "commit_id":      "0000000000000001",
    "msg_uuid":       "<msg_uuid>",
    "collection":     "<collection>",
    "action":         "create",
    "committed_at":   "2026-05-02T03:50:00Z",
    "materialized_at": "<materialized_at>"
  }
}
```

### Loc 1 — ESP Freezer (`Illumina 130-32`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8",
  "collection": "loc",
  "type_id": null,
  "properties": {
    "name":             "Illumina 130-32",
    "kind":             "Freezer (6-shelf)",
    "barcode":          "FREEZE012",
    "source_system":    "esp-entity",
    "source_id":        "019a3a62-8fa8-74d8-ad5c-c7f294c9a331",
    "source_type_id":   "019a3a49-6dd8-7dcc-af68-130207d9a1de"
  },
  "links": {},
  "_head": { /* as above with collection="loc", action="create" */ }
}
```

### Loc 2 — ESP Bin (`PP050`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
  "collection": "loc",
  "type_id": null,
  "properties": {
    "name":              "PP050",
    "kind":              "Bin 9x3",
    "barcode":           "BIN057",
    "source_system":     "esp-entity",
    "source_id":         "019a3a60-9628-7c90-bc47-f40518a12127",
    "source_type_id":    "019a3a49-3672-73ec-842d-6c21c5ad9be7",
    "source_numeric_id": 50
  },
  "links": {},
  "_head": { /* as above */ }
}
```

### Loc 3 — ESP Plate (`27-474501`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d",
  "collection": "loc",
  "type_id": null,
  "properties": {
    "name":              "27-474501",
    "kind":              "96W Plate",
    "barcode":           "27-474501",
    "format":            "96-well",
    "rows":              8,
    "columns":           12,
    "source_system":     "esp-entity",
    "source_id":         "019a420c-728d-7f4c-a817-cd8ba13a1e36",
    "source_type_id":    "019a3ac2-b494-71ac-82cc-fadc028be18f",
    "source_numeric_id": 474501
  },
  "links": {},
  "_head": { /* as above */ }
}
```

### Loc 4 — Clarity Tube (`27-170230`, LIMSID `27-10000`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~clarity_tube_27-10000",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~clarity_tube_27-10000",
  "collection": "loc",
  "type_id": null,
  "properties": {
    "name":          "27-170230",
    "kind":          "Tube",
    "source_system": "clarity",
    "source_id":     "27-10000",
    "source_state":  "Populated"
  },
  "links": {},
  "_head": { /* as above */ }
}
```

### Typ — `contents` link type (reused canonical id)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "id":   "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "collection": "typ",
  "type_id": null,
  "properties": {
    "kind":                       "link_type",
    "name":                       "contents",
    "description":                "containment relationship between a container location and its contents",
    "left_role":                  "container",
    "right_role":                 "content",
    "left_to_right_label":        "contains",
    "right_to_left_label":        "contained_by",
    "allowed_left_collections":   ["loc"],
    "allowed_right_collections":  ["loc", "ent"]
  },
  "links": {},
  "_head": { /* as above with collection="typ" */ }
}
```

### Lnk 1 — Freezer → Bin (`Illumina 130-32` slot 2 holds `PP050`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2",
  "collection": "lnk",
  "type_id":    "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "left":       "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8",
  "right":      "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
  "properties": {
    "position": {
      "kind":  "freezer_slot",
      "label": "2",
      "slot":  2
    }
  },
  "links": {},
  "_head": { /* as above with collection="lnk" */ }
}
```

### Lnk 2 — Bin → Plate (`PP050` well A1 holds `27-474501`)

```json
{
  "_id":  "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_bin_pp050_to_plate_27-474501_a1",
  "id":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_bin_pp050_to_plate_27-474501_a1",
  "collection": "lnk",
  "type_id":    "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "left":       "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
  "right":      "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d",
  "properties": {
    "position": {
      "kind":   "bin_slot",
      "label":  "A1",
      "row":    "A",
      "column": 1
    }
  },
  "links": {},
  "_head": { /* as above */ }
}
```

Per `ContentsLinkReadService`, both `Lnk 1` and `Lnk 2` flow through
`findContents(containerId)` (forward, by `left`) and
`findLocations(objectId)` (reverse, by `right`) without any change to
the read service.

## Source Kafka transaction messages (single committed transaction)

These are the messages whose successful commit would yield the seven
roots above. The transaction header `txn.uuid`
`018fd849-c0c0-7000-8a01-c1a141e5e500` matches the txn-uuid embedded
in every prototype id. Group/client/user follow canonical examples.

Open:

```json
{
  "txn":    { "uuid": "018fd849-c0c0-7000-8a01-c1a141e5e500",
              "group": { "org": "jade-tipi-org", "grp": "dev" },
              "client": "kli",
              "user": "0000-0002-1825-0097" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa01",
  "collection": "txn",
  "action": "open",
  "data": { "description": "TASK-019 Clarity/ESP container materialization prototype" }
}
```

Loc 1 — ESP Freezer:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa02",
  "collection": "loc",
  "action": "create",
  "data": {
    "id":              "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8",
    "name":            "Illumina 130-32",
    "kind":            "Freezer (6-shelf)",
    "barcode":         "FREEZE012",
    "source_system":   "esp-entity",
    "source_id":       "019a3a62-8fa8-74d8-ad5c-c7f294c9a331",
    "source_type_id":  "019a3a49-6dd8-7dcc-af68-130207d9a1de"
  }
}
```

Loc 2 — ESP Bin:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa03",
  "collection": "loc",
  "action": "create",
  "data": {
    "id":                "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
    "name":              "PP050",
    "kind":              "Bin 9x3",
    "barcode":           "BIN057",
    "source_system":     "esp-entity",
    "source_id":         "019a3a60-9628-7c90-bc47-f40518a12127",
    "source_type_id":    "019a3a49-3672-73ec-842d-6c21c5ad9be7",
    "source_numeric_id": 50
  }
}
```

Loc 3 — ESP Plate:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa04",
  "collection": "loc",
  "action": "create",
  "data": {
    "id":                "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d",
    "name":              "27-474501",
    "kind":              "96W Plate",
    "barcode":           "27-474501",
    "format":            "96-well",
    "rows":              8,
    "columns":           12,
    "source_system":     "esp-entity",
    "source_id":         "019a420c-728d-7f4c-a817-cd8ba13a1e36",
    "source_type_id":    "019a3ac2-b494-71ac-82cc-fadc028be18f",
    "source_numeric_id": 474501
  }
}
```

Loc 4 — Clarity Tube:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa05",
  "collection": "loc",
  "action": "create",
  "data": {
    "id":            "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~clarity_tube_27-10000",
    "name":          "27-170230",
    "kind":          "Tube",
    "source_system": "clarity",
    "source_id":     "27-10000",
    "source_state":  "Populated"
  }
}
```

Typ — `contents` link type (reused canonical declaration):

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa06",
  "collection": "typ",
  "action": "create",
  "data": {
    "kind":                       "link_type",
    "id":                         "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "name":                       "contents",
    "description":                "containment relationship between a container location and its contents",
    "left_role":                  "container",
    "right_role":                 "content",
    "left_to_right_label":        "contains",
    "right_to_left_label":        "contained_by",
    "allowed_left_collections":   ["loc"],
    "allowed_right_collections":  ["loc", "ent"]
  }
}
```

Lnk 1 — Freezer → Bin:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa07",
  "collection": "lnk",
  "action": "create",
  "data": {
    "id":      "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_freezer_illumina130-32_to_bin_pp050_slot2",
    "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "left":    "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_freezer_019a3a62-8fa8",
    "right":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
    "properties": {
      "position": { "kind": "freezer_slot", "label": "2", "slot": 2 }
    }
  }
}
```

Lnk 2 — Bin → Plate:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa08",
  "collection": "lnk",
  "action": "create",
  "data": {
    "id":      "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~lnk~contents_bin_pp050_to_plate_27-474501_a1",
    "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "left":    "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_bin_019a3a60-9628",
    "right":   "jade-tipi-org~dev~018fd849-c0c0-7000-8a01-c1a141e5e500~loc~esp_plate_019a420c-728d",
    "properties": {
      "position": { "kind": "bin_slot", "label": "A1", "row": "A", "column": 1 }
    }
  }
}
```

Commit:

```json
{
  "txn":    { "...": "as above" },
  "uuid":   "018fd849-c0c0-7100-8a01-aaaaaaaaaa09",
  "collection": "txn",
  "action": "commit",
  "data": { "comment": "TASK-019 prototype committed" }
}
```

## Expected Mongo collections after materialization

| Collection | Roots written by this transaction                                       |
|------------|-------------------------------------------------------------------------|
| `loc`      | `Loc 1` (ESP freezer), `Loc 2` (ESP bin), `Loc 3` (ESP plate), `Loc 4` (Clarity tube). |
| `typ`      | `Typ` `contents` link-type declaration (idempotent if already present). |
| `lnk`      | `Lnk 1` (Freezer→Bin), `Lnk 2` (Bin→Plate).                             |
| `txn`      | The committed transaction WAL header + each message row, owned by the existing persistence path. |

Read-side verification by `ContentsLinkReadService`:

- `findContents("...~loc~esp_bin_019a3a60-9628")` → `[Lnk 2]`.
- `findLocations("...~loc~esp_plate_019a420c-728d")` → `[Lnk 2]`.
- `findContents("...~loc~esp_freezer_019a3a62-8fa8")` → `[Lnk 1]`.
- `findLocations("...~loc~esp_bin_019a3a60-9628")` → `[Lnk 1]`.
- `findContents("...~loc~clarity_tube_27-10000")` → `[]` (no contents
  link materialized for the Clarity tube; sample analyte `NPR578A22PA1`
  is intentionally not part of this prototype).
- `findLocations("...~loc~clarity_tube_27-10000")` → `[]`.

## Known ambiguities (not blockers)

- **A1 — ESP type vocabulary**. ESP's `type_name` strings
  (`"Bin 9x3"`, `"96W Plate"`, `"Freezer (6-shelf)"`, `"Tube"` etc.)
  are free-form labels, not a canonical Jade-Tipi enum. The prototype
  preserves them verbatim in `properties.kind` plus the source type
  id for traceability. Building a Jade-Tipi container-kind taxonomy
  is a future task.
- **A2 — Freezer record not directly fetched**. Sample E1a is
  inferred from the embedded `container` reference on the bin. The
  prototype assumes the freezer can be materialized as a `loc` from
  that embedded view. If the freezer's own ESP document carries
  additional fields the prototype should propagate, a one-shot fetch
  during real implementation would resolve it.
- **A3 — Multi-source identity**. Some Clarity containers also
  appear in ESP (per `DESIGN_NOTES`). The prototype creates one
  `loc` per source record and would need a deduplication/merge rule
  if a real import claims both as the same physical container. Out
  of scope here.
- **A4 — Bin/freezer slot units**. ESP `Bin.container.slot` for the
  sampled bin is `"2"` (a numeric string). Other observed bins use
  `"1".."6"`. The prototype treats this as `slot: <int>` after
  parsing; a future ESP record with a non-numeric label would force
  a string-only fallback.
- **A5 — Plate well notation**. Wells in the sampled plate use
  `"A1".."H10"` plus single-letter rows + 1–2-digit columns. The
  parsing rule (`row = first uppercase letter`, `column = remaining
  digits`) holds for `96W` and `384W` plates seen so far.
- **A6 — Provenance committed_at type**. The materializer puts
  `snapshot.committedAt` (an `Instant`) directly into the document.
  The prototype example shows the ISO-8601 string form; the real
  Mongo document would store a BSON datetime — irrelevant to the
  mapping decisions but worth noting for any future round-trip
  comparison test.

## Verification commands

### Read-only CouchDB sampling (operator can rerun)

The prototype examples can be reproduced by any developer with the
local CouchDB running (no remote credentials required, and no writes).
All commands assume `cd` into the `developers/claude-1` worktree so
the materialized `.env` carries `COUCHDB_USER`/`COUCHDB_PASSWORD`.

```sh
set -a; . ./.env; set +a
AUTH="$COUCHDB_USER:$COUCHDB_PASSWORD"
BASE=http://127.0.0.1:5984

# 1. Sanity: replicated databases exist and have docs.
curl -fsS -u "$AUTH" "$BASE/_all_dbs"          | jq .
curl -fsS -u "$AUTH" "$BASE/clarity"           | jq '{db_name, doc_count}'
curl -fsS -u "$AUTH" "$BASE/esp-entity"        | jq '{db_name, doc_count}'

# 2. Clarity tube sample.
curl -fsS -u "$AUTH" "$BASE/clarity/containers_27-10000" | jq '{_id, limsid, name:(.json.name), type:(.json.type.name), state:(.json.state), placement:(.json.placement)}'

# 3. ESP bin sample (PP050).
curl -fsS -u "$AUTH" "$BASE/esp-entity/019a3a60-9628-7c90-bc47-f40518a12127" \
  | jq '{_id, name, type_name, class_name, barcode, container, contents_keys:(.contents|keys)}'

# 4. ESP plate sample (27-474501, well A1 of PP050).
curl -fsS -u "$AUTH" "$BASE/esp-entity/019a420c-728d-7f4c-a817-cd8ba13a1e36" \
  | jq '{_id, name, type_name, class_name, barcode, container, contents_keys:(.contents|keys)}'

# 5. Optional: confirm 96W Plate well-key convention with a different plate.
curl -fsS -u "$AUTH" "$BASE/esp-entity/_find" -H 'content-type: application/json' \
  -d '{"selector":{"class_name":"Container","type_name":"96W Plate"},"fields":["_id","name","contents"],"limit":1}' \
  | jq '.docs[0] | { _id, name, well_keys: (.contents|keys) }'
```

Credentials are interpolated by curl's `-u` and never appear in the
shell history or in the design doc. No write verbs (`PUT`, `POST`,
`DELETE`) appear in any of these commands.

### CouchDB setup (only if not already running)

```sh
docker compose -f docker/docker-compose.yml up -d couchdb
docker compose -f docker/docker-compose.yml up -d couchdb-init
```

Then re-run the sampling commands above. If the local `.env` lacks
`COUCHDB_USER` / `COUCHDB_PASSWORD`, add them to the orchestrator
overlay
`/Users/duncanscott/orchestrator/jade-tipi/config/env/project.env.local`
and re-materialize the worktree.

### Implementation-iteration verification (proposed)

These commands would run only after the director moves `TASK-019` to
`READY_FOR_IMPLEMENTATION` and only against test code. The proposal
is unit-test-only because the existing materializer already has
integration coverage and the prototype's only novelty is the example
documents.

```sh
# Compile sanity.
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy

# Unit test — the prototype constructs the eight messages above
# (open, four loc, one typ, two lnk, commit) into a snapshot, drives
# CommittedTransactionMaterializer.materialize(snapshot), and asserts
# the seven materialized roots match the JSON shown in this doc
# modulo _head.provenance.materialized_at.
./gradlew :jade-tipi:test --tests '*ClarityEspContainerMappingSpec*'

# Full unit suite to catch incidental regressions.
./gradlew :jade-tipi:test
```

Optional integration coverage (default proposal: skip):

```sh
# Only included if the director judges unit-test evidence insufficient.
JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*ClarityEspContainerMaterializationSpec*'
```

If Gradle, Docker, or MongoDB setup blocks the implementation
iteration, the documented setup commands are
`docker compose -f docker/docker-compose.yml up -d`,
`./gradlew --stop` for stale daemons, and the existing
`docker compose ... --profile mongodb up -d` profile when only Mongo
is needed. Setup blockers are reported with the exact command/error
rather than treated as product blockers.
