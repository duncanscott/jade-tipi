# codex-1 Next Step

Pre-work response for TASK-013.

STATUS: PREWORK_COMPLETE
TASK: TASK-013 - Define materialized root document contract
DATE: 2026-05-01

## Directive Summary

- Latest `DIRECTIVES.md` signal is `REQUEST_NEXT_STEP`.
- Active task is `TASK-013`, a research/design task in `READY_FOR_PREWORK`.
- `TASK-012` remains paused. Do not implement its accepted integration plan
  until the root document contract is accepted and the director reauthorizes,
  rewrites, or replaces that task.
- This turn is pre-work only. No production code, tests, schemas, canonical
  examples, Docker, Gradle, security, Kafka listener behavior, controllers, or
  frontend changes are authorized.
- Source inspection covered the requested direction docs, message vocabulary,
  canonical `loc`/`typ`/`lnk`/`ent` examples, `message.schema.json`, current
  materializer/read service/controller source, and accepted TASK-009 through
  TASK-012 reports.

## Proposed Root Document Contract

Use a canonical root document as the first durable materialized shape. The
Mongo collection can remain the physical collection (`loc`, `typ`, `lnk`,
`ent`, etc.), but each root should be self-describing:

```json
{
  "_id": "<object_id>",
  "id": "<object_id>",
  "collection": "<collection_abbr>",
  "type_id": "<typ_id_or_null>",
  "properties": {},
  "links": {},
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "<object_id>",
    "provenance": {
      "txn_id": "<txn_id>",
      "commit_id": "<commit_id>",
      "msg_uuid": "<message_uuid>",
      "collection": "<source_collection>",
      "action": "create",
      "committed_at": "<instant>",
      "materialized_at": "<instant>"
    }
  }
}
```

Shared root fields:
- `_id` and `id` are both the Jade-Tipi object id. Keep both for Mongo primary
  key behavior and JSON transparency.
- `collection` is the logical collection abbreviation, even when the physical
  Mongo collection already implies it.
- `type_id` is top-level for all roots. It is nullable/absent only when the
  accepted create vocabulary does not provide a type yet, such as the current
  `loc` example and current `typ` link-type declaration.
- `properties` contains explicit user-visible values only. The materializer
  must not invent required/default values.
- `links` contains denormalized link projections for object roots. A `lnk` root
  remains the canonical relationship object; endpoint projections are rebuildable
  read accelerators.
- `_head` is the only reserved storage/provenance namespace. The existing
  `_jt_provenance` field should move to `_head.provenance`; readers can carry a
  short transitional fallback if needed, but new root documents should not
  continue writing `_jt_provenance`.

## Example Root Documents

`loc` root with a denormalized outgoing `contents` projection:

```json
{
  "_id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
  "id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
  "collection": "loc",
  "type_id": null,
  "properties": {
    "name": "plate_b1",
    "description": "96-well plate B1"
  },
  "links": {
    "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1": {
      "link_id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
      "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
      "direction": "out",
      "role": "container",
      "other_id": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
      "other_collection": "ent",
      "properties": {
        "position": {
          "kind": "plate_well",
          "label": "A1",
          "row": "A",
          "column": 1
        }
      }
    }
  },
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
    "provenance": {
      "txn_id": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi-org~dev~kli",
      "commit_id": "commit-1",
      "msg_uuid": "018fd849-2a47-7777-8f01-aaaaaaaaaaaa",
      "collection": "loc",
      "action": "create",
      "committed_at": "2026-05-01T00:00:00Z",
      "materialized_at": "2026-05-01T00:00:01Z"
    }
  }
}
```

`typ` root for the `contents` link-type declaration:

```json
{
  "_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "collection": "typ",
  "type_id": null,
  "properties": {
    "kind": "link_type",
    "name": "contents",
    "description": "containment relationship between a container location and its contents",
    "left_role": "container",
    "right_role": "content",
    "left_to_right_label": "contains",
    "right_to_left_label": "contained_by",
    "allowed_left_collections": ["loc"],
    "allowed_right_collections": ["loc", "ent"]
  },
  "links": {},
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
    "provenance": {
      "txn_id": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi-org~dev~kli",
      "commit_id": "commit-1",
      "msg_uuid": "018fd849-2a49-7999-8a09-aaaaaaaaaaab",
      "collection": "typ",
      "action": "create",
      "committed_at": "2026-05-01T00:00:00Z",
      "materialized_at": "2026-05-01T00:00:01Z"
    }
  }
}
```

Canonical `lnk` root. This is the source of truth for the relationship; the
endpoint `links` entries above and below are denormalized projections:

```json
{
  "_id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
  "id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
  "collection": "lnk",
  "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
  "left": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
  "right": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
  "properties": {
    "position": {
      "kind": "plate_well",
      "label": "A1",
      "row": "A",
      "column": 1
    }
  },
  "links": {},
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
    "provenance": {
      "txn_id": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi-org~dev~kli",
      "commit_id": "commit-1",
      "msg_uuid": "018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb",
      "collection": "lnk",
      "action": "create",
      "committed_at": "2026-05-01T00:00:00Z",
      "materialized_at": "2026-05-01T00:00:01Z"
    }
  }
}
```

