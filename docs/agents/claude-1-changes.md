# claude-1 Changes

The developer writes completed work reports here.

STATUS: COMPLETED
TASK: TASK-031 — Human-readable Kafka property-definition materialization path (implementation)
DATE: 2026-05-03
SUMMARY: Implemented the bounded `ppy + create` `data.kind == "definition"`
materializer path per the accepted pre-work plan and the director's
TASK-031 `DIRECTOR_PREWORK_REVIEW` rulings. `CommittedTransactionMaterializer`
now declares `COLLECTION_PPY = 'ppy'`, `FIELD_KIND = 'kind'`, and
`KIND_DEFINITION = 'definition'`, and `isSupported()` accepts
`collection == "ppy"` paired with `action == "create"` only when
`message.data.get("kind") == "definition"`. Every other `ppy + create`
case — `data.kind == "assignment"`, missing `kind`, blank `kind`, and
unknown `kind` values — continues to fall through `isSupported()` and is
counted as `skippedUnsupported` exactly as before. No new
`MaterializeResult` counter, no new `processMessage`/`buildDocument`
branch, and no new duplicate-handling code was required: the existing
inline-properties fallback in `buildDocument()` already projects the
`02-…`/`03-…` flat wire shape (`data.kind`, `data.name`,
`data.value_schema`) under root `properties` after `id` is excluded,
yielding `properties.kind == "definition"`, `properties.name`, and
`properties.value_schema` (copied verbatim as an opaque JSON object) on
the materialized `ppy` document. The new root carries `_id == data.id`,
`id == data.id`, `collection == "ppy"`, `type_id == null`, `links == {}`,
and the standard `_head` block with `provenance.collection == "ppy"` and
`provenance.action == "create"`. `value_schema` is treated as opaque —
no JSON-Schema interpretation library is loaded, no nested-key
validation is performed, and the existing `isSamePayload` helper
deep-equals the entire materialized doc (minus
`_head.provenance.materialized_at`) so the nested schema participates in
idempotent-vs-conflicting duplicate detection out of the box.

Example resource JSON was not edited: `02-create-property-definition-text.json`
and `03-create-property-definition-numeric.json` already show the
accepted human-readable wire shape verbatim (top-level `collection: "ppy"`,
`action: "create"`, `data.kind: "definition"`, `data.id`, `data.name`,
`data.value_schema`); `07-…`/`08-…` continue to declare the canonical
assignment shape and remain `skippedUnsupported`; the directive forbids
broad ID-abbreviation cleanup so the `~pp~` 2-char segments in `02-…`,
`03-…`, `07-…`, `08-…`, and the `05-….data.property_id` cross-reference
are preserved exactly as-is. The renamed Spock helper
`propertyDefinitionCreateMessage(...)` aligned its synthetic test ID to
`~pp~barcode` per the director's `ID_DIRECTION` allowance ("if the
existing helper is renamed anyway, it may be aligned to the canonical
`~pp~` segment only within that helper/test fixture"); no other helper
or example was retitled. No HTTP submission endpoint,
`ppy + create kind=assignment` materialization, semantic `property_id`
or `entity_id` resolution, value-shape validation against
`data.value_schema`, required-property enforcement, contents-link read
change, endpoint projection maintenance, broad ID cleanup, or nested
Kafka operation DSL was added. All edits stayed within TASK-031's
`OWNED_PATHS`.

Files changed (this turn):

