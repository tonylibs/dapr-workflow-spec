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
