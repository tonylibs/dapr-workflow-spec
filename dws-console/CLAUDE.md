# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-console-specific idioms only.

`AGENTS.md` in this directory is TanStack Intent skill boilerplate, not real conventions — this file
is the ground truth for this package's style.

## Commands

```shell
cd dws-console
pnpm dev              # vite dev --port 3000
pnpm generate-routes   # tsr generate
pnpm check              # biome check (lint + format)
pnpm typecheck            # tsc --noEmit
pnpm test                  # vitest run
pnpm test:watch
pnpm build                   # vite build
```

CI-equivalent gate: `pnpm check && pnpm typecheck && pnpm test && pnpm build`.

## Style guide

### Components: plain `function` declarations, named exports, no `React.FC`

```tsx
// routes/instances/index.tsx:64
function InstanceList() { /* ... */ }

// components/states.tsx:9,34
export function EmptyState({ message }: { message: string }) { /* ... */ }
export function Banner({ children, ...rest }: { children: ReactNode } & Omit<ComponentPropsWithoutRef<'div'>, 'children'>) { /* ... */ }
```

Props are typed inline right above/in the function signature, not via `React.FC<Props>`. Route
files export a `Route` const from `createFileRoute` plus the page component, never a default export.

### Loops: `.map()` with a stable domain-id key, never array index

```tsx
// routes/workflows/$name.tsx:247
{d.deployments.map((dep) => (
  <div key={dep.version}>{dep.version}</div>
))}

// components/definition-editor.tsx:259 — composite string key when no single field is unique
{errors.map((error) => (
  <li key={`${error.path}:${error.message}:${error.line ?? ''}`}>{error.message}</li>
))}
```

### Null/undefined: `?.`/`??` by default, `instanceof` for typed error narrowing, destructuring defaults over `??` for hook results

```tsx
// admin-hooks.ts:116-119
const rows = query.data?.pages.flatMap((page) => page.items) ?? [];

// instances/index.tsx:93 — default via destructuring, not a separate ?? line
const { data: workflowNames = [] } = useWorkflowNames();

// routes/workflows/$name.tsx:53
if (error instanceof ApiError && error.status === 404) {
  return <EmptyState message="workflow not found" />;
}
```

### Async UI state: TanStack Query's own `isPending`/`isError`/`error`, sequential early-return blocks per screen

```tsx
// routes/workflows/$name.tsx:66-133 pattern — don't collapse this into one ternary
if (notFound) return <EmptyState message="not found" />;
if (error) return <Banner variant="error">{error.message}</Banner>;
if (isPending || !detail) return <Skeletons />;
// real render below
```

Don't reimplement `loading`/`error` as local `useState` — every async screen uses the query's own
flags directly.

### State: TanStack Query for server state, `useState` for ephemeral UI state, Zustand+`persist` for durable client state, no URL state

```tsx
// admin-hooks.ts — the single data-fetching entry point; routes never call fetch() directly
export function useWorkflowDetail(name: string) {
  return useQuery({ queryKey: ['workflow', name], queryFn: () => getWorkflow(name), retry: retryUnlessClientError });
}

// routes/workflows/$name.tsx:46 — ephemeral, component-local
const [tab, setTab] = useState<Tab>('versions');

// lib/definition-draft-store.ts — durable, survives reload
export const useDefinitionDraftStore = create(persist((set) => ({ definition: null, setDefinition: (d) => set({ definition: d }) }), { name: 'definition-draft' }));

// definition-editor.tsx — select one slice, don't destructure the whole store
const definition = useDefinitionDraftStore((state) => state.definition);
```

No `validateSearch`/`useSearch`/URL-based filter state exists anywhere — filters and tabs live in
component `useState`. Don't add search-param state without discussing it first; it'd be a new
pattern for this codebase, not an extension of an existing one.

### Data fetching: client-side TanStack Query hooks through one `adminFetch` boundary — no server functions, no route loaders

