## Why

Epic 2 stood up `dws-admin` and now populates a Postgres read model from the `dws.events` stream,
but nothing reads it back out: `WorkflowsModule` and `InstancesModule` ship empty `@Controller()`
scaffolds with no routes (deferred to Epic 3 per that design's D1). Operators still have no HTTP
surface to see what workflow definitions exist, which deployments succeeded, or how instances and
their tasks are progressing — the data is in Postgres but unreachable. The planned `dws-console`
operator UI also needs a typed, documented contract to generate a client from before it can start.

## What Changes

- Add read-only REST endpoints to `WorkflowsModule` and `InstancesModule` over the existing read
  model — no new tables, no schema changes, no mutating endpoints, no auth.
- `GET /workflows` — list workflow definitions, one row per name (latest version + status).
- `GET /workflows/:name` — full version history for one workflow definition.
- `GET /workflows/:name/deployments` — deployments for a workflow.
- `GET /instances` — list workflow instances, filterable by workflow name and status.
- `GET /instances/:id` — one instance's detail.
- `GET /instances/:id/tasks` — that instance's `task_events`, ordered.
- Pagination on all three list endpoints (cursor vs. offset decided in `design.md`).
- Validated request/response DTOs on every endpoint, surfaced as an OpenAPI document via
  `@nestjs/swagger` so `dws-console` can generate a typed client from the contract.
- Add `@nestjs/swagger` (and, if needed, `class-validator`/`class-transformer`) as dependencies;
  wire `SwaggerModule` in `main.ts`.

## Capabilities

### New Capabilities
- `admin-read-api`: `dws-admin`'s read-only REST surface over the read model — the workflow and
  instance list/detail endpoints, their query filters and pagination, the request/response DTO
  contract, and the generated OpenAPI document.

### Modified Capabilities
<!-- None. The read-model schema (admin-read-model-schema) and event ingestion
     (admin-event-ingestion) are consumed unchanged; no requirement of either is modified. -->

## Impact

- **Component**: `dws-admin/` only — `src/workflows/` and `src/instances/` (controllers, services,
  DTOs), `src/main.ts` (Swagger wiring). No changes to schema, migrations, or event ingestion.
- **New dependencies**: `@nestjs/swagger` (+ `class-validator`/`class-transformer` if the DTO
  validation path needs them).
- **No new infrastructure**: reads the existing tables through the already-wired `'DB'` client; no
  new Postgres objects, no new Dapr wiring, no cluster-level prerequisites.
- **Non-goals** (carried from Epic 2): any write/mutating endpoint (retry, cancel), the `commands`
  audit-log table (Epic 5), auth / multi-tenancy / rate limiting, and `dws-console` itself.
- **CI**: the existing `dws-admin` gate (`pnpm lint && pnpm test && pnpm build`) covers this change;
  no CI workflow changes.
