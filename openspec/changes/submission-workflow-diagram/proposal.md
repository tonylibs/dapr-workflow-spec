# Proposal

## Why

The definition editor (`dws-console` route `/workflows/new`) shows operators only raw YAML/JSON
text. They cannot see the control flow a draft describes: `switch` jumps, `try`/`catch` regions,
`for` loops, and `fork` branches. `components/definition-graph.tsx` is a hardcoded, hand-positioned
SVG of one example workflow and is not connected to any real definition. Phases 1–3 of
`docs/roadmaps/dws-console-submission.md` are done, so the draft buffer the diagram reads from
exists. Phase 4 (workflow diagram) is the next roadmap phase, and Phase 5 (visual editor) builds on
it. The design is settled in §8 of that roadmap.

## What Changes

- Add a live, automatically laid-out **execution/control-flow diagram** of the current draft to the
  definition editor page. It updates as the operator types, debounced to about 300 ms.
- `switch` cases and `then` jumps render as edges. `try`, `catch.do`, `for`, and `fork` bodies
  render as compound container nodes that hold their nested tasks.
- Add a pure, React-free definition → graph model module (`lib/definition-graph-model.ts`). It
  parses the buffer, applies a minimal shape guard, and builds the graph with
  `@openworkflowspec/sdk`'s `buildGraph` over `new Classes.Workflow(parsedObject)`. It never calls
  `Classes.Workflow.deserialize()` and never surfaces the SDK's DSL 1.0.3 validation errors.
- Node IDs are the SDK's JSON-pointer `taskReference` (for example `/do/1/approve`). Phase 2
  validation errors (`errors[].path`) show as badges on the matching node.
- On an unparseable or malformed buffer, keep the last good graph with a "stale" indicator and
  show the parse or build error. The page never crashes.
- Render with `@xyflow/react` + `elkjs` (layered, compound nodes), loaded client-only and lazily.
  Run ELK in a web worker where the elkjs worker build works with Vite.
- Keyboard-reachable zoom/fit controls; nodes carry accessible names.
- New dependencies in `dws-console`: `@openworkflowspec/sdk` (exact-pinned prerelease),
  `@xyflow/react`, `elkjs` (EPL-2.0, used unmodified, with a third-party licence notice), and
  `yaml` (client-side parse).
- Fixture-parity test: every `dws-controller` fixture that compiles today yields a non-empty graph;
  `broken.yaml` yields a handled error. `order.yaml` and `dws-orchestrator`'s `try-order.yaml`
  cover `try`/`catch`/`for`/`fork`/`switch`.
- Not breaking. No wire contract, DSL behaviour, deployed resource, or runtime interpretation
  changes.

## Capabilities

### New Capabilities

- `console-workflow-diagram`: the `dws-console` definition editor's live, read-only
  execution/control-flow diagram of the draft buffer, including stale-graph handling, validation
  error badges on nodes, client-only loading, and keyboard accessibility.

### Modified Capabilities

None. `console-definition-submission` (editor, preview, submit) and
`console-definition-draft-management` (import, persisted draft) keep their requirements unchanged.
The diagram only reads the draft buffer and the latest validation result they already produce.

## Impact

- **Component:** `dws-console` only. Its independent build and gate (`pnpm check`,
  `pnpm typecheck`, `pnpm test`, `pnpm build`) are unchanged in shape.
- **Code:** new `src/lib/definition-graph-model.ts` (+ tests), a new lazily loaded client-only
  diagram component, a layout helper (ELK, worker where possible), and wiring in
  `src/components/definition-editor.tsx`. `components/definition-graph.tsx` stays on the
  workflow-detail "Definition" tab for now.
- **Dependencies:** `@openworkflowspec/sdk` (exact version), `@xyflow/react`, `elkjs`, `yaml`.
  A third-party notice file is added for elkjs's EPL-2.0 licence.
- **DSL behaviour:** none. Spec validation stays in `dws-admin` (DSL 1.0.1, SDK
  `serverlessworkflow-types-7.26.0.Final`); deployability stays in `dws-controller`. The DSL
  version pin is untouched.
- **Deployed resources / runtime:** none. No change to `dws-controller`, `dws-orchestrator`,
  `dws-admin`, step services, or content-addressed versioning.
- **Compatibility:** every definition the editor accepts today still loads, previews, and submits
  exactly as before. Definitions DWS accepts but DSL 1.0.3 rejects (object-form `run.shell` /
  `run.script` `arguments`, `run-script-bad-language.yaml`) still render.
- **Non-goals:** structural Flow/Step view (future, from the `dws-controller` dry-run plan); canvas
  editing (Phase 5); live instance status on nodes; the workflow/instance "Definition" tab
  (`dws-admin` stores no definition text today); fixing the ADR-0003 `fork` drift in
  `docs/roadmaps/workflow-visual-model.md`; any DSL 1.0.3 upgrade.
