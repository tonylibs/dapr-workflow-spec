## ADDED Requirements

### Requirement: Go step images run as Dapr Workflow activity workers
The `dws-call-http` and `dws-run` (all three run images) components SHALL run as Dapr Workflow
activity workers rather than plain HTTP servers. Each deployed step SHALL register a single
canonical activity named `Run` against its Dapr app-id, and the activity handler MUST execute the
same step logic (`runner.Run`) the HTTP `POST /run` handler executed.

#### Scenario: step registers the canonical activity
- **WHEN** a `dws-call-http` or `dws-run` step service starts
- **THEN** it registers a Dapr Workflow activity named `Run` for its app-id
- **AND** it is reachable as a multi-app activity target by the orchestrator

#### Scenario: activity executes the existing runner
- **WHEN** the `Run` activity is invoked with the current workflow data
- **THEN** the step produces the same result the HTTP `POST /run` handler produced for that input

### Requirement: Activity input and empty-result behavior are preserved
The `Run` activity SHALL accept the current workflow data as a JSON object input; an absent or
empty input MUST be treated as `{}`. When the underlying runner produces no output (nil/empty),
the activity MUST return a result that leaves the workflow data document unchanged, matching the
prior HTTP behavior.

#### Scenario: empty input is empty workflow data
- **WHEN** the `Run` activity is invoked with no input
- **THEN** the step executes with `{}` as the workflow data

#### Scenario: no output leaves data unchanged
- **WHEN** the runner produces no output value
- **THEN** the activity returns a value that leaves the workflow data document unchanged

### Requirement: OUTPUT shaping is preserved on the activity path
The `Run` activity SHALL apply the same `OUTPUT=replace|merge` response shaping to the runner's
raw value that the HTTP handler applied, so the data document the orchestrator receives is
identical to the pre-migration behavior for the same input and configuration.

#### Scenario: OUTPUT=merge merges into workflow data
- **WHEN** the step is configured `OUTPUT=merge` and the runner yields an object
- **THEN** the activity result merges that object into the incoming workflow data

#### Scenario: OUTPUT=replace replaces workflow data
- **WHEN** the step is configured `OUTPUT=replace` and the runner yields a value
- **THEN** the activity result replaces the workflow data with the shaped value

### Requirement: Upstream and configuration failures are distinguished for the workflow runtime
The `Run` activity SHALL distinguish retryable upstream/transport-equivalent failures (a non-2xx
upstream response for `dws-call-http`; a non-zero subprocess exit or spawn failure for `dws-run`,
where `RETURN` does not treat the code as data) from non-retryable configuration/shaping failures.
The failure it surfaces MUST carry a stable marker so `dws-orchestrator` classifies it into the
same error kind the HTTP `502`/`500` split produced.

#### Scenario: upstream fault is marked retryable
- **WHEN** `dws-call-http`'s upstream returns a non-2xx response
- **THEN** the `Run` activity fails with a marker indicating an upstream/transport-equivalent fault

#### Scenario: spawn failure is marked retryable
- **WHEN** a `dws-run` step cannot spawn its subprocess
- **THEN** the `Run` activity fails with a marker indicating an upstream/transport-equivalent fault

#### Scenario: configuration fault is marked non-retryable
- **WHEN** the step fails due to a configuration or output-shaping error
- **THEN** the `Run` activity fails with a marker distinct from the upstream/transport marker
