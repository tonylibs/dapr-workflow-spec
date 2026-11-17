# Spec Delta

## Purpose

Defines how the `charts/dws` Helm chart provisions Dapr's two machine-auth tokens for the
control-plane sidecars — the Dapr API token and the app API token — and how the shared Gateway
presents the Dapr API token, so that only holders of the shared secret can call a sidecar and
only a pod's own sidecar can call its app.

## ADDED Requirements

### Requirement: Tokens are provisioned only when auth is enabled

When `auth.enabled=false` (default), `charts/dws` SHALL render no token Secret, no
`dapr.io/api-token-secret` or `dapr.io/app-token-secret` annotation, no token environment variable
on any container, and no token header on any route. Releases with auth off MUST render identically
to a chart without this capability. Owning component: `charts/dws`.

#### Scenario: Auth off renders no token objects
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the output contains no Secret whose name ends in `-dapr-api-token` or `-app-api-token`
- **AND** no pod annotation `dapr.io/api-token-secret` or `dapr.io/app-token-secret`
- **AND** no environment variable `DAPR_API_TOKEN` or `APP_API_TOKEN`

#### Scenario: Auth on renders the token objects
- **WHEN** the chart is rendered with `auth.enabled=true` and a valid issuer and audience
- **THEN** the token Secrets, annotations and environment variables described below are present

### Requirement: Admin and controller sidecars each require a Dapr API token

With `auth.enabled=true`, the `dws-admin` and `dws-controller` pods SHALL each carry
`dapr.io/api-token-secret` naming a Secret that holds that component's own token under key
`token`. The two components MUST NOT share a token. The Secrets SHALL be created by the chart
unless the operator supplies an existing Secret name for that component. Owning component:
`charts/dws`.

#### Scenario: Each component names its own Secret
- **WHEN** the chart is rendered with `auth.enabled=true`
- **THEN** the admin pod's `dapr.io/api-token-secret` and the controller pod's
  `dapr.io/api-token-secret` name two different Secrets

#### Scenario: Operator-supplied Secret is used and not recreated
- **WHEN** the chart is rendered with `auth.enabled=true` and an existing Secret name set for the
  admin Dapr API token
- **THEN** the admin pod's `dapr.io/api-token-secret` names that Secret
- **AND** the chart renders no chart-generated Secret for the admin Dapr API token

### Requirement: Admin app port is protected by an app API token

With `auth.enabled=true`, the `dws-admin` pod SHALL carry `dapr.io/app-token-secret` naming a
Secret holding the admin app API token under key `token`, and the `admin` container SHALL receive
that value as `APP_API_TOKEN` from the same Secret. The `admin` container SHALL also receive the
admin Dapr API token as `DAPR_API_TOKEN`, from the Secret, never as a literal value. Owning
component: `charts/dws`.

#### Scenario: Admin container receives both tokens from Secrets
- **WHEN** the chart is rendered with `auth.enabled=true`
- **THEN** the `admin` container's `APP_API_TOKEN` and `DAPR_API_TOKEN` are each a
  `secretKeyRef` and carry no `value`
- **AND** the `APP_API_TOKEN` reference resolves to the Secret named by the pod's
  `dapr.io/app-token-secret`

### Requirement: Controller container receives its Dapr API token

With `auth.enabled=true`, the `controller` container SHALL receive the controller Dapr API token as
`DAPR_API_TOKEN` from its Secret, so the controller's own calls to its sidecar (lifecycle-event
publishing) keep working once the sidecar requires the token. The controller pod SHALL NOT carry
`dapr.io/app-token-secret` in this change. Owning component: `charts/dws`.

#### Scenario: Controller event publishing keeps its token
- **WHEN** the chart is rendered with `auth.enabled=true`
- **THEN** the `controller` container has `DAPR_API_TOKEN` as a `secretKeyRef` to the controller
  Dapr API token Secret

#### Scenario: Controller app token is deferred
- **WHEN** the chart is rendered with `auth.enabled=true`
- **THEN** the controller pod has no `dapr.io/app-token-secret` annotation and no
  `APP_API_TOKEN` variable

### Requirement: Generated tokens are strong, stable and never printed

Chart-generated tokens SHALL be random strings of at least 32 characters. A generated token MUST
be preserved across `helm upgrade`, reusing the value already in the cluster. The chart MUST NOT
print any token value in `NOTES.txt`, in a Helm test, or in any other chart output intended for
display. Owning component: `charts/dws`.

