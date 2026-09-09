## MODIFIED Requirements

### Requirement: Recursive classification into Flow and Step nodes

`V2StructuralCompiler` SHALL walk a parsed DSL 1.0 definition and produce a `CompiledNode` tree
rooted at the top-level `do` scope, preserving source order within every task list. Every `FlowNode`
SHALL be one of exactly two shapes: a **sequencer** (`scope` `main` or `do`), which owns its own task
list in source order and carries no scope-specific configuration; or a **controller** (`scope` `for`,
`try-catch`, or `fork`), which owns that scope's configuration, renders an empty `tasks` list, and
delegates each task list it owns to a `do`-shaped child. It SHALL classify `main`, each nested `do`,
each `for`, each `try`, each `fork`, and every other task kind — `set`, `switch`, `wait`, `listen`,
`emit`, `raise`, `call`, `run` — as a `StepNode`, with no exceptions.

#### Scenario: a nested try/for/catch definition classifies into five nodes under main

<!-- This change grows the shape to nine nodes (see body) but keeps the original scenario name —
     an archive-time delta apply requires a MODIFIED requirement to preserve every scenario name
     the current spec already has. -->

- **WHEN** a definition's `do` holds a `set` task `validateOrder` and a `try` task `fulfillOrder`
  whose `try` list holds a `for` task `reserveItems` containing a `call: http` task `reserveItem`,
  and whose `catch.do` holds a `set` task `markOrderFailed`
- **THEN** the compiled tree SHALL be a `main` `FlowNode` with children `validateOrder` (`StepNode`)
  and `fulfillOrder` (`FlowNode`, `scope` `try-catch`, controller), where `fulfillOrder` has children
  `try` (`fulfillOrder.try`, `FlowNode`, `scope` `do`, sequencer) and `catch` (`fulfillOrder.catch`,
  `FlowNode`, `scope` `do`, sequencer); `fulfillOrder.try` has child `reserveItems` (`FlowNode`,
  `scope` `for`, controller); `reserveItems` has child `do` (`reserveItems.do`, `FlowNode`, `scope`
  `do`, sequencer); `reserveItems.do` has child `reserveItem` (`StepNode`); and `fulfillOrder.catch`
  has child `markOrderFailed` (`StepNode`) — 9 nodes total, up from 7 under the prior shape

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

- **WHEN** a sequencer's task list declares tasks in a given order
- **THEN** that `FlowNode`'s `children()` SHALL list the corresponding nodes in the same order, and
  its rendered `tasks` array SHALL carry the task objects in that same order. A controller's rendered
  `tasks` array SHALL always be empty; source order for the list it owns SHALL instead be preserved
  on its `do`-shaped child

#### Scenario: a for scope is a controller carrying its own loop configuration

- **WHEN** a `for` task `reserveItems` declares `each: item`, `in: .items`, and a `do` list holding a
  `call: http` task `reserveItem`
- **THEN** `reserveItems` SHALL compile to a `FlowNode` with `scope` `for`, an empty `tasks` list,
  `each` `item` and `in` `.items` on its own rendered `specText`, and exactly one child keyed `do`
  (`nodeId` `reserveItems.do`) whose own children hold `reserveItem`

#### Scenario: a try scope is a controller carrying its own error filter and retry policy

- **WHEN** a `try` task `processPayment` declares a guarded list, a `catch.errors.with.status` filter
  of `402`, and a `catch.do` recovery list
- **THEN** `processPayment` SHALL compile to a `FlowNode` with `scope` `try-catch`, an empty `tasks`
  list, `errors` `{ "with": { "status": 402 } }` on its own rendered `specText`, and two children:
  `try` (`processPayment.try`, holding the guarded list) and `catch` (`processPayment.catch`,
  holding the recovery list)

### Requirement: Fork compiles to its own Flow node

A `fork` task SHALL compile to its own `FlowNode` carrying `forkMode` `all` when `compete` is false
or absent and `any` when `compete` is true, an empty `tasks` list, and one child per `fork.branches`
item. Each child SHALL be that branch's root task's own compiled node, classified exactly as it would
be at any other position in the definition — a fork SHALL NOT introduce an intermediate node between
itself and a branch root.

#### Scenario: a non-competing fork classifies as forkMode all

- **WHEN** a `fork` task `notifyChannels` declares `compete: false` with branches `notifyRecipients`
  and `writeAudit`
