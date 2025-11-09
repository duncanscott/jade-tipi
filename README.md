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
- Transaction token generation (`/api/transactions/open`) to support future write coordination.
- JWT-based authentication via Keycloak OAuth2/OIDC integration.
- Gradle build with separate unit and integration test tasks; integration tests require MongoDB and Keycloak running.

### Frontend (Next.js 15 / React 19)

- Lightweight admin UI for creating, viewing, editing, and deleting documents.
- NextAuth.js (v5) integration with Keycloak for user authentication.
- Fetches API base URL from `frontend/.env.local`, which the backend generates on boot.
- Uses Tailwind CSS v4 (PostCSS pipeline) and client-side routing for quick iteration.

### Authentication (Keycloak)

- Keycloak 26.0 provides OAuth2/OpenID Connect authentication.
- Realm: `jade-tipi` with pre-configured clients for backend, frontend, and CLI tools.
- Test user credentials: `testuser` / `testpassword`
- JWT tokens are validated by the backend for all `/api/**` endpoints.

### Data & Persistence

- Default developer profile targets the bundled MongoDB container (port 27017).
- Keycloak container provides authentication services (port 8484).
- FoundationDB support is experimental; configuration scaffolding lives under `jade-tipi/src/main/resources/application-foundationdb.yml`.
- Future adapters (Kafka/Flink sinks, lakehouse integration) are tracked in the architecture document.

**Docker Services:**
```bash
docker-compose up         # Start MongoDB and Keycloak
docker-compose down       # Stop and remove containers
docker-compose logs -f    # View logs from all services
```

## Getting Started

### Prerequisites

- Java 21
- Node.js 20
- Docker & Docker Compose

### First Run

```bash
# 1. Start MongoDB and Keycloak containers
docker-compose up -d

# 2. Wait for Keycloak to initialize (about 30 seconds)
#    Check readiness: curl http://localhost:8484/health/ready

# 3. Launch the reactive backend (generates frontend/.env.local on first run)
./gradlew bootRun

# 4. In a new terminal, start the Next.js dev server
cd frontend
npm install
npm run dev
```

**Access the Application:**
- **Frontend UI**: http://localhost:3000
- **Backend API**: http://localhost:8765/actuator/health
- **Keycloak Admin**: http://localhost:8484 (admin/admin)

**Sign In to the Application:**
1. Navigate to http://localhost:3000
2. Click "Sign In with Keycloak"
3. Login with test credentials:
   - **Username**: `testuser`
   - **Password**: `testpassword`
4. You can now create, view, edit, and delete documents

Shut everything down when you are done exploring:

```bash
docker-compose down
```

### Environment Configuration

The backend automatically generates `frontend/.env.local` on startup with the API URL. For authentication to work, ensure these Keycloak settings are present:

```env
# Auto-generated
NEXT_PUBLIC_API_URL=http://localhost:8765

# Required for authentication (should be present after setup)
KEYCLOAK_CLIENT_ID=jade-tipi-frontend
KEYCLOAK_CLIENT_SECRET=
KEYCLOAK_ISSUER=http://localhost:8484/realms/jade-tipi
AUTH_SECRET=jade-tipi-secret-change-in-production-minimum-32-characters-required
```

### Useful Gradle Tasks

```bash
./gradlew test             # Backend unit tests
./gradlew integrationTest  # Reactive integration tests (MongoDB must be running)
./gradlew bootRun          # Run the WebFlux service on port 8765
```

## API Highlights

### Document Service (`/api/documents`)

**All document endpoints require JWT authentication via Bearer token.**

- `GET /api/documents` — stream summaries (currently `_id` and `name` fields).
- `GET /api/documents/{id}` — fetch a JSON document by identifier (hex ObjectId or custom ID).
- `POST /api/documents/{id}` — create a new document with a caller-supplied ID.
- `PUT /api/documents/{id}` — replace an existing document.
- `DELETE /api/documents/{id}` — remove a document.
- `DELETE /api/documents/cleanup/corrupted` — purge records containing legacy Jackson metadata.

### Transaction Service (`/api/transactions`)

- `POST /api/transactions/open` — issue a transaction token scoped to `organization` and `group`. Returns a public identifier plus write secret; intended for future multi-party write flows.

### Authentication

**Obtaining a JWT Token:**

For testing with curl or CLI clients, obtain a token from Keycloak:

```bash
curl -X POST http://localhost:8484/realms/jade-tipi/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=jade-tipi-backend" \
  -d "client_secret=d84c91af-7f37-4b8d-9157-0f6c6a91fb45"
```

