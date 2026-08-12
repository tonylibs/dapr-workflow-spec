## 1. API client + config (dws-console)

- [ ] 1.1 Add `src/lib/admin-client.ts`: `adminUrl(path)` reading `import.meta.env.VITE_DWS_ADMIN_URL` (with documented default), a typed `ApiError` (carrying HTTP `status`), and a `getJson<T>(path, params?)` fetch helper that throws `ApiError` on non-2xx.
- [ ] 1.2 Add `.env.example` documenting `VITE_DWS_ADMIN_URL`.
- [ ] 1.3 Define admin DTO types + the page envelope `Page<T> = { items: T[]; nextCursor: string | null }` (co-locate in `admin-client.ts` or `src/lib/admin-types.ts`).

## 2. Adapters (DTO → view model)

- [ ] 2.1 Add `src/lib/admin-adapters.ts` with `formatRelative()` / `formatAbsolute()` and a status-normalizer guarding the typed enums.
- [ ] 2.2 Map `WorkflowSummaryDto → WorkflowRow`, `WorkflowVersionDto → WorkflowVersion`, `DeploymentDto → WorkflowDeployment` (orchestrator ← orchestratorAppId), and assemble `WorkflowDetail` from the two workflow endpoints.
- [ ] 2.3 Map `InstanceSummaryDto → InstanceRow` (id ← instanceId); `InstanceDetailDto + tasks → InstanceDetail` deriving `duration`, `taskCount`, `failedCount`, `retries`.
- [ ] 2.4 Map `TaskEventDto[] → TaskEvent[]`: group by `taskName`, derive status/when/duration/statusLabel; leave `attempts`/`attemptHistory`/`retryPolicy`/`caughtBy`/`caughtError`/`indent` unset.

## 3. Query hooks

- [ ] 3.1 Add `src/lib/admin-hooks.ts`: `useWorkflows()` and `useInstances(filters)` as `useInfiniteQuery` (getNextPageParam ← nextCursor, bounded limit); filters in the instances query key.
- [ ] 3.2 Add `useWorkflowDetail(name)` (versions + deployments) and `useInstanceDetail(id)` (summary + tasks) as `useQuery`, surfacing `ApiError.status` for 404 handling.

## 4. Rewire `mock-data.ts`

- [ ] 4.1 Remove the static `workflows`/`instances` data, `workflowDetails`/`instanceDetails` maps, and `getWorkflowDetail`/`getInstanceDetail`; KEEP the exported view-model types, enums, `INSTANCE_STATUSES`, and `statusClass`.

## 5. Rewire routes

- [ ] 5.1 `routes/workflows/index.tsx`: consume `useWorkflows`; drive skeleton/empty/error from query status; wire "Load more" to `fetchNextPage`/`hasNextPage`; remove `StateSwitch`.
- [ ] 5.2 `routes/workflows/$name.tsx`: consume `useWorkflowDetail`; render versions + deployment cards from live data; 404 → not-found view; remove `StateSwitch`.
- [ ] 5.3 `routes/instances/index.tsx`: consume `useInstances(filters)` with server-side workflow/status filters + cursor "Load more"; empty/error states; remove client-side table filtering for the wired path and `StateSwitch`.
- [ ] 5.4 `routes/instances/$id.tsx`: consume `useInstanceDetail`; render header + grouped task timeline; 404 → not-found; degrade rows without `attemptHistory`; remove `StateSwitch`.

## 6. Verify (dws-console)

- [ ] 6.1 `cd dws-console && pnpm lint && pnpm typecheck && pnpm build` — all green.
- [ ] 6.2 Run against a live `dws-admin`: confirm all 4 routes render real data, devtools show a cache entry per endpoint, and empty/error states appear when dws-admin is unreachable.