- `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
  — Added `COLLECTION_PPY`, `FIELD_KIND`, and `KIND_DEFINITION`
  constants. Extended `isSupported()`'s create-action switch with a
  `case COLLECTION_PPY` branch that returns `true` only when
  `message.data?.get(FIELD_KIND) == KIND_DEFINITION`. Updated the class
  Javadoc to document the new `ppy + create` bullet (kind discriminator
  gate, opaque `value_schema` copy, no validation), and amended the
  "Every other collection/action combination" sentence to call out
  `ppy + create` whose `data.kind` is not `"definition"` as still
  skipped. No other source changes; `processMessage`, `buildDocument`,
  `buildInlineProperties`, `copyProperties`, `buildHead`,
  `handleInsertError`, `isSamePayload`, `extractDocId`, and
  `MaterializeResult` are unchanged.
- `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
  — Added `PPY_ID`, `PPY_MSG_UUID`, `PPY_ASSIGNMENT_ID`, and
  `PPY_ASSIGNMENT_MSG_UUID` constants (the assignment id is
  `ENT_ID + '~' + PPY_ID` per the canonical composite shape used by
  `07-…`). Renamed `propertyCreateMessage()` to
  `propertyDefinitionCreateMessage(Map dataOverrides = [:])`, expanded
  it to carry `data.value_schema` matching `02-…`'s shape, and aligned
  the synthetic id to `~pp~barcode`. Added
  `propertyAssignmentCreateMessage(Map dataOverrides = [:])` carrying
  `kind: "assignment"`, `entity_id`, `property_id`, and `value` per
  `07-…`. Replaced the old `'skips a ppy create message'` feature with
  six new features: positive `'materializes a ppy + create kind=definition
  as a root document with kind, name, and value_schema in root
  properties'`, `'skips a ppy + create kind=assignment as
  skippedUnsupported without touching mongo'`, parameterised
  `'skips a ppy + create with missing or other data.kind as
  skippedUnsupported'` (where-block over `[null, '', '   ', 'unknown',
  'something_else']`), `'skips a ppy + create kind=definition with
  missing data.id as skippedInvalid'`, parameterised `'skips a ppy +
  create kind=definition with blank or whitespace data.id as
  skippedInvalid'`, `'identical-payload ppy duplicate is matching even
  when materialized_at differs'`, and `'differing-payload ppy duplicate
  is conflicting and not overwritten'`. Updated the mixed-snapshot
  feature so the ppy definition message materializes (counts move from
  `materialized == 5, skippedUnsupported == 1` to `materialized == 6,
  skippedUnsupported == 0`); `insertOrder == ['loc', 'ppy', 'typ',
  'typ', 'ent', 'lnk']`. The when-block label was updated accordingly.
- `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
  — Added `'ppy + create definition example uses the human-readable
  kind, id, name, and value_schema shape'` (reads `02-…`, asserts
  `Collection.PROPERTY + Action.CREATE`, the verbatim id, name, and
  the nested `value_schema` keys, plus `data.keySet() == ['kind',
  'id', 'name', 'value_schema'] as Set`). Added `'ppy + create numeric
  definition example uses kind=definition with a multi-key value_schema'`
  (reads `03-…`, asserts the multi-key required array and nested
  number/unit_id types). Added `'ppy + create assignment example uses
  the human-readable kind, id, entity_id, property_id, and value
  shape'` (reads `07-…`, documents the canonical assignment shape so a
  future assignment-materialization task does not have to rediscover
  it). Added `'property-definition transaction example sequence shares
  one txn id and the assignment example references the
  property-definition and entity by id'` (reads `01 + 02 + 04 + 06 +
  07 + 09`, asserts shared `txn.uuid` and the cross-references
  `07-….data.entity_id == 06-….data.id` and `07-….data.property_id ==
  02-….data.id`). The `EXAMPLE_PATHS` round-trip / schema-validate
  loop already covers `02-…`, `03-…`, `07-…`, and `08-…` and is
  unchanged.
- `jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/PropertyDefinitionCreateKafkaMaterializeIntegrationSpec.groovy`
  — New dedicated opt-in Kafka/Mongo integration spec (gated by
  `JADETIPI_IT_KAFKA` plus the standard 2-second `AdminClient`
  cluster-describe probe). Publishes `open + ppy-definition +
  ppy-assignment + commit` to a per-spec topic, waits for the
  `txn` header to reach `state == "committed"` with a non-blank
  `commit_id`, then asserts the materialized `ppy` document at
  `_id == propertyDefinitionId` carries `collection == "ppy"`,
  `type_id == null`, `properties.kind == "definition"`,
  `properties.name == "barcode"`, `properties.value_schema` verbatim
  (matching the published wire-shape JSON object), `links == [:]`, and
  `_head.provenance` pointing at the original definition message uuid.
  Also asserts the trailing `ppy + create kind=assignment` message did
  not materialize a `ppy` document at the assignment id, proving the
  skip-still-skips behaviour end-to-end.
