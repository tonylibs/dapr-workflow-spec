# Design

## Context

See `proposal.md` for motivation. Settled decisions are recorded in
`docs/roadmaps/dws-console-submission.md` §8; this design explains how they fit together.

Current state in `dws-console` (TanStack Start with SSR, React 19, Tailwind 4, Vite 8):

- `components/definition-editor.tsx` owns the draft through `lib/definition-draft-store.ts`
  (Zustand `persist`, key `dws:draft`, fields `definition` and `format`). It also holds the Phase 2
  preview result (`DefinitionPreview`), which clears whenever the buffer changes.
- The console has no YAML parser. `dws-admin` parses with the `yaml` package.
- `components/definition-graph.tsx` is a hardcoded SVG used only on the workflow-detail
  "Definition" tab (`routes/workflows/$name.tsx`).
- No `@xyflow/react`, `elkjs`, or `@openworkflowspec/sdk` dependency exists.
- Verified 2026-09-26: `new Classes.Workflow(parsed)` + `buildGraph` succeeds for 10 of the 11
  `dws-controller/src/test/resources/fixtures/*.yaml`. `broken.yaml` throws inside `buildGraph`
  ("undefined is not iterable"). `run-shell`, `run-script-js`, `run-script-python`, and
  `run-script-bad-language` fail SDK 1.0.3 validation but build a graph.
- Finding for the follow-up "Definition" tab (checked 2026-09-29): `dws-admin`'s
  `workflow_definitions` table holds only `name`, `version`, `status`, `created_at`, and no read
  endpoint returns definition text. That tab needs a `dws-admin` read-model change first.

## Goals / Non-Goals

**Goals:**

- One pure conversion path, draft text → `DefinitionGraphModel`, reusable by Phase 5.
- Zero SSR impact and zero extra bytes on pages that do not show the diagram.
- Typing latency in CodeMirror unchanged in practice.

**Non-Goals:**

- No change to any cross-component contract. Task → app-id mapping, step-service HTTP behaviour,
  content-addressed versioning, and `dws-admin`/`dws-controller` validation are untouched.
- No layout persistence. Layout is recomputed on every build (the §5 persistence question stays
  open for Phase 5).
- No replacement of `definition-graph.tsx` on the workflow-detail tab in this change.

## Decisions

### D1. Pipeline: parse → guard → SDK model → `buildGraph` → our model

`lib/definition-graph-model.ts` exports one pure function, roughly
`buildDefinitionGraph(text, format): {ok: true, graph} | {ok: false, error}`:

1. Parse with `yaml` (`parse` for YAML; `JSON.parse` for JSON). Parse errors carry line/column.
2. Shape guard: the result is an object, `document` is an object, `do` is a non-empty array.
   Failures return `{ok: false}` with a readable message.
3. `new Classes.Workflow(parsed)` and `buildGraph(workflow)`, inside try/catch. Any throw becomes
   `{ok: false, error}`. Never call `Classes.Workflow.deserialize()`: it validates against DSL 1.0.3
   and rejects object-form `run` `arguments`, which DWS deploys.
4. Map the SDK graph into a small console-owned type: nodes
   (`id` = `taskReference`, `name`, `taskType`, `parentId?` for container children, `kind` =
   `task | container | start | end`) and edges (`source`, `target`, `label?`).

Owning the output type isolates the rest of the console from SDK graph-shape churn: a prerelease
bump only touches step 4. **Alternative considered:** walk the DSL ourselves. Rejected because
`switch`/`then`/`try`/`fork` semantics are exactly what `buildGraph` already encodes, and the
roadmap decision is to use it.

### D2. Exact-pinned SDK prerelease

`@openworkflowspec/sdk` is pinned to an exact `1.0.3-alphaN` version (no caret). The parity test
(D7) is the tripwire for graph-shape changes on bump. The SDK is used for its graph builder only;
its validators are never surfaced.

### D3. Rendering: `@xyflow/react` + `elkjs`

A new `components/workflow-diagram.tsx` renders our model with `@xyflow/react`. Nodes use a custom
node type styled like `wf-node-card` with `TaskTypeBadge`; containers use xyflow sub-flows
(`parentId`, `extent: "parent"`). Layout uses ELK `layered` with hierarchy handling
(`elk.hierarchyHandling: INCLUDE_CHILDREN`) so container sizes come from their children.
**Alternative considered:** `@openworkflowspec/diagram-editor`. Rejected: it validates with 1.0.3
(false error badges), has no `onChange`, ships its own shadcn/Tailwind CSS, and is about 10 MB.
**Alternative considered:** dagre. Rejected: no compound-node support.

### D4. Client-only, lazy loading

