# TASK-041 - Object property-value reads and effective type properties

ID: TASK-041
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-03)
SOURCE_TASK:
  - TASK-040
  - TASK-038
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-041-object-property-value-reads.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/controller/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - gradle-verification

GOAL:
Implement plan task J (root-only, per drift note 8.4/8.5): make TASK-040's
projected `property_values` inspectable over HTTP, plus the read-side
consequence of the ratified inheritance direction — an effective-properties
view answering "which properties may objects of this type carry?" as the
union of `property_refs` up the `parent_type_id` chain.

CONTEXT:
- `ObjectPropertyValuesReadService` reads a materialized object root (`loc`
  or `ent`) and returns its `property_values` entries with resolved
  human-readable property names (definition join, same pattern as the
  entity reader), keeping the legacy inline `properties` bag clearly
  separated in the response.
- `TypeEffectivePropertiesReadService` walks the subject `typ` root's
  `parent_type_id` chain (bounded, cycle-safe — mirroring the TASK-040
  gate), unions the `property_refs`, attributes each effective property to
  the type that registered it (nearest/most-derived registration wins for
  reference metadata), and reports `chainComplete: false` instead of
  failing when the chain is broken (missing ancestor, cycle, depth cap).
- Routes follow the resource-read convention (subject root required, 404
  when missing): `GET /api/locations/{id}/property-values` and
  `GET /api/types/{id}/effective-properties`.
- The legacy `GET /api/entities/{id}/property-values` route and its reader
  stay untouched until plan task L; the new service supports `ent` roots
  for future use but no ent HTTP route is added in this task.
- Read-only: no overlay of committed-but-unapplied messages (plan task K),
  no writes, no permission enforcement, no pagination.

ACCEPTANCE_CRITERIA:
- `GET /api/locations/{id}/property-values` returns the loc root's
  `objectId`, `collection`, `typeId`, verbatim inline `properties` and
  `links`, root provenance, and a `propertyValues` map keyed by `ppy` ID
  whose entries carry `value`, `txnId`, `commitId`, `msgUuid`, `appliedAt`,
  and resolved `propertyName` (null when the definition is missing).
  Missing loc root → 404; existing root with no projected values →
  200 with an empty `propertyValues` map.
- Stale tolerance mirrors accepted readers: a non-map `property_values`
  sub-document or non-map entry is ignored; a non-map entry `value`
  surfaces as an empty map.
- `GET /api/types/{id}/effective-properties` returns `typeId`, `typeName`,
  the ordered `typeChain` (subject first), `chainComplete`, and an
  `effectiveProperties` map keyed by `ppy` ID with `sourceTypeId`, the
  verbatim reference metadata, and resolved `propertyName`. The
  most-derived registration wins when a property is registered at multiple
  levels. Missing subject typ root → 404.
- Unit specs cover both services (happy paths, name join, missing subject,
  stale tolerance, inheritance union, nearest-wins, broken chain, cycle,
  depth cap) and both controllers (200/404).
- A Kafka-gated integration spec publishes the TASK-040 hierarchy sequence
  and asserts both HTTP routes end-to-end with a real JWT.
- Vocabulary doc gains sections for both reads and two new rows in the
  read-surface map table.

OUT_OF_SCOPE:
- No ent HTTP route for the new reader; the legacy entity route/reader stay
  untouched (plan task L).
- No overlay reads (plan task K), no writes, no schema changes, no UI.
- No value validation against `value_schema`.
- No pagination or permission enforcement.

VERIFICATION:
- `./gradlew :jade-tipi:compileGroovy :jade-tipi:compileTestGroovy :jade-tipi:compileIntegrationTestGroovy`
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*PlatePropertyValuesHttpReadIntegrationSpec*'`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-03):
- New services: `ObjectPropertyValuesReadService` (generic over `loc`/`ent`
  roots; `property_values` extraction sorted by property ID; definition-name
  join; stale tolerance per accepted readers) and
  `TypeEffectivePropertiesReadService` (ancestor walk mirroring the TASK-040
  gate bounds; subject-first union with most-derived-wins; partial results
  with `chainComplete: false` on a broken chain; definition-name join).
- New records: `ObjectPropertyValuesRecord`, `ObjectPropertyValueEntryRecord`,
  `TypeEffectivePropertiesRecord`, `TypeEffectivePropertyRecord`.
- New controllers: `LocationPropertyValuesReadController`
  (`GET /api/locations/{id}/property-values`) and
  `TypeEffectivePropertiesReadController`
  (`GET /api/types/{id}/effective-properties`); both resource reads (404 on
  missing subject), covered by `/api/**` JWT security automatically.
- Tests: service specs 10 + 8 features (happy paths, name join, missing
  subject, stale tolerance, sorted output, ent support, argument guards;
  inheritance union with source attribution, most-derived-wins, missing
  ancestor, cycle, depth cap); controller specs 2 + 2 (200/404);
  `PlatePropertyValuesHttpReadIntegrationSpec` (Kafka + Keycloak gated)
  publishes the TASK-040 hierarchy sequence and asserts both routes with a
  real JWT, including the inherited barcode's `sourceTypeId` attribution and
  both 404 contracts.
- Docs: vocabulary doc gains "Reading Object Property Values" and "Reading
  Effective Type Properties" sections plus two read-surface map rows.
- Legacy `GET /api/entities/{id}/property-values` and its reader are
  untouched, per plan (cleanup at task L).
- Verification results: all compile targets and `:jade-tipi:test` BUILD
  SUCCESSFUL (full unit suite green with the new specs); the Kafka+HTTP
  integration spec green (1/1) against the local Docker stack;
  `git diff --check` clean.
