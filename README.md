# Jade-Tipi

An open scientific metadata framework for managing JSON documents with flexible database backends.

## Overview

Jade-Tipi is a full-stack proof of concept demonstrating a reactive, cloud-native approach to scientific metadata management. Built with Spring Boot and Next.js, it provides a RESTful API and modern web interface for creating, reading, updating, and deleting JSON documents.

## Features

- **Reactive API**: Non-blocking, streaming-capable REST endpoints built with Spring WebFlux
- **Flexible Storage**: Switchable database backend (MongoDB or FoundationDB)
- **Modern Frontend**: Next.js 15 with React 19, TypeScript, and Tailwind CSS
- **Docker Integration**: Complete containerized development environment
- **Production Ready**: Spring Boot Actuator monitoring, structured logging, integration tests

## Tech Stack

**Backend**
- Spring Boot 3.5.6 with Groovy
- Spring WebFlux (reactive)
- MongoDB Reactive Driver / FoundationDB
- Java 21

**Frontend**
- Next.js 15 with Turbopack
- React 19
- TypeScript
- Tailwind CSS v4

## Quick Start

### Prerequisites

- Java 21+
- Node.js 20+
- Docker & Docker Compose

### Run with Docker

```bash
# Start MongoDB
docker-compose --profile mongodb up -d

# Run backend (port 8765)
./gradlew bootRun

# Run frontend (port 3000)
cd frontend && npm install && npm run dev
```

Access the application at http://localhost:3000

### Available Scripts

```bash
# Backend
./gradlew bootRun              # Start backend server
./gradlew test                 # Run unit tests
./gradlew integrationTest      # Run integration tests

# Frontend
npm run dev                    # Start development server
npm run build                  # Build for production

# Docker
docker-compose --profile mongodb up -d      # Start MongoDB + Mongo Express
docker-compose down                         # Stop all containers
```

## Database Backends

### MongoDB (Default)

```bash
docker-compose --profile mongodb up -d
./gradlew bootRun
```

MongoDB admin interface available at http://localhost:8081

### FoundationDB (Alternative)

```bash
docker-compose --profile foundationdb up -d
./gradlew bootRun -Pdb=foundationdb
```

## API Endpoints

- `GET /api/documents` - List all documents
- `GET /api/documents/{id}` - Get document by ID
- `POST /api/documents/{id}` - Create new document
- `PUT /api/documents/{id}` - Update existing document
- `DELETE /api/documents/{id}` - Delete document
- `DELETE /api/documents/cleanup/corrupted` - Clean up corrupted documents

## Configuration

Backend port and other settings in `gradle.properties`:

```properties
backendPort=8765
```

Frontend API URL auto-generated in `frontend/.env.local` during build.

## License

Jade-Tipi is released under a **dual-license model**:

- **Open Source License (default):**
  This program is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
  You may use, modify, and redistribute Jade-Tipi under the terms of the AGPL-3.0.

- **Commercial License:**
  For organizations that wish to integrate Jade-Tipi into proprietary or closed systems
  without the reciprocal open-source obligations of the AGPL, commercial licenses are available.

  👉 Contact **licensing@jade-tipi.org** for details and pricing.

Learn more at [https://jade-tipi.org/license](https://jade-tipi.org/license)
