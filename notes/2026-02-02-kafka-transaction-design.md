# Kafka Transaction Design Discussion - February 2, 2026

## Overview

This document captures the design decisions and architectural direction from a working session focused on evolving the Jade-Tipi transaction system from HTTP-based to Kafka-based message processing.

## Context

The project had existing HTTP-based transaction management:
- `POST /api/transactions/open` - opens a transaction, returns ID + secret
- `POST /api/transactions/commit` - commits a transaction using the secret

The goal was to redesign for Kafka message streaming while maintaining security and simplicity.

---

## Key Design Decisions

### 1. Kafka Topic Strategy

**Decision:** One topic per client, pattern-based subscription

- Topic naming convention: `jdtp-txn-<client-id>` (e.g., `jdtp-txn-jade-cli`)
- Consumer subscribes to pattern: `jdtp-txn-.*`
- Topics are registered with the backend via HTTP before use
- Each topic is associated with organization, group, and client-id at registration time

**Rationale:**
- Avoids multiple clients writing to the same topic
- Simplifies access control and auditing
- Pattern subscription auto-discovers new topics

### 2. Authentication Model

**Decision:** Trust Keycloak authentication at the topic level; no per-message secrets

**Evolution of thinking:**
1. Initially considered including a secret in messages with hash validation
2. Realized Kafka SASL/OAuth with Keycloak provides sufficient authentication
3. Decided the complexity of secret management wasn't justified when topics are already authenticated

**Rationale:**
- Defense in depth provided by Keycloak topic authentication
- Simpler client implementation
- No need for response channels to return secrets

### 3. ID Formats

#### Transaction ID

**Format:** `<UUIDv7>~<organization>~<group>~<client-id>`

**Example:** `018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli`

**Components:**
- UUIDv7: Provides timestamp (epoch ms) + uniqueness, chronologically sortable
- Organization: From registered topic
- Group: From registered topic
- Client-id: Keycloak client identifier

#### Message ID

**Format:** Simple `<UUIDv7>`

**Example:** `018fd849-2a41-7123-8c67-333333333333`

**Rationale:** Message IDs always exist within transaction context; full format would be redundant.

#### Entity ID

**Format:** `<transactionId>~<messageId>~<type>~<subtype>`

**Example:** `018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli~018fd849-2a41-7123-8c67-333333333333~sample~plate-384`

**Components:**
- transactionId: Links entity to its creating transaction
- messageId: Unique identifier for the creating message
- type: Category of entity (e.g., `txn`, `sample`, `experiment`)
- subtype: Refinement (e.g., `open`, `commit`, `plate-384`)

**Rationale:**
- Self-describing IDs (anyone can see type/subtype in the ID)
- Every entity traces back to its originating transaction
- Chronologically sortable via UUIDv7 components

#### Key Distribution (Prefix Decision)

**Decision:** No random prefix needed; UUIDv7 provides sufficient key distribution

**Rationale:**
- MongoDB handles timestamp-prefixed keys well (like its own ObjectIds)
- The 74 random bits in UUIDv7 provide distribution within time periods
- Dropping the prefix preserves chronological sortability
- Simpler ID structure

### 4. UUIDv7 Selection

**Decision:** Use UUIDv7 (RFC 9562) for all ID generation

**Key properties:**
- First 48 bits: Unix epoch timestamp in milliseconds
- Lexicographically sortable = chronologically sortable
- Standard UUID format (database compatible)

**Consideration:** Within the same millisecond, ordering is not guaranteed (random bits). This was deemed acceptable since:
- Kafka preserves message order within partitions
- Millisecond precision sufficient for transaction ordering
- Monotonic variants available if needed later

**Library support:** Growing across all major languages. `uuid-creator` library recommended for Java.

### 5. Message Structure

**Decision:** Uniform structure with action-based discrimination

**Note:** The initial design used type/subtype/operation fields. The implemented structure uses a simpler action enum approach. See "DTO Implementation" section below for the final structure.

#### Implemented JSON Structure

```json
{
  "txn": {
    "uuid": "018fd849-2a40-7abc-8a45-111111111111",
    "group": {
      "org": "jade-tipi-org",
      "grp": "development"
    },
    "client": "jade-cli"
  },
  "uuid": "018fd849-2a40-7def-8b56-222222222222",
  "action": "open",
  "data": {
    "description": "importing sample batch"
  }
}
```

**Field definitions:**
- **txn**: Transaction context (uuid, group, client)
- **uuid**: Message UUIDv7 (unique to this message)
- **action**: Enum value (`open`, `commit`, `create`, `update`, `delete`)
- **data**: Message-specific payload (all keys must be snake_case)

