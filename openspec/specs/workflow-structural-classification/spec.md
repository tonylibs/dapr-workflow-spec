# workflow-structural-classification Specification

## Purpose
TBD - created by archiving change phase-1-structural-classification. Update Purpose after archive.

## Requirements

### Requirement: Recursive classification into Flow and Step nodes

Owning component: `dws-controller`.

`V2StructuralCompiler` SHALL walk a DSL 1.0 definition from `main` in source order. Every `FlowNode` is either a sequencer (`main`, `do`) with its own `tasks` and no scope config, or a controller (`for`, `try-catch`, `fork`, `wait`, `listen`) with empty `tasks` and its own scope config (`wait` and `listen` are leaf controllers with empty `children`). It SHALL classify `main`, nested `do`, `for`, `try`, `fork`, `wait`, and `listen` as `FlowNode`s, and `set`, `switch`, `emit`, `raise`, `call`, `run` as `StepNode`s.

#### Scenario: a nested try/for/catch definition classifies into five nodes under main

<!-- Kept under the original scenario name so archive-time delta apply matches verbatim. -->

- **WHEN** a definition's `do` holds a `set` task `validateOrder` and a `try` task `fulfillOrder`
  whose `try` list holds a `for` task `reserveItems` containing a `call: http` task `reserveItem`,
  and whose `catch.do` holds a `set` task `markOrderFailed`
- **THEN** the compiled tree SHALL be a `main` `FlowNode` with children `validateOrder` (`StepNode`)
  and `fulfillOrder` (`FlowNode`, `scope` `try-catch`, controller), where `fulfillOrder` has children
  `try` (`fulfillOrder.try`, `FlowNode`, `scope` `do`, sequencer) and `catch` (`fulfillOrder.catch`,
  `FlowNode`, `scope` `do`, sequencer); `fulfillOrder.try` has child `reserveItems` (`FlowNode`,
  `scope` `for`, controller); `reserveItems` has child `do` (`reserveItems.do`, `FlowNode`, `scope`
  `do`, sequencer); `reserveItems.do` has child `reserveItem` (`StepNode`); and `fulfillOrder.catch`
  has child `markOrderFailed` (`StepNode`) — 9 nodes total

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

#### Scenario: a wait task compiles to a leaf Flow controller carrying its duration verbatim

- **WHEN** a `wait` task `reminderDelay` declares `wait: PT5M` (or a duration object such as
  `wait: { seconds: 30 }`)
- **THEN** `reminderDelay` SHALL compile to a `FlowNode` with `scope` `wait`, an empty `tasks` list,
  no `children`, and `wait` carrying that duration value verbatim on its rendered `specText`

#### Scenario: a listen task compiles to a leaf Flow controller carrying its to selection and timeout verbatim

- **WHEN** a `listen` task `awaitApproval` declares `listen.to` (and optionally `timeout` on the
  task body and/or `listen.read`)
- **THEN** `awaitApproval` SHALL compile to a `FlowNode` with `scope` `listen`, an empty `tasks`
  list, no `children`, `to` carrying `listen.to` verbatim, `timeout` carrying the task's `timeout`
  verbatim when present (omitted when absent), and `read` carrying `listen.read` verbatim when
  present (omitted when absent)

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

Owning component: `dws-controller`.

The compiler SHALL derive each node's `nodeId` and sanitize it into a DNS-1123 `appId` (splitting camelCase and collapsing non-alphanumerics to `-`). Named tasks keep their name; unnamed scopes and companions derive `<workflow>.main`, `<try>.try`, `<try>.catch`, `<for>.do`, and `<task>.dataflow`. Fork branches keep each branch root's own task name (no derived branch identifier).

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
- **AND** a `wait` or `listen` `FlowNode` SHALL render an empty `children` object, even when it
  references a companion data-flow node

#### Scenario: an app ID exceeding the DNS-1123 length limit is rejected

- **WHEN** a derived identifier (including `<task>.dataflow`) sanitizes to an app ID longer than 63
  characters
- **THEN** compilation SHALL fail with a `CompilationException` naming the offending `nodeId` and
  the app ID it produced, rather than truncating it

#### Scenario: a companion data-flow step derives its identifier from its structural task

