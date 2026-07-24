# Tasks: dws-admin-skeleton (Epic 2)

Maps to scope items S2.1–S2.5. Do S2.1 (scaffold) first, then S2.2 (schema), then S2.3/S2.4
(event ingestion, sharing the decoder/transaction helper from design D2/D4), then S2.5 (health +
local dev).

## 1. Scaffold the Nest project (S2.1)

- [ ] 1.1 Create `dws-admin/` with `nest new` conventions: `package.json` (pnpm, Node >=24 engine),
  `tsconfig.json`, `nest-cli.json`, `src/main.ts`, `src/app.module.ts`.
- [ ] 1.2 Add direct dependencies: `@nestjs/core`, `@nestjs/common`, `@nestjs/platform-express`,
  `@nestjs/config`, `@nestjs/terminus`, `@knaadh/nestjs-drizzle-postgres`, `drizzle-orm`,
  `postgres`, `@dbc-tech/nest-dapr`, `@dapr/dapr`; dev dependencies: `drizzle-kit`, `jest`,
  `ts-jest` (or Nest's default Jest setup), `@nestjs/testing`, `typescript`.
- [ ] 1.3 Add `build` (`nest build`), `start` (`nest start`), `start:dev` (`nest start --watch`),
  `test` (Jest), `lint` scripts to `package.json` — do not copy `dws-call-openapi`'s script names.
- [ ] 1.4 Add `ConfigModule` (`src/config/config.module.ts`) wrapping env vars: `DATABASE_URL`,
  `DAPR_PUBSUB_NAME` (default `pubsub`), `DAPR_PUBSUB_TOPIC` (default `dws.events`), `DAPR_HTTP_PORT`
  / `DAPR_GRPC_PORT` as needed by `@dbc-tech/nest-dapr`, `PORT` for the Nest app itself; validate at
  boot (e.g. `@nestjs/config` `validationSchema` or a small manual validator) and fail fast on a
  missing required var.
- [ ] 1.5 Add `StoreModule` (`src/store/store.module.ts`) importing
  `DrizzlePostgresModule.registerAsync({ tag: 'DB', imports: [ConfigModule], useFactory: ... ,
  inject: [ConfigService] })` with `config.schema` pointing at the schema barrel from task 2.1;
  re-export the `'DB'` provider.
- [ ] 1.6 Add Dapr wiring (`src/dapr/dapr.module.ts` or inline in `DaprEventsModule`) using
  `DaprModule.registerAsync({ imports: [ConfigModule], useFactory: ..., inject: [ConfigService] })`.
- [ ] 1.7 Add `DaprEventsModule` (`src/events/dapr-events.module.ts`) importing `StoreModule` and
  the Dapr module; scaffold empty `ControllerEventsHandler`/`OrchestratorEventsHandler` providers
  (bodies filled in section 4).
- [ ] 1.8 Add `WorkflowsModule` and `InstancesModule` with empty `@Controller()` classes and no
  route methods; import both into `AppModule` alongside `ConfigModule`/`StoreModule`/
  `DaprEventsModule`.
- [ ] 1.9 `cd dws-admin && pnpm install && pnpm build` succeeds on the empty-but-wired scaffold.

## 2. Postgres schema (Drizzle) (S2.2)

- [ ] 2.1 Add schema files under `dws-admin/src/store/schema/`: `workflow-definitions.ts`,
  `deployments.ts`, `workflow-instances.ts`, `task-events.ts`, `processed-events.ts`, plus an
  `index.ts` barrel re-exporting all tables (this is the `schema` object passed to
  `DrizzlePostgresModule`).
- [ ] 2.2 `workflow_definitions`: `name` (text), `version` (text), `status` (text), `created_at`
  (timestamptz); unique constraint on `(name, version)`.
- [ ] 2.3 `deployments`: `workflow` (text), `version` (text), `step_services` (jsonb),
  `orchestrator_app_id` (text), `status` (text), `drained_at` (timestamptz, nullable); unique
  constraint on `(workflow, version)`.
- [ ] 2.4 `workflow_instances`: `instance_id` (text, primary key), `workflow` (text), `version`
  (text), `app_id` (text), `status` (text), `started_at` (timestamptz, nullable), `ended_at`
  (timestamptz, nullable).
- [ ] 2.5 `task_events`: `id` (text, primary key — the CloudEvent id per design D5), `instance_id`
  (text, foreign key → `workflow_instances.instance_id`), `task_name` (text), `type` (text —
  payload `taskType`), `status` (text — lifecycle phase), `timestamp` (timestamptz), `error` (text,
  nullable).
- [ ] 2.6 `processed_events`: `event_id` (text, primary key), `processed_at` (timestamptz).
- [ ] 2.7 Add `drizzle.config.ts` pointing at the schema barrel and `dws-admin/drizzle/` as the
  migrations output directory; add `pnpm db:generate` (`drizzle-kit generate`) and `pnpm db:migrate`
  scripts.
- [ ] 2.8 Run `pnpm db:generate`, commit the resulting SQL under `dws-admin/drizzle/`.
- [ ] 2.9 Add a boot-time migration step in `src/main.ts` (gated by `RUN_MIGRATIONS_ON_BOOT`,
  default `true`) that runs `drizzle-orm`'s `migrate()` against the injected connection before
  `app.listen()`.
- [ ] 2.10 Verify `pnpm db:migrate` applies cleanly against a fresh `docker-compose` Postgres
  (manual check; automate as part of section 6's docker-compose task if convenient).

## 3. Shared event decoder and transaction helper (design D2/D4/D5)

- [ ] 3.1 Add `src/events/event-envelope.ts`: a typed decoder for the two-level envelope (Dapr
  handler payload → our `{id, source, type, time, datacontenttype, data}` → per-type `data`
  payload), with a type guard/parser and unit tests covering at least one payload example per
  event category (definition/deployment/instance/task) taken from `docs/events.md`.
- [ ] 3.2 Add `src/events/idempotent-handler.ts` (or similar): a helper that, given a Drizzle
  transaction, the injected `'DB'` client, and an event `id`, performs the
  `INSERT INTO processed_events ... ON CONFLICT (event_id) DO NOTHING RETURNING event_id` check and
  runs a supplied callback only if a row was returned, all inside one `db.transaction(...)` call.
- [ ] 3.3 Unit test the helper: calling it twice with the same event id runs the callback exactly
  once; a callback that throws leaves no `processed_events` row committed.
- [ ] 3.4 **Verification task (design D2 risk)**: after `pnpm install`, inspect the installed
  `@dbc-tech/nest-dapr` package's TypeScript types/source for the exact `@DaprPubSub` handler
  parameter shape; confirm or correct the decoder in 3.1 against the real shape (not just the
  package README), and update the design doc's note if the runtime shape differs.

## 4. Controller event subscriptions (S2.3)

- [ ] 4.1 Implement `ControllerEventsHandler` (`@Injectable`, in `DaprEventsModule`) with a single
  `@DaprPubSub(pubsubName, topic)` method (config values read per design D3) that decodes the
  envelope (task 3.1) and switches on `envelope.type` for `definition.created`, `definition.updated`,
  `deployment.applied`, `deployment.failed`, `deployment.drained`, `deployment.collected`; unknown
  types are logged and acked, not thrown.
- [ ] 4.2 Implement the `definition.created`/`definition.updated` upsert into `workflow_definitions`
  keyed on `(name, version)`, wrapped in the idempotency helper from 3.2.
- [ ] 4.3 Implement the `deployment.applied`/`failed`/`drained`/`collected` upsert into
  `deployments` keyed on `(workflow, version)`, storing `orchestratorAppId`/`stepServices` verbatim
  and applying the ranked-status `CASE` / `COALESCE(drained_at, ...)` merge rules from design D5.
- [ ] 4.4 Unit tests: idempotent replay (same event id processed twice → one row, second call makes
  no domain write); `deployment.failed` payload's `error` field lands in the row; unknown event type
  does not throw.

## 5. Orchestrator event subscriptions (S2.4)

- [ ] 5.1 Implement `OrchestratorEventsHandler` (`@Injectable`, in `DaprEventsModule`) with its own
  `@DaprPubSub(pubsubName, topic)` method switching on `envelope.type` for `instance.started`,
  `instance.completed`, `instance.failed`, `task.started`, `task.completed`, `task.failed`.
- [ ] 5.2 Implement the `instance.*` upsert into `workflow_instances` keyed on `instance_id`,
  applying design D5's `COALESCE`/ranked-status merge rules (`started_at` set-once, `ended_at`
  set-once, status never regresses from a terminal state).
- [ ] 5.3 Implement the `task.*` insert into `task_events`, primary-keyed on the CloudEvent `id`,
  storing `taskType` as `type` and the lifecycle phase as `status`; `ON CONFLICT (id) DO NOTHING`.
- [ ] 5.4 Unit tests: idempotent replay for `workflow_instances` (same event id twice → one row);
  **out-of-order case** — `instance.completed` processed before `instance.started` for the same
  `instance_id` creates a completed row, and a subsequent `instance.started` backfills `started_at`
  without reverting `status` away from "completed"; `task.failed` records `error`.

## 6. Health checks + local dev (S2.5)

- [ ] 6.1 Add `@nestjs/terminus`-based `GET /health` checking DB connectivity through the injected
  `'DB'` client (e.g. a raw `SELECT 1`).
- [ ] 6.2 Add `dws-admin/docker-compose.yml` starting a local Postgres matching the `DATABASE_URL`
  documented in the README/`.env.example`.
- [ ] 6.3 Add `dws-admin/.env.example` documenting all `ConfigModule` env vars from task 1.4.
- [ ] 6.4 Add a README section: `docker-compose up`, `pnpm start:dev`, and the `dapr run` command
  (app-id, `--app-port`, Dapr HTTP/gRPC ports) needed to run `dws-admin` locally under Dapr,
  mirroring the pattern already documented for `dws-orchestrator`.
- [ ] 6.5 Manually verify: `docker-compose up -d`, `pnpm db:migrate`, `dapr run ... -- pnpm
  start:dev`, then publish a sample event (e.g. `dapr publish` or a locally-running
  `dws-controller`/`dws-orchestrator`) and confirm a row lands in the read model and `GET /health`
  returns 200.

## 7. Acceptance verification

- [ ] 7.1 `cd dws-admin && pnpm build` and `pnpm test` are green.
- [ ] 7.2 Confirm `@knaadh/nestjs-drizzle-postgres` and `@dbc-tech/nest-dapr` are both direct
  dependencies in `dws-admin/package.json` (not vendored/reimplemented).
- [ ] 7.3 Confirm no HTTP route exists beyond `GET /health` (grep for `@Get`/`@Post`/etc. outside
  the health module).
- [ ] 7.4 Confirm `dws-admin/drizzle/` contains the only schema-affecting SQL in the repo for this
  component (no hand-written migration files).
- [ ] 7.5 `openspec verify dws-admin-skeleton` (or `/opsx:verify`) passes.
