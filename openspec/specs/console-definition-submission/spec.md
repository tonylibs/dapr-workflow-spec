# console-definition-submission Specification

## Purpose

Defines the authenticated `dws-console` raw DSL editor and its submission behavior through the
`dws-admin` controller relay.

## Requirements

### Requirement: Definition editor is available to authenticated console users
The `dws-console` SHALL provide a dedicated workflow-definition editor route for an authenticated
operator. The route SHALL contain a writable raw-text buffer for a DSL 1.0 definition and a
submission control, and SHALL be reachable from the console's workflow navigation.

#### Scenario: Operator opens the definition editor
- **WHEN** an authenticated operator selects the workflow-definition authoring entry point
- **THEN** the console renders the dedicated editor route with an empty writable DSL buffer and a
  submit control

### Requirement: Editor supports YAML and JSON source highlighting
The `dws-console` SHALL render the definition buffer with CodeMirror 6 using
`@codemirror/lang-yaml` and `@codemirror/lang-json`, and SHALL provide a format selection that
applies the corresponding syntax-highlighting extension without mutating the buffer text. The
editor theme SHALL use existing console design tokens via `EditorView.theme()` and SHALL NOT add
Monaco.

#### Scenario: Operator selects JSON highlighting
- **WHEN** an operator selects JSON as the editor format
- **THEN** the existing raw buffer remains byte-for-byte unchanged and CodeMirror uses the JSON
  language extension

#### Scenario: Operator selects YAML highlighting
- **WHEN** an operator selects YAML as the editor format
- **THEN** the existing raw buffer remains byte-for-byte unchanged and CodeMirror uses the YAML
  language extension

### Requirement: Editor submits definitions through the authenticated admin relay
The `dws-console` SHALL submit the buffer to `dws-admin`'s `POST /workflows` relay with
`dryRun=false`, using the configured admin base URL. The request SHALL preserve the raw buffer as
its body, identify it as YAML/JSON source, and carry `Authorization: Bearer <access-token>` using
the current OIDC token. The console SHALL NOT call `dws-controller` directly.

#### Scenario: Definition applies successfully
- **WHEN** an operator submits a non-empty definition and the relay returns a successful
  `ApplyResult` with `created: true`
- **THEN** the console displays the applied result as a success outcome

#### Scenario: Identical definition is resubmitted
- **WHEN** an operator submits a definition whose canonical content is already applied and the
  relay returns an `ApplyResult` with `created: false`
- **THEN** the console displays an idempotent no-op success outcome rather than an error

### Requirement: Editor renders controller validation and request failures distinctly
The `dws-console` SHALL render every string in the raw `errors[]` list returned by a 400 response
as a validation-error outcome. It SHALL render non-400 or transport failures as an explicit request
error and retain the operator's buffer in both failure cases. It SHALL NOT claim line/path
locations or highlight source positions from the current flat error response.

#### Scenario: Controller rejects an invalid definition
- **WHEN** the relay returns HTTP 400 with an `errors[]` list
- **THEN** the console displays each returned error string and keeps the edited definition in the
  buffer

#### Scenario: Relay is unreachable
- **WHEN** a submission cannot reach the relay or receives a non-400 error response
- **THEN** the console displays a request-failure outcome and keeps the edited definition in the
  buffer

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
