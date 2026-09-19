## 1. Establish the chart values and compatibility baseline

- [x] 1.1 Capture a deterministic pre-change default `helm template dws charts/dws --api-versions dapr.io/v1alpha1` fixture or equivalent comparison input for the disabled-state byte-identity assertion.
- [x] 1.2 Add the complete top-level `observability` block to `charts/dws/values.yaml` with the roadmap defaults and `enabled: false` as the master gate.
- [x] 1.3 Add release-safe helpers for the Instrumentation resource, OTLP Secret, shared controller/admin Configuration names, OTLP protocol/security normalization, and `dws.observability.podAnnotations` without changing resource names in existing default or auth-only renders.

## 2. Add the OpenTelemetry chart resources and preflight

- [x] 2.1 Add `templates/observability/instrumentation.yaml` with the endpoint, propagators, parent-based sampler, merged resource attributes, Java/Node.js/.NET sections, signal exporter settings, protocol, and optional header Secret reference.
- [x] 2.2 Add `templates/observability/otlp-secret.yaml` so inline headers produce one deterministic `headers` value, an existing Secret takes precedence, and no Secret renders when headers are absent.
- [x] 2.3 Add the exact `dws.preflight.observability` CRD check to `_preflight.tpl` and register it in `templates/preflight.yaml` alongside the Dapr and API Gateway checks.
- [x] 2.4 Document the OpenTelemetry Operator as an external prerequisite pinned to chart `0.123.0` / operator `0.159.0`, including cert-manager and the existing-Secret `headers` key contract, without adding a `Chart.yaml` dependency.

## 3. Merge tracing into the existing Dapr Configurations

- [x] 3.1 Rename the controller auth configuration template to `controller/configuration.yaml`, use the shared controller config-name helper, and render one resource when auth or observability is enabled.
- [x] 3.2 Preserve the controller bearer handler exclusively under `spec.appHttpPipeline` when auth is enabled, and add observability-only `spec.tracing` with literal `samplingRate: "1"`, the ADR 0005 comment, endpoint, derived security, and mapped Dapr protocol.
- [x] 3.3 Rename the admin auth configuration template to `admin/configuration.yaml`, use the shared admin config-name helper, and render one resource when auth or observability is enabled.
- [x] 3.4 Preserve the admin bearer handler exclusively under `spec.httpPipeline` when auth is enabled, and add the same independently rendered tracing block without introducing `spec.appHttpPipeline`.
- [x] 3.5 Update all configuration helper call sites and confirm no standalone tracing Configuration or second `dapr.io/config` annotation exists.

## 4. Target controller and admin pod instrumentation

- [x] 4.1 Widen the controller Deployment's configuration annotation gate to auth-or-observability and include the observability helper for Java injection targeted exactly to container `controller`.
- [x] 4.2 Widen the admin Deployment's annotations/configuration and Dapr environment gates as required by observability, and include Node.js injection targeted exactly to container `admin` while preserving auth-only sidecar listen-address behavior.
- [x] 4.3 Render the four auth/observability combinations and inspect both Deployments to confirm default, auth-only, observability-only, and combined annotations remain internally consistent.

## 5. Add render regression coverage and CI wiring

- [x] 5.1 Add `charts/dws/tests/observability-render-test.sh` covering all four auth/observability combinations, exactly one Configuration per enabled component, exact controller/admin pipeline placement, conditional tracing, literal Dapr sampling `"1"`, matching configuration annotations, targeted injection, protocol/security mapping, and Secret precedence.
- [x] 5.2 Add the disabled-state baseline comparison to the observability render test so any default manifest byte change fails with actionable output.
- [x] 5.3 Add the observability render test to `.github/workflows/helm.yml` beside the existing chart scripts and to the root `CLAUDE.md` chart gate block.
- [x] 5.4 Run `bash charts/dws/tests/auth-pipeline-placement-test.sh`, `bash charts/dws/tests/api-gateway-render-test.sh`, `bash charts/dws/tests/values-schema-test.sh`, and `bash charts/dws/tests/observability-render-test.sh`, fixing only regressions caused by this change.

## 6. Verify chart rendering and live telemetry

- [x] 6.1 Run `helm lint charts/dws` and the default `helm template dws charts/dws --api-versions dapr.io/v1alpha1`, confirming no observability resource or annotation appears.
- [x] 6.2 Run the enabled render with both `dapr.io/v1alpha1` and `opentelemetry.io/v1alpha1`, then verify the Instrumentation, merged Configurations, OTLP Secret modes, sampling ownership, and pod annotations against the specs.
- [x] 6.3 Exercise the negative preflight without the Operator API and the explicit `observability.operator.required=false` bypass, confirming the exact failure guidance and successful opt-out.
- [ ] 6.4 In a cluster with the pinned Operator and a reachable test Collector, enable observability and capture evidence that enabled traces, metrics, and logs arrive and that one connected trace spans controller → Dapr → admin → Postgres without application image or code changes.

## 7. Close roadmap and tracker documentation

- [x] 7.1 Update `docs/roadmaps/observability.md` Phase 1 to complete and add a Findings log entry recording the merged single-Configuration implementation, pinned Dapr sampling, protocol mapping, Secret contract, and verification evidence.
- [x] 7.2 Update `docs/roadmaps/README.md` so its Current phase table reflects Observability Phase 1 completion.
- [ ] 7.3 Update the linked Notion observability roadmap mirror and the tracker database Phase 1 row with the same status, decisions, and evidence.
- [ ] 7.4 Update the Notion “Roadmaps — Overview” current-phase entry and cross-check repository and Notion status text for drift before completion.

## Outstanding

- **6.4** needs a cluster with the pinned OpenTelemetry Operator (`0.123.0` / `0.159.0`),
  cert-manager, and a reachable OTLP receiver. Not available in the environment this change was
  implemented in, so the connected controller → Dapr → admin → Postgres trace has not been
  captured. Phase 1 is recorded as ⚠️ in `docs/roadmaps/observability.md` for exactly this reason.
  Everything render-level is complete and guarded by `charts/dws/tests/observability-render-test.sh`.
- **7.3 / 7.4** write to the external Notion roadmap mirror and tracker database. Left for an
  explicit go-ahead rather than done implicitly — the repository-side documentation (7.1, 7.2) is
  complete and is the source the mirror should be synced from.
