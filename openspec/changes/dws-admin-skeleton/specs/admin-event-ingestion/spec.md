## ADDED Requirements

### Requirement: dws-admin subscribes to the full dws.events catalog
`dws-admin` SHALL subscribe, via `@DaprPubSub`, to the Dapr pub/sub component and topic named by
configuration (defaulting to component `pubsub`, topic `dws.events`, per `docs/events.md`), and
SHALL handle every event type documented there: `io.dws.definition.created`,
`io.dws.definition.updated`, `io.dws.deployment.applied`, `io.dws.deployment.failed`,
`io.dws.deployment.drained`, `io.dws.deployment.collected`, `io.dws.instance.started`,
`io.dws.instance.completed`, `io.dws.instance.failed`, `io.dws.task.started`,
`io.dws.task.completed`, `io.dws.task.failed`.

#### Scenario: Every documented event type is handled
- **WHEN** an event of any type listed in `docs/events.md`'s event catalog is published to the
  configured topic
- **THEN** `dws-admin` processes it into a read-model write, rather than leaving it unhandled

#### Scenario: Unknown event type does not crash the subscription
- **WHEN** an event with a `type` value not in the documented catalog is delivered
- **THEN** `dws-admin` logs it and acknowledges the message without throwing, and the subscription
  keeps processing subsequent events

### Requirement: Definition and deployment events upsert into the read model
`io.dws.definition.created`/`updated` events SHALL upsert a `workflow_definitions` row keyed on
`(name, version)`. `io.dws.deployment.applied`/`failed`/`drained`/`collected` events SHALL upsert a
`deployments` row keyed on `(workflow, version)`, using the payload's `orchestratorAppId` and
`stepServices` fields verbatim.

#### Scenario: definition.created creates a new definition row
- **WHEN** an `io.dws.definition.created` event for a `(workflow, version)` not yet in
  `workflow_definitions` is processed
- **THEN** a new row is created with `status` reflecting "created" and `created_at` set from the
  event payload's `createdAt`

#### Scenario: deployment.failed records the error
- **WHEN** an `io.dws.deployment.failed` event is processed
- **THEN** the corresponding `deployments` row's `status` reflects failure and its stored data
  includes the event payload's `error` field

### Requirement: Instance and task events upsert into the read model
`io.dws.instance.started`/`completed`/`failed` events SHALL upsert a `workflow_instances` row keyed
on `instance_id`. `io.dws.task.started`/`completed`/`failed` events SHALL insert a `task_events`
row referencing that `instance_id`.

#### Scenario: instance.completed sets ended_at
- **WHEN** an `io.dws.instance.completed` event is processed
- **THEN** the `workflow_instances` row for that `instance_id` has `ended_at` set from the event
  payload's `endedAt` and `status` reflecting completion

#### Scenario: task.failed is recorded with its error
- **WHEN** an `io.dws.task.failed` event is processed
- **THEN** a `task_events` row is inserted for that `instance_id`/`task_name` with `status`
  reflecting failure and `error` populated from the event payload

### Requirement: Event processing is idempotent
Before applying any event's write, `dws-admin` SHALL attempt to record the event's `id` in
`processed_events` and skip the domain write entirely if that `id` was already recorded. The
`processed_events` insert and the corresponding domain write SHALL occur inside the same database
transaction, so a crash between them leaves neither committed.

#### Scenario: Replaying the same event twice results in one row
- **WHEN** the same event (identical `id`) is delivered twice to `dws-admin`
- **THEN** exactly one corresponding row exists in the read model after both deliveries, and the
  second delivery performs no domain-table write

#### Scenario: A crash between marking processed and writing the domain row is not observable
- **WHEN** the transaction containing the `processed_events` insert and the domain upsert fails to
  commit (e.g. process crash mid-transaction)
- **THEN** neither the `processed_events` row nor the domain write is durably present, and a
  redelivery of the same event is processed as if for the first time

### Requirement: Out-of-order delivery does not corrupt aggregate state
Because delivery is at-least-once with no ordering guarantee, `dws-admin`'s upserts SHALL produce
the same final state regardless of the order in which related events for the same aggregate arrive
— specifically, a terminal status (`completed`/`failed`) SHALL never be overwritten by a `started`
event arriving after it, and `started_at`/`ended_at` SHALL each be set once and never cleared by a
later write.

#### Scenario: instance.completed arrives before instance.started
- **WHEN** `io.dws.instance.completed` for an `instance_id` is processed before any
  `io.dws.instance.started` event for that same `instance_id` has been processed
- **THEN** a `workflow_instances` row is created reflecting the completed state (including
  `ended_at`)

#### Scenario: instance.started arrives after instance.completed and does not regress status
- **WHEN** `io.dws.instance.started` for an `instance_id` is processed after
  `io.dws.instance.completed` for the same `instance_id` has already been processed
- **THEN** the `workflow_instances` row's `status` remains "completed" (not reverted to
  "started"/"running"), and `started_at` is backfilled from the `started` event's payload if it was
  not already set
