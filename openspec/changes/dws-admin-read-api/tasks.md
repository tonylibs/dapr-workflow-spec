## 1. Dependencies & app wiring

- [ ] 1.1 Add `@nestjs/swagger`, `class-validator`, `class-transformer` to `dws-admin/package.json`; `pnpm install`
- [ ] 1.2 In `src/main.ts` register a global `ValidationPipe` (`whitelist: true`, `transform: true`) and wire `SwaggerModule.setup('docs', ...)` with a `DocumentBuilder` titled for the read API
- [ ] 1.3 Verify `pnpm build` passes and `/docs` serves an OpenAPI document (empty is fine at this stage)

## 2. Shared pagination

- [ ] 2.1 Add a page-envelope response DTO `{ items, nextCursor }` and a base query DTO (`limit` bounded with default/max, `cursor` optional string) with `class-validator` + `@ApiProperty` decorators
- [ ] 2.2 Add cursor encode/decode helper (base64url of the sort-key anchor), tolerant of a null anchor component
- [ ] 2.3 Unit spec (`*.spec.ts`) for cursor round-trip encode/decode including the null-anchor case

## 3. Workflows endpoints

- [ ] 3.1 Add `WorkflowsService` (`@Inject(DB)`) with queries: latest-per-name via `DISTINCT ON (name) ORDER BY name, created_at DESC` (keyset by `name`); full version history for one name (newest first); deployments for one name
- [ ] 3.2 Add response DTOs (workflow summary, workflow version, deployment) and register routes on `WorkflowsController`: `GET /workflows`, `GET /workflows/:name`, `GET /workflows/:name/deployments`
- [ ] 3.3 Return `404` for unknown `:name` on `/workflows/:name` and `/workflows/:name/deployments`; `200` empty page when the list has no rows
- [ ] 3.4 Integration spec against real Postgres (seed via `test-support/test-db.ts`): latest-per-name selection, history ordering, deployments, and 404 cases

## 4. Instances endpoints

- [ ] 4.1 Add `InstancesService` (`@Inject(DB)`) with queries: list with optional `workflow` + `status` filters, keyset-ordered by `(started_at desc nulls last, instance_id)`; single instance by id; task events by instance id ordered `(timestamp, id)` asc
- [ ] 4.2 Add response DTOs (instance summary, instance detail with nullable `startedAt`/`endedAt`, task event) and query DTO (filters + pagination); register routes on `InstancesController`: `GET /instances`, `GET /instances/:id`, `GET /instances/:id/tasks`
- [ ] 4.3 Return `404` for unknown `:id` on `/instances/:id` and `/instances/:id/tasks`; `400` on out-of-range `limit`
- [ ] 4.4 Integration spec against real Postgres: filter combinations, detail, ordered task events, 404 cases, and paging across the `started_at` null/non-null boundary

## 5. Contract & gate

- [ ] 5.1 Confirm the OpenAPI document covers all six endpoints with their DTO schemas and nullable fields marked
- [ ] 5.2 Run `pnpm lint && pnpm test && pnpm build` in `dws-admin/` (with `docker-compose up -d` + `pnpm db:migrate` first) and confirm green
