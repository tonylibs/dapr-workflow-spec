## Purpose

Establishes how a workflow definition references cluster-side secrets by **name** without ever
carrying their values: `document.use.secrets` allow-lists the names a definition may reference;
`dws-controller` compiles those references into `secretKeyRef`-backed env on the deployed
`StepService` and `dws-orchestrator` Deployment rather than resolving them; and
`dws-orchestrator` exposes the mounted values to `jq` expressions as `$secrets.NAME` at
interpretation time.

## ADDED Requirements

### Requirement: document.use.secrets declares the allow-list of referenceable names
A workflow document MAY declare `document.use.secrets: [NAME, ...]`. Any DSL value of the form
`{ use.secret: X }` anywhere in the definition SHALL reference a name X present in that
allow-list, or `dws-controller` MUST fail compilation with a diagnostic naming both the
referencing location and X. A document that declares no `use.secrets` and uses no
`{ use.secret: ... }` value SHALL compile and behave exactly as today. Owning component:
`dws-controller`.

#### Scenario: Reference to an allow-listed name compiles
- **WHEN** `document.use.secrets` is `[GITHUB_TOKEN]` and an endpoint references
  `{ use.secret: GITHUB_TOKEN }`
- **THEN** compilation succeeds and the compiled `StepService` carries a `secretKeyRef` env
  entry naming the K8s `Secret` key for `GITHUB_TOKEN`

#### Scenario: Reference to a missing name fails compilation
- **WHEN** an endpoint references `{ use.secret: TYPO }` and `TYPO` is not in
  `document.use.secrets`
- **THEN** compilation fails with an error naming the endpoint and `TYPO`

#### Scenario: A definition using no secrets is unaffected
- **WHEN** a document declares no `use.secrets` and uses no `{ use.secret: ... }`
- **THEN** compilation and deployed resources are identical to before this capability existed

### Requirement: Secret values never appear in the definition or the compile output
`dws-controller` SHALL NOT accept a plaintext secret value anywhere in a submitted definition
(the DSL surface for consuming a secret is `{ use.secret: NAME }`, not a literal string), SHALL
NOT read from any K8s `Secret` during compilation, and SHALL NOT emit a plaintext credential
value on the compiled `StepService`, in the definition ConfigMap, or in any Dapr `Component`
metadata. Every secret consumed at runtime SHALL enter the pod exclusively via a
`secretKeyRef`-backed env var (for step services and orchestrators) or via a Dapr
`Component`'s `secretKeyRef` metadata (for the oauth2 middleware). Owning component:
`dws-controller`.

#### Scenario: Definition ConfigMap contains only secret names
- **WHEN** a definition using `{ use.secret: T }` is `POST`ed
- **THEN** the stored definition ConfigMap contains the literal string `T` (the name) and no
  value that could be `T`'s content

#### Scenario: Compiled StepService env carries no plaintext credential
- **WHEN** a `StepService` is compiled with `bearer` auth referencing `{ use.secret: T }`
- **THEN** the `StepService`'s env for `AUTH_TOKEN` is a `secretKeyRef` reference
- **AND** no `EnvVar` on that `StepService` carries a plaintext value that could be `T`'s
  content

#### Scenario: oauth2 middleware component references secret by ref
- **WHEN** an oauth2 `Component` is synthesised with a `client_id` / `client_secret` from
  `{ use.secret: ... }`
- **THEN** the `Component` metadata for those fields is a `secretKeyRef`, not a literal value

### Requirement: Orchestrator binds $secrets to jq at startup
`dws-orchestrator` SHALL, at pod startup, read every env var whose name matches the pattern
`SECRET_<NAME>` once, strip the `SECRET_` prefix, and populate an immutable
`Map<String, String>` on the runtime state. The map SHALL be passed to `JqEvaluator` and made
available as the `$secrets` binding, so a `set` or `switch` `jq` expression can reference
`$secrets.NAME` and receive the corresponding value. The map SHALL NOT change after startup, and
values SHALL NOT be emitted into workflow lifecycle events or into workflow input / output data
by this capability. Owning component: `dws-orchestrator`.

#### Scenario: $secrets exposes a mounted secret to jq
- **WHEN** `dws-orchestrator` starts with env `SECRET_API_KEY=abc` and a `set` task uses
  `$secrets.API_KEY`
- **THEN** the expression evaluates to `"abc"`

#### Scenario: $secrets is available in switch expressions
- **WHEN** a `switch` task uses `.header == $secrets.SHARED_TOKEN`
- **THEN** the branch selection uses the mounted value

#### Scenario: $secrets is loaded once and immutable
- **WHEN** env changes could conceptually occur after startup (they cannot inside a pod's
  lifetime, but the contract is stated explicitly)
- **THEN** `$secrets` continues to reflect the values read at startup, not later ones

#### Scenario: Secret values are not published as lifecycle events
- **WHEN** a `set` task binds `$secrets.API_KEY` into workflow data
- **THEN** the operator is responsible for that placement, but `dws-orchestrator` itself SHALL
  NOT publish the value of `$secrets.*` as a separate field on any lifecycle event

### Requirement: Missing referenced secret is a startup failure, not a runtime surprise
`dws-orchestrator` SHALL rely on K8s to fail the pod at startup when a `secretKeyRef`
references a `Secret` or key that does not exist, so a `POST`ed definition either produces a
running pod with all its secrets present or a `CrashLoopBackOff` visible via standard K8s
observability. The orchestrator SHALL NOT silently fall back to a null / empty `$secrets.NAME`
at jq time for a reference the pod expected to have mounted. Owning components:
`dws-controller`, `dws-orchestrator`.

#### Scenario: Missing referenced Secret prevents pod startup
- **WHEN** a compiled orchestrator or step-service Deployment references a `Secret` / key that
  does not exist in the namespace
- **THEN** the pod fails to start via the standard K8s `secretKeyRef` error path, and the
  workflow instance is not created

#### Scenario: Unreferenced secrets in $secrets are permitted
- **WHEN** `dws-orchestrator`'s env includes `SECRET_UNUSED=x` for a name not consumed by any
  task
- **THEN** the pod starts normally and `$secrets.UNUSED` is available if a future definition
  version were to reference it
