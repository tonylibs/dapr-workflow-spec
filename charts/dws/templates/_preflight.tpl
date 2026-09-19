{{/*
Preflight check for Dapr: when dapr.enabled is false, this chart does not install Dapr itself,
so the admin Deployment's dapr.io/* sidecar annotations only work if Dapr is already present in
the target cluster. Capabilities.APIVersions is populated from the real cluster at
install/upgrade time, so this fails fast (before any workload is created) instead of leaving the
admin pod stuck waiting on a sidecar that will never be injected.
*/}}
{{- define "dws.preflight.dapr" -}}
{{- if not .Values.dapr.enabled }}
{{- if not (.Capabilities.APIVersions.Has "dapr.io/v1alpha1") }}
{{- fail "dapr.enabled=false but Dapr CRDs (dapr.io/v1alpha1) were not found in the cluster. Either set dapr.enabled=true to let this chart install Dapr, or install Dapr separately before running helm install/upgrade." }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Preflight check for an externally managed APISIX/Gateway API controller (auth roadmap §2b):
when apiGateway.enabled=true and apisix.enabled=false, this chart renders Gateway API and APISIX
GatewayProxy objects but does not install their CRDs itself, so both API groups must already be
served by the target cluster. Capabilities.APIVersions reflects the real cluster at
install/upgrade time, so this fails fast — before any Gateway/HTTPRoute/GatewayProxy object is
created — instead of leaving unreconciled resources behind.

Deliberately skipped when apisix.enabled=true: Helm computes .Capabilities.APIVersions from the
cluster BEFORE a fresh install's own dependency CRDs (bundled in
charts/apisix/charts/apisix-ingress-controller/crds/) are applied, so checking here on a first
bundled install would false-fail even though Helm's normal CRD-then-template ordering installs
them correctly.
*/}}
{{- define "dws.preflight.apiGateway" -}}
{{- if and .Values.apiGateway.enabled (not .Values.apisix.enabled) }}
{{- if not (.Capabilities.APIVersions.Has "gateway.networking.k8s.io/v1") }}
{{- fail "apiGateway.enabled=true with apisix.enabled=false requires Kubernetes Gateway API v1 CRDs (gateway.networking.k8s.io/v1) to already be installed in the cluster. Either install the Gateway API CRDs and a compatible APISIX Gateway API controller before this install/upgrade, or set apisix.enabled=true to let this chart install its own bundled APISIX plus Gateway API CRDs." }}
{{- end }}
{{- if not (.Capabilities.APIVersions.Has "apisix.apache.org/v1alpha1") }}
{{- fail "apiGateway.enabled=true with apisix.enabled=false requires the APISIX apisix.apache.org/v1alpha1 CRDs (specifically GatewayProxy) to already be installed in the cluster. Either install a compatible external APISIX ingress controller's CRDs before this install/upgrade, or set apisix.enabled=true to let this chart install bundled APISIX." }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Preflight check for the OpenTelemetry Operator (observability roadmap Phase 1): when
observability.enabled=true this chart renders an opentelemetry.io/v1alpha1 Instrumentation
resource and annotates pods for the Operator's mutating webhook, but it does NOT install the
Operator itself. Mirrors dws.preflight.dapr one-for-one — assert the CRDs are present,
otherwise fail with a message naming both ways out.

The Operator is a documented prerequisite rather than a Chart.yaml dependency specifically
because it requires cert-manager, which is a cluster singleton: bundling it risks colliding
with an existing install, and would hit the same "Capabilities computed before a fresh
install's own dependency CRDs land" hazard that dws.preflight.apiGateway already documents.

observability.operator.required=false is the explicit opt-out for controlled environments
that install the Operator out of band after this release.
*/}}
{{- define "dws.preflight.observability" -}}
{{- if and .Values.observability.enabled .Values.observability.operator.required }}
{{- if not (.Capabilities.APIVersions.Has "opentelemetry.io/v1alpha1") }}
{{- fail "observability.enabled=true but the OpenTelemetry Operator CRDs (opentelemetry.io/v1alpha1) were not found in the cluster. Install the OpenTelemetry Operator (which requires cert-manager) before running helm install/upgrade, or set observability.operator.required=false to skip this check." }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Value-shape validation for the observability block. Separate from dws.preflight.observability
(which asks the CLUSTER a question) because this asks the VALUES a question, and because it must
run even when neither the controller nor the admin is enabled.

Both checks would otherwise only be reached through the two component Configuration templates:
dws.observability.daprOtelProtocol is the sole rejecter of an unknown protocol, and nothing at
all rejects an empty endpoint. With `controller.enabled=false admin.enabled=false` an invalid
protocol rendered clean into OTEL_EXPORTER_OTLP_PROTOCOL, and an empty endpoint rendered an
empty exporter endpoint — Dapr then silently skips tracing (it guards on a non-empty address)
and the agents fall back to the SDK's own localhost default. Both are quiet misconfigurations
that only surface as "no telemetry arrives".

Called unconditionally from templates/preflight.yaml.
*/}}
{{- define "dws.observability.validate" -}}
{{- if .Values.observability.enabled }}
{{- if not .Values.observability.otlp.endpoint }}
{{- fail "observability.enabled=true requires a non-empty observability.otlp.endpoint (scheme-qualified, e.g. http://dws-otel-collector:4318). An empty endpoint renders an empty exporter address: Dapr silently skips tracing and the application agents fall back to their own localhost default, so no telemetry reaches your receiver." }}
{{- end }}
{{- if not (hasPrefix "http://" .Values.observability.otlp.endpoint | or (hasPrefix "https://" .Values.observability.otlp.endpoint)) }}
{{- fail (printf "observability.otlp.endpoint must start with http:// or https://, got %q. The scheme sets the Instrumentation resource's OTEL_EXPORTER_OTLP_ENDPOINT (which requires it) and derives Dapr's tracing.otel.isSecure." .Values.observability.otlp.endpoint) }}
{{- end }}
{{- /* Reached for its fail() side effect: this is the only rejecter of an unknown protocol. */ -}}
{{- $_ := include "dws.observability.daprOtelProtocol" . }}
{{- end }}
{{- end }}