- **THEN** `notifyChannels` SHALL compile to a `FlowNode` with `forkMode` `all`, an empty `tasks`
  list, and exactly two children keyed `notifyRecipients` and `writeAudit`

#### Scenario: a competing fork classifies as forkMode any

- **WHEN** a `fork` task declares `compete: true`
- **THEN** its `FlowNode` SHALL carry `forkMode` `any`

#### Scenario: a branch whose root task is structural produces two nodes

<!-- Retitled in substance by this change — the branch now produces exactly one node (the for node
     itself), not two ("forkBranch" wrapper + for). Kept under the original scenario name because
     an archive-time delta apply requires a MODIFIED requirement to preserve every scenario name
     the current spec already has. -->

- **WHEN** a fork branch `notifyRecipients` is itself a `for` task containing a `call: http` task
  `sendEmail`
- **THEN** the fork's `children` map SHALL key `notifyRecipients` directly to a `for` `FlowNode`
  (`scope` `for`, controller), which in turn has `sendEmail` as a descendant `StepNode` — with no
  `forkBranch` node in between

#### Scenario: a branch whose root task is a leaf produces a branch flow over a step

<!-- Retitled in substance by this change — no "branch flow" wrapper exists any more; the branch
     produces the leaf StepNode directly. Kept under the original scenario name for the same
     archive-time preservation reason as above. -->

- **WHEN** a fork branch `writeAudit` is a `set` task
- **THEN** the fork's `children` map SHALL key `writeAudit` directly to a `StepNode`, dispatched by a
  caller as `CallActivityAsync` rather than `CallChildWorkflowAsync` — with no `forkBranch` node in
  between

### Requirement: Derived identifiers and DNS-1123 app IDs

The compiler SHALL derive each node's `nodeId` and SHALL sanitize it into that node's `appId` as a
DNS-1123 label. A scope the DSL names SHALL keep that name as its `nodeId`. Scopes the DSL does not
name SHALL derive one: `<workflow>.main` for the top-level scope, `<try-task>.try` for a try
controller's guarded-list child, `<try-task>.catch` for a try controller's catch child, `<for-task>
.do` for a for controller's loop-body child. A fork's children keep their own task names — a fork
introduces no derived identifier of its own beyond the fork task's name itself, and the
`<fork-task>.branch.<branch-root-task>` identifier is retired along with `forkBranch`. Sanitization
SHALL collapse dots and other non-alphanumeric characters to single dashes and SHALL split camelCase
boundaries.

#### Scenario: an unnamed catch scope derives its identifier from its try task

- **WHEN** a `try` task named `fulfillOrder` declares a `catch.do` list
- **THEN** the catch `FlowNode`'s `nodeId` SHALL be `fulfillOrder.catch` and its `appId` SHALL be
  `fulfill-order-catch`

#### Scenario: an unnamed try-body scope derives its identifier from its try task

- **WHEN** a `try` task named `fulfillOrder` declares a guarded task list
- **THEN** the guarded-list `FlowNode`'s `nodeId` SHALL be `fulfillOrder.try` and its `appId` SHALL be
  `fulfill-order-try`

#### Scenario: an unnamed loop-body scope derives its identifier from its for task

- **WHEN** a `for` task named `reserveItems` declares a `do` list
- **THEN** the loop-body `FlowNode`'s `nodeId` SHALL be `reserveItems.do` and its `appId` SHALL be
  `reserve-items-do`

#### Scenario: the main scope derives its identifier from the workflow name

- **WHEN** a workflow named `order-fulfillment` is compiled
- **THEN** the root `FlowNode`'s `nodeId` SHALL be `order-fulfillment.main` and its `appId` SHALL be
  `order-fulfillment-main`

#### Scenario: a named scope keeps its own task name

- **WHEN** a `for` task is named `reserveItems`
- **THEN** its `FlowNode`'s `nodeId` SHALL be `reserveItems` and its `appId` SHALL be
  `reserve-items`

#### Scenario: a fork branch derives its identifier from fork and branch root

<!-- Retitled in substance by this change — fork now derives no identifier for a branch at all; the
     branch root keeps its own task name unchanged. Kept under the original scenario name for the
     archive-time preservation reason noted above. -->

- **WHEN** a `fork` task `notifyChannels` has a branch whose root task is named `notifyRecipients`
- **THEN** that branch's root node's `nodeId` SHALL be `notifyRecipients` and its `appId` SHALL be
  `notify-recipients` — not `notifyChannels.branch.notifyRecipients`, since fork introduces no
  intermediate node or derived identifier of its own for a branch

