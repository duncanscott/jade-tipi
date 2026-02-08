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
