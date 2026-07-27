## Context

Epic 2 (`openspec/changes/archive/2026-07-24-dws-admin-skeleton`) built `dws-admin` as a NestJS
service that ingests `dws.events` into a Drizzle/Postgres read model and exposes only `/health`.
Five tables exist (`workflow_definitions`, `deployments`, `workflow_instances`, `task_events`,
`processed_events`); `WorkflowsModule` and `InstancesModule` import `StoreModule` and register empty
`@Controller('workflows')` / `@Controller('instances')` classes with no routes (Epic 2 D1).

This change fills those controllers with read-only endpoints. Constraints inherited from Epic 2 and
the schema:

- **No persistence changes.** The schema is fixed; endpoints are pure queries over existing columns.
- **Read model is eventually consistent and out-of-order tolerant.** Rows can be partially
  populated (`instance.completed` may land before `instance.started`), and `started_at`/`ended_at`
  are nullable. Response DTOs must model nullability honestly.
- **`workflow_definitions` has no `id`** — its identity is `(name, version)` with a unique
  constraint; `deployments` identity is `(workflow, version)`. `workflow_instances` is keyed on
  `instance_id`. `task_events` is append-only, keyed on the CloudEvent `id`, indexed on
  `instance_id`.
- **Tooling**: injected `'DB'` client (`@Inject(DB)`, typed `Db`), Drizzle query builder, Jest
  (`*.spec.ts`, `--runInBand`) integration tests against a real Postgres (`docker-compose up -d` +
  `pnpm db:migrate`), per the `test-support/test-db.ts` convention. `@nestjs/swagger` is not yet a
  dependency.

A downstream consumer — `dws-console` (planned React/TanStack operator UI, not yet started) — will
generate a typed client from the OpenAPI document this change produces, so the DTO contract is the
real deliverable, not just the routes.

## Goals / Non-Goals

**Goals:**
- Real, read-only REST endpoints on `WorkflowsModule` and `InstancesModule` over the existing schema.
- A validated request/response DTO per endpoint, with a complete `@nestjs/swagger` OpenAPI document.
- Pagination on every list endpoint, with one consistent scheme across all three.
- Integration tests against real Postgres following the existing `test-db.ts` convention.
- `pnpm lint && pnpm test && pnpm build` green.

**Non-Goals:**
- Any write/mutating endpoint (retry, cancel) — Epic 4+.
- The `commands` audit-log table — Epic 5.
- Auth, multi-tenancy, rate limiting.
- Schema changes, new indexes, or new migrations.
- `dws-console` itself, or shipping a generated client.

## Decisions

### D1: Keyset (cursor) pagination, uniform across all three list endpoints
All list endpoints (`GET /workflows`, `GET /instances`, `GET /workflows/:name/deployments` is small
and single-workflow but paginated for contract uniformity) use **keyset/cursor** pagination, not
offset/limit. Request: `?limit=<n>&cursor=<opaque>`. Response envelope:
`{ items: T[], nextCursor: string | null }`. The cursor is an opaque, base64url-encoded token
encoding the last row's sort key; `nextCursor: null` means the last page.

- **Why keyset over offset**: `workflow_instances` grows unbounded and is the hot list; `OFFSET n`
  makes Postgres scan and discard `n` rows, so deep pages degrade linearly and, worse, rows
  *shifting* under a live-ingesting read model make offset pages skip or duplicate items. Keyset
  paginates on a stable, indexed sort key (`instance_id` PK for instances, `(name, version)` for
  definition history, `id` for task events), giving stable pages and flat performance regardless of
  depth. An opaque cursor also keeps the pagination mechanism out of the public contract, so
  `dws-console`'s generated client treats it as a token, not a number to arithmetic on.
- **Alternative — offset/limit**: rejected for the reasons above; its only advantage (jump to page
  N, total count) is not a requirement for an operator list view and is unsafe on a live stream.
- **Cost**: no "total count" and no random page access. Accepted — neither is needed here. Where a
  cheap count is trivially useful (e.g. `GET /workflows` over the small definitions table) it can be
  added later without breaking the cursor contract.
