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

### D1 — Library: `oidc-spa`
Use [`oidc-spa`](https://www.oidc-spa.dev/) as the OIDC client.
- **Why:** it is purpose-built for Vite + React single-page apps and covers every deliverable out
  of the box — Authorization Code + PKCE, automatic **iframe silent renew**, and **RP-initiated
  logout** (via the IdP's `end_session_endpoint`) — with **zero runtime dependencies**. Its React
  binding (`createReactOidc` → `OidcProvider` + `useOidc`) gives the app-wide auth context and hook
  (deliverable 4) directly. Tokens are held **in memory by design** (see D2), which matches the
  ground rule instead of having to be configured around. As a bonus it **syncs login/logout state
  across tabs** (D7) — not required by the roadmap, but valuable precisely because the token is
  in-memory-only: a second tab would otherwise not learn the session ended.
- **Alternatives:** `oidc-client-ts` + `react-oidc-context` (also viable and OIDC-certified, but
  needs an explicit `InMemoryWebStorage` userStore, a hand-wired context, and a separate
  silent-callback route — more plumbing for the same result; no cross-tab sync); `oauth4webapi`
  (spec-correct low-level primitives, but session/renew/logout are hand-built); Auth.js / Better
  Auth (wrong category — server-session BFF frameworks that keep the token server-side, contrary to
  the in-memory-token ground rule); vendor SDKs like `@auth0/auth0-react` (IdP-locked, not a generic
  Dex fit).

### D2 — In-memory token by design
`oidc-spa` keeps the access/ID tokens in memory (a JS closure), never in `localStorage` or
`sessionStorage`, so the "token in memory only" ground rule holds without extra storage config.
- **Why:** a reload therefore starts with no token in web storage; `oidc-spa` re-establishes the
  session with a silent (`prompt=none`) SSO check against Dex if the IdP session is still valid, or
  presents sign-in otherwise.
- **Nuance:** any transient value `oidc-spa` needs to carry across the redirect (PKCE state) is
  short-lived and single-use, never the access token — a test asserts the access token is absent
  from both web storages (see Risks).

### D3 — SSR safety via `oidc-spa`'s official TanStack Start adapter + Vite plugin
The installed `oidc-spa` (v10) ships a dedicated TanStack Start integration: the
`oidc-spa/react-tanstack-start` entrypoint and the `oidc-spa/vite-plugin` Vite plugin, which
auto-detects a TanStack Start project (by the `tanstack-react-start:config` plugin) and performs the
client/server entry transformations itself. Use those rather than hand-rolling a
`typeof window !== "undefined"` guard.
- **Why:** `dws-console` server-renders every non-asset request (`server.js` + `tanstackStart()`), so
  a browser-only OIDC client needs SSR handling. The library solves this for exactly this stack —
  the plugin injects the server-entry wiring (its `__withOidcSpaServerEntry` /
  `__disableSsrIfLoginEnforced` exports are underscore-prefixed internals, applied by the plugin, not
  called by app code). Using the official path is more robust than a bespoke guard and is
  forward-compatible with Phases 5/6 (the same adapter exposes `oidcFnMiddleware` and `enforceLogin`,
  both unused here).
- **Plugin ordering:** the plugin declares `enforce: "pre"`, so Vite orders it ahead of
  `tanstackStart()` regardless of array position; list it early anyway for readability.
- **Alternative:** the pure-client `oidc-spa/react-spa` adapter plus a manual SSR guard — simpler in
  isolation, but fights the SSR setup this app actually has and would be replaced at Phase 5.

### D4 — Redirect URI is the app root; no `/callback` route
`oidc-spa` v10 **does not accept a redirect-URI parameter at all**. Its README is explicit: *"The
Redirect URI (callback URL) is the root URL of your app (no public/callback.html involved)"*, and
`createOidc`/`bootstrapOidc` take only `issuerUri`, `clientId`, `scopes` (plus optional tuning). The
library completes the code exchange in-place on the app's own root URL and then restores the route
the operator started from.
- **Consequence:** there is **no `/callback` route**, and `dex.consoleRedirectURI` must be registered
  as the console's **root URL** (`http://localhost:3000/` by default), not `…/callback`. The original
  plan called for a `/callback` route; that is not expressible with this library, and registering a
  `/callback` URI that `oidc-spa` will never use would only break login. The underlying bug the
  roadmap flagged — the chart default pointing at the wrong port (`:5173`, while the dev server runs
  on `:3000`) — is still fixed, just to `http://localhost:3000/`.
- **Silent renew** is likewise internal: `oidc-spa` runs the hidden-iframe `prompt=none` flow itself,
  with no dedicated route and no static callback asset.

### D5 — OIDC configuration via `VITE_OIDC_*`
Pass `oidc-spa`'s `issuerUri`, `clientId`, and `scopes` from `import.meta.env.VITE_OIDC_*`
(documented in `.env.example`), defaulting `clientId` to `dws-console` and scopes to
`profile email` (`openid` is added by the library automatically). There are no redirect URLs to
configure (D4) — the library derives them from the app's own origin, which is what keeps one build
working across dev and deployment origins.
- **Bootstrap shape:** v10 exposes no React provider component. `oidcSpa.createUtils()` returns a
  module-level `{ bootstrapOidc, useOidc, getOidc, enforceLogin }` singleton; `bootstrapOidc({
  implementation: "real", … })` is called once at module scope. Auth state is therefore app-wide by
  construction, without a context provider in `__root.tsx`.

### D6 — Silent renew wiring
Rely on `oidc-spa`'s built-in automatic silent renew: it schedules a `prompt=none` iframe request
before token expiry and refreshes the in-memory tokens with no full-page redirect. On a renew that
fails because the Dex session is gone (`login_required`/`interaction_required`), surface a
signed-out state through `useOidc` rather than looping.

### D7 — Cross-tab session sync (bonus)
Keep `oidc-spa`'s cross-tab synchronization on: when one tab logs out (or a session ends), other
open tabs observe the signed-out state too.
- **Why:** with an in-memory-only token, each tab holds its own token and a second tab would not
  otherwise learn the session ended until its own next renew. This is not a roadmap requirement but
  it closes a real gap the in-memory constraint creates, at no extra cost since `oidc-spa` provides
  it. Captured as a spec scenario so the behavior is intentional, not incidental.

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
- **SSR double-mount / hydration.** A browser OIDC instance reaching the server render would break
  SSR. → D3 delegates this to `oidc-spa`'s official Vite plugin + TanStack Start adapter; verify
  `pnpm build` (SSR bundle) and a dev load both work.
- **Redirect URI is not configurable (resolved, was an open question).** `oidc-spa` v10 pins the
  redirect URI to the app root. → D4: register the console's root URL in Dex and add no `/callback`
  route. Anyone re-reading the roadmap's "implement the matching /callback route" wording should read
  D4 first — that deliverable is not expressible with this library.
- **Access token must not leak to web storage.** → D2: `oidc-spa` holds tokens in memory; a test
  asserts the access token is absent from both `localStorage` and `sessionStorage` while signed in.
- **`http://` (non-TLS) origins.** `crypto.subtle` (PKCE) requires a secure context; `localhost` is
  treated as secure so dev works, but a non-localhost `http://` deployment would break PKCE. →
  Note as a deployment constraint (out of scope to fix here).

## Migration Plan

Additive and dev-facing; no data migration.
- Add the dep in `dws-console` (`pnpm add oidc-spa`), wire the provider and `/callback` route,
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
