# Chart render fixtures

## `default-render-baseline.yaml`

The complete default `helm template` output captured from the chart **immediately before**
the observability Phase 1 change (`openspec/changes/observability-template`). It exists so
`tests/observability-render-test.sh` can prove that `observability.enabled=false` — the
default — leaves the rendered manifest byte-identical to the pre-change chart.

It is regenerated with:

```sh
# The Helm version pinned as HELM_VERSION in .github/workflows/helm.yml is required (currently 4.3.0).
bash charts/dws/tests/observability-render-test.sh charts/dws --update-baseline
```

Two Bitnami-generated Postgres passwords are pinned on the command line
(`postgresql.auth.password`, `postgresql.auth.postgresPassword`) because the subchart
generates them randomly on every render; nothing else about the render is overridden.

Regenerate this file **only** when a chart change is deliberately meant to alter the default
render (including a `Chart.yaml` `version`/`appVersion` bump, which lands in every resource's
`helm.sh/chart` and `app.kubernetes.io/version` labels), and review the resulting diff.
The test rejects other Helm versions so a fixture generated locally cannot diverge from CI's
byte-for-byte comparison.

## `otel-collector-dev.yaml`

A dev/eval receiver for verifying observability against a live cluster: an OpenTelemetry
Collector, Jaeger all-in-one for traces, and a single-binary Loki (pinned `grafana/loki:3.7.9`)
for log lines. Not part of the chart and not production scaffolding: the chart deliberately ships
no collector (`observability.collector.enabled` is reserved for Phase 6 and currently does nothing).

```sh
kubectl apply -n <release-namespace> -f charts/dws/tests/fixtures/otel-collector-dev.yaml
kubectl port-forward -n <release-namespace> svc/jaeger 16686:16686   # -> http://localhost:16686
kubectl port-forward -n <release-namespace> svc/loki 3100:3100
# log lines of one trace (trace_id is Loki structured metadata, not a label):
curl -G localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={k8s_namespace_name="<release-namespace>"} | trace_id="<32-hex trace id>"'
```

The Collector Service is named `dws-otel-collector` on purpose: that is what the chart's
default `observability.otlp.endpoint` resolves to, so a release in the same namespace needs no
endpoint override.

Jaeger renders the trace tree: the deliverable is one connected trace across
controller -> Dapr -> admin -> Postgres, and parentage is the part that has to be proven. Loki
answers "which log lines belong to this trace". The Collector's `debug` exporter also prints
metrics and logs, which Jaeger does not accept. Storage is in-memory (Jaeger) or an `emptyDir`
(Loki); a pod restart loses everything, which is the intent.

### The `filelog` receiver

Logs have a single path: component stdout (`observability.logs.format=json`,
[`docs/log-format.md`](../../../../docs/log-format.md)) -> node log files -> the Collector's `filelog`
receiver -> the logs pipeline, whose exporters are `debug` and `otlphttp/loki`. The agents' own
OTLP log export is off (`OTEL_LOGS_EXPORTER=none`), so nothing arrives twice. The receiver:

- reads only the namespace the fixture is applied into (`${env:K8S_NAMESPACE}`, from the downward
  API), not every pod on the node;
- excludes the `daprd` sidecar (different log schema) and the collector's own container (its
  `debug` exporter prints every record to stdout, so reading it back is a feedback loop), plus the
  `jaeger` and `loki` containers, which are not DWS components;
- runs `container` (CRI framing, `k8s.*` resource attributes), then `json_parser` only on a body
  that starts with `{`, then `trace_parser` for `trace_id`/`span_id`, then moves `message` to the
  body and `logger` to the `logger.name` attribute. A non-JSON line keeps its raw body and carries no
  trace context.

Loki needs `limits_config.allow_structured_metadata: true` for its native OTLP endpoint
(`/otlp`); without it the push is rejected.

`tests/observability-render-test.sh` reads this file and fails if the exclusions or the
trace-context operators go missing, or if the chart README's reference `filelog` block stops
matching the receiver here. Edit the two together.

## Packaging

`charts/dws/.helmignore` excludes `/tests/`, so nothing in this directory ships in the packaged
chart. The render tests run from a repo checkout in `.github/workflows/helm.yml`. The leading
slash is load-bearing — a bare `tests/` would also exclude `templates/tests/`, the real
`helm test` hooks.
