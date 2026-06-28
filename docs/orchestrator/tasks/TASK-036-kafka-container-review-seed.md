# TASK-036 - Kafka container review seed

ID: TASK-036
TYPE: implementation
ARTIFACT_INTENT: implementation
STATUS: READY_FOR_REVIEW
OWNER: direct-codex
SOURCE_TASK:
  - TASK-019
  - TASK-026
  - TASK-027
  - TASK-028
  - TASK-035
NEXT_TASK:
  - TASK-037
PAUSE_SOURCE_TASKS: true
OWNED_PATHS:
  - docs/architecture/clarity-esp-container-mapping.md
  - docs/orchestrator/tasks/TASK-036-kafka-container-review-seed.md
  - jade-tipi/src/integrationTest/groovy/org/jadetipi/jadetipi/kafka/
REQUIRED_CAPABILITIES:
  - code-implementation
  - docker-stack
  - gradle-verification

GOAL:
Create a small, opt-in Kafka-backed review seed that persists representative
Clarity/ESP container and sample data into local MongoDB so the resulting
Jade-Tipi JSON root structures can be inspected before building a broader
import/synchronization path or UI.

ACCEPTANCE_CRITERIA:
- Publish the seed through Kafka using canonical `Message` DTOs and the
  existing `TransactionMessageListener` / `TransactionMessagePersistenceService`
  / `CommittedTransactionMaterializer` path.
- Materialize stable review rows for the documented ESP freezer/bin/plate chain,
  the documented Clarity tube, one ESP Illumina Library sample entity from the
  plate contents, and `contents` links for freezer->bin, bin->plate, and
  plate->sample.
- Include a transaction-local `contents` link-type `typ` root and a simple
  `illumina_library` entity-type `typ` root.
- Keep all review root IDs stable and delete only those seed roots plus the
  seed transaction WAL rows before republishing, so repeated seed runs replace
  the review set predictably.
- Leave the materialized `loc`, `lnk`, `ent`, and `typ` roots in MongoDB after
  the spec completes for manual JSON review.
- Gate the spec behind both `JADETIPI_IT_KAFKA` and `JADETIPI_REVIEW_SEED`, with
  the same fast Kafka reachability probe pattern used by existing Kafka
  integration specs.
- Target the local application Mongo database `jdtp` by default for review
  inspection, with `JADETIPI_REVIEW_SEED_MONGO_DATABASE` available as an
  explicit override.
- Document the local run and Mongo inspection commands.

OUT_OF_SCOPE:
- No production CouchDB importer or ongoing synchronization.
- No direct CouchDB reads in this slice; the seed uses the already-documented
  representative records.
- No frontend UI.
- No write-side materializer changes, DTO schema changes, or root-document
  contract changes.
- No property-definition/property-assignment loop for the sample entity; the
  review seed uses root inline properties for the first JSON inspection point.
- No Keycloak-gated HTTP read verification; this slice is about persisted
  MongoDB structures produced through Kafka.

IMPLEMENTATION_SUMMARY:
- Added `ClarityEspContainerReviewSeedKafkaIntegrationSpec`, an opt-in Kafka
  integration seed guarded by `JADETIPI_IT_KAFKA=1` and
  `JADETIPI_REVIEW_SEED=1`.
- The seed publishes one committed transaction containing:
  - two `typ + create` messages (`contents`, `illumina_library`);
  - four `loc + create` messages (ESP freezer, ESP bin, ESP plate, Clarity tube);
  - one `ent + create` message (ESP Illumina Library `LHCPOT`);
  - three `lnk + create` messages for freezer->bin, bin->plate, and
    plate->library containment.
- The spec clears only the stable review seed roots and the stable seed
  transaction WAL rows before publishing, then intentionally leaves the fresh
  materialized roots in MongoDB database `jdtp` for inspection.
- Updated the Clarity/ESP container mapping document with the TASK-036 review
  seed contract, run command, and Mongo inspection notes.

VERIFICATION_RESULTS:
- `./gradlew :jade-tipi:compileIntegrationTestGroovy` passed.
- `JADETIPI_IT_KAFKA=1 JADETIPI_REVIEW_SEED=1 ./gradlew :jade-tipi:integrationTest --tests '*ClarityEspContainerReviewSeedKafkaIntegrationSpec*'`
  passed.
- Manual Mongo verification confirmed database `jdtp` contains the expected
  review rows: `typ=2`, `loc=4`, `ent=1`, `lnk=3`, plus `txn=11` WAL rows for
  transaction
  `018fd849-c0c0-7000-8a01-c1a141e5e501~jade-tipi-org~dev~review-seed`.
- Manual Mongo verification confirmed the accidental pre-override `test_db`
  seed rows were removed.
- `./gradlew :jade-tipi:test` passed.
- `git diff --check` passed.
