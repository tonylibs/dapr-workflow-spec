# console-workflow-diagram Specification

## Purpose

Defines the `dws-console` definition editor's live, read-only execution/control-flow diagram of the
draft definition buffer. Owning component: `dws-console`.

## ADDED Requirements

### Requirement: Definition editor shows a live control-flow diagram of the draft
The `dws-console` definition editor SHALL render a diagram of the task graph that the current draft
definition describes, laid out automatically, with no network call. The console SHALL rebuild the
diagram after the draft text stops changing for about 300 ms, and SHALL NOT block text input while
it rebuilds.

#### Scenario: Operator types a valid definition
- **WHEN** an operator enters a DSL definition whose `do` list has tasks and pauses typing
- **THEN** the editor shows one node per task, connected in execution order, within about 300 ms plus layout time

#### Scenario: Operator imports or restores a draft
- **WHEN** the draft buffer is filled by a file import or restored from the persisted draft
- **THEN** the editor shows the diagram for that draft without further operator action

#### Scenario: Continuous typing
- **WHEN** an operator types continuously in the editor
- **THEN** the diagram does not rebuild on each keystroke and the editor stays responsive

### Requirement: Diagram represents DSL control flow
The diagram SHALL show `switch` cases and `then` jumps as edges between tasks, and SHALL show
`try` bodies, `catch.do` bodies, `for` bodies, and `fork` branches as container nodes that
enclose their nested tasks. The diagram SHALL NOT classify tasks into Flow/Step nodes.

#### Scenario: Switch jumps
- **WHEN** the draft contains a `switch` task whose cases use `then: <taskName>`
- **THEN** the diagram shows an edge from the switch to each target task

#### Scenario: Nested regions
- **WHEN** the draft contains `try`/`catch`, `for`, or `fork` tasks
- **THEN** the diagram shows each body or branch as a container with its nested tasks inside it

### Requirement: Diagram accepts every definition DWS accepts
The console SHALL build the diagram for every definition that `dws-controller` compiles today,
including definitions that DSL 1.0.3 rejects. The diagram SHALL NOT report DSL 1.0.3 schema
validation errors; spec validation remains the responsibility of `dws-admin`.

#### Scenario: Object-form run arguments
- **WHEN** the draft uses object-form `run.shell` or `run.script` `arguments`
- **THEN** the diagram renders the definition and shows no schema error

#### Scenario: Controller fixtures
- **WHEN** any `dws-controller` test fixture that compiles today is loaded as the draft
- **THEN** the diagram contains at least one task node

### Requirement: Invalid or partial drafts keep the last good diagram
When the draft cannot be parsed or does not have a buildable shape, the console SHALL keep the last
successfully built diagram visible, mark it as stale, and show the parse or build error. If no
diagram was built yet, the console SHALL show the error without a diagram. The page SHALL NOT
crash.

#### Scenario: Syntax error while editing
- **WHEN** a draft that produced a diagram is edited into invalid YAML or JSON
- **THEN** the previous diagram stays visible with a stale indicator and the parse error is shown

#### Scenario: Malformed definition shape
- **WHEN** the draft parses but has no `document` object or no `do` list, or graph building fails (for example `broken.yaml`)
- **THEN** the console shows a handled error and does not throw

#### Scenario: Draft recovers
- **WHEN** a stale draft is edited back into a buildable definition
- **THEN** the diagram rebuilds and the stale indicator clears

### Requirement: Validation errors appear on matching nodes
Each task node SHALL be identified by its JSON-pointer task reference (for example
`/do/1/approve`). When the editor holds a spec-validation result for the current draft, the diagram
SHALL badge each node that is the closest enclosing task of an error's path. Errors with no
enclosing task SHALL remain only in the existing error list.

#### Scenario: Error inside a task
- **WHEN** the preview reports an error whose path is `/do/1/approve/call`
- **THEN** the node for `/do/1/approve` shows an error badge

#### Scenario: Document-level error
- **WHEN** the preview reports an error whose path is `/document/name`
- **THEN** no node shows a badge and the error remains in the error list

#### Scenario: Draft changes after preview
- **WHEN** the draft changes after a validation result was shown
- **THEN** the node badges clear together with the preview result

### Requirement: Diagram loads client-only and lazily
The diagram's rendering and layout code SHALL load only in the browser and only when the diagram is
displayed. Server-side rendering of the console SHALL NOT import or execute it.

#### Scenario: Server render of the editor page
- **WHEN** the definition editor page is server-rendered
- **THEN** the render succeeds and shows a placeholder where the diagram will appear

#### Scenario: Other pages
- **WHEN** an operator opens a console page without the definition editor
- **THEN** the diagram's rendering and layout code is not downloaded

### Requirement: Diagram is keyboard accessible
The diagram's zoom-in, zoom-out, and fit-view controls SHALL be reachable and operable by keyboard
and SHALL have accessible names. Each task node SHALL expose an accessible name that includes the
task name and task type.

#### Scenario: Keyboard-only operator
- **WHEN** an operator tabs through the definition editor page
- **THEN** focus reaches the zoom and fit controls and Enter or Space activates them

#### Scenario: Screen reader
- **WHEN** a screen reader reads a task node
- **THEN** it announces the task name and its task type
