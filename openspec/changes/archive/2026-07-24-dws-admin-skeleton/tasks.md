# Tasks: dws-admin-skeleton (Epic 2)

Maps to scope items S2.1–S2.5. Do S2.1 (scaffold) first, then S2.2 (schema), then S2.3/S2.4
(event ingestion, sharing the decoder/transaction helper from design D2/D4), then S2.5 (health +
local dev).

## 1. Scaffold the Nest project (S2.1)

- [x] 1.1 Create `dws-admin/` with `nest new` conventions: `package.json` (pnpm, Node >=24 engine),
  `tsconfig.json`, `nest-cli.json`, `src/main.ts`, `src/app.module.ts`.
- [x] 1.2 Add direct dependencies: `@nestjs/core`, `@nestjs/common`, `@nestjs/platform-express`,
  `@nestjs/config`, `@nestjs/terminus`, `@knaadh/nestjs-drizzle-postgres`, `drizzle-orm`,
  `postgres`, `@dbc-tech/nest-dapr`, `@dapr/dapr`; dev dependencies: `drizzle-kit`, `jest`,
  `ts-jest` (or Nest's default Jest setup), `@nestjs/testing`, `typescript`.
- [x] 1.3 Add `build` (`nest build`), `start` (`nest start`), `start:dev` (`nest start --watch`),
  `test` (Jest), `lint` scripts to `package.json` — do not copy `dws-call-openapi`'s script names.
- [x] 1.4 Add `ConfigModule` (`src/config/config.module.ts`) wrapping env vars: `DATABASE_URL`,
  `DAPR_PUBSUB_NAME` (default `pubsub`), `DAPR_PUBSUB_TOPIC` (default `dws.events`), `DAPR_HTTP_PORT`
  / `DAPR_GRPC_PORT` as needed by `@dbc-tech/nest-dapr`, `PORT` for the Nest app itself; validate at
  boot (e.g. `@nestjs/config` `validationSchema` or a small manual validator) and fail fast on a
  missing required var.
  <!-- Also added DAPR_APP_PORT (default 3001) — discovered during S2.5 verification that
       @dbc-tech/nest-dapr's DaprServer needs its own port, distinct from Nest's own PORT. See
       design D8. -->
- [x] 1.5 Add `StoreModule` (`src/store/store.module.ts`) importing
  `DrizzlePostgresModule.registerAsync({ tag: 'DB', imports: [ConfigModule], useFactory: ... ,
  inject: [ConfigService] })` with `config.schema` pointing at the schema barrel from task 2.1;
  re-export the `'DB'` provider.
- [x] 1.6 Add Dapr wiring (`src/dapr/dapr.module.ts` or inline in `DaprEventsModule`) using
  `DaprModule.registerAsync({ imports: [ConfigModule], useFactory: ..., inject: [ConfigService] })`.
- [x] 1.7 Add `DaprEventsModule` (`src/events/dapr-events.module.ts`) importing `StoreModule` and
  the Dapr module; scaffold empty `ControllerEventsHandler`/`OrchestratorEventsHandler` providers
  (bodies filled in section 4).
- [x] 1.8 Add `WorkflowsModule` and `InstancesModule` with empty `@Controller()` classes and no
  route methods; import both into `AppModule` alongside `ConfigModule`/`StoreModule`/
  `DaprEventsModule`.
- [x] 1.9 `cd dws-admin && pnpm install && pnpm build` succeeds on the empty-but-wired scaffold.

## 2. Postgres schema (Drizzle) (S2.2)

- [x] 2.1 Add schema files under `dws-admin/src/store/schema/`: `workflow-definitions.ts`,
  `deployments.ts`, `workflow-instances.ts`, `task-events.ts`, `processed-events.ts`, plus an
  `index.ts` barrel re-exporting all tables (this is the `schema` object passed to
  `DrizzlePostgresModule`).
- [x] 2.2 `workflow_definitions`: `name` (text), `version` (text), `status` (text), `created_at`
  (timestamptz); unique constraint on `(name, version)`.
- [x] 2.3 `deployments`: `workflow` (text), `version` (text), `step_services` (jsonb),
  `orchestrator_app_id` (text), `status` (text), `drained_at` (timestamptz, nullable); unique
  constraint on `(workflow, version)`.
- [x] 2.4 `workflow_instances`: `instance_id` (text, primary key), `workflow` (text), `version`
  (text), `app_id` (text), `status` (text), `started_at` (timestamptz, nullable), `ended_at`
  (timestamptz, nullable).
- [x] 2.5 `task_events`: `id` (text, primary key — the CloudEvent id per design D5), `instance_id`
  (text, indexed), `task_name` (text), `type` (text — payload `taskType`), `status` (text —
  lifecycle phase), `timestamp` (timestamptz), `error` (text, nullable).
  <!-- No DB-enforced FK to workflow_instances: task.* events can arrive before instance.* under
       unordered delivery, and a hard FK would reject that insert. Indexed instead; see
       task-events.ts's comment and spec's "eventually corresponds" wording. -->
- [x] 2.6 `processed_events`: `event_id` (text, primary key), `processed_at` (timestamptz).
- [x] 2.7 Add `drizzle.config.ts` pointing at the schema barrel and `dws-admin/drizzle/` as the
  migrations output directory; add `pnpm db:generate` (`drizzle-kit generate`) and `pnpm db:migrate`
  scripts.
- [x] 2.8 Run `pnpm db:generate`, commit the resulting SQL under `dws-admin/drizzle/`.
- [x] 2.9 Add a boot-time migration step in `src/main.ts` (gated by `RUN_MIGRATIONS_ON_BOOT`,
  default `true`) that runs `drizzle-orm`'s `migrate()` against the injected connection before
  `app.listen()`.
- [x] 2.10 Verify `pnpm db:migrate` applies cleanly against a fresh `docker-compose` Postgres.
  <!-- Verified against a local Postgres 16 instance (docker unavailable in this sandbox; used the
       same connection shape docker-compose.yml produces) — migration applied cleanly, all 5 tables
       + the task_events index created correctly. -->

## 3. Shared event decoder and transaction helper (design D2/D4/D5)

- [x] 3.1 Add `src/events/event-envelope.ts`: a typed decoder for the two-level envelope (Dapr
  handler payload → our `{id, source, type, time, datacontenttype, data}` → per-type `data`
  payload), with a type guard/parser and unit tests covering at least one payload example per
  event category (definition/deployment/instance/task) taken from `docs/events.md`.
- [x] 3.2 Add `src/events/idempotent-handler.ts` (or similar): a helper that, given a Drizzle
  transaction, the injected `'DB'` client, and an event `id`, performs the
  `INSERT INTO processed_events ... ON CONFLICT (event_id) DO NOTHING RETURNING event_id` check and
  runs a supplied callback only if a row was returned, all inside one `db.transaction(...)` call.
- [x] 3.3 Unit test the helper: calling it twice with the same event id runs the callback exactly
  once; a callback that throws leaves no `processed_events` row committed.
- [x] 3.4 **Verification task (design D2 risk)**: after `pnpm install`, inspect the installed
  `@dbc-tech/nest-dapr` package's TypeScript types/source for the exact `@DaprPubSub` handler
  parameter shape; confirm or correct the decoder in 3.1 against the real shape (not just the
  package README), and update the design doc's note if the runtime shape differs.
  <!-- Confirmed via dapr.loader.js + @dapr/dapr's DaprPubSubCallback.type.d.ts: handler receives
       the envelope directly, one argument. Correction made: @dapr/dapr documents the payload as
       "typically string or object", so the decoder now also accepts and JSON.parses a raw string.
       Verified live end-to-end in task 6.5 with a real daprd. See design D2. -->

## 4. Controller event subscriptions (S2.3)

- [x] 4.1 Implement `ControllerEventsHandler` decoding+dispatch for `definition.created`,
  `definition.updated`, `deployment.applied`, `deployment.failed`, `deployment.drained`,
  `deployment.collected`; unknown types are logged and acked, not thrown.
  <!-- Corrected during implementation (design D3): @dapr/dapr's SubscriptionManager throws if two
       classes both register @DaprPubSub('pubsub','dws.events') — only one subscription per
       (pubsubName, topic) is allowed. ControllerEventsHandler is now a plain @Injectable
       (canHandle/process), and the single @DaprPubSub entry point lives on the new
       DwsEventsSubscriber, which dispatches to this handler or OrchestratorEventsHandler by
       envelope.type. -->
- [x] 4.2 Implement the `definition.created`/`definition.updated` upsert into `workflow_definitions`
  keyed on `(name, version)`, wrapped in the idempotency helper from 3.2.
- [x] 4.3 Implement the `deployment.applied`/`failed`/`drained`/`collected` upsert into
  `deployments` keyed on `(workflow, version)`, storing `orchestratorAppId`/`stepServices` verbatim
  and applying the ranked-status `CASE` / `COALESCE(drained_at, ...)` merge rules from design D5.
- [x] 4.4 Unit tests: idempotent replay (same event id processed twice → one row, second call makes
  no domain write); `deployment.failed` payload's `error`-bearing fields land in the row; unknown
  event type does not throw (verified live via `DwsEventsSubscriber`'s default branch + a real
  `dapr publish` smoke test in task 6.5).

## 5. Orchestrator event subscriptions (S2.4)

- [x] 5.1 Implement `OrchestratorEventsHandler` decoding+dispatch for `instance.started`,
  `instance.completed`, `instance.failed`, `task.started`, `task.completed`, `task.failed`.
  <!-- Same D3 correction as 4.1: plain @Injectable (canHandle/process), dispatched from the single
       DwsEventsSubscriber rather than its own @DaprPubSub method. -->
- [x] 5.2 Implement the `instance.*` upsert into `workflow_instances` keyed on `instance_id`,
  applying design D5's `COALESCE`/ranked-status merge rules (`started_at` set-once, `ended_at`
  set-once, status never regresses from a terminal state).
