## Context

See `proposal.md` — Why. Phase 0 already registered `dws-console` in Dex as a public PKCE client
(client id `dws-console`, one redirect URI from `dex.consoleRedirectURI`, issuer default
`http://dex.dws.local/dex`). This phase adds the browser login only.

Constraints that shape the approach:

- **`dws-console` is a TanStack Start app (SSR)**, not a pure SPA — `vite.config.ts` uses
  `tanstackStart()`, `routes/__root.tsx` renders a full HTML document, and `server.js` server-side
  renders every non-asset request. All OIDC work depends on browser-only APIs (`crypto.subtle` for
  PKCE, `window.location`, hidden iframes) and MUST NOT run during SSR.
- Routes are TanStack Router **file routes** under `src/routes/`; `routeTree.gen.ts` is generated
  (`pnpm generate-routes` / `tsr generate`). The import alias is `#/*` → `./src/*`. Tooling is
  Biome (lint/format) + Vitest; there is no top-level build (`cd dws-console`).
- Config already flows through `import.meta.env.VITE_*` (see `admin-client.ts` /
  `.env.example`) — new OIDC config follows the same convention.
- Ground rules are non-negotiable (from `dws-auth.md`): access token in memory only; silent renew
  via iframe `prompt=none`, no full-page redirect; logout hits Dex's own `end_session_endpoint`.

## Goals / Non-Goals

**Goals:**
- A working Authorization Code + PKCE login against Dex with an in-memory access token, iframe
  silent renew, and RP-initiated logout, exposed app-wide via a React context.
- Keep the SSR render path clean — auth never executes on the server.
- Keep login strictly additive: the existing unauthenticated read UI is untouched.

