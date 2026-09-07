# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-call-openapi-specific idioms only.

## Commands

```shell
cd dws-call-openapi
pnpm install
pnpm lint             # eslint .
pnpm test             # vitest run
pnpm test:watch
pnpm build             # tsc -p tsconfig.json
pnpm dev                # node --watch --experimental-strip-types src/index.ts
pnpm vitest run test/auth.test.ts   # single test file
```

CI gate: `pnpm lint && pnpm test && pnpm build`.

## Style guide

Nearly identical to `dws-call-asyncapi` (same Fastify skeleton — see that package's `CLAUDE.md` for
what's shared). This file covers what's specific to the OpenAPI package, plus the shared pattern in
full for whichever file you open first.

### Loops/collections: `for...of` and `.map`/`.filter`, never a classic `for`

```typescript
// runner.ts:97
for (const [name, value] of Object.entries(evaluated)) {
    // ...
}

// validator.ts:130
const messages = errors.map((error) => error.message);
```

### Null/undefined: explicit `=== undefined` for domain values, `?.`/`??` for optional/nested access

```typescript
// config.ts:88-93 — explicit check on a value that's meaningfully "present or not"
const value = nonEmpty(env.API_KEY);
if (value === undefined) {
    throw new ConfigError('API_KEY is required');
}

// engine.ts:66,69,72 — ?./?? for optional nested structure
const baseUrl = server?.url;
const scheme = auth.scheme?.name ?? DEFAULT_API_KEY_HEADER;
```

No Result/Either pattern — errors are thrown, not returned as values.

### DI: Fastify plugin registration via `fastify-plugin`, explicit `dependencies` array for load order

```typescript
// plugins/openapi.ts:34
export default fp(openapiPlugin, { name: 'openapi', dependencies: ['config'] });

// plugins/runner.ts:20
export default fp(runnerPlugin, { name: 'runner', dependencies: ['openapi'] });
```

Type-augment `declare module 'fastify'` for anything a plugin decorates onto the instance — don't
smuggle shared state through module-level variables.

### DTOs: plain `interface`/discriminated-union `type`, no classes, no zod

```typescript
// config.ts:12-25
type SecretRef = { source: 'env'; name: string } | { source: 'file'; path: string };

interface AuthConfig {
    scheme: 'apiKey' | 'bearer' | 'basic';
    ref: SecretRef;
}
```

No DTO-vs-domain-model split exists — `Config` and the OpenAPI-derived `OperationTemplate`/`Binding`
types are the only shapes, used directly through the request pipeline.

### Errors: custom `Error` subclasses per failure category, caught centrally by `instanceof`

```typescript
// runner.ts:17-48
class BindingError extends Error {}                                   // -> 400
class UpstreamError extends Error {                                   // -> 502
    constructor(public status: number, public body: string) { super(`upstream ${status}`); }
}
class TransportError extends Error {}                                 // -> 502

// routes.ts:34-56 — sequential instanceof checks, fall through to 500
function handleRunError(err: unknown, reply: FastifyReply) {
    if (err instanceof BindingError) return reply.code(400).send({ error: err.message });
    if (err instanceof UpstreamError) return reply.code(502).send({ error: err.message });
    if (err instanceof TransportError) return reply.code(502).send({ error: err.message });
    reply.log.error(err);
    return reply.code(500).send({ error: 'internal error' });
}
```

`ConfigError` (`config.ts:51-56`) is separate and never HTTP-mapped — it's a startup-time failure
that crashes the process, not a request-time one.

### Functions: named `function` declarations, arrow functions only for short inline callbacks

```typescript
// routes.ts:34 — named function for anything with real logic
function handleRunError(err: unknown, reply: FastifyReply) { /* ... */ }

// inline callback — arrow is fine here, it's a one-liner
const paths = [...url.matchAll(pattern)].map((match) => match[1]);
```

### Tests: Vitest, top-level `test/` dir (not colocated), undici `MockAgent`, Fastify `.inject()`

```typescript
// test/run.test.ts — pattern
import { describe, it, expect } from 'vitest';
import { MockAgent, setGlobalDispatcher } from 'undici';

describe('POST /run', () => {
    it('returns 502 on upstream failure', async () => {
        const agent = new MockAgent();
        setGlobalDispatcher(agent);
        const app = await buildApp({ env, logger: false });
        const res = await app.inject({ method: 'POST', url: '/run', payload: {} });
        expect(res.statusCode).toBe(502);
    });
});
```

Don't stand up a real listening port for HTTP-level tests — use `.inject()`.

### ESLint (`eslint.config.mjs`, full — identical between openapi and asyncapi)

```javascript
tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  { rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/consistent-type-imports': 'error',
      'no-console': 'error',
  }},
  { files: ['test/**/*.ts'], rules: { '@typescript-eslint/no-explicit-any': 'off' } },
);
```

`any` is banned in `src/`, allowed in `test/`. No prettier config exists — don't add one without
discussing it, since `dws-admin` already has an unused `eslint-config-prettier` sitting dead.

### Env var naming — don't copy from `dws-call-asyncapi` assuming it matches

This package uses `DOCUMENT_URL`/`DOCUMENT_SHA256` for the OpenAPI document location/hash.
`dws-call-asyncapi` uses `DOC_ENDPOINT`/`DOC_SHA256` for the equivalent AsyncAPI concept — the two
have drifted, don't assume either name transfers to the other package.
