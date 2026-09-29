# Tasks

## 1. Roadmap and dependencies

- [x] 1.1 Add "§8 Phase 4 design" to `docs/roadmaps/dws-console-submission.md` and verify it records decisions 1–6, the `dws-admin` definition-text finding, and the follow-ups
- [x] 1.2 Add `@openworkflowspec/sdk` (exact prerelease pin, no caret), `@xyflow/react`, `elkjs`, and `yaml` to `dws-console/package.json`; verify `pnpm install` succeeds and the lockfile updates
- [x] 1.3 Add `dws-console/THIRD_PARTY_NOTICES.md` for elkjs (EPL-2.0, unmodified, source URL) and copy it in the `Dockerfile`; verify it is present in the built image layout

## 2. Pure graph model

- [x] 2.1 Create `src/lib/definition-graph-model.ts` with parse (YAML/JSON), shape guard, `new Classes.Workflow(parsed)` + `buildGraph` in try/catch, and mapping to a console-owned node/edge type keyed by `taskReference`; verify it has no React import
- [x] 2.2 Add the error-path → node helper (longest segment-wise prefix); verify with unit tests for task-level, nested, and document-level paths
- [x] 2.3 Add the fixture-parity test over all `dws-controller/src/test/resources/fixtures/*.yaml`; verify every fixture but `broken.yaml` yields a non-empty graph and `broken.yaml` yields a handled error
- [x] 2.4 Add control-flow tests for `order.yaml` and `dws-orchestrator/src/test/resources/try-order.yaml`; verify container nodes for `try`/`catch`/`for`/`fork` and edges for `switch` jumps
- [x] 2.5 Add tests for parse errors (line/column), missing `do`, and JSON input; verify `pnpm test` passes

## 3. Layout and rendering

- [x] 3.1 Add an ELK layout helper using the elkjs worker build via `new URL(..., import.meta.url)`; verify the worker chunk appears in `pnpm build` output, or fall back to `elk.bundled.js` and record why in `design.md` and the roadmap
- [x] 3.2 Create `src/components/workflow-diagram.tsx` rendering the laid-out graph with `@xyflow/react`, `wf-node-card` styled nodes with `TaskTypeBadge`, and sub-flow containers; verify visually on the editor page with `order.yaml`
- [x] 3.3 Add 300 ms debounce, stale-result discard, last-good-graph retention, and the stale banner with the parse/build error; verify with a component test that an invalid edit keeps the graph and shows the banner
- [x] 3.4 Add keyboard-reachable zoom-in/zoom-out/fit controls with `aria-label`s and per-node accessible names; verify with a component test querying by role and name

## 4. Editor integration

- [x] 4.1 Mount the diagram in `src/components/definition-editor.tsx` via `React.lazy` behind a client-only gate with a skeleton fallback; verify `pnpm build` emits xyflow/elkjs in a separate chunk and the SSR render shows the placeholder
- [x] 4.2 Pass the current preview's spec errors to the diagram and render node badges; verify badges appear for a task-level error and clear on the next buffer change

## 5. Gates and docs

- [x] 5.1 Run `pnpm check`, `pnpm typecheck`, `pnpm test`, `pnpm build` in `dws-console`; verify all pass
- [x] 5.2 Update `docs/roadmaps/dws-console-submission.md` Phase 4 row and "Current progress", plus `docs/roadmaps/README.md` and `docs/roadmaps/dws-console.md` where they mention Phase 4; verify the rows say done with the date
- [ ] 5.3 Sync the Notion mirror page "Console Definition Submission Roadmap", the "Roadmaps — Overview" row, and the "DWS Roadmap Tracker" row "Phase 4 — Workflow diagram" (In Progress → Done, with notes); verify each page shows the new status

## Outstanding

- **5.3** writes to the external Notion roadmap mirror and tracker database. Left for explicit user sync or manual paste since the Notion connector is unauthenticated in this environment — the repository-side documentation (`docs/roadmaps/dws-console-submission.md`, `docs/roadmaps/dws-console.md`, `docs/roadmaps/README.md`) is complete and is the source the mirror should be synced from:
  - Notion "Console Definition Submission Roadmap" (`https://app.notion.com/p/3c92f73e4fd981988252dcbff0736f60`):
    - Update Dependency Graph: `Phase 4: Workflow diagram` → `Phase 4: Workflow diagram ✅ 2026-09-29`
    - Update Phased roadmap table: Phase 4 row status → `✅ done 2026-09-29 — submission-workflow-diagram; execution-view graph with @xyflow/react + elkjs, see §8`
    - Update "Current progress": update with `Current progress (2026-09-29)` summarizing Phase 4 completion
    - Update Status legend: `Updated 2026-09-29`
  - Notion "Roadmaps — Overview":
    - Update `dws-console definition submission` row: current phase reflects Phase 4 done on 2026-09-29
  - Notion "DWS Roadmap Tracker":
    - "Phase 4 — Workflow diagram" row: Status `In Progress` → `Done`
    - Notes: Live execution-view graph with `@xyflow/react` + `elkjs` (`elk.bundled.js`), pure graph model in `lib/definition-graph-model.ts` with shape guard and error-path mapping from `@openworkflowspec/sdk` `1.0.3-alpha8`, debounced live preview in editor with stale-result banner and accessible zoom/fit controls.