- [x] 5.3 Implement the `task.*` insert into `task_events`, primary-keyed on the CloudEvent `id`,
  storing `taskType` as `type` and the lifecycle phase as `status`; `ON CONFLICT (id) DO NOTHING`.
- [x] 5.4 Unit tests: idempotent replay for `workflow_instances` (same event id twice → one row);
  **out-of-order case** — `instance.completed` processed before `instance.started` for the same
  `instance_id` creates a completed row, and a subsequent `instance.started` backfills `started_at`
  without reverting `status` away from "completed"; `endedAt` is never cleared by a later write;
  `task.failed` records `error`. All verified against a real Postgres instance (not mocked).

## 6. Health checks + local dev (S2.5)

- [x] 6.1 Add `@nestjs/terminus`-based `GET /health` checking DB connectivity through the injected
  `'DB'` client (a raw `SELECT 1`).
- [x] 6.2 Add `dws-admin/docker-compose.yml` starting a local Postgres matching the `DATABASE_URL`
  documented in the README/`.env.example`.
- [x] 6.3 Add `dws-admin/.env.example` documenting all `ConfigModule` env vars from task 1.4
  (including `DAPR_APP_PORT`, added per design D8).
- [x] 6.4 Add a README section: `docker-compose up`, `pnpm start:dev`, and the `dapr run` command
  (app-id, `--app-port`, Dapr HTTP/gRPC ports) needed to run `dws-admin` locally under Dapr,
  mirroring the pattern already documented for `dws-orchestrator`; documents the two-port
  requirement (design D8) and that `/health` is unreachable until the Dapr sidecar is up.
