## ADDED Requirements

### Requirement: Scheduler resolves each workflow's active trigger from the cluster

Owner: `dws-schedule-time`. The scheduler SHALL watch, in its namespace, ConfigMaps labeled
`dws.io/trigger=time` and orchestrator Deployments labeled `dws.io/managed-by=dws-controller`. A
workflow's active trigger SHALL be the trigger config of its version whose orchestrator Deployment
exists and is not annotated `dws.io/drain: "true"`.

#### Scenario: Draining version's trigger is ignored
- **GIVEN** versions A (annotated `dws.io/drain: "true"`) and B of `order-sync` both have trigger ConfigMaps
- **WHEN** the scheduler resolves active triggers
- **THEN** only version B's trigger is active for `order-sync`

### Requirement: Scheduler keeps one job per scheduled workflow

Owner: `dws-schedule-time`. For each workflow with an active trigger, the scheduler SHALL keep exactly
one registered Dapr job named `dws-sched-<workflow>`. Reconciliation SHALL be idempotent across
scheduler replicas, and the scheduler SHALL keep no state of its own beyond its jobs, the `after`
bookkeeping and the per-instance-ID start claims.

#### Scenario: Newly deployed scheduled workflow is registered
- **GIVEN** `dws-schedule-time` is running
- **WHEN** the controller deploys `order-sync` with `schedule.cron: "*/5 * * * *"`
- **THEN** a Dapr job `dws-sched-order-sync` exists with schedule `0 */5 * * * *`

#### Scenario: Rebuild after restart
- **GIVEN** three scheduled workflows are deployed and `dws-schedule-time` is restarted
- **WHEN** the new scheduler pod finishes its initial list of the cluster
- **THEN** each of the three workflows has exactly one registered job matching its active trigger

### Requirement: Unchanged jobs are not overwritten

Owner: `dws-schedule-time`. The scheduler SHALL overwrite a job only when the active trigger's version,
kind or schedule differs from the registered job's data.

#### Scenario: Unchanged trigger is not re-anchored
- **GIVEN** `order-sync` has `every: PT1H` registered at 10:00Z
- **WHEN** the scheduler reconciles again at 10:20Z with no change to the active trigger
- **THEN** the job is not overwritten and its next fire stays at 11:00Z

### Requirement: Version takeover moves the trigger to the new version

Owner: `dws-schedule-time`. When a new version of a scheduled workflow becomes active, every
subsequent fire SHALL start an instance of the new version. Instances already started on the
draining version SHALL be left untouched. When the new version has no time trigger, the job SHALL
be deleted.

#### Scenario: New version takes over the schedule
- **GIVEN** version A of `order-sync` is active with `cron: "0 * * * *"`
- **WHEN** version B with `cron: "30 * * * *"` is deployed and version A is annotated `dws.io/drain: "true"`
- **THEN** job `dws-sched-order-sync` is overwritten with version B's schedule
- **AND** the next fire starts an instance on version B's orchestrator app-id

#### Scenario: New version drops the schedule
- **GIVEN** version A of `order-sync` is active with a time trigger
- **WHEN** version B without `schedule` becomes active
- **THEN** job `dws-sched-order-sync` is deleted and no further instances are started by the scheduler

### Requirement: Deleted workflows stop firing

Owner: `dws-schedule-time`. When a workflow has no active trigger, the scheduler SHALL delete its job.
If a fire arrives for a workflow with no active trigger, the scheduler SHALL delete the job and
SHALL NOT start an instance.

#### Scenario: Workflow deleted while scheduler runs
- **GIVEN** `order-sync` is scheduled
- **WHEN** the workflow is deleted through the controller API
- **THEN** job `dws-sched-order-sync` is deleted

#### Scenario: Stale job fires after missed deletion
- **GIVEN** `order-sync` was deleted while `dws-schedule-time` was down and its job still exists
- **WHEN** the job fires
- **THEN** no instance is started and the job is deleted

### Requirement: every fires at a fixed interval and may overlap

Owner: `dws-schedule-time`. For `kind: every`, the job SHALL fire once per interval counted from
registration. Each fire SHALL start a new instance whether or not earlier instances are still
running. The fire key SHALL be the interval tick computed from the registration anchor, in UTC
`yyyyMMddTHHmmssZ`.

#### Scenario: Overlapping runs are allowed
- **GIVEN** `every: PT1M` and an instance that takes 3 minutes
- **WHEN** three intervals elapse
- **THEN** three instances have been started and up to three run concurrently

### Requirement: cron fires on UTC matches

