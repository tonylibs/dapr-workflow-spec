# Observability Phase 6 — live evidence

Notion task: "Observability Phase 6 — Bundled collector" (last acceptance criterion)  
Live run: 2026-11-20, 21:03–21:13 UTC  
Chart: `charts/dws` 1.1.1 from `main` at `4750122`, Helm 4.3.0  
Cluster: Docker Desktop kind, Kubernetes v1.36.1, three nodes · Namespace: `dws-p6` · Release: `dws`  
Collector: the bundled subchart (`opentelemetry-collector` 0.179.1, `otel/opentelemetry-collector-contrib:0.163.2`), DaemonSet on both workers  
Trace: `8cffb573ff0214ddfc075880cf4b41d4`

**Result in one line:** with `observability.collector.enabled=true` and the OTLP endpoint left at
its default, the controller, admin and an instrumented compiled orchestrator all sent spans and
metrics to the bundled collector. Their JSON stdout lines arrived through `filelog` with the trace
context lifted into the log record, and the orchestrator's activity line carried the same trace and
span IDs as the exported activity span. Everything was read from the collector's `debug` output, with
no backend involved.

This closes the criterion the [Phase 6 findings entry](observability.md#2026-11-18--phase-6-bundled-collector-landed)
left open: the 2026-11-18 run had shown only a synthetic emitter, because the first-party images
could not be pulled.

## Setup

| Item | Value |
|---|---|
| Pre-installed | Dapr 1.19.1 (`dapr.enabled=false`), Knative Serving 1.24.0 with Kourier, OpenTelemetry Operator 0.163.0, cert-manager |
| Values | `observability.enabled=true`, `observability.collector.enabled=true`, `observability.traces.samplingRate=1`, `observability.otlp.endpoint` **not set** (default `http://dws-otel-collector:4318`), `observability.logs.format` not set (`json`) |
| Redis | `redis:7.4-alpine` in the namespace through `redis.external.*` (the chart's Redis is tied to `dapr.enabled`) |
| Controller store key | `observability.enabled` = `true` in `dws-controller-config`, read back through the controller sidecar's configuration API |

Images, all `ghcr.io/tonylibs/<name>:main`. Each one is built from the latest commit that touched
its package, so it matches `4750122`'s source for that package:

| Image | Digest | Source revision |
|---|---|---|
| `dws-controller` | `sha256:f825f5af…dddd` | `d418c01` (includes the jsii package-cache fix, `8df833a`) |
| `dws-orchestrator` | `sha256:0d3a7146…679c` | `a1f3868` |
| `dws-admin` | `sha256:5b39a60d…2b02` | `33d5378` |
| `dws-schedule-time` | `sha256:cb2eeaa5…4485` | `9e26f94` |
| `dws-call-http` | `sha256:d81ab9d8…03dc` | `116684c` |

## Run

| Instance | Workflow | Tasks | Status |
|---|---|---|---|
| `p6-run-1` | `p6demo@ve41aaf76` | `fetchHealth` (`call: http`, admin `/health`) → `announce` (`emit`) | `COMPLETED`; output is admin's health response |

The workflow was submitted with `POST /workflows` (HTTP 201). The instance was started with the
orchestrator's `POST /workflows/p6demo/instances?instanceId=p6-run-1` (HTTP 202). The `emit` task is
there for the same reason as in Phase 4: it is the orchestrator activity that logs at `INFO` inside
its span (see [Phase 4 evidence, "Why an `emit` task"](observability-phase4-evidence.md#why-an-emit-task)).

The orchestrator pod carried `instrumentation.opentelemetry.io/inject-java: "true"`,
`container-names: orchestrator`, `dapr.io/config: p6demo-ve41aaf76-cfg` (tracing
`endpointAddress: dws-otel-collector:4318`, `samplingRate: "1"`), the Java agent init container,
`OTEL_SERVICE_NAME=p6demo`, `DWS_LOG_FORMAT=json` and
`OTEL_EXPORTER_OTLP_ENDPOINT=http://dws-otel-collector:4318`. The controller and admin pods had their
Java and Node.js agents with the same endpoint and `OTEL_LOGS_EXPORTER=none`. `dws-controller` was
not changed for any of this: the chart's default endpoint and the controller's existing stamping are
enough.

The Service `dws-otel-collector` (4317/4318) had one endpoint per worker node
(`internalTrafficPolicy: Local`, finding 5 of the landing entry).

## Collector `debug` output

Read with `kubectl logs` from both collector pods (16,353 and 27,382 lines) and summarised with a
throwaway parser of the `verbosity: detailed` format.

### Spans

| Service | Spans in trace `8cffb573…41d4` | Spans in total |
|---|---|---|
| `p6demo` (orchestrator agent and its `daprd`) | 49 | 77 |
| `dws-admin` (the six lifecycle events over pub/sub) | 102 | 632 |
| `dws-controller` | — | 128 |

The controller's spans belong to the deploy request: trace `9b349d39eee720a06566dc5fb6fee273` has 52
spans in the collector. The `p6demo` spans in the run trace include `POST /workflows/{name}/instances`,
`StartInstance`/`CompleteActivityTask` on both the agent and the sidecar side, six
`activity:…AdminEventActivity` and one `activity:…EmitEventActivity`:

```text
Span #4
    Trace ID       : 8cffb573ff0214ddfc075880cf4b41d4
    Parent ID      : 1bf4b38c52c5ab64
    ID             : f566a69c14fe80ae
    Name           : activity:io.dws.orchestrator.workflow.activity.EmitEventActivity
    Kind           : Internal
    Start time     : 2026-11-20 21:11:05.10269754 +0000 UTC
    End time       : 2026-11-20 21:11:05.112538243 +0000 UTC
Attributes:
     -> durabletask.task.instance_id: Str(p6-run-1)
     -> durabletask.task.id: Int(5)
```

### Metrics

| Service | Metric names | Examples |
|---|---|---|
| `p6demo` | 20 | `http.server.request.duration`, `jvm.memory.used` |
| `dws-controller` | 21 | `http.server.request.duration`, `http.client.request.duration`, `jvm.memory.used` |
| `dws-admin` | 17 | `http.server.request.duration`, `http.client.request.duration` |

The `p6demo` metrics carry the controller-stamped resource attributes (`dws.workflow.name`,
`dws.workflow.version`, `k8s.deployment.name`).

### Logs

`filelog` read every `dws-p6` pod except the collector's own container and the `daprd` containers,
which is what the exclusions say. Log records carrying a trace ID:

| Source container | Records with a trace ID |
|---|---|
| `p6demo-ve41aaf76-…` / `orchestrator` | 4 |
| `dws-controller-…` / `controller` | 5 (trace `9b349d39…`) |
| `dws-schedule-time-…` / `schedule-time` | 558 (see [observations](#observations)) |

The orchestrator's `emit` line, with the trace context moved from the JSON into the record's
Trace ID and Span ID fields:

```text
ResourceLog #0
Resource attributes:
     -> k8s.pod.name: Str(p6demo-ve41aaf76-67657f885f-blnd2)
     -> k8s.container.name: Str(orchestrator)
     -> k8s.namespace.name: Str(dws-p6)
ScopeLogs #0
InstrumentationScope github.com/open-telemetry/opentelemetry-collector-contrib/receiver/filelogreceiver 0.163.2
LogRecord #1
Timestamp: 2026-11-20 21:11:05.104 +0000 UTC
SeverityText: INFO
Body: Str(Publishing to pubsub 'pubsub' topic 'announce')
Attributes:
     -> logger.name: Str(io.dws.orchestrator.workflow.activity.EmitEventActivity)
     -> log.iostream: Str(stdout)
Trace ID: 8cffb573ff0214ddfc075880cf4b41d4
Span ID: f566a69c14fe80ae
```

Its Span ID is the `EmitEventActivity` span above. So one collector carries a span, the metrics of
the same process and the log line written inside that span, joined by IDs.

## Deviations from a default install

- **Image pull secret on service accounts.** The `ghcr.io/tonylibs/*` packages are private, and
  GHCR accepts only a classic personal access token (`read:packages`), not a fine-grained one. A
  `kubernetes.io/dockerconfigjson` Secret was added to the `default`, `dws-controller`,
  `dws-schedule-time` and `dws-orchestrator` service accounts by hand, and the controller and
  scheduler pods were recreated once. See observation 2 for why `imagePullSecrets` was not enough.
- **`main` images instead of the chart's pinned tags.** See observation 1.
- **Dapr 1.19.1, OTel Operator 0.163.0** were already installed. The chart pins Dapr 1.18.7 for its own
  install, and the README names Operator 0.159.0.

## Observations

1. **8 of the 13 pinned first-party image tags do not exist**
   ([#205](https://github.com/tonylibs/dapr-workflow-spec/issues/205)). This run hit two of them:
   `controller.images.callHttp` (`dws-call-http:0.2.0`) and `scheduleTime.image`
   (`dws-schedule-time:0.1.0`). A check of every pin afterwards found `dws-call-openapi`,
   `dws-call-grpc`, `dws-call-asyncapi` and the three `dws-run-*` images missing too. Each package
   has its `<component>-v<version>` git tag, but no tag build ever ran, so only `main`/`latest`/SHA
   images exist. A chart-default install cannot pull them even with credentials.
   `dws-controller:1.0.0`, `dws-orchestrator:0.1.0` and `dws-admin:0.2.1` exist but are far behind
   the packages' release-please versions (1.4.0, 0.3.0, 0.4.0).
2. **`imagePullSecrets` reaches only admin and console**
   ([#207](https://github.com/tonylibs/dapr-workflow-spec/issues/207)). The value is wired into
   `templates/admin/deployment.yaml` and `templates/console/deployment.yaml` only. The controller and
   `dws-schedule-time` Deployments ignore it, and the orchestrator Deployments and Knative step
   Services the controller generates have no way to get it.
3. **`dws-schedule-time` logs every `/healthz` probe at `INFO`, with a trace ID**
   ([#206](https://github.com/tonylibs/dapr-workflow-spec/issues/206)). Each probe writes several
   lines (request start, endpoint, result, finish), which made 558 trace-carrying records in about
   nine minutes. The service has no OTLP endpoint
   (landing finding 6), so these IDs come from ASP.NET's own activities and match no exported
   span. That is noise in the log pipeline rather than a correlation defect, but a query by
   trace ID will never find their traces.
4. The step pod (`fetch-health`, `dws-call-http`) sent no spans: step images are Track B (2b/3),
   as expected. Its `user-container` and `queue-proxy` lines were ingested as plain text.

## Reproducing

1. A cluster with Dapr, Knative Serving, cert-manager and the OpenTelemetry Operator.
2. A GHCR pull Secret from a classic `read:packages` token, attached to the namespace's `default`
   service account and, after the install creates them, to `dws-controller`, `dws-schedule-time` and
   `dws-orchestrator` (or use locally built images).
3. Install with `dapr.enabled=false`, `redis.external.*`, `observability.enabled=true`,
   `observability.collector.enabled=true`, `observability.traces.samplingRate=1`, and the five images
   above at `main`.
4. `SET observability.enabled "true||1"` in the Redis behind `dws-controller-config`.
5. `POST /workflows` with a `call: http` → `emit` workflow, then start one instance.
6. `kubectl logs -n <ns> -l app.kubernetes.io/name=opentelemetry-collector` on every collector pod,
   and find the orchestrator's `EmitEventActivity` record and the span with the same Span ID.

## Cleanup

The `dws` release was uninstalled and the `dws-p6` namespace deleted, including the Redis pod and
the GHCR pull Secret. The chart creates no cluster-scoped resources. Dapr, Knative, cert-manager and
the OTel Operator were left in place.
