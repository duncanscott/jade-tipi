# Kafka Transaction Design Notes

Last updated: 2026-02-03

## Current State

Transitioning from HTTP-based to Kafka-based transaction processing.
New DTO library created in `libraries/jade-tipi-dto/src/main/java/org/jadetipi/dto/message/`.

## Key Decisions

### Architecture
- Topic per client: `jdtp-txn-<client-id>`
- Consumer pattern: `jdtp-txn-.*`
- Topics registered via HTTP, associated with org/group/client
- Trust Keycloak authentication; no per-message secrets

### ID Formats
- **Transaction ID**: `<UUIDv7>~<org>~<grp>~<client>` (composed from Transaction record)
- **Message ID**: `<txn.getId()>~<uuid>` (composed from Message record)
- UUIDv7 for timestamp + sortability; `uuid-creator` library for Java
- Separator: `~` (defined in Constants.ID_SEPARATOR)

### DTO Structure (libraries/jade-tipi-dto)

**Message.java** - Main Kafka message DTO:
```java
public record Message(
    Transaction txn,    // transaction context
    String uuid,        // message UUIDv7
    Action action,      // OPEN, COMMIT, CREATE, UPDATE, DELETE
    Map<String, Object> data  // payload (snake_case keys enforced)
)
```

**Transaction.java** - Transaction identifier:
```java
public record Transaction(
    String uuid,    // transaction UUIDv7
    Group group,    // org + grp
    String client   // client ID
)
```

**Group.java** - Organization and group:
```java
public record Group(
    String org,  // organization
    String grp   // group
)
```

**Action.java** - Enum for message actions:
- OPEN, COMMIT, CREATE, UPDATE, DELETE
- Serializes to lowercase JSON: "open", "commit", etc.

### JSON Message Structure
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

### Data Field Validation
- All keys in `data` map must be snake_case
- Validation is recursive (nested objects and arrays)
- Pattern: `^[a-z][a-z0-9_]*$`
- Throws IllegalArgumentException with path on violation

### Jackson Annotations
- All DTOs have `@JsonProperty` annotations for explicit JSON contract
- `@JsonIgnore` on `getId()` methods (computed, not serialized)
- `@JsonValue`/`@JsonCreator` on Action enum for lowercase serialization
- Dependencies: jackson-annotations, jackson-databind (2.18.2)

### Utility Classes
- **MessageMapper** - serialize/deserialize Message to/from JSON
  - `toJson(Message)`, `fromJson(String)`
  - `toBytes(Message)`, `fromBytes(byte[])`
  - `toJsonPretty(Message)`

### Infrastructure
- Docker files in `docker/` directory
- Kafka 4.1.1 with KRaft (no ZooKeeper), port 9092
- Keycloak healthcheck on port 9000 (management interface)
- MongoDB requires `--profile mongodb` flag

## Keycloak Clients
1. **jade-tipi** - Backend service account
2. **jade-tipi-frontend** - Frontend (PKCE)
3. **tipi-cli** - CLI with kafka-audience mapper
4. **jade-cli** - CLI with kafka-audience mapper
5. **kafka-broker** - Kafka broker service account

## TODO
1. Add Kafka dependencies to Spring Boot
2. Create Kafka consumer with pattern subscription
3. Implement topic registration endpoint
4. MongoDB persistence for entities
5. TTL cleanup for uncommitted transactions

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
