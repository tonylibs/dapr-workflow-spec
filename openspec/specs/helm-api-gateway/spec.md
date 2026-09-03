# helm-api-gateway Specification

## Purpose

Defines the shared APISIX Gateway API front door for the console and bearer-gated admin routes.

## Requirements

### Requirement: One Gateway API front door renders for console and admin

When `apiGateway.enabled=true`, `charts/dws` SHALL render a release-and-namespace-qualified `GatewayClass`, one
namespaced `Gateway`, and HTTPRoutes attached to that Gateway for both `dws-console` and
`dws-admin`. The GatewayClass MUST identify the APISIX ingress controller, and the Gateway MUST
bind to the APISIX GatewayProxy/data-plane configuration selected by bundled or external mode.
When `apiGateway.enabled=false`, none of these objects SHALL render. Owning component:
`charts/dws`.

#### Scenario: Gateway disabled renders no front-door objects
- **WHEN** the chart renders with `apiGateway.enabled=false`
- **THEN** no DWS-owned GatewayClass, Gateway, HTTPRoute, or GatewayProxy object is present

#### Scenario: Gateway enabled renders shared topology
- **WHEN** the chart renders with `apiGateway.enabled=true` and all prerequisite values are valid
- **THEN** exactly one DWS Gateway and one release-and-namespace-qualified APISIX GatewayClass render
- **AND** console and admin HTTPRoutes both reference that Gateway

#### Scenario: Release names do not collide
- **WHEN** two releases render in different namespaces or with different release names
- **THEN** their chart-owned GatewayClass/Gateway/GatewayProxy names and references are
  deterministic and do not accidentally bind to the other release

### Requirement: Admin prefix routes through Dapr bearer-gated invocation

The admin HTTPRoute SHALL match `PathPrefix /dws-admin`, rewrite that prefix to
`/v1.0/invoke/<admin-app-id>/method/`, and forward to the admin Service port whose target is the
Dapr sidecar HTTP port 3500. It MUST preserve the suffix path, query string, request method,
request body, `Authorization` and content headers, response status/body/headers, and streaming
response behavior. It SHALL NOT target the Nest app port directly. Owning component:
`charts/dws`.

#### Scenario: Read route becomes Dapr invoke request
- **WHEN** a caller requests `GET /dws-admin/workflows?limit=20` through the Gateway
- **THEN** APISIX forwards `GET /v1.0/invoke/<admin-app-id>/method/workflows?limit=20` to the
  sidecar-backed admin Service

#### Scenario: Write body and token are preserved
- **WHEN** a caller posts YAML to `/dws-admin/workflows?dryRun=true` with a bearer token
- **THEN** the upstream request retains the YAML bytes, content type, Authorization header, and
  `dryRun=true` query on the admin Dapr invoke URL

#### Scenario: SSE response is not configured for buffering
- **WHEN** an admin SSE path is routed through the Gateway
- **THEN** the route uses the same sidecar-backed backend and introduces no filter that buffers or
  transforms the event-stream body

### Requirement: Console fallback route shares the same listener and origin

The console HTTPRoute SHALL match `PathPrefix /` on the same Gateway listener and forward to the
console Service. Route matching MUST select the more-specific `/dws-admin` rule for admin paths
and the console rule for all other paths. Hostname and optional TLS certificate references SHALL
be configured once at the Gateway/listener boundary and applied consistently to both routes.
Owning component: `charts/dws`.

#### Scenario: Console navigation reaches console Service
- **WHEN** a caller requests `/workflows` on the configured Gateway host
- **THEN** the request routes to the console Service

#### Scenario: Admin prefix wins over console fallback
- **WHEN** a caller requests `/dws-admin/instances`
- **THEN** the request routes to the admin sidecar-backed Service rather than the console Service

#### Scenario: TLS listener uses configured Secret
- **WHEN** Gateway TLS is enabled with a certificate Secret reference
- **THEN** the Gateway listener terminates TLS with that Secret and both HTTPRoutes attach to it

### Requirement: Gateway mode validates its security and workload prerequisites

Enabling `apiGateway.enabled` MUST require `auth.enabled=true`, `admin.enabled=true`, and
`console.enabled=true`. Invalid combinations SHALL fail at Helm render time with an actionable
message naming the incompatible values. APISIX SHALL remain a router; JWT validation MUST remain
in the admin Dapr sidecar's bearer middleware. Owning component: `charts/dws`.

#### Scenario: Gateway without auth is rejected
- **WHEN** `apiGateway.enabled=true` and `auth.enabled=false`
- **THEN** Helm rendering fails with an error stating that the shared admin route requires Dapr
  bearer authentication

#### Scenario: Missing backend is rejected
- **WHEN** `apiGateway.enabled=true` and either `admin.enabled=false` or `console.enabled=false`
- **THEN** rendering fails with an error naming the disabled required backend

### Requirement: Legacy console Ingress configuration fails with migration guidance

The chart SHALL NOT silently ignore an existing `console.ingress.enabled=true` value after the
Ingress template is removed. Rendering MUST fail with a migration message directing the operator
to enable `apiGateway`, choose bundled or external APISIX, move the hostname and TLS Secret to the
Gateway listener, remove Ingress class/annotations, and align the OIDC redirect URI with the shared
origin. Owning component: `charts/dws`.

#### Scenario: Legacy Ingress value is caught during upgrade
- **WHEN** a prior installation's values contain `console.ingress.enabled=true`
- **THEN** Helm upgrade rendering fails before resources change and prints the Gateway migration
  steps

#### Scenario: Migrated values render no Ingress
- **WHEN** the operator disables the legacy Ingress and supplies valid API Gateway values
- **THEN** Gateway API resources render and no `networking.k8s.io/v1 Ingress` is emitted
