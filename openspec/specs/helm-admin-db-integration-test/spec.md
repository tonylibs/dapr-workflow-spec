# helm-admin-db-integration-test

## Purpose

Prove a deployed `dws-admin` can connect to its PostgreSQL backend after a real Helm install.

## Requirements

### Requirement: Helm test asserts the database health indicator

`charts/dws` SHALL provide the `helm.sh/hook: test` resource
`templates/tests/admin-db-connection.yaml`. It SHALL request the deployed admin Service's
`/health` endpoint and fail unless the response is HTTP 200 and its `database` health indicator
reports `up`.

#### Scenario: Healthy database

- **WHEN** `helm test` runs after admin can reach PostgreSQL
- **THEN** the test Pod succeeds

#### Scenario: Unhealthy database

- **WHEN** the health response is non-200 or its database indicator is not `up`
- **THEN** the test Pod and `helm test` fail

### Requirement: CI exercises a real install

The Helm workflow SHALL install the chart without `--dry-run`, wait for the admin Deployment and
the Bitnami PostgreSQL StatefulSet, and run `helm test`.

#### Scenario: Broken integration

- **WHEN** the test hook fails
- **THEN** the Helm workflow fails