**Making Authenticated API Calls:**

```bash
# Get access token
TOKEN=$(curl -s -X POST http://localhost:8484/realms/jade-tipi/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=jade-tipi-backend" \
  -d "client_secret=d84c91af-7f37-4b8d-9157-0f6c6a91fb45" | jq -r '.access_token')

# Use token to access API
curl -H "Authorization: Bearer $TOKEN" http://localhost:8765/api/documents
```

**Public Endpoints** (no authentication required):
- `/` — root endpoint
- `/version` — application version
- `/docs` — API documentation
- `/actuator/**` — health and metrics
- `/error` — error handling

All responses are JSON format.

### Keycloak Realm Configuration

The `jade-tipi` realm includes four pre-configured clients:

1. **jade-tipi-backend** (confidential client)
   - Used by the Spring Boot backend for service account authentication
   - Client ID: `jade-tipi-backend`
   - Client Secret: `d84c91af-7f37-4b8d-9157-0f6c6a91fb45`
   - Supports: Authorization Code Flow, Client Credentials Grant

2. **jade-tipi-frontend** (public client)
   - Used by the Next.js frontend via NextAuth.js
   - Client ID: `jade-tipi-frontend`
   - Uses PKCE for secure browser-based authentication

3. **tipi-cli** (confidential client)
   - CLI tool with custom claims (`tipi_org`, `tipi_group`)
   - Client ID: `tipi-cli`
   - Client Secret: `7e8d5df5-5afb-4cc0-8d56-9f3f5c7cc5fd`

4. **jade-cli** (confidential client)
   - CLI tool with custom claims
   - Client ID: `jade-cli`
   - Client Secret: `62ba4c1e-7f7e-46c7-9793-f752c63f2e10`

**Realm Configuration:** `jade-tipi-realm.json` is automatically imported on Keycloak startup.

## Repository Layout

```
├── jade-tipi/                # Spring Boot WebFlux service (Groovy)
│   ├── src/main/groovy       # Controllers, services, and persistence adapters
│   ├── src/test/groovy       # Unit tests (JUnit 5)
│   └── src/integrationTest   # Reactive integration specs (Spock)
├── frontend/                 # Next.js 15 application
├── docs/                     # Protocol and architecture references
├── clients/                  # Gradle CLI prototypes (jade & tipi)
├── jade-tipi-realm.json      # Keycloak realm configuration
└── docker-compose.yml        # MongoDB and Keycloak containers
```

## Troubleshooting

### Frontend "Failed to Fetch" Errors

If the frontend cannot access the API:

1. **Check that Keycloak is running:**
   ```bash
   docker ps --filter "name=jade-tipi-keycloak"
   curl http://localhost:8484/health/ready
   ```

2. **Verify frontend environment variables** in `frontend/.env.local`:
   - Must include `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_ISSUER`, and `AUTH_SECRET`
   - If missing, add them manually (see Environment Configuration section above)

3. **Restart the frontend dev server** after updating `.env.local`:
   ```bash
   cd frontend
   # Stop with Ctrl+C, then:
   npm run dev
   ```

4. **Clear browser session** and sign in again if authentication was working before

### Integration Tests Failing

Integration tests require both MongoDB and Keycloak:

```bash
# Start all services
docker-compose up -d

# Wait for Keycloak to be ready (30 seconds)
sleep 30

# Run tests
./gradlew integrationTest
```

### Keycloak Realm Not Imported

If logging in fails with "invalid client" errors:

```bash
# Restart Keycloak to re-import the realm
docker restart jade-tipi-keycloak
sleep 20
```

The realm configuration in `jade-tipi-realm.json` is automatically imported when Keycloak starts with the `--import-realm` flag.

## Next Steps

- Expand API to allow creation and search of documents.
- Finish FoundationDB adapter and transaction log streaming.
- Build richer frontend workflows for curation, provenance, and collaboration.
- Flesh out CLI clients (`clients/jade`, `clients/tipi`) for scripted interactions.

Track progress and contribute ideas in [`IMPROVEMENTS.md`](IMPROVEMENTS.md) and the issues board.

## License & Commercial Terms

Jade-Tipi is dual-licensed:

- [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) for open-source use.
- Commercial licenses for closed deployments — contact **licensing@jade-tipi.org** or see [`DUAL-LICENSE.txt`](DUAL-LICENSE.txt).

Contributions are welcomed under the AGPL-3.0. Please include the dual-license header on new Groovy or Java sources.
