## Purpose

Post-apply proof that a deployed `dws-admin` can actually reach a deployed Postgres — closing the
gap left by CI's existing dry-run-only verification, which starts no pods and so cannot observe a
real DB connection failure.

## ADDED Requirements

### Requirement: A Helm test hook asserts admin's DB indicator is up

`charts/dws` SHALL provide a Helm test hook
(`templates/tests/admin-db-connection.yaml`, annotated `helm.sh/hook: test`) that, after
`helm install`/`helm test`, issues an HTTP request to the deployed admin Service's `/health`
endpoint and fails the hook unless the response is HTTP `200` **and** the response body's
`database` health indicator (per `dws-admin`'s `@nestjs/terminus` payload shape) reports status
`up`. A `200` response whose `database` indicator is not `up` SHALL still fail the hook.

#### Scenario: Hook passes when the DB indicator is up
- **WHEN** `helm test` runs against a release where admin can reach Postgres
- **THEN** the test hook Job/Pod succeeds

#### Scenario: Hook fails when the DB indicator is down
- **WHEN** `helm test` runs against a release where admin's `/health` reports the `database`
  indicator as anything other than `up` (including a `200` response with a down `database`
  indicator)
- **THEN** the test hook Job/Pod fails, and `helm test` reports failure

#### Scenario: Hook is gated with the admin resources
- **WHEN** `helm template charts/dws --set admin.enabled=false --show-only templates/tests/admin-db-connection.yaml` is run
- **THEN** no test hook resource is rendered

### Requirement: CI performs a real install and runs the Helm test

`.github/workflows/helm.yml` SHALL run a job that installs `charts/dws` onto the existing kind
cluster without `--dry-run`, with `postgresql.enabled=true`, waits for both the `admin` and
`postgres` rollouts to complete (`kubectl rollout status`), then runs `helm test` against the
release and fails the workflow if the test hook fails. The workflow SHALL document, in a comment
near this job, why the existing dry-run job cannot substitute for it (dry-run starts no pods, so
no Postgres or admin process ever runs and no DB connectivity is exercised).

#### Scenario: CI fails on a broken DB integration
- **WHEN** the real-install job's `helm test` step reports failure
- **THEN** the `helm-chart` workflow run fails

#### Scenario: CI passes on a healthy install
- **WHEN** both rollouts complete and `helm test` succeeds
- **THEN** the real-install job succeeds

#### Scenario: Existing dry-run job is unchanged
- **WHEN** the `helm-chart` workflow runs
- **THEN** the pre-existing lint/template/dry-run job still runs as before, and the new real-install
  job runs in addition to it (not as a replacement)
