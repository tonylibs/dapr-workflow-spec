## Why

`dws-flow` registers a placeholder workflow, so no v2 `main` or `do` node can execute. Phase 3a gives
the Flow host a real Sequencer so that Phases 3b to 3g can add controller behavior on a working core,
and so that Step and Flow children can be exercised end to end against stand-ins without waiting for
Phase 2.

## What Changes

**FlowWorkflow**
- From: no-op placeholder workflow.
- To: a Sequencer that runs `main`/`do` task lists in source order, resolves `then`, calls each child
  by type (Step activity or Flow child workflow), enforces task `timeout`, and propagates child
  failures unchanged.
- Reason: core of the v2 runtime.
- Impact: non-breaking; `dws-flow` is not deployed yet.

**Controller scopes**
- From: accepted by the loader, silently no-op.
- To: routed through one dispatch point and failing with a "not implemented yet" configuration failure.

## Capabilities

### New Capabilities
- `dws-flow-sequencer`: Sequencer behavior of the Flow host: ordering, `then`, child dispatch, instance
  IDs, envelope, failure propagation, task timeout, controller-scope routing, replay safety.

### Modified Capabilities

## Impact

`dws-flow/` only (`src/`, `test/`, README/CLAUDE.md notes). No change to `dws-step`, `dws-controller`,
the schema, roadmaps or deployment. Non-goals: `for`, `try-catch`/`retry`, `fork`, `wait`, `listen`,
data-flow transforms, path-filtered CI, `switch` directive transport.
