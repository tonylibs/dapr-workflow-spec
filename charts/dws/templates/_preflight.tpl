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
