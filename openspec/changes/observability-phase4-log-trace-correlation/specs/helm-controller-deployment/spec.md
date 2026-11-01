# Spec Delta

## ADDED Requirements

### Requirement: Controller container carries the log format when observability logs are on

The controller container SHALL have the environment variable `DWS_LOG_FORMAT` set to `observability.logs.format` when `observability.enabled` and `observability.logs.enabled` are both true, and SHALL NOT have it otherwise. The controller uses the same value for its own log output and for the orchestrator Deployments it compiles. Owning component: `charts/dws`.

#### Scenario: Variable present when logs are on

- **WHEN** the chart is rendered with `observability.enabled=true`
- **THEN** the controller container has `DWS_LOG_FORMAT` = `json`

#### Scenario: Variable absent by default

- **WHEN** the chart is rendered with default values
- **THEN** the controller container has no `DWS_LOG_FORMAT`