- **WHEN** a structural task named `reserveItems` declares `input`, `output`, or `export`
- **THEN** its companion `StepNode`'s `nodeId` SHALL be `reserveItems.dataflow` and its `appId`
  SHALL be `reserve-items-dataflow`

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

Owning component: `dws-controller`.

A `StepNode` compiled from a `call` or `run` task SHALL keep the plain task-derived app ID for
itself and SHALL record its underlying function's Knative Service app ID as `functionAppId`, formed
by appending `-fn` to its own app ID. Every other `StepNode` — including companion data-flow
`StepNode`s — SHALL leave `functionAppId` empty.

#### Scenario: a call step records the -fn suffixed function app ID

- **WHEN** a `call: http` task named `reserveItem` is compiled
- **THEN** its `StepNode`'s `appId` SHALL be `reserve-item` and its `functionAppId` SHALL be
  `reserve-item-fn`

#### Scenario: a run step records the -fn suffixed function app ID

- **WHEN** a `run: shell` task named `rebuildIndex` is compiled
- **THEN** its `StepNode`'s `appId` SHALL be `rebuild-index` and its `functionAppId` SHALL be
  `rebuild-index-fn`

#### Scenario: a non-IO step has no function app ID

- **WHEN** a `set`, `switch`, `emit`, or `raise` task — or a companion `<task>.dataflow` step — is
  compiled
- **THEN** its `StepNode`'s `functionAppId` SHALL be empty

### Requirement: Ambiguous derived identifiers are rejected

Owning component: `dws-controller`.

Compilation SHALL fail when two deployables in one definition resolve to the same Dapr app ID —
including companion `<task>.dataflow` Step nodes and `-fn` function app IDs — a node's derived app
ID is not a usable DNS-1123 label, or a task name cannot be resolved to exactly one node. The raised
`CompilationException` SHALL name the offending task or node and the derived identifier at fault,
and SHALL NOT return a partial plan.

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

#### Scenario: a companion data-flow node app ID colliding with another node fails compilation

- **WHEN** a structural task `reserveItems` declares data flow (producing `reserve-items-dataflow`)
  alongside another task named `reserveItemsDataflow` (also producing `reserve-items-dataflow`)
- **THEN** compilation SHALL raise `CompilationException` naming both `reserveItems.dataflow` and
  `reserveItemsDataflow` and the shared app ID `reserve-items-dataflow`

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

Owning component: `dws-controller`.

Every compiled node's `specText` SHALL satisfy `single-node-definition.schema.json` with `nodeId` set to its sanitized `appId`. Sequencer flows render `scope`, `tasks`, and `children`; controller flows render empty `tasks`, scope-specific config (`for`, `try-catch`, `fork`, `wait`, `listen`), and `children` (empty for `wait`/`listen`); non-`main` flows with a companion render `"dataflow"`; steps render `task` and `functionAppId` (only for `call`/`run`).

#### Scenario: a flow node renders scope, tasks, and children

<!-- Kept under the original scenario name for archive-time delta apply compatibility. -->

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

#### Scenario: a wait node renders wait and empty tasks and children

- **WHEN** a `wait` node `reminderDelay` with `wait: PT5M` is rendered
- **THEN** its `specText` SHALL declare `kind` `flow`, `scope` `wait`, an empty `tasks` array, an
  empty `children` object, and `"wait": "PT5M"`

#### Scenario: a listen node renders to, optional timeout and read, and empty tasks and children

- **WHEN** a `listen` node `awaitApproval` with `listen.to` and task-level `timeout: PT1H` is
  rendered
- **THEN** its `specText` SHALL declare `kind` `flow`, `scope` `listen`, an empty `tasks` array, an
  empty `children` object, `"to"` matching `listen.to` verbatim, and `"timeout": "PT1H"`

#### Scenario: a structural node with data flow renders its companion app ID under dataflow

- **WHEN** a `for` node `reserveItems` declares `input` and `output`
- **THEN** `reserveItems`'s `specText` SHALL declare `"dataflow": "reserve-items-dataflow"` and
  SHALL NOT include `"dataflow"` inside `"children"`

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

Owning component: `dws-controller`.

The compiler SHALL reproduce, exactly, the compiled graph for each of the six worked examples in
`docs/roadmaps/workflow-runtime-architecture.md` — nested try/for/catch, parallel fork branches,
state and decision steps, timing and event steps (with `wait` and `listen` classified as leaf
`FlowNode`s per ADR 0006), external call and run steps, and raise and recovery steps.

