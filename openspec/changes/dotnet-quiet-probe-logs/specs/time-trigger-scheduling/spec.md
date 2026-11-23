## ADDED Requirements

### Requirement: Successful trigger starts are logged by the scheduler

Owner: `dws-schedule-time`. When an `every`/`cron` fire or an `after` start ends with the outcome "started", the scheduler SHALL log exactly one `Information` record from its own category. The record SHALL name the workflow, the instance ID and the start detail. This lets a fire be seen without framework request logging.

#### Scenario: every fire that starts an instance is logged

- **GIVEN** `order-sync` has an active `every: PT1M` trigger and the orchestrator answers `202`
- **WHEN** the scheduler handles a fire with fire key `dws-sched:order-sync:<key>`
- **THEN** one `Information` record from `Dws.ScheduleTime.ScheduledJobHandler` names `order-sync`, `dws-sched:order-sync:<key>` and `HTTP 202`

#### Scenario: Replayed tick is logged as already started

- **GIVEN** an instance with fire key `dws-sched:order-sync:<key>` was already started through the claim
- **WHEN** a replayed tick for the same fire key is handled
- **THEN** no second instance is started
- **AND** one `Information` record names that instance ID with the detail `already started`

#### Scenario: after start is logged

- **GIVEN** `order-sync` has an active `after` trigger in phase `Waiting` at sequence 6
- **WHEN** the scheduler starts `dws-after:order-sync:7` and the orchestrator answers `202`
- **THEN** one `Information` record from `Dws.ScheduleTime.AfterLifecycle` names `order-sync` and `dws-after:order-sync:7`

#### Scenario: Failed start is not logged as started

- **GIVEN** the orchestrator answers `503` to a start
- **WHEN** a fire is handled
- **THEN** no "started" `Information` record is written for that fire key, and the existing error record is written instead
