## Context

`dws-console` is a TanStack Start (React 19, Vite, Biome) operator UI. Phases 1–2 built the workflow
browser and instance monitor screens, but they render static data from `src/lib/mock-data.ts`. The
`QueryClient` is provided in `src/integrations/tanstack-query/root-provider.tsx` and the SSR-query
integration is set up in `src/router.tsx`, yet no route calls `useQuery` — Phase 2.5 is the first
live wiring.

`dws-admin` (NestJS) owns the read API (`openspec/specs/admin-read-api/spec.md`): six read-only
endpoints, validated DTOs, and a uniform cursor page envelope `{ items, nextCursor }`. Its DTOs
supply the primitive fields; the console's mock view models additionally carry *presentation-derived*
and *richer* fields the API does not return. A field-level diff (captured in brainstorm.md) shows the
swap is **not 1:1** — an adapter layer is required.

Constraint: reuse the existing UI kit (`states.tsx`, `skeleton.tsx`, `status.tsx`,
`data-table.tsx`); do not build new state components. Console-only change; no dws-admin/DSL edits.

## Goals / Non-Goals

**Goals:**
- All 4 read routes render live `dws-admin` data via TanStack Query.
- A single typed API client + DTO→view-model adapters isolate every mapping and the base URL.
- Cursor pagination on the two list endpoints, wired to the existing "Load more" controls.
- Loading/empty/error/404/400 states driven by query status through the existing components.
- Base URL configurable via env var; nothing hardcoded.
- The adapter layer is unit-tested (test-first) under `vitest`.
- `pnpm lint && pnpm typecheck && pnpm test && pnpm build` green.

**Non-Goals:**
- `GET /health` consumption; live polling/push (Phase 3); definition submission (Phase 4); auth
  (Phase 5).
- Rich retry/attempt/catch visualization beyond flat `task_events` (no API source).
- OpenAPI-generated typed client; route-loader SSR prefetch.
- Any dws-admin, controller, orchestrator, or step-service change.

## Decisions

### D1: Typed client + per-endpoint hooks in `src/lib/`, types retained
- **Choice:** A small `src/lib/admin-client.ts` (fetch + `adminUrl()` + `ApiError`) and
  `src/lib/admin-hooks.ts` (`useWorkflows`, `useWorkflowDetail`, `useInstances`,
  `useInstanceDetail`) plus adapters. `mock-data.ts` loses its data/lookups but **keeps** the
  exported view-model types and `statusClass`/enum exports, so route JSX barely changes.
- **Rationale:** centralizes base-URL/error/pagination logic; minimal churn in routes.
- **Alternatives:** inline fetch per route (duplication) — rejected; OpenAPI-generated client
  (heavier, DTOs are few/stable) — deferred.

### D2: Group `task_events` per task; degrade rich fields gracefully
- **Choice:** Adapter groups `TaskEventDto[]` by `taskName` → one `TaskEvent` row (matches built UI).
  Row `status` from the terminal event (`started→running`, `completed→completed`, `failed→failed`);
  `when` = first-event offset from instance start; `duration` = last−first timestamp;
  `statusLabel` derived. Fields with no API source (`attempts`, `attemptHistory`, `retryPolicy`,
  `caughtBy`, `caughtError`, `indent`) stay `undefined`; the timeline's `getRowCanExpand` keys off
  `attemptHistory`, so rows simply don't expand.
- **Rationale:** faithful to available data without fabrication; reuses existing render code.
- **Alternatives:** one row per raw event (diverges from UI); synthesize attempt data (fabrication)
  — both rejected. Richer visualization needs richer `task_events` → future phase.

### D3: Adapter formats ISO timestamps to the UI's string fields
- **Choice:** `formatRelative()`/`formatAbsolute()` helpers convert DTO `date-time` into the
  relative/absolute strings routes already render (`updated`, `started`, `created`, …). Derived
  numeric fields (`taskCount`, `failedCount`, `retries`, instance `duration`) computed in the
  instance-detail adapter from tasks + timestamps. `note` (no source) → empty/"—".
