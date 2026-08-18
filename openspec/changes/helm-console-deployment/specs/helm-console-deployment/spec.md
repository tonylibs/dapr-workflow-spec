## Purpose

Render the `dws-console` Deployment, Service, and Ingress from chart values, including the default
same-host `/dws-admin` proxy topology to the `dws-admin` Service, so an operator can bring up the
console UI as part of the same `helm install` as the rest of the control plane.

## ADDED Requirements

### Requirement: Console resources render from values

The chart SHALL render the `dws-console` Deployment and Service only when `.Values.console.enabled`
is `true`. The Deployment SHALL expose container port 3000; the Service SHALL expose port
`console.service.port`, targeting the container's port 3000.

Owning component: `charts/dws` (`templates/console/deployment.yaml`,
`templates/console/service.yaml`).

#### Scenario: Console enabled

- **WHEN** `helm template charts/dws` is run with `console.enabled=true`
- **THEN** one console Deployment and one console Service are rendered

#### Scenario: Console disabled (default)

- **WHEN** `helm template charts/dws` is run with default values
- **THEN** no console Deployment, Service, or Ingress is rendered

### Requirement: Console environment and health probes follow the container contract

The Deployment SHALL NOT set `VITE_DWS_ADMIN_URL` as a runtime environment variable — it is a
build-time value baked into the image. The Deployment's liveness and readiness probes SHALL
request `/healthz` on the container's HTTP port.

#### Scenario: Default render

- **WHEN** `helm template charts/dws` is run with `console.enabled=true`
- **THEN** the console Deployment's container has liveness and readiness `httpGet` probes on path
  `/healthz` and no `VITE_DWS_ADMIN_URL` environment variable

### Requirement: Console Ingress proxies the admin API under one host by default

When `console.enabled` and `console.ingress.enabled` are both `true`, the chart SHALL render one
Ingress on `console.ingress.host` routing `/` to the console Service and `console.ingress.
adminPath` (default `/dws-admin`) to the admin Service, stripping that path prefix before
forwarding to the admin Service so admin — which sets no application-level route prefix — receives
unprefixed paths (for example, `/workflows`, `/health`).

Owning component: `charts/dws` (`templates/console/ingress.yaml`).

#### Scenario: Ingress enabled

- **WHEN** `helm template charts/dws` is run with `console.enabled=true` and
  `console.ingress.enabled=true`
- **THEN** one Ingress renders on `console.ingress.host` with a rule forwarding
  `console.ingress.adminPath` (with any trailing path) to the admin Service and a rule forwarding
  all other paths to the console Service, and a strip-prefix rewrite is configured for the admin
  rule

#### Scenario: Ingress disabled

- **WHEN** `console.ingress.enabled` is `false` (default when console is enabled)
- **THEN** no Ingress is rendered, even if `console.enabled=true`

#### Scenario: Console disabled implies no Ingress

- **WHEN** `console.enabled=false`
- **THEN** no Ingress renders regardless of `console.ingress.enabled`
