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
| [Observability](#observability) | Traces (and agent metrics/logs) to your OTLP collector | Today, partial coverage |
| [Bring-your-own infra](#bring-your-own-infra) | Your Postgres, Redis, broker, OTLP instead of bundled ones | Today for Postgres/Redis/OTLP; more planned (Phase 13) |
| [Engine auth](#engine-auth-planned) | Tokens for CI and services calling the engine | Planned, not designed |
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

**Trace your workflows too** (two switches):

1. Chart: `observability.enabled=true` (keeps `observability.workflows.enabled=true`).
2. Set key `observability.enabled=true` in the `dws-controller-config` store (Redis).
3. Re-deploy the workflow. Only **new deploys** are traced; running versions are not retrofitted.

| Covered today | Not yet |
|---|---|
| `dws-controller`, Admin API, workflow runtimes (`dws-orchestrator`) | Step services (Knative), `dws-flow` / `dws-step` |

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

## Engine auth (planned)

For **machines** calling the engine (CI deploying workflows, services starting instances). Separate
from console sign-in.

| Today | Planned |
|---|---|
| `auth.enabled=false`: controller open inside the cluster | Gate on deploy and start, machine tokens, per-action scopes |
| `auth.enabled=true` / console on: controller accepts only console (Dex/issuer) tokens | Accept both console user tokens and machine tokens |

Open design items: one IdP with two audiences vs two IdPs. Tracked under "Open" in
[`helm-packaging.md`](../../docs/roadmaps/helm-packaging.md).

**Watch out:** even with `auth.enabled=true`, a pod that reaches the controller pod IP on port
`8080` directly skips the token check unless your CNI enforces NetworkPolicy.