- Sort keys: `/workflows` by `name` asc; `/workflows/:name` history by `version` (or `created_at`
  desc, tie-broken by `version`) so newest-first; `/workflows/:name/deployments` by `version`;
  `/instances` by `(started_at desc nulls last, instance_id)` so most-recent-first with a stable
  tiebreak; `/instances/:id/tasks` ordered by `(timestamp, id)` ascending (task_events is not a
  list endpoint's cursor page in the same sense — see D4).

### D2: Thin service-per-module querying Drizzle directly; no repository abstraction
Each module gets a `WorkflowsService` / `InstancesService` (`@Injectable()`, `@Inject(DB) db: Db`)
holding the Drizzle queries; the controller maps HTTP → service call → DTO. No generic repository
layer — the queries are simple selects and Epic 2 established direct `db` use in the event handlers.
- **Alternative — query in the controller**: rejected; keeps HTTP concerns and SQL entangled and
  makes the query logic untestable without the HTTP layer.

### D3: "Latest version per name" for `GET /workflows` via `DISTINCT ON`
`workflow_definitions` stores one row per `(name, version)`. The list view wants one row per name
showing the latest version and its status. Use Postgres `DISTINCT ON (name) ... ORDER BY name,
created_at DESC` (Drizzle raw/`sql` fragment) to pick the newest row per name in a single query,
then keyset-paginate by `name`. `GET /workflows/:name` returns the full unfolded history (all
versions) for the one name.
- **Alternative — group + subquery for max version**: `version` is a content-addressed
  `<name>@v<sha8>` string, not monotonically ordered, so "latest" must be defined by `created_at`,
  which `DISTINCT ON ... ORDER BY created_at DESC` expresses directly and cheaply.

### D4: DTOs with `@nestjs/swagger` decorators; request validation via `ValidationPipe`
Response DTOs are plain classes annotated with `@ApiProperty({ nullable: ... })` mirroring column
nullability (`startedAt`/`endedAt`/`drainedAt`/`error` nullable). Query DTOs (`limit`, `cursor`,
`workflow`, `status` filters) are classes validated by a global `ValidationPipe`
(`whitelist: true, transform: true`) using `class-validator` decorators (`@IsOptional`, `@IsInt`,
`@Min`/`@Max` on `limit`, `@IsString` on `cursor`/filters). Path params (`:name`, `:id`) validated
inline. `SwaggerModule.setup('docs', ...)` in `main.ts` serves the OpenAPI JSON/UI.
- `limit` bounded (default e.g. 20, max e.g. 100) so a client can't request an unbounded page.
- **Alternative — return raw Drizzle row types**: rejected; couples the public contract to the DB
  schema and gives Swagger nothing to document. Explicit DTOs are the deliverable `dws-console`
  generates from.
- **Alternative — interface-only DTOs (no classes)**: rejected; `@nestjs/swagger` needs runtime
  class metadata to emit schema, and `class-validator` needs decoratable classes.

### D5: Unknown/empty results are `200` with an empty page; unknown `:name`/`:id` is `404`
A list endpoint with no matching rows returns `200 { items: [], nextCursor: null }`. A detail
endpoint (`/workflows/:name`, `/instances/:id`) for a name/id with zero rows returns `404`.
`/workflows/:name/deployments` and `/instances/:id/tasks` for a non-existent parent return `404`
(parent-not-found) rather than an empty `200`, so a client can distinguish "no such workflow" from
"workflow exists, no deployments yet."
- **Alternative — always `200` empty**: rejected; hides the not-found case the operator UI needs to
  render differently.

## Risks / Trade-offs

- **[Keyset cursor encodes a composite sort key (e.g. `started_at`+`instance_id`) and must survive
  nulls]** → `started_at` is nullable, so the `nulls last` ordering and cursor comparison must
  handle a null anchor. Mitigation: encode a discriminator for the null case in the cursor and use
  an explicit `(started_at, instance_id)` tuple comparison with `NULLS LAST`; cover with an
  integration test that pages across the null/non-null boundary.
- **[Partially-populated rows]** → a client sees `startedAt: null` on a running-but-unacked
  instance. Mitigation: DTO models every lifecycle-derived field as nullable; documented in
  `@ApiProperty`. This is inherent to the eventually-consistent read model, not a bug.
- **[Opaque cursor is not authenticated/signed]** → a client could hand-craft a cursor. Mitigation:
  the cursor only selects an ordering anchor over read-only data; a malformed cursor yields `400`
  (validation), a well-formed one selects a valid page — no data exposure risk since there's no auth
  boundary in scope anyway.
- **[New deps `@nestjs/swagger` + `class-validator`/`class-transformer`]** → dependency surface
  growth. Mitigation: all first-party/standard Nest ecosystem packages already implied by Nest 11;
  no transitive-risk libraries introduced.

## Migration Plan

1. Add `@nestjs/swagger`, `class-validator`, `class-transformer`; wire global `ValidationPipe` +
   `SwaggerModule` in `main.ts`. Verify `pnpm build` and `/docs` serves an (empty) OpenAPI doc.
2. Shared pagination helper (cursor encode/decode, page envelope DTO) with unit `.spec.ts`.
3. `WorkflowsService` + DTOs + `WorkflowsController` (three endpoints); integration `.spec.ts`
   against real Postgres seeded via the existing test-db helper.
4. `InstancesService` + DTOs + `InstancesController` (three endpoints); integration `.spec.ts`,
   including the started_at-null paging boundary.
5. Full gate: `pnpm lint && pnpm test && pnpm build` green.
- **Rollback**: additive and read-only; reverting removes routes only — no schema/data/consumer
  impact (`dws-console` doesn't exist yet).

## Open Questions

- Default and max `limit` values — pick sensible defaults (e.g. 20 / 100) at implementation time;
  not a contract-breaking choice since they can widen later.
- Whether `GET /workflows` should also surface a per-name instance/deployment count — deferred; not
  in the requested scope, addable without breaking the cursor contract.