- **Rationale:** keeps route JSX untouched; all presentation mapping in one place.

### D4: Base URL from `VITE_DWS_ADMIN_URL`
- **Choice:** `adminUrl(path)` reads `import.meta.env.VITE_DWS_ADMIN_URL` (dev default; empty →
  same-origin). Documented in `.env.example`.
- **Alternatives:** hardcoded host (rejected — requirement); runtime `/config.json` (overkill).

### D5: `useInfiniteQuery` for lists; single high-limit page for detail sub-lists
- **Choice:** `/workflows` and `/instances` use `useInfiniteQuery`,
  `getNextPageParam = last => last.nextCursor ?? undefined`, bounded `limit`. "Load more" →
  `fetchNextPage`, disabled via `hasNextPage`. Instance filters (`workflow`, `status`) become query
  params in the query key so filtering refetches server-side (replacing client-side table filtering
  for the wired path). Versions/deployments/tasks fetch one page at max `limit` (still envelope-aware);
  auto-paging deferred.
- **Rationale:** matches the cursor-only API and the existing pager UI; keeps detail simple.

### D6: Query status drives states; demo `StateSwitch` removed from wired routes
- **Choice:** `isPending`→skeleton, empty items→`EmptyState`, `isError`→`Banner` (list) or
  not-found `EmptyState` (detail). `ApiError.status === 404`→not-found view; `400`→warn `Banner`.
  Remove `StateSwitch` from the 4 routes; keep the component exported.
- **Rationale:** real states, per `states.tsx`'s own guidance.

### D7: Component-level hooks (no loader prefetch yet)
- **Choice:** `useQuery`/`useInfiniteQuery` inside components; the router SSR-query integration
  handles hydration. Loader `ensureQueryData` prefetch deferred.
- **Rationale:** smallest first wiring; verify wants visible devtools cache entries.

### D9: Same-origin calls via a dev proxy; never retry a 4xx
- **Choice:** Both decisions came out of running the console against a real dws-admin.
  (a) dws-admin serves no `Access-Control-Allow-Origin`, so a browser cannot call it cross-origin at
  all. `vite.config.ts` proxies a `/dws-admin` prefix to `DWS_ADMIN_PROXY_TARGET` in development and
  `VITE_DWS_ADMIN_URL` is set to that prefix, keeping every request same-origin; deployments put the
  console and dws-admin behind one ingress for the same reason.
  (b) TanStack Query retries a failed query three times by default, which left the not-found view
  hidden behind backing-off retries after a `404`. `retryUnlessClientError` retries transport and
  5xx failures but never a 4xx.
- **Rationale:** keeps the change console-only — enabling CORS would mean editing dws-admin, which
  this change explicitly does not touch — and makes 404/400 outcomes render immediately (measured
  ~0.3–1s, previously a multi-second apparent hang).
- **Alternatives:** enabling CORS in dws-admin (out of scope, and a same-origin deployment is
  preferable anyway); a blanket `retry: false` (would also give up on transient transport blips).

### D8: Add `vitest` and test the adapter layer first
- **Choice:** Add `vitest` as a devDependency with a `test` script, and write the adapter unit tests
  before the adapter implementations (RED→GREEN). Coverage targets the pure functions:
  timestamp/status normalization, each DTO→view-model map, the instance-detail derivations, and the
  `task_events` grouping. The client, hooks, and route components are not unit-tested — they are
  covered by `typecheck`/`build` plus the manual live-run in the verify step.
- **Rationale:** `dws-console` had no test runner, and the adapter layer is exactly where this change
  concentrates its risk (field renames, derived values, lifecycle-status collapsing). Pure functions
  make it cheap; no DOM environment or component-testing stack is needed.
- **Alternatives:** no tests at all (leaves the riskiest logic unguarded) — rejected; full component
  testing with `@testing-library/react` + a DOM environment — deferred, disproportionate for a first
  wiring.

