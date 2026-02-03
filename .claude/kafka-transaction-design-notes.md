# Kafka Transaction Design Notes

Last updated: 2026-02-02

## Current State

Transitioning from HTTP-based to Kafka-based transaction processing.

## Key Decisions (2026-02-02)

### Architecture
- Topic per client: `jdtp-txn-<client-id>`
- Consumer pattern: `jdtp-txn-.*`
- Topics registered via HTTP, associated with org/group/client
- Trust Keycloak authentication; no per-message secrets

### ID Formats
- **transactionId**: `<UUIDv7>~<org>~<group>~<client-id>`
- **messageId**: `<UUIDv7>` (simple)
- **entityId**: `<transactionId>~<messageId>~<type>~<subtype>`
- UUIDv7 for timestamp + sortability; `uuid-creator` library for Java
- No random prefix needed (UUIDv7 provides sufficient key distribution)

### Message Structure
```json
// Transaction OPEN
{
  "transactionId": "<UUIDv7>~<org>~<group>~<client>",
  "messageId": "<UUIDv7>",
  "type": "txn",
  "subtype": "open",
  "payload": { ... }
}

// Transaction COMMIT
{
  "transactionId": "<UUIDv7>~<org>~<group>~<client>",
  "messageId": "<UUIDv7>",
  "type": "txn",
  "subtype": "commit",
  "payload": { ... }
}

// Entity operation (CREATE, UPDATE, DELETE)
{
  "transactionId": "<UUIDv7>~<org>~<group>~<client>",
  "messageId": "<UUIDv7>",
  "type": "sample",
  "subtype": "plate-384",
  "operation": "create",
  "payload": { ... }
}
```
- **type**: Always present, category of thing (`txn`, `sample`, etc.)
- **subtype**: Always present, refinement (`open`, `commit`, `plate-384`, etc.)
- **operation**: Only for entity messages (`create`, `update`, `delete`)
- Kafka message key = transactionId (partition ordering)

### Entity Storage
- All entities stored with entityId: `<transactionId>~<messageId>~<type>~<subtype>`
- Transaction entities: `<transactionId>~<msgId>~txn~open`, `<transactionId>~<msgId>~txn~commit`
- Query by transactionId + type + subtype to find transaction records
- Self-describing IDs (type/subtype visible in ID)

### Infrastructure
- Docker files moved to `docker/` directory
- Kafka 4.1.1 with KRaft (no ZooKeeper), port 9092
- Keycloak healthcheck on port 9000 (management interface)
- MongoDB requires `--profile mongodb` flag

## Dropped Concepts
- Per-message secret/hash validation (trust authenticated topic)
- Backend-generated transaction IDs (client generates with UUIDv7)
- Complex ID format with backend sequence numbers
- Random prefix for key distribution (UUIDv7 sufficient)
- "action" field (replaced by type/subtype + operation)

## Library Consolidation Needed
- `jade-tipi-id` and `jade-tipi-dto` have overlapping/muddled designs
- `JadeTipiIdDto` has prefix, timestamp, sequence fields (obsolete)
- `TransactionToken`, `TransactionOpen`, etc. have secret/hash fields (obsolete)
- Need to consolidate around new message structure

## TODO
1. Consolidate/simplify DTO libraries
2. Add Kafka dependencies to Spring Boot
3. Create Kafka consumer with pattern subscription
4. Implement topic registration endpoint
5. MongoDB persistence for entities
6. TTL cleanup for uncommitted transactions

## Files Changed (2026-02-02)
- `docker/docker-compose.yml` - Added Kafka, fixed Keycloak healthcheck
- `docker/jade-tipi-realm.json` - Added kafka-broker client, audience mappers
- `docs/OVERVIEW.md` - Updated docker paths
- `AGENTS.md` - Updated docker paths

## Useful Commands
```bash
# Start full stack
docker compose -f docker/docker-compose.yml --profile mongodb up -d

# List Kafka topics
docker exec jade-tipi-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Create topic
docker exec jade-tipi-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic jdtp-txn-jade-cli --partitions 1
```

## Client Understanding (jade CLI)
- Thin wrapper around shared JadeTipiCli library
- Authenticates with Keycloak (client credentials flow)
- Opens transactions via HTTP, stores token locally
- Commits by loading stored token and calling HTTP API
- Local storage: `~/.jade-tipi/clients/<client-id>/transactions/`
