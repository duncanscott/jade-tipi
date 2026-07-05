# Jade-Tipi Roadmap

This roadmap keeps the implementation work aligned with the longer-range
Jade-Tipi program: a compelling MVP, a durable JDTP specification, and a
FAIR-native scientific data platform for human and AI collaboration.

It is not a release commitment. It is a planning document for choosing the next
bounded development slice without losing the larger objective.

## North Star

Jade-Tipi should become portable scientific metadata infrastructure:

- a JSON-first protocol for transparent, world-mergeable scientific metadata;
- a transaction model that preserves provenance and can be replayed;
- a reference implementation that proves the model against real laboratory
  metadata;
- a platform where search, graph, vector, archive, UI, and AI-agent
  capabilities can be added as derived services rather than hard-coded into the
  core object model.

The current MVP path is intentionally grounded in local JGI evidence: represent
container and sample information from the replicated `clarity` and
`esp-entity` CouchDB databases in Jade-Tipi, submit representative updates via
Kafka messages, persist the resulting root-shaped JSON in MongoDB, and then
build a UI that can inspect and eventually update those container records.

## Proposal Context

Jade-Tipi fits well with AI-for-science programs such as DOE's Genesis Mission.
DOE describes Genesis as a national effort that brings DOE National Labs,
industry, academia, supercomputers, AI systems, experimental facilities, and
unique datasets together to accelerate scientific discovery and national
science and technology challenges:

- DOE Genesis Mission overview:
  <https://www.energy.gov/undersecretaryforscience/genesis-mission/genesis-mission>
- DOE Office of Science FOA page for "The Genesis Mission: Transforming Science
  and Energy with AI" (`DE-FOA-0003612`):
  <https://science.osti.gov/grants/FOAs/FOAs/2026/DE-FOA-0003612>

The durable takeaway for Jade-Tipi is not that the project should depend on one
funding call. The takeaway is that the roadmap should keep producing artifacts
that are proposal-ready:

- inspectable real laboratory JSON examples;
- a clear transaction/provenance model;
- AI-ready search and retrieval surfaces;
- architecture diagrams that separate protocol, storage, streams, indexes, and
  user-facing tools;
- a written JDTP specification that can stand apart from the reference
  implementation.

## Architecture Commitments

### Protocol first

The JDTP protocol and transaction model should remain independent of any one
database, stream processor, queue, or search engine. A Jade-Tipi deployment
should be described in terms of stable roles:

- canonical transaction log;
- materialized entity/location/link/property repository;
- delivery mechanism for submitted or committed changes;
- derived read models;
- optional search, graph, vector, and archive sidecars.

The current implementation uses MongoDB and Kafka because they are practical
for the local MVP and because Kafka exercises ordering, replay, idempotency,
and transaction-log durability. Kafka should remain an adapter, not the
definition of JDTP.

### Canonical truth vs. delivery

Keep these concepts separate:

- Canonical truth: JDTP transactions and materialized root documents.
- Delivery: Kafka, Kinesis, DynamoDB Streams, EventBridge, SQS/SNS, HTTP, or
  another adapter that moves transaction messages and change events.

This separation keeps a local Kafka-backed MVP compatible with a future
cloud-native deployment where the canonical log might live in DynamoDB,
Postgres, MongoDB, FoundationDB, S3/Iceberg, or another durable store.

### Derived capabilities

Advanced query and AI capabilities should be derived from the repository and
transaction log. They should not become the source of truth.

Planned adapter seams:

- `SearchProvider`: OpenSearch, Elasticsearch, MongoDB Atlas Search, Postgres
  full-text search, Lucene, or a no-op implementation.
- `GraphProvider`: Neo4j, JanusGraph, or another relationship index.
- `VectorProvider`: pgvector, OpenSearch vectors, a dedicated vector database,
  or a no-op implementation.
- `ArchiveProvider`: S3/Iceberg, lakehouse tables, compressed transaction log
  exports, or institutional archive storage.

