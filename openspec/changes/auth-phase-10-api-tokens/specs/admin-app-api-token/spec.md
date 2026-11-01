# Spec Delta

## Purpose

Defines how `dws-admin` authenticates callers on its app port and authenticates itself to its own
Dapr sidecar when machine-auth tokens are configured, so the app accepts requests only from its own
sidecar and the pod-IP direct-port bypass is closed.

## ADDED Requirements

### Requirement: App port accepts only calls carrying the app API token

When `APP_API_TOKEN` is configured, `dws-admin` SHALL reject with HTTP 401 every request to its app
port that does not carry a `dapr-api-token` header equal to that value, before any body parsing or
route handling. The comparison MUST be constant-time. The 401 response SHALL NOT reveal whether the
header was absent or wrong. Owning component: `dws-admin`.

#### Scenario: Request without the header is rejected
- **WHEN** a peer sends `GET /instances` to `<pod-ip>:3000` with no `dapr-api-token` header
- **THEN** the response is 401 and no route handler runs

#### Scenario: Request with a wrong token is rejected
- **WHEN** a peer sends a request with `dapr-api-token: wrong`
- **THEN** the response is 401, identical to the missing-header response

#### Scenario: Request with the correct token is served
- **WHEN** a request carries `dapr-api-token` equal to `APP_API_TOKEN`
- **THEN** it reaches its route handler and is served normally

#### Scenario: Rejection happens before body parsing
- **WHEN** an unauthenticated peer sends a large `POST /dapr/events/dws` body
- **THEN** the response is 401 and the body is not parsed

### Requirement: Event injection through the app port is closed

`POST /dapr/events/dws` and `GET /dapr/subscribe` SHALL be served only to requests carrying the app
API token. A request without it MUST NOT reach the event subscriber, so it cannot create or modify
read-model rows or emit a live SSE event. Owning component: `dws-admin`.

#### Scenario: Forged event is rejected
- **WHEN** a pod-network peer posts a valid-looking `io.dws.instance.started` CloudEvent to
  `<pod-ip>:3000/dapr/events/dws` without the token
- **THEN** the response is 401, no database row changes, and no SSE frame is emitted

#### Scenario: Genuine sidecar delivery still works
- **WHEN** the pod's own sidecar discovers subscriptions and delivers a `dws.events` message, each
  carrying the app API token
- **THEN** the subscription registers and the event is ingested

### Requirement: Health route stays open for the kubelet

`GET /health` SHALL be served without the app API token so kubelet liveness and readiness probes,
which call the pod IP directly and cannot carry the token, keep working. No other route SHALL be
exempt, and a request that is not exactly that route, including a trailing-slash or differently
cased variant, MUST still require the token. Owning component: `dws-admin`.

#### Scenario: Probe succeeds without a token
- **WHEN** the kubelet sends `GET /health` with no `dapr-api-token`
- **THEN** the response is the normal health response

#### Scenario: Other routes are not exempt
- **WHEN** a peer sends `GET /health/` or `GET /docs` without the token
- **THEN** the response is 401

### Requirement: Token check is off unless configured

When `APP_API_TOKEN` is unset or empty, `dws-admin` SHALL behave exactly as it does today and apply
no token check. This is the `auth.enabled=false` path and also supports local development and the
existing unit and integration tests. Owning component: `dws-admin`.

#### Scenario: No token configured
- **WHEN** `dws-admin` starts without `APP_API_TOKEN`
- **THEN** requests without a `dapr-api-token` header are served as before

### Requirement: Relay presents the Dapr API token to its sidecar

When `DAPR_API_TOKEN` is configured, the controller relay SHALL send it as the `dapr-api-token`
header on its outbound call to its own sidecar, in addition to forwarding the caller's
`Authorization` header verbatim. The relay MUST NOT forward the inbound `dapr-api-token`, which is
the app API token, to the sidecar or on to the controller. Owning component: `dws-admin`.

#### Scenario: Relay attaches the Dapr API token
- **WHEN** `DAPR_API_TOKEN` is set and the relay forwards `POST /workflows`
- **THEN** the outbound sidecar request carries `dapr-api-token` equal to `DAPR_API_TOKEN` and the
  original `Authorization` header

#### Scenario: App API token is not leaked downstream
- **WHEN** the inbound request carried `dapr-api-token` equal to the app API token
- **THEN** the outbound sidecar request's `dapr-api-token` is `DAPR_API_TOKEN`, never the app token

#### Scenario: No Dapr API token configured
- **WHEN** `DAPR_API_TOKEN` is unset
- **THEN** the relay sends no `dapr-api-token` header, as today

### Requirement: Token values are never logged or returned

`dws-admin` MUST NOT write either token to logs or error messages, and MUST NOT include either in
any HTTP response body or header. Owning component: `dws-admin`.

#### Scenario: Rejected request logs no secret
- **WHEN** a request is rejected for a wrong token
- **THEN** neither the supplied value nor the configured value appears in logs or the response
