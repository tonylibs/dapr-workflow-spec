# dws-admin

The read-model and eventing gateway for DWS. It subscribes to the shared lifecycle-event stream
([`docs/events.md`](../docs/events.md)) published by `dws-controller` and `dws-orchestrator`, and
turns it into a queryable Postgres read model — which definitions exist, which deployments
succeeded or failed, and which workflow instances/tasks are running.

- **Node 24**, **NestJS**, TypeScript
- **Drizzle ORM** against Postgres, wired via `@knaadh/nestjs-drizzle-postgres`
- **Dapr** pub/sub subscription served on Nest's own HTTP listener: `DaprSubscriptionController`
  implements Dapr's programmatic subscription contract (`GET /dapr/subscribe`, `POST
  /dapr/events/dws`) as a thin adapter over the domain `DwsEventsSubscriber` service
- **CloudEvents JS SDK** (`cloudevents`) to decode and validate each consumed message
- Package manager: **pnpm**

Alongside the paginated read endpoints, it pushes instance and task status changes to connected
clients over **server-sent events**, so `dws-console` can show a running instance live instead of
polling. Both SSE endpoints are ordinary `GET` routes on the same Nest app (`PORT`) as the read API
and the Dapr subscription routes — there is no second listener or port.

> **Single replica.** The push fan-out is in-process, so an event ingested by one replica reaches
> only the clients connected to *that* replica. Running more than one `dws-admin` needs a
> cross-replica bus (Postgres `LISTEN`/`NOTIFY`, or a dedicated pub/sub topic) first.

---

## Module layout

| Module | Responsibility |
|---|---|
| `ConfigModule` | `@nestjs/config`-wrapped env vars (DB URL, Dapr pub/sub name/topic, port). |
| `StoreModule` | `DrizzlePostgresModule.registerAsync` (tag `'DB'`); re-exports the typed Drizzle client. |
| `DaprEventsModule` | `DaprSubscriptionController` (Dapr's `GET /dapr/subscribe` discovery and `POST /dapr/events/dws` delivery routes, on Nest's own listener) plus the handlers that upsert events into the read model. The consumed message is decoded as a `CloudEvent` (`cloudevents` SDK) — spec conformance (`specversion`, `source`, `type`, RFC 3339 `time`) is validated by the SDK, and a payload that fails validation is logged and dropped rather than retried. |
| `WorkflowsModule` / `InstancesModule` | The read API. `InstancesModule` also serves the two SSE push endpoints (`GET /instances/:id/events`, `GET /instances/events`), which stream status changes as they are ingested. |
| `InstanceEventsModule` | In-process live-event bus (an RxJS `Subject`) between event ingestion and the SSE endpoints. Ingestion publishes to it *after* its transaction commits, so a rolled-back write is never pushed. Single-replica only — see the note below. |
| `HealthModule` | `GET /health` (`@nestjs/terminus`), checking DB connectivity. |

## Read model schema

Defined in Drizzle under `src/store/schema/`: `workflow_definitions`, `deployments`,
`workflow_instances`, `task_events`, `processed_events` (idempotency guard). Schema changes ship
only as `drizzle-kit generate` output checked into [`drizzle/`](drizzle/) — never hand-edited SQL.

```bash
pnpm db:generate   # after changing a schema file, generates a new migration
pnpm db:migrate     # applies pending migrations (also runs on boot by default)
```

---

## Local development

### 1. Start Postgres

```bash
docker-compose up -d
```

### 2. Configure environment

```bash
cp .env.example .env
```

### 3. Install, migrate, run

```bash
pnpm install
pnpm db:migrate
pnpm start:dev
```

### 4. Run under a Dapr sidecar

Needs the same pub/sub `Component` (`pubsub`, topic `dws.events`) that `dws-controller`/
`dws-orchestrator` already require (see `docs/events.md`'s deployment prerequisite).

Dapr's programmatic subscription discovery (`GET /dapr/subscribe`) and pub/sub delivery
(`POST /dapr/events/dws`) are served on Nest's own HTTP listener (`PORT`, `3000`) alongside the
read API and SSE endpoints — there is **no second application port**. The sidecar's `--app-port`
must target `PORT`:

```bash
dapr run --app-id dws-admin --app-port 3000 \
  --dapr-http-port 3501 --dapr-grpc-port 50002 \
  --resources-path ~/.dapr/components \
  -- pnpm start:dev
```

`--dapr-http-port`/`--dapr-grpc-port` are shifted from `dws-orchestrator`'s defaults
(`3500`/`50001`) so both can run side by side against the same local `dapr init` control plane.

Once running, `GET http://localhost:3000/health` should return `200` when Postgres is reachable
(hit directly, not through the sidecar — health checks aren't part of the Dapr-mediated traffic),
and events published by a locally-running `dws-controller`/`dws-orchestrator` should appear as
rows in the read model.

### 5. Calling the API from a browser

This service sends no browser CORS headers. The public path is **same-origin**: a shared Gateway
routes `/dws-admin/*` to this service (through Dapr's bearer-gated service-invocation path) and
everything else to `dws-console`, so a browser page served from that Gateway never makes a
cross-origin request here.

For local development, `dws-console`'s dev server proxies `/dws-admin` requests to this API
(see `dws-console`'s Vite config) instead of relying on CORS — run both services locally and use
the console's dev-server URL, not this service's port, from a browser.

---

## Build & test

Event-ingestion tests exercise the real upsert SQL (`ON CONFLICT ... DO UPDATE` merge rules) against
a live Postgres rather than a mock, so `pnpm test` needs one reachable — `docker-compose up -d` +
`pnpm db:migrate` first, or point `TEST_DATABASE_URL` at another instance with the migrations
applied. Tests run with `--runInBand`: they share one database and clean up via `TRUNCATE` between
cases, which isn't safe to parallelize across test files.

```bash
pnpm build
pnpm test
pnpm lint
```

CI (`.github/workflows/dws-admin.yml`) gates on `pnpm lint && pnpm test && pnpm build` against a
Postgres service container, and publishes `ghcr.io/tonylibs/dws-admin` on merges to `main` (PRs
build the image to validate the `Dockerfile` but don't push), matching the other components'
convention.

### Container image

```bash
docker build -t ghcr.io/tonylibs/dws-admin:latest dws-admin
```

The image runs `node dist/main.js`, which applies pending migrations on boot by default (see
`RUN_MIGRATIONS_ON_BOOT` above) before listening.
