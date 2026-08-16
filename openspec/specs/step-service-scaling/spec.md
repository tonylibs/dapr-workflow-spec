# step-service-scaling

## Purpose

How `dws-controller` scales deployed step services: the Knative `min-scale` annotation is
conditional on `TaskKind` so activity-invoked steps stay live to receive dispatched work while
HTTP-triggered `call: openapi` steps keep scale-to-zero. Also the shared-namespace/state-store
prerequisite that cross-app activity dispatch depends on.

## Requirements

### Requirement: Knative min-scale is conditional on task kind
`dws-controller` SHALL set the `autoscaling.knative.dev/min-scale` annotation on each synthesized
step Knative Service from the step's `TaskKind`: `"1"` for `CALL_HTTP`, `RUN_SHELL`,
`RUN_SCRIPT_JS`, and `RUN_SCRIPT_PYTHON` steps (which are invoked as activities and MUST stay
live to receive dispatched work), and `"0"` for `CALL_OPENAPI` steps (which remain
HTTP-triggered and MAY scale to zero). No other step annotation behavior changes.

#### Scenario: activity-invoked step stays live
- **WHEN** the controller synthesizes the Knative Service for a `CALL_HTTP`, `RUN_SHELL`, or `RUN_SCRIPT` step
- **THEN** its `autoscaling.knative.dev/min-scale` annotation is `"1"`

#### Scenario: openapi step keeps scale-to-zero
- **WHEN** the controller synthesizes the Knative Service for a `CALL_OPENAPI` step
- **THEN** its `autoscaling.knative.dev/min-scale` annotation is `"0"`

#### Scenario: other step annotations are unchanged
- **WHEN** the controller synthesizes any step Knative Service
- **THEN** the `dapr.io/enabled`, `dapr.io/app-id`, and `dapr.io/app-port` annotations are set as before
- **AND** only the `min-scale` value varies by task kind

### Requirement: Step and orchestrator pods share the dispatch backend
`dws-controller` SHALL deploy the step Knative Services and the workflow's orchestrator
Deployment into the same namespace, wired to the same Dapr workflow/actor state-store component,
so that cross-app activity dispatch resolves a shared backend.

#### Scenario: shared namespace and state store
- **WHEN** the controller deploys a workflow stack with activity-invoked steps
- **THEN** the orchestrator Deployment and the step Knative Services are in the same namespace
- **AND** they reference the same Dapr workflow/actor state-store component
