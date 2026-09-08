# workflow-structural-classification Specification

## Purpose
TBD - created by archiving change phase-1-structural-classification. Update Purpose after archive.

## Requirements

### Requirement: Recursive classification into Flow and Step nodes

`V2StructuralCompiler` SHALL walk a parsed DSL 1.0 definition and produce a `CompiledNode` tree
rooted at the top-level `do` scope, preserving source order within every task list. It SHALL
classify `main`, each nested `do`, each `for`, each `try`, each `catch`, each `fork`, and each
`fork.branches` item as a `FlowNode`, and every other task kind — `set`, `switch`, `wait`,
`listen`, `emit`, `raise`, `call`, `run` — as a `StepNode`, with no exceptions.

#### Scenario: a nested try/for/catch definition classifies into five nodes under main

- **WHEN** a definition's `do` holds a `set` task `validateOrder` and a `try` task `fulfillOrder`
  whose `try` list holds a `for` task `reserveItems` containing a `call: http` task `reserveItem`,
  and whose `catch.do` holds a `set` task `markOrderFailed`
- **THEN** the compiled tree SHALL be a `main` `FlowNode` with children `validateOrder` (`StepNode`)
  and `fulfillOrder` (`FlowNode`), where `fulfillOrder` has children `reserveItems` (`FlowNode`) and
  `fulfillOrder.catch` (`FlowNode`), `reserveItems` has child `reserveItem` (`StepNode`), and
  `fulfillOrder.catch` has child `markOrderFailed` (`StepNode`)

#### Scenario: a nested do task is a Flow over its own task list

- **WHEN** a definition contains a task whose body is a `do` task list — a `do` task named
  `prepareOrder` holding a `set` task `stampOrder` and a `call: http` task `loadCustomer`
- **THEN** `prepareOrder` SHALL compile to a `FlowNode` with `scope` `do`, keeping `prepareOrder`
  as its `nodeId`, whose children SHALL be that list's nodes in source order — `stampOrder` and
  `loadCustomer`, each a `StepNode` — and SHALL NOT compile to a `StepNode` carrying a task list

#### Scenario: switch is a Step, not a Flow

- **WHEN** a definition contains a `switch` task whose cases route to other named tasks
- **THEN** that task SHALL compile to a `StepNode` with no children, and each routed-to task SHALL
  compile to its own node under the same parent flow

#### Scenario: source order within a task list is preserved

- **WHEN** a flow scope's task list declares tasks in a given order
- **THEN** that `FlowNode`'s `children()` SHALL list the corresponding nodes in the same order, and
  its rendered `tasks` array SHALL carry the task objects in that same order

### Requirement: Fork compiles to its own Flow node

A `fork` task SHALL compile to its own `FlowNode` carrying `forkMode` `all` when `compete` is false
or absent and `any` when `compete` is true, an empty `tasks` list, and one child `FlowNode` per
`fork.branches` item. Each branch `FlowNode` SHALL have `scope` `forkBranch` and SHALL hold the
branch's root task as its child node.

#### Scenario: a non-competing fork classifies as forkMode all

- **WHEN** a `fork` task `notifyChannels` declares `compete: false` with branches `notifyRecipients`
  and `writeAudit`
- **THEN** `notifyChannels` SHALL compile to a `FlowNode` with `forkMode` `all`, an empty `tasks`
  list, and exactly two child `FlowNode`s

#### Scenario: a competing fork classifies as forkMode any

- **WHEN** a `fork` task declares `compete: true`
- **THEN** its `FlowNode` SHALL carry `forkMode` `any`

#### Scenario: a branch whose root task is structural produces two nodes

- **WHEN** a fork branch `notifyRecipients` is itself a `for` task containing a `call: http` task
  `sendEmail`
- **THEN** the branch SHALL compile to a `forkBranch` `FlowNode` whose single child is a `for`
  `FlowNode`, which in turn has `sendEmail` as its child `StepNode`

#### Scenario: a branch whose root task is a leaf produces a branch flow over a step

- **WHEN** a fork branch `writeAudit` is a `set` task
- **THEN** the branch SHALL compile to a `forkBranch` `FlowNode` whose single child is a `StepNode`

### Requirement: Derived identifiers and DNS-1123 app IDs

The compiler SHALL derive each node's `nodeId` and SHALL sanitize it into that node's `appId` as a
DNS-1123 label. A scope the DSL names SHALL keep that name as its `nodeId`. Scopes the DSL does not
name SHALL derive one: `<workflow>.main` for the top-level scope, `<try-task>.catch` for a catch
scope, `<fork-task>.branch.<branch-root-task>` for a fork branch. A structural task sitting at a fork branch's
root is a named scope and keeps its own task name, so that a flow's `children` keys always match its
`tasks` entries' task names. Sanitization SHALL collapse dots and other
non-alphanumeric characters to single dashes and SHALL split camelCase boundaries.

#### Scenario: an unnamed catch scope derives its identifier from its try task

