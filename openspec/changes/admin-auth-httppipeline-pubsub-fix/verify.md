# Verification

## Static (run in this session — passing)

- `helm lint charts/dws` → `1 chart(s) linted, 0 chart(s) failed`.
- `charts/dws/tests/values-schema-test.sh charts/dws` → all checks passed.
- `charts/dws/tests/api-gateway-render-test.sh charts/dws` → all checks passed.
- `charts/dws/tests/auth-pipeline-placement-test.sh charts/dws` → all checks passed. Negative
  check confirmed: reverting the admin Configuration to `appHttpPipeline` makes the test fail, and
  restoring `httpPipeline` makes it pass again.
- Rendered `helm template ... --set auth.enabled=true` confirms:
  - admin `dws-admin-config` `Configuration` is on `spec.httpPipeline` (no `spec.appHttpPipeline`).
  - controller `dws-controller-config` `Configuration` remains on `spec.appHttpPipeline`.
- `dws-controller/src/main/java/io/dws/controller/events/DaprClientProducer.java` read directly:
  builds `new DaprClientBuilder().build()` (default gRPC transport) — event publishing is not
  HTTP-gated.
- `dws-admin/src/controller-relay/controller-relay.service.ts` read directly: still forwards the
  incoming `Authorization` header verbatim on the outbound sidecar invoke.

## Live (OWED — environment-blocked in this session, no cluster available)

All prior live evidence predates the pipeline move and must be re-run, not cited. daprd reloads
`Configuration` only at startup — restart the admin and controller pods after `helm upgrade`
before every check below (a bare upgrade leaves a half-state).

1. `auth.enabled=true` + `apiGateway.enabled=true`, pods restarted: subscription registers,
   `dws.events` reaches the read model, SSE stays non-buffered end-to-end (§2d).
2. Full negative-bearer matrix on BOTH `dws-admin` and `dws-controller`
   (no-auth / malformed / tampered-sig / wrong-aud / wrong-iss → 401).
3. APISIX upstream health checks against the now-gated admin port do not 401 the upstream into
   unhealthy.

Record command evidence, exit codes, and the pod-restart procedure here once a cluster is
available, then check off tasks 4.1–4.4.
