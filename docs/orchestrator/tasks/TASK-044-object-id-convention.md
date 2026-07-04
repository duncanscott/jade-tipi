# TASK-044 - Restore and enforce the object identifier convention

ID: TASK-044
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude (interactive session with director, 2026-07-04)
SOURCE_TASK:
  - TASK-040
  - TASK-021
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-044-object-id-convention.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/kli-plate-runbook.md
  - DIRECTION.md
  - clients/kafka-kli/examples/
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - gradle-verification

DIRECTOR_REVIEW (2026-07-04):
The director flagged drift from the original object identifier design:
object IDs embed a UUID version 7. Decisions ratified: (1) an object ID
embeds either the creating transaction's or the creating message's UUIDv7 —
both existing patterns are sanctioned; (2) the `jdtp-admin` genesis ID keeps
its literal `genesis` segment as the single sanctioned non-UUID exception;
(3) enforcement is document-plus-warn now, with hard schema validation
ledgered for the transitional-shapes cleanup (drift-note plan task L).

GOAL:
Re-establish `<org>~<grp>~<uuidv7>~<collection>~<suffix>` as the stated,
softly-enforced object identifier rule, and repair the drift this session
introduced plus the pre-existing v4 drift in the admin path.

DRIFT ACCOUNTING:
- Conformant: canonical dto examples (message-UUID form), TASK-042 seed and
  TASK-043 import prefixes (transaction-UUID form, D4/D5), all wire
  transaction/message UUIDs (UUIDv7 via UuidCreator).
- Drift fixed by this task: kli plate runbook and its step files (literal
  `plate96-demo` segment; TASK-040); `GroupAdminService.synthesizeId`
  (random UUIDv4; TASK-021); integration/unit fixture IDs introduced by
  TASK-040/041/043 specs (`jadetipi-itest-*` shapes and placeholder
  segments).
- Drift ledgered for plan task L (accepted-era artifacts, not churned
  here): fixture IDs in the TASK-030..037-era specs
  (`jadetipi-itest-ppyasn~pp~...` and similar), plus hard `data.id`
  schema validation once every fixture conforms.
- Sanctioned exceptions: the genesis segment
  (`...~genesis~usr~jdtp-admin`); legacy composite assignment IDs
  (`<object_id>~<property_id>`, ten segments) until plan task L retires
  that path.

ACCEPTANCE_CRITERIA:
- The vocabulary doc states the convention (structure, both sanctioned
  UUIDv7 sources, suffix uniqueness within a transaction for the
  transaction-UUID form, the genesis exception, composite assignment IDs,
  enforcement status), and DIRECTION.md carries the direction-level
  statement.
- `CommittedTransactionMaterializer` logs a warning for any create-path
  `data.id` that does not conform (structural check: at least five
  segments, UUIDv7-or-genesis in the third segment, a known collection
  abbreviation in the fourth; composite assignment IDs check both halves).
  Warn-only: materialization behavior is unchanged.
- The conformance predicate has direct unit coverage (conforming forms,
  genesis, composite, and rejection cases).
- `GroupAdminService.synthesizeId` mints UUIDv7; its spec asserts the
  version-7 segment.
- The kli plate runbook and all step files embed a fixed documented
  UUIDv7 instead of `plate96-demo`, and the runbook explains the
  convention and how to mint a fresh UUIDv7 for re-runs.
- Fixture IDs in the TASK-040/041/043 specs conform (transaction-UUID
  form derived from each spec's own txn where natural, fixed UUIDv7
  literals otherwise), so test runs stop emitting convention warnings.

OUT_OF_SCOPE:
- No `message.schema.json` change (ledgered for plan task L).
- No re-minting of the genesis ID.
- No rewriting of accepted-era (TASK-030..037) spec fixtures.
- No ID migration of existing MongoDB rows.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest --tests '*PlateTypeHierarchy*' --tests '*PlatePropertyValues*' --tests '*PropertyAssignmentKafkaMaterializeIntegrationSpec*'`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-04):
- Docs: vocabulary doc gains the "Object Identifier Convention" section
  (structure, both sanctioned UUIDv7 sources, suffix uniqueness for the
  transaction-UUID form, genesis exception, composite assignment IDs,
  warn-only enforcement status); DIRECTION.md gains the "Object
  Identifiers" direction statement.
- Materializer: `isConformingObjectId` structural predicate (>=5 segments,
  UUIDv7-or-genesis third segment, known collection abbreviation fourth,
  both halves of composite IDs) with `warnIfNonconformingObjectId` wired
  into the generic create path and the legacy assignment path. Warn-only;
  behavior unchanged. Predicate pinned by
  `CommittedTransactionMaterializerIdConventionSpec` (12 cases).
- `GroupAdminService.synthesizeId` now mints UUIDv7 via
  `UuidCreator.getTimeOrderedEpoch()` (uuid-creator is an `api` dependency
  of the dto library, already on the app classpath); the admin spec asserts
  the version-7 segment.
- kli runbook: `plate96-demo` replaced by the fixed demo UUIDv7
  `018fd84a-51a7-7e96-8de1-000000000001` across all nine step files and the
  runbook text, which now states the convention and the fresh-UUIDv7 re-run
  guidance.
- Fixtures: TASK-040/041 Kafka integration specs derive IDs from their own
  transaction's UUIDv7 (`jade-itest-org~kafka~<txn-uuid>~...`); TASK-043
  mapper-spec prefix and the TASK-040/041 unit/controller-spec placeholder
  segments replaced with conformant UUIDv7 literals.
- Verification results: full `:jade-tipi:test` green; the three gated
  integration specs green; `git diff --check` clean. Warning efficacy
  proven in the same run: the deliberately-untouched TASK-032-era spec
  emitted exactly five convention warnings (the drift ledgered for plan
  task L), while the updated specs emitted none.
