# DWS lifecycle events

DWS emits a stream of **lifecycle events** so a future read model (`dws-admin`) can track what the
platform is doing without either publisher coupling to the reader. Two independently-built
components publish onto the same stream:

- **`dws-controller`** — definition and deployment lifecycle (from its apply pass).
- **`dws-orchestrator`** — workflow instance and task lifecycle (from the interpreter).

Publishing is **additive and best-effort**. It never changes existing DSL semantics
(`call`/`switch`/`set`/`wait`/`listen`/`emit`), never adds persistence, and a publish failure never
fails an apply pass or a workflow instance. The cluster remains the single source of truth; events
are a derived signal only.

## Transport binding

| | |
|---|---|
| **Pub/sub component** | `pubsub` (the same component `dws-orchestrator` already names for `emit`) |
| **Topic** | `dws.events` |

Every event — regardless of type — goes to component `pubsub`, topic `dws.events`. Publishers MUST
NOT invent per-event-type topics or alternate components; the event `type` field drives consumer
routing.

## Envelope

Each event is a JSON object in a **CloudEvents-style** envelope:

| Field | Type | Notes |
|---|---|---|
| `id` | string | Unique per event. |
| `source` | string | Identifies the publisher (see below). |
| `type` | string | One of the `io.dws.*` types below. |
| `time` | string | RFC 3339 / ISO 8601 UTC timestamp. |
| `datacontenttype` | string | Always `application/json`. |
| `data` | object | The per-type payload defined below. |

Example:

```json
{
  "id": "order@vab12cd34-1",
  "source": "dws-orchestrator/order",
  "type": "io.dws.instance.started",
  "time": "2026-07-24T15:37:12.851Z",
  "datacontenttype": "application/json",
  "data": {
    "instanceId": "…",
    "workflow": "order",
    "version": "v3",
    "appId": "order",
    "startedAt": "2026-07-24T15:37:12.851Z"
  }
}
```

> Dapr itself wraps published bytes in a CloudEvent at the transport layer. This envelope is our own
> explicit, documented `data` contract; the two are independent and both publishers honor this one.

Because the envelope is CloudEvents-shaped, a consumer may decode it with a CloudEvents SDK —
`dws-admin` decodes it with the [CloudEvents JS SDK](https://github.com/cloudevents/sdk-javascript)
and validates it against the v1 schema. Publishers must therefore keep the envelope **spec-valid**:
`id`/`source`/`type` non-empty (`specversion` is assumed `1.0` when absent), `time` a real RFC 3339
timestamp, and no extra top-level attributes beyond those above unless they are valid CloudEvents
extension names (lower-case `a`–`z` / `0`–`9` only) — a payload that fails validation is dropped by
the consumer, not retried.

### `source` convention

| Publisher | `source` |
|---|---|
| `dws-controller` | `dws-controller` |
| `dws-orchestrator` | `dws-orchestrator/<appId>` |

## Event catalog

All types are under the `io.dws.` prefix.

### Definition events — `dws-controller`

| Type | When |
|---|---|
| `io.dws.definition.created` | A definition version is materialized that was not previously present. |
| `io.dws.definition.updated` | An apply pass resolves to an already-present definition version (a re-assert; definition content is immutable per its version hash). |

**Payload:**

```json
{ "workflow": "order", "version": "vab12cd34", "createdAt": "2026-07-24T15:37:12.851Z" }
```

### Deployment events — `dws-controller`

| Type | When |
|---|---|
| `io.dws.deployment.applied` | An apply pass completes successfully. |
| `io.dws.deployment.failed` | An apply pass throws (the original exception still propagates unchanged). |
| `io.dws.deployment.drained` | A superseded orchestrator version is annotated for drain. |
| `io.dws.deployment.collected` | A drained version's resources are garbage-collected. |

**`applied` / `failed` payload** (`failed` adds `error`):

```json
{
  "workflow": "order",
  "version": "vab12cd34",
  "stepServices": ["check-inventory", "charge-payment", "notify-out-of-stock"],
  "orchestratorAppId": "order",
  "error": "…only on io.dws.deployment.failed…"
}
```

**`drained` / `collected` payload:**

```json
{ "workflow": "order", "version": "vab12cd34", "orchestratorAppId": "order" }
```

### Instance events — `dws-orchestrator`

| Type | When |
|---|---|
| `io.dws.instance.started` | The interpreter begins executing an instance. |
| `io.dws.instance.completed` | The interpreter loop completes without error (exactly one terminal event). |
| `io.dws.instance.failed` | The interpreter loop terminates with an error (the error still propagates). |

**Payload** (`completed`/`failed` add `endedAt`; `failed` adds `error`):

```json
{
  "instanceId": "…",
  "workflow": "order",
  "version": "v3",
  "appId": "order",
  "startedAt": "2026-07-24T15:37:12.851Z",
  "endedAt": "…only on completed/failed…",
  "error": "…only on failed…"
}
```

### Task events — `dws-orchestrator`

| Type | When |
|---|---|
| `io.dws.task.started` | Before dispatching a task item in the interpreter loop. |
| `io.dws.task.completed` | After the task item dispatches successfully. |
| `io.dws.task.failed` | The task item dispatch throws. |

**Payload** (`failed` adds `error`):

```json
{
  "instanceId": "…",
  "taskName": "checkInventory",
  "taskType": "call",
  "timestamp": "2026-07-24T15:37:12.851Z",
  "error": "…only on failed…"
}
```

## Determinism (orchestrator)

Orchestrator events are published **through a Dapr workflow activity** (`AdminEventActivity`,
mirroring `EmitEventActivity`), never by calling the Dapr client directly inside
`InterpreterWorkflow.execute`. Any timestamp or id placed in an event is derived from replay-safe
workflow context values (`ctx.getCurrentInstant()`, `ctx.getInstanceId()`) — never `Instant.now()`
or a random generator inside the workflow method — so events are identical across replays.

The controller is not replay-constrained, so it stamps `time`/`id` at the wall-clock boundary.

## Deployment prerequisite

A Dapr pub/sub **`Component` named `pubsub` carrying topic `dws.events`** MUST exist in-cluster
before either component's event publishing works. This component is **not** provisioned by either
component's own `k8s/` manifests — it is a platform prerequisite you must install into the target
cluster (the same component the orchestrator already uses for `emit`). When it is absent, publishing
degrades gracefully (the controller swallows and logs; the orchestrator's admin publish tolerates
failure) and no other behavior is affected.
