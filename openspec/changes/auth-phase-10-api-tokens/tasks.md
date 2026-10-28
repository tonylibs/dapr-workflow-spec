# Tasks

Live steps (groups 1 and 6) need a real cluster with Dapr and, for the gateway, APISIX. They run
on the owner's local cluster, not in CI or in the authoring session. Do not tick them from a
render-only check.

## 1. Spike: can APISIX inject a Secret-backed header? (gates group 4)

- [ ] 1.1 On a disposable namespace with bundled APISIX, create an `HTTPRoute` with both a `URLRewrite` and a `RequestHeaderModifier` `set` filter. Verify via the APISIX Admin API (`/apisix/admin/routes`) that both land in one `proxy-rewrite` config (design D4 assumption a). Record the JSON in `verify.md`.
- [ ] 1.2 Set `apisix.extraEnvVars` (Secret-sourced) and `apisix.nginx.envs` for a test variable, use `$env://VAR` as the header value, and verify an echo upstream receives the Secret's value, not the literal `$env://VAR` string (assumption b). Never print the value: compare by hash or length only.
- [ ] 1.3 Decide the path and record it in `design.md` D4: pass → proceed; (a) fails → switch to `PluginConfig`/`ExtensionRef`; (b) fails → stop and ask the owner before using any literal-in-manifest fallback.
- [ ] 1.4 Verify on the same cluster what daprd sends the app: confirm the inbound `dapr-api-token` the app sees on a service-invoked request is the **app** token, not the Dapr API token that APISIX/the caller sent (design Risks). Update D3 if it is not.

## 2. dws-admin: app-token middleware and relay

- [x] 2.1 Add `apiToken` and `appToken` to `AppConfig` in `src/config/configuration.ts`, read from `DAPR_API_TOKEN` and `APP_API_TOKEN` (empty or unset means feature off). Verify in `configuration.spec.ts` that both names are read, defaults are off, and a blank value is treated as unset.
- [x] 2.2 Implement `configureAppTokenGuard(app, token)` in `src/http/` (Express middleware, constant-time SHA-256 digest compare, exact `GET /health` exemption, identical body for missing and wrong, no token in logs). Verify with HTTP-level specs booted against the same function production calls: missing, wrong, correct, `/health`, `/health/`, `/Health`, `/docs`, `GET /dapr/subscribe`, `POST /dapr/events/dws`, and no-token-configured pass-through.
- [x] 2.3 Call it first in `bootstrap` in `src/main.ts`, before `configureBodyParsers`. Verify with a spec that an oversized unauthenticated body gets 401 without the JSON parser running.
- [x] 2.4 Verify the forged-event scenario end to end in an integration spec: `POST /dapr/events/dws` without the token returns 401 and leaves the read-model tables and the SSE stream unchanged; with the token it ingests.
- [x] 2.5 Make the relay send `dapr-api-token: DAPR_API_TOKEN` when configured, keep forwarding `Authorization` verbatim, and never forward the inbound `dapr-api-token`. Extend `controller-relay.spec.ts` for: token attached, app token not leaked, token absent when unconfigured.
- [x] 2.6 Document both variables in `dws-admin/README.md` and note the contract in `dws-admin/CLAUDE.md` if it lists config. Run `cd dws-admin && pnpm lint && pnpm test && pnpm build` and verify green.

## 3. Chart: token Secrets, annotations and env

- [x] 3.1 Add `auth.apiTokens.{admin,controller}.{daprApi,appApi}.existingSecret` to `values.yaml` (commented, empty default; controller `appApi` is documented as reserved for the follow-up and not rendered) and helpers in `_helpers.tpl` for the Secret name, the generate-or-reuse value (`lookup` + `randAlphaNum 48`), and a `secretKeyRef` snippet. Verify with `helm template` that names follow D1.
- [x] 3.2 Add `templates/auth/api-token-secrets.yaml` rendering the three Secrets (key `token`) only when `auth.enabled` and no existing Secret is supplied. Verify the auth-off render has none and the auth-on render has exactly three.
- [x] 3.3 Admin Deployment: add `dapr.io/api-token-secret`, `dapr.io/app-token-secret` annotations and `DAPR_API_TOKEN`, `APP_API_TOKEN` `secretKeyRef` env (auth only). Controller Deployment: `dapr.io/api-token-secret` and `DAPR_API_TOKEN` only. Verify no literal `value:` on either variable and no controller `app-token` references.
- [x] 3.4 `NOTES.txt` (auth on): list the Secrets that hold the tokens and the restart command; verify the rendered NOTES contains no token and no command that prints one.
- [x] 3.5 Add a "Machine-auth tokens" section to `charts/dws/README.md`: what each token is, `existingSecret` shape (key `token`), the Helm release-Secret caveat from D2, rotation runbook (Secret, pods, APISIX), restart requirement, external-APISIX variable contract. Verify the commands in it are copy-pasteable and none prints a token.

