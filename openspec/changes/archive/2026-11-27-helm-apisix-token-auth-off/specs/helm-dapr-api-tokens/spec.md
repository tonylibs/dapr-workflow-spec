## ADDED Requirements

### Requirement: Bundled gateway token reference never blocks APISIX startup

With `apisix.enabled=true` and default values, the bundled APISIX's `DWS_ADMIN_DAPR_API_TOKEN`
SHALL be a `secretKeyRef` marked `optional: true`, for every `auth.enabled`, `admin.enabled` and
`apiGateway.enabled` combination. No workload MUST carry a required reference to a token Secret
that is neither rendered nor operator-supplied. Other APISIX env entries MUST render unchanged.
Owning component: `charts/dws`.

#### Scenario: Auth off with bundled APISIX
- **GIVEN** `auth.enabled=false` and `apisix.enabled=true`, all other values at chart defaults
- **WHEN** `helm template charts/dws` is run
- **THEN** rendering succeeds
- **AND** no Secret whose name ends in `-dapr-api-token` or `-app-api-token` is rendered
- **AND** the APISIX Deployment's `DWS_ADMIN_DAPR_API_TOKEN` entry, if present, is a `secretKeyRef`
  with `optional: true`
- **AND** no container in the render has a non-optional `secretKeyRef` to a Secret whose name ends
  in `-dapr-api-token` or `-app-api-token`

#### Scenario: Admin disabled with bundled APISIX and no shared Gateway
- **GIVEN** `auth.enabled=true`, `admin.enabled=false`, `apisix.enabled=true` and
  `apiGateway.enabled=false`
- **WHEN** `helm template charts/dws` is run
- **THEN** rendering succeeds and no `<release>-admin-dapr-api-token` Secret is rendered
- **AND** the APISIX Deployment's reference to `<release>-admin-dapr-api-token` has
  `optional: true`

#### Scenario: Unrelated APISIX env settings remain intact
- **GIVEN** `auth.enabled=false`, `apisix.enabled=true` and an APISIX setting that renders its own
  env entry (for example `apisix.timezone`)
- **WHEN** `helm template charts/dws` is run
- **THEN** that env entry is rendered on the APISIX container unchanged

#### Scenario: APISIX starts without the token Secret
- **GIVEN** a fresh install with `auth.enabled=false` and `apisix.enabled=true` on a disposable
  cluster
- **WHEN** the release settles
- **THEN** the APISIX pod reaches Ready and is not in `CreateContainerConfigError`
- **AND** the APISIX container has no `DWS_ADMIN_DAPR_API_TOKEN` variable set

## MODIFIED Requirements

### Requirement: Tokens are provisioned only when auth is enabled

When `auth.enabled=false` (default), `charts/dws` SHALL render no token Secret, no
`dapr.io/api-token-secret` or `dapr.io/app-token-secret` annotation, no token header on any route,
and no token environment variable that resolves on any container. The only exception is the
bundled APISIX's optional `DWS_ADMIN_DAPR_API_TOKEN` reference, which resolves to nothing. With
`apisix.enabled=false`, the render MUST be identical to a chart without this capability. Owning
component: `charts/dws`.

#### Scenario: Auth off renders no token objects
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the output contains no Secret whose name ends in `-dapr-api-token` or `-app-api-token`
- **AND** no pod annotation `dapr.io/api-token-secret` or `dapr.io/app-token-secret`
- **AND** no environment variable `DAPR_API_TOKEN` or `APP_API_TOKEN`

#### Scenario: Auth off with bundled APISIX renders no required token dependency
- **WHEN** the chart is rendered with `auth.enabled=false` and `apisix.enabled=true`
- **THEN** the output contains no token Secret, no `dapr.io/api-token-secret` or
  `dapr.io/app-token-secret` annotation, no `dapr-api-token` header filter, and no environment
  variable `DAPR_API_TOKEN` or `APP_API_TOKEN`