#### Scenario: Token survives upgrade
- **WHEN** a release with generated tokens is upgraded
- **THEN** the rendered token Secrets carry the values already present in the cluster

#### Scenario: NOTES does not disclose tokens
- **WHEN** `NOTES.txt` is rendered with `auth.enabled=true`
- **THEN** it contains no token value
- **AND** it names the Secrets that hold the tokens and the command to restart the pods

#### Scenario: Generated token has sufficient length
- **WHEN** the chart is rendered with `auth.enabled=true` and no existing Secret supplied
- **THEN** each generated token is at least 32 characters

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
  the admin Dapr API token Secret

#### Scenario: Client-supplied token is replaced
- **WHEN** a client calls `/dws-admin/instances` through the Gateway with a forged `dapr-api-token`
  header and a valid bearer token
- **THEN** the upstream request carries the Secret's token, not the forged value

### Requirement: Bundled gateway wiring cannot silently drift from the Secret

When `apiGateway.enabled=true` and `apisix.enabled=true`, rendering MUST fail with an actionable
message unless the bundled APISIX both sources the admin Dapr API token variable from the admin
Dapr API token Secret and lists it among the environment variables its nginx workers may read.
Owning component: `charts/dws`.

#### Scenario: Overriding the APISIX environment without the token fails render
- **WHEN** `apiGateway.enabled=true`, `apisix.enabled=true` and `apisix.extraEnvVars` is replaced
  with a list lacking the admin Dapr API token variable
- **THEN** `helm template` fails with a message naming the missing variable

#### Scenario: Variable not exposed to nginx workers fails render
- **WHEN** the variable is sourced from the Secret but absent from `apisix.nginx.envs`
- **THEN** `helm template` fails with a message naming `apisix.nginx.envs`

#### Scenario: Supplying an existing Secret without re-pointing APISIX fails render
- **WHEN** the operator supplies an existing admin Dapr API token Secret and leaves
  `apisix.extraEnvVars` at its default
- **THEN** `helm template` fails with a message naming both Secrets and the value to override

#### Scenario: External gateway mode relies on operator wiring
- **WHEN** `apiGateway.enabled=true` and `apisix.enabled=false`
- **THEN** no environment variable is rendered into any workload this chart does not own
- **AND** the chart README documents the variable and Secret the operator must expose

### Requirement: Bearer check and token checks both apply

With `auth.enabled=true`, a request to a control-plane sidecar's HTTP API MUST satisfy both the
`dapr-api-token` check and the existing bearer-middleware check. Satisfying one MUST NOT bypass the
other. Owning component: `charts/dws`.

#### Scenario: Valid token, no bearer
- **WHEN** a request to the admin sidecar's invoke path carries the correct `dapr-api-token` and no
  `Authorization` header
- **THEN** the sidecar responds 401 (bearer middleware) and the app does not observe the request

#### Scenario: Valid bearer, no token
- **WHEN** a request carries a valid bearer JWT and no `dapr-api-token`
- **THEN** the sidecar responds 401 and the app does not observe the request

#### Scenario: Valid bearer, wrong token
- **WHEN** a request carries a valid bearer JWT and a wrong `dapr-api-token`
- **THEN** the sidecar responds 401

#### Scenario: Both present and valid
- **WHEN** a request carries a valid bearer JWT and the correct `dapr-api-token`
- **THEN** the request reaches the app and succeeds

#### Scenario: Negative bearer matrix unchanged on the admin sidecar
- **WHEN** a request to the admin sidecar's invoke path carries the correct `dapr-api-token` and a
  missing, malformed, tampered-signature, wrong-audience or wrong-issuer bearer token
- **THEN** the sidecar responds 401 and the app does not observe the request

#### Scenario: Negative bearer matrix unchanged on the controller
- **WHEN** a caller on another pod invokes `dws-controller` through Dapr service invocation with a
  missing, malformed, tampered-signature, wrong-audience or wrong-issuer bearer token
- **THEN** the controller sidecar responds 401 and the controller container does not observe the
  request, exactly as before this change

#### Scenario: Controller sidecar public API requires the token
- **WHEN** a request reaches the controller sidecar's own HTTP API without a `dapr-api-token`
- **THEN** the sidecar responds 401
