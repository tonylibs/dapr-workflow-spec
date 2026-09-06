<!--
Raw capture of the design exploration for the Phase 1 compiler seam.
The design space here was already explored and settled in an *Accepted* ADR
(docs/adr/0002-workflow-compiler-strategy-split.md, with 0001 and 0003 for
context). This brainstorm captures that decision log as-is rather than
re-litigating decisions the ADR already fixed; design.md reorganizes it.
-->

# Brainstorm — Phase 1 compiler strategy split (the seam)

## Background

`WorkflowCompiler` in `dws-controller` is today a single concrete class with one
entry point, `DeploymentPlan compile(String specText)`. It is wired by a CDI
producer (`CompilerProducer`) and consumed only as `compiler.compile(...)` in
`WorkflowResource` (POST deploy, `?dryRun`, and GET `/{name}/plan`).
`StackSynthesizer` reads the resulting `DeploymentPlan` 1:1 to render Kubernetes
objects.

Phase 1 of the workflow-runtime-v2 roadmap needs to add a second, structurally
different compile pass (a Flow/Step graph) *alongside* the existing one. The
roadmap's Phase 5 already assumes "run v1 and v2 side by side behind a controller
flag" is possible. That seam has to exist before any v2 classification code can
be written, otherwise the first line of new compiler code either breaks v1 or has
nowhere to live. ADR 0002 fixes how the seam is built; this change is that ADR's
recommended *first* Phase 1 task — a pure refactor, no classification logic.

## Decision chain (from ADR 0002, Accepted 2026-09-04)

### Q1 — How do v1 and v2 coexist without v1 changing?
**Decision:** `WorkflowCompiler` becomes an interface (`DeploymentPlan
compile(String specText)`, identical signature). The current class body is
renamed verbatim to `V1OrchestratorCompiler implements WorkflowCompiler` — a
copy-paste rename, zero behavior change. A new empty `V2StructuralCompiler
implements WorkflowCompiler` stub holds Phase 1's future graph logic.
**Why:** keeping the interface named `WorkflowCompiler` means `WorkflowResource`
and every other caller's field type is unchanged — the split is invisible to all
consumers while only v1 is wired.
**Rejected:** rewriting/cleaning up v1's compiler while moving it. v1's code may
be gnarly; touching it risks the byte-for-byte-identical output requirement. Move
it untouched; flag suspected bugs separately, never fix in this refactor.

### Q2 — Where does the v1/v2 selection flag live?
**Decision:** a Dapr Configuration resource, read via the Configuration API
(`DaprClient.getConfiguration`), not a Quarkus config property or env var. A new
`configuration.redis` Component `dws-controller-config`, scoped to
`dws-controller` only, holds the compiler-version key(s). `CompilerProducer`
reads the flag and produces the matching strategy, default `v1`.
**Why:** mirrors the precedent this repo already set for `dws-definitions`
(`charts/dws/templates/definitions-component.yaml`) — a scoped
`configuration.redis` Component read via the Configuration API. Scoping to
`dws-controller` only isolates a slow Redis warm-up from unrelated sidecars,
exactly as `dws-definitions` is scoped to `dws-orchestrator` only. A k/v store
(not a single flag) can carry a global default or per-workflow overrides later
with no schema change.
**Rejected:** Quarkus `@ConfigProperty`/env var (doesn't match repo precedent,
can't be flipped without redeploy); committing now to `subscribeConfiguration`
live updates (needs `CompilerProducer`'s `@ApplicationScoped` singleton wiring
reworked to re-check per call — left as a later Phase 1 choice).

### Q3 — How does `DeploymentPlan` carry the v2 graph?
**Decision:** widen `DeploymentPlan` additively with `List<CompiledNode>
flowStepGraph` (default empty). Existing fields keep their exact v1 meaning. The
graph is a Composite over a sealed interface:

```
sealed interface CompiledNode permits FlowNode, StepNode {
  String nodeId(); String appId(); String definitionResource(); String specText();
  List<CompiledNode> children();
  default String key() { ... last dotted segment of nodeId() ... }
}
record FlowNode(nodeId, appId, definitionResource, specText, children) implements CompiledNode {}
record StepNode(nodeId, appId, definitionResource, specText, functionAppId) implements CompiledNode {
  children() -> List.of()   // leaf
}
```

**Why a tree, not a flat list:** v2's classification pass is a recursive descent
into nested scopes (try/for/catch/fork), so the tree is its natural output and
matches the Phase 0 schema's own shape (a `flow` has `children`, a `step` does
not). A `flatten()` pre-order walk later gives `StackSynthesizer` its flat
"one Deployment per node" view without the compiler materializing one.
**Why `children()` is a plain `List`, not a `Map<String,CompiledNode>`:**
dispatch is decided by the child's sealed type alone (StepNode → callActivity,
FlowNode → callChildWorkflow), never by key lookup; and a child's key is derived
from its own `nodeId()` via `key()`, not assigned by the parent. The wire-format
keyed `children` object is a serialization-time projection
(`toMap(CompiledNode::key, CompiledNode::appId)`), not a stored structure.

### Q4 — Serialization of the sealed graph?
**Decision:** `CompiledNode`/`FlowNode`/`StepNode` need Jackson polymorphic-type
config (`@JsonTypeInfo` + `@JsonSubTypes`) so the `/plan` dry-run endpoint
serializes them cleanly — unlike the rest of `DeploymentPlan`'s plain records.
**Why:** a sealed interface with two record impls won't round-trip through Jackson
without a type discriminator; the dry-run endpoint already serializes the whole
`DeploymentPlan`.

### Q5 — The compatibility contract
**Decision (fixed by the ADR):**
- `V1OrchestratorCompiler` populates only the legacy fields; `flowStepGraph` empty.
- `V2StructuralCompiler` populates only `flowStepGraph`; legacy fields empty.
- No field is ever populated by both strategies.
- `DeploymentPlan`'s shape is append-only for the migration's duration.

## Verification stance (the one hard requirement)

v1's `DeploymentPlan` output must be byte-for-byte identical before and after the
refactor for every existing fixture (and, if easy, a couple of real specTexts).
Capture golden JSON of the pre-refactor plan (legacy fields), re-run the same
fixtures through `V1OrchestratorCompiler` after, diff → zero diff on legacy
fields. The only intended serialization delta is the new `flowStepGraph: []`
field appearing, empty, under v1. Do not improve, reorder, or simplify any v1
logic while moving it.

## Out of scope (later Phase 1 work)

Classification rules, derived-identifier sanitization, fork handling, the `-fn`
naming-collision handling from ADR 0001, and golden tests against the spec's 5
worked examples. This change builds only the seam.
