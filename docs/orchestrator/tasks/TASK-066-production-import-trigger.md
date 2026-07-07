# TASK-066 - Production import trigger: plan and drive clarity imports from a CLI

ID: TASK-066
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-059
  - TASK-060
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-066-production-import-trigger.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Turn the proven clarity aliquot import slice (TASK-059) into something
that can be run deliberately against real data: a CLI entry point in the
excisable `importers/jgi-import` module that plans a named scope into
the dependency-ordered `import_queue`, drives pending items to the
transaction topic in batched open→messages→commit transactions, records
progress on the queue rows, is resumable after interruption, and reports
counters. Every import prerequisite is in place (value updates TASK-061,
snapshot isolation TASK-062/063); what is missing is the trigger — today
the drive loop exists only inside the live integration test.

DESIGN:
- **Form factor: CLI, not an HTTP endpoint** (recommended and accepted
  2026-07-07): scriptable, runs on demand, no always-on import service,
  and the trigger stays inside the excisable module rather than adding
  JGI-specific routes to the shared backend. The module gains a small
  Spring Boot main (`JgiImportCliApplication`, web-type NONE, its own
  `spring.config.name=jgi-import-cli` so the module's resources never
  collide with the application's YAML on the shared itest classpath) run
  via the Gradle `application` plugin:
  `./gradlew :importers:jgi-import:run --args='...'`.
- **The drive loop moves from the itest into a main-scope
  `ClarityImportDriver`**, reproducing the proven mechanics: drain
  `pendingInOrder` in batches; map each item by kind (bootstrap type /
  container / artifact / process) through `ClarityAliquotImportMapper`;
  mint message-UUID-form JDTP ids (`<org>~<grp>~<uuid>~<collection>~
  <suffix>`, plain-String concat — the GString gotcha); record each
  row's minted id (`recordJdtpId`) before publishing; publish one
  transaction per batch (open → mapped messages → commit) through an
  `ImportMessagePublisher` (Kafka producer, acks=all); `markDone` with
  the transaction id after the commit is sent. The module's main source
  set gains the dto library, kafka-clients, and uuid-creator (library
  dependencies only — the app still never depends on the module).
- **Resume and cross-batch references**: the id resolver consults, in
  order — this run's minted ids, then the queue row's recorded
  `jdtp_id` (`jdtpIdOf`, the cross-batch mechanism built in TASK-059),
  then mints fresh. A referenced dependency imported by an earlier run
  or batch therefore resolves to its recorded id instead of minting a
  duplicate. Interruption safety falls out of the existing machinery:
  items not yet marked done stay pending; a batch whose commit never
  published leaves an open transaction that the lease (TASK-063)
  rolls back durably, and the re-run re-emits the same recorded ids in
  a fresh transaction.
- **Failure isolation**: a source document missing at drive time marks
  that item failed (with the error) and the drive continues — bulk
  resilience; failures surface in the report and the exit code.
- **CLI contract** (properties or `--args`):
  `jgi-import.mode=plan|drive|import` (import = plan then drive);
  `jgi-import.process=<doc id>[,<doc id>...]` for planning;
  `jgi-import.org` / `jgi-import.grp` / `jgi-import.user` (org and grp
  REQUIRED for any mode that mints ids — no defaults, so nothing is
  ever minted under an accidental identity); `jgi-import.batch-size`
  (default 200); `jgi-import.kafka.bootstrap-servers` and
  `jgi-import.kafka.topic` (required for drive; the publisher bean is
  conditional on the topic property so application and test contexts
  without it are untouched); Mongo/CouchDB via the standard
  `spring.data.mongodb.uri` and `jadetipi.import.couchdb.*` properties.
  Exit code is non-zero when any item failed.
- **Scope discovery is deliberately explicit**: planning takes process
  document ids. "All processes of type X" discovery belongs to clarity
  phase 2 (it needs CouchDB view/find support in the reader).
- **The live integration test drives through the production path**: the
  existing `ClarityAliquotImportKafkaIntegrationSpec` drive section is
  reworked to call the driver (its inline loop retires), asserting the
  same materialized graph via the queue rows' recorded ids and
  endpoint-queried links, plus the report counters.

ACCEPTANCE_CRITERIA:
- Unit: driver features cover the published transaction envelope
  (open → mapped messages in queue order → commit, one transaction per
  batch, multiple batches under a small batch size); `recordJdtpId`
  before publish and `markDone` with the transaction id after; the
  resolver preferring recorded ids (resume/cross-batch) over fresh
  mints; a missing source document marking the item failed without
  stopping the batch; an empty queue publishing nothing.
- The reworked live itest imports the real aliquot process end to end
  THROUGH THE DRIVER: committed transaction with the full message
  count, prc root with resolved `output_input`, analyte/container
  contents link with position, fil roots with produced_by links,
  procedure_input link, every queue row done with the driver's
  transaction id and recorded jdtp_id, zero failed items in the report.
- The CLI main class boots (web NONE, own config name) and the runner
  and publisher beans are property-gated so the application context and
  the other integration tests are unaffected (their green runs prove
  it).
- Module docs (`importers/jgi-import/docs/bulk-import-design.md`) gain
  the trigger section (invocation, properties, resume semantics);
  DIRECTION.md Bulk Import notes the trigger as implemented.

OUT_OF_SCOPE:
- Clarity phase 2 (process-type discovery, the other 51 process types,
  samples, artifact groups, files relation).
- ESP import phases; the Kafka output feed.
- Any change to the shared application.

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented as designed — the import can now be run deliberately. The
drive loop moved from the integration test into main scope
(`ClarityImportDriver`), a Kafka publisher and a property-gated Spring
Boot CLI joined it, and the live aliquot integration test now imports
the real process THROUGH the production path: driver + module publisher,
one committed transaction, 14/14 items done, zero failures, full graph
materialized. All suites green in both modules (unit + integration,
`JADETIPI_IT_KAFKA=1`); `git diff --check` clean.

CHANGES (all inside the excisable module):
- `ClarityImportDriver` (main): batched drain of `pendingInOrder`; maps
  by kind through the mapper; mints message-UUID-form ids under the
  caller's org/grp (plain-String concat); the resolver prefers this
  run's mints, then the row's recorded `jdtp_id` (resume/cross-batch),
  then mints fresh; records ids BEFORE publishing; publishes
  open → messages → commit per batch (acks=all, every send awaited);
  marks rows done with the transaction id after the commit; a missing
  source document marks that item failed and the batch continues.
  Returns an `ImportDriveReport` (batches, done, failed, messages,
  txn ids).
- `ImportMessagePublisher` (main): thin awaited-send Kafka producer,
  keyed by transaction id.
- `JgiImportCliApplication` (main): Spring Boot main, web-type NONE,
  own `spring.config.name=jgi-import-cli` (module YAML inert on the
  shared itest classpath). Modes plan/drive/import; org/grp REQUIRED to
  drive (no default identity); publisher bean conditional on
  `jgi-import.kafka.topic`, runner conditional on `jgi-import.mode`, so
  the application context and the other integration tests are untouched
  — their green runs prove it. Non-zero exit when any item failed.
  Invocation: `./gradlew :importers:jgi-import:run --args='...'`
  (application plugin).
- `ClarityAliquotImportMapper` became a bean (`@Service`; it was
  previously constructed inline by the test).
- Module build: main gains the dto library, kafka-clients, uuid-creator,
  and reactor-netty-http (runtime, for the standalone WebClient) —
  library dependencies only; the application still never depends on the
  module, and excisability is unchanged.

TESTS:
- `ClarityImportDriverSpec` (5 features): one transaction per batch with
  open → mapped messages in queue order → commit and org/grp/user
  identity on every message; ids recorded and rows done-marked with the
  transaction id; the resolver preferring a recorded id over a fresh
  mint; a missing document failing one item while the batch ships the
  rest; an empty queue publishing nothing; a batch size of 1 splitting
  into two transactions.
- `ClarityAliquotImportKafkaIntegrationSpec` reworked to the production
  path: the driver + module publisher (activated by the
  `jgi-import.kafka.*` test properties) drain the planned queue; the
  report shows 1 batch / 14 done / 0 failed; `message_count` matches;
  the prc `output_input`, typed analyte with positioned contents link,
  fil roots with produced_by links, and the procedure_input link are
  asserted via the rows' recorded ids and endpoint-queried links;
  every row ends done with the drive transaction and a conformant
  recorded id. Cleanup now purges by provenance txn_id.

DOCS:
- `importers/jgi-import/docs/bulk-import-design.md`: "The production
  trigger (TASK-066)" section (invocation, properties, resume,
  failure handling, health signal); the overlap section's stale
  "value updates must be implemented first" prose updated to
  implemented (TASK-061).
- DIRECTION.md Bulk Import: trigger recorded as implemented.

NOTES:
- Interruption safety is inherited, not bolted on: unpublished commits
  leave open transactions for the TASK-063 lease to roll back; reruns
  reuse recorded ids and land as clean creates or counted idempotent
  conflicts.
- Next in this thread: clarity phase 2 (process-type discovery needs
  CouchDB view/find support in the reader, then the remaining 51
  process types, samples, artifact groups, files).
