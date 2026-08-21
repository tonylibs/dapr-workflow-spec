## 1. Dependencies & configuration

- [x] 1.1 In `dws-console`, add `oidc-spa` (`pnpm add oidc-spa`); confirm `pnpm install` and lockfile update.
- [x] 1.2 Add `VITE_OIDC_*` entries to `dws-console/.env.example` (issuer, client id default `dws-console`, scopes default `profile email` — `openid` is added by the library) with comments mirroring the existing `VITE_DWS_ADMIN_URL` style, and note the Dex issuer placeholder-host dev caveat (`http://dex.dws.local/dex` needs hosts/DNS or a port-forward).
- [x] 1.3 Fix `charts/dws/values.yaml`: change `dex.consoleRedirectURI` default from `http://localhost:5173/callback` to `http://localhost:3000/` (app root — `oidc-spa` pins the redirect URI to the app root, design D4); update the neighboring comment to explain the root-URL requirement.

## 2. OIDC bootstrap & SSR wiring

- [x] 2.1 Add `dws-console/src/lib/oidc.ts` using `oidcSpa` from `oidc-spa/react-tanstack-start`: `.createUtils()`, then `bootstrapOidc({ implementation: "real", issuerUri, clientId, scopes })` sourced from `import.meta.env.VITE_OIDC_*` (defaults `dws-console` / `["profile","email"]`). Export `useOidc`/`getOidc`. No redirect-URI option exists — the library uses the app root. (design D4, D5)
- [x] 2.2 Add the `oidcSpa()` plugin from `oidc-spa/vite-plugin` to `dws-console/vite.config.ts` so the TanStack Start client/server entries are wired for SSR. (design D3)

## 3. App-wide auth state

- [x] 3.1 Verify auth state is readable app-wide via the module-level `useOidc()` singleton (no provider component exists in v10); confirm the app shell renders correctly for both signed-in and signed-out states under SSR. (spec: authenticated identity available app-wide; design D3/D5)
- [x] 3.2 Expose a thin accessor over `useOidc()` so routes/components read authenticated state and the operator identity from ID-token claims (`decodedIdToken`) without importing OIDC internals.

## 4. Redirect handling & silent renew

- [x] 4.1 Confirm `oidc-spa` completes the PKCE exchange on the app root and restores the originating route. Verified against the installed v10.2.11: `createOidc`/`bootstrapOidc` expose no redirect-URI option and the README states the redirect URI is the app root. No `/callback` route is added. (spec: sign-in via PKCE; registered redirect URI matches the URL the console serves; design D4)
- [x] 4.2 Confirm silent renew needs no dedicated route or static asset — `oidc-spa` runs the hidden-iframe `prompt=none` flow internally (`sessionRestorationMethod`, default `"auto"`). (spec: silent renew; design D4)

## 5. Sign-in / sign-out UI

- [x] 5.1 Add a sign-in control (button/view) that starts the redirect to Dex via `useOidc()`'s `login`, shown when unauthenticated. (spec: sign-in redirects to the IdP)
- [x] 5.2 Add a signed-in identity surface + logout control in the app chrome (e.g. via `app-layout.tsx` `topRight`) that logs out via `useOidc()`'s `logout` — clears in-memory state AND redirects through Dex's `end_session_endpoint`. (spec: logout is RP-initiated; identity available app-wide)

## 6. Silent renew, cross-tab & resilience behavior

- [ ] 6.1 **BLOCKED — needs a reachable Dex.** Verify `oidc-spa`'s automatic silent renew keeps an open tab authenticated across access-token expiry via the iframe `prompt=none` flow, and that a `login_required`/`interaction_required` result moves to a signed-out state without a redirect loop. (spec: session survives token expiry; silent renew fails cleanly)
- [ ] 6.2 **BLOCKED — needs a reachable Dex.** Verify cross-tab consistency: logging out (or the session ending) in one tab converges other open tabs to a signed-out state. (spec: session state is consistent across tabs; design D7)
- [x] 6.3 Confirm login is additive. Verified with Dex unreachable: `pnpm dev` serves `/workflows` and `/instances` at HTTP 200 with the full app shell server-rendered, `/` still 307-redirects as before, and the auth control renders nothing (it collapses in both `initializing` and `unavailable` states) rather than offering a sign-in that could only fail. (spec: login is additive)

## 7. Verification: token-in-memory & IdP logout

- [x] 7.1 Cover the token-handling invariants this code owns with Vitest (`src/lib/oidc-config.test.ts`): the auth state exposes no token/credential field in any variant, sign-out goes through the IdP (`logout({redirectTo:"home"})`) rather than only clearing local state, and config/claim-label resolution is correct. **Scope note:** asserting an access token is absent from `localStorage`/`sessionStorage` *while authenticated* is not unit-testable here — it needs a real login against a live IdP in a browser, and the repo has no DOM test environment. Keeping the token out of web storage is `oidc-spa`'s documented in-memory design (design D2); the runtime check belongs to the 8.3 smoke. (spec: access token in memory only; design D2)
- [ ] 7.2 **BLOCKED — needs a reachable Dex.** Verify against the Phase-0-deployed Dex's `/.well-known/openid-configuration` that `end_session_endpoint` is advertised; if absent, raise it and apply the documented fallback rather than silently dropping IdP logout. (design Open Questions)

## 8. Gates

- [x] 8.1 From `dws-console`, run `pnpm lint`, `pnpm typecheck`, `pnpm test` (56 passed), and `pnpm build` (client + SSR bundles) — all green.
- [x] 8.2 `helm template dws charts/dws --set dex.enabled=true` renders the `dws-console` static client with `redirectURIs: [http://localhost:3000/]` by default (decoded from the `dex-config` Secret), `public: true` and no secret.
- [ ] 8.3 **BLOCKED — needs a reachable Dex.** Manual smoke (dev, Dex reachable): sign in via Dex → see authenticated identity → confirm no access token in `localStorage`/`sessionStorage` → session survives token expiry via silent renew → logout returns to a signed-out state through Dex's end-session endpoint.

**Blocked tasks (6.1, 6.2, 7.2, 8.3)** all need a reachable Dex, which this environment does not have: there is no cluster (`kubectl cluster-info` refused) and the chart's default issuer host `dex.dws.local` does not resolve. Per the roadmap's instruction to flag rather than work around, they are left for an environment with Dex deployed and its issuer resolvable (hosts entry or port-forward, with `VITE_OIDC_ISSUER_URI` and `dex.issuer` pointed at the same URL).
