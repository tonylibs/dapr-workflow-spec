## Why

`dws-controller` and `dws-orchestrator` now publish lifecycle events (`docs/events.md`, Epic 1,
already merged) onto Dapr pub/sub topic `dws.events`, but nothing subscribes to them. Operators
still have no live view of what definitions exist, which deployments succeeded or failed, or which
workflow instances/tasks are running — they'd have to read cluster state and orchestrator logs
directly. This change builds the first slice of `dws-admin`: a new NestJS service that scaffolds
the project, defines the Postgres read-model schema, and subscribes to the event stream to
populate it. It does not expose any read/write REST API yet (that's Epic 3) — only health checks.

## What Changes

- Add a new top-level component `dws-admin/` (Node 24, TypeScript, NestJS, pnpm), sibling to
  `dws-controller`/`dws-orchestrator`/`dws-call-http`/`dws-call-openapi`, each independently
  toolchained per root `CLAUDE.md`.
- Scaffold Nest modules: `ConfigModule` (env-driven config), `StoreModule` (Drizzle/Postgres client
  via `@knaadh/nestjs-drizzle-postgres`), `DaprEventsModule` (subscription handlers via
  `@dbc-tech/nest-dapr`), `WorkflowsModule` and `InstancesModule` (empty controller scaffolding
  only — real handlers land in Epic 3).
- Define the Postgres read-model schema in Drizzle (`workflow_definitions`, `deployments`,
  `workflow_instances`, `task_events`, `processed_events`) with checked-in `drizzle-kit` migrations
  under `dws-admin/drizzle/`.
- Subscribe to topic `dws.events` (component name from config) and upsert into the read model for
  every event type in `docs/events.md`: `definition.created`/`updated`,
  `deployment.applied`/`failed`/`drained`/`collected`, `instance.started`/`completed`/`failed`,
  `task.started`/`completed`/`failed`.
- Make every handler idempotent (via a `processed_events` guard, checked in the same DB transaction
  as the write) and tolerant of out-of-order, at-least-once delivery.
- Add a `/health` endpoint (`@nestjs/terminus`) checking DB connectivity, a local
  `docker-compose.yml` for Postgres, and a README section on running `dapr run` locally.
- **Out of scope**: any read/write REST endpoint beyond health checks, the `commands` audit-log
  table (Epic 5), and any UI.

## Capabilities

### New Capabilities
- `admin-service-scaffold`: the `dws-admin` NestJS project itself — module layout
  (`ConfigModule`/`StoreModule`/`DaprEventsModule`/`WorkflowsModule`/`InstancesModule`), health
  endpoint, and local dev setup (docker-compose + `dapr run`).
- `admin-read-model-schema`: the Postgres/Drizzle schema for the read model
  (`workflow_definitions`, `deployments`, `workflow_instances`, `task_events`,
  `processed_events`) and its migration discipline (checked-in `drizzle-kit generate` output only).
- `admin-event-ingestion`: `dws-admin`'s Dapr pub/sub subscription to `dws.events`, covering every
  event type from `docs/events.md`, with idempotent, out-of-order-safe upserts into the read model.

### Modified Capabilities
<!-- None. docs/events.md (the lifecycle-events contract from Epic 1) is consumed, not changed. -->

## Impact

- **New component**: `dws-admin/` — `package.json`, `src/` (Nest modules), `drizzle/`
  (migrations), `docker-compose.yml`, `README.md` section.
- **New dependencies**: `@nestjs/*` core packages, `@knaadh/nestjs-drizzle-postgres`,
  `drizzle-orm` + `postgres`, `@dbc-tech/nest-dapr`, `@dapr/dapr`, `@nestjs/config`,
  `@nestjs/terminus`, `drizzle-kit` (dev), Jest (dev).
- **Deployment**: requires a Postgres instance and the same in-cluster Dapr pub/sub `Component`
  (`pubsub` / topic `dws.events`) that Epic 1 already documents as a prerequisite — no new
  cluster-level prerequisite is introduced.
- **No changes** to `dws-controller`, `dws-orchestrator`, `dws-call-http`, or `dws-call-openapi`,
  and no changes to the `docs/events.md` contract.
- **CI**: a path-filtered GitHub Actions workflow for `dws-admin` is explicitly deferred to a later
  task, not this change.