- **WHEN** a `try` task named `fulfillOrder` declares a `catch.do` list
- **THEN** the catch `FlowNode`'s `nodeId` SHALL be `fulfillOrder.catch` and its `appId` SHALL be
  `fulfill-order-catch`

#### Scenario: the main scope derives its identifier from the workflow name

- **WHEN** a workflow named `order-fulfillment` is compiled
- **THEN** the root `FlowNode`'s `nodeId` SHALL be `order-fulfillment.main` and its `appId` SHALL be
  `order-fulfillment-main`

#### Scenario: a named scope keeps its own task name

- **WHEN** a `for` task is named `reserveItems`
- **THEN** its `FlowNode`'s `nodeId` SHALL be `reserveItems` and its `appId` SHALL be
  `reserve-items`

#### Scenario: a fork branch derives its identifier from fork and branch root

- **WHEN** a `fork` task `notifyChannels` has a branch whose root task is named `notifyRecipients`
- **THEN** the branch `FlowNode`'s `nodeId` SHALL be `notifyChannels.branch.notifyRecipients` and
  its `appId` SHALL be `notify-channels-branch-notify-recipients`

#### Scenario: a structural task at a branch root keeps its own name

- **WHEN** the branch `notifyChannels.branch.notifyRecipients`'s root task is a `for` named
  `notifyRecipients`
- **THEN** that `for` `FlowNode`'s `nodeId` SHALL be `notifyRecipients` and its `appId` SHALL be
  `notify-recipients`

#### Scenario: a flow's children keys match its task names

- **WHEN** a `FlowNode` whose `scope` is not `fork` has its `specText` rendered
- **THEN** every key of its `children` object other than the dedicated `catch` key SHALL equal the
  task name of the corresponding entry in its `tasks` array, in the same order
- **AND** the dedicated `catch` child SHALL be exempt, since a catch scope hangs off the `try`
  task's `catch.do` and therefore has no entry of its own in the `try` node's `tasks` array
- **AND** a `fork` `FlowNode` SHALL be exempt, since it renders an empty `tasks` array and keys its
  `children` by each branch's root task name

#### Scenario: an app ID exceeding the DNS-1123 length limit is rejected

- **WHEN** a derived identifier sanitizes to an app ID longer than 63 characters
- **THEN** compilation SHALL fail with a `CompilationException` naming the offending `nodeId` and
  the app ID it produced, rather than truncating it

### Requirement: Per-node definition resource key

Each node SHALL carry its own `definitionResource` of the form
`dws-def-<workflow>-<versionId>-<appId>`, distinct from every other node's in the same compiled
version.

#### Scenario: each node gets its own resource key

- **WHEN** workflow `order-fulfillment` at version `v1a2b3c4d` compiles a catch node with app ID
  `fulfill-order-catch`
- **THEN** that node's `definitionResource` SHALL be
  `dws-def-order-fulfillment-v1a2b3c4d-fulfill-order-catch`

### Requirement: Naming-collision rule for call and run steps

A `StepNode` compiled from a `call` or `run` task SHALL keep the plain task-derived app ID for
itself and SHALL record its underlying function's Knative Service app ID as `functionAppId`, formed
by appending `-fn` to its own app ID. Every other `StepNode` SHALL leave `functionAppId` empty.

#### Scenario: a call step records the -fn suffixed function app ID

- **WHEN** a `call: http` task named `reserveItem` is compiled
- **THEN** its `StepNode`'s `appId` SHALL be `reserve-item` and its `functionAppId` SHALL be
  `reserve-item-fn`

#### Scenario: a run step records the -fn suffixed function app ID

- **WHEN** a `run: shell` task named `rebuildIndex` is compiled
- **THEN** its `StepNode`'s `appId` SHALL be `rebuild-index` and its `functionAppId` SHALL be
  `rebuild-index-fn`

#### Scenario: a non-IO step has no function app ID

- **WHEN** a `set`, `switch`, `wait`, `listen`, `emit`, or `raise` task is compiled
- **THEN** its `StepNode`'s `functionAppId` SHALL be empty

### Requirement: Ambiguous derived identifiers are rejected

Compilation SHALL fail when two deployables in one definition resolve to the same Dapr app ID, a
node's derived app ID is not a usable DNS-1123 label, or a task name cannot be resolved to exactly
one node. The raised `CompilationException` SHALL name the offending task or node and the derived
identifier at fault, and SHALL NOT return a partial plan.

#### Scenario: two tasks sanitizing to one app ID fail compilation

- **WHEN** a definition contains distinct tasks whose derived identifiers both sanitize to the same
  DNS-1123 app ID
- **THEN** compilation SHALL raise `CompilationException` reporting both `nodeId`s and the shared
  app ID, and SHALL NOT return a partial plan

#### Scenario: a step's function app ID colliding with a node's app ID fails compilation

- **WHEN** a definition contains a `call` or `run` task `reserveItem`, whose `functionAppId` is
  `reserve-item-fn`, alongside any other task whose own app ID is also `reserve-item-fn`
- **THEN** compilation SHALL raise `CompilationException` naming the shared app ID and
  distinguishing the node app ID from the function app ID, since both name a deployable in the one
  Dapr app-id namespace

