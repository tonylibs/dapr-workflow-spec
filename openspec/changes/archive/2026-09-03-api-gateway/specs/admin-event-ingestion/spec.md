## MODIFIED Requirements

### Requirement: dws-admin subscribes to the full dws.events catalog

`dws-admin` SHALL expose Dapr's programmatic-subscription contract on its Nest HTTP listener:
`GET /dapr/subscribe` MUST advertise exactly one subscription for the configured pub/sub component
and topic (defaulting to `pubsub` and `dws.events`), and the advertised `POST` callback route MUST
accept Dapr's transport CloudEvent, pass its `data` payload to the existing lifecycle-event
processor, and handle every event type documented in `docs/events.md`:
`io.dws.definition.created`, `io.dws.definition.updated`, `io.dws.deployment.applied`,
`io.dws.deployment.failed`, `io.dws.deployment.drained`, `io.dws.deployment.collected`,
`io.dws.instance.started`, `io.dws.instance.completed`, `io.dws.instance.failed`,
`io.dws.task.started`, `io.dws.task.completed`, `io.dws.task.failed`. Discovery, event callbacks,
read/write APIs, and SSE SHALL share Nest port 3000; no second Dapr application server or port
3001 SHALL be required. Owning component: `dws-admin`.

#### Scenario: Subscription discovery advertises one configured route
- **WHEN** Dapr requests `GET /dapr/subscribe`
- **THEN** the response contains exactly one entry with the configured `pubsubname`, topic, and
  callback route

#### Scenario: Every documented event type is handled
- **WHEN** Dapr posts a transport CloudEvent whose `data` contains any event type listed in
  `docs/events.md`
- **THEN** `dws-admin` unwraps it and processes it into the same read-model write as before

#### Scenario: Unknown event type does not crash the subscription
- **WHEN** the callback receives a valid inner event whose `type` is outside the documented catalog
- **THEN** `dws-admin` logs it, responds with a successful acknowledgement, and continues
  processing subsequent events

#### Scenario: Malformed inner event is dropped deliberately
- **WHEN** the transport CloudEvent's `data` is not a valid DWS lifecycle envelope
- **THEN** `dws-admin` logs the validation failure and returns a Dapr success/drop outcome rather
  than retrying an event that cannot become valid

#### Scenario: Unexpected processing failure is retried
- **WHEN** database or handler processing throws unexpectedly
- **THEN** the callback returns a non-success outcome so Dapr retries according to its pub/sub
  delivery contract

