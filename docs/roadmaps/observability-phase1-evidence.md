# Observability Phase 1 — live evidence

Run date: 2026-09-21  
Namespace: `dws-obs-e2e`  
Collector: `dws-otel-collector` fixture + Jaeger 1.62  
Trace: `61a3d135fd15003bbbff6a11f74c1d2b`

## Admission and signal checks

| Check | Result |
|---|---|
| Instrumentation CR | `dws-instrumentation` present; endpoint `http://dws-otel-collector:4318`; sampler `1` for the live run |
| Controller injection | `opentelemetry-auto-instrumentation-java` init; app container `controller` only |
| Admin injection | `opentelemetry-auto-instrumentation-nodejs` init; app container `admin` only |
| Dapr injection target | Neither `daprd` container received an OTel init container |
| Collector traces | Debug exporter showed `dapr-diagnostics` and application spans |
| Collector metrics | Debug exporter batches showed 18–23 metrics and 66–78 data points |
| Collector logs | Fixture `filelog` receiver read pod log files and the debug exporter emitted `data_type: logs`; the stock agents did not emit separate OTLP log records |

The first Helm install reproduced an admission ordering race: the Operator webhook admitted the
two Deployments before Helm had created `Instrumentation`. The new `post-install,post-upgrade`
hook waits for the CR and re-admits pods that lack the expected init container. The hook completed
successfully on the upgrade; the render regression test now checks for this recovery behavior.

## Connected trace

The Jaeger trace contains 49 nested spans. The compact operation list below is intentionally kept
as evidence rather than a raw Jaeger export:

```text
dws-controller  POST /workflows
dws-controller  /dapr.proto.runtime.v1.Dapr/PublishEvent
dws-controller  dapr.proto.runtime.v1.Dapr/GetConfiguration
dws-controller  dapr.proto.runtime.v1.Dapr/PublishEvent
dws-admin       POST /dapr/events/dws
dws-admin       request handler - /dapr/events/dws
dws-admin       DaprSubscriptionController.deliver
dws-admin       pubsub/dws.events
dws-admin       deliver
```

The Collector debug output also showed Dapr's `dapr-diagnostics` instrumentation scope and the
same trace IDs as the application spans. A screenshot-style trace view is checked in at
[`observability-phase1-jaeger.svg`](observability-phase1-jaeger.svg).

## Replay spike 0-A(c)

No replay was observed. The controller-created orchestrator could not become live because its
default GHCR image is private in this cluster, so there was no Dapr Workflow execution to replay.
The event path itself continued cleanly: one POST produced the controller publish and admin
delivery spans without duplicate workflow activity spans or a restarted trace. Phase 2a must repeat
this check with a runnable orchestrator image before the replay behavior can be called conclusive.

## Database-span limitation

The admin event was persisted successfully (the readiness/health checks remained green), but the
trace has no separate Postgres client span. The admin uses the `postgres` (postgres-js) driver;
the pinned Node auto-instrumentation image includes `@opentelemetry/instrumentation-pg`, not a
postgres-js instrumentation. Adding application instrumentation is outside this chart-only Phase
1 change and is tracked with the Phase 2a runtime follow-up.
