## MODIFIED Requirements

### Requirement: Fork nodes declare their scope and join mode

<!-- Retitled in substance by this change — the requirement now defines the whole `scope` enum and
     both flow-node shapes, of which fork's join mode is only one part. Kept under the original
     requirement name because an archive-time delta apply matches a MODIFIED requirement to the
     current spec by its header, verbatim. -->

A `kind: "flow"` single-node definition SHALL declare `scope` as exactly one of five values: `main`,
`do`, `for`, `try-catch`, `fork`. The values `try`, `catch`, and `forkBranch` are retired and SHALL
be rejected.

A flow node SHALL be one of two shapes. A **sequencer** (`scope` `main` or `do`) SHALL carry its own
source-ordered `tasks` array and a `children` object keyed by each of those tasks' names, and SHALL
NOT carry any scope configuration field. A **controller** (`scope` `for`, `try-catch`, or `fork`)
SHALL carry an empty `tasks` array plus its own scope's configuration, and SHALL delegate execution
to its `children`:

- A `for` definition SHALL carry `in`, and MAY carry `each`, `at`, and `while`. It SHALL carry
  exactly one child, keyed `do`. A definition whose `scope` is anything other than `for` SHALL NOT
  carry `each`, `in`, `at`, or `while`.
- A `try-catch` definition MAY carry `errors`, `retry`, `as`, `when`, and `exceptWhen` — the `catch`
  block's own error filter, retry policy, error-variable name, and recovery guards — and SHALL carry
  `catch` (the recovery child's app ID) exactly when the definition supplies a recovery list. Its
  `children` SHALL be keyed `try` and, when present, `catch`. A definition whose `scope` is anything
  other than `try-catch` SHALL NOT carry `errors`, `retry`, `as`, `when`, `exceptWhen`, or `catch`.
- A `fork` definition SHALL carry `forkMode` with the value `all` or `any`, since a fork node
  sequences nothing of its own — it fans out to its `children` and joins. Its `children` SHALL be
  keyed by each branch's own root task name, with no intermediate branch node. A definition whose
  `scope` is anything other than `fork` SHALL NOT carry `forkMode`.

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

- **WHEN** a definition declares `forkMode` alongside a `scope` of `main`, `do`, `for`, or
  `try-catch`
- **THEN** validation fails, since only a fork node performs a join

#### Scenario: A competing fork declares forkMode any

- **WHEN** a `fork` task declared `compete: true` is compiled
- **THEN** its single-node definition SHALL carry `forkMode: "any"`, and **WHEN** `compete` is false
  or absent **THEN** it SHALL carry `forkMode: "all"`

#### Scenario: A retired scope value is rejected

- **WHEN** a definition declares `scope` of `try`, `catch`, or `forkBranch`
- **THEN** validation fails, since the enum admits only `main`, `do`, `for`, `try-catch`, and `fork`

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
  `as`, `when`, `exceptWhen`, `catch`, or `forkMode`
