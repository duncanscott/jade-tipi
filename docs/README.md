# Jade-Tipi: JSON Transparent Data Protocol

**A Modern Protocol for Managing Metadata at the JGI and Beyond**

---

## Overview

Jade-Tipi (JDTP) is designed to be a domain-agnostic, technology-agnostic foundation for any organization needing scalable, FAIR, machine-actionable metadata. By addressing key shortcomings of current FAIR systems, Jade-Tipi provides a frictionless ecosystem where scientific data is easily shared and fully machine-actionable.

The protocol is entirely text-based, using the commonplace JSON format, and is consequently free of any tie-in to particular vendor implementations. The JSON objects that comprise the system are completely transparent to machine actors seeking to ingest and develop insights from the repository.

## Vision

Jade-Tipi is intended as a generic implementation of the FAIR data principles outlined in the Nature article ["The FAIR Guiding Principles for Scientific Data Management and Stewardship"](https://www.nature.com/articles/sdata201618). FAIR guidelines aim to make data sharing and machine access more efficient. The original paper stresses that while adherence to FAIR principles is a step in the right direction, current FAIR systems suffer shortcomings that are a source of significant friction. The primary problem is the proliferation of bespoke systems, which require myriad complex custom parsers.

Jade-Tipi offers a flexible, extensible standard suitable for any domain, scientific or otherwise. It promotes a transparent and navigable data ecosystem.

The broad vision for Jade-Tipi is to have it adopted by various systems seeking to follow FAIR guidelines. This adoption could involve translating existing systems into Jade-Tipi or developing new systems based on the protocol. Widespread adoption could foster shared vocabularies, canonical properties, and the development of general-purpose parsers, visualizers, and extensions.

## Key Benefits

- **Flexible, Rapid Evolution:** Metadata is stored as JSON so new data types and properties can be added without disruptive migrations or schema changes.

- **Designed to Work with Established Schema Languages:** May store objects constrained by LinkML, JSON Schema, Protocol Buffer, and Avro schemas. See the [full specification](Jade-Tipi.md#extending-jade-tipi-with-linkml-and-external-schemas) for details.

- **Foundation for Automated Science:** Jade-Tipi provides a machine-friendly protocol for adding new metadata and a standard API for retrieving results from past experiments.

- **Mergeability:** Jade-Tipi metadata repositories are designed to facilitate merging data from different groups and institutions into common repositories.

- **Granular Collaboration:** Fine-grained permission controls let multiple teams and collaborators safely annotate and enrich metadata, with clear ownership tracking.

- **Development of Common Vocabulary:** Widespread use will help establish shared vocabularies and canonical properties. Combining data into common repositories provides the opportunity to translate type and property names to facilitate the evolution of common nomenclature.

- **Proven Technology Stack:** Leverages established, scalable technologies (Kafka, Flink, FoundationDB, etc.) for reliability and future integrations.

- **Enables Full FAIR Compliance:** Adopting Jade-Tipi as a LIMS (Laboratory Information Management System) with integrated electronic notebooks would provide the detailed provenance of data.

- **Seamless Lakehouse Integration:** Publishes all metadata changes to a transaction stream, allowing flexible loading and transformation into one or more lakehouses for analysis and experimentation.

- **Extensible Query API:** The API can be extended to support advanced graph, geospatial, and full-text search, as well as aggregations.

The development and adoption of Jade-Tipi would position the JGI as a leader in open, reproducible science and machine-accessible metadata.

## Core Concepts

### Metadata Types

Metadata in Jade-Tipi falls into three broad categories:

1. **Entity** — Things, real or conceptual (e.g., organism, sequence file, assembly, annotation, collaborator)
2. **Property** — Characteristics used to describe entities (e.g., volume, Ensembl ID, gene function)
3. **Link** — Relationships between two entities, consisting of named pointers and optional properties

### World-Unique Identifiers

Every object in a Jade-Tipi is assigned a **world-unique ID**. Naming conventions ensure that IDs are unique without requiring a central repository. IDs are generated on demand by a local service and follow a structured format:

```
institution~group~timestamp~increment~type~suffix
```

For example:
```
lbl_gov~jgi_pps~1747977406~azba~en~aab
```

This naming scheme ensures:
- IDs from different repositories never conflict
- Metadata can be merged seamlessly between institutions
- All IDs work directly as keys in popular databases (MongoDB, PostgreSQL, Redis, etc.)

### Groups and Permissions

All objects belong to a **group**, which defines ownership and read/write permissions. Members of one group may add properties to objects owned by another group. For example, a QC group may analyze a DNA assembly owned by the assembly group and assign a quality score. Properties are owned by the group that added them, enabling fine-grained permission control.

### Types and Validation

A **type** is defined as a set of properties. Types may include **validations** to determine whether instances meet requirements defined by schemas (JSON Schema, Avro, Protocol Buffers, LinkML, etc.). Types may subclass other types, inheriting their property sets and validations while adding new ones.

### Transactions

Metadata objects are added and modified via **transactions**. Each transaction:
- Receives a sequential ID when initiated
- Includes a secret key for authentication
- Contains a series of messages that create or modify objects
- Is committed with a commit ID once all messages are processed

The transaction stream serves as the definitive "source of truth" for system state. The history is preserved in the transaction stream, which can be compressed, saved, and used to recover the system at any point in time.

## Current Implementation Status

This repository contains a **proof-of-concept reference implementation** of the Jade-Tipi protocol. The project is actively taking shape, with core functionality being developed and refined.

### What's Working

**Backend (Spring Boot WebFlux / Groovy):**
- Reactive REST API serving JSON metadata on port 8765
- Document lifecycle endpoints (`/api/documents`) for CRUD operations
- Transaction token generation (`/api/transactions/open` and `/api/transactions/commit`)
- MongoDB persistence with proper BSON type handling (dates stored as Date objects)
- JWT-based authentication via Keycloak OAuth2/OIDC integration
- Comprehensive test coverage with unit and integration tests

**Authentication (Keycloak):**
- OAuth2/OpenID Connect authentication
- Pre-configured realm with multiple client types (backend, frontend, CLI)
- Custom claims for organization and group membership
- Test user and service account credentials

**Data Persistence:**
- MongoDB backend for document storage (port 27017)
- Transaction metadata tracking with proper timestamp handling
- Support for world-unique ID format in transaction documents

### What's Planned

**FoundationDB Integration:**
- FoundationDB is the target database for production deployments
- Can handle trillions of objects with transactional guarantees
- Configuration scaffolding exists but integration is experimental

**Transaction Streaming:**
- Kafka server to implement submission and transaction streams
- Kafka Streams application to validate messages and update repository
- Flink connectors to push changes to data lakes

**Frontend:**
- A lightweight Next.js UI exists as a stub for future development
- Current implementation provides basic document management interface
- Not yet functional for production use
- Future versions will support richer curation, provenance tracking, and collaboration workflows

**Extended Query API:**
- Graph queries with JanusGraph
- Full-text search, aggregations, and geospatial queries with Elasticsearch
- Data submission endpoints to streamline message creation

## Technical Architecture

The current implementation follows a reactive, microservices-oriented architecture:

### Backend Services

The Spring Boot WebFlux backend provides:

- **Document Service**: CRUD operations on JSON documents stored in MongoDB
- **Transaction Service**: Generation of transaction IDs with timestamp-based sequencing and secret key authentication
- **Permission System**: Integration with Keycloak for group-based access control

Transaction IDs are generated using a timestamp-based format with sequence numbers, ensuring global uniqueness and ASCII sort order preservation for commit ordering.

### Data Model

Documents in MongoDB include:
- **Entities**: Stored as flexible JSON documents with dynamic properties
- **Transactions**: Include metadata about opens and commits with proper BSON Date types for timestamp fields (`opened`, `committed`)
- **Groups**: Organization and group identifiers embedded in transaction records

The system stores timestamps as Java `Instant` objects, which MongoDB persists as BSON Date types. This enables proper date-based queries and indexing.

### Authentication Flow

1. Client requests JWT token from Keycloak
2. Keycloak validates credentials and issues token with custom claims (`tipi_org`, `tipi_group`)
3. Client includes Bearer token in API requests
4. Backend validates token and extracts group membership
5. Group information controls document access and transaction permissions

## Next Steps

The project is evolving toward the full vision outlined in [`Jade-Tipi.md`](Jade-Tipi.md). Near-term priorities include:

- Complete FoundationDB adapter and transaction log streaming
- Implement full transaction protocol with submission and commit streams
- Develop richer metadata type system with validation support
- Build link management capabilities for entity relationships
- Enhance CLI clients for scripted interactions
- Expand API to support advanced queries (graph, geospatial, full-text)

## Documentation

- **Full Protocol Specification**: See [`Jade-Tipi.md`](Jade-Tipi.md) for complete technical details
- **Project Setup**: See the [root README](../README.md) for development setup instructions
- **API Documentation**: Available at `http://localhost:8765/swagger-ui/index.html` when running locally

## Recording Data Provenance

A great challenge in fulfilling FAIR guidelines is describing the provenance of data. If Jade-Tipi is adopted to record how data is created, not just what data has been created, it can straightforwardly accomplish the objective of sharing data provenance. This would involve adopting Jade-Tipi as a LIMS (Laboratory Information Management System) to record step-by-step how data is produced.

The protocol can be extended with conventions for "Procedure" and "Task" entities that link inputs to outputs, creating a complete audit trail of laboratory work. See the [full specification](Jade-Tipi.md#jade-tipi-lims--recording-data-provenance) for details on this vision.

## License & Commercial Terms

Jade-Tipi is dual-licensed:

- [GNU Affero General Public License v3.0 (AGPL-3.0)](../LICENSE) for open-source use
- Commercial licenses for closed deployments — contact **licensing@jade-tipi.org** or see [`DUAL-LICENSE.txt`](../DUAL-LICENSE.txt)

Contributions are welcomed under the AGPL-3.0.

---

**Author:** Duncan Scott
**Organization:** JGI Production Informatics
**Contact:** dscott@lbl.gov
**Last Updated:** January 2025
