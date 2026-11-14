## ADDED Requirements

### Requirement: Trigger starts carry a deterministic instance ID

Owner: `dws-schedule-time` (caller); Phase 6b `dws-schedule-event` SHALL reuse this contract. A trigger
start SHALL name the target workflow, its active version's orchestrator app-id, the input, and an
instance ID `<source>:<workflow>:<fire-key>`, where `<fire-key>` is unique per logical fire. Two
deliveries of the same logical fire SHALL produce the same instance ID.

#### Scenario: Same fire delivered twice yields one instance
- **GIVEN** a cron trigger whose fire for 2026-11-05T00:00Z is delivered to `dws-schedule-time` twice
- **WHEN** the scheduler handles both deliveries
- **THEN** both start calls carry instance ID `dws-sched:<workflow>:20261105T000000Z`
- **AND** exactly one workflow instance with that ID exists

#### Scenario: Distinct fires yield distinct instances
- **GIVEN** an `every: PT1H` trigger that fires at 10:00Z and 11:00Z
- **WHEN** both fires are handled
- **THEN** two instances exist, with IDs differing only in the fire key

### Requirement: Instance ID sources are reserved per trigger kind

Owner: `dws-schedule-time`. `<source>` SHALL be `dws-sched` for `every` and `cron` and `dws-after` for
`after`. `dws-event` SHALL be reserved for Phase 6b.

#### Scenario: after uses its own source
- **GIVEN** an `after` trigger for `order-sync` at sequence 3
- **WHEN** the scheduler starts the instance
- **THEN** its ID is `dws-after:order-sync:3`

### Requirement: Time-triggered input is empty

Owner: `dws-schedule-time`. A time-triggered start SHALL pass an empty JSON object as the workflow input.

#### Scenario: Input is an empty object
- **GIVEN** a time trigger fires
- **WHEN** the instance starts
- **THEN** the workflow's input is `{}`

### Requirement: Orchestrator start endpoint accepts a caller-supplied instance ID

Owner: `dws-orchestrator`. `POST /workflows/{name}/instances` SHALL accept an optional `instanceId`
query parameter. When it is absent, behaviour SHALL be unchanged (generated ID, `202 Accepted`).
When it is present and valid and no instance with that ID exists, the orchestrator SHALL start the
instance under that ID and return `202 Accepted`.

#### Scenario: Start without instance ID is unchanged
- **GIVEN** the orchestrator serving workflow `order-sync`
- **WHEN** a client calls `POST /workflows/order-sync/instances` with no `instanceId`
- **THEN** the response is `202` with a generated `instanceId` and the workflow name

#### Scenario: Start with a new instance ID
- **GIVEN** no instance `dws-sched:order-sync:20261105T000000Z` exists
- **WHEN** a client calls `POST /workflows/order-sync/instances?instanceId=dws-sched:order-sync:20261105T000000Z`
- **THEN** the response is `202` naming that instance ID
- **AND** a new instance with that ID is running

### Requirement: Orchestrator start is idempotent per instance ID

Owner: `dws-orchestrator`. If an instance with the supplied `instanceId` already exists, in any
runtime status, the orchestrator SHALL NOT start a second one or restart the existing one. It SHALL
return `200 OK` with the same response body shape, naming the existing instance. This SHALL also
hold when a concurrent start creates the instance first.

#### Scenario: Repeated start with the same instance ID
- **GIVEN** instance `dws-sched:order-sync:20261105T000000Z` already exists and has completed
- **WHEN** a client calls the start endpoint again with that `instanceId`
- **THEN** the response is `200` naming the existing instance
- **AND** the existing instance is not restarted and its output is unchanged

### Requirement: Orchestrator validates the supplied instance ID

Owner: `dws-orchestrator`. An `instanceId` that is empty, longer than 256 characters, or contains
characters outside `[A-Za-z0-9:._-]` SHALL be rejected with `400`, and no instance SHALL start.

#### Scenario: Invalid instance ID is rejected
- **GIVEN** the orchestrator serving workflow `order-sync`
- **WHEN** a client calls the start endpoint with `instanceId=bad id/with spaces`
- **THEN** the response is `400` and no instance is started

### Requirement: Scheduler starts instances through one seam selected by the spike

Owner: `dws-schedule-time`. The scheduler SHALL start instances through a single start seam. It SHALL
use direct cross-app `DaprWorkflowClient` scheduling only if the Phase 6a spike proves that Dapr
1.18 supports it with duplicate-ID safety. Otherwise it SHALL use Dapr service invocation of the
orchestrator's start endpoint with `instanceId`. ADR 0013 SHALL record the choice.

#### Scenario: Fallback start through service invocation
- **GIVEN** the spike concluded that cross-app workflow start is not usable
- **WHEN** `dws-schedule-time` starts an instance of `order-sync`
- **THEN** it invokes the orchestrator app-id's `POST /workflows/order-sync/instances` with the deterministic `instanceId` through its Dapr sidecar
- **AND** it treats both `202` and `200` as a successful start

#### Scenario: Target not ready
- **GIVEN** the target orchestrator's sidecar is unreachable or returns a 5xx status
- **WHEN** `dws-schedule-time` attempts a start
- **THEN** the start is reported as failed to the job handler so the fire is retried (see `time-trigger-scheduling`)
