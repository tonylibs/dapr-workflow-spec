## Why

The workflow-runtime-v2 roadmap's Phase 1 needs to grow a second, structurally
different compile pass (the Flow/Step graph) alongside today's `WorkflowCompiler`
without breaking v1. Right now `WorkflowCompiler` is a single concrete class, so
the first line of v2 code would either mutate v1 or have nowhere to live. ADR
0002 fixes the seam that isolates the two; this change builds that seam and
nothing else, so the rest of Phase 1 can land incrementally against it. It is a
pure refactor: v1's compiled `DeploymentPlan` output must be byte-for-byte
identical before and after.

## What Changes

**`WorkflowCompiler` type**
- From: a single concrete class `WorkflowCompiler` with `DeploymentPlan compile(String)`.
- To: an interface `WorkflowCompiler` with the same method; the current class body
  renamed verbatim to `V1OrchestratorCompiler implements WorkflowCompiler`, plus an
  empty `V2StructuralCompiler implements WorkflowCompiler` stub.
- Reason: let v2 be built without touching v1; keep the interface name so no caller changes.
- Impact: non-breaking. `WorkflowResource` and every other caller keep `WorkflowCompiler compiler`.

**`DeploymentPlan` shape**
- From: legacy fields only (`steps`, `bindings`, `orchestrator`, `oauthEndpoints`, `bindingComponents`, …).
- To: additively widened with `List<CompiledNode> flowStepGraph` (default empty), a
  Composite over the sealed `CompiledNode`/`FlowNode`/`StepNode` graph from ADR 0002,
  with Jackson polymorphic-type config so it serializes on the `/plan` dry-run endpoint.
- Reason: both strategies must return the same `DeploymentPlan` type.
- Impact: non-breaking; v1 leaves `flowStepGraph` empty. The only serialization delta
  under v1 is the new empty `flowStepGraph: []`.

**Strategy selection**
- From: `CompilerProducer` unconditionally builds the one compiler.
- To: `CompilerProducer` reads a v1/v2 flag from a new scoped `configuration.redis`
  Dapr Component (`dws-controller-config`) via the Configuration API and produces the
  matching strategy, default v1.
- Reason: ADR 0002's chosen mechanism, mirroring `dws-definitions`.
- Impact: non-breaking; defaults to v1 with or without the Component present.

**Compatibility contract:** `V1OrchestratorCompiler` populates only legacy fields;
`V2StructuralCompiler` populates only `flowStepGraph`; never both.

## Capabilities

### New Capabilities
- `workflow-compiler-strategy`: the controller selects between a v1 and a v2 workflow
  compiler behind a stable `WorkflowCompiler` interface, driven by a Dapr Configuration
  flag defaulting to v1, over an additively widened `DeploymentPlan`.

### Modified Capabilities
- (none — no existing spec's requirements change; v1 behavior is preserved exactly)

## Impact

- Code: `dws-controller` `compile` package (`WorkflowCompiler`, new
  `V1OrchestratorCompiler`, `V2StructuralCompiler`, `CompilerProducer`) and `model`
  package (`DeploymentPlan`, new `CompiledNode`/`FlowNode`/`StepNode`).
- Deploy: new `charts/dws/templates/controller/config-component.yaml`
  (`configuration.redis`, scoped to `dws-controller`).
- Consumers unchanged: `WorkflowResource`, `StackSynthesizer`, `StackApplier`.
- Out of scope: v2 classification rules, identifier sanitization, fork handling,
  golden tests against the spec's 5 worked examples.
