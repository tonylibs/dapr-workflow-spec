# single-node-definition-contract Specification

## Purpose
TBD - created by archiving change phase-1-structural-classification. Update Purpose after archive.

## Requirements

### Requirement: Fork nodes declare their scope and join mode

A `kind: "flow"` single-node definition SHALL accept `fork` as a value of `scope`, alongside
`main`, `do`, `for`, `try`, `catch`, and `forkBranch`. A definition whose `scope` is `fork` SHALL
carry `forkMode` with the value `all` or `any`, and SHALL carry an empty `tasks` array, since a fork
node sequences nothing of its own — it fans out to its `children` and joins. A definition whose `scope`
is anything other than `fork` SHALL NOT carry `forkMode`.

#### Scenario: A fork definition carries forkMode and no tasks

- **WHEN** a `dws-flow` instance loads a `kind: "flow"` definition with `scope: "fork"`,
  `forkMode: "all"`, an empty `tasks` array, and one `children` entry per branch
- **THEN** the definition validates and the instance starts, dispatching each `children` entry as a
  child workflow

#### Scenario: A fork definition missing forkMode is rejected

- **WHEN** a definition declares `scope: "fork"` with no `forkMode`
- **THEN** validation fails and the instance fails to start, logging the missing field

#### Scenario: forkMode on a non-fork scope is rejected

- **WHEN** a definition declares `forkMode` alongside a `scope` of `main`, `do`, `for`, `try`,
  `catch`, or `forkBranch`
- **THEN** validation fails, since only a fork node performs a join

#### Scenario: A competing fork declares forkMode any

- **WHEN** a `fork` task declared `compete: true` is compiled
- **THEN** its single-node definition SHALL carry `forkMode: "any"`, and **WHEN** `compete` is false
  or absent **THEN** it SHALL carry `forkMode: "all"`
