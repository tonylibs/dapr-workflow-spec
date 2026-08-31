## ADDED Requirements

### Requirement: dws-step is an independently toolchained Java/Spring component
`dws-step` SHALL exist as a top-level directory sibling to `dws-controller`, `dws-orchestrator`,
and the other components, using Maven with its own `pom.xml` and no shared build step with any
other component, mirroring `dws-orchestrator`'s toolchain (Java 25, Spring Boot).

#### Scenario: Building dws-step in isolation
- **WHEN** a developer runs `./mvnw verify` inside `dws-step/` on a clean checkout
- **THEN** the build succeeds without requiring any other component's directory to be built first

### Requirement: One deployed instance hosts exactly one compiled Step task
`dws-step` SHALL register exactly one Dapr Workflow Activity, under a constant name, whose
behavior at startup is determined entirely by the single-node definition it loads — not by any
per-task-kind code path selected at compile time.

#### Scenario: The same binary hosts different task kinds depending only on its loaded definition
- **WHEN** two `dws-step` instances are started from the same image with different single-node
  definition files (one wrapping a `set` task, one wrapping a `call: http` task)
- **THEN** both instances start successfully and each logs the task kind it loaded, with no source
  code difference between the two instances beyond the definition file

### Requirement: Startup fails fast on a missing or invalid single-node definition
`dws-step` SHALL fail to start (non-zero exit, clear log message) if its configured definition
file is missing, is not valid JSON, or fails schema/shape validation against the `kind: "step"`
contract.

#### Scenario: Missing definition file
- **WHEN** `dws-step` starts with its definition file path unset or pointing at a nonexistent file
- **THEN** the process exits non-zero and logs that the definition could not be loaded

#### Scenario: Definition with wrong kind
- **WHEN** `dws-step` starts with a definition file whose `kind` is `"flow"`
- **THEN** the process exits non-zero and logs the kind mismatch

### Requirement: Health endpoint reports definition-load status
`dws-step` SHALL expose `GET /healthz`, matching the existing step-service HTTP contract's naming
(overriding Spring Boot Actuator's default `/actuator/health` path rather than adding a second,
inconsistent health path), reporting healthy once the pinned definition has loaded and passed
validation.

#### Scenario: Healthy after successful startup
- **WHEN** `GET /healthz` is called after `dws-step` has started successfully
- **THEN** the response indicates healthy

### Requirement: Local development is documented
`dws-step/README.md` SHALL document the `dapr run` invocation (app-id, app port, Dapr HTTP/gRPC
ports) needed to run a single instance locally against a sample single-node definition file,
mirroring the pattern already documented in `dws-orchestrator/README.md`.

#### Scenario: README documents the dapr run invocation
- **WHEN** a developer follows `dws-step/README.md`'s local-dev section
- **THEN** it specifies the exact `dapr run` command and where the sample definition file lives
