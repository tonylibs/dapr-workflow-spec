## Why

`dws-console`'s auth roadmap (`docs/roadmaps/dws-auth.md`) delivered its IdP in Phase 0 — Dex,
in-chart, with `dws-console` pre-registered as a public PKCE client — but nothing in the console
actually logs in yet. Phase 1 closes that gap: a browser-side OIDC Authorization Code + PKCE
login against Dex, so an operator can sign in, see they are authenticated, keep the session alive
across token expiry, and sign out cleanly. It is the prerequisite for every later write/guard
phase (5/6), which have nothing to attach a token to until login exists.

## What Changes

- Add an OIDC client dependency to `dws-console` (`oidc-client-ts` + the `react-oidc-context`
  React wrapper) covering Authorization Code + PKCE, iframe-based silent renew, and RP-initiated
  logout. Rationale in `design.md`: `react-oidc-context` gives an app-wide React context and a
  `useAuth()` hook out of the box, and `oidc-client-ts` ships an `InMemoryWebStorage` so the
  access token never touches `localStorage`/`sessionStorage`.
- Mount an `AuthProvider` above the app (in `routes/__root.tsx`) configured for the Dex authority,
  the `dws-console` public client, in-memory user/token storage, and automatic silent renew.
- Add a sign-in trigger (a header control / sign-in view) that starts the redirect to Dex.
- Add a `/callback` TanStack Router file route (`routes/callback.tsx`) that completes the PKCE
  code exchange and returns the operator to the app.
- Add a `/silent-callback` route (minimal, client-only) that finishes the hidden-iframe
  `prompt=none` renew handshake.
- Expose authenticated identity app-wide (in-memory only) so UI can reflect signed-in state; wire
  a logout control that clears local state **and** redirects through Dex's `end_session_endpoint`.
- **Fix** `charts/dws/values.yaml`: `dex.consoleRedirectURI` defaults to
  `http://localhost:5173/callback`, but the console dev server runs on port 3000
  (`vite dev --port 3000`). Change the default to `http://localhost:3000/callback` so the
  registered redirect URI matches the route the console actually serves.
- Add the OIDC configuration knobs to `dws-console/.env.example` (authority/issuer, client id,
  scopes) following the existing `VITE_*` convention.

Non-goals (kept out deliberately, per the roadmap phase table):

- No change to any existing `dws-admin` read call or route — reads stay unauthenticated (Phase 6).
- The access token is **not** attached to any outbound request — nothing consumes it yet (Phase 5).
- No touch to `dws-controller`, the admin gateway, or the `dws-admin` relay (Phases 2–4).
- No new route guards; every existing route remains reachable exactly as today.

Compatibility: additive. With Dex unreachable or auth misconfigured the existing (unauthenticated)
console must keep rendering and reading — login is an added capability, never a gate on what
already works.

## Capabilities

### New Capabilities
- `console-auth`: `dws-console`'s browser-side OIDC login — Authorization Code + PKCE sign-in
  against Dex, in-memory access-token storage, the `/callback` code exchange, app-wide in-memory
  auth state, iframe silent renew, and RP-initiated logout through Dex's `end_session_endpoint`.

### Modified Capabilities
(none) — the `dex.consoleRedirectURI` fix changes only a **default value** in
`charts/dws/values.yaml`, not a behavior contract: `helm-dex-idp`'s existing requirement already
sources the redirect URI from `dex.consoleRedirectURI` and never pins the default. The
port-agreement it needs to satisfy (console serves `/callback` on the same port Dex is told to
redirect to) is captured as console behavior under `console-auth` below.

## Impact

- `dws-console/package.json`: add `oidc-client-ts` and `react-oidc-context` dependencies.
- `dws-console/src/routes/__root.tsx`: wrap the app shell in the OIDC `AuthProvider`.
- `dws-console/src/routes/callback.tsx`, `.../silent-callback.tsx`: new file routes (regenerates
  `routeTree.gen.ts`).
- `dws-console/src/lib/` (new auth module) + `src/components/`: OIDC config builder, sign-in /
  sign-out controls, signed-in identity surface.
- `dws-console/.env.example`: new `VITE_OIDC_*` entries.
- `charts/dws/values.yaml`: `dex.consoleRedirectURI` default corrected to port 3000.
- SSR caveat (TanStack Start): all OIDC/browser-only work (`crypto.subtle`, iframes, redirects)
  must be client-guarded so server rendering does not break — addressed in `design.md`.
- Dev prerequisite to flag, not solve here: Dex's issuer `http://dex.dws.local/dex` is a
  placeholder host that needs local DNS/`hosts` resolution or a port-forward to reach in dev.
