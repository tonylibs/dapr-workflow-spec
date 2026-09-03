# Shared APISIX Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the nginx admin gateway and console Ingress with one optional APISIX-backed Gateway API front door after consolidating `dws-admin` on one bearer-gated Dapr app port.

**Architecture:** Nest serves API, SSE, subscription discovery, and pub/sub delivery on port 3000; the admin Kubernetes Service points at daprd 3500 in gateway mode. A shared Gateway sends `/dws-admin/*` through Dapr's invoke URL and all other paths to the console, while the console attaches its in-memory OIDC token to JSON and fetch-based SSE requests.

**Tech Stack:** Node 24, NestJS 11, Jest, React 19, TanStack Query/Start, Vitest, browser Fetch/ReadableStream, Dapr HTTP pub/sub/service invocation, Helm 3, Kubernetes Gateway API v1, Apache APISIX Helm chart 2.16.0 (APISIX 3.17.0).

**Spec:** `openspec/changes/api-gateway/design.md`, `openspec/changes/api-gateway/specs/`

## Global Constraints

- Do not modify any file under `openspec/changes/archive/`.
- Pin the APISIX dependency to chart `2.16.0`; update `Chart.lock` and vendor the matching archive.
- Keep `apisix.enabled=false` and `apiGateway.enabled=false` by default.
- `apiGateway.enabled=true` requires `auth.enabled=true`, `admin.enabled=true`, and `console.enabled=true`.
- Keep Dapr as the only JWT verifier; do not add token validation to APISIX or Nest.
- Keep access tokens only in the OIDC client's memory; never place them in URLs, storage, cookies, query keys, logs, or React auth state.
- Preserve `/dws-admin` as the console's default admin prefix and preserve all admin endpoint request/response contracts.
- Preserve workflow DSL behavior, content-addressed versions, controller routing, and step-service contracts.
- Treat live SSE-over-APISIX/Dapr as deferred verification; do not claim live success without evidence.
- Run commands from `dws-admin`, `dws-console`, or `charts/dws` as appropriate; there is no root build.

## File Structure

- `dws-admin/src/events/dapr-subscription.controller.ts`: Dapr discovery/delivery HTTP adapter only.
- `dws-admin/src/events/dws-events.subscriber.ts`: decorator-free lifecycle event processor.
- `dws-admin/src/events/dapr-subscription.controller.spec.ts`: request/acknowledgement contract tests.
- `dws-console/src/lib/admin-client.ts`: centralized authenticated JSON and SSE transport.
- `dws-console/src/lib/oidc.ts`: the sole function that obtains the current access token.
- `charts/dws/templates/api-gateway/*.yaml`: GatewayClass, GatewayProxy, Gateway, and two HTTPRoutes.
- `charts/dws/templates/_preflight.tpl`: external APISIX/Gateway API capability checks.
- `charts/dws/tests/api-gateway-render-test.sh`: bundled/external/negative render matrix.

---

### Task 1: Put Dapr subscription discovery and delivery on Nest port 3000

**Files:**
- Create: `dws-admin/src/events/dapr-subscription.controller.ts`
- Create: `dws-admin/src/events/dapr-subscription.controller.spec.ts`
- Modify: `dws-admin/src/events/dws-events.subscriber.ts`
- Modify: `dws-admin/src/events/dapr-events.module.ts`
- Modify: `dws-admin/src/instances/instances-sse.integration.spec.ts`
- Test: `dws-admin/src/events/dapr-subscription.controller.spec.ts`

**Interfaces:**
- Consumes: `DwsEventsSubscriber.onMessage(message: CloudEventV1<unknown> | string): Promise<void>` behavior and configured `dapr.pubsubName`/`dapr.topic`.
- Produces: `DaprSubscriptionController.listSubscriptions(): Subscription[]` and `deliver(transport: DaprTransportEvent): Promise<{status: 'SUCCESS'}>` on Nest port 3000.

