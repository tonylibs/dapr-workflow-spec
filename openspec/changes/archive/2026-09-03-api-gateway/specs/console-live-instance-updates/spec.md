## ADDED Requirements

### Requirement: Live streams carry bearer authentication over fetch

The console SHALL open both admin SSE endpoints with a fetch-based streaming transport that can
set `Authorization: Bearer <current-token>`. It MUST preserve named SSE event parsing, abort on
unmount or terminal instance state, reacquire a current token before reconnecting, and invoke the
existing resync behavior after reconnection. Native `EventSource` SHALL NOT be used for the gated
admin streams because it cannot set the bearer header. Owning component: `dws-console`.

#### Scenario: Fleet stream carries bearer token
- **WHEN** a signed-in operator opens the instance list
- **THEN** the `/instances/events` streaming fetch includes the current Authorization header

#### Scenario: Instance stream aborts at terminal state
- **WHEN** `/instances/:id/events` reports that the viewed instance completed or failed
- **THEN** the console aborts that streaming request and opens no further reconnect for it

#### Scenario: Reconnect uses renewed token and resyncs
- **WHEN** a stream disconnects after OIDC has renewed the access token
- **THEN** the reconnect acquires the new token, sends it in the header, and refetches current
  instance state to cover the delivery gap

#### Scenario: Stream authentication failure degrades gracefully
- **WHEN** token acquisition fails or the stream responds 401
- **THEN** the console stops reconnecting anonymously, retains last-fetched data, and surfaces a
  sign-in/session-expired outcome while manual authenticated refresh remains available

