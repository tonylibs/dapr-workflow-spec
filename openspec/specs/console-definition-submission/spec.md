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