- [x] **Step 1: Remove the decorator from the domain processor and write the failing discovery test**

  Rename `onMessage` to `process`, update the SSE integration test caller, and remove the
  `@DaprPubSub` import/decorator. In the new spec, construct a Nest testing module with a mocked subscriber and assert:

  ```ts
  await request(app.getHttpServer())
    .get('/dapr/subscribe')
    .expect(200)
    .expect([
      {
        pubsubname: 'pubsub',
        topic: 'dws.events',
        routes: { default: '/dapr/events/dws' },
      },
    ]);
  ```

- [x] **Step 2: Run the focused spec and verify the route is missing**

  Run: `pnpm test -- dapr-subscription.controller.spec.ts`

  Expected: FAIL with 404 for `/dapr/subscribe` or missing controller/module symbols.

- [x] **Step 3: Implement typed discovery and delivery routes**

  Add narrow transport/subscription types and a thin controller:

  ```ts
  interface DaprTransportEvent {
    data?: unknown;
  }

  @Controller('dapr')
  export class DaprSubscriptionController {
    constructor(
      private readonly config: ConfigService<AppConfig, true>,
      private readonly subscriber: DwsEventsSubscriber,
    ) {}

    @Get('subscribe')
    listSubscriptions() {
      const dapr = this.config.get('dapr', { infer: true });
      return [{
        pubsubname: dapr.pubsubName,
        topic: dapr.topic,
        routes: { default: '/dapr/events/dws' },
      }];
    }

    @Post('events/dws')
    async deliver(@Body() transport: DaprTransportEvent) {
      await this.subscriber.process(transport.data);
      return { status: 'SUCCESS' as const };
    }
  }
  ```

  Register the controller in `DaprEventsModule`; keep all database/idempotency work in
  `DwsEventsSubscriber`.

- [x] **Step 4: Add acknowledgement tests and make them pass**

  Cover a valid transport envelope, an unknown inner type, a malformed inner event (the existing
  processor logs/drops it), and a mocked database failure. Assert the first three return 201/2xx
  with `SUCCESS`; assert the unexpected rejection produces 500 so Dapr retries.

  Run: `pnpm test -- dapr-subscription.controller.spec.ts event-envelope.spec.ts idempotent-handler.spec.ts`

  Expected: PASS.

- [ ] **Step 5: Commit the independently working callback adapter**

  ```bash
  git add dws-admin/src/events
  git commit -m "refactor: serve Dapr events through Nest"
  ```

### Task 2: Remove dws-admin's second server and redundant CORS surface

**Files:**
- Delete: `dws-admin/src/dapr/dapr.module.ts`
- Delete: `dws-admin/src/config/cors.ts`
- Delete: `dws-admin/src/config/cors.spec.ts`
- Modify: `dws-admin/src/events/dapr-events.module.ts`
- Modify: `dws-admin/src/config/configuration.ts`
- Modify: `dws-admin/src/main.ts`
- Modify: `dws-admin/src/app.module.ts`
- Modify: `dws-admin/package.json`
- Modify: `dws-admin/pnpm-lock.yaml`
- Modify: `dws-admin/.env.example`
- Modify: `dws-admin/Dockerfile`
- Modify: `dws-admin/README.md`

**Interfaces:**
- Consumes: Task 1's Nest routes.
- Produces: one process listener at `PORT=3000`; no `DAPR_APP_PORT` or port 3001 contract.

- [x] **Step 1: Write/adjust configuration tests to reject the old shape**

  Update test fixtures so `AppConfig.dapr` contains `pubsubName`, `topic`, Dapr sidecar host/port,
  and controller app-id, but no `serverHost` or `appPort`. Add a source assertion that bootstrap
  contains no `enableCors` call.

- [x] **Step 2: Run the focused tests and verify old imports/config fail**

  Run: `pnpm test -- configuration cors controller-relay event`

  Expected: FAIL until `DaprModule`, CORS, and dual-port fields are removed.

- [x] **Step 3: Remove the SDK server and CORS code minimally**

  Remove `@dbc-tech/nest-dapr`; remove `DaprModule` imports; remove `corsOrigins`, `serverHost`, and
  `appPort` from `AppConfig`; remove `app.enableCors(...)`; keep `@dapr/dapr` only if a remaining
  runtime import exists after `rg -n "@dapr/dapr" src` (otherwise remove it too). Run `pnpm install`
  to update the lockfile.

