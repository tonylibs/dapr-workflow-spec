## ADDED Requirements

### Requirement: Read queries are bearer-authenticated and auth-aware

Every `dws-console` TanStack Query function that calls a `dws-admin` JSON read endpoint SHALL use
the centralized authenticated admin transport and attach the current bearer token. Queries SHALL
remain disabled until OIDC initialization completes with a signed-in user. A 401 response MUST be
treated as an authentication/session outcome and SHALL NOT use the normal transport/server retry
policy. Owning component: `dws-console`.

#### Scenario: Workflow list sends token
- **WHEN** a signed-in operator opens the workflow list
- **THEN** `GET /workflows` includes the current bearer token

#### Scenario: Signed-out query remains idle
- **WHEN** the workflow or instance route renders while the user is signed out
- **THEN** its admin query sends no request and the route offers sign-in rather than a transport
  error

#### Scenario: Unauthorized read is not retried
- **WHEN** an admin read returns 401
- **THEN** the query reports an authentication outcome without exponential transport retries

