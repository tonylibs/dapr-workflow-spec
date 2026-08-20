## Why

Roadmap Phase 3 (`docs/roadmaps/openworkflow-features.md`) is next up after Phase 2's fault surface
(`try`/`catch`/`retry`, `raise`) landed. Today `ErrorKind` invents its own type-URI namespace
(`open-workflow-specification.org/dsl/errors/types/...`) instead of the spec's real
`serverlessworkflow.io` catalogue, and covers only three of the five standard kinds — there is no
`authorization` or `expression` kind, and no way to express a timeout as a typed, catchable error.
Separately, no task or workflow ever times out: a stuck step or an infinite `wait`/`listen` blocks
the instance forever, and `CatchPolicy.rejectUnsupported()` throws rather than honoring
`retry.limit.attempt.duration` because the interpreter has no timeout mechanism to implement it
with. Both gaps close together because a timeout's natural expression *is* a `timeout` error flowing
through the `catch.errors.with`/`catch.when` machinery Phase 2 already built.

## What Changes

- **BREAKING**: `ErrorKind`'s error `type` URIs move from the invented
  `open-workflow-specification.org/dsl/errors/types/...` prefix to the spec's real
  `https://serverlessworkflow.io/spec/1.0.0/errors/...` prefix. A `catch.errors.with.type` filter
  written against the old prefix stops matching.
- Expand `ErrorKind` from 3 to the spec's standard catalogue: `validation` (400, existing),
  `communication` (502, existing), `authorization` (403, new), `expression` (400, new), `timeout`
  (408, new). `runtime` is retained as this runtime's own non-standard catch-all for failures the
  spec's catalogue does not name (unchanged 500 default) — flagged in design.md for confirmation.
  `WorkflowErrors`'s five-field shape (`type`/`status`/`instance`/`title`/`detail`) is unchanged.
- Add task-level timeout: a task declaring `timeout` (inline or via `use.timeouts`) fails with a
  `timeout` (408) error when the durable deadline elapses before the task completes, entering the
  existing `catch.errors.with`/`catch.when` path unchanged. Uncaught, it fails the task/instance
  like any other error.
- Add workflow-level timeout: a definition declaring a document-level `timeout` fails the instance
  the same way once the instance-wide deadline elapses.
- Implement `retry.limit.attempt.duration` (a per-attempt timeout inside the retry loop) and delete
  `CatchPolicy.rejectUnsupported()`, which currently throws for it.
- Consolidate the duplicated `durationOf(TimeoutAfter)` helper (identical copies in `CatchPolicy`
  and `InterpreterWorkflow`) into one shared implementation reused by both existing call sites and
  the new timeout paths.

## Capabilities

### New Capabilities
- `workflow-timeouts`: task-level and workflow-level durable timeouts, and the per-attempt retry
  timeout, all expressed as a `timeout` (408) error flowing through the existing catch machinery.

### Modified Capabilities
- `workflow-error-handling`: the error-type catalogue and its URI prefix change (breaking), and
  `retry.limit.attempt.duration` moves from an explicit unsupported-and-rejected knob to an
  implemented one. Note: this capability's spec has not yet been synced from
  `openspec/changes/try-catch-retry` into `openspec/specs/` (that change is merged to `main` but not
  yet archived) — this change's delta is written against that pending spec's content and both should
  land in `openspec/specs/` in an order that keeps the capability's requirement history coherent.

## Impact

- **Code**: `dws-orchestrator` only —
  `error/ErrorKind.java`, `error/WorkflowErrors.java` (kind catalogue + defaults),
  `workflow/activity/CatchPolicy.java` (per-attempt timeout, `durationOf` consolidation, delete
  `rejectUnsupported`), `workflow/InterpreterWorkflow.java` (task/workflow timeout wiring,
  `durationOf` consolidation). Confirmed via `WorkflowCompiler.walk()`: `dws-controller` needs no
  change — `timeout` deploys no resource and is pure orchestrator interpretation, matching the
  `raise`/`try`/`catch`/`retry` precedent.
- **Compatibility**: existing definitions with no `timeout` declared and no `catch.errors.with.type`
  filter are unaffected. A definition filtering on the old invented type-URI prefix breaks (the
  **BREAKING** item above) — no deployed workflow definition is known to rely on it yet, since
  Phase 3 is what introduces the real catalogue.
- **Non-goals**: Phase 4 (`basic`/`bearer`/`oauth2` auth, secrets) and everything after it in the
  phase dependency graph (protocol expansion, scheduling, catalogs/extensions) — out of scope here.
