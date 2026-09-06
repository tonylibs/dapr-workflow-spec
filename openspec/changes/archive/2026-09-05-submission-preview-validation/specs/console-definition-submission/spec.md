## ADDED Requirements

### Requirement: Definition editor offers a non-mutating preview action
The `dws-console` definition editor SHALL provide a preview control alongside its
existing submit control. Preview SHALL NOT apply the definition to the cluster and
SHALL NOT alter the editor buffer. The control SHALL be disabled when the buffer
is empty or the operator is not signed in, matching the submit control's own
enablement.

#### Scenario: Operator previews a draft without applying it
- **WHEN** an authenticated operator with a non-empty buffer activates preview
- **THEN** the console reports the preview outcome and no definition is applied to
  the cluster

#### Scenario: Preview is unavailable for an empty buffer
- **WHEN** the buffer is empty
- **THEN** the preview control is disabled

### Requirement: Preview validates spec conformance before requesting a deployment plan
The `dws-console` SHALL run preview as two ordered layers: first `dws-admin`'s
spec-conformance validation of the raw buffer, and only when that reports the
document valid SHALL it request a compile-only deployment plan from
`dws-controller` via the `dws-admin` relay with `dryRun=true`. When the spec layer
reports the document invalid, the console SHALL NOT issue the dry-run request.
Both calls SHALL go through the console's centralized admin transport and carry
the current OIDC bearer token; the console SHALL NOT call `dws-controller`
directly.

#### Scenario: Spec-invalid definition never reaches the controller
- **WHEN** the spec-conformance layer reports the document invalid
- **THEN** the console renders the spec errors and issues no dry-run request

#### Scenario: Spec-valid definition proceeds to the dry run
- **WHEN** the spec-conformance layer reports the document valid
- **THEN** the console requests a dry-run compile through the relay

### Requirement: Preview renders spec errors with their field paths
The `dws-console` SHALL render each spec-conformance error with both its message
and the field path reported by `dws-admin`, and SHALL render a parse failure's
line and column when present. It SHALL retain the operator's buffer unchanged in
every error case.

#### Scenario: Structural error shows its path
- **WHEN** the spec layer returns an error carrying a field path
- **THEN** the console displays that path together with the error message

#### Scenario: Parse failure shows its position
- **WHEN** the spec layer returns a parse error carrying a line and column
- **THEN** the console displays that position with the error message

### Requirement: Preview renders the deployment plan a valid definition would produce
When the dry-run compile succeeds, the `dws-console` SHALL render the returned
deployment plan: the workflow name and version, each deployable step's name, kind,
and image, each pub/sub binding's task, direction, and topic, and the
orchestrator's name and image. The console SHALL parse the plan response against
an expected shape rather than casting it, and SHALL surface an explicit request
error when the response does not match.

#### Scenario: Valid definition renders its plan
- **WHEN** the dry-run compile returns a deployment plan
- **THEN** the console renders the workflow version, the step list, the binding
  list, and the orchestrator

#### Scenario: Plan response has an unexpected shape
- **WHEN** the dry-run response does not match the expected plan shape
- **THEN** the console renders a request-failure outcome rather than partial or
  undefined values

### Requirement: Preview renders deployability rejections distinctly from spec errors
When the dry-run compile is rejected by `dws-controller`, the `dws-console` SHALL
render the returned flat error strings as a deployability outcome, visibly
distinct from spec-conformance errors, and SHALL NOT claim field paths or source
positions for them. Transport and authentication failures during preview SHALL be
rendered as request errors, as they already are for submission.

#### Scenario: Controller rejects a spec-valid definition
- **WHEN** the dry-run request returns a validation rejection with a flat error
  list
- **THEN** the console displays those error strings as a deployability outcome
  without attaching paths or positions

#### Scenario: Preview cannot reach the relay
- **WHEN** a preview request fails to reach the relay or the session has expired
- **THEN** the console displays a request-failure outcome and keeps the buffer
