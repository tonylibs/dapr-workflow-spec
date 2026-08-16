## MODIFIED Requirements

### Requirement: Shared step-service HTTP contract
The `dws-run` component (all three images) SHALL run as a Dapr Workflow **activity worker**:
each deployed step registers a single canonical activity named `Run` against its Dapr app-id,
and the activity handler executes the same step logic the prior HTTP `POST /run` handler
executed. The activity SHALL accept the current workflow data as a JSON object input; an absent
or empty input MUST be treated as `{}` rather than an error. An input that cannot be decoded as a
JSON object MUST fail the activity as a configuration error (distinct from a retryable upstream
fault) and MUST NOT spawn a subprocess. The image SHALL still expose a health signal
(`GET /healthz`) sufficient for Knative readiness.

#### Scenario: Health endpoint responds
- **WHEN** a client sends `GET /healthz`
- **THEN** the service responds `200` with a JSON body containing `status` and `task`

#### Scenario: Step registers the canonical activity
- **WHEN** a `dws-run` step service starts
- **THEN** it registers a Dapr Workflow activity named `Run` for its app-id
- **AND** it is reachable as a multi-app activity target

#### Scenario: Empty body is empty workflow data
- **WHEN** the `Run` activity is invoked with no input
- **THEN** the step executes with `{}` as the workflow data

#### Scenario: Malformed body is rejected
- **WHEN** the `Run` activity is invoked with an input that is not a valid JSON object
- **THEN** the activity fails with a non-retryable configuration marker
- **AND** no subprocess is spawned

### Requirement: Spawn failures are retryable
A failure to spawn the subprocess at all — a missing interpreter, a permission error, or any
other transport-equivalent fault — SHALL fail the `Run` activity with a stable
upstream/transport-equivalent marker, matching `dws-call-http`'s handling of transport errors,
so `dws-orchestrator` classifies it as a communication error and the workflow retries rather than
failing the instance.

#### Scenario: Spawn failure returns 502
- **WHEN** the configured interpreter cannot be executed
- **THEN** the `Run` activity fails with the upstream/transport-equivalent marker
- **AND** the orchestrator classifies the failure as a communication error, equivalent to the prior `502`