## 4. Chart: gateway injection and guard (requires 1.3 outcome)

- [x] 4.1 Add the `RequestHeaderModifier` `set` filter for `dapr-api-token` to `templates/api-gateway/admin-httproute.yaml` (value `$env://DWS_ADMIN_DAPR_API_TOKEN`), none on the console route. Update the route's header comment. Verify by render.
- [x] 4.2 Default `apisix.extraEnvVars` and `apisix.nginx.envs` in `values.yaml` for the bundled gateway, using `{{ .Release.Name }}` per D1. Verify the bundled APISIX Deployment renders the `secretKeyRef` env and the nginx `env` directive.
- [x] 4.3 Extend `dws.apiGateway.validate` to fail render for: variable missing from `extraEnvVars`; sourced from a different Secret than the resolved admin Dapr API token Secret; absent from `apisix.nginx.envs`. Verify each failure message by render.
- [x] 4.4 Add `charts/dws/tests/api-tokens-render-test.sh` covering every render scenario in `specs/helm-dapr-api-tokens/spec.md` (auth off none; auth on three Secrets; existing-Secret substitution; env is `secretKeyRef`; controller has no app token; console route has no header; the three validation failures). It must not echo Secret data. Verify it passes, and that reverting any one wiring makes it fail.
- [x] 4.5 Wire the test into `.github/workflows/helm.yml` beside the other render tests. Extend `auth-pipeline-placement-test.sh` only if it needs to still pass unchanged (it should). Verify by running all.
- [x] 4.6 Run the full chart gate from the repo root (Helm 4.3.0): `helm lint charts/dws`, `helm template dws charts/dws`, and the five existing `tests/*.sh` plus the new one. Verify every command exits 0.
  - Ran with Helm 4.3.0: `helm lint`, `helm template` and all six `charts/dws/tests/*.sh` exit 0.

## 5. Callers that deliberately skip the token

- [x] 5.1 `scripts/verify-gateway-sse-path.sh`: the direct-to-sidecar and direct-to-app calls (port-forward to the admin Service, `bearer-caller` pod) must now send the required token header from a Secret read inside the script without echoing it, and gain new negative cases (no token, wrong token, valid bearer + no token, valid token + no bearer, forged `dapr-api-token` through the Gateway). Verify against `bash -n` and shellcheck here; live run is group 6.
- [ ] 5.2 `charts/dws/templates/tests/controller-auth-negative.yaml`: keep passing (the Job calls through its own sidecar, so its call is unaffected); add a second assertion that the controller sidecar's public API rejects a call with no `dapr-api-token`, without printing any token. Verify by render and, live, `helm test`.
  - Render done, live `helm test` pending (group 6). The controller sidecar API is loopback-only (no `dapr.io/sidecar-listen-addresses` on the controller), so a hook pod cannot call it. Implemented instead as a second Job, `<controller>-token-negative-test`, whose own sidecar is configured with the controller's token Secret: 401 with no or a wrong token, 200 with the Secret's token, and the controller Service path 401 or refused. The token goes through `SENTINEL_DAPR_API_TOKEN`, never `DAPR_API_TOKEN`, to avoid injector duplication. Asserting on the real controller sidecar still needs same-pod access (task 6.3).
- [x] 5.3 `.github/workflows/helm.yml` pubsub e2e job installs with auth off, so its direct `:3000` and `:3500` calls are unaffected; verify by reading the job's `--set` list and note it in `verify.md`. If any other job turns auth on, update its calls.
- [ ] 5.4 Confirm `scripts/verify-console-ingress-migration.sh` needs no change (it issues no direct app or sidecar calls) and that it passes in group 6.
  - Read: no change needed (it makes no direct app or sidecar call; its only live calls are `wget` to the legacy admin-gateway nginx `/healthz`). Its bundled and external auth-on upgrade steps still render with the new defaults and guard. The "passes in group 6" half is pending.