**Rationale:**
- Simpler structure than type/subtype/operation
- Action enum provides clear discrimination
- Snake_case enforcement on data keys ensures consistency

### 6. Kafka Message Key

**Decision:** Use transaction ID as the Kafka message key

**Rationale:**
- Ensures all messages for a transaction go to the same partition
- Guarantees ordering within a transaction
- Natural partitioning strategy
- Transaction ID composed as: `txn.uuid~txn.group.org~txn.group.grp~txn.client`

---

## Infrastructure Changes

### Docker Compose Updates

1. **Moved files to `docker/` directory** to reduce root sprawl:
   - `docker-compose.yml`
   - `jade-tipi-realm.json`

2. **Added Kafka 4.1.1 with KRaft** (no ZooKeeper):
   - Port 9092 for client connections
   - PLAINTEXT security (authentication at application layer)

3. **Fixed Keycloak healthcheck:**
   - Health endpoint is on port 9000 (management interface) in Keycloak 26
   - Updated healthcheck to use correct port

4. **Added Keycloak clients:**
   - `kafka-broker` client for broker authentication
   - Added `kafka-audience` mapper to CLI clients

### Current Docker Stack

| Service | Port | Purpose |
|---------|------|---------|
| Keycloak | 8484 | OAuth2/OIDC authentication |
| Kafka | 9092 | Message streaming |
| MongoDB | 27017 | Data persistence (requires `--profile mongodb`) |

---

## DTO Implementation (2026-02-03)

New DTOs have been created in `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/`:

### Message.java
Main Kafka message record:
```java
public record Message(
    Transaction txn,           // transaction context
    String uuid,               // message UUIDv7
    Action action,             // OPEN, COMMIT, CREATE, UPDATE, DELETE
    Map<String, Object> data   // payload with snake_case keys
)
```

### Transaction.java
Transaction identifier:
```java
public record Transaction(
    String uuid,    // transaction UUIDv7
    Group group,    // organization + group
    String client   // client ID
)
```

### Group.java
Organization and group:
```java
public record Group(
    String org,   // organization
    String grp    // group
)
```

### Action.java
Enum for message actions:
- `OPEN`, `COMMIT`, `CREATE`, `UPDATE`, `DELETE`
- Serializes to lowercase JSON via `@JsonValue`

### Features
- **Jackson annotations** for explicit JSON contract (`@JsonProperty`, `@JsonIgnore`)
- **Snake_case validation** on `data` keys (recursive, including nested objects/arrays)
- **MessageMapper utility** for JSON serialization/deserialization

---

## Example JSON Message

```json
{
  "txn": {
    "uuid": "018fd849-2a40-7abc-8a45-111111111111",
    "group": {
      "org": "jade-tipi-org",
      "grp": "development"
    },
    "client": "jade-cli"
  },
  "uuid": "018fd849-2a40-7def-8b56-222222222222",
  "action": "open",
  "data": {
    "description": "importing sample batch"
  }
}
```

### ID Composition
- **Transaction ID:** `txn.uuid~txn.group.org~txn.group.grp~txn.client`
  - Example: `018fd849-2a40-7abc-8a45-111111111111~jade-tipi-org~development~jade-cli`
- **Message ID:** `<transaction-id>~<message-uuid>`
  - Example: `018fd849-...~jade-tipi-org~development~jade-cli~018fd849-2a40-7def-8b56-222222222222`

---

## Security Model Summary

1. **Topic-level authentication:** Keycloak SASL/OAuth controls who can write to topics
2. **Topic registration:** Associates topic with organization/group/client
3. **Transaction isolation:** All messages in a topic share the same org/group/client
4. **No per-message secrets:** Trust authenticated topic; simplifies implementation

---

## Keycloak Clients

The `jade-tipi` realm includes five pre-configured clients:

1. **jade-tipi** - Backend service account
2. **jade-tipi-frontend** - Frontend with PKCE
3. **tipi-cli** - CLI with `kafka-audience` mapper
4. **jade-cli** - CLI with `kafka-audience` mapper
5. **kafka-broker** - Kafka broker service account

---

## Next Steps

1. **Add Kafka dependencies** to Spring Boot project
2. **Create Kafka consumer** with pattern subscription `jdtp-txn-.*`
3. **Implement topic registration endpoint**
4. **MongoDB persistence** — store entities with entityId as key
5. **TTL cleanup** for uncommitted transactions

---

## References

- RFC 9562: UUIDv7 specification
- Keycloak 26 documentation (health endpoint on management port 9000)
- Apache Kafka 4.1.1 with KRaft
