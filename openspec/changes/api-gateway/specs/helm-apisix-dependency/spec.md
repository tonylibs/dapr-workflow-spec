## ADDED Requirements

### Requirement: APISIX is a pinned optional chart dependency

`charts/dws/Chart.yaml` SHALL declare the upstream `apisix` chart as an explicitly versioned
dependency from the official Apache repository with `condition: apisix.enabled`. `Chart.lock` and
the vendored archive under `charts/dws/charts/` MUST match that declaration. The selected bundled
configuration SHALL enable an APISIX data plane and its ingress controller with Gateway API
support. Owning component: `charts/dws`.

#### Scenario: APISIX disabled by default
- **WHEN** the chart renders with default values and `apisix.enabled=false`
- **THEN** no APISIX dependency workload or Service is present in the rendered output

#### Scenario: APISIX enabled renders controller and data plane
- **WHEN** the chart renders with `apisix.enabled=true`
- **THEN** the output contains the pinned APISIX data-plane resources and APISIX ingress
  controller resources required to reconcile Gateway API objects

#### Scenario: Dependency artifacts are reproducible
- **WHEN** `helm dependency build charts/dws` is run from the checked-in `Chart.yaml` and lock
- **THEN** the resolved APISIX chart version and digest match `Chart.lock` and the vendored archive

### Requirement: External APISIX mode fails fast when required CRDs are absent

When `apiGateway.enabled=true` and `apisix.enabled=false`, the chart MUST verify through Helm
capabilities that the target cluster serves Gateway API v1 and every APISIX API version required
by the rendered Gateway binding. Missing APIs SHALL fail render/install/upgrade with an error that
names the missing prerequisite and offers either `apisix.enabled=true` or external installation
as remediation. The check SHALL NOT reject a first bundled install solely because dependency CRDs
are absent from the pre-install capability snapshot. Owning component: `charts/dws`.

#### Scenario: External mode without Gateway API CRDs fails
- **WHEN** `apiGateway.enabled=true`, `apisix.enabled=false`, and Gateway API v1 is absent from
  `.Capabilities.APIVersions`
- **THEN** rendering fails with an error naming Gateway API CRDs

#### Scenario: External mode without APISIX CRDs fails
- **WHEN** Gateway API v1 is present but the APISIX API used by the GatewayProxy binding is absent
- **THEN** rendering fails with an error naming the APISIX CRD prerequisite

#### Scenario: External mode with compatible CRDs renders
- **WHEN** `apiGateway.enabled=true`, `apisix.enabled=false`, and all expected APIs are supplied
  through Helm capabilities
- **THEN** preflight succeeds and the DWS Gateway API resources render without bundled APISIX
  workloads

#### Scenario: Bundled mode does not false-fail preflight
- **WHEN** `apiGateway.enabled=true` and `apisix.enabled=true` on a cluster capability snapshot
  that does not yet report dependency-installed CRDs
- **THEN** the external-controller preflight is skipped and Helm can install dependency CRDs in
  its normal CRD phase