#### Scenario: every worked example matches its golden fixture

- **WHEN** each of the six worked-example definitions is compiled
- **THEN** the resulting tree shape, every node's `nodeId`, `appId`, `definitionResource`, and
  `functionAppId`, and every node's rendered `specText` SHALL match that example's checked-in
  fixture byte-for-byte

#### Scenario: every rendered node validates against the schema

- **WHEN** each node produced from the six worked examples is rendered
- **THEN** its `specText` SHALL validate against `single-node-definition.schema.json` with no
  violations

### Requirement: Companion data-flow Step nodes for structural tasks

Owning component: `dws-controller`.

For every structural task (`for`, `try`, `fork`, `wait`, `listen`, nested `do`) declaring `input`, `output`, or `export`, `V2StructuralCompiler` SHALL emit one companion `StepNode` (`<task>.dataflow`, DNS-1123 `appId`, empty `functionAppId`) whose `task` `{ "<task>": { ... } }` carries only those declared sections verbatim in source order, referenced via `"dataflow"` and visited by `flatten()`. No data flow emits no companion; `main`, derived `do` wrappers, and leaf steps never emit companions.

#### Scenario: A structural task declaring input, output, or export emits a companion Step node

- **WHEN** a `for`, `try`, `fork`, `wait`, `listen`, or nested `do` task named `work` declares any
  non-empty subset of `input`, `output`, and `export`
- **THEN** the compiled `FlowNode` for `work` SHALL carry a companion `StepNode` with `nodeId`
  `work.dataflow`, `appId` `work-dataflow`, and `specText` containing `kind: "step"` and
  `task: {"work": { ... }}` with only the declared `input`/`output`/`export` keys verbatim
- **AND** `work`'s `FlowNode` `specText` SHALL carry `"dataflow": "work-dataflow"`
- **AND** `root.flatten()` SHALL include `work-dataflow`

#### Scenario: A structural task without data flow emits no companion

- **WHEN** a `for`, `try`, `fork`, `wait`, `listen`, or nested `do` task declares none of `input`,
  `output`, or `export`
- **THEN** no `<task>.dataflow` `StepNode` SHALL be emitted and the `FlowNode`'s `specText` SHALL
  omit `"dataflow"`

#### Scenario: A leaf Step task with data flow emits no companion

- **WHEN** a `set` or `call` task declares `input`, `output`, and `export`
- **THEN** it SHALL compile to its own single `StepNode` carrying the full task body, and no
  `<task>.dataflow` companion node SHALL be emitted

#### Scenario: Derived do wrappers never emit companions

- **WHEN** a `for` task or `try` task declares `input`/`output`/`export`
- **THEN** the companion node SHALL attach to the `for` or `try-catch` controller node itself
  (`<task>.dataflow`), and its derived `do` children (`<task>.do`, `<task>.try`, `<task>.catch`)
  SHALL have no companion and no `"dataflow"` field

### Requirement: Deterministic compilation and structural golden coverage

Owning component: `dws-controller`.

Compiling the same workflow document twice with `V2StructuralCompiler` SHALL produce identical graph topology and byte-identical `specText` across `root.flatten()`. Checked-in golden fixtures SHALL cover every structural kind (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) with and without data flow, with every rendered node validating against `single-node-definition.schema.json`.

#### Scenario: Same document compiles byte-identically twice

- **WHEN** a workflow document containing structural tasks (with and without data flow) and leaf
  tasks is compiled twice by `V2StructuralCompiler`
- **THEN** both compiled graphs SHALL produce the exact same sequence of nodes in `flatten()` and
  byte-for-byte identical `specText` for every node

#### Scenario: Golden fixtures cover all six structural kinds with and without data flow

- **WHEN** `V2GoldenTest` runs across all checked-in v2 golden fixtures
- **THEN** every structural kind (`for`, `try-catch`, `fork`, `wait`, `listen`, nested `do`) SHALL
  be exercised both without data flow (no companion, no `dataflow` field) and with data flow
  (companion `<task>.dataflow` emitted and referenced), matching its fixture byte-for-byte and
  passing schema validation with zero violations
