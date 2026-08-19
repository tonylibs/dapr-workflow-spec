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

### D3 — SSR safety: client-only auth boundary
`oidc-spa` is browser-only. Mount `OidcProvider` in `routes/__root.tsx` but construct the OIDC
instance lazily/browser-guarded (`typeof window !== "undefined"`, a `ClientOnly` boundary, or
effect-time init) so TanStack Start's server render never instantiates it or reads `window`; on the
server the tree renders in an inert/unauthenticated state and hydrates the real client in the
browser.
- **Why:** instantiating a browser OIDC client during SSR throws; `server.js` server-renders every
  non-asset request, so the guard is required.
- **Alternative:** move auth entirely below a client-only subtree — heavier and still needs the same
  guard; the guard is the minimal correct fix.

### D4 — Callback handling on `/callback`; silent renew is internal
Dex has one registered redirect URI (`dex.consoleRedirectURI`, corrected to `…/callback`), and the
spec requires the console to handle the redirect at `/callback`. Configure `oidc-spa` so its OIDC
`redirect_uri` is that `/callback` URL, and add a `src/routes/callback.tsx` file route where
`oidc-spa` finishes the exchange (it detects the `?code&state` params, completes PKCE, then returns
the operator into the app). Regenerate `routeTree.gen.ts` after adding it.
- **No separate `/silent-callback` route** (unlike an `oidc-client-ts` setup): `oidc-spa` runs the
  hidden-iframe `prompt=none` renew internally. Depending on the installed `oidc-spa` version this
  may require a small static silent-SSO asset under `public/` — confirm against that version's docs
  at implementation time and add it if needed. This is the one integration detail to verify; it does
  not change the specs.
- **Redirect-URI mechanics to confirm:** `oidc-spa`'s exact API for pinning the `redirect_uri` to a
  `/callback` subpath (vs. its default home-URL handling) is version-specific — verify it lines up
  with Dex's registered value during implementation; the fixed constraint is "Dex-registered URI ==
  the path the console serves", not any particular `oidc-spa` option name.

### D5 — OIDC configuration via `VITE_OIDC_*`, redirect derived at runtime
Pass `oidc-spa`'s `issuerUri`, `clientId`, and `scopes` from `import.meta.env.VITE_OIDC_*`
(documented in `.env.example`), defaulting `clientId` to `dws-console` and scopes to
`openid profile email`. Derive the redirect/post-logout URLs from `window.location.origin` + fixed
paths (`/callback`, `/`) at runtime rather than pinning a host in env.
- **Why:** the console is served from different origins in dev vs deployment; deriving from the live
  origin keeps one build working everywhere and matches how Dex validates the registered URI (path +
  port). The chart default `dex.consoleRedirectURI` is corrected to `http://localhost:3000/callback`
  so a default local install lines up with `vite dev --port 3000`.
- **Alternative:** fully env-pinned redirect URIs — more env surface, easy to drift from the served
  port; rejected for the derive-from-origin approach.

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
- **SSR double-mount / hydration.** A browser OIDC instance constructed at import time would break
  SSR. → D3's lazy, browser-guarded construction; verify `pnpm build` (SSR bundle) and a dev load
  both work.
- **`oidc-spa` redirect-URI / silent-SSO mechanics are version-specific.** Pinning the OIDC
  `redirect_uri` to `/callback` and whether a static silent-SSO asset is needed depend on the
  installed `oidc-spa` version. → D4: confirm against that version's docs during implementation; the
  fixed constraint (Dex-registered URI == served path) does not change.
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