- **AND** every `secretKeyRef` to a Secret whose name ends in `-dapr-api-token` has
  `optional: true`

#### Scenario: Auth on renders the token objects
- **WHEN** the chart is rendered with `auth.enabled=true` and a valid issuer and audience
- **THEN** the token Secrets, annotations and environment variables described below are present

### Requirement: Gateway injects the admin Dapr API token on the admin route only

With `apiGateway.enabled=true`, the admin HTTPRoute SHALL set the `dapr-api-token` request header
to the admin Dapr API token, resolved from a Secret-backed environment variable of the gateway
data plane, so the token does not appear as a cleartext value in any rendered manifest. The console
HTTPRoute MUST NOT set that header. Any `dapr-api-token` header supplied by the client MUST be
replaced, not forwarded. Owning component: `charts/dws`.

#### Scenario: Admin route sets the header from a reference
- **WHEN** the chart is rendered with `apiGateway.enabled=true`
- **THEN** the admin HTTPRoute contains a header-set filter for `dapr-api-token`
- **AND** its value is an environment-variable reference, not a literal token

#### Scenario: Console route is untouched
- **WHEN** the chart is rendered with `apiGateway.enabled=true`
- **THEN** the console HTTPRoute contains no `dapr-api-token` header filter

#### Scenario: Bundled gateway receives the token from the Secret
- **WHEN** the chart is rendered with `apiGateway.enabled=true` and `apisix.enabled=true`
- **THEN** the bundled APISIX workload has an environment variable sourced by `secretKeyRef` from
  the admin Dapr API token Secret, key `token`, with `optional: true` and no literal `value`
- **AND** on a live install where that Secret exists, the variable resolves to the Secret's token
  and admin requests through the Gateway succeed

#### Scenario: Client-supplied token is replaced
- **WHEN** a client calls `/dws-admin/instances` through the Gateway with a forged `dapr-api-token`
  header and a valid bearer token
- **THEN** the upstream request carries the Secret's token, not the forged value

### Requirement: Bundled gateway wiring cannot silently drift from the Secret

When `apiGateway.enabled=true` and `apisix.enabled=true`, rendering MUST fail with an actionable
message unless the bundled APISIX both sources the admin Dapr API token variable from the admin
Dapr API token Secret and lists it among the environment variables its nginx workers may read.
These checks MUST apply whether or not the reference is marked `optional`. An optional reference
does not relax the Secret-name or key check. Owning component: `charts/dws`.

#### Scenario: Overriding the APISIX environment without the token fails render
- **WHEN** `apiGateway.enabled=true`, `apisix.enabled=true` and `apisix.extraEnvVars` is replaced
  with a list lacking the admin Dapr API token variable
- **THEN** `helm template` fails with a message naming the missing variable

#### Scenario: Variable not exposed to nginx workers fails render
- **WHEN** the variable is sourced from the Secret but absent from `apisix.nginx.envs`
- **THEN** `helm template` fails with a message naming `apisix.nginx.envs`

#### Scenario: Supplying an existing Secret without re-pointing APISIX fails render
- **WHEN** the operator supplies an existing admin Dapr API token Secret and leaves
  `apisix.extraEnvVars` at its default (which is marked `optional: true`)
- **THEN** `helm template` fails with a message naming both Secrets and the value to override

#### Scenario: Re-pointed reference without the optional flag still renders
- **WHEN** the operator supplies an existing admin Dapr API token Secret and overrides
  `apisix.extraEnvVars` with a `secretKeyRef` to that Secret, key `token`, without `optional`
- **THEN** rendering succeeds and APISIX reads the supplied Secret

#### Scenario: External gateway mode relies on operator wiring
- **WHEN** `apiGateway.enabled=true` and `apisix.enabled=false`
- **THEN** no environment variable is rendered into any workload this chart does not own
- **AND** the chart README documents the variable and Secret the operator must expose
