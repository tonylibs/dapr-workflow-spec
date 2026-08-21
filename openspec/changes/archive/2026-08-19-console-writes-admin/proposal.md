## Why

`dws-console`'s instance list and instance detail screens only ever show data as of the last
`GET` — an operator watching a running instance must manually hit "Refresh" to see task progress
or a terminal status land. This is `dws-console` roadmap Phase 3 (`docs/roadmaps/dws-console.md`),
next in sequence now that Phases 0–2.5 wired both screens to live `dws-admin` reads. `dws-admin`
has no push mechanism today, so this phase covers designing and building that server-side piece as
well as consuming it from the console.

## What Changes

- Add an SSE push endpoint to `dws-admin`, hosted on Nest's existing Express app (the same
  listener that serves `GET /instances*` today) — **not** on `@dbc-tech/nest-dapr`'s `DaprServer`
  second HTTP listener. That listener is a `@dapr/dapr`-owned Express instance dedicated to the
  Dapr sidecar's pubsub/binding callback contract (confirmed from the `@dbc-tech/nest-dapr@0.8.0`
  source: `DaprModule` constructs `new DaprServer(...)` directly from `@dapr/dapr`, independent of
  Nest's HTTP adapter), with no extension point for arbitrary routes — reusing it would conflate a
  sidecar-internal callback surface with public browser traffic.
- New endpoint `GET /instances/:id/events`: an SSE stream emitting the same instance-status and
  task-event shapes already returned by `GET /instances/:id` and `GET /instances/:id/tasks`,
  pushed as they land in the read model. The stream closes server-side once the instance reaches a
  terminal status (`completed`/`failed`).
- Hook the SSE fan-out into the existing ingestion path (`upserts.ts`'s `upsertWorkflowInstance` /
  `insertTaskEvent`, currently invoked from `orchestrator-events.handler.ts`) via an in-process
  event emitter — no new persistence, no change to the ingestion contract itself.
- Wire `dws-console`'s `routes/instances/$id.tsx` to subscribe to the new stream for the open
  instance and patch the TanStack Query cache incrementally, following the existing
  `lib/admin-client.ts` / `lib/admin-hooks.ts` conventions (typed fetch client, `["instance", id]`
  query key, adapters in `lib/admin-adapters.ts`).
- Wire `routes/instances/index.tsx` to a second endpoint, `GET /instances/events`, a
  lighter-weight stream carrying only `{ instanceId, status, endedAt }` deltas, used to patch
  already-loaded rows in place. Only rows currently in `started` status subscribe/react; completed
  or failed rows and instances not yet loaded on the page are unaffected — no polling, no new rows
  inserted into an open page.
- On connect/reconnect the console does one authoritative `GET` refetch to resync, then applies
  incremental SSE deltas — avoids needing gap-replay/backfill logic server-side for a dropped
  connection.
- **Non-goal / known limitation**: the in-process fan-out assumes a single `dws-admin` replica.
  Multi-replica fan-out (e.g. re-publishing to Dapr pubsub so every replica can push to its own
  connected clients) is out of scope for this change and left as follow-up if `dws-admin` is ever
  horizontally scaled.
- **Non-goal**: WebSocket is not used — the data flow is one-directional (server→client) and the
  Nest app already exposes plain HTTP; adding a WebSocket dependency and k8s ingress
  Upgrade-header handling buys nothing here. Short-poll was also considered and rejected as the
  primary mechanism since a genuine push is feasible on the existing listener.

## Capabilities

### New Capabilities
- `admin-instance-push-api`: `dws-admin`'s SSE endpoints (`GET /instances/:id/events`,
  `GET /instances/events`) pushing instance/task status changes as they're ingested, hosted on the
  existing REST listener.
- `console-live-instance-updates`: `dws-console`'s instance list and instance detail routes
  subscribing to the push API to live-refresh only running/in-progress instances, without polling.

### Modified Capabilities
(none — existing `GET /instances*` and console read-wiring requirements are unchanged; this adds a
new, additive push surface alongside them)

## Impact

- `dws-admin`: new `InstancesEventsController` (or extension of `InstancesController`) plus a
  small in-process pub/sub bridge (e.g. `EventEmitter2`) fed from `upserts.ts`; no schema change,
  no new external dependency (Nest's `@Sse()` is built into `@nestjs/common`).
- `dws-console`: `lib/admin-client.ts` gains an `EventSource`-based subscription helper;
  `lib/admin-hooks.ts` gains hooks that layer SSE-driven cache patches on top of the existing
  `useInstanceDetail`/`useInstances` queries; `routes/instances/$id.tsx` and
  `routes/instances/index.tsx` consume them, gated on `status === "started"`.
- `docs/roadmaps/dws-console.md`: Phase 3 status and §6 progress snapshot updated once implemented.
- No change to `dws-controller`, `dws-orchestrator`, or any other component; no change to the
  `dws.events` CloudEvents contract on the wire.