The first implementation of any provider should be narrow and demonstrable:
index a small number of committed root documents, prove replay/update behavior,
and keep the canonical repository authoritative.

## Development Tracks

### Track 1: Container/sample MVP

Goal: make real or representative JGI container/sample data inspectable through
Jade-Tipi.

Current state:

- `TASK-036` creates a Kafka-backed review seed that leaves representative
  Clarity/ESP container and sample roots in local MongoDB.
- `TASK-037` adds `GET /api/locations/{id}/contents`, composing existing read
  services into a forward resolved location-contents API.

Next useful slices:

- Build a small UI screen for reviewing one seeded container/location and its
  immediate contents through `/api/locations/{id}/contents`.
- Add a narrow local CouchDB-to-JDTP import loop that reads a small selected
  set from `clarity` and `esp-entity`, emits canonical JDTP messages, and
  proves the data lands in MongoDB through the same materializer path.
- Add an update workflow only after read/review UX exposes the JSON structures
  well enough to judge what edits should look like.

### Track 2: JDTP specification and examples

Goal: keep implementation decisions feeding a protocol document rather than
remaining only code behavior.

Current state:

- `TASK-047` extracted the ratified contracts into
  `docs/jdtp-specification.md` (version 0.1.0-draft): identifiers, the
  root-document contract, types and inheritance, property values, links,
  groups, users and writer audit, the message vocabulary and lifecycle,
  idempotency, read models, and an enforcement summary — every section
  tagged Normative vs Planned.

Next useful slices:

- Keep the specification current with each ratified change; bump its
  version when a Planned section becomes Normative.
- Maintain canonical example transactions for `loc`, `lnk`, `ent`, `typ`,
  `ppy`, and `grp`.
- Keep example IDs and source-system mappings stable enough that reviewers can
  compare JSON across releases.

### Track 3: Search and AI retrieval

Goal: make Jade-Tipi useful to both human users and AI agents without coupling
the core repository to one search product.

Next useful slices:

- Define a minimal `SearchProvider` contract against committed root documents.
- Prototype a local implementation after the container/sample seed is stable.
- Index enough container/sample fields to answer operational searches such as
  barcode, sample name, container type, status, and source-system ID.
- Defer vector and semantic search until the full-text indexing contract is
  proven and replay behavior is clear.

### Track 4: Cloud-native portability

Goal: make a future AWS deployment credible without making the local MVP more
complex than necessary.

Next useful slices:

- Document how JDTP maps onto a DynamoDB transaction table plus materialized
  object table.
- Document event-delivery alternatives: DynamoDB Streams, Kinesis Data Streams,
  EventBridge, and SQS/SNS.
- Identify which guarantees are required from any delivery adapter: ordering
  scope, idempotency, replay, retention, failure handling, and exactly what
  remains canonical when delivery is lossy or short-retained.

### Track 5: Proposal and publication artifacts

Goal: convert steady implementation progress into reusable material for
papers, proposals, and collaborator discussions.

Next useful slices:

- Maintain one current architecture diagram that shows JDTP protocol,
  transaction log, repository, read models, search, graph, vector, and archive
  sidecars.
- Write one proposal-ready paragraph per development track explaining the
  scientific value, technical novelty, and next demonstrable milestone.
- Preserve reviewer notes and accepted decisions in the architecture docs so
  they can be cited later without reconstructing the reasoning from commits.

## Guardrails

- Prefer real source-system examples over invented demo data when practical.
- Keep near-term tasks reviewable and bounded; do not jump from the seed data
  directly to a broad importer or cloud rewrite.
- Do not make Kafka, MongoDB, OpenSearch, DynamoDB, or any other product part
  of the JDTP definition.
- Treat search, graph, vector, and archive systems as projections from the
  canonical transaction/repository state.
- Keep UI work tied to inspectable backend data so screens validate real JSON
  structures rather than aspirational workflows.
