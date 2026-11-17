# helm-controller-auth-middleware

## Purpose

The `charts/dws` Helm chart's rendering of Dapr bearer-middleware authentication in front of the
`dws-controller` sidecar — the `Component`, `Configuration`, pod annotations, Service targeting,
and values contract that together enforce JWT verification on inbound cluster traffic while
leaving unauthenticated releases (default `auth.enabled=false`) topologically unchanged. Introduced
in `dws-console-auth-phase-2`.

## Requirements

### Requirement: Bearer middleware Component renders when auth is enabled

When `.Values.auth.enabled` is `true`, `charts/dws` SHALL render a single Dapr `Component` of
`type: middleware.http.bearer` (apiVersion `dapr.io/v1alpha1`), named
`{{ include "dws.controller.fullname" . }}-auth`, in the release namespace. The Component SHALL
be scoped to the controller app-id only (`scopes: [<controller fullname>]`). Its `spec.metadata`
SHALL carry an `issuer` (from `.Values.auth.issuer` or the derived Dex issuer), an `audience`
(from `.Values.auth.audience` or the derived Dex client ID), and, when configured, a `jwksURL`
(from `.Values.auth.jwksURL` or the derived Dex JWKS URL). When `.Values.auth.enabled` is
`false` (default), no bearer Component SHALL be rendered.

Owning component: `charts/dws` (`templates/controller/auth-component.yaml`).

#### Scenario: Auth disabled (default) renders no Component
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no Component of type `middleware.http.bearer` appears in the rendered output

#### Scenario: Auth enabled with explicit issuer/audience/jwksURL renders one scoped Component
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller --set auth.jwksURL=https://idp.example.com/keys` is run
- **THEN** the output contains exactly one Component of type `middleware.http.bearer`
- **AND** its `metadata.name` is `<controller fullname>-auth`
- **AND** its `spec.metadata` contains `issuer=https://idp.example.com`, `audience=dws-controller`,
  and `jwksURL=https://idp.example.com/keys`
- **AND** its `scopes` list contains exactly the controller fullname and nothing else

#### Scenario: Auth enabled with Dex mode derives issuer/audience from in-chart Dex
- **WHEN** `helm template charts/dws --set dex.enabled=true --set auth.enabled=true --set auth.dex.enabled=true` is run
- **THEN** the rendered bearer Component's `issuer` equals the in-chart Dex issuer URL
- **AND** its `audience` equals the Dex `dws-controller` static client's client ID (or the
  console client ID when the chart values pin one shared audience)
- **AND** the caller is not required to supply `auth.issuer` / `auth.audience` explicitly

#### Scenario: jwksURL is optional
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller` is run without `auth.jwksURL`
- **THEN** the rendered Component omits the `jwksURL` metadata key (Dapr's bearer middleware
  discovers the JWKS URL from the issuer's OIDC discovery document)

#### Scenario: Auth enabled with missing issuer/audience fails render
- **WHEN** `helm template charts/dws --set auth.enabled=true` is run with no explicit
  `auth.issuer`/`auth.audience` and `auth.dex.enabled` is false
- **THEN** rendering fails with an explicit error message naming the missing required value

### Requirement: Configuration wires bearer middleware into the controller sidecar's inbound pipeline

When the controller is enabled and either `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, `charts/dws` SHALL render exactly one Dapr `Configuration` (apiVersion `dapr.io/v1alpha1`) named `{{ include "dws.controller.fullname" . }}-config` in the release namespace. When auth is enabled, its `spec.appHttpPipeline.handlers` list SHALL contain exactly one handler of `type: middleware.http.bearer` referencing the auth Component by name. The pipeline SHALL apply to inbound HTTP calls addressed to the controller's app-id and SHALL NOT apply to other app-ids or the sidecar's own management/health endpoints. When auth is disabled, the Configuration SHALL NOT contain `appHttpPipeline`, even if it renders for observability. When observability is enabled, the same Configuration SHALL contain the `spec.tracing` contract defined by `helm-observability`; no second Configuration SHALL be created.

Owning component: `charts/dws` (`templates/controller/configuration.yaml`).