- `docs/architecture/kafka-transaction-message-vocabulary.md`
  — In "Property Definitions", appended a paragraph describing the
  materialized `ppy` root shape (`_id == data.id`, `collection: "ppy"`,
  `type_id: null`, root `properties.kind`, `properties.name`,
  `properties.value_schema` opaque-verbatim, empty `links`,
  `_head.provenance.collection == "ppy"`, no value-schema validation).
  In "Property Value Assignment", added a one-sentence note that
  assignment materialization remains a separate future task. In
  "Committed Materialization Of Locations And Links", expanded the
  supported set to include `ppy + create` whose
  `data.kind == "definition"` and the `ppy` long-term collection, and
  amended the `skippedUnsupported` sentence to call out
  `ppy + create` whose `data.kind` is not `"definition"` (including
  `"assignment"`).
- `docs/OVERVIEW.md`
  — Updated the "Next Steps" Kafka materialization bullet to add
  `ppy + create` whose `data.kind == "definition"` (carrying inline
  `kind`, `name`, and verbatim `value_schema` under root `properties`)
  next to the existing `loc + create` / `typ + create` /
  `typ + update add_property` entries.

VERIFICATION:
Commands run inside this developer worktree
(`/Users/duncanscott/orchestrator/jade-tipi/developers/claude-1`). The
local Docker stack (`jade-tipi-mongo`, `jade-tipi-kafka`,
`jade-tipi-keycloak`, `jade-tipi-couchdb`) was already up and healthy
at the start of the implementation turn (no `docker compose up -d`
needed).

- `./gradlew :libraries:jade-tipi-dto:test` — BUILD SUCCESSFUL in 1s. The
  existing `EXAMPLE_PATHS` round-trip and schema-validate loop covers
  `02-…`/`03-…`/`07-…`/`08-…` unchanged; the four new focused features
  passed.
- `./gradlew :jade-tipi:test` — BUILD SUCCESSFUL in 7s. The flipped
  positive `'materializes a ppy + create kind=definition…'` feature,
  the assignment/missing-kind/missing-id skip features, the
  duplicate-matching/conflicting-duplicate features, the renamed helper,
  and the updated mixed-snapshot feature all passed.
  `JadetipiApplicationTests.contextLoads` (which opens a Mongo
  connection) succeeded against the running `jade-tipi-mongo`
  container.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*PropertyDefinitionCreateKafkaMaterializeIntegrationSpec*'` — BUILD
  SUCCESSFUL in 9s. Materializer log line confirms `Materialized ppy
  root: id=jadetipi-itest-ppy~pp~barcode_<short-uuid>, txnId=<...>,
  commitId=<...>`. The trailing assignment did not materialize.
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests
  '*EntityCreateKafkaMaterializeIntegrationSpec*'` (regression check) —
  BUILD SUCCESSFUL in 8s.

OUT_OF_SCOPE_CONFIRMED:
No HTTP submission endpoints, no property-value assignment
materialization, no semantic `property_id`/`entity_id` resolution, no
required-property enforcement, no value-shape validation against
`data.value_schema`, no permission enforcement, no contents-link read
changes, no entity/location/typ-update materialization changes, no
endpoint projection maintenance, no broad ID-abbreviation cleanup
(only the renamed `propertyDefinitionCreateMessage()` helper's local
synthetic id was aligned to `~pp~barcode` within that helper/test
fixture as expressly permitted by the director's `ID_DIRECTION`), and
no nested Kafka operation DSL was added in this turn.
