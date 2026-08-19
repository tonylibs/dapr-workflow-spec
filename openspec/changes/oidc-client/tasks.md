## 1. Dependencies & configuration

- [ ] 1.1 In `dws-console`, add `oidc-spa` (`pnpm add oidc-spa`); confirm `pnpm install` and lockfile update.
- [ ] 1.2 Add `VITE_OIDC_*` entries to `dws-console/.env.example` (authority/issuer, client id default `dws-console`, scope default `openid profile email`) with comments mirroring the existing `VITE_DWS_ADMIN_URL` style, and note the Dex issuer placeholder-host dev caveat (`http://dex.dws.local/dex` needs hosts/DNS or a port-forward).
- [ ] 1.3 Fix `charts/dws/values.yaml`: change `dex.consoleRedirectURI` default from `http://localhost:5173/callback` to `http://localhost:3000/callback`; adjust the neighboring comment if it references the old port.

## 2. OIDC config & client wiring

- [ ] 2.1 Add an auth module under `dws-console/src/lib/` that calls `oidc-spa`'s `createReactOidc` with `issuerUri`/`clientId`/`scopes` from `import.meta.env.VITE_OIDC_*` (documented defaults: `clientId=dws-console`, `scopes=openid profile email`) and the redirect/post-logout URLs derived from `window.location.origin` + `/callback` / `/`. Pin the OIDC `redirect_uri` to the `/callback` path Dex has registered (confirm `oidc-spa`'s option for this against the installed version). (design D4, D5)
- [ ] 2.2 Construct the `oidc-spa` instance lazily/browser-guarded (`typeof window !== "undefined"` / `ClientOnly` / effect-time init) so SSR never instantiates it or reads `window`. (design D3)

## 3. Provider & app-wide auth state

- [ ] 3.1 Wrap the app shell in `oidc-spa`'s `OidcProvider` in `dws-console/src/routes/__root.tsx`, degrading to an inert/unauthenticated state during SSR. (spec: authenticated identity available app-wide; design D3)
- [ ] 3.2 Expose a thin `useOidc()`-based accessor (or re-export) so any route/component can read authenticated state and the operator identity from ID-token claims (`decodedIdToken`).

## 4. Callback route & silent renew

- [ ] 4.1 Add `dws-console/src/routes/callback.tsx` (client-guarded) where `oidc-spa` completes the PKCE exchange and returns the operator into the app; handle error/denied by showing a sign-in error, not looping. (spec: sign-in via PKCE; callback route matches redirect URI)
- [ ] 4.2 Confirm whether the installed `oidc-spa` version needs a static silent-SSO asset under `public/` for the hidden-iframe `prompt=none` renew; add it if so. No dedicated `/silent-callback` route — `oidc-spa` handles silent renew internally. (spec: silent renew; design D4)
- [ ] 4.3 Run `pnpm generate-routes` and verify `routeTree.gen.ts` includes the `/callback` route.

## 5. Sign-in / sign-out UI

- [ ] 5.1 Add a sign-in control (button/view) that starts the redirect to Dex via `oidc-spa`'s `login`, shown when unauthenticated. (spec: sign-in redirects to the IdP)
- [ ] 5.2 Add a signed-in identity surface + logout control in the app chrome (e.g. via `app-layout.tsx` `topRight`) that logs out via `oidc-spa`'s `logout` — clears in-memory state AND redirects through Dex's `end_session_endpoint`. (spec: logout is RP-initiated; identity available app-wide)

## 6. Silent renew, cross-tab & resilience behavior

- [ ] 6.1 Verify `oidc-spa`'s automatic silent renew keeps an open tab authenticated across access-token expiry via the iframe `prompt=none` flow, and that a `login_required`/`interaction_required` result moves to a signed-out state without a redirect loop. (spec: session survives token expiry; silent renew fails cleanly)
- [ ] 6.2 Verify cross-tab consistency: logging out (or the session ending) in one tab converges other open tabs to a signed-out state. (spec: session state is consistent across tabs; design D7)
- [ ] 6.3 Confirm login is additive: existing read routes render and load `dws-admin` data whether or not signed in and when Dex is unreachable/misconfigured (only sign-in is unavailable). (spec: login is additive)

## 7. Verification: token-in-memory & IdP logout

- [ ] 7.1 Add a Vitest test asserting the access token is absent from both `localStorage` and `sessionStorage` while authenticated (`oidc-spa` holds tokens in memory; only short-lived transient state may touch storage, never the access token). (spec: access token in memory only; design D2)
- [ ] 7.2 Verify against the Phase-0-deployed Dex's `/.well-known/openid-configuration` that `end_session_endpoint` is advertised; if absent, raise it with the user and apply the documented fallback rather than silently dropping IdP logout. (design Open Questions)

## 8. Gates

- [ ] 8.1 From `dws-console`, run `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build` (SSR bundle) — all green.
- [ ] 8.2 From repo root, `helm template charts/dws --set dex.enabled=true` renders the `dws-console` static client with `redirectURIs` containing `http://localhost:3000/callback` by default.
- [ ] 8.3 Manual smoke (dev, Dex reachable): sign in via Dex → see authenticated identity → session survives token expiry via silent renew → logout returns to a signed-out state through Dex's end-session endpoint.
