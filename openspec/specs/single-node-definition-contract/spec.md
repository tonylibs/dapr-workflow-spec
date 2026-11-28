# single-node-definition-contract Specification

## Purpose
TBD - created by archiving change phase-1-structural-classification. Update Purpose after archive.

## Requirements

### Requirement: Fork nodes declare their scope and join mode

Owning components: `single-node-definition.schema.json`, `dws-controller`, `dws-flow`.

A `kind: "flow"` definition SHALL declare `scope` as `main`, `do`, `for`, `try-catch`, `fork`, `wait`, or `listen` (retired `try`, `catch`, `forkBranch` rejected). Sequencers (`main`, `do`) carry `tasks`, `children`, and no scope config. Controllers carry empty `tasks` and scope-exclusive config (`for`, `try-catch`, `fork` delegating to `children`; leaf controllers `wait` and `listen` with empty `children`, `wait` carrying `wait` and `listen` carrying `to` plus optional `timeout`/`read`).

#### Scenario: A fork definition carries forkMode and no tasks

- **WHEN** a `dws-flow` instance loads a `kind: "flow"` definition with `scope: "fork"`,
  `forkMode: "all"`, an empty `tasks` array, and one `children` entry per branch, keyed by that
  branch's own root task name
- **THEN** the definition validates and the instance starts, dispatching each `children` entry as a
  child workflow

#### Scenario: A fork definition missing forkMode is rejected

- **WHEN** a definition declares `scope: "fork"` with no `forkMode`
- **THEN** validation fails and the instance fails to start, logging the missing field

#### Scenario: forkMode on a non-fork scope is rejected

- **WHEN** a definition declares `forkMode` alongside a `scope` of `main`, `do`, `for`, `try-catch`,
  `wait`, or `listen`
- **THEN** validation fails, since only a fork node performs a join

#### Scenario: A competing fork declares forkMode any

- **WHEN** a `fork` task declared `compete: true` is compiled
- **THEN** its single-node definition SHALL carry `forkMode: "any"`, and **WHEN** `compete` is false
  or absent **THEN** it SHALL carry `forkMode: "all"`

#### Scenario: A retired scope value is rejected

- **WHEN** a definition declares `scope` of `try`, `catch`, or `forkBranch`
- **THEN** validation fails, since the enum admits only `main`, `do`, `for`, `try-catch`, `fork`,
  `wait`, and `listen`

#### Scenario: A controller definition carries its scope's configuration and an empty task list

- **WHEN** a definition declares `scope: "for"` with `in: ".items"`, an empty `tasks` array, and one
  `children` entry keyed `do`
- **THEN** the definition validates, and **WHEN** the same definition instead declares a non-empty
  `tasks` array **THEN** validation fails, since a controller sequences nothing of its own

#### Scenario: A try-catch controller carries its catch block's guards

- **WHEN** a `try` task's `catch` block declares `errors`, `retry`, `as`, `when`, or `exceptWhen`
- **THEN** its compiled `try-catch` definition SHALL carry each of those fields verbatim, so a
  runtime can reconstruct the author's recovery condition without re-reading the source document

#### Scenario: A sequencer definition carries no scope configuration

- **WHEN** a definition declares `scope: "main"` or `scope: "do"`
- **THEN** validation fails if it carries any of `each`, `in`, `at`, `while`, `errors`, `retry`,
  `as`, `when`, `exceptWhen`, `catch`, `forkMode`, `wait`, `to`, `timeout`, or `read`

#### Scenario: A wait leaf controller validates with string or object duration and empty tasks and children

- **WHEN** a `kind: "flow"` definition declares `scope: "wait"`, empty `tasks`, empty `children`,
  and `wait` as either `"PT5M"` or `{"seconds": 10}`
- **THEN** validation succeeds

#### Scenario: A wait definition missing wait or carrying tasks or children is rejected

- **WHEN** a `scope: "wait"` definition omits `wait`, has non-empty `tasks`, or has non-empty
  `children`
- **THEN** validation fails

#### Scenario: A wait field on a non-wait scope is rejected

- **WHEN** a definition declares `wait` alongside any `scope` other than `wait`
- **THEN** validation fails

#### Scenario: A listen leaf controller validates with to and optional timeout and read

- **WHEN** a `kind: "flow"` definition declares `scope: "listen"`, empty `tasks`, empty `children`,
  `to` as an event selection object, and optionally `timeout` (string or object) and `read` (string)
- **THEN** validation succeeds

#### Scenario: A listen definition missing to or carrying tasks or children is rejected

- **WHEN** a `scope: "listen"` definition omits `to`, has non-empty `tasks`, or has non-empty
  `children`
- **THEN** validation fails

#### Scenario: Listen fields on a non-listen scope are rejected

- **WHEN** a definition declares `to`, `timeout`, or `read` alongside any `scope` other than
  `listen`
- **THEN** validation fails, keeping all existing scopes unchanged

### Requirement: Structural flow nodes may reference a companion data-flow step

Owning components: `single-node-definition.schema.json`, `dws-controller`, `dws-flow`.

A non-`main` `kind: "flow"` definition (`do`, `for`, `try-catch`, `fork`, `wait`, `listen`) MAY carry a top-level DNS-1123 `dataflow` property naming its companion data-flow Step app ID; `dataflow` SHALL NOT appear on `scope: "main"` or inside `children`. A companion node SHALL be a `kind: "step"` definition with no `functionAppId` and `task` equal to `{ "<taskName>": { ... } }` carrying only declared `input`/`output`/`export`.

#### Scenario: A structural flow node with a companion dataflow reference validates

- **WHEN** a `kind: "flow"` definition with `scope` `for`, `try-catch`, `fork`, `wait`, `listen`,
  or `do` carries `"dataflow": "reserve-items-dataflow"` and keeps its `children` restricted to its
  scope's execution children (empty for `wait` and `listen`)
- **THEN** validation succeeds against `single-node-definition.schema.json`

#### Scenario: A main flow node carrying dataflow is rejected

- **WHEN** a `kind: "flow"` definition declares `scope: "main"` and carries a `dataflow` property
- **THEN** validation fails, because document-level data flow is out of scope

#### Scenario: An invalid dataflow app ID is rejected

- **WHEN** a `kind: "flow"` definition carries `dataflow` with an empty string or a value outside
  the DNS-1123 label pattern `^[a-z0-9]([-a-z0-9]*[a-z0-9])?$` (max 63 characters)
- **THEN** validation fails

#### Scenario: A companion data-flow step definition validates against the step schema

- **WHEN** a `kind: "step"` definition has `nodeId: "reserve-items-dataflow"`, no `functionAppId`,
  and `task: {"reserveItems": {"input": {"from": "${ .payload }"}, "output": {"as": "${ . }" }}}`
- **THEN** validation succeeds against `single-node-definition.schema.json`
