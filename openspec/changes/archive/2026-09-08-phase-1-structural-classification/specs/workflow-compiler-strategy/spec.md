## MODIFIED Requirements

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
