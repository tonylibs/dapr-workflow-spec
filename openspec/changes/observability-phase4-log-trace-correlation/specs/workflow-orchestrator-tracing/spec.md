# Spec Delta

## ADDED Requirements

### Requirement: Instrumented orchestrator carries the install's log format

When observability is on for a workflow deploy and `dws-controller` was started with `DWS_LOG_FORMAT` set to `json`, the orchestrator container SHALL carry `DWS_LOG_FORMAT=json`. When observability is off, or the controller's `DWS_LOG_FORMAT` is absent or not `json`, the orchestrator Deployment SHALL equal the Deployment produced before this change. A `DWS_LOG_FORMAT` already defined on the orchestrator container SHALL NOT be overwritten. Owning component: `dws-controller`.

#### Scenario: Instrumented orchestrator receives the format

- **WHEN** observability is on, the controller has `DWS_LOG_FORMAT=json`, and a workflow is deployed
- **THEN** the orchestrator container has `DWS_LOG_FORMAT=json`
- **AND** the other stamped observability entries are unchanged

#### Scenario: Observability off leaves the orchestrator unchanged

- **WHEN** observability is off and the controller has `DWS_LOG_FORMAT=json`
- **THEN** the orchestrator Deployment equals the Deployment produced before this change

#### Scenario: Controller without a log format leaves the orchestrator unchanged

- **WHEN** observability is on and the controller's `DWS_LOG_FORMAT` is absent
- **THEN** the orchestrator container has no `DWS_LOG_FORMAT`

#### Scenario: Existing value is kept

- **WHEN** the orchestrator spec already defines `DWS_LOG_FORMAT`
- **THEN** the rendered container keeps that value
