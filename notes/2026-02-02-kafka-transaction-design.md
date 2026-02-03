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

**Decision:** Uniform structure with type/subtype discrimination

#### Transaction Messages

```json
// OPEN transaction
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a40-7def-8b56-222222222222",
  "type": "txn",
  "subtype": "open",
  "payload": { "description": "Importing sample batch" }
}

// COMMIT transaction
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a43-7789-8e89-555555555555",
  "type": "txn",
  "subtype": "commit",
  "payload": { "comment": "Batch import complete" }
}
```

#### Entity Messages

```json
// CREATE entity
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a41-7123-8c67-333333333333",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "create",
  "payload": {
    "data": { "name": "Sample A", "volume": 100 }
  }
}

// UPDATE entity
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a42-7456-8d78-444444444444",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "update",
  "payload": {
    "entityId": "...full entity ID...",
    "data": { "volume": 150 }
  }
}

// DELETE entity
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a42-7789-8e90-666666666666",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "delete",
  "payload": {
    "entityId": "...full entity ID..."
  }
}
```

**Field definitions:**
- **type**: Always present. Category of thing (`txn`, `sample`, `experiment`, etc.)
- **subtype**: Always present. Refinement (`open`, `commit`, `plate-384`, etc.)
- **operation**: Only for entity messages (`create`, `update`, `delete`)
- **payload**: Message-specific data

**Rationale:**
- Type/subtype become part of entityId (self-describing IDs)
- For transaction lifecycle, subtype IS the operation (`open`, `commit`)
- For entities, operation is separate because type/subtype describe the entity, not the action

### 6. Entity Storage Model

**Storage key:** entityId = `<transactionId>~<messageId>~<type>~<subtype>`

**Transaction entities:**
```
// OPEN record
018fd849-...~jade-org~my-group~jade-cli~018fd849-...-222222~txn~open

// COMMIT record
018fd849-...~jade-org~my-group~jade-cli~018fd849-...-555555~txn~commit
```

**Finding transaction records:**
- Query by transactionId + type="txn" + subtype="open" or "commit"
- The messageId varies, so lookup is by indexed fields, not exact entityId match
- Given any transactionId (from CREATE/UPDATE/DELETE messages), you can find both records

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

## Library Consolidation Needed

The existing DTO libraries have overlapping and now-obsolete designs:

### jade-tipi-id (`JadeTipiIdDto`)
- Contains: prefix, timestamp, sequence, organization, group, uuid, type, subtype
- **Obsolete:** prefix, timestamp, sequence fields
- **Keep:** organization, group, type, subtype concepts

### jade-tipi-dto (transaction DTOs)
- `TransactionToken`: id, secret, grp — obsolete (secret-based)
- `TransactionOpen`: transactionId, hash — obsolete (hash-based)
- `TransactionAction`: transactionId, messageId, hash, message — obsolete (hash-based)
- `TransactionCommit`: transactionId, messageId, hash, secret — obsolete (secret+hash)
- `Group`: organization, group — still useful

**Recommendation:** Consolidate into new DTOs matching the Kafka message structure.

---

## Example Complete Transaction

```json
// Kafka topic: jdtp-txn-jade-cli
// All messages keyed by transactionId

// Message 1: OPEN
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a40-7def-8b56-222222222222",
  "type": "txn",
  "subtype": "open",
  "payload": { "description": "Importing sample batch" }
}

// Message 2: CREATE sample
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a41-7123-8c67-333333333333",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "create",
  "payload": {
    "data": { "name": "Sample A", "volume": 100 }
  }
}

// Message 3: CREATE sample
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a42-7456-8d78-444444444444",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "create",
  "payload": {
    "data": { "name": "Sample B", "volume": 200 }
  }
}

// Message 4: COMMIT
{
  "transactionId": "018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli",
  "messageId": "018fd849-2a43-7789-8e89-555555555555",
  "type": "txn",
  "subtype": "commit",
  "payload": { "comment": "Batch import complete" }
}
```

**Resulting entity IDs:**
```
// OPEN entity
018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli~018fd849-2a40-7def-8b56-222222222222~txn~open

// Sample A entity
018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli~018fd849-2a41-7123-8c67-333333333333~sample~plate-384

// Sample B entity
018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli~018fd849-2a42-7456-8d78-444444444444~sample~plate-384

// COMMIT entity
018fd849-2a40-7abc-8a45-111111111111~jade-org~my-group~jade-cli~018fd849-2a43-7789-8e89-555555555555~txn~commit
```

---

## Security Model Summary

1. **Topic-level authentication:** Keycloak SASL/OAuth controls who can write to topics
2. **Topic registration:** Associates topic with organization/group/client
3. **Transaction isolation:** All messages in a topic share the same org/group/client
4. **No per-message secrets:** Trust authenticated topic; simplifies implementation

---

## Next Steps

1. **Consolidate DTO libraries** — create new DTOs matching Kafka message structure
2. **Add Kafka dependencies** to Spring Boot project
3. **Create Kafka consumer** with pattern subscription `jdtp-txn-.*`
4. **Implement topic registration endpoint**
5. **MongoDB persistence** — store entities with entityId as key
6. **TTL cleanup** for uncommitted transactions

---

## References

- RFC 9562: UUIDv7 specification
- Keycloak 26 documentation (health endpoint on management port 9000)
- Apache Kafka 4.1.1 with KRaft
