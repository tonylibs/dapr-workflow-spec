## ADDED Requirements

### Requirement: Controller validates the time trigger at submit

Owner: `dws-controller`. When a submitted definition carries `schedule`, the controller SHALL reject
it with a compilation error naming the offending `schedule` field, before deploying anything,
unless exactly one of `every`, `cron` or `after` is set.

#### Scenario: Valid cron schedule is accepted
- **GIVEN** a definition with `schedule: { cron: "0 0 * * *" }` and otherwise valid content
- **WHEN** the definition is submitted to the controller
- **THEN** compilation succeeds and the workflow is deployed

#### Scenario: More than one time trigger is rejected
- **GIVEN** a definition with `schedule: { every: "PT1H", cron: "0 0 * * *" }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that exactly one of `every`, `cron` or `after` is allowed
- **AND** no resources are created or changed

#### Scenario: Empty schedule block is rejected
- **GIVEN** a definition with `schedule: {}`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that one of `every`, `cron`, `after` or `on` is required

### Requirement: Schedule durations are literal and at least one second

Owner: `dws-controller`. A `schedule.every` or `schedule.after` duration SHALL be a literal
ISO-8601 string or a duration object (days to milliseconds) of at least 1 second. A runtime
expression SHALL be rejected, because no instance exists to evaluate it against.

#### Scenario: Duration object is accepted
- **GIVEN** a definition with `schedule: { every: { minutes: 5 } }`
- **WHEN** the definition is submitted
- **THEN** compilation succeeds

#### Scenario: Interval below the minimum is rejected
- **GIVEN** a definition with `schedule: { every: "PT0.5S" }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error naming `schedule.every` and the 1 second minimum

#### Scenario: Runtime-expression duration is rejected
- **GIVEN** a definition with `schedule: { after: "${ .delay }" }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that runtime-expression durations are not supported for `schedule.after`

### Requirement: Schedule cron is a standard 5-field expression

Owner: `dws-controller`. `schedule.cron` SHALL be a valid standard 5-field expression (minute,
hour, day-of-month, month, day-of-week). Any other field count SHALL be rejected.

#### Scenario: Malformed cron is rejected
- **GIVEN** a definition with `schedule: { cron: "0 0 * *" }` (four fields)
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error naming `schedule.cron`

#### Scenario: Six-field cron is rejected
- **GIVEN** a definition with `schedule: { cron: "0 0 0 * * *" }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that a 5-field cron expression is required

### Requirement: Controller rejects event-trigger fields until Phase 6b

Owner: `dws-controller`. `schedule.on` SHALL be rejected as not yet supported. `schedule.read` SHALL
be rejected whenever `on` is absent.

#### Scenario: Event trigger is rejected until Phase 6b
- **GIVEN** a definition with `schedule: { on: { one: { with: { type: "com.example.order" } } } }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that `schedule.on` is not yet supported

#### Scenario: read without on is rejected
- **GIVEN** a definition with `schedule: { every: "PT1H", read: "envelope" }`
- **WHEN** the definition is submitted
- **THEN** the controller returns a compilation error stating that `schedule.read` is valid only with `schedule.on`

### Requirement: Runtime v2 strategy rejects schedule until supported

Owner: `dws-controller`. When the controller runs the v2 compiler strategy (ADR 0002), a definition
carrying `schedule` SHALL be rejected with an error stating that scheduling is not yet supported on
runtime v2. The controller SHALL NOT deploy a v2 stack whose trigger silently never fires.

#### Scenario: Scheduled definition under the v2 strategy
- **GIVEN** the controller is configured with the v2 compiler strategy
- **WHEN** a definition with `schedule: { every: "PT1H" }` is submitted
- **THEN** the controller returns a compilation error stating that `schedule` is not yet supported on runtime v2

### Requirement: Controller stamps a normalized trigger config for scheduled versions

Owner: `dws-controller`. For each deployed version with a valid time trigger, the controller SHALL
apply exactly one ConfigMap labeled with the standard `dws.io/*` labels plus `dws.io/trigger: time`.
Its `trigger.json` key SHALL hold:
- `workflow`;
- `version`;
- `appId` (the orchestrator app-id);
- `kind` (`every`, `cron` or `after`);
- `schedule` (a canonical ISO-8601 duration, or the 5-field cron).

#### Scenario: Scheduled version gets a trigger ConfigMap
- **GIVEN** a definition `order-sync` with `schedule: { every: { hours: 1, minutes: 30 } }`
- **WHEN** it is submitted and deployed as version `v1abc`
- **THEN** a ConfigMap labeled `dws.io/workflow=order-sync`, `dws.io/version=v1abc` and `dws.io/trigger=time` exists
- **AND** its `trigger.json` is `{"workflow":"order-sync","version":"v1abc","appId":<orchestrator app-id>,"kind":"every","schedule":"PT1H30M"}`

### Requirement: Unscheduled versions are unchanged

Owner: `dws-controller`. A version without `schedule` SHALL get no trigger ConfigMap. The trigger
ConfigMap SHALL NOT change how a version ID is computed.

#### Scenario: Unscheduled version gets no trigger ConfigMap
- **GIVEN** a definition with no `schedule` block
- **WHEN** it is submitted and deployed
- **THEN** no ConfigMap labeled `dws.io/trigger` exists for that workflow
- **AND** the version ID equals the one the controller computed before this change for the same text

### Requirement: Trigger ConfigMap shares the version's lifecycle

Owner: `dws-controller`. The trigger ConfigMap SHALL be deleted together with the rest of its
version's stack.

#### Scenario: Trigger ConfigMap is removed with the stack
- **GIVEN** a deployed scheduled workflow
- **WHEN** the workflow is deleted through the controller API
- **THEN** its trigger ConfigMap is deleted along with its other resources

#### Scenario: Removing the schedule in a new version
- **GIVEN** version A of a workflow has `schedule.cron` and version B of the same workflow has no `schedule`
- **WHEN** version B is deployed and version A is draining
- **THEN** version B has no trigger ConfigMap
- **AND** version A's trigger ConfigMap remains only as long as version A's stack exists
