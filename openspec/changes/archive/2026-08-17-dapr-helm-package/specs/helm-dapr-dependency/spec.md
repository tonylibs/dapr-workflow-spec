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

### Requirement: A missed Dapr sidecar injection on the admin Pod self-heals

When `dapr.enabled` is `true`, the chart SHALL run a post-install/post-upgrade hook Job
that waits for the `dapr-sidecar-injector` Deployment's rollout and then deletes the admin
Pod if it does not have a `daprd` container, so the Deployment recreates it. This addresses
sidecar injection being a one-shot admission-webhook decision made at Pod creation: a plain
`helm install` creates the Dapr control plane and the admin Deployment in the same atomic
apply, so the injector's webhook reporting Ready does not guarantee it is reachable from the
API server by the time the admin Pod is admitted, and a missed injection never recovers via
container restart alone.

Owning component: `charts/dws` (`templates/dapr-ready-hook.yaml`).

#### Scenario: Sidecar injected successfully

- **WHEN** the admin Pod already has a `daprd` container by the time the hook Job runs
- **THEN** the hook Job takes no action

#### Scenario: Sidecar injection was missed

- **WHEN** the admin Pod exists but has no `daprd` container by the time the hook Job runs
- **THEN** the hook Job deletes that Pod so the admin Deployment recreates it

#### Scenario: Dapr disabled skips the hook

- **WHEN** `dapr.enabled` is `false`
- **THEN** the hook Job does not render