```tsx
// lib/admin-client.ts — the one fetch boundary; attaches the bearer token, normalizes errors
async function adminFetch(path: string, init?: RequestInit) {
  const token = await getToken();
  const res = await fetch(path, { ...init, headers: { ...init?.headers, Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new ApiError(res.status, await res.text());
  return res;
}
```

`createServerFn`/route `loader` data-fetching does **not** appear anywhere in `src/routes` despite
the README's generic TanStack-Start scaffolding text describing them — that text is template
leftover, not this project's actual pattern. New screens should add a hook to `admin-hooks.ts` and
call it from the component, not add a route loader.

Responses are validated with zod (`admin-client.ts` — `applyResultSchema.safeParse(...)`), never
cast — throw `ApiError` on shape drift instead of trusting the response.

Live updates use a hand-rolled authenticated SSE parser (`admin-client.ts` `openStream`/`readFrames`)
merged into the Query cache via `queryClient.setQueryData`, not `invalidateQueries` per event —
`EventSource` can't set an `Authorization` header, which is why this isn't the native browser API.

### Forms: no form library in actual use — `@tanstack/react-form` is a dead dependency, don't reach for it

The one real form (`definition-editor.tsx`) is hand-rolled: controlled `<select>`/`<input>` with
`onChange`, manual `useState` for submit/validate flow. If you add a new form, follow that pattern
(controlled inputs + local state) rather than introducing `@tanstack/react-form` — it's listed in
`package.json` but has zero references in `src/`; using it now would introduce a new pattern
mid-file rather than follow the existing one.

### Styling: two systems coexist — shadcn/Tailwind for `components/ui/` primitives, hand-rolled CSS-custom-property classes for route/app code

```tsx
// components/ui/button.tsx — cva + Tailwind utilities, only inside components/ui/
const buttonVariants = cva('inline-flex items-center rounded-md', {
  variants: { variant: { default: 'bg-primary text-primary-foreground', outline: 'border' } },
});

// routes/instances/index.tsx:152 — route-level code uses plain string classNames instead
<button className={`fchip${status === s ? ' active' : ''}`}>{s}</button>
```

Design tokens live in `styles.css` as CSS custom properties (`--color-bg`, `--color-accent`), used
directly in inline `style={{ color: 'var(--color-accent)' }}` and the CodeMirror theme. When adding
a new `components/ui/*` primitive, use Tailwind + `cva` to match its siblings; when adding
route/page-level UI, use the existing CSS-custom-property classes rather than introducing raw
Tailwind utilities into route files — that would mix both systems in the same file.

Tailwind v4 is configured CSS-first (`@import 'tailwindcss'` in `styles.css`, `@tailwindcss/vite` in
`vite.config.ts`) — there's no `tailwind.config.js` to edit.

### Biome (`biome.json`, full)

```json
{
  "formatter": { "enabled": true, "indentStyle": "tab" },
  "assist": { "actions": { "source": { "organizeImports": "on" } } },
  "linter": { "enabled": true, "rules": { "recommended": true } },
  "javascript": { "formatter": { "quoteStyle": "double" } }
}
```

Tabs, double quotes, imports auto-organized on save/format (React/library imports first, then
`#/...`-aliased local imports). `routeTree.gen.ts` and `styles.css` are excluded from
formatting/linting — don't hand-format either. Only `recommended` linter rules are on, no custom
overrides — don't add a rule override without discussing it, this config has stayed minimal
deliberately.

The `#/*` alias maps to `./src/*` — use `import { Foo } from '#/components/foo'`, never `../../`
relative paths across top-level directories.

### Tests: Vitest + Testing Library, colocated `*.test.tsx`, per-file `// @vitest-environment jsdom` pragma, manual `vi.mock`

```tsx
// components/definition-editor.test.tsx pattern
// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { vi } from 'vitest';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => mockNavigate }));
```

No shared global test-setup file — each test file declares its own jsdom pragma and mocks. No
Playwright/e2e config exists in this package.
