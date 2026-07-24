## ADDED Requirements

### Requirement: Shared event topic and component
All DWS lifecycle events SHALL be published to a single Dapr pub/sub topic named `dws.events` on
the Dapr pub/sub component named `pubsub` (the same component referenced by `dws.default-pubsub` in
`dws-orchestrator`). Publishers MUST NOT invent per-event-type topics or alternate components.

#### Scenario: Every event uses the shared topic
- **WHEN** any DWS component publishes a lifecycle event
- **THEN** it is published to component `pubsub`, topic `dws.events`
- **AND** no lifecycle event is published to any other topic

### Requirement: CloudEvents-style envelope
Every lifecycle event SHALL be wrapped in a CloudEvents-style envelope carrying the fields `id`,
`source`, `type`, `time`, `datacontenttype`, and `data`. `type` MUST be one of the defined
`io.dws.*` event types. `datacontenttype` MUST be `application/json`. `time` MUST be an
RFC 3339 / ISO 8601 UTC timestamp. `data` MUST be the JSON payload defined for that event `type`.

#### Scenario: Envelope is well-formed
- **WHEN** a lifecycle event is published
- **THEN** the message body contains `id`, `source`, `type`, `time`, `datacontenttype`, and `data`
- **AND** `datacontenttype` equals `application/json`
- **AND** `type` is one of the documented `io.dws.*` values

#### Scenario: Source identifies the publisher
- **WHEN** `dws-controller` publishes an event
- **THEN** `source` identifies the controller
- **WHEN** `dws-orchestrator` publishes an event
- **THEN** `source` identifies the orchestrator (including its `appId`)

### Requirement: Definition event payloads
The event types `io.dws.definition.created` and `io.dws.definition.updated` SHALL carry a payload
with `workflow`, `version`, and `createdAt`.

#### Scenario: Definition event payload shape
- **WHEN** a `io.dws.definition.created` or `io.dws.definition.updated` event is published
- **THEN** `data` contains `workflow`, `version`, and `createdAt`

### Requirement: Deployment event payloads
The event types `io.dws.deployment.applied` and `io.dws.deployment.failed` SHALL carry a payload
with `workflow`, `version`, `stepServices` (a list), `orchestratorAppId`, and an optional `error`
(present on `failed`). The event types `io.dws.deployment.drained` and `io.dws.deployment.collected`
SHALL carry a payload with `workflow`, `version`, and `orchestratorAppId`.

#### Scenario: Applied and failed payloads
- **WHEN** a `io.dws.deployment.applied` event is published
- **THEN** `data` contains `workflow`, `version`, `stepServices`, and `orchestratorAppId`
- **WHEN** a `io.dws.deployment.failed` event is published
- **THEN** `data` additionally contains `error`

#### Scenario: Drained and collected payloads
- **WHEN** a `io.dws.deployment.drained` or `io.dws.deployment.collected` event is published
- **THEN** `data` contains `workflow`, `version`, and `orchestratorAppId`

### Requirement: Instance event payloads
Every instance event (`io.dws.instance.started`, `io.dws.instance.completed`, `io.dws.instance.failed`) SHALL carry a payload with `instanceId`, `workflow`, `version`, `appId`, and `startedAt`, plus an optional `endedAt` (present on completed/failed) and an optional `error` (present on failed).

#### Scenario: Instance payload shape
- **WHEN** a `io.dws.instance.started` event is published
- **THEN** `data` contains `instanceId`, `workflow`, `version`, `appId`, and `startedAt`
- **WHEN** a `io.dws.instance.completed` or `io.dws.instance.failed` event is published
- **THEN** `data` additionally contains `endedAt`
- **AND** `io.dws.instance.failed` additionally contains `error`

### Requirement: Task event payloads
The event types `io.dws.task.started`, `io.dws.task.completed`, and `io.dws.task.failed` SHALL
carry a payload with `instanceId`, `taskName`, `taskType`, `timestamp`, and an optional `error`
(present on failed).

#### Scenario: Task payload shape
- **WHEN** a `io.dws.task.started` or `io.dws.task.completed` event is published
- **THEN** `data` contains `instanceId`, `taskName`, `taskType`, and `timestamp`
- **WHEN** a `io.dws.task.failed` event is published
- **THEN** `data` additionally contains `error`

### Requirement: Documented contract at repo root
The event envelope, the topic and component, and every event type with its payload SHALL be
documented in `docs/events.md` at the repository root, because the contract is shared across two
independently-built components. The document SHALL also state the deployment prerequisite that a
Dapr pub/sub `Component` named `pubsub` carrying topic `dws.events` must exist in-cluster before
either component's event publishing works, and that this component is not provisioned by either
component's own `k8s/` manifests.

#### Scenario: Contract doc exists and is complete
- **WHEN** a developer opens `docs/events.md`
- **THEN** it describes the envelope fields, the `pubsub`/`dws.events` binding, and all `io.dws.*`
  event types with their payloads
- **AND** it states the in-cluster `pubsub` component prerequisite
