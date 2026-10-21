# Spec Delta

## ADDED Requirements

### Requirement: Admin container carries the log format when observability logs are on

The admin container SHALL have the environment variable `DWS_LOG_FORMAT` set to `observability.logs.format` when `observability.enabled` and `observability.logs.enabled` are both true, and SHALL NOT have it otherwise. Owning component: `charts/dws`.

#### Scenario: Variable present when logs are on

- **WHEN** the chart is rendered with `observability.enabled=true`
- **THEN** the admin container has `DWS_LOG_FORMAT` = `json`

#### Scenario: Variable absent by default

- **WHEN** the chart is rendered with default values
- **THEN** the admin container has no `DWS_LOG_FORMAT`
