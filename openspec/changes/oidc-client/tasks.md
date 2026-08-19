## 1. Dependencies & configuration

- [ ] 1.1 In `dws-console`, add `oidc-client-ts` and `react-oidc-context` (`pnpm add oidc-client-ts react-oidc-context`); confirm `pnpm install` and lockfile update.
- [ ] 1.2 Add `VITE_OIDC_*` entries to `dws-console/.env.example` (authority/issuer, client id default `dws-console`, scope default `openid profile email`) with comments mirroring the existing `VITE_DWS_ADMIN_URL` style, and note the Dex issuer placeholder-host dev caveat (`http://dex.dws.local/dex` needs hosts/DNS or a port-forward).
- [ ] 1.3 Fix `charts/dws/values.yaml`: change `dex.consoleRedirectURI` default from `http://localhost:5173/callback` to `http://localhost:3000/callback`; adjust the neighboring comment if it references the old port.

## 2. OIDC config & client wiring

- [ ] 2.1 Add an auth config module under `dws-console/src/lib/` that builds the `oidc-client-ts` `UserManager` settings: `authority`/`client_id`/`scope` from `import.meta.env.VITE_OIDC_*` (with the documented defaults), `redirect_uri`/`post_logout_redirect_uri`/`silent_redirect_uri` derived from `window.location.origin` + `/callback` / `/` / `/silent-callback`, `response_type=code`, `userStore` = `new WebStorageStateStore({ store: new InMemoryWebStorage() })`, and `automaticSilentRenew: true`. (design D2, D5, D6)
- [ ] 2.2 Ensure the manager/config is constructed lazily and browser-guarded (`typeof window !== "undefined"`) so SSR never instantiates a `UserManager` or reads `window`. (design D3)

## 3. Provider & app-wide auth state

- [ ] 3.1 Wrap the app shell in `react-oidc-context`'s `<AuthProvider>` in `dws-console/src/routes/__root.tsx`, degrading to an inert/unauthenticated state during SSR. (spec: authenticated identity available app-wide; design D3)
- [ ] 3.2 Expose a thin `useAuth()`-based accessor (or re-export) so any route/component can read authenticated state and the operator identity from ID-token claims.

## 4. Routes: callback & silent renew

- [ ] 4.1 Add `dws-console/src/routes/callback.tsx` (client-guarded) that completes the interactive code exchange (`signinCallback`) using the stored PKCE verifier and navigates back into the app; handle error/mismatched-state by showing a sign-in error, not looping. (spec: sign-in via PKCE; callback route matches redirect URI)
- [ ] 4.2 Add `dws-console/src/routes/silent-callback.tsx` (minimal, client-only) that calls `signinSilentCallback()` for the hidden-iframe `prompt=none` handshake and renders nothing app-like. (spec: silent renew; design D4)
- [ ] 4.3 Run `pnpm generate-routes` and verify `routeTree.gen.ts` includes both new routes.

## 5. Sign-in / sign-out UI

- [ ] 5.1 Add a sign-in control (button/view) that starts the redirect to Dex (`signinRedirect`), shown when unauthenticated. (spec: sign-in redirects to the IdP)
- [ ] 5.2 Add a signed-in identity surface + logout control in the app chrome (e.g. via `app-layout.tsx` `topRight`) that on logout clears in-memory state AND calls `signoutRedirect` (RP-initiated, hits Dex `end_session_endpoint`). (spec: logout is RP-initiated; identity available app-wide)

## 6. Silent renew & resilience behavior

- [ ] 6.1 Verify `automaticSilentRenew` keeps an open tab authenticated across access-token expiry via the iframe `prompt=none` flow, and that a `login_required`/`interaction_required` result moves to signed-out state without a redirect loop. (spec: session survives token expiry; silent renew fails cleanly)
- [ ] 6.2 Confirm login is additive: existing read routes render and load `dws-admin` data whether or not signed in and when Dex is unreachable/misconfigured (only sign-in is unavailable). (spec: login is additive)

## 7. Verification: token-in-memory & IdP logout

- [ ] 7.1 Add a Vitest test asserting the access token is absent from both `localStorage` and `sessionStorage` while authenticated (only transient PKCE `oidc.*` state may appear in sessionStorage, never the access token). (spec: access token in memory only; design D2)
- [ ] 7.2 Verify against the Phase-0-deployed Dex's `/.well-known/openid-configuration` that `end_session_endpoint` is advertised; if absent, raise it with the user and apply the documented fallback rather than silently dropping IdP logout. (design Open Questions)

## 8. Gates

- [ ] 8.1 From `dws-console`, run `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build` (SSR bundle) — all green.
- [ ] 8.2 From repo root, `helm template charts/dws --set dex.enabled=true` renders the `dws-console` static client with `redirectURIs` containing `http://localhost:3000/callback` by default.
- [ ] 8.3 Manual smoke (dev, Dex reachable): sign in via Dex → see authenticated identity → session survives token expiry via silent renew → logout returns to a signed-out state through Dex's end-session endpoint.
