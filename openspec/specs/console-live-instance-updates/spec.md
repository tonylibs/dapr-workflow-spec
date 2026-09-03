# console-live-instance-updates

## Purpose

`dws-console`'s consumption of `dws-admin`'s push API to keep the instance list and instance
detail screens current for running instances without the operator manually refreshing.

## Requirements

### Requirement: Instance detail live status
The instance detail route (`routes/instances/$id.tsx`) SHALL subscribe to the instance's push
stream while its status is `started`, and SHALL apply received status and task-event updates to
the rendered header and task timeline without a full page reload or manual refresh.

#### Scenario: Running instance updates live
- **WHEN** the instance detail route is open for an instance with status `started` and a task
  event is pushed for it
- **THEN** the task timeline reflects the new event without the operator clicking "Refresh"

#### Scenario: Instance reaches terminal status while viewed
- **WHEN** the instance detail route is open for a running instance and it transitions to
  `completed` or `failed`
- **THEN** the header updates to the terminal status and end timestamp, and the route stops
  subscribing to further updates for that instance

#### Scenario: Already-completed instance does not subscribe
- **WHEN** the instance detail route is opened for an instance whose status is already `completed`
  or `failed`
- **THEN** the route does not open a live subscription for it

### Requirement: Instance list live status for running rows
The instance list route (`routes/instances/index.tsx`) SHALL subscribe to the fleet-wide push
stream and, for each currently-loaded row whose status is `started`, apply status and end-timestamp
updates to that row in place. Rows already in a terminal status, and instances not currently loaded
on the page, SHALL NOT be affected.

#### Scenario: Loaded running row updates in place
- **WHEN** the instance list is showing a loaded row with status `started` and that instance's
  status changes
- **THEN** the row's status badge and end-timestamp column update without reloading the page or
  losing the current scroll/pagination position

#### Scenario: Completed rows are not live-patched
- **WHEN** the instance list is showing rows already in a terminal status
- **THEN** those rows do not react to further push events

### Requirement: Resync on connect and reconnect
On opening or re-establishing a push subscription, the console SHALL fetch current state via the
existing `GET` endpoints before or alongside applying subsequently received live deltas, so a gap
in the stream (initial connect, dropped connection, reconnect) does not leave stale data displayed
indefinitely.

#### Scenario: Reconnect after a dropped stream resyncs
- **WHEN** a live subscription drops and later reconnects
- **THEN** the console re-fetches the instance's current state via `GET` so any changes missed
  while disconnected are reflected, rather than only resuming from the next pushed event

### Requirement: Graceful degradation when push is unavailable
If a push subscription fails to open or errors, the console SHALL continue to render the
last-fetched data and the existing manual "Refresh" control SHALL remain functional; a push failure
SHALL NOT block or error the route.

#### Scenario: Push stream unreachable
- **WHEN** the push endpoint is unreachable or returns an error when the console attempts to
  subscribe
- **THEN** the route still renders the data already fetched via `GET`, and the operator can still
  use "Refresh" to update it manually

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