## 6. Live verification on the owner's local cluster

Daprd reads `Configuration` and token env only at startup. After every `helm install`/`upgrade`,
`kubectl rollout restart` admin, controller **and** APISIX, and wait for rollout, before asserting.
Never print a token; assert on status codes.

- [ ] 6.1 Install with `auth.enabled=true`, `apiGateway.enabled=true`, `apisix.enabled=true`. Verify the subscription registers (`app is subscribed to the following topics: [[dws.events]]`), a `dws.events` message reaches the read model, and daprd logs show no 401 from the app channel (`/dapr/subscribe`, delivery, `/dapr/config`).
- [ ] 6.2 Negative bearer matrix on **both apps**, now with the correct Dapr API token on every request: no-auth / malformed / tampered-sig / wrong-aud / wrong-iss → 401, valid → 200. Admin via the sidecar, controller via service invocation from the caller pod. Record the matrix.
- [ ] 6.3 Token matrix on the admin sidecar: valid bearer + no token, valid bearer + wrong token, valid token + no bearer → 401; both valid → 200. Controller sidecar public API with no token → 401. Record.
- [ ] 6.4 Pod-IP bypass closed: from another pod, `GET <admin-pod-ip>:3000/instances` and a forged `POST <admin-pod-ip>:3000/dapr/events/dws` with no token and with a wrong token → 401, read model unchanged. `GET <admin-pod-ip>:3000/health` → 200, and the pod stays Ready (kubelet probes). Record the controller `<pod-ip>:8080` result as still open (expected 200), supporting D7.
- [ ] 6.5 Non-buffered SSE (§2d) through Gateway → APISIX → Dapr invoke → Nest: a named `event: instance` frame arrives while the connection is still open. Record time-to-first-frame.
- [ ] 6.6 APISIX upstream health checks: query the Admin API and confirm the admin upstream still has no active/passive `checks` (script assertion), or, if that changed, that the probe carries the token. Ten authenticated requests stay 200 after the negative cases.
- [ ] 6.7 Pod restart behavior: delete the admin pod, the controller pod, and the APISIX pod in turn. Verify each comes back Ready, the Gateway serves 200 with a valid bearer within the rollout window, the subscription re-registers, and the injected token still matches (no 401 loop).
- [ ] 6.8 Forged `dapr-api-token` through the Gateway with a valid bearer still returns 200 (APISIX replaced it) and a forged one cannot be used to reach the sidecar directly (401).
- [ ] 6.9 Run `scripts/verify-gateway-sse-path.sh` and `scripts/verify-console-ingress-migration.sh`; both exit 0. Run `helm test` on the release; passes.
- [ ] 6.10 Write `openspec/changes/auth-phase-10-api-tokens/verify.md` with cluster context, commands, exit codes, and the full matrix as a table (no tokens, no JWTs). Report the matrix to the owner.

## 7. Roadmap and contracts

- [ ] 7.1 `docs/roadmaps/dws-auth.md`: mark Phase 10 done with a dated progress entry; rewrite the "Accepted risk" bullet in the 2026-09-13 decision as closed; correct §4's pod-IP:8080 item (re-point to the controller app-token follow-up, withdraw `quarkus.http.host=127.0.0.1` because kubelet `httpGet` probes dial the pod IP); correct the §2e Phase 10 paragraph if verification shows it was wrong. Verify the document renders and links resolve.
  - Done except "mark Phase 10 done": the row says 🟢 implemented, not ✅, until groups 1 and 6 pass. The §2e paragraph was not changed on substance (no live data yet); a pre-existing splice in it was repaired.
- [x] 7.2 Root `CLAUDE.md`: add the chart ↔ `dws-admin` token env contract (`DAPR_API_TOKEN`, `APP_API_TOKEN`, Secret key `token`) to the cross-package contract section, naming the tests that pin it. Verify it matches the names in `configuration.ts` and the Deployment.
- [x] 7.3 Record the `dws-controller` finding (D7) as a roadmap §4 open item with severity, scope and the sized work, so the follow-up change has a starting point. Verify the item names the exact endpoints exposed.

## Workflow follow-up

- Archive the change after groups 1–7 are done and `verify.md` carries the live matrix.
- Open the `dws-controller` app-token change (D7) next.
