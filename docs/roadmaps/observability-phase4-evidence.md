# Observability Phase 4 — live evidence

Change: `observability-phase4-log-trace-correlation`  
Live run: 2026-10-31, 19:08–19:21 UTC  
Built from: branch `claude/beautiful-cori-mdvp04` at `474e48f` (images tagged `p4-474e48f`)  
Cluster: Docker Desktop kind, Kubernetes v1.34.3, three nodes · Namespace: `dws-p4`  
Backends: collector-contrib 0.115.1 (`filelog` → Loki), Jaeger 1.62.0, Loki 3.7.9 (fixture
`charts/dws/tests/fixtures/otel-collector-dev.yaml`)  
Trace: `191da925703700db0675fc0b538fbe9f`

**Result in one line:** with the chart defaults (`observability.logs.format` unset → `json`),
controller, admin and the instrumented orchestrator all ran with `DWS_LOG_FORMAT=json`. Every line
on **stdout** was one JSON object. Querying Loki by the Jaeger trace ID returned exactly the
orchestrator's activity-span line, with the matching `span_id`, once and with no secret. Every
non-JSON line came from outside the logging systems and was on **stderr**.

Read the [deviations](#deviations-from-the-procedure) and the [defects](#defects-found-on-the-way)
before treating this as a production-shaped run.

## Run

| Instance | Workflow | Tasks | Status | Trace |
|---|---|---|---|---|
| `p4-run-1` | `logdemo@vd74cd9fe` | `fetch` (`call: http`) | `COMPLETED` | not captured: the collector was not yet running |
| `p4-run-2` | `logdemo2@v653d6b64` | `fetchHealth` (`call: http`) → `announce` (`emit`) | `COMPLETED` 19:17:28.17 → 19:17:28.91 | `191da925703700db0675fc0b538fbe9f` |

Both `call: http` steps ran on real Knative Revisions (`fetch-00001`, `fetch-health-00001`) using the
`dws-call-http` image built from this commit. Each called `dws-admin`'s `/health` endpoint inside the
cluster, and the instance output is that response. Instances were started through the orchestrator
sidecar's Dapr workflow API (`POST /v1.0/workflows/dapr/<workflow>/start`); the controller has no
start endpoint.

`logdemo2` exists because of the [`emit` task](#why-an-emit-task). It is the run the rest of this
file describes.

## Pod env and `jq` (task 9.1)

Read with `kubectl exec … -- env` on the running containers:

| Container | `DWS_LOG_FORMAT` | `OTEL_SERVICE_NAME` | `OTEL_LOGS_EXPORTER` | Sampler |
|---|---|---|---|---|
| `dws-controller` / `controller` | `json` (chart, `observability.logs.format` default) | `dws-controller` | `none` | `parentbased_traceidratio`, `1` |
| `dws-admin` / `admin` | `json` (chart) | `dws-admin` | `none` | `parentbased_traceidratio`, `1` |
| `logdemo2-v653d6b64` / `orchestrator` | `json` (stamped by the controller) | `logdemo2` | `none` | `parentbased_traceidratio`, `1` |

The orchestrator pod carried `instrumentation.opentelemetry.io/inject-java: "true"`,
`container-names: orchestrator`, `dapr.io/config: logdemo2-v653d6b64-cfg` (tracing
`endpointAddress: dws-otel-collector:4318`, `samplingRate: "1"`) and the
`autoinstrumentation-java:2.31.1` init container.

`kubectl logs` of each container, every line piped through `jq -e .`:

| Container | Lines | Parse with `jq -e .` | The rest |
|---|---|---|---|
| `admin` | 35 | **35** | none |
| `controller` (pod after the collector came up) | 8 | **3** (all Quarkus lines) | 5: `run-java.sh` launcher (`INFO exec -a "java" …`, `INFO running in /deployments`), JVM (`Picked up JAVA_TOOL_OPTIONS`, CDS warning), OTel agent version banner |
| `orchestrator` (`logdemo2`) | 121 | **50** (every Logback line) | 71: JVM, OTel agent banner, netty `WARNING: sun.misc.Unsafe …` ×4, and the agent's own OTLP export-failure stack traces from before the collector was running |
| `orchestrator` (`logdemo`, restarted after the collector came up) | 40 | **33** | 7: JVM, agent banner, netty `sun.misc.Unsafe` |

Every line that fails `jq` comes from outside the logging system: the image launcher script, the
JVM, the OTel Java agent's own logger, or netty's JVM-level warning. That is what `design.md` D7
expects. So **`kubectl logs | jq -e .` on the whole stream fails for both Java packages**.
`jq -R 'fromjson? // .'` handles it.

**These lines are all on stderr.** The collector records each line's CRI stream as `log_iostream`.
For pods started after the collector came up, Loki shows:

| Pod / container | stdout, parsed as JSON | stderr, kept raw |
|---|---|---|
| `dws-controller-6cfdf446f5-p52qf` / `controller` | 3 | 5 |
| `logdemo-vd74cd9fe-64bbccb869-dscnh` / `orchestrator` | all its Logback lines | 7 |

So "nothing else on stdout" holds literally, and a consumer that reads only stdout gets clean JSON.
`kubectl logs` merges both streams, which is why a whole-stream `jq` fails.

## Trace in Jaeger

`GET /api/traces/191da925703700db0675fc0b538fbe9f`: **146 spans**, 44 from service `logdemo2` and
102 from `dws-admin`, which received the six lifecycle events over pub/sub. The service list was
`dws-controller`, `dws-admin`, `logdemo2`. The `logdemo2` side:

```text
create_orchestration||logdemo2                                   1
orchestration||logdemo2                                          1
activity||…AdminEventActivity  /  activity:…AdminEventActivity   6 / 6
activity||…EmitEventActivity   /  activity:…EmitEventActivity    1 / 1
Dapr/PublishEvent (agent + daprd)                                7 + 7
TaskHubSidecarService/CompleteActivityTask (agent + daprd)       7 + 7
```

Span `314609ad8d112a91` is `activity:io.dws.orchestrator.workflow.activity.EmitEventActivity`. Its
ancestry is `activity||…EmitEventActivity` ← `orchestration||logdemo2` ←
`create_orchestration||logdemo2`. As in Phase 2a, there is no span for the `fetchHealth` step call
itself: the step sidecar has no tracing configuration (Track B).

## Logs by trace ID in Loki (task 9.2)

Query (LogQL on structured metadata), range 19:00 UTC to now:

```logql
{k8s_namespace_name="dws-p4"} | trace_id="191da925703700db0675fc0b538fbe9f"
```

Output: **one stream, one line**:

```text
line:   Publishing to pubsub 'pubsub' topic 'announce'
stream: k8s_namespace_name=dws-p4  k8s_pod_name=logdemo2-v653d6b64-fb5cdd76d-kjbst
        k8s_container_name=orchestrator  k8s_container_restart_count=0
        service_name=orchestrator  log_iostream=stdout  logtag=F  log_file_name=0.log
        trace_id=191da925703700db0675fc0b538fbe9f  span_id=314609ad8d112a91
        severity_text=INFO  severity_number=9  detected_level=info
        logger_name=io.dws.orchestrator.workflow.activity.EmitEventActivity
```

The same line, as written to stdout by the orchestrator:

```json
{"timestamp":"2026-10-31T19:17:28.786Z","severity":"INFO","logger":"io.dws.orchestrator.workflow.activity.EmitEventActivity","message":"Publishing to pubsub 'pubsub' topic 'announce'","trace_id":"191da925703700db0675fc0b538fbe9f","span_id":"314609ad8d112a91"}
```

| Check | Result |
|---|---|
| Orchestrator activity-span line returned | yes, from `activity:…EmitEventActivity` |
| `span_id` matches the Jaeger span | yes, `314609ad8d112a91` |
| Duplicates | none (`sort \| uniq -d` over the returned bodies: 0) |
| Bearer token / `SECRET_*` / `Authorization` | none (`grep -i -E 'bearer\|SECRET_\|authorization'` over bodies and metadata: 0) |
| Lines in `kubectl logs` with this trace ID | orchestrator 1 and admin 0, so Loki returned everything there was |

The trace's six `AdminEventActivity` spans have no log line because that activity logs only when a
publish fails. `dws-admin` wrote no line during its pub/sub deliveries. Neither is a gap in
correlation: there was nothing to correlate.

### Why an `emit` task

In a successful run, the only orchestrator code that logs inside an activity span at `INFO` is
`EmitEventActivity` and `CallServiceActivity`. `call: http` runs in the step pod, not in an
orchestrator activity, and `AdminEventActivity` is silent on success. `logdemo` (`call: http` only)
therefore produced no trace-correlated orchestrator line at all. `logdemo2` adds an `emit` so that
task 9.2 has a line to find.

## Collector receiver behaviour (verify warning W1)

Pods started after the collector came up:

| Spec scenario | Observed |
|---|---|
| JSON line becomes a trace-correlated record | The record body is `message`. `severity_text`/`severity_number` come from `severity`, `logger_name` from `logger`, `trace_id`/`span_id` from the JSON, and the record timestamp is the JSON `timestamp` (Loki entry `…335431000000` = `19:18:55.431Z`, the line's own value) |
| Non-JSON line is kept | `WARNING: sun.misc.Unsafe::allocateMemory has been called by …` arrived with its raw body, `log_iostream=stderr`, and no `trace_id`, `span_id` or `severity_text` |
| Logs are retrievable by trace ID | see above |
| Loki version / OTLP endpoint (design D6 open question) | `grafana/loki:3.7.9` accepted `otlphttp` at `http://loki:3100/otlp`, and `trace_id`/`span_id` became queryable structured metadata |

## Defects found on the way

None of these concerns log format. Each was worked around **in the live namespace only**; the
repository is unchanged.

| # | Symptom | Cause (as observed) | Live workaround |
|---|---|---|---|
| 1 | `dws-controller` and `dws-admin` sidecars crash-loop: `Secret "dws-redis" not found` | with `dapr.enabled=false` (Dapr pre-installed) the bundled Redis is not installed, but the three Redis Components still default to it | a throwaway `redis:7-alpine` in the namespace plus `redis.external.host`/`existingSecret`, as `scripts/verify-gateway-sse-path.sh` does |
| 2 | `POST /workflows` → 500, `JsiiError: Cannot use the jsii package cache in /home/default/.cache/aws/jsii/package-cache … /home/default … is writable by group or others (mode 0770)` | the controller image's home directory mode makes jsii refuse its package cache | `JSII_RUNTIME_PACKAGE_CACHE=disabled` on the controller Deployment |
| 3 | Re-posting an already-deployed workflow → 500: `admission webhook "validation.webhook.serving.knative.dev" denied the request: … annotation value is immutable: metadata.annotations.serving.knative.dev/creator` | the controller `PUT`s the step KService without the creator annotation Knative set on create | deleted the KService, then re-posted |

## Deviations from the procedure

- **Collector as a DaemonSet.** The fixture runs one collector replica as a Deployment, and the
  `filelog` receiver reads only its own node's `/var/log/pods`. On this three-node cluster it would
  have missed most pods, so the run applied the fixture with `kind: DaemonSet` and no `replicas`;
  nothing else changed. The control-plane node is tainted and ran no workload or collector. The
  fixture's single-replica Deployment is only correct on a one-node cluster.
- **Images built locally** from `474e48f` (`dws-controller`, `dws-orchestrator`, `dws-admin`,
  `dws-call-http`), not pulled from `ghcr.io`. The controller was built in `maven:3.9-eclipse-temurin-25`
  because the Windows `mvnw` path failed. Knative resolves image tags to digests against a registry,
  so the step image was retagged `dev.local/dws-call-http:p4-474e48f` (a prefix in Knative's default
  skip list) and `controller.images.callHttp.repository` pointed at it.
- **Dapr 1.18.2 pre-installed** (`dapr.enabled=false`), with Redis from defect 1's workaround. The
  chart pins Dapr 1.18.7 for its own install.
- **Knative Serving 1.21.2 without an ingress layer.** `net-istio` was removed before the run because
  Istio was not installed. KServices never report `Ready` (no ingress), but Revisions deploy and
  Dapr invokes the step pods directly, so the steps ran.
- **Filelog starts at the end of each file** (`start_at: end`). Lines written before the collector
  started are not in Loki. That is why the receiver-behaviour rows use the `logdemo` orchestrator
  and the controller, both restarted after the collector came up, and why `p4-run-1` has no trace.
- **Helm 4.2.4** locally; CI pins 4.3.0.
- The OTel Operator pod needed one restart after the cluster started: it had come up before the pod
  network could reach the API server and exited with "OpenTelemetryCollector CRDs are not present".

## Limits

- **No cluster evidence for `dws-flow` or `dws-step`.** No synthesis creates their pods yet
  (roadmap 2a′). Their formatters are covered only by their golden-case tests.
- **Unsampled traces not exercised.** The run used `samplingRate: 1`. The contract says
  `trace_id`/`span_id` are omitted outside a *valid* span, and an unsampled span still has a valid
  context, so lines would carry IDs that Jaeger never received. That remains a reading of the code
  and `docs/log-format.md`, not an observation.
- Error and exception lines (`exception.*` keys) and the scrubber were not triggered live; they are
  covered by the golden cases.
- One instance, one restart per pod, no retries.

## Gates (task 9.4)

Run on a clean worktree of `474e48f`, the last code commit; the commit that records this file
is docs only.

| Package | Gate | Result |
|---|---|---|
| `dws-controller` | `mvn verify` (Java 25 container) | 375 tests, 0 failures |
| `dws-orchestrator` | `mvn verify` | 285 tests, 0 failures, 1 skipped |
| `dws-step` | `mvn spotless:check`, `mvn verify` | clean; 75 tests, 0 failures |
| `dws-admin` | `pnpm db:migrate`, `lint`, `test`, `build` (Node 24 + `postgres:16-alpine`, as in CI) | 21 suites, 142 tests, all pass |
| `dws-flow` | `dotnet test test/dws-flow.Tests.csproj` | 130 passed |
| `charts/dws` | `helm lint`, `helm template`, the five `tests/*.sh` | all pass; `observability-render-test.sh` under Helm 4.3.0 |

## Reproducing

1. Cluster with the OTel Operator (chart `0.123.0` / operator `0.159.0`), cert-manager, Dapr and
   Knative Serving. Apply `charts/dws/tests/fixtures/otel-collector-dev.yaml` into the release
   namespace, as a DaemonSet on a multi-node cluster.
2. Install the chart with `observability.enabled=true` and `observability.traces.samplingRate=1`,
   leaving `observability.logs.format` unset.
3. Set `observability.enabled` to `true` in the `dws-controller-config` store.
4. `POST /workflows` with a workflow that has a `call: http` task and an `emit` task. Check the
   orchestrator pod's `DWS_LOG_FORMAT`.
5. Start an instance, take the trace ID from the `EmitEventActivity` log line or from Jaeger, and
   run the LogQL query above.
