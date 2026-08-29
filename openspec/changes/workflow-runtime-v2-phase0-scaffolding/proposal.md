## Why

Today `dws-orchestrator` interprets an entire workflow definition in-process, and only `call`/`run`
tasks are farmed out to prebuilt Knative functions. The Workflow Runtime Architecture spec
(`docs/roadmaps/workflow-runtime-architecture.md`) and its Implementation Roadmap
(`docs/roadmaps/workflow-runtime-architecture-roadmap.md`) define a target-state rewrite where
every compiled Flow scope and Step task becomes its own deployable, cross-app-invoked component.
ADR 0001 (`docs/adr/0001-workflow-runtime-v2-decisions.md`) resolved the two structural design
questions blocking that work — one Dapr app per graph node, and a uniform plain-HTTP transport for
every step's underlying function image, including the `-fn` app-ID naming rule that falls out of
it. This change is Phase 0 of the roadmap: it scaffolds the two new per-node-deployable component
templates (`dws-flow`, `dws-step`) the rest of the roadmap builds on, and defines the single-node
definition JSON contract `dws-controller` will hand each deployed instance starting in Phase 1.
Nothing in this change touches `dws-controller`'s compiler, `dws-orchestrator`, or any existing
`dws-call-*`/`dws-run-*` image — v1 keeps running unchanged; `dws-flow`/`dws-step` are scaffolded
alongside it, not wired into any deployment path yet.

## What Changes

- Add two new top-level components, sibling to the existing ones per root `CLAUDE.md`:
  - **`dws-flow`** (.NET, Dapr Workflow SDK) — a generic per-node Flow host. One running instance
    hosts exactly one compiled Flow scope (`main`, a `for`, a `try`, a `catch`, or a fork branch) as
    its single registered workflow type, loaded from a pinned single-node definition at startup —
    the same "generic image, pinned definition" shape `dws-orchestrator` already uses today,
    narrowed to one node instead of the whole workflow.
  - **`dws-step`** (Java 25, Spring Boot, Dapr Workflow SDK) — a generic per-node Step host. One
    running instance hosts exactly one compiled Step task (a `set`/`switch`/`wait`/`listen`/`emit`/
    `raise`, or a proxy `call`/`run` task) as its single registered Activity, loaded from the same
    kind of pinned single-node definition.
- Define the single-node definition JSON contract both components consume: a node's own task list
  (for `dws-flow`) or its own task (for `dws-step`), plus its children's resolved target Dapr app
  IDs — language-neutral, produced by `dws-controller`'s compiler in a later phase, but the schema
  and both runtimes' parsing of it land now so Phases 1–3 can build against a fixed shape.
- Both components boot with **no real task execution logic**. This phase proves the skeleton
  (definition loading and validation, health check, empty dispatch loop) compiles, starts, and
  fails fast on a malformed or missing definition. Actual `CallActivityAsync`/
  `CallChildWorkflowAsync` dispatch (Phase 3) and Activity implementations (Phase 2) are out of
  scope here.
- **Out of scope**: `dws-controller`'s structural compiler pass (Phase 1), any Dapr app deployment
  or synthesis (Phase 4), any change to `dws-orchestrator` or the six prebuilt function images, and
  CI workflows for the two new components (deferred — see Impact).

## Capabilities

### New Capabilities
- `dws-flow-scaffold`: the `dws-flow` .NET project itself — project layout, Dapr Workflow SDK
  wiring, single-node definition loading at startup, health endpoint, local dev instructions.
- `dws-step-scaffold`: the `dws-step` Java/Spring project itself — Maven layout, Dapr Workflow SDK
  (Java) wiring, single-node definition loading at startup, health endpoint, local dev instructions.
- `single-node-definition-contract`: the JSON schema and shared semantics for the per-node
  definition payload `dws-controller` will hand each `dws-flow`/`dws-step` instance — field shape,
  validation rules, and the fail-fast behavior both runtimes must implement against it.

### Modified Capabilities
<!-- None. This phase adds two new components and a new contract; it does not change
     dws-controller, dws-orchestrator, or any dws-call-*/dws-run-* behavior. -->

## Impact

- **New components**: `dws-flow/` (.NET project, Dockerfile, README), `dws-step/` (Maven project,
  Dockerfile, README).
- **New shared artifact**: single-node definition JSON Schema, checked in under
  `openspec/schemas/single-node-definition.schema.json` and referenced by both new components'
  parsers.
- **New dependencies**: .NET SDK + `Dapr.Workflow`/`Dapr.Client` NuGet packages for `dws-flow`;
  Java 25 + Spring Boot + the Dapr Java Workflow SDK (`io.dapr:dapr-sdk-workflows`) for `dws-step`,
  mirroring `dws-orchestrator`'s existing Dapr Workflow Java dependency.
- **No changes** to `dws-controller`, `dws-orchestrator`, any `dws-call-*`/`dws-run-*` image, or any
  deployed resource — both new components are dead code (not built into any image CI pushes, not
  deployed by any chart) until a later phase wires them in.
- **Build/verification caveat**: this device has no local .NET SDK and no standalone Maven/JDK 25,
  so `dws-flow` could not be `dotnet build`-verified locally, and `dws-step` could only be checked
  for structural correctness, not a full `mvnw verify` — see `tasks.md` for what was and wasn't
  verified. A CI workflow (deferred, see below) becomes the first real build gate for both.
- **CI**: a path-filtered GitHub Actions workflow for `dws-flow`/`dws-step` is explicitly deferred
  to a later task, not this change — matching how `dws-admin`'s skeleton change deferred its own CI
  wiring.
