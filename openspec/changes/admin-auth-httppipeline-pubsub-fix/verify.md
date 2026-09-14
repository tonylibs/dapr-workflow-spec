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

## Live (Docker Desktop `docker-desktop`, 2026-09-14)

All prior live evidence predates the pipeline move and must be re-run, not cited. daprd reloads
`Configuration` only at startup — restart the admin and controller pods after `helm upgrade`
before every check below (a bare upgrade leaves a half-state).

1. `auth.enabled=true` + `apiGateway.enabled=true`, pods restarted: subscription registers,
   `dws.events` reaches the read model, SSE stays non-buffered end-to-end (§2d).
2. Full negative-bearer matrix on BOTH `dws-admin` and `dws-controller`
   (no-auth / malformed / tampered-sig / wrong-aud / wrong-iss → 401).
3. APISIX upstream health checks against the now-gated admin port do not 401 the upstream into
   unhealthy.

`helm dependency build charts/dws` completed with exit 0. The live script was run with
`auth.enabled=true`, `apiGateway.enabled=true`, `apisix.enabled=true`, and `dapr.enabled=false`
in disposable namespace `dws-gw-e2e`; it builds local admin/console images and loads them into all
three Docker Desktop nodes. The script performs this required startup refresh before assertions:

```
kubectl -n dws-gw-e2e rollout restart deployment/dws-admin deployment/dws-controller
kubectl -n dws-gw-e2e rollout status deployment/dws-admin --timeout=6m
kubectl -n dws-gw-e2e rollout status deployment/dws-controller --timeout=6m
```

The admin daprd log positively reported `app is subscribed to the following topics: [[dws.events]]`
through `pubsub=pubsub`, with no subscription 401. A normal Dapr publish from the injected caller
sidecar returned HTTP 204 and the quoted CloudEvent was accepted by the read model; the SSE probe
received a named `event: instance` frame while the connection remained open.

The full matrix passed on both paths (admin direct sidecar invoke and controller invoke from the
caller sidecar): no-auth, malformed, tampered-signature, wrong-audience, and wrong-issuer each
returned HTTP 401; the valid token returned HTTP 200. APISIX’s runtime upstream query showed the
admin node on port 3500 with no active/passive `checks`; ten authenticated requests remained 200
after the negative matrix. The verification script exits nonzero on any failed assertion.
