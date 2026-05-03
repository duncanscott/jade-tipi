# CLI Authentication Design: Keycloak Device Flow + ORCID + Kafka

## Overview

A command-line client authenticates users via their ORCID iD using the OAuth 2.0 Device Authorization Grant (RFC 8628). The CLI extracts the verified ORCID iD from the resulting token and includes it in messages published to a Kafka topic. The backend trusts the CLI and does not independently verify the identity.

## Components

1. **Keycloak** — Identity provider. Brokers ORCID as an external IdP. Issues tokens via the device flow.
2. **ORCID** — External OIDC identity provider, configured in Keycloak as an identity broker.
3. **CLI client** — Authenticates via Keycloak device flow. Extracts the ORCID iD from the token. Publishes messages to Kafka containing the ORCID iD and payload data.
4. **Kafka** — Message broker between the CLI and the backend.
5. **Spring Boot backend** — Kafka consumer. Trusts the ORCID iD in the message as verified.

## Keycloak Configuration

### ORCID as Identity Broker
- Add ORCID as an external OIDC identity provider in Keycloak.
- ORCID OIDC endpoints: https://orcid.org/.well-known/openid-configuration
- Configure a mapper to extract the ORCID iD from the brokered login and include it as a token claim (e.g., `orcid`).

### CLI Client Registration in Keycloak
- Client type: **public** (a CLI cannot securely store a client secret)
- Enabled grant type: **Device Authorization Grant** (RFC 8628)
- Scope: **`openid`** (no need for `offline_access` since the backend does not exchange tokens)

## CLI Authentication Flow

1. CLI sends a POST to Keycloak's device authorization endpoint:
   `POST /realms/<realm>/protocol/openid-connect/auth/device`
   with `client_id` and `scope=openid`.

2. Keycloak responds with:
   - `device_code`
   - `user_code`
   - `verification_uri` (or `verification_uri_complete`)
   - `interval` (polling interval in seconds)

3. CLI displays to the user:
   "Go to <verification_uri> and enter code: <user_code>"

4. User opens the URL in any browser (can be a different device), chooses "Sign in with ORCID" on the Keycloak login screen, and completes ORCID OAuth2 authentication.

5. CLI polls Keycloak's token endpoint with the `device_code` at the specified interval until the user completes login.

6. On success, CLI receives an ID token (JWT) containing the `orcid` claim.

7. CLI decodes the ID token and extracts the `orcid` claim. This is the verified ORCID iD.

8. CLI caches the ORCID iD locally (e.g., `~/.myapp/config`) for use in subsequent submissions without requiring re-authentication each time.

## CLI Submission Flow

1. CLI constructs a Kafka message containing:
   - The **ORCID iD** (extracted from the token during authentication)
   - The **payload data**

2. CLI publishes the message to a designated Kafka topic.

No tokens are included in the message. The ORCID iD is a plain string.

## Backend Processing Flow

1. Backend consumes a message from the Kafka topic.
2. Backend reads the ORCID iD and payload from the message.
3. Backend processes the data, trusting that the CLI verified the ORCID iD via Keycloak/ORCID authentication.

## Group Permission Direction

Jade-Tipi authorization should be based on group membership before it attempts
finer-grained exceptions. Users are members of one or more groups through
Keycloak claims or a future membership service. Objects and property assignments
are owned by groups. Members of the owning group have read/write access to the
objects and properties owned by that group.

Each `grp` record should be a normal Jade-Tipi object with a world-unique ID,
properties, possible links, and a permissions map for other groups. The initial
map should use only two permission values:

- `rw`: the referenced group may read and write objects owned by this group.
- `r`: the referenced group may read objects owned by this group.

Because property assignments are owned by groups, effective access eventually
needs to be evaluated at property scope. Object-level permission overrides and
property-value-level overrides may be useful later, but they should not be part
of the first implementation unless a concrete use case requires them.

## Trust Model

The backend trusts the CLI. The verification of the ORCID iD happens at the CLI layer during the Keycloak device flow. The ORCID iD in Kafka messages is not independently validated by the backend. This is appropriate when:
- The CLI is a known, controlled application (not arbitrary third-party code)
- The Kafka topic is restricted to authorized producers
- The threat model does not include a compromised or spoofed CLI

## Security Considerations

- **Kafka topic ACLs**: restrict which producers can write to the topic to limit the ability to inject messages with fabricated ORCID iDs.
- **TLS**: encrypt Kafka transport.
- **CLI token storage**: if caching the ORCID iD locally, use restricted file permissions (e.g., `chmod 600`).
- **Re-authentication**: consider requiring periodic re-authentication (e.g., cached ORCID iD expires after N days) to ensure the user still controls the ORCID account.

