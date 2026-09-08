# workflow-compiler-strategy Specification

## Purpose
Defines how `dws-controller` selects between a v1 (legacy) and a v2 (structural) workflow compiler
behind a stable `WorkflowCompiler` interface, driven by a Dapr Configuration flag that defaults to
v1, over an additively widened `DeploymentPlan`. This is the ADR 0002 seam that lets the v2
structural compiler be built without changing v1's behavior, and it fixes the compatibility
contract: v1 populates only the legacy plan fields, v2 populates only `flowStepGraph`, never both.

## Requirements

### Requirement: WorkflowCompiler strategy interface

The `dws-controller` SHALL expose workflow compilation behind an interface
`WorkflowCompiler` declaring exactly `DeploymentPlan compile(String specText)`, with two
implementations: `V1OrchestratorCompiler` (the existing v1 compile pass, moved verbatim) and
`V2StructuralCompiler` (the v2 structural pass). Every consumer, including `WorkflowResource`,
SHALL depend only on the `WorkflowCompiler` interface type, so the implementation can change
without touching callers.

#### Scenario: v1 compilation is behavior-preserving

- **WHEN** any definition that compiled successfully before the refactor is compiled by
  `V1OrchestratorCompiler`
- **THEN** the resulting `DeploymentPlan`'s legacy fields (`workflow`, `versionId`, `version`,
  `definitionResource`, `specText`, `steps`, `bindings`, `orchestrator`, `oauthEndpoints`,
  `bindingComponents`) SHALL be byte-for-byte identical to the pre-refactor output for that
  definition.

#### Scenario: callers are unchanged by the split

- **WHEN** the compiler is split into an interface plus two strategies
- **THEN** `WorkflowResource` and every other consumer SHALL keep a field of type
  `WorkflowCompiler` and SHALL require no source change.

#### Scenario: an invalid definition still fails the same way

- **WHEN** a definition that previously raised `CompilationException` is compiled by
  `V1OrchestratorCompiler`
- **THEN** it SHALL raise `CompilationException` with the same error content as before the refactor.

### Requirement: Additively widened DeploymentPlan with a Flow/Step graph

`DeploymentPlan` SHALL be widened additively with a field `List<CompiledNode> flowStepGraph`
that defaults to empty and leaves all existing fields and their meanings unchanged.
`CompiledNode` SHALL be a sealed interface permitting `FlowNode` (a node with `children()`) and
`StepNode` (a leaf whose `children()` returns an empty list), each exposing `nodeId()`, `appId()`,
`definitionResource()`, `specText()`, and `children()`, plus a default `key()` deriving this
node's label from the last dotted segment of `nodeId()` and a default `flatten()` returning this
node and all its descendants in pre-order. The graph SHALL carry Jackson polymorphic-type
configuration so it serializes on the `/plan` dry-run endpoint.

#### Scenario: existing DeploymentPlan construction keeps compiling

- **WHEN** existing code constructs a `DeploymentPlan` without supplying `flowStepGraph`
- **THEN** the plan SHALL be created with an empty `flowStepGraph` and SHALL require no call-site change.

#### Scenario: the sealed graph serializes with a type discriminator

- **WHEN** a `DeploymentPlan` whose `flowStepGraph` contains `FlowNode` and `StepNode` values is
  serialized for the dry-run endpoint
- **THEN** each node SHALL serialize with a type discriminator that distinguishes `FlowNode` from
  `StepNode`, and SHALL deserialize back to the same node type.

#### Scenario: key() derives the node label from nodeId()

- **WHEN** a node has `nodeId()` `"fulfillOrder.catch"`
- **THEN** its `key()` SHALL return `"catch"`, and **WHEN** a node's `nodeId()` has no dot **THEN**
  `key()` SHALL return the whole `nodeId()`.

#### Scenario: flatten() walks the tree in pre-order

- **WHEN** `flatten()` is called on a `FlowNode` with children
- **THEN** it SHALL return that node first, followed by each child's own flattened nodes in
  `children()` order, and **WHEN** called on a `StepNode` **THEN** it SHALL return that node alone.

### Requirement: Strategy selection via Dapr Configuration, default v1

`CompilerProducer` SHALL select the compiler strategy by reading a compiler-version flag from a
Dapr Configuration store via the Configuration API (`getConfiguration`), backed by a
`configuration.redis` Component named `dws-controller-config` scoped to `dws-controller` only.
It SHALL produce `V1OrchestratorCompiler` by default and `V2StructuralCompiler` only when the flag
selects v2. Any missing store, missing key, absent sidecar, or fetch error SHALL resolve to v1.

#### Scenario: default is v1 when the flag is absent

- **WHEN** the controller starts with no compiler-version flag set (or no configuration store reachable)
- **THEN** `CompilerProducer` SHALL produce `V1OrchestratorCompiler`.

#### Scenario: v2 is produced only on explicit opt-in

- **WHEN** the compiler-version flag resolves to the v2 value
- **THEN** `CompilerProducer` SHALL produce `V2StructuralCompiler`.

#### Scenario: the config Component is scoped to the controller only

- **WHEN** the `dws-controller-config` `configuration.redis` Component is rendered by the chart
- **THEN** its `scopes` SHALL list `dws-controller` only, mirroring how `dws-definitions` is scoped
  to `dws-orchestrator` only.

### Requirement: Strategy compatibility contract

The two strategies SHALL never populate overlapping regions of `DeploymentPlan`.
`V1OrchestratorCompiler` SHALL populate only the legacy fields and leave `flowStepGraph` empty.
`V2StructuralCompiler` SHALL populate `flowStepGraph` and the shared identity fields (`workflow`,
`versionId`, `version`, `definitionResource`, `specText`), computing each identity field exactly as
`V1OrchestratorCompiler` computes it, and SHALL leave the legacy `steps`, `bindings`,
`orchestrator`, `oauthEndpoints`, and `bindingComponents` fields empty.

#### Scenario: v1 leaves the graph empty

- **WHEN** `V1OrchestratorCompiler` compiles any definition
- **THEN** the resulting `DeploymentPlan.flowStepGraph` SHALL be empty.

#### Scenario: v2 leaves legacy fields empty

- **WHEN** `V2StructuralCompiler` compiles any definition
- **THEN** the resulting `DeploymentPlan` SHALL leave `steps`, `bindings`, `orchestrator`,
  `oauthEndpoints`, and `bindingComponents` empty and SHALL populate `flowStepGraph`.

#### Scenario: both strategies agree on workflow identity

- **WHEN** the same definition is compiled by `V1OrchestratorCompiler` and by
  `V2StructuralCompiler`
- **THEN** both plans SHALL carry identical `workflow`, `versionId`, `version`,
  `definitionResource`, and `specText` values.

#### Scenario: the compiler default is unchanged

- **WHEN** the controller starts with no `compiler.version` flag set
- **THEN** `CompilerProducer` SHALL still produce `V1OrchestratorCompiler`, unaffected by the v2
  compiler gaining a working compile pass.
