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

{{- define "dws.auth.componentName" -}}
{{- printf "%s-auth" (include "dws.controller.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{- define "dws.auth.configName" -}}
{{- printf "%s-config" (include "dws.controller.fullname" .) | trunc 63 | trimSuffix "-" -}}
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
