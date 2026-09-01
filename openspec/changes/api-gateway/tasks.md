## 1. Consolidate dws-admin on one Dapr app port

- [ ] 1.1 Add request-level tests for `GET /dapr/subscribe` and the advertised event callback,
  covering configured pubsub/topic values, transport CloudEvent `data` unwrapping, valid event
  processing, deliberate malformed/unknown-event acknowledgement, and unexpected failure retry.
- [ ] 1.2 Refactor `DwsEventsSubscriber` into a decorator-free injectable event processor and add
  a thin Nest controller/module that implements Dapr's programmatic subscription HTTP contract on
  port 3000.
- [ ] 1.3 Remove `@dbc-tech/nest-dapr`, the wrapper `src/dapr/` module, `DAPR_APP_PORT`, port 3001,
  and obsolete dual-listener comments/config from package files, `.env.example`, Dockerfile, and
  README; update the pnpm lockfile.
- [ ] 1.4 Remove `dws-admin` browser CORS bootstrap/config/tests and document that the public
  console/admin path is same-origin while local development uses the console proxy.
- [ ] 1.5 Run focused event/subscription tests, then `pnpm lint`, `pnpm test`, and `pnpm build` from
  `dws-admin`.

## 2. Authenticate every dws-console admin transport

- [ ] 2.1 Add admin-client tests proving every JSON read and definition write acquires and sends
  the current OIDC bearer token, uses a renewed token on later calls, and never includes a token in
  URLs, query keys, logs, or browser persistence.
- [ ] 2.2 Centralize async token acquisition and authenticated fetch behavior in the OIDC/admin
  client boundary; migrate all read hooks and definition submission to that boundary without
  changing raw-body or response parsing contracts.
- [ ] 2.3 Add tests for signed-out/initializing/expired auth states, disable TanStack Query requests
  until sign-in, and treat token failures/401 responses as authentication outcomes with no normal
  transport retries.
- [ ] 2.4 Add tests for bearer-authenticated SSE parsing, named events, cancellation, terminal-state
  closure, token reacquisition, reconnect/resync, and 401 degradation; implement a fetch-based SSE
  transport and remove native `EventSource` use for admin streams.
- [ ] 2.5 Update console dev-proxy/auth documentation and any selected streaming dependency/lockfile,
  then run `pnpm lint`, `pnpm test`, `pnpm typecheck`, and `pnpm build` from `dws-console`.

## 3. Add the optional APISIX dependency and preflight contract

- [ ] 3.1 Pin the official APISIX chart dependency in `charts/dws/Chart.yaml` with
  `condition: apisix.enabled`; configure its data plane and ingress-controller child for Gateway
  API support using release-safe names and Secret references.
- [ ] 3.2 Run Helm dependency update/build, check in the matching `Chart.lock` entry and vendored
  APISIX archive, and add a dependency-consistency assertion to the chart test surface.
- [ ] 3.3 Add documented `apisix.*` and `apiGateway.*` values for bundled/external mode, listener
  hostname/TLS, GatewayClass ownership/reference, APISIX GatewayProxy binding, and APISIX Service
  exposure; extend values-schema coverage.
- [ ] 3.4 Extend `_preflight.tpl`/`preflight.yaml` with external-mode Gateway API v1 and APISIX CRD
  checks; add template tests for each missing API, all APIs present, and bundled first-install skip.

## 4. Render the shared Gateway API topology

- [ ] 4.1 Add release-and-namespace-qualified helpers for the cluster-scoped GatewayClass plus
  release-qualified helpers and validation for Gateway, GatewayProxy,
  console/admin HTTPRoutes, required `auth/admin/console` flags, and bundled-versus-external
  references.
- [ ] 4.2 Add the APISIX `GatewayClass` and GatewayProxy binding templates, plus a shared Gateway
  listener supporting configured hostname and optional TLS Secret references.
- [ ] 4.3 Add the admin HTTPRoute matching `/dws-admin`, using the pinned APISIX-supported Gateway
  API v1 URLRewrite form to produce the Dapr invoke prefix and targeting the sidecar-backed admin
  Service.
- [ ] 4.4 Add the console root HTTPRoute on the same listener, targeting the console Service, and
  assert route attachment/path precedence for `/dws-admin/*` versus console routes.
- [ ] 4.5 Add render assertions for method/body/header/query transparency, no SSE buffering filter,
  TLS/hostname wiring, release-qualified references, and zero Gateway objects when disabled.

## 5. Align the admin Deployment, Service, and bearer gate

- [ ] 5.1 Change the admin pod annotation and container contract to app-port 3000; remove container
  port 3001 and `DAPR_APP_PORT` while preserving pubsub name/topic and pod-local health probes.
- [ ] 5.2 In secured API Gateway mode, render exactly one admin Service port targeting daprd 3500
  and no app-port bypass; preserve the documented gateway-disabled migration topology.
- [ ] 5.3 Update chart tests/spec comments for bearer enforcement across admin reads, writes, docs,
  and SSE while confirming Dapr subscription discovery/callbacks still reach Nest internally.
- [ ] 5.4 Render the admin Deployment/Service/auth resources in default, auth-only, and gateway
  modes and assert the exact port/annotation/Configuration matrix.

## 6. Remove superseded front doors and publish migration guidance

- [ ] 6.1 Delete `charts/dws/templates/admin-gateway/`, its nginx/CORS regression test,
  `adminGateway.*` values/helpers/labels, and any nginx-specific documentation.
- [ ] 6.2 Delete `charts/dws/templates/console/ingress.yaml`; retain a deprecated
  `console.ingress.enabled` validation trap that fails upgrades with explicit host/TLS/OIDC/APISIX
  migration steps instead of silently dropping the route.
- [ ] 6.3 Update chart NOTES/values comments, component READMEs, generated-source documentation
  inputs, and `docs/roadmaps/dws-auth.md` to describe the shared Gateway, sidecar-only admin path,
  one-listener resolution, rollback ordering, and deferred live SSE verification.
- [ ] 6.4 Confirm no file under `openspec/changes/archive/` changed and no nginx admin-gateway or
  console Ingress resource appears in any migrated render.

## 7. Run end-to-end local gates and capture deferred live work

- [ ] 7.1 Run `helm lint` and `helm template` with defaults (`apisix.enabled=false`, gateway off)
  and assert the existing core chart remains valid with no Gateway/APISIX/legacy front-door objects.
- [ ] 7.2 Run bundled mode gates with `apiGateway.enabled=true`, `apisix.enabled=true`, auth/admin/
  console enabled and valid issuer/audience; assert APISIX, Gateway API routes, sidecar-only admin
  Service, and zero legacy objects.
- [ ] 7.3 Run external mode gates with `apiGateway.enabled=true`, `apisix.enabled=false` and explicit
  Helm API capabilities; assert DWS routes render without APISIX workloads and negative preflight
  cases fail with actionable errors.
- [ ] 7.4 Run the chart's shell/schema regression tests and all changed component CI gates; address
  any dependency, lint, test, typecheck, or build findings.
- [ ] 7.5 Run `openspec validate --change api-gateway --strict` and create `verify.md` with command
  evidence, the valid/invalid bearer live-matrix recipe, and SSE-over-Gateway/Dapr explicitly
  marked as later verification rather than falsely claimed complete.