**Non-Goals (design-level, on top of the proposal's scope):**
- No custom crypto or hand-rolled PKCE/JWT handling — delegate to the library.
- No route guards, no attaching the token to requests, no server-side session/cookie.
- No production DNS/ingress wiring for Dex's placeholder issuer host — dev reachability is flagged,
  not solved here.

## Decisions

### D1 — Library: `oidc-client-ts` + `react-oidc-context`
Use `oidc-client-ts` (the maintained successor to `oidc-client-js`) with the `react-oidc-context`
wrapper.
- **Why:** `react-oidc-context` provides `<AuthProvider>` and a `useAuth()` hook — a ready app-wide
  React context (deliverable 4) — while `oidc-client-ts` implements PKCE, the iframe silent-renew
  handshake (`automaticSilentRenew` / `signinSilent`), and RP-initiated logout
  (`signoutRedirect` using the discovered `end_session_endpoint`) directly (deliverables 1, 5, 6).
  Both are actively maintained and framework-appropriate.
- **Alternatives:** raw `oidc-client-ts` without the wrapper (more context/plumbing to hand-write);
  `@auth0/auth0-react` or similar (vendor-oriented, not a generic Dex/OIDC fit); hand-rolling the
  flow (rejected — crypto and token handling should not be bespoke).

### D2 — In-memory token store via `InMemoryWebStorage`
Configure the `UserManager` `userStore` as
`new WebStorageStateStore({ store: new InMemoryWebStorage() })` (both from `oidc-client-ts`).
- **Why:** this is exactly the "token in memory only" ground rule — the `User` object (which holds
  the access/ID tokens) lives in a plain in-memory map, never `localStorage`/`sessionStorage`. A
  reload therefore starts with no token (the app then attempts a silent renew or shows sign-in).
- **Trade-off / nuance:** the *transient* PKCE `state`/`code_verifier` (`stateStore`) legitimately
  must survive the full-page redirect to Dex and back, so it cannot be pure in-memory. Leave the
  `stateStore` on the default `sessionStorage`: it holds only short-lived, single-use PKCE
  state, never the access token, and oidc-client-ts clears it on callback. This satisfies the
  ground rule (which targets the *access token*) — documented so a reviewer doesn't read the
  sessionStorage `oidc.*` state entry as a violation.

### D3 — SSR safety: client-only auth boundary
Mount `<AuthProvider>` in `routes/__root.tsx` but ensure the `UserManager` and every OIDC call only
touch the browser. Construct OIDC config/manager lazily and guard on `typeof window !== "undefined"`
(and/or a `ClientOnly` boundary / effect-time initialization), so server rendering never constructs
a `UserManager` or reads `window`.
- **Why:** `oidc-client-ts` assumes a browser; instantiating it during SSR throws. TanStack Start
  renders `__root` on the server, so the provider must degrade to an inert/unauthenticated state on
  the server and hydrate the real client on the browser.
- **Alternative:** move auth entirely below a client-only subtree — heavier and still needs the same
  guard; the guard is the minimal correct fix.

### D4 — Routes: `/callback` and `/silent-callback`
Add two file routes:
- `src/routes/callback.tsx` — completes the interactive code exchange (`signinCallback`), then
  navigates back into the app. Matches the registered redirect URI path.
- `src/routes/silent-callback.tsx` — minimal, client-only; finishes the hidden-iframe
  `prompt=none` handshake (`signinSilentCallback`) and renders nothing meaningful (it lives inside
  the iframe). Its URL is the `silent_redirect_uri`.
- **Why two routes:** oidc-client-ts distinguishes the top-level callback from the iframe silent
  callback; conflating them makes the iframe try to render the full app. Regenerate
  `routeTree.gen.ts` after adding them.

### D5 — OIDC configuration via `VITE_OIDC_*`, redirect derived at runtime
Source `authority` (Dex issuer), `client_id`, and `scope` from `VITE_OIDC_*` env (documented in
`.env.example`), defaulting `client_id` to `dws-console` and scope to `openid profile email`.
Derive `redirect_uri`/`post_logout_redirect_uri`/`silent_redirect_uri` from
`window.location.origin` + a fixed path (`/callback`, `/`, `/silent-callback`) at runtime rather
than pinning a host in env.
- **Why:** the console is served from different origins in dev vs deployment; deriving the redirect
  from the live origin keeps one build working everywhere, and matches how Dex validates the
  registered URI (path + port). The chart default `dex.consoleRedirectURI` is corrected to
  `http://localhost:3000/callback` so a default local install lines up with `vite dev --port 3000`.
- **Alternative:** fully env-pinned redirect URIs — more env surface and easy to get out of sync
  with the served port; rejected for the derive-from-origin approach.

### D6 — Silent renew wiring
Enable `automaticSilentRenew: true` on the `UserManager` and set `silent_redirect_uri`; the library
schedules a `prompt=none` iframe request before expiry and updates the in-memory user. Surface
renew failure by moving to signed-out state (no redirect loop).

## Risks / Trade-offs

- **Dex may not expose `end_session_endpoint`.** RP-initiated logout depends on Dex advertising
  `end_session_endpoint` in its discovery document; some Dex versions/configs do not. → Verify
  against the Phase-0-deployed Dex's `/.well-known/openid-configuration`. If absent, `signoutRedirect`
  can't run; fall back to clearing local state plus a documented gap and raise it — do not silently
  drop the IdP-logout ground rule. Tracked in Open Questions.
- **Issuer host is a placeholder.** `http://dex.dws.local/dex` needs `hosts`/DNS or a port-forward
  to reach in dev. → Flag in `.env.example` / README; do not invent a workaround. Discovery will
  fail loudly if unreachable, which is acceptable (login unavailable, rest of app fine — per the
  additive requirement).
- **SSR double-mount / hydration.** A `UserManager` constructed at import time would break SSR. →
  D3's lazy, browser-guarded construction; verify `pnpm build` (SSR bundle) and a dev load both work.
- **sessionStorage PKCE state misread as a token leak.** → D2 documents that only transient PKCE
  state is there, never the access token; a test asserts the access token is absent from both web
  storages.
- **`http://` (non-TLS) origins.** `crypto.subtle` (PKCE) requires a secure context; `localhost` is
  treated as secure so dev works, but a non-localhost `http://` deployment would break PKCE. →
  Note as a deployment constraint (out of scope to fix here).

## Migration Plan

Additive and dev-facing; no data migration.
- Add deps in `dws-console` (`pnpm add oidc-client-ts react-oidc-context`), wire provider/routes,
  regenerate routes, add `.env.example` entries, and correct the chart default.
- Rollback: revert the `dws-console` changes and the one-line `values.yaml` default. Because login
  is additive and never gates reads, reverting leaves the console exactly as it was.
- Validate with the console's own gates: `pnpm lint`, `pnpm test`, `pnpm build`, `pnpm typecheck`.

## Open Questions

- Does the Phase-0 Dex build advertise `end_session_endpoint`? Confirm from its live discovery
  document during implementation; if it doesn't, decide (with the user) between enabling it in the
  Dex config vs. accepting a documented logout gap. This can be answered at implementation time
  without changing the specs — the requirement stays "logout is RP-initiated"; only the fallback
  path would differ.
