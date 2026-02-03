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

### 3. Transaction ID Format

**Decision:** `<UUIDv7>~<organization>~<group>~<client-id>`

Example: `018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli`

**Components:**
- UUIDv7: Provides timestamp (epoch ms) + uniqueness, chronologically sortable
- Organization: From registered topic
- Group: From registered topic
- Client-id: Keycloak client identifier

**Rationale:**
- Self-describing IDs work outside Kafka context
- UUIDv7 chosen over UUIDv4 for embedded timestamp and sortability
- Consistent with existing `~` separator convention
- Client generates the ID (no HTTP call needed for ID generation)

### 4. Message ID Format

**Decision:** Simple UUIDv7

Example: `018fd849-2a41-7123-8c67-333333333333`

**Rationale:**
- Message IDs always exist within transaction context
- Full format would be redundant
- Simpler for clients to generate

### 5. UUIDv7 Selection

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

### 6. Message Structure

**Decision:** Uniform structure for all messages with action-based discrimination

```json
{
  "transactionId": "<UUIDv7>~<org>~<group>~<client>",
  "messageId": "<UUIDv7>",
  "action": "<ACTION>",
  "payload": { ... }
}
```

**Action controlled vocabulary:**
- `OPEN` - Start transaction (payload optional, can contain metadata)
- `COMMIT` - Finalize transaction (payload optional, can contain metadata)
- `CREATE` - Create new entity
- `UPDATE` - Modify existing entity
- `DELETE` - Remove entity
- *(extensible for future actions)*

**Rationale:**
- Consistent structure across all message types
- Optional payload on OPEN/COMMIT allows for future use cases
- Action as CV (controlled vocabulary) is extensible

### 7. Kafka Message Key

**Decision:** Use `transactionId` as the Kafka message key

**Rationale:**
- Ensures all messages for a transaction go to the same partition
- Guarantees ordering within a transaction
- Natural partitioning strategy

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

## Topics for Future Implementation

### Immediate Next Steps

1. **Update DTOs** to match new message structure:
   - Remove secret-related fields
   - Add action field
   - Simplify to new format

2. **Add Kafka dependencies** to Spring Boot project

3. **Create Kafka consumer** with pattern subscription

4. **Implement topic registration endpoint**

### Deferred Decisions

1. **MongoDB persistence model** for pending/committed transactions
2. **TTL/cleanup** for uncommitted transactions (MongoDB will handle)
3. **Specific payload schemas** for CREATE/UPDATE/DELETE actions

---

## Example Complete Transaction

```json
// Kafka topic: jdtp-txn-jade-cli
// All messages keyed by transactionId

// Message 1: OPEN
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli",
  "messageId": "018fd849-2a40-7def-8b56-222222222222",
  "action": "OPEN",
  "payload": { "description": "Importing sample batch" }
}

// Message 2: CREATE
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli",
  "messageId": "018fd849-2a41-7123-8c67-333333333333",
  "action": "CREATE",
  "payload": {
    "entityType": "sample",
    "data": { "name": "Sample A", "volume": 100 }
  }
}

// Message 3: CREATE
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli",
  "messageId": "018fd849-2a42-7456-8d78-444444444444",
  "action": "CREATE",
  "payload": {
    "entityType": "sample",
    "data": { "name": "Sample B", "volume": 200 }
  }
}

// Message 4: COMMIT
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-tipi_org~my-group~jade-cli",
  "messageId": "018fd849-2a43-7789-8e89-555555555555",
  "action": "COMMIT",
  "payload": { "comment": "Batch import complete" }
}
```

---

## Security Model Summary

1. **Topic-level authentication:** Keycloak SASL/OAuth controls who can write to topics
2. **Topic registration:** Associates topic with organization/group/client
3. **Transaction isolation:** All messages in a topic share the same org/group/client
4. **No per-message secrets:** Trust authenticated topic; simplifies implementation

---

## References

- RFC 9562: UUIDv7 specification
- Keycloak 26 documentation (health endpoint on management port 9000)
- Apache Kafka 4.1.1 with KRaft
