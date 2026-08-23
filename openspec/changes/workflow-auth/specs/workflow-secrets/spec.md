## ADDED Requirements

### Requirement: Workflow declarations identify scalar Kubernetes secrets

The controller SHALL accept `use.secrets` as a list of unique logical secret names. Each declared
name SHALL resolve to Kubernetes `secretKeyRef.name` of the same name and `secretKeyRef.key`
`value`; the definition SHALL contain names only, never secret values or a configurable key map.

#### Scenario: Declared scalar secret compiles to a reference
- **WHEN** a workflow declares `use.secrets: [API_TOKEN]`
- **THEN** its deployment plan contains a typed reference to Secret `API_TOKEN` key `value` and
  contains no resolved Secret value

#### Scenario: Duplicate secret declaration is rejected
- **WHEN** a workflow declares the same secret name more than once
- **THEN** the controller rejects the definition with a validation error

### Requirement: Secret values are projected without controller-side retrieval

The controller and synthesizer SHALL represent secret-backed environment values separately from
literals and SHALL render them as Kubernetes `EnvVar.valueFrom.secretKeyRef`. The controller SHALL
NOT read Secret values or gain RBAC permission to read Secret objects. The orchestrator SHALL load
its projected `SECRET_<NAME>` environment values once at startup.

#### Scenario: Synthesized workload contains a secretKeyRef
- **WHEN** an orchestrator or step service requires a declared secret
- **THEN** its Pod environment entry uses `valueFrom.secretKeyRef` for the declared Secret name and
  `value` key rather than an `EnvVar.value` literal

#### Scenario: Missing Secret is not read by the controller
- **WHEN** a referenced Kubernetes Secret or key does not exist
- **THEN** the controller still performs no Secret read and Kubernetes reports the workload-startup
  failure through its normal reference validation

### Requirement: Declared secrets are available to jq as a DWS extension

The orchestrator SHALL bind declared scalar secret values under `$secrets.NAME` for jq evaluation.
DWS SHALL support that binding in `set` and `switch` expressions and SHALL document this as a
DWS-specific extension that can expose secret material.

#### Scenario: Set expression resolves a declared secret
- **WHEN** a `set` task evaluates an expression containing `$secrets.API_TOKEN`
- **THEN** jq receives the startup-loaded value for `API_TOKEN`

#### Scenario: Switch expression resolves a declared secret
- **WHEN** a `switch` condition evaluates an expression containing `$secrets.API_TOKEN`
- **THEN** jq evaluates the condition with that value bound

### Requirement: Existing workflows remain secret-free by default

Definitions that omit `use.secrets` SHALL compile and run with no added Secret environment entries
or changed workflow behavior.

#### Scenario: Existing definition has no secret declarations
- **WHEN** a pre-Phase-4 definition contains no `use.secrets`
- **THEN** its generated orchestrator and step-service environments contain no Phase-4 `SECRET_*`
  entries