#### Scenario: Configuration renders when auth is enabled
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller` is run
- **THEN** the output contains exactly one `Configuration` named `<controller fullname>-config`
- **AND** its `spec.appHttpPipeline.handlers[0].name` matches the auth Component's name
- **AND** its `spec.appHttpPipeline.handlers[0].type` is `middleware.http.bearer`

#### Scenario: Configuration is absent when auth is disabled
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no `Configuration` referencing bearer middleware is rendered

#### Scenario: Auth-only Configuration preserves appHttpPipeline

- **WHEN** the chart renders with auth enabled, observability disabled, and valid issuer/audience values
- **THEN** exactly one `Configuration` named `<controller fullname>-config` is rendered
- **AND** its `spec.appHttpPipeline.handlers[0]` references the controller auth Component with type `middleware.http.bearer`
- **AND** it has no `spec.tracing` key

#### Scenario: Observability-only Configuration has tracing without auth pipeline

- **WHEN** the chart renders with auth disabled and observability enabled
- **THEN** exactly one `Configuration` named `<controller fullname>-config` is rendered
- **AND** it contains the required tracing block
- **AND** it has no `spec.appHttpPipeline` key and no bearer handler

#### Scenario: Auth and observability share one Configuration

- **WHEN** both auth and observability are enabled
- **THEN** exactly one controller `Configuration` is rendered
- **AND** it contains both `spec.appHttpPipeline` and `spec.tracing`

#### Scenario: Configuration is absent when both features are disabled

- **WHEN** the chart renders with default values
- **THEN** no `Configuration` named `<controller fullname>-config` is rendered

### Requirement: Controller pod carries dapr.io/app-port and dapr.io/config annotations

The controller Deployment's pod template SHALL always carry `dapr.io/app-port: "8080"` (matching the container's `http` port). When `.Values.auth.enabled` or `.Values.observability.enabled` is `true`, the pod template SHALL carry exactly one `dapr.io/config: <controller fullname>-config`. Existing `dapr.io/enabled: "true"` and `dapr.io/app-id` annotations SHALL be preserved unchanged.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: app-port annotation is always present

- **WHEN** `helm template charts/dws` is run with any combination of Dapr, auth, and observability toggles
- **THEN** the controller pod template carries `dapr.io/app-port: "8080"`

#### Scenario: dapr.io/config annotation follows auth toggle
- **WHEN** `helm template charts/dws --set auth.enabled=true` is run
- **THEN** the controller pod template carries `dapr.io/config: <controller fullname>-config`

#### Scenario: dapr.io/config is absent when auth is disabled
- **WHEN** `helm template charts/dws` is run with default values (`auth.enabled=false`)
- **THEN** the controller pod template does NOT carry a `dapr.io/config` annotation

#### Scenario: dapr.io/config follows either feature toggle

- **WHEN** auth or observability is enabled
- **THEN** the controller pod template carries exactly one `dapr.io/config: <controller fullname>-config`

#### Scenario: dapr.io/config is absent when both features are disabled

- **WHEN** the chart is rendered with default values
- **THEN** the controller pod template does NOT carry a `dapr.io/config` annotation

### Requirement: Controller Service fronts the Dapr sidecar port when auth is enabled

When `.Values.auth.enabled` is `true`, the controller Kubernetes Service SHALL target the Dapr
sidecar's HTTP port (`daprd`'s `3500`, discovered by the `dapr.io/daprd-http-port` container port
Dapr injects) rather than the controller container's `http` port. This closes the direct
application-port bypass — cluster callers reaching the Service traverse the sidecar (and its
inbound pipeline, which carries the bearer middleware) before the request reaches the app port.
When `.Values.auth.enabled` is `false`, the Service SHALL preserve the pre-existing behavior of
targeting the controller container's `http` port so that unauthenticated releases render an
unchanged topology.

Owning component: `charts/dws` (`templates/controller/service.yaml`).

#### Scenario: Service targets the sidecar port when auth is enabled
- **WHEN** `helm template charts/dws --set auth.enabled=true --set auth.issuer=https://idp.example.com --set auth.audience=dws-controller` is run
- **THEN** the controller Service's `spec.ports[0].targetPort` is `3500` (or the equivalent
  named `dapr-http` port on the pod), NOT the controller container's `http` port

#### Scenario: Service preserves pre-existing target when auth is disabled
- **WHEN** `helm template charts/dws` is run with default values
- **THEN** the controller Service's `spec.ports[0].targetPort` is the controller container's
  `http` port, matching pre-Phase-2 behavior

### Requirement: Health probes remain reachable

Kubelet liveness and readiness probes on the controller container SHALL continue to reach the
controller's app-port health endpoints (`/q/health/live` and `/q/health/ready` on port `8080`)
regardless of `.Values.auth.enabled`. The Dapr sidecar's own health endpoint (`/v1.0/healthz`)
SHALL NOT be intercepted by the bearer middleware.

Owning component: `charts/dws` (`templates/controller/deployment.yaml`).

#### Scenario: Liveness/readiness probes stay on the app port
- **WHEN** `helm template charts/dws --set auth.enabled=true` is run
- **THEN** the controller container's `livenessProbe.httpGet.port` and
  `readinessProbe.httpGet.port` resolve to the controller container's `http` port (`8080`),
  not the sidecar port

### Requirement: Auth values contract is documented and validated

`charts/dws/values.yaml` SHALL declare an `auth` block with the following fields and defaults:
`enabled: false`, `issuer: ""`, `audience: ""`, `jwksURL: ""`, `dex.enabled: false`. Enabling
`auth.enabled` without either (a) a non-empty `auth.issuer` + `auth.audience`, or (b)
`auth.dex.enabled=true` combined with `dex.enabled=true`, SHALL cause `helm template` to fail
with a message that identifies the missing values. When `auth.dex.enabled=true`, the chart SHALL
derive `issuer`, `audience`, and `jwksURL` from the in-chart Dex configuration and MUST NOT
require the operator to duplicate them under `auth.*`.

Owning component: `charts/dws` (`values.yaml`, `templates/controller/auth-*.yaml`,
`templates/_helpers.tpl`).

#### Scenario: values.yaml exposes the auth block with safe defaults
- **WHEN** `charts/dws/values.yaml` is read
- **THEN** it contains an `auth:` block with `enabled: false`, `issuer: ""`, `audience: ""`,
  `jwksURL: ""`, and `dex.enabled: false`

#### Scenario: Dex-mode derivation avoids duplicate configuration
- **WHEN** `helm template charts/dws --set dex.enabled=true --set auth.enabled=true --set auth.dex.enabled=true` is run
- **THEN** rendering succeeds without any explicit `auth.issuer`/`auth.audience`/`auth.jwksURL`
- **AND** the rendered bearer Component's issuer/audience/jwksURL agree with the Dex-derived
  values used elsewhere in the chart