#### Scenario: a derived app ID outside the DNS-1123 character class is rejected

- **WHEN** a task name sanitizes to an app ID containing any character outside
  `^[a-z0-9]([-a-z0-9]*[a-z0-9])?$` — for example a non-ASCII letter, which survives sanitization
- **THEN** compilation SHALL raise `CompilationException` naming the `nodeId` and the app ID it
  produced, rather than rendering a `specText` the single-node definition schema rejects

#### Scenario: a task name that sanitizes to an empty app ID is rejected

- **WHEN** a task name contains no alphanumeric character, so sanitization yields an empty app ID
- **THEN** compilation SHALL raise `CompilationException` naming that `nodeId`

#### Scenario: a dotted task name is rejected

- **WHEN** a definition contains a task whose name contains `.`
- **THEN** compilation SHALL raise `CompilationException` naming that task, since a node's `key()`
  is the last dotted segment of its `nodeId` and a dotted task name would collide with the derived
  ids the compiler synthesizes for scopes the DSL does not name

#### Scenario: two children of one flow resolving to the same key are rejected

- **WHEN** two children of one `FlowNode` resolve to the same `key()` — for example a task literally
  named `catch` inside a `try` list whose `catch.do` also produces a catch node
- **THEN** compilation SHALL raise `CompilationException` naming the shared key, rather than merging
  the two into one `children` entry and leaving one node unreachable

#### Scenario: a task item carrying more than one property is rejected

- **WHEN** a submitted document contains a task item object with more than one property, which the
  typed model silently narrows to its first
- **THEN** compilation SHALL raise `CompilationException` naming that task item, rather than
  rendering a node whose verbatim `task` smuggles in a second, node-less task

#### Scenario: a valid definition with no collisions compiles

- **WHEN** every node in a definition resolves to a distinct app ID
- **THEN** compilation SHALL succeed and the plan's `flowStepGraph` SHALL contain the full tree

### Requirement: Each node renders a valid single-node definition

Every compiled node SHALL carry a `specText` that satisfies
`openspec/schemas/single-node-definition.schema.json`. A `FlowNode`'s `specText` SHALL carry the
common envelope plus `scope`, its own source-ordered `tasks`, and a `children` object mapping each
child's `key()` to that child's `appId`; it SHALL carry `catch` when the scope has a catch block
with a non-empty `do` list and `forkMode` when the scope is `fork`. A `StepNode`'s `specText` SHALL
carry the common envelope plus its `task`, and `functionAppId` exactly when the task is a `call` or
`run`. The envelope's `nodeId` field SHALL carry the node's sanitized `appId`.

#### Scenario: a flow node renders scope, tasks, and children

- **WHEN** the `try` node `fulfillOrder` containing a `for` task `reserveItems` and an attached
  catch block is rendered
- **THEN** its `specText` SHALL declare `kind` `flow` and `scope` `try`, SHALL list the `for` task
  object in `tasks`, SHALL map `reserveItems` and `catch` to their children's app IDs in `children`,
  and SHALL set `catch` to `fulfill-order-catch`

#### Scenario: a fork node renders forkMode and an empty task list

- **WHEN** a `fork` node with `compete: false` is rendered
- **THEN** its `specText` SHALL declare `scope` `fork`, `forkMode` `all`, an empty `tasks` array,
  and one `children` entry per branch

#### Scenario: the children object is projected from each child's key

- **WHEN** a flow node's `children` object is built
- **THEN** each key SHALL be the corresponding child's `key()` value and each value SHALL be that
  child's `appId`, with no separately maintained parent-side map

#### Scenario: the envelope carries the sanitized app ID

- **WHEN** a node whose `nodeId` is `fulfillOrder.catch` is rendered
- **THEN** its `specText`'s `nodeId` field SHALL be `fulfill-order-catch`, a valid DNS-1123 label

#### Scenario: a call step renders its function app ID

- **WHEN** a `call: http` `StepNode` is rendered
- **THEN** its `specText` SHALL declare `kind` `step`, SHALL carry the task object under `task`, and
  SHALL carry its `-fn` suffixed `functionAppId`

### Requirement: The worked examples compile to fixed graphs

The compiler SHALL reproduce, exactly, the compiled graph for each of the six worked examples in
`docs/roadmaps/workflow-runtime-architecture.md` — nested try/for/catch, parallel fork branches,
state and decision steps, timing and event steps, external call and run steps, and raise and
recovery steps.

#### Scenario: every worked example matches its golden fixture

- **WHEN** each of the six worked-example definitions is compiled
- **THEN** the resulting tree shape, every node's `nodeId`, `appId`, `definitionResource`, and
  `functionAppId`, and every node's rendered `specText` SHALL match that example's checked-in
  fixture byte-for-byte

#### Scenario: every rendered node validates against the schema

- **WHEN** each node produced from the six worked examples is rendered
- **THEN** its `specText` SHALL validate against `single-node-definition.schema.json` with no
  violations
