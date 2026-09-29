# Tasks

## 1. Roadmap and dependencies

- [x] 1.1 Add "§8 Phase 4 design" to `docs/roadmaps/dws-console-submission.md` and verify it records decisions 1–6, the `dws-admin` definition-text finding, and the follow-ups
- [ ] 1.2 Add `@openworkflowspec/sdk` (exact prerelease pin, no caret), `@xyflow/react`, `elkjs`, and `yaml` to `dws-console/package.json`; verify `pnpm install` succeeds and the lockfile updates
- [ ] 1.3 Add `dws-console/THIRD_PARTY_NOTICES.md` for elkjs (EPL-2.0, unmodified, source URL) and copy it in the `Dockerfile`; verify it is present in the built image layout

## 2. Pure graph model

- [ ] 2.1 Create `src/lib/definition-graph-model.ts` with parse (YAML/JSON), shape guard, `new Classes.Workflow(parsed)` + `buildGraph` in try/catch, and mapping to a console-owned node/edge type keyed by `taskReference`; verify it has no React import
- [ ] 2.2 Add the error-path → node helper (longest segment-wise prefix); verify with unit tests for task-level, nested, and document-level paths
- [ ] 2.3 Add the fixture-parity test over all `dws-controller/src/test/resources/fixtures/*.yaml`; verify every fixture but `broken.yaml` yields a non-empty graph and `broken.yaml` yields a handled error
- [ ] 2.4 Add control-flow tests for `order.yaml` and `dws-orchestrator/src/test/resources/try-order.yaml`; verify container nodes for `try`/`catch`/`for`/`fork` and edges for `switch` jumps
- [ ] 2.5 Add tests for parse errors (line/column), missing `do`, and JSON input; verify `pnpm test` passes

## 3. Layout and rendering

- [ ] 3.1 Add an ELK layout helper using the elkjs worker build via `new URL(..., import.meta.url)`; verify the worker chunk appears in `pnpm build` output, or fall back to `elk.bundled.js` and record why in `design.md` and the roadmap
- [ ] 3.2 Create `src/components/workflow-diagram.tsx` rendering the laid-out graph with `@xyflow/react`, `wf-node-card` styled nodes with `TaskTypeBadge`, and sub-flow containers; verify visually on the editor page with `order.yaml`
- [ ] 3.3 Add 300 ms debounce, stale-result discard, last-good-graph retention, and the stale banner with the parse/build error; verify with a component test that an invalid edit keeps the graph and shows the banner
- [ ] 3.4 Add keyboard-reachable zoom-in/zoom-out/fit controls with `aria-label`s and per-node accessible names; verify with a component test querying by role and name

## 4. Editor integration

- [ ] 4.1 Mount the diagram in `src/components/definition-editor.tsx` via `React.lazy` behind a client-only gate with a skeleton fallback; verify `pnpm build` emits xyflow/elkjs in a separate chunk and the SSR render shows the placeholder
- [ ] 4.2 Pass the current preview's spec errors to the diagram and render node badges; verify badges appear for a task-level error and clear on the next buffer change

## 5. Gates and docs

- [ ] 5.1 Run `pnpm check`, `pnpm typecheck`, `pnpm test`, `pnpm build` in `dws-console`; verify all pass
- [ ] 5.2 Update `docs/roadmaps/dws-console-submission.md` Phase 4 row and "Current progress", plus `docs/roadmaps/README.md` and `docs/roadmaps/dws-console.md` where they mention Phase 4; verify the rows say done with the date
- [ ] 5.3 Sync the Notion mirror page "Console Definition Submission Roadmap", the "Roadmaps — Overview" row, and the "DWS Roadmap Tracker" row "Phase 4 — Workflow diagram" (In Progress → Done, with notes); verify each page shows the new status
