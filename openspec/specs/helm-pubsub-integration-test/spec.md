# helm-pubsub-integration-test

## Purpose

Prove a deployed `pubsub` Dapr Component actually carries traffic after a real Helm install,
rather than only asserting that the chart renders and installs it.

## Requirements

### Requirement: CI proves the pubsub Component is Ready and delivers messages

The Helm workflow's `integration` job (or a comparable leg) SHALL confirm, after a real install
with `dapr.enabled=true`, that the deployed `pubsub` Component reports a Ready state in-cluster and
that a message published to topic `dws.events` is delivered to a subscriber. A full
controller/orchestrator/admin round-trip is not required — a minimal publisher/subscriber
sufficient to prove delivery satisfies this requirement.

Owning component: `.github/workflows/helm.yml`.

#### Scenario: Healthy pubsub wiring

- **WHEN** the `integration` job installs the chart with `dapr.enabled=true` and runs the pubsub
  assertion
- **THEN** the `pubsub` Component reports Ready and a message published to `dws.events` is
  observed by a subscriber
- **AND** the job succeeds

#### Scenario: Broken pubsub wiring

- **WHEN** the `pubsub` Component fails to report Ready, or a published message is not delivered
  within the job's timeout
- **THEN** the Helm workflow fails

### Requirement: The dapr-disabled CI leg is unaffected

The existing `dapr.enabled=false` CI leg (`integration-dapr-preinstalled`) SHALL NOT be required
to run the pubsub delivery assertion, since it does not install the admin/controller workload
stack.

Owning component: `.github/workflows/helm.yml`.

#### Scenario: Dapr-preinstalled leg unchanged

- **WHEN** the `integration-dapr-preinstalled` job runs
- **THEN** it continues to assert only that no chart-installed Dapr resources appear, with no
  pubsub delivery assertion added