### D10: Speak the read model's status vocabulary, not the mockups'
- **Choice:** The view-model status unions are the literals `dws-admin` actually writes —
  definitions `created`/`updated`, deployments `applied`/`failed`/`drained`/`collected`,
  instances and task events `started`/`completed`/`failed` (see
  `dws-admin/src/events/controller-events.handler.ts` and `orchestrator-events.handler.ts`).
  `statusClass` maps each to a hue and `INSTANCE_STATUSES` offers exactly the three the instance
  filter can match.
- **Rationale:** the unions inherited from the Phase 1–2 mockups (`DEPLOYED`, `ACTIVE`, `RUNNING`)
  never existed in the read model. Against live data every status pill would have rendered
  unrecognized in the neutral hue, and every filter chip would have returned zero rows, because
  dws-admin compares `?status=` case-sensitively against the stored value.
- **Alternatives:** translating stored values into the mockups' labels (`created` → `DEPLOYED`) —
  rejected: `created`/`updated` describe a *definition* record, not a deployment state, so the
  mapping would assert something the read model does not know.
- **Guard:** `admin-adapters.test.ts` asserts every stored status maps to a non-neutral hue and that
  the filter chips equal the stored instance vocabulary, so a future divergence fails the suite.

## Risks / Trade-offs

- [Risk] `task_events` grouping assumes each task emits a coherent started→terminal sequence; a
  missing/duplicate lifecycle event could mislabel a row. → Mitigation: take the last event as
  terminal status and guard empty groups; acceptable for read-only display.
- [Risk] Instance `duration`/counts are client-derived, not authoritative. → Mitigation: derive
  only from returned tasks/timestamps; document as presentational. A server-provided summary is a
  later enhancement.
- [Trade-off] Rich retry/catch UI goes dark until dws-admin emits richer events. → Accepted: no
  fabrication; UI degrades cleanly (no expansion).
- [Trade-off] Detail sub-lists fetch one page (high limit) instead of full auto-paging. → Accepted:
  version/deployment/task counts are small in practice; revisit if truncation appears.
- [Risk] A production build has no dev-server proxy, so the deployed console needs the console and
  dws-admin behind one origin (or CORS enabled on dws-admin). → Mitigation: `.env.example` documents
  the same-origin default; the ingress arrangement is a deployment task for a later phase.
- [Risk] SSR fetch runs against `VITE_DWS_ADMIN_URL` on the server too; an in-cluster vs browser URL
  split could surface later. → Mitigation: Phase 2.5 targets a single reachable URL; document the
  env var. Split-URL config is out of scope.
- [Risk] Status enum casing mismatches (DTO free `string` vs typed unions) could slip bad values into
  badge components. → Mitigation: adapter normalizes/asserts known enum values; unknown → pending/fallback.

## Migration Plan

Console-only, no deployment/DB/endpoint change on the backend. Rollout:
1. Land client + adapters + hooks; rewire the 4 routes; add `.env.example`.
2. `pnpm lint && pnpm typecheck && pnpm build` green.
3. Run `dws-console` against a live `dws-admin`; confirm each route renders real data and devtools
   show per-endpoint cache entries; confirm empty/error states when dws-admin is unreachable.

Rollback: revert the console change; the mock-data prototype returns. No data migration, no backend
coordination.

## Open Questions

- The Workflows list "Status" column now shows a definition-record status (`created`/`updated`),
  which tells an operator nothing about whether that version is deployed or drained — deployment
  state lives in `GET /workflows/:name/deployments`. Should the list column instead derive from the
  latest deployment (a second request per row, or a new dws-admin field)? This is a product
  decision, not a wiring one, and is left open.
- Should instance `duration`/counts eventually come from a server-side summary DTO rather than being
  client-derived? (Deferred; not blocking Phase 2.5.)
- Will a separate in-cluster vs browser base URL be needed once the console is deployed? (Phase 5/deploy.)
