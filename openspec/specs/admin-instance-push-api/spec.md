# admin-instance-push-api

## Purpose

`dws-admin`'s server-push surface for workflow-instance and task status changes, so a client
watching a running instance observes status transitions as they're ingested instead of polling.

## Requirements

### Requirement: Instance detail push stream
`dws-admin` SHALL expose `GET /instances/:id/events` as a server-sent-events stream that emits an
event whenever the identified instance's status changes or a new task event is recorded for it.
Emitted event payloads SHALL carry the same fields as the corresponding `GET /instances/:id` and
`GET /instances/:id/tasks` responses (instance status/timestamps, or task name/type/status/
timestamp/error).

#### Scenario: Instance status change is pushed
- **WHEN** a connected client is streaming `GET /instances/:id/events` for a running instance and
  that instance's status is updated to `completed` or `failed` in the read model
- **THEN** the client receives an event carrying the new status and end timestamp

#### Scenario: New task event is pushed
- **WHEN** a connected client is streaming `GET /instances/:id/events` and a new task event is
  recorded for that instance
- **THEN** the client receives an event carrying that task event's name, type, status, timestamp,
  and error when present

#### Scenario: Unknown instance id
- **WHEN** a client requests `GET /instances/:id/events` for an id with no matching instance
- **THEN** the system returns `404` and does not open a stream

### Requirement: Stream closes on terminal instance status
`dws-admin` SHALL close an open `GET /instances/:id/events` stream once the identified instance
reaches a terminal status (`completed` or `failed`), after delivering the terminal status event.

#### Scenario: Stream ends after terminal event
- **WHEN** an instance being streamed transitions to `completed` or `failed`
- **THEN** the client receives that terminal status event and the server then ends the stream

### Requirement: Fleet-wide instance status stream
`dws-admin` SHALL expose `GET /instances/events` as a server-sent-events stream that emits a
lightweight delta (`instanceId`, `status`, `endedAt`) whenever any instance's status changes,
without requiring a per-instance subscription.

#### Scenario: Any instance status change is pushed
- **WHEN** a connected client is streaming `GET /instances/events` and any instance's status
  changes
- **THEN** the client receives a delta identifying that instance's id, new status, and end
  timestamp

### Requirement: No historical replay on connect
Both push endpoints SHALL emit only events that occur after the stream connection is established;
they SHALL NOT replay events that occurred before connection. A client that needs current state as
of connection time SHALL obtain it from the corresponding `GET` endpoint.

#### Scenario: Connecting client does not receive past events
- **WHEN** a client opens `GET /instances/:id/events` or `GET /instances/events` after status
  changes have already occurred
- **THEN** the client receives no events describing those already-occurred changes, only events
  for changes that occur from connection time forward

### Requirement: Push endpoints follow the existing read-API CORS policy
Both push endpoints SHALL be reachable under the same cross-origin policy as `dws-admin`'s existing
`GET` read endpoints (`CORS_ORIGINS`-controlled, credential-less), so a browser client that can call
`GET /instances/:id` can also open these streams without additional configuration.

#### Scenario: Browser client from an allowed origin can open a stream
- **WHEN** a browser page served from an origin allowed by `CORS_ORIGINS` opens
  `GET /instances/:id/events`
- **THEN** the stream opens without a cross-origin failure
