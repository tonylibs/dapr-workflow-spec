# admin-definition-validation Specification

## Purpose

Defines non-mutating DSL definition validation exposed by `dws-admin`.

## Requirements

### Requirement: Admin exposes a non-mutating definition spec-validation endpoint
`dws-admin` SHALL expose `POST /definitions/validate`, which accepts a raw DSL
definition as the request body with `Content-Type` `application/yaml`,
`application/x-yaml`, `text/yaml`, or `application/json`, and SHALL return a
validation report without contacting `dws-controller` and without mutating any
state. The endpoint SHALL require the same bearer authentication as every other
`dws-admin` route. It SHALL return HTTP 200 for any well-formed request,
reporting document validity in the body rather than in the status code, and SHALL
reserve non-2xx statuses for malformed requests: 400 for an empty body or an
unsupported content type, and 413 for a body larger than 1 MiB.

#### Scenario: Valid definition is reported as valid
- **WHEN** a client posts a definition that parses and satisfies the vendored DSL
  schema
- **THEN** the endpoint returns HTTP 200 with `{ "valid": true }` and no errors

#### Scenario: Empty body is a request error
- **WHEN** a client posts an empty body
- **THEN** the endpoint returns HTTP 400 and does not return a validation report

#### Scenario: Oversized body is rejected before parsing
- **WHEN** a client posts a body larger than 1 MiB
- **THEN** the endpoint returns HTTP 413 and does not parse or validate the body

### Requirement: Unparseable definitions are reported with a source position
`dws-admin` SHALL parse the submitted body as YAML (which subsumes JSON) before
schema validation. When parsing fails, it SHALL report the failure as a validation
error distinct from a schema error, and SHALL include the `line` and `column`
reported by the parser when the parser provides a position.

#### Scenario: Malformed YAML reports line and column
- **WHEN** a client posts a body that is not well-formed YAML or JSON
- **THEN** the report has `valid: false` and contains an error carrying the
  parser's message and its `line` and `column`

### Requirement: Definitions are validated against the schema the controller's parser was generated from
`dws-admin` SHALL validate the parsed document against a vendored JSON Schema
extracted from the `serverlessworkflow-types` artifact whose version is pinned by
`dws-controller`'s `pom.xml` (`serverlessworkflow.version`). It SHALL NOT validate
against a different published revision of the DSL schema. Validation SHALL use a
JSON Schema draft 2020-12 validator with all errors collected, and each reported
error SHALL carry the JSON pointer (`path`) of the offending location and the
validator's message.

#### Scenario: Structural violation is reported with a JSON pointer
- **WHEN** a client posts a document whose task violates the schema's shape for
  its task kind
- **THEN** the report has `valid: false` and the error's `path` is the JSON
  pointer to the offending field

#### Scenario: Missing required document fields are reported
- **WHEN** a client posts a document without `document` or without `do`
- **THEN** the report has `valid: false` and names the missing required members

#### Scenario: Definitions the controller compiles today remain valid
- **WHEN** a definition that `dws-controller` compiles successfully is posted,
  including one using the object form of `run.shell` `arguments`
- **THEN** the report has `valid: true`

#### Scenario: Error list is capped
- **WHEN** validation produces more errors than the reporting cap
- **THEN** the report returns at most the capped number of errors and marks the
  result as truncated

### Requirement: Task names are checked for uniqueness across nested bodies
`dws-admin` SHALL additionally verify that task names are unique across the whole
definition, including names declared inside nested `try`, `catch`, `for`, and
`fork` bodies, because JSON Schema alone cannot express that constraint. Each
duplicate SHALL be reported as a validation error whose `path` points at the
repeated occurrence.

#### Scenario: Duplicate name nested inside a try body is rejected
- **WHEN** a client posts a definition whose top-level `do` and a nested
  `try`/`catch` body both declare a task with the same name
- **THEN** the report has `valid: false` and contains a duplicate-task-name error
  whose `path` points at the repeated occurrence

#### Scenario: Distinct names across nested bodies are accepted
- **WHEN** a client posts a definition whose nested bodies declare only distinct
  task names
- **THEN** the uniqueness check contributes no errors

### Requirement: The vendored schema records its provenance and fails on drift
The vendored schema SHALL be accompanied by checked-in provenance recording the
source SDK version, the schema `$id`, the source artifact, and a content hash. A
repository-provided script SHALL regenerate both from the SDK version declared in
`dws-controller/pom.xml`. `dws-admin`'s test suite SHALL fail when the recorded
provenance no longer matches that pinned version or the checked-in schema's `$id`,
so a controller-side SDK upgrade cannot silently desynchronise the two layers.
When `dws-controller/pom.xml` is not present in the checkout, the check SHALL skip
with an explicit message rather than fail.

#### Scenario: Controller bumps the SDK without revendoring
- **WHEN** `dws-controller`'s pinned `serverlessworkflow.version` differs from the
  vendored provenance's recorded SDK version
- **THEN** `dws-admin`'s test suite fails and names both versions

#### Scenario: Vendored schema does not match its recorded identity
- **WHEN** the checked-in schema's `$id` differs from the recorded `schemaId`
- **THEN** `dws-admin`'s test suite fails

#### Scenario: Controller sources are absent from the checkout
- **WHEN** `dws-controller/pom.xml` cannot be found
- **THEN** the drift check skips and reports why, rather than failing