- [x] **Step 4: Update runtime packaging and documentation**

  Make `Dockerfile` expose only 3000, delete `DAPR_APP_PORT=3001` from `.env.example`, change the
  local command to `dapr run --app-id dws-admin --app-port 3000 ...`, and document same-origin
  Gateway/local Vite proxy behavior instead of browser CORS.

- [x] **Step 5: Run the full dws-admin gate and commit**

  Run: `pnpm lint && pnpm test && pnpm build`

  Expected: all pass and `rg -n "3001|nest-dapr|DAPR_APP_PORT|enableCors" src README.md .env.example Dockerfile package.json` returns no live contract references.

  ```bash
  git add dws-admin
  git commit -m "refactor: consolidate admin on one listener"
  ```

  Ran `pnpm lint && pnpm test && pnpm build` (all pass) and the `rg` check above (only the
  intentional negative-assertion strings in `configuration.spec.ts` remain). Commit intentionally
  not run — the task runner for this change did not perform git commits.

### Task 3: Centralize authenticated admin JSON requests in dws-console

**Files:**
- Modify: `dws-console/src/lib/oidc.ts`
- Modify: `dws-console/src/lib/oidc-config.ts`
- Modify: `dws-console/src/lib/admin-client.ts`
- Modify: `dws-console/src/lib/admin-client.test.ts`
- Modify: `dws-console/src/lib/admin-hooks.ts`
- Modify: `dws-console/src/routes/workflows/new.tsx`
- Test: `dws-console/src/lib/admin-client.test.ts`

**Interfaces:**
- Produces: `getAccessToken(): Promise<string>` and `adminFetch(path, init?, signal?): Promise<Response>`.
- Consumes: `getOidc({ assert: 'user logged in' })` from `oidc-spa`.

- [x] **Step 1: Write failing token-acquisition and request-header tests**

  Mock `#/lib/oidc` and assert two sequential reads call `getAccessToken` twice and send the two
  returned tokens:

  ```ts
  vi.mock('#/lib/oidc', () => ({
    getAccessToken: vi.fn()
      .mockResolvedValueOnce('token-1')
      .mockResolvedValueOnce('token-2'),
  }));
  ```

  Also assert `submitDefinition` no longer accepts a token argument and still preserves YAML bytes.

- [x] **Step 2: Run the client test and verify reads are anonymous**

  Run: `pnpm vitest run src/lib/admin-client.test.ts`

  Expected: FAIL because GET headers contain only `Accept` and submission still accepts a caller token.

- [x] **Step 3: Implement the OIDC boundary and authenticated fetch**

  In `oidc.ts` export:

  ```ts
  export async function getAccessToken(): Promise<string> {
    const authenticated = await getOidc({ assert: 'user logged in' });
    return authenticated.getAccessToken();
  }
  ```

  In `admin-client.ts`, make every operation use:

  ```ts
  async function adminFetch(path: string, init: RequestInit = {}): Promise<Response> {
    const token = await getAccessToken();
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return fetch(adminUrl(path), { ...init, headers });
  }
  ```

  Preserve caller `Accept`/`Content-Type`, raw bodies, abort signals, and response parsing.

- [x] **Step 4: Gate TanStack queries on signed-in state and handle 401**

  Add `useOidc()` to the hooks boundary, pass `enabled: oidc.isUserLoggedIn === true` to every admin
  query, and change `retryUnlessClientError` so every 4xx including 401 returns false. Routes use
  the existing auth banner/layout to show sign-in/session expiry; do not add tokens to query keys.

- [x] **Step 5: Run focused tests and commit**

  Run: `pnpm vitest run src/lib/admin-client.test.ts src/lib/oidc-config.test.ts`

  Expected: PASS, including renewed-token and no-token-in-URL assertions.

  ```bash
  git add dws-console/src/lib dws-console/src/routes/workflows/new.tsx
  git commit -m "feat: authenticate console admin requests"
  ```

### Task 4: Replace native EventSource with authenticated fetch streaming

**Files:**
- Modify: `dws-console/src/lib/admin-client.ts`
- Modify: `dws-console/src/lib/admin-client.test.ts`
- Modify: `dws-console/src/lib/admin-hooks.ts`
- Test: `dws-console/src/lib/admin-client.test.ts`

