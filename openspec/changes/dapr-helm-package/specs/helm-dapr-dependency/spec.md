## Purpose

Makes Dapr an optional, chart-managed prerequisite of `charts/dws` — installable by the chart
itself via a conditional dependency, or preflight-checked against an existing cluster install
when the operator opts out of chart-managed Dapr.

## ADDED Requirements

### Requirement: Dapr renders as a conditional chart dependency

`charts/dws`'s `Chart.yaml` SHALL declare `dapr/dapr` as a dependency gated on
`condition: dapr.enabled`. `values.yaml` SHALL expose `dapr.enabled`, defaulting to `true`.
When `dapr.enabled` is `true`, `helm install`/`upgrade` SHALL install the Dapr control plane as
part of the release. When `dapr.enabled` is `false`, the chart SHALL NOT render or install any
Dapr control-plane resources.

Owning component: `charts/dws` (`Chart.yaml`, `values.yaml`).

#### Scenario: Default render installs Dapr

- **WHEN** `helm template charts/dws` (or `helm install`) is run with default values
- **THEN** the rendered/installed release includes the `dapr` subchart's resources

#### Scenario: Dapr disabled renders no Dapr control-plane resources

- **WHEN** `helm template charts/dws` is run with `dapr.enabled=false`
- **THEN** no `dapr` subchart resources are rendered

### Requirement: Preflight check fails fast when Dapr is disabled but absent from the cluster

At `helm install`/`upgrade` time, when `dapr.enabled` is `false`, the chart SHALL check
`.Capabilities.APIVersions.Has` for a Dapr CRD API group. If that API group is not present in
the target cluster, the chart SHALL fail the install/upgrade with an actionable error rather
than deploying workloads that depend on an absent Dapr sidecar injector.

Owning component: `charts/dws` (templates evaluated via `.Capabilities`).

#### Scenario: Dapr disabled, cluster has no Dapr CRDs

- **WHEN** `helm install`/`upgrade` runs with `dapr.enabled=false` against a cluster with no
  Dapr CRDs installed
- **THEN** the operation fails before any workload is created, with an error identifying the
  missing Dapr prerequisite

#### Scenario: Dapr disabled, cluster already has Dapr

- **WHEN** `helm install`/`upgrade` runs with `dapr.enabled=false` against a cluster where Dapr
  CRDs are already present (chart-external installation)
- **THEN** the preflight check passes and the release proceeds normally

#### Scenario: Dapr enabled skips the preflight check

- **WHEN** `helm install`/`upgrade` runs with `dapr.enabled=true` (default)
- **THEN** the preflight check does not run, since the chart installs Dapr itself
