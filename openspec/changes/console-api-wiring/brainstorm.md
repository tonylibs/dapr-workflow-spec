<!--
Raw capture of the console-api-wiring design exploration (superpowers-bridge brainstorm step).
Decision-log format: background → decision chain Q1..Qn → trade-offs.
design.md reorganizes this into structured sections; do not copy verbatim.
-->

# Brainstorm — console-api-wiring (dws-console Phase 2.5)

## Background

`dws-console` (TanStack Start + React 19, Vite, Biome) has Phases 1–2 UI built but every
screen reads from static arrays/maps in `src/lib/mock-data.ts`. The `QueryClient` is wired
into the app shell (`src/integrations/tanstack-query/root-provider.tsx`) and the SSR-query
integration is set up (`src/router.tsx`), but **no route calls `useQuery` yet**. Phase 2.5 is
the first live wiring: replace the mock reads with TanStack Query calls against `dws-admin`'s
read API.

`dws-admin` (NestJS) already exposes the full read surface (spec:
`openspec/specs/admin-read-api/spec.md`), with validated DTOs and a paginated envelope:

| Endpoint | Admin DTO | Console route consuming it |
|---|---|---|
| `GET /workflows` | `Paginated(WorkflowSummaryDto)` | `routes/workflows/index.tsx` (list) |
| `GET /workflows/:name` | `Paginated(WorkflowVersionDto)` | `routes/workflows/$name.tsx` (version history) |
| `GET /workflows/:name/deployments` | `Paginated(DeploymentDto)` | `routes/workflows/$name.tsx` (deployment cards) |
| `GET /instances` | `Paginated(InstanceSummaryDto)` | `routes/instances/index.tsx` (list + filters) |
| `GET /instances/:id` | `InstanceDetailDto` | `routes/instances/$id.tsx` (header) |
| `GET /instances/:id/tasks` | `Paginated(TaskEventDto)` | `routes/instances/$id.tsx` (timeline) |

Existing shared UI to reuse (do NOT rebuild): `components/states.tsx` (`EmptyState`, `Banner`,
`StateSwitch`), `components/skeleton.tsx` (`Skeleton`, `SkeletonRows`), `components/status.tsx`
(status→color badges), `components/data-table.tsx`.

## Key finding: mock shapes and admin DTOs do NOT fully match

The mock-data.ts comment claims the shapes "intentionally track the documented endpoints", but a
field-level diff shows real gaps. The DTOs supply the *primitives*; the console mock carries
*presentation-derived* and *richer* fields the API does not return:

- **Timestamps**: DTOs return ISO `date-time` (`createdAt`, `startedAt`, `endedAt`, `timestamp`).
  Mock carries relative strings (`updated: "2h ago"`, `started: "2m ago"`, `created: "2026-08-02 14:11 · 2h ago"`).
- **`WorkflowVersion.note`** ("current", "drained at 14:11") — no API source.
- **`InstanceDetail`** extra fields — `duration`, `taskCount`, `failedCount`, `retries` — not in
  `InstanceDetailDto` (which is `InstanceSummaryDto` + `appId`). Must be *derived* from tasks/timestamps.
- **`InstanceDetail.orchestrator`** ← DTO `appId`. **`InstanceRow.id`** ← DTO `instanceId`.
- **`TaskEvent`** is the biggest gap. Mock models a *per-task* row with rich retry/catch detail —
  `statusLabel`, `when`, `duration`, `attempts`, `attemptHistory[]` (attempt/backoff mini-timeline),
  `retryPolicy`, `caughtBy`, `caughtError`, `indent`. `TaskEventDto` is a flat *per-lifecycle-event*
  record — `{ id, taskName, type, status(started/completed/failed), timestamp, error }`.
- **Status enum casing**: mock `TaskStatus` = lowercase `completed|running|failed|pending`; DTO task
  `status` = lifecycle `started|completed|failed`. Mock `InstanceStatus`/`WorkflowStatus` are uppercase
  strings; DTO `status` is a free `string`.

Conclusion: an **adapter layer** is required. This is not a 1:1 swap. Design must decide, per gap,
whether to *derive*, *degrade gracefully* (leave undefined), or *reshape the UI*.

## Decision chain

### Q1 — Where does the fetch + adapter logic live?
**Decision:** Add a small typed API client + per-endpoint TanStack Query hooks under `src/lib/`.
Replace `mock-data.ts`'s data (`workflows`, `instances`, `getWorkflowDetail`, `getInstanceDetail`)
with hooks (`useWorkflows`, `useWorkflowDetail`, `useInstances`, `useInstanceDetail`), while KEEPING
the exported view-model types (`WorkflowRow`, `WorkflowDetail`, `InstanceRow`, `InstanceDetail`,
`TaskEvent`, and the `statusClass`/enum exports) so route render code changes as little as possible.
Adapters (DTO → view model) live next to the client.
**Alternatives considered:** (a) fetch inline in each route — rejected: duplicated base-URL/error
logic, no reuse. (b) generate a typed client from the served OpenAPI doc — attractive but heavier;
deferred (the DTOs are stable and few). Keep hand-written client for Phase 2.5.

