# TASK-046 - Hard object identifier validation and ID normalization

ID: TASK-046
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-04)
SOURCE_TASK:
  - TASK-044
  - TASK-045
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-046-hard-object-id-validation.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/architecture/object-property-model-drift.md
  - docs/README.md
  - libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json
  - libraries/jade-tipi-dto/src/main/resources/example/message/
  - libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - gradle-verification

GOAL:
Complete the object identifier arc the director opened (TASK-044:
document + warn): enforce the convention at the wire with a `data.id`
pattern in `message.schema.json`, after normalizing every remaining
nonconforming identifier in canonical examples, docs, and fixtures.

CONTEXT:
- The convention: `<org>~<grp>~<uuidv7|genesis>~<collection>~<suffix>`,
  with `genesis` sanctioned only for the bootstrap `usr`, and a composite
  two-block form tolerated for the deprecated legacy-alias assignment
  payloads (whose `data.id` the materializer ignores).
- Remaining drift found by survey: canonical examples 02-08 (and example
  15's property reference) use the pre-convention two-letter collection
  segments (`~pp~`, `~ty~`, `~en~`); the TASK-030/031-era integration
  specs use three-segment `jadetipi-itest-*` fixture IDs; a few unit read
  fixtures quote `~pp~` property IDs; the schema's own `examples` array
  and the vocabulary doc's example snippets quote the old segments;
  `docs/README.md` still shows the superseded `timestamp~increment` ID
  format.
- The 4th ID segment is the wire `Collection` abbreviation set plus
  `usr` (`ent`, `ppy`, `lnk`, `loc`, `uni`, `grp`, `typ`, `vdn`, `usr`),
  matching the TASK-044 materializer predicate. The two-letter segments in
  the historical manifesto (`docs/Jade-Tipi.md`) are left as-is and
  flagged for a director prose pass rather than edited here.
- The materializer's warn-only check stays as defense in depth for
  non-Kafka writers; the schema becomes the hard gate on the wire (the
  listener already rejects schema-invalid messages as poison pills).

ACCEPTANCE_CRITERIA:
- `message.schema.json` defines an `ObjectId` pattern (org/grp segments,
  UUIDv7-or-`genesis` third segment, known collection abbreviation
  fourth, `[a-z0-9._-]+` suffix, optional second conforming block for the
  legacy composite form) and applies it to top-level `data.id` in both
  the `grp` and non-`grp` branches without constraining nested `id` keys.
- Canonical examples 02-08 and 15 use three-letter collection segments;
  every example still round-trips and validates in `MessageSpec`, whose
  literal assertions are updated; the schema's `examples` array conforms.
- New `MessageSpec` features prove the gate: a nonconforming `data.id`
  is rejected; the genesis identifier and a legacy composite ID are
  accepted.
- The remaining nonconforming fixture IDs (ContentsHttpRead,
  EntityCreate, PropertyDefinitionCreate, TransactionMessageKafkaIngest
  specs; PlateContents unit fixtures; materializer spec constants) are
  normalized to the convention; the full Kafka-gated integration suite
  passes with the hard gate active and zero convention warnings.
- `docs/README.md` shows the current UUIDv7 ID format; the vocabulary
  doc's enforcement paragraph reflects schema enforcement; the drift note
  8.5 ledger marks this task-L item done.

OUT_OF_SCOPE:
- No edits to the historical manifesto prose (`docs/Jade-Tipi.md`) or to
  closed task files quoting old IDs.
- No validation of reference fields (`type_id`, `object_id`,
  `property_id`, `left`, `right`, `parent_type_id`) — a possible later
  tightening once submitted IDs are all conformant.
- No MongoDB migration of existing rows.

VERIFICATION:
- `./gradlew :libraries:jade-tipi-dto:test :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- Normalization: canonical examples 02-08 and 15, the schema's `examples`
  array, `MessageSpec` literals, the vocabulary doc's example snippets, and
  the remaining unit read fixtures moved from two-letter (`~pp~`, `~ty~`,
  `~en~`) to three-letter collection segments. The four old-era
  integration specs (ContentsHttpRead, EntityCreate,
  PropertyDefinitionCreate — including its inline never-exists entity
  reference; TransactionMessageKafkaIngest needed nothing, its payloads
  carry no `data.id`) now derive IDs from their own transaction's UUIDv7.
  `docs/README.md` shows the current UUIDv7 format instead of the
  superseded `timestamp~increment` scheme.
- Schema: new `ObjectId` `$def` (org/grp segments, UUIDv7-or-`genesis`
  third segment, known collection abbreviation fourth, `[a-z0-9._-]+`
  suffix, optional second conforming block for the deprecated composite
  alias) wired into both collection-conditional `data` branches via
  `allOf` with a `DataWithObjectId` wrapper, so only the top-level
  `data.id` is constrained and nested `id` keys are untouched.
- New `MessageSpec` features prove the gate: the pre-TASK-044 runbook
  drift shape is rejected; the message-UUID form, the transaction-UUID
  form with a source-derived suffix, and the legacy composite alias id
  are accepted.
- Docs: vocabulary doc enforcement paragraph now describes the two-layer
  gate (schema on the wire, materializer warning as defense in depth);
  drift note 8.5 marks the identifier arc complete; the historical
  manifesto (`docs/Jade-Tipi.md`) still narrates the earlier
  `timestamp~increment` scheme and is flagged for a director prose pass.
- Observation recorded, not fixed (pre-existing): a `Transaction` built
  with a null `user` serializes `"user": null`, which the schema rejects
  (`string expected`) — every real path supplies a user, but the writer
  task (plan task C) should decide whether null-user envelopes are
  rejected intentionally or the schema should allow the field's absence
  semantics explicitly.
- Verification results: `:libraries:jade-tipi-dto:test` and
  `:jade-tipi:test` BUILD SUCCESSFUL; full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m15s) with the hard gate
  active; `git diff --check` clean; zero object-identifier-convention
  warnings across all integration spec output.
