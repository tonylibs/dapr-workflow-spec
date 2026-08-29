## ADDED Requirements

### Requirement: dws-flow is an independently toolchained .NET component
`dws-flow` SHALL exist as a top-level directory sibling to `dws-controller`, `dws-orchestrator`,
and the other components, with its own project file and dependency set, and no shared build step
with any other component — consistent with the monorepo's "no shared build system" convention.

#### Scenario: Building dws-flow in isolation
- **WHEN** a developer runs the .NET build command inside `dws-flow/` on a clean checkout
- **THEN** the build succeeds without requiring any other component's directory to be built first

### Requirement: One deployed instance hosts exactly one compiled Flow scope
`dws-flow` SHALL register exactly one Dapr Workflow type, under a constant name, whose behavior at
startup is determined entirely by the single-node definition it loads — not by any per-scope code
path compiled into the binary.

#### Scenario: The same binary hosts different scopes depending only on its loaded definition
- **WHEN** two `dws-flow` instances are started from the same image with different single-node
  definition files (one a `main` scope, one a `for` scope)
- **THEN** both instances start successfully and each logs the scope it loaded, with no source
  code difference between the two instances beyond the definition file

### Requirement: Startup fails fast on a missing or invalid single-node definition
`dws-flow` SHALL fail to start (non-zero exit, clear log message) if its configured definition
file is missing, is not valid JSON, or fails schema/shape validation against the `kind: "flow"`
contract.

#### Scenario: Missing definition file
- **WHEN** `dws-flow` starts with its definition file path unset or pointing at a nonexistent file
- **THEN** the process exits non-zero and logs that the definition could not be loaded

#### Scenario: Definition with wrong kind
- **WHEN** `dws-flow` starts with a definition file whose `kind` is `"step"`
- **THEN** the process exits non-zero and logs the kind mismatch

### Requirement: Health endpoint reports definition-load status
`dws-flow` SHALL expose `GET /healthz`, matching the existing step-service HTTP contract's naming,
reporting healthy once the pinned definition has loaded and passed validation.

#### Scenario: Healthy after successful startup
- **WHEN** `GET /healthz` is called after `dws-flow` has started successfully
- **THEN** the response indicates healthy

### Requirement: Local development is documented
`dws-flow/README.md` SHALL document the `dapr run` invocation (app-id, app port, Dapr HTTP/gRPC
ports) needed to run a single instance locally against a sample single-node definition file,
mirroring the pattern already documented in `dws-orchestrator/README.md`.

#### Scenario: README documents the dapr run invocation
- **WHEN** a developer follows `dws-flow/README.md`'s local-dev section
- **THEN** it specifies the exact `dapr run` command and where the sample definition file lives
