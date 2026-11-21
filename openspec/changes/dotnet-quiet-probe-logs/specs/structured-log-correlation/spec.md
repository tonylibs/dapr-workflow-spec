## ADDED Requirements

### Requirement: .NET framework request logging defaults to Warning

In text and JSON mode, `dws-flow` and `dws-schedule-time` SHALL default the minimum level of the `Microsoft.AspNetCore` category, and every category under it, to `Warning`. A health probe or successful request SHALL write no record from a `Microsoft.AspNetCore.*` category. Records at `Warning` or above from those categories SHALL still be written. The package's own categories SHALL keep the `Information` minimum.

Owning components: `dws-flow` and `dws-schedule-time`.

#### Scenario: Health probe writes no log line

- **GIVEN** `dws-schedule-time` (or `dws-flow`) is running with default logging configuration, in text or JSON mode
- **WHEN** a client sends `GET /healthz` and receives `200`
- **THEN** no log record at `Information` or below is written for that request
- **AND** in particular nothing is written from `Microsoft.AspNetCore.Hosting.Diagnostics`, `Microsoft.AspNetCore.Routing.EndpointMiddleware` or `Microsoft.AspNetCore.Http.Result.*`

#### Scenario: Framework warnings still get through

- **GIVEN** default logging configuration
- **WHEN** a `Microsoft.AspNetCore.*` category logs at `Warning` or `Error`
- **THEN** the record is written in the configured format

#### Scenario: Package's own Information records are unaffected

- **GIVEN** default logging configuration
- **WHEN** `Dws.ScheduleTime.ClusterWatcher` logs `Initial cluster list: ...` at `Information`
- **THEN** the record is written

### Requirement: Operator configuration overrides the framework default

In `dws-flow` and `dws-schedule-time`, operator configuration of the `Microsoft.AspNetCore` level through standard .NET logging configuration SHALL take precedence over the `Warning` default. This includes the `Logging__LogLevel__Microsoft.AspNetCore` environment variable and provider-specific keys such as `Logging:Console:LogLevel:Microsoft.AspNetCore`.

Owning components: `dws-flow` and `dws-schedule-time`.

#### Scenario: Operator can turn request logging back on

- **GIVEN** the container has `Logging__LogLevel__Microsoft.AspNetCore=Information`
- **WHEN** a client sends `GET /healthz`
- **THEN** the framework's request records are written at `Information`, as before this change