## Technology Summary

| Component | Technology |
|---|---|
| Identity Provider | Keycloak |
| External IdP (ORCID) | ORCID OIDC brokered through Keycloak |
| CLI auth protocol | OAuth 2.0 Device Authorization Grant (RFC 8628) |
| Message broker | Apache Kafka |
| Backend | Spring Boot (Groovy) with Kafka consumer |
| Identity in messages | ORCID iD as a plain string, verified at CLI auth time |

## Local Admin Group Management (TASK-021)

This section documents the local-development admin path for creating and
editing first-class Jade-Tipi `grp` records. ORCID and federated login are
intentionally not part of this path. The web admin user authenticates against
Keycloak with a normal username and password.

### Required local services

Bring up the project Docker stack before signing in or running the integration
spec:

```sh
docker compose -f docker/docker-compose.yml up -d
```

The admin path requires:

- **Keycloak** (port 8484) — issues realm-scoped access tokens that carry the
  application admin role.
- **MongoDB** — stores the root-shaped `grp` documents.

### Realm and roles

The `jade-tipi` realm import (`docker/jade-tipi-realm.json`) declares one
realm role:

| Realm role | Purpose |
|---|---|
| `jade-tipi-admin` | Authorizes `/api/admin/**` group-management endpoints. |

The Spring backend reads the user's JWT and pulls roles from
`realm_access.roles`. There is no dependency on the Keycloak `master` realm
admin user; the application authorizes from the user's own JWT only. There is
no Keycloak group synchronization, no general permission evaluation, and no
property-level enforcement in this path.

### Realm users

| Username | Display name | Email | Realm roles | Notes |
|---|---|---|---|---|
| `dnscott` | Duncan Scott | `dnscott@jade-tipi.org` | `jade-tipi-admin` | Local development admin user |
| `testuser` | (none) | `test@jade-tipi.org` | (none) | Pre-existing non-admin fixture |

`dnscott`'s development password is `dnscott` (committed in the realm import
as `temporary: false` so login does not require a password-change step).
**This is a development-only credential.** Do not reuse this account or
password in any deployed environment.

### Signing in from the Next.js UI

The frontend Auth.js (NextAuth v5) integration uses the public
`jade-tipi-frontend` Keycloak client and the standard authorization-code +
PKCE flow. After sign-in, `frontend/auth.ts` decodes the access token's
payload locally to compute `session.isAdmin = realm_access.roles.includes("jade-tipi-admin")`,
and exposes only `accessToken`, `idToken`, and `isAdmin` to the browser.
Raw realm claims and the Keycloak client secret are never copied onto the
session.

To sign in as the local admin:

1. Navigate to `http://localhost:3000` and click **Sign In with Keycloak**.
2. Use username `dnscott` / password `dnscott`.
3. The header will gain a **Groups** link visible only when
   `session.isAdmin` is true.

### Backend admin endpoints

All admin routes live under `/api/admin/**` and are guarded by
`hasRole('jade-tipi-admin')` in `SecurityConfig.groovy`. Unauthenticated
requests return `401`; authenticated-non-admin requests return `403`.

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/api/admin/groups` | Create a `grp` root. Synthesizes a world-unique id when one is not supplied. |
| `GET` | `/api/admin/groups` | List all `grp` roots. |
| `GET` | `/api/admin/groups/{id}` | Read one. `404` when missing. |
| `PUT` | `/api/admin/groups/{id}` | Full-replacement update of `name`, `description`, and `permissions`. |

The persisted documents follow the accepted `TASK-020` root-shape contract:
`_id == id`, `collection == "grp"`, `properties.{name, description,
permissions}`, `links == {}`, `_head.{schema_version, document_kind, root_id,
provenance}`. Admin direct writes mark provenance with an
`admin~<uuid>` sentinel under `_head.provenance.txn_id` (and the same value
under `commit_id`) so admin-origin records remain identifiable for future
work.

### Running the admin auth integration spec

```sh
docker compose -f docker/docker-compose.yml up -d
JADETIPI_IT_ADMIN_GROUPS=1 ./gradlew :jade-tipi:integrationTest \
    --tests '*GroupAdminAuthIntegrationSpec*'
```

The spec is gated on the env flag and a Keycloak reachability probe so it is
a no-op in environments without local services.
