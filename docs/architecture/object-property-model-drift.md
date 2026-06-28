# Object property model — drift analysis and migration plan

Status: analysis / proposal. Not accepted. Written 2026-06-28 in response to a
human observation that `loc` records store domain properties as plain strings
instead of as type-gated, `ppy`-keyed property values.

This note (1) confirms the intended model against the foundation documents,
(2) states target vs. current state per collection, (3) maps the blast radius
of the current name-keyed assumption, and (4) proposes a migration order.

## 1. The documented foundation

The intended model is explicit in the foundation documents and matches the
human description that prompted this note.

- `DIRECTION.md`, "Objects, Types, And Properties" (lines 26–32):
  > "For initial implementation, model each object as a typed collection of
  > explicit property-value assignments. The object's `type_id` points to a
  > `typ` record that defines which properties may be assigned to objects of
  > that type. A property must be added to the type before clients may assign
  > that property to an object of the type."

  "each object" — `ent`, `loc`, `lnk`, `grp`, … are peer domain collections
  (`DIRECTION.md` lines 19–22), so this rule is not entity-specific.

- `DIRECTION.md`, "Logical Objects And Physical Documents" (lines 83–84):
  > "Property and link maps should be keyed by the IDs of the property or link
  > objects."

- `docs/architecture/jade-tipi-object-model-design-brief.md`, Core Terms
  (lines 9–17): objects are "collections of property-value assignments"; a type
  "declares the properties that may be assigned to objects of that type"; "a
  property may be assigned only after the object's type definition permits that
  property."

So the target is: every domain object carries a `type_id`; the `typ` declares
its assignable properties; the object holds property **values keyed by `ppy`
ID**, and an assignment is permitted only if the type lists that property.

## 2. The sanctioned "first pass" — and how it became drift

The same documents deliberately allowed a temporary simpler shape, which is
exactly what `loc` still uses today:

- `DIRECTION.md`, "Human-Readable Kafka Submission" (lines 132–137):
  > "`data.type_id` may be absent while type modeling is still immature.
  > `data.properties` is a plain JSON object keyed by **property name** for the
  > initial human-authored path; stricter property-ID-keyed maps and
  > property-definition validation can be layered in after the submission route
  > is proven."

The drift is not a violation of the docs; it is an **incomplete migration**.
The `ppy` property model was built for `ent` only (TASK-031 definitions,
TASK-032 assignments, TASK-033 read), and even there it took a storage shape
that differs from the doc (standalone assignment roots keyed by `entity_id`,
not a `ppy`-keyed map on the object). `loc` never advanced past the first pass,
and the recent container/seed/read work (TASK-026, TASK-034–037) all built on
the first-pass `loc` shape, entrenching it.

A second subtlety: every root (`ent` included) still gets a name-keyed inline
`properties` bag copied verbatim from `data.properties` by the materializer.
So entities today carry **two** property representations — the inline
name-keyed bag on the root *and* separate `ppy` assignment roots — while
locations carry only the inline bag.

## 3. Target vs. current state, per collection

| Collection | Target (per DIRECTION.md / brief) | Current state |
|---|---|---|
| `typ` | Declares assignable properties for its objects (a property-ref set). | Entity types do: `typ + update add_property` writes `properties.property_refs.<ppy_id>` (TASK-030). No **location** type exists; link types carry an unenforced `assignable_properties` list. |
| `ppy` | Property definitions (name + value schema), referenced by ID as the key of object value maps. | Definitions exist (TASK-031). Assignments exist (TASK-032) but only for entities. No definitions exist for container fields (`name`, `barcode`, `kind`, …). |
| `ent` | `type_id` + property **values keyed by `ppy` ID**, gated by the type. | Has the typed path (type_id → property_refs → `ppy` assignment), but assignments are standalone roots keyed by `entity_id`, not projected onto the object; root also keeps a name-keyed inline `properties` bag. The TASK-036 seed entity skipped assignments entirely and used the inline bag. |
| `loc` | `type_id` + property values keyed by `ppy` ID, gated by a location type. | `type_id: null`; `properties` is a plain name-keyed string bag (`name`, `kind`, `barcode`, `format`, `source_*`). No location `typ`, no `ppy` definitions, and the assignment mechanism rejects non-entities. |
| `lnk` | Instance properties on the link; assignable set and semantics on the link type. | Follows the link-specific direction (position-on-link, semantics on `typ~contents`) — intentional. But instance `properties.position` is name-keyed and the link type's `assignable_properties` is an unenforced name list, not `ppy`-keyed `property_refs`, so `lnk` is sanctioned first-pass / deferred, **not fully aligned**. |

