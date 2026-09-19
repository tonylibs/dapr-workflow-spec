## Context

`charts/dws` currently owns the ordinary Kubernetes Deployments for `dws-controller` and `dws-admin`. Both pods use Dapr, and each can already reference one component-specific Dapr `Configuration` when bearer authentication is enabled. The controller configuration uses `spec.appHttpPipeline`; the admin configuration uses `spec.httpPipeline`. That divergence is intentional and protects different traffic paths.

The OpenTelemetry Operator can add Java and Node.js agents to these existing pods at admission time, so Phase 1 does not require changes to either application. The chart must create the Operator's `Instrumentation` resource, opt only the application container into injection, and configure each Dapr sidecar to export its spans to the same OTLP destination. Because `dapr.io/config` is single-valued, tracing cannot be introduced as a second Dapr `Configuration`.

The OpenTelemetry Operator and its cert-manager prerequisite are cluster-scoped operational dependencies. They are not owned by this chart. The default render must remain unchanged until an operator explicitly enables observability.

## Goals / Non-Goals

**Goals:**

- Add the roadmap-defined `observability.*` Helm values with `observability.enabled=false` as the master switch.
- Auto-instrument the chart-managed Java controller and Node.js admin application containers for OTLP traces, metrics, and logs without modifying application code.
- Export Dapr sidecar spans through the same component-specific `Configuration` that may also carry auth middleware.
- Preserve auth behavior for all four combinations of `auth.enabled` and `observability.enabled`.
- Fail early when observability is enabled but the required Operator API is unavailable, unless the operator explicitly disables that preflight.
- Keep credentials out of rendered workload annotations and support either chart-generated or operator-managed OTLP header Secrets.
- Prove default-render compatibility and the auth/observability resource matrix in chart CI.

**Non-Goals:**

- Instrumenting controller-deployed `dws-orchestrator`, `dws-flow`, `dws-step`, or Knative step services.
- Modifying DSL compilation, workflow resource synthesis, runtime interpretation, or any application source code.
- Installing the OpenTelemetry Operator, cert-manager, or an OpenTelemetry Collector as a chart dependency in this phase.
- Managing Knative's cluster-owned `config-observability` ConfigMap.
- Solving Dapr Workflow replay semantics, Knative admission constraints, or Go/Python instrumentation.

## Decisions

### 1. Observability is a disabled-by-default additive chart surface

The chart adds every value in the roadmap's user-facing table, with the documented defaults and `observability.enabled=false`. Templates that create resources or annotations are gated by the master switch. Values intended for later phases (`workflows`, bundled collector, and richer log behavior) establish the stable public surface but do not expand Phase 1's workload scope.

Alternative considered: infer enablement from a non-empty OTLP endpoint. Rejected because the endpoint has a useful default and implicit activation would violate the byte-identical default-render requirement.

### 2. Auth and tracing share one Dapr Configuration per component

Rename each internal template from `auth-configuration.yaml` to `configuration.yaml` and render it when its component is enabled and either auth or observability is enabled. Rename the helpers to `dws.controller.configName` and `dws.admin.configName` so their names describe the shared resource. Kubernetes resource names stay unchanged.

Inside each resource:

- Render the existing bearer handler only when `auth.enabled=true`.
- Preserve `appHttpPipeline` for the controller and `httpPipeline` for the admin.
- Render `spec.tracing` only when `observability.enabled=true`.
- Set Dapr `samplingRate: "1"` literally, with an ADR 0005 comment in each template.
- Source the endpoint and transport from `observability.otlp`. Map public protocol `http/protobuf` to Dapr protocol `http`, retain `grpc` as `grpc`, and derive `otel.isSecure` from whether the configured endpoint uses `https://`; no additional public value is added beyond the roadmap table.

Each Deployment references this one configuration when either feature is on. A standalone tracing configuration or a second `dapr.io/config` annotation is forbidden.

Alternative considered: create a shared `dws-tracing` resource. Rejected because Dapr accepts exactly one configuration annotation and the shared resource would either collide with or replace the shipped auth pipeline.

### 3. The application agent is the only sampling root

The `Instrumentation` resource uses `parentbased_traceidratio` and `observability.traces.samplingRate`. Every Dapr configuration is pinned to `samplingRate: "1"`, so Dapr honors the parent's decision rather than making a second probabilistic decision. This directly implements ADR 0005 Decision 2.

Alternative considered: pass the tunable sampling rate to both the agent and Dapr. Rejected because independent sampling composes multiplicatively and creates partial traces that look like exporter or service failures.

### 4. One namespaced Instrumentation resource configures all Phase 1 agents

When enabled, render `opentelemetry.io/v1alpha1` `Instrumentation` named from a release-safe helper in `dws.namespace`. It contains:

- the configured OTLP endpoint;
- `tracecontext` and `baggage` propagators;
- the parent-based sampler;
- `resource.resourceAttributes.service.namespace: dws`, merged with operator-supplied attributes without allowing the required namespace attribute to be removed;
- present `java: {}`, `nodejs: {}`, and `dotnet: {}` sections;
- common `spec.env` entries for OTLP protocol, per-signal exporter enablement, and optional OTLP headers.

The v1alpha1 Operator API has a common `spec.env` layer, so shared `OTEL_*` settings and a Secret reference do not need to be repeated in the three language sections. `http/protobuf` and `grpc` remain the public protocol values used by application agents.

