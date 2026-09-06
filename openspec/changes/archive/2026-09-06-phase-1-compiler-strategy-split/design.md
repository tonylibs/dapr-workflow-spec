## Context

`dws-controller` compiles Open Workflow Specification DSL 1.0 definitions into a
`DeploymentPlan` (the pure compile pass) which `StackSynthesizer` renders 1:1 into
Kubernetes/Dapr/Knative objects. Today `WorkflowCompiler` is a single concrete
class wired by `CompilerProducer` and consumed as `compiler.compile(specText)` in
`WorkflowResource` (POST deploy, `?dryRun=true`, GET `/{name}/plan`).

Phase 1 of the workflow-runtime-v2 roadmap introduces a structurally different
compile pass — a recursive Flow/Step graph. ADR 0002 (Accepted) fixes how that v2
pass coexists with v1; ADR 0001 sets the target-state decisions v2 builds toward;
ADR 0003 shows fork fits the same node shape. This change is ADR 0002's recommended
*first* Phase 1 task: build the seam, no classification logic.

The single hard constraint: v1's compiled `DeploymentPlan` output is byte-for-byte
identical before and after, for every existing fixture. v1's compiler code is not
cleaned up, reordered, or simplified while it moves; suspected bugs are flagged, not
fixed here.

## Goals / Non-Goals

**Goals:**
- Extract `WorkflowCompiler` as an interface `DeploymentPlan compile(String specText)`.
- Rename the current class body verbatim to `V1OrchestratorCompiler implements WorkflowCompiler`.
- Add an empty `V2StructuralCompiler implements WorkflowCompiler` stub returning a plan
  with only `flowStepGraph` set (empty).
- Widen `DeploymentPlan` additively with `List<CompiledNode> flowStepGraph` (default empty)
  and the sealed `CompiledNode`/`FlowNode`/`StepNode` graph, Jackson-serializable.
- Have `CompilerProducer` read a v1/v2 flag from a scoped `configuration.redis` Component
  via the Dapr Configuration API, default v1, and produce the matching strategy.
- Prove zero legacy-field diff via golden JSON, and keep the existing suite green.

**Non-Goals:**
- v2 classification rules, derived-identifier sanitization, the ADR 0001 `-fn` naming,
  fork handling, golden tests against the spec's 5 worked examples.
- Any change to `WorkflowResource`, `StackSynthesizer`, `StackApplier`, or other callers.
- Committing to `subscribeConfiguration` live flag updates (left a later Phase 1 choice).

## Decisions

### D1: `WorkflowCompiler` becomes an interface, current body renamed verbatim
- **Choice:** new `interface WorkflowCompiler { DeploymentPlan compile(String specText); }`.
  Copy the entire existing class body into `V1OrchestratorCompiler implements WorkflowCompiler`,
  changing only the class name and constructor name — no logic edits.
- **Rationale:** keeping the interface named `WorkflowCompiler` makes the split invisible to
  every caller's field type. A verbatim move is the only way to guarantee identical output.
- **Alternatives considered:** rewriting/tidying v1 during the move — rejected, risks the
  byte-for-byte requirement; an abstract base class — rejected, an interface is the minimal seam.

### D2: `V2StructuralCompiler` is an empty stub honoring the contract
- **Choice:** returns a `DeploymentPlan` with legacy fields empty and only `flowStepGraph` set
  (empty list) — no classification yet.
- **Rationale:** establishes the contract "v2 populates only `flowStepGraph`" from day one so the
  next Phase 1 tasks fill it in without touching the seam.
- **Alternatives:** throwing `UnsupportedOperationException` — rejected, the producer must be able
  to instantiate and return it when the flag says v2, and the dry-run endpoint must serialize it.

### D3: `DeploymentPlan` widened additively with a sealed Composite graph
- **Choice:** add `List<CompiledNode> flowStepGraph` as the last record component, defaulting to
  empty via a compatibility constructor so all existing `new DeploymentPlan(...)` call sites keep
  compiling unchanged. `CompiledNode` is a sealed interface permitting record `FlowNode` (with
  children) and record `StepNode` (leaf, `children()` → `List.of()`), with `key()` a default method
  deriving this node's label from `nodeId()`'s last dotted segment.