Read layer: `EntityPropertyValuesReadService` reconstructs the `ppy`-keyed
view (`valuesByPropertyId`) at read time via a join — so the target shape
exists in the **entity API response**, but not in storage and not for `loc`.

## 4. Blast radius — where the name-keyed / entity-scoped assumption is baked in

Every place that would change (or block) a move to typed, `ppy`-keyed object
property values:

**Write / materialization**
- `CommittedTransactionMaterializer.buildDocument` /
  `buildInlineProperties` / `copyProperties`
  (`jade-tipi/.../service/CommittedTransactionMaterializer.groovy:531-574`):
  copies `data.properties` verbatim (or inline-builds from `data`) into the
  root `properties` for every non-`lnk` collection; `type_id` is taken from
  `data.type_id` and is `null` when absent. No `ppy` resolution or type gating
  happens at root-create time.
- `ppy + create` assignment path (`:320-410`, gated in `isSupported` at
  `:488-517`): requires non-blank `data.entity_id`, requires the target
  **`ent`** root to exist (`findById(entityId, …, COLLECTION_ENT)`), and gates
  on the entity type's `properties.property_refs`. Locations cannot receive a
  property assignment at all. This is the keystone constraint.

**Message vocabulary / schema**
- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:
  `data` is validated as a snake_case object (`SnakeCaseObject`) for every
  collection except the `grp` permissions map. A `ppy`-ID-keyed value map would
  need a new exception, because `ppy` IDs contain `~` and are not snake_case
  (same carve-out already made for `grp` permission keys).
- Assignment payload field name `entity_id` (`07-…`, `08-…` examples and the
  materializer constant `FIELD_ENTITY_ID`) is entity-specific.

**Canonical examples** (`libraries/jade-tipi-dto/src/main/resources/example/message/`)
- `10-create-location.json`: name-keyed `properties` (`name`, `description`),
  no `type_id` — encodes `loc` as first-pass.
- `04/05/05a/06/07/08`: the full entity typed path (type → add_property →
  entity → assignment). There is **no** location equivalent (no location type,
  no location property definitions, no location assignment).

**Seed / fixtures**
- `ClarityEspContainerReviewSeedKafkaIntegrationSpec` (TASK-036): all `loc`
  messages are name-keyed with no `type_id`; the `LHCPOT` entity also uses an
  inline name-keyed bag (the task explicitly skipped the assignment loop).

**Read views** (these are mostly generic pass-throughs, so light to change)
- `LocationRootReadService` (`:54-61`): already carries `typeId` and passes
  `properties` through as an opaque map — no name-key parsing, so it tolerates
  either shape. There is **no** location property-values reader (no `loc`
  analogue of `EntityPropertyValuesReadService`, which queries `ppy`
  assignments by `properties.entity_id`).
- `PlateContentsReadService`, `ObjectLocationsReadService`,
  `LocationContentsReadService`: embed `LocationRootRecord` /
  `EntityPropertyValuesRecord` and pass properties through opaquely. They would
  only change if/when we want resolved object property **values** surfaced for
  containers.

**Docs**
- `docs/architecture/clarity-esp-container-mapping.md`: the entire ESP/Clarity
  mapping maps source fields to name-keyed `loc.properties`.
- `docs/architecture/kafka-transaction-message-vocabulary.md`: "Property Value
  Assignment" describes the entity-keyed shape; the loc/contents reading
  sections assume the name-keyed bag.

## 5. Storage-shape decision (needs a human/director call)

