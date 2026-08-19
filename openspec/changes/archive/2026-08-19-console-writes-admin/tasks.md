## 1. dws-admin: event bridge

- [x] 1.1 Add an RxJS-`Subject`-based bridge (new `InstanceEventsModule`/`InstanceEventsService` in `dws-admin/src/events/`) that emits a domain event after each successful write in `upserts.ts`'s `upsertWorkflowInstance` (event carrying the updated instance's `instanceId`, `status`, `endedAt`) and `insertTaskEvent` (event carrying the inserted `TaskEventDto` plus its `instanceId`).
- [x] 1.2 Wire `OrchestratorEventsHandler`'s call sites (or the upsert functions themselves) to publish through this bridge without changing `upserts.ts`'s existing SQL/ratchet behavior.
- [x] 1.3 Unit test: an `upsertWorkflowInstance`/`insertTaskEvent` call emits the expected domain event with the expected payload; a terminal-status ratchet no-op emits nothing.

## 2. dws-admin: SSE endpoints

- [x] 2.1 Add `GET /instances/:id/events` to `InstancesController` (or a new controller in the same module) using Nest's `@Sse()`, subscribing to the bridge filtered by `instanceId`, returning 404 up front via the existing `instances.exists(id)` check before opening the stream.
- [x] 2.2 Close the `:id/events` stream server-side once a `completed`/`failed` event for that instance is observed, after emitting it.
- [x] 2.3 Add `GET /instances/events` emitting the lightweight `{instanceId, status, endedAt}` delta for every instance-status change, no per-instance filter.
- [x] 2.4 Confirm both routes are served on the existing Nest app port (no change to `main.ts` bootstrap, no new `app.listen`) and inherit the existing `enableCors(corsOptions(...))` config.
- [x] 2.5 Document both endpoints in the `@nestjs/swagger` `DocumentBuilder` setup so they appear at `/docs` alongside the existing read endpoints.
- [x] 2.6 Integration test (against the real Postgres test setup per `dws-admin/README.md`'s test conventions): connecting to `:id/events`, then triggering an ingested lifecycle event for that instance, observes the pushed event; connecting for an unknown id gets `404`; connecting to `/events` observes deltas for any instance.
- [x] 2.7 Run `pnpm lint && pnpm test && pnpm build` in `dws-admin/`.

## 3. dws-console: client and hooks

- [x] 3.1 Add an `EventSource`-based subscription helper to `lib/admin-client.ts` (base-URL-aware, matching the existing `adminUrl(path)` builder), exposing per-instance and fleet-wide subscribe functions with cleanup (`close()`).
- [x] 3.2 Add a hook in `lib/admin-hooks.ts` that layers the per-instance SSE subscription on top of `useInstanceDetail`, patching `queryClient.setQueryData(["instance", id], ...)` on each pushed event, only subscribing while cached status is `started`, and unsubscribing on unmount or on reaching a terminal status.
- [x] 3.3 Add a hook that layers the fleet-wide SSE subscription on top of the instance list's infinite query, patching matching loaded rows' `status`/`endedAt` via `setQueryData` on the list's query key, ignoring deltas for instances not present in any loaded page.
- [x] 3.4 On each hook's subscribe (initial connect and on `EventSource` `open` after a reconnect), trigger a `GET`-based refetch of the corresponding query to resync before/alongside live deltas.
- [x] 3.5 On subscription error, leave the last-fetched query data rendered and do not surface a route-level error — the existing "Refresh" control must remain usable.
- [x] 3.6 Update `lib/admin-adapters.ts`/`lib/admin-types.ts` only if the pushed payload shapes need a distinct type from the existing `InstanceDetailDto`/`TaskEventDto` (reuse those types if the shapes already match).

## 4. dws-console: route wiring

- [x] 4.1 Wire `routes/instances/$id.tsx` to the per-instance live hook, gated on the instance's current status being `started`; verify the task timeline and header update without the manual "Refresh" click once a task event or terminal status is pushed.
- [x] 4.2 Wire `routes/instances/index.tsx` to the fleet-wide live hook; verify a loaded row's status badge and ended column update in place, without disturbing "Load more"/scroll state.
- [x] 4.3 Unit/component test coverage for both hooks' cache-patch logic (e.g. via `admin-hooks`-level tests, following existing `admin-adapters.test.ts` conventions) covering: matching-id patch, non-matching-id no-op, terminal-status unsubscribe, reconnect-triggers-refetch.
- [~] 4.4 Manually verify in the running app — **partially done; blocked on environment**. No Docker daemon and no `dapr` CLI are available here, and `dws-admin` cannot finish booting without a sidecar (`DaprLoader` awaits it before `app.listen`), so the browser walkthrough could not be run. Verified instead over real HTTP, against a locally-built `dws-admin` booted with its read-side modules only: route precedence (`/instances/events` maps *before* `/instances/:id` — confirmed in the Nest route table), `text/event-stream` + CORS headers, a `task` frame then an `instance` frame with the documented payload shapes, the server closing the per-instance stream after the terminal status (curl exit 0), the fleet stream's narrower delta shape, and `404` for an unknown id with no stream opened. Still needs a browser+sidecar pass before release.
- [x] 4.5 Run `pnpm lint && pnpm test && pnpm build` in `dws-console/`.

## 5. Docs

- [x] 5.1 Update `docs/roadmaps/dws-console.md`: mark Phase 3 done in the phase table and mermaid graph, and update §1's endpoint table and §6's progress snapshot to reflect the new push endpoints and live-wired routes.
