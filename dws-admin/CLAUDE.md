# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-admin-specific idioms only.

## Commands

```shell
cd dws-admin
pnpm install
pnpm lint            # eslint "{src,test}/**/*.ts"
pnpm test            # jest --runInBand
pnpm test:watch
pnpm test:cov
pnpm build            # nest build
pnpm start:dev         # nest start --watch
pnpm db:generate        # drizzle-kit generate
pnpm db:migrate          # tsx src/store/migrate.ts
```

CI gate: `pnpm lint && pnpm test && pnpm build`. Integration tests run against a real Postgres —
`--runInBand` because tests share one database and `TRUNCATE` between cases, so don't parallelize
the test run.

## Style guide

### DI/module structure: standard NestJS, constructor-parameter injection, `@Inject(DB)` token for Drizzle

```typescript
// workflows.service.ts:9-11
@Injectable()
export class WorkflowsService {
    constructor(@Inject(DB) private readonly db: Db) {}
}
```

No `inject()` function-style DI anywhere — always the decorator + constructor-parameter form.

### DTOs are separate classes with `class-validator` + `@ApiProperty`, never the Drizzle inferred type directly

```typescript
// store/schema/workflow-instances.ts:13-14 — the Drizzle domain type
export type WorkflowInstance = typeof workflowInstances.$inferSelect;

// dto/instance.dto.ts:23-41 — hand-written response DTO, decorated separately
export class InstanceSummaryDto {
    @ApiProperty()
    name: string;

    @ApiProperty()
    status: string;
}

// common/pagination-query.dto.ts — request DTO, validated by the global ValidationPipe
export class PaginationQueryDto {
    @IsInt()
    @Min(1)
    @Max(100)
    @Type(() => Number)
    limit = 20;
}
```

Enforced globally: `main.ts:25` sets `ValidationPipe({ whitelist: true, transform: true })`. Never
return a `$inferSelect` type straight from a controller — map it into a `*Dto` class first, even if
the fields look identical today.

### Null/undefined: `??`/`?.` in query/service code, explicit checks in validation-heavy files, `NotFoundException` on a falsy lookup

```typescript
// instances.controller.ts:56-60 — truthy check + Nest exception, not === null
if (!instance) {
    throw new NotFoundException(`instance ${id} not found`);
}

// workflows.service.ts:21 — explicit undefined check where that's the real question
return row !== undefined;

// event-envelope.ts — validation file, explicit typeof checks, no ??/?. at all
if (typeof x !== 'string' || x.length === 0) {
    throw new InvalidEventEnvelopeError('name must be a non-empty string');
}
```

Which style to use depends on the file's job: validation code checks types explicitly;
query/service code chains `??`/`?.` for optional data.

### Errors: Nest's built-in `HttpException` subclasses, thrown from anywhere — no global filter, no custom response shape

```typescript
// instances.controller.ts:57-58
throw new NotFoundException(`instance ${id} not found`);

// common/pagination.ts:30,33 — thrown from a plain utility function, not just controllers
export function decodeCursor(cursor: string): unknown[] {
    if (!isValidCursor(cursor)) {
        throw new BadRequestException('invalid cursor');
    }
    // ...
}
```

Nest turns any thrown `HttpException` into the right response automatically — there's no
`ExceptionFilter` registered globally, and no custom error envelope. Throwing an `HttpException` from
a plain (non-controller, non-service) utility function is an accepted pattern here — it still works
because Nest catches by type, not by call-site.

Domain errors that never reach HTTP are plain `Error` subclasses, not `HttpException`s:

```typescript
// events/event-envelope.ts:22-26
export class InvalidEventEnvelopeError extends Error {}
```

`InvalidEventEnvelopeError` is caught and logged by the Dapr event subscriber
(`events/dws-events.subscriber.ts`) — it never becomes an HTTP response, so don't make it an
`HttpException`.

### Loops: array methods dominant, but a plain `for` is fine in test fixture setup

```typescript
// instances.controller.ts — RxJS map, same idiom preference as Array.map
source$.pipe(map((change) => ({ id: change.id, status: change.status })));

// definition-validation.service.ts:71
for (const [name, def] of Object.entries(schema.properties)) {
    // ...
}
```

The one classic `for (let i = 0; i < n; i++)` loop in this package seeds 10 fixture rows in
`instances.integration.spec.ts:56` — that's fine for test setup, don't treat it as license to use
index-based loops in `src/`.

### Tests: Jest (not Vitest), colocated `*.spec.ts`, global `describe`/`it`/`expect` (no import)

```typescript
// common/pagination.spec.ts — no test-framework import, Jest globals only
import { BadRequestException } from '@nestjs/common';
import { decodeCursor } from './pagination';

describe('decodeCursor', () => {
    it('throws on an invalid cursor', () => {
        expect(() => decodeCursor('garbage')).toThrow(BadRequestException);
    });
});
```

Test files sit next to the file they test (`instances.controller.spec.ts` beside
`instances.controller.ts`), not in a separate `test/` directory — this differs from the Fastify
packages (`dws-call-openapi`/`dws-call-asyncapi`), don't copy their `test/*.test.ts` layout here.

### ESLint — intentionally looser than the Fastify packages, leave it that way

```javascript
// eslint.config.mjs — full file
tseslint.config(
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  eslintConfigPrettier,
  { languageOptions: { sourceType: 'commonjs' },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    }},
  { ignores: ['dist/**', 'coverage/**', 'node_modules/**'] },
);
```

`any` is allowed, unused vars only warn — this was a deliberate choice (confirmed), not an oversight
to fix by copying `dws-call-openapi`'s stricter config. Don't add `no-console`/`consistent-type-imports`
rules here to "match" the Fastify packages.