The editor imports the diagram via `React.lazy(() => import("./workflow-diagram"))`, rendered only
after mount (a `useIsClient`/`useEffect` gate) inside `Suspense` with a skeleton placeholder. The
SSR render therefore never evaluates `@xyflow/react` or `elkjs`, and Vite emits them in a separate
chunk. xyflow's stylesheet is imported inside that chunk. `lib/definition-graph-model.ts` also sits
behind the same lazy boundary, so `yaml` and the SDK do not enter the main bundle either.

### D5. ELK in a web worker

Preferred: `elkjs/lib/elk-api` with `workerFactory: () => new Worker(new URL("elkjs/lib/elk-worker.min.js", import.meta.url))`,
so Vite bundles the worker as its own asset. If this fails under Vite 8 / TanStack Start (for
example, the worker file is not ESM-compatible), fall back to `elkjs/lib/elk.bundled.js` on the
main thread and record the reason in this design and the roadmap. The layout call is async in
both cases, so the fallback is a one-line swap. A graph of tens of tasks lays out in milliseconds,
so the fallback is acceptable.

**Finding & Outcome (2026-09-29):** `elkjs/lib/elk-worker.min.js` is a classic GWT script that
is not compatible with Vite's module-worker pipeline without external worker scripts or complex
plugins. We adopted the planned fallback to `elkjs/lib/elk.bundled.js`. It runs asynchronously
within the lazily loaded `workflow-diagram` chunk on the client. Tested layouts of typical
workflows execute in under 10 ms, keeping the main thread responsive while avoiding worker
cross-origin and bundling friction.

### D6. Debounce, stale state, and error badges

- The diagram subscribes to the draft store and debounces `(definition, format)` by 300 ms before
  running D1 and layout. A layout that finishes after a newer input started is discarded (request
  sequence number).
- State is `{lastGood?: LaidOutGraph, error?: string, stale: boolean}`. On `{ok: false}` the last
  good graph stays, `stale` is set, and the error shows in a banner above the canvas.
- Error badges: the editor passes the current preview's spec errors (only while the preview is for
  the current buffer, which Phase 2 already guarantees by clearing it on change). A pure helper maps
  each `errors[].path` to the node whose `id` is the longest segment-wise prefix of that path.
  Unmatched errors badge nothing. The helper lives in the model module and is unit-tested.

### D7. Tests

- `lib/definition-graph-model.test.ts` (Vitest, node environment): fixture parity reads every
  `dws-controller/src/test/resources/fixtures/*.yaml`. Each fixture except `broken.yaml` yields
  `ok: true` with ≥1 task node. `broken.yaml` yields `ok: false` without throwing. Fixtures
  rejected only by `dws-controller` deployability (`run-container.yaml`, `run-workflow.yaml`,
  `run-script-bad-language.yaml`) still build. `order.yaml` and
  `dws-orchestrator/src/test/resources/try-order.yaml` assert container nodes for
  `try`/`catch`/`for`/`fork` and labelled `switch` edges. Also: parse error with line, missing
  `do`, JSON input, error-path → node mapping.
- Component test (jsdom): the editor renders a placeholder on first render, the diagram shows
  a stale banner after an invalid edit, and zoom/fit buttons have accessible names. ELK is mocked in
  jsdom.

### D8. Accessibility

Use xyflow's `<Controls>` (buttons, focusable, with `aria-label`s) or equivalent console buttons
with explicit `aria-label`s ("Zoom in", "Zoom out", "Fit view"). Each node sets `ariaLabel`
(`"<taskName>, <taskType> task"`) and xyflow's built-in node focus stays enabled.

### D9. Licence notice

The repo has no third-party notice file today. Add `dws-console/THIRD_PARTY_NOTICES.md` naming
elkjs, its EPL-2.0 licence, the source URL, and the statement that it is used unmodified. The
console's `Dockerfile` copies it into the image next to the built assets.

## Risks / Trade-offs

- [SDK prerelease changes graph shape] → exact pin, console-owned output type (D1 step 4), parity
  test fails loudly on bump.
- [SDK constructor validates or throws on DWS-only shapes] → verified for all fixtures on
  2026-09-26; try/catch covers the rest.
- [elkjs worker build does not work with Vite] → main-thread fallback (D5), documented.
- [Large bundle] → lazy chunk only on the editor route; measure chunk size in `pnpm build` output
  and record it in the roadmap.
- [Error-path mapping is approximate] → longest-prefix rule is deterministic; unmatched errors
  remain in the list, so no error is hidden.
- [Diagram and preview disagree] → the diagram never judges validity; it shows structure only, and
  badges come solely from `dws-admin`'s result.

## Migration Plan

Additive, client-only. Ships with the next console image. Rollback is reverting the change; there
is no data or contract migration.

## Open Questions

- Exact `@openworkflowspec/sdk` prerelease to pin: take the latest `1.0.3-alpha*` at
  implementation time that passes the parity test.
