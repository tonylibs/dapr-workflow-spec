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
Create the name of the service account to use
*/}}
{{- define "dws.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "dws.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
