{{/*
Expand the name of the chart.
*/}}
{{- define "dws.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "dws.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "dws.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "dws.labels" -}}
helm.sh/chart: {{ include "dws.chart" . }}
{{ include "dws.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "dws.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dws.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Namespace to deploy into. Defaults to the release namespace, overridable via namespaceOverride.
*/}}
{{- define "dws.namespace" -}}
{{- default .Release.Namespace .Values.namespaceOverride }}
{{- end }}

{{/*
Controller fully qualified name.
*/}}
{{- define "dws.controller.fullname" -}}
{{- printf "%s-controller" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Controller selector labels — the common selector labels plus a component marker so the
controller Deployment/Service cannot match sibling components (admin, postgres).
*/}}
{{- define "dws.controller.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: controller
{{- end }}

{{/*
Admin fully qualified name.
*/}}
{{- define "dws.admin.fullname" -}}
{{- printf "%s-admin" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Admin selector labels — the common selector labels plus a component marker so the
admin Deployment/Service cannot match sibling components (controller, postgres).
*/}}
{{- define "dws.admin.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: admin
{{- end }}

{{/*
Console fully qualified name.
*/}}
{{- define "dws.console.fullname" -}}
{{- printf "%s-console" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Console selector labels — the common selector labels plus a component marker so the
console Deployment/Service cannot match sibling components (controller, admin, postgres).
*/}}
{{- define "dws.console.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: console
{{- end }}

{{/*
Postgres fully qualified name — used for the chart-owned DATABASE_URL Secret consumed by admin.
*/}}
{{- define "dws.postgres.fullname" -}}
{{- printf "%s-postgres" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Postgres primary Service host — the Bitnami postgresql subchart's default primary Service name
(<release>-postgresql), overridable via postgresql.fullnameOverride. Kept in sync with the
subchart so the composed DATABASE_URL resolves to the deployed Postgres.
*/}}
{{- define "dws.postgres.host" -}}
{{- if .Values.postgresql.fullnameOverride }}
{{- .Values.postgresql.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-postgresql" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Postgres selector labels — the common selector labels plus a component marker so the
postgres StatefulSet/Service cannot match sibling components (controller, admin).
*/}}
{{- define "dws.postgres.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
Dapr-ready hook fully qualified name — the post-install/post-upgrade Job (and its
ServiceAccount/Role/RoleBinding) that self-heals a missed Dapr sidecar injection.
*/}}
{{- define "dws.daprReadyHook.fullname" -}}
{{- printf "%s-dapr-ready" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Dex fully qualified name — used by chart-owned Dex resources (the admin-credentials Secret).
Not necessarily the same name the dex subchart's own Deployment/Service render under; the two
are independent resources with no naming cross-reference required.
*/}}
{{- define "dws.dex.fullname" -}}
{{- printf "%s-dex" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Dex selector labels — the common selector labels plus a component marker.
*/}}
{{- define "dws.dex.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: dex
{{- end }}

{{/*
Bootstrap-admin credentials Secret fully qualified name — holds the operator-facing plaintext
email/password (see templates/dex/secrets.yaml). Distinct from dex.configSecret.name,
which holds only the bcrypt hash consumed by Dex itself.
*/}}
{{- define "dws.dex.adminCredentials.fullname" -}}
{{- printf "%s-admin-credentials" (include "dws.dex.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Browser origin allowed to call Dex's discovery, token, and keys endpoints.
The registered redirect URI is the console root, but CORS compares only the
scheme and authority. Derive the origin here so the two values cannot drift.
*/}}
{{- define "dws.dex.consoleOrigin" -}}
{{- $redirect := urlParse .Values.dex.consoleRedirectURI -}}
{{- printf "%s://%s" (get $redirect "scheme") (get $redirect "host") -}}
{{- end }}

{{/*
Bootstrap admin password: generated once at first install, never rotated on upgrade.
- dex.adminUser.existingSecret set: read the password from that operator-supplied Secret/key
  instead of generating one.
- Otherwise: `lookup` the chart's own admin-credentials Secret; reuse its stored password if
  found (helm upgrade), else generate a new one with randAlphaNum (helm install).
`lookup` always returns nothing during `helm template`/`--dry-run` (no live cluster to query),
so those render a freshly generated password every time — expected, see design.md's Risks.

IMPORTANT: `include` is not memoized — calling this template twice (once for the plaintext
Secret, once for the config Secret's bcrypt hash) would independently re-run randAlphaNum and
produce two *different* passwords. Call this exactly once per render, capture it in a `$password`
variable, and pass that variable into both the plaintext Secret render and dws.dex.config
(see templates/dex/secrets.yaml, the single file that does both).
*/}}
{{- define "dws.dex.resolvePassword" -}}
{{- if .Values.dex.adminUser.existingSecret }}
{{- $secret := lookup "v1" "Secret" (include "dws.namespace" .) .Values.dex.adminUser.existingSecret }}
{{- if not $secret }}
{{- fail (printf "dex.adminUser.existingSecret %q not found in namespace %q" .Values.dex.adminUser.existingSecret (include "dws.namespace" .)) }}
{{- end }}
{{- index $secret.data (.Values.dex.adminUser.existingSecretKey | default "password") | b64dec }}
{{- else }}
{{- $existing := lookup "v1" "Secret" (include "dws.namespace" .) (include "dws.dex.adminCredentials.fullname" .) }}
{{- if $existing }}
{{- index $existing.data "password" | b64dec }}
{{- else }}
{{- randAlphaNum 20 }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Full Dex configuration document (config.yaml), rendered chart-side rather than passed through
dex.config values, since values.yaml cannot call bcrypt itself — only .tpl files can (see
design.md D2/D3). Seeds exactly one static connector (password DB) and one static client
(dws-console, public PKCE, no secret). Takes a dict `{root: $, password: $password}` rather than
`.` directly so the caller controls exactly which already-resolved password gets hashed here —
see dws.dex.resolvePassword's memoization note.
*/}}
{{- define "dws.dex.config" -}}
{{- $ := .root }}
{{- $config := dict
  "issuer" $.Values.dex.issuer
  "storage" (dict "type" "memory")
  "web" (dict
    "http" "0.0.0.0:5556"
    "allowedOrigins" (list (include "dws.dex.consoleOrigin" $))
  )
  "enablePasswordDB" true
  "staticClients" (list
    (dict
      "id" "dws-console"
      "name" "dws-console"
      "public" true
      "redirectURIs" (list $.Values.dex.consoleRedirectURI)
    )
  )
  "staticPasswords" (list
    (dict
      "email" $.Values.dex.adminUser.email
      "username" "admin"
      "userID" "dws-bootstrap-admin"
      "hash" (bcrypt .password)
    )
  )
}}
{{- toYaml $config }}
{{- end }}

{{/*
Redis connection helpers — resolve host and Secret name/key for the three Dapr Redis
Components (pubsub, dws-definitions, actor state store). Presence of
.Values.redis.external.host is the override signal: unset means the in-chart Bitnami
Redis subchart's own Service (<release>-redis-master:6379) and auto-created Secret
(<release>-redis, key redis-password); set means the external host and
.Values.redis.external.existingSecret / existingSecretKey instead. There is no
redis.enabled toggle — Redis follows dapr.enabled (see Chart.yaml).
*/}}
{{/*
Auth (dws-console auth roadmap Phase 2) — resolve issuer, audience, and JWKS URL for the
controller sidecar's bearer middleware. Two modes:
  (a) auth.dex.enabled=true → derive from the in-chart Dex (issuer from dex.issuer, audience
      from the dws-console static client id, JWKS URL from Dex's fixed /keys path).
  (b) otherwise → use auth.issuer / auth.audience / auth.jwksURL directly.
Each helper fails render with an explicit message when auth.enabled=true and no source is
available for that field. jwksURL is optional (Dapr's bearer middleware discovers it from the
issuer's OIDC discovery document when unset), so its helper returns an empty string when
neither mode supplies one.
*/}}
{{- define "dws.auth.issuer" -}}
{{- if .Values.auth.dex.enabled -}}
{{- if not .Values.dex.enabled -}}
{{- fail "auth.dex.enabled=true requires dex.enabled=true" -}}
{{- end -}}
{{- required "dex.issuer is required when auth.dex.enabled=true" .Values.dex.issuer -}}
{{- else -}}
{{- required "auth.issuer is required when auth.enabled=true (set auth.issuer, or set auth.dex.enabled=true with dex.enabled=true)" .Values.auth.issuer -}}
{{- end -}}
{{- end }}

{{- define "dws.auth.audience" -}}
{{- if .Values.auth.dex.enabled -}}
{{- if not .Values.dex.enabled -}}
{{- fail "auth.dex.enabled=true requires dex.enabled=true" -}}
{{- end -}}
{{- /* Dex issues tokens whose `aud` equals the client_id that requested them. The
       dws-console static client is that client_id; the same token flows through
       Phase 3's dws-admin relay to the controller unchanged. */ -}}
dws-console
{{- else -}}
{{- required "auth.audience is required when auth.enabled=true (set auth.audience, or set auth.dex.enabled=true with dex.enabled=true)" .Values.auth.audience -}}
{{- end -}}
{{- end }}

{{- define "dws.auth.jwksURL" -}}
{{- if .Values.auth.dex.enabled -}}
{{- printf "%s/keys" (trimSuffix "/" .Values.dex.issuer) -}}
{{- else -}}
{{- .Values.auth.jwksURL -}}
{{- end -}}
{{- end }}

{{/*
Controller-scoped Dapr resource names. dws.auth.componentName is the bearer middleware
Component (auth roadmap Phase 2); dws.controller.configName is the SHARED Dapr Configuration
the controller Pod names in dapr.io/config. It carries the auth pipeline, the observability
tracing block, or both — dapr.io/config is single-valued, so there is exactly one such
resource per component (observability roadmap Phase 1). Its rendered Kubernetes name
(<controller fullname>-config) is unchanged from when it was auth-only.
*/}}
{{- define "dws.auth.componentName" -}}
{{- printf "%s-auth" (include "dws.controller.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "dws.controller.configName" -}}
{{- printf "%s-config" (include "dws.controller.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
Admin-scoped Dapr resource names. dws.admin.auth.componentName is the bearer middleware
Component (auth roadmap Phase 4), kept fully qualified so a future reader editing
`dws.auth.componentName` does not accidentally touch admin. dws.admin.configName is the admin
equivalent of dws.controller.configName above: the one shared Configuration carrying the auth
pipeline and/or observability tracing.
*/}}
{{- define "dws.admin.auth.componentName" -}}
{{- printf "%s-auth" (include "dws.admin.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "dws.admin.configName" -}}
{{- printf "%s-config" (include "dws.admin.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
APISIX fully qualified name — mirrors the pinned apache/apisix-helm-chart 2.16.0's own
"apisix.fullname" algorithm exactly (see charts/apisix's templates/_helpers.tpl) so the parent
chart can deterministically reference the bundled subchart's admin Service
(<this>-admin:9180) from the DWS-owned GatewayProxy without depending on `lookup`.
*/}}
{{- define "dws.apisix.fullname" -}}
{{- if .Values.apisix.fullnameOverride -}}
{{- .Values.apisix.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default "apisix" .Values.apisix.nameOverride -}}
{{- if contains $name .Release.Name -}}{{ .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else -}}{{ printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Shared API Gateway (auth roadmap §2b) — release/namespace-qualified names for the DWS-owned
GatewayClass (cluster-scoped, so it must not collide across releases/namespaces), Gateway, and
GatewayProxy. See values.yaml's apiGateway.* block for the operator-facing contract.
*/}}
{{- define "dws.apiGateway.fullname" -}}
{{- printf "%s-gateway" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "dws.apiGateway.selectorLabels" -}}
{{ include "dws.selectorLabels" . }}
app.kubernetes.io/component: api-gateway
{{- end }}

{{/*
GatewayClass name. Cluster-scoped, so a bare release-fullname is not enough to avoid collisions
between releases in different namespaces — fold the namespace in too. When the operator supplies
an explicit apiGateway.gatewayClassName (required whenever createGatewayClass=false, to attach to
an existing operator-owned class), use it verbatim instead.
*/}}
{{- define "dws.apiGateway.className" -}}
{{- if .Values.apiGateway.gatewayClassName -}}
{{- .Values.apiGateway.gatewayClassName -}}
{{- else -}}
{{- printf "%s-%s-apisix" (include "dws.namespace" .) (include "dws.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end }}

{{/*
Namespaced Gateway name — the one shared listener console and admin HTTPRoutes attach to.
*/}}
{{- define "dws.apiGateway.gatewayName" -}}
{{- include "dws.apiGateway.fullname" . -}}
{{- end }}

{{/*
GatewayProxy name the Gateway's infrastructure.parametersRef binds to.
  - Bundled mode (apisix.enabled=true): this chart creates its own release-qualified
    GatewayProxy (templates/api-gateway/gatewayproxy.yaml) bound to the bundled APISIX admin API.
  - External mode (apisix.enabled=false): apiGateway.external.gatewayProxyName MUST already name
    an existing, externally managed GatewayProxy in the release namespace.
*/}}
{{- define "dws.apiGateway.gatewayProxyName" -}}
{{- if .Values.apisix.enabled -}}
{{- printf "%s-proxy" (include "dws.apiGateway.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- required "apiGateway.external.gatewayProxyName is required when apiGateway.enabled=true and apisix.enabled=false: name an existing externally managed APISIX GatewayProxy in the release namespace" .Values.apiGateway.external.gatewayProxyName -}}
{{- end -}}
{{- end }}

{{/*
Validate the shared API Gateway's security/workload prerequisites and value shape before
rendering any Gateway API object. Called unconditionally from templates/preflight.yaml so a
misconfigured apiGateway fails render even if no api-gateway/*.yaml template happens to be the
first one Helm evaluates.
*/}}
{{- define "dws.apiGateway.validate" -}}
{{- if .Values.apiGateway.enabled -}}
{{- if not .Values.auth.enabled -}}
{{- fail "apiGateway.enabled=true requires auth.enabled=true: the shared admin HTTPRoute only forwards traffic that the Dapr sidecar's bearer middleware has already verified — without auth.enabled, admin requests would reach dws-admin ungated." -}}
{{- end -}}
{{- if not .Values.admin.enabled -}}
{{- fail "apiGateway.enabled=true requires admin.enabled=true: the Gateway's admin HTTPRoute has no dws-admin Service to target." -}}
{{- end -}}
{{- if not .Values.console.enabled -}}
{{- fail "apiGateway.enabled=true requires console.enabled=true: the Gateway's console HTTPRoute has no dws-console Service to target." -}}
{{- end -}}
{{- if not .Values.apiGateway.createGatewayClass -}}
{{- if not .Values.apiGateway.gatewayClassName -}}
{{- fail "apiGateway.createGatewayClass=false requires apiGateway.gatewayClassName naming an existing, operator-owned GatewayClass" -}}
{{- end -}}
{{- end -}}
{{- if not .Values.apisix.enabled -}}
{{- if not .Values.apiGateway.external.gatewayProxyName -}}
{{- fail "apiGateway.enabled=true with apisix.enabled=false requires apiGateway.external.gatewayProxyName naming an existing, externally managed APISIX GatewayProxy in the release namespace. Either set that value or set apisix.enabled=true to let this chart create its own." -}}
{{- end -}}
{{- end -}}
{{- if .Values.apiGateway.tls.enabled -}}
{{- if not .Values.apiGateway.tls.certificateName -}}
{{- fail "apiGateway.tls.enabled=true requires apiGateway.tls.certificateName naming an existing TLS Secret in the release namespace" -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end }}

{{/*
Deprecation trap for the removed console Ingress template (auth roadmap §2b). A prior
install's persisted console.ingress.enabled=true value must not be silently ignored now that
templates/console/ingress.yaml no longer exists — fail render/upgrade with explicit migration
steps onto the shared apiGateway front door instead. Called unconditionally from
templates/preflight.yaml, independent of apiGateway.enabled, so the trap fires even before the
operator has opted into the replacement.
*/}}
{{- define "dws.console.legacyIngress.validate" -}}
{{- if .Values.console.ingress.enabled -}}
{{- fail "console.ingress.enabled=true is no longer supported: the chart-owned console Ingress template was removed in favor of the shared Gateway API front door. Migrate by: (1) setting apiGateway.enabled=true (also requires auth.enabled=true, admin.enabled=true, console.enabled=true) and either apisix.enabled=true for a bundled APISIX or apiGateway.external.gatewayProxyName for an externally managed APISIX/Gateway API controller; (2) moving the old Ingress host to apiGateway.hostname and its TLS Secret to apiGateway.tls.enabled=true / apiGateway.tls.certificateName; (3) dropping the old console.ingress.className/annotations, which have no Gateway API equivalent; (4) updating the OIDC redirect URI (dex.consoleRedirectURI, or your external IdP client's registered redirect) to the new shared Gateway origin; then setting console.ingress.enabled=false. IMPORTANT for an EXISTING release like this one: apisix.enabled=true only self-bootstraps its CRDs/etcd on a brand-new `helm install` — enabling it via `helm upgrade` on a release that never had it deadlocks on the bundled etcd sub-chart's pre-upgrade hook. Use apiGateway.external.gatewayProxyName pointed at a separately installed APISIX release instead; see charts/dws/README.md#upgrading-from-a-pre-gateway-release for the verified recipe and scripts/verify-console-ingress-migration.sh for a runnable rehearsal." -}}
{{- end -}}
{{- end }}

{{/*
Resolve a Deployment container's resources by deep-merging the chart default with
the component override. This lets an operator override one request or limit without
repeating the rest of the common resource policy.
*/}}
{{- define "dws.component.resources" -}}
{{- $defaults := default dict .root.Values.defaults.resources -}}
{{- $component := default dict .component.resources -}}
{{- toYaml (mergeOverwrite (deepCopy $defaults) $component) -}}
{{- end }}

{{/*
Render common pod scheduling settings. nodeSelector and affinity are deep-merged;
tolerations are a list, so a component's non-empty list replaces the common list.
*/}}
{{- define "dws.component.scheduling" -}}
{{- $defaults := default dict .root.Values.defaults -}}
{{- $component := default dict .component -}}
{{- $nodeSelector := mergeOverwrite (deepCopy (default dict $defaults.nodeSelector)) (default dict $component.nodeSelector) -}}
{{- $affinity := mergeOverwrite (deepCopy (default dict $defaults.affinity)) (default dict $component.affinity) -}}
{{- if $nodeSelector }}
nodeSelector:
  {{- toYaml $nodeSelector | nindent 2 }}
{{- end }}
{{- if $component.tolerations }}
tolerations:
  {{- toYaml $component.tolerations | nindent 2 }}
{{- else if $defaults.tolerations }}
tolerations:
  {{- toYaml $defaults.tolerations | nindent 2 }}
{{- end }}
{{- if $affinity }}
affinity:
  {{- toYaml $affinity | nindent 2 }}
{{- end }}
{{- end }}

{{- define "dws.redis.host" -}}
{{- if .Values.redis.external.host }}
{{- .Values.redis.external.host }}
{{- else }}
{{- printf "%s-redis-master.%s.svc.cluster.local:6379" .Release.Name (include "dws.namespace" .) }}
{{- end }}
{{- end }}

{{- define "dws.redis.secretName" -}}
{{- if .Values.redis.external.host }}
{{- required "redis.external.existingSecret is required when redis.external.host is set" .Values.redis.external.existingSecret }}
{{- else }}
{{- printf "%s-redis" .Release.Name }}
{{- end }}
{{- end }}

{{- define "dws.redis.secretKey" -}}
{{- if .Values.redis.external.host }}
{{- default "redis-password" .Values.redis.external.existingSecretKey }}
{{- else }}
{{- print "redis-password" }}
{{- end }}
{{- end }}

{{/*
Observability (observability roadmap Phase 1) — release-safe names and shared value
normalization for the OpenTelemetry surface. Every helper below assumes its caller has already
gated on .Values.observability.enabled; none of them re-check the master switch, except
dws.observability.podAnnotations, which is included unconditionally from the Deployments.
*/}}

{{/*
Namespaced Instrumentation resource name — the value both Deployments' inject-* annotations
point at, so the name has exactly one definition.
*/}}
{{- define "dws.observability.instrumentationName" -}}
{{- printf "%s-instrumentation" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
Chart-owned OTLP header Secret name. Only rendered when observability.otlp.headers is
non-empty AND observability.otlp.existingSecret is unset — see dws.observability.otlpSecretName
for the resolved reference.
*/}}
{{- define "dws.observability.otlpSecretFullname" -}}
{{- printf "%s-otlp" (include "dws.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
Resolve WHICH Secret supplies OTEL_EXPORTER_OTLP_HEADERS, or the empty string when headers are
not configured at all. existingSecret wins over inline headers, matching the precedence the
admin database Secret already uses (templates/admin/deployment.yaml). The key is always
`headers`; that single-key contract is what the chart documents to operators.
*/}}
{{- define "dws.observability.otlpSecretName" -}}
{{- if .Values.observability.otlp.existingSecret -}}
{{- .Values.observability.otlp.existingSecret -}}
{{- else if .Values.observability.otlp.headers -}}
{{- include "dws.observability.otlpSecretFullname" . -}}
{{- end -}}
{{- end }}

{{/*
Serialize observability.otlp.headers into the one comma-separated key=value list the
OTEL_EXPORTER_OTLP_HEADERS environment variable expects. Sorted by key (Helm's `keys` is
unordered) so the rendered Secret is deterministic across renders and does not churn the
release on every upgrade.
*/}}
{{- define "dws.observability.otlpHeaderList" -}}
{{- $pairs := list -}}
{{- range $key := (keys .Values.observability.otlp.headers | sortAlpha) -}}
{{- $value := get $.Values.observability.otlp.headers $key | toString -}}
{{- /*
  The list separator is a bare comma with no escaping mechanism in the OTLP environment-variable
  format, so a comma anywhere in a name or value silently splits into extra bogus headers rather
  than failing. Reject it here instead. Use observability.otlp.existingSecret for any header the
  format cannot represent.
*/ -}}
{{- if or (contains "," $key) (contains "," $value) -}}
{{- fail (printf "observability.otlp.headers: header %q contains a comma, which OTEL_EXPORTER_OTLP_HEADERS uses as its list separator and cannot escape. Supply this header through observability.otlp.existingSecret instead." $key) -}}
{{- end -}}
{{- $pairs = append $pairs (printf "%s=%s" $key $value) -}}
{{- end -}}
{{- join "," $pairs -}}
{{- end }}

{{/*
Map the public OTLP protocol value onto Dapr's own, narrower transport vocabulary. Dapr's
`tracing.otel.protocol` accepts only "http" or "grpc"; the OTel SDK/agent value is
"http/protobuf" or "grpc". Centralized here so the controller and admin Configurations cannot
disagree about the mapping.
*/}}
{{- define "dws.observability.daprOtelProtocol" -}}
{{- $protocol := .Values.observability.otlp.protocol | default "http/protobuf" -}}
{{- if eq $protocol "grpc" -}}
grpc
{{- else if eq $protocol "http/protobuf" -}}
http
{{- else -}}
{{- fail (printf "observability.otlp.protocol must be \"http/protobuf\" or \"grpc\", got %q" $protocol) -}}
{{- end -}}
{{- end }}

{{/*
Derive Dapr's `tracing.otel.isSecure` from the configured endpoint's scheme. Dapr needs an
explicit boolean, but the roadmap's operator-facing values table has no separate TLS toggle —
the endpoint URL already carries the answer, so read it from there rather than adding a value
that could drift out of sync with the endpoint it describes.
*/}}
{{- define "dws.observability.otlpIsSecure" -}}
{{- if hasPrefix "https://" (.Values.observability.otlp.endpoint | default "") -}}
true
{{- else -}}
false
{{- end -}}
{{- end }}

{{/*
Dapr's `tracing.otel.endpointAddress` is NOT the same shape as the OTel SDK's endpoint value.
Dapr passes the field verbatim to otlptracehttp.WithEndpoint / otlptracegrpc.WithEndpoint
(dapr/dapr pkg/runtime/runtime.go), and both expect a bare `host:port` — which is exactly why
Dapr carries a separate `isSecure` boolean instead of reading the scheme. Leaving the scheme on
produces a URL like `http://http://collector:4318/v1/traces` and every sidecar export fails at
runtime, with nothing in the CRD schema (plain `type: string`) or `helm lint` to catch it.

observability.otlp.endpoint stays scheme-qualified because the Instrumentation resource's
`spec.exporter.endpoint` becomes OTEL_EXPORTER_OTLP_ENDPOINT, which REQUIRES the scheme. Strip
it only on the way into Dapr. The scheme is not lost — dws.observability.otlpIsSecure reads it.
*/}}
{{- define "dws.observability.daprOtelEndpoint" -}}
{{- .Values.observability.otlp.endpoint | default "" | trimPrefix "https://" | trimPrefix "http://" | trimSuffix "/" -}}
{{- end }}

{{/*
Per-component Dapr tracing block, shared by templates/controller/configuration.yaml and
templates/admin/configuration.yaml so the two cannot drift. Emits the `tracing:` key itself at
the caller's indentation; the caller gates it on .Values.observability.enabled.

ADR 0005 Decision 2: samplingRate is the LITERAL string "1", never
observability.traces.samplingRate. The application agent's parentbased_traceidratio sampler is
the single sampling root; if daprd also sampled probabilistically the two decisions would
compose multiplicatively and produce partial traces that look like exporter failures.
*/}}
{{- define "dws.observability.daprTracing" -}}
tracing:
  # ADR 0005 Decision 2 — pinned to "1" on purpose. The application agent
  # (parentbased_traceidratio, observability.traces.samplingRate) is the only sampling
  # root; daprd must honour the parent decision, not make a second one.
  samplingRate: "1"
  otel:
    endpointAddress: {{ include "dws.observability.daprOtelEndpoint" . | quote }}
    isSecure: {{ include "dws.observability.otlpIsSecure" . }}
    protocol: {{ include "dws.observability.daprOtelProtocol" . | quote }}
{{- end }}

{{/*
OpenTelemetry Operator injection annotations for one pod template. Takes a dict
`{root: $, language: "java"|"nodejs"|"dotnet", container: "<app container name>"}`.

Emits nothing when observability is disabled, so the Deployments can include it
unconditionally and keep their annotation blocks readable.

container-names is MANDATORY, not an optimization: an untargeted inject-* annotation makes the
Operator's webhook instrument EVERY container in the pod, including the daprd sidecar — a Go
binary that cannot take a Java or Node.js agent. Both call sites therefore name exactly the one
application container.
*/}}
{{- define "dws.observability.podAnnotations" -}}
{{- $ := .root -}}
{{- if $.Values.observability.enabled -}}
instrumentation.opentelemetry.io/inject-{{ .language }}: {{ include "dws.observability.instrumentationName" $ | quote }}
instrumentation.opentelemetry.io/container-names: {{ .container | quote }}
{{- end -}}
{{- end }}
