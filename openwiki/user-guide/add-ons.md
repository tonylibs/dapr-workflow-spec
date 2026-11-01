---
type: User Guide
title: Add-ons
description: Features that sit beside the DWS layers rather than inside them — OpenTelemetry observability, bring-your-own infra, engine auth for machine callers, and service mesh — with what works today and what is planned.
tags: [dws, observability, opentelemetry, byo-infra, auth, user-guide]
---

# Add-ons

Add-ons are not layers: switch them on next to whichever [layers](feature-layers.md) you run.

| Add-on | Gives you | Status |
|---|---|---|
| [Observability](#observability) | Traces, metrics, and trace-correlated structured logs | Today, partial coverage |
| [Bring-your-own infra](#bring-your-own-infra) | Your Postgres, Redis, broker, OTLP instead of bundled ones | Today for Postgres/Redis/OTLP; more planned (Phase 13) |
| [Engine auth](#engine-auth) | JWTs plus Dapr sidecar tokens for protected control-plane access | Today, partial |
| Service mesh | Istio | Exploratory |

## Observability

Off by default. On → the controller, the Admin API and (opt-in) each workflow runtime export
OpenTelemetry data to your collector.

**You provide:** the OpenTelemetry Operator (needs cert-manager) and an OTLP receiver. The chart
installs neither.

```sh
helm upgrade dws oci://ghcr.io/tonylibs/charts/dws -n dws-system --reuse-values \
  --set observability.enabled=true \
  --set observability.otlp.endpoint=https://collector.example:4318   # base URL, no /v1/traces
```

| Value | Effect |
|---|---|
| `observability.otlp.endpoint` | Scheme + host + port, no path. Default `http://dws-otel-collector:4318` (a collector you install) |
| `observability.otlp.headers` / `.existingSecret` | Auth headers for the receiver, kept in a Secret |
| `observability.traces.samplingRate` | Sampling ratio (the app agent decides; Dapr follows) |
| `observability.operator.required` | `false` skips the "Operator installed?" preflight |
| `observability.workflows.enabled` | Default `true`: lets workflow runtimes be traced (step 2 below) |
| `observability.logs.enabled` | Default `true`: with the master switch, enables structured-log delivery configuration |
| `observability.logs.format` | Default `json`; use `text` to keep component logs in their prior text format |

### Trace-correlated logs — Today

With `observability.enabled=true`, the chart's default log format is JSON. The controller and
Admin API write one JSON record per line to **stdout**. A traced record includes its `trace_id` and
`span_id`; credentials in the structured fields are scrubbed. The controller applies the same
format to an instrumented orchestrator when you deploy or redeploy that workflow.

Your collector must read container stdout from node log files with a `filelog` receiver (typically
as a DaemonSet), parse the JSON, and send it to your log backend. DWS deliberately sets
`OTEL_LOGS_EXPORTER=none`: agent OTLP log export is not a second delivery path. The chart does not
install a collector. See the [chart's collector receiver example](../../charts/dws/README.md#logs-and-trace-correlation)
and the canonical [structured log format](../../docs/log-format.md).

**Watch out:** `kubectl logs` combines stdout and stderr. JVM launchers, agents, and libraries can
still write non-JSON diagnostics to stderr, so piping the combined stream directly to `jq` can fail.
The observed JSON-only guarantee is for DWS logging-system output on stdout. Existing workflow
versions keep their prior configuration until redeployed.

**Trace your workflows too** (two switches):

1. Chart: `observability.enabled=true` (keeps `observability.workflows.enabled=true`).
2. Set key `observability.enabled=true` in the `dws-controller-config` store (Redis).
3. Re-deploy the workflow. Only **new deploys** are traced; running versions are not retrofitted.

| Covered today | Not yet |
|---|---|
| Traces: `dws-controller`, Admin API, workflow runtimes (`dws-orchestrator`). Structured JSON logs: controller, Admin API, orchestrator; `dws-flow` and `dws-step` implement the format but lack deployed-stack evidence. | Step services (Knative) for traces and structured logs; deployed `dws-flow` / `dws-step` instrumentation awaits runtime-v2 synthesis. |

Details: [chart README → OpenTelemetry](../../charts/dws/README.md#opentelemetry-observability-phase-1),
[`docs/roadmaps/observability.md`](../../docs/roadmaps/observability.md).

## Bring-your-own infra

Swap a bundled piece for your own (recommended for production — bundled ones are dev/eval grade).

| Piece | Today | Planned |
|---|---|---|
| Postgres (Admin API history) | `postgresql.enabled=false` + `admin.database.url` or `admin.database.existingSecret` | — |
| Redis (state, definitions, pub/sub) | `redis.external.host` — all Components move; bundled Redis **still installs** | Separate hosts for pub/sub and state store; only tested Dapr component types allowed |
| Event broker | Moves only with Redis | Durable broker for the [Events](events.md) layer (Kafka, …) instead of in-memory |
| OTLP | `observability.otlp.endpoint` | — |
| Dapr | `dapr.enabled=false` (preflight checks CRDs) | — |
| Knative | Always yours today | Bundled in Core; clash with a cluster-wide Knative handled by preflight (12.1a/b) |

Design: [ADR 0008](../../docs/adr/0008-chart-bring-your-own-infra.md), Helm roadmap Phase 13.

## Engine auth

For **machines** calling the engine (CI deploying workflows, services starting instances). It is
separate from the browser sign-in described in [Console](console.md) and complements rather than
replaces that OIDC login.

### Today (chart 1.x): turn on the control-plane boundary

`auth.enabled=true` keeps the existing bearer JWT middleware and adds Dapr machine-auth tokens to
the control-plane sidecars. A request reaching a protected sidecar must satisfy **both** checks.
The chart creates three token Secrets by default (or lets you supply them), but never prints their
values:

| Protected path | What the chart adds | Result |
|---|---|---|
| Gateway → Admin sidecar | Admin Dapr API token; APISIX injects it on `/dws-admin` | A client-supplied token header is replaced; the browser does not receive the secret. |
| Admin sidecar → `dws-admin` app (`:3000`) | Admin app API token | Direct pod-IP calls, including forged lifecycle events, get `401`; `GET /health` remains open for kubelet probes. |
| Controller sidecar | Controller Dapr API token | Calls to the controller sidecar require the token; the controller can still publish lifecycle events through its own sidecar. |

The controller **app port** is not protected by an app API token yet. Keep NetworkPolicy (or an
equivalent CNI-enforced boundary) around the control plane: a pod that can reach the controller pod
IP on port `8080` can still bypass its sidecar.

Enable this with the existing authentication setting and a valid issuer/audience configuration:

```sh
helm upgrade --install dws oci://ghcr.io/tonylibs/charts/dws -n dws-system \
  --set auth.enabled=true \
  --set auth.issuer=https://issuer.example.com \
  --set auth.audience=dws
```

For production, create a separate Kubernetes Secret per token (key: `token`) and set the matching
`auth.apiTokens.admin.daprApi.existingSecret`,
`auth.apiTokens.admin.appApi.existingSecret`, or
`auth.apiTokens.controller.daprApi.existingSecret` value. The chart otherwise generates stable
tokens, which Helm release readers can recover from rendered manifests.

**Watch out**

- `daprd` reads a token only at startup. After changing or rotating one, restart the affected
  admin/controller deployments and, when it fronts `/dws-admin`, APISIX too. A token mismatch makes
  Admin API requests return `401`.
- With an external APISIX installation, put the admin Dapr API token in its
  `DWS_ADMIN_DAPR_API_TOKEN` environment variable, allow that variable in nginx, and restart it
  after rotation. The chart cannot configure a gateway it does not own.
- `auth.enabled=false` remains the kill switch for these token checks. The shipped APISIX defaults
  need clearing for the unusual auth-off, bundled-APISIX-only topology; see the
  [chart README](../../charts/dws/README.md#machine-auth-tokens).

For the component topology and validation details, see [Helm chart packaging](../architecture/helm-chart-roadmap.md#controller-bearer-middleware).
