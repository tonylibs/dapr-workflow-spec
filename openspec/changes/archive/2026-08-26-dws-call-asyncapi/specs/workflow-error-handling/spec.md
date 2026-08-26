## ADDED Requirements

### Requirement: A step-service payload-validation failure classifies as a validation error

The orchestrator SHALL classify a step-service payload-validation failure as `ErrorKind.VALIDATION`
(error `type` URI `https://serverlessworkflow.io/spec/1.0.0/errors/validation`) rather than a
generic communication or runtime failure, so a `catch.errors.with.type` filter for validation
matches it. Classification SHALL use a stable marker in the failure message, consistent with the
existing timeout, data-flow, config, and step markers, and SHALL be checked before the generic step
communication classification.

#### Scenario: Validation failure is caught by a validation type filter
- **WHEN** a step service rejects a request payload with a validation failure carrying the validation
  marker
- **THEN** the built error object's `type` is the validation error-type URI and a
  `catch.errors.with.type` filter for that URI matches it

#### Scenario: Non-validation step failures are unaffected
- **WHEN** a step service fails with an upstream/transport failure or a non-validation status
- **THEN** the failure continues to classify as a communication error, unchanged by this addition
