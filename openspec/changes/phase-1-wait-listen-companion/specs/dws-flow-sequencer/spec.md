## MODIFIED Requirements

### Requirement: Single node, single workflow type

Owning component: `dws-flow`.

The Flow host SHALL load one single-node definition at startup, register one workflow type, and fail startup on invalid definitions. `SingleNodeDefinitionLoader` SHALL accept `scope` values `main`, `do`, `for`, `try-catch`, `fork`, `wait`, and `listen`, enforcing leaf-controller rules (`wait` with `wait`; `listen` with `to` and optional `timeout`/`read`; empty `tasks`/`children`), scope-exclusive fields, and optional DNS-1123 `dataflow` on non-`main` scopes.

#### Scenario: Invalid definition

- **WHEN** the definition fails validation
- **THEN** startup fails with a definition load error

#### Scenario: Valid wait definition loads

- **WHEN** `SingleNodeDefinitionLoader` loads a `kind: "flow"` definition with `scope: "wait"`,
  empty `tasks`, empty `children`, and `wait` as `"PT5M"` or `{"seconds": 10}`
- **THEN** loading succeeds and exposes the `wait` duration value verbatim

#### Scenario: Valid listen definition loads

- **WHEN** `SingleNodeDefinitionLoader` loads a `kind: "flow"` definition with `scope: "listen"`,
  empty `tasks`, empty `children`, `to` as a JSON object, and optional `timeout` and `read`
- **THEN** loading succeeds and exposes `to`, `timeout`, and `read` verbatim

#### Scenario: Invalid wait or listen definition fails startup

- **WHEN** a `wait` definition omits `wait` or declares non-empty `tasks`/`children`, or a `listen`
  definition omits `to` or declares non-empty `tasks`/`children`, or a non-`wait`/`non-listen`
  definition carries `wait`, `to`, `timeout`, or `read`
- **THEN** loading throws `DefinitionLoadException` naming the offending field or scope

#### Scenario: Structural definition with companion dataflow reference loads

- **WHEN** a non-`main` `kind: "flow"` definition carries `"dataflow": "reserve-items-dataflow"`
- **THEN** loading succeeds and exposes `Dataflow` as `"reserve-items-dataflow"`

#### Scenario: Invalid dataflow reference fails startup

- **WHEN** a `scope: "main"` definition carries `dataflow`, or any flow definition carries an empty
  or non-DNS-1123 `dataflow` value
- **THEN** loading throws `DefinitionLoadException`

### Requirement: Controller scopes routed

Owning component: `dws-flow`.

A node whose scope is `for`, `try-catch`, `fork`, `wait`, or `listen` SHALL fail when run with
`config failure: scope '<scope>' is not implemented yet`, selected through one scope dispatch point.

#### Scenario: For scope

- **WHEN** a node with scope `for` runs
- **THEN** it fails with the not-implemented configuration failure

#### Scenario: Wait scope

- **WHEN** a node with scope `wait` runs
- **THEN** it fails with `config failure: scope 'wait' is not implemented yet`

#### Scenario: Listen scope

- **WHEN** a node with scope `listen` runs
- **THEN** it fails with `config failure: scope 'listen' is not implemented yet`
