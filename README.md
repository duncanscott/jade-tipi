# Jade-Tipi

An open scientific metadata framework and reference implementation focused on flexible, machine-actionable JSON documents.

> Looking for the broader protocol vision and long-term roadmap? Read the living design document in [`docs/Jade-Tipi.md`](docs/Jade-Tipi.md).

## Project Status

- Early proof-of-concept: the core reactive API and web UI target basic CRUD for JSON documents stored in MongoDB.
- MongoDB is the only persistence backend wired in today; FoundationDB profiles and integration layers are actively being explored.
- Frontend scaffolding exists for a document manager and will evolve towards richer curation and visualization flows.
- Expect breaking changes while the API surface, storage models, and protocol language are refined.

## Architecture Overview

### Backend (Groovy / Spring Boot WebFlux)

- Reactive REST API serving JSON metadata on port `8765`.
- Document lifecycle endpoints (`/api/documents`) powered by `ReactiveMongoTemplate`.
- Transaction token generation (`/api/transactions`) to support future write coordination.
- Gradle build with separate unit and integration test tasks; integration tests require MongoDB running.

### Frontend (Next.js 15 / React 19)

- Lightweight admin UI for creating, viewing, editing, and deleting documents.
- Fetches API base URL from `frontend/.env.local`, which the backend generates on boot.
- Uses Tailwind CSS v4 (PostCSS pipeline) and client-side routing for quick iteration.

### Data & Persistence

- Default developer profile targets the bundled MongoDB container (`docker-compose --profile mongodb`).
- FoundationDB support is experimental; configuration scaffolding lives under `jade-tipi/src/main/resources/application-foundationdb.yml`.
- Future adapters (Kafka/Flink sinks, lakehouse integration) are tracked in the architecture document.

## Getting Started

### Prerequisites

- Java 21
- Node.js 20
- Docker & Docker Compose

### First Run

```bash
# 1. Start MongoDB (foreground logs available via docker-compose)
docker-compose --profile mongodb up -d

# 2. Launch the reactive backend (generates frontend/.env.local on first run)
./gradlew bootRun

# 3. (Optional) Start the Next.js dev server
cd frontend
npm install
npm run dev
```

Visit http://localhost:3000 for the UI or http://localhost:8765/actuator/health to check the API.

Shut everything down when you are done exploring:

```bash
docker-compose down
```

### Useful Gradle Tasks

```bash
./gradlew test             # Backend unit tests
./gradlew integrationTest  # Reactive integration tests (MongoDB must be running)
./gradlew bootRun          # Run the WebFlux service on port 8765
```

## API Highlights

### Document Service (`/api/documents`)

- `GET /api/documents` — stream summaries (currently `_id` and `name` fields).
- `GET /api/documents/{id}` — fetch a JSON document by identifier (hex ObjectId or custom ID).
- `POST /api/documents/{id}` — create a new document with a caller-supplied ID.
- `PUT /api/documents/{id}` — replace an existing document.
- `DELETE /api/documents/{id}` — remove a document.
- `DELETE /api/documents/cleanup/corrupted` — purge records containing legacy Jackson metadata.

### Transaction Service (`/api/transactions`)

- `POST /api/transactions` — issue a transaction token scoped to `organization` and `group`. Returns a public identifier plus write secret; intended for future multi-party write flows.

API responses are JSON. Authentication and authorization are not yet implemented; expect secure-key and identity layers in later phases.

## Repository Layout

```
├── jade-tipi/                # Spring Boot WebFlux service (Groovy)
│   ├── src/main/groovy       # Controllers, services, and persistence adapters
│   ├── src/test/groovy       # Unit tests (JUnit 5)
│   └── src/integrationTest   # Reactive integration specs (Spock)
├── frontend/                 # Next.js 15 application
├── docs/                     # Protocol and architecture references
├── clients/                  # Gradle CLI prototypes (jade & tipi)
└── docker-compose.yml        # Developer profiles for MongoDB (and future backends)
```

## Next Steps

- Expand API to allow creation and seatch of documents.
- Finish FoundationDB adapter and transaction log streaming.
- Build richer frontend workflows for curation, provenance, and collaboration.
- Flesh out CLI clients (`clients/jade`, `clients/tipi`) for scripted interactions.

Track progress and contribute ideas in [`IMPROVEMENTS.md`](IMPROVEMENTS.md) and the issues board.

## License & Commercial Terms

Jade-Tipi is dual-licensed:

- [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) for open-source use.
- Commercial licenses for closed deployments — contact **licensing@jade-tipi.org** or see [`DUAL-LICENSE.txt`](DUAL-LICENSE.txt).

Contributions are welcomed under the AGPL-3.0. Please include the dual-license header on new Groovy or Java sources.
