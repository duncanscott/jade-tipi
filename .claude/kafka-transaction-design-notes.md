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
- UUIDv7 for timestamp + sortability; `uuid-creator` library for Java

### Message Structure
```json
{
  "transactionId": "<UUIDv7>~<org>~<group>~<client>",
  "messageId": "<UUIDv7>",
  "action": "OPEN|COMMIT|CREATE|UPDATE|DELETE|...",
  "payload": { ... }
}
```
- All messages have consistent structure
- Action is CV (controlled vocabulary), extensible
- Payload optional for OPEN/COMMIT, required for entity actions
- Kafka message key = transactionId (partition ordering)

### Infrastructure
- Docker files moved to `docker/` directory
- Kafka 4.1.1 with KRaft (no ZooKeeper), port 9092
- Keycloak healthcheck on port 9000 (management interface)
- MongoDB requires `--profile mongodb` flag

## Dropped Concepts
- Per-message secret/hash validation (trust authenticated topic)
- Backend-generated transaction IDs (client generates with UUIDv7)
- Complex ID format with backend sequence numbers

## TODO
1. Update DTOs for new message structure
2. Add Kafka dependencies to Spring Boot
3. Create Kafka consumer with pattern subscription
4. Implement topic registration endpoint
5. MongoDB persistence (deferred - user has thought through)
6. TTL cleanup for uncommitted transactions (MongoDB handles)

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