**Interfaces:**
- Consumes: Task 3's `getAccessToken()` and `adminUrl()`.
- Produces: unchanged `LiveSubscription { close(): void }`, backed by Fetch/ReadableStream.

- [x] **Step 1: Add a failing SSE stream fixture test**

  Stub fetch with a `ReadableStream<Uint8Array>` that emits:

  ```text
  event: instance
  data: {"instanceId":"i-1","status":"completed"}

  event: task
  data: {"id":"t-1","instanceId":"i-1"}

  ```

  Assert the request has `Accept: text/event-stream` and `Authorization: Bearer token-1`, named
  callbacks receive parsed JSON, and `close()` aborts the request.

- [x] **Step 2: Run the focused test and verify EventSource cannot satisfy it**

  Run: `pnpm vitest run src/lib/admin-client.test.ts`

  Expected: FAIL because the existing code constructs `EventSource` and cannot set headers.

- [x] **Step 3: Implement a focused fetch SSE parser**

  Use `TextDecoderStream` when available and a small line-buffer parser that accumulates `event:`
  and multi-line `data:` fields until a blank line, then dispatches JSON to the existing listener
  map. Back each connection with `AbortController`; merge an external abort signal if supplied.
  Treat HTTP 401 as terminal, retry network/5xx closure with bounded backoff, and call `onOpen` on
  each successful response before reading frames.

- [x] **Step 4: Add reconnect, renewal, and terminal-close tests**

  Make the first fetch reject after one event, the second use `token-2`, and assert `onOpen` fires
  twice so existing hooks invalidate queries on reconnect. Assert terminal instance handling calls
  `close()` and prevents a third fetch. Assert 401 causes no anonymous retry.

- [x] **Step 5: Run console gates and commit**

  Run: `pnpm lint && pnpm test && pnpm typecheck && pnpm build`

  Expected: all pass; `rg -n "new EventSource" src` returns no admin-stream usage.

  ```bash
  git add dws-console
  git commit -m "feat: authenticate console SSE streams"
  ```

### Task 5: Add and verify the optional APISIX dependency

**Files:**
- Modify: `charts/dws/Chart.yaml`
- Modify: `charts/dws/Chart.lock`
- Create: `charts/dws/charts/apisix-2.16.0.tgz`
- Modify: `charts/dws/values.yaml`
- Modify: `charts/dws/templates/_preflight.tpl`
- Modify: `charts/dws/templates/preflight.yaml`
- Modify: `charts/dws/tests/values-schema-test.sh`
- Create: `charts/dws/tests/api-gateway-render-test.sh`

**Interfaces:**
- Produces: `apisix.enabled`, bundled chart 2.16.0, and `dws.preflight.apiGateway`.
- Consumes: Helm `.Capabilities.APIVersions` and Task 6's `apiGateway.enabled` value shape.

- [x] **Step 1: Add failing dependency and preflight assertions**

  Extend shell tests to require `Chart.yaml` contains:

  ```yaml
  - name: apisix
    version: 2.16.0
    repository: https://apache.github.io/apisix-helm-chart
    condition: apisix.enabled
  ```

  Add negative renders for missing `gateway.networking.k8s.io/v1` and
  `apisix.apache.org/v1alpha1` when gateway is enabled with external APISIX.

- [x] **Step 2: Run tests and verify the dependency/value is absent**

  Run from `charts/dws`: `bash tests/values-schema-test.sh`

  Expected: FAIL on missing APISIX fields/preflight helper.

- [x] **Step 3: Add dependency, defaults, and bundled child configuration**

  Add the dependency and default:

  ```yaml
  apisix:
    enabled: false
    service:
      type: ClusterIP
    ingress-controller:
      enabled: true
      config:
        disableGatewayAPI: false
        listenerPortMatchMode: auto
      gatewayProxy:
        createDefault: false
  ```

  Keep upstream values overridable. Run `helm dependency update .` and verify the lock/archive
  version and digest before staging them.

- [x] **Step 4: Implement external-only preflight**

  Define `dws.preflight.apiGateway` so it checks both API groups only when
  `and .Values.apiGateway.enabled (not .Values.apisix.enabled)`. Include explicit remediation in
  each `fail` message. Include it from `preflight.yaml` beside the Dapr check.