Alternative considered: duplicate exporter environment variables in every language section. Rejected because it creates three copies of one chart contract and makes later workflow-language additions error-prone.

### 5. OTLP headers use one explicit Secret contract

The Instrumentation resource reads `OTEL_EXPORTER_OTLP_HEADERS` from key `headers` in a Secret. If `observability.otlp.existingSecret` is non-empty, that Secret is referenced and the chart creates none. Otherwise, when `observability.otlp.headers` is non-empty, the chart creates a release-named Opaque Secret whose `stringData.headers` is a deterministic comma-separated `key=value` list compatible with the OTLP environment-variable format. If both values are supplied, `existingSecret` wins, matching the existing admin Secret precedence pattern. If neither is supplied, no Secret or header environment variable is rendered.

This phase does not project header credentials into Dapr's distinct `otel.headers[]` structure because the single existing-Secret string contract does not provide the header-name-to-secret-key mapping that Dapr requires. Deployments that require authenticated Dapr export should point Dapr at an in-cluster collector and let that collector authenticate upstream; adding a richer Dapr header mapping is a future contract change.

Alternative considered: inline header values in the Instrumentation CR. Rejected because Helm output and release metadata would expose credentials outside a Kubernetes Secret.

### 6. Injection annotations target only application containers

Add a `dws.observability.podAnnotations` helper that accepts the root context, language, and application container name. It emits the language injection annotation pointing to the release's Instrumentation resource plus `instrumentation.opentelemetry.io/container-names`.

The controller call uses language `java` and container `controller`; the admin call uses `nodejs` and container `admin`. Targeting is mandatory because an untargeted annotation would attempt to inject into the Dapr sidecar as well.

Alternative considered: place literal annotations independently in both Deployments. Rejected because the reference and targeting rules form one cross-template invariant that should have one implementation.

### 7. The Operator remains an external prerequisite

`dws.preflight.observability` checks for `opentelemetry.io/v1alpha1` only when both observability and `observability.operator.required` are enabled. The exact failure text identifies the Operator, cert-manager prerequisite, and opt-out. The include is registered in `templates/preflight.yaml`.

Operator chart `0.123.0` / operator `0.159.0` is documented as the tested pin. It is not added to `Chart.yaml`, because bundling it would pull in cert-manager and risk ownership conflicts with cluster-singleton installations.

### 8. Render tests pin the feature matrix and the no-op baseline

`observability-render-test.sh` renders all four auth/observability combinations with the required API versions. It asserts exactly one component configuration when either feature is active, exact pipeline placement only under auth, tracing only under observability, pinned Dapr sampling, one matching `dapr.io/config` annotation, targeted injection, and no observability manifests or annotations in the default render. A checked or generated pre-change default baseline comparison guards byte identity. The script runs in `.github/workflows/helm.yml` and is listed in the root chart gate.

## Risks / Trade-offs

- **[Risk] Auth middleware can be silently dropped while adding tracing.** → Keep one configuration resource per component and test the four feature combinations, exact pipeline fields, and matching annotation name.
- **[Risk] A future cleanup makes Dapr sampling tunable.** → Render the literal string `"1"`, add an ADR comment beside it, and assert it in the render test.
- **[Risk] The Operator injects the application agent into `daprd`.** → Always emit the container-names annotation and assert the exact controller/admin container names.
- **[Risk] The external Operator is absent or has incompatible templates.** → Fail preflight by default, document and pin the tested versions, and provide an explicit preflight opt-out for controlled environments.
- **[Risk] Secret header serialization is ambiguous.** → Standardize on one `headers` key containing the OTLP/W3C-baggage `key=value` list and document the existing-Secret contract.
- **[Risk] Dapr's transport vocabulary differs from the OTel SDK vocabulary.** → Centralize the `http/protobuf` to `http` mapping and cover both supported values in rendering tests.
- **[Trade-off] Auto-instrumentation coverage varies by runtime and library.** → Phase 1 proves the chart wiring and connected control-plane trace without promising domain metrics or structured-log correlation, which remain later phases.
- **[Trade-off] Header credentials are applied to application agents, not directly to Dapr.** → Prefer an in-cluster collector as the Dapr destination; defer a separate Dapr header mapping until its values contract is explicit.

## Migration Plan

1. Merge the templates with observability disabled and confirm the default manifest is byte-identical to the pre-change baseline.
2. Operators install the documented OpenTelemetry Operator version and cert-manager, and provide a reachable OTLP receiver plus any header Secret.
3. Operators enable `observability.enabled=true`; Helm creates the Instrumentation resource, optional Secret, merged Dapr configurations, and targeted pod annotations. Deployment rollouts allow the webhook to inject the agents.
4. Validate a connected controller → Dapr → admin → Postgres trace and confirm trace, metric, and log export at the receiver.
5. To roll back observability, set `observability.enabled=false`. Helm removes the observability resources and annotations; auth-only configurations and pipelines remain when auth is enabled. No application or workflow-definition migration is required.

## Open Questions

None block Phase 1. Dapr Workflow replay behavior, compiled Deployment instrumentation, Knative admission, direct Dapr OTLP authentication, bundled collector packaging, and Knative-native telemetry remain explicitly deferred to their roadmap phases.
