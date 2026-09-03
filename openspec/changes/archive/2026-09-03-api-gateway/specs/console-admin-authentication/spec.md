## ADDED Requirements

### Requirement: Every console admin request uses the current in-memory access token

`dws-console` SHALL acquire the current access token from the OIDC client at the centralized admin
transport boundary and attach `Authorization: Bearer <token>` to every JSON read, definition
write, and SSE connection to `dws-admin`. Callers SHALL NOT accept or cache a long-lived token
copy outside that boundary. Owning component: `dws-console`.

#### Scenario: Authenticated read carries bearer token
- **WHEN** a signed-in operator loads a route that fetches admin JSON
- **THEN** the outgoing request includes the current OIDC access token in its Authorization header

#### Scenario: Renewed token is used on a later request
- **WHEN** silent renewal changes the access token between two admin calls
- **THEN** the second call acquires and sends the renewed token rather than a cached old token

#### Scenario: Definition submission remains authenticated
- **WHEN** a signed-in operator submits a workflow definition
- **THEN** the centralized transport sends the raw definition with the current bearer token and
  preserves the existing submission response contract

### Requirement: Access tokens are not persisted or exposed through UI state

The console MUST keep access tokens inside the OIDC client's in-memory storage. Admin transport
code SHALL NOT place tokens in localStorage, sessionStorage, cookies, URLs, query keys, React auth
state, error text, or application logs. Owning component: `dws-console`.

#### Scenario: Browser persistence remains token-free
- **WHEN** authenticated reads, writes, and streams have been used
- **THEN** browser localStorage, sessionStorage, cookies, and rendered URLs contain no access token

#### Scenario: Query cache identity contains no credential
- **WHEN** TanStack Query caches an authenticated admin response
- **THEN** its query key and cached metadata do not contain the bearer token

### Requirement: Signed-out and expired sessions do not issue anonymous admin calls

Admin queries and streams SHALL remain disabled while OIDC is initializing, unavailable, or
signed out. If token acquisition fails or the gateway returns 401, the console MUST surface a
sign-in/session-expired outcome and SHALL NOT repeatedly retry the request as a transport failure.
Owning component: `dws-console`.

#### Scenario: Signed-out route does not fetch admin data
- **WHEN** an operator visits an admin-backed console route while signed out
- **THEN** no admin request is sent and the route presents a sign-in outcome

#### Scenario: Expired session is not retried as server failure
- **WHEN** token acquisition fails or an admin request returns 401
- **THEN** the console reports that authentication is required and does not perform normal
  transport retries