Owner: `dws-schedule-time`. For `kind: cron`, the 5-field expression SHALL be evaluated in UTC and
registered as Dapr's 6-field form with a `0` seconds field. The fire key SHALL be the fire time
truncated to the minute, in UTC.

#### Scenario: Daily cron at midnight UTC
- **GIVEN** `cron: "0 0 * * *"`
- **WHEN** the clock reaches 2026-11-06T00:00:00Z
- **THEN** one instance `dws-sched:<workflow>:20261106T000000Z` is started

### Requirement: after starts the next run a delay after the previous run ends

Owner: `dws-schedule-time`. For `kind: after`, once the latest instance reaches a terminal status
(completed, failed or terminated), the scheduler SHALL start the next instance after the configured
delay. Completion SHALL be detected by polling instance status at a configurable interval (default
15 seconds), never by lifecycle events.

#### Scenario: Next run waits for completion plus delay
- **GIVEN** `after: PT10M` and instance `dws-after:order-sync:1` running
- **WHEN** that instance completes at 12:00Z
- **THEN** no new instance starts before 12:10Z
- **AND** instance `dws-after:order-sync:2` starts at or shortly after 12:10Z

#### Scenario: Failed run still schedules the next one
- **GIVEN** `after: PT5M` and instance `dws-after:order-sync:3` fails
- **WHEN** the scheduler observes the failed status
- **THEN** instance `dws-after:order-sync:4` starts 5 minutes later

#### Scenario: Works with the Events layer off
- **GIVEN** the chart is installed with the Events layer disabled, so no lifecycle events are published
- **WHEN** an `after` workflow's instance completes
- **THEN** the scheduler still starts the next instance after the delay

### Requirement: after sequence is durable

Owner: `dws-schedule-time`. The scheduler SHALL start the first `after` instance as soon as the trigger
becomes active and no bookkeeping exists for the workflow. The sequence SHALL be kept in the Dapr
state store and SHALL survive scheduler restarts. Takeover SHALL apply to the next start, not to
the instance being awaited.

#### Scenario: First activation starts immediately
- **GIVEN** a newly deployed `after: PT10M` workflow with no stored sequence
- **WHEN** the scheduler activates its trigger
- **THEN** instance `dws-after:<workflow>:1` starts without waiting for the delay

#### Scenario: Restart does not reset the sequence
- **GIVEN** `order-sync` has reached sequence 7 and `dws-schedule-time` restarts
- **WHEN** the scheduler resumes
- **THEN** it continues polling `dws-after:order-sync:7` and does not start sequence 1 again

### Requirement: Fire failures are retried briefly, then dropped

Owner: `dws-schedule-time`. When a start fails with a transport error, 5xx or 429, the job handler SHALL
report failure so Dapr retries the fire up to 3 times, 10 seconds apart. A fire that still fails
SHALL be dropped and logged at error level with the workflow, fire key and cause. Other 4xx SHALL
be logged and dropped without retry. The scheduler SHALL NOT catch up missed fires itself. Ticks
that Dapr replays after scheduler downtime SHALL collapse onto one instance, because they share one
fire key and the start is idempotent per instance ID.

#### Scenario: Replayed burst after downtime starts one instance
- **GIVEN** `dws-schedule-time` was down for several `every` intervals
- **WHEN** Dapr delivers the missed ticks in a burst to two replicas
- **THEN** exactly one instance is started, even if the workflow completes within milliseconds

#### Scenario: Target recovers within the retry window
- **GIVEN** the orchestrator returns 503 for the first start attempt and 202 for the second
- **WHEN** a fire is handled
- **THEN** exactly one instance is started with that fire's instance ID

#### Scenario: Target stays down
- **GIVEN** the orchestrator is unreachable for longer than the retry window
- **WHEN** a fire is handled
- **THEN** no instance is started for that fire key, an error log names the workflow and fire key, and the next scheduled fire is attempted normally

### Requirement: Job callbacks and starts work with machine auth on

Owner: `dws-schedule-time` and `charts/dws`. With `auth.enabled=true`, job callbacks SHALL reach the
scheduler app, and starts through its sidecar SHALL authenticate with `DAPR_API_TOKEN`. This SHALL
be proven on a live cluster before the change is archived.

#### Scenario: Scheduled start with auth enabled
- **GIVEN** the chart installed with `auth.enabled=true` and a deployed `every: PT1M` workflow
- **WHEN** one minute elapses
- **THEN** a new instance of that workflow exists and neither sidecar logged a 401
