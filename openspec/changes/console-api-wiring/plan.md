# console-api-wiring Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan
> task-by-task. Read `design.md` for the "why", `specs/console-read-wiring/spec.md` for the "what",
> and `tasks.md` for the checklist this plan expands.

**Goal:** Replace `dws-console`'s static `mock-data.ts` reads with TanStack Query calls against the
live `dws-admin` read API across all four read routes.

**Architecture:** A centralized typed client (`admin-client.ts`, base URL from env, `ApiError`) +
pure DTO→view-model adapters (`admin-adapters.ts`) + per-endpoint TanStack Query hooks
(`admin-hooks.ts`). `mock-data.ts` keeps only its exported types/enums/`statusClass`. The four routes
consume hooks and drive loading/empty/error states from query status through the existing UI kit.

**Tech Stack:** TanStack Start, React 19, `@tanstack/react-query` (`useQuery`/`useInfiniteQuery`),
Vite (`import.meta.env`), TypeScript, Biome, `vitest` (added by this change — dws-console had no test
runner). Verification loop: `pnpm typecheck` + `pnpm lint` + `pnpm test` + `pnpm build`, then a
manual run against a live dws-admin. The adapter layer is written test-first (RED→GREEN); the client,
hooks, and route components are covered by typecheck/build plus the live run.

---

## Task 1: API client + config

- [ ] **Step 1:** Create `src/lib/admin-types.ts` — declare the six DTO interfaces (`WorkflowSummaryDto`, `WorkflowVersionDto`, `DeploymentDto`, `InstanceSummaryDto`, `InstanceDetailDto`, `TaskEventDto`) and `Page<T> = { items: T[]; nextCursor: string | null }`, matching `dws-admin/src/**/dto/*.ts`.
- [ ] **Step 2:** Create `src/lib/admin-client.ts` — `adminUrl(path: string)` reading `import.meta.env.VITE_DWS_ADMIN_URL` (default `""` = same-origin); `class ApiError extends Error { status: number }`.
- [ ] **Step 3:** Add `getJson<T>(path, params?)` — builds URL with query params, `fetch`, throws `ApiError(res.status)` on non-2xx, returns parsed JSON.
- [ ] **Step 4:** Add `.env.example` with `VITE_DWS_ADMIN_URL=http://localhost:3001`.
- [ ] **Step 5:** `pnpm typecheck` — new files compile.

## Task 2: Adapters (test-first)

- [ ] **Step 0a:** Add `vitest` devDependency and a `"test": "vitest run"` script; confirm `pnpm test` runs and reports no test files.
- [ ] **Step 0b:** RED — write `src/lib/admin-adapters.test.ts` covering: `formatRelative`/`formatAbsolute`, `normStatus` fallback on an unknown value, each DTO→view-model map (incl. `orchestrator`←`orchestratorAppId` and `id`←`instanceId`), the instance-detail derivations (`duration`/`taskCount`/`failedCount`/`retries`), and `toTaskEvents` grouping (one row per task name, terminal-status collapse, rich fields left `undefined`). Run `pnpm test` — expect failures for every case.
- [ ] **Step 1:** GREEN — create `src/lib/admin-adapters.ts` — `formatRelative(iso)` and `formatAbsolute(iso)` helpers; `normStatus()` guarding the typed enums (unknown → fallback).
- [ ] **Step 2:** Add `toWorkflowRow`, `toWorkflowVersion`, `toWorkflowDeployment` (orchestrator ← `orchestratorAppId`), and `toWorkflowDetail(versionsPage, deploymentsPage)` assembling `WorkflowDetail` (name/status/latestVersion from newest version).
- [ ] **Step 3:** Add `toInstanceRow` (id ← `instanceId`) and `toInstanceDetail(detailDto, taskEvents)` deriving `duration`, `taskCount`, `failedCount`, `retries`.
- [ ] **Step 4:** Add `toTaskEvents(events)` — group by `taskName`, map lifecycle status (`started→running`/`completed`/`failed`), compute `when`/`duration`/`statusLabel`, leave rich fields unset.
- [ ] **Step 5:** `pnpm test` all green + `pnpm typecheck` — adapters return the exact view-model types from `mock-data.ts`.

## Task 3: Query hooks

- [ ] **Step 1:** Create `src/lib/admin-hooks.ts` — `useWorkflows()` via `useInfiniteQuery`, queryKey `['workflows']`, `getNextPageParam: p => p.nextCursor ?? undefined`, bounded `limit`; flatten pages → `WorkflowRow[]`.
- [ ] **Step 2:** `useInstances(filters)` via `useInfiniteQuery`, queryKey `['instances', filters]`, passing `workflow`/`status`/`limit`/`cursor`.
- [ ] **Step 3:** `useWorkflowDetail(name)` via `useQuery` — fetch versions + deployments (Promise.all), assemble with `toWorkflowDetail`; expose error.
- [ ] **Step 4:** `useInstanceDetail(id)` via `useQuery` — fetch summary + tasks, assemble with `toInstanceDetail`; expose error.
- [ ] **Step 5:** `pnpm typecheck`.

## Task 4: Rewire `mock-data.ts`

- [ ] **Step 1:** Delete the static `workflows`/`instances` arrays, `workflowDetails`/`instanceDetails` maps, and `getWorkflowDetail`/`getInstanceDetail`.
- [ ] **Step 2:** Keep all exported types, enums, `INSTANCE_STATUSES`, `statusClass`. `pnpm typecheck` — expect route errors (fixed in Task 5), no errors inside the lib layer.

## Task 5: Rewire routes

- [ ] **Step 1:** `routes/workflows/index.tsx` — swap `workflows` import for `useWorkflows()`; feed `data` to the table; `isPending`→`SkeletonRows`, empty→`EmptyState`, `isError`→`Banner`; wire "Load more"→`fetchNextPage` gated by `hasNextPage`; delete `StateSwitch`/`state`.
- [ ] **Step 2:** `pnpm typecheck && pnpm lint` for the file; fix.
- [ ] **Step 3:** `routes/workflows/$name.tsx` — swap `getWorkflowDetail(name)` for `useWorkflowDetail(name)`; render versions table + deployment cards from live data; `error?.status===404`→not-found view; `isPending`→existing skeleton; delete `StateSwitch`.
- [ ] **Step 4:** `routes/instances/index.tsx` — swap `instances` for `useInstances({ workflow, status })`; keep the chip/select UI but drive server-side filters via the query key (drop client-side `columnFilters` for the wired path); empty/error states; cursor "Load more"; delete `StateSwitch`.
- [ ] **Step 5:** `routes/instances/$id.tsx` — swap `getInstanceDetail(id)` for `useInstanceDetail(id)`; render header + grouped timeline; `error?.status===404`→not-found; rows without `attemptHistory` don't expand (already handled by `getRowCanExpand`); delete `StateSwitch`.
- [ ] **Step 6:** `pnpm typecheck && pnpm lint` — whole app clean.

## Task 6: Verify

- [ ] **Step 1:** `cd dws-console && pnpm lint && pnpm typecheck && pnpm test && pnpm build` — all green.
- [ ] **Step 2:** Start a `dws-admin` instance; set `VITE_DWS_ADMIN_URL`; run `pnpm dev`.
- [ ] **Step 3:** Visit each of the 4 routes — confirm real data renders; open TanStack Query devtools and confirm one cache entry per endpoint/query key.
- [ ] **Step 4:** Stop dws-admin (or point at a bad URL) — confirm error banners / not-found / empty states render sensibly; confirm no hardcoded host remains (grep for the base URL).
- [ ] **Step 5:** Fill `verify.md` with the observed results.
