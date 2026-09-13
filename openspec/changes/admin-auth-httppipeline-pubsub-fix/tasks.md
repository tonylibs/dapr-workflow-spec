## 1. Chart: move the admin bearer handler, keep the controller's put

- [x] 1.1 `charts/dws/templates/admin/auth-configuration.yaml`: change `spec.appHttpPipeline` to `spec.httpPipeline`; leave the handler entry (name/type) unchanged.
- [x] 1.2 `charts/dws/templates/controller/auth-configuration.yaml`: LEAVE on `spec.appHttpPipeline` (no functional change) — verify it is untouched functionally.
- [x] 1.3 Rewrite the header comments of both `auth-configuration.yaml` files and `templates/admin/auth-component.yaml` so they state the deliberate divergence and why, instead of claiming to mirror each other.
- [x] 1.4 Confirm the Phase 3 relay (`dws-admin/src/controller-relay/controller-relay.service.ts`) still forwards `Authorization` verbatim — no code change, but now load-bearing under `httpPipeline`.

## 2. Chart: static regression test + CI

- [x] 2.1 Add `charts/dws/tests/auth-pipeline-placement-test.sh` asserting admin→`spec.httpPipeline` (and NOT `appHttpPipeline`) and controller→`spec.appHttpPipeline` (and NOT `httpPipeline`).
- [x] 2.2 Wire the new test into `.github/workflows/helm.yml` alongside the existing chart render tests.
- [x] 2.3 Run `helm lint charts/dws`, `charts/dws/tests/values-schema-test.sh`, `charts/dws/tests/api-gateway-render-test.sh`, and the new test — all green.

## 3. Controller-side pre-flight check

- [x] 3.1 Confirm `dws-controller`'s event publishing transport: `events/DaprClientProducer.java` builds the default gRPC `DaprClient`, so HTTP middleware does not gate it. Recorded in `design.md`. (If it were HTTP, the controller move would break publishing — it is not, and the controller is not moving anyway.)

## 4. Live verification (environment-blocked here — must run on a real cluster before archiving)

Every existing piece of evidence predates the pipeline move, so it must be re-produced, not
cited. daprd reloads `Configuration` only at startup, so each step requires restarting the admin
and controller pods after `helm upgrade` (a bare upgrade leaves a half-state).

- [ ] 4.1 On a live cluster with `auth.enabled=true` + `apiGateway.enabled=true`: restart pods, then confirm the subscription registers, a `dws.events` message reaches the read model, and SSE is still non-buffered end-to-end (roadmap §2d). (Environment-blocked: no live cluster in this session.)
- [ ] 4.2 Full negative-bearer matrix on BOTH apps (no-auth / malformed / tampered-sig / wrong-aud / wrong-iss → 401), since the middleware now wraps a different leg on the admin side. (Environment-blocked.)
- [ ] 4.3 APISIX upstream health checks now hit a gated port — confirm they do not 401 the upstream into unhealthy (adjust the health-check probe/route to an ungated path if needed). (Environment-blocked.)
- [ ] 4.4 Record all live evidence, exit codes, and pod-restart steps in `verify.md`. (Environment-blocked.)

## 5. Accepted / deferred (do NOT fix here)

- [x] 5.1 Document, do not close, the post-move unauthenticated reachability of `POST /dapr/events/dws` on the admin app port — owned by roadmap Phase 10. Recorded in `proposal.md` and `design.md`.
