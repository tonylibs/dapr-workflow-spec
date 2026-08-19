## Purpose

Defines `dws-console`'s browser-side operator login: OIDC Authorization Code + PKCE sign-in
against the Dex IdP, an in-memory-only access token, silent renewal, and clean RP-initiated
logout — the authentication foundation later phases attach authorization to.

## ADDED Requirements

### Requirement: Operator can sign in via Authorization Code + PKCE

The `dws-console` SHALL let an operator initiate sign-in from the running app and complete an
OpenID Connect Authorization Code flow with PKCE against the Dex IdP, using the public
`dws-console` client (no client secret). On success the operator SHALL be returned to the console
in an authenticated state.

Owning component: `dws-console`.

#### Scenario: Sign-in redirects to the IdP
- **WHEN** an unauthenticated operator activates the sign-in control
- **THEN** the browser is redirected to Dex's authorization endpoint with `response_type=code`, a
  PKCE `code_challenge`, and the `dws-console` client id

#### Scenario: Callback completes the code exchange
- **WHEN** Dex redirects back to the console's `/callback` route with an authorization code and the
  matching state
- **THEN** the console exchanges the code for tokens using the stored PKCE verifier and transitions
  to an authenticated state without a further full-page redirect

#### Scenario: Failed or denied authorization surfaces an error
- **WHEN** the `/callback` route is reached with an error response (e.g. access denied) or a state
  that does not match the request
- **THEN** the console does not enter an authenticated state and shows a sign-in error rather than
  crashing or looping

### Requirement: Access token is held in memory only

The `dws-console` SHALL keep the access token (and any other token material carrying access) in
memory only. It SHALL NOT write the access token to `localStorage` or `sessionStorage`, so a page
reload or new tab starts unauthenticated rather than rehydrating a token from disk.

Owning component: `dws-console`.

#### Scenario: Token absent from web storage
- **WHEN** an operator is authenticated and the access token is present in memory
- **THEN** neither `localStorage` nor `sessionStorage` contains the access token value

#### Scenario: Reload does not rehydrate the token from storage
- **WHEN** an authenticated operator reloads the page
- **THEN** the console does not restore the previous access token from web storage (it either
  silently re-establishes the session against the IdP or presents sign-in)

### Requirement: Authenticated identity is available app-wide

The `dws-console` SHALL expose the current authentication state and the signed-in operator's
identity (from the ID token claims) through an app-wide React context available to any route or
component, and SHALL reflect signed-in versus signed-out state in the UI.

Owning component: `dws-console`.

#### Scenario: Signed-in identity is readable anywhere
- **WHEN** a component under the app shell reads the auth context while the operator is signed in
- **THEN** it observes an authenticated state and the operator's identity (e.g. email/subject) from
  the ID token

#### Scenario: Signed-out state is observable
- **WHEN** no operator is signed in
- **THEN** the auth context reports an unauthenticated state and the UI offers the sign-in control

### Requirement: Session survives token expiry via silent renew

The `dws-console` SHALL renew an expiring access token without a full-page redirect, using a hidden
iframe request to Dex with `prompt=none`, so an open tab's session survives access-token expiry
while the IdP session is still valid.

Owning component: `dws-console`.

#### Scenario: Silent renew before expiry keeps the session
- **WHEN** the access token is nearing expiry and the Dex session is still valid
- **THEN** the console obtains a fresh token through the hidden-iframe `prompt=none` flow and the
  operator remains authenticated without any visible full-page navigation

#### Scenario: Silent renew fails when the IdP session is gone
- **WHEN** a silent renew is attempted but Dex no longer has a valid session (`prompt=none` returns
  `login_required`/`interaction_required`)
- **THEN** the console transitions to a signed-out state rather than looping, and the operator can
  sign in again

### Requirement: Session state is consistent across tabs

The `dws-console` SHALL keep authentication state consistent across the operator's open tabs of the
app: when the session ends in one tab (explicit logout or session termination), other open tabs
SHALL converge to a signed-out state rather than continuing to present an authenticated UI backed by
a token only that other tab held.

Owning component: `dws-console`.

#### Scenario: Logout in one tab signs out the others
- **WHEN** the operator has the console open in two tabs and logs out (or the session ends) in one
- **THEN** the other tab converges to a signed-out state rather than remaining in an authenticated UI

### Requirement: Logout is RP-initiated through the IdP

The `dws-console` SHALL, on logout, clear its in-memory auth state AND redirect the browser to
Dex's RP-initiated logout (`end_session_endpoint`), rather than only clearing local state. After
logout the operator SHALL land back in the console in a signed-out state.

Owning component: `dws-console`.

#### Scenario: Logout hits the IdP end-session endpoint
- **WHEN** an authenticated operator activates logout
- **THEN** local in-memory auth state is cleared and the browser is redirected to Dex's
  `end_session_endpoint`, terminating the IdP session, not just the local one

#### Scenario: Post-logout return is signed out
- **WHEN** Dex completes RP-initiated logout and returns the browser to the console
- **THEN** the console renders a signed-out state and offers sign-in

### Requirement: Callback route matches the registered redirect URI

The `dws-console` SHALL serve its OIDC redirect handling at the `/callback` path, matching the
redirect URI registered for the `dws-console` client in Dex (`dex.consoleRedirectURI`). The chart's
default `dex.consoleRedirectURI` and the console dev server's port SHALL agree so a default local
install can complete the flow without reconfiguration.

Owning component: `dws-console` (callback route); `charts/dws` (default redirect-URI value).

#### Scenario: Default redirect URI resolves to a served route on the dev port
- **WHEN** the chart is installed with defaults and the console is run with its dev script
- **THEN** the registered redirect URI's path (`/callback`) and port match a route the console
  actually serves, so the redirect back from Dex is handled rather than 404'ing

### Requirement: Login is additive and does not gate existing behavior

Adding login SHALL NOT change any existing console read behavior or route. Every route reachable
before this change SHALL remain reachable, and unauthenticated reads against `dws-admin` SHALL
continue to work whether or not an operator is signed in or the IdP is reachable.

Owning component: `dws-console`.

#### Scenario: Existing routes render without signing in
- **WHEN** an operator uses the console without signing in
- **THEN** the existing workflow/instance read routes render and load data exactly as before this
  change

#### Scenario: IdP unreachable does not break the app
- **WHEN** Dex is unreachable or OIDC is misconfigured
- **THEN** the console still renders and its existing read routes still work; only sign-in is
  unavailable