#### Scenario: a structural task at a branch root keeps its own name

- **WHEN** a fork branch `notifyChannels`'s root task is a `for` named `notifyRecipients`
- **THEN** that `for` `FlowNode`'s `nodeId` SHALL be `notifyRecipients` and its `appId` SHALL be
  `notify-recipients`, exactly as it would be classified anywhere else in the definition — a fork
  branch position confers no different naming behavior

#### Scenario: a flow's children keys match its task names

<!-- Retitled in substance by this change — the rule now has a controller exception (role keys, not
     task names). Kept under the original scenario name for the archive-time preservation reason
     noted above. -->

- **WHEN** a sequencer (`main` or `do`) `FlowNode` has its `specText` rendered
- **THEN** every key of its `children` object SHALL equal the task name of the corresponding entry
  in its `tasks` array, in the same order
- **AND** a `for` `FlowNode`'s single child SHALL be keyed `do`, and a `try-catch` `FlowNode`'s
  children SHALL be keyed `try` and (when present) `catch` — role keys, not task names, since a
  controller renders an empty `tasks` array
- **AND** a `fork` `FlowNode` SHALL key its `children` by each branch's own root task name

#### Scenario: an app ID exceeding the DNS-1123 length limit is rejected

- **WHEN** a derived identifier sanitizes to an app ID longer than 63 characters
- **THEN** compilation SHALL fail with a `CompilationException` naming the offending `nodeId` and
  the app ID it produced, rather than truncating it

### Requirement: Each node renders a valid single-node definition

Every compiled node SHALL carry a `specText` that satisfies
`openspec/schemas/single-node-definition.schema.json`. A sequencer `FlowNode`'s `specText` SHALL
carry the common envelope plus `scope` (`main` or `do`), its own source-ordered `tasks`, and a
`children` object mapping each child's `key()` to that child's `appId`. A controller `FlowNode`'s
`specText` SHALL carry the common envelope plus `scope` (`for`, `try-catch`, or `fork`), an empty
`tasks` array, a `children` object keyed by role (`do` for `for`; `try` and, when present, `catch`
for `try-catch`; each branch's task name for `fork`), and that scope's own configuration: `each`/
`in`/`at`/`while` when `scope` is `for`; `errors`/`retry` (each present only when the DSL supplies
it) and `catch` when `scope` is `try-catch` and a catch child is present; `forkMode` when `scope` is
`fork`. A `StepNode`'s `specText` SHALL carry the common envelope plus its `task`, and
`functionAppId` exactly when the task is a `call` or `run`. The envelope's `nodeId` field SHALL carry
the node's sanitized `appId`.

#### Scenario: a flow node renders scope, tasks, and children

<!-- Retitled in substance by this change — a sequencer renders its own tasks as before; a
     controller renders an empty tasks array plus its own scope configuration (see the two
     scenarios below). Kept under the original scenario name for the archive-time preservation
     reason noted above. -->

- **WHEN** the sequencer `do` node `fulfillOrder.try` containing a `for` task `reserveItems` is
  rendered
- **THEN** its `specText` SHALL declare `kind` `flow` and `scope` `do`, SHALL list the `reserveItems`
  task object in `tasks`, and SHALL map `reserveItems` to its child's app ID in `children`

#### Scenario: a try-catch controller renders its own error filter, not just a catch pointer

- **WHEN** the `try-catch` node `processPayment` declares `catch.errors.with.status: 402` and a
  `catch.do` list
- **THEN** its `specText` SHALL declare `kind` `flow`, `scope` `try-catch`, an empty `tasks` array,
  `errors` `{ "with": { "status": 402 } }`, `catch` set to the catch child's app ID, and `children`
  mapping `try` and `catch` to their respective children's app IDs

#### Scenario: a for controller renders its own loop configuration

- **WHEN** the `for` node `reserveItems` declares `each: item` and `in: .items`
- **THEN** its `specText` SHALL declare `kind` `flow`, `scope` `for`, an empty `tasks` array, `each`
  `item`, `in` `.items`, and `children` mapping `do` to its loop-body child's app ID

#### Scenario: a fork node renders forkMode and an empty task list

- **WHEN** a `fork` node with `compete: false` is rendered
- **THEN** its `specText` SHALL declare `scope` `fork`, `forkMode` `all`, an empty `tasks` array,
  and one `children` entry per branch, keyed by each branch's own task name

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
