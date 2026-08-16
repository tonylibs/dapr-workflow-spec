## Context

`dws-admin` is a NestJS/Express service with two independent HTTP listeners:

- Nest's own Express app, on `PORT` (default `3000`) — serves `GET /health` and the existing
  `/workflows*` / `/instances*` read API, CORS-enabled for browser callers.
- `@dbc-tech/nest-dapr`'s `DaprServer`, on `DAPR_APP_PORT` (default `3001`) — a *separate*
  `@dapr/dapr`-SDK-owned Express instance the sidecar calls into for pubsub delivery and bindings.
  Confirmed from the `@dbc-tech/nest-dapr@0.8.0` source (`dapr.module.ts`): `DaprModule` does
  `new DaprServer({ serverHost, serverPort, ... })` directly from `@dapr/dapr`, wired only through
  `DaprLoader.onApplicationBootstrap` → `daprServer.start()`. Nest's own `NestFactory`/Express
  adapter is never passed to it, and the library exposes no hook to register additional routes on
  it beyond `@DaprPubSub`/`@DaprBinding` handlers. It is not general-purpose HTTP surface.

Status is currently derived read-model state, populated asynchronously: `dws-controller`/
`dws-orchestrator` publish lifecycle CloudEvents on `dws.events` → `DwsEventsSubscriber`
(`src/events/dws-events.subscriber.ts`) decodes and dispatches them → `OrchestratorEventsHandler`
calls `upsertWorkflowInstance` / `insertTaskEvent` (`src/events/upserts.ts`), which write Postgres
via Drizzle. `GET /instances/:id` and `GET /instances/:id/tasks` (`InstancesController` /
`InstancesService`) read that same table. There is no existing fan-out from "a row changed" to any
live client — this design adds exactly that, in-process.

`dws-console` (TanStack Start + TanStack Query) has an established typed-fetch-client +
query-hook layering (`lib/admin-client.ts`, `lib/admin-hooks.ts`, `lib/admin-adapters.ts`,
`lib/admin-types.ts`) that this design extends rather than parallels. See `proposal.md` for why
Phase 3 is scoped this way.

## Goals / Non-Goals

**Goals:**
- Running (`status === "started"`) instances reflect new task events and terminal status in the
  console without a manual refresh.
- Reuse `dws-admin`'s existing REST listener and CORS story; add no new listening port, no new
  ingress/Upgrade-header requirement.
- Keep the ingestion pipeline's contract (CloudEvents in, Postgres read model out) completely
  unchanged — push is a read-side addition only.

**Non-Goals:**
- Multi-replica `dws-admin` fan-out. The bridge described below is in-process
  (`EventEmitter2`); an event ingested by replica A is not seen by a client connected to replica B.
  Acceptable for the current single-replica deployment (no Helm/HPA story exists yet per the
  roadmap's Phase 6 status); revisit if `dws-admin` is ever scaled horizontally (options then:
  Postgres `LISTEN`/`NOTIFY`, or re-publish to a dedicated Dapr pubsub topic every replica
  subscribes to).
