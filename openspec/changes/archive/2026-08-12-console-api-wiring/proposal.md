## Why

`dws-console` Phases 1–2 shipped complete UI (workflow browser, instance monitor) but every screen
reads static arrays from `src/lib/mock-data.ts` — none of it is real. `dws-admin` already exposes
the full read API and the console's `QueryClient` is wired in but unused; no route calls `useQuery`.
Phase 2.5 closes that gap: swap the mock reads for TanStack Query calls so the console shows live
cluster state. Doing it now unblocks every later phase (live updates, submission, auth), which all
assume real data fetching exists.

## What Changes

**Workflow + instance data source**
- From: routes import static `workflows` / `instances` arrays and `getWorkflowDetail` /
  `getInstanceDetail` lookups from `mock-data.ts`.
- To: routes call TanStack Query hooks (`useWorkflows`, `useWorkflowDetail`, `useInstances`,
  `useInstanceDetail`) backed by a typed `dws-admin` client + DTO→view-model adapters.
- Reason: display live cluster state instead of a static prototype.
- Impact: non-breaking (console-only, additive to existing UI); no dws-admin or DSL change.

**Render states**
- From: demo `StateSwitch` toggles loading/empty/error manually.
- To: `isPending`/empty-result/`isError` (incl. 404/400) drive the existing
  `states.tsx`/`skeleton.tsx` components; `StateSwitch` removed from the 4 wired routes.
- Reason: real states, not a reviewer toggle.

**Pagination**
- From: static "Load more" buttons (disabled / decorative cursor text).
- To: `useInfiniteQuery` with the admin cursor envelope (`{ items, nextCursor }`); "Load more" calls
  `fetchNextPage`, gated by `hasNextPage`.

**Base URL**
- From: N/A (no fetch existed).
- To: `import.meta.env.VITE_DWS_ADMIN_URL`, centralized, defaulted, documented in `.env.example`.

## Capabilities

### New Capabilities
- `console-read-wiring`: The `dws-console` behavior of fetching workflow/instance read data from the
  `dws-admin` REST API via TanStack Query — endpoint→route mapping, DTO→view-model adaptation,
  cursor pagination, configurable base URL, and query-driven loading/empty/error states.

### Modified Capabilities
- (none — `admin-read-api` is consumed as-is; no server-side requirement changes)

## Impact

- **Component**: `dws-console` only. `dws-admin`, `dws-controller`, `dws-orchestrator`, step
  services untouched. Independent build boundaries preserved.
- **Code**: `src/lib/mock-data.ts` (data → hooks/adapters, types retained); new `src/lib/`
  client/adapter modules; the 4 routes (`workflows/index.tsx`, `workflows/$name.tsx`,
  `instances/index.tsx`, `instances/$id.tsx`); `.env.example`.
- **Dependencies**: `@tanstack/react-query` already present. `vitest` added as a devDependency —
  `dws-console` had no test runner, and the adapter layer (pure DTO→view-model functions) is the
  part of this change that most needs regression coverage.
- **Config**: new env var `VITE_DWS_ADMIN_URL`; requires a reachable `dws-admin` at runtime.
- **Validation gate**: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`, plus a manual run
  against a live dws-admin.