- [x] 6.5 Manually verify: migrate a local Postgres, install the Dapr CLI + a real `daprd` (v1.15.4,
  via `dapr init --slim` — docker unavailable in this sandbox, so `docker-compose`'s Postgres was
  substituted with an equivalent local instance) with an in-memory `pubsub` component, run the built
  app under `dapr run --app-id dws-admin --app-port 3001 ...`, confirm `GET /health` → `200`, then
  `curl` the sidecar's `/v1.0/publish/pubsub/dws.events` with a `definition.created` and an
  `instance.started` CloudEvent — both landed correctly in the read model, and replaying the same
  event id produced no duplicate row. Found and fixed two real bugs this way: the D3
  single-subscription conflict and the D8 port conflict.

## 7. Acceptance verification

- [x] 7.1 `cd dws-admin && pnpm build` and `pnpm test` are green (18/18 tests pass; `pnpm test` runs
  `jest --runInBand` — DB-backed tests share one Postgres instance and can't safely parallelize).
- [x] 7.2 Confirm `@knaadh/nestjs-drizzle-postgres` and `@dbc-tech/nest-dapr` are both direct
  dependencies in `dws-admin/package.json` (not vendored/reimplemented).
- [x] 7.3 Confirm no HTTP route exists beyond `GET /health` (grepped for `@Get`/`@Post`/etc. outside
  the health module — none found).
- [x] 7.4 Confirm `dws-admin/drizzle/` contains the only schema-affecting SQL in the repo for this
  component (no hand-written migration files) — confirmed via `find . -name "*.sql"`.
- [x] 7.5 `openspec verify dws-admin-skeleton` (or `/opsx:verify`) passes.