- [x] **Step 5: Verify default, bundled, and external dependency modes and commit**

  Run:

  ```bash
  helm dependency list .
  helm lint .
  helm template dws . --set apisix.enabled=true
  helm template dws . --set apiGateway.enabled=true --set apisix.enabled=false \
    --api-versions gateway.networking.k8s.io/v1 --api-versions apisix.apache.org/v1alpha1
  ```

  Expected: dependency list is synced; default lint passes; bundled renders APISIX; external emits
  no APISIX workloads and reaches later gateway validation instead of CRD preflight failure.

  ```bash
  git add charts/dws/Chart.yaml charts/dws/Chart.lock charts/dws/charts charts/dws/values.yaml charts/dws/templates/_preflight.tpl charts/dws/templates/preflight.yaml charts/dws/tests
  git commit -m "feat: add optional APISIX dependency"
  ```

### Task 6: Define API Gateway values, helpers, and validation

**Files:**
- Modify: `charts/dws/values.yaml`
- Modify: `charts/dws/templates/_helpers.tpl`
- Modify: `charts/dws/tests/values-schema-test.sh`
- Modify: `charts/dws/tests/api-gateway-render-test.sh`

**Interfaces:**
- Produces: helpers `dws.apiGateway.className`, `dws.apiGateway.gatewayName`,
  `dws.apiGateway.gatewayProxyName`, and `dws.apisix.fullname`.
- Consumes: `apiGateway.enabled`, `createGatewayClass`, `gatewayClassName`, `hostname`, `tls`, and
  `external.gatewayProxyName` values.

- [x] **Step 1: Write failing value/validation cases**

  Add cases that reject gateway mode when auth/admin/console is disabled, reject external mode
  without an existing GatewayProxy name, and reject `console.ingress.enabled=true` with a message
  naming `apiGateway.enabled`, APISIX, hostname/TLS, and OIDC redirect migration.

- [x] **Step 2: Add the explicit value contract**

  Add:

  ```yaml
  apiGateway:
    enabled: false
    createGatewayClass: true
    gatewayClassName: ""
    controllerName: apisix.apache.org/apisix-ingress-controller
    hostname: ""
    tls:
      enabled: false
      certificateName: ""
    external:
      gatewayProxyName: ""
  ```

  Document that `gatewayClassName` defaults to a release-qualified class when creation is enabled,
  while external operators can set an existing class/proxy.

- [x] **Step 3: Implement release-safe helpers**

  Mirror APISIX 2.16.0's fullname algorithm so the parent can reference its admin Service:

  ```gotemplate
  {{- define "dws.apisix.fullname" -}}
  {{- if .Values.apisix.fullnameOverride -}}
  {{- .Values.apisix.fullnameOverride | trunc 63 | trimSuffix "-" -}}
  {{- else -}}
  {{- $name := default "apisix" .Values.apisix.nameOverride -}}
  {{- if contains $name .Release.Name -}}{{ .Release.Name | trunc 63 | trimSuffix "-" }}
  {{- else -}}{{ printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}{{- end -}}
  {{- end -}}
  {{- end -}}
  ```

  Add a cluster-scoped class helper based on release name plus release namespace, release-qualified
  namespaced Gateway helpers, and a validation helper invoked unconditionally by the gateway
  templates/preflight surface.

- [x] **Step 4: Run value validation tests and commit**

  Run: `bash tests/values-schema-test.sh`

  Expected: all positive shapes render and every invalid combination fails with the asserted text.

  ```bash
  git add charts/dws/values.yaml charts/dws/templates/_helpers.tpl charts/dws/tests
  git commit -m "feat: define shared gateway values"
  ```

### Task 7: Render GatewayClass, GatewayProxy, Gateway, and HTTPRoutes

**Files:**
- Create: `charts/dws/templates/api-gateway/gatewayclass.yaml`
- Create: `charts/dws/templates/api-gateway/gatewayproxy.yaml`
- Create: `charts/dws/templates/api-gateway/gateway.yaml`
- Create: `charts/dws/templates/api-gateway/admin-httproute.yaml`
- Create: `charts/dws/templates/api-gateway/console-httproute.yaml`
- Modify: `charts/dws/tests/api-gateway-render-test.sh`

