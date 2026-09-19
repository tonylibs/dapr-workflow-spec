## Why

Operators currently cannot opt the chart-managed DWS control plane into end-to-end OpenTelemetry telemetry without changing application code or maintaining private deployment patches. Phase 1 adds a disabled-by-default Helm surface that can connect `dws-controller`, its Dapr sidecar, `dws-admin`, and Postgres activity to an operator-supplied OTLP endpoint while preserving the chart's existing default topology.

## What Changes

- Add a top-level `observability` values contract for OTLP connection details, signal toggles, sampling, operator preflight behavior, workflow opt-in, and resource attributes; `observability.enabled` defaults to `false`.
- Render one namespaced OpenTelemetry Operator `Instrumentation` resource when observability is enabled, with Java, Node.js, and .NET instrumentation sections, `service.namespace=dws`, and `parentbased_traceidratio` sampling controlled by `observability.traces.samplingRate`.
- Merge Dapr tracing into the controller and admin `Configuration` resources that already carry auth middleware, because `dapr.io/config` accepts only one resource name. Keep the controller's `appHttpPipeline` and the admin's `httpPipeline` conditional on auth alone, while pinning Dapr `spec.tracing.samplingRate` to `"1"` per ADR 0005.
- Add targeted OTel injection annotations to the controller and admin pod templates, including mandatory container-name selection so the operator does not instrument the Dapr sidecar.
- Add OTLP header Secret rendering/passthrough and a fail-fast preflight for the externally installed OpenTelemetry Operator CRDs.
- Add a four-way auth/observability render matrix test, wire it into Helm CI, and update the chart gate and observability roadmap documentation and mirrors.

## Capabilities

### New Capabilities

- `helm-observability`: Defines the disabled-by-default observability values surface, OpenTelemetry `Instrumentation` and optional OTLP Secret resources, operator preflight, targeted controller/admin injection, and Dapr tracing behavior.

### Modified Capabilities

- `helm-controller-auth-middleware`: The controller's single Dapr `Configuration` can now render for observability without auth and can contain tracing alongside the auth-only `appHttpPipeline`.
- `helm-admin-auth-middleware`: The admin's single Dapr `Configuration` can now render for observability without auth and can contain tracing alongside the auth-only `httpPipeline`.
- `helm-controller-deployment`: The controller pod references its shared Dapr `Configuration` when either auth or observability is enabled and receives targeted Java auto-instrumentation annotations only when observability is enabled.
- `helm-admin-deployment`: The admin pod references its shared Dapr `Configuration` when either auth or observability is enabled and receives targeted Node.js auto-instrumentation annotations only when observability is enabled.

## Impact

- Affected implementation surface: `charts/dws` values, helpers, preflight, controller/admin templates, new observability templates, chart render tests, `.github/workflows/helm.yml`, and the root chart-gate documentation.
- Affected deployed resources: two existing per-component Dapr `Configuration` objects, the controller and admin Deployments, one new `Instrumentation` CR, and an optional OTLP header Secret. No second tracing `Configuration` or second `dapr.io/config` annotation is introduced.
- Affected APIs and dependencies: a new Helm values contract and a documented prerequisite on OpenTelemetry Operator chart `0.123.0` / operator `0.159.0`; the operator and cert-manager remain external and are not added to `Chart.yaml`.
- DSL behavior and workflow compilation are unchanged. Runtime interpretation is unchanged. The `dws-controller` and `dws-admin` application binaries and their independent build boundaries are untouched; all behavior is introduced through pod mutation and Dapr configuration rendered by `charts/dws`.
- Documentation impact includes the repository observability roadmap, roadmap overview, the linked Notion roadmap mirror, and the Phase 1 tracker row.

## Non-goals and Compatibility

- The change does not instrument `dws-orchestrator`, `dws-flow`, `dws-step`, any Knative step image, or Knative's cluster-owned `config-observability`; those remain later phases.
- The change does not add application telemetry SDK code, route additional egress through Dapr, bundle an OpenTelemetry Collector, or install the OpenTelemetry Operator/cert-manager dependency.
- Existing workflow definitions and DSL contracts remain compatible and unchanged.
- With `observability.enabled=false` (the default), rendered chart output must remain byte-identical to the pre-change baseline. Existing auth-only behavior remains intact, including the deliberate controller/admin pipeline divergence.