- Historical replay / gap-filling on the stream itself (see spec's "No historical replay on
  connect"). The console's resync-via-`GET`-on-connect requirement covers this instead, which is
  simpler and reuses already-correct, already-tested `GET` handlers rather than adding an
  event-sourcing replay log.
- Authentication/authorization on the push endpoints — matches the existing unauthenticated,
  credential-less read API (Phase 5 in the roadmap is a separate, unblocked effort).
- Live-inserting brand-new instances into an already-open list page. Only status/timestamp updates
  to rows already loaded on the page are in scope; discovering a new instance still requires a
  page load or "Load more".

## Decisions

**D1 — Transport: Server-Sent Events, not WebSocket, not short-poll.**
The data flow is one-directional (server → client); nothing the console needs to send back over
the same channel. SSE is a plain HTTP response (`Content-Type: text/event-stream`), which:
- Needs no new dependency — Nest's `@Sse()` decorator (`@nestjs/common`) returns an `Observable`
  and handles the wire format.
- Needs no k8s ingress/Upgrade-header changes, unlike WebSocket.
- Is a plain `GET`, so it rides the existing CORS policy (`enableCors(corsOptions(...))`) with no
  preflight, exactly like today's `GET /instances/:id`.
- Gets free auto-reconnect from the browser's native `EventSource` (with backoff), which the
  console's resync-on-reconnect requirement builds on.

WebSocket was rejected: no bidirectional need exists, and it would add a new dependency
(`@nestjs/platform-socket.io` or `ws`) plus ingress configuration for a capability SSE already
covers. Short-poll was rejected as the *primary* mechanism: a genuine push is feasible on the
existing listener (see D2), and the roadmap explicitly frames polling as the fallback SSE avoids,
not a design goal in itself.

**D2 — Host on Nest's main Express app (`PORT`), not `DaprServer`'s listener (`DAPR_APP_PORT`).**
Per Context, `DaprServer` is a separate SDK-owned Express instance with no route-registration
surface beyond Dapr's own pubsub/binding contract — there is no supported way to add an SSE
controller to it. Even if there were, that listener exists to receive sidecar-to-app callbacks, a
different traffic class and trust boundary than public/browser SSE traffic; conflating them would
undo the deliberate two-port separation `configuration.ts`/README document. The new endpoints are
therefore ordinary `@Controller('instances')` routes on the existing app, deployed and reachable
exactly like `GET /instances/:id` today (same CORS config, same ingress path, no new k8s Service
or port to open).

**D3 — In-process fan-out via a plain RxJS `Subject`, fed from the existing upsert functions.**
`upsertWorkflowInstance` and `insertTaskEvent` (`upserts.ts`) are the single choke point every
status change already passes through, regardless of which CloudEvent type produced it. Publishing
a domain event from each successful write:
- Needs no polling of Postgres for changes.
- Keeps the SSE controller thin: it holds no state, just filters the bus by `instanceId` for the
  per-instance stream and maps to the delta shape for the fleet-wide stream.
- Respects the terminal-status ratchet already enforced in the upsert SQL. The upsert uses
  `RETURNING` and publishes only when the row actually landed on the attempted status, so a late
  or duplicate `started` after a terminal write publishes nothing rather than a false regression;
  likewise `insertTaskEvent`'s `onConflictDoNothing` returns no row for a replay, so nothing is
  pushed.

`Subject` rather than `EventEmitter2`/`@nestjs/event-emitter`: that package is not a dws-admin
dependency, while `rxjs` already is, and `@Sse()` consumes an `Observable` directly — an
event-emitter would only have to be bridged back into one. `takeWhile(..., inclusive)` also
expresses "deliver the terminal event, then complete the stream" (D-spec: stream closes on
terminal status) directly, with no manual unsubscribe bookkeeping.

**D3a — Publish after commit, not inside the transaction.** Ingestion runs the upserts inside
`runIdempotent`'s transaction. Publishing there would push a status change that a rolled-back
transaction never persisted, which a client's next `GET` would then contradict. Instead the
handler *returns* what it wrote and `DwsEventsSubscriber` publishes once `runIdempotent` resolves.
`runIdempotent`'s `work` callback is widened to `Promise<unknown>` so handlers may return a value;
its own boolean contract and behavior are unchanged.

**D4 — Two endpoints, not one, to keep detail and list payload sizes matched to what each screen
needs.** `GET /instances/:id/events` carries full instance + task-event detail (what the detail
screen's timeline needs); `GET /instances/events` carries only `{instanceId, status, endedAt}`
(what the list screen needs to patch a row). A single shared stream would force the list page to
either receive full task-event payloads for every running instance it isn't even displaying
detail for, or force the detail page to make a second request to get task-level detail — two
narrowly-scoped endpoints is simpler than one endpoint with a variable/negotiated payload shape.

**D5 — Console: patch the TanStack Query cache directly, don't `invalidateQueries`.**
`invalidateQueries` would trigger a full refetch per event, which is exactly the polling behavior
this change replaces, and for the infinite-query-backed list it risks disturbing loaded pages /
scroll position. Instead:
- Detail (`useInstanceDetail`, a plain `useQuery`): on a pushed event, merge the delta into the
  existing cached value via `queryClient.setQueryData(["instance", id], ...)`.
- List (`useInstances`, an `useInfiniteQuery`): on a pushed delta, walk the cached pages for a
  matching `instanceId` and patch that row's `status`/`endedAt` in place via `setQueryData` on the
  infinite query's key; rows not found in any loaded page (not currently displayed) are ignored.

## Risks / Trade-offs

- **[Risk] In-process fan-out is invisible to other `dws-admin` replicas** → Mitigation: documented
  non-goal (D-Non-Goals); acceptable at current single-replica scale; revisit before any HPA/Helm
  replica-count change lands.
- **[Risk] Long-lived SSE connections held per open browser tab could exhaust server connections
  under many concurrent operators** → Mitigation: connections only exist while an instance is
  `started` and a tab has it open (detail) or the list page is mounted (fleet-wide); both close
  automatically on terminal status / unmount. Revisit only if usage patterns show this is a real
  ceiling.
- **[Risk] A client that never learns its stream dropped (e.g. a proxy silently killing an idle
  connection) could show stale data indefinitely** → Mitigation: `EventSource`'s native
  reconnect fires the browser's `error`/`open` transitions the console hooks into to trigger the
  resync-via-`GET` (spec: "Resync on connect and reconnect"); worst case is a bounded staleness
  window between disconnect and browser-detected reconnect, not permanent staleness.
- **[Trade-off] Two endpoints (D4) instead of one** duplicates some controller/service plumbing
  versus a single generic stream, in exchange for each screen only receiving the payload shape it
  needs. Judged worth it given the fixed, small number of consumers (exactly these two routes).
