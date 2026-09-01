## MODIFIED Requirements

### Requirement: Bearer middleware verifies tokens before the dws-admin app runs

At runtime, the shared API Gateway SHALL route every browser-facing `dws-admin` read, write,
OpenAPI, and SSE request to the admin app only through Dapr service invocation and the
admin sidecar's bearer middleware. The sidecar MUST reject a missing Authorization header, a
malformed token, a tampered signature, a wrong `aud`, or a wrong `iss` before Nest observes the
request. A valid token SHALL be forwarded to the matching Nest route. Dapr's internal
programmatic-subscription discovery and pub/sub callback delivery SHALL continue to reach the app
without requiring a browser bearer token. Owning component: `charts/dws`.

#### Scenario: Missing Authorization header on read
- **WHEN** the gateway sends `GET /instances` through the admin Dapr invoke path without an
  Authorization header
- **THEN** the sidecar responds 401 and Nest does not observe the request

#### Scenario: Valid bearer token reaches read and write routes
- **WHEN** requests for an admin GET route and `POST /workflows` carry a valid configured token
- **THEN** the sidecar forwards both requests to Nest on port 3000

#### Scenario: Invalid token does not reach SSE route
- **WHEN** an SSE request carries a malformed, tampered, wrong-audience, or wrong-issuer token
- **THEN** the sidecar rejects it with 401 and no SSE subscription is opened in Nest

#### Scenario: Pubsub callback remains internal
- **WHEN** Dapr discovers subscriptions or delivers a `dws.events` message to the app callback
- **THEN** the callback reaches Nest on app-port 3000 without an end-user bearer token and event
  ingestion continues
