# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-call-asyncapi-specific idioms only.

## Commands

```shell
cd dws-call-asyncapi
pnpm install
pnpm lint       # eslint .
pnpm test       # vitest run
pnpm test:watch
pnpm build      # tsc -p tsconfig.json
pnpm dev        # node --watch --experimental-strip-types src/index.ts
```

CI gate: `pnpm lint && pnpm test && pnpm build`.

## Style guide

Same Fastify skeleton as `dws-call-openapi` — `app.ts`, `routes.ts`, `plugins/{config,runner}.ts`,
`http.ts` are effectively identical between the two packages, same ESLint config, same test harness.
See `dws-call-openapi/CLAUDE.md` for the full detail on loops, DI-via-plugin, error classification,
named-function-over-arrow style, and Vitest/`MockAgent` testing — all apply here unchanged. This file
covers only where this package genuinely differs.

### No `AuthConfig`/`SecretRef` — this package has no authentication concept

`dws-call-openapi` has `auth.ts`, `secrets.ts`, `AuthConfig`/`SecretRef` types, `AUTH_*` env vars.
This package has none of that — don't port auth types over speculatively; add them only when a real
AsyncAPI-broker auth requirement shows up.

### Payload validation: no explicit shape guard before ajv, unlike `dws-call-openapi`

```typescript
// jq.ts — asyncapi's evaluatePayload has no equivalent of openapi's guard below,
// it defers entirely to the ajv schema check that follows
const result = jq(expression, data);
return result; // no null/object/array check here
```

`dws-call-openapi`'s `evaluateParameters` (`jq.ts:37-39`) explicitly rejects a non-object jq result
before use. This package's `evaluatePayload` doesn't, because the payload schema validator (ajv)
catches a malformed shape anyway. This is an accepted asymmetry, not a bug to backport — don't add
the extra guard here unless you find a case ajv doesn't actually catch.

### Error message contract: 400s are prefixed `validation failed:` for downstream classification

```typescript
// runner.ts:69 — the prefix is load-bearing, dws-orchestrator's WorkflowErrors parses it
throw new BindingError('validation failed: message payload failed schema validation');
```

`dws-call-openapi`'s equivalent `BindingError` message (`'request failed schema validation'`) has no
such prefix. The asymmetry is deliberate per the comment at `runner.ts:14-19` — don't strip the
prefix here or add one to `dws-call-openapi` without checking what `dws-orchestrator`'s
`WorkflowErrors.classify` actually keys off of first.

### Env var naming — `DOC_ENDPOINT`/`DOC_SHA256`, not `DOCUMENT_URL`/`DOCUMENT_SHA256`

This is this package's actual name for the document location/hash setting. `dws-call-openapi` uses
`DOCUMENT_URL`/`DOCUMENT_SHA256` for the same concept — the two have drifted; don't assume either
name transfers to the other package.
