# Chart render fixtures

## `default-render-baseline.yaml`

The complete default `helm template` output captured from the chart **immediately before**
the observability Phase 1 change (`openspec/changes/observability-template`). It exists so
`tests/observability-render-test.sh` can prove that `observability.enabled=false` — the
default — leaves the rendered manifest byte-identical to the pre-change chart.

It is regenerated with:

```sh
bash charts/dws/tests/observability-render-test.sh charts/dws --update-baseline
```

Two Bitnami-generated Postgres passwords are pinned on the command line
(`postgresql.auth.password`, `postgresql.auth.postgresPassword`) because the subchart
generates them randomly on every render; nothing else about the render is overridden.

Regenerate this file **only** when a chart change is deliberately meant to alter the default
render (including a `Chart.yaml` `version`/`appVersion` bump, which lands in every resource's
`helm.sh/chart` and `app.kubernetes.io/version` labels), and review the resulting diff.

## `otel-collector-dev.yaml`

A dev/eval OTLP receiver — an OpenTelemetry Collector plus Jaeger all-in-one — for verifying
observability Phase 1 against a live cluster. Not part of the chart and not production
scaffolding: Phase 1 deliberately ships no collector (`observability.collector.enabled` is
reserved for Phase 6 and currently does nothing).

```sh
kubectl apply -n <release-namespace> -f charts/dws/tests/fixtures/otel-collector-dev.yaml
kubectl port-forward -n <release-namespace> svc/jaeger 16686:16686
# -> http://localhost:16686
```

The Collector Service is named `dws-otel-collector` on purpose: that is what the chart's
default `observability.otlp.endpoint` resolves to, so a release in the same namespace needs no
endpoint override.

Jaeger renders the trace tree — Phase 1's deliverable is one connected trace across
controller → Dapr → admin → Postgres, and parentage is the part that has to be proven. The
Collector's `debug` exporter covers metrics and logs, which Phase 1 also requires and which
Jaeger does not accept. Storage is in-memory; a pod restart loses everything, which is the
intent.

## Packaging

`charts/dws/.helmignore` excludes `/tests/`, so nothing in this directory ships in the packaged
chart. The render tests run from a repo checkout in `.github/workflows/helm.yml`. The leading
slash is load-bearing — a bare `tests/` would also exclude `templates/tests/`, the real
`helm test` hooks.
