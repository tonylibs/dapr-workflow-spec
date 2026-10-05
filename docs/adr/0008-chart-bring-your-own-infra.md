# ADR 0008: Helm Chart — Bring Your Own Infrastructure

- **Status:** Proposed. The direction and the three decisions below were agreed in discussion on
  2026-10-05; this ADR becomes Accepted once the written text is confirmed.
- **Date:** 2026-10-05
- **Context:** [`charts/dws`](../../charts/dws) bundles Postgres and Redis for dev/eval, hardcodes
  Redis as the only Dapr backing store, and supports an external OTLP endpoint. Operators who run
  their own Postgres, Redis, broker or OTLP collector have no single, consistent way to say so.
- **Related:** [ADR 0007](0007-chart-feature-layers.md) (layers — this ADR defines how each layer's
  infrastructure can be replaced), [ADR 0005](0005-observability-instrumentation-decisions.md)
  (the external OTLP pattern this ADR generalises),
  [`docs/roadmaps/helm-packaging.md`](../roadmaps/helm-packaging.md).

## Context

Verified against the chart as of this date:

| Resource | Bundled today | External today | Gap |
|---|---|---|---|
| Postgres (admin read model) | Bitnami subchart | `postgresql.enabled=false` + `admin.database.url` or `existingSecret` | No host/port/TLS fields; migration behaviour fixed |
| Redis | Bitnami subchart | `redis.external.host` + `existingSecret` | Bundled Redis **still installs** alongside (documented trade-off) |
| Broker (Dapr pubsub) | Redis | None | Component type hardcoded to Redis |
| Actor / workflow state store | Redis | None | Component type hardcoded to Redis |
| Workflow definitions store | Redis | Host only | The controller **writes definition keys straight into Redis**, so the backend is not swappable by Dapr component alone |
| OTLP endpoint | None bundled | `observability.otlp.*` (endpoint, headers, existing Secret) | Done — the model to copy |
| Gateway / OIDC issuer | APISIX / Dex | `apiGateway.external.*`, external issuer | Done |

`dws-admin` creates its own tables (workflow definitions, deployments, instances, task events,
processed events) with SQL migrations and runs them **on every boot**; the chart hardcodes that
behaviour. A database user without DDL rights therefore cannot be used today.

## Decision

### 1. One contract for every replaceable resource

Each resource has two states and nothing in between:

| State | Behaviour |
|---|---|
| **Bundled** (default) | The chart installs it. Dev/eval grade. |
| **External** | The chart installs **nothing** for it. The operator supplies connection details, and the render **fails with an error naming the missing field** if they are absent. |

Every external connection takes the same shape: host, port, TLS on/off with an optional CA, and
credentials from an existing Secret (an inline value is allowed for dev only). Setting both
"bundled on" and "external details given" is a render error, which retires the Redis "installs
alongside" trade-off.

### 2. Dapr components are chosen per role, from an allowlist

Roles are independent: pubsub and state store each have their **own** connection, so a Kafka broker
can sit beside a Redis or Postgres state store.

| Role | Allowed backends | Default |
|---|---|---|
| Pubsub (broker) | Redis, Kafka, NATS JetStream | Redis |
| Actor / workflow state | Redis, PostgreSQL | Redis |
| Workflow definitions | Redis only | Redis |
| Admin read model | PostgreSQL only (not a Dapr component) | Bundled Postgres |
| Telemetry | Any OTLP endpoint | None (observability off) |

- **Anything outside the allowlist is rejected at render time.** The allowlist grows only when a
  backend gets its own render test and a documented smoke run.
- **Why definitions are Redis-only:** the controller writes definitions directly into Redis. Moving
  them to another backend is a controller change, not a chart change, and is out of scope here.
  Consequence: **Redis stays a hard requirement even when state is Postgres and pubsub is Kafka.**
  Redis is still replaceable by the operator's own instance, just not removable.
- **Component names do not change** (`pubsub`, the actor state store, `dws-definitions`), so
  workloads and scopes keep working whatever backend sits behind them.
- **State-store eligibility:** the workflow/actor store must support transactions and actors. The
  allowlist enforces that; a non-qualifying type is never offered for that role.

### 3. Postgres schema: three modes

