## ADDED Requirements

### Requirement: Read model schema is defined in Drizzle
The read model SHALL be defined as Drizzle schema files under `dws-admin/src/store/schema/`,
covering five tables: `workflow_definitions`, `deployments`, `workflow_instances`, `task_events`,
and `processed_events`. These schema files SHALL be the `config.schema` passed into
`DrizzlePostgresModule`'s registration, so the injected client is fully typed against them.

#### Scenario: Schema is the single source of table shape
- **WHEN** a repository queries any of the five tables through the injected Drizzle client
- **THEN** the query is type-checked against the schema file's column definitions at compile time

### Requirement: workflow_definitions tracks definition lifecycle
`workflow_definitions` SHALL have columns `name`, `version`, `status`, and `created_at`, keyed so
that a given `(name, version)` pair is unique.

#### Scenario: Definition version is unique
- **WHEN** two rows are written for the same `name` and `version`
- **THEN** the second write updates the existing row rather than creating a duplicate

### Requirement: deployments tracks deployment lifecycle without recomputing identifiers
`deployments` SHALL have columns `workflow`, `version`, `step_services` (jsonb), and
`orchestrator_app_id`, `status`, `drained_at`. `orchestrator_app_id` SHALL be stored exactly as
received from the event payload and never derived from a naming formula inside `dws-admin`.

#### Scenario: orchestrator_app_id is stored, not computed
- **WHEN** a `deployment.applied` event is processed
- **THEN** the row's `orchestrator_app_id` column is set to the event payload's
  `orchestratorAppId` field verbatim, with no kebab-case or other transformation applied

### Requirement: workflow_instances and task_events track runtime state
`workflow_instances` SHALL have `instance_id` as its primary key, plus `workflow`, `version`,
`app_id`, `status`, `started_at`, `ended_at`. `task_events` SHALL have `id` as its primary key, plus
`instance_id` as a foreign key into `workflow_instances`, `task_name`, `type`, `status`,
`timestamp`, `error`.

#### Scenario: task_events references a known instance
- **WHEN** a `task_events` row is inserted for a given `instance_id`
- **THEN** that `instance_id` corresponds to a row in `workflow_instances` (created by the
  corresponding `instance.*` event, regardless of arrival order)

### Requirement: processed_events guards idempotency
`processed_events` SHALL have `event_id` as its primary key and a `processed_at` timestamp column,
used by every event-consuming write path to detect and skip already-processed events.

#### Scenario: Duplicate event id is rejected
- **WHEN** an insert into `processed_events` is attempted for an `event_id` already present
- **THEN** the insert does not create a second row and the conflict is detectable by the caller
  (e.g. via `ON CONFLICT DO NOTHING RETURNING`)

### Requirement: Schema changes ship only as checked-in migrations
All schema changes SHALL be produced by `drizzle-kit generate` and checked into
`dws-admin/drizzle/`. No schema change SHALL be applied to a running database by hand-edited SQL or
by `drizzle-kit push` outside of local development iteration.

#### Scenario: Fresh database reaches current schema via migrations only
- **WHEN** `pnpm db:migrate` (or the boot-time migration step) runs against an empty Postgres
  database
- **THEN** the database ends up with all five tables in their current shape, using only the SQL
  files checked into `dws-admin/drizzle/`
