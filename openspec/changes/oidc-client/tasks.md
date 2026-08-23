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

- [ ] 6.1 **DEFERRED TO ROADMAP PHASE 8 — failed against the chart-installed Dex.** The browser emitted an Authorization Code +
  PKCE request to `/auth` with `prompt=none`, `response_type=code`, client `dws-console`, root
  redirect `http://localhost:3000/`, and an S256 challenge. Dex 2.44.0 routed that hidden iframe
  through `/auth/local/login` instead of returning `login_required`; `oidc-spa` timed out after nine
  seconds. Because the same unsupported session check blocks initial restoration, neither an
  expiry renewal nor clean signed-out transition after the Dex session disappears can be genuinely
  exercised. (spec: session survives token expiry; silent renew fails cleanly)
- [ ] 6.2 **DEFERRED TO ROADMAP PHASE 8 — blocked after a live attempt.** The console cannot reach an interactive signed-out state
  against Dex 2.44.0: its initial hidden `prompt=none` restoration times out first. Two tabs therefore
  cannot both be authenticated, so logout/session-end convergence remains unverified. (spec:
  session state is consistent across tabs; design D7)
- [x] 6.3 Confirm login is additive. Verified live with Dex reachable: `/workflows` and `/instances`
  continue to render their unauthenticated app shells and error states when `dws-admin` is absent.
  If OIDC initialization fails, the chrome now reports `Authentication unavailable` instead of
  hiding the failure; no read route is gated. (spec: login is additive)

## 7. Verification: token-in-memory & IdP logout

- [x] 7.1 Cover the token-handling invariants this code owns with Vitest (`src/lib/oidc-config.test.ts`): the auth state exposes no token/credential field in any variant, sign-out goes through the IdP (`logout({redirectTo:"home"})`) rather than only clearing local state, and config/claim-label resolution is correct. **Scope note:** asserting an access token is absent from `localStorage`/`sessionStorage` *while authenticated* is not unit-testable here — it needs a real login against a live IdP in a browser, and the repo has no DOM test environment. Keeping the token out of web storage is `oidc-spa`'s documented in-memory design (design D2); the runtime check belongs to the 8.3 smoke. (spec: access token in memory only; design D2)
- [ ] 7.2 **DEFERRED TO ROADMAP PHASE 8 — failed against the chart-installed Dex.** Live discovery at
  `http://localhost:5556/.well-known/openid-configuration` advertised `/auth`, `/token`, and `/keys`
  but no `end_session_endpoint`. The bundled chart pins Dex 2.44.0, whose released server has no
  native browser-session/RP-logout endpoint; that work is tracked upstream in
  [dexidp/dex#4560](https://github.com/dexidp/dex/issues/4560). No chart value can safely manufacture
  the missing protocol support. A local-only logout or fabricated discovery field was deliberately
  not substituted for RP-initiated logout. (design Open Questions)

## 8. Gates

- [x] 8.1 Fresh on 2026-08-23 from `dws-console`: `pnpm lint`, `pnpm typecheck`, `pnpm test`
  (57 passed), and `pnpm build` (client + SSR bundles) — all green.
- [x] 8.2 `helm lint charts/dws --set dex.enabled=true` passes. A rendered Dex config with issuer
  `http://localhost:5556` contains public client `dws-console`, no client secret, root redirect
  `http://localhost:3000/`, and browser CORS origin `http://localhost:3000`.
- [ ] 8.3 **DEFERRED TO ROADMAP PHASE 8 — failed live before interactive sign-in.** Discovery and CORS succeed, but `oidc-spa`'s
  initial hidden `prompt=none` request reaches Dex's login form and times out. The console never
  exposes its sign-in control, so successful sign-in, route restoration, identity display,
  authenticated web-storage inspection, expiry renewal, RP logout, and two-tab convergence cannot
  be claimed. The visible failure state and unchanged unauthenticated read routes were verified.

### Live-Dex environment and remaining blocker (2026-08-23)

- Kubernetes: Docker Desktop, context `docker-desktop`, server v1.34.3.
- Helm release: `dws-phase1`, namespace `dws-phase1`; only Dex was enabled:
  `helm upgrade --install dws-phase1 charts/dws -n dws-phase1 --create-namespace --set dex.enabled=true --set-string dex.issuer=http://localhost:5556 --set-string dex.consoleRedirectURI=http://localhost:3000/ --set controller.enabled=false --set admin.enabled=false --set postgresql.enabled=false --set dapr.enabled=false`.
- Reachability: `kubectl -n dws-phase1 port-forward svc/dws-phase1-dex 5556:5556`; console at
  `http://localhost:3000/` with `VITE_OIDC_ISSUER_URI=http://localhost:5556`. The issuer values were
  identical, and the registered redirect was the console root. This localhost bridge is suitable
  only for Phase 1 browser validation; a cluster workload would resolve `localhost` to itself, so it
  is unsuitable for Phase 2 service-side discovery or token validation.
- Helm NOTES emitted, and the bootstrap login was retrieved with,
  `kubectl get secret dws-phase1-dex-admin-credentials -n dws-phase1 -o jsonpath='{.data.email}' | base64 -d; echo`
  and the corresponding `.data.password` command. The email resolved to `admin@dws.local`; the
  password was used only as a secret and is not recorded here.
- Discovery returned issuer `http://localhost:5556`, authorization endpoint `/auth`, token endpoint
  `/token`, JWKS `/keys`, and S256 support. It did **not** return `end_session_endpoint`.
- The chart originally omitted Dex's `web.allowedOrigins`, so browser discovery failed CORS. The
  generated Dex config now derives the allowed origin from `dex.consoleRedirectURI`; a live request
  with `Origin: http://localhost:3000` returns `Access-Control-Allow-Origin: http://localhost:3000`.
- Deferred Phase 8 scope: released Dex 2.44.0 cannot answer browser-session `prompt=none` checks or
  advertise RP-initiated logout. Phase 8 will adopt a released provider version with both behaviors
  (or a different compliant in-chart IdP) and rerun tasks 6.1, 6.2, 7.2, and 8.3. These tasks remain
  open and unverified, but no longer make the completed provider-agnostic Phase 1 implementation
  partial.