| Mode | Who creates and updates tables | DB user needs | Fits |
|---|---|---|---|
| **auto** (default) | `dws-admin` on boot, as today | Table DDL on its schema | Dev, eval, no DBA |
| **job** | A chart-run pre-install/pre-upgrade Job, with its **own** credentials | Runtime user: data access only; Job user: DDL | Production; also avoids replica races |
| **manual** | The operator applies the shipped SQL before install/upgrade | Data access only | Locked-down environments |

- In **job** and **manual**, `dws-admin` does **not** migrate on boot. It checks the schema version
  and **refuses to start, with a clear message, if the schema is behind** — no half-working admin.
- Multiple admin replicas on **auto** may race the migration; this is documented, and **job** is the
  recommended production mode.
- The README lists the minimum privileges per mode, and ships the SQL for **manual**.

### 4. Fit with the layers (ADR 0007)

| Layer | Replaceable infra |
|---|---|
| L0 Core | Pubsub backend, actor state backend, Redis (definitions) |
| L2 Console | Postgres, gateway, issuer |
| L4 Observability | OTLP endpoint (and later, optional bundled collector) |

A layer that is off renders none of its infrastructure, bundled or external, and needs none of its
external details.

## Rationale

- **One contract beats per-resource special cases.** Operators learn it once; the preflight guards
  are uniform and testable.
- **"Nothing in between" removes silent double-install.** The current Redis behaviour surprises
  operators who believe they went external.
- **Allowlist over free-form.** Dapr accepts many component types, but each one needs init-timeout
  tuning, TLS handling and a test. Untested backends fail at runtime, in the sidecar, after install.
- **Separate pubsub and state connections** match how operators actually run these systems.
- **Migration modes** separate "who may change the schema" from "who runs the app", which is the
  usual production requirement.

## Consequences

| Change | Impact |
|---|---|
| `redis.enabled=false` becomes the only way to go external | Existing releases using `redis.external.host` with the subchart still on keep working until the next upgrade, which then fails with a migration message |
| Admin migration becomes configurable | Needs a `dws-admin` change: schema-version check, startup refusal when behind |
| New migration Job | One more chart-owned hook; credentials Secret handling |
| Pubsub/state split | Dapr Component templates stop sharing one Redis helper; definitions keep it |
| Allowlist | Four backends to test per release |
| Switching backend on a live release | **Data is not migrated.** Documented as a new-install or re-seed operation |

## Phasing

| Phase | Scope |
|---|---|
| 1 | Common external contract and preflight for Postgres and Redis; split pubsub/state connections (both still Redis); remove the "installs alongside" trade-off |
| 2 | Postgres migration modes (`auto`/`job`/`manual`) incl. the `dws-admin` schema check |
| 3 | Pubsub and state backend choice from the allowlist, with render tests per backend |
| 4 | README section, minimum-privilege tables, optional connectivity check at install time |

## Open questions

1. **Pinned Dapr version.** Confirm each allowlisted type (Kafka, NATS JetStream pubsub; PostgreSQL
   actor state) is supported and stable on the Dapr version the chart pins, and note any required
   metadata.
2. **TLS and CA distribution.** One shared CA Secret, or one per resource?
3. **Kafka/NATS semantics.** Consumer-group and stream naming so several installs can share one
   broker without colliding.
4. **Definitions backend.** Worth a later controller change to make it non-Redis?
5. **Upgrade path for the Redis change** (see Consequences): hard fail or a deprecation window?
6. **Connectivity check.** Install-time hook (reusing the Dapr-ready hook) or documentation only?

## Not decided here

Value key names (the terms above are illustrative; the implementing change fixes them), the exact
SQL privilege lists, and any chart or `dws-admin` implementation. Handoff prompts follow once this
is Accepted.

## Discussion log

| Step | Change |
|---|---|
| 1 | Proposal: uniform bundled/external contract; Dapr component override; Postgres BYO |
| 2 | Override limited to an **allowlist** (Redis, Kafka, NATS, Postgres) rather than any Dapr type |
| 3 | Postgres schema handled by `auto` / `job` / `manual` modes |
| 4 | Pubsub and state get **separate connections** in Phase 1 |
| 5 | Verified that definitions are written to Redis by the controller, so they stay Redis-only |