**Interfaces:**
- Consumes: Task 6 helpers and existing `dws.admin.fullname`/`dws.console.fullname`.
- Produces: one shared listener and `/dws-admin`/`/` routes.

- [x] **Step 1: Write failing structural render assertions**

  Render a valid bundled configuration and assert one GatewayClass, GatewayProxy, Gateway, and two
  HTTPRoutes; assert both routes reference the same Gateway and no resource renders when disabled.

- [x] **Step 2: Add GatewayClass and bundled GatewayProxy**

  The class uses `spec.controllerName` from values. In bundled mode, create a namespaced
  `apisix.apache.org/v1alpha1 GatewayProxy` whose ControlPlane Service is
  `{{ include "dws.apisix.fullname" . }}-admin:9180` and whose admin key matches the configured
  APISIX admin credential. In external mode, create no proxy and reference
  `apiGateway.external.gatewayProxyName`.

- [x] **Step 3: Add the shared Gateway listener**

  Render `gateway.networking.k8s.io/v1`, use the resolved class name, and set
  `infrastructure.parametersRef` to the resolved GatewayProxy. Render HTTP port 80 by default; if
  TLS is enabled, render HTTPS port 443, `mode: Terminate`, and the configured Secret reference.
  Apply `hostname` only when non-empty.

- [x] **Step 4: Add exact admin and console HTTPRoutes**

  Admin route rule:

  ```yaml
  matches:
    - path: { type: PathPrefix, value: /dws-admin }
  filters:
    - type: URLRewrite
      urlRewrite:
        path:
          type: ReplacePrefixMatch
          replacePrefixMatch: /v1.0/invoke/<admin-fullname>/method
  backendRefs:
    - name: <admin-fullname>
      port: <admin-service-port>
  ```

  Console route matches `/` and targets the console Service. Use the same parentRef section,
  namespace, and hostname on both.

- [x] **Step 5: Run render assertions and commit**

  Run: `bash tests/api-gateway-render-test.sh`

  Expected: bundled/external shapes, rewrite/backend, TLS, route precedence, and disabled-negative
  assertions all pass.

  ```bash
  git add charts/dws/templates/api-gateway charts/dws/tests/api-gateway-render-test.sh
  git commit -m "feat: route console and admin through Gateway API"
  ```

### Task 8: Make gateway-mode admin Service sidecar-only and remove legacy paths

**Files:**
- Modify: `charts/dws/templates/admin/deployment.yaml`
- Modify: `charts/dws/templates/admin/service.yaml`
- Delete: `charts/dws/templates/admin-gateway/configmap.yaml`
- Delete: `charts/dws/templates/admin-gateway/deployment.yaml`
- Delete: `charts/dws/templates/admin-gateway/service.yaml`
- Delete: `charts/dws/templates/console/ingress.yaml`
- Delete: `charts/dws/tests/admin-gateway-cors-test.sh`
- Modify: `charts/dws/templates/_helpers.tpl`
- Modify: `charts/dws/values.yaml`
- Modify: `charts/dws/tests/api-gateway-render-test.sh`

**Interfaces:**
- Consumes: Task 1's one-listener app and Task 7's admin backendRef.
- Produces: `dapr.io/app-port: "3000"` and sidecar-only Service target 3500 in gateway mode.

- [x] **Step 1: Add failing topology and legacy-removal assertions**

  Assert gateway mode has no container port 3001, no `DAPR_APP_PORT`, exactly one admin Service
  port targeting 3500, no targetPort 3000, no nginx image/ConfigMap/Deployment, and no Ingress.

- [x] **Step 2: Update Deployment and Service**

  Set `dapr.io/app-port: "3000"`; remove `dapr-app-port`/3001 and `DAPR_APP_PORT`. In gateway mode,
  make the existing Service's single port target 3500. Outside gateway mode, keep its existing
  `targetPort: http` migration behavior. Keep liveness/readiness on the container's `http` port.

