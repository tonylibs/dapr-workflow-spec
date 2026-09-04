# ADR 0002: WorkflowCompiler as Two Strategies Over a Widened DeploymentPlan

- **Status:** Accepted
- **Date:** 2026-09-04
- **Context:** [`docs/roadmaps/workflow-runtime-architecture-roadmap.md`](../roadmaps/workflow-runtime-architecture-roadmap.md)
  — Phase 1 (structural compiler) is next up; this ADR fixes how it gets built without breaking v1.
- **Related:** [ADR 0001](0001-workflow-runtime-v2-decisions.md) (per-node app IDs, uniform Step
  transport — the target-state decisions this one builds a migration path for)

## Context

`WorkflowCompiler` today is a single concrete class with one public entry point,
`DeploymentPlan compile(String specText)`, wired by a CDI producer (`CompilerProducer`) and
consumed only as `compiler.compile(...)` in `WorkflowResource`. `StackSynthesizer` reads the
resulting `DeploymentPlan` 1:1 to render Kubernetes objects (`orchestratorDeployment`,
`knativeServices`, etc.).

Phase 1 needs to add a second, structurally different compile pass (the Flow/Step graph) alongside
the existing one, and the roadmap's Phase 5 entry already assumes this is possible — "run v1 and
v2 side by side behind a controller flag" — without specifying the mechanism. That mechanism has
to exist before Phase 1 can start, otherwise the first line of new compiler code either breaks v1
or has nowhere to live alongside it.

## Decision

Split `WorkflowCompiler` into an interface plus two concrete strategies, and widen `DeploymentPlan`
additively so both strategies can return it.

**1. `WorkflowCompiler` becomes an interface**: `DeploymentPlan compile(String specText)`, same
signature as today. The current class body is renamed to `V1OrchestratorCompiler implements
WorkflowCompiler` — a pure rename, zero behavior change. A new `V2StructuralCompiler implements
WorkflowCompiler` holds Phase 1's classification/graph logic as it's built.

**2. `CompilerProducer` is the one selection point, reading the flag off a Dapr Configuration
resource — not a Quarkus config property or env var.** This follows the precedent this repo
already set for `dws-definitions` (`charts/dws/templates/definitions-component.yaml`): a
`configuration.redis` Component is the Dapr Configuration API's backing store, and an app reads
it via `DaprClient.getConfiguration(...)` rather than local config. Concretely: a new
`configuration.redis` Component (working name `dws-controller-config`, scoped to `dws-controller`
only — same isolation reason `dws-definitions` is scoped to `dws-orchestrator` only, so a slow
Redis warm-up can't fatally crash an unrelated sidecar) holds the compiler-version key(s);
`CompilerProducer` fetches it via the Configuration API and produces whichever strategy, default
`v1` until Phase 5 cutover. Because it's a k/v store rather than a single flag, the same mechanism
can carry either one global default key or per-workflow override keys later without a schema
change — which key(s) to actually use is still open, see Non-goals. Because the interface keeps
the `WorkflowCompiler` name, `WorkflowResource` and every other caller's field type
(`WorkflowCompiler compiler`) doesn't change — only `CompilerProducer`'s wiring does. This is the
concrete implementation of the roadmap's Phase 5 "controller flag," decided now instead of
deferred to Phase 5. The Configuration API also supports `subscribeConfiguration` for live
updates — worth a look for flipping the flag without redeploying `dws-controller`, though that
needs `CompilerProducer`'s current `@ApplicationScoped` singleton-strategy wiring reworked to
re-check on each `compile()` call rather than freeze the strategy at startup; not committed to
here, left as a Phase 1 implementation choice.

**3. `DeploymentPlan` is widened, not replaced.** Existing fields (`orchestrator`, `steps`,
`bindings`, `oauthEndpoints`, `bindingComponents`) keep their current v1 meaning, unchanged, for as
long as v1 is selected. New fields are added to carry the compiled Flow/Step node graph for v2.
Compatibility contract (fixed by this ADR; the graph's actual data model is Phase 1 design work,
not pinned here):
- `V1OrchestratorCompiler` populates only the legacy fields; new v2 fields are left empty.
- `V2StructuralCompiler` populates only the new fields; legacy `orchestrator`/`steps` are left
  empty.
- No field is ever populated by both strategies. No consumer of the legacy fields needs to change
  while v1 is selected.

**4. The same pattern repeats one layer down at `StackSynthesizer` in Phase 4** — not a second,
different mechanism. `StackSynthesizer` gets the same interface/two-strategy split, consuming the
same widened `DeploymentPlan`, once Phase 4 needs to render the Flow/Step graph into Kubernetes
objects.

## Consequences

- Zero caller-side changes anywhere while only v1 is wired — the interface keeps the
  `WorkflowCompiler` name specifically to make this true.
- `DeploymentPlan`'s widened shape must be treated as append-only for the duration of the
  migration: no repurposing or removing legacy fields until Phase 5 cutover, same "v1 keeps
  working throughout" precedent ADR 0001 Decision 2 already set for the function images.
- This ADR does not pin the Flow/Step graph's actual node/edge types, the Configuration store's
  exact Component name or key naming, whether the flag is global-only or also per-workflow, or
  whether `CompilerProducer` polls vs. subscribes — those are Phase 1 (and Phase 4, for the
  synthesizer side) implementation decisions. It only fixes the compatibility contract (one
  interface, additive schema, mutually exclusive population) and the storage mechanism (Dapr
  Configuration API, not local app config).
- Recommended as the *first* Phase 1 task (a pure refactor — rename the existing class, extract the
  interface, add the empty `V2StructuralCompiler` stub and the new `DeploymentPlan` fields) before
  any classification-rule logic lands, so the rest of Phase 1 can build incrementally against a
  seam that already isolates it from v1.

## Non-goals

Doesn't change Phase 1's actual scope (classification rules, derived-identifier sanitization, the
`-fn` naming-collision handling from ADR 0001, golden tests against the spec's 5 worked examples) —
those are unaffected and still ahead. Doesn't decide the Flow/Step graph's data model, the
Configuration store's Component name or key naming, or the flag's scoping (global vs.
per-workflow) — only that it lives on a Dapr Configuration resource, read via the Configuration
API, not local app config.