DIRECTION.md sketches a `ppy`-ID-keyed property map **on the object root** (with
extension pages for overflow). TASK-032 instead stores **standalone assignment
roots** keyed by `entity_id` and reconstructs the keyed view at read time.

- **Option A — keep standalone assignment roots, join at read** (current `ent`
  approach). Pros: one write per message, no dual source of truth, permissioning
  at property scope is natural (each assignment is its own owned record, matching
  `DIRECTION.md` "Groups And Permissions"). Cons: object property values are not
  visible in the root document; every read needs a join; diverges from the
  "map on the object" sketch.
- **Option B — project values onto the object root keyed by `ppy` ID**
  (literal reading of "Logical Objects And Physical Documents"). Pros: root is
  self-describing; no join. Cons: dual source of truth if assignment roots also
  exist; multi-write per assignment; harder property-scope permissioning;
  contradicts TASK-032's deliberate "don't project" decision.

Recommendation: **Option A is already the working model and aligns with the
property-scope permission direction; keep it and make it object-generic.** Treat
the root's inline `properties` bag as functional/source-provenance only (or
retire it for domain values), and treat `ppy` assignment roots as the canonical
domain property values for every object collection — surfaced through a generic
read view. Revisit Option B only if root self-description becomes a hard
requirement.

## 6. Proposed migration order

Each step is independently shippable and additive (no breaking change to
TASK-031/032/033) until the final cleanup.

1. **Ratify Section 5** (storage shape) with the director/human. Everything else
   assumes Option A.
2. **TASK-038 — generalize property assignment from entity-scoped to
   object-scoped** (drafted alongside this note). Make `ppy + create`
   assignment target any supported object collection (start with `loc` + `ent`)
   via a generic `object_id`, gating on the target object's `type_id` →
   `typ.properties.property_refs` exactly as the entity path does today. Keep
   `entity_id` as a backward-compatible alias. Keystone step.
3. **TASK-039 — type the locations.** Add a location `typ` (or a small set:
   freezer/bin/plate/tube) with `property_refs`, add `ppy` definitions for the
   container fields (`name`, `barcode`, `kind`, `format`, source provenance),
   and submit `loc + create` with a real `type_id`. No materializer change
   needed (typ create, add_property, ppy definition, and loc-with-type_id are
   all already supported).
4. **TASK-040 — migrate container field values to assignments.** Update the
   TASK-036 seed and the canonical `loc` example to assign container fields as
   `ppy` values to typed locations instead of an inline name-keyed bag. Decide
   the fate of the inline `properties` bag (retire for domain values, or reserve
   for source-provenance only).
5. **TASK-041 — generic object property-values read view.** Generalize
   `EntityPropertyValuesReadService` (or add a sibling) to resolve property
   values for any object by `object_id`, and surface it where the container
   read views (`LocationContents`, `PlateContents`, `ObjectLocations`) embed a
   resolved object so containers show typed values, not raw strings.
6. **TASK-042 — schema + docs reconciliation.** Add the `ppy`-ID-keyed
   exception to `message.schema.json` if Option B is ever chosen; otherwise
   document the assignment-root model as canonical for all objects and update
   the container-mapping doc.

## 7. Open decisions for human/director review

- Section 5: Option A (join at read) vs. Option B (project onto root). This note
  recommends A.
- TASK-038: how does the assignment message name its target collection — an
  explicit `object_collection` field, or probe the supported object collections
  by `object_id`? (The materializer should not parse IDs.) Recommend an explicit
  `object_collection`.
- Granularity of location types in TASK-039: one generic `location` type, or
  per-kind types (`freezer`, `bin`, `plate`, `tube`)? The ESP `type_name`
  values suggest per-kind, but that multiplies type/definition setup.
- Whether the inline root `properties` bag is retired for domain values or kept
  for source-provenance fields (`source_system`, `source_id`, …).
- Whether and when to align `lnk` with the typed/`ppy`-keyed model — i.e. move
  link instance properties (e.g. `position`) to `ppy`-keyed values gated by the
  link type's `property_refs` instead of the current name-keyed
  `properties.position` + unenforced `assignable_properties` list. This is
  currently deferred and unscheduled (no task), not declared aligned.