- [x] **Step 3: Delete legacy templates and values**

  Delete `templates/admin-gateway/`, `adminGateway.*`, related helpers/comments/tests, and the
  console Ingress template. Keep only the `console.ingress.enabled` deprecation trap/values needed
  to catch old persisted Helm values; remove class/annotation rendering logic.

- [x] **Step 4: Run chart regression tests and commit**

  Run:

  ```bash
  bash tests/values-schema-test.sh
  bash tests/api-gateway-render-test.sh
  helm lint .
  ```

  Expected: all pass; `rg -n "adminGateway|admin-gateway|kind: Ingress|3001|DAPR_APP_PORT" templates values.yaml tests` finds only intentional migration-message text, if any.

  ```bash
  git add charts/dws
  git commit -m "refactor: remove legacy chart front doors"
  ```

### Task 9: Document migration and run the complete verification matrix

**Files:**
- Modify: `charts/dws/templates/NOTES.txt`
- Modify: `charts/dws/values.yaml`
- Modify: `dws-admin/README.md`
- Modify: `dws-console/Dockerfile`
- Modify: `dws-console/vite.config.ts`
- Modify: `docs/roadmaps/dws-auth.md`
- Create: `openspec/changes/api-gateway/verify.md` during verification

**Interfaces:**
- Consumes: all prior tasks.
- Produces: operator migration/rollback instructions and evidence for every non-live acceptance gate.

- [x] **Step 1: Write exact upgrade and rollback guidance**

  Document the mapping `console.ingress.host` → `apiGateway.hostname`, old TLS Secret →
  `apiGateway.tls.certificateName`, removal of Ingress class/annotations, bundled versus external
  APISIX choice, required auth/admin/console flags, shared-origin Dex redirect URI, and coordinated
  rollback to the prior chart/admin/console images.

- [x] **Step 2: Update component and roadmap source documentation**

  Describe Nest port 3000 as the sole Dapr app port, `/dws-admin` as the shared public prefix, all
  admin traffic as bearer-authenticated, and SSE live proof as deferred. Do not hand-edit generated
  OpenWiki pages.

- [x] **Step 3: Run component gates**

  Run:

  ```bash
  cd dws-admin && pnpm lint && pnpm test && pnpm build
  cd ../dws-console && pnpm lint && pnpm test && pnpm typecheck && pnpm build
  ```

  Expected: all commands pass.

- [x] **Step 4: Run all chart modes**

  From `charts/dws`, run default lint/template, bundled gateway mode with valid auth values, and
  external mode with both API versions supplied. Run both shell test scripts. Confirm bundled mode
  renders APISIX/controller/Gateway resources, external mode renders routes but no APISIX workloads,
  and defaults render neither.

- [x] **Step 5: Validate OpenSpec and audit the archive boundary**

  Run from repository root:

  ```bash
  openspec validate api-gateway --type change --strict --no-interactive
  git diff --name-only -- openspec/changes/archive
  ```

  Expected: strict validation passes and the archive diff is empty.

- [x] **Step 6: Capture evidence without overstating SSE**

  Write `verify.md` with exact commands/results, static and request-level coverage, APISIX/Gateway
  render evidence, and a later live recipe covering valid/invalid tokens, reads/writes, event
  ingestion, cleanup, and SSE. Mark SSE-over-APISIX/Dapr as unverified until that recipe is run.

- [ ] **Step 7: Commit documentation and verification evidence**

  ```bash
  git add charts/dws/templates/NOTES.txt charts/dws/values.yaml dws-admin/README.md dws-console/Dockerfile dws-console/vite.config.ts docs/roadmaps/dws-auth.md openspec/changes/api-gateway
  git commit -m "docs: finalize API gateway migration"
  ```

## Plan Self-Review

- Spec coverage: all nine delta specs map to Tasks 1-9; nginx removal and Ingress migration are
  explicit in Task 8/9, and bundled/external APISIX matrices are explicit in Tasks 5-7/9.
- Placeholder scan: no implementation placeholder remains; the only deferred item is the
  intentionally non-blocking live SSE acceptance run required by the design.
- Type consistency: `getAccessToken`, `adminFetch`, `LiveSubscription`, Gateway helper names, and
  `/dapr/events/dws` are defined before later tasks consume them.