Ordinary `ent` root with a denormalized incoming `contents` projection:

```json
{
  "_id": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
  "id": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
  "collection": "ent",
  "type_id": "jade-tipi-org~dev~018fd849-2a43-7333-8c03-cccccccccccc~typ~sample",
  "properties": {},
  "links": {
    "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1": {
      "link_id": "jade-tipi-org~dev~018fd849-2a4a-7aaa-8b0a-bbbbbbbbbbbb~lnk~plate_b1_sample_x1",
      "type_id": "jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents",
      "direction": "in",
      "role": "content",
      "other_id": "jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~plate_b1",
      "other_collection": "loc",
      "properties": {
        "position": {
          "kind": "plate_well",
          "label": "A1",
          "row": "A",
          "column": 1
        }
      }
    }
  },
  "_head": {
    "schema_version": 1,
    "document_kind": "root",
    "root_id": "jade-tipi-org~dev~018fd849-2a45-7555-8e05-eeeeeeeeeeee~ent~sample_x1",
    "provenance": {
      "txn_id": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi-org~dev~kli",
      "commit_id": "commit-1",
      "msg_uuid": "018fd849-2a45-7555-8e05-eeeeeeeeeeee",
      "collection": "ent",
      "action": "create",
      "committed_at": "2026-05-01T00:00:00Z",
      "materialized_at": "2026-05-01T00:00:01Z"
    }
  }
}
```

## Map Key Policy

- `links` should be keyed by canonical `lnk` object id. This is stable,
  collision-resistant, rebuildable from canonical link roots, and matches the
  current direction.
- `properties` should ultimately be keyed by property object id for true
  property assignments. That avoids name collisions and supports cross-group
  property ownership.
- The immediate problem is that current accepted create examples do not provide
  property ids for inline fields such as `loc.name`, link-type facts like
  `name`/`left_role`, or `lnk.properties.position`. The first materializer
  should not synthesize property ids or require new property-definition
  messages in this contract task.
- Concrete initial policy: preserve current inline property keys exactly inside
  `properties` for fields already carried by the committed payload, while
  documenting them as transitional inline keys. When `ppy kind=assignment`
  materialization is implemented, those assignment entries should use
  `property_id` as the map key. A later normalization task can tighten the
  write vocabulary so inline link/property maps carry ids instead of names.

## Intentionally Absent

The initial root contract should explicitly exclude required properties,
default values, extension property pages, extension link pages, pending pages,
compact page indexes, background compaction, semantic validation of
`type_id`/`left`/`right`/`allowed_*_collections`, update/delete replay,
backfill, transaction-overlay reads, endpoint joins, authorization/scoping
policy, response envelopes, pagination, and plate-shaped projections.

## TASK-012 Recommendation

Do not resume `TASK-012` as-is. Its accepted integration plan would harden the
copied-data shape from TASK-009. After this contract is accepted, replace or
rewrite TASK-012 so the integration test waits for root-shaped materialized
documents, asserts `_head.provenance` instead of `_jt_provenance`, and exercises
contents reads against whichever query paths the root-contract implementation
keeps. If the read service moves `typ.kind/name` under `properties`, TASK-012
must wait until that service is updated too.

## Follow-up Implementation Tasks

1. Update `CommittedTransactionMaterializer` to write root-shaped documents for
   the currently supported committed `loc + create`, `typ link_type + create`,
   and `lnk + create` messages. Unit coverage should pin `_head.provenance`,
   `collection`, `type_id`, explicit `properties`, empty/default `links`, and
   duplicate comparison that ignores `_head.provenance.materialized_at`.
2. Update `ContentsLinkReadService` and `ContentsLinkRecord` for the accepted
   root shape. Likely changes are querying `typ.properties.kind/name`, reading
   provenance from `_head.provenance`, and preserving the public HTTP behavior
   from TASK-011.
3. Rewrite the paused TASK-012 integration coverage to verify the root-shaped
   materializer plus the HTTP reads end-to-end through Kafka.
4. Add a later design task for property-id normalization of inline property maps
   and for endpoint projection maintenance if the first materializer revision
   does not populate `links` on unresolved or missing endpoint roots.

## Blockers And Open Questions

- No blocker for pre-work.
- Open question for director review: should the first materializer update
  populate endpoint `links` projections only when endpoint roots already exist,
  or should it create no projections until `ent` materialization and semantic
  reference policy are designed? My proposed default is to keep canonical `lnk`
  roots first and avoid creating endpoint stubs.
- Open question for director review: is the transitional inline property-key
  policy acceptable for current canonical examples, or should the next task
  first change the write vocabulary to carry property ids for `position`,
  `name`, and link-type declaration facts?