### Q2 — How to bridge the TaskEvent gap (per-event API vs per-task UI)?
**Decision:** Group `task_events` by `taskName` into one timeline row per task (matches the existing
UI). Row status = terminal event's status mapped to `TaskStatus` (`started→running`,
`completed→completed`, `failed→failed`); `when` = first event's offset from instance start;
`duration` = last − first event timestamp; `statusLabel` = derived from status. Rich fields with **no
API source** — `attempts`, `attemptHistory`, `retryPolicy`, `caughtBy`, `caughtError`, `indent` — are
left `undefined`. The timeline **degrades gracefully**: `getRowCanExpand` already keys off
`attemptHistory`, so rows simply don't expand; the mini-timeline never renders.
**Alternatives:** (a) one row per raw event — rejected: diverges from built UI, noisier. (b) invent
retry/attempt data client-side — rejected: fabrication. Rich retry visualization needs richer
`task_events` from dws-admin → out of scope (future phase).

### Q3 — Timestamp presentation (ISO → what the UI shows)?
**Decision:** Adapter formats ISO timestamps into the relative/absolute strings the UI already
expects, via a small `formatRelative()` / `formatAbsolute()` helper. `note` (no source) → omitted
(column renders "—" / empty). Keeps route JSX untouched.

### Q4 — Base URL configuration?
**Decision:** Read from a Vite env var, `import.meta.env.VITE_DWS_ADMIN_URL`, with a dev-friendly
default (empty → same-origin, or `http://localhost:3001`). Never hardcode. A single
`adminUrl(path)` builder centralizes it. Documented in `.env.example`.
**Alternatives:** hardcoded constant — rejected (requirement). Runtime `/config.json` — overkill for now.

### Q5 — Pagination?
**Decision:** Use `useInfiniteQuery` for the two main list endpoints (`/workflows`, `/instances`)
with `getNextPageParam = (lastPage) => lastPage.nextCursor ?? undefined`, `limit` bounded per the
admin-read-api spec. Wire the existing (currently disabled/static) "Load more" buttons to
`fetchNextPage`; `hasNextPage` drives disabled state. Detail sub-lists (versions, deployments,
tasks) are small — fetch a single page at a high `limit` for Phase 2.5 (still cursor-aware
envelope), auto-paging deferred.
**Alternatives:** offset paging — N/A, API is cursor-only. Auto-fetch all pages — deferred.

### Q6 — Loading / empty / error state wiring?
**Decision:** Drive states from query status, not the demo `StateSwitch`:
`isPending` → skeleton (`SkeletonRows`/`Skeleton`); `data.items.length === 0` (or filtered rows 0)
→ `EmptyState`; `isError` → `Banner` (list) or the not-found `EmptyState` (detail); HTTP 404 from
detail endpoints → the existing "No workflow/instance" not-found view; a 400 (bad filter/limit) →
the existing warn `Banner`. Remove the demo `StateSwitch` from the 4 wired routes (its own comment
says live states come from TanStack Query). Keep the `states.tsx` component exports intact.
**Alternatives:** keep StateSwitch as an override — rejected: confusing with real states.

### Q7 — SSR prefetch vs client-only hooks?
**Decision:** Component-level `useQuery`/`useInfiniteQuery` for Phase 2.5 (the "first wiring";
verify wants devtools cache entries). Route-loader `ensureQueryData` prefetch is a later
optimization; the SSR-query integration already in `router.tsx` means hooks still hydrate correctly.
**Alternatives:** loader-based prefetch now — deferred to keep scope tight.

### Q8 — 404 handling for detail routes?
**Decision:** The client throws a typed `ApiError` carrying `status`. `$name.tsx` / `$id.tsx` treat
`error?.status === 404` as the not-found `EmptyState`; any other error → generic error view with a
retry action (`refetch`).

## Out of scope (explicit)
- `GET /health` (no route consumes it).
- Live polling / push updates (Phase 3 — needs new dws-admin push API).
- Definition submission / writes (Phase 4).
- Auth to dws-admin (Phase 5).
- Rich retry/attempt visualization beyond what flat `task_events` provide.
- Generating a typed client from the OpenAPI document.

## Acceptance (verify targets)
- Each of the 4 routes renders real data from a running dws-admin instance.
- TanStack Query devtools show one cache entry per endpoint/query key.
- Empty and error states render sensibly when dws-admin is unreachable or returns empty.
- `pnpm lint`, `pnpm typecheck`, `pnpm build` all pass.
- Base URL comes from `VITE_DWS_ADMIN_URL`; no hardcoded host remains.
