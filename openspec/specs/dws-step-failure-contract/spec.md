# dws-step-failure-contract

## Purpose

The v1-identical failure message wording `dws-step` produces so `dws-orchestrator`'s `WorkflowErrors.classify` categorises step failures correctly across the Dapr activity boundary.

## Requirements

### Requirement: Failures carry v1-identical wording
Only a failure's message crosses the Dapr activity boundary, and v1's `WorkflowErrors.classify` reads
the wording. `dws-step` SHALL therefore produce these messages, with `<task>` the node's task name
(or app id where v1 used it):

| Failure | Retryable | v1 category | Message form | Marker v1 classifies on |
|---|---|---|---|---|
| Transport or upstream failure (HTTP 502 reserved) | Yes | communication | `step '<task>' upstream failure: <detail>` | `upstream failure:` |
| Configuration or shaping fault | No | runtime | `step '<task>' config failure: <detail>` | `config failure:` |
| Payload rejected by validation | No | validation | `validation failed: <detail>` | `validation failed:` |

#### Scenario: Upstream failure
- **WHEN** an upstream/transport failure is raised
- **THEN** its message is `step '<task>' upstream failure: <detail>` and v1's `WorkflowErrors.classify` yields COMMUNICATION

#### Scenario: Configuration failure
- **WHEN** a configuration or shaping fault is raised
- **THEN** its message is `step '<task>' config failure: <detail>`, it is non-retryable, and v1's classify yields RUNTIME

#### Scenario: Validation failure
- **WHEN** a payload is rejected by validation
- **THEN** its message contains `validation failed:` and v1's classify yields VALIDATION

#### Scenario: Wording checked against v1 fixtures
- **WHEN** the failure messages are compared to fixtures taken from `dws-orchestrator`'s tests
- **THEN** they match exactly, and a ported copy of v1's classification rules assigns each the category in the table