- **Rationale:** matches ADR 0002 exactly. A tree (not a flat list) is v2's natural output; a plain
  `List` for children (not a keyed map) because dispatch is decided by sealed type and a child's key
  is derived from its own `nodeId()`.
- **Alternatives:** replacing legacy fields — rejected, breaks v1 and every consumer; a flat list —
  rejected, doesn't match the recursive classification pass or the Phase 0 schema shape.

### D4: Jackson polymorphic-type config on `CompiledNode`
- **Choice:** annotate `CompiledNode` with `@JsonTypeInfo(use = NAME, property = "nodeType")` and
  `@JsonSubTypes({FlowNode, StepNode})` so the `/plan` dry-run endpoint serializes the sealed graph
  with a type discriminator.
- **Rationale:** a sealed interface with two record impls won't round-trip through Jackson without a
  discriminator; the endpoint already serializes the whole `DeploymentPlan`. Under v1 the list is
  empty so no discriminator is emitted, preserving legacy output aside from the empty array.
- **Alternatives:** custom serializer — rejected, the annotation pair is the idiomatic minimum.

### D5: `CompilerProducer` selects strategy from a Dapr Configuration flag, default v1
- **Choice:** add a `configuration.redis` Component `dws-controller-config`, scoped to
  `dws-controller` only, in the controller chart (mirroring `definitions-component.yaml`'s scoping
  for `dws-definitions`). `CompilerProducer` injects the `DaprClient` (already produced by
  `DaprClientProducer`), reads the compiler-version key via `getConfiguration`, and produces
  `V1OrchestratorCompiler` unless the value is `v2`. Any missing store/key/sidecar or error →
  default v1. No other caller changes.
- **Rationale:** ADR 0002's chosen mechanism and the repo's established precedent for
  `dws-definitions`. Scoping to `dws-controller` keeps a slow Redis warm-up from crashing unrelated
  sidecars. A k/v store carries a global or per-workflow key later with no schema change.
- **Alternatives:** Quarkus config property / env var — rejected by ADR 0002 (no repo precedent,
  needs redeploy to flip); `subscribeConfiguration` live updates now — deferred, needs the
  producer's `@ApplicationScoped` singleton wiring reworked to re-check per call.

## Risks / Trade-offs

- [Risk] A subtle logic edit slips in during the class move and changes v1 output →
  Mitigation: copy the body verbatim; prove it with golden JSON of every compilable fixture plus
  inline OAuth/asyncapi/grpc cases, diffing legacy fields for zero delta.
- [Risk] Adding a record component breaks existing `new DeploymentPlan(...)` call sites →
  Mitigation: append the component and add a compatibility constructor defaulting `flowStepGraph`
  to empty, so no call site changes.
- [Trade-off] The dry-run JSON gains an empty `flowStepGraph: []` under v1 → accepted; ADR 0002
  explicitly treats the field as "present but empty under v1", and no consumer reads it.
- [Risk] `CompilerProducer` fetching config at startup fails when no sidecar/store is present (tests,
  local) → Mitigation: catch and default to v1; never let flag resolution fail a deploy.

## Migration Plan

Additive and non-breaking; no rollout ordering required.
- The new `dws-controller-config` Component is created by the chart; absent it, the controller
  defaults to v1, so old and new charts interoperate.
- Rollback: revert the change; `DeploymentPlan`'s extra field and the interface split are
  append-only and unused by v1, so nothing downstream depends on them yet.
- Acceptance: existing suite green; golden legacy-field diff empty for every fixture; new Component
  + producer wiring compiles and defaults to v1; `DeploymentPlan`/`CompiledNode` serialize on the
  dry-run endpoint with the new field present but empty under v1.

## Open Questions

- Exact configuration key name(s) and whether the flag is global-only or per-workflow — ADR 0002
  leaves this open; this change uses a single global key (working name `compiler.version`) defaulting
  to v1, which the k/v store can extend later without a schema change.
- Poll vs. `subscribeConfiguration` for live flips — deferred to a later Phase 1 task.
